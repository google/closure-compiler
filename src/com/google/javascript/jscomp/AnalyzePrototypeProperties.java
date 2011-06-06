/*
 * Copyright 2006 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Analyzes properties on prototypes.
 *
 * Uses a reference graph to analyze prototype properties. Each unique property
 * name is represented by a node in this graph. An edge from property A to
 * property B means that there's a GETPROP access of a property B on some
 * object inside of a method named A.
 *
 * Global functions are also represented by nodes in this graph, with
 * similar semantics.
 *
 */
class AnalyzePrototypeProperties implements CompilerPass {

  // Constants for symbol types, for easier readability.
  private final SymbolType PROPERTY = SymbolType.PROPERTY;
  private final SymbolType VAR = SymbolType.VAR;

  private final AbstractCompiler compiler;
  private final boolean canModifyExterns;
  private final boolean anchorUnusedVars;
  private final JSModuleGraph moduleGraph;
  private final JSModule firstModule;

  // Properties that are implicitly used as part of the JS language.
  private static final Set<String> IMPLICITLY_USED_PROPERTIES =
      ImmutableSet.of("length", "toString", "valueOf");

  // A graph where the nodes are property names or variable names,
  // and the edges signify the modules where the property is referenced.
  // For example, if we had the code:
  //
  // Foo.prototype.bar = function(x) { x.baz(); }; // in module 2.;
  //
  // then this would be represented in the graph by a node representing
  // "bar", a node representing "baz", and an edge between them representing
  // module #2.
  //
  // Similarly, if we had:
  //
  // var scotch = function(f) { return f.age(); };
  //
  // then there would be a node for "scotch", a node for "age", and an edge
  // from scotch to age.
  private final LinkedDirectedGraph<NameInfo, JSModule> symbolGraph =
      LinkedDirectedGraph.createWithoutAnnotations();

  // A dummy node for representing global references.
  private final NameInfo globalNode = new NameInfo("[global]");

  // A dummy node for representing extern references.
  private final NameInfo externNode = new NameInfo("[extern]");

  // A dummy node for representing all anonymous functions with no names.
  private final NameInfo anonymousNode = new NameInfo("[anonymous]");

  // All the real NameInfo for prototype properties, hashed by the name
  // of the property that they represent.
  private final Map<String, NameInfo> propertyNameInfo = Maps.newHashMap();

  // All the NameInfo for global functions, hashed by the name of the
  // gloval variable that it's assigned to.
  private final Map<String, NameInfo> varNameInfo = Maps.newHashMap();

  /**
   * Creates a new pass for analyzing prototype properties.
   * @param compiler The compiler.
   * @param moduleGraph The graph for resolving module dependencies. May be
   *     null if we don't care about module dependencies.
   * @param canModifyExterns If true, then we can move prototype
   *     properties that are declared in the externs file.
   * @param anchorUnusedVars If true, then we must mark all vars as referenced,
   *     even if they are never used.
   */
  AnalyzePrototypeProperties(AbstractCompiler compiler,
      JSModuleGraph moduleGraph, boolean canModifyExterns,
      boolean anchorUnusedVars) {
    this.compiler = compiler;
    this.moduleGraph = moduleGraph;
    this.canModifyExterns = canModifyExterns;
    this.anchorUnusedVars = anchorUnusedVars;

    if (moduleGraph != null) {
      firstModule = moduleGraph.getRootModule();
    } else {
      firstModule = null;
    }

    globalNode.markReference(null);
    externNode.markReference(null);
    symbolGraph.createNode(globalNode);
    symbolGraph.createNode(externNode);

    for (String property : IMPLICITLY_USED_PROPERTIES) {
      NameInfo nameInfo = getNameInfoForName(property, PROPERTY);
      if (moduleGraph == null) {
        symbolGraph.connect(externNode, null, nameInfo);
      } else {
        for (JSModule module : moduleGraph.getAllModules()) {
          symbolGraph.connect(externNode, module, nameInfo);
        }
      }
    }
  }

