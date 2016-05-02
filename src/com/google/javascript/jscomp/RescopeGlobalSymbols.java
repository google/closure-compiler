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
  private static final Set<String> SPECIAL_EXTERNS =
      ImmutableSet.of(WINDOW, "eval", "arguments", "undefined",
          // The javascript built-in objects (listed in Ecma 262 section 4.2)
          "Object", "Function", "Array", "String", "Boolean", "Number", "Math",
          "Date", "RegExp", "JSON", "Error", "EvalError", "ReferenceError",
          "SyntaxError", "TypeError", "URIError");

  private final AbstractCompiler compiler;
  private final String globalSymbolNamespace;
  private final boolean addExtern;
  private final boolean assumeCrossModuleNames;
  private final Set<String> crossModuleNames = new HashSet<>();
  private final Set<String> maybeReferencesThis = new HashSet<>();

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

  private void addExternForGlobalSymbolNamespace() {
    Node varNode = IR.var(globalSymbolNamespace);
    CompilerInput input = compiler.getSynthesizedExternsInput();
    input.getAstRoot(compiler).addChildrenToBack(varNode);
    compiler.reportCodeChange();
  }

  @Override
  public void process(Node externs, Node root) {
    // Make the name of the globalSymbolNamespace an extern.
    if (addExtern) {
      addExternForGlobalSymbolNamespace();
    }
    // Rewrite all references to global symbols to properties of a
    // single symbol by:
    // (If necessary the 4 traversals could be combined. They are left
    // separate for readability reasons.)
    // 1. turning global named function statements into var assignments.
    NodeTraversal.traverseEs6(
        compiler,
        root,
        new RewriteGlobalFunctionStatementsToVarAssignmentsCallback());
    // 2. find global names that are used in more than one module. Those that
    //    are have to be rewritten.
    List<Callback> nonMutatingPasses = new ArrayList<>();
    nonMutatingPasses.add(new FindCrossModuleNamesCallback());
    //    and find names that may refering functions that reference this.
    nonMutatingPasses.add(new FindNamesReferencingThis());
    CombinedCompilerPass.traverse(compiler, root, nonMutatingPasses);
    // 3. rewriting all references to be property accesses of the single symbol.
    RewriteScopeCallback rewriteScope = new RewriteScopeCallback();
    NodeTraversal.traverseEs6(compiler, root, rewriteScope);
    // 4. removing the var from statements in global scope if the declared names
    //    have been rewritten in the previous pass.
    NodeTraversal.traverseEs6(compiler, root, new RemoveGlobalVarCallback());
    rewriteScope.declareModuleGlobals();

    // Extra pass which makes all extern global symbols reference window
    // explicitly.
    NodeTraversal.traverseEs6(
        compiler,
        root,
        new MakeExternsReferenceWindowExplicitly());
  }

  /**
   * Rewrites function statements to var statements + assignment.
   *
   * <pre>function test(){}</pre>
   * becomes
   * <pre>var test = function (){}</pre>
   *
   * After this traversal, the special case of global function statements
   * can be ignored.
   */
  private class RewriteGlobalFunctionStatementsToVarAssignmentsCallback
      extends AbstractShallowStatementCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isFunctionDeclaration(n)) {
        String name = NodeUtil.getName(n);
        n.getFirstChild().setString("");
        Node prev = parent.getChildBefore(n);
        n.detachFromParent();
        Node var = IR.var(name, n).useSourceInfoFromForTree(n);
        if (prev == null) {
          parent.addChildToFront(var);
        } else {
          parent.addChildAfter(var, prev);
        }
        compiler.reportCodeChange();
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
        } else if (parent.isVar()) {
          value = n.getFirstChild();
        } else if (parent.isFunction()) {
          value = parent;
        }
        if (value == null) {
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
        // If anything but a function is assignment we assume that possibly
        // a function referencing this is being assignment. Otherwise we
        // check whether the function that is being assigned references this.
        if (!value.isFunction() || NodeUtil.referencesThis(value)) {
          maybeReferencesThis.add(name);
        }
      }
    }
  }

  /**
   * Visits each NAME token and checks whether it refers to a global variable.
   * If yes, rewrites the name to be a property access on the
   * "globalSymbolNamespace".
   *
   * <pre>var a = 1, b = 2, c = 3;</pre>
   * becomes
   * <pre>var NS.a = 1, NS.b = 2, NS.c = 4</pre>
   * (The var token is removed in a later traversal.)
   *
   * <pre>a + b</pre>
   * becomes
   * <pre>NS.a + NS.b</pre>
   *
   * <pre>a()</pre>
   * becomes
   * <pre>(0,NS.a)()</pre>
   * Notice the special syntax here to preserve the *this* semantics in the
   * function call.
   */
  private class RewriteScopeCallback extends AbstractPostOrderCallback {

    List<ModuleGlobal> preDeclarations = new ArrayList<>();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isName()) {
        return;
      }
      String name = n.getString();
      // Ignore anonymous functions
      if (parent.isFunction() && name.isEmpty()) {
        return;
      }
      Var var = t.getScope().getVar(name);
      if (var == null) {
        return;
      }
      // Don't touch externs.
      if (var.isExtern()) {
        return;
      }
      // When the globalSymbolNamespace is used as a local variable name
      // add suffix to avoid shadowing the namespace. Also add a suffix
      // if a name starts with the name of the globalSymbolNamespace and
      // the suffix.
      if (!var.isExtern() && !var.isGlobal()
          && (name.equals(globalSymbolNamespace)
              || name.startsWith(
                  globalSymbolNamespace + DISAMBIGUATION_SUFFIX))) {
        n.setString(name + DISAMBIGUATION_SUFFIX);
        compiler.reportCodeChange();
      }
      // We only care about global vars.
      if (!var.isGlobal()) {
        return;
      }
      Node nameNode = var.getNameNode();
      // The exception variable (e in try{}catch(e){}) should not be rewritten.
      if (nameNode != null && nameNode.getParent() != null
          && nameNode.getParent().isCatch()) {
        return;
      }
      replaceSymbol(n, name, t.getInput());
    }

    private void replaceSymbol(Node node, String name, CompilerInput input) {
      Node parent = node.getParent();
      boolean isCrossModule = isCrossModuleName(name);
      if (!isCrossModule) {
        // When a non cross module name appears outside a var declaration we
        // never have to do anything.
        if (!parent.isVar()) {
          return;
        }
        // If it is a var declaration, but no cross module names are declared
        // we also don't have to do anything.
        boolean hasCrossModuleChildren = false;
        for (Node c : parent.children()) {
          // Var child is no longer a name means it was transformed already
          // which means there was a cross module name.
          if (!c.isName() || isCrossModuleName(c.getString())) {
            hasCrossModuleChildren = true;
            break;
          }
        }
        if (!hasCrossModuleChildren) {
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
      } else if (isCrossModule) {
        parent.replaceChild(node, replacement);
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
      if (!isCrossModule && parent.isVar()) {
        preDeclarations.add(new ModuleGlobal(
            input.getAstRoot(compiler),
            IR.name(name).srcref(node)));
      }
      compiler.reportCodeChange();
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
          global.root.addChildToFront(
              IR.var(global.name).srcref(global.name));
        }
        compiler.reportCodeChange();
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
   * Removes every occurrence of var that declares a global variable.
   *
   * <pre>var NS.a = 1, NS.b = 2;</pre>
   * becomes
   * <pre>NS.a = 1; NS.b = 2;</pre>
   *
   * <pre>for (var a = 0, b = 0;;)</pre>
   * becomes
   * <pre>for (NS.a = 0, NS.b = 0;;)</pre>
   *
   * Declarations without assignments are optimized away:
   * <pre>var a = 1, b;</pre>
   * becomes
   * <pre>NS.a = 1</pre>
   */
  private class RemoveGlobalVarCallback extends
      AbstractShallowStatementCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isVar()) {
        return;
      }

      List<Node> commas = new ArrayList<>();
      List<Node> interestingChildren = new ArrayList<>();
      // Filter out declarations without assignments.
      // As opposed to regular var nodes, there are always assignments
      // because the previous traversal in RewriteScopeCallback creates
      // them.
      boolean allName = true;
      for (Node c : n.children()) {
        if (!c.isName()) {
          allName = false;
        }
        if (c.isAssign() || parent.isFor()) {
          interestingChildren.add(c);
        }
      }
      // If every child of a var declares a name, it must stay in place.
      // This is the case if none of the declared variables cross module
      // boundaries.
      if (allName) {
        return;
      }
      for (Node c : interestingChildren) {
        if (parent.isFor() && parent.getFirstChild() == n) {
          commas.add(c.cloneTree());
        } else {
          // Var statement outside of for-loop.
          Node expr = IR.exprResult(c.cloneTree()).srcref(c);
          parent.addChildBefore(expr, n);
        }
      }
      if (!commas.isEmpty()) {
        Node comma = joinOnComma(commas, n);
        parent.addChildBefore(comma, n);
      }
      // Remove the var node.
      parent.removeChild(n);
      compiler.reportCodeChange();
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

  /**
   * Rewrites extern names to be explicit children of window instead of only
   * implicitly referencing it.
   * This enables injecting window into a scope and make all global symbol
   * depend on the injected object.
   */
  private class MakeExternsReferenceWindowExplicitly extends
      AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isName()) {
        return;
      }
      String name = n.getString();
      if (globalSymbolNamespace.equals(name)
          || SPECIAL_EXTERNS.contains(name)) {
        return;
      }
      Var var = t.getScope().getVar(name);
      if (name.length() > 0 && (var == null || var.isExtern())) {
        parent.replaceChild(n, IR.getprop(IR.name(WINDOW), IR.string(name))
            .srcrefTree(n));
        compiler.reportCodeChange();
      }
    }
  }
}
