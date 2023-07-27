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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Supplier;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * Find all Functions, VARs, and Exception names and make them unique. Specifically, it will not
 * modify object properties.
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
  private final boolean assertOnChange;

  private MakeDeclaredNamesUnique(Renamer renamer, boolean markChanges, boolean assertOnChange) {
    this.rootRenamer = renamer;
    this.markChanges = markChanges;
    this.assertOnChange = assertOnChange;
  }

  static final class Builder {
    private Renamer renamer;
    private boolean markChanges = true;
    private boolean assertOnChange = false;

    private Builder() {}

    Builder withRenamer(Renamer renamer) {
      this.renamer = renamer;
      return this;
    }

    /**
     * Enables the option to report any code changes to the compiler object.
     *
     * <p>This option is {@code true} by default.
     */
    Builder withMarkChanges(boolean markChanges) {
      this.markChanges = markChanges;
      return this;
    }

    /**
     * If this class finds any names that are not unique, throws an exception
     *
     * <p>This is intended for use in validating that an AST is already normalized.
     *
     * <p>This option is {@code false} by default.
     */
    Builder withAssertOnChange(boolean assertOnChange) {
      this.assertOnChange = assertOnChange;
      return this;
    }

    MakeDeclaredNamesUnique build() {
      if (renamer == null) {
        renamer = new ContextualRenamer();
      }
      return new MakeDeclaredNamesUnique(renamer, markChanges, assertOnChange);
    }
  }

  static Builder builder() {
    return new Builder();
  }

  static CompilerPass getContextualRenameInverter(AbstractCompiler compiler) {
    return new ContextualRenameInverter(compiler);
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
    if (newName == null) {
      return;
    }
    if (assertOnChange) {
      throw new IllegalStateException(
          "Unexpected renaming in MakeDeclaredNamesUnique. new name: " + newName + " for " + n);
    }

    Renamer renamer = renamerStack.peek();
    if (renamer.stripConstIfReplaced()) {
      n.putBooleanProp(Node.IS_CONSTANT_NAME, false);
      Node jsDocInfoNode = NodeUtil.getBestJSDocInfoNode(n);
      if (jsDocInfoNode != null && jsDocInfoNode.getJSDocInfo() != null) {
        JSDocInfo.Builder builder = JSDocInfo.Builder.copyFrom(jsDocInfoNode.getJSDocInfo());
        builder.recordMutable();
        jsDocInfoNode.setJSDocInfo(builder.build());
      }
    }
    n.setString(newName);
    if (markChanges) {
      t.reportCodeChange();
      // If we are renaming a function declaration, make sure the containing scope
      // has the opportunity to act on the change.
      if (parent.isFunction() && NodeUtil.isFunctionDeclaration(parent)) {
        t.getCompiler().reportChangeToEnclosingScope(parent);
      }
    }
  }

  /** Walks the stack of name maps and finds the replacement name for the current scope. */
  private @Nullable String getReplacementName(String oldName) {
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

  /** Declared names renaming policy interface. */
  interface Renamer {

    /**
     * Called when a declared name is found in the local current scope.
     *
     * @param hoisted Whether this name should be declared in the nearest enclosing "hoist scope"
     *     instead of the scope represented by this Renamer.
     */
    void addDeclaredName(String name, boolean hoisted);

    /**
     * @return A replacement name, null if oldName is unknown or should not be replaced.
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

  /** Inverts the transformation by {@link ContextualRenamer}, when possible. */
  static class ContextualRenameInverter implements ScopedCallback, CompilerPass {
    private final AbstractCompiler compiler;

    /**
     * The top of this stack is always an object storing information about the current variable
     * scope or {@code null} for the global scope.
     */
    private final Deque<ScopeContext> scopeContextStack = new ArrayDeque<>();

    /**
     * Keeps track of all {@code VariableInfo} objects for all variables visible in the current
     * variable scope.
     *
     * <p>Basically this is a map whose keys are names and whose values are stacks of {@code
     * VariableInfo} objects. The top of each stack represents the variable with that name that is
     * visible in the scope we are currently traversing.
     */
    private final VariableInfoStackMap variablesStackedByName = new VariableInfoStackMap();

    private ContextualRenameInverter(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node js) {
      NodeTraversal.traverse(compiler, js, this);
    }

    public static String getOriginalName(String name) {
      int index = indexOfSeparator(name);
      return (index <= 0) ? name : name.substring(0, index);
    }

    private static int indexOfSeparator(String name) {
      return name.lastIndexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR);
    }

    /** Prepare a set for the new scope. */
    @Override
    public void enterScope(NodeTraversal t) {
      if (t.inGlobalScope()) {
        // Don't track any information for the global scope, because it is likely to be
        // very large, and we will never rename any variables it contains. MakeDeclaredNamesUnique
        // favors keeping global variables unchanged, so they won't need to be restored.
        return;
      }

      final Scope scope = t.getScope();

      // Create DeclaredVariableInfo objects for all variables declared in this scope.
      // Push them onto the variablesByName data structure to make them visible,
      // and store them into a ScopeContext object for the scope we're entering.
      final ImmutableList.Builder<DeclaredVariableInfo> declaredVariablesBuilder =
          ImmutableList.builder();
      for (Var var : scope.getVarIterable()) {
        DeclaredVariableInfo variableInfo = createDeclaredVariableInfo(var);
        declaredVariablesBuilder.add(variableInfo);
        variablesStackedByName.pushVariableInfo(variableInfo);
      }
      ScopeContext parent = scopeContextStack.peek();
      scopeContextStack.push(new ScopeContext(scope, declaredVariablesBuilder.build(), parent));
    }

    private DeclaredVariableInfo createDeclaredVariableInfo(Var var) {
      final String currentName = var.getName();
      int indexOfSeparator = indexOfSeparator(currentName);
      final RenamingInfo renamingInfo;
      if (indexOfSeparator > 0) {
        // We check for an index > 0, because a variable created directly by the compiler generally
        // begin with `$jscomp$` (the separator). We can't and don't want to try to rename any
        // variable to an empty string.
        final String invertedName = currentName.substring(0, indexOfSeparator);
        renamingInfo = new RenamingInfo(currentName, invertedName);
      } else {
        renamingInfo = null;
      }
      return new DeclaredVariableInfo(var, renamingInfo);
    }

    /** Keep track of information needed to rename a {@code DeclaredVariableInfo}. */
    class RenamingInfo {
      /** Will be filled with all nodes referring to the variable. */
      private final List<Node> referenceNodes = new ArrayList<>();

      /**
       * Will be filled with objects representing variables whose names must not conflict with this
       * one.
       */
      private final Set<VariableInfo> potentialShadowVariables = new LinkedHashSet<>();

      /** The name the variable has currently */
      private final String currentName;

      /** The name we want to use when renaming the variable. */
      private final String preferredName;

      RenamingInfo(String currentName, String preferredName) {
        this.currentName = currentName;
        this.preferredName = preferredName;
      }

      void addPotentialShadowVariable(VariableInfo variableInfo) {
        potentialShadowVariables.add(variableInfo);
      }

      String attemptRename() {
        final Set<String> disallowedNames = new LinkedHashSet<>();
        // If we somehow ended up with "arguments$jscomp$..." it's not safe to rename
        // that to "arguments", because that name is special, but it would still be good
        // to simplify its name, if possible. See the note below regarding why we
        // rename variables to something other than their original name.
        disallowedNames.add(ARGUMENTS);
        for (VariableInfo potentialShadowVariable : potentialShadowVariables) {
          // NOTE: We need to look up these names immediately before making our rename
          // decision, because some of these variables could themselves have been renamed.
          final String potentialShadowName = potentialShadowVariable.getName();
          disallowedNames.add(potentialShadowName);
        }
        String newName = preferredName;
        if (disallowedNames.contains(preferredName)) {
          // Why are we bothering to find a name other than the original one?
          // The reason is that we would like the final output name to depend more on the shape
          // of the output code than on the process the compiler followed to reach that shape.
          // This keeps our unit tests more stable. Without it, a tweak to the details of some
          // optimization pass would be more likely to make a trivial change to the output that
          // breaks unit tests.
          final String baseName = preferredName + ContextualRenamer.UNIQUE_ID_SEPARATOR;
          int i = 0;
          do {
            newName = baseName + i++;
          } while (disallowedNames.contains(newName));
        }

        // It's possible we ended up generating the same name we already had.
        if (newName.equals(currentName)) {
          return currentName;
        }

        for (Node referenceNode : referenceNodes) {
          referenceNode.setString(newName);
          // NOTE: This could probably be made more efficient by only reporting after doing all the
          // nodes that occur in the same scope.
          compiler.reportChangeToEnclosingScope(referenceNode);
          Node parent = referenceNode.getParent();
          // If we are renaming a function declaration, make sure the containing scope
          // has the opportunity to act on the change.
          if (parent.isFunction() && NodeUtil.isFunctionDeclaration(parent)) {
            compiler.reportChangeToEnclosingScope(parent);
          }
        }

        return newName;
      }
    }

    /** Records information about a variable for which we have seen a non-global declaration. */
    private static class DeclaredVariableInfo implements VariableInfo {
      final Var var;
      String name;

      /**
       * Keep track of information needed to rename this variable, if that is possible.
       *
       * <p>This will be {@code null} if renaming has already been done or cannot be done.
       */
      private @Nullable RenamingInfo renamingInfo;

      DeclaredVariableInfo(Var var, @Nullable RenamingInfo renamingInfo) {
        this.var = var;
        this.name = var.getName();
        this.renamingInfo = renamingInfo;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public int getScopeDepth() {
        return var.getScope().getDepth();
      }

      @Override
      public void addPotentialConflict(VariableInfo variableInfo) {
        if (renamingInfo != null && variableInfo != this) {
          renamingInfo.addPotentialShadowVariable(variableInfo);
        } // else we won't rename, so we don't care.
      }

      @Override
      public void addReferenceNode(Node nameNode) {
        if (renamingInfo != null) {
          renamingInfo.referenceNodes.add(nameNode);
        } // else we won't rename, so we don't care
      }

      private void tryToInvertName() {
        if (renamingInfo != null) {
          // update the name for the sake of other variables that may check this one for
          // conflicts.
          name = renamingInfo.attemptRename();
          renamingInfo = null; // allow garbage collection
        }
      }
    }

    /**
     * Rename vars for the current scope, and merge any referenced names into the parent scope
     * reference set.
     */
    @Override
    public void exitScope(NodeTraversal t) {
      if (t.inGlobalScope()) {
        return;
      }

      ScopeContext scopeContext = checkNotNull(scopeContextStack.pop());

      // Tell all the variables we referenced and the variables that might conflict with them
      // about each other.
      // This needs to happen before we start trying to rename the variables in this scope next.
      for (VariableInfo referencedVariable : scopeContext.referencedVariables) {
        scopeContext.recordPotentialConflicts(referencedVariable);
      }

      // Pop each variable declared in the scope we're exiting off of the variable stack
      // data structure.
      // Try to rename each one.
      for (DeclaredVariableInfo declaredVariableInfo : scopeContext.declaredVariableInfos) {
        final String variableName = declaredVariableInfo.getName();
        // Out of an abundance of caution:
        // 1. Pop the variable from its name stack and make sure it matches
        // 2. Do this before renaming causes the name to be different.
        VariableInfo oldStackTop = variablesStackedByName.popVariableInfo(variableName);
        checkState(
            oldStackTop == declaredVariableInfo,
            "Declared variable \"%s\" was not the top of the name stack",
            variableName);
        declaredVariableInfo.tryToInvertName();
      }

      if (scopeContextStack.isEmpty()) {
        // clear the records for any global or undeclared variables before we start traversing
        // a new local scope.
        variablesStackedByName.clear();
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      ScopeContext scopeContext = scopeContextStack.peek();
      if (scopeContext != null) {
        // We're not in the global scope
        if (NodeUtil.isReferenceName(node) || node.isImportStar()) {
          final String name = node.getString();
          // Get the corresponding VariableInfo, creating one if we haven't found a declaration
          // for it.
          final VariableInfo variableInfo =
              variablesStackedByName.getOrCreateCurrentVariableInfo(name);
          // add this reference to the variable
          variableInfo.addReferenceNode(node);
          // tell the current scope that it references the variable
          scopeContext.addReferencedVariableInfo(variableInfo);
        }
      } // else nothing to do for the global scope
    }

    /**
     * Keep track of information about variables declared and referenced in a local scope.
     *
     * <p>We do not create one of these objects for the global scope, because we know those won't be
     * renamed. It isn't worthwhile to track all of them.
     */
    private static class ScopeContext {
      final @Nullable ScopeContext parent;
      final Scope scope;
      final ImmutableList<DeclaredVariableInfo> declaredVariableInfos;

      /** Will be filled with objects for all variables we reference in this scope. */
      final Set<VariableInfo> referencedVariables = new LinkedHashSet<>();

      private ScopeContext(
          Scope scope,
          ImmutableList<DeclaredVariableInfo> declaredVariableInfos,
          @Nullable ScopeContext parent) {
        this.scope = scope;
        this.declaredVariableInfos = declaredVariableInfos;
        this.parent = parent;
      }

      public void addReferencedVariableInfo(VariableInfo variableInfo) {
        referencedVariables.add(variableInfo);
      }

      public void recordPotentialConflicts(VariableInfo referencedVariable) {
        int thisScopeDepth = scope.getDepth();
        int referencedVariableDeclarationDepth = referencedVariable.getScopeDepth();
        if (thisScopeDepth >= referencedVariableDeclarationDepth) {
          for (DeclaredVariableInfo declaredVariableInfo : declaredVariableInfos) {
            // NOTE: No need to check whether the 2 variables are actually the same here.
            // The logic in addPotentialConflict() can do that more efficiently.

            // The referenced variable must be sure not to "hide behind" another variable
            // declared between its declaration and the location where it is referenced.
            referencedVariable.addPotentialConflict(declaredVariableInfo);

            // The declared variable must be sure not to "shadow" a variable declared in its own
            // or a higher scope, thus preventing the reference from seeing it.
            declaredVariableInfo.addPotentialConflict(referencedVariable);
          }
          if (parent != null) {
            parent.recordPotentialConflicts(referencedVariable);
          }
        }
      }
    }

    /**
     * Handle storing and looking up {@code VariableInfo} objects for the variables that are visible
     * in the current {@code ScopeContext}.
     */
    private class VariableInfoStackMap {
      final Map<String, Deque<VariableInfo>> mapOfStacks = new LinkedHashMap<>();

      /** Push a {@code VariableInfo} onto the stack corresponding to its name. */
      void pushVariableInfo(VariableInfo info) {
        final String name = info.getName();
        Deque<VariableInfo> variableInfoStack = getVariableInfoStack(name);
        variableInfoStack.push(info);
      }

      private Deque<VariableInfo> getVariableInfoStack(String name) {
        return mapOfStacks.computeIfAbsent(name, ignoredKey -> new ArrayDeque<>());
      }

      VariableInfo getOrCreateCurrentVariableInfo(String name) {
        final Deque<VariableInfo> variableInfoStack = getVariableInfoStack(name);
        final VariableInfo variableInfo;
        if (variableInfoStack.isEmpty()) {
          variableInfo = createUndeclaredVariableInfo(name);
          variableInfoStack.push(variableInfo);
        } else {
          variableInfo = variableInfoStack.peek();
        }
        return variableInfo;
      }

      /** Pop the topmost {@code VariableInfo} off of the stack for the given variable name. */
      VariableInfo popVariableInfo(String name) {
        Deque<VariableInfo> variableInfos = mapOfStacks.get(name);
        checkNotNull(variableInfos, "Nonexistent variable info requested: '%s'", name);
        VariableInfo info = variableInfos.pop();
        if (variableInfos.isEmpty()) {
          // Remove empty stacks, so they can be garbage collected.
          mapOfStacks.remove(name);
        }
        return info;
      }

      /** Empty all the variable name stacks. */
      void clear() {
        mapOfStacks.clear();
      }
    }

    private VariableInfo createUndeclaredVariableInfo(String name) {
      return new UndeclaredVariableInfo(name);
    }

    /**
     * Represent a variable we've seen referenced but not declared.
     *
     * <p>One of these will be created when a global variable or simply undeclared variable is
     * referenced. It doesn't do much more than keep track of the name and the fact that we don't
     * have a declaration for it (as a 0 scope depth).
     */
    private static class UndeclaredVariableInfo implements VariableInfo {
      final String name;

      private UndeclaredVariableInfo(String name) {
        this.name = name;
      }

      @Override
      public void addReferenceNode(Node nameNode) {
        // no need to track references
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public int getScopeDepth() {
        // NOTE: Pretend that any variable for which we didn't see a declaration was declared in
        // the global scope, which is depth 0. This will most often be actually true, since we skip
        // creating variables for the global scope to avoid wasting time and space on variables that
        // will never be renamed.
        return 0;
      }

      @Override
      public void addPotentialConflict(VariableInfo variableInfo) {
        // No need to record the potential conflict, because this variable will never be renamed.
      }
    }

    /** One of these will be created for every variable we encounter. */
    interface VariableInfo {

      void addReferenceNode(Node nameNode);

      String getName();

      int getScopeDepth();

      void addPotentialConflict(VariableInfo variableInfo);
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
    private final @Nullable Node scopeRoot;

    // This multiset is shared between this ContextualRenamer and its parent (and its parent's
    // parent, etc.) because it tracks counts of variables across the entire JS program.
    private final Multiset<String> nameUsage;

    // By contrast, this is a different map for each ContextualRenamer because it's just keeping
    // track of the names used by this renamer.
    private final Map<String, String> declarations = new LinkedHashMap<>();
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

    /** Adds a name to the map of names declared in this scope. */
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

    /** Given a name and the associated id, create a new unique name. */
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
   * Rename every declared name to be unique. Typically, this would be used when injecting code to
   * ensure that names do not conflict with existing names.
   *
   * <p>Used by the FunctionInjector
   *
   * @see FunctionInjector
   */
  static class InlineRenamer implements Renamer {
    private final Map<String, String> declarations = new LinkedHashMap<>();
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
        declarations.computeIfAbsent(name, this::getUniqueName);
      }
    }

    private String getUniqueName(String name) {
      if (name.isEmpty()) {
        return name;
      }

      if (name.contains(ContextualRenamer.UNIQUE_ID_SEPARATOR)) {
        name = name.substring(0, name.lastIndexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR));
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
   * For injecting boilerplate libraries. Leaves global names alone and renames local names like
   * InlineRenamer.
   */
  static class BoilerplateRenamer extends ContextualRenamer {
    private final Supplier<String> uniqueIdSupplier;
    private final String idPrefix;
    private final CodingConvention convention;

    BoilerplateRenamer(
        CodingConvention convention, Supplier<String> uniqueIdSupplier, String idPrefix) {
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
    public @Nullable String getReplacementName(String oldName) {
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
