/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.collect.Sets;
import com.google.javascript.jscomp.Scope.Var;

import java.util.Iterator;
import java.util.Set;

/**
 * Generates new variables names that would not collide with existing names in
 * a scope.
 *
 *
 */
class VariableNameGenerator {
  private final NameGenerator names;
  VariableNameGenerator(Scope scope) {
    Set<String> usedNames = Sets.newHashSet();
    for (Iterator<Var> i = scope.getVars(); i.hasNext();) {
      usedNames.add(i.next().getName());
    }
    names = new NameGenerator(usedNames, "", null);
  }

  String getNameNewName() {
    return names.generateNextName();
  }
}
