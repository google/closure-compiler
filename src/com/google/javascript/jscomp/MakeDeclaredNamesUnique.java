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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Supplier;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multiset;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TokenStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 *  Find all Functions, VARs, and Exception names and make them
 *  unique.  Specifically, it will not modify object properties.
 */
class MakeDeclaredNamesUnique extends NodeTraversal.AbstractScopedCallback {

  // Arguments is special cased to handle cases where a local name shadows
  // the arguments declaration.
  public static final String ARGUMENTS = "arguments";

  // There is one renamer on the stack for each scope. This was added before any support for ES6 was
  // in place, so it was necessary to maintain this separate stack, rather than just using the
  // NodeTraversal's stack of scopes, in order to handle catch blocks and function names correctly.
  private final Deque<Renamer> renamerStack = new ArrayDeque<>();
  private final Renamer rootRenamer;
  private final boolean markChanges;

  MakeDeclaredNamesUnique() {
    this(new ContextualRenamer(), true);
  }

  MakeDeclaredNamesUnique(Renamer renamer) {
    this(renamer, true);
  }

  MakeDeclaredNamesUnique(Renamer renamer, boolean markChanges) {
    this.rootRenamer = renamer;
    this.markChanges = markChanges;
  }

  static CompilerPass getContextualRenameInverter(AbstractCompiler compiler) {
    return new ContextualRenameInverter(compiler, true);
  }

  @Override
  public void enterScope(NodeTraversal t) {
    Node declarationRoot = t.getScopeRoot();

    Renamer renamer;
    if (renamerStack.isEmpty()) {
      // If the contextual renamer is being used, the starting context can not
      // be a function.
      checkState(!declarationRoot.isFunction() || !(rootRenamer instanceof ContextualRenamer));
      renamer = rootRenamer;
    } else {
      boolean hoist = !declarationRoot.isFunction() && !NodeUtil.createsBlockScope(declarationRoot);
      renamer = renamerStack.peek().createForChildScope(t.getScopeRoot(), hoist);
    }

    renamerStack.push(renamer);

    findDeclaredNames(t, declarationRoot);
  }

  @Override
  public void exitScope(NodeTraversal t) {
    if (!t.inGlobalScope()) {
      renamerStack.pop();
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case NAME:
      case IMPORT_STAR:
        visitNameOrImportStar(t, n, parent);
        break;

      default:
        break;
    }
  }

  private void visitNameOrImportStar(NodeTraversal t, Node n, Node parent) {
    // Don't rename the exported name foo in export {a as foo}; or import {foo as b};
    if (n.isName() && NodeUtil.isNonlocalModuleExportName(n)) {
      return;
    }
    String newName = getReplacementName(n.getString());
    if (newName != null) {
      Renamer renamer = renamerStack.peek();
      if (renamer.stripConstIfReplaced()) {
        n.removeProp(Node.IS_CONSTANT_NAME);
        Node jsDocInfoNode = NodeUtil.getBestJSDocInfoNode(n);
        if (jsDocInfoNode != null && jsDocInfoNode.getJSDocInfo() != null) {
          JSDocInfoBuilder builder = JSDocInfoBuilder.copyFrom(jsDocInfoNode.getJSDocInfo());
          builder.recordMutable();
          jsDocInfoNode.setJSDocInfo(builder.build());
        }
      }
      n.setString(newName);
      if (markChanges) {
        t.reportCodeChange();
        // If we are renaming a function declaration, make sure the containing scope
        // has the opporunity to act on the change.
        if (parent.isFunction() && NodeUtil.isFunctionDeclaration(parent)) {
          t.getCompiler().reportChangeToEnclosingScope(parent);
        }
      }
    }
  }

  /**
   * Walks the stack of name maps and finds the replacement name for the
   * current scope.
   */
  private String getReplacementName(String oldName) {
    for (Renamer renamer : renamerStack) {
      String newName = renamer.getReplacementName(oldName);
      if (newName != null) {
        return newName;
      }
    }
    return null;
  }

  /**
   * Traverses the current scope and collects declared names by calling {@code addDeclaredName} on
   * the {@code Renamer} that is at the top of the {@code renamerStack}.
   */
  private void findDeclaredNames(NodeTraversal t, Node n) {
    checkState(NodeUtil.createsScope(n) || n.isScript(), n);

    for (Var v : t.getScope().getVarIterable()) {
      renamerStack.peek().addDeclaredName(v.getName(), false);
    }
  }

  /**
   * Declared names renaming policy interface.
   */
  interface Renamer {

