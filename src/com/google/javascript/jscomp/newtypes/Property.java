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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;

import java.util.List;
import java.util.Map;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
class Property {
  private final JSType inferredType;
  private final JSType declaredType;
  private boolean isOptional;

  Property(JSType inferredType, JSType declaredType, boolean isOptional) {
    Preconditions.checkArgument(inferredType != null);
    this.inferredType = inferredType;
    this.declaredType = declaredType;
    this.isOptional = isOptional;
  }

  boolean isOptional() {
    return isOptional;
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
    return new Property(inferredType, declaredType, true);
  }

  Property withRequired() {
    return new Property(inferredType, declaredType, false);
  }

  Property specialize(Property other) {
    return new Property(
        this.inferredType.specialize(other.inferredType),
        this.declaredType,
        this.isOptional && other.isOptional);
  }

  static Property meet(Property p1, Property p2) {
    return new Property(
        JSType.meet(p1.inferredType, p2.inferredType),
        null,
        p1.isOptional && p2.isOptional);
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
        p1.isOptional || p2.isOptional);
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
        p1.isOptional && p2.isOptional);
  }

  /** Returns whether unification succeeded */
  boolean unifyWith(
      Property other,
      List<String> templateVars,
      Multimap<String, JSType> typeMultimap) {
    if (!inferredType.unifyWith(
        other.inferredType, templateVars, typeMultimap)) {
      return false;
    } else if (declaredType != null && other.declaredType != null &&
        !declaredType.unifyWith(
        other.declaredType, templateVars, typeMultimap)) {
      return false;
    }
    return true;
  }

  Property substituteGenerics(Map<String, JSType> concreteTypes) {
    return new Property(
        inferredType.substituteGenerics(concreteTypes),
        declaredType == null ?
        null : declaredType.substituteGenerics(concreteTypes),
        isOptional);
  }

  @Override
  public String toString() {
    return inferredType.toString() + (isOptional ? "=" : "");
  }

  @Override
  public boolean equals(Object o) {
    Preconditions.checkArgument(o instanceof Property);
    Property p2 = (Property) o;
    return inferredType.equals(p2.inferredType) && isOptional == p2.isOptional;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(inferredType, isOptional);
  }
}
