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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes properties on prototypes.
 *
 * <p>Uses a reference graph to analyze prototype properties. Each unique property name is
 * represented by a node in this graph. An edge from property A to property B means that there's a
 * GETPROP access of a property B on some object inside of a method named A.
 *
 * <p>Global functions are also represented by nodes in this graph, with similar semantics.
 */
class AnalyzePrototypeProperties implements CompilerPass {

  // Constants for symbol types, for easier readability.
  private static final SymbolType PROPERTY = SymbolType.PROPERTY;
  private static final SymbolType VAR = SymbolType.VAR;

  private final AbstractCompiler compiler;
  private final boolean canModifyExterns;
  private final boolean anchorUnusedVars;
  private final boolean rootScopeUsesAreGlobal;

  private final JSModuleGraph moduleGraph;
  private final JSModule firstModule;

  // Properties that are implicitly used as part of the JS language.
  private static final ImmutableSet<String> IMPLICITLY_USED_PROPERTIES =
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
  private final Map<String, NameInfo> propertyNameInfo = new LinkedHashMap<>();

  // All the NameInfo for global functions, hashed by the name of the
  // global variable that it's assigned to.
  private final Map<String, NameInfo> varNameInfo = new LinkedHashMap<>();

  /**
   * Creates a new pass for analyzing prototype properties.
   *
   * @param compiler The compiler.
   * @param moduleGraph The graph for resolving module dependencies.
   * @param canModifyExterns If true, then we can move prototype properties that are declared in the
   *     externs file.
   * @param anchorUnusedVars If true, then we must mark all vars as referenced, even if they are
   *     never used.
   * @param rootScopeUsesAreGlobal If true, all uses in root level scope are treated as references
   *     from '[global]', even if they are assignments to a property.
   */
  AnalyzePrototypeProperties(
      AbstractCompiler compiler,
      JSModuleGraph moduleGraph,
      boolean canModifyExterns,
      boolean anchorUnusedVars,
      boolean rootScopeUsesAreGlobal) {
    this.compiler = compiler;
    this.moduleGraph = moduleGraph;
    this.canModifyExterns = canModifyExterns;
    this.anchorUnusedVars = anchorUnusedVars;
    this.rootScopeUsesAreGlobal = rootScopeUsesAreGlobal;

    if (moduleGraph.getModuleCount() > 1) {
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
    checkState(compiler.getLifeCycleStage().isNormalized());
    if (!canModifyExterns) {
      NodeTraversal.traverse(compiler, externRoot, new ProcessExternProperties());
    }

    NodeTraversal.traverse(compiler, root, new ProcessProperties());

    FixedPointGraphTraversal<NameInfo, JSModule> t =
        FixedPointGraphTraversal.newTraversal(new PropagateReferences());
    t.computeFixedPoint(symbolGraph, ImmutableSet.of(externNode, globalNode));
  }

  /** Returns information on all prototype properties. */
  public Collection<NameInfo> getAllNameInfo() {
    List<NameInfo> result = new ArrayList<>(propertyNameInfo.values());
    result.addAll(varNameInfo.values());
    return result;
  }

  /**
   * Gets the name info for the property or variable of a given name, and creates a new one if
   * necessary.
   *
   * @param name The name of the symbol.
   * @param type The type of symbol.
   */
  private NameInfo getNameInfoForName(String name, SymbolType type) {
    Map<String, NameInfo> map = type == PROPERTY ? propertyNameInfo : varNameInfo;
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
    private final Deque<NameContext> symbolStack = new ArrayDeque<>();

    @Override
    public void enterScope(NodeTraversal t) {
      Node n = t.getCurrentNode();
      Scope scope = t.getScope();
      Node root = scope.getRootNode();
      if (root.isFunction()) {
        String propName = getPrototypePropertyNameFromRValue(n);
        if (propName != null) {
          symbolStack.push(new NameContext(getNameInfoForName(propName, PROPERTY), scope));
        } else if (isGlobalFunctionDeclaration(t, n)) {
          Node parent = n.getParent();
          String name =
              parent.isName()
                  ? parent.getString() /* VAR */
                  : n.getFirstChild().getString() /* named function */;
          symbolStack.push(
              new NameContext(getNameInfoForName(name, VAR), scope.getClosestHoistScope()));
        } else {
          // NOTE(nicksantos): We use the same anonymous node for all
          // functions that do not have reasonable names. I can't remember
          // at the moment why we do this. I think it's because anonymous
          // nodes can never have in-edges. They're just there as a placeholder
          // for scope information, and do not matter in the edge propagation.
          symbolStack.push(new NameContext(anonymousNode, scope));
        }
      } else if (t.inGlobalScope()) {
        symbolStack.push(new NameContext(globalNode, scope));
      } else {
        // TODO(moz): It's not yet clear if we need another kind of NameContext for block scopes
        // in ES6, use anonymous node for now and investigate later.
        checkState(NodeUtil.createsBlockScope(root) || root.isModuleBody(), scope);
        symbolStack.push(new NameContext(anonymousNode, scope));
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
      symbolStack.pop();
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (!rootScopeUsesAreGlobal) {
        // Process assignment of a non-function expression to a prototype property.
        String propName = processNonFunctionPrototypeAssign(n, parent);
        if (propName != null) {
          symbolStack.push(new NameContext(getNameInfoForName(propName, PROPERTY), null));
        }
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case GETPROP:
          String propName = n.getSecondChild().getString();

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
              if (parent.isAssign() && n == parent.getFirstChild()) {
                String rValueName = getPrototypePropertyNameFromRValue(n);
                if (rValueName != null) {
                  return;
                }
              }
            }
          }

          addSymbolUse(propName, t.getModule(), PROPERTY);
          break;

        case OBJECTLIT:
          // Make sure that we're not handling object literals being
          // assigned to a prototype, as in:
          // Foo.prototype = {bar: 3, baz: 5};
          String lValueName = NodeUtil.getBestLValueName(NodeUtil.getBestLValue(n));
          if (lValueName != null && lValueName.endsWith(".prototype")) {
            return;
          }

          // Fall through.
        case OBJECT_PATTERN:
          // `var x = {a: 1, b: 2}` and `var {a: x, b: y} = obj;`
          // should count as a use of property a and b.
          for (Node propNode = n.getFirstChild(); propNode != null; propNode = propNode.getNext()) {
            switch (propNode.getToken()) {
              case COMPUTED_PROP:
              case ITER_REST:
              case OBJECT_REST:
              case ITER_SPREAD:
              case OBJECT_SPREAD:
                break;

              case STRING_KEY:
              case GETTER_DEF:
              case SETTER_DEF:
              case MEMBER_FUNCTION_DEF:
                if (!propNode.isQuotedString()) {
                  // May be STRING, GET, or SET, but NUMBER isn't interesting.
                  addSymbolUse(propNode.getString(), t.getModule(), PROPERTY);
                }
                break;

              default:
                throw new IllegalStateException(
                    "Unexpected child of " + n.getToken() + ": " + propNode.toStringTree());
            }
          }
          break;

        case CLASS:
          Node classMembers = n.getLastChild();
          for (Node child = classMembers.getFirstChild(); child != null; child = child.getNext()) {
            if (child.isMemberFunctionDef() || child.isSetterDef() || child.isGetterDef()) {
              processMemberDef(t, child);
            }
          }
          break;

        case NAME:
          String name = n.getString();

          Var var = t.getScope().getVar(name);
          if (var != null) {
            // Only process global functions.
            if (var.isGlobal()) {
              if (var.getInitialValue() != null && var.getInitialValue().isFunction()) {
                if (t.inGlobalHoistScope()) {
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
            } else if (var.getScope() != t.getScope()) {
              for (NameContext context : symbolStack) {
                if (context.scope == var.getScope()) {
                  break;
                }

                context.name.readClosureVariables = true;
              }
            }
          }
          break;

        default:
          break;
      }
      // Process prototype assignments to non-functions.
      if (!rootScopeUsesAreGlobal && processNonFunctionPrototypeAssign(n, parent) != null) {
        symbolStack.pop();
      }
    }

    private void addSymbolUse(String name, JSModule module, SymbolType type) {
      NameInfo info = getNameInfoForName(name, type);
      NameInfo def = null;
      // Skip all anonymous nodes. We care only about symbols with names.
      for (NameContext context : symbolStack) {
        def = context.name;
        if (def != anonymousNode) {
          break;
        }
      }
      if (!def.equals(info)) {
        symbolGraph.connect(def, module, info);
      }
    }

    /** If this is a non-function prototype assign, return the prop name. Otherwise, return null. */
    private String processNonFunctionPrototypeAssign(Node n, Node parent) {
      if (isAssignRValue(n, parent) && !n.isFunction()) {
        return getPrototypePropertyNameFromRValue(n);
      }
      return null;
    }

    /** Determines whether {@code n} is the FUNCTION node in a global function declaration. */
    private boolean isGlobalFunctionDeclaration(NodeTraversal t, Node n) {
      // Make sure we're not in a function scope, or if we are then the function we're looking at
      // is defined in the global scope.
      if (!(t.inGlobalHoistScope()
          || (n.isFunction()
              && t.getScopeRoot() == n
              && t.getScope().getParent().getClosestHoistScope().isGlobal()))) {
        return false;
      }

      return NodeUtil.isFunctionDeclaration(n) || (n.isFunction() && n.getParent().isName());
    }

    /** Returns true if this is the r-value of an assignment. */
    private boolean isAssignRValue(Node n, Node parent) {
      return parent != null && parent.isAssign() && parent.getFirstChild() != n;
    }

    /**
     * Returns the name of a prototype property being assigned to this r-value.
     *
     * <p>Returns null if this is not the R-value of a prototype property, or if the R-value is used
     * in multiple expressions (i.e., if there's a prototype property assignment in a more complex
     * expression).
     */
    private String getPrototypePropertyNameFromRValue(Node rValue) {
      Node lValue = NodeUtil.getBestLValue(rValue);
      if (lValue == null
          || !((NodeUtil.mayBeObjectLitKey(lValue) && !lValue.isQuotedString())
              || NodeUtil.isExprAssign(lValue.getGrandparent()))) {
        return null;
      }

      String lValueName = NodeUtil.getBestLValueName(lValue);

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
     * Processes a NAME node to see if it's a global function declaration. If it is, record it and
     * return true. Otherwise, return false.
     */
    private boolean processGlobalFunctionDeclaration(NodeTraversal t, Node nameNode, Var v) {
      Node firstChild = nameNode.getFirstChild();
      Node parent = nameNode.getParent();

      if ( // Check for a named FUNCTION.
      isGlobalFunctionDeclaration(t, parent)
          ||
          // Check for a VAR declaration.
          (firstChild != null && isGlobalFunctionDeclaration(t, firstChild))) {
        String name = nameNode.getString();
        getNameInfoForName(name, VAR)
            .getDeclarations()
            .add(new GlobalFunction(nameNode, v, t.getModule()));

        // If the function name is exported, we should create an edge here
        // so that it's never removed.
        if (compiler.getCodingConvention().isExported(name) || anchorUnusedVars) {
          addGlobalUseOfSymbol(name, t.getModule(), VAR);
        }

        return true;
      }
      return false;
    }

    /**
     * Processes the GETPROP of prototype, which can either be under another GETPROP (in the case of
     * Foo.prototype.bar), or can be under an assignment (in the case of Foo.prototype = ...).
     *
     * @return True if a declaration was added.
     */
    private boolean processPrototypeRef(NodeTraversal t, Node ref) {
      Node root = NodeUtil.getRootOfQualifiedName(ref);

      Node n = ref.getParent();
      switch (n.getToken()) {
          // Foo.prototype.getBar = function() { ... }
        case GETPROP:
          Node dest = n.getSecondChild();
          Node parent = n.getParent();
          Node grandParent = parent.getParent();

          if (dest.isString()
              && NodeUtil.isExprAssign(grandParent)
              && NodeUtil.isNameDeclOrSimpleAssignLhs(n, parent)) {
            String name = dest.getString();
            PrototypeProperty prop =
                new AssignmentPrototypeProperty(grandParent, maybeGetVar(t, root), t.getModule());
            getNameInfoForName(name, PROPERTY).getDeclarations().add(prop);
            return true;
          }
          break;

          // Foo.prototype = { "getBar" : function() { ... } }
        case ASSIGN:
          Node map = n.getSecondChild();
          if (map.isObjectLit()) {
            for (Node key = map.getFirstChild(); key != null; key = key.getNext()) {
              if (!key.isQuotedString() && !key.isComputedProp()) {
                // We won't consider quoted or computed properties for any kind of modification,
                // so key may be STRING_KEY, GETTER_DEF, SETTER_DEF, or MEMBER_FUNCTION_DEF
                String name = key.getString();
                PrototypeProperty prop =
                    new LiteralPrototypeProperty(
                        key.getFirstChild(), n, maybeGetVar(t, root), t.getModule());
                getNameInfoForName(name, PROPERTY).getDeclarations().add(prop);
              }
            }
            return true;
          }
          break;
        default:
          break;
      }
      return false;
    }

    private void processMemberDef(NodeTraversal t, Node n) {
      checkState(n.isMemberFunctionDef() || n.isGetterDef() || n.isSetterDef());
      String name = n.getString();
      // Don't want to add a declaration for constructors and static members
      // so they aren't removed
      if (NodeUtil.isEs6ConstructorMemberFunctionDef(n) || n.isStaticMember()) {
        return;
      }

      Node classNameNode = NodeUtil.getNameNode(n.getGrandparent());
      Var var =
          (classNameNode != null && classNameNode.isName())
              ? t.getScope().getVar(classNameNode.getString())
              : null;
      getNameInfoForName(name, PROPERTY)
          .getDeclarations()
          .add(new ClassMemberFunction(n, var, t.getModule()));
    }

    private Var maybeGetVar(NodeTraversal t, Node maybeName) {
      return maybeName.isName() ? t.getScope().getVar(maybeName.getString()) : null;
    }

    private void addGlobalUseOfSymbol(String name, JSModule module, SymbolType type) {
      symbolGraph.connect(globalNode, module, getNameInfoForName(name, type));
    }
  }

  private class ProcessExternProperties extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isGetProp()) {
        symbolGraph.connect(
            externNode, firstModule, getNameInfoForName(n.getLastChild().getString(), PROPERTY));
      } else if (n.isMemberFunctionDef() || n.isGetterDef() || n.isSetterDef()) {
        // As of 2019-08-29 the only user of this class is CrossChunkMethodMotion, which never
        // moves static methods, but that could change. So, we're intentionally including static
        // methods, static getters, and static setters here, because there are cases where they
        // could act like prototype properties.
        //
        // e.g.
        // // externs.js
        // class Foo {
        //   static foo() {}
        // }
        //
        // // src.js
        // /** @record */
        // class ObjWithFooMethod {
        //   foo() {}
        // }
        // /** @type {!ObjWithFooMethod} */
        // let objWithFooMethod = Foo; // yes, this is valid
        //
        symbolGraph.connect(externNode, firstModule, getNameInfoForName(n.getString(), PROPERTY));
      }
    }
  }

