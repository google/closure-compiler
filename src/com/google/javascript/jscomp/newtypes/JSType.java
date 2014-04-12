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
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
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
public class JSType {
  private static final int BOTTOM_MASK = 0x0;
  private static final int STRING_MASK = 0x1;
  private static final int NUMBER_MASK = 0x2;
  private static final int UNDEFINED_MASK = 0x4;
  private static final int TRUE_MASK = 0x8;
  private static final int FALSE_MASK = 0x10;
  private static final int NULL_MASK = 0x20;
  private static final int NON_SCALAR_MASK = 0x40;
  private static final int TYPEVAR_MASK = 0x80;
  private static final int END_MASK = TYPEVAR_MASK * 2;
  // When either of the next two bits is set, the rest of the type isn't
  // guaranteed to be in a consistent state.
  private static final int TRUTHY_MASK = 0x100;
  private static final int FALSY_MASK = 0x200;
  // Room to grow.
  private static final int UNKNOWN_MASK = 0x7fffffff; // @type {?}
  private static final int TOP_MASK = 0xffffffff; // @type {*}

  private static final int BOOLEAN_MASK = TRUE_MASK | FALSE_MASK;
  private static final int TOP_SCALAR_MASK =
      NUMBER_MASK | STRING_MASK | BOOLEAN_MASK | NULL_MASK | UNDEFINED_MASK;

  private final int mask;
  // objs is null for scalar types
  private final ImmutableSet<ObjectType> objs;
  // typeVar is null for non-generic types
  private final String typeVar;
  private final String location;

  private JSType(int mask, String location, ImmutableSet<ObjectType> objs,
      String typeVar) {
    this.typeVar = typeVar;
    this.location = location;
    if (objs == null) {
      this.mask = mask;
      this.objs = null;
    } else if (objs.isEmpty()) {
      this.mask = mask & ~NON_SCALAR_MASK;
      this.objs = null;
    } else {
      this.mask = mask | NON_SCALAR_MASK;
      this.objs = objs;
    }
    Preconditions.checkState(this.isValidType(),
        "Cannot create type with bits <<<%s>>>, " +
        "objs <<<%s>>>, and typeVar <<<%s>>>",
        Integer.toHexString(mask), objs, typeVar);
  }

  private JSType(int mask) {
    this(mask, null, null, null);
  }

  // Factory method for wrapping a function in a JSType
  public static JSType fromFunctionType(FunctionType fn) {
    return new JSType(NON_SCALAR_MASK, null,
        ImmutableSet.of(ObjectType.fromFunction(fn)), null);
  }

  public static JSType fromObjectType(ObjectType obj) {
    return new JSType(NON_SCALAR_MASK, null, ImmutableSet.of(obj), null);
  }

  public static JSType fromTypeVar(String template) {
    return new JSType(TYPEVAR_MASK, null, null, template);
  }

  boolean isValidType() {
    if (isUnknown() || isTop()) {
      return true;
    }
    if ((mask & NON_SCALAR_MASK) != 0 && (objs == null || objs.isEmpty())) {
      return false;
    }
    if ((mask & NON_SCALAR_MASK) == 0 && objs != null) {
      return false;
    }
    return ((mask & TYPEVAR_MASK) != 0) == (typeVar != null);
  }

  public static final JSType BOOLEAN = new JSType(TRUE_MASK | FALSE_MASK);
  public static final JSType BOTTOM = new JSType(BOTTOM_MASK);
  public static final JSType FALSE_TYPE = new JSType(FALSE_MASK);
  public static final JSType FALSY = new JSType(FALSY_MASK);
  public static final JSType NULL = new JSType(NULL_MASK);
  public static final JSType NUMBER = new JSType(NUMBER_MASK);
  public static final JSType STRING = new JSType(STRING_MASK);
  public static final JSType TOP = new JSType(TOP_MASK);
  public static final JSType TOP_SCALAR = new JSType(TOP_SCALAR_MASK);
  public static final JSType TRUE_TYPE = new JSType(TRUE_MASK);
  public static final JSType TRUTHY = new JSType(TRUTHY_MASK);
  public static final JSType UNDEFINED = new JSType(UNDEFINED_MASK);
  public static final JSType UNKNOWN = new JSType(UNKNOWN_MASK);

