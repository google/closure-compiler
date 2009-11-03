/*
 * Copyright 2006 Google Inc.
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

import javax.annotation.Nullable;
import com.google.common.base.Preconditions;
import com.google.common.base.StringUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.FunctionNode;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * <p>AliasExternals provides wrappers and aliases for external globals and
 * properties to that they can be referenced by their full name only once
 * intead of in all use sites.</p>
 *
 * <p>The property alias pass creates function wrappers for properties that need
 * to be accessed externally. These function wrappers are then used by all
 * internal calls to the property, and the names will be compressed during the
 * RenamePrototypes step.</p>
 *
 * <p>Properties that are accessed externally are either system functions
 * (i.e. window.document), or used by javascript embedded on a page.</p>
 *
 * <p>Properties that are rvalues are changed to use array notation with
 * a string that has been defined separately and can be compressed
 * i.e. document.window -> document[PROP_window].</p>
 *
 * <p>Properties that are lvalues and can be renamed are renamed to
 * SETPROP_prop. I.e. node.innerHTML = '&lt;div&gt;Hello&lt;/div&gt;' ->
 * SETPROP_innerHTML(node, '&lt;div&gt;hello&lt;/div&gt;').</p>
 *
 * <p>Properties will only be renamed if they are used more than requiredUsage_
 * times, as there is overhead for adding the accessor and mutator functions.
 * This is initialized to DEFAULT_REQUIRED_USAGE (=4), but can be
 * overridden.</p>
 *
 * <p>Certain usages (increment, decrement) won't be addressed, as they would
 * require a getprop, setprop, and custom logic, and aren't worth
 * optimizing.</p>
 *
 * <p>The global alias pass creates aliases for global variables and functions
 * that are declared or need to be used externally. These aliases are then used
 * throughout the code, and will be compressed during the RenameVars step.</p>
 *
 * <p>Globals are aliased by inserting code like "var GLOBAL_window = window;"
 * and then replacing all other uses of "window" with "GLOBAL_window."</p>
 *
 * <p>Globals that are lvalues are not aliased.</p>
 *
*
 */
class AliasExternals implements CompilerPass {

  /** Number of times a proprety needs to be accessed in order to alias */
  private static final int DEFAULT_REQUIRED_USAGE = 4;

  /** Number of times a property must be referenced in order to be aliased */
  private int requiredUsage = DEFAULT_REQUIRED_USAGE;

  /** Minimium property size to be worth renaming */
  private static final int MIN_PROP_SIZE = 4;

  /**
   * The name of the variable used for the "prototype" string value. This is
   * special-cased to make deobfuscated stack traces shorter and more readable
   * ("$MyClass$$P$$method$" rather than "$MyClass$$$PROP_prototype$method$").
   * @see NameAnonymousFunctions
   */
  static final String PROTOTYPE_PROPERTY_NAME =
      getArrayNotationNameFor("prototype");

  /** Map of all properties that we may be renaming */
  private final Map<String, Property> props = Maps.newHashMap();

  /** Holds the properties that can be renamed to GETPROP_ */
  private final List<Node> accessors = Lists.newArrayList();

  /** Holds the properties that can be renamed to SETPROP_ */
  private final List<Node> mutators = Lists.newArrayList();

  /**
   * Map of node replacements -
   * Identity map because Node implements equals() but not hashCode()
   */
  private final Map<Node, Node> replacementMap =
    new IdentityHashMap<Node, Node>();

  /** Map of all globals that we may aliasing */
  private final Map<String, Property> globals = Maps.newHashMap();

  /** Holds all of the globals that can be aliased to GLOBAL_ */
  private final List<Node> globalUses = Lists.newArrayList();

  /** Reference to JS Compiler */
  private final AbstractCompiler compiler;

  /** Reference to module inputs */
  private final JSModuleGraph moduleGraph;

  /** Root in parse tree for adding generated nodes */
  private Node defaultRoot;

