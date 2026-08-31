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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowStatementCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds all references to global symbols and rewrites them to be property accesses to a special
 * object with the same name as the global symbol.
 *
 * <p>Given the name of the global object is NS
 *
 * <pre> var a = 1; function b() { return a }</pre>
 *
 * becomes
 *
 * <pre> NS.a = 1; NS.b = function b() { return NS.a }</pre>
 *
 * This allows splitting code into chunks that depend on each other's global symbols, without using
 * polluting JavaScript's global scope with those symbols. You typically define just a single global
 * symbol, wrap each chunk in a function wrapper, and pass the global symbol around, eg,
 *
 * <pre> var uniqueNs = uniqueNs || {}; </pre>
 *
 * <pre> (function (NS) { ...your chunk code here... })(uniqueNs); </pre>
 *
 * <p>This compile step requires rewriteGlobalDeclarationsForTryCatchWrapping to be turned on to
 * guarantee semantics.
 *
 * <p>For lots of examples, see the unit test.
 */
final class RescopeGlobalSymbols implements CompilerPass {

  private final AbstractCompiler compiler;
  private final String globalSymbolNamespace;
  private final boolean addExtern;
  private final boolean assumeCrossChunkNames;
  private final CompilerOptions.OptimizeLocalAccess optimizeLocalAccess;

  /**
   * Constructor for the RescopeGlobalSymbols compiler pass.
   *
   * @param compiler The JSCompiler, for reporting code changes.
   * @param globalSymbolNamespace Name of namespace into which all global symbols are transferred.
   * @param assumeCrossChunkNames If true, all global symbols will be assumed cross chunk boundaries
   *     and thus require renaming.
   * @param optimizeLocalAccess If true, write aliases for global symbols in the chunk where they
   *     are defined, for more efficient access.
   */
  RescopeGlobalSymbols(
      AbstractCompiler compiler,
      String globalSymbolNamespace,
      boolean assumeCrossChunkNames,
      CompilerOptions.OptimizeLocalAccess optimizeLocalAccess) {
    this(compiler, globalSymbolNamespace, true, assumeCrossChunkNames, optimizeLocalAccess);
  }

  /**
   * Constructor for the RescopeGlobalSymbols compiler pass for use in testing.
   *
   * @param compiler The JSCompiler, for reporting code changes.
   * @param globalSymbolNamespace Name of namespace into which all global symbols are transferred.
   * @param addExtern If true, the compiler will consider the globalSymbolNamespace an extern name.
   * @param assumeCrossChunkNames If true, all global symbols will be assumed cross chunk boundaries
   *     and thus require renaming. VisibleForTesting
   * @param optimizeLocalAccess Mode for optimizing local access to global symbols.
   */
  RescopeGlobalSymbols(
      AbstractCompiler compiler,
      String globalSymbolNamespace,
      boolean addExtern,
      boolean assumeCrossChunkNames,
      CompilerOptions.OptimizeLocalAccess optimizeLocalAccess) {
    this.compiler = compiler;
    this.globalSymbolNamespace = globalSymbolNamespace;
    this.addExtern = addExtern;
    this.assumeCrossChunkNames = assumeCrossChunkNames;
    this.optimizeLocalAccess = optimizeLocalAccess;
  }

  private void addExternForGlobalSymbolNamespace() {
    Node varNode = IR.var(IR.name(globalSymbolNamespace));
    CompilerInput input = compiler.getSynthesizedExternsInput();
    input.getAstRoot(compiler).addChildToBack(varNode);
    compiler.reportChangeToEnclosingScope(varNode);
  }

  @Override
  public void process(Node externs, Node root) {
    // Make the name of the globalSymbolNamespace an extern.
    if (addExtern) {
      addExternForGlobalSymbolNamespace();
    }

    // Rewrite all references to global symbols to properties of a single symbol:

    // Turn global named function statements into var assignments.
    NodeTraversal.traverse(
        compiler,
        root,
        new RewriteGlobalClassFunctionDeclarationsToVarAssignmentsCallback(compiler));

    // Find global names that are used in more than one chunk. Those that
    // are have to be rewritten.
    FindCrossChunkNamesCallback findCrossChunkNames =
        new FindCrossChunkNamesCallback(
            optimizeLocalAccess != CompilerOptions.OptimizeLocalAccess.DISABLED);

    // And find names that may refer to functions that reference this.
    FindNamesReferencingThis findNamesReferencingThis = new FindNamesReferencingThis();

    CombinedCompilerPass.traverse(
        compiler, root, ImmutableList.of(findCrossChunkNames, findNamesReferencingThis));

    RescopeGlobalSymbolsRewriteCallback.SymbolInformation symbolInfo =
        new RescopeGlobalSymbolsRewriteCallback.SymbolInformation(
            findCrossChunkNames.crossChunkNames,
            findCrossChunkNames.crossChunkNamesWithWriteFromOtherChunk,
            findCrossChunkNames.globalNamesWithReadInDefiningChunk,
            findCrossChunkNames.globalNamesWithInnerScopeWriteInDefiningChunk,
            findNamesReferencingThis.maybeReferencesThis);

    // Rewrite all references to be property accesses of the single symbol.
    RescopeGlobalSymbolsRewriteCallback rewriteScope =
        new RescopeGlobalSymbolsRewriteCallback(
            compiler,
            globalSymbolNamespace,
            assumeCrossChunkNames,
            optimizeLocalAccess,
            externs,
            symbolInfo);
    NodeTraversal.traverse(compiler, root, rewriteScope);

    // Remove the var from statements in global scope if the declared names have been rewritten
    // in the previous pass.
    NodeTraversal.traverse(compiler, root, new RemoveGlobalVarCallback(compiler));

    rewriteScope.addDeclarations();
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
  private static class RewriteGlobalClassFunctionDeclarationsToVarAssignmentsCallback
      extends AbstractShallowStatementCallback {
    private final AbstractCompiler compiler;

    RewriteGlobalClassFunctionDeclarationsToVarAssignmentsCallback(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Ignore block scopes within the global scope, as class and function declarations are
      // block-scoped.
      // Note that we should never find block-scoped function declarations if outputting ES5
      // code. Es6RewriteBlockScopedFunctionDeclaration will have rewritten them.
      if (!t.inGlobalScope()) {
        return;
      }
      // Ignore everything that's not a function or class declaration.
      if (!NodeUtil.isFunctionDeclaration(n) && !NodeUtil.isClassDeclaration(n)) {
        return;
      }
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
        var.insertAfter(prev);
      }
      compiler.reportChangeToEnclosingScope(parent);
    }
  }

