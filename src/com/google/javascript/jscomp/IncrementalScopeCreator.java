/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.Es6SyntacticScopeCreator.ScopeScanner;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A reusable scope creator which invalidates scopes based on reported
 * AST changes to SCRIPT and FUNCTION codes (aka "change scopes").  This
 * class stores an instance of itself on the compiler object which is accessible via
 * the "getInstance" static method. To ensure that consumers see a consistent state,
 * they must call "freeze"/"thaw" before and after use (typically for the duration
 * of a NodeTraveral).
 *
 * This class delegates to the Es6SyntacticScopeCreator and requires a consistent
 * definition of global Scope (the global scope root must include both externs and code).
 */
class IncrementalScopeCreator implements ScopeCreator {

  private final AbstractCompiler compiler;
  // TODO(johnlenz): This leaks scope object for scopes removed from the AST.
  // Soon we will track removed function nodes use that to remove scopes.
  private final Map<Node, PeristentScope> scopesByScopeRoot = new HashMap<>();
  private final Es6SyntacticScopeCreator delegate;

  private boolean frozen;

  private IncrementalScopeCreator(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.delegate = createInternalScopeCreator(compiler);
  }

  // Get an instance of the ScopeCreator
  public static IncrementalScopeCreator getInstance(AbstractCompiler compiler) {
    IncrementalScopeCreator creator = compiler.getScopeCreator();
    if (creator == null) {
      creator = new IncrementalScopeCreator(compiler);
      compiler.putScopeCreator(creator);
    }
    return creator;
  }

  public IncrementalScopeCreator freeze() {
    checkState(!this.frozen, "inconsistent freeze state: already frozen");
    frozen = true;
    invalidateChangedScopes();
    return this;
  }

  public IncrementalScopeCreator thaw() {
    checkState(this.frozen, "inconsistent freeze state: already thaw'd");
    frozen = false;
    return this;
  }

  private void invalidateChangedScopes() {
    List<Node> changedRoots = compiler.getChangedScopeNodesForPass("Scopes");
    if (changedRoots != null) {
      for (Node root : changedRoots) {
        if (root.isScript()) {
          invalidateScript(root);
        } else {
          invalidateRoot(root);
        }
      }
    }
  }

  private void invalidateScript(Node n) {
    Node root = n.getParent().getParent();
    // TODO(johnlenz): break global scope into a series of merged script declarations.
    invalidateRoot(root);
  }

  private void invalidateRoot(Node n) {
    PeristentScope scope = scopesByScopeRoot.get(n);
    if (scope != null) {
      scope.invalidate();
    }
  }

  @Override
  public Scope createScope(Node n, Scope parent) {
    checkState(parent == null || parent instanceof PeristentScope);
    checkState(parent == null || ((PeristentScope) parent).isValid(), "parent is not valid");
    checkState(frozen, "freeze() must be called before retrieving scopes");
    PeristentScope scope = scopesByScopeRoot.get(n);
    if (scope == null) {
      scope = (PeristentScope) delegate.createScope(n, parent);
      scopesByScopeRoot.put(n, scope);
    } else {
      scope.refresh(compiler, (PeristentScope) parent);
    }
    checkState(scope.isValid(), "scope is not valid");
    return scope;
  }

  @Override
  public boolean hasBlockScope() {
    return delegate.hasBlockScope();
  }

  /**
   * A subclass of the traditional Scope class that knows about its children,
   * and has methods for updating the scope heirarchy.
   */
  private static class PeristentScope extends Scope {
    // A list of Scope within the "change scope" (those not crossing function boundaries)
    // which were added to this scope.
    List<PeristentScope> validChildren = new ArrayList<>();
    boolean valid = true; // starts as valid

    PeristentScope(PeristentScope parent, Node rootNode) {
      super(parent, rootNode);
      parent.addChildScope(this);
    }

    protected PeristentScope(Node rootNode) {
      super(rootNode);
      checkArgument(rootNode.isRoot());
    }

    static PeristentScope create(PeristentScope parent, Node rootNode) {
      if (parent == null) {
        checkArgument(rootNode.isRoot() && rootNode.getParent() == null, rootNode);
        return new PeristentScope(rootNode);
      } else {
        return new PeristentScope(parent, rootNode);
      }
    }

    private void addChildScope(PeristentScope scope) {
      // Keep track of valid children within the "change scope".
      if (!NodeUtil.isChangeScopeRoot(scope.getRootNode())) {
        validChildren.add(scope);
      }
    }

    public boolean isValid() {
      return valid;
    }

    private void invalidate() {
      if (valid) {
        valid = false;
        for (PeristentScope child : validChildren) {
          checkState(!NodeUtil.isChangeScopeRoot(child.getRootNode()));
          child.invalidate();
        }
        validChildren.clear();
      }
    }

    void refresh(AbstractCompiler compiler, PeristentScope newParent) {
      checkArgument(newParent == null || newParent.isValid());
      checkState((parent == null) == (newParent == null));

      // Even if this scope hasn't been invalidated, its parent scopes may have,
      // so we update the scope chaining.
      if (parent != null && (!valid || this.parent != newParent)) {
        this.parent = newParent;
        if (!valid) {
          // TODO(johnlenz): It doesn't really matter which
          // parent scope in the "change scope" invalidates this scope,
          // but if we were previously invalidated no parent
          // has this instance in its list, so add it to the new parent.
          getParent().addChildScope(this);
        }
        // Even if the parent hasn't changed the depth might have, update it now.
        this.depth = parent.getDepth() + 1;
      }

      // Update the scope if needed.
      if (!this.valid) {
        vars.clear();
        // TODO(johnlenz): consider merging Es6SyntacticScopeCreator with
        // scope so that refreshing the scope is not quite so awkward. The scope
        // should know how to refresh itself.
        new ScopeScanner(compiler, this).populate();
        this.valid = true;
      }
    }

    @Override
    public PeristentScope getParent() {
      PeristentScope parent = (PeristentScope) super.getParent();
      checkState(parent == null || parent.valid, "parent scope is not valid");
      // The node traversal should ask for scopes in order, so parents should always be valid.
      return parent;
    }
  }

  Es6SyntacticScopeCreator createInternalScopeCreator(AbstractCompiler compiler) {
    return new Es6SyntacticScopeCreator(compiler, new PersistentScopeFactory());
  }

  private static class PersistentScopeFactory implements Es6SyntacticScopeCreator.ScopeFactory {
    @Override
    public PeristentScope create(Scope parent, Node n) {
      return PeristentScope.create((PeristentScope) parent, n);
    }
  }
}
