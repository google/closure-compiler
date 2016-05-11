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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
final class ObjectType implements TypeWithProperties {
  // TODO(dimvar): currently, we can't distinguish between an obj at the top of
  // the proto chain (nominalType = null) and an obj for which we can't figure
  // out its class
  private final NominalType nominalType;
  // If an ObjectType is a namespace, we record the Namespace object created
  // during GTI here.
  private final Namespace ns;
  private final FunctionType fn;
  private final boolean isLoose;
  private final PersistentMap<String, Property> props;
  private final ObjectKind objectKind;

  // Currently, TOP_OBJECT has two conflicting roles: the supertype of all
  // object types, and the type of an empty object literal.
  // In particular, its kind is UNRESTRICTED, which is confusing, because this
  // kind is a subkind of STRUCT and DICT.
  // We take that into account in the specialize method, but not yet in meet
  // and join.
  // TODO(dimvar): Find a clean way to split the two types & avoid the confusion
  static final ObjectType TOP_OBJECT =
      makeObjectType(null, null, null, null, false, ObjectKind.UNRESTRICTED);
  static final ObjectType TOP_STRUCT =
      makeObjectType(null, null, null, null, false, ObjectKind.STRUCT);
  static final ObjectType TOP_DICT =
      makeObjectType(null, null, null, null, false, ObjectKind.DICT);
  private static final PersistentMap<String, Property> BOTTOM_MAP =
      PersistentMap.of("_", Property.make(JSType.BOTTOM, JSType.BOTTOM));
  private static final ObjectType BOTTOM_OBJECT = new ObjectType(
      null, BOTTOM_MAP, null, null, false, ObjectKind.UNRESTRICTED);
  private static final Property UNKNOWN_PROP = Property.make(JSType.UNKNOWN, null);

  // Represents the built-in Object type. It's not available when the ObjectType
  // class is initialized because we read the definition from the externs.
  // It is kind of a hack that this is a static field (since we do create an
  // instance of JSTypes).
  // Making it non static requires significant changes and I'm not sure it's
  // worth it.
  private static NominalType builtinObject = null;

  private ObjectType(NominalType nominalType,
      PersistentMap<String, Property> props, FunctionType fn, Namespace ns,
      boolean isLoose, ObjectKind objectKind) {
    Preconditions.checkArgument(
        fn == null || fn.isQmarkFunction() || fn.isLoose() == isLoose,
        "isLoose: %s, fn: %s", isLoose, fn);
    Preconditions.checkArgument(FunctionType.isInhabitable(fn));
    Preconditions.checkArgument(fn == null || nominalType != null,
          "Cannot create function %s without nominal type", fn);
    if (ns != null && nominalType != null) {
      String name = nominalType.getName();
      Preconditions.checkArgument(name.equals("Object")
          || name.equals("Function") || name.equals("Window"),
          "Can't create namespace with nominal type %s", name);
    }
    if (nominalType != null) {
      Preconditions.checkArgument(!nominalType.isClassy() || !isLoose,
          "Cannot create loose objectType with nominal type %s", nominalType);
      Preconditions.checkArgument(fn == null || nominalType.isFunction(),
          "Cannot create objectType of nominal type %s with function (%s)",
          nominalType, fn);
    }
    this.nominalType = nominalType;
    this.props = isLoose ? loosenProps(props) : props;
    this.fn = fn;
    this.ns = ns;
    this.isLoose = isLoose;
    // Don't track @struct-ness/@dict-ness for loose objects
    this.objectKind = isLoose ? ObjectKind.UNRESTRICTED : objectKind;
  }

  // Loose object types may have properties that are also loose objects, eg,
  //   function f(obj) { obj.a.b.c = 123; }
  // This function makes sure we mark these object properties as loose.
  private static PersistentMap<String, Property> loosenProps(
      PersistentMap<String, Property> props) {
    PersistentMap<String, Property> newProps = props;
    for (Map.Entry<String, Property> entry : props.entrySet()) {
      JSType propType = entry.getValue().getType();
      ObjectType objType = propType.getObjTypeIfSingletonObj();
      if (objType != null
          && !objType.getNominalType().isClassy() && !objType.isLoose()) {
        newProps = newProps.with(
            entry.getKey(),
            Property.make(propType.withLoose(), null));
      }
    }
    return newProps;
  }

  static ObjectType makeObjectType(NominalType nominalType,
      PersistentMap<String, Property> props, FunctionType fn, Namespace ns,
      boolean isLoose, ObjectKind ok) {
    if (props == null) {
      props = PersistentMap.create();
    } else if (containsBottomProp(props) || !FunctionType.isInhabitable(fn)) {
      return BOTTOM_OBJECT;
    }
    if (fn != null && !props.containsKey("prototype")
        && (ns == null || ns.getNsProp("prototype") == null)) {
      props = props.with("prototype", UNKNOWN_PROP);
    }
    return new ObjectType(nominalType, props, fn, ns, isLoose, ok);
  }

