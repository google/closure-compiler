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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Node traversal callback to rewrite global symbols as part of the RescopeGlobalSymbols pass.
 *
 * <p>Visits each NAME token and checks whether it refers to a global variable. If yes, rewrites the
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
final class RescopeGlobalSymbolsRewriteCallback implements NodeTraversal.Callback {

  private final AbstractCompiler compiler;
  private final CompilerOptions.OptimizeLocalAccess optimizeLocalAccess;
  private final RescopeGlobalSymbolsInfo symbolInfo;
  private final RescopeGlobalSymbolsRenameHelper renameHelper;

  private final List<ChunkGlobal> preDeclarations = new ArrayList<>();

  /**
   * Map from chunks to the set of global symbols to be aliased directly for that chunk (e.g. `var
   * {a} = _`). Only populated if optimizeLocalAccess is an ALL_CHUNKS option. These symbols are
   * used in that chunk, to avoid declaring unnecessary aliases. They can only be reassigned during
   * static execution of the defining chunk, and that defining chunk must be a dependency of the
   * using chunk. If ALL_CHUNKS_WITH_WRAPPED_REASSIGNABLE_SYMBOLS is set, then symbols that should
   * be wrapped and aliased will be tracked separately in wrappedCrossChunkNames.
   */
  private final Map<JSChunk, Set<String>> localAliasesForUnwrappedCrossChunkNames =
      new LinkedHashMap<>();

  /**
   * Map from cross-chunk global symbols to the chunks that reference them. Only populated if
   * optimizeLocalAccess.shouldWrapReassignableSymbols() is true. This is used to determine which
   * chunks to define wrapper objects in and when a symbol on the global namespace can be safely
   * deleted after its last local alias is set.
   */
  private final Map<String, Set<JSChunk>> chunksUsingCrossChunkSymbols = new LinkedHashMap<>();

  RescopeGlobalSymbolsRewriteCallback(
      AbstractCompiler compiler,
      CompilerOptions.OptimizeLocalAccess optimizeLocalAccess,
      RescopeGlobalSymbolsInfo symbolInfo,
      RescopeGlobalSymbolsRenameHelper renameHelper) {
    this.compiler = compiler;
    this.optimizeLocalAccess = optimizeLocalAccess;
    this.symbolInfo = symbolInfo;
    this.renameHelper = renameHelper;
  }

  private boolean isCrossChunkName(String name) {
    return symbolInfo.crossChunkNames().contains(name);
  }

  private boolean isWrappedCrossChunkName(String name) {
    return optimizeLocalAccess.shouldWrapReassignableSymbols()
        && symbolInfo.wrappedCrossChunkNames().contains(name);
  }

  private boolean isExternVar(String varname, NodeTraversal t) {
    if (varname.isEmpty()) {
      return false;
    }
    Var v = t.getScope().getVar(varname);
    return v == null
        || v.isExtern()
        || (v.getScope().isGlobal() && symbolInfo.externNames().contains(varname));
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (NodeUtil.isNameDeclaration(n)) {
      visitNameDeclaration(t, n);
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isName()) {
      visitName(t, n, parent);
    }
  }

  private void visitNameDeclaration(NodeTraversal t, Node declaration) {
    ArrayList<Node> allLhsNodes = new ArrayList<>();
    NodeUtil.visitLhsNodesInNode(declaration, allLhsNodes::add);
    if (allLhsNodes.isEmpty()) {
      return;
    }
    boolean hasImportantName = false;
    boolean isGlobalDeclaration = t.getScope().getVar(allLhsNodes.get(0).getString()).isGlobal();

    // Check if any names are in the externs or are global and cross chunk.
    for (Node lhs : allLhsNodes) {
      checkState(lhs.isName(), "Unexpected lhs node %s, expected NAME", lhs);
      if ((isGlobalDeclaration && isCrossChunkName(lhs.getString()))
          || isExternVar(lhs.getString(), t)) {
        hasImportantName = true;
        break;
      }
    }

    if (hasImportantName) {
      rewriteNameDeclaration(t, declaration, allLhsNodes, isGlobalDeclaration);
    }
  }

