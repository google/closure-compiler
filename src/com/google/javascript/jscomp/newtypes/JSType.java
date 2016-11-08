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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.FunctionTypeI;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.TypeI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
public abstract class JSType implements TypeI, FunctionTypeI, ObjectTypeI {
  // NOTE(dimvar): the masks that are protected are used from the subclasses
  // of JSType in this file. Unfortunately, protected fields are also package visible;
  // but the masks should not be used outside this file.
  private static final int BOTTOM_MASK = 0x0;
  protected static final int TYPEVAR_MASK = 0x1;
  protected static final int NON_SCALAR_MASK = 0x2;
  private static final int ENUM_MASK = 0x4;
  // The less important use case for TRUE_MASK and FALSE_MASK is to type the
  // values true and false precisely. But people don't write: if (true) {...}
  // More importantly, these masks come up as the negation of TRUTHY_MASK and
  // FALSY_MASK when the ! operator is used.
  private static final int TRUE_MASK = 0x8;  // These two print out
  private static final int FALSE_MASK = 0x10; // as 'boolean'
  protected static final int NULL_MASK = 0x20;
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

  //Masks for common types:
  private static final int NUMBER_OR_STRING_MASK = NUMBER_MASK | STRING_MASK;
  // union of undefined and stuff
  private static final int UNDEFINED_OR_BOOLEAN_MASK = UNDEFINED_MASK | TRUE_MASK | FALSE_MASK;
  private static final int UNDEFINED_OR_NUMBER_MASK = UNDEFINED_MASK | NUMBER_MASK;
  private static final int UNDEFINED_OR_STRING_MASK = UNDEFINED_MASK | STRING_MASK;
  private static final int UNDEFINED_OR_NULL_MASK = UNDEFINED_MASK | NULL_MASK;
  // union of null and stuff
  private static final int NULL_OR_BOOLEAN_MASK = NULL_MASK | TRUE_MASK | FALSE_MASK;
  private static final int NULL_OR_NUMBER_MASK = NULL_MASK | NUMBER_MASK;
  private static final int NULL_OR_STRING_MASK = NULL_MASK | STRING_MASK;

  private static final ImmutableSet<ObjectType> NO_OBJS = ImmutableSet.<ObjectType>of();
  private static final ImmutableSet<EnumType> NO_ENUMS = ImmutableSet.<EnumType>of();

  private final JSTypes commonTypes;

  // Used only for development, to test performance of the code without the cost
  // of printing the error messages.
  public static boolean mockToString = false;

  JSType(JSTypes commonTypes) {
    Preconditions.checkNotNull(commonTypes);
    this.commonTypes = commonTypes;
  }

  private static JSType makeType(JSTypes commonTypes,
      int mask, ImmutableSet<ObjectType> objs,
      String typeVar, ImmutableSet<EnumType> enums) {
    // Fix up the mask for objects and enums
    if (Preconditions.checkNotNull(enums).isEmpty()) {
      mask &= ~ENUM_MASK;
    } else {
      mask |= ENUM_MASK;
    }

    if (Preconditions.checkNotNull(objs).isEmpty()) {
      mask &= ~NON_SCALAR_MASK;
    } else {
      mask |= NON_SCALAR_MASK;
    }

    if (objs.isEmpty() && enums.isEmpty()
        && typeVar == null && (mask & TYPEVAR_MASK) == 0) {
      return makeMaskType(commonTypes, mask);
    }
    if (!JSType.isInhabitable(objs)) {
      return commonTypes.BOTTOM;
    }
    if (mask == NON_SCALAR_MASK) {
      return new ObjsType(commonTypes, objs);
    }
    if (mask == (NON_SCALAR_MASK | NULL_MASK)) {
      return new NullableObjsType(commonTypes, objs);
    }
    return new UnionType(commonTypes, mask, objs, typeVar, enums);
  }

  private static JSType makeType(JSTypes commonTypes, int mask) {
    return makeType(commonTypes, mask, NO_OBJS, null, NO_ENUMS);
  }

  static JSType makeMaskType(JSTypes commonTypes, int mask) {
    switch (mask) {
      case BOTTOM_MASK:
        return commonTypes.BOTTOM;
      case TRUE_MASK:
        return commonTypes.TRUE_TYPE;
      case FALSE_MASK:
        return commonTypes.FALSE_TYPE;
      case NULL_MASK:
        return commonTypes.NULL;
      case NUMBER_MASK:
        return commonTypes.NUMBER;
      case STRING_MASK:
        return commonTypes.STRING;
      case UNDEFINED_MASK:
        return commonTypes.UNDEFINED;
      case TRUTHY_MASK:
        return commonTypes.TRUTHY;
      case FALSY_MASK:
        return commonTypes.FALSY;
      case UNKNOWN_MASK:
        return commonTypes.UNKNOWN;
      case TOP_MASK:
        return commonTypes.TOP;
      case BOOLEAN_MASK:
        return commonTypes.BOOLEAN;
      case NUMBER_OR_STRING_MASK:
        return commonTypes.NUMBER_OR_STRING;
      case UNDEFINED_OR_BOOLEAN_MASK:
        return commonTypes.UNDEFINED_OR_BOOLEAN;
      case UNDEFINED_OR_NUMBER_MASK:
        return commonTypes.UNDEFINED_OR_NUMBER;
      case UNDEFINED_OR_STRING_MASK:
        return commonTypes.UNDEFINED_OR_STRING;
      case UNDEFINED_OR_NULL_MASK:
        return commonTypes.NULL_OR_UNDEFINED;
      case NULL_OR_BOOLEAN_MASK:
        return commonTypes.NULL_OR_BOOLEAN;
      case NULL_OR_NUMBER_MASK:
        return commonTypes.NULL_OR_NUMBER;
      case NULL_OR_STRING_MASK:
        return commonTypes.NULL_OR_STRING;
      default:
        return new MaskType(commonTypes, mask);
    }
  }

  protected abstract int getMask();

  abstract ImmutableSet<ObjectType> getObjs();

  protected abstract String getTypeVar();

  protected abstract ImmutableSet<EnumType> getEnums();

  // Factory method for wrapping a function in a JSType
  static JSType fromFunctionType(FunctionType fn, NominalType fnNominal) {
    return makeType(
        fn.getCommonTypes(),
        NON_SCALAR_MASK,
        ImmutableSet.of(ObjectType.fromFunction(fn, fnNominal)),
        null, NO_ENUMS);
  }

  public static JSType fromObjectType(ObjectType obj) {
    return makeType(obj.getCommonTypes(), NON_SCALAR_MASK, ImmutableSet.of(obj), null, NO_ENUMS);
  }

  public static JSType fromTypeVar(JSTypes commonTypes, String typevarName) {
    return makeType(commonTypes, TYPEVAR_MASK, NO_OBJS, typevarName, NO_ENUMS);
  }

  static JSType fromEnum(EnumType e) {
    return makeType(e.getCommonTypes(), ENUM_MASK, NO_OBJS, null, ImmutableSet.of(e));
  }

  boolean isValidType() {
    if (isUnknown() || isTop()) {
      return true;
    }
    if ((getMask() & NON_SCALAR_MASK) != 0
        && getObjs().isEmpty()) {
      return false;
    }
    if ((getMask() & NON_SCALAR_MASK) == 0 && !getObjs().isEmpty()) {
      return false;
    }
    if ((getMask() & ENUM_MASK) != 0
        && getEnums().isEmpty()) {
      return false;
    }
    if ((getMask() & ENUM_MASK) == 0 && !getEnums().isEmpty()) {
      return false;
    }
    return ((getMask() & TYPEVAR_MASK) != 0) == (getTypeVar() != null);
  }