  static ObjectType fromFunction(FunctionType fn, NominalType fnNominal) {
    return makeObjectType(
        fnNominal, null, fn, null, fn.isLoose(), ObjectKind.UNRESTRICTED);
  }

  static ObjectType fromNominalType(NominalType cl) {
    return makeObjectType(cl, null, null, null, false, cl.getObjectKind());
  }

  /** Construct an object with the given declared properties. */
  static ObjectType fromProperties(Map<String, Property> oldProps) {
    PersistentMap<String, Property> newProps = PersistentMap.create();
    for (Map.Entry<String, Property> entry : oldProps.entrySet()) {
      Property prop = entry.getValue();
      if (prop.getDeclaredType().isBottom()) {
        return BOTTOM_OBJECT;
      }
      newProps = newProps.with(entry.getKey(), prop);
    }
    return new ObjectType(null, newProps, null, null, false, ObjectKind.UNRESTRICTED);
  }

  static void setObjectType(NominalType builtinObject) {
    ObjectType.builtinObject = builtinObject;
  }

  boolean isInhabitable() {
    return this != BOTTOM_OBJECT;
  }

  static boolean containsBottomProp(PersistentMap<String, Property> props) {
    for (Property p : props.values()) {
      if (p.getType().isBottom()) {
        return true;
      }
    }
    return false;
  }

  boolean isStruct() {
    return objectKind.isStruct();
  }

  boolean isLoose() {
    return isLoose;
  }

  boolean isDict() {
    return objectKind.isDict();
  }

  boolean isFunctionWithProperties() {
    return this.fn != null && hasNonPrototypeProperties();
  }

  boolean isInterfaceInstance() {
    return this.nominalType != null && this.nominalType.isInterface();
  }

  boolean isNamespace() {
    return this.ns != null;
  }

  private boolean hasNonPrototypeProperties() {
    for (String pname : this.props.keySet()) {
      if (!pname.equals("prototype")) {
        return true;
      }
    }
    return this.ns != null;
  }

