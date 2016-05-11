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
public final class EnumType extends Namespace implements TypeWithProperties {

  private enum State {
    NOT_RESOLVED,
    DURING_RESOLUTION,
    RESOLVED
  }

  private State state;
  private JSTypeExpression typeExpr;
  // The type that accompanies the enum declaration
  private JSType declaredType;
  // The type of the enum's properties, a subtype of the previous field.
  private JSType enumPropType;
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
    return this.state == State.RESOLVED;
  }

  public JSType getEnumeratedType() {
    Preconditions.checkState(this.state == State.RESOLVED);
    return declaredType;
  }

  public JSType getPropType() {
    Preconditions.checkState(this.state == State.RESOLVED);
    return enumPropType;
  }

  // Returns null iff there is a type cycle
  public JSTypeExpression getTypeExpr() {
    Preconditions.checkState(this.state != State.RESOLVED);
    if (this.state == State.DURING_RESOLUTION) {
      return null;
    }
    this.state = State.DURING_RESOLUTION;
    return typeExpr;
  }

  public JSTypeExpression getTypeExprForErrorReporting() {
    Preconditions.checkState(this.state == State.DURING_RESOLUTION);
    return typeExpr;
  }

  void resolveEnum(JSType t) {
    Preconditions.checkNotNull(t);
    if (this.state == State.RESOLVED) {
      return;
    }
    Preconditions.checkState(this.state == State.DURING_RESOLUTION,
        "Expected state DURING_RESOLUTION but found %s", this.state.toString());
    this.state = State.RESOLVED;
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
  @Override
  protected JSType computeJSType(JSTypes commonTypes) {
    Preconditions.checkNotNull(enumPropType);
    Preconditions.checkState(this.namespaceType == null);
    PersistentMap<String, Property> propMap = PersistentMap.create();
    for (String s : this.props) {
      propMap = propMap.with(s,
          Property.makeConstant(null, enumPropType, enumPropType));
    }
    return JSType.fromObjectType(ObjectType.makeObjectType(
        null, propMap, null, this, false, ObjectKind.UNRESTRICTED));
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

  static boolean hasScalar(ImmutableSet<EnumType> enums) {
    for (EnumType e : enums) {
      if (e.declaredType.hasScalar()) {
        return true;
      }
    }
    return false;
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
      if (e.declaredType.isSubtypeOf(joinWithoutEnums, SubtypeCache.create())) {
        recreateEnums = true;
        break;
      }
    }
    if (!recreateEnums) {
      return newEnums;
    }
    ImmutableSet.Builder<EnumType> builder = ImmutableSet.builder();
    for (EnumType e : newEnums) {
      if (!e.declaredType.isSubtypeOf(joinWithoutEnums, SubtypeCache.create())) {
        builder.add(e);
      }
    }
    return builder.build();
  }

  static boolean areSubtypes(JSType t1, JSType t2, SubtypeCache subSuperMap) {
    ImmutableSet<EnumType> s1 = t1.getEnums();
    if (s1 == null) {
      return true;
    }
    ImmutableSet<EnumType> s2 = t2.getEnums();
    for (EnumType e : s1) {
      if (s2 != null && s2.contains(e)) {
        continue;
      }
      if (!e.declaredType.isSubtypeOf(t2, subSuperMap)) {
        return false;
      }
    }
    return true;
  }
}