  /** Root in each module for adding generated nodes, if using modules */
  private Map<JSModule, Node> moduleRoots;

  /**
   * A set of globals that can not be aliased since they may be undefined or
   * can cause errors
   */
  private final Set<String> unaliasableGlobals = Sets.newHashSet(
      // While "arguments" is declared as a global extern, it really only has
      // meaning inside function bodies and should not be aliased at a global
      // level.
      "arguments",
      // Eval should not be aliased, per the ECMA 262 spec section 15.1.2.1
      "eval",
      // "NodeFilter" is not defined in IE and throws an error if you try to
      // do var foo = NodeFilter.
      "NodeFilter",
      // Calls to this special function are eliminated by the RenameProperties
      // compiler pass.
      "JSCompiler_renameProperty");

  /** Whitelist of aliasable externs. */
  private final Set<String> aliasableGlobals = Sets.newHashSet();

  /**
   * Creates an instance.
   *
   * @param compiler The Compiler
   * @param moduleGraph The graph of input modules. May be null. If given, we'll
   *     try to push aliased externs into the deepest possible module.
   */
  AliasExternals(AbstractCompiler compiler, JSModuleGraph moduleGraph) {
    this(compiler, moduleGraph, null, null);
  }

  /**
   * Creates an instance.
   *
   * @param compiler The Compiler
   * @param moduleGraph The graph of input modules. May be null. If given, we'll
   *     try to push aliased externs into the deepest possible module.
   * @param unaliasableGlobals Comma-separated list of additional globals that
   *     cannot be aliased since they may be undefined or can cause errors
   *     (e.g. "foo,bar"). May be null or the empty string.
   * @param aliasableGlobals Comma-separated list of globals that
   *     can be aliased. If provided, only this list of globals can be aliased.
   */
  AliasExternals(AbstractCompiler compiler, JSModuleGraph moduleGraph,
                 @Nullable String unaliasableGlobals,
                 @Nullable String aliasableGlobals) {
    this.compiler = compiler;
    this.moduleGraph = moduleGraph;

    if (!StringUtil.isEmpty(unaliasableGlobals) &&
        !StringUtil.isEmpty(aliasableGlobals)) {
      throw new IllegalArgumentException(
          "Cannot pass in both unaliasable and aliasable globals; you must " +
          "choose one or the other.");
    }

    if (!StringUtil.isEmpty(unaliasableGlobals)) {
      this.unaliasableGlobals.addAll(
          Arrays.asList(unaliasableGlobals.split(",")));
    }

    if (!StringUtil.isEmpty(aliasableGlobals)) {
      this.aliasableGlobals.addAll(Arrays.asList(aliasableGlobals.split(",")));
    }

    if (moduleGraph != null) {
      moduleRoots = Maps.newHashMap();
    }
  }

  /**
   * Sets the number of times a property needs to be referenced in order to
   * create an alias for it.
   * @param usage Number of times
   */
  public void setRequiredUsage(int usage) {
    this.requiredUsage = usage;
  }

  /**
   * Do all processing on the root node.
   */
  public void process(Node externs, Node root) {
    defaultRoot = root.getFirstChild();
    Preconditions.checkState(defaultRoot.getType() == Token.SCRIPT);

    aliasProperties(externs, root);
    aliasGlobals(externs, root);
  }