  public static final JSType TOP_OBJECT = fromObjectType(ObjectType.TOP_OBJECT);
  public static final JSType TOP_STRUCT = fromObjectType(ObjectType.TOP_STRUCT);
  public static final JSType TOP_DICT = fromObjectType(ObjectType.TOP_DICT);
  private static JSType TOP_FUNCTION = null;

  // Some commonly used types
  public static final JSType NULL_OR_UNDEF =
      new JSType(NULL_MASK | UNDEFINED_MASK);
  public static final JSType NUM_OR_STR = new JSType(NUMBER_MASK | STRING_MASK);

  private static final JSType TOP_MINUS_NULL = new JSType(
      TRUE_MASK | FALSE_MASK | NUMBER_MASK | STRING_MASK | UNDEFINED_MASK |
      NON_SCALAR_MASK,
      null, ImmutableSet.of(ObjectType.TOP_OBJECT), null);
  private static final JSType TOP_MINUS_UNDEF = new JSType(
      TRUE_MASK | FALSE_MASK | NUMBER_MASK | STRING_MASK | NULL_MASK |
      NON_SCALAR_MASK,
      null, ImmutableSet.of(ObjectType.TOP_OBJECT), null);

  public static JSType topFunction() {
    if (TOP_FUNCTION == null) {
      TOP_FUNCTION = fromFunctionType(FunctionType.TOP_FUNCTION);
    }
    return TOP_FUNCTION;
  }

  // Corresponds to Function, which is a subtype and supertype of all functions.
  static JSType qmarkFunction() {
    return fromFunctionType(FunctionType.QMARK_FUNCTION);
  }

  public boolean isTop() {
    return TOP_MASK == mask;
  }

  public boolean isBottom() {
    return BOTTOM_MASK == mask;
  }

  public boolean isUnknown() {
    return UNKNOWN_MASK == mask;
  }

  public boolean isTruthy() {
    return TRUTHY_MASK == mask || TRUE_MASK == mask;
  }

  public boolean isFalsy() {
    return FALSY_MASK == mask || FALSE_MASK == mask;
  }

  public boolean isBoolean() {
    return (mask & ~BOOLEAN_MASK) == 0 && (mask & BOOLEAN_MASK) != 0;
  }

  public boolean isNullOrUndef() {
    int nullUndefMask = NULL_MASK | UNDEFINED_MASK;
    return mask != 0 && (mask | nullUndefMask) == nullUndefMask;
  }

  public boolean isScalar() {
    return mask == NUMBER_MASK ||
        mask == STRING_MASK ||
        mask == NULL_MASK ||
        mask == UNDEFINED_MASK ||
        this.isBoolean();
  }

  // True iff there exists a value that can have this type
  public boolean isInhabitable() {
    if (isBottom()) {
      return false;
    } else if (objs == null) {
      return true;
    }
    for (ObjectType obj : objs) {
      if (!obj.isInhabitable()) {
        return false;
      }
    }
    return true;
  }

  public boolean hasNonScalar() {
    return objs != null;
  }

  public boolean isNullable() {
    return (mask & NULL_MASK) != 0;
  }

  boolean isTypeVariable() {
    return (mask & ~TYPEVAR_MASK) == 0;
  }

  public boolean isStruct() {
    if (objs == null) {
      return false;
    }
    for (ObjectType objType : objs) {
      if (objType.isStruct()) {
        return true;
      }
    }
    return false;
  }