  static ImmutableSet<ObjectType> withLooseObjects(Set<ObjectType> objs) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : objs) {
      newObjs.add(obj.withLoose());
    }
    return newObjs.build();
  }

  private static boolean hasOnlyBuiltinProps(ObjectType obj, ObjectType someBuiltinObj) {
    for (String pname : obj.props.keySet()) {
      if (!someBuiltinObj.mayHaveProp(new QualifiedName(pname))) {
        return false;
      }
    }
    return true;
  }

  // Crude heuristic to decide whether a loose object is actually a scalar type
  // and methods have been called on it.
  // Does not apply to too-common properties such as toString (and for this
  // reason it doesn't apply to booleans).
  // Only uses property names; change it to use types if precision isn't
  // satisfactory.
  static JSType mayTurnLooseObjectToScalar(JSType t, JSTypes commonTypes) {
    ObjectType obj = t.getObjTypeIfSingletonObj();
    if (obj == null || !obj.isLoose() || obj.props.isEmpty() || obj.fn != null
        || hasOnlyBuiltinProps(obj, TOP_OBJECT)
        || hasOnlyBuiltinProps(
            obj, commonTypes.getArrayInstance().getObjTypeIfSingletonObj())) {
      return t;
    }
    if (hasOnlyBuiltinProps(obj, commonTypes.getNumberInstanceObjType())) {
      return JSType.NUMBER;
    }
    if (hasOnlyBuiltinProps(obj, commonTypes.getStringInstanceObjType())) {
      return JSType.STRING;
    }
    return t;
  }

  // Trade-offs about property behavior on loose object types:
  // We never mark properties as optional on loose objects. The reason is that
  // we cannot know for sure when a property is optional or not.
  // Eg, when we see an assignment to a loose obj
  //   obj.p1 = 123;
  // we cannot know if obj already has p1, or if this is a property creation.
  // If the assignment is inside an IF branch, we should not say after the IF
  // that p1 is optional. But as a consequence, this means that any property we
  // see on a loose object might be optional. That's why we don't warn about
  // possibly-inexistent properties on loose objects.
  // Last, say we infer a loose object type with a property p1 for a formal
  // parameter of a function f. If we pass a non-loose object to f that does not
  // have a p1, we warn. This may create spurious warnings, if p1 is optional,
  // but mostly it catches real bugs.

  private ObjectType withLoose() {
    if (isLoose()
        // Don't loosen nominal types
        || this.nominalType != null && this.nominalType.isClassy()
        // Don't loosen namespaces
        || this.ns != null) {
      return this;
    }
    FunctionType fn = this.fn == null ? null : this.fn.withLoose();
    PersistentMap<String, Property> newProps = PersistentMap.create();
    for (Map.Entry<String, Property> propsEntry : this.props.entrySet()) {
      String pname = propsEntry.getKey();
      Property prop = propsEntry.getValue();
      // It's wrong to warn about a possibly absent property on loose objects.
      newProps = newProps.with(pname, prop.withRequired());
    }
    // No need to call makeObjectType; we know that the new object is inhabitable.
    return new ObjectType(this.nominalType, newProps, fn, null, true, this.objectKind);
  }

  ObjectType withFunction(FunctionType ft, NominalType fnNominal) {
    Preconditions.checkState(this.isNamespace());
    Preconditions.checkState(!ft.isLoose() || ft.isQmarkFunction());
    ObjectType obj = makeObjectType(
        fnNominal, this.props, ft, this.ns, false, this.objectKind);
    this.ns.updateNamespaceType(JSType.fromObjectType(obj));
    return obj;
  }

  static ImmutableSet<ObjectType> withoutProperty(
      Set<ObjectType> objs, QualifiedName qname) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : objs) {
      newObjs.add(obj.withProperty(qname, null));
    }
    return newObjs.build();
  }

  // When type is null, this method removes the property.
  // If the property is already declared, but isDeclared is false, be careful
  // to not un-declare it.
  // If the property is already constant, but isConstant is false, be careful
  // to not un-const it.
  private ObjectType withPropertyHelper(QualifiedName qname, JSType type,
      boolean isDeclared, boolean isConstant) {
    // TODO(dimvar): We do some short-circuiting based on the declared type,
    // but maybe we can do more based also on the existing inferred type (?)
    PersistentMap<String, Property> newProps = this.props;
    if (qname.isIdentifier()) {
      String pname = qname.getLeftmostName();
      JSType declType = getDeclaredProp(qname);
      if (type == null) {
        type = declType;
      }
      if (declType != null) {
        isDeclared = true;
        if (hasConstantProp(qname)) {
          isConstant = true;
        }
        if (type != null && !type.isSubtypeOf(declType, SubtypeCache.create())) {
          // Can happen in inheritance-related type errors.
          // Not sure what the best approach is.
          // For now, just forget the inferred type.
          type = declType;
        }
      }

      if (type == null && declType == null) {
        newProps = newProps.without(pname);
      } else if (!type.equals(declType)) {
        if (isDeclared && declType == null) {
          declType = type;
        }
        newProps = newProps.with(pname,
            isConstant ?
            Property.makeConstant(null, type, declType) :
            Property.make(type, isDeclared ? declType : null));
      }
    } else { // This has a nested object
      String objName = qname.getLeftmostName();
      QualifiedName objQname = new QualifiedName(objName);
      if (!mayHaveProp(objQname)) {
        Preconditions.checkState(type == null,
            "Trying to update property %s on type %s, but sub-property %s does"
            + " not exist", qname, this, objName);
        return this;
      }
      QualifiedName innerProps = qname.getAllButLeftmost();
      Property objProp = getLeftmostProp(objQname);
      JSType inferred = type == null ?
          objProp.getType().withoutProperty(innerProps) :
          objProp.getType().withProperty(innerProps, type);
      JSType declared = objProp.getDeclaredType();
      if (!inferred.equals(declared)) {
        newProps = newProps.with(objName, objProp.isOptional() ?
            Property.makeOptional(null, inferred, declared) :
            Property.make(inferred, declared));
      }
    }
    // check for ref equality to avoid creating a new type
    if (newProps == this.props) {
      return this;
    }
    return makeObjectType(this.nominalType, newProps,
        this.fn, this.ns, this.isLoose, this.objectKind);
  }

  // When type is null, this method removes the property.
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
    Property newProp = oldProp == null
        ? UNKNOWN_PROP
        : Property.make(oldProp.getType(), oldProp.getDeclaredType());
    return makeObjectType(this.nominalType, this.props.with(pname, newProp),
        this.fn, this.ns, this.isLoose, this.objectKind);
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
        Property otherProp = resultNominalType.getProp(pname);
        if (otherProp != null) {
          newProps = addOrRemoveProp(
              specializeProps1, newProps, pname, otherProp, propsEntry.getValue());
          if (newProps == BOTTOM_MAP) {
            return BOTTOM_MAP;
          }
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
      Property otherProp =
          resultNominalType == null ? null : resultNominalType.getProp(pname);
      if (otherProp != null) {
        newProps = addOrRemoveProp(specializeProps1, newProps, pname, otherProp, newProp);
        if (newProps == BOTTOM_MAP) {
          return BOTTOM_MAP;
        }
      } else {
        if (newProp.getType().isBottom()) {
          return BOTTOM_MAP;
        }
        newProps = newProps.with(pname, newProp);
      }
    }
    return newProps;
  }

  private static PersistentMap<String, Property> addOrRemoveProp(
      boolean specializeProps1, PersistentMap<String, Property> props,
      String pname, Property nomProp, Property objProp) {
    JSType nomPropType = nomProp.getType();
    Property newProp = specializeProps1
        ? nomProp.specialize(objProp)
        : Property.meet(nomProp, objProp);
    JSType newPropType = newProp.getType();
    if (newPropType.isBottom()) {
      return BOTTOM_MAP;
    }
    if (!newPropType.isUnknown()
        && newPropType.isSubtypeOf(nomPropType, SubtypeCache.create())
        && !newPropType.equals(nomPropType)) {
      return props.with(pname, newProp);
    }
    return props.without(pname);
  }

  private static Property getProp(Map<String, Property> props, NominalType nom, String pname) {
    if (props.containsKey(pname)) {
      return props.get(pname);
    } else if (nom != null) {
      return nom.getProp(pname);
    }
    return null;
  }

  // This method needs the nominal types because otherwise a property may become
  // optional by mistake after the join.
  // joinPropsLoosely doesn't need that, because we don't create optional props
  // on loose types.
  private static PersistentMap<String, Property> joinProps(
      Map<String, Property> props1, Map<String, Property> props2,
      NominalType nom1, NominalType nom2) {
    PersistentMap<String, Property> newProps = PersistentMap.create();
    for (String pname : Sets.union(props1.keySet(), props2.keySet())) {
      Property prop1 = getProp(props1, nom1, pname);
      Property prop2 = getProp(props2, nom2, pname);
      Property newProp = null;
      if (prop1 == null) {
        newProp = prop2.withOptional();
      } else if (prop2 == null) {
        newProp = prop1.withOptional();
      } else {
        newProp = Property.join(prop1, prop2);
      }
      newProps = newProps.with(pname, newProp);
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
      if (newProps == BOTTOM_MAP) {
        return BOTTOM_MAP;
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
      if (newProps == BOTTOM_MAP) {
        return BOTTOM_MAP;
      }
    }
    return newProps;
  }

  static boolean isUnionSubtype(boolean keepLoosenessOfThis,
      Set<ObjectType> objs1, Set<ObjectType> objs2, SubtypeCache subSuperMap) {
    for (ObjectType obj1 : objs1) {
      boolean foundSupertype = false;
      for (ObjectType obj2 : objs2) {
        if (obj1.isSubtypeOfHelper(keepLoosenessOfThis, obj2, subSuperMap)) {
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

  boolean isSubtypeOf(ObjectType obj2, SubtypeCache subSuperMap) {
    return isSubtypeOfHelper(true, obj2, subSuperMap);
  }

  /**
   * Required properties are acceptable where an optional is required,
   * but not vice versa.
   * Optional properties create cycles in the type lattice, eg,
   * { } \le { p: num= }  and also   { p: num= } \le { }.
   */
  private boolean isSubtypeOfHelper(boolean keepLoosenessOfThis,
      ObjectType other, SubtypeCache subSuperMap) {
    if (other == TOP_OBJECT) {
      return true;
    }

    if ((keepLoosenessOfThis && this.isLoose) || other.isLoose) {
      return this.isLooseSubtypeOf(other, subSuperMap);
    }

    NominalType thisNt = this.nominalType;
    NominalType otherNt = other.nominalType;
    boolean checkOnlyLocalProps = true;
    if (otherNt != null && otherNt.isStructuralInterface()) {
      if (otherNt.equals(subSuperMap.get(thisNt))) {
        return true;
      }
      subSuperMap = subSuperMap.with(thisNt, otherNt);
      if (thisNt == null || !thisNt.isNominalSubtypeOf(otherNt)) {
        checkOnlyLocalProps = false;
      }
    } else if (otherNt != null && !otherNt.isStructuralInterface()
        && (thisNt == null || !thisNt.isNominalSubtypeOf(otherNt))) {
      return false;
    }

    // If nominalType1 < nominalType2, we only need to check that the
    // properties of other are in (obj1 or nominalType1)
    Set<String> otherPropNames;
    if (checkOnlyLocalProps) {
      otherPropNames = other.props.keySet();
    } else {
      otherPropNames = otherNt.getAllPropsOfInterface();
      if (otherPropNames == null) {
        // Can't check structural interfaces for subtyping during GlobalTypeInfo
        return false;
      }
    }
    if (!arePropertiesSubtypes(other, otherPropNames, subSuperMap)) {
      return false;
    }

    if (other.fn == null) {
      return true;
    } else if (this.fn == null) {
      // Can only be executed if we have declared types for callable objects.
      return false;
    }
    return this.fn.isSubtypeOf(other.fn, subSuperMap);
  }

  private boolean arePropertiesSubtypes(ObjectType other,
      Set<String> otherPropNames, SubtypeCache subSuperMap) {
    for (String pname : otherPropNames) {
      QualifiedName qname = new QualifiedName(pname);
      if (!isPropertySubtype(
          this.getLeftmostProp(qname), other.getLeftmostProp(qname),
          subSuperMap)) {
        return false;
      }
    }
    if (other.ns != null) {
      for (String pname : other.ns.getAllPropsOfNamespace()) {
        if (!otherPropNames.contains(pname)) {
          QualifiedName qname = new QualifiedName(pname);
          if (!isPropertySubtype(
              this.getLeftmostProp(qname), other.getLeftmostProp(qname),
              subSuperMap)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean isPropertySubtype(
      Property prop1, Property prop2, SubtypeCache subSuperMap) {
    if (prop2.isOptional()) {
      if (prop1 != null
          && !prop1.getType().isSubtypeOf(prop2.getType(), subSuperMap)) {
        return false;
      }
    } else {
      if (prop1 == null || prop1.isOptional() ||
          !prop1.getType().isSubtypeOf(prop2.getType(), subSuperMap)) {
        return false;
      }
    }
    return true;
  }

  // We never infer properties as optional on loose objects,
  // and we don't warn about possibly inexistent properties.
  boolean isLooseSubtypeOf(ObjectType other, SubtypeCache subSuperMap) {
    Preconditions.checkState(isLoose || other.isLoose);
    if (other == TOP_OBJECT) {
      return true;
    }

    if (!isLoose) {
      for (String pname : other.props.keySet()) {
        QualifiedName qname = new QualifiedName(pname);
        if (isStruct()) {
          if (!mayHaveProp(qname)
              || !getProp(qname).isSubtypeOf(other.getProp(qname), subSuperMap)) {
            return false;
          }
        } else {
          if (mayHaveProp(qname)
              && !getProp(qname).isSubtypeOf(other.getProp(qname), subSuperMap)) {
            return false;
          }
        }
      }
    } else { // this is loose, other may be loose
      for (String pname : this.props.keySet()) {
        QualifiedName qname = new QualifiedName(pname);
        if (other.mayHaveProp(qname)
            && !getProp(qname).isSubtypeOf(other.getProp(qname), subSuperMap)) {
          return false;
        }
      }
    }

    if (other.fn == null) {
      return this.fn == null || other.isLoose();
    } else if (this.fn == null) {
      return isLoose;
    }
    return fn.isLooseSubtypeOf(other.fn, subSuperMap);
  }

  ObjectType specialize(ObjectType other) {
    Preconditions.checkState(
        areRelatedNominalTypes(this.nominalType, other.nominalType));
    if (this == TOP_OBJECT && other.objectKind.isUnrestricted()) {
      return other;
    }
    if (this.ns != null) {
      return specializeNamespace(other);
    }
    NominalType resultNomType =
        NominalType.pickSubclass(this.nominalType, other.nominalType);
    if (resultNomType != null && resultNomType.isClassy()) {
      Preconditions.checkState(this.fn == null && other.fn == null);
      PersistentMap<String, Property> newProps =
          meetPropsHelper(true, resultNomType, this.props, other.props);
      if (newProps == BOTTOM_MAP) {
        return BOTTOM_OBJECT;
      }
      return new ObjectType(
          resultNomType, newProps, null, this.ns, false, this.objectKind);
    }
    FunctionType thisFn = this.fn;
    boolean isLoose = this.isLoose;
    if (resultNomType != null && resultNomType.isFunction() && this.fn == null) {
      thisFn = other.fn;
      isLoose = other.fn.isLoose();
    }
    PersistentMap<String, Property> newProps =
        meetPropsHelper(true, resultNomType, this.props, other.props);
    if (newProps == BOTTOM_MAP) {
      return BOTTOM_OBJECT;
    }
    FunctionType newFn = thisFn == null ? null : thisFn.specialize(other.fn);
    if (!FunctionType.isInhabitable(newFn)) {
      return BOTTOM_OBJECT;
    }
    return new ObjectType(
        resultNomType, newProps, newFn, this.ns, isLoose, this.objectKind);
  }

  // If obj represents a type of the form {p1: p2: {... {p_n: A}}}
  // then return the path p1,p2,...,p_n. Otherwise, return null.
  private static QualifiedName getPropertyPath(ObjectType obj) {
    if (obj.props.size() != 1) {
      return null;
    }
    Map.Entry<String, Property> entry = obj.props.entrySet().iterator().next();
    QualifiedName leftmostPname = new QualifiedName(entry.getKey());
    ObjectType propAsObj = entry.getValue().getType().getObjTypeIfSingletonObj();

    if (propAsObj == null) {
      return leftmostPname;
    }
    QualifiedName restPath = getPropertyPath(propAsObj);
    if (restPath == null) {
      return leftmostPname;
    }
    return QualifiedName.join(leftmostPname, restPath);
  }

  // Specializing namespace types is very expensive; not just the operation
  // itself, but also the fact that you create a large type that you flow around
  // later and many other expensive type operations happen on it.
  // Therefore, we only specialize namespace types in a very specific case: to
  // narrow down a mutable namespace field that has a union type, eg,
  // if (goog.bar.baz !== null) { ... }
  ObjectType specializeNamespace(ObjectType other) {
    Preconditions.checkNotNull(this.ns);
    if (this == other
        || other.ns != null
        || !other.getNominalType().equals(builtinObject)) {
      return this;
    }
    QualifiedName propPath = getPropertyPath(other);
    if (propPath == null) {
      return this;
    }
    JSType otherPropType = other.getProp(propPath);
    JSType thisPropType = mayHaveProp(propPath) ? getProp(propPath) : null;
    JSType newPropType =
        thisPropType == null ? null : thisPropType.specialize(otherPropType);
    if (thisPropType != null
        // Don't specialize for things like: if (goog.DEBUG) { ... }
        && thisPropType.isUnion()
        && !newPropType.isBottom()
        && newPropType.isSubtypeOf(thisPropType)
        && !thisPropType.isSubtypeOf(newPropType)) {
      return withProperty(propPath, newPropType);
    }
    return this;
  }

  static ObjectType meet(ObjectType obj1, ObjectType obj2) {
    Preconditions.checkState(
        areRelatedNominalTypes(obj1.nominalType, obj2.nominalType));
    if (obj1 == TOP_OBJECT) {
      return obj2;
    } else if (obj2 == TOP_OBJECT) {
      return obj1;
    }
    NominalType resultNomType =
        NominalType.pickSubclass(obj1.nominalType, obj2.nominalType);
    FunctionType fn = FunctionType.meet(obj1.fn, obj2.fn);
    if (!FunctionType.isInhabitable(fn)) {
      return BOTTOM_OBJECT;
    }
    boolean isLoose = obj1.isLoose && obj2.isLoose || fn != null && fn.isLoose();
    if (resultNomType != null && resultNomType.isFunction() && fn == null) {
      fn = obj1.fn == null ? obj2.fn : obj1.fn;
      isLoose = fn.isLoose();
    }
    PersistentMap<String, Property> props;
    if (isLoose) {
      props = joinPropsLoosely(obj1.props, obj2.props);
    } else {
      props = meetPropsHelper(false, resultNomType, obj1.props, obj2.props);
    }
    if (props == BOTTOM_MAP) {
      return BOTTOM_OBJECT;
    }
    ObjectKind ok = ObjectKind.meet(obj1.objectKind, obj2.objectKind);
    Namespace resultNs = Objects.equals(obj1.ns, obj2.ns) ? obj1.ns : null;
    return new ObjectType(resultNomType, props, fn, resultNs, isLoose, ok);
  }

  private static ObjectType join(ObjectType obj1, ObjectType obj2) {
    if (obj1 == TOP_OBJECT || obj2 == TOP_OBJECT) {
      return TOP_OBJECT;
    }
    NominalType nom1 = obj1.nominalType;
    NominalType nom2 = obj2.nominalType;
    Preconditions.checkState(areRelatedNominalTypes(nom1, nom2));

    if (obj1.equals(obj2)) {
      return obj1;
    }
    boolean isLoose = obj1.isLoose || obj2.isLoose;
    FunctionType fn = FunctionType.join(obj1.fn, obj2.fn);
    PersistentMap<String, Property> props;
    if (isLoose) {
      fn = fn == null ? null : fn.withLoose();
      props = joinPropsLoosely(obj1.props, obj2.props);
    } else {
      props = joinProps(obj1.props, obj2.props, nom1, nom2);
    }
    NominalType nominal = NominalType.pickSuperclass(nom1, nom2);
    // TODO(blickly): Split TOP_OBJECT from empty object and remove this case
    if (nominal == null || !nominal.isFunction()) {
      fn = null;
    }
    Namespace ns = Objects.equals(obj1.ns, obj2.ns) ? obj1.ns : null;
    return makeObjectType(nominal, props, fn, ns, isLoose,
        ObjectKind.join(obj1.objectKind, obj2.objectKind));
  }

  static ImmutableSet<ObjectType> joinSets(
      ImmutableSet<ObjectType> objs1, ImmutableSet<ObjectType> objs2) {
    if (objs1.isEmpty()) {
      return objs2;
    } else if (objs2.isEmpty()) {
      return objs1;
    }
    ObjectType[] objs1Arr = objs1.toArray(new ObjectType[0]);
    ObjectType[] keptFrom1 = Arrays.copyOf(objs1Arr, objs1Arr.length);
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj2 : objs2) {
      boolean addedObj2 = false;
      for (int i = 0; i < objs1Arr.length; i++) {
        ObjectType obj1 = objs1Arr[i];
        NominalType nominalType1 = obj1.nominalType;
        NominalType nominalType2 = obj2.nominalType;
        if (areRelatedNominalTypes(nominalType1, nominalType2)) {
          if (nominalType2 == null && nominalType1 != null
              && !obj1.isSubtypeOf(obj2, SubtypeCache.create())
              || nominalType1 == null && nominalType2 != null
              && !obj2.isSubtypeOf(obj1, SubtypeCache.create())) {
            // Don't merge other classes with record types
            break;
          }
          keptFrom1[i] = null;
          addedObj2 = true;
          // obj1 and obj2 may be in a subtype relation.
          // Even then, we want to join them because we don't want to forget
          // any extra properties in the subtype object.
          // TODO(dimvar): currently, a class and a @record that is a
          // supertype can be in the same union. We must normalize like we do
          // for other types, to maintain the invariant that the members of
          // a union are not subtypes of each other.
          newObjs.add(join(obj1, obj2));
          break;
        }
      }
      if (!addedObj2) {
        newObjs.add(obj2);
      }
    }
    for (ObjectType o : keptFrom1) {
      if (o != null) {
        newObjs.add(o);
      }
    }
    return newObjs.build();
  }

  private static boolean areRelatedNominalTypes(NominalType c1, NominalType c2) {
    if (c1 == null || c2 == null) {
      return true;
    }
    return c1.isNominalSubtypeOf(c2) || c2.isNominalSubtypeOf(c1);
  }

  // TODO(dimvar): handle greatest lower bound of interface types.
  // If we do that, we need to normalize the output, otherwise it could contain
  // two object types that are in a subtype relation, eg, see
  // NewTypeInferenceES5OrLowerTest#testDifficultObjectSpecialization.
  static ImmutableSet<ObjectType> meetSetsHelper(
      boolean specializeObjs1,
      Set<ObjectType> objs1, Set<ObjectType> objs2) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj2 : objs2) {
      for (ObjectType obj1 : objs1) {
        if (areRelatedNominalTypes(obj1.nominalType, obj2.nominalType)) {
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
        }
      }
    }
    return newObjs.build();
  }

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
    return this.nominalType == null ? ObjectType.builtinObject : this.nominalType;
  }

  @Override
  public JSType getProp(QualifiedName qname) {
    Property p = getLeftmostProp(qname);
    if (qname.isIdentifier()) {
      return p == null ? JSType.UNDEFINED : p.getType();
    } else {
      Preconditions.checkState(p != null);
      return p.getType().getProp(qname.getAllButLeftmost());
    }
  }

  @Override
  public JSType getDeclaredProp(QualifiedName qname) {
    Property p = getLeftmostProp(qname);
    if (p == null) {
      return null;
    } else if (qname.isIdentifier()) {
      return p.isDeclared() ? p.getDeclaredType() : null;
    }
    return p.getType().getDeclaredProp(qname.getAllButLeftmost());
  }

  private Property getLeftmostProp(QualifiedName qname) {
    String pname = qname.getLeftmostName();
    Property p = props.get(pname);
    if (p != null) {
      return p;
    }
    if (this.ns != null) {
      p = this.ns.getNsProp(pname);
      if (p != null) {
        return p;
      }
    }
    if (this.nominalType != null) {
      return this.nominalType.getProp(pname);
    }
    return builtinObject == null ? null : builtinObject.getProp(pname);
  }

  @Override
  public boolean mayHaveProp(QualifiedName qname) {
    Property p = getLeftmostProp(qname);
    return p != null &&
        (qname.isIdentifier() ||
        p.getType().mayHaveProp(qname.getAllButLeftmost()));
  }

  @Override
  public boolean hasProp(QualifiedName qname) {
    Preconditions.checkArgument(qname.isIdentifier());
    Property p = getLeftmostProp(qname);
    return p != null;
  }

  @Override
  public boolean hasConstantProp(QualifiedName qname) {
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
    // t1 and/or t2 may be loose in cases where there is a union of loose and
    // non-loose types, eg, !Array<!Function|string>|!Function.
    // Maybe the equality check below is too strict, we may end up checking for
    // subtyping, depending on how people use these in code.
    if (t1.isLoose()) {
      return t1.equals(t2) ? t1 : null;
    }
    if (t2.isLoose()) {
      return null;
    }
    if (!Objects.equals(t1.ns, t2.ns)) {
      return null;
    }
    NominalType nt1 = t1.nominalType;
    NominalType nt2 = t2.nominalType;
    NominalType nt;
    if (nt1 == null && nt2 == null) {
      nt = null;
    } else if (nt1 == null || nt2 == null) {
      return null;
    } else {
      nt = NominalType.unifyUnknowns(nt1, nt2);
      if (nt == null) {
        return null;
      }
    }
    FunctionType newFn = null;
    if (t1.fn != null || t2.fn != null) {
      newFn = FunctionType.unifyUnknowns(t1.fn, t2.fn);
      if (newFn == null) {
        return null;
      }
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
    return makeObjectType(nt, newProps, newFn, t1.ns, false,
        ObjectKind.join(t1.objectKind, t2.objectKind));
  }

  /**
   * Unify {@code this}, which may contain free type variables,
   * with {@code other}, a concrete type, modifying the supplied
   * {@code typeMultimap} to add any new template varaible type bindings.
   * @return Whether unification succeeded
   */
  boolean unifyWithSubtype(ObjectType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap, SubtypeCache subSuperMap) {
    if (fn != null) {
      if (other.fn == null ||
          !fn.unifyWithSubtype(other.fn, typeParameters, typeMultimap, subSuperMap)) {
        return false;
      }
    }
    NominalType thisNt = this.nominalType;
    NominalType otherNt = other.nominalType;
    if (thisNt != null && otherNt != null) {
      if (thisNt.unifyWithSubtype(
          otherNt, typeParameters, typeMultimap, subSuperMap)) {
        return true;
      }
      if (thisNt.isClass()) {
        return false;
      }
      if (thisNt.isStructuralInterface()) {
        if (thisNt.equals(subSuperMap.get(otherNt))) {
          return true;
        }
        subSuperMap = subSuperMap.with(otherNt, thisNt);
      }
    }
    if (thisNt != null && !thisNt.isStructuralInterface() && otherNt == null) {
      return false;
    }
    Set<String> thisProps = thisNt != null && thisNt.isStructuralInterface()
        ? thisNt.getAllPropsOfInterface() : this.props.keySet();
    return unifyPropsWithSubtype(
        other, thisProps, typeParameters, typeMultimap, subSuperMap);
  }

  private boolean unifyPropsWithSubtype(ObjectType other,
      Set<String> thisProps, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap, SubtypeCache subSuperMap) {
    for (String pname : thisProps) {
      QualifiedName qname = new QualifiedName(pname);
      Property thisProp = getLeftmostProp(qname);
      Property otherProp = other.getLeftmostProp(qname);
      if (thisProp.isOptional()) {
        if (otherProp != null
            && !thisProp.getType().unifyWithSubtype(
                otherProp.getType(), typeParameters, typeMultimap, subSuperMap)) {
          return false;
        }
      } else if (otherProp == null || otherProp.isOptional()
          || !thisProp.getType().unifyWithSubtype(
              otherProp.getType(), typeParameters, typeMultimap, subSuperMap)) {
        return false;
      }
    }
    return true;
  }

  ObjectType substituteGenerics(Map<String, JSType> concreteTypes) {
    if (concreteTypes.isEmpty()) {
      return this;
    }
    PersistentMap<String, Property> newProps = PersistentMap.create();
    for (Map.Entry<String, Property> propsEntry : this.props.entrySet()) {
      String pname = propsEntry.getKey();
      Property newProp =
          propsEntry.getValue().substituteGenerics(concreteTypes);
      newProps = newProps.with(pname, newProp);
    }
    FunctionType newFn = fn == null ? null : fn.substituteGenerics(concreteTypes);
    return makeObjectType(
        this.nominalType == null ? null :
        this.nominalType.instantiateGenerics(concreteTypes),
        newProps,
        newFn,
        this.ns,
        newFn != null && newFn.isQmarkFunction() || isLoose,
        this.objectKind);
  }

  @Override
  public String toString() {
    return appendTo(new StringBuilder()).toString();
  }

  StringBuilder appendTo(StringBuilder builder) {
    if (!hasNonPrototypeProperties()) {
      if (fn != null) {
        return fn.appendTo(builder);
      } else if (getNominalType() != null) {
        return getNominalType().appendTo(builder);
      }
    }
    if (nominalType != null && !nominalType.getName().equals("Function")) {
      nominalType.appendTo(builder);
    } else if (isStruct()) {
      builder.append("struct");
    } else if (isDict()) {
      builder.append("dict");
    } else if (this.ns != null) {
      builder.append(this.ns.toString());
    }
    if (this.fn != null) {
      builder.append("<|");
      fn.appendTo(builder);
      builder.append("|>");
    }
    if (nominalType == null || !props.isEmpty()) {
      builder.append('{');
      boolean firstIteration = true;
      for (String pname : new TreeSet<>(props.keySet())) {
        if (firstIteration) {
          firstIteration = false;
        } else {
          builder.append(", ");
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
    if (this == o) {
      return true;
    }
    Preconditions.checkArgument(o instanceof ObjectType);
    ObjectType other = (ObjectType) o;
    return Objects.equals(this.fn, other.fn)
        && Objects.equals(this.ns, other.ns)
        && Objects.equals(this.nominalType, other.nominalType)
        && Objects.equals(this.props, other.props);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.fn, this.ns, this.props, this.nominalType);
  }
}
