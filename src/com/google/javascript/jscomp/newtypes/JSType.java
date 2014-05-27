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
  private static final int TYPEVAR_MASK = 0x1;
  private static final int NON_SCALAR_MASK = 0x2;
  private static final int ENUM_MASK = 0x4;
  private static final int TRUE_MASK = 0x8;  // These two print out
  private static final int FALSE_MASK = 0x10; // as 'boolean'
  private static final int NULL_MASK = 0x20;
  private static final int NUMBER_MASK = 0x40;
  private static final int STRING_MASK = 0x80;
  private static final int UNDEFINED_MASK = 0x100;
  private static final int END_MASK = UNDEFINED_MASK * 2;
  // When either of the next two bits is set, the rest of the type isn't
  // guaranteed to be in a consistent state.
  private static final int TRUTHY_MASK = 0x200;
  private static final int FALSY_MASK = 0x400;
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
  // enums is null for types that don't have enums
  private final ImmutableSet<EnumType> enums;
  // Non-null for types which represent values flowed to a function from
  // outside. Local vals are null, which gives stricter type checking.
  private final String location;
  // A type tainted with this is non local, but it's not the type of a formal,
  // eg, it can be a prop of a formal whose decl type is a record type.
  static final String GENERIC_LOCATION = "%";

  private JSType(int mask, String location, ImmutableSet<ObjectType> objs,
      String typeVar, ImmutableSet<EnumType> enums) {
    if (enums == null) {
      this.enums = null;
    } else if (enums.isEmpty()) {
      mask &= ~ENUM_MASK;
      this.enums = null;
    } else {
      mask |= ENUM_MASK;
      this.enums = enums;
    }

    if (objs == null) {
      this.objs = null;
    } else if (objs.isEmpty()) {
      mask &= ~NON_SCALAR_MASK;
      this.objs = null;
    } else {
      mask |= NON_SCALAR_MASK;
      this.objs = objs;
    }

    if (typeVar != null) {
      mask |= TYPEVAR_MASK;
    }

    this.typeVar = typeVar;
    this.location = location;
    this.mask = mask;

    Preconditions.checkState(isValidType(),
        "Cannot create type with bits <<<%s>>>, " +
        "objs <<<%s>>>, typeVar <<<%s>>>, enums <<<%s>>>",
        mask, objs, typeVar, enums);
  }

  private JSType(int mask) {
    this(mask, null, null, null, null);
  }

  // Factory method for wrapping a function in a JSType
  public static JSType fromFunctionType(FunctionType fn) {
    return new JSType(NON_SCALAR_MASK, null,
        ImmutableSet.of(ObjectType.fromFunction(fn)), null, null);
  }

  public static JSType fromObjectType(ObjectType obj) {
    return new JSType(NON_SCALAR_MASK, null, ImmutableSet.of(obj), null, null);
  }

  public static JSType fromTypeVar(String template) {
    return new JSType(TYPEVAR_MASK, null, null, template, null);
  }

  static JSType fromEnum(EnumType e) {
    return new JSType(ENUM_MASK, null, null, null, ImmutableSet.of(e));
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
    if ((mask & ENUM_MASK) != 0 && (enums == null || enums.isEmpty())) {
      return false;
    }
    if ((mask & ENUM_MASK) == 0 && enums != null) {
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

  // Explicitly contains most types. Used only by removeType.
  private static final JSType ALMOST_TOP = new JSType(
      TRUE_MASK | FALSE_MASK | NUMBER_MASK | STRING_MASK | NULL_MASK |
      UNDEFINED_MASK | NON_SCALAR_MASK,
      null, ImmutableSet.of(ObjectType.TOP_OBJECT), null, null);

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

  public boolean isFromUndeclaredFormal() {
    return location != null;
  }

  public boolean isScalar() {
    return mask == NUMBER_MASK ||
        mask == STRING_MASK ||
        mask == NULL_MASK ||
        mask == UNDEFINED_MASK ||
        isBoolean();
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
    return objs != null || EnumType.hasNonScalar(enums);
  }

  public boolean isNullable() {
    return (mask & NULL_MASK) != 0;
  }

  boolean isTypeVariable() {
    return (mask & TYPEVAR_MASK) != 0 && (mask & ~TYPEVAR_MASK) == 0;
  }

  public boolean isRecordType() {
    return mask == NON_SCALAR_MASK && objs.size() == 1 &&
        Iterables.getOnlyElement(objs).isRecordType();
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

  public boolean isEnumElement() {
    return mask == ENUM_MASK && enums.size() == 1;
  }

  public boolean isUnion() {
    if (isBottom() || isTop() || isUnknown() ||
        isScalar() || isTypeVariable() || isEnumElement()) {
      return false;
    }
    if (mask == NON_SCALAR_MASK && objs.size() == 1) {
      return false;
    }
    return true;
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

  ImmutableSet<EnumType> getEnums() {
    return enums;
  }

  // Only makes sense for a JSType that represents a single enum
  public JSType getEnumeratedType() {
    return isEnumElement() ?
        Iterables.getOnlyElement(enums).getEnumeratedType() : null;
  }

  static JSType nullAcceptingJoin(JSType t1, JSType t2) {
    if (t1 == null) {
      return t2;
    } else if (t2 == null) {
      return t1;
    }
    return JSType.join(t1, t2);
  }

  // When joining w/ TOP or UNKNOWN, avoid setting more fields on them, eg, obj.
  public static JSType join(JSType lhs, JSType rhs) {
    if (lhs.isTop() || rhs.isTop()) {
      return TOP;
    } else if (lhs.isUnknown() || rhs.isUnknown()) {
      return UNKNOWN;
    } else if (lhs.isBottom()) {
      return rhs;
    } else if (rhs.isBottom()) {
      return lhs;
    }
    if (lhs.typeVar != null && rhs.typeVar != null &&
        !lhs.typeVar.equals(rhs.typeVar)) {
      // For now return ? when joining two type vars. This is probably uncommon.
      return UNKNOWN;
    }

    int newMask = lhs.mask | rhs.mask;
    String newLoc = joinLocs(lhs.location, rhs.location);
    ImmutableSet<ObjectType> newObjs = ObjectType.joinSets(lhs.objs, rhs.objs);
    String newTypevar = lhs.typeVar != null ? lhs.typeVar : rhs.typeVar;
    ImmutableSet<EnumType> newEnums = EnumType.union(lhs.enums, rhs.enums);
    if (newEnums == null) {
      return new JSType(newMask, newLoc, newObjs, newTypevar, null);
    }
    JSType tmpJoin = new JSType(
        newMask & ~ENUM_MASK, newLoc, newObjs, newTypevar, null);
    return new JSType(newMask, newLoc, newObjs, newTypevar,
        EnumType.normalizeForJoin(newEnums, tmpJoin));
  }

  private static String joinLocs(String loc1, String loc2) {
    if (loc1 == null || loc2 == null) {
      return null;
    }
    return loc1.equals(loc2) ? loc1 : GENERIC_LOCATION;
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
    JSType current =
        new JSType(mask & ~TYPEVAR_MASK, location, newObjs, null, enums);
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

    ImmutableSet<EnumType> newEnums = null;
    if (t1.enums == null) {
      if (t2.enums != null) {
        return null;
      }
      newEnums = null;
    } else if (t2.enums == null) {
      return null;
    } else if (!t1.enums.equals(t2.enums)) {
      return null;
    } else {
      newEnums = t1.enums;
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
        t1Mask, null, ImmutableSet.copyOf(unifiedObjs), t1.typeVar, newEnums);
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
      updateTypemap(typeMultimap, typeVar,
          new JSType(promoteBoolean(other.mask), null, other.objs,
              other.typeVar, other.enums));
      return true;
    } else if (other.isTop()) {
      return false;
    } else if (other.isUnknown()) {
      return true;
    }

    Set<EnumType> ununifiedEnums = null;
    if (this.enums == null) {
      ununifiedEnums = other.enums;
    } else if (other.enums == null) {
      return false;
    } else {
      ununifiedEnums = Sets.newHashSet();
      for (EnumType e : this.enums) {
        if (!other.enums.contains(e)) {
          return false;
        }
      }
      for (EnumType e : other.enums) {
        if (!this.enums.contains(e)) {
          ununifiedEnums.add(e);
        }
      }
      if (ununifiedEnums.isEmpty()) {
        ununifiedEnums = null;
      }
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
      // this is (T | ...)
      int templateMask = 0;
      int thisScalarBits = this.mask & ~NON_SCALAR_MASK & ~TYPEVAR_MASK;
      int otherScalarBits = other.mask & ~NON_SCALAR_MASK & ~TYPEVAR_MASK;
      templateMask |= otherScalarBits & ~thisScalarBits;

      if (templateMask == BOTTOM_MASK) {
        // nothing left in other to assign to thisTypevar
        return false;
      }
      JSType templateType = new JSType(
          promoteBoolean(templateMask), null,
          ImmutableSet.copyOf(ununified), otherTypevar,
          ununifiedEnums == null ? null : ImmutableSet.copyOf(ununifiedEnums));
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
      newMask &= ~TYPEVAR_MASK;
    }
    return meetEnums(
        newMask, this.mask | other.mask, this.location,
        ObjectType.specializeSet(this.objs, other.objs),
        newTypevar, this.objs, other.objs, enums, other.enums);
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
    return meetEnums(
        newMask, lhs.mask | rhs.mask, null,
        ObjectType.meetSets(lhs.objs, rhs.objs),
        newTypevar, lhs.objs, rhs.objs, lhs.enums, rhs.enums);
  }

  /**
   * Both {@code meet} and {@code specialize} do the same computation for enums.
   * They don't just compute the set of enums; they may modify mask and objs.
   * So, both methods finish off by calling this one.
   */
  private static JSType meetEnums(int newMask, int unionMask,
      String newLocation, ImmutableSet<ObjectType> newObjs, String newTypevar,
      ImmutableSet<ObjectType> objs1, ImmutableSet<ObjectType> objs2,
      ImmutableSet<EnumType> enums1, ImmutableSet<EnumType> enums2) {
    if (Objects.equal(enums1, enums2)) {
      return new JSType(newMask, newLocation, newObjs, newTypevar, enums1);
    }
    ImmutableSet.Builder<EnumType> enumBuilder = ImmutableSet.builder();
    ImmutableSet<EnumType> allEnums = EnumType.union(enums1, enums2);
    for (EnumType e : allEnums) {
      // An enum in the intersection will always be in the result
      if (enums1 != null && enums1.contains(e) &&
          enums2 != null && enums2.contains(e)) {
        enumBuilder.add(e);
        continue;
      }
      // An enum {?} in the union will always be in the result
      JSType enumeratedType = e.getEnumeratedType();
      if (enumeratedType.isUnknown()) {
        enumBuilder.add(e);
        continue;
      }
      // An enum {TypeA} meets with any supertype of TypeA. When this happens,
      // we put the enum in the result and remove the supertype.
      // The following would be much more complex if we allowed the type of
      // an enum to be a union.
      if (enumeratedType.mask != NON_SCALAR_MASK) {
        if ((enumeratedType.mask & unionMask) != 0) {
          enumBuilder.add(e);
          newMask &= ~enumeratedType.mask;
        }
      } else if (objs1 != null || objs2 != null) {
        Set<ObjectType> objsToRemove = Sets.newHashSet();
        ObjectType enumObj = Iterables.getOnlyElement(enumeratedType.objs);
        if (objs1 != null) {
          for (ObjectType obj1 : objs1) {
            if (enumObj.isSubtypeOf(obj1)) {
              enumBuilder.add(e);
              objsToRemove.add(obj1);
            }
          }
        }
        if (objs2 != null) {
          for (ObjectType obj2 : objs2) {
            if (enumObj.isSubtypeOf(obj2)) {
              enumBuilder.add(e);
              objsToRemove.add(obj2);
            }
          }
        }
        if (!objsToRemove.isEmpty() && newObjs != null) {
          newObjs = Sets.difference(newObjs, objsToRemove).immutableCopy();
        }
      }
    }
    return new JSType(
        newMask, newLocation, newObjs, newTypevar, enumBuilder.build());
  }

  private JSType makeTruthy() {
    if (this.isTop() || this.isUnknown()) {
      return this;
    }
    return new JSType(mask & ~NULL_MASK & ~FALSE_MASK & ~UNDEFINED_MASK,
        location, objs, typeVar, enums);
  }

  private JSType makeFalsy() {
    if (this.isTop() || this.isUnknown()) {
      return this;
    }
    return new JSType(
        mask & ~TRUE_MASK & ~NON_SCALAR_MASK, location, null, typeVar, enums);
  }

  public static JSType plus(JSType lhs, JSType rhs) {
    int newtype = (lhs.mask | rhs.mask) & STRING_MASK;
    if ((lhs.mask & ~STRING_MASK) != 0 && (rhs.mask & ~STRING_MASK) != 0) {
      newtype |= NUMBER_MASK;
    }
    return new JSType(newtype);
  }

  public JSType negate() {
    if (isTop() || isUnknown()) {
      return this;
    }
    if (isTruthy()) {
      return FALSY;
    } else if (isFalsy()) {
      return TRUTHY;
    }
    return UNKNOWN;
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
    }
    if (!EnumType.areSubtypes(this, other)) {
      return false;
    }
    int mask = this.mask & ~ENUM_MASK;
    if ((mask | other.mask) != other.mask) {
      return false;
    }
    if (!Objects.equal(this.typeVar, other.typeVar)) {
      return false;
    }
    if (this.objs == null) {
      return true;
    }
    // Because of optional properties,
    //   x \le y \iff x \join y = y does not hold.
    return ObjectType.isUnionSubtype(
        keepLoosenessOfThis, this.objs, other.objs);
  }

  public JSType removeType(JSType other) {
    int otherMask = other.mask;
    Preconditions.checkState(
        !other.isTop() && !other.isUnknown() &&
        (otherMask & TYPEVAR_MASK) == 0 && (otherMask & ENUM_MASK) == 0);
    if (isUnknown()) {
      return this;
    }
    if (isTop()) {
      return ALMOST_TOP.removeType(other);
    }
    int newMask = mask & ~otherMask;
    if ((otherMask & NON_SCALAR_MASK) == 0) {
      return new JSType(newMask, location, objs, typeVar, enums);
    }
    // TODO(dimvar): If objs and enums stay unchanged, reuse, don't recreate.
    Preconditions.checkState(other.objs.size() == 1,
        "Invalid type to remove: %s", other);
    ObjectType otherObj = Iterables.getOnlyElement(other.objs);
    ImmutableSet<ObjectType> newObjs = null;
    ImmutableSet<EnumType> newEnums = null;
    if (objs != null) {
      ImmutableSet.Builder<ObjectType> builder = ImmutableSet.builder();
      for (ObjectType obj : objs) {
        if (!obj.isSubtypeOf(otherObj)) {
          builder.add(obj);
        }
      }
      newObjs = builder.build();
    }
    if (enums != null) {
      ImmutableSet.Builder<EnumType> builder = ImmutableSet.builder();
      for (EnumType e : enums) {
        if (!e.getEnumeratedType().isSubtypeOf(other)) {
          builder.add(e);
        }
      }
      newEnums = builder.build();
    }
    return new JSType(newMask, location, newObjs, typeVar, newEnums);
  }

  public JSType withLocation(String location) {
    if (Objects.equal(location, this.location)) {
      return this;
    }
    String newLocation = location == null ? null : JSType.GENERIC_LOCATION;
    return new JSType(mask, location,
        objs == null ? null : ObjectType.withLocation(objs, newLocation),
        typeVar, enums);
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
    if (objs == null) {
      Preconditions.checkState(enums != null);
      return this;
    }
    return new JSType(
        this.mask, this.location,
        ObjectType.withLooseObjects(this.objs), typeVar, enums);
  }

  public JSType getProp(QualifiedName qname) {
    if (isBottom() || isUnknown()) {
      return UNKNOWN;
    }
    Preconditions.checkState(objs != null || enums != null);
    return nullAcceptingJoin(
        TypeWithProperties.getProp(objs, qname),
        TypeWithProperties.getProp(enums, qname));
  }

  public JSType getDeclaredProp(QualifiedName qname) {
    if (isUnknown()) {
      return UNKNOWN;
    }
    Preconditions.checkState(objs != null || enums != null);
    return nullAcceptingJoin(
        TypeWithProperties.getDeclaredProp(objs, qname),
        TypeWithProperties.getDeclaredProp(enums, qname));
  }

  public boolean mayHaveProp(QualifiedName qname) {
    return TypeWithProperties.mayHaveProp(objs, qname) ||
        TypeWithProperties.mayHaveProp(enums, qname);
  }

  public boolean hasProp(QualifiedName qname) {
    if (objs != null && !TypeWithProperties.hasProp(objs, qname)) {
      return false;
    }
    if (enums != null && !TypeWithProperties.hasProp(enums, qname)) {
      return false;
    }
    return enums != null || objs != null;
  }

  public boolean hasConstantProp(QualifiedName pname) {
    Preconditions.checkArgument(pname.isIdentifier());
    return TypeWithProperties.hasConstantProp(objs, pname) ||
        TypeWithProperties.hasConstantProp(enums, pname);
  }

  public JSType withoutProperty(QualifiedName qname) {
    return this.objs == null ?
        this :
        new JSType(this.mask, this.location,
            ObjectType.withoutProperty(this.objs, qname), typeVar, enums);
  }

  public JSType withProperty(QualifiedName qname, JSType type) {
    Preconditions.checkArgument(type != null);
    if (isUnknown()) {
      return this;
    }
    Preconditions.checkState(this.objs != null);
    return new JSType(this.mask, this.location,
        ObjectType.withProperty(this.objs, qname, type), typeVar, enums);
  }

  public JSType withDeclaredProperty(
      QualifiedName qname, JSType type, boolean isConstant) {
    Preconditions.checkState(this.objs != null && this.location == null);
    if (type == null && isConstant) {
      type = JSType.UNKNOWN;
    }
    return new JSType(this.mask, null,
        ObjectType.withDeclaredProperty(this.objs, qname, type, isConstant),
        typeVar, enums);
  }

  public JSType withPropertyRequired(String pname) {
    return (isUnknown() || this.objs == null) ?
        this :
        new JSType(this.mask, this.location,
            ObjectType.withPropertyRequired(this.objs, pname), typeVar, enums);
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
    return appendTo(new StringBuilder()).toString();
  }

  public StringBuilder appendTo(StringBuilder builder) {
    if (location == null) {
      return typeToString(builder);
    } else {
      return typeToString(builder).append('@').append(location);
    }
  }

  /** For use in {@link #typeToString} */
  private static final Joiner PIPE_JOINER = Joiner.on("|");

  private StringBuilder typeToString(StringBuilder builder) {
    switch (mask) {
      case BOTTOM_MASK:
        return builder.append("bottom");
      case TOP_MASK:
        return builder.append("*");
      case UNKNOWN_MASK:
        return builder.append("?");
      default:
        int tags = mask;
        boolean firstIteration = true;
        for (int tag = 1; tag != END_MASK; tag <<= 1) {
          if ((tags & tag) != 0) {
            if (!firstIteration) {
              builder.append('|');
            }
            firstIteration = false;
            switch (tag) {
              case TRUE_MASK:
              case FALSE_MASK:
                builder.append("boolean");
                tags &= ~BOOLEAN_MASK;
                continue;
              case NULL_MASK:
                builder.append("null");
                tags &= ~NULL_MASK;
                continue;
              case NUMBER_MASK:
                builder.append("number");
                tags &= ~NUMBER_MASK;
                continue;
              case STRING_MASK:
                builder.append("string");
                tags &= ~STRING_MASK;
                continue;
              case UNDEFINED_MASK:
                builder.append("undefined");
                tags &= ~UNDEFINED_MASK;
                continue;
              case TYPEVAR_MASK:
                builder.append(typeVar);
                tags &= ~TYPEVAR_MASK;
                continue;
              case NON_SCALAR_MASK: {
                if (objs.size() == 1) {
                  Iterables.getOnlyElement(objs).appendTo(builder);
                } else {
                  Set<String> strReps = Sets.newTreeSet();
                  for (ObjectType obj : objs) {
                    strReps.add(obj.toString());
                  }
                  PIPE_JOINER.appendTo(builder, strReps);
                }
                tags &= ~NON_SCALAR_MASK;
                continue;
              }
              case ENUM_MASK: {
                if (enums.size() == 1) {
                  builder.append(Iterables.getOnlyElement(enums).toString());
                } else {
                  Set<String> strReps = Sets.newTreeSet();
                  for (EnumType e : enums) {
                    strReps.add(e.toString());
                  }
                  PIPE_JOINER.appendTo(builder, strReps);
                }
                tags &= ~ENUM_MASK;
                continue;
              }
            }
          }
        }
        if (tags == 0) { // Found all types in the union
          return builder;
        } else if (tags == TRUTHY_MASK) {
          return builder.append("truthy");
        } else if (tags == FALSY_MASK) {
          return builder.append("falsy");
        } else {
          return builder.append("Unrecognized type: " + tags);
        }
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