  private void aliasProperties(Node externs, Node root) {
    // Get the reserved names, filtered by the whitelist.
    NodeTraversal.traverse(compiler, externs,
                           new GetAliasableNames(aliasableGlobals));
    props.put("prototype", new Property("prototype"));

    // Find the props that can be changed
    NodeTraversal.traverse(compiler, root, new PropertyGatherer());

    // Iterate through the reserved names, decide what to change
    // This could have been done during property traversal, but
    // This gives opportunity for review & modification if needed
    for (Property prop : props.values()) {
      if (prop.name.length() >= MIN_PROP_SIZE) {
        if (prop.accessorCount >= requiredUsage) {
          prop.aliasAccessor = true;
        }
        if (prop.mutatorCount >= requiredUsage) {
          prop.aliasMutator = true;
        }
      }
    }
    // Change the references to the property gets
    for (Node propInfo : accessors) {
      replaceAccessor(propInfo);
    }

    // Change the references to the property sets
    for (Node propInfo : mutators) {
      replaceMutator(propInfo);
    }

    // And add the accessor and mutator functions, if needed. Property names are
    // grouped together so that the CollapseVariableDeclarations pass can put
    // them in a single variable declaration statement.
    for (Property prop : props.values()) {
      if (prop.aliasAccessor) {
        addAccessorPropName(prop.name, getAddingRoot(prop.deepestModuleAccess));
      }
    }

    for (Property prop : props.values()) {
      if (prop.aliasMutator) {
        addMutatorFunction(prop.name, getAddingRoot(prop.deepestModuleMutate));
      }
    }
  }

  /*
   * Replaces a GETPROP with array notation, so that
   * it can be optimized.
   * I.e. prop.length -> prop[PROP_length] -> prop[a];
   */
  private void replaceAccessor(Node getPropNode) {
    /*
     *  BEFORE
        getprop
            NODE...
            string length
        AFTER
        getelem
            NODE...
            name PROP_length
     */
    Node propNameNode = getPropNode.getLastChild();
    String propName = propNameNode.getString();
    if (props.get(propName).aliasAccessor) {
      Node propSrc = getPropNode.getFirstChild();
      getPropNode.removeChild(propSrc);

      Node newNameNode =
        Node.newString(Token.NAME, getArrayNotationNameFor(propName));

      Node elemNode = new Node(Token.GETELEM, propSrc, newNameNode);
      replaceNode(getPropNode.getParent(), getPropNode, elemNode);

      compiler.reportCodeChange();
    }
  }

  /**
   * Changes a.prop = b to SETPROP_prop(a, b);
   */
  private void replaceMutator(Node getPropNode) {
    /*
       BEFORE
       exprstmt 1
           assign 128
               getprop
                   NodeTree A
                   string prop
               NODE TREE B

       AFTER
       exprstmt 1
           call
               name SETPROP_prop
               NodeTree A
               NODE TREE B
    */
    Node propNameNode = getPropNode.getLastChild();
    Node parentNode = getPropNode.getParent();

    Property prop = props.get(propNameNode.getString());
    if (prop.aliasMutator) {
      Node propSrc = getPropNode.getFirstChild();
      Node propDest = parentNode.getLastChild();

      // Remove the orphaned children
      getPropNode.removeChild(propSrc);
      getPropNode.removeChild(propNameNode);
      parentNode.removeChild(propDest);

      // Create the call GETPROP_prop() node, using the old propSrc as the
      // one paremeter to GETPROP_prop() call.
      Node callName = Node.newString(Token.NAME,
        getMutatorFor(propNameNode.getString()));
      Node call = new Node(Token.CALL, callName, propSrc, propDest);

      // And replace the assign statement with the new call
      replaceNode(parentNode.getParent(), parentNode, call);

      compiler.reportCodeChange();
    }
  }

  /**
   * Utility function to replace a Node with another node.
   * Keeps track of previous replacements so that if you try to replace
   * a child of a parent that has changed, it replaces on the new parent
   * @param parent Parent of node to be replaced
   * @param before Node to be replaced
   * @param after Replacement node
   */
  private void replaceNode(Node parent, Node before, Node after) {
    if (replacementMap.containsKey(parent)) {
      parent = replacementMap.get(parent);
    }
    parent.replaceChild(before, after);
    replacementMap.put(before, after);
  }