  private class PropagateReferences implements EdgeCallback<NameInfo, JSModule> {
    @Override
    public boolean traverseEdge(NameInfo start, JSModule edge, NameInfo dest) {
      if (start.isReferenced()) {
        JSModule startModule = start.getDeepestCommonModuleRef();
        if (startModule != null && moduleGraph.dependsOn(startModule, edge)) {
          return dest.markReference(startModule);
        } else {
          return dest.markReference(edge);
        }
      }
      return false;
    }
  }

  /** The declaration of an abstract symbol. */
  interface Symbol {
    /** The variable for the root of this symbol. */
    Var getRootVar();

    /** Returns the module where this appears. */
    JSModule getModule();
  }

  private enum SymbolType {
    PROPERTY,
    VAR
  }

  /**
   * A function initialized as a VAR statement or LET AND CONST and global or a function
   * declaration.
   */
  static class GlobalFunction implements Symbol {
    private final Var var;
    private final JSModule module;

    GlobalFunction(Node nameNode, Var var, JSModule module) {
      Node parent = nameNode.getParent();
      checkState(
          (NodeUtil.isNameDeclaration(parent) && var.isGlobal())
              || NodeUtil.isFunctionDeclaration(parent));
      this.var = var;
      this.module = module;
    }

    @Override
    public Var getRootVar() {
      return var;
    }

