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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.newtypes.RawNominalType.PropAccess;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class NominalType implements Serializable {
  // In the case of a generic type (rawType.typeParameters non-empty) either:
  // a) typeMap is empty, this is an uninstantiated generic type (Foo<T>), or
  // b) typeMap's keys exactly correspond to the type parameters of rawType;
  //    this represents a completely instantiated generic type (Foo<number>).
  private final ImmutableMap<String, JSType> typeMap;
  private final RawNominalType rawType;
  private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");

  NominalType(ImmutableMap<String, JSType> typeMap, RawNominalType rawType) {
    checkState(
        typeMap.isEmpty()
            || (typeMap.keySet().containsAll(rawType.getTypeParameters())
                && rawType.getTypeParameters().containsAll(typeMap.keySet())));
    this.typeMap = typeMap;
    this.rawType = rawType;
  }

  /**
   * Use with caution.
   * After GlobalTypeInfo, most calling contexts usually want the fully-instantiated types for
   * properties defined on types, etc., but by accessing the raw nominal type directly they will
   * get the uninstantiated generic types instead.
   */
  public RawNominalType getRawNominalType() {
    return this.rawType;
  }

  // NOTE(dimvar): we need this to get access to the static properties of the class.
  // It'd be good if these properties were on the type returned by getConstructorFunction,
  // but there are some circularity issues when we're computing the namespace types.
  // Maybe revisit in the future to improve this.
  public JSType getNamespaceType() {
    return this.rawType.toJSType();
  }

  public JSType getInstanceAsJSType() {
    return (this.rawType.isGeneric() && !typeMap.isEmpty())
        ? JSType.fromObjectType(ObjectType.fromNominalType(this))
        : this.rawType.getInstanceAsJSType();
  }

  ObjectType getInstanceAsObjectType() {
    return (this.rawType.isGeneric() && !typeMap.isEmpty())
        ? ObjectType.fromNominalType(this)
        : this.rawType.getInstanceAsJSType().getObjTypeIfSingletonObj();
  }

  JSTypes getCommonTypes() {
    return this.rawType.getCommonTypes();
  }

  ObjectKind getObjectKind() {
    return this.rawType.getObjectKind();
  }

  ImmutableMap<String, JSType> getTypeMap() {
    return typeMap;
  }

  ImmutableList<String> getTypeParameters() {
    return this.rawType.getTypeParameters();
  }

  JSType getIndexType() {
    if (isIObject()) {
      return this.typeMap.get(this.rawType.getTypeParameters().get(0));
    }
    // This type is a subtype of all indexed types it inherits from,
    // and we use contravariance for the key of the index operation,
    // so we join here.
    JSType result = getCommonTypes().BOTTOM;
    for (NominalType interf : getInstantiatedIObjectInterfaces()) {
      JSType tmp = interf.getIndexType();
      if (tmp != null) {
        result = JSType.join(result, tmp);
      }
    }
    return result.isBottom() ? null : result;
  }

  JSType getIndexedType() {
    if (isIObject()) {
      return this.typeMap.get(this.rawType.getTypeParameters().get(1));
    }
    // This type is a subtype of all indexed types it inherits from,
    // and we use covariance for the value of the index operation,
    // so we meet here.
    JSType result = getCommonTypes().TOP;
    // We need this because the index type may explicitly be TOP.
    boolean foundIObject = false;
    for (NominalType interf : getInstantiatedIObjectInterfaces()) {
      JSType tmp = interf.getIndexedType();
      if (tmp != null) {
        foundIObject = true;
        result = JSType.meet(result, tmp);
      }
    }
    return foundIObject ? result : null;
  }

  boolean inheritsFromIObjectReflexive() {
    return this.rawType.inheritsFromIObjectReflexive();
  }

  boolean isClassy() {
    return !isFunction() && !isBuiltinObject() && !isLiteralObject();
  }

  public boolean isFunction() {
    return this.rawType.isBuiltinWithName("Function");
  }

  public boolean isBuiltinObject() {
    return this.rawType.isBuiltinObject();
  }

  public boolean isLiteralObject() {
    return this.rawType.isBuiltinWithName(JSTypes.OBJLIT_CLASS_NAME);
  }

  boolean isIObject() {
    return this.rawType.isBuiltinWithName("IObject");
  }

  boolean isIArrayLike() {
    return this.rawType.isBuiltinWithName("IArrayLike");
  }

  public boolean isStruct() {
    return this.rawType.isStruct();
  }

  public boolean isDict() {
    return this.rawType.isDict();
  }

  public boolean isGeneric() {
    return this.rawType.isGeneric();
  }

  public boolean isUninstantiatedGenericType() {
    return this.rawType.isGeneric() && typeMap.isEmpty();
  }

  public Node getDefSite() {
    return this.rawType.getDefSite();
  }

  public JSType getPrototypeObject() {
    return this.rawType.getPrototypeObject();
  }

  public FunctionType getConstructorFunction() {
    if (this.typeMap.isEmpty()) {
      return this.rawType.getConstructorFunction();
    }
    return this.rawType.getConstructorFunction().instantiateGenerics(this.typeMap);
  }

  /**
   * Substitute the free type variables in this type using the provided type map.
   * The most common case when this happens is when a nominal type has already been "instantiated"
   * to type variables, and now we want to substitute concrete types for these type variables.
   * For example, in the program below, Array's T is instantiated to U in the type of f,
   * and when we call f, we substitute boolean for U.
   */
  // Written as line comment to enable use of jsdoc
  // /**
  //  * @template U
  //  * @param {!Array<U>} x
  //  */
  // function f(x) { return x[0]; }
  // f([true, false]);
  NominalType substituteGenerics(Map<String, JSType> newTypeMap) {
    if (!isGeneric()) {
      return this.rawType.getAsNominalType();
    }
    // NOTE(dimvar): in rare cases, because of the way we represent types, we may end up calling
    // substituteGenerics on a nominal type that has an empty type map, which is counter-intuitive.
    // Might be worth it at some point to identify all those cases and make sure that types are
    // instantiated to identity, rather than having an empty type map. Not super important though.
    if (this.typeMap.isEmpty()) {
      return instantiateGenerics(newTypeMap);
    }
    if (newTypeMap.isEmpty()) {
      return this;
    }
    ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
    for (String oldKey : this.typeMap.keySet()) {
      builder.put(oldKey, this.typeMap.get(oldKey).substituteGenerics(newTypeMap));
    }
    return new NominalType(builder.build(), this.rawType);
  }

  NominalType instantiateGenerics(Map<String, JSType> newTypeMap) {
    if (newTypeMap.isEmpty()) {
      return this;
    }
    if (!this.rawType.isGeneric()) {
      return this.rawType.getAsNominalType();
    }
    Preconditions.checkState(this.typeMap.isEmpty(),
        "Expected empty typemap, found: %s", this.typeMap);
    ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
    ImmutableMap<String, JSType> resultMap;
    ImmutableList<String> typeParams = getTypeParameters();
    for (String newKey : typeParams) {
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
    if (resultMap.size() < typeParams.size()) {
      return this;
    }
    return new NominalType(resultMap, this.rawType);
  }

  NominalType instantiateGenerics(List<JSType> types) {
    ImmutableList<String> typeParams = this.rawType.getTypeParameters();
    checkState(types.size() == typeParams.size());
    Map<String, JSType> typeMap = new LinkedHashMap<>();
    for (int i = 0; i < typeParams.size(); i++) {
      typeMap.put(typeParams.get(i), types.get(i));
    }
    return instantiateGenerics(typeMap);
  }

  NominalType instantiateGenericsWithUnknown() {
    NominalType thisWithoutTypemap = this.rawType.getAsNominalType();
    return thisWithoutTypemap.instantiateGenerics(getCommonTypes().MAP_TO_UNKNOWN);
  }

  NominalType instantiateGenericsWithIdentity() {
    checkState(isUninstantiatedGenericType());
    Map<String, JSType> m = new LinkedHashMap<>();
    for (String typeParam : this.getTypeParameters()) {
      m.put(typeParam, JSType.fromTypeVar(this.getCommonTypes(), typeParam));
    }
    return instantiateGenerics(m);
  }

  public String getName() {
    return this.rawType.name;
  }

  // Only used for keys in GlobalTypeInfo
  public RawNominalType getId() {
    return this.rawType;
  }

  Set<RawNominalType> getSubtypes() {
    return this.rawType.getSubtypes();
  }

  public boolean isClass() {
    return this.rawType.isClass();
  }

  public boolean isAbstractClass() {
    return this.rawType.isAbstractClass();
  }

  public boolean isInterface() {
    return this.rawType.isInterface();
  }

  boolean isStructuralInterface() {
    return this.rawType.isStructuralInterface();
  }

  public boolean isFrozen() {
    return this.rawType.isFrozen();
  }

  boolean hasAncestorClass(RawNominalType ancestor) {
    return this.rawType.hasAncestorClass(ancestor);
  }

  boolean hasAncestorInterface(RawNominalType ancestor) {
    return this.rawType.hasAncestorInterface(ancestor);
  }

  public ImmutableSet<String> getPropertyNames() {
    return this.rawType.getPropertyNames();
  }

  public Set<String> getAllNonInheritedProps() {
    return this.rawType.getAllNonInheritedProps();
  }

  public Set<String> getAllNonInheritedInstanceProps() {
    return this.rawType.getAllNonInheritedInstanceProps();
  }

  /**
   * Use with caution during GlobalTypeInfo; if some types are not known/resolved,
   * the instantiation may be wrong.
   */
  public NominalType getInstantiatedSuperclass() {
    if (this.rawType.getSuperClass() == null) {
      return null;
    }
    return this.rawType.getSuperClass().substituteGenerics(typeMap);
  }

  // We require a frozen type for the interfaces here because the inheritance
  // chain of each type may not be correct until after the type is frozen.
  public ImmutableSet<NominalType> getInstantiatedInterfaces() {
    checkState(this.rawType.isFrozen());
    ImmutableSet.Builder<NominalType> result = ImmutableSet.builder();
    for (NominalType interf : this.rawType.getInterfaces()) {
      result.add(interf.substituteGenerics(typeMap));
    }
    return result.build();
  }

  // The main difference from getInstantiatedInterfaces is that this method
  // can be used on non-frozen types.
  private ImmutableSet<NominalType> getInstantiatedIObjectInterfaces() {
    ImmutableSet.Builder<NominalType> result = ImmutableSet.builder();
    for (NominalType interf : this.rawType.getInterfaces()) {
      if (interf.inheritsFromIObjectReflexive()) {
        result.add(interf.substituteGenerics(typeMap));
      }
    }
    return result.build();
  }

  NominalType getTopDefiningInterface(String pname) {
    Preconditions.checkState(isInterface(), "Expected interface, found: %s", this);
    NominalType result = null;
    if (getNonInheritedProp(pname) != null) {
      result = this;
    }
    for (NominalType nt : this.getInstantiatedInterfaces()) {
      if (nt.getNonInheritedProp(pname) != null) {
        result = nt.getTopDefiningInterface(pname);
      }
    }
    return result;
  }

  Property getProp(String pname, PropAccess propAccess) {
    if (this.rawType.isBuiltinWithName("Array")
        && NUMERIC_PATTERN.matcher(pname).matches()) {
      if (typeMap.isEmpty()) {
        return Property.make(getCommonTypes().UNKNOWN, null);
      }
      checkState(typeMap.size() == 1);
      JSType elmType = Iterables.getOnlyElement(typeMap.values());
      return Property.make(elmType, null);
    }
    Property p = this.rawType.getProp(pname, propAccess);
    // TODO(aravindpg): Also look for getters and setters specially (in RawNominalType::protoProps),
    // but avoid putting them in the hot path of getProp.
    return p == null ? null : p.substituteGenerics(typeMap);
  }

  public JSDocInfo getPropertyJsdoc(String pname) {
    Property p = getProp(pname, PropAccess.EXCLUDE_STRAY_PROPS);
    if (p == null) {
      return null;
    }
    Node defSite = p.getDefSite();
    return defSite == null ? null : NodeUtil.getBestJSDocInfo(defSite);
  }

  public JSType getPropDeclaredType(String pname) {
    JSType type = this.rawType.getInstancePropDeclaredType(pname);
    if (type == null) {
      return null;
    }
    return type.substituteGenerics(typeMap);
  }

  Property getNonInheritedProp(String pname) {
    Property p = this.rawType.getNonInheritedProp(pname, PropAccess.INCLUDE_STRAY_PROPS);
    return p == null ? null : p.substituteGenerics(typeMap);
  }

  public boolean hasConstantProp(String pname) {
    Property p = this.rawType.getProp(pname, PropAccess.EXCLUDE_STRAY_PROPS);
    return p != null && p.isConstant();
  }

  boolean mayHaveProp(String pname) {
    return this.rawType.mayHaveProp(pname);
  }

  public boolean hasAbstractMethod(String pname) {
    return this.rawType.hasAbstractMethod(pname);
  }

  // Checks for subtyping without taking generics into account
  boolean isRawSubtypeOf(NominalType other) {
    return this.rawType.isSubtypeOf(other.rawType);
  }

  boolean isNominalSubtypeOf(NominalType other) {
    RawNominalType thisRaw = this.rawType;
    if (thisRaw == other.rawType) {
      return areTypeMapsCompatible(other);
    }
    if (other.isBuiltinObject()) {
      return true;
    }
    if (other.isInterface()) {
      // If thisRaw is not frozen, thisRaw.interfaces may be null.
      for (NominalType i : thisRaw.getInterfaces()) {
        if (i.substituteGenerics(this.typeMap).isNominalSubtypeOf(other)) {
          return true;
        }
      }
    }
    // Note that other can still be an interface here (implemented by a superclass)
    return isClass() && thisRaw.getSuperClass() != null
      && thisRaw.getSuperClass().substituteGenerics(this.typeMap).isNominalSubtypeOf(other);
  }

  boolean isIObjectSubtypeOf(NominalType other) {
    checkState(this.inheritsFromIObjectReflexive() && other.inheritsFromIObjectReflexive());
    // Contravariance for the index type and covariance for the indexed type.
    return other.getIndexType().isSubtypeOf(this.getIndexType())
        && this.getIndexedType().isSubtypeOf(other.getIndexedType());
  }

  private boolean areTypeMapsCompatible(NominalType other) {
    checkState(this.rawType.equals(other.rawType));
    if (this.typeMap.isEmpty() || other.typeMap.isEmpty()) {
      return true;
    }
    for (String typeVar : this.rawType.getTypeParameters()) {
      Preconditions.checkState(this.typeMap.containsKey(typeVar),
          "Type variable %s not in the domain: %s",
          typeVar, this.typeMap.keySet());
      Preconditions.checkState(other.typeMap.containsKey(typeVar),
          "Other (%s) doesn't contain mapping (%s->%s) from this (%s)",
          other, typeVar, this.typeMap.get(typeVar), this);
      JSType thisType = this.typeMap.get(typeVar);
      JSType otherType = other.typeMap.get(typeVar);
      JSTypes commonTypes = getCommonTypes();
      if (commonTypes.bivariantArrayGenerics && this.rawType.isBuiltinWithName("Array")) {
        thisType = thisType.removeType(commonTypes.NULL_OR_UNDEFINED);
        otherType = otherType.removeType(commonTypes.NULL_OR_UNDEFINED);
        if (!thisType.isSubtypeOf(otherType) && !otherType.isSubtypeOf(thisType)) {
          return false;
        }
      } else if (!thisType.isSubtypeOf(otherType)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Unify the two types symmetrically, given that we have already instantiated
   * the type variables of interest in {@code nt1} and {@code nt2}, treating
   * JSType.UNKNOWN as a "hole" to be filled.
   * @return The unified type, or null if unification fails
   */
  static NominalType unifyUnknowns(NominalType nt1, NominalType nt2) {
    if (!nt1.rawType.equals(nt2.rawType)) {
      return null;
    }
    Map<String, JSType> m1 = nt1.typeMap;
    Map<String, JSType> m2 = nt2.typeMap;
    if (m1.isEmpty() && m2.isEmpty()) {
      return nt1;
    } else if (m1.isEmpty() || m2.isEmpty()) {
      return null;
    }
    ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
    for (Map.Entry<String, JSType> entry : m1.entrySet()) {
      String typeVar = entry.getKey();
      JSType t1 = entry.getValue();
      JSType t2 = m2.get(typeVar);
      if (t1.isUnknown()) {
        builder.put(typeVar, t2);
      } else if (t2.isUnknown()) {
        builder.put(typeVar, t1);
      } else {
        JSType newType = JSType.unifyUnknowns(t1, t2);
        if (newType == null) {
          return null;
        }
        builder.put(typeVar, newType);
      }
    }
    return new NominalType(builder.build(), nt1.rawType);
  }

  private static NominalType joinTypeMaps(NominalType nt1, NominalType nt2) {
    checkState(nt1.rawType.equals(nt2.rawType));
    ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
    if (nt1.isIObject()) {
      // Special case IObject, whose first type parameter is contravariant.
      String indexTypevar = nt1.rawType.getTypeParameters().get(0);
      builder.put(indexTypevar, JSType.meet(nt1.getIndexType(), nt2.getIndexType()));
      String indexedTypevar = nt1.rawType.getTypeParameters().get(1);
      builder.put(indexedTypevar, JSType.join(nt1.getIndexedType(), nt2.getIndexedType()));
      return new NominalType(builder.build(), nt1.rawType);
    }
    if (nt1.typeMap.isEmpty() || nt2.typeMap.isEmpty()) {
      return nt1.instantiateGenericsWithUnknown();
    }
    for (String typevar : nt1.typeMap.keySet()) {
      builder.put(typevar, JSType.join(nt1.typeMap.get(typevar), nt2.typeMap.get(typevar)));
    }
    return new NominalType(builder.build(), nt1.rawType);
  }

  /**
   * A special-case of join. If either argument is null, it returns null.
   */
  static NominalType join(NominalType c1, NominalType c2) {
    if (c1 == null || c2 == null) {
      return null;
    }
    if (c1.isNominalSubtypeOf(c2)) {
      return c2;
    }
    if (c2.isNominalSubtypeOf(c1)) {
      return c1;
    }
    if (c1.rawType.equals(c2.rawType)) {
      return c1.isGeneric() ? joinTypeMaps(c1, c2) : c1;
    }
    // If c1.isRawSubtypeOf(c2) but not c1.isNominalSubtypeOf(c2), we would want to change
    // joinTypeMaps to handle type maps with different domains. Basically, we want to go up
    // c1's inheritance chain and get instantiated ancestors until we reach the ancestor with the
    // same raw type as c2, and then join.
    // Putting the preconditions check in order to get notified if we ever need to handle this.
    checkState(!c1.isRawSubtypeOf(c2) && !c2.isRawSubtypeOf(c1));
    return null;
  }

  // A special-case of meet
  static NominalType pickSubclass(NominalType c1, NominalType c2) {
    if (c1 == null) {
      return c2;
    }
    if (c2 == null) {
      return c1;
    }
    if (c1.isNominalSubtypeOf(c2)) {
      return c1;
    }
    return c2.isNominalSubtypeOf(c1) ? c2 : null;
  }

  boolean unifyWithSubtype(NominalType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap, SubtypeCache subSuperMap) {
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
    // body of a method of a polymorphic class, then this.typeMap and/or other.typeMap
    // can be empty. For now, don't do anything fancy in that case.
    if (this.typeMap.isEmpty() || other.typeMap.isEmpty()) {
      return true;
    }
    boolean hasUnified = true;
    for (String typeParam : this.rawType.getTypeParameters()) {
      JSType fromOtherMap = other.typeMap.get(typeParam);
      Preconditions.checkNotNull(fromOtherMap,
          "Type variable %s not found in map %s",
          typeParam, other.typeMap);
      hasUnified = hasUnified && this.typeMap.get(typeParam)
          .unifyWithSubtype(fromOtherMap, typeParameters, typeMultimap, subSuperMap);
    }
    return hasUnified;
  }

  // Returns a type with the same raw type as other, but possibly different type maps.
  private NominalType findMatchingAncestorWith(NominalType other) {
    RawNominalType thisRaw = this.rawType;
    if (thisRaw == other.rawType) {
      return this;
    }
    if (other.isInterface()) {
      for (NominalType i : thisRaw.getInterfaces()) {
        NominalType nt = i.substituteGenerics(this.typeMap).findMatchingAncestorWith(other);
        if (nt != null) {
          return nt;
        }
      }
    }
    // Note that other can still be an interface here (implemented by a superclass)
    if (isClass() && thisRaw.getSuperClass() != null) {
      return thisRaw.getSuperClass().substituteGenerics(this.typeMap)
          .findMatchingAncestorWith(other);
    }
    return null;
  }

  boolean isPropDefinedOnSubtype(QualifiedName pname) {
    checkArgument(pname.isIdentifier());
    return this.rawType.isPropDefinedOnSubtype(pname.getLeftmostName());
  }

  Set<JSType> getSubtypesWithProperty(QualifiedName pname) {
    checkArgument(pname.isIdentifier());
    return this.rawType.getSubtypesWithProperty(pname.getLeftmostName());
  }

  static boolean equalRawTypes(NominalType n1, NominalType n2) {
    return n1.rawType.equals(n2.rawType);
  }

  @Override
  public String toString() {
    return appendTo(new StringBuilder(), ToStringContext.TO_STRING).toString();
  }

  StringBuilder appendTo(StringBuilder builder, ToStringContext ctx) {
    if (ctx.forAnnotation()) {
      builder.append("!");
    }
    this.rawType.appendTo(builder, ctx);
    if (this.typeMap.isEmpty()) {
      return builder;
    }
    ImmutableList<String> typeParams = this.rawType.getTypeParameters();
    checkState(this.typeMap.keySet().containsAll(typeParams));
    boolean firstIteration = true;
    builder.append('<');
    for (String typeParam : typeParams) {
      if (firstIteration) {
        firstIteration = false;
      } else {
        builder.append(',');
      }
      JSType concrete = this.typeMap.get(typeParam);
      checkNotNull(concrete).appendTo(builder, ctx);
    }
    builder.append('>');
    return builder;
  }

  @Override
  public int hashCode() {
    return Objects.hash(typeMap, this.rawType);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof NominalType)) {
      return false;
    }
    if (this == other) {
      return true;
    }
    NominalType o = (NominalType) other;
    return this.rawType.equals(o.rawType) && Objects.equals(typeMap, o.typeMap);
  }
}
