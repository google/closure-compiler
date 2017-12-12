/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowStatementCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds all references to global symbols and rewrites them to be property
 * accesses to a special object with the same name as the global symbol.
 *
 * Given the name of the global object is NS
 * <pre> var a = 1; function b() { return a }</pre>
 * becomes
 * <pre> NS.a = 1; NS.b = function b() { return NS.a }</pre>
 *
 * This allows splitting code into modules that depend on each other's
 * global symbols, without using polluting JavaScript's global scope with those
 * symbols. You typically define just a single global symbol, wrap each module
 * in a function wrapper, and pass the global symbol around, eg,
 * <pre> var uniqueNs = uniqueNs || {}; </pre>
 * <pre> (function (NS) { ...your module code here... })(uniqueNs); </pre>
 *
 *
 * <p>This compile step requires moveFunctionDeclarations to be turned on
 * to guarantee semantics.
 *
 * <p>For lots of examples, see the unit test.
 *
 *
 */
final class RescopeGlobalSymbols implements CompilerPass {

  // Appended to variables names that conflict with globalSymbolNamespace.
  private static final String DISAMBIGUATION_SUFFIX = "$";
  private static final String WINDOW = "window";
  private static final ImmutableSet<String> SPECIAL_EXTERNS =
      ImmutableSet.of(
          WINDOW,
          "eval",
          "arguments",
          "undefined",
          // The javascript built-in objects (listed in Ecma 262 section 4.2)
          "Object",
          "Function",
          "Array",
          "String",
          "Boolean",
          "Number",
          "Math",
          "Date",
          "RegExp",
          "JSON",
          "Error",
          "EvalError",
          "ReferenceError",
          "SyntaxError",
          "TypeError",
          "URIError");

  private final AbstractCompiler compiler;
  private final String globalSymbolNamespace;
  private final boolean addExtern;
  private final boolean assumeCrossModuleNames;
  private final Set<String> crossModuleNames = new HashSet<>();
  /** Global identifiers that may be a non-arrow function referencing "this" */
  private final Set<String> maybeReferencesThis = new HashSet<>();
  private Set<String> externNames;

  /**
   * Constructor for the RescopeGlobalSymbols compiler pass.
   *
   * @param compiler The JSCompiler, for reporting code changes.
   * @param globalSymbolNamespace Name of namespace into which all global
   *     symbols are transferred.
   * @param assumeCrossModuleNames If true, all global symbols will be assumed
   *     cross module boundaries and thus require renaming.
   */
  RescopeGlobalSymbols(
      AbstractCompiler compiler,
      String globalSymbolNamespace,
      boolean assumeCrossModuleNames) {
    this(compiler, globalSymbolNamespace, true, assumeCrossModuleNames);
  }

  /**
   * Constructor for the RescopeGlobalSymbols compiler pass for use in testing.
   *
   * @param compiler The JSCompiler, for reporting code changes.
   * @param globalSymbolNamespace Name of namespace into which all global
   *     symbols are transferred.
   * @param addExtern If true, the compiler will consider the
   *    globalSymbolNamespace an extern name.
   * @param assumeCrossModuleNames If true, all global symbols will be assumed
   *     cross module boundaries and thus require renaming.
   * VisibleForTesting
   */
  RescopeGlobalSymbols(
      AbstractCompiler compiler,
      String globalSymbolNamespace,
      boolean addExtern,
      boolean assumeCrossModuleNames) {
    this.compiler = compiler;
    this.globalSymbolNamespace = globalSymbolNamespace;
    this.addExtern = addExtern;
    this.assumeCrossModuleNames = assumeCrossModuleNames;
  }

  private boolean isCrossModuleName(String name) {
    return assumeCrossModuleNames || crossModuleNames.contains(name)
        || compiler.getCodingConvention().isExported(name, false);
  }

  private boolean isExternVar(String varname, NodeTraversal t) {
    if (varname.isEmpty()) {
      return false;
    }
    Var v = t.getScope().getVar(varname);
    return v == null || v.isExtern() || (v.scope.isGlobal() && this.externNames.contains(varname));
  }

  private void addExternForGlobalSymbolNamespace() {
    Node varNode = IR.var(IR.name(globalSymbolNamespace));
    CompilerInput input = compiler.getSynthesizedExternsInput();
    input.getAstRoot(compiler).addChildToBack(varNode);
    compiler.reportChangeToEnclosingScope(varNode);
  }