    /**
     * Called when a declared name is found in the local current scope.
     * @param hoisted Whether this name should be declared in the nearest enclosing "hoist scope"
     *     instead of the scope represented by this Renamer.
     */
    void addDeclaredName(String name, boolean hoisted);

    /**
     * @return A replacement name, null if oldName is unknown or should not
     * be replaced.
     */
    String getReplacementName(String oldName);

    /**
     * @return Whether the constant-ness of a name should be removed.
     */
    boolean stripConstIfReplaced();

    /**
     * @param hoisted True if this is a "hoist" scope: A function, module, or global scope.
     * @return A Renamer for a scope within the scope of the current Renamer.
     */
    Renamer createForChildScope(Node scopeRoot, boolean hoisted);

    /**
     * @return The closest hoisting target for var and function declarations.
     */
    Renamer getHoistRenamer();
  }

  /**
   * Inverts the transformation by {@link ContextualRenamer}, when possible.
   */
  static class ContextualRenameInverter implements ScopedCallback, CompilerPass {
    private final AbstractCompiler compiler;

    // The set of names referenced in the current scope.
    private Set<String> referencedNames = ImmutableSet.of();

    // Stack reference sets.
    private final Deque<Set<String>> referenceStack = new ArrayDeque<>();

    // Name are globally unique initially, so we don't need a per-scope map.
    private final ListMultimap<String, Node> nameMap =
        MultimapBuilder.hashKeys().arrayListValues().build();

    // Whether to report changes to the compiler.
    private final boolean markChanges;

    private ContextualRenameInverter(AbstractCompiler compiler, boolean markChanges) {
      this.compiler = compiler;
      this.markChanges = markChanges;
    }

    @Override
    public void process(Node externs, Node js) {
      NodeTraversal.traverse(compiler, js, this);
    }

    public static String getOriginalName(String name) {
      int index = indexOfSeparator(name);
      return (index == -1) ? name : name.substring(0, index);
    }

    private static int indexOfSeparator(String name) {
      return name.lastIndexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR);
    }

    private static boolean containsSeparator(String name) {
      return name.contains(ContextualRenamer.UNIQUE_ID_SEPARATOR);
    }

    /**
     * Prepare a set for the new scope.
     */
    @Override
    public void enterScope(NodeTraversal t) {
      if (t.inGlobalScope()) {
        return;
      }

      referenceStack.push(referencedNames);
      referencedNames = new HashSet<>();
    }

    /**
     * Rename vars for the current scope, and merge any referenced
     * names into the parent scope reference set.
     */
    @Override
    public void exitScope(NodeTraversal t) {
      if (t.inGlobalScope()) {
        return;
      }

      for (Var v : t.getScope().getVarIterable()) {
        handleScopeVar(v);
      }

      // Merge any names that were referenced but not declared in the current
      // scope.
      Set<String> current = referencedNames;
      referencedNames = referenceStack.pop();
      // If there isn't anything left in the stack we will be going into the
      // global scope: don't try to build a set of referenced names for the
      // global scope.
      if (!referenceStack.isEmpty()) {
        referencedNames.addAll(current);
      }
    }

    /**
     * For the Var declared in the current scope determine if it is possible
     * to revert the name to its original form without conflicting with other
     * values.
     */
    void handleScopeVar(Var v) {
      String name = v.getName();
      if (containsSeparator(name) && !getOriginalName(name).isEmpty()) {
        String newName = findReplacementName(name);
        referencedNames.remove(name);
        // Adding a reference to the new name to prevent either the parent
        // scopes or the current scope renaming another var to this new name.
        referencedNames.add(newName);
        List<Node> references = nameMap.get(name);
        for (Node n : references) {
          checkState(n.isName() || n.isImportStar(), n);
          n.setString(newName);
          if (markChanges) {
            compiler.reportChangeToEnclosingScope(n);
            Node parent = n.getParent();
            // If we are renaming a function declaration, make sure the containing scope
            // has the opportunity to act on the change.
            if (parent.isFunction() && NodeUtil.isFunctionDeclaration(parent)) {
              compiler.reportChangeToEnclosingScope(parent);
            }
          }
        }
        nameMap.removeAll(name);
      }
    }

    /**
     * Find a name usable in the local scope.
     */
    private String findReplacementName(String name) {
      String original = getOriginalName(name);
      String newName = original;
      int i = 0;
      while (!isValidName(newName)) {
        newName = original + ContextualRenamer.UNIQUE_ID_SEPARATOR + i++;
      }
      return newName;
    }