  /**
   * Adds a string that can be used to reference properties by array []
   * notation.
   *
   * PROP_prototype = 'prototype';
   *
   * @param propName Name of property
   * @param root Root of output tree that function can be added to
   */
  private void addAccessorPropName(String propName, Node root) {
    /*
     *  Target:

      var 1
        name PROP_length
            string length
     */
    Node propValue = Node.newString(Token.STRING, propName);
    Node propNameNode =
      Node.newString(Token.NAME, getArrayNotationNameFor(propName));
    propNameNode.addChildToFront(propValue);
    Node var = new Node(Token.VAR, propNameNode);
    root.addChildToFront(var);

    compiler.reportCodeChange();
  }

  /**
   * Create set property function in JS. Output will be:
   * SETPROP_prop(a, b) {a.prop = b;}
   *
   * @param propName Name of property
   * @param root Root of output tree that function can be added to
   */
  private void addMutatorFunction(String propName, Node root) {
    /*
      function SETPROP_prop
        name SETPROP_prop
        lp
            name a
            name b
        block 1
            return 1
                assign
                    getprop
                        name a
                        string prop
                    name b
    */

    // Function name node
    Node functionName = Node.newString(Token.NAME,
      getMutatorFor(propName));

    // Function arguments
    String localPropName = getMutatorFor(propName) + "$a";
    String localValueName = getMutatorFor(propName) + "$b";
    Node hasPropNode = Node.newString(Token.NAME, localPropName);
    Node propValueNode = Node.newString(Token.NAME, localValueName);
    Node args = new Node(Token.LP, hasPropNode, propValueNode);

    // Function body
    Node propNameNode = Node.newString(Token.NAME, localPropName);
    Node propValue = Node.newString(Token.STRING, propName);
    Node getProp = new Node(Token.GETPROP, propNameNode, propValue);
    Node assignFrom = Node.newString(Token.NAME, localValueName);
    Node assign = new Node(Token.ASSIGN, getProp, assignFrom);
    Node returnNode = new Node(Token.RETURN, assign);
    Node functionBlock = new Node(Token.BLOCK, returnNode);

    // Create the function and append to front of output tree
    FunctionNode fnNode = new FunctionNode(functionName.getString());
    // A hack to preserve the existing Compiler code that depends on
    // having the first child node being a NAME node.
    // TODO(user): Remove this for the final merge with Rhino and adjust the
    // rest of Compiler to know about the new location of function names.
    fnNode.addChildToBack(functionName);
    fnNode.addChildToBack(args);
    fnNode.addChildToBack(functionBlock);
    root.addChildToFront(fnNode);

    compiler.reportCodeChange();
  }

  /**
   * Gets a SCRIPT node for code insertion in {@code m} or, if {@code m} is
   * empty, in as deep an ancestor module of {@code m} as possible. Returns
   * {@code this.defaultRoot} if {@code m} is null.
   *
   * @param m The module to find a root in (may be null)
   * @return A root node
   */
  private Node getAddingRoot(JSModule m) {
    if (m != null) {
      Node root = moduleRoots.get(m);
      if (root != null) {
        return root;
      }

      root = compiler.getNodeForCodeInsertion(m);
      if (root != null) {
        moduleRoots.put(m, root);
        return root;
      }
    }

    return defaultRoot;
  }

  /**
   * Gets the aliasable names from externs.js
   */
  private class GetAliasableNames extends AbstractPostOrderCallback {
    private final Set<String> whitelist;