  @Override
  public void process(Node externs, Node root) {
    // Collect variables in externs; they can be shadowed by the same names in global scope.
    this.externNames = NodeUtil.collectExternVariableNames(this.compiler, externs);

    // Make the name of the globalSymbolNamespace an extern.
    if (addExtern) {
      addExternForGlobalSymbolNamespace();
    }

    // Rewrite all references to global symbols to properties of a single symbol:

    // Turn global named function statements into var assignments.
    NodeTraversal.traverseEs6(
        compiler, root, new RewriteGlobalClassFunctionDeclarationsToVarAssignmentsCallback());

    // Find global names that are used in more than one module. Those that
    // are have to be rewritten.
    List<Callback> nonMutatingPasses = new ArrayList<>();
    nonMutatingPasses.add(new FindCrossModuleNamesCallback());

    // And find names that may refer to functions that reference this.
    nonMutatingPasses.add(new FindNamesReferencingThis());
    CombinedCompilerPass.traverse(compiler, root, nonMutatingPasses);

    // Rewrite all references to be property accesses of the single symbol.
    RewriteScopeCallback rewriteScope = new RewriteScopeCallback();
    NodeTraversal.traverseEs6(compiler, root, rewriteScope);

    // Remove the var from statements in global scope if the declared names have been rewritten
    // in the previous pass.
    NodeTraversal.traverseEs6(compiler, root, new RemoveGlobalVarCallback());
    rewriteScope.declareModuleGlobals();
  }

