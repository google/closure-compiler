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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.Es6SyntacticScopeCreator.ScopeScanner;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
  private final Map<Node, PersistentScope> scopesByScopeRoot = new HashMap<>();
  private final Es6SyntacticScopeCreator delegate;

  private final PersistentScopeFactory factory = new PersistentScopeFactory();

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
    List<Node> scripts = new ArrayList<>();
    if (changedRoots != null) {
      for (Node root : changedRoots) {
        if (root.isScript()) {
          scripts.add(root);
        } else {
          checkState(!root.isRoot());
          invalidateRoot(root);
        }
      }
      invalidateScripts(scripts);
    }
  }

  private void invalidateScripts(List<Node> invalidatedScripts) {
    if (!invalidatedScripts.isEmpty()) {
      Node root = compiler.getRoot();
      PersistentGlobalScope scope = (PersistentGlobalScope) scopesByScopeRoot.get(root);
      if (scope != null) {
        scope.invalidate(invalidatedScripts);
      }
    }
  }

  private void invalidateRoot(Node n) {
    PersistentLocalScope scope = (PersistentLocalScope) scopesByScopeRoot.get(n);
    if (scope != null) {
      scope.invalidate();
    }
  }

  @Override
  public Scope createScope(Node n, AbstractScope<?, ?> parent) {
    checkState(parent == null || parent instanceof PersistentScope);
    checkState(parent == null || ((PersistentScope) parent).isValid(), "parent is not valid");
    checkState(frozen, "freeze() must be called before retrieving scopes");
    checkArgument(parent != null || n == compiler.getRoot(),
        "the shared persistent scope must always be root at the tip of the AST");

    PersistentScope scope = scopesByScopeRoot.get(n);
    if (scope == null) {
      scope = (PersistentScope) delegate.createScope(n, parent);
      scopesByScopeRoot.put(n, scope);
    } else {
      scope.refresh(compiler, (PersistentScope) parent);
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
  private abstract static class PersistentScope extends Scope {
    boolean valid = true; // starts as valid
    PersistentScope parent;
    int depth;

    PersistentScope(PersistentScope parent, Node rootNode) {
      super(rootNode);
      checkChildScope(parent);
      this.parent = parent;
      this.depth = parent.depth + 1;
    }

    PersistentScope(Node rootNode) {
      super(rootNode);
      checkArgument(rootNode.isRoot()); // Note: this is a stronger check than checkRootScope()
      this.parent = null;
      this.depth = 0;
    }

    static PersistentScope create(PersistentScope parent, Node rootNode) {
      if (parent == null) {
        checkArgument(rootNode.isRoot() && rootNode.getParent() == null, rootNode);
        return new PersistentGlobalScope(rootNode);
      } else {
        return new PersistentLocalScope(parent, rootNode);
      }
    }

    public boolean isValid() {
      return valid;
    }

    @Override
    public PersistentScope getParent() {
      checkState(parent == null || parent.valid, "parent scope is not valid");
      // The node traversal should ask for scopes in order, so parents should always be valid.
      return parent;
    }

    @Override
    public int getDepth() {
      return depth;
    }

    abstract void refresh(AbstractCompiler compiler, PersistentScope newParent);

    abstract void addChildScope(PersistentLocalScope scope);
  }


  private static class PersistentGlobalScope extends PersistentScope {
    Multimap<Node, PersistentLocalScope> validChildren = ArrayListMultimap.create();
    Set<Node> scriptsToUpdate = new HashSet<>();
    Multimap<Node, Var> scriptToVarMap = ArrayListMultimap.create();
    Multimap<Node, Node> scriptDeclarationsPairs = HashMultimap.create();
    PersistentScopeFactory factory = new PersistentScopeFactory();

    protected PersistentGlobalScope(Node rootNode) {
      super(rootNode);
      checkArgument(rootNode.isRoot() && rootNode.getParent() == null);
    }

    @Override
    void addChildScope(PersistentLocalScope scope) {
      // Only track child scopes that should be
      // invalidated when a "change scope" is changed,
      // not scopes that are themselves change scope roots.
      if (!NodeUtil.isChangeScopeRoot(scope.getRootNode())) {
        Node script = getContainingScript(scope.getRootNode());
        checkState(script.isScript());
        validChildren.put(script, scope);
      }
    }

    public void invalidate(List<Node> invalidatedScripts) {
      valid = false;
      for (Node script : invalidatedScripts) {
        checkState(script.isScript());
        // invalidate any generated child scopes
        for (PersistentLocalScope scope : validChildren.removeAll(script)) {
          scope.invalidate();
        }
      }
      scriptsToUpdate.addAll(invalidatedScripts);
    }

    @Override
    void refresh(AbstractCompiler compiler, PersistentScope newParent) {
      checkArgument(newParent == null);

      // Update the scope if needed.
      if (!this.valid) {
        checkState(!scriptsToUpdate.isEmpty());
        expandInvalidatedScriptPairs();
        clearPairsForInvalidatedScripts();
        undeclareVarsForInvalidatedScripts();

        new ScopeScanner(compiler, factory, this, scriptsToUpdate).populate();

        scriptsToUpdate.clear();
        this.valid = true;
      } else {
        checkState(scriptsToUpdate.isEmpty());
      }
    }

    void expandInvalidatedScriptPairs() {
      // Make a copy as before we star to update the set
      List<Node> scripts = new ArrayList<>(scriptsToUpdate);
      for (Node script : scripts) {
        expandInvalidatedScript(script);
      }
    }

    // For every script look for scripts which may contains redeclarations
    void expandInvalidatedScript(Node script) {
      Collection<Node> pairs = scriptDeclarationsPairs.get(script);
      for (Node n : pairs) {
        if (scriptsToUpdate.add(n)) {
          expandInvalidatedScript(script);
        }
      }
    }

    void clearPairsForInvalidatedScripts() {
      for (Node script : scriptsToUpdate) {
        scriptDeclarationsPairs.removeAll(script);
      }
    }

    /** undeclare all vars in the invalidated scripts */
    void undeclareVarsForInvalidatedScripts() {
      for (Node script : scriptsToUpdate) {
        for (Var var : scriptToVarMap.removeAll(script)) {
          super.undeclareInteral(var);
        }
      }
    }

    Node getContainingScript(Node n) {
      while (!n.isScript()) {
        n = n.getParent();
      }
      return n;
    }

    @Override
    Var declare(String name, Node nameNode, CompilerInput input) {
      Node declareScript = getContainingScript(nameNode);
      Var v = super.declare(name, nameNode, input);
      scriptToVarMap.put(declareScript, v);
      return v;
    }

    /**
     * link any script that redeclares a variable to the original script so the two scripts
     * always get built together.
     */
    public void redeclare(Node n) {
      checkArgument(n.isName());
      String name = n.getString();
      checkArgument(!Var.ARGUMENTS.equals(name));
      Node redeclareScript = getContainingScript(n);
      Var v = getOwnSlot(name);
      Node declarationScript = getContainingScript(v.getNode());
      if (redeclareScript != declarationScript) {
        scriptDeclarationsPairs.put(redeclareScript, declarationScript);
        scriptDeclarationsPairs.put(declarationScript, redeclareScript);
      }
    }
  }

  private static final ImmutableList<PersistentLocalScope> PRIMORDIAL_LIST = ImmutableList.of();

  /**
   * A subclass of the traditional Scope class that knows about its children,
   * and has methods for updating the scope hierarchy.
   */
  private static class PersistentLocalScope extends PersistentScope {
    // A list of Scope within the "change scope" (those not crossing function boundaries)
    // which were added to this scope.
    List<PersistentLocalScope> validChildren = PRIMORDIAL_LIST;

    PersistentLocalScope(PersistentScope parent, Node rootNode) {
      super(parent, rootNode);
      parent.addChildScope(this);
    }

    @Override
    void addChildScope(PersistentLocalScope scope) {
      // Keep track of valid children within the "change scope".
      if (!NodeUtil.isChangeScopeRoot(scope.getRootNode())) {
        // The first time we have added to the list, create a real list.
        if (validChildren == PRIMORDIAL_LIST) {
          validChildren = new ArrayList<>();
        }
        validChildren.add(scope);
      }
    }

    @Override
    public boolean isValid() {
      return valid;
    }

    void invalidate() {
      if (valid) {
        valid = false;
        for (PersistentLocalScope child : validChildren) {
          checkState(!NodeUtil.isChangeScopeRoot(child.getRootNode()));
          child.invalidate();
        }
        if (validChildren != PRIMORDIAL_LIST) {
          validChildren.clear();
        }
      }
    }

    @Override
    void refresh(AbstractCompiler compiler, PersistentScope newParent) {
      checkArgument(newParent != null && newParent.isValid());
      checkState(parent != null);

      // Even if this scope hasn't been invalidated, its parent scopes may have,
      // so update the scope chaining.
      this.parent = newParent;

      // Even if the parent hasn't changed the depth might have, update it now.
      this.depth = parent.getDepth() + 1;

      // Update the scope if needed.
      if (!valid) {
        clearVarsInternal();
        new ScopeScanner(compiler, this).populate();
        valid = true;

        // NOTE(johnlenz): It doesn't really matter which parent scope in the "change scope"
        // invalidates this scope so it doesn't need to update when the parent changes.
        getParent().addChildScope(this);
      }
    }
  }

  Es6SyntacticScopeCreator createInternalScopeCreator(AbstractCompiler compiler) {
    return new Es6SyntacticScopeCreator(compiler, factory, factory);
  }

  private static class PersistentScopeFactory
      implements Es6SyntacticScopeCreator.ScopeFactory,
          Es6SyntacticScopeCreator.RedeclarationHandler {
    @Override
    public PersistentScope create(Scope parent, Node n) {
      return PersistentScope.create((PersistentScope) parent, n);
    }

    @Override
    public void onRedeclaration(Scope s, String name, Node n, CompilerInput input) {
      if (s.isGlobal()) {
        ((PersistentGlobalScope) s).redeclare(n);
        // TODO(johnlenz): link source script and the redeclaration script so
        // that the global scope is rebuilt in the presense of redeclarations.
      }
    }
  }
}