  public void process(Node externRoot, Node root) {
    if (!canModifyExterns) {
      NodeTraversal.traverse(compiler, externRoot,
          new ProcessExternProperties());
    }

    NodeTraversal.traverse(compiler, root, new ProcessProperties());

    FixedPointGraphTraversal<NameInfo, JSModule> t =
        FixedPointGraphTraversal.newTraversal(new PropagateReferences());
    t.computeFixedPoint(symbolGraph,
        Sets.newHashSet(externNode, globalNode));
  }

  /**
   * Returns information on all prototype properties.
   */
  public Collection<NameInfo> getAllNameInfo() {
    List<NameInfo> result = Lists.newArrayList(propertyNameInfo.values());
    result.addAll(varNameInfo.values());
    return result;
  }

  /**
   * Gets the name info for the property or variable of a given name,
   * and creates a new one if necessary.
   *
   * @param name The name of the symbol.
   * @param type The type of symbol.
   */
  private NameInfo getNameInfoForName(String name, SymbolType type) {
    Map<String, NameInfo> map = type == PROPERTY ?
        propertyNameInfo : varNameInfo;
    if (map.containsKey(name)) {
      return map.get(name);
    } else {
      NameInfo nameInfo = new NameInfo(name);
      map.put(name, nameInfo);
      symbolGraph.createNode(nameInfo);
      return nameInfo;
    }
  }

  private class ProcessProperties implements NodeTraversal.ScopedCallback {
    private Stack<NameContext> symbolStack = new Stack<NameContext>();

    private ProcessProperties() {
      symbolStack.push(new NameContext(globalNode));
    }

    @Override
    public void enterScope(NodeTraversal t) {
      symbolStack.peek().scope = t.getScope();
    }