  public boolean isLooseStruct() {
    if (objs == null) {
      return false;
    }
    boolean foundLooseStruct = false;
    boolean foundNonLooseStruct = false;
    for (ObjectType objType : objs) {
      if (objType.isLooseStruct()) {
        foundLooseStruct = true;
      } else if (objType.isStruct()) {
        foundNonLooseStruct = true;
      }
    }
    return foundLooseStruct && !foundNonLooseStruct;
  }

  public boolean isDict() {
    if (objs == null) {
      return false;
    }
    for (ObjectType objType : objs) {
      if (objType.isDict()) {
        return true;
      }
    }
    return false;
  }

  public static boolean areCompatibleScalarTypes(JSType lhs, JSType rhs) {
    Preconditions.checkArgument(
        lhs.isSubtypeOf(TOP_SCALAR) || rhs.isSubtypeOf(TOP_SCALAR));
    if (lhs.isBottom() || rhs.isBottom() ||
        lhs.isUnknown() || rhs.isUnknown() ||
        (lhs.isBoolean() && rhs.isBoolean()) ||
        lhs.equals(rhs)) {
      return true;
    }
    return false;
  }

  // When joining w/ TOP or UNKNOWN, avoid setting more fields on them, eg, obj.
  public static JSType join(JSType lhs, JSType rhs) {
    if (lhs.isTop() || rhs.isTop()) {
      return TOP;
    } else if (lhs.isUnknown() || rhs.isUnknown()) {
      return UNKNOWN;
    }
    if (lhs.typeVar != null && rhs.typeVar != null &&
        !lhs.typeVar.equals(rhs.typeVar)) {
      // For now return ? when joining two type vars. This is probably uncommon.
      return UNKNOWN;
    }
    return new JSType(
        lhs.mask | rhs.mask,
        Objects.equal(lhs.location, rhs.location) ? lhs.location : null,
        ObjectType.joinSets(lhs.objs, rhs.objs),
        lhs.typeVar != null ? lhs.typeVar : rhs.typeVar);
  }

  public JSType substituteGenerics(Map<String, JSType> concreteTypes) {
    if (isTop() || isUnknown()) {
      return this;
    }
    ImmutableSet<ObjectType> newObjs = null;
    if (objs != null) {
      ImmutableSet.Builder<ObjectType> builder = ImmutableSet.builder();
      for (ObjectType obj : objs) {
        builder.add(obj.substituteGenerics(concreteTypes));
      }
      newObjs = builder.build();
    }
    JSType current = new JSType(mask & ~TYPEVAR_MASK, location, newObjs, null);
    if ((mask & TYPEVAR_MASK) != 0) {
      current = JSType.join(current, concreteTypes.containsKey(typeVar) ?
          concreteTypes.get(typeVar) : fromTypeVar(typeVar));
    }
    return current;
  }

  private static void updateTypemap(
      Multimap<String, JSType> typeMultimap,
      String typeParam, JSType type) {
    Set<JSType> typesToRemove = Sets.newHashSet();
    for (JSType other : typeMultimap.get(typeParam)) {
      JSType unified = unifyUnknowns(type, other);
      if (unified != null) {
        // Can't remove elms while iterating over the collection, so do it later
        typesToRemove.add(other);
        type = unified;
      }
    }
    for (JSType typeToRemove : typesToRemove) {
      typeMultimap.remove(typeParam, typeToRemove);
    }
    typeMultimap.put(typeParam, type);
  }

  private static int promoteBoolean(int mask) {
    if ((mask & (TRUE_MASK | FALSE_MASK)) != 0) {
      return mask | TRUE_MASK | FALSE_MASK;
    }
    return mask;
  }