  /**
   * Rewrites global function and class declarations to var statements + assignment. Ignores
   * non-global function and class declarations.
   *
   * <pre>function test(){}</pre>
   *
   * becomes
   *
   * <pre>var test = function (){}</pre>
   *
   * <pre>class A {}</pre>
   *
   * becomes
   *
   * <pre>var A = class {}</pre>
   *
   * After this traversal, the special case of global class and function statements can be ignored.
   *
   * <p>This is helpful when rewriting simple names to property accesses on the global symbol, since
   * {@code class A {}} cannot be rewritten directly to {@code class NS.A {}}
   */
  private class RewriteGlobalClassFunctionDeclarationsToVarAssignmentsCallback
      extends AbstractShallowStatementCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isFunctionDeclaration(n)
          // Since class declarations are block-scoped, only handle them if in the global scope.
          || (NodeUtil.isClassDeclaration(n) && t.inGlobalScope())) {
        Node nameNode = NodeUtil.getNameNode(n);
        String name = nameNode.getString();
        // Remove the class or function name. Anonymous classes have an EMPTY node, while anonymous
        // functions have a NAME node with an empty string.
        if (n.isClass()) {
          nameNode.replaceWith(IR.empty().srcref(nameNode));
        } else {
          nameNode.setString("");
          compiler.reportChangeToEnclosingScope(nameNode);
        }
        Node prev = n.getPrevious();
        n.detach();
        Node var = NodeUtil.newVarNode(name, n);
        if (prev == null) {
          parent.addChildToFront(var);
        } else {
          parent.addChildAfter(var, prev);
        }
        compiler.reportChangeToEnclosingScope(parent);
      }
    }
  }

  /**
   * Find all global names that are used in more than one module. The following
   * compiler transformations can ignore the globals that are not.
   */
  private class FindCrossModuleNamesCallback extends
      AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String name = n.getString();
        if ("".equals(name) || crossModuleNames.contains(name)) {
          return;
        }
        Scope s = t.getScope();
        Var v = s.getVar(name);
        if (v == null || !v.isGlobal()) {
          return;
        }
        CompilerInput input = v.getInput();
        if (input == null) {
          // We know nothing. Assume name is used across modules.
          crossModuleNames.add(name);
          return;
        }
        // Compare the module where the variable is declared to the current
        // module. If they are different, the variable is used across modules.
        JSModule module = input.getModule();
        if (module != t.getModule()) {
          crossModuleNames.add(name);
        }
      }
    }
  }

  /**
   * Builds the maybeReferencesThis set of names that may reference a function
   * that references this. If the function a name references does not reference
   * this it can be called as a method call where the this value is not the
   * same as in a normal function call.
   */
  private class FindNamesReferencingThis extends
      AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String name = n.getString();
        if (name.isEmpty()) {
          return;
        }
        Node value = null;
        if (parent.isAssign() && n == parent.getFirstChild()) {
          value = parent.getLastChild();
        } else if (NodeUtil.isNameDeclaration(parent)) {
          value = n.getFirstChild();
        } else if (parent.isFunction()) {
          value = parent;
        }
        if (value == null && !NodeUtil.isLhsByDestructuring(n)) {
          // If n is assigned in a destructuring pattern, don't bother finding its value and just
          // assume it may reference this.
          return;
        }
        // We already added this symbol. Done after checks above because those
        // are comparatively cheap.
        if (maybeReferencesThis.contains(name)) {
          return;
        }
        Scope s = t.getScope();
        Var v = s.getVar(name);
        if (v == null || !v.isGlobal()) {
          return;
        }
        // If anything but a function is assigned we assume that possibly
        // a function referencing this is being assigned. Otherwise we
        // check whether the function assigned is a) an arrow function, which has a
        // lexically-scoped this, or b) a non-arrow function that does not reference this.
        if (value == null
            || !value.isFunction()
            || (!value.isArrowFunction() && NodeUtil.referencesThis(value))) {
          maybeReferencesThis.add(name);
        }
      }
    }
  }

  /**
   * Visits each NAME token and checks whether it refers to a global variable. If yes, rewrites the
   * name to be a property access on the "globalSymbolNamespace". If the NAME is an extern variable,
   * it becomes a property access on window.
   *
   * <pre>var a = 1, b = 2, c = 3;</pre>
   *
   * becomes
   *
   * <pre>var NS.a = 1, NS.b = 2, NS.c = 4</pre>
   *
   * (The var token is removed in a later traversal.)
   *
   * <pre>a + b</pre>
   *
   * becomes
   *
   * <pre>NS.a + NS.b</pre>
   *
   * <pre>a()</pre>
   *
   * becomes
   *
   * <pre>(0,NS.a)()</pre>
   *
   * Notice the special syntax here to preserve the *this* semantics in the function call.
   *
   * <pre>var {a: b} = {}</pre>
   *
   * becomes
   *
   * <pre>var {a: NS.b} = {}</pre>
   *
   * (This is invalid syntax, but the VAR token is removed later).
   */
  private class RewriteScopeCallback extends AbstractPostOrderCallback {
    List<ModuleGlobal> preDeclarations = new ArrayList<>();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName() && !NodeUtil.isLhsByDestructuring(n)) {
        // NOTE: we visit names that are lhs by destructuring in {@code visitDestructuringPattern}.
        visitName(t, n, parent);
      } else if (n.isDestructuringPattern()) {
        visitDestructuringPattern(t, n, parent);
      }
    }

    /**
     * Rewrites all cross-module names inside destructuring patterns, and converts destructuring
     * declarations containing any cross-module names to assignments.
     */
    private void visitDestructuringPattern(NodeTraversal t, Node n, Node parent) {
      if (!(parent.isAssign() || parent.isParamList() || parent.isDestructuringLhs())) {
        // Don't handle patterns that are nested within another pattern.
        return;
      }
      List<Node> lhsNodes = NodeUtil.findLhsNodesInNode(n.getParent());
      boolean hasCrossModuleName = false;
      // Go through all lhs name nodes in the destructuring pattern, and call {@code visitName}
      // on them to rescope any cross-module globals.
      // e.g. after the loop finishes, [a, b] = [1, 2]; becomes [NS.a, NS.b] = [1, 2];
      for (Node lhs : lhsNodes) {
        if (!lhs.isName()) {
          // The LHS could also be a GETPROP or GETELEM, which get handled when the traversal hits
          // their NAME nodes.
          continue;
        }
        visitName(t, lhs, lhs.getParent());
        hasCrossModuleName = hasCrossModuleName || isCrossModuleName(lhs.getString());
      }

      // If the parent is not a destructuring lhs, this is an assignment, not a declaration, and
      // there's nothing left to do.
      if (!parent.isDestructuringLhs()) {
        return;
      }
      Node nameDeclaration = parent.getParent();
      // If this declaration is global and has any cross-module names, rewrite it to not be a
      // declaration. RemoveGlobalVarCallback will remove the actual var/let/const node.
      if (hasCrossModuleName
          && (t.inGlobalScope() || (nameDeclaration.isVar() && t.inGlobalHoistScope()))) {
        Node value = n.getNext();
        if (value != null) {
          // If the destructuring pattern has an rhs, convert this to be an ASSIGN.
          parent.removeChild(n);
          parent.removeChild(value);
          Node assign = IR.assign(n, value).srcref(n);
          nameDeclaration.replaceChild(parent, assign);
        } else {
          // In a for-in or for-of loop initializer, the rhs value is null.
          // Move the destructuring pattern to be a direct child of the name declaration.
          parent.removeChild(n);
          nameDeclaration.replaceChild(parent, n);
        }
        compiler.reportChangeToEnclosingScope(nameDeclaration);

        // If there are any declared names that are not cross module, they need to be declared
        // before the destructuring pattern, since we converted their declaration to an assignment.
        CompilerInput input = t.getInput();
        for (Node lhs : lhsNodes) {
          if (!lhs.isName()) {
            continue;
          }
          String name = lhs.getString();
          if (!isCrossModuleName(name)) {
            preDeclarations.add(
                new ModuleGlobal(input.getAstRoot(compiler), IR.name(name).srcref(lhs)));
          }
        }
      }
    }

    private void visitName(NodeTraversal t, Node n, Node parent) {
      String name = n.getString();
      // Ignore anonymous functions
      if (parent.isFunction() && name.isEmpty()) {
        return;
      }
      if (isExternVar(name, t)) {
        visitExtern(n, parent);
        return;
      }
      // When the globalSymbolNamespace is used as a local variable name
      // add suffix to avoid shadowing the namespace. Also add a suffix
      // if a name starts with the name of the globalSymbolNamespace and
      // the suffix.
      Var var = t.getScope().getVar(name);
      if (!var.isGlobal()
          && (name.equals(globalSymbolNamespace)
              || name.startsWith(globalSymbolNamespace + DISAMBIGUATION_SUFFIX))) {
        n.setString(name + DISAMBIGUATION_SUFFIX);
        compiler.reportChangeToEnclosingScope(n);
      }
      // We only care about global vars.
      if (!var.isGlobal()) {
        return;
      }
      Node nameNode = var.getNameNode();
      // The exception variable (e in try{}catch(e){}) should not be rewritten.
      if (nameNode != null && nameNode.getParent() != null && nameNode.getParent().isCatch()) {
        return;
      }
      replaceSymbol(t, n, name, t.getInput());
    }

    private void replaceSymbol(NodeTraversal t, Node node, String name, CompilerInput input) {
      Node parent = node.getParent();
      boolean isCrossModule = isCrossModuleName(name);
      if (!isCrossModule) {
        // When a non cross module name appears outside a var declaration we
        // never have to do anything.
        // If it's inside a destructuring pattern declaration, then it's handled elsewhere.
        if (!NodeUtil.isNameDeclaration(parent)) {
          return;
        }
        boolean hasInterestingChildren = false;
        for (Node c : parent.children()) {
          // VAR child is no longer a name means it was transformed already.
          if (!c.isName() || isCrossModuleName(c.getString()) || isExternVar(c.getString(), t)) {
            hasInterestingChildren = true;
            break;
          }
        }
        if (!hasInterestingChildren) {
          return;
        }
      }
      Node replacement = isCrossModule
          ? IR.getprop(
              IR.name(globalSymbolNamespace).srcref(node),
              IR.string(name).srcref(node))
          : IR.name(name).srcref(node);
      replacement.srcref(node);
      if (node.hasChildren()) {
        // var declaration list: var a = 1, b = 2;
        Node assign = IR.assign(
            replacement,
            node.removeFirstChild());
        parent.replaceChild(node, assign);
        compiler.reportChangeToEnclosingScope(assign);
      } else if (isCrossModule) {
        parent.replaceChild(node, replacement);
        compiler.reportChangeToEnclosingScope(replacement);
        if (parent.isCall() && !maybeReferencesThis.contains(name)) {
          // Do not write calls like this: (0, _a)() but rather as _.a(). The
          // this inside the function will be wrong, but it doesn't matter
          // because the this is never read.
          parent.putBooleanProp(Node.FREE_CALL, false);
        }
      }
      // If we changed a non cross module name that was in a var declaration
      // we need to preserve that var declaration. Because it is global
      // anyway, we just put it at the beginning of the current input.
      // Example:
      // var crossModule = i++, notCrossModule = i++
      // becomes
      // var notCrossModule;_.crossModule = i++, notCrossModule = i++
      if (!isCrossModule && NodeUtil.isNameDeclaration(parent)) {
        preDeclarations.add(new ModuleGlobal(
            input.getAstRoot(compiler),
            IR.name(name).srcref(node)));
      }
      compiler.reportChangeToEnclosingScope(parent);
    }

    /**
     * Rewrites extern names to be explicit children of window instead of only implicitly
     * referencing it. This enables injecting window into a scope and make all global symbols
     * depend on the injected object.
     */
    private void visitExtern(Node nameNode, Node parent) {
      String name = nameNode.getString();
      if (globalSymbolNamespace.equals(name) || SPECIAL_EXTERNS.contains(name)) {
        return;
      }
      Node windowPropAccess = IR.getprop(IR.name(WINDOW), IR.string(name));
      if (NodeUtil.isNameDeclaration(parent) && nameNode.hasOneChild()) {
        Node assign = IR.assign(windowPropAccess, nameNode.removeFirstChild());
        assign.setJSDocInfo(parent.getJSDocInfo());
        parent.replaceChild(nameNode, assign.srcrefTree(parent));
      } else {
        parent.replaceChild(nameNode, windowPropAccess.srcrefTree(nameNode));
      }
      compiler.reportChangeToEnclosingScope(parent);
    }

    /**
     * Adds back declarations for variables that do not cross module boundaries.
     * Must be called after RemoveGlobalVarCallback.
     */
    void declareModuleGlobals() {
      for (ModuleGlobal global : preDeclarations) {
        if (global.root.getFirstChild() != null
            && global.root.getFirstChild().isVar()) {
          global.root.getFirstChild().addChildToBack(global.name);
        } else {
          global.root.addChildToFront(IR.var(global.name).srcref(global.name));
        }
        compiler.reportChangeToEnclosingScope(global.root);
      }
    }

    /**
     * Variable that doesn't cross module boundaries.
     */
    private class ModuleGlobal {
      final Node root;
      final Node name;

      ModuleGlobal(Node root, Node name) {
        this.root = root;
        this.name = name;
      }
    }
  }

  /**
   * Removes every occurrence of var/let/const that declares a global variable.
   *
   * <pre>var NS.a = 1, NS.b = 2;</pre>
   *
   * becomes
   *
   * <pre>NS.a = 1; NS.b = 2;</pre>
   *
   * <pre>for (var a = 0, b = 0;;)</pre>
   *
   * becomes
   *
   * <pre>for (NS.a = 0, NS.b = 0;;)</pre>
   *
   * Declarations without assignments are optimized away:
   *
   * <pre>var a = 1, b;</pre>
   *
   * becomes
   *
   * <pre>NS.a = 1</pre>
   */
  private class RemoveGlobalVarCallback extends AbstractShallowStatementCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!NodeUtil.isNameDeclaration(n)) {
        return;
      }

      List<Node> commas = new ArrayList<>();
      List<Node> interestingChildren = new ArrayList<>();
      // Filter out declarations without assignments.
      // As opposed to regular var nodes, there are always assignments
      // because the previous traversal in RewriteScopeCallback creates
      // them.
      boolean allNameOrDestructuring = true;
      for (Node c : n.children()) {
        if (!c.isName() && !c.isDestructuringLhs()) {
          allNameOrDestructuring = false;
        }
        if (c.isAssign() || NodeUtil.isAnyFor(parent)) {
          interestingChildren.add(c);
        }
      }
      // If every child of a var declares a name, it must stay in place.
      // This is the case if none of the declared variables cross module
      // boundaries.
      if (allNameOrDestructuring) {
        return;
      }
      for (Node c : interestingChildren) {
        if (NodeUtil.isAnyFor(parent) && parent.getFirstChild() == n) {
          commas.add(c.cloneTree());
        } else {
          // Var statement outside of for-loop.
          Node expr = IR.exprResult(c.cloneTree()).srcref(c);
          NodeUtil.markNewScopesChanged(expr, compiler);
          parent.addChildBefore(expr, n);
        }
      }
      if (!commas.isEmpty()) {
        Node comma = joinOnComma(commas, n);
        parent.addChildBefore(comma, n);
      }
      // Remove the var/const/let node.
      parent.removeChild(n);
      NodeUtil.markFunctionsDeleted(n, compiler);
      compiler.reportChangeToEnclosingScope(parent);
    }

    private Node joinOnComma(List<Node> commas, Node source) {
      Node comma = commas.get(0);
      for (int i = 1; i < commas.size(); i++) {
        Node nextComma = IR.comma(comma, commas.get(i));
        nextComma.useSourceInfoIfMissingFrom(source);
        comma = nextComma;
      }
      return comma;
    }
  }
}