    public GetAliasableNames(final Set<String> whitelist) {
      this.whitelist = whitelist;
    }

    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP:
        case Token.GETELEM:
          Node dest = n.getFirstChild().getNext();
          if (dest.getType() == Token.STRING &&
              (whitelist.isEmpty() || whitelist.contains(dest.getString()))) {
            props.put(dest.getString(), new Property(dest.getString()));
          }
      }
    }
  }

  /**
   * Gets references to all of the replacable nodes, as well
   * as counting the usage for each property name.
   */
  private final class PropertyGatherer extends AbstractPostOrderCallback {

    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.GETPROP) {
        Node propNameNode = n.getLastChild();

        if (canReplaceWithGetProp(propNameNode, n, parent)) {
          String name = propNameNode.getString();
          props.get(name).recordAccessor(t);
          accessors.add(n);
        }
        if (canReplaceWithSetProp(propNameNode, n, parent)) {
          String name = propNameNode.getString();

          props.get(name).recordMutator(t);
          mutators.add(n);
        }
      }
    }

    /**
     * Logic for when a getprop can be replaced.
     * Can't alias a call to eval per ECMA 262 spec section 15.1.2.1
     * Can't be an assign -> no a.b = c;
     * Can't be inc or dec -> no a.b++; or a.b--;
     * Must be a GETPROP (NODE, A) where A is a reserved name
     * @param propNameNode Property name node
     * @param getPropNode GETPROP node
     * @param parent parent node
     * @return True if can be replaced
     */
    private boolean canReplaceWithGetProp(Node propNameNode, Node getPropNode,
          Node parent) {
      boolean isCallTarget = (parent.getType() == Token.CALL)
          && (parent.getFirstChild() == getPropNode);
      boolean isAssignTarget = NodeUtil.isAssignmentOp(parent)
          && (parent.getFirstChild() == getPropNode);
      boolean isIncOrDec = (parent.getType() == Token.INC) ||
          (parent.getType() == Token.DEC);
      return (propNameNode.getType() == Token.STRING) && !isAssignTarget
          && (!isCallTarget || !"eval".equals(propNameNode.getString()))
          && !isIncOrDec
          && props.containsKey(propNameNode.getString());
    }

    /**
     * Logic for whether a setprop can be replaced.
     *
     * True if it is target of assign (i.e. foo = A.B), and B is a reserved name
     * @param propNameNode Property name node
     * @param getPropNode GETPROP node
     * @param parent parent node
     * @return True if can be replaced
     */
    private boolean canReplaceWithSetProp(Node propNameNode, Node getPropNode,
        Node parent) {
      boolean isAssignTarget = (parent.getType() == Token.ASSIGN)
          && (parent.getFirstChild() == getPropNode);
      return (propNameNode.getType() == Token.STRING) && isAssignTarget
          && props.containsKey(propNameNode.getString());
    }
  }

  /**
   * Gets the mutator name for a property.
   */
  private static String getMutatorFor(String prop) {
    return "SETPROP_" + prop;
  }

  /**
   * Gets the array notation name for a property.
   */
  private static String getArrayNotationNameFor(String prop) {
    return "$$PROP_" + prop;
  }

  private void aliasGlobals(Node externs, Node root) {
    // Find all the extern globals that we should alias
    NodeTraversal.traverse(compiler, externs, new GetGlobals());

    // Find all the globals that can be changed
    NodeTraversal.traverse(compiler, root, new GlobalGatherer());

    // Iterate through the used globals, decide what to change.
    for (Property global : globals.values()) {
      if (global.mutatorCount > 0) {
        continue;
      }

      // We assume that each alias variable will end up compressed to two letter
      // names. There is also the overhead of "var xx=<global>;"
      int currentBytes = global.name.length() * global.accessorCount;
      int aliasedBytes = 8 + global.name.length() + 2 * global.accessorCount;

      if (aliasedBytes < currentBytes) {
        global.aliasAccessor = true;
      }
    }

    // Change the references to the globals
    for (Node globalUse : globalUses) {
      replaceGlobalUse(globalUse);
    }

    for (Property global : globals.values()) {
      if (global.aliasAccessor) {
        addGlobalAliasNode(global.name,
                           getAddingRoot(global.deepestModuleAccess));
      }
    }
  }

  /**
   * Gets the aliasable names from externs.js
   */
  private class GetGlobals extends NodeTraversal.AbstractShallowCallback {
    private void getGlobalName(NodeTraversal t, Node dest, Node parent) {
      if (dest.getType() == Token.NAME) {

        JSDocInfo docInfo = dest.getJSDocInfo() == null ?
            parent.getJSDocInfo() : dest.getJSDocInfo();
        boolean aliasable = !unaliasableGlobals.contains(dest.getString()) &&
            (docInfo == null || !docInfo.isNoAlias());

        if (aliasable) {
          String name = dest.getString();
          Scope.Var var = t.getScope().getVar(name);

          if (var != null && !var.isLocal()) {
            globals.put(name, new Property(name));
          }
        }
      }
    }

    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.FUNCTION:
          getGlobalName(t, n.getFirstChild(), n);
          break;
        case Token.VAR:
          for (Node varChild = n.getFirstChild();
               varChild != null;
               varChild = varChild.getNext()) {
            getGlobalName(t, varChild, n);
          }
          break;
      }
    }
  }

  /**
   * Gets references to all of the replacable nodes, as well as counting the
   * usage for each global.
   */
  private final class GlobalGatherer extends AbstractPostOrderCallback {
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.NAME) {
        String name = n.getString();
        Scope.Var var = t.getScope().getVar(name);

        // It's ok for var to be null since it won't be in any scope if it's
        // an extern
        if (var != null && var.isLocal()) {
          return;
        }

        Property global = globals.get(name);
        if (global != null) {
          // If a global is being assigned to or otherwise modified, then we
          // don't want to alias it.
          // Using "new" with this global is not a mutator, but it's also
          // something that we want to avoid when aliasing, since we may be
          // dealing with external objects (e.g. ActiveXObject in MSIE)
          if ((NodeUtil.isAssignmentOp(parent) &&
              parent.getFirstChild() == n) ||
              parent.getType() == Token.INC ||
              parent.getType() == Token.DEC ||
              parent.getType() == Token.NEW) {
            global.recordMutator(t);
          } else {
            global.recordAccessor(t);
          }

          globalUses.add(n);
        }
      }
    }
  }

  /**
   * Replace uses of a global with its aliased name.
   */
  private void replaceGlobalUse(Node globalUse) {
    String globalName = globalUse.getString();
    if (globals.get(globalName).aliasAccessor) {
      globalUse.setString("GLOBAL_" + globalName);
      compiler.reportCodeChange();
    }
  }

  /**
   * Adds an alias variable for the global:
   *
   * var GLOBAL_window = window;
   *
   * @param globalName Name of global
   * @param root Root of output tree that function can be added to
   */
  private void addGlobalAliasNode(String globalName, Node root) {
    /*
     *  Target:

      var 1
        name GLOBAL_window
            name window
     */
    Node globalValue = Node.newString(Token.NAME, globalName);
    Node globalNameNode =
      Node.newString(Token.NAME, "GLOBAL_" + globalName);
    globalNameNode.addChildToFront(globalValue);
    Node var = new Node(Token.VAR, globalNameNode);
    root.addChildToFront(var);

    compiler.reportCodeChange();
  }

  /** Struct to hold information about properties & usage */
  private class Property {
    public String name;
    public int accessorCount = 0;
    public int mutatorCount = 0;
    public boolean aliasMutator = false;
    public boolean aliasAccessor = false;

    JSModule deepestModuleAccess = null;
    JSModule deepestModuleMutate = null;

    public Property(String name) {
      this.name = name;
    }

    void recordAccessor(NodeTraversal t) {
      accessorCount++;
      if (moduleGraph != null) {
        deepestModuleAccess = (deepestModuleAccess == null) ?
            t.getModule() :
            moduleGraph.getDeepestCommonDependencyInclusive(
                t.getModule(), deepestModuleAccess);
      }
    }

    void recordMutator(NodeTraversal t) {
      mutatorCount++;
      if (moduleGraph != null) {
        deepestModuleMutate = (deepestModuleMutate == null) ?
            t.getModule() :
            moduleGraph.getDeepestCommonDependencyInclusive(
                t.getModule(), deepestModuleMutate);
      }
    }
  }
}
