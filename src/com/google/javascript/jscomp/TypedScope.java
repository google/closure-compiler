/*
 * Copyright 2015 The Closure Compiler Authors.
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
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedScope;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TypedScope contains information about variables and their types.
 * Scopes can be nested, a scope points back to its parent scope.
 * <p>
 * TypedScope is also used as a lattice element for flow-sensitive type inference.
 * As a lattice element, a scope is viewed as a map from names to types. A name
 * not in the map is considered to have the bottom type. The join of two maps m1
 * and m2 is the map of the union of names with {@link JSType#getLeastSupertype}
 * to meet the m1 type and m2 type.
 *
 * @see NodeTraversal
 * @see DataFlowAnalysis
 *
 * Several methods in this class, such as {@code isBlockScope} throw an exception when called.
 * The reason for this is that we want to shadow methods from the parent class, to avoid calling
 * them accidentally.
 */
public class TypedScope extends Scope implements StaticTypedScope<JSType> {
  private final Map<String, TypedVar> vars = new LinkedHashMap<>();
  private final TypedScope parent;
  /** Whether this is a bottom scope for the purposes of type inference. */
  private final boolean isBottom;
  // Scope.java contains an arguments field.
  // We haven't added it here because it's unused by the passes that need typed scopes.

  private static final Predicate<TypedVar> DECLARATIVELY_UNBOUND_VARS_WITHOUT_TYPES =
    new Predicate<TypedVar>() {
      @Override
      public boolean apply(TypedVar var) {
        return var.getParentNode() != null
          && var.getType() == null
          && var.getParentNode().isVar()
          && !var.isExtern();
      }
    };

  TypedScope(TypedScope parent, Node rootNode) {
    super(parent, rootNode);
    this.parent = parent;
    this.isBottom = false;
  }

  /**
   * Creates a empty Scope (bottom of the lattice).
   * @param rootNode Typically a FUNCTION node or the global BLOCK node.
   * @param isBottom Whether this is the bottom of a lattice. Otherwise,
   *     it must be a global scope.
   */
  private TypedScope(Node rootNode, boolean isBottom) {
    super(rootNode);
    this.parent = null;
    this.isBottom = isBottom;
  }

  static TypedScope createGlobalScope(Node rootNode) {
    return new TypedScope(rootNode, false);
  }

  static TypedScope createLatticeBottom(Node rootNode) {
    return new TypedScope(rootNode, true);
  }

  /** Whether this is the bottom of the lattice. */
  boolean isBottom() {
    return isBottom;
  }

  @Override
  int getDepth() {
    return depth;
  }

  @Override
  public Node getRootNode() {
    return rootNode;
  }

  @Override
  public TypedScope getParent() {
    return parent;
  }

  @Override
  TypedScope getGlobalScope() {
    TypedScope result = this;
    while (result.getParent() != null) {
      result = result.getParent();
    }
    return result;
  }

  @Override
  public StaticTypedScope<JSType> getParentScope() {
    return parent;
  }

  /**
   * Gets the type of {@code this} in the current scope.
   */
  @Override
  public JSType getTypeOfThis() {
    if (isGlobal()) {
      return ObjectType.cast(rootNode.getJSType());
    }

    Preconditions.checkState(rootNode.isFunction());
    JSType nodeType = rootNode.getJSType();
    if (nodeType != null && nodeType.isFunctionType()) {
      return nodeType.toMaybeFunctionType().getTypeOfThis();
    } else {
      // Executed when the current scope has not been typechecked.
      return null;
    }
  }

  Var declare(String name, Node nameNode, CompilerInput input) {
    throw new IllegalStateException(
        "Method declare(untyped) cannot be called on typed scopes.");
  }

  TypedVar declare(String name, Node nameNode, JSType type, CompilerInput input) {
    return declare(name, nameNode, type, input, true);
  }

  TypedVar declare(String name, Node nameNode,
      JSType type, CompilerInput input, boolean inferred) {
    Preconditions.checkState(name != null && !name.isEmpty());
    TypedVar var = new TypedVar(inferred, name, nameNode, type, this, vars.size(), input);
    vars.put(name, var);
    return var;
  }

  @Override
  void undeclare(Var var) {
    TypedVar tvar = (TypedVar) var;
    Preconditions.checkState(tvar.scope == this);
    Preconditions.checkState(vars.get(tvar.name) == tvar);
    vars.remove(tvar.name);
  }

  @Override
  public TypedVar getSlot(String name) {
    return getVar(name);
  }

  @Override
  public TypedVar getOwnSlot(String name) {
    return vars.get(name);
  }

  @Override
  public TypedVar getVar(String name) {
    TypedScope scope = this;
    while (scope != null) {
      TypedVar var = scope.vars.get(name);
      if (var != null) {
        return var;
      }
      // Recurse up the parent Scope
      scope = scope.parent;
    }
    return null;
  }

  public Var getArgumentsVar() {
    throw new IllegalStateException("Method getArgumentsVar cannot be called on typed scopes.");
  }

  @Override
  public boolean isDeclared(String name, boolean recurse) {
    TypedScope scope = this;
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

  @Override
  public Iterator<TypedVar> getVars() {
    return vars.values().iterator();
  }

  Iterable<Var> getVarIterable() {
    throw new IllegalStateException("Method getVarIterable cannot be called on typed scopes.");
  }

  @Override
  public Iterable<TypedVar> getAllSymbols() {
    return Collections.unmodifiableCollection(vars.values());
  }

  @Override
  public int getVarCount() {
    return vars.size();
  }

  @Override
  public boolean isGlobal() {
    return parent == null;
  }

  @Override
  public boolean isLocal() {
    return parent != null;
  }

  public Iterator<TypedVar> getDeclarativelyUnboundVarsWithoutTypes() {
    return Iterators.filter(getVars(), DECLARATIVELY_UNBOUND_VARS_WITHOUT_TYPES);
  }

  static interface TypeResolver {
    void resolveTypes();
  }

  private TypeResolver typeResolver;

  /** Resolve all type references. Only used on typed scopes. */
  void resolveTypes() {
    if (typeResolver != null) {
      typeResolver.resolveTypes();
      typeResolver = null;
    }
  }

  void setTypeResolver(TypeResolver resolver) {
    this.typeResolver = resolver;
  }

  public boolean isBlockScope() {
    throw new IllegalStateException("Method isBlockScope cannot be called on typed scopes.");
  }

  public boolean isHoistScope() {
    throw new IllegalStateException("Method isHoistScope cannot be called on typed scopes.");
  }

  public boolean isFunctionBlockScope() {
    throw new IllegalStateException(
        "Method isFunctionBlockScope cannot be called on typed scopes.");
  }

  public Scope getClosestHoistScope() {
    throw new IllegalStateException(
        "Method getClosestHoistScope cannot be called on typed scopes.");
  }
}