  /**
   * Unify the two types symmetrically, given that we have already instantiated
   * the type variables of interest in {@code t1} and {@code t2}, treating
   * JSType.UNKNOWN as a "hole" to be filled.
   * @return The unified type, or null if unification fails */
  static JSType unifyUnknowns(JSType t1, JSType t2) {
    if (t1.isUnknown()) {
      return t2;
    } else if (t2.isUnknown()) {
      return t1;
    } else if (t1.isTop() && t2.isTop()) {
      return TOP;
    } else if (t1.isTop() || t2.isTop()) {
      return null;
    }

    int t1Mask = promoteBoolean(t1.mask);
    int t2Mask = promoteBoolean(t2.mask);
    if (t1Mask != t2Mask || !Objects.equal(t1.typeVar, t2.typeVar)) {
      return null;
    }
    // All scalar types are equal
    if ((t1Mask & NON_SCALAR_MASK) == 0) {
      return t1;
    }
    if (t1.objs.size() != t2.objs.size()) {
      return null;
    }

    Set<ObjectType> ununified = Sets.newHashSet(t2.objs);
    Set<ObjectType> unifiedObjs = Sets.newHashSet();
    for (ObjectType objType1 : t1.objs) {
      ObjectType unified = objType1;
      boolean hasUnified = false;
      for (ObjectType objType2 : t2.objs) {
        ObjectType tmp = ObjectType.unifyUnknowns(unified, objType2);
        if (tmp != null) {
          hasUnified = true;
          ununified.remove(objType2);
          unified = tmp;
        }
      }
      if (!hasUnified) {
        return null;
      }
      unifiedObjs.add(unified);
    }
    if (!ununified.isEmpty()) {
      return null;
    }
    return new JSType(
        t1Mask, null, ImmutableSet.copyOf(unifiedObjs), t1.typeVar);
  }

  /**
   * Unify {@code this}, which may contain free type variables,
   * with {@code other}, a concrete type, modifying the supplied
   * {@code typeMultimap} to add any new template variable type bindings.
   * @return Whether unification succeeded
   */
  public boolean unifyWith(JSType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap) {
    if (this.isUnknown()) {
      return true;
    } else if (this.isTop()) {
      return other.isTop();
    } else if (this.mask == TYPEVAR_MASK && typeParameters.contains(typeVar)) {
      updateTypemap(typeMultimap, typeVar, new JSType(
          promoteBoolean(other.mask), null, other.objs, other.typeVar));
      return true;
    } else if (other.isTop()) {
      return false;
    } else if (other.isUnknown()) {
      return true;
    }

    Set<ObjectType> ununified = ImmutableSet.of();
    if (other.objs != null) {
      ununified = Sets.newHashSet(other.objs);
    }
    // Each obj in this must unify w/ exactly one obj in other.
    // However, we don't check that two different objects of this don't unify
    // with the same other type.
    if (this.objs != null) {
      if (other.objs == null) {
        return false;
      }
      for (ObjectType targetObj : this.objs) {
        boolean hasUnified = false;
        for (ObjectType sourceObj : other.objs) {
          if (targetObj.unifyWith(sourceObj, typeParameters, typeMultimap)) {
            ununified.remove(sourceObj);
            hasUnified = true;
          }
        }
        if (!hasUnified) {
          return false;
        }
      }
    }

    String thisTypevar = this.typeVar;
    String otherTypevar = other.typeVar;
    if (thisTypevar == null) {
      return otherTypevar == null && mask == other.mask;
    } else if (!typeParameters.contains(thisTypevar)) {
      return thisTypevar.equals(otherTypevar) && mask == other.mask;
    } else {
      // this is T (|...)
      int templateMask = 0;
      if (!ununified.isEmpty()) {
        templateMask |= NON_SCALAR_MASK;
      }
      if ((other.mask & TYPEVAR_MASK) != 0) {
        templateMask |= TYPEVAR_MASK;
      }
      int thisScalarBits = this.mask & ~NON_SCALAR_MASK & ~TYPEVAR_MASK;
      int otherScalarBits = other.mask & ~NON_SCALAR_MASK & ~TYPEVAR_MASK;
      templateMask |= otherScalarBits & ~thisScalarBits;

      if (templateMask == BOTTOM_MASK) {
        // nothing left in other to assign to thisTypevar
        return false;
      }
      JSType templateType = new JSType(
          promoteBoolean(templateMask), null,
          ImmutableSet.copyOf(ununified), otherTypevar);
      updateTypemap(typeMultimap, typeVar, templateType);
      // We don't do fancy unification, eg,
      // T|number doesn't unify with TOP
      // Foo<number>|Foo<string> doesn't unify with Foo<T>|Foo<string>
      return true;
    }
  }

