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
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
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

  // Appended to variables names that conflict with globalSymbolNamespace.
  private static final String DISAMBIGUATION_SUFFIX = "$";

  static record SymbolInformation(
      ImmutableSet<String> crossChunkNames,
      ImmutableSet<String> crossChunkNamesWithWriteFromOtherChunk,
      ImmutableSet<String> globalNamesWithReadInDefiningChunk,
      ImmutableSet<String> globalNamesWithInnerScopeWriteInDefiningChunk,
      ImmutableSet<String> maybeReferencesThis) {

    SymbolInformation(
        Set<String> crossChunkNames,
        Set<String> crossChunkNamesWithWriteFromOtherChunk,
        Set<String> globalNamesWithReadInDefiningChunk,
        Set<String> globalNamesWithInnerScopeWriteInDefiningChunk,
        Set<String> maybeReferencesThis) {
      this(
          ImmutableSet.copyOf(crossChunkNames),
          ImmutableSet.copyOf(crossChunkNamesWithWriteFromOtherChunk),
          ImmutableSet.copyOf(globalNamesWithReadInDefiningChunk),
          ImmutableSet.copyOf(globalNamesWithInnerScopeWriteInDefiningChunk),
          ImmutableSet.copyOf(maybeReferencesThis));
    }
  }

  private final AbstractCompiler compiler;
  private final String globalSymbolNamespace;
  private final boolean assumeCrossChunkNames;
  private final CompilerOptions.OptimizeLocalAccess optimizeLocalAccess;
  private final ImmutableSet<String> externNames;
  private final SymbolInformation symbolInfo;

  private final List<ChunkGlobal> preDeclarations = new ArrayList<>();

  /**
   * Map from chunks to the set of global symbols to be aliased directly for that chunk (e.g. `var
   * {a} = _`). Only populated if optimizeLocalAccess is an ALL_CHUNKS option. These symbols are
   * used in that chunk, to avoid declaring unnecessary aliases. They can only be reassigned during
   * static execution of the defining chunk. If ALL_CHUNKS_WITH_WRAPPED_REASSIGNABLE_SYMBOLS is set,
   * then reassignable symbols that should be wrapped and aliased will be tracked separately in
   * wrappedReassignableCrossChunkNames.
   */
  private final Map<JSChunk, Set<String>> localAliasesForUnwrappedCrossChunkNames =
      new LinkedHashMap<>();

  /**
   * Map from global wrapped reassignable symbols to the chunks that use them. Only populated if
   * optimizeLocalAccess is ALL_CHUNKS_WITH_WRAPPED_REASSIGNABLE_SYMBOLS. This is used to determine
   * which chunks to define the wrapper objects in. These symbols are wrapped in holder objects in
   * that chunk (e.g. `_.a = {}`). Subsequent assignments look like `a._ = value`, where `a` is a
   * local alias for `_.a`). These symbols may be reassigned anywhere.
   */
  private final Map<String, Set<JSChunk>> chunksUsingWrappedReassignableSymbols =
      new LinkedHashMap<>();

  /**
   * Global symbols that are reassignable and will be wrapped in an object. This allows for constant
   * tracking and inlining by the JavaScript runtime, and enables safe aliasing in all chunks. Only
   * populated if optimizeLocalAccess is ALL_CHUNKS_WITH_WRAPPED_REASSIGNABLE_SYMBOLS.
   */
  private final ImmutableSet<String> wrappedReassignableCrossChunkNames;

  RescopeGlobalSymbolsRewriteCallback(
      AbstractCompiler compiler,
      String globalSymbolNamespace,
      boolean assumeCrossChunkNames,
      CompilerOptions.OptimizeLocalAccess optimizeLocalAccess,
      Node externs,
      SymbolInformation symbolInfo) {
    this.compiler = compiler;
    this.globalSymbolNamespace = globalSymbolNamespace;
    this.assumeCrossChunkNames = assumeCrossChunkNames;
    this.optimizeLocalAccess = optimizeLocalAccess;
    this.externNames = NodeUtil.collectExternVariableNames(compiler, externs);
    this.symbolInfo = symbolInfo;
    this.wrappedReassignableCrossChunkNames = collectWrappedReassignableCrossChunkNames();
  }

  private boolean isCrossChunkName(String name) {
    return assumeCrossChunkNames
        || symbolInfo.crossChunkNames().contains(name)
        || compiler.getCodingConvention().isExported(name, /* local= */ false);
  }

  private boolean isExternVar(String varname, NodeTraversal t) {
    if (varname.isEmpty()) {
      return false;
    }
    Var v = t.getScope().getVar(varname);
    return v == null || v.isExtern() || (v.getScope().isGlobal() && externNames.contains(varname));
  }

  private ImmutableSet<String> collectWrappedReassignableCrossChunkNames() {
    if (optimizeLocalAccess
        != CompilerOptions.OptimizeLocalAccess.ALL_CHUNKS_WITH_WRAPPED_REASSIGNABLE_SYMBOLS) {
      return ImmutableSet.of();
    }

    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    builder.addAll(symbolInfo.crossChunkNamesWithWriteFromOtherChunk());
    for (String name : symbolInfo.globalNamesWithInnerScopeWriteInDefiningChunk()) {
      if (isCrossChunkName(name)) {
        builder.add(name);
      }
    }
    return builder.build();
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
   * Partially rewrites a declaration as an assignment.
   *
   * <p>In the post traversal, all global, cross-chunk names and extern name references will become
   * property accesses. They will then be invalid as the lhs of a declaration, so we need to convert
   * them to assignments. We also convert any other names or destructuring patterns in the same
   * declaration to assignments and add an earlier declaration.
   */
  private void rewriteNameDeclaration(
      NodeTraversal t, Node declaration, List<Node> allLhsNodes, boolean isGlobalDeclaration) {
    CompilerInput input = t.getInput();

    // Add pre-declarations for all LHS variables that are neither global/cross-chunk names nor
    // externs.
    if (optimizeLocalAccess != CompilerOptions.OptimizeLocalAccess.DISABLED) {
      // If we are optimizing local accesses, we only want to pre-declare variables if the
      // declaration will be converted to assignments/property accesses and removed by
      // RemoveGlobalVarCallback. We don't want to add a new var declaration otherwise to avoid
      // redeclaration errors (e.g. "var a; let a;").
      if (containsRescopedOrInitializedVars(declaration, isGlobalDeclaration)) {
        for (Node lhs : allLhsNodes) {
          String name = lhs.getString();
          if (!symbolInfo.crossChunkNamesWithWriteFromOtherChunk().contains(name)
              && !wrappedReassignableCrossChunkNames.contains(name)
              && !isExternVar(name, t)
              && (!isCrossChunkName(name)
                  || symbolInfo.globalNamesWithReadInDefiningChunk().contains(name))) {
            preDeclarations.add(
                new ChunkGlobal(input.getAstRoot(compiler), IR.name(name).srcref(lhs)));
          }
        }
      }
    } else {
      for (Node lhs : allLhsNodes) {
        String name = lhs.getString();
        if (!(isGlobalDeclaration && isCrossChunkName(name)) && !isExternVar(name, t)) {
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
   * rescoped (replaced with property accesses) or initialized (replaced with assignments). Assumes
   * that optimizeLocalAccess is true.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>Rescoped: `var a;` where `a` is a cross-chunk name with a nonlocal write (becomes `_.a`),
   *       or `a` is a reassignable cross-chunk name (becomes `a._`).
   *   <li>Initialized: `var a = 1;` (has children) or `var [a] = [1];` (destructuring LHS).
   * </ul>
   *
   * <p>If a declaration contains any such variables, the statement will be removed by
   * RemoveGlobalVarCallback, and we must pre-declare any other variables in it.
   */
  private boolean containsRescopedOrInitializedVars(Node declaration, boolean isGlobalDeclaration) {
    for (Node child = declaration.getFirstChild(); child != null; child = child.getNext()) {
      if (isGlobalDeclaration
          && child.isName()
          && (symbolInfo.crossChunkNamesWithWriteFromOtherChunk().contains(child.getString())
              || wrappedReassignableCrossChunkNames.contains(child.getString()))) {
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
    if (!var.isGlobal()
        && (name.equals(globalSymbolNamespace)
            || name.startsWith(globalSymbolNamespace + DISAMBIGUATION_SUFFIX))) {
      n.setString(name + DISAMBIGUATION_SUFFIX);
      compiler.reportChangeToEnclosingScope(n);
    }

    // We only care about global vars.
    if (!(var.isGlobal() && isCrossChunkName(name))) {
      return;
    }

    JSChunk currentChunk = t.getChunk();

    if (optimizeLocalAccess != CompilerOptions.OptimizeLocalAccess.DISABLED) {
      JSChunk definingChunk = var.getInput().getChunk();
      if (currentChunk == definingChunk) {
        if (!symbolInfo.crossChunkNamesWithWriteFromOtherChunk().contains(name)
            && !wrappedReassignableCrossChunkNames.contains(name)
            && symbolInfo.globalNamesWithReadInDefiningChunk().contains(name)) {
          // If the cross-chunk variable is defined in this chunk, not re-assigned in any other
          // chunk, and not rewritten to wrapper access, then we skip the replacement and keep the
          // local name.
          if (NodeUtil.isLValue(n) && (!NodeUtil.isNameDeclaration(parent) || n.hasChildren())) {
            // Any assignment needs to be also set on the global namespace symbol.
            addGlobalNamespaceAlias(n, name);
          }
          // Early return to skip replacing the symbol.
          return;
        }
      } else if (optimizeLocalAccess == CompilerOptions.OptimizeLocalAccess.ALL_CHUNKS
          || optimizeLocalAccess
              == CompilerOptions.OptimizeLocalAccess.ALL_CHUNKS_WITH_WRAPPED_REASSIGNABLE_SYMBOLS) {
        // Variables defined in a different chunk are still safe for local aliasing if all of the
        // following conditions are met.
        // 1. The variable is not written to in any other chunk than in the defining chunk.
        // 2. The variable is not written to in an inner scope in the defining chunk, so all
        //     assignments are guaranteed to happen during the initial execution of the chunk.
        // 3. The current chunk depends on the defining chunk, so all writes are guaranteed to
        //     have occurred already when the current chunk is loaded.
        if (!symbolInfo.crossChunkNamesWithWriteFromOtherChunk().contains(name)
            && !symbolInfo.globalNamesWithInnerScopeWriteInDefiningChunk().contains(name)
            && compiler.getChunkGraph().dependsOn(currentChunk, definingChunk)) {
          localAliasesForUnwrappedCrossChunkNames
              .computeIfAbsent(currentChunk, k -> new LinkedHashSet<>())
              .add(name);
          // Early return to skip replacing the symbol.
          return;
        }
      }
    }

    if (wrappedReassignableCrossChunkNames.contains(name)) {
      // If the symbol is a wrapper for a reassignable symbol, record the chunk using the symbol
      // to ensure the wrapper is initialized before use, and replace the symbol with an access to
      // the wrapper.
      chunksUsingWrappedReassignableSymbols
          .computeIfAbsent(name, k -> new LinkedHashSet<>())
          .add(currentChunk);
      Node replacement = IR.getprop(IR.name(name), "_");
      replaceSymbol(n, name, replacement);
    } else {
      // Otherwise replace the symbol with an access on the global namespace symbol.
      Node replacement = IR.getprop(IR.name(globalSymbolNamespace), name);
      replaceSymbol(n, name, replacement);
    }
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
   * <p>For simple assignments, this chains the assignment to avoid an extra statement (e.g.
   * converts `a = x` to `a = _.a = x`). For other writes (e.g. compound assignments, destructuring,
   * increments), it inserts a separate assignment statement after the current one (e.g. appends
   * `_.a = a;`).
   */
  private void addGlobalNamespaceAlias(Node n, String name) {
    Node stmt = NodeUtil.getEnclosingStatement(n);
    if (stmt == null) {
      return;
    }
    Node parent = n.getParent();
    if (parent.isAssign() && parent.getFirstChild() == n) {
      // Optimized path for simple assignments (e.g. `a = x`).
      // We chain the assignment to the global namespace to reduce output size:
      // `a = _.a = x` instead of `a = x; _.a = a;`.
      Node rhs = parent.getSecondChild();
      Node globalNamespaceAssign =
          IR.assign(IR.getprop(IR.name(globalSymbolNamespace), name), rhs.detach());
      globalNamespaceAssign.srcrefTree(n);
      parent.addChildToBack(globalNamespaceAssign);
      compiler.reportChangeToEnclosingScope(parent);
    } else {
      // Fallback path for other writes (e.g. `a++`, `a += x`, destructuring).
      // In these cases, we cannot easily chain the assignment without changing semantics
      // or making the code overly complex. Instead, we append a separate assignment
      // statement (`_.a = a;`).
      Node globalNamespaceAssign =
          IR.assign(IR.getprop(IR.name(globalSymbolNamespace), name), IR.name(name));
      Node aliasStatement = IR.exprResult(globalNamespaceAssign);
      aliasStatement.srcrefTree(n);
      aliasStatement.insertAfter(stmt);
      compiler.reportChangeToEnclosingScope(stmt.getParent());
    }
  }

  /**
   * Adds back declarations for variables that do not cross chunk boundaries, and declares local
   * aliases and wrappers. Must be called after RemoveGlobalVarCallback.
   */
  void addDeclarations() {
    declareChunkGlobals();
    if (optimizeLocalAccess == CompilerOptions.OptimizeLocalAccess.ALL_CHUNKS
        || optimizeLocalAccess
            == CompilerOptions.OptimizeLocalAccess.ALL_CHUNKS_WITH_WRAPPED_REASSIGNABLE_SYMBOLS) {
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

    // Compute the chunk in which to define the wrapper for each wrapped reassignable symbol.
    Map<String, JSChunk> wrapperAssignmentChunks = new LinkedHashMap<>();
    if (!wrappedReassignableCrossChunkNames.isEmpty()) {
      for (String name : wrappedReassignableCrossChunkNames) {
        Set<JSChunk> usingChunks =
            chunksUsingWrappedReassignableSymbols.getOrDefault(name, ImmutableSet.of());
        JSChunk wrapperAssignmentChunk =
            usingChunks.isEmpty()
                ? defaultRootChunk
                : chunkGraph.getDeepestCommonDependencyInclusive(usingChunks);
        wrapperAssignmentChunks.put(name, wrapperAssignmentChunk);
      }
    }

    // Prepend local aliases and wrapper assignments to each chunk.
    for (JSChunk chunk : chunkGraph.getAllChunks()) {
      declareLocalAliasesAndWrappersForChunk(chunk, wrapperAssignmentChunks);
    }
  }

  /**
   * Declares all local aliases and wrapper assignments that need to be prepended to the given
   * chunk.
   */
  private void declareLocalAliasesAndWrappersForChunk(
      JSChunk chunk, Map<String, JSChunk> wrapperAssignmentChunks) {
    ImmutableList<CompilerInput> inputs = chunk.getInputs();
    if (inputs.isEmpty()) {
      return;
    }
    Node script = inputs.get(0).getAstRoot(compiler);
    Node insertionPoint = script.getFirstChild();
    boolean changed = false;

    Set<String> wrappedReassignableLocalAliases = ImmutableSet.of();
    if (optimizeLocalAccess
        == CompilerOptions.OptimizeLocalAccess.ALL_CHUNKS_WITH_WRAPPED_REASSIGNABLE_SYMBOLS) {
      wrappedReassignableLocalAliases = new LinkedHashSet<>();

      for (String name : wrappedReassignableCrossChunkNames) {
        JSChunk assignmentChunk = wrapperAssignmentChunks.get(name);
        Set<JSChunk> usingChunks = chunksUsingWrappedReassignableSymbols.get(name);
        if (chunk.equals(assignmentChunk)) {
          // Define the wrapper object for the reassignable symbol in this chunk.
          if (usingChunks != null && usingChunks.contains(chunk)) {
            // The symbol is used in this chunk, so we also declare a local alias:
            // `var name = _.name = {};`.
            addDeclaration(
                script,
                insertionPoint,
                IR.var(
                    IR.name(name),
                    IR.assign(IR.getprop(IR.name(globalSymbolNamespace), name), IR.objectlit())));
          } else {
            // The symbol is not used in this chunk, so we only define the wrapper object:
            // `_.name = {};`.
            addDeclaration(
                script,
                insertionPoint,
                IR.exprResult(
                    IR.assign(IR.getprop(IR.name(globalSymbolNamespace), name), IR.objectlit())));
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
    Iterable<String> localAliases =
        Iterables.concat(
            localAliasesForUnwrappedCrossChunkNames.getOrDefault(chunk, ImmutableSet.of()),
            wrappedReassignableLocalAliases);
    if (!Iterables.isEmpty(localAliases)) {
      if (compiler.getOptions().getOutputFeatureSet().contains(Feature.OBJECT_DESTRUCTURING)) {
        Node objectPattern = IR.objectPattern();
        for (String name : localAliases) {
          Node stringKey = IR.stringKey(name, IR.name(name));
          stringKey.setShorthandProperty(true);
          objectPattern.addChildToBack(stringKey);
        }
        addDeclaration(
            script, insertionPoint, IR.var(objectPattern, IR.name(globalSymbolNamespace)));
        NodeUtil.addFeatureToScript(script, Feature.OBJECT_DESTRUCTURING, compiler);
      } else {
        for (String name : localAliases) {
          addDeclaration(
              script,
              insertionPoint,
              IR.var(IR.name(name), IR.getprop(IR.name(globalSymbolNamespace), name)));
        }
      }
      changed = true;
    }

    if (changed) {
      compiler.reportChangeToEnclosingScope(script);
    }
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
