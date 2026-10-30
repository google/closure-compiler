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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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

    RescopeGlobalSymbolsRenameHelper.HelperBuilder renameHelperBuilder =
        new RescopeGlobalSymbolsRenameHelper.HelperBuilder(
            compiler, globalSymbolNamespace, optimizeLocalAccess);

    // Find global names that are used in more than one chunk. Those that
    // are have to be rewritten.
    FindCrossChunkNamesCallback findCrossChunkNames =
        new FindCrossChunkNamesCallback(
            compiler,
            assumeCrossChunkNames,
            optimizeLocalAccess.shouldOptimize(),
            renameHelperBuilder);

    // And find names that may refer to functions that reference this.
    FindNamesReferencingThis findNamesReferencingThis = new FindNamesReferencingThis();

    CombinedCompilerPass.traverse(
        compiler, root, ImmutableList.of(findCrossChunkNames, findNamesReferencingThis));

    ImmutableSet<String> externNames = NodeUtil.collectExternVariableNames(compiler, externs);

    RescopeGlobalSymbolsInfo symbolInfo =
        new RescopeGlobalSymbolsInfo(
            globalSymbolNamespace,
            externNames,
            findCrossChunkNames.crossChunkNames,
            findCrossChunkNames.crossChunkNamesWithWriteFromOtherChunk,
            findCrossChunkNames.crossChunkNamesUsedInNonDependentChunk,
            findCrossChunkNames.globalNamesWithReadInDefiningChunk,
            findCrossChunkNames.globalNamesWithInnerScopeWriteInDefiningChunk,
            findCrossChunkNames.globalNamesWithUnsafeWriteForLocalAliasInDefiningChunk,
            findNamesReferencingThis.maybeReferencesThis);

    RescopeGlobalSymbolsRenameHelper renameHelper = renameHelperBuilder.build(symbolInfo);

    // Rewrite all references to be property accesses of the single symbol.
    RescopeGlobalSymbolsRewriteCallback rewriteScope =
        new RescopeGlobalSymbolsRewriteCallback(
            compiler, optimizeLocalAccess, symbolInfo, renameHelper);
    NodeTraversal.traverse(compiler, root, rewriteScope);

    // Remove the var from statements in global scope if the declared names have been rewritten
    // in the previous pass.
    NodeTraversal.traverse(
        compiler,
        root,
        new RemoveGlobalVarCallback(
            compiler, optimizeLocalAccess.shouldPreserveLocalVarDeclarations()));

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
    private final AbstractCompiler compiler;
    private final boolean assumeCrossChunkNames;
    private final boolean trackLocalAccessSets;
    final Set<String> crossChunkNames = new LinkedHashSet<>();

    /**
     * Global cross-chunk identifiers that are written to from a chunk other than the defining one.
     * Only populated if trackLocalAccessSets is true.
     */
    final Set<String> crossChunkNamesWithWriteFromOtherChunk = new LinkedHashSet<>();

    /**
     * Global cross-chunk identifiers that are referenced in a chunk that does not depend on their
     * defining chunk. Only populated if trackLocalAccessSets is true.
     */
    final Set<String> crossChunkNamesUsedInNonDependentChunk = new LinkedHashSet<>();

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

    /**
     * Global identifiers that have an L-value write in their defining chunk that cannot be safely
     * mirrored by {@link RescopeGlobalSymbolsRewriteCallback#addGlobalNamespaceAlias} (such as a
     * destructuring assignment in an expression context or a {@code for-in}/{@code for-of} loop
     * header). Only populated if trackLocalAccessSets is true.
     */
    final Set<String> globalNamesWithUnsafeWriteForLocalAliasInDefiningChunk =
        new LinkedHashSet<>();

    private final RescopeGlobalSymbolsRenameHelper.HelperBuilder renameHelperBuilder;

    FindCrossChunkNamesCallback(
        AbstractCompiler compiler,
        boolean assumeCrossChunkNames,
        boolean trackLocalAccessSets,
        RescopeGlobalSymbolsRenameHelper.HelperBuilder renameHelperBuilder) {
      this.compiler = compiler;
      this.assumeCrossChunkNames = assumeCrossChunkNames;
      this.trackLocalAccessSets = trackLocalAccessSets;
      this.renameHelperBuilder = renameHelperBuilder;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String name = n.getString();
        if (name.isEmpty() || (!trackLocalAccessSets && crossChunkNames.contains(name))) {
          return;
        }
        renameHelperBuilder.recordExistingName(name);
        Scope s = t.getScope();
        Var v = s.getVar(name);
        if (v == null || !v.isGlobal()) {
          return;
        }

        if (assumeCrossChunkNames
            || compiler.getCodingConvention().isExported(name, /* local= */ false)) {
          crossChunkNames.add(name);
        }

        CompilerInput input = v.getInput();

        if (trackLocalAccessSets
            && !t.inGlobalScope()
            && (input == null || input.getChunk() == t.getChunk())
            && NodeUtil.isLValueWrite(n)) {
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

          if (trackLocalAccessSets) {
            // If tracking local access, track names with non-local writes separately. This includes
            // all L-value names except for declarations without children.
            if (NodeUtil.isLValueWrite(n)) {
              crossChunkNamesWithWriteFromOtherChunk.add(name);
            }
            if (!compiler.getChunkGraph().dependsOn(t.getChunk(), chunk)) {
              crossChunkNamesUsedInNonDependentChunk.add(name);
            }
          }
        } else if (trackLocalAccessSets) {
          if (isReadReference(n)) {
            globalNamesWithReadInDefiningChunk.add(name);
          }
          if (NodeUtil.isLValueWrite(n) && !isWriteSafeForLocalAliasMirroring(n, parent)) {
            globalNamesWithUnsafeWriteForLocalAliasInDefiningChunk.add(name);
          }
        }
      }
    }

    /**
     * Returns whether the {@code NAME} node {@code n} reads the variable's value (either as a
     * non-L-value reference or as the target of a read-modify-write operation such as {@code a +=
     * 1} or {@code a++}).
     */
    private static boolean isReadReference(Node n) {
      if (!NodeUtil.isLValue(n)) {
        return true;
      }
      Node parent = n.getParent();
      return NodeUtil.isCompoundAssignmentOp(parent) || parent.isInc() || parent.isDec();
    }

    /**
     * Returns whether an L-value write {@code n} in its defining chunk can be safely mirrored to
     * the global namespace or wrapper object by {@link
     * RescopeGlobalSymbolsRewriteCallback#addGlobalNamespaceAlias}.
     *
     * <p>{@code addGlobalNamespaceAlias} rewrites assignments ({@code a = x}, {@code a += x}),
     * initialized declarations ({@code var a = x}), and increments/decrements ({@code ++a}, {@code
     * a++}) in place as expressions, which works in any expression context. For destructuring
     * writes, it appends a separate {@code _.a._ = _a;} statement after the enclosing statement,
     * which is only sound when the write is a top-level destructuring assignment statement or a
     * non-for-loop destructuring declaration.
     */
    private static boolean isWriteSafeForLocalAliasMirroring(Node n, Node parent) {
      if ((NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n)
          || parent.isInc()
          || parent.isDec()) {
        return true;
      }
      if (NodeUtil.isNameDeclaration(parent)) {
        return !NodeUtil.isEnhancedFor(parent.getParent());
      }
      if (NodeUtil.isLhsByDestructuring(n)) {
        Node rootTarget = NodeUtil.getRootTarget(n);
        Node rootTargetParent = rootTarget.getParent();
        if (rootTargetParent.isDestructuringLhs()) {
          Node declaration = rootTargetParent.getParent();
          return NodeUtil.isStatementBlock(declaration.getParent());
        }
        if (rootTargetParent.isAssign() && rootTargetParent.getFirstChild() == rootTarget) {
          Node exprResult = rootTargetParent.getParent();
          return exprResult.isExprResult() && NodeUtil.isStatementBlock(exprResult.getParent());
        }
      }
      return false;
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
   * Removes {@code var}/{@code let}/{@code const} declarations whose global variables have been
   * rewritten to property assignments by {@link RescopeGlobalSymbolsRewriteCallback}, while
   * preserving {@code var} declarations for variables that remain local to the chunk.
   *
   * <p>When all variables in a declaration are rewritten to namespace assignments:
   *
   * <pre>var NS.a = 1, NS.b = 2;</pre>
   *
   * becomes
   *
   * <pre>NS.a = 1; NS.b = 2;</pre>
   *
   * <p>When {@code preserveLocalVarDeclarations} is true and a declaration mixes local variables
   * with rewritten namespace assignments, the statement is split in order so local variables retain
   * their {@code var} declarations in place:
   *
   * <pre>var a = NS.a = 1, NS.b = 2, c = 3;</pre>
   *
   * becomes
   *
   * <pre>var a = NS.a = 1; NS.b = 2; var c = 3;</pre>
   *
   * <p>In {@code for}-loop headers:
   *
   * <pre>for (var a = 0, b = 0;;)</pre>
   *
   * becomes
   *
   * <pre>for (NS.a = 0, NS.b = 0;;)</pre>
   *
   * <p>Rewritten declarations without assignments are optimized away:
   *
   * <pre>var a = 1, b;</pre>
   *
   * becomes
   *
   * <pre>NS.a = 1</pre>
   */
  private static class RemoveGlobalVarCallback extends AbstractShallowStatementCallback {
    private final AbstractCompiler compiler;

    /**
     * Whether {@code NAME} children remaining in a partially rewritten declaration should be
     * preserved in place as {@code var} declarations.
     *
     * <p>When {@code true} (used when {@code
     * optimizeLocalAccess.shouldPreserveLocalVarDeclarations()} is true), variables that stay local
     * to their defining chunk are left as {@code NAME} nodes (with optional initializers) in the
     * declaration by {@link RescopeGlobalSymbolsRewriteCallback} and must be emitted as {@code var}
     * statements in place. When {@code false}, all local variables in a partially rewritten
     * declaration have already been hoisted to the top of the chunk via {@code preDeclarations} and
     * their initializers converted to {@code ASSIGN} nodes, so any remaining bare {@code NAME}
     * children are uninitialized and should be dropped.
     */
    private final boolean preserveLocalVarDeclarations;

    /**
     * @param compiler The JSCompiler, for reporting code changes.
     * @param preserveLocalVarDeclarations If true, preserve remaining {@code NAME} children as
     *     in-place {@code var} declarations rather than dropping them.
     */
    RemoveGlobalVarCallback(AbstractCompiler compiler, boolean preserveLocalVarDeclarations) {
      this.compiler = compiler;
      this.preserveLocalVarDeclarations = preserveLocalVarDeclarations;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!NodeUtil.isNameDeclaration(n)) {
        return;
      }

      // If every child of a non-empty declaration is still a variable binding (NAME or
      // DESTRUCTURING_LHS) rather than a rewritten assignment (ASSIGN) or property access
      // (GETPROP), then no variables in the declaration were rewritten into standalone
      // expressions, and the declaration node must stay in place as-is.
      if (hasOnlyNameOrDestructuringChildren(n)) {
        return;
      }

      if (NodeUtil.isAnyFor(parent) && parent.getFirstChild() == n) {
        // Declaration in a for-loop header (`for (var a = 0, b = 1; ...)` or
        // `for (var x in/of ...)`). A for-loop initializer or iteration target slot only accepts a
        // single expression and cannot be split into multiple statements. In a standard for-loop,
        // join all children into a comma expression (`for (_.a = 0, b = 1; ...)`). In a for-in or
        // for-of loop, the single rewritten LHS child (e.g. `_.x` or `[_.a, _.b]`) replaces the
        // declaration directly (`for (_.x in ...)`). Any local variables declared in the loop
        // header were already recorded in `preDeclarations` by RescopeGlobalSymbolsRewriteCallback
        // to be hoisted to the top of the chunk.
        List<Node> commas = new ArrayList<>();
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
          commas.add(c.cloneTree());
        }
        if (!commas.isEmpty()) {
          Node comma = joinOnComma(commas, n);
          comma.insertBefore(n);
        }
      } else {
        // Declaration statement outside of a for-loop header.
        // Group consecutive local variable bindings (NAME) into VAR declarations when
        // preserveLocalVarDeclarations is enabled, and emit rewritten ASSIGN children as standalone
        // EXPR_RESULT statements in original declaration order. Any DESTRUCTURING_LHS children in a
        // rewritten declaration were already converted to ASSIGN nodes by
        // RescopeGlobalSymbolsRewriteCallback.
        // Any other children (such as bare GETPROP nodes from uninitialized cross-chunk
        // declarations, or uninitialized NAME nodes when preserveLocalVarDeclarations is false)
        // have no side effects and are omitted.
        Node currentVar = null;
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
          checkState(!c.isDestructuringLhs(), "Unexpected DESTRUCTURING_LHS child: %s", c);
          if (preserveLocalVarDeclarations && c.isName()) {
            if (currentVar == null) {
              currentVar = IR.var(c.cloneTree()).srcref(n);
              currentVar.setJSDocInfo(n.getJSDocInfo());
              currentVar.insertBefore(n);
            } else {
              currentVar.addChildToBack(c.cloneTree());
            }
          } else if (c.isAssign()) {
            // Finalize any in-progress VAR declaration before emitting the assignment expression
            // so that initializer evaluation order is preserved across mixed declarations.
            if (currentVar != null) {
              NodeUtil.markNewScopesChanged(currentVar, compiler);
              currentVar = null;
            }
            Node expr = IR.exprResult(c.cloneTree()).srcref(c);
            NodeUtil.markNewScopesChanged(expr, compiler);
            expr.insertBefore(n);
          }
        }
        if (currentVar != null) {
          NodeUtil.markNewScopesChanged(currentVar, compiler);
        }
      }
      // Remove the original var/const/let node.
      n.detach();
      NodeUtil.markFunctionsDeleted(n, compiler);
      compiler.reportChangeToEnclosingScope(parent);
    }

    private static boolean hasOnlyNameOrDestructuringChildren(Node n) {
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        if (!c.isName() && !c.isDestructuringLhs()) {
          return false;
        }
      }
      return n.hasChildren();
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