    @Override
    public JSModule getModule() {
      return module;
    }
  }

  /**
   * Represents property definitions done either directly on a '.prototype' object or as a member
   * method, getter, or setter.
   */
  interface Property extends Symbol {}

  static class ClassMemberFunction implements Property {
    private final Node node;
    private final Var var;
    private final JSModule module;

    ClassMemberFunction(Node node, Var var, JSModule module) {
      checkState(node.getParent().isClassMembers());
      checkState(node.isMemberFunctionDef() || node.isSetterDef() || node.isGetterDef());
      this.node = node;
      this.var = var;
      this.module = module;
    }

    @Override
    public Var getRootVar() {
      return var;
    }

    @Override
    public JSModule getModule() {
      return module;
    }

    /** Returns the function node within the definition. */
    public Node getFunctionNode() {
      return node.getOnlyChild();
    }

    /**
     * Returns the MEMBER_FUNCTION_DEF, GETTER_DEF, or SETTER_DEF node that defines the property.
     */
    public Node getDefinitionNode() {
      return node;
    }
  }

  /**
   * Represents a property that is defined on a '.prototype' object.
   *
   * <p>This includes both of these cases.
   *
   * <pre><code>
   * Foo.prototype = {
   *       a: 1, // LiteralPrototypeProperty
   *       b: 2  // LiteralPrototypeProperty
   *     };
   * Bar.prototype.method = function() {}; // AssignmentPrototypeProperty.
   * </code></pre>
   *
   * <p>Since there are two ways of assigning properties to prototypes, we hide them behind this
   * interface so they can both be removed regardless of type.
   */
  interface PrototypeProperty extends Property {

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
  static class AssignmentPrototypeProperty implements PrototypeProperty {
    private final Node exprNode;
    private final Var rootVar;
    private final JSModule module;

