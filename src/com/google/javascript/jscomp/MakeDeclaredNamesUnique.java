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
import com.google.common.base.Supplier;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *  Find all Functions, VARs, and Exception names and make them
 *  unique.  Specifically, it will not modify object properties.
 *  @author johnlenz@google.com (John Lenz)
 *  TODO(johnlenz): Try to merge this with the ScopeCreator.
 */
class MakeDeclaredNamesUnique
    implements NodeTraversal.ScopedCallback {

  // Arguments is special cased to handle cases where a local name shadows
  // the arguments declaration.
  public static final String ARGUMENTS = "arguments";

  // The name stack is similar to how we model scopes but handles some
  // additional cases that are not handled by the current Scope object.
  // Specifically, a Scope currently has only two concepts of scope (global,
  // and function local).  But there are in reality a couple of additional
  // case to worry about:
  //   catch expressions
  //   function expressions names
  // Both belong to a scope by themselves.
  private Deque<Renamer> nameStack = new ArrayDeque<>();
  private final Renamer rootRenamer;

  MakeDeclaredNamesUnique() {
    this(new ContextualRenamer());
  }

  MakeDeclaredNamesUnique(Renamer renamer) {
    this.rootRenamer = renamer;
  }

  static CompilerPass getContextualRenameInverter(AbstractCompiler compiler) {
    return new ContextualRenameInverter(compiler);
  }

  @Override
  public void enterScope(NodeTraversal t) {
    Node declarationRoot = t.getScopeRoot();
    Renamer renamer;
    if (nameStack.isEmpty()) {
      // If the contextual renamer is being used, the starting context can not
      // be a function.
      Preconditions.checkState(
          !declarationRoot.isFunction() ||
          !(rootRenamer instanceof ContextualRenamer));
      Preconditions.checkState(t.inGlobalScope());
      renamer = rootRenamer;
    } else {
      renamer = nameStack.peek().forChildScope();
    }

    if (!declarationRoot.isFunction()) {
      // Add the block declarations
      findDeclaredNames(declarationRoot, null, renamer);
    }
    nameStack.push(renamer);
  }

  @Override
  public void exitScope(NodeTraversal t) {
    if (!t.inGlobalScope()) {
      nameStack.pop();
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {

    switch (n.getType()) {
      case Token.FUNCTION:
        {
          // Add recursive function name, if needed.
          // NOTE: "enterScope" is called after we need to pick up this name.
          Renamer renamer = nameStack.peek().forChildScope();

          // If needed, add the function recursive name.
          String name = n.getFirstChild().getString();
          if (name != null && !name.isEmpty() && parent != null
              && !NodeUtil.isFunctionDeclaration(n)) {
            renamer.addDeclaredName(name);
          }

          nameStack.push(renamer);
        }
        break;

      case Token.PARAM_LIST: {
          Renamer renamer = nameStack.peek().forChildScope();

          // Add the function parameters
          for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
            String name = c.getString();
            renamer.addDeclaredName(name);
          }

          // Add the function body declarations
          Node functionBody = n.getNext();
          findDeclaredNames(functionBody, null, renamer);

          nameStack.push(renamer);
        }
        break;

      case Token.CATCH:
        {
          Renamer renamer = nameStack.peek().forChildScope();

          String name = n.getFirstChild().getString();
          renamer.addDeclaredName(name);

          nameStack.push(renamer);
        }
        break;
    }

    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.NAME:
        String newName = getReplacementName(n.getString());
        if (newName != null) {
          Renamer renamer = nameStack.peek();
          if (renamer.stripConstIfReplaced()) {
            // TODO(johnlenz): Do we need to do anything about the Javadoc?
            n.removeProp(Node.IS_CONSTANT_NAME);
          }
          n.setString(newName);
          t.getCompiler().reportCodeChange();
        }
        break;

      case Token.FUNCTION:
        // Remove the function body scope
        nameStack.pop();
        // Remove function recursive name (if any).
        nameStack.pop();
        break;

      case Token.PARAM_LIST:
        // Note: The parameters and function body variables live in the
        // same scope, we introduce the scope when in the "shouldTraverse"
        // visit of LP, but remove it when when we exit the function above.
        break;

      case Token.CATCH:
        // Remove catch except name from the stack of names.
        nameStack.pop();
        break;
    }
  }

  /**
   * Walks the stack of name maps and finds the replacement name for the
   * current scope.
   */
  private String getReplacementName(String oldName) {
    for (Renamer names : nameStack) {
      String newName = names.getReplacementName(oldName);
      if (newName != null) {
        return newName;
      }
    }
    return null;
  }

  /**
   * Traverses the current scope and collects declared names.  Does not
   * decent into functions or add CATCH exceptions.
   */
  private static void findDeclaredNames(Node n, Node parent, Renamer renamer) {
    // Do a shallow traversal, so don't traverse into function declarations,
    // except for the name of the function itself.
    if (parent == null
        || !parent.isFunction()
        || n == parent.getFirstChild()) {
      if (NodeUtil.isVarDeclaration(n)) {
        renamer.addDeclaredName(n.getString());
      } else if (NodeUtil.isFunctionDeclaration(n)) {
        Node nameNode = n.getFirstChild();
        renamer.addDeclaredName(nameNode.getString());
      }

      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        findDeclaredNames(c, n, renamer);
      }
    }
  }

  /**
   * Declared names renaming policy interface.
   */
  interface Renamer {

    /**
     * Called when a declared name is found in the local current scope.
     */
    void addDeclaredName(String name);

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
     * @return A Renamer for a scope within the scope of the current Renamer.
     */
    Renamer forChildScope();
  }

  /**
   * Inverts the transformation by {@link ContextualRenamer}, when possible.
   */
  static class ContextualRenameInverter
      implements ScopedCallback, CompilerPass {
    private final AbstractCompiler compiler;

    // The set of names referenced in the current scope.
    private Set<String> referencedNames = ImmutableSet.of();

    // Stack reference sets.
    private Deque<Set<String>> referenceStack = new ArrayDeque<>();

    // Name are globally unique initially, so we don't need a per-scope map.
    private Map<String, List<Node>> nameMap = Maps.newHashMap();

    private ContextualRenameInverter(AbstractCompiler compiler) {
      this.compiler = compiler;
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
      referencedNames = Sets.newHashSet();
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

      for (Iterator<Var> it = t.getScope().getVars(); it.hasNext();) {
        Var v = it.next();
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
      String name  = v.getName();
      if (containsSeparator(name) && !getOriginalName(name).isEmpty()) {
        String newName = findReplacementName(name);
        referencedNames.remove(name);
        // Adding a reference to the new name to prevent either the parent
        // scopes or the current scope renaming another var to this new name.
        referencedNames.add(newName);
        List<Node> references = nameMap.get(name);
        Preconditions.checkState(references != null);
        for (Node n : references) {
          Preconditions.checkState(n.isName());
          n.setString(newName);
        }
        compiler.reportCodeChange();
        nameMap.remove(name);
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

      if (NodeUtil.isReferenceName(node)) {
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
      List<Node> nodes = nameMap.get(name);
      if (null == nodes) {
        nodes = Lists.newLinkedList();
        nameMap.put(name, nodes);
      }
      nodes.add(n);
    }
  }

  /**
   * Rename every locally name to be unique, the first encountered declaration
   * (specifically global names) are left in their original form. Those that are
   * renamed are made unique by giving them a unique suffix based on
   * the number of declarations of the name.
   *
   * The root ContextualRenamer is assumed to be in GlobalScope.
   *
   * Used by the Normalize pass.
   * @see Normalize
   */
  static class ContextualRenamer implements Renamer {
    private final Multiset<String> nameUsage;
    private final Map<String, String> declarations = Maps.newHashMap();
    private final boolean global;

    static final String UNIQUE_ID_SEPARATOR = "$$";

    ContextualRenamer() {
      this.global = true;
      nameUsage = HashMultiset.create();
    }

    /**
     * Constructor for child scopes.
     */
    private ContextualRenamer(Multiset<String> nameUsage) {
      this.global = false;
      this.nameUsage = nameUsage;
    }

    /**
     * Create a ContextualRenamer
     */
    @Override
    public Renamer forChildScope() {
      return new ContextualRenamer(nameUsage);
    }

    /**
     * Adds a name to the map of names declared in this scope.
     */
    @Override
    public void addDeclaredName(String name) {
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
    private final Map<String, String> declarations = Maps.newHashMap();
    private final Supplier<String> uniqueIdSupplier;
    private final String idPrefix;
    private final boolean removeConstness;
    private final CodingConvention convention;

    InlineRenamer(
        CodingConvention convention,
        Supplier<String> uniqueIdSupplier,
        String idPrefix,
        boolean removeConstness) {
      this.convention = convention;
      this.uniqueIdSupplier = uniqueIdSupplier;
      // To ensure that the id does not conflict with the id from the
      // ContextualRenamer some prefix is needed.
      Preconditions.checkArgument(!idPrefix.isEmpty());
      this.idPrefix = idPrefix;
      this.removeConstness = removeConstness;
    }

    @Override
    public void addDeclaredName(String name) {
      Preconditions.checkState(!name.equals(ARGUMENTS));
      if (!declarations.containsKey(name)) {
        declarations.put(name, getUniqueName(name));
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
      return name + ContextualRenamer.UNIQUE_ID_SEPARATOR
          + idPrefix + uniqueIdSupplier.get();
    }

    @Override
    public String getReplacementName(String oldName) {
      return declarations.get(oldName);
    }

    @Override
    public Renamer forChildScope() {
      return new InlineRenamer(
          convention, uniqueIdSupplier, idPrefix, removeConstness);
    }

    @Override
    public boolean stripConstIfReplaced() {
      return removeConstness;
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
    public Renamer forChildScope() {
      return new InlineRenamer(convention, uniqueIdSupplier, idPrefix, false);
    }
  }

  /** Only rename things that match the whitelist. Wraps another renamer. */
  static class WhitelistedRenamer implements Renamer {
    private Renamer delegate;
    private Set<String> whitelist;

    WhitelistedRenamer(Renamer delegate, Set<String> whitelist) {
      this.delegate = delegate;
      this.whitelist = whitelist;
    }

    @Override public void addDeclaredName(String name) {
      if (whitelist.contains(name)) {
        delegate.addDeclaredName(name);
      }
    }

    @Override public String getReplacementName(String oldName) {
      return whitelist.contains(oldName)
          ? delegate.getReplacementName(oldName) : null;
    }

    @Override public boolean stripConstIfReplaced() {
      return delegate.stripConstIfReplaced();
    }

    @Override public Renamer forChildScope() {
      return new WhitelistedRenamer(delegate.forChildScope(), whitelist);
    }
  }

}
