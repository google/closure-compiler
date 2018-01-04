/*
 * Copyright 2004 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.Node;

/**
 * Scope contains information about a variable scope in JavaScript.
 * Scopes can be nested, a scope points back to its parent scope.
 * A Scope contains information about variables defined in that scope.
 *
 * @see NodeTraversal
 *
 */
public class Scope extends AbstractScope<Scope, Var> {

  /**
   * Creates a Scope given the parent Scope and the root node of the current scope.
   *
   * @param parent The parent Scope. Cannot be null.
   * @param rootNode The root node of the current scope. Cannot be null.
   */
  Scope(Scope parent, Node rootNode) {
    super(parent, rootNode);
  }

  Scope(Node rootNode) {
    super(rootNode);
  }

  static Scope createGlobalScope(Node rootNode) {
    // TODO(tbreisacher): Can we tighten this to allow only ROOT nodes?
    checkArgument(rootNode.isRoot() || rootNode.isScript(), rootNode);
    return new Scope(rootNode);
  }

  /**
   * Declares a variable.
   *
   * @param name name of the variable
   * @param nameNode the NAME node declaring the variable
   * @param input the input in which this variable is defined.
   */
  Var declare(String name, Node nameNode, CompilerInput input) {
    checkArgument(!name.isEmpty());
    // Make sure that it's declared only once
    checkState(vars.get(name) == null);
    Var var = new Var(name, nameNode, this, vars.size(), input);
    vars.put(name, var);
    return var;
  }

  @Override
  Var makeArgumentsVar() {
    return Var.makeArgumentsVar(this);
  }
}
