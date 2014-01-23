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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
class UnionType extends JSType {
  private static final int BOTTOM_MASK = 0x0;
  private static final int STRING_MASK = 0x1;
  private static final int NUMBER_MASK = 0x2;
  private static final int UNDEFINED_MASK = 0x4;
  private static final int TRUE_MASK = 0x8;
  private static final int FALSE_MASK = 0x10;
  private static final int NULL_MASK = 0x20;
  private static final int NON_SCALAR_MASK = 0x40;
  private static final int END_MASK = NON_SCALAR_MASK * 2;
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

  final int type;
  // objs is null for scalar types
  final ImmutableSet<ObjectType> objs;
  final String location;

  private UnionType(int type, String location, ImmutableSet<ObjectType> objs) {
    this.location = location;
    if (objs == null) {
      this.type = type;
      this.objs = null;
    } else if (objs.size() == 0) {
      this.type = type & ~NON_SCALAR_MASK;
      this.objs = null;
    } else {
      this.type = type | NON_SCALAR_MASK;
      this.objs = objs;
    }
    Preconditions.checkState(this.isValidType());
  }

  private UnionType(int type) {
    this(type, null, null);
  }

  // Factory method for wrapping a function in a JSType
  static UnionType fromFunctionType_(FunctionType fn) {
    return new UnionType(NON_SCALAR_MASK, null,
        ImmutableSet.of(ObjectType.fromFunction(fn)));
  }

  static UnionType fromObjectType_(ObjectType obj) {
    return new UnionType(NON_SCALAR_MASK, null, ImmutableSet.of(obj));
  }

  boolean isValidType() {
    return isUnknown() || isTop() ||
        ((type & NON_SCALAR_MASK) != 0 && !objs.isEmpty()) ||
        ((type & NON_SCALAR_MASK) == 0 && objs == null);
  }

  static final UnionType BOOLEAN = new UnionType(TRUE_MASK | FALSE_MASK);
  static final UnionType BOTTOM = new UnionType(BOTTOM_MASK);
  static final JSType FALSE_TYPE = new UnionType(FALSE_MASK);
  static final JSType FALSY = new UnionType(FALSY_MASK);
  static final UnionType NULL = new UnionType(NULL_MASK);
  static final UnionType NUMBER = new UnionType(NUMBER_MASK);
  static final UnionType STRING = new UnionType(STRING_MASK);
  static final UnionType TOP = new UnionType(TOP_MASK);
  static final JSType TOP_SCALAR = new UnionType(TOP_SCALAR_MASK);
  static final JSType TRUE_TYPE = new UnionType(TRUE_MASK);
  static final JSType TRUTHY = new UnionType(TRUTHY_MASK);
  static final UnionType UNDEFINED = new UnionType(UNDEFINED_MASK);
  static final UnionType UNKNOWN = new UnionType(UNKNOWN_MASK);

  private static final UnionType TOP_MINUS_NULL = new UnionType(
      TRUE_MASK | FALSE_MASK | NUMBER_MASK | STRING_MASK | UNDEFINED_MASK |
      NON_SCALAR_MASK,
      null, ImmutableSet.of(ObjectType.TOP_OBJECT));
  private static final UnionType TOP_MINUS_UNDEF = new UnionType(
      TRUE_MASK | FALSE_MASK | NUMBER_MASK | STRING_MASK | NULL_MASK |
      NON_SCALAR_MASK,
      null, ImmutableSet.of(ObjectType.TOP_OBJECT));

  @Override
  public boolean isTop() {
    return TOP_MASK == type;
  }

  @Override
  public boolean isBottom() {
    return BOTTOM_MASK == type;
  }

  @Override
  public boolean isUnknown() {
    return UNKNOWN_MASK == type;
  }

  @Override
  public boolean isTruthy() {
    return TRUTHY_MASK == type || TRUE_MASK == type;
  }

  @Override
  public boolean isFalsy() {
    return FALSY_MASK == type || FALSE_MASK == type;
  }

  @Override
  public boolean isBoolean() {
    return (type & ~BOOLEAN_MASK) == 0 && (type & BOOLEAN_MASK) != 0;
  }

  @Override
  public boolean isNullOrUndef() {
    int mask = NULL_MASK | UNDEFINED_MASK;
    return type != 0 && (type | mask) == mask;
  }

  @Override
  public boolean isScalar() {
    return type == NUMBER_MASK ||
        type == STRING_MASK ||
        type == NULL_MASK ||
        type == UNDEFINED_MASK ||
        this.isBoolean();
  }

