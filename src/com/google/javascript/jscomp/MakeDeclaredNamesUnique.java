/*
 * Copyright 2009 Google Inc.
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
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 *  Find all Functions, VARs, and Exception names and make them
 *  unique.  Specifically, it will not modify object properties.
 *  @author johnlenz@google.com (John Lenz)
 *  TODO(johnlenz): Try to merge this with the ScopeCreator.
 */
class MakeDeclaredNamesUnique
    implements NodeTraversal.ScopedCallback {

  private Deque<Renamer> nameStack = new ArrayDeque<Renamer>();
  private final Renamer rootRenamer;

  MakeDeclaredNamesUnique() {
    this.rootRenamer = new ContextualRenamer();
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
      // If the contextual renamer is being used the starting context can not
      // be a function.
      Preconditions.checkState(
          declarationRoot.getType() != Token.FUNCTION ||
          !(rootRenamer instanceof ContextualRenamer));
      Preconditions.checkState(t.inGlobalScope());
      renamer = rootRenamer;
    } else {
      renamer = nameStack.peek().forChildScope();
    }

    if (declarationRoot.getType() == Token.FUNCTION) {
      // Add the function parameters
      Node fnParams = declarationRoot.getFirstChild().getNext();
      for (Node c = fnParams.getFirstChild(); c != null; c = c.getNext()) {
        String name = c.getString();
        renamer.addDeclaredName(name);
      }

      // Add the function body declarations
      Node functionBody = declarationRoot.getLastChild();
      findDeclaredNames(functionBody, null, renamer);
    } else {
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
            // TODO(johnlenz): Do we need to do anything about the javadoc?
            n.removeProp(Node.IS_CONSTANT_NAME);
          }
          n.setString(newName);
          t.getCompiler().reportCodeChange();
        }
        break;

      case Token.FUNCTION:
        // Remove function recursive name (if any).
        nameStack.pop();
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
  private void findDeclaredNames(Node n, Node parent, Renamer renamer) {
    // Do a shallow traversal, so don't traverse into function declarations,
    // except for the name of the function itself.
    if (parent == null
        || parent.getType() != Token.FUNCTION
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
  static class ContextualRenameInverter extends AbstractPostOrderCallback
      implements CompilerPass {
    private final AbstractCompiler compiler;

    // A mapping from long names to short ones.
    private Map<Var, String> nameMap = Maps.newHashMap();

    private ContextualRenameInverter(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    public void process(Node externs, Node js) {
      NodeTraversal.traverse(compiler, js, this);
    }

    public static String getOrginalName(String name) {
      int index = indexOfSeparator(name);
      return (index == -1) ? name : name.substring(0, index);
    }

    private static int indexOfSeparator(String name) {
      return name.lastIndexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR);
    }

    private static String getOrginalNameInternal(String name, int index) {
      return name.substring(0, index);
    }

    private static String getNameSuffix(String name, int index) {
      return name.substring(
          index + ContextualRenamer.UNIQUE_ID_SEPARATOR.length(),
          name.length());
    }

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      if (node.getType() == Token.NAME) {
        String oldName = node.getString();
        if (oldName.indexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR) != -1) {
          Scope scope = t.getScope();
          Var var = t.getScope().getVar(oldName);
          if (var == null || var.isGlobal()) {
            return;
          }

          if (nameMap.containsKey(var)) {
            node.setString(nameMap.get(var));
          } else {
            int index = indexOfSeparator(oldName);
            String newName = getOrginalNameInternal(oldName, index);
            String suffix = getNameSuffix(oldName, index);

            // The convention we are using here is that names of the form:
            //    a$$1  ($$ followed by a digit are allowed to mask a global)
            //    a$$inline_1 ($$ followed by anything that isn't a digit isn't
            //       allowed to mask a global.
            // This preserves existing behavior while allowing simpler diffs
            // when inlining is enabled.
            // TODO(johnlenz): Remove this distiction when scoping is properly
            // checked.
            boolean recurseScopes = false;
            if (!suffix.matches("\\d+")) {
              // Non-contextual renamed value.
              recurseScopes = true;
            }

            // Before we change the name of this variable, double-check to
            // make sure we're not declaring a duplicate name in the
            // same scope as the var declaration.
            // TODO(johnlenz): This test isn't sufficient; specifically,
            // a reference to a global may have been introduced. Shortening
            // the name without checking for such a reference may mask the
            // global causing the wrong value to be referenced.
            if (var.scope.isDeclared(newName, recurseScopes) ||
                !TokenStream.isJSIdentifier(newName)) {
              newName = oldName;
            } else {
              var.scope.declare(newName, var.nameNode, null, null);

              // Handle bleeding functions.
              Node parentNode = var.getParentNode();
              if (parentNode.getType() == Token.FUNCTION &&
                  parentNode == var.scope.getRootNode()) {
                var.getNameNode().setString(newName);
              }

              node.setString(newName);
              compiler.reportCodeChange();
            }

            nameMap.put(var, newName);
          }
        }
      }
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

    final static String UNIQUE_ID_SEPARATOR = "$$";

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

    @Override
    public String getReplacementName(String oldName) {
      return declarations.get(oldName);
    }

    /**
     * Given a name and the associated id, create a new unique name.
     */
    private String getUniqueName(String name, int id) {
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

    InlineRenamer(
        Supplier<String> uniqueIdSupplier,
        String idPrefix,
        boolean removeConstness) {
      this.uniqueIdSupplier = uniqueIdSupplier;
      // To ensure that the id does not conflict with the id from the
      // ContextualRenamer some prefix is needed.
      Preconditions.checkArgument(!idPrefix.isEmpty());
      this.idPrefix = idPrefix;
      this.removeConstness = removeConstness;
    }

    @Override
    public void addDeclaredName(String name) {
      if (!declarations.containsKey(name)) {
        declarations.put(name, getUniqueName(name));
      }
    }

    private String getUniqueName(String name) {
      if (name.isEmpty()) {
        return name;
      }

      if (name.indexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR) != -1) {
          name = name.substring(
              0, name.lastIndexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR));
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
      return new InlineRenamer(uniqueIdSupplier, idPrefix, removeConstness);
    }

    @Override
    public boolean stripConstIfReplaced() {
      return removeConstness;
    }
  }

}