  // Specialize this type by meeting with other, but keeping location
  public JSType specialize(JSType other) {
    if (other.isTop() || other.isUnknown()) {
      return this;
    } else if (other.isTruthy()) {
      return makeTruthy();
    } else if (other.isFalsy()) {
      return makeFalsy();
    } else if (this.isTop() || this.isUnknown()) {
      return other.withLocation(this.location);
    }
    int newMask = this.mask & other.mask;
    String newTypevar;
    if (Objects.equal(this.typeVar, other.typeVar)) {
      newTypevar = this.typeVar;
    } else {
      newTypevar = null;
      newMask = newMask & ~TYPEVAR_MASK;
    }
    return new JSType(newMask, this.location,
        ObjectType.specializeSet(this.objs, other.objs), newTypevar);
  }

  private JSType makeTruthy() {
    if (this.isTop() || this.isUnknown()) {
      return this;
    }
    return new JSType(mask & ~NULL_MASK & ~FALSE_MASK & ~UNDEFINED_MASK,
        location, objs, typeVar);
  }

  private JSType makeFalsy() {
    if (this.isTop() || this.isUnknown()) {
      return this;
    }
    return new JSType(
        mask & ~TRUE_MASK & ~NON_SCALAR_MASK, location, null, typeVar);
  }

  // Meet two types, location agnostic
  public static JSType meet(JSType lhs, JSType rhs) {
    if (lhs.isTop()) {
      return rhs;
    } else if (rhs.isTop()) {
      return lhs;
    } else if (lhs.isUnknown()) {
      return rhs;
    } else if (rhs.isUnknown()) {
      return lhs;
    }
    int newMask = lhs.mask & rhs.mask;
    String newTypevar;
    if (Objects.equal(lhs.typeVar, rhs.typeVar)) {
      newTypevar = lhs.typeVar;
    } else {
      newTypevar = null;
      newMask = newMask & ~TYPEVAR_MASK;
    }
    return new JSType(
        newMask, null, ObjectType.meetSets(lhs.objs, rhs.objs), newTypevar);
  }

  public static JSType plus(JSType lhs, JSType rhs) {
    int newtype = (lhs.mask | rhs.mask) & STRING_MASK;
    if ((lhs.mask & ~STRING_MASK) != 0 && (rhs.mask & ~STRING_MASK) != 0) {
      newtype |= NUMBER_MASK;
    }
    return new JSType(newtype);
  }

  // Only handles scalars
  public JSType negate() {
    if (isTop() || isUnknown() || objs != null || typeVar != null) {
      return this;
    }
    if (isTruthy()) {
      return FALSY;
    } else if (isFalsy()) {
      return TRUTHY;
    }
    return new JSType(TOP_SCALAR_MASK & ~mask);
  }

  public JSType toBoolean() {
    if (isTruthy()) {
      return TRUE_TYPE;
    } else if (isFalsy()) {
      return FALSE_TYPE;
    }
    return BOOLEAN;
  }

  public boolean isNonLooseSubtypeOf(JSType other) {
    return isSubtypeOfHelper(false, other);
  }

  public boolean isSubtypeOf(JSType other) {
    return isSubtypeOfHelper(true, other);
  }