  // True iff there exists a value that can have this type
  @Override
  public boolean isInhabitable() {
    if (isBottom()) {
      return false;
    } else if (objs == null) {
      return true;
    }
    for (ObjectType obj: objs) {
      if (!obj.isInhabitable()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean hasNonScalar() {
    return objs != null;
  }

  // Specialize this type by meeting with other, but keeping location
  @Override
  public JSType specialize(JSType otherJstype) {
    UnionType other = (UnionType) otherJstype;
    if (other.isTop() || other.isUnknown()) {
      return this;
    } else if (other.isTruthy()) {
      return makeTruthy();
    } else if (other.isFalsy()) {
      return makeFalsy();
    } else if (this.isTop() || this.isUnknown()) {
      return other.withLocation(this.location);
    }
    return new UnionType(this.type & other.type, this.location,
        ObjectType.specializeSet(this.objs, other.objs));
  }

  private JSType makeTruthy() {
    if (this.isTop() || this.isUnknown()) {
      return this;
    }
    return new UnionType(type & ~NULL_MASK & ~FALSE_MASK & ~UNDEFINED_MASK,
        location, objs);
  }

  private JSType makeFalsy() {
    if (this.isTop() || this.isUnknown()) {
      return this;
    }
    return new UnionType(type & ~TRUE_MASK & ~NON_SCALAR_MASK, location, null);
  }

  @Override
  public JSType negate() {
    if (isTruthy()) {
      return FALSY;
    } else if (isFalsy()) {
      return TRUTHY;
    }
    return this;
  }

  @Override
  public JSType toBoolean() {
    if (isTruthy()) {
      return TRUE_TYPE;
    } else if (isFalsy()) {
      return FALSE_TYPE;
    }
    return BOOLEAN;
  }

  @Override
  public boolean isSubtypeOf(JSType otherJstype) {
    UnionType other = (UnionType) otherJstype;
    if (isUnknown() || other.isUnknown() || other.isTop()) {
      return true;
    } else if ((type | other.type) != other.type) {
      return false;
    } else if (this.objs == null) {
      return true;
    }
    // Because of optional properties,
    //   x \le y \iff x \join y = y does not hold.
    return ObjectType.isUnionSubtype(this.objs, other.objs);
  }

  @Override
  public UnionType removeType(JSType otherJstype) {
    UnionType other = (UnionType) otherJstype;
    if ((isTop() || isUnknown()) && other.equals(NULL)) {
      return TOP_MINUS_NULL;
    }
    if ((isTop() || isUnknown()) && other.equals(UNDEFINED)) {
      return TOP_MINUS_UNDEF;
    }
    if (other.equals(NULL) || other.equals(UNDEFINED)) {
      return new UnionType(type & ~other.type, location, objs);
    }
    if (objs == null) {
      return this;
    }
    Preconditions.checkState(
        (other.type & ~NON_SCALAR_MASK) == 0 && other.objs.size() == 1);
    NominalType otherKlass =
        Iterables.getOnlyElement(other.objs).getClassType();
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj: objs) {
      if (!Objects.equal(obj.getClassType(), otherKlass)) {
        newObjs.add(obj);
      }
    }
    return new UnionType(type, location, newObjs.build());
  }

  @Override
  public JSType withLocation(String location) {
    return new UnionType(type, location, objs);
  }

  @Override
  public String getLocation() {
    return location;
  }

  @Override
  public FunctionType getFunTypeIfSingletonObj() {
    if (type != NON_SCALAR_MASK || objs.size() > 1) {
      return null;
    }
    return Iterables.getOnlyElement(objs).getFunType();
  }

  @Override
  public FunctionType getFunType() {
    if (objs == null) {
      return null;
    }
    Preconditions.checkState(objs.size() == 1);
    return Iterables.getOnlyElement(objs).getFunType();
  }

  @Override
  NominalType getClassTypeIfUnique() {
    if (objs == null || objs.size() > 1) {
      return null;
    }
    return Iterables.getOnlyElement(objs).getClassType();
  }

  /** Turns the class-less object of this type (if any) into a loose object */
  @Override
  public JSType withLoose() {
    Preconditions.checkNotNull(this.objs);
    return new UnionType(
        this.type, this.location, ObjectType.withLooseObjects(this.objs));
  }

  @Override
  public JSType getProp(String qName) {
    if (isBottom() || isUnknown()) {
      return UNKNOWN;
    }
    Preconditions.checkState(objs != null);
    JSType ptype = BOTTOM;
    for (ObjectType o : objs) {
      if (o.mayHaveProp(qName)) {
        ptype = JSType.join(ptype, o.getProp(qName));
      }
    }
    if (ptype.isBottom()) {
      return null;
    }
    return ptype;
  }

  @Override
  public boolean mayHaveProp(String qName) {
    if (objs == null) {
      return false;
    }
    for (ObjectType o : objs) {
      if (o.mayHaveProp(qName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasProp(String qName) {
    if (objs == null) {
      return false;
    }
    for (ObjectType o : objs) {
      if (!o.hasProp(qName)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public JSType getDeclaredProp(String qName) {
    if (isUnknown()) {
      return UNKNOWN;
    }
    Preconditions.checkState(objs != null);
    JSType ptype = BOTTOM;
    for (ObjectType o : objs) {
      JSType declType = o.getDeclaredProp(qName);
      if (declType != null) {
        ptype = JSType.join(ptype, declType);
      }
    }
    return ptype.isBottom() ? null : ptype;
  }

  @Override
  public JSType withoutProperty(String qname) {
    return this.objs == null ?
        this :
        new UnionType(this.type, this.location,
            ObjectType.withoutProperty(this.objs, qname));
  }

  @Override
  public JSType withProperty(String qname, JSType type) {
    if (isUnknown()) {
      return this;
    }
    Preconditions.checkState(this.objs != null);
    return new UnionType(this.type, this.location,
        ObjectType.withProperty(this.objs, qname, type));
  }

  @Override
  public JSType withDeclaredProperty(String qname, JSType type) {
    Preconditions.checkState(this.objs != null && this.location == null);
    return new UnionType(this.type, null,
        ObjectType.withDeclaredProperty(this.objs, qname, type));
  }

  @Override
  public JSType withPropertyRequired(String qname) {
    return (isUnknown() || this.objs == null) ?
        this :
        new UnionType(this.type, this.location,
            ObjectType.withPropertyRequired(this.objs, qname));
  }

  @Override
  public String toString() {
    return typeToString() + locationPostfix(location);
  }

  private String typeToString() {
    switch (type) {
      case BOTTOM_MASK:
      case TOP_MASK:
      case UNKNOWN_MASK:
        return tagToString(type, null);
      default:
        int tags = type;
        Set<String> types = Sets.newTreeSet();
        for (int mask = 1; mask != END_MASK; mask <<= 1) {
          if ((tags & mask) != 0) {
            types.add(tagToString(mask, objs));
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

  // When joining w/ TOP or UNKNOWN, avoid setting more fields on them, eg, obj.
  public static UnionType join(UnionType lhs, UnionType rhs) {
    if (lhs.isTop() || rhs.isTop()) {
      return TOP;
    } else if (lhs.isUnknown() || rhs.isUnknown()) {
      return UNKNOWN;
    }
    return new UnionType(
        lhs.type | rhs.type,
        Objects.equal(lhs.location, rhs.location) ? lhs.location : null,
        ObjectType.joinSets(lhs.objs, rhs.objs));
  }

  // Meet two types, location agnostic
  public static UnionType meet(UnionType lhs, UnionType rhs) {
    if (lhs.isTop()) {
      return rhs;
    } else if (rhs.isTop()) {
      return lhs;
    } else if (lhs.isUnknown()) {
      return rhs;
    } else if (rhs.isUnknown()) {
      return lhs;
    }
    return new UnionType(lhs.type & rhs.type, null,
        ObjectType.meetSets(lhs.objs, rhs.objs));
  }

  public static JSType plus(UnionType lhs, UnionType rhs) {
    int newtype = (lhs.type | rhs.type) & STRING_MASK;
    if ((lhs.type & ~STRING_MASK) != 0 && (rhs.type & ~STRING_MASK) != 0) {
      newtype |= NUMBER_MASK;
    }
    return new UnionType(newtype);
  }

  /**
   * Takes a type tag with a single bit set (including the non-scalar bit),
   * and prints the string representation of that single type.
   */
  private static String tagToString(int tag, Set<ObjectType> objs) {
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
        return "unknown";
      default: // Must be a union type.
        return null;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    Preconditions.checkArgument(o instanceof UnionType);
    UnionType t2 = (UnionType) o;
    return this.type == t2.type && Objects.equal(this.objs, t2.objs);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type, objs);
  }
}
