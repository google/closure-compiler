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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public abstract class JSType {
  protected static final int BOTTOM_MASK = 0x0;
  protected static final int TYPEVAR_MASK = 0x1;
  protected static final int NON_SCALAR_MASK = 0x2;
  protected static final int ENUM_MASK = 0x4;
  protected static final int TRUE_MASK = 0x8;  // These two print out
  protected static final int FALSE_MASK = 0x10; // as 'boolean'
  protected static final int NULL_MASK = 0x20;
  protected static final int NUMBER_MASK = 0x40;
  protected static final int STRING_MASK = 0x80;
  protected static final int UNDEFINED_MASK = 0x100;
  protected static final int END_MASK = UNDEFINED_MASK * 2;
  // When either of the next two bits is set, the rest of the type isn't
  // guaranteed to be in a consistent state.
  protected static final int TRUTHY_MASK = 0x200;
  protected static final int FALSY_MASK = 0x400;
  // Room to grow.
  protected static final int UNKNOWN_MASK = 0x7fffffff; // @type {?}
  protected static final int TOP_MASK = 0xffffffff; // @type {*}

  protected static final int BOOLEAN_MASK = TRUE_MASK | FALSE_MASK;
  protected static final int TOP_SCALAR_MASK =
      NUMBER_MASK | STRING_MASK | BOOLEAN_MASK | NULL_MASK | UNDEFINED_MASK;

  // A type tainted with this is non local, but it's not the type of a formal,
  // eg, it can be a prop of a formal whose decl type is a record type.
  static String GENERIC_LOCATION = "%";

  // Used only for development
  public static boolean mockToString = false;

  private static JSType makeType(int mask, String location,
      ImmutableSet<ObjectType> objs, String typeVar,
      ImmutableSet<EnumType> enums) {
    // Fix up the mask for objects and enums
    if (enums != null) {
      if (enums.isEmpty()) {
        mask &= ~ENUM_MASK;
      } else {
        mask |= ENUM_MASK;
      }
    }

    if (objs != null) {
      if (objs.isEmpty()) {
        mask &= ~NON_SCALAR_MASK;
      } else {
        mask |= NON_SCALAR_MASK;
      }
    }

    if (objs == null && typeVar == null && enums == null && location == null) {
      return MaskType.make(mask);
    }
    if (mask == NON_SCALAR_MASK && location == null) {
      return new ObjsType(objs);
    }
    if (mask == (NON_SCALAR_MASK | NULL_MASK) && location == null) {
      return new NullableObjsType(objs);
    }
    return new UnionType(mask, location, objs, typeVar, enums);
  }

  private static JSType makeType(int mask) {
    return makeType(mask, null, null, null, null);
  }

  protected abstract int getMask();

  protected abstract ImmutableSet<ObjectType> getObjs();

  protected abstract String getTypeVar();

  protected abstract ImmutableSet<EnumType> getEnums();

  public abstract String getLocation();

  // Factory method for wrapping a function in a JSType
  public static JSType fromFunctionType(FunctionType fn) {
    return makeType(NON_SCALAR_MASK, null,
        ImmutableSet.of(ObjectType.fromFunction(fn)), null, null);
  }

  public static JSType fromObjectType(ObjectType obj) {
    return makeType(NON_SCALAR_MASK, null, ImmutableSet.of(obj), null, null);
  }

  public static JSType fromTypeVar(String template) {
    return makeType(TYPEVAR_MASK, null, null, template, null);
  }

  static JSType fromEnum(EnumType e) {
    return makeType(ENUM_MASK, null, null, null, ImmutableSet.of(e));
  }

  boolean isValidType() {
    if (isUnknown() || isTop()) {
      return true;
    }
    if ((getMask() & NON_SCALAR_MASK) != 0
        && (getObjs() == null || getObjs().isEmpty())) {
      return false;
    }
    if ((getMask() & NON_SCALAR_MASK) == 0 && getObjs() != null) {
      return false;
    }
    if ((getMask() & ENUM_MASK) != 0
        && (getEnums() == null || getEnums().isEmpty())) {
      return false;
    }
    if ((getMask() & ENUM_MASK) == 0 && getEnums() != null) {
      return false;
    }
    return ((getMask() & TYPEVAR_MASK) != 0) == (getTypeVar() != null);
  }

  public static final JSType BOOLEAN = new MaskType(TRUE_MASK | FALSE_MASK);
  public static final JSType BOTTOM = new MaskType(BOTTOM_MASK);
  public static final JSType FALSE_TYPE = new MaskType(FALSE_MASK);
  public static final JSType FALSY = new MaskType(FALSY_MASK);
  public static final JSType NULL = new MaskType(NULL_MASK);
  public static final JSType NUMBER = new MaskType(NUMBER_MASK);
  public static final JSType STRING = new MaskType(STRING_MASK);
  public static final JSType TOP = new MaskType(TOP_MASK);
  public static final JSType TOP_SCALAR = makeType(TOP_SCALAR_MASK);
  public static final JSType TRUE_TYPE = new MaskType(TRUE_MASK);
  public static final JSType TRUTHY = new MaskType(TRUTHY_MASK);
  public static final JSType UNDEFINED = new MaskType(UNDEFINED_MASK);
  public static final JSType UNKNOWN = new MaskType(UNKNOWN_MASK);

  public static final JSType TOP_OBJECT = fromObjectType(ObjectType.TOP_OBJECT);
  public static final JSType TOP_STRUCT = fromObjectType(ObjectType.TOP_STRUCT);
  public static final JSType TOP_DICT = fromObjectType(ObjectType.TOP_DICT);
  private static JSType TOP_FUNCTION = null;

  // Some commonly used types
  public static final JSType NULL_OR_UNDEF =
      new MaskType(NULL_MASK | UNDEFINED_MASK);
  public static final JSType NUM_OR_STR =
      new MaskType(NUMBER_MASK | STRING_MASK);

  // Explicitly contains most types. Used only by removeType.
  private static final JSType ALMOST_TOP = makeType(
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
    return TOP_MASK == getMask();
  }

  public boolean isBottom() {
    return BOTTOM_MASK == getMask();
  }

  public boolean isUnknown() {
    return UNKNOWN_MASK == getMask();
  }

  public boolean isTruthy() {
    return TRUTHY_MASK == getMask() || TRUE_MASK == getMask();
  }

  public boolean isFalsy() {
    return FALSY_MASK == getMask() || FALSE_MASK == getMask();
  }

  public boolean isBoolean() {
    return (getMask() & ~BOOLEAN_MASK) == 0 && (getMask() & BOOLEAN_MASK) != 0;
  }

  public boolean isNullOrUndef() {
    int nullUndefMask = NULL_MASK | UNDEFINED_MASK;
    return getMask() != 0 && (getMask() | nullUndefMask) == nullUndefMask;
  }

  public boolean isFromUndeclaredFormal() {
    return getLocation() != null;
  }

  public boolean isScalar() {
    return getMask() == NUMBER_MASK
        || getMask() == STRING_MASK
        || getMask() == NULL_MASK
        || getMask() == UNDEFINED_MASK
        || isBoolean();
  }

  // True iff there exists a value that can have this type
  public boolean isInhabitable() {
    if (isBottom()) {
      return false;
    } else if (getObjs() == null) {
      return true;
    }
    for (ObjectType obj : getObjs()) {
      if (!obj.isInhabitable()) {
        return false;
      }
    }
    return true;
  }

  public boolean hasNonScalar() {
    return getObjs() != null || EnumType.hasNonScalar(getEnums());
  }

  public boolean isNullable() {
    return (getMask() & NULL_MASK) != 0;
  }

  boolean isTypeVariable() {
    return (getMask() & TYPEVAR_MASK) != 0 && (getMask() & ~TYPEVAR_MASK) == 0;
  }

  public boolean isRecordType() {
    return getMask() == NON_SCALAR_MASK && getObjs().size() == 1
        && Iterables.getOnlyElement(getObjs()).isRecordType();
  }

  public boolean isStruct() {
    if (getObjs() == null) {
      return false;
    }
    for (ObjectType objType : getObjs()) {
      if (objType.isStruct()) {
        return true;
      }
    }
    return false;
  }

  public boolean isLooseStruct() {
    if (getObjs() == null) {
      return false;
    }
    boolean foundLooseStruct = false;
    boolean foundNonLooseStruct = false;
    for (ObjectType objType : getObjs()) {
      if (objType.isLooseStruct()) {
        foundLooseStruct = true;
      } else if (objType.isStruct()) {
        foundNonLooseStruct = true;
      }
    }
    return foundLooseStruct && !foundNonLooseStruct;
  }

  public boolean isDict() {
    if (getObjs() == null) {
      return false;
    }
    for (ObjectType objType : getObjs()) {
      if (objType.isDict()) {
        return true;
      }
    }
    return false;
  }

  public boolean isEnumElement() {
    return getMask() == ENUM_MASK && getEnums().size() == 1;
  }

  public boolean isUnion() {
    if (isBottom() || isTop() || isUnknown()
        || isScalar() || isTypeVariable() || isEnumElement()) {
      return false;
    }
    if (getMask() == NON_SCALAR_MASK && getObjs().size() == 1) {
      return false;
    }
    return true;
  }

  public static boolean areCompatibleScalarTypes(JSType lhs, JSType rhs) {
    Preconditions.checkArgument(
        lhs.isSubtypeOf(TOP_SCALAR) || rhs.isSubtypeOf(TOP_SCALAR));
    if (lhs.isBottom() || rhs.isBottom()
        || lhs.isUnknown() || rhs.isUnknown()
        || (lhs.isBoolean() && rhs.isBoolean())
        || lhs.equals(rhs)) {
      return true;
    }
    return false;
  }

  // Only makes sense for a JSType that represents a single enum
  public JSType getEnumeratedType() {
    return isEnumElement() ?
        Iterables.getOnlyElement(getEnums()).getEnumeratedType() : null;
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
    if (lhs.getTypeVar() != null && rhs.getTypeVar() != null
        && !lhs.getTypeVar().equals(rhs.getTypeVar())) {
      // For now return ? when joining two type vars. This is probably uncommon.
      return UNKNOWN;
    }

    int newMask = lhs.getMask() | rhs.getMask();
    String newLoc = joinLocs(lhs.getLocation(), rhs.getLocation());
    ImmutableSet<ObjectType> newObjs =
        ObjectType.joinSets(lhs.getObjs(), rhs.getObjs());
    String newTypevar =
        lhs.getTypeVar() != null ? lhs.getTypeVar() : rhs.getTypeVar();
    ImmutableSet<EnumType> newEnums =
        EnumType.union(lhs.getEnums(), rhs.getEnums());
    if (newEnums == null) {
      return makeType(newMask, newLoc, newObjs, newTypevar, null);
    }
    JSType tmpJoin = makeType(
        newMask & ~ENUM_MASK, newLoc, newObjs, newTypevar, null);
    return makeType(newMask, newLoc, newObjs, newTypevar,
        EnumType.normalizeForJoin(newEnums, tmpJoin));
  }

  private static String joinLocs(String loc1, String loc2) {
    if (loc1 == null || loc2 == null) {
      return null;
    }
    return loc1.equals(loc2) ? loc1 : GENERIC_LOCATION;
  }

  public JSType substituteGenerics(Map<String, JSType> concreteTypes) {
    if (isTop() || isUnknown()
        || getObjs() == null && getTypeVar() == null) {
      return this;
    }
    ImmutableSet<ObjectType> newObjs = null;
    if (getObjs() != null) {
      ImmutableSet.Builder<ObjectType> builder = ImmutableSet.builder();
      for (ObjectType obj : getObjs()) {
        builder.add(obj.substituteGenerics(concreteTypes));
      }
      newObjs = builder.build();
    }
    JSType current = makeType(
        getMask() & ~TYPEVAR_MASK, getLocation(), newObjs, null, getEnums());
    if ((getMask() & TYPEVAR_MASK) != 0) {
      current = JSType.join(current, concreteTypes.containsKey(getTypeVar()) ?
          concreteTypes.get(getTypeVar()) : fromTypeVar(getTypeVar()));
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
    if (t1.getEnums() == null) {
      if (t2.getEnums() != null) {
        return null;
      }
      newEnums = null;
    } else if (t2.getEnums() == null) {
      return null;
    } else if (!t1.getEnums().equals(t2.getEnums())) {
      return null;
    } else {
      newEnums = t1.getEnums();
    }

    int t1Mask = promoteBoolean(t1.getMask());
    int t2Mask = promoteBoolean(t2.getMask());
    if (t1Mask != t2Mask || !Objects.equals(t1.getTypeVar(), t2.getTypeVar())) {
      return null;
    }
    // All scalar types are equal
    if ((t1Mask & NON_SCALAR_MASK) == 0) {
      return t1;
    }
    if (t1.getObjs().size() != t2.getObjs().size()) {
      return null;
    }

    Set<ObjectType> ununified = Sets.newHashSet(t2.getObjs());
    Set<ObjectType> unifiedObjs = Sets.newHashSet();
    for (ObjectType objType1 : t1.getObjs()) {
      ObjectType unified = objType1;
      boolean hasUnified = false;
      for (ObjectType objType2 : t2.getObjs()) {
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
    return makeType(t1Mask, null, ImmutableSet.copyOf(unifiedObjs),
        t1.getTypeVar(), newEnums);
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
    } else if (getMask() == TYPEVAR_MASK
        && typeParameters.contains(getTypeVar())) {
      updateTypemap(typeMultimap, getTypeVar(),
          makeType(promoteBoolean(other.getMask()), null, other.getObjs(),
              other.getTypeVar(), other.getEnums()));
      return true;
    } else if (other.isTop()) {
      return false;
    } else if (other.isUnknown()) {
      return true;
    }

    Set<EnumType> ununifiedEnums = null;
    if (getEnums() == null) {
      ununifiedEnums = other.getEnums();
    } else if (other.getEnums() == null) {
      return false;
    } else {
      ununifiedEnums = Sets.newHashSet();
      for (EnumType e : getEnums()) {
        if (!other.getEnums().contains(e)) {
          return false;
        }
      }
      for (EnumType e : other.getEnums()) {
        if (!getEnums().contains(e)) {
          ununifiedEnums.add(e);
        }
      }
      if (ununifiedEnums.isEmpty()) {
        ununifiedEnums = null;
      }
    }

    Set<ObjectType> ununified = ImmutableSet.of();
    if (other.getObjs() != null) {
      ununified = Sets.newHashSet(other.getObjs());
    }
    // Each obj in this must unify w/ exactly one obj in other.
    // However, we don't check that two different objects of this don't unify
    // with the same other type.
    if (getObjs() != null) {
      if (other.getObjs() == null) {
        return false;
      }
      for (ObjectType targetObj : getObjs()) {
        boolean hasUnified = false;
        for (ObjectType sourceObj : other.getObjs()) {
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

    String thisTypevar = getTypeVar();
    String otherTypevar = other.getTypeVar();
    if (thisTypevar == null) {
      return otherTypevar == null && getMask() == other.getMask();
    } else if (!typeParameters.contains(thisTypevar)) {
      return thisTypevar.equals(otherTypevar) && getMask() == other.getMask();
    } else {
      // this is (T | ...)
      int templateMask = 0;
      int thisScalarBits = getMask() & ~NON_SCALAR_MASK & ~TYPEVAR_MASK;
      int otherScalarBits = other.getMask() & ~NON_SCALAR_MASK & ~TYPEVAR_MASK;
      templateMask |= otherScalarBits & ~thisScalarBits;

      if (templateMask == BOTTOM_MASK) {
        // nothing left in other to assign to thisTypevar
        return false;
      }
      JSType templateType = makeType(
          promoteBoolean(templateMask), null,
          ImmutableSet.copyOf(ununified), otherTypevar,
          ununifiedEnums == null ? null : ImmutableSet.copyOf(ununifiedEnums));
      updateTypemap(typeMultimap, getTypeVar(), templateType);
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
      return other.withLocation(getLocation());
    }
    int newMask = getMask() & other.getMask();
    String newTypevar;
    if (Objects.equals(getTypeVar(), other.getTypeVar())) {
      newTypevar = getTypeVar();
    } else {
      newTypevar = null;
      newMask &= ~TYPEVAR_MASK;
    }
    return meetEnums(
        newMask, getMask() | other.getMask(), getLocation(),
        ObjectType.specializeSet(getObjs(), other.getObjs()),
        newTypevar, getObjs(), other.getObjs(), getEnums(), other.getEnums());
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
    int newMask = lhs.getMask() & rhs.getMask();
    String newTypevar;
    if (Objects.equals(lhs.getTypeVar(), rhs.getTypeVar())) {
      newTypevar = lhs.getTypeVar();
    } else {
      newTypevar = null;
      newMask = newMask & ~TYPEVAR_MASK;
    }
    return meetEnums(
        newMask, lhs.getMask() | rhs.getMask(), null,
        ObjectType.meetSets(lhs.getObjs(), rhs.getObjs()),
        newTypevar, lhs.getObjs(), rhs.getObjs(),
        lhs.getEnums(), rhs.getEnums());
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
    if (Objects.equals(enums1, enums2)) {
      return makeType(newMask, newLocation, newObjs, newTypevar, enums1);
    }
    ImmutableSet.Builder<EnumType> enumBuilder = ImmutableSet.builder();
    ImmutableSet<EnumType> allEnums = EnumType.union(enums1, enums2);
    for (EnumType e : allEnums) {
      // An enum in the intersection will always be in the result
      if (enums1 != null && enums1.contains(e)
          && enums2 != null && enums2.contains(e)) {
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
      if (enumeratedType.getMask() != NON_SCALAR_MASK) {
        if ((enumeratedType.getMask() & unionMask) != 0) {
          enumBuilder.add(e);
          newMask &= ~enumeratedType.getMask();
        }
      } else if (objs1 != null || objs2 != null) {
        Set<ObjectType> objsToRemove = Sets.newHashSet();
        ObjectType enumObj = Iterables.getOnlyElement(enumeratedType.getObjs());
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
    return makeType(
        newMask, newLocation, newObjs, newTypevar, enumBuilder.build());
  }

  private JSType makeTruthy() {
    if (this.isTop() || this.isUnknown()) {
      return this;
    }
    return makeType(getMask() & ~NULL_MASK & ~FALSE_MASK & ~UNDEFINED_MASK,
        getLocation(), getObjs(), getTypeVar(), getEnums());
  }

  private JSType makeFalsy() {
    if (this.isTop() || this.isUnknown()) {
      return this;
    }
    return makeType(getMask() & ~TRUE_MASK & ~NON_SCALAR_MASK,
        getLocation(), null, getTypeVar(), getEnums());
  }

  public static JSType plus(JSType lhs, JSType rhs) {
    int newtype = (lhs.getMask() | rhs.getMask()) & STRING_MASK;
    if ((lhs.getMask() & ~STRING_MASK) != 0
        && (rhs.getMask() & ~STRING_MASK) != 0) {
      newtype |= NUMBER_MASK;
    }
    return makeType(newtype);
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
    int mask = getMask() & ~ENUM_MASK;
    if ((mask | other.getMask()) != other.getMask()) {
      return false;
    }
    if (!Objects.equals(getTypeVar(), other.getTypeVar())) {
      return false;
    }
    if (getObjs() == null) {
      return true;
    }
    // Because of optional properties,
    //   x \le y \iff x \join y = y does not hold.
    return ObjectType.isUnionSubtype(
        keepLoosenessOfThis, getObjs(), other.getObjs());
  }

  public JSType removeType(JSType other) {
    int otherMask = other.getMask();
    Preconditions.checkState(
        !other.isTop() && !other.isUnknown()
        && (otherMask & TYPEVAR_MASK) == 0 && (otherMask & ENUM_MASK) == 0);
    if (isUnknown()) {
      return this;
    }
    if (isTop()) {
      return ALMOST_TOP.removeType(other);
    }
    int newMask = getMask() & ~otherMask;
    if ((otherMask & NON_SCALAR_MASK) == 0) {
      return makeType(
          newMask, getLocation(), getObjs(), getTypeVar(), getEnums());
    }
    // TODO(dimvar): If objs and enums stay unchanged, reuse, don't recreate.
    Preconditions.checkState(other.getObjs().size() == 1,
        "Invalid type to remove: %s", other);
    ObjectType otherObj = Iterables.getOnlyElement(other.getObjs());
    ImmutableSet<ObjectType> newObjs = null;
    ImmutableSet<EnumType> newEnums = null;
    if (getObjs() != null) {
      ImmutableSet.Builder<ObjectType> builder = ImmutableSet.builder();
      for (ObjectType obj : getObjs()) {
        if (!obj.isSubtypeOf(otherObj)) {
          builder.add(obj);
        }
      }
      newObjs = builder.build();
    }
    if (getEnums() != null) {
      ImmutableSet.Builder<EnumType> builder = ImmutableSet.builder();
      for (EnumType e : getEnums()) {
        if (!e.getEnumeratedType().isSubtypeOf(other)) {
          builder.add(e);
        }
      }
      newEnums = builder.build();
    }
    return makeType(newMask, getLocation(), newObjs, getTypeVar(), newEnums);
  }

  public JSType withLocation(String location) {
    if (Objects.equals(location, getLocation())) {
      return this;
    }
    String newLoc = location == null ? null : JSType.GENERIC_LOCATION;
    return makeType(getMask(), location,
        getObjs() == null ? null : ObjectType.withLocation(getObjs(), newLoc),
        getTypeVar(), getEnums());
  }

  public FunctionType getFunTypeIfSingletonObj() {
    if (getMask() != NON_SCALAR_MASK || getObjs().size() > 1) {
      return null;
    }
    return Iterables.getOnlyElement(getObjs()).getFunType();
  }

  public FunctionType getFunType() {
    if (getObjs() == null) {
      return null;
    }
    if (getObjs().size() == 1) { // The common case is fast
      return Iterables.getOnlyElement(getObjs()).getFunType();
    }
    FunctionType result = FunctionType.TOP_FUNCTION;
    for (ObjectType obj : getObjs()) {
      result = FunctionType.meet(result, obj.getFunType());
    }
    return result;
  }

  NominalType getNominalTypeIfUnique() {
    if (getObjs() == null || getObjs().size() > 1) {
      return null;
    }
    return Iterables.getOnlyElement(getObjs()).getNominalType();
  }

  public boolean isInterfaceDefinition() {
    if (getObjs() == null || getObjs().size() > 1) {
      return false;
    }
    FunctionType ft = Iterables.getOnlyElement(getObjs()).getFunType();
    return ft != null && ft.isInterfaceDefinition();
  }

  /** Turns the class-less object of this type (if any) into a loose object */
  public JSType withLoose() {
    if (getObjs() == null) {
      Preconditions.checkState(getEnums() != null);
      return this;
    }
    return makeType(
        getMask(), getLocation(),
        ObjectType.withLooseObjects(getObjs()), getTypeVar(), getEnums());
  }

  public JSType getProp(QualifiedName qname) {
    if (isBottom() || isUnknown()) {
      return UNKNOWN;
    }
    Preconditions.checkState(getObjs() != null || getEnums() != null);
    return nullAcceptingJoin(
        TypeWithPropertiesStatics.getProp(getObjs(), qname),
        TypeWithPropertiesStatics.getProp(getEnums(), qname));
  }

  public JSType getDeclaredProp(QualifiedName qname) {
    if (isUnknown()) {
      return UNKNOWN;
    }
    Preconditions.checkState(getObjs() != null || getEnums() != null);
    return nullAcceptingJoin(
        TypeWithPropertiesStatics.getDeclaredProp(getObjs(), qname),
        TypeWithPropertiesStatics.getDeclaredProp(getEnums(), qname));
  }

  public boolean mayHaveProp(QualifiedName qname) {
    return TypeWithPropertiesStatics.mayHaveProp(getObjs(), qname) ||
        TypeWithPropertiesStatics.mayHaveProp(getEnums(), qname);
  }

  public boolean hasProp(QualifiedName qname) {
    if (getObjs() != null
        && !TypeWithPropertiesStatics.hasProp(getObjs(), qname)) {
      return false;
    }
    if (getEnums() != null
        && !TypeWithPropertiesStatics.hasProp(getEnums(), qname)) {
      return false;
    }
    return getEnums() != null || getObjs() != null;
  }

  public boolean hasConstantProp(QualifiedName pname) {
    Preconditions.checkArgument(pname.isIdentifier());
    return TypeWithPropertiesStatics.hasConstantProp(getObjs(), pname) ||
        TypeWithPropertiesStatics.hasConstantProp(getEnums(), pname);
  }

  public JSType withoutProperty(QualifiedName qname) {
    return getObjs() == null ?
        this :
        makeType(getMask(), getLocation(),
            ObjectType.withoutProperty(getObjs(), qname),
            getTypeVar(), getEnums());
  }

  public JSType withProperty(QualifiedName qname, JSType type) {
    Preconditions.checkArgument(type != null);
    if (isUnknown()) {
      return this;
    }
    Preconditions.checkState(getObjs() != null);
    return makeType(getMask(), getLocation(),
        ObjectType.withProperty(getObjs(), qname, type),
        getTypeVar(), getEnums());
  }

  public JSType withDeclaredProperty(
      QualifiedName qname, JSType type, boolean isConstant) {
    Preconditions.checkState(getObjs() != null && getLocation() == null);
    if (type == null && isConstant) {
      type = JSType.UNKNOWN;
    }
    return makeType(getMask(), null,
        ObjectType.withDeclaredProperty(getObjs(), qname, type, isConstant),
        getTypeVar(), getEnums());
  }

  public JSType withPropertyRequired(String pname) {
    return (isUnknown() || getObjs() == null) ?
        this :
        makeType(getMask(), getLocation(),
            ObjectType.withPropertyRequired(getObjs(), pname),
            getTypeVar(), getEnums());
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
    if (mockToString) {
      return "";
    }
    return appendTo(new StringBuilder()).toString();
  }

  public StringBuilder appendTo(StringBuilder builder) {
    if (getLocation() == null) {
      return typeToString(builder);
    } else {
      return typeToString(builder).append('@').append(getLocation());
    }
  }

  /** For use in {@link #typeToString} */
  private static final Joiner PIPE_JOINER = Joiner.on("|");

  private StringBuilder typeToString(StringBuilder builder) {
    switch (getMask()) {
      case BOTTOM_MASK:
        return builder.append("bottom");
      case TOP_MASK:
        return builder.append("*");
      case UNKNOWN_MASK:
        return builder.append("?");
      default:
        int tags = getMask();
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
                builder.append(getTypeVar());
                tags &= ~TYPEVAR_MASK;
                continue;
              case NON_SCALAR_MASK: {
                if (getObjs().size() == 1) {
                  Iterables.getOnlyElement(getObjs()).appendTo(builder);
                } else {
                  Set<String> strReps = Sets.newTreeSet();
                  for (ObjectType obj : getObjs()) {
                    strReps.add(obj.toString());
                  }
                  PIPE_JOINER.appendTo(builder, strReps);
                }
                tags &= ~NON_SCALAR_MASK;
                continue;
              }
              case ENUM_MASK: {
                if (getEnums().size() == 1) {
                  builder.append(
                      Iterables.getOnlyElement(getEnums()).toString());
                } else {
                  Set<String> strReps = Sets.newTreeSet();
                  for (EnumType e : getEnums()) {
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
    return getMask() == t2.getMask() && Objects.equals(getObjs(), t2.getObjs());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getMask(), getObjs());
  }
}

final class UnionType extends JSType {
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

  UnionType(int mask, String location, ImmutableSet<ObjectType> objs,
      String typeVar, ImmutableSet<EnumType> enums) {
    if (enums == null) {
      this.enums = null;
    } else if (enums.isEmpty()) {
      this.enums = null;
    } else {
      this.enums = enums;
    }

    if (objs == null) {
      this.objs = null;
    } else if (objs.isEmpty()) {
      this.objs = null;
    } else {
      this.objs = objs;
    }

    if (typeVar != null) {
      mask |= TYPEVAR_MASK;
    }

    this.typeVar = typeVar;
    this.location = location;
    this.mask = mask;

    Preconditions.checkState(isValidType(),
        "Cannot create type with bits <<<%s>>>, "
        + "objs <<<%s>>>, typeVar <<<%s>>>, enums <<<%s>>>",
        mask, objs, typeVar, enums);
  }

  UnionType(int mask) {
    this(mask, null, null, null, null);
  }

  protected int getMask() {
    return mask;
  }

  protected ImmutableSet<ObjectType> getObjs() {
    return objs;
  }

  protected String getTypeVar() {
    return typeVar;
  }

  protected ImmutableSet<EnumType> getEnums() {
    return enums;
  }

  public String getLocation() {
    return location;
  }
}

class MaskType extends JSType {
  // Masks for common types:
  private static final int NUMBER_OR_STRING_MASK = NUMBER_MASK | STRING_MASK;
  // union of undefined and stuff
  private static final int UNDEFINED_OR_BOOLEAN_MASK =
      UNDEFINED_MASK | TRUE_MASK | FALSE_MASK;
  private static final int UNDEFINED_OR_NUMBER_MASK =
      UNDEFINED_MASK | NUMBER_MASK;
  private static final int UNDEFINED_OR_STRING_MASK =
      UNDEFINED_MASK | STRING_MASK;
  private static final int UNDEFINED_OR_NULL_MASK = UNDEFINED_MASK | NULL_MASK;
  // union of null and stuff
  private static final int NULL_OR_BOOLEAN_MASK =
      NULL_MASK | TRUE_MASK | FALSE_MASK;
  private static final int NULL_OR_NUMBER_MASK = NULL_MASK | NUMBER_MASK;
  private static final int NULL_OR_STRING_MASK = NULL_MASK | STRING_MASK;

  private static final MaskType NUMBER_OR_STRING =
      new MaskType(NUMBER_OR_STRING_MASK);
  private static final MaskType UNDEFINED_OR_BOOLEAN =
      new MaskType(UNDEFINED_OR_BOOLEAN_MASK);
  private static final MaskType UNDEFINED_OR_NUMBER =
      new MaskType(UNDEFINED_OR_NUMBER_MASK);
  private static final MaskType UNDEFINED_OR_STRING =
      new MaskType(UNDEFINED_OR_STRING_MASK);
  private static final MaskType UNDEFINED_OR_NULL =
      new MaskType(UNDEFINED_OR_NULL_MASK);
  private static final MaskType NULL_OR_BOOLEAN =
      new MaskType(NULL_OR_BOOLEAN_MASK);
  private static final MaskType NULL_OR_NUMBER =
      new MaskType(NULL_OR_NUMBER_MASK);
  private static final MaskType NULL_OR_STRING =
      new MaskType(NULL_OR_STRING_MASK);

  protected final int mask;

  MaskType(int mask) {
    this.mask = mask;
  }

  static JSType make(int mask) {
    switch (mask) {
      case BOTTOM_MASK:
        return JSType.BOTTOM;
      case TRUE_MASK:
        return JSType.TRUE_TYPE;
      case FALSE_MASK:
        return JSType.FALSE_TYPE;
      case NULL_MASK:
        return JSType.NULL;
      case NUMBER_MASK:
        return JSType.NUMBER;
      case STRING_MASK:
        return JSType.STRING;
      case UNDEFINED_MASK:
        return JSType.UNDEFINED;
      case TRUTHY_MASK:
        return JSType.TRUTHY;
      case FALSY_MASK:
        return JSType.FALSY;
      case UNKNOWN_MASK:
        return JSType.UNKNOWN;
      case TOP_MASK:
        return JSType.TOP;
      case BOOLEAN_MASK:
        return JSType.BOOLEAN;
      case NUMBER_OR_STRING_MASK:
        return NUMBER_OR_STRING;
      case UNDEFINED_OR_BOOLEAN_MASK:
        return UNDEFINED_OR_BOOLEAN;
      case UNDEFINED_OR_NUMBER_MASK:
        return UNDEFINED_OR_NUMBER;
      case UNDEFINED_OR_STRING_MASK:
        return UNDEFINED_OR_STRING;
      case UNDEFINED_OR_NULL_MASK:
        return UNDEFINED_OR_NULL;
      case NULL_OR_BOOLEAN_MASK:
        return NULL_OR_BOOLEAN;
      case NULL_OR_NUMBER_MASK:
        return NULL_OR_NUMBER;
      case NULL_OR_STRING_MASK:
        return NULL_OR_STRING;
      default:
        return new MaskType(mask);
    }
  }

  protected int getMask() {
    return mask;
  }

  protected ImmutableSet<ObjectType> getObjs() {
    return null;
  }

  protected String getTypeVar() {
    return null;
  }

  protected ImmutableSet<EnumType> getEnums() {
    return null;
  }

  public String getLocation() {
    return null;
  }
}

final class ObjsType extends JSType {
  private ImmutableSet<ObjectType> objs;

  ObjsType(ImmutableSet<ObjectType> objs) {
    this.objs = objs;
  }

  protected int getMask() {
    return NON_SCALAR_MASK;
  }

  protected ImmutableSet<ObjectType> getObjs() {
    return objs;
  }

  protected String getTypeVar() {
    return null;
  }

  protected ImmutableSet<EnumType> getEnums() {
    return null;
  }

  public String getLocation() {
    return null;
  }
}

final class NullableObjsType extends JSType {
  private ImmutableSet<ObjectType> objs;

  NullableObjsType(ImmutableSet<ObjectType> objs) {
    this.objs = objs;
  }

  protected int getMask() {
    return NON_SCALAR_MASK | NULL_MASK;
  }

  protected ImmutableSet<ObjectType> getObjs() {
    return objs;
  }

  protected String getTypeVar() {
    return null;
  }

  protected ImmutableSet<EnumType> getEnums() {
    return null;
  }

  public String getLocation() {
    return null;
  }
}