  static Map<String, JSType> createScalars(JSTypes commonTypes) {
    LinkedHashMap<String, JSType> types = new LinkedHashMap<>();

    types.put("BOOLEAN", new MaskType(commonTypes, TRUE_MASK | FALSE_MASK));
    types.put("BOTTOM", new MaskType(commonTypes, BOTTOM_MASK));
    types.put("FALSE_TYPE", new MaskType(commonTypes, FALSE_MASK));
    types.put("FALSY", new MaskType(commonTypes, FALSY_MASK));
    types.put("NULL", new MaskType(commonTypes, NULL_MASK));
    types.put("NUMBER", new MaskType(commonTypes, NUMBER_MASK));
    types.put("STRING", new MaskType(commonTypes, STRING_MASK));
    types.put("TOP", new MaskType(commonTypes, TOP_MASK));
    types.put("TOP_SCALAR", new MaskType(commonTypes, TOP_SCALAR_MASK));
    types.put("TRUE_TYPE", new MaskType(commonTypes, TRUE_MASK));
    types.put("TRUTHY", new MaskType(commonTypes, TRUTHY_MASK));
    types.put("UNDEFINED", new MaskType(commonTypes, UNDEFINED_MASK));
    types.put("UNKNOWN", new MaskType(commonTypes, UNKNOWN_MASK));

    types.put("UNDEFINED_OR_BOOLEAN", new MaskType(commonTypes, UNDEFINED_OR_BOOLEAN_MASK));
    types.put("UNDEFINED_OR_NUMBER", new MaskType(commonTypes, UNDEFINED_OR_NUMBER_MASK));
    types.put("UNDEFINED_OR_STRING", new MaskType(commonTypes, UNDEFINED_OR_STRING_MASK));
    types.put("NULL_OR_BOOLEAN", new MaskType(commonTypes, NULL_OR_BOOLEAN_MASK));
    types.put("NULL_OR_NUMBER", new MaskType(commonTypes, NULL_OR_NUMBER_MASK));
    types.put("NULL_OR_STRING", new MaskType(commonTypes, NULL_OR_STRING_MASK));
    types.put("NULL_OR_UNDEFINED", new MaskType(commonTypes, NULL_MASK | UNDEFINED_MASK));
    types.put("NUMBER_OR_STRING", new MaskType(commonTypes, NUMBER_MASK | STRING_MASK));

    return types;
  }

  @Override
  public boolean isTop() {
    return TOP_MASK == getMask();
  }

  @Override
  public boolean isBottom() {
    return BOTTOM_MASK == getMask();
  }

  public boolean isUndefined() {
    return UNDEFINED_MASK == getMask();
  }

  public boolean isUnknown() {
    return UNKNOWN_MASK == getMask();
  }

  public boolean isTrueOrTruthy() {
    return TRUTHY_MASK == getMask() || TRUE_MASK == getMask();
  }

  private boolean hasTruthyMask() {
    return TRUTHY_MASK == getMask();
  }

  public boolean isFalseOrFalsy() {
    return FALSY_MASK == getMask() || FALSE_MASK == getMask();
  }

  // Ignoring enums for simplicity
  public boolean isAnyTruthyType() {
    int mask = getMask();
    int truthyMask = TRUTHY_MASK | TRUE_MASK | NON_SCALAR_MASK;
    return mask != BOTTOM_MASK && (mask | truthyMask) == truthyMask;
  }

  // Ignoring enums for simplicity
  public boolean isAnyFalsyType() {
    int mask = getMask();
    int falsyMask = FALSY_MASK | FALSE_MASK | NULL_MASK | UNDEFINED_MASK;
    return mask != BOTTOM_MASK && (mask | falsyMask) == falsyMask;
  }

  private boolean hasFalsyMask() {
    return FALSY_MASK == getMask();
  }

  public boolean isBoolean() {
    return (getMask() & ~BOOLEAN_MASK) == 0 && (getMask() & BOOLEAN_MASK) != 0;
  }

  public boolean isString() {
    return STRING_MASK == getMask();
  }

  public boolean isNumber() {
    return NUMBER_MASK == getMask();
  }

  public boolean isNullOrUndef() {
    int nullUndefMask = NULL_MASK | UNDEFINED_MASK;
    return getMask() != 0 && (getMask() | nullUndefMask) == nullUndefMask;
  }

  public boolean isScalar() {
    return getMask() == NUMBER_MASK
        || getMask() == STRING_MASK
        || getMask() == NULL_MASK
        || getMask() == UNDEFINED_MASK
        || isBoolean();
  }

  // True iff there exists a value that can have this type
  private static boolean isInhabitable(Set<ObjectType> objs) {
    for (ObjectType obj : objs) {
      if (!obj.isInhabitable()) {
        return false;
      }
    }
    return true;
  }

  JSTypes getCommonTypes() {
    return this.commonTypes;
  }

  boolean hasScalar() {
    return (getMask() & TOP_SCALAR_MASK) != 0 || EnumType.hasScalar(getEnums());
  }

  public boolean hasNonScalar() {
    return !getObjs().isEmpty() || EnumType.hasNonScalar(getEnums());
  }

  @Override
  public boolean isNullable() {
    return !isTop() && (getMask() & NULL_MASK) != 0;
  }

  @Override
  public boolean isTypeVariable() {
    return getMask() == TYPEVAR_MASK;
  }

  public boolean hasTypeVariable() {
    return (getMask() & TYPEVAR_MASK) != 0;
  }

  public boolean isStruct() {
    if (isUnknown()) {
      return false;
    }
    Preconditions.checkState(!getObjs().isEmpty(),
        "Expected object type but found %s", this);
    for (ObjectType objType : getObjs()) {
      if (!objType.isStruct()) {
        return false;
      }
    }
    return true;
  }

  public boolean mayBeStruct() {
    for (ObjectType objType : getObjs()) {
      if (objType.isStruct()) {
        return true;
      }
    }
    return false;
  }

  public boolean isStructWithoutProp(QualifiedName pname) {
    for (ObjectType obj : getObjs()) {
      if (obj.isStruct() && !obj.mayHaveProp(pname)) {
        return true;
      }
    }
    return false;
  }

  public boolean isLoose() {
    ImmutableSet<ObjectType> objs = getObjs();
    return objs.size() == 1 && Iterables.getOnlyElement(objs).isLoose();
  }

  public boolean isDict() {
    if (isUnknown()) {
      return false;
    }
    Preconditions.checkState(!getObjs().isEmpty());
    for (ObjectType objType : getObjs()) {
      if (!objType.isDict()) {
        return false;
      }
    }
    return true;
  }

  // Returns null if this type doesn't inherit from IObject
  public JSType getIndexType() {
    if (getMask() != NON_SCALAR_MASK) {
      return null;
    }
    // This (union) type is a supertype of all indexed types in the union.
    // Different from NominalType#getIndexType, which uses join.
    JSType result = this.commonTypes.TOP;
    // We need this because the index type may explicitly be TOP.
    boolean foundIObject = false;
    for (ObjectType objType : getObjs()) {
      JSType tmp = objType.getNominalType().getIndexType();
      if (tmp == null) {
        return null;
      }
      foundIObject = true;
      result = meet(result, tmp);
    }
    return foundIObject ? result : null;
  }

  // May be called for types that include non-objects, and we ignore the
  // non-object parts in those cases.
  public JSType getIndexedType() {
    if ((getMask() & NON_SCALAR_MASK) == 0) {
      return null;
    }
    JSType result = this.commonTypes.BOTTOM;
    for (ObjectType objType : getObjs()) {
      JSType tmp = objType.getNominalType().getIndexedType();
      if (tmp == null) {
        return null;
      }
      result = join(result, tmp);
    }
    return result.isBottom() ? null : result;
  }