  private boolean isSubtypeOfHelper(
      boolean keepLoosenessOfThis, JSType other) {
    if (isUnknown() || other.isUnknown() || other.isTop()) {
      return true;
    } else if ((mask | other.mask) != other.mask) {
      return false;
    } else if (!Objects.equal(this.typeVar, other.typeVar)) {
      return false;
    } else if (this.objs == null) {
      return true;
    }
    // Because of optional properties,
    //   x \le y \iff x \join y = y does not hold.
    return ObjectType.isUnionSubtype(keepLoosenessOfThis, this.objs, other.objs);
  }

  public JSType removeType(JSType other) {
    if ((isTop() || isUnknown()) && other.equals(NULL)) {
      return TOP_MINUS_NULL;
    }
    if ((isTop() || isUnknown()) && other.equals(UNDEFINED)) {
      return TOP_MINUS_UNDEF;
    }
    if (other.equals(NULL) || other.equals(UNDEFINED)) {
      return new JSType(mask & ~other.mask, location, objs, typeVar);
    }
    if (objs == null) {
      return this;
    }
    Preconditions.checkState(
        (other.mask & ~NON_SCALAR_MASK) == 0 && other.objs.size() == 1);
    NominalType otherKlass =
        Iterables.getOnlyElement(other.objs).getNominalType();
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : objs) {
      if (!Objects.equal(obj.getNominalType(), otherKlass)) {
        newObjs.add(obj);
      }
    }
    return new JSType(mask, location, newObjs.build(), typeVar);
  }

  public JSType withLocation(String location) {
    return new JSType(mask, location, objs, typeVar);
  }

  public String getLocation() {
    return location;
  }

  public FunctionType getFunTypeIfSingletonObj() {
    if (mask != NON_SCALAR_MASK || objs.size() > 1) {
      return null;
    }
    return Iterables.getOnlyElement(objs).getFunType();
  }

  public FunctionType getFunType() {
    if (objs == null) {
      return null;
    }
    if (objs.size() == 1) { // The common case is fast
      return Iterables.getOnlyElement(objs).getFunType();
    }
    FunctionType result = FunctionType.TOP_FUNCTION;
    for (ObjectType obj : objs) {
      result = FunctionType.meet(result, obj.getFunType());
    }
    return result;
  }

  NominalType getNominalTypeIfUnique() {
    if (objs == null || objs.size() > 1) {
      return null;
    }
    return Iterables.getOnlyElement(objs).getNominalType();
  }

  public boolean isInterfaceDefinition() {
    if (objs == null || objs.size() > 1) {
      return false;
    }
    FunctionType ft = Iterables.getOnlyElement(objs).getFunType();
    return ft != null && ft.isInterfaceDefinition();
  }

  /** Turns the class-less object of this type (if any) into a loose object */
  public JSType withLoose() {
    Preconditions.checkNotNull(this.objs);
    return new JSType(
        this.mask, this.location,
        ObjectType.withLooseObjects(this.objs), typeVar);
  }

  public JSType getProp(QualifiedName qname) {
    if (isBottom() || isUnknown()) {
      return UNKNOWN;
    }
    Preconditions.checkState(objs != null);
    JSType ptype = BOTTOM;
    for (ObjectType o : objs) {
      if (o.mayHaveProp(qname)) {
        ptype = join(ptype, o.getProp(qname));
      }
    }
    if (ptype.isBottom()) {
      return null;
    }
    return ptype;
  }

  public boolean mayHaveProp(QualifiedName qname) {
    if (objs == null) {
      return false;
    }
    for (ObjectType o : objs) {
      if (o.mayHaveProp(qname)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasProp(QualifiedName qname) {
    if (objs == null) {
      return false;
    }
    for (ObjectType o : objs) {
      if (!o.hasProp(qname)) {
        return false;
      }
    }
    return true;
  }

  public boolean hasInferredProp(QualifiedName pname) {
    Preconditions.checkState(pname.isIdentifier());
    return hasProp(pname) && getDeclaredProp(pname) == null;
  }

  public JSType getDeclaredProp(QualifiedName qname) {
    if (isUnknown()) {
      return UNKNOWN;
    }
    Preconditions.checkState(objs != null);
    JSType ptype = BOTTOM;
    for (ObjectType o : objs) {
      JSType declType = o.getDeclaredProp(qname);
      if (declType != null) {
        ptype = join(ptype, declType);
      }
    }
    return ptype.isBottom() ? null : ptype;
  }

  public JSType withoutProperty(QualifiedName qname) {
    return this.objs == null ?
        this :
        new JSType(this.mask, this.location,
            ObjectType.withoutProperty(this.objs, qname), typeVar);
  }

  public JSType withProperty(QualifiedName qname, JSType type) {
    if (isUnknown()) {
      return this;
    }
    Preconditions.checkState(this.objs != null);
    return new JSType(this.mask, this.location,
        ObjectType.withProperty(this.objs, qname, type), typeVar);
  }

  public JSType withDeclaredProperty(QualifiedName qname, JSType type) {
    Preconditions.checkState(this.objs != null && this.location == null);
    return new JSType(this.mask, null,
        ObjectType.withDeclaredProperty(this.objs, qname, type), typeVar);
  }

  public JSType withPropertyRequired(String pname) {
    return (isUnknown() || this.objs == null) ?
        this :
        new JSType(this.mask, this.location,
            ObjectType.withPropertyRequired(this.objs, pname), typeVar);
  }

  static List<JSType> fixLengthOfTypeList(
      int desiredLength, List<JSType> typeList) {
    int length = typeList.size();
    if (length == desiredLength) {
      return typeList;
    }
    ImmutableList.Builder<JSType> builder = ImmutableList.builder();
    for (int i = 0; i < desiredLength; i++) {
      builder.add(i < length ? typeList.get(i) : UNKNOWN);
    }
    return builder.build();
  }

  @Override
  public String toString() {
    return typeToString() + locationPostfix(location);
  }

  private String typeToString() {
    switch (mask) {
      case BOTTOM_MASK:
      case TOP_MASK:
      case UNKNOWN_MASK:
        return tagToString(mask, null, null);
      default:
        int tags = mask;
        Set<String> types = Sets.newTreeSet();
        for (int mask = 1; mask != END_MASK; mask <<= 1) {
          if ((tags & mask) != 0) {
            types.add(tagToString(mask, objs, typeVar));
            tags = tags & ~mask;  // Remove current mask from union
          }
        }
        if (tags == 0) { // Found all types in the union
          return Joiner.on("|").join(types);
        } else if (tags == TRUTHY_MASK) {
          return "truthy";
        } else if (tags == FALSY_MASK) {
          return "falsy";
        } else {
          return "Unrecognized type: " + tags;
        }
    }
  }

  private static String locationPostfix(String location) {
    if (location == null) {
      return "";
    } else {
      return "@" + location;
    }
  }

  /**
   * Takes a type tag with a single bit set (including the non-scalar bit),
   * and prints the string representation of that single type.
   */
  private static String tagToString(int tag, Set<ObjectType> objs, String T) {
    switch (tag) {
      case TRUE_MASK:
      case FALSE_MASK:
        return "boolean";
      case BOTTOM_MASK:
        return "bottom";
      case STRING_MASK:
        return "string";
      case NON_SCALAR_MASK:
        Set<String> strReps = Sets.newHashSet();
        for (ObjectType obj : objs) {
          strReps.add(obj.toString());
        }
        return Joiner.on("|").join(strReps);
      case NULL_MASK:
        return "null";
      case NUMBER_MASK:
        return "number";
      case TOP_MASK:
        return "top";
      case UNDEFINED_MASK:
        return "undefined";
      case UNKNOWN_MASK:
        return "?";
      case TYPEVAR_MASK:
        return T;
      default: // Must be a union type.
        return null;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    Preconditions.checkArgument(o instanceof JSType);
    JSType t2 = (JSType) o;
    return this.mask == t2.mask && Objects.equal(this.objs, t2.objs);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(mask, objs);
  }
}