  /**
   * Returns whether a global variable declared in the current chunk remains a local variable in
   * that chunk (rather than being replaced everywhere in the chunk by a property access like {@code
   * _.a} or {@code a._}, or referring to an extern).
   *
   * <p>A global variable stays local in its defining chunk if it is not an extern and either:
   *
   * <ul>
   *   <li>it is not a cross-chunk global variable (so it is only ever used within this chunk), or
   *   <li>{@code optimizeLocalAccess} is enabled and the cross-chunk variable is read in its
   *       defining chunk, is not written to from any other chunk, and is not wrapped in a holder
   *       object.
   * </ul>
   */
  private boolean globalVarStaysLocalInDefiningChunk(String name, NodeTraversal t) {
    checkState(t.getScope().getVar(name).isGlobal(), "Expected global variable: %s", name);
    if (isExternVar(name, t)) {
      return false;
    }
    if (!isCrossChunkName(name)) {
      return true;
    }
    if (!optimizeLocalAccess.shouldOptimize()) {
      return false;
    }
    return !symbolInfo.crossChunkNamesWithWriteFromOtherChunk().contains(name)
        && !isWrappedCrossChunkName(name)
        && symbolInfo.globalNamesWithReadInDefiningChunk().contains(name);
  }

  /**
   * Partially rewrites a variable declaration that contains at least one cross-chunk global or
   * extern variable.
   *
   * <p>In the post-order traversal ({@link #visitName}), all rescoped cross-chunk global references
   * will become property accesses (e.g. {@code _.a}), which are invalid as the LHS of a variable
   * declaration and therefore must be converted into {@code ASSIGN} nodes beforehand.
   *
   * <ul>
   *   <li>When {@code optimizeLocalAccess.shouldPreserveLocalVarDeclarations()} is true (outside of
   *       {@code for}-loop headers), {@code NAME} declarations that remain local variables in their
   *       defining chunk (both chunk-local variables and local-access-optimized cross-chunk
   *       variables) are kept in place as {@code NAME} children on the {@code VAR} declaration so
   *       their initializers stay attached to {@code var}. Only non-local {@code NAME} children
   *       with initializers and {@code DESTRUCTURING_LHS} children are converted to {@code ASSIGN}
   *       nodes.
   *   <li>Otherwise (when {@code optimizeLocalAccess.shouldPreserveLocalVarDeclarations()} is
   *       false, or in {@code for}-loop headers), all local variables in the statement are recorded
   *       in {@link #preDeclarations} to be hoisted as uninitialized {@code var} declarations at
   *       the top of the chunk, and all initialized {@code NAME} children and {@code
   *       DESTRUCTURING_LHS} children are converted to {@code ASSIGN} nodes.
   * </ul>
   */
  private void rewriteNameDeclaration(
      NodeTraversal t, Node declaration, List<Node> allLhsNodes, boolean isGlobalDeclaration) {
    CompilerInput input = t.getInput();

    if (optimizeLocalAccess.shouldPreserveLocalVarDeclarations()
        && !NodeUtil.isAnyFor(declaration.getParent())) {
      // If all variables in a global `let` or `const` declaration stay local (e.g. `let a = 1;`
      // becoming `var a = _.a = 1;`), RemoveGlobalVarCallback will leave the declaration node in
      // place, so convert its token to `VAR`. Uninitialized declarations with no rescoped variables
      // (e.g. `let a;`) are left untouched.
      boolean changed = false;
      if (isGlobalDeclaration
          && !declaration.isVar()
          && containsRescopedOrInitializedVars(declaration, isGlobalDeclaration)) {
        declaration.setToken(Token.VAR);
        changed = true;
      }
      for (Node child = declaration.getFirstChild(); child != null; ) {
        final Node next = child.getNext();
        if (child.isName()) {
          String name = child.getString();
          if (!globalVarStaysLocalInDefiningChunk(name, t)) {
            if (child.hasChildren()) {
              Node assign = IR.assign(child.cloneNode(), child.removeFirstChild());
              child.replaceWith(assign);
              assign.setJSDocInfo(declaration.getJSDocInfo());
              changed = true;
            } else if (isExternVar(name, t)) {
              // Detach uninitialized extern redeclarations (e.g. `var externVar, a = 1;`) so
              // RemoveGlobalVarCallback does not treat the remaining NAME node as a local variable.
              child.detach();
              changed = true;
            }
          }
        } else if (child.isDestructuringLhs()) {
          // Destructuring declarations are converted to assignments, so any bound variables that
          // stay local to the defining chunk must be pre-declared at the top of the chunk.
          NodeUtil.visitLhsNodesInNode(
              child,
              lhs -> {
                String name = lhs.getString();
                if (globalVarStaysLocalInDefiningChunk(name, t)) {
                  preDeclarations.add(
                      new ChunkGlobal(input.getAstRoot(compiler), IR.name(name).srcref(lhs)));
                }
              });
          Node assign = IR.assign(child.removeFirstChild(), child.removeFirstChild());
          child.replaceWith(assign);
          assign.setJSDocInfo(declaration.getJSDocInfo());
          changed = true;
        }
        child = next;
      }
      if (changed) {
        compiler.reportChangeToEnclosingScope(declaration);
      }
      return;
    }

    // Add pre-declarations for all LHS variables that remain local to the defining chunk.
    // If we are optimizing local accesses (in a for-loop header), we only want to pre-declare
    // variables if the declaration will be converted to assignments/property accesses and removed
    // by RemoveGlobalVarCallback, to avoid redeclaration errors (e.g. "var a; let a;").
    if (!optimizeLocalAccess.shouldOptimize()
        || containsRescopedOrInitializedVars(declaration, isGlobalDeclaration)) {
      for (Node lhs : allLhsNodes) {
        String name = lhs.getString();
        if (globalVarStaysLocalInDefiningChunk(name, t)) {
          preDeclarations.add(
              new ChunkGlobal(input.getAstRoot(compiler), IR.name(name).srcref(lhs)));
        }
      }
    }

    // Convert all names with an rhs and all destructuring patterns to be assignments. e.g.
    //  VAR
    //    NAME foo
    //      NUMBER 3
    // becomes
    //  VAR
    //    ASSIGN
    //      NAME foo
    //      NUMBER 3
    for (Node child = declaration.getFirstChild(); child != null; ) {
      final Node next = child.getNext();
      if (child.isName() && child.hasChildren()) {
        Node assign = IR.assign(child.cloneNode(), child.removeFirstChild());
        child.replaceWith(assign);
        assign.setJSDocInfo(declaration.getJSDocInfo());
      } else if (child.isDestructuringLhs()) {
        if (child.hasOneChild()) {
          checkState(
              NodeUtil.isEnhancedFor(declaration.getParent()),
              "DESTRUCTURING_LHS should have two children: %s",
              declaration.toStringTree());
          // remove the DESTRUCTURING_LHS but leave the actual destructuring pattern
          child.replaceWith(child.removeFirstChild());
        } else {
          Node assign = IR.assign(child.removeFirstChild(), child.removeFirstChild());
          child.replaceWith(assign);
          assign.setJSDocInfo(declaration.getJSDocInfo());
        }
      }
      child = next;
    }
    compiler.reportChangeToEnclosingScope(declaration);
  }