  public boolean mayBeDict() {
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
        || isScalar() || isTypeVariable() || isEnumElement()
        || hasTruthyMask() || hasFalsyMask()) {
      return false;
    }
    return !(getMask() == NON_SCALAR_MASK && getObjs().size() == 1);
  }

  public boolean isFunctionWithProperties() {
    ObjectType obj = getObjTypeIfSingletonObj();
    return obj != null && obj.isFunctionWithProperties();
  }

  public boolean isNamespace() {
    ObjectType obj = getObjTypeIfSingletonObj();
    return obj != null && obj.isNamespace();
  }

  // Only makes sense for a JSType that represents a single enum
  public JSType getEnumeratedType() {
    return isEnumElement() ?
        Iterables.getOnlyElement(getEnums()).getEnumeratedType() : null;
  }

  public JSType autobox() {
    if (isTop() || isUnknown()) {
      return this;
    }
    int mask = getMask();
    if ((mask & (NUMBER_MASK | STRING_MASK | BOOLEAN_MASK | ENUM_MASK)) == BOTTOM_MASK) {
      return this;
    }
    switch (mask) {
      case NUMBER_MASK:
        return this.commonTypes.getNumberInstance();
      case BOOLEAN_MASK:
      case TRUE_MASK:
      case FALSE_MASK:
        return this.commonTypes.getBooleanInstance();
      case STRING_MASK:
        return this.commonTypes.getStringInstance();
    }
    // For each set bit, add the corresponding obj to the new objs
    // construct and return the new type.
    // Don't bother autoboxing enums.
    ImmutableSet.Builder<ObjectType> builder = ImmutableSet.builder();
    builder.addAll(getObjs());
    if ((mask & NUMBER_MASK) != 0) {
      builder.add(this.commonTypes.getNumberInstanceObjType());
    }
    if ((mask & STRING_MASK) != 0) {
      builder.add(this.commonTypes.getStringInstanceObjType());
    }
    if ((mask & BOOLEAN_MASK) != 0) { // may have truthy or falsy
      builder.add(this.commonTypes.getBooleanInstanceObjType());
    }
    JSType result = makeType(
        this.commonTypes,
        mask & ~(NUMBER_MASK | STRING_MASK | BOOLEAN_MASK),
        builder.build(), getTypeVar(), NO_ENUMS);
    for (EnumType e : getEnums()) {
      result = join(result, e.getEnumeratedType().autobox());
    }
    return result;
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
    Preconditions.checkNotNull(lhs);
    Preconditions.checkNotNull(rhs);
    JSTypes commonTypes = lhs.commonTypes;
    if (lhs.isTop() || rhs.isTop()) {
      return commonTypes.TOP;
    }
    if (lhs.isUnknown() || rhs.isUnknown()) {
      return commonTypes.UNKNOWN;
    }
    if (lhs.isBottom()) {
      return rhs;
    }
    if (rhs.isBottom()) {
      return lhs;
    }
    if (lhs.hasTruthyMask() || lhs.hasFalsyMask()
        || rhs.hasTruthyMask() || rhs.hasFalsyMask()) {
      return commonTypes.UNKNOWN;
    }
    if (lhs.getTypeVar() != null && rhs.getTypeVar() != null
        && !lhs.getTypeVar().equals(rhs.getTypeVar())) {
      // For now return ? when joining two type vars. This is probably uncommon.
      return commonTypes.UNKNOWN;
    }

    int newMask = lhs.getMask() | rhs.getMask();
    ImmutableSet<ObjectType> newObjs =
        ObjectType.joinSets(lhs.getObjs(), rhs.getObjs());
    String newTypevar =
        lhs.getTypeVar() != null ? lhs.getTypeVar() : rhs.getTypeVar();
    ImmutableSet<EnumType> newEnums =
        EnumType.union(lhs.getEnums(), rhs.getEnums());
    if (newEnums.isEmpty()) {
      return makeType(commonTypes, newMask, newObjs, newTypevar, NO_ENUMS);
    }
    JSType tmpJoin =
        makeType(commonTypes, newMask & ~ENUM_MASK, newObjs, newTypevar, NO_ENUMS);
    return makeType(commonTypes, newMask, newObjs,
        newTypevar, EnumType.normalizeForJoin(newEnums, tmpJoin));
  }

  public JSType substituteGenerics(Map<String, JSType> concreteTypes) {
    if (isTop()
        || isUnknown()
        || getObjs().isEmpty() && getTypeVar() == null
        || concreteTypes.isEmpty()) {
      return this;
    }
    ImmutableSet.Builder<ObjectType> builder = ImmutableSet.builder();
    for (ObjectType obj : getObjs()) {
      builder.add(obj.substituteGenerics(concreteTypes));
    }
    JSType current = makeType(
        this.commonTypes, getMask() & ~TYPEVAR_MASK, builder.build(), null, getEnums());
    if (hasTypeVariable()) {
      current = JSType.join(current, concreteTypes.containsKey(getTypeVar()) ?
          concreteTypes.get(getTypeVar()) : fromTypeVar(this.commonTypes, getTypeVar()));
    }
    return current;
  }

  public JSType substituteGenericsWithUnknown() {
    return substituteGenerics(this.commonTypes.MAP_TO_UNKNOWN);
  }

  private static void updateTypemap(
      Multimap<String, JSType> typeMultimap,
      String typeParam, JSType type) {
    Preconditions.checkNotNull(type);
    Set<JSType> typesToRemove = new LinkedHashSet<>();
    for (JSType other : typeMultimap.get(typeParam)) {
      if (type.isUnknown()) {
        typesToRemove.add(other);
        continue;
      }
      if (other.isUnknown()) {
        type = null;
        break;
      }
      // The only way to instantiate with a loose type is if there are no
      // concrete types available. We may miss some warnings this way but we
      // also avoid false positives.
      if (type.isLoose()) {
        type = null;
        break;
      } else if (other.isLoose()) {
        typesToRemove.add(other);
        continue;
      }
      JSType unified = unifyUnknowns(type, other);
      if (unified != null) {
        // Can't remove elms while iterating over the collection, so do it later
        typesToRemove.add(other);
        type = unified;
      } else if (other.isSubtypeOf(type, SubtypeCache.create())) {
        typesToRemove.add(other);
      } else if (type.isSubtypeOf(other, SubtypeCache.create())) {
        type = null;
        break;
      }
    }
    for (JSType typeToRemove : typesToRemove) {
      typeMultimap.remove(typeParam, typeToRemove);
    }
    if (type != null) {
      typeMultimap.put(typeParam, type);
    }
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
    Preconditions.checkNotNull(t1);
    Preconditions.checkNotNull(t2);
    if (t1.isUnknown() || t1.isLoose()) {
      return t2;
    } else if (t2.isUnknown() || t2.isLoose()) {
      return t1;
    } else if (t1.isTop() && t2.isTop()) {
      return t1.commonTypes.TOP;
    } else if (t1.isTop() || t2.isTop()) {
      return null;
    }

    if (!t1.getEnums().equals(t2.getEnums())) {
      return null;
    }
    ImmutableSet<EnumType> newEnums = t1.getEnums();

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

    Set<ObjectType> ununified = new LinkedHashSet<>(t2.getObjs());
    Set<ObjectType> unifiedObjs = new LinkedHashSet<>();
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
    return makeType(t1.commonTypes, t1Mask,
        ImmutableSet.copyOf(unifiedObjs), t1.getTypeVar(), newEnums);
  }

  /**
   * Unify {@code this}, which may contain free type variables,
   * with {@code other}, a concrete subtype, modifying the supplied
   * {@code typeMultimap} to add any new template variable type bindings.
   * Note that if {@code this} is a union type, some of the union members may
   * be ignored if they are not present in {@code other}.
   * @return Whether unification succeeded
   *
   * This method should only be called outside the newtypes package;
   * classes inside the package should use unifyWithSubtype.
   */
  public boolean unifyWith(JSType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap) {
    return unifyWithSubtype(
        other, typeParameters, typeMultimap, SubtypeCache.create());
  }

  boolean unifyWithSubtype(JSType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap, SubtypeCache subSuperMap) {
    Preconditions.checkNotNull(other);
    if (this.isUnknown() || this.isTop()) {
      return true;
    } else if (getMask() == TYPEVAR_MASK
        && typeParameters.contains(getTypeVar())) {
      updateTypemap(typeMultimap, getTypeVar(), other);
      return true;
    } else if (other.isUnknown() || other.isTrueOrTruthy()) {
      return true;
    } else if (other.isTop()) {
      // T|number doesn't unify with TOP
      return false;
    }

    Set<EnumType> ununifiedEnums = ImmutableSet.of();
    if (!other.getEnums().isEmpty()) {
      ununifiedEnums = new LinkedHashSet<>();
      for (EnumType e : other.getEnums()) {
        if (!fromEnum(e).isSubtypeOf(this, SubtypeCache.create())) {
          ununifiedEnums.add(e);
        }
      }
    }

    Set<ObjectType> ununifiedObjs = new LinkedHashSet<>(other.getObjs());
    // We don't check that two different objects of this don't unify
    // with the same other type.
    // Fancy cases are unfortunately iteration-order dependent, eg,
    // Foo<number>|Foo<string> may or may not unify with Foo<T>|Foo<string>
    for (ObjectType targetObj : getObjs()) {
      for (ObjectType sourceObj : other.getObjs()) {
        if (targetObj.unifyWithSubtype(
              sourceObj, typeParameters, typeMultimap, subSuperMap)) {
          ununifiedObjs.remove(sourceObj);
        }
      }
    }

    String thisTypevar = getTypeVar();
    String otherTypevar = other.getTypeVar();
    if (thisTypevar == null || !typeParameters.contains(thisTypevar)) {
      return ununifiedObjs.isEmpty() && ununifiedEnums.isEmpty()
          && (otherTypevar == null || otherTypevar.equals(thisTypevar))
          && getMask() == (getMask() | (other.getMask() & ~ENUM_MASK));
    } else {
      // this is (T | ...)
      int thisScalarBits = getMask() & ~NON_SCALAR_MASK & ~TYPEVAR_MASK;
      int templateMask = other.getMask() & ~thisScalarBits;
      if (ununifiedObjs.isEmpty()) {
        templateMask &= ~NON_SCALAR_MASK;
      }

      if (templateMask == BOTTOM_MASK) {
        // nothing left in other to assign to thisTypevar, so don't update typemap
        return ununifiedObjs.isEmpty() && ununifiedEnums.isEmpty();
      }
      JSType templateType = makeType(
          this.commonTypes,
          promoteBoolean(templateMask),
          ImmutableSet.copyOf(ununifiedObjs),
          otherTypevar, ImmutableSet.copyOf(ununifiedEnums));
      updateTypemap(typeMultimap, getTypeVar(), templateType);
      return true;
    }
  }

  public JSType specialize(JSType other) {
    JSType t = specializeHelper(other);
    if (t.isBottom() && (isLoose() || other.isLoose())) {
      t = autobox().specializeHelper(other.autobox());
      // If the autoboxed specialization is not null, this means that one of
      // the two types contains scalars that when autoboxed are compatible with
      // the loose object in the other type. In this case, don't return bottom,
      // just leave the type unspecialized.
      if (!t.isBottom()) {
        return this;
      }
    }
    if (t.isLoose()) {
      JSType maybeScalar = ObjectType.mayTurnLooseObjectToScalar(t, this.commonTypes);
      if (t != maybeScalar) { // ref equality on purpose
        return maybeScalar;
      }
    }
    return t;
  }

  private JSType specializeHelper(JSType other) {
    if (other.isTop() || other.isUnknown() || this == other) {
      return this;
    }
    if (other.hasTruthyMask()) {
      return makeTruthy();
    }
    if (hasTruthyMask()) {
      // If the only thing we know about this type is that it's truthy, that's very
      // little information, so we loosen the other type to avoid spurious warnings.
      JSType otherTruthy = other.makeTruthy();
      return otherTruthy.hasNonScalar()
          ? otherTruthy.withLoose()
          : otherTruthy;
    }
    if (other.hasFalsyMask()) {
      return makeFalsy();
    }
    if (hasFalsyMask()) {
      return other.makeFalsy();
    }
    if (this.isTop()) {
      return other;
    }
    if (this.isUnknown()) {
      NominalType otherNt = other.getNominalTypeIfSingletonObj();
      return otherNt != null && otherNt.isBuiltinObject() ? other.withLoose() : other;
    }
    int newMask = getMask() & other.getMask();
    String newTypevar;
    if (Objects.equals(getTypeVar(), other.getTypeVar())) {
      newTypevar = getTypeVar();
    } else if (getTypeVar() != null && other.getTypeVar() == null) {
      // Consider, e.g., function f(/** T|number */ x) { if (typeof x === 'string') { ... } }
      // We want to specialize the T to string, rather than going to bottom.
      return other;
    } else {
      newTypevar = null;
      newMask &= ~TYPEVAR_MASK;
    }
    return meetEnums(
        this.commonTypes, newMask, getMask() | other.getMask(),
        ObjectType.specializeSet(getObjs(), other.getObjs()),
        newTypevar, getObjs(), other.getObjs(), getEnums(), other.getEnums());
  }

  public static JSType meet(JSType lhs, JSType rhs) {
    JSType t = meetHelper(lhs, rhs);
    if (t.isBottom() && (lhs.isLoose() || rhs.isLoose())) {
      t = meetHelper(lhs.autobox(), rhs.autobox());
      // If the autoboxed meet is not null, this means that one of
      // the two types contains scalars that when autoboxed are compatible with
      // the loose object in the other type. In this case, don't return bottom,
      // use some heuristic to return a result.
      if (!t.isBottom()) {
        if (!lhs.isLoose()) {
          return lhs;
        } else {
          // If this fails, find repro case, add a test, and return ? here.
          Preconditions.checkState(!rhs.isLoose(), "Two loose types %s and %s"
              + " that meet to bottom, meet to non-bottom after autoboxing.",
              lhs.toString(), rhs.toString());
          return rhs;
        }
      }
    }
    return t;
  }

  private static JSType meetHelper(JSType lhs, JSType rhs) {
    if (lhs.isTop()) {
      return rhs;
    }
    if (rhs.isTop()) {
      return lhs;
    }
    if (lhs.isUnknown()) {
      return rhs;
    }
    if (rhs.isUnknown()) {
      return lhs;
    }
    if (lhs.isBottom() || rhs.isBottom()) {
      return lhs.commonTypes.BOTTOM;
    }
    if (lhs.hasTruthyMask()) {
      return rhs.makeTruthy();
    }
    if (lhs.hasFalsyMask()) {
      return rhs.makeFalsy();
    }
    if (rhs.hasTruthyMask()) {
      return lhs.makeTruthy();
    }
    if (rhs.hasFalsyMask()) {
      return lhs.makeFalsy();
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
        lhs.commonTypes, newMask, lhs.getMask() | rhs.getMask(),
        ObjectType.meetSets(lhs.getObjs(), rhs.getObjs()),
        newTypevar, lhs.getObjs(), rhs.getObjs(),
        lhs.getEnums(), rhs.getEnums());
  }

  /**
   * Both {@code meet} and {@code specialize} do the same computation for enums.
   * They don't just compute the set of enums; they may modify mask and objs.
   * So, both methods finish off by calling this one.
   */
  private static JSType meetEnums(JSTypes commonTypes, int newMask, int unionMask,
      ImmutableSet<ObjectType> newObjs, String newTypevar,
      ImmutableSet<ObjectType> objs1, ImmutableSet<ObjectType> objs2,
      ImmutableSet<EnumType> enums1, ImmutableSet<EnumType> enums2) {
    if (Objects.equals(enums1, enums2)) {
      return makeType(commonTypes, newMask, newObjs, newTypevar, enums1);
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
      } else if (!objs1.isEmpty() || !objs2.isEmpty()) {
        Set<ObjectType> objsToRemove = new LinkedHashSet<>();
        ObjectType enumObj = Iterables.getOnlyElement(enumeratedType.getObjs());
        for (ObjectType obj1 : objs1) {
          if (enumObj.isSubtypeOf(obj1, SubtypeCache.create())) {
            enumBuilder.add(e);
            objsToRemove.add(obj1);
          }
        }
        for (ObjectType obj2 : objs2) {
          if (enumObj.isSubtypeOf(obj2, SubtypeCache.create())) {
            enumBuilder.add(e);
            objsToRemove.add(obj2);
          }
        }
        if (!objsToRemove.isEmpty()) {
          newObjs = Sets.difference(newObjs, objsToRemove).immutableCopy();
        }
      }
    }
    return makeType(commonTypes, newMask, newObjs, newTypevar, enumBuilder.build());
  }

  public static boolean haveCommonSubtype(JSType lhs, JSType rhs) {
    return lhs.isBottom() || rhs.isBottom() || !meet(lhs, rhs).isBottom();
  }

  private JSType makeTruthy() {
    if (this.isTop()) {
      return this;
    }
    if (this.isUnknown()) {
      return this.commonTypes.TRUTHY;
    }
    return makeType(this.commonTypes,
        getMask() & ~NULL_MASK & ~FALSE_MASK & ~UNDEFINED_MASK,
        getObjs(), getTypeVar(), getEnums());
  }

  private JSType makeFalsy() {
    if (this.isTop()) {
      return this;
    }
    if (this.isUnknown()) {
      return this.commonTypes.FALSY;
    }
    return makeType(this.commonTypes,
        getMask() & ~TRUE_MASK & ~NON_SCALAR_MASK, NO_OBJS, getTypeVar(), getEnums());
  }

  public static JSType plus(JSType lhs, JSType rhs) {
    JSTypes commonTypes = lhs.commonTypes;
    if (!lhs.isUnknown() && !lhs.isBottom() && lhs.isSubtypeOf(commonTypes.STRING)
        || !rhs.isUnknown() && !rhs.isBottom() && rhs.isSubtypeOf(commonTypes.STRING)) {
      return commonTypes.STRING;
    }
    if (lhs.isUnknown() || lhs.isTop() || rhs.isUnknown() || rhs.isTop()) {
      return commonTypes.UNKNOWN;
    }
    // If either has string, string in the result.
    int newtype = (lhs.getMask() | rhs.getMask()) & STRING_MASK;
    // If both have non-string (including boolean, null, undefined),
    // number in the result.
    if ((lhs.getMask() & ~STRING_MASK) != 0
        && (rhs.getMask() & ~STRING_MASK) != 0) {
      newtype |= NUMBER_MASK;
    }
    return makeType(lhs.commonTypes, newtype);
  }

  public JSType negate() {
    if (isTop() || isUnknown()) {
      return this;
    }
    if (isTrueOrTruthy()) {
      return this.commonTypes.FALSY;
    } else if (isFalseOrFalsy()) {
      return this.commonTypes.TRUTHY;
    }
    return this.commonTypes.UNKNOWN;
  }

  public JSType toBoolean() {
    if (isTrueOrTruthy()) {
      return this.commonTypes.TRUE_TYPE;
    } else if (isFalseOrFalsy()) {
      return this.commonTypes.FALSE_TYPE;
    }
    return this.commonTypes.BOOLEAN;
  }

  public boolean isNonLooseSubtypeOf(JSType other) {
    return isSubtypeOfHelper(false, other, SubtypeCache.create(), null);
  }

  @Override
  public boolean isSubtypeOf(TypeI other) {
    return isSubtypeOf(other, SubtypeCache.create());
  }

  public static MismatchInfo whyNotSubtypeOf(JSType t1, JSType t2) {
    if (t1.isSingletonObj() && t2.isSingletonObj()) {
      MismatchInfo[] boxedInfo = new MismatchInfo[1];
      ObjectType.whyNotSubtypeOf(
          t1.getObjTypeIfSingletonObj(),
          t2.getObjTypeIfSingletonObj(),
          boxedInfo);
      return boxedInfo[0];
    }
    if (t1.isUnion()) {
      MismatchInfo[] boxedInfo = new MismatchInfo[1];
      boolean areSubtypes =
          t1.isSubtypeOfHelper(true, t2, SubtypeCache.create(), boxedInfo);
      Preconditions.checkState(!areSubtypes);
      return boxedInfo[0];
    }
    return null;
  }

  boolean isSubtypeOf(TypeI other, SubtypeCache subSuperMap) {
    if (this == other) {
      return true;
    }
    JSType type2 = (JSType) other;
    if (isLoose() || type2.isLoose()) {
      return this.commonTypes.looseSubtypingForLooseObjects
          ? haveCommonSubtype(autobox(), type2.autobox())
          : autobox().isSubtypeOfHelper(true, type2.autobox(), subSuperMap, null);
    } else {
      return isSubtypeOfHelper(true, type2, subSuperMap, null);
    }
  }

  private boolean isSubtypeOfHelper(
      boolean keepLoosenessOfThis, JSType other, SubtypeCache subSuperMap,
      MismatchInfo[] boxedInfo) {
    if (isUnknown() || other.isUnknown() || other.isTop()) {
      return true;
    }
    if (hasTruthyMask()) {
      return !other.makeTruthy().isBottom();
    }
    if (hasFalsyMask()) {
      return !other.makeFalsy().isBottom();
    }
    if (!EnumType.areSubtypes(this, other, subSuperMap)) {
      return false;
    }
    int mask = getMask() & ~ENUM_MASK;
    if ((mask | other.getMask()) != other.getMask()) {
      if (boxedInfo != null && isUnion()) {
        whyNotUnionSubtypes(this, other, boxedInfo);
      }
      return false;
    }
    if (getTypeVar() != null && !getTypeVar().equals(other.getTypeVar())) {
      return false;
    }
    if (getObjs().isEmpty()) {
      return true;
    }
    // Because of optional properties,
    //   x \le y \iff x \join y = y does not hold.
    boolean result = ObjectType.isUnionSubtype(
        keepLoosenessOfThis, getObjs(), other.getObjs(), subSuperMap);
    if (boxedInfo != null) {
      ObjectType.whyNotUnionSubtypes(
          keepLoosenessOfThis, getObjs(), other.getObjs(),
          subSuperMap, boxedInfo);
    }
    return result;
  }

  private static void whyNotUnionSubtypes(
      JSType found, JSType expected, MismatchInfo[] boxedInfo) {
    JSTypes commonTypes = found.commonTypes;
    if (commonTypes.NUMBER.isSubtypeOf(found) && !commonTypes.NUMBER.isSubtypeOf(expected)) {
      boxedInfo[0] = MismatchInfo.makeUnionTypeMismatch(commonTypes.NUMBER);
    } else if (commonTypes.STRING.isSubtypeOf(found)
        && !commonTypes.STRING.isSubtypeOf(expected)) {
      boxedInfo[0] = MismatchInfo.makeUnionTypeMismatch(commonTypes.STRING);
    } else if (commonTypes.BOOLEAN.isSubtypeOf(found)
        && !commonTypes.BOOLEAN.isSubtypeOf(expected)) {
      boxedInfo[0] = MismatchInfo.makeUnionTypeMismatch(commonTypes.BOOLEAN);
    } else if (commonTypes.NULL.isSubtypeOf(found)
        && !commonTypes.NULL.isSubtypeOf(expected)) {
      boxedInfo[0] = MismatchInfo.makeUnionTypeMismatch(commonTypes.NULL);
    } else if (commonTypes.UNDEFINED.isSubtypeOf(found)
        && !commonTypes.UNDEFINED.isSubtypeOf(expected)) {
      boxedInfo[0] = MismatchInfo.makeUnionTypeMismatch(commonTypes.UNDEFINED);
    } else if (found.hasTypeVariable() && !expected.hasTypeVariable()) {
      boxedInfo[0] = MismatchInfo.makeUnionTypeMismatch(
          fromTypeVar(found.commonTypes, found.getTypeVar()));
    } else if ((found.getMask() & NON_SCALAR_MASK) != 0
        && (expected.getMask() & NON_SCALAR_MASK) == 0) {
      boxedInfo[0] = MismatchInfo.makeUnionTypeMismatch(makeType(
          found.commonTypes, NON_SCALAR_MASK, found.getObjs(), null, NO_ENUMS));
    }
  }

  public JSType removeType(JSType other) {
    int otherMask = other.getMask();
    Preconditions.checkState(
        !other.isTop() && !other.isUnknown()
        && (otherMask & TYPEVAR_MASK) == 0 && (otherMask & ENUM_MASK) == 0,
        "Requested invalid type to remove: %s", other);
    if (isUnknown()) {
      return this;
    }
    if (isTop()) {
      JSType almostTop = makeType(
          commonTypes,
          TRUE_MASK | FALSE_MASK | NUMBER_MASK | STRING_MASK
          | NULL_MASK | UNDEFINED_MASK | NON_SCALAR_MASK,
          ImmutableSet.of(this.commonTypes.TOP_OBJECTTYPE), null, NO_ENUMS);
      return almostTop.removeType(other);
    }
    int newMask = getMask() & ~otherMask;
    if ((otherMask & NON_SCALAR_MASK) == 0) {
      return newMask == getMask()
          ? this : makeType(this.commonTypes, newMask, getObjs(), getTypeVar(), getEnums());
    }
    // TODO(dimvar): If objs and enums stay unchanged, reuse, don't recreate.
    Preconditions.checkState(other.getObjs().size() == 1,
        "Invalid type to remove: %s", other);
    ObjectType otherObj = Iterables.getOnlyElement(other.getObjs());
    ImmutableSet.Builder<ObjectType> objsBuilder = ImmutableSet.builder();
    for (ObjectType obj : getObjs()) {
      if (!obj.isSubtypeOf(otherObj, SubtypeCache.create())) {
        objsBuilder.add(obj);
      }
    }
    ImmutableSet.Builder<EnumType> enumBuilder = ImmutableSet.builder();
    for (EnumType e : getEnums()) {
      if (!e.getEnumeratedType().isSubtypeOf(other, SubtypeCache.create())) {
        enumBuilder.add(e);
      }
    }
    return makeType(
       this.commonTypes, newMask, objsBuilder.build(), getTypeVar(), enumBuilder.build());
  }

  // Adds ft to this type, replacing the current function, if any.
  public JSType withFunction(FunctionType ft, NominalType fnNominal) {
    // This method is used for a very narrow purpose, hence these checks.
    Preconditions.checkNotNull(ft);
    Preconditions.checkState(this.isNamespace());
    return fromObjectType(
        getObjTypeIfSingletonObj().withFunction(ft, fnNominal));
  }

  public static String createGetterPropName(String originalPropName) {
    return "%getter_fun" + originalPropName;
  }

  public static String createSetterPropName(String originalPropName) {
    return "%setter_fun" + originalPropName;
  }

  public boolean isSingletonObj() {
    return getMask() == NON_SCALAR_MASK && getObjs().size() == 1;
  }

  boolean isSingletonObjWithNull() {
    return getMask() == (NON_SCALAR_MASK | NULL_MASK) && getObjs().size() == 1;
  }

  ObjectType getObjTypeIfSingletonObj() {
    return isSingletonObj() ? Iterables.getOnlyElement(getObjs()) : null;
  }

  public FunctionType getFunTypeIfSingletonObj() {
    ObjectType obj = getObjTypeIfSingletonObj();
    return obj == null ? null : obj.getFunType();
  }

  public FunctionType getFunType() {
    for (ObjectType obj : getObjs()) {
      FunctionType ft = obj.getFunType();
      if (ft != null) {
        return ft;
      }
    }
    return null;
  }

  public NominalType getNominalTypeIfSingletonObj() {
    return isSingletonObj()
        ? Iterables.getOnlyElement(getObjs()).getNominalType() : null;
  }

  public boolean isInterfaceInstance() {
    NominalType nt = getNominalTypeIfSingletonObj();
    return nt != null && nt.isInterface();
  }

  // True for functions and instances of Object (including object literals).
  public boolean isNonClassyObject() {
    NominalType nt = getNominalTypeIfSingletonObj();
    return nt != null && !nt.isClassy();
  }

  public boolean isInterfaceDefinition() {
    FunctionType ft = getFunTypeIfSingletonObj();
    return ft != null && ft.isInterfaceDefinition();
  }

  /** Turns the class-less object of this type (if any) into a loose object */
  public JSType withLoose() {
    if (getObjs().isEmpty()) {
      Preconditions.checkState(!getEnums().isEmpty());
      return this;
    }
    return makeType(this.commonTypes, getMask(),
        ObjectType.withLooseObjects(getObjs()), getTypeVar(), getEnums());
  }

  public JSType getProp(QualifiedName qname) {
    if (isBottom() || isUnknown() || hasTruthyMask()) {
      return this.commonTypes.UNKNOWN;
    }
    Preconditions.checkState(!getObjs().isEmpty() || !getEnums().isEmpty(),
        "Can't getProp %s of type %s", qname, this);
    return nullAcceptingJoin(
        TypeWithPropertiesStatics.getProp(getObjs(), qname),
        TypeWithPropertiesStatics.getProp(getEnums(), qname));
  }

  public JSType getDeclaredProp(QualifiedName qname) {
    if (isUnknown()) {
      return this.commonTypes.UNKNOWN;
    }
    Preconditions.checkState(!getObjs().isEmpty() || !getEnums().isEmpty());
    return nullAcceptingJoin(
        TypeWithPropertiesStatics.getDeclaredProp(getObjs(), qname),
        TypeWithPropertiesStatics.getDeclaredProp(getEnums(), qname));
  }

  public boolean mayHaveProp(QualifiedName qname) {
    return TypeWithPropertiesStatics.mayHaveProp(getObjs(), qname) ||
        TypeWithPropertiesStatics.mayHaveProp(getEnums(), qname);
  }

  public boolean hasProp(QualifiedName qname) {
    if (!getObjs().isEmpty()
        && !TypeWithPropertiesStatics.hasProp(getObjs(), qname)) {
      return false;
    }
    if (!getEnums().isEmpty()
        && !TypeWithPropertiesStatics.hasProp(getEnums(), qname)) {
      return false;
    }
    return !getEnums().isEmpty() || !getObjs().isEmpty();
  }

  public boolean hasConstantProp(QualifiedName pname) {
    Preconditions.checkArgument(pname.isIdentifier());
    return TypeWithPropertiesStatics.hasConstantProp(getObjs(), pname) ||
        TypeWithPropertiesStatics.hasConstantProp(getEnums(), pname);
  }

  @Override
  public boolean containsArray() {
    ObjectType arrayType = this.commonTypes.getArrayInstance().getObjTypeIfSingletonObj();
    Preconditions.checkNotNull(arrayType);
    for (ObjectType objType : this.getObjs()) {
      if (objType.isSubtypeOf(arrayType, SubtypeCache.create())) {
        return true;
      }
    }
    return false;
  }

  public JSType withoutProperty(QualifiedName qname) {
    return getObjs().isEmpty() ?
        this :
        makeType(this.commonTypes, getMask(),
            ObjectType.withoutProperty(getObjs(), qname), getTypeVar(), getEnums());
  }

  public JSType withProperty(QualifiedName qname, JSType type) {
    Preconditions.checkArgument(type != null);
    if (isUnknown() || isBottom() || getObjs().isEmpty()) {
      return this;
    }
    return makeType(this.commonTypes, getMask(),
        ObjectType.withProperty(getObjs(), qname, type), getTypeVar(), getEnums());
  }

  public JSType withDeclaredProperty(
      QualifiedName qname, JSType type, boolean isConstant) {
    Preconditions.checkState(!getObjs().isEmpty());
    if (type == null && isConstant) {
      type = this.commonTypes.UNKNOWN;
    }
    return makeType(this.commonTypes,
        getMask(),
        ObjectType.withDeclaredProperty(getObjs(), qname, type, isConstant), getTypeVar(), getEnums());
  }

  public JSType withPropertyRequired(String pname) {
    return (isUnknown() || getObjs().isEmpty()) ?
        this :
        makeType(this.commonTypes, getMask(),
            ObjectType.withPropertyRequired(getObjs(), pname), getTypeVar(), getEnums());
  }

  // For a type A, this method tries to return the greatest subtype of A that
  // has a property called pname. If it can't safely find a subtype, it
  // returns bottom.
  public JSType findSubtypeWithProp(QualifiedName pname) {
    Preconditions.checkArgument(pname.isIdentifier());
    // common cases first
    if (isTop() || isUnknown() || (getMask() & NON_SCALAR_MASK) == 0) {
      return this.commonTypes.BOTTOM;
    }
    // For simplicity, if this type has scalars with pname, return bottom.
    // If it has enums, return bottom.
    if (this.commonTypes.NUMBER.isSubtypeOf(this)
        && this.commonTypes.getNumberInstance().mayHaveProp(pname)
        || this.commonTypes.STRING.isSubtypeOf(this)
        && this.commonTypes.getNumberInstance().mayHaveProp(pname)
        || this.commonTypes.BOOLEAN.isSubtypeOf(this)
        && this.commonTypes.getBooleanInstance().mayHaveProp(pname)) {
      return this.commonTypes.BOTTOM;
    }
    if ((getMask() & ENUM_MASK) != 0) {
      return this.commonTypes.BOTTOM;
    }
    // NOTE(dimvar): Nothing for type variables for now, but we will have to
    // handle them here when we implement bounded generics.
    if (getObjs().size() == 1) {
      ObjectType obj = Iterables.getOnlyElement(getObjs());
      return obj.mayHaveProp(pname) ? this : this.commonTypes.BOTTOM;
    }
    ImmutableSet.Builder<ObjectType> builder = ImmutableSet.builder();
    boolean foundObjWithProp = false;
    for (ObjectType o : getObjs()) {
      if (o.mayHaveProp(pname)) {
        foundObjWithProp = true;
        builder.add(o);
      }
    }
    return foundObjWithProp
        ? makeType(this.commonTypes, NON_SCALAR_MASK, builder.build(), null, NO_ENUMS)
        : this.commonTypes.BOTTOM;
  }

  public boolean isPropDefinedOnSubtype(QualifiedName pname) {
    Preconditions.checkArgument(pname.isIdentifier());
    for (ObjectType obj : getObjs()) {
      if (obj.isPropDefinedOnSubtype(pname)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    if (mockToString) {
      return "";
    }
    return appendTo(new StringBuilder()).toString();
  }

  public StringBuilder appendTo(StringBuilder builder) {
    return typeToString(builder);
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
                builder.append(UniqueNameGenerator.getOriginalName(getTypeVar()));
                tags &= ~TYPEVAR_MASK;
                continue;
              case NON_SCALAR_MASK: {
                if (getObjs().size() == 1) {
                  Iterables.getOnlyElement(getObjs()).appendTo(builder);
                } else {
                  Set<String> strReps = new TreeSet<>();
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
                  builder.append(Iterables.getOnlyElement(getEnums()));
                } else {
                  Set<String> strReps = new TreeSet<>();
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
          return builder.append("Unrecognized type: ").append(tags);
        }
    }
  }

  @Override
  public boolean isConstructor() {
    FunctionType ft = getFunTypeIfSingletonObj();
    return ft != null && ft.isUniqueConstructor();
  }

  @Override
  public boolean isEquivalentTo(TypeI type) {
    return equals(type);
  }

  @Override
  public boolean isFunctionType() {
    return getFunTypeIfSingletonObj() != null;
  }

  @Override
  public boolean isInterface() {
    return isInterfaceDefinition();
  }

  @Override
  public boolean isUnknownType() {
    return isUnknown();
  }

  @Override
  public boolean isSomeUnknownType() {
    FunctionType ft = this.getFunTypeIfSingletonObj();
    return isUnknown()
        || (isUnknownObject() && isLoose())
        || (ft != null && ft.isTopFunction());
  }

  @Override
  public boolean isUnresolved() {
    // TODO(aravindpg): This is purely a stub to ensure we never get into a codepath that
    // depends on us being an unresolved type. We currently do not mark unresolved types as such
    // in NTI since the main use-case (warning for unfulfilled forward declares) can be
    // handled differently (by warning after GTI), so we don't want to change the type system.
    return false;
  }

  @Override
  public boolean isUnresolvedOrResolvedUnknown() {
    return isUnknown();
  }

  @Override
  public boolean isUnionType() {
    return isUnion();
  }

  @Override
  public boolean isVoidable() {
    return !isTop() && (getMask() & UNDEFINED_MASK) != 0;
  }

  @Override
  public boolean isNullType() {
    return equals(this.commonTypes.NULL);
  }

  @Override
  public boolean isVoidType() {
    return equals(this.commonTypes.UNDEFINED);
  }

  @Override
  public TypeI restrictByNotNullOrUndefined() {
    return this.removeType(this.commonTypes.NULL_OR_UNDEFINED);
  }

  @Override
  public FunctionTypeI toMaybeFunctionType() {
    return isFunctionType() ? this : null;
  }

  @Override
  public ObjectTypeI toMaybeObjectType() {
    return isSingletonObj() ? this : null;
  }

  @Override
  public ObjectTypeI autoboxAndGetObject() {
    return this.autobox().restrictByNotNullOrUndefined().toMaybeObjectType();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (this == o) {
      return true;
    }
    Preconditions.checkArgument(o instanceof JSType);
    JSType t2 = (JSType) o;
    return getMask() == t2.getMask() && Objects.equals(getObjs(), t2.getObjs())
        && Objects.equals(getEnums(), t2.getEnums())
        && Objects.equals(getTypeVar(), t2.getTypeVar());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getMask(), getObjs(), getEnums(), getTypeVar());
  }

  @Override
  public String getDisplayName() {
    // TODO(aravindpg): could implement in a more sophisticated way.
    // One particular pain point is that we currently return the object literal representation of
    // prototype objects instead of something more readable such as "Foo.prototype". But this is
    // difficult to fix since we don't represent prototype objects in any special way.
    NominalType nt = getNominalTypeIfSingletonObj();
    // Prefer just the class name to the name bundled with all its properties.
    if (nt != null && nt.isClassy()) {
      return nt.toString();
    }
    return toString();
  }

  @Override
  public TypeI convertMethodToFunction() {
    throw new UnsupportedOperationException("convertMethodToFunction not implemented yet");
  }

  @Override
  public boolean hasInstanceType() {
    Preconditions.checkState(this.isFunctionType());
    return getFunTypeIfSingletonObj().getInstanceTypeOfCtor() != null;
  }

  @Override
  public ObjectTypeI getInstanceType() {
    Preconditions.checkState(this.isFunctionType());
    JSType instanceType = getFunTypeIfSingletonObj().getInstanceTypeOfCtor();
    return instanceType == null ? null : instanceType.toMaybeObjectType();
  }

  @Override
  public String getReferenceName() {
    throw new UnsupportedOperationException("getReferenceName not implemented yet");
  }

  @Override
  public Node getSource() {
    if (isConstructor()) {
      JSType instance = getFunTypeIfSingletonObj().getInstanceTypeOfCtor();
      return instance.getNominalTypeIfSingletonObj().getDefSite();
    }
    return this.isSingletonObj()
        ? getNominalTypeIfSingletonObj().getDefSite()
        : null;
  }

  @Override
  public List<? extends FunctionTypeI> getSubTypes() {
    throw new UnsupportedOperationException("getSubTypes not implemented yet");
  }

  @Override
  public TypeI getTypeOfThis() {
    Preconditions.checkState(this.isFunctionType());
    return getFunTypeIfSingletonObj().getThisType();
  }

  @Override
  public boolean acceptsArguments(List<? extends TypeI> argumentTypes) {
    Preconditions.checkState(this.isFunctionType());

    int numArgs = argumentTypes.size();
    FunctionType fnType = this.getFunTypeIfSingletonObj();

    if (numArgs < fnType.getMinArity() || numArgs > fnType.getMaxArity()) {
      return false;
    }

    for (int i = 0; i < numArgs; i++) {
      TypeI ithArgType = argumentTypes.get(i);
      JSType ithParamType = fnType.getFormalType(i);
      if (!ithArgType.isSubtypeOf(ithParamType)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean hasProperties() {
    throw new UnsupportedOperationException("hasProperties not implemented yet");
  }

  @Override
  public void setSource(Node n) {
    throw new UnsupportedOperationException("setSource not implemented yet");
  }

  @Override
  public TypeI getReturnType() {
    Preconditions.checkState(this.isFunctionType());
    return getFunTypeIfSingletonObj().getReturnType();
  }

  @Override
  public FunctionTypeI getConstructor() {
    Preconditions.checkState(this.isSingletonObj());
    FunctionType ctorType = this.getNominalTypeIfSingletonObj().getConstructorFunction();
    return this.commonTypes.fromFunctionType(ctorType);
  }

  @Override
  public FunctionTypeI getSuperClassConstructor() {
    ObjectTypeI proto = getPrototypeObject();
    return proto == null ? null : proto.getConstructor();
  }

  @Override
  public JSType getPrototypeObject() {
    Preconditions.checkState(this.isSingletonObj());
    JSType proto = getNominalTypeIfSingletonObj().getPrototypePropertyOfCtor();
    if (this.equals(proto)) {
      // In JS's dynamic semantics, the only object without a __proto__ is
      // Object.prototype, but it's not representable in NTI.
      // Object.prototype is the only case where we are equal to our own prototype.
      // In this case, we should return null.
      Preconditions.checkState(
          this.isUnknownObject(),
          "Failed to reach Object.prototype in prototype chain, unexpected self-link found at %s",
          this);
      return null;
    }
    return proto;
  }

  @Override
  public JSDocInfo getJSDocInfo() {
    return getSource() == null ? null : NodeUtil.getBestJSDocInfo(getSource());
  }

  @Override
  public JSDocInfo getOwnPropertyJSDocInfo(String propertyName) {
    Node defsite = this.getOwnPropertyDefSite(propertyName);
    return defsite == null ? null : NodeUtil.getBestJSDocInfo(defsite);
  }

  @Override
  public JSDocInfo getPropertyJSDocInfo(String propertyName) {
    Node defsite = this.getPropertyDefSite(propertyName);
    return defsite == null ? null : NodeUtil.getBestJSDocInfo(defsite);
  }

  @Override
  public Node getOwnPropertyDefSite(String propertyName) {
    Preconditions.checkState(this.isSingletonObj());
    return this.getObjTypeIfSingletonObj().getOwnPropertyDefSite(propertyName);
  }

  @Override
  public Node getPropertyDefSite(String propertyName) {
    Preconditions.checkState(this.isSingletonObj());
    return this.getObjTypeIfSingletonObj().getPropertyDefSite(propertyName);
  }

  /** Returns the names of all the properties directly on this type. */
  @Override
  public Iterable<String> getOwnPropertyNames() {
    Preconditions.checkState(this.isSingletonObj());
    // TODO(aravindpg): this might need to also include the extra properties as stored in the
    // ObjectType::props. If so, demonstrate a test case that needs it and fix this.
    Set<String> props = getNominalTypeIfSingletonObj().getAllOwnClassProps();
    return props;
  }

  @Override
  public boolean isPrototypeObject() {
    // TODO(aravindpg): this is just a complete stub to ensure that we never enter a codepath
    // that depends on us being a prototype object.
    return false;
  }

  public boolean isUnknownObject() {
    return isSingletonObj() && getNominalTypeIfSingletonObj().isBuiltinObject();
  }

  @Override
  public boolean isInstanceofObject() {
    return isSingletonObj() && getNominalTypeIfSingletonObj().isLiteralObject();
  }

  public boolean mayContainUnknownObject() {
    for (ObjectType obj : this.getObjs()) {
      if (obj.getNominalType().isBuiltinObject()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isInstanceType() {
    Preconditions.checkState(this.isSingletonObj());
    return this.getNominalTypeIfSingletonObj().isClassy();
  }

  @Override
  public boolean hasProperty(String propertyName) {
    Preconditions.checkState(this.isSingletonObj());
    Preconditions.checkArgument(!propertyName.contains("."));
    return hasProp(new QualifiedName(propertyName));
  }

  @Override
  public Iterable<TypeI> getUnionMembers() {
    ImmutableSet.Builder<TypeI> builder = ImmutableSet.builder();
    JSType[] primitiveTypes = {
        this.commonTypes.BOOLEAN,
        this.commonTypes.NUMBER,
        this.commonTypes.STRING,
        this.commonTypes.UNDEFINED,
        this.commonTypes.NULL };
    for (JSType primitiveType : primitiveTypes) {
      if ((this.getMask() & primitiveType.getMask()) != 0) {
        builder.add(primitiveType);
      }
    }
    for (ObjectType obj : this.getObjs()) {
      builder.add(JSType.fromObjectType(obj));
    }
    for (EnumType e : this.getEnums()) {
      builder.add(JSType.fromEnum(e));
    }
    if (this.getTypeVar() != null) {
      builder.add(JSType.fromTypeVar(this.commonTypes, getTypeVar()));
    }
    return builder.build();
  }

  @Override
  public ObjectTypeI normalizeObjectForCheckAccessControls() {
    if (isSingletonObj()) {
      FunctionTypeI ctor = getConstructor();
      if (ctor != null) {
        return ctor.getInstanceType();
      }
    }
    return this;
  }
}

final class UnionType extends JSType {
  private final int mask;
  // objs is empty for scalar types
  private final ImmutableSet<ObjectType> objs;
  // typeVar is null for non-generic types
  private final String typeVar;
  // enums is empty for types that don't have enums
  private final ImmutableSet<EnumType> enums;

  UnionType(JSTypes commonTypes, int mask, ImmutableSet<ObjectType> objs,
      String typeVar, ImmutableSet<EnumType> enums) {
    super(commonTypes);
    this.enums = Preconditions.checkNotNull(enums);
    this.objs = Preconditions.checkNotNull(objs);

    if (typeVar != null) {
      mask |= TYPEVAR_MASK;
    }

    this.typeVar = typeVar;
    this.mask = mask;

    if (!isValidType()) {
      throw new IllegalStateException(SimpleFormat.format(
          "Cannot create type with bits <<<%x>>>, "
          + "objs <<<%s>>>, typeVar <<<%s>>>, enums <<<%s>>>",
          mask, objs, typeVar, enums));
    }
  }

  @Override
  protected int getMask() {
    return mask;
  }

  @Override
  protected ImmutableSet<ObjectType> getObjs() {
    return Preconditions.checkNotNull(objs);
  }

  @Override
  protected String getTypeVar() {
    return typeVar;
  }

  @Override
  protected ImmutableSet<EnumType> getEnums() {
    return Preconditions.checkNotNull(enums);
  }
}

class MaskType extends JSType {
  protected final int mask;

  MaskType(JSTypes commonTypes, int mask) {
    super(commonTypes);
    this.mask = mask;
  }

  @Override
  protected int getMask() {
    return mask;
  }

  @Override
  protected ImmutableSet<ObjectType> getObjs() {
    return ImmutableSet.of();
  }

  @Override
  protected String getTypeVar() {
    return null;
  }

  @Override
  protected ImmutableSet<EnumType> getEnums() {
    return ImmutableSet.of();
  }
}

final class ObjsType extends JSType {
  private ImmutableSet<ObjectType> objs;

  ObjsType(JSTypes commonTypes, ImmutableSet<ObjectType> objs) {
    super(commonTypes);
    this.objs = Preconditions.checkNotNull(objs);
  }

  @Override
  protected int getMask() {
    return NON_SCALAR_MASK;
  }

  @Override
  protected ImmutableSet<ObjectType> getObjs() {
    return objs;
  }

  @Override
  protected String getTypeVar() {
    return null;
  }

  @Override
  protected ImmutableSet<EnumType> getEnums() {
    return ImmutableSet.of();
  }
}

final class NullableObjsType extends JSType {
  private ImmutableSet<ObjectType> objs;

  NullableObjsType(JSTypes commonTypes, ImmutableSet<ObjectType> objs) {
    super(commonTypes);
    this.objs = Preconditions.checkNotNull(objs);
  }

  @Override
  protected int getMask() {
    return NON_SCALAR_MASK | NULL_MASK;
  }

  @Override
  protected ImmutableSet<ObjectType> getObjs() {
    return objs;
  }

  @Override
  protected String getTypeVar() {
    return null;
  }

  @Override
  protected ImmutableSet<EnumType> getEnums() {
    return ImmutableSet.of();
  }
}
