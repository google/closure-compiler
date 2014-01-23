/*
 * Copyright 2014 The Closure Compiler Authors.
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

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public abstract class JSType {

  public abstract boolean isTop();

  public abstract boolean isBottom();

  public abstract boolean isUnknown();

  public abstract boolean isTruthy();

  public abstract boolean isFalsy();

  public abstract boolean isBoolean();

  public abstract boolean isNullOrUndef();

  public abstract boolean isScalar();

  // True iff there exists a value that can have this type
  public abstract boolean isInhabitable();

  public abstract boolean hasNonScalar();

  // Specialize this type by meeting with other, but keeping location
  public abstract JSType specialize(JSType other);

  public abstract JSType negate();

  public abstract JSType toBoolean();

  public abstract boolean isSubtypeOf(JSType other);

  public abstract JSType removeType(JSType other);

  public abstract JSType withLocation(String location);

  public abstract String getLocation();

  public abstract FunctionType getFunTypeIfSingletonObj();

  public abstract FunctionType getFunType();

  abstract NominalType getClassTypeIfUnique();

  /** Turns the class-less object of this type (if any) into a loose object */
  public abstract JSType withLoose();

  public abstract JSType getProp(String qName);

  public abstract boolean mayHaveProp(String qName);

  public abstract boolean hasProp(String qName);

  public abstract JSType getDeclaredProp(String qName);

  public abstract JSType withoutProperty(String qname);

  public abstract JSType withProperty(String qname, JSType type);

  public abstract JSType withDeclaredProperty(String qname, JSType type);

  public abstract JSType withPropertyRequired(String qname);

  public static JSType join(JSType lhs, JSType rhs) {
    return UnionType.join((UnionType) lhs, (UnionType) rhs);
  }

  // Meet two types, location agnostic
  public static JSType meet(JSType lhs, JSType rhs) {
    return UnionType.meet((UnionType) lhs, (UnionType) rhs);
  }

  public static boolean areCompatibleScalarTypes(JSType lhs, JSType rhs) {
    Preconditions.checkArgument(
        lhs.isSubtypeOf(TypeConsts.TOP_SCALAR) || rhs.isSubtypeOf(TypeConsts.TOP_SCALAR));
    if (lhs.isBottom() || rhs.isBottom() ||
        lhs.isUnknown() || rhs.isUnknown() ||
        (lhs.isBoolean() && rhs.isBoolean()) ||
        lhs.equals(rhs)) {
      return true;
    }
    return false;
  }

  public static JSType plus(JSType lhs, JSType rhs) {
    return UnionType.plus((UnionType) lhs, (UnionType) rhs);
  }

  public static JSType fromFunctionType(FunctionType fn) {
    return UnionType.fromFunctionType_(fn);
  }

  public static JSType fromObjectType(ObjectType obj) {
    return UnionType.fromObjectType_(obj);
  }

}
