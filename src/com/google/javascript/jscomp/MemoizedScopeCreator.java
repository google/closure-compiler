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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.StaticSymbolTable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Memoize a scope creator.
 *
 * This allows you to make multiple passes, without worrying about
 * the expense of generating Scope objects over and over again.
 *
 * On the other hand, you also have to be more aware of what your passes
 * are doing. Scopes are memoized stupidly, so if the underlying tree
 * changes, the scope may be out of sync.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class MemoizedScopeCreator
    implements ScopeCreator, StaticSymbolTable<Var, Var> {

  private final Map<Node, Scope> scopes = Maps.newLinkedHashMap();
  private final ScopeCreator delegate;

  /**
   * @param delegate The real source of Scope objects.
   */
  MemoizedScopeCreator(ScopeCreator delegate) {
    this.delegate = delegate;
  }

  @Override
  public Iterable<Var> getReferences(Var var) {
    return ImmutableList.of(var);
  }

  @Override
  public Scope getScope(Var var) {
    return var.scope;
  }

  @Override
  public Iterable<Var> getAllSymbols() {
    List<Var> vars = Lists.newArrayList();
    for (Scope s : scopes.values()) {
      Iterables.addAll(vars, s.getAllSymbols());
    }
    return vars;
  }

  @Override
  public Scope createScope(Node n, Scope parent) {
    Scope scope = scopes.get(n);
    if (scope == null) {
      scope = delegate.createScope(n, parent);
      scopes.put(n, scope);
    } else {
      Preconditions.checkState(parent == scope.getParent());
    }
    return scope;
  }

  Collection<Scope> getAllMemoizedScopes() {
    // Return scopes in reverse order of creation so that IIFEs will
    // come before the global scope.
    List<Scope> temp = Lists.newArrayList(scopes.values());
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
