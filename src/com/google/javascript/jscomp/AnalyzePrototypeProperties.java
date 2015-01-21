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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
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
 * @author nicksantos@google.com (Nick Santos)
 */
class AnalyzePrototypeProperties implements CompilerPass {

  // Constants for symbol types, for easier readability.
  private static final SymbolType PROPERTY = SymbolType.PROPERTY;
  private static final SymbolType VAR = SymbolType.VAR;

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
  private final Map<String, NameInfo> propertyNameInfo = Maps.newLinkedHashMap();

  // All the NameInfo for global functions, hashed by the name of the
  // global variable that it's assigned to.
  private final Map<String, NameInfo> varNameInfo = Maps.newLinkedHashMap();

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

  @Override
  public void process(Node externRoot, Node root) {
    if (!canModifyExterns) {
      NodeTraversal.traverse(compiler, externRoot,
          new ProcessExternProperties());
    }

    NodeTraversal.traverse(compiler, root, new ProcessProperties());

    FixedPointGraphTraversal<NameInfo, JSModule> t =
        FixedPointGraphTraversal.newTraversal(new PropagateReferences());
    t.computeFixedPoint(symbolGraph,
        ImmutableSet.of(externNode, globalNode));
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
    // There are two types of context information on this stack:
    // 1) Every scope has a NameContext corresponding to its scope.
    //    Variables are given VAR contexts.
    //    Prototype properties are given PROPERTY contexts.
    //    The global scope is given the special [global] context.
    //    And function expressions that we aren't able to give a reasonable
    //    name are given a special [anonymous] context.
    // 2) Every assignment of a prototype property of a non-function is
    //    given a name context. These contexts do not have scopes.
    private final Stack<NameContext> symbolStack = new Stack<>();

    @Override
    public void enterScope(NodeTraversal t) {
      Node n = t.getCurrentNode();
      if (n.isFunction()) {
        String propName = getPrototypePropertyNameFromRValue(n);
        if (propName != null) {
          symbolStack.push(
              new NameContext(
                  getNameInfoForName(propName, PROPERTY),
                  t.getScope()));
        } else if (isGlobalFunctionDeclaration(t, n)) {
          Node parent = n.getParent();
          String name = parent.isName() ?
              parent.getString() /* VAR */ :
              n.getFirstChild().getString() /* named function */;
          symbolStack.push(
              new NameContext(getNameInfoForName(name, VAR), t.getScope()));
        } else {
          // NOTE(nicksantos): We use the same anonymous node for all
          // functions that do not have reasonable names. I can't remember
          // at the moment why we do this. I think it's because anonymous
          // nodes can never have in-edges. They're just there as a placeholder
          // for scope information, and do not matter in the edge propagation.
          symbolStack.push(new NameContext(anonymousNode, t.getScope()));
        }
      } else {
        Preconditions.checkState(t.inGlobalScope());
        symbolStack.push(new NameContext(globalNode, t.getScope()));
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
      symbolStack.pop();
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      // Process prototype assignments to non-functions.
      String propName = processNonFunctionPrototypeAssign(n, parent);
      if (propName != null) {
        symbolStack.push(
            new NameContext(
                getNameInfoForName(propName, PROPERTY), null));
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isGetProp()) {
        String propName = n.getFirstChild().getNext().getString();

        if (n.isQualifiedName()) {
          if (propName.equals("prototype")) {
            if (processPrototypeRef(t, n)) {
              return;
            }
          } else if (compiler.getCodingConvention().isExported(propName)) {
            addGlobalUseOfSymbol(propName, t.getModule(), PROPERTY);
            return;
          } else {
            // Do not mark prototype prop assigns as a 'use' in the global scope.
            if (n.getParent().isAssign() && n.getNext() != null) {
              String rValueName = getPrototypePropertyNameFromRValue(n);
              if (rValueName != null) {
                return;
              }
            }
          }
        }

        addSymbolUse(propName, t.getModule(), PROPERTY);
      } else if (n.isObjectLit()) {
        // Make sure that we're not handling object literals being
        // assigned to a prototype, as in:
        // Foo.prototype = {bar: 3, baz: 5};
        String lValueName = NodeUtil.getBestLValueName(
            NodeUtil.getBestLValue(n));
        if (lValueName != null && lValueName.endsWith(".prototype")) {
          return;
        }

        // var x = {a: 1, b: 2}
        // should count as a use of property a and b.
        for (Node propNameNode = n.getFirstChild(); propNameNode != null;
             propNameNode = propNameNode.getNext()) {
          // May be STRING, GET, or SET, but NUMBER isn't interesting.
          if (!propNameNode.isQuotedString()) {
            addSymbolUse(propNameNode.getString(), t.getModule(), PROPERTY);
          }
        }
      } else if (n.isName()) {
        String name = n.getString();

        Var var = t.getScope().getVar(name);
        if (var != null) {
          // Only process global functions.
          if (var.isGlobal()) {
            if (var.getInitialValue() != null &&
                var.getInitialValue().isFunction()) {
              if (t.inGlobalScope()) {
                if (!processGlobalFunctionDeclaration(t, n, var)) {
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
              if (context.scope == var.getScope()) {
                break;
              }

              context.name.readClosureVariables = true;
            }
          }
        }
      }

      // Process prototype assignments to non-functions.
      if (processNonFunctionPrototypeAssign(n, parent) != null) {
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
     * If this is a non-function prototype assign, return the prop name.
     * Otherwise, return null.
     */
    private String processNonFunctionPrototypeAssign(Node n, Node parent) {
      if (isAssignRValue(n, parent) && !n.isFunction()) {
        return getPrototypePropertyNameFromRValue(n);
      }
      return null;
    }

    /**
     * Determines whether {@code n} is the FUNCTION node in a global function
     * declaration.
     */
    private boolean isGlobalFunctionDeclaration(NodeTraversal t, Node n) {
      // Make sure we're either in the global scope, or the function
      // we're looking at is the root of the current local scope.
      Scope s = t.getScope();
      if (!(s.isGlobal() ||
            s.getDepth() == 1 && s.getRootNode() == n)) {
        return false;
      }

      return NodeUtil.isFunctionDeclaration(n) ||
          n.isFunction() && n.getParent().isName();
    }

    /**
     * Returns true if this is the r-value of an assignment.
     */
    private boolean isAssignRValue(Node n, Node parent) {
      return parent != null && parent.isAssign() && parent.getFirstChild() != n;
    }

    /**
     * Returns the name of a prototype property being assigned to this r-value.
     *
     * Returns null if this is not the R-value of a prototype property, or if
     * the R-value is used in multiple expressions (i.e., if there's
     * a prototype property assignment in a more complex expression).
     */
    private String getPrototypePropertyNameFromRValue(Node rValue) {
      Node lValue = NodeUtil.getBestLValue(rValue);
      if (lValue == null ||
          lValue.getParent() == null ||
          lValue.getParent().getParent() == null ||
          !((NodeUtil.isObjectLitKey(lValue) && !lValue.isQuotedString()) ||
            NodeUtil.isExprAssign(lValue.getParent().getParent()))) {
        return null;
      }

      String lValueName =
          NodeUtil.getBestLValueName(NodeUtil.getBestLValue(rValue));
      if (lValueName == null) {
        return null;
      }
      int lastDot = lValueName.lastIndexOf('.');
      if (lastDot == -1) {
        return null;
      }

      String firstPart = lValueName.substring(0, lastDot);
      if (!firstPart.endsWith(".prototype")) {
        return null;
      }

      return lValueName.substring(lastDot + 1);
    }

    /**
     * Processes a NAME node to see if it's a global function declaration.
     * If it is, record it and return true. Otherwise, return false.
     */
    private boolean processGlobalFunctionDeclaration(NodeTraversal t,
        Node nameNode, Var v) {
      Node firstChild = nameNode.getFirstChild();
      Node parent = nameNode.getParent();

      if (// Check for a named FUNCTION.
          isGlobalFunctionDeclaration(t, parent) ||
          // Check for a VAR declaration.
          firstChild != null &&
          isGlobalFunctionDeclaration(t, firstChild)) {
        String name = nameNode.getString();
        getNameInfoForName(name, VAR).getDeclarations().add(
            new GlobalFunction(nameNode, v, t.getModule()));

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
     * Processes the GETPROP of prototype, which can either be under
     * another GETPROP (in the case of Foo.prototype.bar), or can be
     * under an assignment (in the case of Foo.prototype = ...).
     * @return True if a declaration was added.
     */
    private boolean processPrototypeRef(NodeTraversal t, Node ref) {
      Node root = NodeUtil.getRootOfQualifiedName(ref);

      Node n = ref.getParent();
      switch (n.getType()) {
        // Foo.prototype.getBar = function() { ... }
        case Token.GETPROP:
          Node dest = n.getFirstChild().getNext();
          Node parent = n.getParent();
          Node grandParent = parent.getParent();

          if (dest.isString() &&
              NodeUtil.isExprAssign(grandParent) &&
              NodeUtil.isVarOrSimpleAssignLhs(n, parent)) {
            String name = dest.getString();
            Property prop = new AssignmentProperty(
                grandParent,
                maybeGetVar(t, root),
                t.getModule());
            getNameInfoForName(name, PROPERTY).getDeclarations().add(prop);
            return true;
          }
          break;

        // Foo.prototype = { "getBar" : function() { ... } }
        case Token.ASSIGN:
          Node map = n.getFirstChild().getNext();
          if (map.isObjectLit()) {
            for (Node key = map.getFirstChild();
                 key != null; key = key.getNext()) {
              if (!key.isQuotedString()) {
                // May be STRING, GETTER_DEF, or SETTER_DEF,
                String name = key.getString();
                Property prop = new LiteralProperty(
                    key, key.getFirstChild(), map, n,
                    maybeGetVar(t, root),
                    t.getModule());
                getNameInfoForName(name, PROPERTY).getDeclarations().add(prop);
              }
            }
            return true;
          }
          break;
      }
      return false;
    }

    private Var maybeGetVar(NodeTraversal t, Node maybeName) {
      return maybeName.isName()
          ? t.getScope().getVar(maybeName.getString()) : null;
    }

    private void addGlobalUseOfSymbol(String name, JSModule module,
        SymbolType type) {
      symbolGraph.connect(globalNode, module, getNameInfoForName(name, type));
    }
  }

  private class ProcessExternProperties extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isGetProp()) {
        symbolGraph.connect(externNode, firstModule,
            getNameInfoForName(n.getLastChild().getString(), PROPERTY));
      }
    }
  }

  private class PropagateReferences
      implements EdgeCallback<NameInfo, JSModule> {
    @Override
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
    void remove(AbstractCompiler compiler);

    /**
     * The variable for the root of this symbol.
     */
    Var getRootVar();

    /**
     * Returns the module where this appears.
     */
    JSModule getModule();
  }

  private static enum SymbolType {
    PROPERTY,
    VAR
  }

  /**
   * A function initialized as a VAR statement or a function declaration.
   */
  static class GlobalFunction implements Symbol {
    private final Node nameNode;
    private final Var var;
    private final JSModule module;

    GlobalFunction(Node nameNode, Var var, JSModule module) {
      Node parent = nameNode.getParent();
      Preconditions.checkState(
          parent.isVar() ||
          NodeUtil.isFunctionDeclaration(parent));
      this.nameNode = nameNode;
      this.var = var;
      this.module = module;
    }

    @Override
    public Var getRootVar() {
      return var;
    }

    @Override
    public void remove(AbstractCompiler compiler) {
      Node parent = nameNode.getParent();
      compiler.reportChangeToEnclosingScope(parent);
      if (parent.isFunction() || parent.hasOneChild()) {
        NodeUtil.removeChild(parent.getParent(), parent);
      } else {
        Preconditions.checkState(parent.isVar());
        parent.removeChild(nameNode);
      }
    }

    @Override
    public JSModule getModule() {
      return module;
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
    private final Var rootVar;
    private final JSModule module;

    /**
     * @param node An EXPR node.
     */
    AssignmentProperty(Node node, Var rootVar, JSModule module) {
      this.exprNode = node;
      this.rootVar = rootVar;
      this.module = module;
    }

    @Override
    public Var getRootVar() {
      return rootVar;
    }

    @Override
    public void remove(AbstractCompiler compiler) {
      compiler.reportChangeToEnclosingScope(exprNode);
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
    private final Var rootVar;
    private final JSModule module;

    LiteralProperty(Node key, Node value, Node map, Node assign,
        Var rootVar, JSModule module) {
      this.key = key;
      this.value = value;
      this.map = map;
      this.assign = assign;
      this.rootVar = rootVar;
      this.module = module;
    }

    @Override
    public Var getRootVar() {
      return rootVar;
    }

    @Override
    public void remove(AbstractCompiler compiler) {
      compiler.reportChangeToEnclosingScope(key);
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

    // If this is a function context, then scope will be the scope of the
    // corresponding function. Otherwise, it will be null.
    final Scope scope;

    NameContext(NameInfo name, Scope scope) {
      this.name = name;
      this.scope = scope;
    }
  }

  /**
   * Information on all properties or global variables of a given name.
   */
  class NameInfo {

    final String name;

    private boolean referenced = false;
    private final Deque<Symbol> declarations = new ArrayDeque<>();
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
