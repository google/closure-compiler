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
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class ObjectType {
  // TODO(user): currently, we can't distinguish between an obj at the top of
  // the proto chain (nominalType = null) and an obj for which we can't figure
  // out its class
  private final NominalType nominalType;
  private final FunctionType fn;
  private final boolean isLoose;
  private final ImmutableMap<String, Property> props;

  static final ObjectType TOP_OBJECT =
      ObjectType.makeObjectType(null, null, null, false);

  private ObjectType(NominalType nominalType,
      ImmutableMap<String, Property> props, FunctionType fn, boolean isLoose) {
    this.nominalType = nominalType;
    this.props = props;
    this.fn = fn;
    this.isLoose = isLoose;
  }

  static ObjectType makeObjectType(NominalType nominalType,
      Map<String, Property> props, FunctionType fn, boolean isLoose) {
    if (props == null) {
      props = ImmutableMap.of();
    }
    return new ObjectType(nominalType, ImmutableMap.copyOf(props), fn, isLoose);
  }

  static ObjectType fromFunction(FunctionType fn) {
    return ObjectType.makeObjectType(null, null, fn, fn.isLoose());
  }

  public static ObjectType fromNominalType(NominalType cl) {
    return ObjectType.makeObjectType(cl, null, null, false);
  }

  /** Construct an object with the given declared non-optional properties. */
  static ObjectType fromProperties(Map<String, JSType> props) {
    ImmutableMap.Builder<String, Property> builder = ImmutableMap.builder();
    for (String propName : props.keySet()) {
      JSType propType = props.get(propName);
      builder.put(propName, new Property(propType, propType, false));
    }
    return ObjectType.makeObjectType(null, builder.build(), null, false);
  }

  boolean isInhabitable() {
    for (String pname: props.keySet()) {
      if (!props.get(pname).getType().isInhabitable()) {
        return false;
      }
    }
    // TODO(user): do we need a stricter check for functions?
    return true;
  }

  static ImmutableSet<ObjectType> withLooseObjects(Set<ObjectType> objs) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj: objs) {
      newObjs.add(obj.withLoose());
    }
    return newObjs.build();
  }

  private ObjectType withLoose() {
    if (this.nominalType != null) { // Don't loosen nominal types
      return this;
    }
    FunctionType fn = this.fn == null ? null : this.fn.withLoose();
    Map<String, Property> newProps = Maps.newHashMap();
    for (String pname: this.props.keySet()) {
      // It's wrong to warn about a possibly absent property on loose objects.
      newProps.put(pname, this.props.get(pname).withRequired());
    }
    return ObjectType.makeObjectType(nominalType, newProps, fn, true);
  }

  static ImmutableSet<ObjectType> withoutProperty(
      Set<ObjectType> objs, String qname) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj: objs) {
      newObjs.add(obj.withProperty(qname, null));
    }
    return newObjs.build();
  }

  private ObjectType withPropertyHelper(
      String qname, JSType type, boolean isDeclared) {
    // TODO(blickly): If the prop exists with right type, short circuit here.
    Map<String, Property> newProps = Maps.newHashMap(this.props);
    if (TypeUtils.isIdentifier(qname)) {
      if (type == null) {
        newProps.remove(qname);
      } else {
        newProps.put(qname,
            new Property(type, isDeclared ? type : null, false));
      }
    } else { // This has a nested object
      String objName = qname.substring(0, qname.indexOf('.'));
      String innerProps = qname.substring(qname.indexOf('.') + 1);
      if (!this.props.containsKey(objName)) {
        Preconditions.checkState(mayHaveProp(objName));
        newProps.put(objName, getLeftmostProp(objName));
      }
      Property objProp = newProps.get(objName);
      newProps.put(objName,
          new Property(objProp.getType().withProperty(innerProps, type),
            objProp.getDeclaredType(), objProp.isOptional()));
    }
    return ObjectType.makeObjectType(nominalType, newProps, fn, isLoose);
  }

  ObjectType withProperty(String qname, JSType type) {
    return withPropertyHelper(qname, type, false);
  }

  static ImmutableSet<ObjectType> withProperty(
      Set<ObjectType> objs, String qname, JSType type) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj: objs) {
      newObjs.add(obj.withProperty(qname, type));
    }
    return newObjs.build();
  }

  static ImmutableSet<ObjectType> withDeclaredProperty(
      Set<ObjectType> objs, String qname, JSType type) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj: objs) {
      newObjs.add(obj.withPropertyHelper(qname, type, true));
    }
    return newObjs.build();
  }

  private ObjectType withPropertyRequired(String qname) {
    Preconditions.checkArgument(TypeUtils.isIdentifier(qname));
    Map<String, Property> newProps = Maps.newHashMap(this.props);
    Property oldProp = this.props.get(qname);
    Property newProp = oldProp == null ?
        new Property(JSType.UNKNOWN, null, false) :
        new Property(oldProp.getType(), oldProp.getDeclaredType(), false);
    newProps.put(qname, newProp);
    return ObjectType.makeObjectType(nominalType, newProps, fn, isLoose);
  }

  static ImmutableSet<ObjectType> withPropertyRequired(
      Set<ObjectType> objs, String qname) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj: objs) {
      newObjs.add(obj.withPropertyRequired(qname));
    }
    return newObjs.build();
  }

  private static Map<String, Property> meetPropsHelper(
      boolean specializeProps1,
      NominalType resultNominalType,
      Map<String, Property> props1, Map<String, Property> props2) {
    Map<String, Property> newProps = Maps.newHashMap();
    for (String pname : props1.keySet()) {
      if (!props2.containsKey(pname)) {
        mayPutProp(pname, props1.get(pname), newProps, resultNominalType);
      }
    }
    for (String pname : props2.keySet()) {
      Property prop2 = props2.get(pname);
      Property newProp;
      if (props1.containsKey(pname)) {
        newProp = specializeProps1 ?
            props1.get(pname).specialize(prop2) :
            Property.meet(props1.get(pname), prop2);
      } else {
        newProp = prop2;
      }
      mayPutProp(pname, newProp, newProps, resultNominalType);
    }
    return newProps;
  }

  private static void mayPutProp(String pname, Property prop,
      Map<String, Property> props, NominalType nom) {
    Property nomProp = nom == null ? null : nom.getProp(pname);
    if (nomProp == null || prop.getType().isSubtypeOf(nomProp.getType())) {
      props.put(pname, prop);
    }
  }

  private static Map<String, Property> joinProps(
      Map<String, Property> props1, Map<String, Property> props2) {
    Map<String, Property> newProps = Maps.newHashMap();
    for (String pname : props1.keySet()) {
      if (!props2.containsKey(pname)) {
        newProps.put(pname, props1.get(pname).withOptional());
      }
    }
    for (String pname : props2.keySet()) {
      Property prop2 = props2.get(pname);
      if (props1.containsKey(pname)) {
        newProps.put(pname, Property.join(props1.get(pname), prop2));
      } else {
        newProps.put(pname, prop2.withOptional());
      }
    }
    return newProps;
  }

  private static Map<String, Property> joinPropsLoosely(
      Map<String, Property> props1, Map<String, Property> props2) {
    Map<String, Property> newProps = Maps.newHashMap();
    for (String pname : props1.keySet()) {
      if (!props2.containsKey(pname)) {
        newProps.put(pname, props1.get(pname).withRequired());
      }
    }
    for (String pname : props2.keySet()) {
      Property prop2 = props2.get(pname);
      if (props1.containsKey(pname)) {
        newProps.put(pname,
            Property.join(props1.get(pname), prop2).withRequired());
      } else {
        newProps.put(pname, prop2.withRequired());
      }
    }
    return newProps;
  }

  static boolean isUnionSubtype(Set<ObjectType> objs1, Set<ObjectType> objs2) {
    for (ObjectType obj1: objs1) {
      boolean foundSupertype = false;
      for (ObjectType obj2: objs2) {
        if (obj1.isSubtypeOf(obj2)) {
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

  /**
   * Required properties are acceptable where a optional is required,
   * but not vice versa.
   * Optional properties create cycles in the type lattice, eg,
   * { } \le { p: num= }  and also   { p: num= } \le { }.
   */
  boolean isSubtypeOf(ObjectType obj2) {
    if (this.isLoose || obj2.isLoose) {
      return this.isLooseSubtypeOf(obj2);
    }
    // If nominalType1 < nominalType2, we only need to check that the
    // properties of obj2 are in (obj1 or nominalType1)
    for (String pname : obj2.props.keySet()) {
      Property prop2 = obj2.props.get(pname);
      Property prop1 = this.getLeftmostProp(pname);

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

    if ((this.nominalType == null && obj2.nominalType != null)
        || this.nominalType != null && obj2.nominalType != null &&
        !this.nominalType.isSubclassOf(obj2.nominalType)) {
      return false;
    }

    if (obj2.fn == null) {
      return true;
    } else if (this.fn == null) {
      // Can only be executed if we have declared types for callable objects.
      return false;
    }
    return this.fn.isSubtypeOf(obj2.fn);
  }

  boolean isLooseSubtypeOf(ObjectType obj2) {
    if (obj2 == TOP_OBJECT) {
      return true;
    }
    for (String pname: obj2.props.keySet()) {
      if (props.containsKey(pname)) {
        if (!props.get(pname).getType()
            .isSubtypeOf(obj2.props.get(pname).getType())) {
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

  ObjectType specialize(ObjectType obj2) {
    Preconditions.checkState(
        areRelatedClasses(this.nominalType, obj2.nominalType));
    NominalType resultNominalType =
        NominalType.pickSubclass(this.nominalType, obj2.nominalType);
    return ObjectType.makeObjectType(
        resultNominalType,
        meetPropsHelper(true, resultNominalType, this.props, obj2.props),
        (fn == null) ? null : fn.specialize(obj2.fn),
        this.isLoose || obj2.isLoose);
  }

  static ObjectType meet(ObjectType obj1, ObjectType obj2) {
    Preconditions.checkState(
        areRelatedClasses(obj1.nominalType, obj2.nominalType));
    NominalType resultNominalType =
        NominalType.pickSubclass(obj1.nominalType, obj2.nominalType);
    return ObjectType.makeObjectType(
        resultNominalType,
        meetPropsHelper(false, resultNominalType, obj1.props, obj2.props),
        FunctionType.meet(obj1.fn, obj2.fn),
        obj1.isLoose || obj2.isLoose);
  }

  static ObjectType join(ObjectType obj1, ObjectType obj2) {
    Preconditions.checkState(
        areRelatedClasses(obj1.nominalType, obj2.nominalType));
    return ObjectType.makeObjectType(
        NominalType.pickSuperclass(obj1.nominalType, obj2.nominalType),
        (obj1.isLoose || obj2.isLoose) ?
          joinPropsLoosely(obj1.props, obj2.props) :
          joinProps(obj1.props, obj2.props),
        FunctionType.join(obj1.fn, obj2.fn),
        obj1.isLoose || obj2.isLoose);
  }

  static ImmutableSet<ObjectType> joinSets(
      ImmutableSet<ObjectType> objs1, ImmutableSet<ObjectType> objs2) {
    if (objs1 == null) {
      return objs2;
    } else if (objs2 == null) {
      return objs1;
    }
    Set<ObjectType> newObjs = Sets.newHashSet(objs1);
    for (ObjectType obj2: objs2) {
      boolean addedObj2 = false;
      for (ObjectType obj1: objs1) {
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
    // TODO(user): handle greatest lower bound of interface types
    if (objs1 == null || objs2 == null) {
      return null;
    }
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj2: objs2) {
      for (ObjectType obj1: objs1) {
        if (areRelatedClasses(obj1.nominalType, obj2.nominalType)) {
          newObjs.add(specializeObjs1 ?
              obj1.specialize(obj2) : meet(obj1, obj2));
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

  JSType getProp(String qname) {
    Property p = getLeftmostProp(qname);
    if (TypeUtils.isIdentifier(qname)) {
      return p == null ? JSType.UNDEFINED : p.getType();
    } else {
      Preconditions.checkState(p != null);
      return p.getType().getProp(TypeUtils.getPropPath(qname));
    }
  }

  NominalType getNominalType() {
    return nominalType;
  }

  boolean mayHaveProp(String qname) {
    Property p = getLeftmostProp(qname);
    return p != null &&
        (TypeUtils.isIdentifier(qname) ||
        p.getType().mayHaveProp(TypeUtils.getPropPath(qname)));
  }

  boolean hasProp(String pname) {
    Preconditions.checkArgument(TypeUtils.isIdentifier(pname));
    Property p = getLeftmostProp(pname);
    if (p == null || p.isOptional()) {
      return false;
    }
    return true;
  }

  JSType getDeclaredProp(String pname) {
    Preconditions.checkArgument((TypeUtils.isIdentifier(pname)));
    Property p = getLeftmostProp(pname);
    if (p == null) {
      return null;
    } else {
      return p.isDeclared() ? p.getDeclaredType() : null;
    }
  }

  private Property getLeftmostProp(String qname) {
    String objName = TypeUtils.getQnameRoot(qname);
    Property p = props.get(objName);
    if (p == null && nominalType != null) {
      p = nominalType.getProp(objName);
    }
    return p;
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
    Map<String, Property> newprops = Maps.newHashMap();
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
      newprops.put(propName, p);
    }
    return makeObjectType(t1.nominalType, newprops, t1.fn, t1.isLoose || t2.isLoose);
  }

  /**
   * Unify {@code this}, which may contain free type variables,
   * with {@code other}, a concrete type, modifying the supplied
   * {@code typeMultimap} to add any new template varaible type bindings.
   * @return Whether unification succeeded
   */
  boolean unifyWith(ObjectType other, List<String> templateVars,
      Multimap<String, JSType> typeMultimap) {
    // TODO(blickly): With generic classes, we will need to have nominalType.unifyWith
    if (nominalType != other.nominalType) {
      return false;
    }
    if (fn != null) {
      if (other.fn == null ||
          !fn.unifyWith(other.fn, templateVars, typeMultimap)) {
        return false;
      }
    }
    for (String propName : this.props.keySet()) {
      Property thisProp = props.get(propName);
      Property otherProp = other.props.get(propName);
      if (otherProp == null ||
          !thisProp.unifyWith(otherProp, templateVars, typeMultimap)) {
        return false;
      }
    }
    return true;
  }

  ObjectType substituteGenerics(Map<String, JSType> concreteTypes) {
    ImmutableMap<String, Property> newProps = null;
    if (props != null) {
      ImmutableMap.Builder<String, Property> builder = ImmutableMap.builder();
      for (String p: props.keySet()) {
        builder.put(p, props.get(p).substituteGenerics(concreteTypes));
      }
      newProps = builder.build();
    }
    return new ObjectType(
        nominalType == null ? null :
        nominalType.instantiateGenerics(concreteTypes),
        newProps,
        fn == null ? null : fn.substituteGenerics(concreteTypes),
        isLoose);
  }

  @Override
  public String toString() {
    if ((props.isEmpty() ||
         (props.size() == 1 && props.containsKey("prototype"))) &&
        fn != null) {
      return fn.toString();
    }
    SortedSet<String> propStrings = Sets.newTreeSet();
    for (String pname : props.keySet()) {
      propStrings.add(pname + " : " + props.get(pname).toString());
    }
    String result = nominalType == null ? "" : nominalType.toString();
    result += (nominalType != null && propStrings.isEmpty()) ?
        "" : "{" + Joiner.on(", ").join(propStrings) + "}";
    result += (isLoose ? " (loose)" : "");
    return result;
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
