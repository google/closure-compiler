/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the type-parameter names of a generic function.
 * If the function uses TTL, this class also knows about the TTL AST of each type parameter.
 * We do not use this class for generic nominal types, because they do not use TTL.
 */
public final class TypeParameters implements Serializable {
  /**
   * If a type parameter is a TTL parameter, we map it to its TTL AST. Otherwise, to an empty node.
   */
  private final ImmutableMap<String, Node> typeParams;
  static final TypeParameters EMPTY = new TypeParameters(ImmutableMap.<String, Node>of());

  private TypeParameters(Map<String, Node> typeParams) {
    this.typeParams = ImmutableMap.copyOf(typeParams);
  }

  static TypeParameters make(List<String> ordinaryTypeParams) {
    return make(ordinaryTypeParams, ImmutableMap.<String, Node>of());
  }

  static TypeParameters make(List<String> ordinaryTypeParams, Map<String, Node> ttlParams) {
    ImmutableMap.Builder<String, Node> builder = ImmutableMap.builder();
    for (String typeParam : ordinaryTypeParams) {
      builder.put(typeParam, IR.empty());
    }
    builder.putAll(ttlParams);
    return new TypeParameters(builder.build());
  }

  ImmutableList<String> asList() {
    return this.typeParams.keySet().asList();
  }

  /**
   * Returns a non-null list of type variables, by filtering out the TTL variables from
   * this.typeParams.
   */
  ImmutableList<String> getOrdinaryTypeParams() {
    boolean foundTtlVariable = false;
    for (Map.Entry<String, Node> entry : this.typeParams.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        foundTtlVariable = true;
        break;
      }
    }
    if (foundTtlVariable) {
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      for (Map.Entry<String, Node> entry : this.typeParams.entrySet()) {
        if (entry.getValue().isEmpty()) {
          builder.add(entry.getKey());
        }
      }
      return builder.build();
    }
    return this.typeParams.keySet().asList();
  }

  /**
   * Returns a non-null map from TTL variables to their ASTs, by filtering out the non-TTL variables
   * from this.typeParams.
   */
  public ImmutableMap<String, Node> getTypeTransformations() {
    boolean foundOrdinaryTypeVariable = false;
    for (Map.Entry<String, Node> entry : this.typeParams.entrySet()) {
      if (entry.getValue().isEmpty()) {
        foundOrdinaryTypeVariable = true;
        break;
      }
    }
    if (foundOrdinaryTypeVariable) {
      ImmutableMap.Builder<String, Node> builder = ImmutableMap.builder();
      for (Map.Entry<String, Node> entry : this.typeParams.entrySet()) {
        if (!entry.getValue().isEmpty()) {
          builder.put(entry);
        }
      }
      return builder.build();
    }
    return this.typeParams;
  }

  boolean isEmpty() {
    return this.typeParams.isEmpty();
  }

  boolean contains(String typeParam) {
    return this.typeParams.containsKey(typeParam);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof TypeParameters && this.typeParams.equals(((TypeParameters) o).typeParams);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.typeParams);
  }
}