    /**
     * @return Whether the name is valid to use in the local scope.
     */
    private boolean isValidName(String name) {
      return TokenStream.isJSIdentifier(name) && !referencedNames.contains(name)
          && !name.equals(ARGUMENTS);
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      if (t.inGlobalScope()) {
        return;
      }

      if (NodeUtil.isReferenceName(node) || node.isImportStar()) {
        String name = node.getString();
        // Add all referenced names to the set so it is possible to check for
        // conflicts.
        referencedNames.add(name);
        // Store only references to candidate names in the node map.
        if (containsSeparator(name)) {
          addCandidateNameReference(name, node);
        }
      }
    }

    private void addCandidateNameReference(String name, Node n) {
      nameMap.put(name, n);
    }
  }

  /**
   * Renames every local name to be unique. The first encountered declaration of a given name
   * (specifically a global declaration) is left in its original form. Those that are renamed are
   * made unique by giving them a unique suffix based on the number of declarations of the name.
   *
   * <p>The root ContextualRenamer is assumed to be in GlobalScope.
   *
   * <p>Used by the Normalize pass.
   *
   * @see Normalize
   */
  static class ContextualRenamer implements Renamer {
    @Nullable
    private final Node scopeRoot;

    // This multiset is shared between this ContextualRenamer and its parent (and its parent's
    // parent, etc.) because it tracks counts of variables across the entire JS program.
    private final Multiset<String> nameUsage;

    // By contrast, this is a different map for each ContextualRenamer because it's just keeping
    // track of the names used by this renamer.
    private final Map<String, String> declarations = new HashMap<>();
    private final boolean global;

    private final Renamer hoistRenamer;

    static final String UNIQUE_ID_SEPARATOR = "$jscomp$";

    @Override
    public String toString() {
      return toStringHelper(this)
          .add("scopeRoot", scopeRoot)
          .add("nameUsage", nameUsage)
          .add("declarations", declarations)
          .add("global", global)
          .toString();
    }

    ContextualRenamer() {
      scopeRoot = null;
      global = true;
      nameUsage = HashMultiset.create();

      hoistRenamer = this;
    }

    /** Constructor for child scopes. */
    private ContextualRenamer(
        Node scopeRoot, Multiset<String> nameUsage, boolean hoistingTargetScope, Renamer parent) {
      checkState(NodeUtil.createsScope(scopeRoot), scopeRoot);

      if (scopeRoot.isFunction()) {
        checkState(!hoistingTargetScope, scopeRoot);
      }

      this.scopeRoot = scopeRoot;
      this.global = false;
      this.nameUsage = nameUsage;

      if (hoistingTargetScope) {
        checkState(!NodeUtil.createsBlockScope(scopeRoot), scopeRoot);
        hoistRenamer = this;
      } else {
        checkState(NodeUtil.createsBlockScope(scopeRoot) || scopeRoot.isFunction(), scopeRoot);
        hoistRenamer = parent.getHoistRenamer();
      }
    }

    /** Create a ContextualRenamer */
    @Override
    public Renamer createForChildScope(Node scopeRoot, boolean hoistingTargetScope) {
      return new ContextualRenamer(scopeRoot, nameUsage, hoistingTargetScope, this);
    }

    /**
     * Adds a name to the map of names declared in this scope.
     */
    @Override
    public void addDeclaredName(String name, boolean hoisted) {
      if (hoisted && hoistRenamer != this) {
        hoistRenamer.addDeclaredName(name, true);
      } else {
        if (!name.equals(ARGUMENTS)) {
          if (global) {
            reserveName(name);
          } else {
            // It hasn't been declared locally yet, so increment the count.
            if (!declarations.containsKey(name)) {
              int id = incrementNameCount(name);
              String newName = null;
              if (id != 0) {
                newName = getUniqueName(name, id);
              }
              declarations.put(name, newName);
            }
          }
        }
      }
    }

    @Override
    public String getReplacementName(String oldName) {
      return declarations.get(oldName);
    }

    /**
     * Given a name and the associated id, create a new unique name.
     */
    private static String getUniqueName(String name, int id) {
      return name + UNIQUE_ID_SEPARATOR + id;
    }

    private void reserveName(String name) {
      nameUsage.setCount(name, 0, 1);
    }

    private int incrementNameCount(String name) {
      return nameUsage.add(name, 1);
    }

    @Override
    public boolean stripConstIfReplaced() {
      return false;
    }

    @Override
    public Renamer getHoistRenamer() {
      return hoistRenamer;
    }
  }


  /**
   * Rename every declared name to be unique. Typically this would be used
   * when injecting code to insure that names do not conflict with existing
   * names.
   *
   * Used by the FunctionInjector
   * @see FunctionInjector
   */
  static class InlineRenamer implements Renamer {
    private final Map<String, String> declarations = new HashMap<>();
    private final Supplier<String> uniqueIdSupplier;
    private final String idPrefix;
    private final boolean removeConstness;
    private final CodingConvention convention;

    private final Renamer hoistRenamer;

    InlineRenamer(
        CodingConvention convention,
        Supplier<String> uniqueIdSupplier,
        String idPrefix,
        boolean removeConstness,
        boolean hoistingTargetScope,
        Renamer parent) {
      this.convention = convention;
      this.uniqueIdSupplier = uniqueIdSupplier;
      // To ensure that the id does not conflict with the id from the
      // ContextualRenamer some prefix is needed.
      checkArgument(!idPrefix.isEmpty());
      this.idPrefix = idPrefix;
      this.removeConstness = removeConstness;

      if (hoistingTargetScope) {
        hoistRenamer = this;
      } else {
        hoistRenamer = parent.getHoistRenamer();
      }
    }

    @Override
    public void addDeclaredName(String name, boolean hoisted) {
      checkState(!name.equals(ARGUMENTS));
      if (hoisted && hoistRenamer != this) {
        hoistRenamer.addDeclaredName(name, hoisted);
      } else {
        if (!declarations.containsKey(name)) {
          declarations.put(name, getUniqueName(name));
        }
      }
    }

    private String getUniqueName(String name) {
      if (name.isEmpty()) {
        return name;
      }

      if (name.contains(ContextualRenamer.UNIQUE_ID_SEPARATOR)) {
          name = name.substring(
              0, name.lastIndexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR));
      }

      if (convention.isExported(name)) {
        // The google internal coding convention includes a naming convention
        // to export names starting with "_".  Simply strip "_" those to avoid
        // exporting names.
        name = "JSCompiler_" + name;
      }

      // By using the same separator the id will be stripped if it isn't
      // needed when variable renaming is turned off.
      return name + ContextualRenamer.UNIQUE_ID_SEPARATOR + idPrefix + uniqueIdSupplier.get();
    }

    @Override
    public String getReplacementName(String oldName) {
      return declarations.get(oldName);
    }

    @Override
    public Renamer createForChildScope(Node scopeRoot, boolean hoistingTargetScope) {
      return new InlineRenamer(
          convention, uniqueIdSupplier, idPrefix, removeConstness, hoistingTargetScope, this);
    }

    @Override
    public boolean stripConstIfReplaced() {
      return removeConstness;
    }

    @Override
    public Renamer getHoistRenamer() {
      return hoistRenamer;
    }
  }

  /**
   * For injecting boilerplate libraries. Leaves global names alone
   * and renames local names like InlineRenamer.
   */
  static class BoilerplateRenamer extends ContextualRenamer {
    private final Supplier<String> uniqueIdSupplier;
    private final String idPrefix;
    private final CodingConvention convention;

    BoilerplateRenamer(
        CodingConvention convention,
        Supplier<String> uniqueIdSupplier,
        String idPrefix) {
      this.convention = convention;
      this.uniqueIdSupplier = uniqueIdSupplier;
      this.idPrefix = idPrefix;
    }

    @Override
    public Renamer createForChildScope(Node scopeRoot, boolean hoisted) {
      return new InlineRenamer(convention, uniqueIdSupplier, idPrefix, false, hoisted, this);
    }
  }

  /** Only rename things that match specific names. Wraps another renamer. */
  static class TargettedRenamer implements Renamer {
    private final Renamer delegate;
    private final Set<String> targets;

    TargettedRenamer(Renamer delegate, Set<String> targets) {
      this.delegate = delegate;
      this.targets = targets;
    }

    @Override
    public void addDeclaredName(String name, boolean hoisted) {
      if (targets.contains(name)) {
        delegate.addDeclaredName(name, hoisted);
      }
    }

    @Override
    public String getReplacementName(String oldName) {
      return targets.contains(oldName) ? delegate.getReplacementName(oldName) : null;
    }

    @Override
    public boolean stripConstIfReplaced() {
      return delegate.stripConstIfReplaced();
    }

    @Override
    public Renamer createForChildScope(Node scopeRoot, boolean hoistingTargetScope) {
      return new TargettedRenamer(
          delegate.createForChildScope(scopeRoot, hoistingTargetScope), targets);
    }

    @Override
    public Renamer getHoistRenamer() {
      return delegate.getHoistRenamer();
    }
  }

}