  /**
   * Determines whether a variable declaration statement contains any variables that will be
   * rescoped (replaced with property accesses) or initialized (replaced with assignments or chained
   * namespace initializers). Assumes that optimizeLocalAccess is true.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>Rescoped: `var a;` where `a` is a cross-chunk name with a nonlocal write (becomes `_.a`),
   *       or `a` is a wrapped cross-chunk name (becomes `a._`).
   *   <li>Initialized: `var a = 1;` (has children) or `var [a] = [1];` (destructuring LHS).
   * </ul>
   *
   * <p>In a {@code for}-loop header, if a declaration contains any such variables, the declaration
   * keyword will be removed by {@code RemoveGlobalVarCallback} and any local variables in it must
   * be pre-declared. Outside a {@code for}-loop header, global {@code let}/{@code const}
   * declarations containing any such variables are converted to {@code var}.
   */
  private boolean containsRescopedOrInitializedVars(Node declaration, boolean isGlobalDeclaration) {
    for (Node child = declaration.getFirstChild(); child != null; child = child.getNext()) {
      if (isGlobalDeclaration
          && child.isName()
          && (symbolInfo.crossChunkNamesWithWriteFromOtherChunk().contains(child.getString())
              || isWrappedCrossChunkName(child.getString()))) {
        return true;
      }
      if (child.hasChildren() || child.isDestructuringLhs()) {
        return true;
      }
    }
    return false;
  }

