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
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Iterator;
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
    Iterator<TypeEnv> envsIter = envs.iterator();
    TypeEnv firstEnv = envsIter.next();
    if (!envsIter.hasNext()) {
      return firstEnv;
    }
    PersistentMap<String, JSType> newMap = firstEnv.typeMap;

    while (envsIter.hasNext()) {
      TypeEnv env = envsIter.next();
      for (Map.Entry<String, JSType> entry : env.typeMap.entrySet()) {
        String name = entry.getKey();
        // TODO(dimvar):
        // If the iteration order in the type envs is guaranteed to get the
        // keys in the same order for any env, then we can iterate through the
        // two type envs at the same time, to avoid the map lookup here.
        JSType currentType = newMap.get(name);
        JSType otherType = entry.getValue();
        Preconditions.checkNotNull(
            currentType, "%s is missing from an env", name);
        if (!currentType.equals(otherType)) {
          newMap = newMap.with(name, JSType.join(currentType, otherType));
        }
      }
    }
    return new TypeEnv(newMap);
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
