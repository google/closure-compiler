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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.Map;

/**
 * A persistent map from variables to abstract values (types)
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class TypeEnv {
  private final PersistentMap<String, JSType> typeMap;

  public TypeEnv() {
    this.typeMap = PersistentMap.create();
  }

  private TypeEnv(PersistentMap<String, JSType> typeMap) {
    this.typeMap = typeMap;
  }

  public JSType getType(String n) {
    Preconditions.checkArgument(!n.contains("."));
    return typeMap.get(n);
  }

  public TypeEnv putType(String n, JSType t) {
    Preconditions.checkArgument(!n.contains("."));
    Preconditions.checkArgument(t != null);
    return new TypeEnv(typeMap.with(n, t));
  }

  public TypeEnv split() {
    return this;
  }

  public static TypeEnv join(TypeEnv e1, TypeEnv e2) {
    return join(ImmutableSet.of(e1, e2));
  }

  public static TypeEnv join(Collection<TypeEnv> envs) {
    Preconditions.checkArgument(!envs.isEmpty());
    TypeEnv firstEnv = envs.iterator().next();
    if (envs.size() == 1) {
      return firstEnv;
    }
    PersistentMap<String, JSType> newMap = firstEnv.typeMap;
    ImmutableSet.Builder<String> keys = ImmutableSet.builder();
    for (TypeEnv env : envs) {
      keys.addAll(env.typeMap.keySet());
    }
    for (String n : keys.build()) {
      JSType joinedType = null;
      for (TypeEnv env : envs) {
        JSType otherType = env.getType(n);
        Preconditions.checkNotNull(otherType, "%s is missing from an env", n);
        if (joinedType == null) {
          joinedType = otherType;
        } else if (!joinedType.equals(otherType)) {
          joinedType = JSType.join(joinedType, otherType);
        }
      }
      newMap = newMap.with(n, joinedType);
    }
    return new TypeEnv(newMap);
  }

  public Multimap<String, String> getTaints() {
    Multimap<String, String> taints = HashMultimap.create();
    for (Map.Entry<String, JSType> entry : typeMap.entrySet()) {
      String formal = entry.getValue().getLocation();
      if (formal != null) {
        taints.put(formal, entry.getKey());
      }
    }
    return taints;
  }

  @Override
  public String toString() {
    Objects.ToStringHelper helper = Objects.toStringHelper(this.getClass());
    for (String key : typeMap.keySet()) {
      helper.add(key, getType(key));
    }
    return helper.toString();
  }
}