  private void visitName(NodeTraversal t, Node n, Node parent) {
    String name = n.getString();

    // Ignore anonymous functions
    if (parent.isFunction() && name.isEmpty()) {
      return;
    }

    if (isExternVar(name, t)) {
      return;
    }

    // When the globalSymbolNamespace is used as a local variable name
    // add suffix to avoid shadowing the namespace. Also add a suffix
    // if a name starts with the name of the globalSymbolNamespace and
    // the suffix.
    Var var = t.getScope().getVar(name);
    renameHelper.maybeDisambiguateLocalVar(n, var);

    // We only care about global vars.
    if (!(var.isGlobal() && isCrossChunkName(name))) {
      return;
    }

    JSChunk currentChunk = t.getChunk();
    if (optimizeLocalAccess.shouldWrapReassignableSymbols()) {
      chunksUsingCrossChunkSymbols
          .computeIfAbsent(name, k -> new LinkedHashSet<>())
          .add(currentChunk);
    }

    if (isWrappedCrossChunkName(name)) {
      // Replace the symbol with an access to the wrapper.
      Node replacement = IR.getprop(IR.name(name), "_");
      replaceSymbol(n, name, replacement);
      return;
    }

    if (optimizeLocalAccess.shouldOptimize()
        && !symbolInfo.crossChunkNamesWithWriteFromOtherChunk().contains(name)) {
      JSChunk definingChunk = var.getInput().getChunk();
      if (currentChunk == definingChunk) {
        if (symbolInfo.globalNamesWithReadInDefiningChunk().contains(name)) {
          // If the cross-chunk variable is defined in this chunk, not re-assigned in any other
          // chunk, and not rewritten to wrapper access, then we skip the replacement and keep the
          // local name.
          if (NodeUtil.isLValueWrite(n)) {
            // Any assignment needs to be also set on the global namespace symbol.
            addGlobalNamespaceAlias(n, name);
          }
          // Early return to skip replacing the symbol.
          return;
        }
        // If the variable is only initialized in its defining chunk and never read there, replace
        // it directly with an access on the global namespace symbol instead of keeping an unused
        // local variable.
        Node replacement = IR.getprop(IR.name(symbolInfo.globalSymbolNamespace()), name);
        replaceSymbol(n, name, replacement);
        return;
      } else if (optimizeLocalAccess.shouldOptimizeAllChunks()) {
        // Variables defined in a different chunk are still safe for local aliasing if all of the
        // following conditions are met.
        // 1. The variable is not written to in any other chunk than in the defining chunk.
        // 2. The variable is not written to in an inner scope in the defining chunk, so all
        //     assignments are guaranteed to happen during the initial execution of the chunk.
        // 3. The current chunk depends on the defining chunk, so all writes are guaranteed to
        //     have occurred already when the current chunk is loaded.
        if (!symbolInfo.globalNamesWithInnerScopeWriteInDefiningChunk().contains(name)
            && compiler.getChunkGraph().dependsOn(currentChunk, definingChunk)) {
          localAliasesForUnwrappedCrossChunkNames
              .computeIfAbsent(currentChunk, k -> new LinkedHashSet<>())
              .add(name);
          // Early return to skip replacing the symbol.
          return;
        }
      }
    }

    if (optimizeLocalAccess.shouldWrapReassignableSymbols()) {
      throw new IllegalStateException(
          "Unexpected unaliased cross-chunk symbol under"
              + " ALL_CHUNKS_WITH_WRAPPED_REASSIGNABLE_SYMBOLS: "
              + name);
    }

    // Otherwise replace the symbol with an access on the global namespace symbol.
    Node replacement = IR.getprop(IR.name(symbolInfo.globalSymbolNamespace()), name);
    replaceSymbol(n, name, replacement);
  }

  /** Replaces a symbol with an access to the replacement. */
  private void replaceSymbol(Node node, String name, Node replacement) {
    Node parent = node.getParent();
    replacement.srcrefTree(node);
    node.replaceWith(replacement);
    compiler.reportChangeToEnclosingScope(replacement);
    if (parent.isCall() && !symbolInfo.maybeReferencesThis().contains(name)) {
      // Do not write calls like this: (0, _a)() but rather as _.a(). The
      // this inside the function will be wrong, but it doesn't matter
      // because the this is never read.
      parent.putBooleanProp(Node.FREE_CALL, false);
    }
    compiler.reportChangeToEnclosingScope(parent);
  }

