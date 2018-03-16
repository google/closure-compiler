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
public abstract class Scope extends AbstractScope<Scope, Var> {

  Scope(Node rootNode) {
    super(rootNode);
  }

  @Override
  public Scope untyped() {
    return this;
  }

  static Scope createGlobalScope(Node rootNode) {
    return new Scope.Simple(rootNode);
  }

  static Scope createChildScope(Scope parent, Node rootNode) {
    return new Scope.Simple(parent, rootNode);
  }

  /**
   * Declares a variable.
   *
   * @param name name of the variable
   * @param nameNode the NAME node declaring the variable
   * @param input the input in which this variable is defined.
   */
  // Non-final for PersisteneScope.
  Var declare(String name, Node nameNode, CompilerInput input) {
    checkArgument(!name.isEmpty());
    // Make sure that it's declared only once
    checkState(getOwnSlot(name) == null);
    Var var = new Var(name, nameNode, this, getVarCount(), input);
    declareInternal(name, var);
    return var;
  }

  @Override
  Var makeImplicitVar(ImplicitVar var) {
    return new Var(var.name, null /* nameNode */, this, -1 /* index */, null /* input */);
  }

  private static final class Simple extends Scope {
    final Scope parent;
    final int depth;

    Simple(Scope parent, Node rootNode) {
      super(rootNode);
      checkChildScope(parent);
      this.parent = parent;
      this.depth = parent.getDepth() + 1;
    }

    Simple(Node rootNode) {
      super(rootNode);
      checkRootScope();
      this.parent = null;
      this.depth = 0;
    }

    @Override
    public int getDepth() {
      return depth;
    }

    @Override
    public Scope getParent() {
      return parent;
    }
  }
}
