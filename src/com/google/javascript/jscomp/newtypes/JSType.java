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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.FunctionTypeI;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.TypeI;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
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
  private static final int BOTTOM_MASK = 0x0;
  private static final int TYPEVAR_MASK = 0x1;
  private static final int NON_SCALAR_MASK = 0x2;
  private static final int ENUM_MASK = 0x4;
  // The less important use case for TRUE_MASK and FALSE_MASK is to type the
  // values true and false precisely. But people don't write: if (true) {...}
  // More importantly, these masks come up as the negation of TRUTHY_MASK and
  // FALSY_MASK when the ! operator is used.
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

  /**
   * The unresolved type is useful for per-library type checking. This type is neither a subtype
   * nor a supertype of any other type. In effect, if you try to use it, you get a warning.
   * To avoid flowing this type around and having to change all type operations to recognize it,
   * if an expression evaluates to this type, we warn and return ? instead.
   */
  private static final int UNRESOLVED_MASK = 0x6fffffff;
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

  private JSType(JSTypes commonTypes) {
    checkNotNull(commonTypes);
    this.commonTypes = commonTypes;
  }

  private static JSType makeType(JSTypes commonTypes,
      int mask, ImmutableSet<ObjectType> objs,
      String typeVar, ImmutableSet<EnumType> enums) {
    // Fix up the mask for objects and enums
    if (checkNotNull(enums).isEmpty()) {
      mask &= ~ENUM_MASK;
    } else {
      mask |= ENUM_MASK;
    }

    if (checkNotNull(objs).isEmpty()) {
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
      case UNRESOLVED_MASK:
        return commonTypes.UNRESOLVED;
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

  final boolean isValidType() {
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
    types.put("UNRESOLVED", new MaskType(commonTypes, UNRESOLVED_MASK));

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
  public final boolean isTop() {
    return TOP_MASK == getMask();
  }

  @Override
  public final boolean isBottom() {
    return BOTTOM_MASK == getMask();
  }

  public final boolean isUndefined() {
    return UNDEFINED_MASK == getMask();
  }

  public final boolean isUnknown() {
    return UNKNOWN_MASK == getMask();
  }

  public final boolean isTrueOrTruthy() {
    return TRUTHY_MASK == getMask() || TRUE_MASK == getMask();
  }

  private boolean isTheTruthyType() {
    return TRUTHY_MASK == getMask();
  }

  private boolean isTheTrueType() {
    return TRUE_MASK == getMask();
  }

  public final boolean isFalseOrFalsy() {
    return FALSY_MASK == getMask() || FALSE_MASK == getMask();
  }

  // Ignoring enums for simplicity
  public final boolean isAnyTruthyType() {
    int mask = getMask();
    int truthyMask = TRUTHY_MASK | TRUE_MASK | NON_SCALAR_MASK;
    return mask != BOTTOM_MASK && (mask | truthyMask) == truthyMask;
  }

  // Ignoring enums for simplicity
  public final boolean isAnyFalsyType() {
    int mask = getMask();
    int falsyMask = FALSY_MASK | FALSE_MASK | NULL_MASK | UNDEFINED_MASK;
    return mask != BOTTOM_MASK && (mask | falsyMask) == falsyMask;
  }

  private boolean isTheFalsyType() {
    return FALSY_MASK == getMask();
  }

  private boolean isTheFalseType() {
    return FALSE_MASK == getMask();
  }

  public final boolean isBoolean() {
    return (getMask() & ~BOOLEAN_MASK) == 0 && (getMask() & BOOLEAN_MASK) != 0;
  }

  @Override
  public final boolean isBooleanValueType() {
    return isBoolean();
  }

  public final boolean isString() {
    return STRING_MASK == getMask();
  }

  @Override
  public final boolean isStringValueType() {
    return isString();
  }

  public final boolean isNumber() {
    return NUMBER_MASK == getMask();
  }

  @Override
  public final boolean isNumberValueType() {
    return isNumber();
  }

  public final boolean isNullOrUndef() {
    int nullUndefMask = NULL_MASK | UNDEFINED_MASK;
    return getMask() != 0 && (getMask() | nullUndefMask) == nullUndefMask;
  }

  public final boolean isScalar() {
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

  final JSTypes getCommonTypes() {
    return this.commonTypes;
  }

  final boolean hasScalar() {
    return (getMask() & TOP_SCALAR_MASK) != 0 || EnumType.hasScalar(getEnums());
  }

  public final boolean hasNonScalar() {
    return !getObjs().isEmpty() || EnumType.hasNonScalar(getEnums());
  }

  @Override
  public final boolean isNullable() {
    return !isTop() && (getMask() & NULL_MASK) != 0;
  }

  @Override
  public final boolean isTypeVariable() {
    return getMask() == TYPEVAR_MASK;
  }

  public final boolean hasTypeVariable() {
    return (getMask() & TYPEVAR_MASK) != 0;
  }

  public final boolean isStruct() {
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

  public final boolean mayBeStruct() {
    for (ObjectType objType : getObjs()) {
      if (objType.isStruct()) {
        return true;
      }
    }
    return false;
  }

  public final boolean isStructWithoutProp(QualifiedName pname) {
    for (ObjectType obj : getObjs()) {
      if (obj.isStruct() && !obj.mayHaveProp(pname)) {
        return true;
      }
    }
    return false;
  }

  public final boolean isLoose() {
    ImmutableSet<ObjectType> objs = getObjs();
    return objs.size() == 1 && Iterables.getOnlyElement(objs).isLoose();
  }

  public final boolean isDict() {
    if (isUnknown()) {
      return false;
    }
    checkState(!getObjs().isEmpty());
    for (ObjectType objType : getObjs()) {
      if (!objType.isDict()) {
        return false;
      }
    }
    return true;
  }

  // Returns null if this type doesn't inherit from IObject
  public final JSType getIndexType() {
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
  public final JSType getIndexedType() {
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

  public final boolean mayBeDict() {
    for (ObjectType objType : getObjs()) {
      if (objType.isDict()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public final boolean isEnumElement() {
    return getMask() == ENUM_MASK && getEnums().size() == 1;
  }

  @Override
  public final boolean isEnumObject() {
    ObjectType obj = getObjTypeIfSingletonObj();
    return obj != null && obj.isEnumObject();
  }

  @Override
  public final TypeI getEnumeratedTypeOfEnumObject() {
    return isEnumObject() ? getObjTypeIfSingletonObj().getEnumType().getEnumeratedType() : null;
  }

  public final boolean isUnion() {
    if (isBottom() || isTop() || isUnknown()
        || isScalar() || isTypeVariable() || isEnumElement()
        || isTheTruthyType() || isTheFalsyType()) {
      return false;
    }
    return !(getMask() == NON_SCALAR_MASK && getObjs().size() == 1);
  }

  public final boolean isFunctionWithProperties() {
    ObjectType obj = getObjTypeIfSingletonObj();
    return obj != null && obj.isFunctionWithProperties();
  }

  public final boolean isNamespace() {
    ObjectType obj = getObjTypeIfSingletonObj();
    return obj != null && obj.isNamespace();
  }

  // Only makes sense for a JSType that represents a single enum
  @Override
  public final JSType getEnumeratedTypeOfEnumElement() {
    return isEnumElement() ?
        Iterables.getOnlyElement(getEnums()).getEnumeratedType() : null;
  }

  @Override
  public final JSType autobox() {
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
      default:
        // Not a scalar type: handled below.
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

  static JSType joinManyTypes(JSTypes commonTypes, Iterable<JSType> types) {
    JSType result = commonTypes.BOTTOM;
    for (JSType t : types) {
      result = join(result, t);
    }
    return result;
  }

  // When joining w/ TOP or UNKNOWN, avoid setting more fields on them, e.g., obj.
  public static JSType join(JSType lhs, JSType rhs) {
    checkNotNull(lhs);
    checkNotNull(rhs);
    JSTypes commonTypes = lhs.commonTypes;
    if (lhs.isUnresolved() || rhs.isUnresolved()) {
      return commonTypes.UNRESOLVED;
    }
    if (lhs.isUnknown() || rhs.isUnknown()) {
      return commonTypes.UNKNOWN;
    }
    if (lhs.isTop() || rhs.isTop()) {
      return commonTypes.TOP;
    }
    if (lhs.isBottom()) {
      return rhs;
    }
    if (rhs.isBottom()) {
      return lhs;
    }
    if (lhs.isTheTruthyType() || lhs.isTheFalsyType()
        || rhs.isTheTruthyType() || rhs.isTheFalsyType()) {
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
    ImmutableSet<EnumType> newEnums = EnumType.union(lhs.getEnums(), rhs.getEnums());
    if (newEnums.isEmpty()) {
      return makeType(commonTypes, newMask, newObjs, newTypevar, NO_ENUMS);
    }
    JSType tmpJoin = makeType(commonTypes, newMask & ~ENUM_MASK, newObjs, newTypevar, NO_ENUMS);
    return makeType(
        commonTypes, newMask, newObjs, newTypevar, EnumType.normalizeForJoin(newEnums, tmpJoin));
  }

  /**
   * Creates a new JSType by deeply substituting the *free* type variables in this type with
   * concrete replacements from the given map.
   * This is also what all other substituteGenerics methods in this package do.
   *
   * Do not confuse with the instantiateGenerics methods, which take an *uninstantiated*
   * generic type (a nominal type or a generic function), and substitute the *bound* type variables
   * with the concrete types from the map.

   * For example, when you define a class Foo with T, an
   * uninstantiated Foo object has type: {@code ForAll T.Foo<T>}.
   * When you define a generic function f with U, that takes a {@code Foo<U>} as an argument and
   * returns a U, f's type is {@code ForAll U.Foo<U> => U}.
   * When you call f with some argument, say Foo of string, you instantiate U in f's type.
   * The part of f's type without the ForAll contains U as a free type variable.
   * So you call substituteGenerics in order to convert the {@code Foo<U>} to a {@code Foo<string>}
   */
  public final JSType substituteGenerics(Map<String, JSType> concreteTypes) {
    if (isTop()
        || isUnknown()
        || (getObjs().isEmpty() && getTypeVar() == null)
        || concreteTypes.isEmpty()) {
      return this;
    }
    // TODO(dimvar): By adding prints, I found that the majority of the time, when
    // we substitute generics in obj, it has no effect; the result is equal to the type
    // before substitution. I did some timing tests to estimate what the improvement
    // would be if we did better here, and they were inconclusive because of large variance
    // in the test runs. Intuitively though, it seems that we can save time here,
    // so revisit this in the future when I have time to dig in deeper.
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

  public final JSType substituteGenericsWithUnknown() {
    return substituteGenerics(this.commonTypes.MAP_TO_UNKNOWN);
  }

  @Override
  public JSType getInstantiatedTypeArgument(TypeI supertype) {
    RawNominalType rawType =
        ((JSType) supertype).getNominalTypeIfSingletonObj().getRawNominalType();
    List<String> typeParameters = rawType.getTypeParameters();
    checkState(typeParameters.size() == 1);

    String param = typeParameters.get(0);
    Map<String, JSType> typeMap = new LinkedHashMap<>();
    typeMap.put(param, JSType.fromTypeVar(this.commonTypes, param));

    JSType reinstantiated = rawType.getInstanceAsJSType().substituteGenerics(typeMap);
    Multimap<String, JSType> typeMultimap = LinkedHashMultimap.create();
    reinstantiated.unifyWith(this, typeParameters, typeMultimap);
    return joinManyTypes(this.commonTypes, typeMultimap.get(param));
  }

  private static void updateTypemap(
      Multimap<String, JSType> typeMultimap,
      String typeParam, JSType type) {
    checkNotNull(type);
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

  private final JSType promoteBoolean() {
    return isTheTrueType() || isTheFalseType() ? this.commonTypes.BOOLEAN : this;
  }

  private static int promoteBooleanMask(int mask) {
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
    checkNotNull(t1);
    checkNotNull(t2);
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

    int t1Mask = promoteBooleanMask(t1.getMask());
    int t2Mask = promoteBooleanMask(t2.getMask());
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
   *
   * This method should only be called outside the newtypes package;
   * classes inside the package should use unifyWithSubtype.
   *
   * Unification algorithm.
   *
   * Say that {@code this} is a potentially generic type G and {@code other} is a concrete type C.
   * 1. If C is not a union:
   *    (A) If C is a subtype of G, then unification succeeds and we don't update the type map.
   *        E.g., (T|string) unifies with string and we learn nothing about T.
   *    (B) If some member of G unifies with C, unification succeeds and we update the type map.
   *    (C) If 2+ members of G unify with C, we use each of them to update the type map. This is
   *        simpler than depending on the iteration order of the members of G to decide which one
   *        to unify with C.
   *        E.g., if Foo extends Bar and implements Baz,
   *        (Bar(T)|Baz(T)) unifies with Foo(number) and T is number.
   *        (Bar(number, T)|Baz(T, string)) doesn't unify with Foo(number,string), but each member
   *        unifies with C, and we get a "not unique instantiation" warning.
   *
   * 2. If C is a union:
   *    We throw away C's members that are subtypes of G, and for each remaining
   *    member we try to individually unify G with it. A single member of G may unify with
   *    2+ members of C.
   *    Let SubC be a type that contains all members of C that are left over: they are not subtypes
   *    of G and G doesn't unify with them. If G is of the form (T|...), then T is mapped to SubC.
   *    E.g., (T|Foo|Bar(U)) unifies with (number|string|Foo|Bar(Baz))
   *    SubC is (number|string), T is mapped to (number|string), and U is mapped to Baz.
   */
  public final void unifyWith(JSType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap) {
    unifyWithSubtype(other, typeParameters, typeMultimap, SubtypeCache.create());
  }

  /**
   * Unify this, which is a potentially generic type G, with other, a concrete type C.
   * Unifying means finding concrete types to substitute for the type variables in G and get a
   * type G', such that C is a subtype of G'.
   *
   * Doing unification of function types correctly also requires a unifyWithSupertype method.
   * See {@link FunctionType#unifyWithSubtype} for details. For now, we think that's an overkill.
   *
   * @param subSuperMap needed to avoid infinite recursion when unifying structural interfaces.
   * @return whether unification succeeded
   */
  final boolean unifyWithSubtype(JSType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap, SubtypeCache subSuperMap) {
    if (other.isUnknown()) {
      String thisTypevar = getTypeVar();
      if (thisTypevar != null && typeParameters.contains(thisTypevar)) {
        updateTypemap(typeMultimap, thisTypevar, other);
      }
      return true;
    }
    HashSet<JSType> leftovers = new HashSet<>();
    for (JSType otherMember : other.getUnionMembers()) {
      if (otherMember.isSubtypeOf(this)) {
        continue;
      }
      otherMember = otherMember.promoteBoolean();
      if (!unifyWithSingleType(otherMember, typeParameters, typeMultimap, subSuperMap)) {
        leftovers.add(otherMember);
      }
    }
    if (leftovers.isEmpty()) {
      return true;
    }
    String thisTypevar = getTypeVar();
    if (thisTypevar != null && typeParameters.contains(thisTypevar)) {
      updateTypemap(typeMultimap, thisTypevar, joinManyTypes(this.commonTypes, leftovers));
      return true;
    }
    return false;
  }

  /**
   * Unify other, which is NOT a union type, with this, which may be a union.
   * Note that if other is a primitive type then this method will always return false:
   * primitives may only be unified with a typevar as "leftovers".
   */
  final boolean unifyWithSingleType(JSType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap, SubtypeCache subSuperMap) {
    Preconditions.checkArgument(!other.isUnion(), "Expected non-union type but found: %s", other);
    if (other.isEnumElement()) {
      JSType enumType = other.getEnumeratedTypeOfEnumElement();
      return unifyWithSingleType(enumType, typeParameters, typeMultimap, subSuperMap);
    }
    if (other.isSingletonObj()) {
      ObjectType otherObj = other.getObjTypeIfSingletonObj();
      boolean result = false;
      for (ObjectType thisObj : getObjs()) {
        // Note that we want to execute thisObj.unifyWithSubtype(otherObj, ...) even if result
        // is already true, because several members of the generic type may unify with otherObj.
        if (thisObj.unifyWithSubtype(otherObj, typeParameters, typeMultimap, subSuperMap)) {
          result = true;
        }
      }
      return result;
    }
    return false;
  }

  public final JSType specialize(JSType other) {
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
    return t.isLoose() ? ObjectType.mayTurnLooseObjectToScalar(t, this.commonTypes) : t;
  }

  @SuppressWarnings("ReferenceEquality")
  private JSType specializeHelper(JSType other) {
    if (other.isTop() || other.isUnknown() || this == other) {
      return this;
    }
    if (other.isTheTruthyType()) {
      return makeTruthy();
    }
    if (isTheTruthyType()) {
      // If the only thing we know about this type is that it's truthy, that's very
      // little information, so we loosen the other type to avoid spurious warnings.
      JSType otherTruthy = other.makeTruthy();
      return otherTruthy.hasNonScalar()
          ? otherTruthy.withLoose()
          : otherTruthy;
    }
    if (other.isTheFalsyType()) {
      return makeFalsy();
    }
    if (isTheFalsyType()) {
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
    if (lhs.isTheTruthyType()) {
      return rhs.makeTruthy();
    }
    if (lhs.isTheFalsyType()) {
      return rhs.makeFalsy();
    }
    if (rhs.isTheTruthyType()) {
      return lhs.makeTruthy();
    }
    if (rhs.isTheFalsyType()) {
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
    if ((!lhs.isUnknown() && !lhs.isBottom() && lhs.isSubtypeOf(commonTypes.STRING))
        || (!rhs.isUnknown() && !rhs.isBottom() && rhs.isSubtypeOf(commonTypes.STRING))) {
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

  public final JSType negate() {
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

  public final JSType toBoolean() {
    if (isTrueOrTruthy()) {
      return this.commonTypes.TRUE_TYPE;
    } else if (isFalseOrFalsy()) {
      return this.commonTypes.FALSE_TYPE;
    }
    return this.commonTypes.BOOLEAN;
  }

  public final boolean isNonLooseSubtypeOf(JSType other) {
    return isSubtypeOfHelper(false, other, SubtypeCache.create(), null);
  }

  public static MismatchInfo whyNotSubtypeOf(JSType found, JSType expected) {
    if (found.isSingletonObj() && expected.isSingletonObj()) {
      MismatchInfo[] boxedInfo = new MismatchInfo[1];
      ObjectType.whyNotSubtypeOf(
          found.getObjTypeIfSingletonObj(),
          expected.getObjTypeIfSingletonObj(),
          boxedInfo);
      return boxedInfo[0];
    }
    if (found.isUnion()) {
      MismatchInfo[] boxedInfo = new MismatchInfo[1];
      boolean areSubtypes =
          found.isSubtypeOfHelper(true, expected, SubtypeCache.create(), boxedInfo);
      checkState(!areSubtypes);
      return boxedInfo[0];
    }
    return null;
  }

  @Override
  public final boolean isSubtypeOf(TypeI other) {
    return isSubtypeOf(other, SubtypeCache.create());
  }

  final boolean isSubtypeOf(TypeI other, SubtypeCache subSuperMap) {
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
    if (other.isUnresolved()) {
      return false;
    }
    if (isTheTruthyType()) {
      return !other.makeTruthy().isBottom();
    }
    if (isTheFalsyType()) {
      return !other.makeFalsy().isBottom();
    }
    if (other.isTheTruthyType()) {
      return isAnyTruthyType();
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

  public final JSType removeType(JSType other) {
    int otherMask = other.getMask();
    Preconditions.checkState(
        !other.isTop() && !other.isUnknown()
        && (otherMask & TYPEVAR_MASK) == 0 && (otherMask & ENUM_MASK) == 0,
        "Requested invalid type to remove: %s", other);
    if (isUnknown() || isUnresolved()) {
      return this;
    }
    if (isTop()) {
      JSType almostTop = makeType(
          commonTypes,
          TRUE_MASK | FALSE_MASK | NUMBER_MASK | STRING_MASK
          | NULL_MASK | UNDEFINED_MASK | NON_SCALAR_MASK,
          ImmutableSet.of(this.commonTypes.getTopObjectType()), null, NO_ENUMS);
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
      if (obj.isLoose() || !obj.isSubtypeOf(otherObj, SubtypeCache.create())) {
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
  public final JSType withFunction(FunctionType ft, NominalType fnNominal) {
    // This method is used for a very narrow purpose, hence these checks.
    checkNotNull(ft);
    checkState(this.isNamespace());
    return fromObjectType(
        getObjTypeIfSingletonObj().withFunction(ft, fnNominal));
  }

  public final boolean isSingletonObj() {
    return getMask() == NON_SCALAR_MASK && getObjs().size() == 1;
  }

  final boolean isSingletonObjWithNull() {
    return getMask() == (NON_SCALAR_MASK | NULL_MASK) && getObjs().size() == 1;
  }

  final ObjectType getObjTypeIfSingletonObj() {
    return isSingletonObj() ? Iterables.getOnlyElement(getObjs()) : null;
  }

  public final FunctionType getFunTypeIfSingletonObj() {
    ObjectType obj = getObjTypeIfSingletonObj();
    return obj == null ? null : obj.getFunType();
  }

  public final FunctionType getFunType() {
    for (ObjectType obj : getObjs()) {
      FunctionType ft = obj.getFunType();
      if (ft != null) {
        return ft;
      }
    }
    return null;
  }

  public final NominalType getNominalTypeIfSingletonObj() {
    return isSingletonObj()
        ? Iterables.getOnlyElement(getObjs()).getNominalType() : null;
  }

  public final boolean isInterfaceInstance() {
    NominalType nt = getNominalTypeIfSingletonObj();
    return nt != null && nt.isInterface();
  }

  // True for functions and instances of Object (including object literals).
  public final boolean isNonClassyObject() {
    NominalType nt = getNominalTypeIfSingletonObj();
    return nt != null && !nt.isClassy();
  }

  public final boolean isIObject() {
    NominalType nt = getNominalTypeIfSingletonObj();
    return nt != null && nt.isIObject();
  }

  public final boolean isInterfaceDefinition() {
    FunctionType ft = getFunTypeIfSingletonObj();
    return ft != null && ft.isInterfaceDefinition();
  }

  /** Turns the class-less object of this type (if any) into a loose object */
  public final JSType withLoose() {
    if (getObjs().isEmpty()) {
      checkState(!getEnums().isEmpty());
      return this;
    }
    // TODO(dimvar): here, a lot of the time the set of objects will only contain objects for which
    // withLoose is a no-op. Worth it to detect it and return `this` in that case, to avoid
    // unnecessary creation of types?
    ImmutableSet.Builder<ObjectType> looseObjs = ImmutableSet.builder();
    for (ObjectType obj : getObjs()) {
      looseObjs.add(obj.withLoose());
    }
    return makeType(this.commonTypes, getMask(), looseObjs.build(), getTypeVar(), getEnums());
  }

  public final JSType getProp(QualifiedName qname) {
    if (isBottom() || isUnknown() || isTheTruthyType()) {
      return this.commonTypes.UNKNOWN;
    }
    Preconditions.checkState(!getObjs().isEmpty() || !getEnums().isEmpty(),
        "Can't getProp %s of type %s", qname, this);
    return nullAcceptingJoin(
        TypeWithPropertiesStatics.getProp(getObjs(), qname),
        TypeWithPropertiesStatics.getProp(getEnums(), qname));
  }

  public final JSType getDeclaredProp(QualifiedName qname) {
    if (isUnknown()) {
      return this.commonTypes.UNKNOWN;
    }
    checkState(!getObjs().isEmpty() || !getEnums().isEmpty());
    return nullAcceptingJoin(
        TypeWithPropertiesStatics.getDeclaredProp(getObjs(), qname),
        TypeWithPropertiesStatics.getDeclaredProp(getEnums(), qname));
  }

  public final boolean mayHaveProp(QualifiedName qname) {
    return TypeWithPropertiesStatics.mayHaveProp(getObjs(), qname) ||
        TypeWithPropertiesStatics.mayHaveProp(getEnums(), qname);
  }

  public final boolean hasProp(QualifiedName qname) {
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

  public final boolean hasConstantProp(QualifiedName pname) {
    checkArgument(pname.isIdentifier());
    return TypeWithPropertiesStatics.hasConstantProp(getObjs(), pname) ||
        TypeWithPropertiesStatics.hasConstantProp(getEnums(), pname);
  }

  @Override
  public final boolean containsArray() {
    ObjectType arrayType = this.commonTypes.getArrayInstance().getObjTypeIfSingletonObj();
    checkNotNull(arrayType);
    for (ObjectType objType : this.getObjs()) {
      if (objType.isSubtypeOf(arrayType, SubtypeCache.create())) {
        return true;
      }
    }
    return false;
  }

  public final JSType withoutProperty(QualifiedName qname) {
    return getObjs().isEmpty() ?
        this :
        makeType(this.commonTypes, getMask(),
            ObjectType.withoutProperty(getObjs(), qname), getTypeVar(), getEnums());
  }

  public final JSType withProperty(QualifiedName qname, JSType type) {
    checkArgument(type != null);
    if (isUnknown() || isBottom() || getObjs().isEmpty()) {
      return this;
    }
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : getObjs()) {
      newObjs.add(obj.withProperty(qname, type));
    }
    return makeType(this.commonTypes, getMask(), newObjs.build(), getTypeVar(), getEnums());
  }

  public final JSType withDeclaredProperty(
      QualifiedName qname, JSType type, boolean isConstant) {
    checkState(!getObjs().isEmpty());
    if (type == null && isConstant) {
      type = this.commonTypes.UNKNOWN;
    }
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : getObjs()) {
      newObjs.add(obj.withDeclaredProperty(qname, type, isConstant));
    }
    return makeType(this.commonTypes, getMask(), newObjs.build(), getTypeVar(), getEnums());
  }

  public final JSType withPropertyRequired(String pname) {
    if (isUnknown() || getObjs().isEmpty()) {
      return this;
    }
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : getObjs()) {
      newObjs.add(obj.withPropertyRequired(pname));
    }
    return makeType(this.commonTypes, getMask(), newObjs.build(), getTypeVar(), getEnums());
  }

  // For a type A, this method tries to return the greatest subtype of A that
  // has a property called pname. If it can't safely find a subtype, it
  // returns bottom.
  public final JSType findSubtypeWithProp(QualifiedName pname) {
    checkArgument(pname.isIdentifier());
    // common cases first
    if (isTop() || isUnknown() || (getMask() & NON_SCALAR_MASK) == 0) {
      return this.commonTypes.BOTTOM;
    }
    // For simplicity, if this type has scalars with pname, return bottom.
    // If it has enums, return bottom.
    if ((this.commonTypes.NUMBER.isSubtypeOf(this)
            && this.commonTypes.getNumberInstance().mayHaveProp(pname))
        || (this.commonTypes.STRING.isSubtypeOf(this)
            && this.commonTypes.getNumberInstance().mayHaveProp(pname))
        || (this.commonTypes.BOOLEAN.isSubtypeOf(this)
            && this.commonTypes.getBooleanInstance().mayHaveProp(pname))) {
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

  public final boolean isPropDefinedOnSubtype(QualifiedName pname) {
    checkArgument(pname.isIdentifier());
    for (ObjectType obj : getObjs()) {
      if (obj.isPropDefinedOnSubtype(pname)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public final String toString() {
    if (mockToString) {
      return "";
    }
    return appendTo(new StringBuilder(), ToStringContext.TO_STRING).toString();
  }

  public final String toString(ToStringContext ctx) {
    return appendTo(new StringBuilder(), ctx).toString();
  }

  /** For use in {@link #appendTo} */
  private static final Joiner PIPE_JOINER = Joiner.on("|");

  /**
   * Appends this type to the `builder`. If `forAnnotations` is true, then
   * the type will be in a format suitable for type annotations during
   * code generation.
   */
  StringBuilder appendTo(StringBuilder builder, ToStringContext ctx) {
    // TODO(sdh): checkState(!forAnnotations) all cases that don't work for annotations.
    boolean isUnion = isUnion();
    switch (getMask()) {
      case BOTTOM_MASK:
        return builder.append("bottom");
      case TOP_MASK:
        return builder.append("*");
      case UNKNOWN_MASK:
        return builder.append("?");
      case UNRESOLVED_MASK:
        return builder.append("unresolved");
      default:
        if (isUnion) {
          builder.append("(");
        }
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
                builder.append(
                    (tags & BOOLEAN_MASK) == BOOLEAN_MASK
                        ? "boolean"
                        : tag == TRUE_MASK
                        ? "true"
                        : "false");
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
                builder.append(ctx.formatTypeVar(getTypeVar()));
                tags &= ~TYPEVAR_MASK;
                continue;
              case NON_SCALAR_MASK: {
                boolean isNullable = isNullable();
                if (getObjs().size() == 1 && !isNullable) {
                  Iterables.getOnlyElement(getObjs()).appendTo(builder, ctx);
                } else {
                  Set<String> strReps = new TreeSet<>();
                  for (ObjectType obj : getObjs()) {
                    String rep = obj.toString(ctx);
                    if (isNullable && rep.charAt(0) == '!') {
                      rep = rep.substring(1);
                    }
                    strReps.add(rep);
                  }
                  PIPE_JOINER.appendTo(builder, strReps);
                }
                tags &= ~NON_SCALAR_MASK;
                continue;
              }
              case ENUM_MASK: {
                if (getEnums().size() == 1) {
                  builder.append(Iterables.getOnlyElement(getEnums()).toString());
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
              default:
                throw new AssertionError("Impossible: " + tag);
            }
          }
        }
        if (tags == 0) { // Found all types in the union
          // nothing else to do
        } else if (tags == TRUTHY_MASK) {
          builder.append("truthy");
        } else if (tags == FALSY_MASK) {
          builder.append("falsy");
        } else {
          builder.append("Unrecognized type: ").append(tags);
        }
        if (isUnion) {
          builder.append(")");
        }
        return builder;
    }
  }

  @Override
  public final String toAnnotationString(Nullability nullability) {
    String result = appendTo(new StringBuilder(), ToStringContext.FOR_ANNOTATION).toString();
    return nullability == Nullability.IMPLICIT ? result.replaceAll("^!", "") : result;
  }

  @Override
  public final boolean isConstructor() {
    FunctionType ft = getFunTypeIfSingletonObj();
    return ft != null && ft.isUniqueConstructor();
  }

  @Override
  public final boolean isEquivalentTo(TypeI type) {
    return equals(type);
  }

  @Override
  public final boolean isFunctionType() {
    return getFunTypeIfSingletonObj() != null;
  }

  @Override
  public final boolean isInterface() {
    return isInterfaceDefinition();
  }

  @Override
  public final boolean isUnknownType() {
    return isUnknown();
  }

  @Override
  public final boolean isSomeUnknownType() {
    FunctionType ft = this.getFunTypeIfSingletonObj();
    return isUnknown()
        || (isAmbiguousObject() && isLoose())
        || (ft != null && ft.isTopFunction());
  }

  @Override
  public final boolean isUnresolved() {
    return getMask() == UNRESOLVED_MASK;
  }

  @Override
  public final boolean isUnresolvedOrResolvedUnknown() {
    return isUnknown();
  }

  @Override
  public final boolean isUnionType() {
    return isUnion();
  }

  @Override
  public final boolean isVoidable() {
    return !isTop() && (getMask() & UNDEFINED_MASK) != 0;
  }

  @Override
  public final boolean isNullType() {
    return equals(this.commonTypes.NULL);
  }

  @Override
  public final boolean isVoidType() {
    return equals(this.commonTypes.UNDEFINED);
  }

  @Override
  public final JSType restrictByNotNullOrUndefined() {
    return this.removeType(this.commonTypes.NULL_OR_UNDEFINED);
  }

  @Override
  public final FunctionTypeI toMaybeFunctionType() {
    return isFunctionType() ? this : null;
  }

  @Override
  public final ObjectTypeI toMaybeObjectType() {
    return isSingletonObj() ? this : null;
  }

  @Override
  public final ObjectTypeI autoboxAndGetObject() {
    return this.autobox().restrictByNotNullOrUndefined().toMaybeObjectType();
  }

  @Override
  public final TypeI meetWith(TypeI other) {
    return meet(this, (JSType) other);
  }

  @Override
  public final boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (this == o) {
      return true;
    }
    Preconditions.checkArgument(o instanceof JSType,
        "Expected newtypes.JSType but found %s", o);
    JSType t2 = (JSType) o;
    return getMask() == t2.getMask() && Objects.equals(getObjs(), t2.getObjs())
        && Objects.equals(getEnums(), t2.getEnums())
        && Objects.equals(getTypeVar(), t2.getTypeVar());
  }

  @Override
  public final int hashCode() {
    return Objects.hash(getMask(), getObjs(), getEnums(), getTypeVar());
  }

  @Override
  public final String getDisplayName() {
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
  public final TypeI convertMethodToFunction() {
    checkState(this.isFunctionType());
    FunctionType devirtualized = getFunTypeIfSingletonObj().devirtualize();
    return commonTypes.fromFunctionType(devirtualized);
  }

  @Override
  public final boolean hasInstanceType() {
    checkState(this.isFunctionType());
    return getFunTypeIfSingletonObj().getInstanceTypeOfCtor() != null;
  }

  @Override
  public final ObjectTypeI getInstanceType() {
    checkState(this.isFunctionType());
    JSType instanceType = getFunTypeIfSingletonObj().getInstanceTypeOfCtor();
    return instanceType == null ? null : instanceType.toMaybeObjectType();
  }

  @Override
  public final String getReferenceName() {
    return isConstructor() ? getInstanceType().toString() : null;
  }

  @Override
  public final Node getSource() {
    return this.isSingletonObj() ? getObjTypeIfSingletonObj().getDefSite() : null;
  }

  @Override
  public final ImmutableCollection<FunctionTypeI> getDirectSubTypes() {
    Preconditions.checkState(this.isConstructor() || this.isInterface());
    ImmutableList.Builder<FunctionTypeI> result = ImmutableList.builder();
    NominalType nt =
        getFunTypeIfSingletonObj().getInstanceTypeOfCtor().getNominalTypeIfSingletonObj();
    if (nt != null) {
      for (RawNominalType rawType : nt.getSubtypes()) {
        result.add(this.commonTypes.fromFunctionType(rawType.getConstructorFunction()));
      }
    }
    return result.build();
  }

  @Override
  public final TypeI getTypeOfThis() {
    checkState(this.isFunctionType());
    return getFunTypeIfSingletonObj().getThisType();
  }

  @Override
  public final boolean acceptsArguments(List<? extends TypeI> argumentTypes) {
    checkState(this.isFunctionType());

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
  public final int getMinArity() {
    checkState(this.isFunctionType());
    return this.getFunTypeIfSingletonObj().getMinArity();
  }

  @Override
  public final int getMaxArity() {
    checkState(this.isFunctionType());
    return this.getFunTypeIfSingletonObj().getMaxArity();
  }

  @Override
  public final List<TypeI> getTypeParameters() {
    if (isFunctionType()) {
      return transformTypeParamsToTypeVars(this.getFunTypeIfSingletonObj().getTypeParameters());
    }
    NominalType nt = getNominalTypeIfSingletonObj();
    checkNotNull(nt, this);
    return transformTypeParamsToTypeVars(nt.getTypeParameters());
  }

  private List<TypeI> transformTypeParamsToTypeVars(List<String> names) {
    return Lists.transform(names, name -> JSType.fromTypeVar(commonTypes, name));
  }

  @Override
  public final boolean hasProperties() {
    checkState(this.isFunctionType());
    return getObjTypeIfSingletonObj().isFunctionWithProperties();
  }

  @Override
  public final void setSource(Node n) {
    throw new UnsupportedOperationException("setSource not implemented yet");
  }

  @Override
  public final TypeI getReturnType() {
    checkState(this.isFunctionType());
    return getFunTypeIfSingletonObj().getReturnType();
  }

  @Override
  public Builder toBuilder() {
    checkState(this.isFunctionType());
    return new FunctionBuilderImpl();
  }

  /** Private implementation used by toBuilder. */
  private class FunctionBuilderImpl implements Builder {
    FunctionType function = getFunTypeIfSingletonObj();

    @Override
    public Builder withUnknownReturnType() {
      function = function.withReturnType(commonTypes.UNKNOWN);
      return this;
    }

    @Override
    public Builder withReturnType(TypeI type) {
      checkArgument(type instanceof JSType);
      function = function.withReturnType((JSType) type);
      return this;
    }

    @Override
    public Builder withNoParameters() {
      function = function.withNoParameters();
      return this;
    }

    @Override
      public FunctionTypeI build() {
      return commonTypes.fromFunctionType(function);
    }
  }

  @Override
  public final FunctionTypeI getConstructor() {
    checkState(this.isSingletonObj());
    FunctionType ctorType = this.getNominalTypeIfSingletonObj().getConstructorFunction();
    return this.commonTypes.fromFunctionType(ctorType);
  }

  @Override
  public final FunctionTypeI getSuperClassConstructor() {
    if (equals(this.commonTypes.getTopObject())) {
      return null;
    }
    ObjectTypeI proto = getPrototypeObject();
    return proto == null ? null : proto.getConstructor();
  }

  @Override
  public final JSType getPrototypeObject() {
    checkState(this.isSingletonObj());
    ObjectType proto = getObjTypeIfSingletonObj().getPrototypeObject();
    return proto != null ? fromObjectType(proto) : null;
  }

  @Override
  public final JSDocInfo getJSDocInfo() {
    return getSource() == null ? null : NodeUtil.getBestJSDocInfo(getSource());
  }

  @Override
  public final JSDocInfo getOwnPropertyJSDocInfo(String propertyName) {
    Node defsite = this.getOwnPropertyDefSite(propertyName);
    return defsite == null ? null : NodeUtil.getBestJSDocInfo(defsite);
  }

  @Override
  public final JSDocInfo getPropertyJSDocInfo(String propertyName) {
    Node defsite = this.getPropertyDefSite(propertyName);
    return defsite == null ? null : NodeUtil.getBestJSDocInfo(defsite);
  }

  @Override
  public final Node getOwnPropertyDefSite(String propertyName) {
    checkState(this.isSingletonObj());
    return this.getObjTypeIfSingletonObj().getNonInheritedPropertyDefSite(propertyName);
  }

  @Override
  public final Node getPropertyDefSite(String propertyName) {
    return isSingletonObj() ? getObjTypeIfSingletonObj().getPropertyDefSite(propertyName) : null;
  }

  @Override
  public final Iterable<String> getOwnPropertyNames() {
    checkState(this.isSingletonObj());
    return getObjTypeIfSingletonObj().getNonInheritedPropertyNames();
  }

  @Override
  public final boolean isPrototypeObject() {
    return isSingletonObj() && getObjTypeIfSingletonObj().isPrototypeObject();
  }

  @Override
  public final boolean isAmbiguousObject() {
    ObjectType obj = getObjTypeIfSingletonObj();
    return obj != null && obj.isAmbiguousObject();
  }

  @Override
  public final boolean isLiteralObject() {
    return isSingletonObj() && getNominalTypeIfSingletonObj().isLiteralObject();
  }

  @Override
  public final boolean isInstanceofObject() {
    if (isSingletonObj()) {
      NominalType nt = getNominalTypeIfSingletonObj();
      return nt.isLiteralObject() || nt.isBuiltinObject() || nt.isIObject();
    }
    return false;
  }

  public final boolean mayContainUnknownObject() {
    for (ObjectType obj : this.getObjs()) {
      if (obj.getNominalType().isBuiltinObject()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public final boolean isInstanceType() {
    checkState(this.isSingletonObj());
    return this.getNominalTypeIfSingletonObj().isClassy();
  }

  @Override
  public final boolean hasProperty(String propertyName) {
    checkState(this.isSingletonObj());
    checkArgument(!propertyName.contains("."));
    return hasProp(new QualifiedName(propertyName));
  }

  @Override
  public final ImmutableCollection<JSType> getUnionMembers() {
    if (!isUnion()) {
      return ImmutableList.of(this);
    }
    ImmutableSet.Builder<JSType> builder = ImmutableSet.builder();
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
  public final ObjectTypeI normalizeObjectForCheckAccessControls() {
    if (isSingletonObj()) {
      FunctionTypeI ctor = getConstructor();
      if (ctor != null) {
        return ctor.getInstanceType();
      }
    }
    return this;
  }

  @Override
  public final boolean isBoxableScalar() {
    return isNumber()
        || isString()
        || isBoolean()
        || (isEnumElement() && getEnumeratedTypeOfEnumElement().isBoxableScalar());
  }

  @Override
  public final boolean isObjectType() {
    return !isBottom() && !isUnknown() && isSubtypeOf(this.commonTypes.getTopObject());
  }

  @Override
  public final boolean isGenericObjectType() {
    NominalType nt = getNominalTypeIfSingletonObj();
    return nt != null && nt.isGeneric();
  }

  @Override
  public final Collection<ObjectTypeI> getAncestorInterfaces() {
    FunctionType funType = getFunTypeIfSingletonObj();
    if (!funType.isUniqueConstructor() && !funType.isInterfaceDefinition()) {
      return ImmutableSet.of();
    }
    NominalType nt = funType.getInstanceTypeOfCtor().getNominalTypeIfSingletonObj();
    Set<ObjectTypeI> interfaces = new HashSet<>();
    for (NominalType i : nt.getInstantiatedInterfaces()) {
      if (!i.isBuiltinObject()) { // interfaces inherit from Object, remove it here?
        interfaces.add(i.getInstanceAsJSType());
      }
    }
    return interfaces;
  }

  @Override
  public final boolean isStructuralInterface() {
    FunctionType ft = getFunTypeIfSingletonObj();
    if (ft != null && ft.isSomeConstructorOrInterface()) {
      NominalType nt = ft.getThisType().getNominalTypeIfSingletonObj();
      return nt != null && nt.isStructuralInterface();
    }
    return false;
  }

  @Override
  public final boolean hasOwnProperty(String propertyName) {
    ObjectType obj = getObjTypeIfSingletonObj();
    return obj != null && obj.hasNonInheritedProperty(new QualifiedName(propertyName));
  }

  @Override
  public final ObjectTypeI getRawType() {
    NominalType nt = getNominalTypeIfSingletonObj();
    return nt.isGeneric() ? nt.getRawNominalType().getInstanceAsJSType() : this;
  }

  @Override
  public final ObjectTypeI instantiateGenericsWithUnknown() {
    NominalType nt = getNominalTypeIfSingletonObj();
    if (nt != null && nt.isGeneric()) {
      return nt.instantiateGenericsWithUnknown().getInstanceAsJSType();
    }
    return this;
  }

  /**
   * If this type represents an uninstantiated nominal type or function, instantiate it using
   * the given type arguments. Otherwise, return the type unchanged.
   */
  public final JSType instantiateGenerics(List<? extends TypeI> typeiArgs) {
    @SuppressWarnings("unchecked")
    List<JSType> typeArgs = (List<JSType>) typeiArgs;
    FunctionType ft = getFunTypeIfSingletonObj();
    if (ft != null && ft.isGeneric()) {
      return this.commonTypes.fromFunctionType(
          ft.instantiateGenericsFromArgumentTypes(null, typeArgs));
    }
    NominalType nt = getNominalTypeIfSingletonObj();
    if (nt.isUninstantiatedGenericType()) {
      return nt.instantiateGenerics(typeArgs).getInstanceAsJSType();
    }
    return this;
  }

  /**
   * Create an anonymous record type with the given properties.
   */
  public static JSType fromProperties(JSTypes commonTypes, Map<String, JSType> propTypes) {
    Map<String, Property> propMap = new LinkedHashMap<>();
    for (Map.Entry<String, JSType> entry : propTypes.entrySet()) {
      String pname = entry.getKey();
      JSType ptype = entry.getValue();
      propMap.put(pname, Property.make(ptype, ptype));
    }
    return fromObjectType(ObjectType.fromProperties(commonTypes, propMap));
  }

  /**
   * Create an anonymous record using the "own" properties of the given type.
   * Used by the TTL.
   */
  public static JSType buildRecordTypeFromObject(JSTypes commonTypes, JSType t) {
    ObjectType obj = t.getObjTypeIfSingletonObj();
    ObjectType builtinObj = ObjectType.fromNominalType(commonTypes.getObjectType());
    return fromObjectType(obj == null ? builtinObj : obj.toAnonymousRecord());
  }

  @Override
  public final boolean isLegacyNamedType() {
    return false;
  }

  @Override
  public final TypeI getLegacyResolvedType() {
    throw new UnsupportedOperationException(
        "NTI does not have NamedType. This method should never be called on NTI types.");
  }

  final Collection<JSType> getSubtypesWithProperty(QualifiedName qname) {
    Collection<JSType> typesWithProp =
        TypeWithPropertiesStatics.getSubtypesWithProperty(getEnums(), qname);
    typesWithProp.addAll(TypeWithPropertiesStatics.getSubtypesWithProperty(getObjs(), qname));
    return typesWithProp;
  }

  @Override
  public final TypeI getGreatestSubtypeWithProperty(String pname) {
    return joinManyTypes(this.commonTypes, getSubtypesWithProperty(new QualifiedName(pname)));
  }

  @Override
  public final ObjectTypeI getPrototypeProperty() {
    return getProp(new QualifiedName("prototype"));
  }

  @Override
  public final JSType getTopDefiningInterface(String pname) {
    NominalType nt = getNominalTypeIfSingletonObj();
    if (nt != null && nt.isInterface()) {
      nt = nt.getTopDefiningInterface(pname);
      return nt == null ? null : nt.getInstanceAsJSType();
    }
    return null;
  }

  @Override
  public final FunctionTypeI getOwnerFunction() {
    if (isPrototypeObject()) {
      return this.commonTypes.fromFunctionType(getObjTypeIfSingletonObj().getOwnerFunction());
    }
    return null;
  }

  @Override
  public final boolean isSubtypeWithoutStructuralTyping(TypeI other) {
    if (!isSubtypeOf(other)) {
      return false;
    }
    NominalType thisNt = getNominalTypeIfSingletonObj();
    NominalType otherNt = ((JSType) other).getNominalTypeIfSingletonObj();
    return thisNt == null || otherNt == null || thisNt.isNominalSubtypeOf(otherNt);
  }

  @Override
  public final Iterable<TypeI> getParameterTypes() {
    return checkNotNull(getFunType()).getParameterTypes();
  }

  @Override
  public TypeI getPropertyType(String propName) {
    return isObjectType() ? getProp(new QualifiedName(propName)) : null;
  }

  @Override
  public boolean isRecordType() {
    NominalType nt = getNominalTypeIfSingletonObj();
    return nt != null && (nt.isBuiltinObject() || nt.isLiteralObject());
  }

  @Override
  public boolean isFullyInstantiated() {
    NominalType nt = getNominalTypeIfSingletonObj();
    return nt == null || !nt.isUninstantiatedGenericType();
  }

  @Override
  public boolean isPartiallyInstantiated() {
    return isFullyInstantiated();
  }

  @Override
  public ImmutableList<? extends TypeI> getTemplateTypes() {
    NominalType nt = getNominalTypeIfSingletonObj();
    if (nt.isGeneric()) {
      ImmutableList.Builder<JSType> builder = ImmutableList.builder();
      return builder.addAll(nt.getTypeMap().values()).build();
    }
    return null;
  }

  @Override
  public Set<String> getPropertyNames() {
    ObjectType obj = getObjTypeIfSingletonObj();
    return obj == null ? ImmutableSet.<String>of() : obj.getPropertyNames();
  }

  @Override
  public ObjectTypeI withoutStrayProperties() {
    ObjectType obj = getObjTypeIfSingletonObj();
    NominalType nt = getNominalTypeIfSingletonObj();
    if (nt.isLiteralObject()) {
      return this;
    }
    if (obj.isPrototypeObject()) {
      // The canonical prototype, without any specialized properties
      return obj.getOwnerFunction().getInstanceTypeOfCtor().getPrototypeObject();
    }
    if (obj.isNamespace()) {
      return obj.getNamespaceType();
    }
    return nt.getInstanceAsJSType();
  }

  @Override
  public TypeInference typeInference() {
    return TypeInference.NTI;
  }

  // Note: concrete subclasses follow below.  The above code in JSType
  // should not depend on any of these specific implementations, other
  // than instantiating them when appropriate (in the makeType methods).
  // They exist as optimizations.  All subclass fields should be
  // treated as private to the specific subclass.

  private static final class UnionType extends JSType {
    final int mask;
    // objs is empty for scalar types
    transient Collection<ObjectType> objs;
    // typeVar is null for non-generic types
    final String typeVar;
    // enums is empty for types that don't have enums
    transient Collection<EnumType> enums;

    UnionType(JSTypes commonTypes, int mask, ImmutableSet<ObjectType> objs,
        String typeVar, ImmutableSet<EnumType> enums) {
      super(commonTypes);
      this.enums = checkNotNull(enums);
      this.objs = checkNotNull(objs);

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
    protected String getTypeVar() {
      return typeVar;
    }

    @Override
    protected ImmutableSet<ObjectType> getObjs() {
      checkNotNull(this.objs);
      if (!(this.objs instanceof ImmutableSet)) {
        this.objs = ImmutableSet.copyOf(this.objs);
      }
      return (ImmutableSet<ObjectType>) this.objs;
    }

    @Override
    protected ImmutableSet<EnumType> getEnums() {
      checkNotNull(this.enums);
      if (!(this.enums instanceof ImmutableSet)) {
        this.enums = ImmutableSet.copyOf(this.enums);
      }
      return (ImmutableSet<EnumType>) this.enums;
    }

    @GwtIncompatible("ObjectOutputStream")
    private void writeObject(ObjectOutputStream out) throws IOException {
      out.defaultWriteObject();
      out.writeObject(new ArrayList<>(this.objs));
      out.writeObject(new ArrayList<>(this.enums));
    }

    @SuppressWarnings("unchecked")
    @GwtIncompatible("ObjectInputStream")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      this.objs = (ArrayList<ObjectType>) in.readObject();
      this.enums = (ArrayList<EnumType>) in.readObject();
    }
  }

  private static final class MaskType extends JSType {
    final int mask;

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

  private static final class ObjsType extends JSType {
    transient Collection<ObjectType> objs;

    ObjsType(JSTypes commonTypes, ImmutableSet<ObjectType> objs) {
      super(commonTypes);
      this.objs = checkNotNull(objs);
    }

    @Override
    protected int getMask() {
      return NON_SCALAR_MASK;
    }

    @Override
    protected String getTypeVar() {
      return null;
    }

    @Override
    protected ImmutableSet<ObjectType> getObjs() {
      checkNotNull(this.objs);
      if (!(this.objs instanceof ImmutableSet)) {
        this.objs = ImmutableSet.copyOf(this.objs);
      }
      return (ImmutableSet<ObjectType>) this.objs;
    }

    @Override
    protected ImmutableSet<EnumType> getEnums() {
      return ImmutableSet.of();
    }

    @GwtIncompatible("ObjectOutputStream")
    private void writeObject(ObjectOutputStream out) throws IOException {
      out.defaultWriteObject();
      out.writeObject(new ArrayList<>(this.objs));
    }

    @SuppressWarnings("unchecked")
    @GwtIncompatible("ObjectInputStream")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      this.objs = (ArrayList<ObjectType>) in.readObject();
    }
  }

  private static final class NullableObjsType extends JSType {
    transient Collection<ObjectType> objs;

    NullableObjsType(JSTypes commonTypes, ImmutableSet<ObjectType> objs) {
      super(commonTypes);
      this.objs = checkNotNull(objs);
    }

    @Override
    protected int getMask() {
      return NON_SCALAR_MASK | NULL_MASK;
    }

    @Override
    protected String getTypeVar() {
      return null;
    }

    @Override
    protected ImmutableSet<ObjectType> getObjs() {
      checkNotNull(this.objs);
      if (!(this.objs instanceof ImmutableSet)) {
        this.objs = ImmutableSet.copyOf(this.objs);
      }
      return (ImmutableSet<ObjectType>) this.objs;
    }

    @Override
    protected ImmutableSet<EnumType> getEnums() {
      return ImmutableSet.of();
    }

    @GwtIncompatible("ObjectOutputStream")
    private void writeObject(ObjectOutputStream out) throws IOException {
      out.defaultWriteObject();
      out.writeObject(new ArrayList<>(this.objs));
    }

    @SuppressWarnings("unchecked")
    @GwtIncompatible("ObjectInputStream")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      this.objs = (ArrayList<ObjectType>) in.readObject();
    }
  }
}