  /**
   * Aliases the local variable's value to the global namespace, so that it can be accessed from
   * other chunks.
   *
   * <p>For assignments, variable declarations with initializers, and increments/decrements, this
   * rewrites the expression in place to avoid an extra statement (e.g. converts {@code a = x} to
   * {@code a = _.a = x}, {@code var a = x} to {@code var a = _.a = x}, {@code a += x} to {@code _.a
   * = a += x}, {@code ++a} to {@code _.a = ++a}, and {@code a++} to {@code (++_.a, a++)}). For
   * other writes (e.g. destructuring), it inserts a separate assignment statement after the current
   * one (e.g. appends {@code _.a = a;}).
   */
  private void addGlobalNamespaceAlias(Node n, String name) {
    Node stmt = NodeUtil.getEnclosingStatement(n);
    if (stmt == null) {
      return;
    }
    Node globalTarget = IR.getprop(IR.name(symbolInfo.globalSymbolNamespace()), name);
    Node parent = n.getParent();
    if (parent.isAssign() && parent.getFirstChild() == n) {
      // Optimized path for simple assignments (e.g. `a = x`).
      // We chain the assignment to the global namespace to reduce output size:
      // `a = _.a = x` instead of `a = x; _.a = a;`.
      Node rhs = parent.getSecondChild();
      Node globalNamespaceAssign = IR.assign(globalTarget, rhs.detach());
      globalNamespaceAssign.srcrefTree(n);
      parent.addChildToBack(globalNamespaceAssign);
      compiler.reportChangeToEnclosingScope(parent);
    } else if (NodeUtil.isNameDeclaration(parent)) {
      // Optimized path for variable declarations with initializers (e.g. `var a = x`).
      // We chain the assignment to the global namespace: `var a = _.a = x`.
      checkState(n.hasChildren(), "Expected initialized declaration for %s", n);
      Node rhs = n.removeFirstChild();
      Node globalNamespaceAssign = IR.assign(globalTarget, rhs);
      globalNamespaceAssign.srcrefTree(n);
      n.addChildToBack(globalNamespaceAssign);
      compiler.reportChangeToEnclosingScope(n);
    } else if (optimizeLocalAccess.shouldRewriteCompoundAndIncrementWritesInPlace()
        && NodeUtil.isCompoundAssignmentOp(parent)
        && parent.getFirstChild() == n) {
      // Compound assignments (e.g. `a += x`) evaluate to the updated value of the local variable,
      // so we can chain an assignment to the global namespace in place: `_.a = a += x`.
      Node placeholder = IR.empty();
      parent.replaceWith(placeholder);
      Node globalNamespaceAssign = IR.assign(globalTarget.srcrefTree(n), parent).srcref(n);
      placeholder.replaceWith(globalNamespaceAssign);
      compiler.reportChangeToEnclosingScope(globalNamespaceAssign);
    } else if (optimizeLocalAccess.shouldRewriteCompoundAndIncrementWritesInPlace()
        && (parent.isInc() || parent.isDec())) {
      if (!parent.getBooleanProp(Node.INCRDECR_PROP)) {
        // Prefix increments/decrements (e.g. `++a`) evaluate to the updated value of the local
        // variable, so we can chain an assignment to the global namespace in place: `_.a = ++a`.
        Node placeholder = IR.empty();
        parent.replaceWith(placeholder);
        Node globalNamespaceAssign = IR.assign(globalTarget.srcrefTree(n), parent).srcref(n);
        placeholder.replaceWith(globalNamespaceAssign);
        compiler.reportChangeToEnclosingScope(globalNamespaceAssign);
      } else {
        // Postfix increments/decrements (e.g. `a++`) evaluate to the pre-update value of the local
        // variable, so chaining `_.a = a++` would assign the old value to `_.a`. Instead, mirror
        // the increment/decrement on the global namespace target in a comma expression:
        // `(++_.a, a++)`.
        Node placeholder = IR.empty();
        parent.replaceWith(placeholder);
        Node globalUpdate =
            (parent.isInc() ? IR.inc(globalTarget, false) : IR.dec(globalTarget, false))
                .srcrefTree(n);
        Node comma = IR.comma(globalUpdate, parent).srcref(n);
        placeholder.replaceWith(comma);
        compiler.reportChangeToEnclosingScope(comma);
      }
    } else if (!optimizeLocalAccess.shouldRewriteCompoundAndIncrementWritesInPlace()
        || isSafeDestructuringWrite(n, stmt)) {
      // Fallback path for destructuring writes (e.g. `var [a] = x;` or `[a] = x;`), or for
      // compound assignments and increments/decrements when in-place rewriting is disabled.
      // In these cases, we cannot chain the assignment in place without changing semantics.
      // Instead, we append a separate assignment statement (`_.a = a;`).
      Node globalNamespaceAssign = IR.assign(globalTarget, IR.name(name));
      Node aliasStatement = IR.exprResult(globalNamespaceAssign);
      aliasStatement.srcrefTree(n);
      aliasStatement.insertAfter(stmt);
      compiler.reportChangeToEnclosingScope(stmt.getParent());
    } else {
      throw new IllegalStateException("Unexpected unsafe write to local alias: " + n);
    }
  }

