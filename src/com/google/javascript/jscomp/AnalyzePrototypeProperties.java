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
import com.google.javascript.jscomp.CodingConvention.ObjectLiteralCast;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

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
  private final boolean doNotPinExternsPropertiesOnPrototypes;
  private final boolean trackThisPropertiesDefinitions;
  private final boolean anchorUnusedVars;
  private final boolean anchorObjectLiteralProperties;
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
  // global variable that it's assigned to.
  private final Map<String, NameInfo> varNameInfo = Maps.newHashMap();

  // A list of extern property names that have not been added to the
  // symbolGraph.
  private final Set<String> deferredExternPropNames = Sets.newHashSet();

  /**
   * Creates a new pass for analyzing prototype properties.
   * @param compiler The compiler.
   * @param moduleGraph The graph for resolving module dependencies. May be
   *     null if we don't care about module dependencies.
   * @param doNotPinExternsPropertiesOnPrototypes If true, do not consider
   *     externs property definitions when looking for uses of properties
   *     defined on prototypes.
   * @param anchorUnusedVars If true, mark all vars as referenced,
   *     even if they are never used.
   * @param trackThisPropertiesDefinitions If true, add assignments to
   *     properties defined on "this" as definitions in the symbolGraph.
   * @param pinPropertiesDefinedOnObjectLiterals If true, mark all properties
   *     on object literals (that are not otherwise prototype
   *     property definitions) as referenced.
   */
  AnalyzePrototypeProperties(AbstractCompiler compiler,
      JSModuleGraph moduleGraph,
      boolean doNotPinExternsPropertiesOnPrototypes,
      boolean anchorUnusedVars,
      boolean trackThisPropertiesDefinitions,
      boolean pinPropertiesDefinedOnObjectLiterals) {
    this.compiler = compiler;
    this.moduleGraph = moduleGraph;
    this.doNotPinExternsPropertiesOnPrototypes =
         doNotPinExternsPropertiesOnPrototypes;
    this.trackThisPropertiesDefinitions = trackThisPropertiesDefinitions;
    this.anchorUnusedVars = anchorUnusedVars;
    this.anchorObjectLiteralProperties = pinPropertiesDefinedOnObjectLiterals;

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
    NodeTraversal.traverse(compiler, externRoot,
        new ProcessExternProperties());

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
    // There are two types of context information on this stack:
    // 1) Every scope has a NameContext corresponding to its scope.
    //    Variables are given VAR contexts.
    //    Prototype properties are given PROPERTY contexts.
    //    The global scope is given the special [global] context.
    //    And function expressions that we aren't able to give a reasonable
    //    name are given a special [anonymous] context.
    // 2) Every assignment of a prototype property of a non-function is
    //    given a name context. These contexts do not have scopes.
    private ArrayDeque<NameContext> symbolStack = new ArrayDeque<NameContext>();
    // When a side-effect is encountered, any dependent value references must
    // be associated with the current scope.
    private ArrayDeque<NameContext> scopeStack = new ArrayDeque<NameContext>();

    @Override
    public void enterScope(NodeTraversal t) {
      Node n = t.getCurrentNode();
      NameContext nameContext;
      if (n.isFunction()) {
        if (isGlobalFunctionDeclaration(t, n)) {
          Node parent = n.getParent();
          String name = parent.isName() ?
              parent.getString() /* VAR */ :
              n.getFirstChild().getString() /* named function */;
          nameContext =
              new NameContext(
                  symbolStack.peek(),
                  getNameInfoForName(name, VAR), t.getScope(),
                  /* chained */ false);
        } else {
          // We use the same anonymous node for all function expressions.
          // They're just there as a placeholder for scope information, and
          // do not matter in the edge propagation.
          nameContext = new NameContext(
              symbolStack.peek(), anonymousNode, t.getScope(),
              /* chained */ true);
        }
      } else {
        Preconditions.checkState(t.inGlobalScope());
        nameContext = new NameContext(
            null, globalNode, t.getScope(), /* chained */ false);
      }

      symbolStack.push(nameContext);
      scopeStack.push(nameContext);
    }

    @Override
    public void exitScope(NodeTraversal t) {
      symbolStack.pop();
      scopeStack.pop();
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      NameContext context = maybeGetContextForNode(n, true);
      if (context != null) {
        symbolStack.push(context);
      }
      return true;
    }

    private NameContext maybeGetContextForNode(
        Node n, boolean checkSideEffects) {
      NameContext context = null;
      // NameInfo dependencies contained in untracked side-effect nodes are
      // assigned to the current scope NameContext. At the point in the
      // traversal that we detect the side-effect
      // conditional NameInfo dependences have already been assigned.  For
      // example:  "x ? f() : g()".  The covering HOOK node does not have
      // side effects, neither does the condition "x".  Assuming that "f()"
      // and "g()" have side-effects, the HOOK can not be removed, so "x" must
      // be rescued.
      //
      if (n.isHook() || n.isOr() || n.isAnd()) {
        if (checkSideEffects && NodeUtil.mayHaveSideEffects(n, compiler)) {
          // Any property (or global name) references are add as a dependency
          // on the current scope.
          context = scopeStack.peek();
        } else {
          // There aren't any side-effects so continue with the current context.
          context = symbolStack.peek();
        }
      } else if (NodeUtil.nodeTypeMayHaveSideEffects(n, compiler)) {
        // Assignments and other contexts
        String propName = null;
        if (isUnpinnedPropertyUseParent(n)) {
          propName = getPrototypePropertyName(n.getFirstChild());
        }
        context = getContextForPropName(propName,
            NodeUtil.isExpressionResultUsed(n));
      } else if (NodeUtil.isObjectLitKey(n, n.getParent())) {
        // Handle object literal definitions potential property assignment
        String propName = getPrototypePropertyName(n);
        context = getContextForPropName(propName, false);
      }
      return context;
    }

    private boolean isContextIntroducingNode(Node n) {
      return maybeGetContextForNode(n, false) != null;
    }

    private NameContext getContextForPropName(
        String propName, boolean resultUsed) {
      NameContext context;
      if (propName != null) {
        context = new NameContext(
            symbolStack.peek(),
            getNameInfoForName(propName, PROPERTY), null,
            resultUsed);
      } else {
        // side-effects should be associated with the enclosing scope,
        // regardless of any enclosing prop assignment
        context = scopeStack.peek();
      }
      return context;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP:
          // Check for interesting property definitions and references
          visitGetProp(t, n);
          break;
        case Token.OBJECTLIT:
          // Check for interesting property definitions
          visitObjectLit(t, n);
          break;
        case Token.NAME:
          // Check for interesting variable definitions and references
          visitName(t, n);
          break;
        case Token.CALL:
          // Check for special case uses of properties
          visitCall(t, n);
          break;
      }

      if (isContextIntroducingNode(n)) {
        symbolStack.pop();
      }
    }

    private void visitGetProp(NodeTraversal t, Node n) {
      if (n.getFirstChild().isThis()) {
        if (processThisRef(t, n.getFirstChild())) {
          return;
        }
      }

      String propName = n.getFirstChild().getNext().getString();
      boolean isPinningUse = isPinningPropertyUse(n);

      if (n.isQualifiedName()) {
        if (propName.equals("prototype")) {
          if (processPrototypeRef(t, n)) {
            return;
          }
        } else if (compiler.getCodingConvention().isExported(propName)) {
          addGlobalUseOfSymbol(propName, t.getModule(), PROPERTY);
          return;
        } else {
          // Do not mark prototype prop assigns as a 'use' in the global
          // scope.
          if (!isPinningUse) {
            String lValueName = getPrototypePropertyName(n);
            if (lValueName != null) {
              return;
            }
          }
        }
      }

      if (isPinningUse) {
        addSymbolUse(propName, t.getModule(), PROPERTY);
      }
    }

    private void visitObjectLit(NodeTraversal t, Node n) {
      if (anchorObjectLiteralProperties) {
        // Make sure that we're not handling object literals being
        // assigned to a prototype, as in:
        // Foo.prototype = {bar: 3, baz: 5};
        String lValueName = NodeUtil.getBestLValueName(
            NodeUtil.getBestLValue(n));
        if (lValueName != null && lValueName.endsWith(".prototype")) {
          return;
        }

        pinObjectLiteralProperties(n, t.getModule());
      }
    }

    private void visitName(NodeTraversal t, Node n) {
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
          handleScopeReference(var.getScope());
        }
      }
    }

    private void visitCall(NodeTraversal t, Node n) {
      // Look for properties referenced through "JSCompiler_propertyRename".
      Node target = n.getFirstChild();
      if (n.hasMoreThanOneChild()
          && target.isName()
          && target.getString().equals(NodeUtil.JSC_PROPERTY_NAME_FN)) {
        Node propNode = target.getNext();
        if (propNode.isString()) {
          String propName = propNode.getString();
          addSymbolUse(propName, t.getModule(), PROPERTY);
        }
      } else {
        // ... and for calls to "goog.reflect.object" and the ilk.
        ObjectLiteralCast cast = compiler.getCodingConvention()
            .getObjectLiteralCast(t, n);
        if (cast != null) {
          pinObjectLiteralProperties(cast.objectNode, t.getModule());
        }
      }
    }

    /**
     * Handle a reference to a scope from an inner scope.
     * @param scope The referenced scope
     */
    private void handleScopeReference(Scope scope) {
      NameContext context = symbolStack.peek();
      while (context != null) {
        context.name.readClosureVariables = true;
        if (context.parent != null && context.parent.scope == scope) {
          break;
        }
        context = context.parent;
      }

      while (context != null && context.resultUsed) {
        context.name.readClosureVariables = true;
        // Stop when we would cross into another scope.  Function declarations
        // don't chain so there is no need to explicitly check for them here.
        if (context.parent == null || context.parent.name == anonymousNode) {
          break;
        }
        context = context.parent;
      }
    }

    /**
     * Mark properties in the literal as referenced in the provided module.
     * @param n The object literal
     * @param module The module
     */
    private void pinObjectLiteralProperties(Node n, JSModule module) {
      Preconditions.checkArgument(n.isObjectLit());

      // var x = {a: 1, b: 2}
      // should count as a use of property a and b.
      for (Node key = n.getFirstChild(); key != null; key = key.getNext()) {
        // May be STRING, GET, or SET, but NUMBER isn't interesting.
        if (!key.isQuotedString()) {
          addSymbolUse(key.getString(), module, PROPERTY);
        }
      }
    }

    /**
     * @return Whether the property is used in a way that prevents its removal.
     */
    private boolean isPinningPropertyUse(Node n) {
      // Rather than looking for cases that are uses, we assume all references
      // are pinning uses unless they are:
      //  - a simple assignment (x.a = 1)
      //  - a compound assignment or increment (x++, x += 1) whose result is
      //    otherwise unused

      Node parent = n.getParent();
      if (n == parent.getFirstChild()) {
        if (parent.isAssign()) {
          // A simple assignment doesn't pin the property.
          return false;
        } else if (NodeUtil.isAssignmentOp(parent)
              || parent.isInc() || parent.isDec()) {
          // In general, compound assignments are both reads and writes, but
          // if the property is never otherwise read we can consider it simply
          // a write.
          // However if the assign expression is used as part of a larger
          // expression, we much consider it a read. For example:
          //    x = (y.a += 1);
          return NodeUtil.isExpressionResultUsed(parent);
        }
      }
      return true;
    }

    /**
     * @return Whether any children are unpinned property uses.
     */
    private boolean isUnpinnedPropertyUseParent(Node n) {
      if (n.hasChildren()) {
        // Only the first child can be an unpinned use.
        return !isPinningPropertyUse(n.getFirstChild());
      }
      return false;
    }

    private void addSymbolUse(String name, JSModule module, SymbolType type) {
      NameInfo info = getNameInfoForName(name, type);
      NameContext context = symbolStack.peek();

      context.connect(symbolGraph, module, info);
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

      // Looking for: "function f() {}" or "var f = function()"
      return NodeUtil.isFunctionDeclaration(n) ||
          n.isFunction() && n.getParent().isName();
    }

    /**
     * Returns the name of a prototype property being assigned to this r-value.
     *
     * Returns null if this is not the R-value of a prototype property, or if
     * the R-value is used in multiple expressions (i.e., if there's
     * a prototype property assignment in a more complex expression).
     */
    private String getPrototypePropertyName(Node lValue) {
      String lValueName = NodeUtil.getBestLValueName(lValue);
      if (lValueName == null) {
        return null;
      }

      int lastDot = lValueName.lastIndexOf('.');
      if (lastDot == -1) {
        return null;
      }

      String firstPart = lValueName.substring(0, lastDot);
      if (!firstPart.endsWith(".prototype")
          && !(trackThisPropertiesDefinitions && firstPart.equals("this"))) {
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
          // NOTE: for properties defined on the prototype we don't ever
          // need to check the deferred definitions.  They have either been
          // added during the pass over the externs or we are ignoring them
          // because canModifyExternsPrototypes is set.
          return processGetProp(t, n, root,
              /* checkDeferredExterns */ false);

        // Foo.prototype = { "getBar" : function() { ... } }
        case Token.ASSIGN:
          Node map = n.getFirstChild().getNext();
          if (map.isObjectLit()) {
            for (Node key = map.getFirstChild();
                 key != null; key = key.getNext()) {
              // May be STRING, GETTER_DEF, or SETTER_DEF,
              String name = key.getString();
              Property prop = new LiteralProperty(
                  key, key.getFirstChild(), map, n,
                  maybeGetVar(t, root),
                  t.getModule());
              getNameInfoForName(name, PROPERTY).getDeclarations().add(prop);
            }
            return true;
          }
          break;
      }
      return false;
    }

    /**
     * Processes a "this" reference, which may be a
     * GETPROP (in the case of this.bar).
     * under an assignment (in the case of Foo.prototype = ...).
     * @return True if a declaration was added.
     */
    private boolean processThisRef(NodeTraversal t, Node ref) {
      if (trackThisPropertiesDefinitions) {
        Node n = ref.getParent();
        // this.getBar = function() { ... }
        if (n.isGetProp()) {
          // NOTE: for properties defined on this, we don't ever
          // need to check the deferred definitions.  They have either been
          // added during the pass over the externs or we are ignoring them
          // because canModifyExternsPrototypes is set.
          return processGetProp(t, n, null,
              /* checkDeferredExterns */ true);
        }
      }
      return false;
    }

    /**
     * Given a GETPROP, determine if the reference is property write operation.
     * @return true if a GETPROP use is a candidate for removal.
     */
    private boolean processGetProp(NodeTraversal t, Node n, Node rootName,
        boolean checkDeferredExterns) {
      Node dest = n.getFirstChild().getNext();
      Node parent = n.getParent();
      Preconditions.checkState(dest.isString());
      if (!isPinningPropertyUse(n)) {
        String name = dest.getString();
        if (!checkDeferredExterns || !deferredExternPropNames.contains(name)) {
          Property prop = new AssignmentProperty(
              parent,
              maybeGetVar(t, rootName),
              t.getModule());
          getNameInfoForName(name, PROPERTY).getDeclarations().add(prop);
        }
        return true;
      }
      return false;
    }

    private Var maybeGetVar(NodeTraversal t, @Nullable Node maybeName) {
      return (maybeName != null && maybeName.isName())
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
        if (doNotPinExternsPropertiesOnPrototypes) {
          deferredExternPropNames.add(n.getLastChild().getString());
        } else {
          connectExternProp(n.getLastChild().getString());
        }
      }
    }
  }

  private void connectExternProp(String propName) {
    symbolGraph.connect(externNode, firstModule,
        getNameInfoForName(propName, PROPERTY));
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
    void remove(CodeChangeHandler reporter);

    /**
     * The variable for the root of this symbol.
     */
    Var getRootVar();

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
    public void remove(CodeChangeHandler reporter) {
      Node parent = nameNode.getParent();
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

    public Node getFunctionNode() {
      Node parent = nameNode.getParent();

      if (parent.isFunction()) {
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
    private final Node assignNode;
    private final Var rootVar;
    private final JSModule module;

    /**
     * @param node An EXPR node.
     */
    AssignmentProperty(Node node, Var rootVar, JSModule module) {
      this.assignNode = node;
      this.rootVar = rootVar;
      this.module = module;
    }

    @Override
    public Var getRootVar() {
      return rootVar;
    }

    @Override
    public void remove(CodeChangeHandler reporter) {
      // TODO(johnlenz): use trySimplifyUnusedResult
      if (NodeUtil.isAssignmentOp(assignNode)) {
        Node value = getAssignNode().getLastChild();
        Node exprParent = assignNode.getParent();
        if (exprParent.isExprResult()
            && !NodeUtil.mayHaveSideEffects(value)) {
          NodeUtil.removeChild(exprParent.getParent(), exprParent);
        } else {
          if (!NodeUtil.isExpressionResultUsed(assignNode)) {
            Node result = NodeUtil.trySimplifyUnusedResult(value, reporter);
            if (result == null) {
              // FOR init or increment expressions, the first op of COMMA, etc
              // must have a node to be valid, so simple use a literal "0" if
              // nothing remains after simplification.
              value = IR.number(0);
            } else {
              value = result.detachFromParent();
            }
          } else {
            value.detachFromParent();
          }
          exprParent.replaceChild(getAssignNode(), value);
        }
      } else if (assignNode.isInc() || assignNode.isDec()) {
        assignNode.getParent().replaceChild(assignNode, IR.number(0));
      } else {
        throw new IllegalStateException("unexpected: "+ assignNode);
      }
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
      return assignNode;
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
    public void remove(CodeChangeHandler reporter) {
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
    final NameContext parent;

    // If this is a function context, then scope will be the scope of the
    // corresponding function. Otherwise, it will be null.
    final Scope scope;

    // Whether any dependencies should also be added to the parent context.
    // This is the case with assignment expressions such as:
    //   a = b = foo;
    // or
    //   a = function() {}
    final boolean resultUsed;

    NameContext(
        NameContext parent, NameInfo name, Scope scope, boolean resultUsed) {
      this.parent = parent;
      this.name = name;
      this.scope = scope;
      this.resultUsed = resultUsed;
    }

    void connect(
        LinkedDirectedGraph<NameInfo, JSModule> symbolGraph,
        JSModule module, NameInfo info) {
      NameInfo def = this.name;
      // don't add self connections
      if (def != anonymousNode && !info.equals(def)) {
        symbolGraph.connect(this.name, module, info);
      }
      if (this.resultUsed) {
        this.parent.connect(symbolGraph, module, info);
      }
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
