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

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
class Property {
  private final JSType inferredType;
  private final JSType declaredType;
  // Attributes are ordered: constant <= required <= optional
  private enum Attribute {
    CONSTANT, // For required props only
    OPTIONAL,
    REQUIRED;
  }
  private Attribute attribute;

  private Property(
      JSType inferredType, JSType declaredType, Attribute attribute) {
    Preconditions.checkArgument(inferredType != null);
    this.inferredType = inferredType;
    this.declaredType = declaredType;
    this.attribute = attribute;
  }

  static Property make(JSType inferredType, JSType declaredType) {
    return new Property(inferredType, declaredType, Attribute.REQUIRED);
  }

  static Property makeConstant(JSType inferredType, JSType declaredType) {
    return new Property(inferredType, declaredType, Attribute.CONSTANT);
  }

  static Property makeOptional(JSType inferredType, JSType declaredType) {
    return new Property(inferredType, declaredType, Attribute.OPTIONAL);
  }

  boolean isOptional() {
    return attribute == Attribute.OPTIONAL;
  }

  boolean isConstant() {
    return attribute == Attribute.CONSTANT;
  }

  boolean isDeclared() {
    return declaredType != null;
  }

  JSType getType() {
    return inferredType;
  }

  JSType getDeclaredType() {
    return declaredType;
  }

  Property withOptional() {
    return new Property(inferredType, declaredType, Attribute.OPTIONAL);
  }

  Property withRequired() {
    return new Property(inferredType, declaredType, Attribute.REQUIRED);
  }

  private static Attribute meetAttributes(Attribute a1, Attribute a2) {
    if (a1 == Attribute.CONSTANT || a2 == Attribute.CONSTANT) {
      return Attribute.CONSTANT;
    }
    if (a1 == Attribute.REQUIRED || a2 == Attribute.REQUIRED) {
      return Attribute.REQUIRED;
    }
    return Attribute.OPTIONAL;
  }

  private static Attribute joinAttributes(Attribute a1, Attribute a2) {
    if (a1 == Attribute.OPTIONAL || a2 == Attribute.OPTIONAL) {
      return Attribute.OPTIONAL;
    }
    if (a1 == Attribute.REQUIRED || a2 == Attribute.REQUIRED) {
      return Attribute.REQUIRED;
    }
    return Attribute.CONSTANT;
  }

  Property specialize(Property other) {
    return new Property(
        this.inferredType.specialize(other.inferredType),
        this.declaredType,
        meetAttributes(this.attribute, other.attribute));
  }

  static Property meet(Property p1, Property p2) {
    return new Property(
        JSType.meet(p1.inferredType, p2.inferredType),
        null,
        meetAttributes(p1.attribute, p2.attribute));
  }

  static Property join(Property p1, Property p2) {
    JSType declType, p1decl = p1.declaredType, p2decl = p2.declaredType;
    if (p1decl == null || p2decl == null) {
      declType = null;
    } else if (p1decl.equals(p2decl)) {
      declType = p1decl;
    } else {
      declType = null;
    }
    return new Property(
        JSType.join(p1.inferredType, p2.inferredType),
        declType,
        joinAttributes(p1.attribute, p2.attribute));
  }

  /**
   * Unify the two types bidirectionally, ignoring type variables, but
   * treating JSType.UNKNOWN as a "hole" to be filled.
   * @return The unified type, or null if unification fails
   */
  static Property unifyUnknowns(Property p1, Property p2) {
    JSType unifiedDeclaredType = null;
    if (p1.declaredType != null && p2.declaredType != null) {
      unifiedDeclaredType =
        JSType.unifyUnknowns(p1.declaredType, p2.declaredType);
      if (unifiedDeclaredType == null) {
        return null;
      }
    }
    JSType unifiedInferredType =
        JSType.unifyUnknowns(p1.inferredType, p2.inferredType);
    if (unifiedInferredType == null) {
      return null;
    }
    return new Property(
        unifiedInferredType, unifiedDeclaredType,
        meetAttributes(p1.attribute, p2.attribute));
  }

  /** Returns whether unification succeeded */
  boolean unifyWith(
      Property other,
      List<String> typeParameters,
      Multimap<String, JSType> typeMultimap) {
    if (!inferredType.unifyWith(
        other.inferredType, typeParameters, typeMultimap)) {
      return false;
    } else if (declaredType != null && other.declaredType != null &&
        !declaredType.unifyWith(
        other.declaredType, typeParameters, typeMultimap)) {
      return false;
    }
    return true;
  }

  Property substituteGenerics(Map<String, JSType> concreteTypes) {
    if (concreteTypes.isEmpty()) {
      return this;
    }
    return new Property(
        inferredType.substituteGenerics(concreteTypes),
        declaredType == null ?
        null : declaredType.substituteGenerics(concreteTypes),
        attribute);
  }

  @Override
  public String toString() {
    return appendTo(new StringBuilder()).toString();
  }

  public StringBuilder appendTo(StringBuilder builder) {
    switch (attribute) {
      case CONSTANT:
        return inferredType.appendTo(builder).append('^');
      case REQUIRED:
        return inferredType.appendTo(builder);
      case OPTIONAL:
        return inferredType.appendTo(builder).append('=');
      default:
        throw new RuntimeException("Unknown Attribute value " + attribute);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (this == o) {
      return true;
    }
    Preconditions.checkArgument(o instanceof Property);
    Property p2 = (Property) o;
    return inferredType.equals(p2.inferredType) &&
        attribute == p2.attribute;
  }

  @Override
  public int hashCode() {
    return Objects.hash(inferredType, attribute);
  }
}