  /**
   * Returns whether {@code n} is a destructuring write whose updated local variable can be safely
   * mirrored by inserting a separate assignment statement immediately after {@code stmt}.
   *
   * <p>Note that {@link #rewriteNameDeclaration} has already converted {@code DESTRUCTURING_LHS}
   * children of non-enhanced-for declarations into {@code ASSIGN} nodes before {@link #visitName}
   * is called.
   */
  private static boolean isSafeDestructuringWrite(Node n, Node stmt) {
    if (!NodeUtil.isLhsByDestructuring(n) || !NodeUtil.isStatementBlock(stmt.getParent())) {
      return false;
    }
    Node rootTarget = NodeUtil.getRootTarget(n);
    Node rootTargetParent = rootTarget.getParent();
    if (!rootTargetParent.isAssign() || rootTargetParent.getParent() != stmt) {
      return false;
    }
    return stmt.isExprResult() || NodeUtil.isNameDeclaration(stmt);
  }

  /**
   * Adds back declarations for variables that do not cross chunk boundaries, and declares local
   * aliases and wrappers. Must be called after RemoveGlobalVarCallback.
   */
  void addDeclarations() {
    declareChunkGlobals();
    if (optimizeLocalAccess.shouldOptimizeAllChunks()) {
      declareLocalAliasesAndWrappers();
    }
  }

  /**
   * Adds back declarations for variables that do not cross chunk boundaries. Must be called after
   * RemoveGlobalVarCallback.
   */
  private void declareChunkGlobals() {
    for (ChunkGlobal global : preDeclarations) {
      if (global.root.hasChildren() && global.root.getFirstChild().isVar()) {
        global.root.getFirstChild().addChildToBack(global.name);
      } else {
        global.root.addChildToFront(IR.var(global.name).srcref(global.name));
      }
      compiler.reportChangeToEnclosingScope(global.root);
    }
  }

  /**
   * Declares local aliases (e.g. `var {a} = _;` or `var a = _.a;`) and wrapper objects (e.g. `_.a =
   * {};` or `var a = _.a = {};`) at the beginning of chunks for cross-chunk variables that can be
   * safely accessed locally. Must be called after RemoveGlobalVarCallback. Only applies if
   * optimizeLocalAccess is an ALL_CHUNKS option.
   */
  private void declareLocalAliasesAndWrappers() {
    JSChunkGraph chunkGraph = compiler.getChunkGraph();
    Iterable<JSChunk> chunks = chunkGraph.getAllChunks();
    JSChunk defaultRootChunk = chunks.iterator().next();

    // Compute the chunk in which to define the wrapper for each wrapped symbol.
    Map<String, JSChunk> wrapperAssignmentChunks = new LinkedHashMap<>();
    if (optimizeLocalAccess.shouldWrapReassignableSymbols()
        && !symbolInfo.wrappedCrossChunkNames().isEmpty()) {
      for (String name : symbolInfo.wrappedCrossChunkNames()) {
        Set<JSChunk> usingChunks =
            chunksUsingCrossChunkSymbols.getOrDefault(name, ImmutableSet.of());
        JSChunk wrapperAssignmentChunk =
            usingChunks.isEmpty()
                ? defaultRootChunk
                : chunkGraph.getDeepestCommonDependencyInclusive(usingChunks);
        wrapperAssignmentChunks.put(name, wrapperAssignmentChunk);
      }
    }

    // Prepend local aliases and wrapper assignments to each chunk.
    String cleanupHelperName = getCleanupHelperPropertyName();
    boolean needsCleanupHelper = false;
    for (JSChunk chunk : chunkGraph.getAllChunks()) {
      if (declareLocalAliasesAndWrappersForChunk(
          chunk, wrapperAssignmentChunks, cleanupHelperName)) {
        needsCleanupHelper = true;
      }
    }
    if (needsCleanupHelper) {
      declareCleanupHelper(cleanupHelperName);
    }
  }

  private String getCleanupHelperPropertyName() {
    String name = "_";
    while (chunksUsingCrossChunkSymbols.containsKey(name)) {
      name += "$";
    }
    return name;
  }