    /** @param node An EXPR node. */
    AssignmentPrototypeProperty(Node node, Var rootVar, JSModule module) {
      this.exprNode = node;
      this.rootVar = rootVar;
      this.module = module;
    }

    @Override
    public Var getRootVar() {
      return rootVar;
    }

    @Override
    public Node getPrototype() {
      return getAssignNode().getFirstFirstChild();
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
  static class LiteralPrototypeProperty implements PrototypeProperty {
    private final Node value;
    private final Node assign;
    private final Var rootVar;
    private final JSModule module;

    LiteralPrototypeProperty(Node value, Node assign, Var rootVar, JSModule module) {
      this.value = value;
      this.assign = assign;
      this.rootVar = rootVar;
      this.module = module;
    }

    @Override
    public Var getRootVar() {
      return rootVar;
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
   * The context of the current name. This includes the NameInfo and the scope if it is a scope
   * defining name (function).
   */
  private static class NameContext {
    final NameInfo name;

    // If this is a function context, then scope will be the scope of the
    // corresponding function. Otherwise, it will be null.
    final Scope scope;

    NameContext(NameInfo name, Scope scope) {
      this.name = name;
      this.scope = scope;
    }
  }

  /** Information on all properties or global variables of a given name. */
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
     *
     * @param name The name of the property that this represents. May be null to signify dummy nodes
     *     in the property graph.
     */
    NameInfo(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }

    /** Determines whether we've marked a reference to this property name. */
    boolean isReferenced() {
      return referenced;
    }

    /** Determines whether it reads a closure variable. */
    boolean readsClosureVariables() {
      return readClosureVariables;
    }

    /**
     * Mark a reference in a given module to this property name, and record the deepest common
     * module reference.
     *
     * @param module The module where it was referenced.
     * @return Whether the name info has changed.
     */
    boolean markReference(JSModule module) {
      boolean hasChanged = false;
      if (!referenced) {
        referenced = true;
        hasChanged = true;
      }

      JSModule originalDeepestCommon = deepestCommonModuleRef;

      if (deepestCommonModuleRef == null) {
        deepestCommonModuleRef = module;
      } else {
        deepestCommonModuleRef =
            moduleGraph.getDeepestCommonDependencyInclusive(deepestCommonModuleRef, module);
      }

      if (originalDeepestCommon != deepestCommonModuleRef) {
        hasChanged = true;
      }

      return hasChanged;
    }

    /** Returns the deepest common module of all the references to this property. */
    JSModule getDeepestCommonModuleRef() {
      return deepestCommonModuleRef;
    }

    /**
     * Returns a mutable collection of all the prototype property declarations of this property
     * name.
     */
    Deque<Symbol> getDeclarations() {
      return declarations;
    }
  }
}
