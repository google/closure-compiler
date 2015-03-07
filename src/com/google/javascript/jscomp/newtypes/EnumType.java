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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.JSTypeExpression;

import java.util.Collection;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 *
 * Represents an enumerated type.
 * Each enum declaration produces two types of interest:
 * - We represent the object literal that defined the enum as an ObjectType.
 * - We represent an element of the enum by using this class in JSType.
 */
public class EnumType extends Namespace implements TypeWithProperties {

  private enum State {
    NOT_RESOLVED,
    DURING_RESOLUTION,
    RESOLVED
  }

  private State state;
  private JSTypeExpression typeExpr;
  private String name;
  // The type that accompanies the enum declaration
  private JSType declaredType;
  // The type of the enum's properties, a subtype of the previous field.
  private JSType enumPropType;
  // The type of the object literal that defines the enum
  private JSType enumObjType;
  // All properties have the same type, so we only need a set, not a map.
  private ImmutableSet<String> props;

  private EnumType(
      String name, JSTypeExpression typeExpr, Collection<String> props) {
    Preconditions.checkNotNull(typeExpr);
    this.state = State.NOT_RESOLVED;
    this.name = name;
    // typeExpr is non-null iff the enum is not resolved
    this.typeExpr = typeExpr;
    this.props = ImmutableSet.copyOf(props);
  }

  public static EnumType make(
      String name, JSTypeExpression typeExpr, Collection<String> props) {
    return new EnumType(name, typeExpr, props);
  }

  public boolean isResolved() {
    return state == State.RESOLVED;
  }

  public JSType getEnumeratedType() {
    Preconditions.checkState(state == State.RESOLVED);
    return declaredType;
  }

  public JSType getPropType() {
    Preconditions.checkState(state == State.RESOLVED);
    return enumPropType;
  }

  public JSType toJSType() {
    Preconditions.checkState(state == State.RESOLVED);
    if (enumObjType == null) {
      enumObjType = computeObjType();
    }
    return enumObjType;
  }

  // Returns null iff there is a type cycle
  public JSTypeExpression getTypeExpr() {
    Preconditions.checkState(state != State.RESOLVED);
    if (state == State.DURING_RESOLUTION) {
      return null;
    }
    state = State.DURING_RESOLUTION;
    return typeExpr;
  }

  public JSTypeExpression getTypeExprForErrorReporting() {
    Preconditions.checkState(state == State.DURING_RESOLUTION);
    return typeExpr;
  }

  void resolveEnum(JSType t) {
    Preconditions.checkNotNull(t);
    if (state == State.RESOLVED) {
      return;
    }
    Preconditions.checkState(state == State.DURING_RESOLUTION,
        "Expected state DURING_RESOLUTION but found %s", state.toString());
    state = State.RESOLVED;
    typeExpr = null;
    declaredType = t;
    enumPropType = JSType.fromEnum(this);
  }

  /**
   * When defining an enum such as
   *   /** @enum {number} * /
   *   var X = { ONE: 1, TWO: 2 };
   * the properties of the object literal are constant.
   */
  private JSType computeObjType() {
    Preconditions.checkState(enumPropType != null);
    PersistentMap<String, Property> propMap = otherProps;
    for (String s : props) {
      propMap = propMap.with(s,
          Property.makeConstant(enumPropType, enumPropType));
    }
    return withNamedTypes(
        ObjectType.makeObjectType(
            null, propMap, null, false, ObjectKind.UNRESTRICTED));
  }

  @Override
  public JSType getProp(QualifiedName qname) {
    return declaredType.getProp(qname);
  }

  @Override
  public JSType getDeclaredProp(QualifiedName qname) {
    return declaredType.getDeclaredProp(qname);
  }

  @Override
  public boolean mayHaveProp(QualifiedName qname) {
    return declaredType.mayHaveProp(qname);
  }

  @Override
  public boolean hasProp(QualifiedName qname) {
    return declaredType.hasProp(qname);
  }

  @Override
  public boolean hasConstantProp(QualifiedName qname) {
    return declaredType.hasConstantProp(qname);
  }

  // Unlike hasProp, this method asks about the object literal in the enum
  // definition, not about the declared type of the enum.
  public boolean enumLiteralHasKey(String name) {
    return props.contains(name);
  }

  static boolean hasNonScalar(ImmutableSet<EnumType> enums) {
    for (EnumType e : enums) {
      if (e.declaredType.hasNonScalar()) {
        return true;
      }
    }
    return false;
  }

  static ImmutableSet<EnumType> union(
      ImmutableSet<EnumType> s1, ImmutableSet<EnumType> s2) {
    if (s1.isEmpty()) {
      return s2;
    }
    if (s2.isEmpty() || s1.equals(s2)) {
      return s1;
    }
    return Sets.union(s1, s2).immutableCopy();
  }

  // We normalize the type so that it doesn't contain both enum {T1} and T1.
  static ImmutableSet<EnumType> normalizeForJoin(
      ImmutableSet<EnumType> newEnums, JSType joinWithoutEnums) {
    boolean recreateEnums = false;
    for (EnumType e : newEnums) {
      if (e.declaredType.isSubtypeOf(joinWithoutEnums)) {
        recreateEnums = true;
        break;
      }
    }
    if (!recreateEnums) {
      return newEnums;
    }
    ImmutableSet.Builder<EnumType> builder = ImmutableSet.builder();
    for (EnumType e : newEnums) {
      if (!e.declaredType.isSubtypeOf(joinWithoutEnums)) {
        builder.add(e);
      }
    }
    return builder.build();
  }

  static boolean areSubtypes(JSType t1, JSType t2) {
    ImmutableSet<EnumType> s1 = t1.getEnums();
    if (s1 == null) {
      return true;
    }
    ImmutableSet<EnumType> s2 = t2.getEnums();
    for (EnumType e : s1) {
      if (s2 != null && s2.contains(e)) {
        continue;
      }
      if (!e.declaredType.isSubtypeOf(t2)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return name;
  }
}