  /**
   * Declares a helper function on the global namespace object in the root chunk to delete symbol
   * entries by comma-separated name list, e.g.:
   *
   * <pre>{@code
   * _._ = function(a) { for (let b of a.split(',')) { delete _[b]; } };
   * }</pre>
   */
  private void declareCleanupHelper(String cleanupHelperName) {
    Node script = compiler.getNodeForCodeInsertion(null);
    String globalSymbolNamespace = symbolInfo.globalSymbolNamespace();
    String paramName = globalSymbolNamespace.equals("a") ? "b" : "a";
    String loopVar = !paramName.equals("b") && !globalSymbolNamespace.equals("b") ? "b" : "c";
    Node splitCall = IR.call(IR.getprop(IR.name(paramName), "split"), IR.string(","));
    Node deleteStmt =
        IR.exprResult(IR.delprop(IR.getelem(IR.name(globalSymbolNamespace), IR.name(loopVar))));
    Node forOf = new Node(Token.FOR_OF, IR.let(IR.name(loopVar)), splitCall, IR.block(deleteStmt));
    Node helperFn = IR.function(IR.name(""), IR.paramList(IR.name(paramName)), IR.block(forOf));
    Node helperDecl =
        IR.exprResult(
            IR.assign(IR.getprop(IR.name(globalSymbolNamespace), cleanupHelperName), helperFn));
    addDeclaration(script, script.getFirstChild(), helperDecl);
    NodeUtil.markNewScopesChanged(helperFn, compiler);
    NodeUtil.addFeatureToScript(script, Feature.FOR_OF, compiler);
    NodeUtil.addFeatureToScript(script, Feature.LET_DECLARATIONS, compiler);
    compiler.reportChangeToEnclosingScope(script);
  }

