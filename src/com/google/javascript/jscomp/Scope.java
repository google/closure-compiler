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

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scope contains information about a variable scope in JavaScript.
 * Scopes can be nested, a scope points back to its parent scope.
 * A Scope contains information about variables defined in that scope.
 *
 * @see NodeTraversal
 *
 */
public class Scope implements StaticScope {
  private final Map<String, Var> vars = new LinkedHashMap<>();
  private final Scope parent;
  protected final int depth;
  protected final Node rootNode;
  private Var arguments;

  /**
   * Creates a Scope given the parent Scope and the root node of the scope.
   * @param parent  The parent Scope. Cannot be null.
   * @param rootNode  Typically the FUNCTION node.
   */
  Scope(Scope parent, Node rootNode) {
    Preconditions.checkNotNull(parent);
    Preconditions.checkArgument(rootNode != parent.rootNode);

    this.parent = parent;
    this.rootNode = rootNode;
    this.depth = parent.depth + 1;
  }

  protected Scope(Node rootNode) {
    this.parent = null;
    this.rootNode = rootNode;
    this.depth = 0;
  }

  static Scope createGlobalScope(Node rootNode) {
    return new Scope(rootNode);
  }

  /** The depth of the scope. The global scope has depth 0. */
  int getDepth() {
    return depth;
  }

  /**
   * Gets the container node of the scope. This is typically the FUNCTION
   * node or the global BLOCK/SCRIPT node.
   */
  @Override
  public Node getRootNode() {
    return rootNode;
  }

  public Scope getParent() {
    return parent;
  }

  Scope getGlobalScope() {
    Scope result = this;
    while (result.getParent() != null) {
      result = result.getParent();
    }
    return result;
  }

  @Override
  public StaticScope getParentScope() {
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
    Preconditions.checkState(name != null && !name.isEmpty());
    // Make sure that it's declared only once
    Preconditions.checkState(vars.get(name) == null);
    Var var = new Var(name, nameNode, this, vars.size(), input);
    vars.put(name, var);
    return var;
  }

  /**
   * Undeclares a variable, to be used when the compiler optimizes out
   * a variable and removes it from the scope.
   */
  void undeclare(Var var) {
    Preconditions.checkState(var.scope == this);
    Preconditions.checkState(vars.get(var.name) == var);
    vars.remove(var.name);
  }

  @Override
  public Var getSlot(String name) {
    return getVar(name);
  }

  @Override
  public Var getOwnSlot(String name) {
    return vars.get(name);
  }

  /**
   * Returns the variable, may be null
   */
  public Var getVar(String name) {
    Scope scope = this;
    while (scope != null) {
      Var var = scope.vars.get(name);
      if (var != null) {
        return var;
      }
      // Recurse up the parent Scope
      scope = scope.parent;
    }
    return null;
  }

  /**
   * Get a unique VAR object to represents "arguments" within this scope
   */
  public Var getArgumentsVar() {
    if (arguments == null) {
      arguments = Var.makeArgumentsVar(this);
    }
    return arguments;
  }

  /**
   * Returns true if a variable is declared.
   */
  public boolean isDeclared(String name, boolean recurse) {
    Scope scope = this;
    while (true) {
      if (scope.vars.containsKey(name)) {
        return true;
      }
      if (scope.parent != null && recurse) {
        scope = scope.parent;
        continue;
      }
      return false;
    }
  }

  /**
   * Return an iterator over all of the variables declared in this scope.
   */
  @SuppressWarnings("unchecked")
  // Untyped scopes always only contain untyped vars; getVars is polymorphic
  // so that TypedScope#getVars can return Iterator<TypedVar>.
  public <T extends Var> Iterator<T> getVars() {
    return (Iterator<T>) vars.values().iterator();
  }

  /**
   * Return an iterable over all of the variables declared in this scope.
   */
  Iterable<Var> getVarIterable() {
    return vars.values();
  }

  public Iterable<? extends Var> getAllSymbols() {
    return Collections.unmodifiableCollection(vars.values());
  }

  /**
   * Returns number of variables in this scope
   */
  public int getVarCount() {
    return vars.size();
  }

  /**
   * Returns whether this is the global scope.
   */
  public boolean isGlobal() {
    return parent == null;
  }

  /**
   * Returns whether this is a local scope (i.e. not the global scope).
   */
  public boolean isLocal() {
    return parent != null;
  }

  public boolean isBlockScope() {
    return NodeUtil.createsBlockScope(rootNode);
  }

  /**
   * A hoist scope is the hoist target for enclosing var declarations. It is
   * either the top-level block of a function, a global scope, or a module scope.
   *
   * TODO(moz): Module scopes are not global, but are also hoist targets.
   * Support them once module is implemented.
   *
   * @return Whether the scope is a hoist target for var declarations.
   */
  public boolean isHoistScope() {
    return isFunctionBlockScope() || isGlobal();
  }

  public boolean isFunctionBlockScope() {
    return isBlockScope() && parent != null && parent.getRootNode().isFunction();
  }

  public Scope getClosestHoistScope() {
    Scope current = this;
    while (current != null) {
      if (current.isHoistScope()) {
        return current;
      }
      current = current.parent;
    }
    return null;
  }
}