    @Override
    public void exitScope(NodeTraversal t) {

    }

    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (isPrototypePropertyAssign(n)) {
        symbolStack.push(new NameContext(getNameInfoForName(
                n.getFirstChild().getLastChild().getString(), PROPERTY)));
      } else if (isGlobalFunctionDeclaration(t, n)) {
        String name = parent.getType() == Token.NAME ?
            parent.getString() /* VAR */ :
            n.getFirstChild().getString() /* named function */;
        symbolStack.push(new NameContext(getNameInfoForName(name, VAR)));
      } else if (NodeUtil.isFunction(n)) {
        symbolStack.push(new NameContext(anonymousNode));
      }
      return true;
    }

    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.GETPROP) {
        String propName = n.getFirstChild().getNext().getString();
        if (propName.equals("prototype")) {
          processPrototypeParent(t, parent);
        } else if (compiler.getCodingConvention().isExported(propName)) {
          addGlobalUseOfSymbol(propName, t.getModule(), PROPERTY);
        } else {
          addSymbolUse(propName, t.getModule(), PROPERTY);
        }
      } else if (n.getType() == Token.OBJECTLIT &&
          // Make sure that we're not handling object literals being
          // assigned to a prototype, as in:
          // Foo.prototype = {bar: 3, baz: 5};
          !(parent.getType() == Token.ASSIGN &&
            parent.getFirstChild().getType() == Token.GETPROP &&
            parent.getFirstChild().getLastChild().getString().equals(
                "prototype"))) {
        // var x = {a: 1, b: 2}
        // should count as a use of property a and b.
        for (Node propNameNode = n.getFirstChild(); propNameNode != null;
             propNameNode = propNameNode.getNext()) {
          // May be STRING, GET, or SET, but NUMBER isn't interesting.
          if (!propNameNode.isQuotedString()) {
            addSymbolUse(propNameNode.getString(), t.getModule(), PROPERTY);
          }
        }
      } else if (n.getType() == Token.NAME) {
        String name = n.getString();

        Var var = t.getScope().getVar(name);
        if (var != null) {
          // Only process global functions.
          if (var.isGlobal()) {
            if (var.getInitialValue() != null &&
                var.getInitialValue().getType() == Token.FUNCTION) {
              if (t.inGlobalScope()) {
                if (!processGlobalFunctionDeclaration(t, n, parent,
                        parent.getParent())) {
                  addGlobalUseOfSymbol(name, t.getModule(), VAR);
                }
              } else {
                addSymbolUse(name, t.getModule(), VAR);
              }
            }

          // If it is not a global, it might be accessing a local of the outer
          // scope. If that's the case the functions between the variable's
          // declaring scope and the variable reference scope cannot be moved.
          } else if (var.getScope() != t.getScope()){
            for (int i = symbolStack.size() - 1; i >= 0; i--) {
              NameContext context = symbolStack.get(i);
              context.name.readClosureVariables = true;
              if (context.scope == var.getScope()) {
                break;
              }
            }
          }
        }
      }

      if (isPrototypePropertyAssign(n) ||
          isGlobalFunctionDeclaration(t, n) ||
          NodeUtil.isFunction(n)) {
        symbolStack.pop();
      }
    }

    private void addSymbolUse(String name, JSModule module, SymbolType type) {
      NameInfo info = getNameInfoForName(name, type);
      NameInfo def = null;
      // Skip all anonymous nodes. We care only about symbols with names.
      for (int i = symbolStack.size() - 1; i >= 0; i--) {
        def = symbolStack.get(i).name;
        if (def != anonymousNode) {
          break;
        }
      }
      if (!def.equals(info)) {
        symbolGraph.connect(def, module, info);
      }
    }

    /**
     * Determines whether {@code n} is the FUNCTION node in a global function
     * declaration.
     */
    private boolean isGlobalFunctionDeclaration(NodeTraversal t, Node n) {
      return t.inGlobalScope() &&
          (NodeUtil.isFunctionDeclaration(n) ||
           n.getType() == Token.FUNCTION &&
           n.getParent().getType() == Token.NAME);
    }

    private boolean isPrototypePropertyAssign(Node assign) {
      Node n = assign.getFirstChild();
      if (n != null && NodeUtil.isVarOrSimpleAssignLhs(n, assign)
          && n.getType() == Token.GETPROP
          && assign.getParent().getType() == Token.EXPR_RESULT) {
        // We want to exclude the assignment itself from the usage list
        boolean isChainedProperty =
            n.getFirstChild().getType() == Token.GETPROP;

        if (isChainedProperty) {
          Node child = n.getFirstChild().getFirstChild().getNext();

          if (child.getType() == Token.STRING &&
              child.getString().equals("prototype")) {
            return true;
          }
        }
      }

      return false;
    }

    /**
     * Processes a NAME node to see if it's a global function declaration.
     * If it is, record it and return true. Otherwise, return false.
     */
    private boolean processGlobalFunctionDeclaration(NodeTraversal t,
        Node nameNode, Node parent, Node gramps) {
      Node firstChild = nameNode.getFirstChild();

      if (// Check for a named FUNCTION.
          isGlobalFunctionDeclaration(t, parent) ||
          // Check for a VAR declaration.
          firstChild != null &&
          isGlobalFunctionDeclaration(t, firstChild)) {
        String name = nameNode.getString();
        getNameInfoForName(name, VAR).getDeclarations().add(
            new GlobalFunction(nameNode, parent, gramps, t.getModule()));

        // If the function name is exported, we should create an edge here
        // so that it's never removed.
        if (compiler.getCodingConvention().isExported(name) ||
            anchorUnusedVars) {
          addGlobalUseOfSymbol(name, t.getModule(), VAR);
        }

        return true;
      }
      return false;
    }

    /**
     * Processes the parent of a GETPROP prototype, which can either be
     * another GETPROP (in the case of Foo.prototype.bar), or can be
     * an assignment (in the case of Foo.prototype = ...).
     */
    private void processPrototypeParent(NodeTraversal t, Node n) {
      switch (n.getType()) {
        // Foo.prototype.getBar = function() { ... }
        case Token.GETPROP:
          Node dest = n.getFirstChild().getNext();
          Node parent = n.getParent();
          Node grandParent = parent.getParent();

          if (dest.getType() == Token.STRING &&
              NodeUtil.isExprAssign(grandParent) &&
              NodeUtil.isVarOrSimpleAssignLhs(n, parent)) {
            String name = dest.getString();
            Property prop = new AssignmentProperty(grandParent, t.getModule());
            getNameInfoForName(name, PROPERTY).getDeclarations().add(prop);
          }
          break;

        // Foo.prototype = { "getBar" : function() { ... } }
        case Token.ASSIGN:
          Node map = n.getFirstChild().getNext();
          if (map.getType() == Token.OBJECTLIT) {
            for (Node key = map.getFirstChild();
                 key != null; key = key.getNext()) {
              // May be STRING, GET, or SET,
              String name = key.getString();
              Property prop = new LiteralProperty(
                  key, key.getFirstChild(), map, n, t.getModule());
              getNameInfoForName(name, PROPERTY).getDeclarations().add(prop);
            }
          }
          break;
      }
    }

    private void addGlobalUseOfSymbol(String name, JSModule module,
        SymbolType type) {
      symbolGraph.connect(globalNode, module, getNameInfoForName(name, type));
    }
  }

  private class ProcessExternProperties extends AbstractPostOrderCallback {
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.GETPROP) {
        symbolGraph.connect(externNode, firstModule,
            getNameInfoForName(n.getLastChild().getString(), PROPERTY));
      }
    }
  }

  private class PropagateReferences
      implements EdgeCallback<NameInfo, JSModule> {
    public boolean traverseEdge(NameInfo start, JSModule edge, NameInfo dest) {
      if (start.isReferenced()) {
        JSModule startModule = start.getDeepestCommonModuleRef();
        if (startModule != null &&
            moduleGraph.dependsOn(startModule, edge)) {
          return dest.markReference(startModule);
        } else {
          return dest.markReference(edge);
        }
      }
      return false;
    }
  }

  // TODO(user): We can use DefinitionsRemover and UseSite here. Then all
  // we need to do is call getDefinition() and we'll magically know everything
  // about the definition.

  /**
   * The declaration of an abstract symbol.
   */
  interface Symbol {
    /**
     * Remove the declaration from the AST.
     */
    void remove();

    /**
     * Returns the module where this appears.
     */
    JSModule getModule();
  }

  private enum SymbolType {
    PROPERTY,
    VAR;
  }

  /**
   * A function initialized as a VAR statement or a function declaration.
   */
   class GlobalFunction implements Symbol {
    private final Node nameNode;
    private final JSModule module;

    GlobalFunction(Node nameNode, Node parent, Node gramps, JSModule module) {
      Preconditions.checkState(
          parent.getType() == Token.VAR ||
          NodeUtil.isFunctionDeclaration(parent));
      this.nameNode = nameNode;
      this.module = module;
    }

    @Override
    public void remove() {
      Node parent = nameNode.getParent();
      if (parent.getType() == Token.FUNCTION || parent.hasOneChild()) {
        NodeUtil.removeChild(parent.getParent(), parent);
      } else {
        Preconditions.checkState(parent.getType() == Token.VAR);
        parent.removeChild(nameNode);
      }
    }

    @Override
    public JSModule getModule() {
      return module;
    }

    public Node getFunctionNode() {
      Node parent = nameNode.getParent();

      if (NodeUtil.isFunction(parent)) {
        return parent;
      } else {
        // we are the name of a var node, so the function is name's second child
        return nameNode.getChildAtIndex(1);
      }
    }
  }

  /**
   * Since there are two ways of assigning properties to prototypes, we hide
   * then behind this interface so they can both be removed regardless of type.
   */
  interface Property extends Symbol {

    /** Returns the GETPROP node that refers to the prototype. */
    Node getPrototype();

    /** Returns the value of this property. */
    Node getValue();
  }

  /**
   * Properties created via EXPR assignment:
   *
   * <pre>function Foo() { ... };
   * Foo.prototype.bar = function() { ... };</pre>
   */
  static class AssignmentProperty implements Property {
    private final Node exprNode;
    private final JSModule module;

    /**
     * @param node An EXPR node.
     */
    AssignmentProperty(Node node, JSModule module) {
      this.exprNode = node;
      this.module = module;
    }

    @Override
    public void remove() {
      NodeUtil.removeChild(exprNode.getParent(), exprNode);
    }

    @Override
    public Node getPrototype() {
      return getAssignNode().getFirstChild().getFirstChild();
    }

    @Override
    public Node getValue() {
      return getAssignNode().getLastChild();
    }

    private Node getAssignNode() {
      return exprNode.getFirstChild();
    }

    @Override
    public JSModule getModule() {
      return module;
    }
  }

  /**
   * Properties created via object literals:
   *
   * <pre>function Foo() { ... };
   * Foo.prototype = {bar: function() { ... };</pre>
   */
  static class LiteralProperty implements Property {
    private final Node key;
    private final Node value;
    private final Node map;
    private final Node assign;
    private final JSModule module;

    LiteralProperty(Node key, Node value, Node map, Node assign,
        JSModule module) {
      this.key = key;
      this.value = value;
      this.map = map;
      this.assign = assign;
      this.module = module;
    }

    @Override
    public void remove() {
      map.removeChild(key);
    }

    @Override
    public Node getPrototype() {
      return assign.getFirstChild();
    }

    @Override
    public Node getValue() {
      return value;
    }

    @Override
    public JSModule getModule() {
      return module;
    }
  }

  /**
   * The context of the current name. This includes the NameInfo and the scope
   * if it is a scope defining name (function).
   */
  private class NameContext {
    final NameInfo name;
    Scope scope;
    NameContext(NameInfo name) {
      this.name = name;
    }
  }

  /**
   * Information on all properties or global variables of a given name.
   */
  class NameInfo {

    final String name;

    private boolean referenced = false;
    private final Deque<Symbol> declarations = new ArrayDeque<Symbol>();
    private JSModule deepestCommonModuleRef = null;

    // True if this property is a function that reads a variable from an
    // outer scope which isn't the global scope.
    private boolean readClosureVariables = false;

    /**
     * Constructs a new NameInfo.
     * @param name The name of the property that this represents. May be null
     *     to signify dummy nodes in the property graph.
     */
    NameInfo(String name) {
      this.name = name;
    }

    @Override public String toString() { return name; }

    /** Determines whether we've marked a reference to this property name. */
    boolean isReferenced() {
      return referenced;
    }

    /** Determines whether it reads a closure variable. */
    boolean readsClosureVariables() {
      return readClosureVariables;
    }

    /**
     * Mark a reference in a given module to this property name, and record
     * the deepest common module reference.
     * @param module The module where it was referenced.
     * @return Whether the name info has changed.
     */
    boolean markReference(JSModule module) {
      boolean hasChanged = false;
      if (!referenced) {
        referenced = true;
        hasChanged = true;
      }

      if (moduleGraph != null) {
        JSModule originalDeepestCommon = deepestCommonModuleRef;

        if (deepestCommonModuleRef == null) {
          deepestCommonModuleRef = module;
        } else {
          deepestCommonModuleRef =
              moduleGraph.getDeepestCommonDependencyInclusive(
                  deepestCommonModuleRef, module);
        }

        if (originalDeepestCommon != deepestCommonModuleRef) {
          hasChanged = true;
        }
      }

      return hasChanged;
    }

    /**
     * Returns the deepest common module of all the references to this
     * property.
     */
    JSModule getDeepestCommonModuleRef() {
      return deepestCommonModuleRef;
    }

    /**
     * Returns a mutable collection of all the prototype property declarations
     * of this property name.
     */
    Deque<Symbol> getDeclarations() {
      return declarations;
    }
  }
}
