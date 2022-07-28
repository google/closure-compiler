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
import org.jspecify.nullness.Nullable;

/**
 * Scope contains information about a variable scope in JavaScript. Scopes can be nested, a scope
 * points back to its parent scope. A Scope contains information about variables defined in that
 * scope.
 *
 * @see NodeTraversal
 */
public final class Scope extends AbstractScope<Scope, Var> {

  private final @Nullable Scope parent;
  private final int depth;

  static Scope createGlobalScope(Node rootNode) {
    return new Scope(rootNode);
  }

  static Scope createChildScope(Scope parent, Node rootNode) {
    return new Scope(parent, rootNode);
  }

  private Scope(Scope parent, Node rootNode) {
    super(rootNode);
    checkChildScope(parent);
    this.parent = parent;
    this.depth = parent.getDepth() + 1;
  }

  private Scope(Node rootNode) {
    super(rootNode);
    checkRootScope();
    this.parent = null;
    this.depth = 0;
  }

  @Override
  public Scope untyped() {
    return this;
  }

  @Override
  public int getDepth() {
    return depth;
  }

  @Override
  public Scope getParent() {
    return parent;
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
    checkState(getOwnSlot(name) == null);
    Var var =
        new Var(
            name,
            nameNode,
            this,
            getVarCount(),
            input,
            /* implicitGoogNamespaceDefinition= */ null);
    declareInternal(name, var);
    return var;
  }

  /**
   * Declares an implicit goog.provide or goog.module namespace in this scope
   *
   * @param name name of the variable
   * @param definition the STRINGLIT node holding the full namespace
   */
  Var declareImplicitGoogNamespaceIfAbsent(String name, Node definition) {
    checkArgument(!name.isEmpty());
    checkState(this.isGlobal(), "Cannot declare implicit goog namespace in local scope %s", this);
    // Allow redeclarations of provides, since they are implicit and don't have a single
    // declaration site.
    Var var = getOwnSlot(name);
    if (var == null) {
      var = Var.createImplicitGoogNamespace(name, this, definition);
      declareInternal(name, var);
    } else if (var.isImplicitGoogNamespace()) {
      var.addImplicitGoogNamespaceDefinition(definition);
    }
    return var;
  }

  @Override
  Var makeImplicitVar(ImplicitVar var) {
    return new Var(
        var.name,
        /* nameNode= */ null,
        this,
        /* index= */ -1,
        /* input= */ null,
        /* implicitGoogNamespaceDefinition= */ null);
  }
}
