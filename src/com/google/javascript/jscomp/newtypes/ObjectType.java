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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.javascript.jscomp.newtypes.RawNominalType.PropAccess;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * {@link JSType}s include a possibly-empty set of ObjectType instances,
 * representing the non-primitive components of a union type. An ObjectType
 * instance includes information about the object: nominal type, function
 * signature, and extra properties.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
final class ObjectType implements TypeWithProperties {
  private final NominalType nominalType;
  // If an ObjectType is a namespace, we record the Namespace object created
  // during GTI here.
  private final Namespace ns;
  private final FunctionType fn;
  private final boolean isLoose;
  private final PersistentMap<String, Property> props;
  private final ObjectKind objectKind;

  private final JSTypes commonTypes;

  /** Creates a new "bottom" object. Should only be called in JSTypes. */
  static ObjectType createBottomObject(JSTypes commonTypes) {
    return new ObjectType(
        commonTypes,
        commonTypes.getObjectType(),
        checkNotNull(commonTypes.BOTTOM_PROPERTY_MAP),
        null,
        null,
        false,
        ObjectKind.UNRESTRICTED);
  }

  private ObjectType(JSTypes commonTypes, NominalType nominalType,
      PersistentMap<String, Property> props, FunctionType fn, Namespace ns,
      boolean isLoose, ObjectKind objectKind) {
    checkNotNull(commonTypes);
    checkNotNull(nominalType);
    Preconditions.checkArgument(
        fn == null || fn.isQmarkFunction() || fn.isLoose() == isLoose,
        "isLoose: %s, fn: %s", isLoose, fn);
    checkArgument(FunctionType.isInhabitable(fn));
    if (ns != null) {
      String name = nominalType.getName();
      Preconditions.checkArgument(name.equals(JSTypes.OBJLIT_CLASS_NAME)
          || name.equals("Function") || name.equals("Window"),
          "Can't create namespace with nominal type %s", name);
    }
    if (isLoose) {
      Preconditions.checkArgument(nominalType.isBuiltinObject() || nominalType.isFunction(),
          "Cannot create loose objectType with nominal type %s", nominalType);
    }
    Preconditions.checkArgument(fn == null || nominalType.isFunction(),
        "Cannot create objectType of nominal type %s with function (%s)",
        nominalType, fn);
    this.commonTypes = commonTypes;
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
          && !objType.nominalType.isClassy() && !objType.isLoose()) {
        newProps = newProps.with(
            entry.getKey(),
            Property.make(propType.withLoose(), null));
      }
    }
    return newProps;
  }

  static ObjectType makeObjectType(JSTypes commonTypes, NominalType nominalType,
      PersistentMap<String, Property> props, FunctionType fn, Namespace ns,
      boolean isLoose, ObjectKind ok) {
    checkNotNull(nominalType);
    if (props == null) {
      props = PersistentMap.create();
    } else if (containsBottomProp(props) || !FunctionType.isInhabitable(fn)) {
      return commonTypes.getBottomObject();
    }
    if (fn != null && !props.containsKey("prototype")
        && (ns == null || ns.getNsProp("prototype") == null)) {
      props = props.with("prototype", Property.make(fn.getCommonTypes().UNKNOWN, null));
    }
    return new ObjectType(commonTypes, nominalType, props, fn, ns, isLoose, ok);
  }

  static ObjectType fromFunction(FunctionType fn, NominalType fnNominal) {
    return makeObjectType(fn.getCommonTypes(),
        fnNominal, null, fn, null, fn.isLoose(), ObjectKind.UNRESTRICTED);
  }

  static ObjectType fromNominalType(NominalType cl) {
    return makeObjectType(cl.getCommonTypes(), cl, null, null, null, false, cl.getObjectKind());
  }

  /** Construct an object with the given declared properties. */
  static ObjectType fromProperties(JSTypes commonTypes, Map<String, Property> oldProps) {
    PersistentMap<String, Property> newProps = PersistentMap.create();
    for (Map.Entry<String, Property> entry : oldProps.entrySet()) {
      Property prop = entry.getValue();
      if (prop.getDeclaredType().isBottom()) {
        return commonTypes.getBottomObject();
      }
      newProps = newProps.with(entry.getKey(), prop);
    }
    return new ObjectType(commonTypes, commonTypes.getObjectType(), newProps,
        null, null, false, ObjectKind.UNRESTRICTED);
  }

  JSTypes getCommonTypes() {
    return this.commonTypes;
  }

  @SuppressWarnings("ReferenceEquality")
  boolean isInhabitable() {
    return this != this.commonTypes.getBottomObject();
  }

  /**
   * Returns true if the property map contains bottom. To guarantee
   * that no non-bottom objects have any bottom-typed properties,
   * the caller should typically short-circuit a return of the bottom
   * object if this occurs.
   */
  private static boolean containsBottomProp(PersistentMap<String, Property> props) {
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
    return this.nominalType.isInterface();
  }

  boolean isNamespace() {
    return this.ns != null;
  }

  private boolean isBuiltinObjectPrototype() {
    return this.nominalType.isBuiltinObject() && isPrototypeObject();
  }

  /**
   * Returns the prototype of this object type. For Object.prototype it returns null.
   * When Bar extends Foo, the prototype of Bar.prototype is the canonical Foo instance. (This is
   * accomplished in OTI by using the special type PrototypeObjectType.)
   * As a result, this method may return a type X for which isPrototypeObject is false, i.e.,
   * a type that has no owner function.
   */
  @Nullable
  ObjectType getPrototypeObject() {
    ObjectType proto;
    if (isPrototypeObject() && !isBuiltinObjectPrototype()) {
      proto = this.nominalType.getInstanceAsObjectType();
    } else {
      proto = this.nominalType.getPrototypeObject().getObjTypeIfSingletonObj();
    }
    if (this.equals(proto)) {
      // In JS's dynamic semantics, the only object without a __proto__ is
      // Object.prototype, but it's not representable in NTI.
      // Object.prototype is the only case where we are equal to our own prototype.
      // In this case, we should return null.
      Preconditions.checkState(
          isBuiltinObjectPrototype(),
          "Failed to reach Object.prototype in prototype chain, unexpected self-link found at %s",
          this);
      return null;
    }
    return proto;
  }

  boolean isPrototypeObject() {
    return getOwnerFunction() != null;
  }

  /**
   * If this a prototype object (e.g. Foo.prototype), this method returns the
   * associated constructor (e.g. Foo). Otherwise returns null.
   *
   * Note that we don't have a robust way of recognizing prototype objects, so we use a heuristic.
   * It must have the "constructor" property, and the same nominal type as the stored prototype.
   */
  FunctionType getOwnerFunction() {
    JSType t = getProp(new QualifiedName("constructor"));
    if (t != null && t.isFunctionType()) {
      FunctionType maybeCtor = t.getFunTypeIfSingletonObj();
      if (maybeCtor.isSomeConstructorOrInterface()) {
        JSType proto = maybeCtor.getPrototypeOfNewInstances();
        if (this.nominalType.equals(proto.getNominalTypeIfSingletonObj())) {
          return maybeCtor;
        }
      }
    }
    return null;
  }

  private boolean hasNonPrototypeProperties() {
    for (String pname : this.props.keySet()) {
      if (!pname.equals("prototype")) {
        return true;
      }
    }
    return this.ns != null;
  }

  /**
   * Returns true if the properties of obj are a subset of the
   * properties of someBuiltinObj.
   */
  private static boolean hasOnlyBuiltinProps(ObjectType obj, ObjectType someBuiltinObj) {
    for (String pname : obj.props.keySet()) {
      if (!someBuiltinObj.mayHaveProp(new QualifiedName(pname))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Applies a crude heuristic to decide whether a loose object is actually a
   * scalar type (specifically number or string) based on methods that have been
   * called on it. Does not apply to too-common properties such as toString (and
   * for this reason it doesn't apply to booleans). Only uses property names,
   * though it could be changed to use types if precision isn't satisfactory.
   */
  static JSType mayTurnLooseObjectToScalar(JSType t, JSTypes commonTypes) {
    ObjectType obj = t.getObjTypeIfSingletonObj();
    if (obj == null || !obj.isLoose() || obj.props.isEmpty() || obj.fn != null
        || hasOnlyBuiltinProps(obj, t.getCommonTypes().getTopObjectType())
        || hasOnlyBuiltinProps(
            obj, commonTypes.getArrayInstance().getObjTypeIfSingletonObj())) {
      return t;
    }
    if (hasOnlyBuiltinProps(obj, commonTypes.getNumberInstanceObjType())) {
      return t.getCommonTypes().NUMBER;
    }
    if (hasOnlyBuiltinProps(obj, commonTypes.getStringInstanceObjType())) {
      return t.getCommonTypes().STRING;
    }
    return t;
  }

  /**
   * Returns a "loose" version of this object type.
   *
   * Trade-offs about property behavior on loose object types:
   * (1) We never mark properties as optional on loose objects. The reason is
   *     that we cannot know for sure when a property is optional or not
   *     (e.g, when we see an assignment to a loose object `obj.p1 = 123`,
   *     we cannot know if obj already has p1, or if this is a property creation).
   * (2) If the assignment is inside an IF branch, we should not say after the IF
   *     that p1 is optional. But as a consequence, this means that any property we
   *     see on a loose object might be optional. That's why we don't warn about
   *     possibly-inexistent properties on loose objects.
   * (3) Last, say we infer a loose object type with a property p1 for a formal
   *     parameter of a function f. If we pass a non-loose object to f that does not
   *     have a p1, we warn. This may create spurious warnings, if p1 is optional,
   *     but mostly it catches real bugs.
   */
  ObjectType withLoose() {
    if (isTopObject()) {
      return this.commonTypes.getLooseTopObjectType();
    }
    if (isLoose()
        // Don't loosen nominal types
        || (!this.nominalType.isBuiltinObject() && !this.nominalType.isFunction())
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
    return new ObjectType(
        this.commonTypes, this.nominalType, newProps, fn, null, true, this.objectKind);
  }

  /**
   * Returns a version of this object with the given function signature.
   * This only makes sense if (1) this is a namespace, and (2) the function
   * is not loose (with the exception of the question-mark function).
   */
  ObjectType withFunction(FunctionType ft, NominalType fnNominal) {
    checkState(this.isNamespace());
    checkState(!ft.isLoose() || ft.isQmarkFunction());
    ObjectType obj = makeObjectType(
        this.commonTypes, fnNominal, this.props, ft, this.ns, false, this.objectKind);
    this.ns.updateNamespaceType(JSType.fromObjectType(obj));
    return obj;
  }

  /** Returns a transformed set of object types without the given property. */
  static ImmutableSet<ObjectType> withoutProperty(Set<ObjectType> objs, QualifiedName qname) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : objs) {
      newObjs.add(obj.withProperty(qname, null));
    }
    return newObjs.build();
  }

  /**
   * Helper method to return a new object with the property added, updated,
   * or removed. If the type is null, the property is removed. Declared
   * and constant are "one-way" switches: a declared property will not be
   * made un-declared, nor a const property made non-constant. If a
   * property is already inferred with the correct type, it will not be
   * explicitly added.
   */
  @SuppressWarnings("ReferenceEquality")
  private ObjectType withPropertyHelper(QualifiedName qname, JSType type,
      boolean isDeclared, boolean isConstant) {
    // TODO(dimvar): We do some short-circuiting based on the declared type,
    // but maybe we can do more based also on the existing inferred type (?)
    PersistentMap<String, Property> newProps = this.props;
    if (qname.isIdentifier()) {
      String pname = qname.getLeftmostName();
      JSType declType = getDeclaredProp(qname);
      JSType inferred = hasProp(qname) ? getProp(qname) : null;
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

      if (type == null) {
        newProps = newProps.without(pname);
      } else if (!type.equals(declType) && !type.equals(inferred)) {
        if (isDeclared && declType == null) {
          declType = type;
        }
        // If we're about to override an existing property, then preserve its defsite.
        Node defsite = null;
        if (hasProp(qname)) {
          defsite = getLeftmostProp(qname).getDefSite();
        }
        newProps = newProps.with(pname,
            isConstant ?
            Property.makeConstant(defsite, type, declType) :
            Property.makeWithDefsite(defsite, type, isDeclared ? declType : null));
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
    return makeObjectType(this.commonTypes, this.nominalType, newProps,
        this.fn, this.ns, this.isLoose, this.objectKind);
  }

  /**
   * Returns a new object type with the given property added or
   * (if type is null) removed.
   */
  ObjectType withProperty(QualifiedName qname, JSType type) {
    return withPropertyHelper(qname, type, false, false);
  }

  /**
   * Returns a new object type with the given declared property added.
   * Passing null for the type will mark an inferred property as
   * declared, rather than removing it.
   */
  ObjectType withDeclaredProperty(QualifiedName qname, JSType type, boolean isConstant) {
    return withPropertyHelper(qname, type, true, isConstant);
  }

  /**
   * Returns a new object type with the named property marked as required.
   * If the property is not present, it will be typed as unknown.
   */
  ObjectType withPropertyRequired(String pname) {
    Property oldProp = this.props.get(pname);
    Property newProp = oldProp == null
        ? Property.make(this.commonTypes.UNKNOWN, null)
        : Property.make(oldProp.getType(), oldProp.getDeclaredType());
    return makeObjectType(this.commonTypes, this.nominalType, this.props.with(pname, newProp),
        this.fn, this.ns, this.isLoose, this.objectKind);
  }

  /**
   * Meets the given property maps. The returned map will be a union of
   * the input maps (since the set of properties is contravariant). When
   * a property is present in both, the result will be either the meet
   * of the two types, or if specializeProps1 is given, it will be the
   * type from props1 specialized by the type from props2. The
   * resultNominalType should be the nominal type that belongs with the
   * returned property map (i.e. of the met or specialized type).
   */
  private static PersistentMap<String, Property> meetPropsHelper(
      JSTypes commonTypes,
      boolean specializeProps1, NominalType resultNominalType,
      PersistentMap<String, Property> props1,
      PersistentMap<String, Property> props2) {
    checkNotNull(resultNominalType);
    PersistentMap<String, Property> newProps = props1;
    for (Map.Entry<String, Property> propsEntry : props1.entrySet()) {
      String pname = propsEntry.getKey();
      Property otherProp = resultNominalType.getProp(pname, PropAccess.INCLUDE_STRAY_PROPS);
      if (otherProp != null) {
        newProps = addOrRemoveProp(
            specializeProps1, newProps, pname, otherProp, propsEntry.getValue());
        if (commonTypes.isBottomPropertyMap(newProps)) {
          return commonTypes.BOTTOM_PROPERTY_MAP;
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
      Property otherProp = resultNominalType.getProp(pname, PropAccess.INCLUDE_STRAY_PROPS);
      if (otherProp != null) {
        newProps = addOrRemoveProp(specializeProps1, newProps, pname, otherProp, newProp);
        if (commonTypes.isBottomPropertyMap(newProps)) {
          return commonTypes.BOTTOM_PROPERTY_MAP;
        }
      } else {
        if (newProp.getType().isBottom()) {
          return commonTypes.BOTTOM_PROPERTY_MAP;
        }
        newProps = newProps.with(pname, newProp);
      }
    }
    return newProps;
  }

  /**
   * Utility method used by meetPropsHelper. Adds the property if the result
   * type is a known proper subtype of the nominal type's property, otherwise
   * removes (since there is nothing new beyond what's already stored in the
   * nominal type). Returns bottom map if the result type would be bottom.
   */
  private static PersistentMap<String, Property> addOrRemoveProp(
      boolean specializeProps1, PersistentMap<String, Property> props,
      String pname, Property nomProp, Property objProp) {
    JSType nomPropType = nomProp.getType();
    Property newProp = specializeProps1
        ? nomProp.specialize(objProp)
        : Property.meet(nomProp, objProp);
    JSType newPropType = newProp.getType();
    if (newPropType.isBottom()) {
      return newPropType.getCommonTypes().BOTTOM_PROPERTY_MAP;
    }
    if (!newPropType.isUnknown()
        && newPropType.isSubtypeOf(nomPropType, SubtypeCache.create())
        && !newPropType.equals(nomPropType)) {
      return props.with(pname, newProp);
    }
    return props.without(pname);
  }

  /**
   * Looks up the property in the property map, falling back on the given nominal type
   * if it's not in the map.
   * Called only when joining two ObjectTypes; that's why it does not deal with namespaces.
   * @see #join
   */
  private static Property getProp(Map<String, Property> props, NominalType nom, String pname) {
    if (props.containsKey(pname)) {
      return props.get(pname);
    } else if (nom != null) {
      return nom.getProp(pname, PropAccess.INCLUDE_STRAY_PROPS);
    }
    return null;
  }

  /**
   * Joins two property maps. The nominal types are required because otherwise
   * a property may become optional by mistake after the join. This is not
   * necessary for {@link #joinPropsLoosely}, because we don't create optional
   * props on loose types.
   */
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

  /**
   * Loosely joins two property maps. Properties are marked as required because there
   * is no concept of optional properties on loose types. @see #withLoose.
   */
  private static PersistentMap<String, Property> joinPropsLoosely(
      Map<String, Property> props1, Map<String, Property> props2) {
    // Note: If ever newProps == BOTTOM_PROPERTY_MAP, it could be returned early,
    // but as long as it only ever comes from with(), that is impossible. We may
    // want to bail out early if either props1 or props2 is bottom.
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

  /**
   * Returns true if the union objs1 is a subtype of the union objs2.
   * This means that every member of objs1 has at least one supertype
   * in objs2. If keepLoosenessOfThis is false, the looseness of
   * members of objs1 will be disregarded.
   */
  static boolean isUnionSubtype(boolean keepLoosenessOfThis,
      Set<ObjectType> objs1, Set<ObjectType> objs2, SubtypeCache subSuperMap) {
    return isUnionSubtypeHelper(keepLoosenessOfThis, objs1, objs2, subSuperMap, null);
  }

  /**
   * Fills boxedInfo (a single-element "output" array) with the reason
   * why objs1 is not a subtype of objs2, for the purpose of building
   * informative error messages.
   * @throws IllegalArgumentException if objs1 actually is a subtype of objs2.
   */
  static void whyNotUnionSubtypes(boolean keepLoosenessOfThis,
      Set<ObjectType> objs1, Set<ObjectType> objs2, SubtypeCache subSuperMap,
      MismatchInfo[] boxedInfo) {
    checkArgument(boxedInfo.length == 1);
    boolean areSubtypes = isUnionSubtypeHelper(
        keepLoosenessOfThis, objs1, objs2, subSuperMap, boxedInfo);
    checkArgument(!areSubtypes);
  }

  private static boolean isUnionSubtypeHelper(boolean keepLoosenessOfThis,
      Set<ObjectType> objs1, Set<ObjectType> objs2, SubtypeCache subSuperMap,
      MismatchInfo[] boxedInfo) {
    for (ObjectType obj1 : objs1) {
      boolean foundSupertype = false;
      for (ObjectType obj2 : objs2) {
        if (obj1.isSubtypeOfHelper(keepLoosenessOfThis, obj2, subSuperMap, null)) {
          foundSupertype = true;
          break;
        }
      }
      if (!foundSupertype) {
        if (boxedInfo != null) {
          boxedInfo[0] =
              MismatchInfo.makeUnionTypeMismatch(JSType.fromObjectType(obj1));
        }
        return false;
      }
    }
    return true;
  }

  /** Returns true if this is a subtype of obj2. */
  boolean isSubtypeOf(ObjectType obj2, SubtypeCache subSuperMap) {
    return isSubtypeOfHelper(true, obj2, subSuperMap, null);
  }

  /**
   * Fills boxedInfo (a single-element "output" array) with the reason
   * why obj1 is not a subtype of obj2, for the purpose of building
   * informative error messages.
   * @throws IllegalArgumentException if obj1 actually is a subtype of obj2.
   */
  static void whyNotSubtypeOf(ObjectType obj1, ObjectType obj2, MismatchInfo[] boxedInfo) {
    checkArgument(boxedInfo.length == 1);
    boolean areSubtypes = obj1.isSubtypeOfHelper(true, obj2, SubtypeCache.create(), boxedInfo);
    Preconditions.checkState(!areSubtypes, "Type %s shouldn't be a subtype of %s", obj1, obj2);
  }

  /**
   * Required properties are acceptable where an optional is required,
   * but not vice versa.
   * Optional properties create cycles in the type lattice, eg,
   *    { } ≤ { p: num= }   and also   { p: num= } ≤ { }.
   */
  private boolean isSubtypeOfHelper(boolean keepLoosenessOfThis,
      ObjectType other, SubtypeCache subSuperMap, MismatchInfo[] boxedInfo) {
    if (other.isTopObject()) {
      return true;
    }

    if ((keepLoosenessOfThis && this.isLoose) || other.isLoose) {
      return this.isLooseSubtypeOf(other, subSuperMap);
    }

    NominalType thisNt = this.nominalType;
    NominalType otherNt = other.nominalType;
    boolean checkOnlyLocalProps = true;
    if (otherNt.isStructuralInterface()) {
      if (otherNt.equals(subSuperMap.get(thisNt))) {
        return true;
      }
      subSuperMap = subSuperMap.with(thisNt, otherNt);
      if (!thisNt.isNominalSubtypeOf(otherNt)) {
        checkOnlyLocalProps = false;
      }
      // IObject and IArrayLike are treated specially;
      // unlike other structural types, we check that the generics match.
      if (thisNt.inheritsFromIObjectReflexive() && otherNt.inheritsFromIObjectReflexive()
          && !thisNt.isIObjectSubtypeOf(otherNt)) {
        return false;
      }
      if ((thisNt.isBuiltinObject() || thisNt.isLiteralObject()) && otherNt.isIObject()) {
        return compareRecordTypeToIObject(otherNt, subSuperMap);
      }
    } else if (!thisNt.isNominalSubtypeOf(otherNt)) {
      return false;
    }

    // If nominalType1 < nominalType2, we only need to check that the
    // properties of other are in (obj1 or nominalType1)
    Set<String> otherPropNames;
    if (checkOnlyLocalProps) {
      otherPropNames = other.props.keySet();
    } else {
      otherPropNames = otherNt.getPropertyNames();
      if (otherPropNames == null) {
        // Can't check structural interfaces for subtyping during GlobalTypeInfo
        return false;
      }
    }
    if (!arePropertiesSubtypes(other, otherPropNames, subSuperMap, boxedInfo)) {
      return false;
    }

    if (other.fn == null) {
      return true;
    } else if (this.fn == null) {
      // Can only be executed if we have declared types for callable objects.
      return false;
    }
    boolean areFunsSubtypes = this.fn.isSubtypeOf(other.fn, subSuperMap);
    if (boxedInfo != null) {
      FunctionType.whyNotSubtypeOf(this.fn, other.fn, subSuperMap, boxedInfo);
    }
    return areFunsSubtypes;
  }

  /**
   * Special case of isSubtypeOf for testing a builtin or literal object as a
   * subtype of IObject⟨KEY, VALUE⟩. Specifically, checks that all properties
   * have the correct key and value type.
   *
   * NOTE(dimvar): it's not ideal that the unquoted properties of the
   * object literal are checked as part of the IObject type. We want
   * a property to either always be accessed with dot or with brackets,
   * and checking the unquoted properties against IObject gives the
   * impression that we support both kinds of accesses for the same
   * property. The alternatives are (none is very satisfactory):
   * (1) Don't check any object-literal properties against IObject
   * (2) Check all object-literal properties against IObject (what we're currently doing)
   * (3) Check only the quoted object-literal properties against IObject.
   *     This is not great because NTI also checks quoted properties individually
   *     if the name is known.
   * (4) Remember in the property map whether a property name was declared as
   *     quoted or not. This will likely involve a lot of extra plumbing.
   *
   * NOTE(sdh): if the index of the IObject is a string enum, we can do extra checking in this
   * method; we can check that the record type's properties match the enum keys. Useful or overkill?
   */
  private boolean compareRecordTypeToIObject(NominalType otherNt, SubtypeCache subSuperMap) {
    JSType keyType = otherNt.getIndexType();
    if (!keyType.isNumber() && !keyType.isString() && !keyType.isUnknown()) {
      return this.props.isEmpty();
    }
    JSType valueType = otherNt.getIndexedType();
    for (Map.Entry<String, Property> entry : this.props.entrySet()) {
      String pname = entry.getKey();
      JSType ptype = entry.getValue().getType();
      if (keyType.isNumber() && Ints.tryParse(pname) == null) {
        return false;
      }
      // Bracket accesses on the IObject (or on an Array) can generally return undefined
      // and we don't warn about that; so ignore undefined for the object literal as well.
      if (!ptype.removeType(this.commonTypes.UNDEFINED).isSubtypeOf(valueType, subSuperMap)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Iterates over the specified properties of the other object and tests that
   * all corresponding properties in this object are subtypes of other's
   * properties. Fills in boxedInfo if non-null.
   */
  private boolean arePropertiesSubtypes(ObjectType other,
      Set<String> otherPropNames, SubtypeCache subSuperMap,
      MismatchInfo[] boxedInfo) {
    for (String pname : otherPropNames) {
      QualifiedName qname = new QualifiedName(pname);
      if (!isPropertySubtype(
          pname, this.getLeftmostProp(qname), other.getLeftmostProp(qname),
          subSuperMap, boxedInfo)) {
        return false;
      }
    }
    if (other.ns != null) {
      for (String pname : other.ns.getAllPropsOfNamespace()) {
        if (!otherPropNames.contains(pname)) {
          QualifiedName qname = new QualifiedName(pname);
          if (!isPropertySubtype(
              pname, this.getLeftmostProp(qname), other.getLeftmostProp(qname),
              subSuperMap, boxedInfo)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * Returns true if prop1 (which may be null, representing an absent property)
   * is a subtype of prop2. Fills in boxedInfo if non-null.
   */
  private static boolean isPropertySubtype(String pname, Property prop1,
      Property prop2, SubtypeCache subSuperMap, MismatchInfo[] boxedInfo) {
    return boxedInfo != null
        ? getPropMismatchInfo(pname, prop1, prop2, subSuperMap, boxedInfo)
        : isPropertySubtypeHelper(prop1, prop2, subSuperMap);
  }

  private static boolean isPropertySubtypeHelper(
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

  // Like isPropertySubtypeHelper, but also provides mismatch information
  private static boolean getPropMismatchInfo(String pname, Property prop1,
      Property prop2, SubtypeCache subSuperMap, MismatchInfo[] boxedInfo) {
    checkNotNull(pname);
    if (prop2.isOptional()) {
      if (prop1 != null
          && !prop1.getType().isSubtypeOf(prop2.getType(), subSuperMap)) {
        boxedInfo[0] = MismatchInfo.makePropTypeMismatch(
            pname, prop2.getType(), prop1.getType());
        return false;
      }
    } else {
      if (prop1 == null) {
        boxedInfo[0] = MismatchInfo.makeMissingPropMismatch(pname);
        return false;
      } else if (prop1.isOptional()) {
        boxedInfo[0] = MismatchInfo.makeMaybeMissingPropMismatch(pname);
        return false;
      } else if (!prop1.getType().isSubtypeOf(prop2.getType(), subSuperMap)) {
        boxedInfo[0] = MismatchInfo.makePropTypeMismatch(
            pname, prop2.getType(), prop1.getType());
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if this is a "loose subtype" of other. This only makes
   * sense if either this or other is a loose object.  This takes into
   * account whether this object is a struct, and if all the common
   * properties are in a correct subtype relationship.
   *
   * Note that we never infer properties as optional on loose objects,
   * but also don't warn about possibly-inexistent properties.
   */
  boolean isLooseSubtypeOf(ObjectType other, SubtypeCache subSuperMap) {
    checkState(isLoose || other.isLoose);
    if (other.isTopObject()) {
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
      return this.fn == null
          || other.nominalType.isBuiltinObject() || other.isLoose();
    } else if (this.fn == null) {
      return isLoose;
    }
    return fn.isLooseSubtypeOf(other.fn);
  }

  /**
   * Specializes this object with the {@code other} (related) object type.
   * TODO(sdh): explain a bit more concretely how this is different from meet.
   */
  ObjectType specialize(ObjectType other) {
    checkState(areRelatedNominalTypes(this.nominalType, other.nominalType));
    if (isTopObject() && other.objectKind.isUnrestricted()) {
      return other;
    }
    if (this.ns != null) {
      return specializeNamespace(other);
    }
    NominalType resultNomType;
    // Don't turn an inline-record type to a @record, because doing this hides implicit type flows
    // needed for property disambiguation. In all other cases, use the result of pickSubclass.
    if (this.nominalType.isBuiltinObject() && other.nominalType.isStructuralInterface()) {
      resultNomType = this.nominalType;
    } else {
      resultNomType = NominalType.pickSubclass(this.nominalType, other.nominalType);
    }
    if (resultNomType.isClassy()) {
      checkState(this.fn == null, this.fn);
      checkState(other.fn == null, other.fn);
      PersistentMap<String, Property> newProps =
          meetPropsHelper(this.commonTypes, true, resultNomType, this.props, other.props);
      if (this.commonTypes.isBottomPropertyMap(newProps)) {
        return this.commonTypes.getBottomObject();
      }
      return new ObjectType(
          this.commonTypes, resultNomType, newProps, null, this.ns, false, this.objectKind);
    }
    FunctionType thisFn = this.fn;
    boolean isLoose = this.isLoose;
    if (resultNomType.isFunction() && this.fn == null) {
      thisFn = other.fn;
      isLoose = other.fn.isLoose();
    }
    if (isLoose && resultNomType.isLiteralObject()) {
      resultNomType = this.commonTypes.getObjectType();
    }
    PersistentMap<String, Property> newProps =
        meetPropsHelper(this.commonTypes, true, resultNomType, this.props, other.props);
    if (this.commonTypes.isBottomPropertyMap(newProps)) {
      return this.commonTypes.getBottomObject();
    }
    FunctionType newFn = thisFn == null ? null : thisFn.specialize(other.fn);
    if (!FunctionType.isInhabitable(newFn)) {
      return this.commonTypes.getBottomObject();
    }
    return new ObjectType(
        this.commonTypes, resultNomType, newProps, newFn, this.ns, isLoose, this.objectKind);
  }

  /**
   * Determines if the given object type is of the form {@code
   *   {p1: {p2: { ... {p_n: A}}}}
   * }. If so, returns the full qualified name "p1.p2.....p_n".
   * Otherwise, returns null.
   */
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

  /**
   * Handle the specific case of specializing a namespace (note that other
   * is not necessarily a namespace). Because specializing namespaces is
   * very expensive (not just the operation itself, but also the fact that
   * you create a large type that you flow around later and many other
   * expensive type operations happen on it), we only specialize namespace
   * types in a very specific case: to narrow down a mutable namespace field
   * that has a union type, e.g., {@code
   *   if (goog.bar.baz !== null) { ... }
   * }. In this case, other's nominal type is Object, and other has a single
   * "property path". In all other cases, we just return this namespace
   * object unchanged.
   */
  @SuppressWarnings("ReferenceEquality")
  ObjectType specializeNamespace(ObjectType other) {
    checkNotNull(this.ns);
    if (this == other
        || other.ns != null
        || !other.nominalType.equals(this.commonTypes.getObjectType())) {
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

  @SuppressWarnings("ReferenceEquality")
  private boolean isTopObject() {
    // Reference equality because we want to make sure that we only ever create
    // one top object type.
    return this == this.commonTypes.getTopObjectType();
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isBottomObject() {
    // Reference equality because we want to make sure that we only ever create
    // one bottom object type.
    return this == this.commonTypes.getBottomObject();
  }

  /**
   * If this is an enum object (i.e. the namespace itself, not the individual
   * elements), then returns the associated EnumType; otherwise returns null.
   */
  EnumType getEnumType() {
    if (!this.nominalType.isLiteralObject()) {
      return null;
    }
    for (Property p : this.props.values()) {
      JSType t = p.getType();
      if (t.isEnumElement()) {
        EnumType e = Iterables.getOnlyElement(t.getEnums());
        return this.equals(e.toJSType().getObjTypeIfSingletonObj()) ? e : null;
      }
    }
    return null;
  }

  boolean isEnumObject() {
    return this.getEnumType() != null;
  }

  /**
   * Computes the intersection type between two object types. Generally
   * this involves picking the subclass between the (related) nominal
   * types, a property map with properties from both object types (with
   * meets when properties overlap), and meeting any functions. If the
   * result is loose (either because both arguments are loose, or because
   * it is a loose function) then common properties are joined, instead.
   * Namespaces are not handled: a common namespace is preserved, but
   * otherwise will be dropped from the result.
   */
  static ObjectType meet(ObjectType obj1, ObjectType obj2) {
    NominalType nt1 = obj1.nominalType;
    NominalType nt2 = obj2.nominalType;
    Preconditions.checkState(areRelatedNominalTypes(nt1, nt2),
        "Unrelated nominal types %s and %s", nt1, nt2);
    if (obj1.isTopObject() || obj2.isBottomObject()) {
      return obj2;
    } else if (obj2.isTopObject() || obj1.isBottomObject()) {
      return obj1;
    }
    JSTypes commonTypes = obj1.commonTypes;
    NominalType resultNomType = NominalType.pickSubclass(nt1, nt2);
    FunctionType fn = FunctionType.meet(obj1.fn, obj2.fn);
    if (!FunctionType.isInhabitable(fn)) {
      return commonTypes.getBottomObject();
    }
    boolean isLoose = (obj1.isLoose && obj2.isLoose) || (fn != null && fn.isLoose());
    if (resultNomType.isFunction() && fn == null) {
      fn = obj1.fn == null ? obj2.fn : obj1.fn;
      isLoose = fn.isLoose();
    }
    PersistentMap<String, Property> props;
    if (isLoose) {
      // Do a simple union of the maps
      props = joinPropsLoosely(obj1.props, obj2.props);
    } else {
      props = meetPropsHelper(commonTypes, false, resultNomType, obj1.props, obj2.props);
    }
    if (commonTypes.isBottomPropertyMap(props)) {
      return commonTypes.getBottomObject();
    }
    ObjectKind ok = ObjectKind.meet(obj1.objectKind, obj2.objectKind);
    Namespace resultNs = Objects.equals(obj1.ns, obj2.ns) ? obj1.ns : null;
    return new ObjectType(commonTypes, resultNomType, props, fn, resultNs, isLoose, ok);
  }

  /**
   * Computes the union type between two object types. Generally this
   * involves picking the superclass between the (related) nominal types,
   * the union of the property maps (joining on overlaps), and joining
   * any functions. Namespaces are not handled: a common namespace is
   * preserved, but otherwise will be dropped from the result.
   */
  static ObjectType join(ObjectType obj1, ObjectType obj2) {
    if (obj1.isTopObject() || obj2.isTopObject()) {
      return obj1.commonTypes.getTopObjectType();
    }
    if (obj1.equals(obj2)) {
      return obj1;
    }
    if (obj1.isPrototypeObject() && obj2.isPrototypeObject()) {
      // When Bar and Baz extend Foo, joining Bar.prototype and Baz.prototype returns Foo.
      return join(
          obj1.getNominalType().getInstanceAsObjectType(),
          obj2.getNominalType().getInstanceAsObjectType());
    }
    NominalType nt1 = obj1.nominalType;
    NominalType nt2 = obj2.nominalType;
    checkState(nt1.isRawSubtypeOf(nt2) || nt2.isRawSubtypeOf(nt1));
    JSTypes commonTypes = obj1.commonTypes;
    boolean isLoose = obj1.isLoose || obj2.isLoose;
    FunctionType fn = FunctionType.join(obj1.fn, obj2.fn);
    PersistentMap<String, Property> props;
    if (isLoose) {
      fn = fn == null ? null : fn.withLoose();
      props = joinPropsLoosely(obj1.props, obj2.props);
    } else {
      props = joinProps(obj1.props, obj2.props, nt1, nt2);
    }
    NominalType nominal = NominalType.join(nt1, nt2);
    if (nominal.isBuiltinObject() && fn != null) {
      if (isLoose) {
        nominal = obj1.commonTypes.getFunctionType();
      } else {
        // NOTE(dimvar): we don't have a unit test that triggers this case,
        // but it happens in our regression tests.
        fn = null;
      }
    }
    Namespace ns = Objects.equals(obj1.ns, obj2.ns) ? obj1.ns : null;
    return makeObjectType(commonTypes, nominal, props, fn, ns, isLoose,
        ObjectKind.join(obj1.objectKind, obj2.objectKind));
  }

  private static boolean canMergeObjectsInJoin(ObjectType obj1, ObjectType obj2) {
    if (obj1.isTopObject() || obj2.isTopObject() || obj1.equals(obj2)) {
      return true;
    }
    NominalType nt1 = obj1.nominalType;
    NominalType nt2 = obj2.nominalType;
    // In a union, there is at most one object whose nominal type is Object (or literal object).
    if (!obj1.isPrototypeObject()
        && (nt1.isBuiltinObject() || nt1.isLiteralObject())
        && !obj2.isPrototypeObject()
        && (nt2.isBuiltinObject() || nt2.isLiteralObject())) {
      return true;
    }
    // Merge related classy objects, but don't merge a classy object with a built-in object.
    // The reason for the latter is that some joins happen during typedef resolution, when we
    // have not registered all properties on nominal types yet.
    if (nt1.isBuiltinObject()) {
      return obj1.isLoose && obj2.isSubtypeOf(obj1, SubtypeCache.create());
    }
    if (nt2.isBuiltinObject()) {
      return obj2.isLoose && obj1.isSubtypeOf(obj2, SubtypeCache.create());
    }
    return !obj1.isPrototypeObject()
        && !obj2.isPrototypeObject()
        && (areRelatedNominalTypes(nt1, nt2) || NominalType.equalRawTypes(nt1, nt2));
  }

  /**
   * Joins two sets of object types.
   * First, we put the types from both sets in a collection.
   * Then, we iterate over the collection and normalize it, so that no two elements in the
   * collection are in the subtype relationship. Joining the elements of objs1 and objs2 pairwise
   * does not ensure that the result is normalized.
   */
  static ImmutableSet<ObjectType> joinSets(
      ImmutableSet<ObjectType> objs1, ImmutableSet<ObjectType> objs2) {
    if (objs1.isEmpty()) {
      return objs2;
    } else if (objs2.isEmpty()) {
      return objs1;
    }
    List<ObjectType> objs = new ArrayList<>(objs1);
    objs.addAll(objs2);
    for (int i = 0; i < objs.size() - 1; i++) {
      ObjectType obj1 = objs.get(i);
      for (int j = i + 1; j < objs.size(); j++) {
        ObjectType obj2 = objs.get(j);
        if (canMergeObjectsInJoin(obj1, obj2)) {
          // obj1 and obj2 may be in the subtype relation.
          // Even then, we want to join them because we don't want to forget
          // any extra properties present in the subtype object.
          // TODO(dimvar): currently, a class and a @record that is a
          // supertype can be in the same union. We must normalize like we do
          // for other types, to maintain the invariant that the members of
          // a union are not subtypes of each other.
          objs.set(i, null);
          objs.set(j, join(obj1, obj2));
        }
      }
    }
    ImmutableSet.Builder<ObjectType> builder = ImmutableSet.builder();
    for (ObjectType obj : objs) {
      if (obj != null) {
        builder.add(obj);
      }
    }
    return builder.build();
  }

  /** Returns true if either nominal type is a subtype of the other. */
  private static boolean areRelatedNominalTypes(NominalType c1, NominalType c2) {
    return c1.isNominalSubtypeOf(c2) || c2.isNominalSubtypeOf(c1);
  }

  /**
   * Utility method extracting common functionality between
   * {@link #meetSets} and {@link #specializeSet}.
   *
   * TODO(dimvar): handle greatest lower bound of interface types.
   * If we do that, we need to normalize the output, otherwise it could
   * contain two object types that are in a subtype relation, e.g.,
   * see NewTypeInferenceTest#testDifficultObjectSpecialization.
   */
  static ImmutableSet<ObjectType> meetSetsHelper(
      boolean specializeObjs1, Set<ObjectType> objs1, Set<ObjectType> objs2) {
    ObjectsBuilder newObjs = new ObjectsBuilder();
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
        } else if (obj1.nominalType.isStructuralInterface()
            && obj2.isSubtypeOf(obj1, SubtypeCache.create())) {
          newObjs.add(obj2);
        } else if (obj2.nominalType.isStructuralInterface()
            && obj1.isSubtypeOf(obj2, SubtypeCache.create())) {
          newObjs.add(obj1);
        }
      }
    }
    return newObjs.build();
  }

  /**
   * Meets two sets of object types by distributing the meet over
   * the union: (A|B)∧(C|D) = (A∧C)|(A∧D)|(B∧C)|(B∧D), though an
   * individual meet will only show up in the result if the two
   * types are related - that is, one is a subclass of the other,
   * either nominally (for classes and interfaces) or structurally
   * (for records). All other cases meet to bottom and are therefore
   * excluded from the result.
   */
  static ImmutableSet<ObjectType> meetSets(Set<ObjectType> objs1, Set<ObjectType> objs2) {
    return meetSetsHelper(false, objs1, objs2);
  }

  /**
   * Similar to {@link #meetSets}, but related nominal types are
   * specalized instead of met.
   */
  static ImmutableSet<ObjectType> specializeSet(Set<ObjectType> objs1, Set<ObjectType> objs2) {
    return meetSetsHelper(true, objs1, objs2);
  }

  FunctionType getFunType() {
    return fn;
  }

  NominalType getNominalType() {
    return this.nominalType;
  }

  @Override
  public JSType getProp(QualifiedName qname) {
    Property p = getLeftmostProp(qname);
    if (qname.isIdentifier()) {
      return p == null ? this.commonTypes.UNDEFINED : p.getType();
    } else {
      checkState(p != null);
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

  /**
   * Returns the property corresponding to the left-most component of the
   * qname. All other components of qname are ignored. First checks the
   * property map, then the namespace, and finally the nominal type.
   * Returns null if no property is found.
   */
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
    return this.nominalType.getProp(pname, PropAccess.INCLUDE_STRAY_PROPS);
  }

  /**
   * Similar to {@link #getLeftmostProp}, but does not return the specialized
   * versions of any properties already present on the nominal type.
   *
   * TODO(aravindpg): This may be unsuitable from a typing point of view.
   * Revisit if needed.
   */
  private Property getLeftmostNonInheritedProp(QualifiedName qname) {
    String pname = qname.getLeftmostName();
    Property p = props.get(pname);
    // Only return the extra/specialized prop p if we know that we don't have this property
    // on our nominal type.
    if (p != null && !this.nominalType.mayHaveProp(pname)) {
      return p;
    }
    if (this.ns != null) {
      p = this.ns.getNsProp(pname);
      if (p != null) {
        return p;
      }
    }
    return this.nominalType.getNonInheritedProp(pname);
  }

  /**
   * Returns the node that defines the given property, or null if the
   * property does not exist. May return definitions on supertypes.
   */
  Node getPropertyDefSite(String propertyName) {
    return getPropertyDefSiteHelper(propertyName, false);
  }

  /**
   * Returns the node that defines the given property on this exact object, or null if the
   * property does not exist (or only exists on supertypes).
   */
  Node getNonInheritedPropertyDefSite(String propertyName) {
    return getPropertyDefSiteHelper(propertyName, true);
  }

  private Node getPropertyDefSiteHelper(String propertyName, boolean nonInheritedProp) {
    QualifiedName qname = new QualifiedName(propertyName);
    Property p = nonInheritedProp ? getLeftmostNonInheritedProp(qname) : getLeftmostProp(qname);
    // Try getters and setters specially.
    if (p == null) {
      p = getLeftmostProp(new QualifiedName(this.commonTypes.createGetterPropName(propertyName)));
    }
    if (p == null) {
      p = getLeftmostProp(new QualifiedName(this.commonTypes.createSetterPropName(propertyName)));
    }
    return p == null ? null : p.getDefSite();
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
    checkArgument(qname.isIdentifier());
    Property p = getLeftmostProp(qname);
    return p != null;
  }

  /**
   * Similar to {@link #hasProp}, but disregards properties that are
   * only defined on supertypes.
   */
  boolean hasNonInheritedProperty(QualifiedName qname) {
    checkArgument(qname.isIdentifier());
    Property p = getLeftmostNonInheritedProp(qname);
    String pname = qname.getLeftmostName();
    // Try getters and setters specially.
    if (p == null) {
      p = getLeftmostNonInheritedProp(
          new QualifiedName(this.commonTypes.createGetterPropName(pname)));
    }
    if (p == null) {
      p = getLeftmostProp(new QualifiedName(this.commonTypes.createSetterPropName(pname)));
    }
    return p != null;
  }

  @Override
  public boolean hasConstantProp(QualifiedName qname) {
    checkArgument(qname.isIdentifier());
    Property p = getLeftmostProp(qname);
    return p != null && p.isConstant();
  }

  /**
   * Unify the two types symmetrically, given that we have already instantiated
   * the type variables of interest in {@code t1} and {@code t2}, treating
   * UNKNOWN as a "hole" to be filled.
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
    if (t1.isTopObject()) {
      return t2.isTopObject() ? t1 : null;
    } else if (t2.isTopObject()) {
      return null;
    } else if (t1.isBottomObject()) {
      return t2.isBottomObject() ? t1 : null;
    } else if (t2.isBottomObject()) {
      return null;
    }
    NominalType nt = NominalType.unifyUnknowns(t1.nominalType, t2.nominalType);
    if (nt == null) {
      return null;
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
    return makeObjectType(t1.commonTypes, nt, newProps, newFn, t1.ns, false,
        ObjectKind.join(t1.objectKind, t2.objectKind));
  }

  /**
   * Unify {@code this}, which may contain free type variables,
   * with {@code other}, a concrete type, modifying the supplied
   * {@code typeMultimap} to add any new template variable type bindings.
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
    if (!thisNt.isBuiltinObject() && !otherNt.isBuiltinObject()) {
      if (thisNt.unifyWithSubtype(otherNt, typeParameters, typeMultimap, subSuperMap)) {
        return true;
      }
      if (thisNt.isStructuralInterface()) {
        if (thisNt.equals(subSuperMap.get(otherNt))) {
          return true;
        }
        subSuperMap = subSuperMap.with(otherNt, thisNt);
      } else { // class or nominal interface
        return false;
      }
    }
    if (!thisNt.isBuiltinObject() && !thisNt.isStructuralInterface()
        && otherNt.isBuiltinObject()) {
      return false;
    }
    Set<String> thisProps = !thisNt.isBuiltinObject() && thisNt.isStructuralInterface()
        ? thisNt.getPropertyNames() : this.props.keySet();
    if (thisProps == null) {// Can happen during GTI when types aren't frozen yet.
      return true;
    }
    return unifyPropsWithSubtype(other, thisProps, typeParameters, typeMultimap, subSuperMap);
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

  /**
   * Takes a map M from type variables to concrete types. For each *free*
   * type variable in this type, if the type variable is in the domain of
   * M, then it gets deeply replaced with the corresponding concrete type
   * in the returned ObjectType.
   */
  ObjectType substituteGenerics(Map<String, JSType> typeMap) {
    if (isTopObject() || typeMap.isEmpty()) {
      return this;
    }
    PersistentMap<String, Property> newProps = PersistentMap.create();
    for (Map.Entry<String, Property> propsEntry : this.props.entrySet()) {
      String pname = propsEntry.getKey();
      Property newProp =
          propsEntry.getValue().substituteGenerics(typeMap);
      newProps = newProps.with(pname, newProp);
    }
    FunctionType newFn = fn == null ? null : fn.substituteGenerics(typeMap);
    return makeObjectType(
        this.commonTypes,
        this.nominalType.substituteGenerics(typeMap),
        newProps,
        newFn,
        this.ns,
        (newFn != null && newFn.isQmarkFunction()) || isLoose,
        this.objectKind);
  }

  /**
   * Looks for the given property name (which must be a non-qualified
   * identifier) on any nominal subtypes. The built-in object type
   * always returns true.
   */
  boolean isPropDefinedOnSubtype(QualifiedName pname) {
    checkArgument(pname.isIdentifier());
    return this.nominalType.isBuiltinObject() || this.nominalType.isPropDefinedOnSubtype(pname);
  }

  /**
   * Returns true if this object refers to the type of an ambiguous object
   */
  boolean isAmbiguousObject() {
    // TODO(sdh): It's somewhat odd that we treat function namespaces differently
    // from object namespaces. The reason is for consistency with OTI, which treats
    // most object literals as anonymous objects, but not so for functions. We
    // could remove the 'fn' check and simply return true for all namespaces, but
    // we'll need to update a bunch of expectations in DisambiguatePropertiesTest
    // (which will then differ from OTI).
    if (isEnumObject() || isPrototypeObject() || (this.ns != null && this.fn != null)) {
      return false;
    }
    NominalType nt;
    if (fn != null && fn.isSomeConstructorOrInterface()) {
      // All constructors have "Function" as their nominalType, so look at instance
      // types instead for these cases.
      ObjectType instance = fn.getInstanceTypeOfCtor().getObjTypeIfSingletonObj();
      nt = instance != null ? instance.nominalType : getCommonTypes().getObjectType();
    } else {
      nt = this.nominalType;
    }
    return nt.isFunction() || nt.isBuiltinObject() || nt.isLiteralObject();
  }

  Set<String> getPropertyNames() {
    Set<String> props = new LinkedHashSet<>();
    props.addAll(this.props.keySet());
    props.addAll(this.nominalType.getPropertyNames());
    return props;
  }

  Iterable<String> getNonInheritedPropertyNames() {
    if (this.nominalType.isBuiltinObject() || this.nominalType.isLiteralObject()) {
      return this.props.keySet();
    }
    return Iterables.concat(this.props.keySet(), this.nominalType.getAllNonInheritedProps());
  }

  ObjectType toAnonymousRecord() {
    if (this.nominalType.isBuiltinObject() || this.nominalType.isLiteralObject()) {
      return this;
    }
    Map<String, Property> propMap = new LinkedHashMap<>();
    for (String pname : getNonInheritedPropertyNames()) {
      JSType ptype = getProp(new QualifiedName(pname));
      propMap.put(pname, Property.make(ptype, ptype));
    }
    return fromProperties(this.commonTypes, propMap);
  }

  Node getDefSite() {
    if (this.ns != null) {
      return this.ns.getDefSite();
    }
    if (this.fn != null && this.fn.isSomeConstructorOrInterface()) {
      return this.fn.getInstanceTypeOfCtor().getSource();
    }
    if (this.nominalType != null) {
      return this.nominalType.getDefSite();
    }
    return null;
  }

  JSType getNamespaceType() {
    return this.ns.toJSType();
  }

  @Override
  public String toString() {
    return appendTo(new StringBuilder(), ToStringContext.TO_STRING).toString();
  }

  String toString(ToStringContext ctx) {
    return appendTo(new StringBuilder(), ctx).toString();
  }

  StringBuilder appendTo(StringBuilder builder, ToStringContext ctx) {
    // "Foo.prototype" is a valid type when appropriate.
    if (isPrototypeObject()) {
      return builder.append(getOwnerFunction().getThisType().getDisplayName()).append(".prototype");
    }
    // Annotations need simpler output that can be re-parsed.
    if (ctx.forAnnotation()) {
      if (fn != null) {
        fn.appendTo(builder, ctx);
      } else if (!props.isEmpty()) {
        appendPropsTo(builder, ctx);
      } else if (nominalType.isLiteralObject()) {
        // Note: if nominalType.isLiteralObject (e.g. from a non-const namespace)
        // then it will append as "Object{}", which is not a valid annotation.
        builder.append("!Object");
      } else {
        nominalType.appendTo(builder, ctx);
      }
      return builder;
    }
    // If it's just a simple function or class with no stray fields, return that.
    if (!hasNonPrototypeProperties()) {
      if (fn != null) {
        return fn.appendTo(builder, ctx);
      }
      return this.nominalType.appendTo(builder, ctx);
    }
    // More thorough stringification when annotation support is not needed.
    if (!nominalType.isFunction()
        && !nominalType.isBuiltinObject()
        && !nominalType.isLiteralObject()
        && !isNamespace()) {
      nominalType.appendTo(builder, ctx);
    } else if (isStruct()) {
      builder.append("struct");
    } else if (isDict()) {
      builder.append("dict");
    } else if (this.ns != null) {
      if (this.fn != null && (this.fn.isUniqueConstructor() || this.fn.isInterfaceDefinition())) {
        // Add prefix to distinguish a constructor namespace from its instance type
        builder.append("class:");
      }
      builder.append(this.ns);
    } else if (this.fn != null) {
      builder.append("<|");
      fn.appendTo(builder, ctx);
      builder.append("|>");
    }
    if (ns == null) {
      appendPropsTo(builder, ctx);
    }
    if (isLoose) {
      builder.append(" (loose)");
    }
    return builder;
  }

  private void appendPropsTo(StringBuilder builder, ToStringContext ctx) {
    builder.append('{');
    boolean firstIteration = true;
    for (String pname : new TreeSet<>(props.keySet())) {
      if (firstIteration) {
        firstIteration = false;
      } else {
        builder.append(", ");
      }
      builder.append(pname);
      builder.append(": ");
      props.get(pname).appendTo(builder, ctx);
    }
    builder.append('}');
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ObjectType)) {
      return false;
    }
    if (this == o) {
      return true;
    }
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

  @Override
  public Collection<JSType> getSubtypesWithProperty(QualifiedName qname) {
    return this.nominalType.getSubtypesWithProperty(qname);
  }
}
