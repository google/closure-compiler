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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSymbolTable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memoize a scope creator.
 *
 * This allows you to make multiple passes, without worrying about
 * the expense of generating Scope objects over and over again.
 *
 * <p>On the other hand, you also have to be more aware of what your passes
 * are doing. Scopes are memoized stupidly, so if the underlying tree
 * changes, the scope may be out of sync.
 *
 * <p>Only used to memoize typed scope creators, not untyped ones.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class MemoizedScopeCreator implements ScopeCreator, StaticSymbolTable<TypedVar, TypedVar> {

  private final Map<Node, TypedScope> scopes = new LinkedHashMap<>();
  private final ScopeCreator delegate;

  /**
   * @param delegate The real source of Scope objects.
   */
  MemoizedScopeCreator(ScopeCreator delegate) {
    this.delegate = delegate;
  }

  @Override
  public Iterable<TypedVar> getReferences(TypedVar var) {
    return ImmutableList.of(var);
  }

  @Override
  public TypedScope getScope(TypedVar var) {
    return var.scope;
  }

  @Override
  public Iterable<TypedVar> getAllSymbols() {
    List<TypedVar> vars = new ArrayList<>();
    for (TypedScope s : scopes.values()) {
      Iterables.addAll(vars, s.getAllSymbols());
    }
    return vars;
  }

  @Override
  @SuppressWarnings("unchecked")
  // ScopeCreator#createScope has type: <T extends Scope> T createScope(...);
  // TypedScope is the only subclass of Scope, so the suppression is safe.
  public TypedScope createScope(Node n, Scope parent) {
    Preconditions.checkArgument(parent == null || parent instanceof TypedScope);
    TypedScope typedParent = (TypedScope) parent;
    TypedScope scope = scopes.get(n);
    if (scope == null) {
      scope = (TypedScope) delegate.createScope(n, typedParent);
      scopes.put(n, scope);
    } else {
      Preconditions.checkState(typedParent == scope.getParent());
    }
    return scope;
  }

  Collection<TypedScope> getAllMemoizedScopes() {
    // Return scopes in reverse order of creation so that IIFEs will
    // come before the global scope.
    List<TypedScope> temp = new ArrayList<>(scopes.values());
    Collections.reverse(temp);
    return Collections.unmodifiableCollection(temp);
  }

  /**
   * Removes all scopes with root nodes from a given script file.
   *
   * @param scriptName the name of the script file to remove nodes for.
   */
  void removeScopesForScript(String scriptName) {
    for (Node scopeRoot : ImmutableSet.copyOf(scopes.keySet())) {
      if (scriptName.equals(scopeRoot.getSourceFileName())) {
        scopes.remove(scopeRoot);
      }
    }
  }

  @Override
  public boolean hasBlockScope() {
    return delegate.hasBlockScope();
  }
}