  /**
   * Declares all local aliases and wrapper assignments that need to be prepended to the given
   * chunk. Returns true if the chunk emitted a call to the cleanup helper.
   */
  private boolean declareLocalAliasesAndWrappersForChunk(
      JSChunk chunk, Map<String, JSChunk> wrapperAssignmentChunks, String cleanupHelperName) {
    ImmutableList<CompilerInput> inputs = chunk.getInputs();
    if (inputs.isEmpty()) {
      return false;
    }
    Node script = inputs.get(0).getAstRoot(compiler);
    Node insertionPoint = script.getFirstChild();
    boolean changed = false;
    boolean usedCleanupHelper = false;

    Set<String> wrappedReassignableLocalAliases = ImmutableSet.of();
    if (optimizeLocalAccess.shouldWrapReassignableSymbols()) {
      wrappedReassignableLocalAliases = new LinkedHashSet<>();

      for (String name : symbolInfo.wrappedCrossChunkNames()) {
        JSChunk assignmentChunk = wrapperAssignmentChunks.get(name);
        Set<JSChunk> usingChunks = chunksUsingCrossChunkSymbols.get(name);
        if (chunk.equals(assignmentChunk)) {
          // Define the wrapper object for the wrapped symbol in this chunk.
          if (usingChunks != null && usingChunks.contains(chunk)) {
            // The symbol is used in this chunk, so we also declare a local alias:
            // `var name = _.name = {};`.
            addDeclaration(
                script,
                insertionPoint,
                IR.var(
                    IR.name(name),
                    IR.assign(
                        IR.getprop(IR.name(symbolInfo.globalSymbolNamespace()), name),
                        IR.objectlit())));
          } else {
            // The symbol is not used in this chunk, so we only define the wrapper object:
            // `_.name = {};`.
            addDeclaration(
                script,
                insertionPoint,
                IR.exprResult(
                    IR.assign(
                        IR.getprop(IR.name(symbolInfo.globalSymbolNamespace()), name),
                        IR.objectlit())));
          }
          changed = true;
        } else if (usingChunks != null && usingChunks.contains(chunk)) {
          // If the wrapper is defined in a different chunk, but the symbol is used in this
          // chunk, then only declare a local alias.
          wrappedReassignableLocalAliases.add(name);
        }
      }
    }

    // Combine all local aliases into a single destructuring assignment
    // (e.g. `var {a, b, c} = _;`) if output is ES2015+, otherwise declare
    // individual local aliases (e.g. `var a = _.a;`).
    Iterable<String> allLocalAliases =
        Iterables.concat(
            localAliasesForUnwrappedCrossChunkNames.getOrDefault(chunk, ImmutableSet.of()),
            wrappedReassignableLocalAliases);
    if (!Iterables.isEmpty(allLocalAliases)) {
      List<String> aliasesToKeep = new ArrayList<>();
      List<String> aliasesToDelete = new ArrayList<>();
      for (String name : allLocalAliases) {
        if (optimizeLocalAccess.shouldDeleteGlobalsOnLastAccess()
            && canDeleteFromGlobalNamespace(chunk, name)) {
          aliasesToDelete.add(name);
        } else {
          aliasesToKeep.add(name);
        }
      }
      // Place aliasesToDelete contiguously at the end of the destructuring assignment so that the
      // comma-separated symbol list in `_._('x,y')` is an exact substring immediately following
      // `var {..., x, y} = _;`, minimizing compressed binary size.
      Iterable<String> localAliases = Iterables.concat(aliasesToKeep, aliasesToDelete);
      FeatureSet outputFeatures = compiler.getOptions().getOutputFeatureSet();
      if (outputFeatures.contains(Feature.OBJECT_DESTRUCTURING)
          && outputFeatures.contains(Feature.FOR_OF)
          && outputFeatures.contains(Feature.LET_DECLARATIONS)) {
        Node objectPattern = IR.objectPattern();
        for (String name : localAliases) {
          Node stringKey = IR.stringKey(name, IR.name(name));
          stringKey.setShorthandProperty(true);
          objectPattern.addChildToBack(stringKey);
        }
        addDeclaration(
            script,
            insertionPoint,
            IR.var(objectPattern, IR.name(symbolInfo.globalSymbolNamespace())));
        NodeUtil.addFeatureToScript(script, Feature.OBJECT_DESTRUCTURING, compiler);
        if (!aliasesToDelete.isEmpty()) {
          Node cleanupCall =
              IR.exprResult(
                  IR.call(
                      IR.getprop(IR.name(symbolInfo.globalSymbolNamespace()), cleanupHelperName),
                      IR.string(Joiner.on(',').join(aliasesToDelete))));
          addDeclaration(script, insertionPoint, cleanupCall);
          usedCleanupHelper = true;
        }
      } else {
        for (String name : aliasesToKeep) {
          addDeclaration(
              script,
              insertionPoint,
              IR.var(IR.name(name), IR.getprop(IR.name(symbolInfo.globalSymbolNamespace()), name)));
        }
        for (String name : aliasesToDelete) {
          addDeclaration(
              script,
              insertionPoint,
              IR.var(IR.name(name), IR.getprop(IR.name(symbolInfo.globalSymbolNamespace()), name)));
          addDeclaration(
              script,
              insertionPoint,
              IR.exprResult(
                  IR.delprop(IR.getprop(IR.name(symbolInfo.globalSymbolNamespace()), name))));
        }
      }
      changed = true;
    }

    if (changed) {
      compiler.reportChangeToEnclosingScope(script);
    }
    return usedCleanupHelper;
  }

  /**
   * Returns true if {@code name} can be safely deleted from the global symbol namespace in {@code
   * currentChunk} right after its local alias is initialized, i.e. when all other chunks that
   * reference {@code name} are transitive dependencies (parent chunks) of {@code currentChunk}.
   */
  private boolean canDeleteFromGlobalNamespace(JSChunk currentChunk, String name) {
    if (compiler.getCodingConvention().isExported(name, /* local= */ false)) {
      return false;
    }
    Set<JSChunk> usingChunks = chunksUsingCrossChunkSymbols.get(name);
    if (usingChunks == null || !usingChunks.contains(currentChunk)) {
      return false;
    }
    JSChunkGraph chunkGraph = compiler.getChunkGraph();
    for (JSChunk otherChunk : usingChunks) {
      if (!otherChunk.equals(currentChunk) && !chunkGraph.dependsOn(currentChunk, otherChunk)) {
        return false;
      }
    }
    return true;
  }

  private static void addDeclaration(Node script, Node insertionPoint, Node decl) {
    decl.srcrefTree(script);
    if (insertionPoint == null) {
      script.addChildToBack(decl);
    } else {
      decl.insertBefore(insertionPoint);
    }
  }

  /** Variable that doesn't cross chunk boundaries. */
  private static class ChunkGlobal {
    final Node root;
    final Node name;

    ChunkGlobal(Node root, Node name) {
      this.root = root;
      this.name = name;
    }
  }
}
