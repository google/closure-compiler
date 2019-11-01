/*
 * Copyright 2008 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

import com.google.common.collect.ImmutableSet;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Group a set of related diagnostic types together, so that they can be toggled on and off as one
 * unit.
 */
public final class DiagnosticGroup implements Serializable {
  private static final long serialVersionUID = 1;

  // The set of types represented by this group, hashed by key.
  private final ImmutableSet<DiagnosticType> types;

  // A human-readable name for the group.
  private final String name;

  /**
   * Create a group that matches all errors of the given types.
   */
  DiagnosticGroup(String name, DiagnosticType ...types) {
    this.name = name;
    this.types = ImmutableSet.copyOf(Arrays.asList(types));
  }

  /**
   * Create a group that matches all errors of the given types.
   */
  public DiagnosticGroup(DiagnosticType ...types) {
    this(null, types);
  }

  /**
   * Create a diagnostic group with no name that only matches the given type.
   */
  private DiagnosticGroup(DiagnosticType type) {
    this.name = null;
    this.types = ImmutableSet.of(type);
  }

  /** Create a composite group. */
  public DiagnosticGroup(DiagnosticGroup... groups) {
    this(null, groups);
  }

  /** Create a composite group. */
  public DiagnosticGroup(String name, DiagnosticGroup... groups) {
    ImmutableSet.Builder<DiagnosticType> set = ImmutableSet.builder();

    for (DiagnosticGroup group : groups) {
      set.addAll(group.types);
    }

    this.name = name;
    this.types = set.build();
  }

  // DiagnosticGroups with only a single DiagnosticType.
  private static final Map<DiagnosticType, DiagnosticGroup> singletons = new HashMap<>();

  /** Create a diagnostic group that matches only the given type. */
  public static synchronized DiagnosticGroup forType(DiagnosticType type) {
    singletons.computeIfAbsent(type, DiagnosticGroup::new);
    return singletons.get(type);
  }

  /**
   * Returns whether the given error's type matches a type
   * in this group.
   */
  public boolean matches(JSError error) {
    return matches(error.getType());
  }

  /**
   * Returns whether the given type matches a type in this group.
   */
  public boolean matches(DiagnosticType type) {
    return types.contains(type);
  }

  /**
   * Returns whether all of the types in the given group are in this group.
   */
  boolean isSubGroup(DiagnosticGroup group) {
    for (DiagnosticType type : group.types) {
      if (!matches(type)) {
        return false;
      }
    }
    return true;
  }

  /** Returns all the types in this group. */
  public ImmutableSet<DiagnosticType> getTypes() {
    return types;
  }

  String getName() {
    return this.name;
  }

  @Override
  public String toString() {
    return name == null ? super.toString() : "DiagnosticGroup<" + name + ">";
  }
}