  /**
   * Find all global names that are used in more than one chunk. The following compiler
   * transformations can ignore the globals that are not.
   */
  private static class FindCrossChunkNamesCallback extends AbstractPostOrderCallback {
    private final boolean trackLocalAccessSets;
    final Set<String> crossChunkNames = new LinkedHashSet<>();

    /**
     * Global cross-chunk identifiers that are written to from a chunk other than the defining one.
     * Only populated if trackLocalAccessSets is true.
     */
    final Set<String> crossChunkNamesWithWriteFromOtherChunk = new LinkedHashSet<>();

    /**
     * Global identifiers that are read in the chunk where they are defined. Only populated if
     * trackLocalAccessSets is true.
     */
    final Set<String> globalNamesWithReadInDefiningChunk = new LinkedHashSet<>();

    /**
     * Global identifiers that are written to from a nested scope (i.e. not the global scope) in
     * their defining chunk. Only populated if trackLocalAccessSets is true.
     */
    final Set<String> globalNamesWithInnerScopeWriteInDefiningChunk = new LinkedHashSet<>();

    FindCrossChunkNamesCallback(boolean trackLocalAccessSets) {
      this.trackLocalAccessSets = trackLocalAccessSets;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String name = n.getString();
        if (name.isEmpty() || (!trackLocalAccessSets && crossChunkNames.contains(name))) {
          return;
        }
        Scope s = t.getScope();
        Var v = s.getVar(name);
        if (v == null || !v.isGlobal()) {
          return;
        }

        CompilerInput input = v.getInput();

        if (trackLocalAccessSets
            && !t.inGlobalScope()
            && (input == null || input.getChunk() == t.getChunk())
            && NodeUtil.isLValue(n)
            && (!NodeUtil.isNameDeclaration(parent) || n.hasChildren())) {
          globalNamesWithInnerScopeWriteInDefiningChunk.add(name);
        }

        if (input == null) {
          // We know nothing. Assume name is used across chunks.
          crossChunkNames.add(name);
          return;
        }
        // Compare the chunk where the variable is declared to the current
        // chunk. If they are different, the variable is used across chunks.
        JSChunk chunk = input.getChunk();
        if (chunk != t.getChunk()) {
          crossChunkNames.add(name);

          // If tracking local access, track names with non-local writes separately. This includes
          // all L-value names except for declarations without children.
          if (trackLocalAccessSets
              && NodeUtil.isLValue(n)
              && (!NodeUtil.isNameDeclaration(parent) || n.hasChildren())) {
            crossChunkNamesWithWriteFromOtherChunk.add(name);
          }
        } else if (trackLocalAccessSets && n != v.getNameNode()) {
          globalNamesWithReadInDefiningChunk.add(name);
        }
      }
    }
  }

  /**
   * Builds the maybeReferencesThis set of names that may reference a function that references this.
   * If the function a name references does not reference this it can be called as a method call
   * where the this value is not the same as in a normal function call.
   */
  private static class FindNamesReferencingThis extends AbstractPostOrderCallback {
    final Set<String> maybeReferencesThis = new LinkedHashSet<>();

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
        if (value == null || !value.isFunction() || NodeUtil.referencesOwnReceiver(value)) {
          maybeReferencesThis.add(name);
        }
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
  private static class RemoveGlobalVarCallback extends AbstractShallowStatementCallback {
    private final AbstractCompiler compiler;

    RemoveGlobalVarCallback(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!NodeUtil.isNameDeclaration(n)) {
        return;
      }

      List<Node> commas = new ArrayList<>();
      List<Node> interestingChildren = new ArrayList<>();
      // Filter out declarations without assignments.
      // As opposed to regular var nodes, there are always assignments
      // because the previous traversal in RescopeGlobalSymbolsRewriteCallback creates
      // them.
      boolean allNameOrDestructuring = true;
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        if (!c.isName() && !c.isDestructuringLhs()) {
          allNameOrDestructuring = false;
        }
        if (c.isAssign() || NodeUtil.isAnyFor(parent)) {
          interestingChildren.add(c);
        }
      }
      // If every child of a var declares a name, it must stay in place.
      // This is the case if none of the declared variables cross chunk
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
          expr.insertBefore(n);
        }
      }
      if (!commas.isEmpty()) {
        Node comma = joinOnComma(commas, n);
        comma.insertBefore(n);
      }
      // Remove the var/const/let node.
      n.detach();
      NodeUtil.markFunctionsDeleted(n, compiler);
      compiler.reportChangeToEnclosingScope(parent);
    }

    private Node joinOnComma(List<Node> commas, Node source) {
      Node comma = commas.get(0);
      for (int i = 1; i < commas.size(); i++) {
        Node nextComma = IR.comma(comma, commas.get(i));
        nextComma.srcrefIfMissing(source);
        comma = nextComma;
      }
      return comma;
    }
  }
}
