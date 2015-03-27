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

import com.google.common.base.MoreObjects;
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
public final class TypeEnv {
  private final PersistentMap<String, JSType> typeMap;

  // Above this threshold, the type env keeps track of which variables have
  // changed, in order to improve the speed of joins.
  private static final int SIZE_THRESHOLD = 100;
  private PersistentSet<String> changedVars = null;

  public TypeEnv() {
    this.typeMap = PersistentMap.create();
  }

  private TypeEnv(PersistentMap<String, JSType> typeMap) {
    this.typeMap = typeMap;
    if (typeMap.size() >= SIZE_THRESHOLD) {
      this.changedVars = PersistentSet.create();
    }
  }

  private TypeEnv(PersistentMap<String, JSType> typeMap,
      PersistentSet<String> changedVars) {
    Preconditions.checkState(typeMap.size() >= SIZE_THRESHOLD);
    this.typeMap = typeMap;
    this.changedVars = changedVars;
  }

  public JSType getType(String n) {
    Preconditions.checkArgument(!n.contains("."));
    return typeMap.get(n);
  }

  public TypeEnv putType(String n, JSType t) {
    Preconditions.checkArgument(!n.contains("."));
    Preconditions.checkArgument(t != null);
    if (changedVars == null) {
      return new TypeEnv(typeMap.with(n, t));
    }
    JSType oldType = typeMap.get(n);
    if (oldType == null) {
      // The environment is being initialized; don't keep a log yet.
      return new TypeEnv(typeMap.with(n, t));
    } else if (t.equals(oldType)) {
      return this;
    } else {
      return new TypeEnv(typeMap.with(n, t), changedVars.with(n));
    }
  }

  // This method clears the change log, so it can change the correctness
  // because it influences what is joined.
  // In NewTypeInference, we call it only from statements at the top level of a
  // scope, where we know that we are not in a branch, so we can forget the log.
  public TypeEnv clearChangeLog() {
    if (changedVars == null || changedVars.isEmpty()) {
      return this;
    }
    return new TypeEnv(typeMap);
  }

  public static TypeEnv join(TypeEnv e1, TypeEnv e2) {
    return join(ImmutableSet.of(e1, e2));
  }

  // In NewTypeInference, we call join with a set of type envs.
  // This is OK because we use reference equality for type envs.
  // Don't override equals to do logical equality!
  public static TypeEnv join(Collection<TypeEnv> envs) {
    Preconditions.checkArgument(!envs.isEmpty());
    Iterator<TypeEnv> envsIter = envs.iterator();
    TypeEnv firstEnv = envsIter.next();

    if (!envsIter.hasNext()) {
      return firstEnv;
    }
    PersistentMap<String, JSType> newMap = firstEnv.typeMap;

    if (firstEnv.changedVars == null) {
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

    PersistentSet<String> newLog = PersistentSet.create();
    for (TypeEnv env : envs) {
      for (String varName : env.changedVars) {
        newLog = newLog.with(varName);
      }
    }
    while (envsIter.hasNext()) {
      TypeEnv env = envsIter.next();
      for (String changedVar : newLog) {
        JSType currentType = newMap.get(changedVar);
        JSType otherType = env.typeMap.get(changedVar);
        if (!currentType.equals(otherType)) {
          newMap = newMap.with(changedVar, JSType.join(currentType, otherType));
        }
      }
    }
    return new TypeEnv(newMap, newLog);
  }

  @Override
  public String toString() {
    MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
    for (String key : typeMap.keySet()) {
      helper.add(key, getType(key));
    }
    return helper.toString();
  }
}
