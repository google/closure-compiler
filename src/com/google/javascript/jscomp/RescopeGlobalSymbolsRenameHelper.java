/*
 * Copyright 2026 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class for disambiguating variable names in {@link RescopeGlobalSymbols} and {@link
 * RescopeGlobalSymbolsRewriteCallback}.
 *
 * <p>Consolidates two variable-renaming mechanisms:
 *
 * <ol>
 *   <li><b>Namespace-conflict disambiguation ({@code $} suffix):</b> Non-global variables whose
 *       names match {@code globalSymbolNamespace} (e.g. {@code _}) or start with {@code
 *       globalSymbolNamespace + "$"} (e.g. {@code _$}) have {@link #DISAMBIGUATION_SUFFIX} ({@code
 *       $}) appended so they do not shadow the global symbol namespace parameter.
 *   <li><b>Defining-chunk unwrapped local variable disambiguation ({@code _} prefix):</b> When
 *       {@link CompilerOptions.OptimizeLocalAccess#shouldKeepLocalAliasForWrappedReassignables()}
 *       is enabled, wrapped cross-chunk symbols that remain unwrapped local variables in their
 *       defining chunk are renamed by prepending {@link #UNWRAPPED_LOCAL_NAME_PREFIX} ({@code _},
 *       e.g. {@code _a}) so they do not collide with local wrapper aliases ({@code var {a} = _;})
 *       when multiple chunks are concatenated into the same function scope. If {@code _a} is
 *       already used (including by post-{@code $}-disambiguation local variables), additional
 *       {@code _} prefixes are prepended until a free identifier is found.
 * </ol>
 */
final class RescopeGlobalSymbolsRenameHelper {

  /** Appended to non-global variable names that conflict with {@code globalSymbolNamespace}. */
  private static final String DISAMBIGUATION_SUFFIX = "$";

  /**
   * Prepended to unwrapped local variable names in the defining chunk for wrapped reassignable
   * symbols to avoid collisions with local wrapper aliases across concatenated chunks.
   */
  private static final String UNWRAPPED_LOCAL_NAME_PREFIX = "_";

  private final AbstractCompiler compiler;
  private final String globalSymbolNamespace;

  /**
   * Map from global wrapped symbols that are kept as unwrapped local variables in their defining
   * chunk to their disambiguated local variable name (e.g. {@code _a}), avoiding collisions with
   * local wrapper aliases ({@code var {a} = _;}) when multiple chunks are concatenated into the
   * same function scope.
   */
  private final ImmutableMap<String, String> unwrappedLocalNamesInDefiningChunk;

  private RescopeGlobalSymbolsRenameHelper(
      AbstractCompiler compiler,
      String globalSymbolNamespace,
      ImmutableMap<String, String> unwrappedLocalNamesInDefiningChunk) {
    this.compiler = compiler;
    this.globalSymbolNamespace = globalSymbolNamespace;
    this.unwrappedLocalNamesInDefiningChunk = unwrappedLocalNamesInDefiningChunk;
  }

  /**
   * Builder for {@link RescopeGlobalSymbolsRenameHelper} that records identifiers encountered
   * during the pre-traversal and collects the disambiguated unwrapped local variable names in the
   * defining chunk when {@link #build} is called.
   */
  static final class HelperBuilder {
    private final AbstractCompiler compiler;
    private final String globalSymbolNamespace;
    private final CompilerOptions.OptimizeLocalAccess optimizeLocalAccess;

    /**
     * Identifiers starting with {@code _} (including post-disambiguation names for local variables
     * conflicting with {@code globalSymbolNamespace}) used to avoid collisions when naming
     * unwrapped local variables in the defining chunk.
     */
    private final Set<String> namesStartingWithUnderscore = new LinkedHashSet<>();

    HelperBuilder(
        AbstractCompiler compiler,
        String globalSymbolNamespace,
        CompilerOptions.OptimizeLocalAccess optimizeLocalAccess) {
      this.compiler = compiler;
      this.globalSymbolNamespace = globalSymbolNamespace;
      this.optimizeLocalAccess = optimizeLocalAccess;
    }

    /**
     * Records an identifier seen during the pre-traversal so that unwrapped local variable names in
     * the defining chunk (which start with {@link #UNWRAPPED_LOCAL_NAME_PREFIX}) do not collide
     * with existing identifiers or with local variables after {@link #DISAMBIGUATION_SUFFIX}
     * renaming.
     */
    void recordExistingName(String name) {
      if (!optimizeLocalAccess.shouldKeepLocalAliasForWrappedReassignables()) {
        return;
      }
      if (name.startsWith(UNWRAPPED_LOCAL_NAME_PREFIX)) {
        namesStartingWithUnderscore.add(name);
      }
      // Non-global variables matching `globalSymbolNamespace` (e.g. `_`) or starting with
      // `globalSymbolNamespace + "$"` (e.g. `_$`) are renamed by appending `$` in
      // maybeDisambiguateLocalVar so they do not shadow the namespace parameter. Record that
      // post-disambiguation name (e.g. `_$`) so that an unwrapped local variable in the defining
      // chunk for a global symbol like `$` (which would otherwise be named `_$`) does not collide
      // with or get shadowed by the disambiguated local variable.
      if (conflictsWithGlobalSymbolNamespace(name, globalSymbolNamespace)) {
        String disambiguatedName = getDisambiguatedLocalName(name);
        if (disambiguatedName.startsWith(UNWRAPPED_LOCAL_NAME_PREFIX)) {
          namesStartingWithUnderscore.add(disambiguatedName);
        }
      }
    }

    /**
     * Collects the disambiguated local variable names in the defining chunk from all recorded names
     * and creates the {@link RescopeGlobalSymbolsRenameHelper}.
     */
    RescopeGlobalSymbolsRenameHelper build(RescopeGlobalSymbolsInfo symbolInfo) {
      ImmutableMap<String, String> unwrappedLocalNamesInDefiningChunk =
          collectUnwrappedLocalNamesInDefiningChunk(
              optimizeLocalAccess, symbolInfo, namesStartingWithUnderscore);
      return new RescopeGlobalSymbolsRenameHelper(
          compiler, globalSymbolNamespace, unwrappedLocalNamesInDefiningChunk);
    }
  }

  /**
   * Returns whether a non-global variable name conflicts with {@code globalSymbolNamespace} (either
   * matching {@code globalSymbolNamespace} or starting with {@code globalSymbolNamespace + "$"}).
   */
  private static boolean conflictsWithGlobalSymbolNamespace(
      String name, String globalSymbolNamespace) {
    return name.equals(globalSymbolNamespace)
        || name.startsWith(globalSymbolNamespace + DISAMBIGUATION_SUFFIX);
  }

  /**
   * Returns the disambiguated name for a non-global variable that conflicts with {@code
   * globalSymbolNamespace}.
   */
  private static String getDisambiguatedLocalName(String name) {
    return name + DISAMBIGUATION_SUFFIX;
  }

  /**
   * Collects disambiguated local variable names (e.g. {@code _a}) for wrapped cross-chunk symbols
   * that remain unwrapped local variables in their defining chunk (because they are read in their
   * defining chunk, have no unsafe writes in their defining chunk, and are never written to from
   * another chunk).
   *
   * <p>Prepending {@code _} avoids collisions with local wrapper aliases ({@code var {a} = _;})
   * declared in other chunks when multiple chunks are concatenated into the same function scope. If
   * {@code _a} is already in use (including post-disambiguation names for local variables that
   * conflict with {@code globalSymbolNamespace}), additional {@code _} prefixes are prepended until
   * a free identifier is found.
   */
  private static ImmutableMap<String, String> collectUnwrappedLocalNamesInDefiningChunk(
      CompilerOptions.OptimizeLocalAccess optimizeLocalAccess,
      RescopeGlobalSymbolsInfo symbolInfo,
      Set<String> namesStartingWithUnderscore) {
    if (!optimizeLocalAccess.shouldKeepLocalAliasForWrappedReassignables()) {
      return ImmutableMap.of();
    }

    Set<String> usedNames = new LinkedHashSet<>(namesStartingWithUnderscore);
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (String name : symbolInfo.wrappedCrossChunkNames()) {
      if (!symbolInfo.crossChunkNamesWithWriteFromOtherChunk().contains(name)
          && !symbolInfo.globalNamesWithUnsafeWriteForLocalAliasInDefiningChunk().contains(name)
          && symbolInfo.globalNamesWithReadInDefiningChunk().contains(name)) {
        String unwrappedName = UNWRAPPED_LOCAL_NAME_PREFIX + name;
        while (usedNames.contains(unwrappedName)
            || symbolInfo.externNames().contains(unwrappedName)
            || unwrappedName.equals(symbolInfo.globalSymbolNamespace())) {
          unwrappedName = UNWRAPPED_LOCAL_NAME_PREFIX + unwrappedName;
        }
        usedNames.add(unwrappedName);
        builder.put(name, unwrappedName);
      }
    }
    return builder.buildOrThrow();
  }

  /**
   * Returns whether the given wrapped cross-chunk symbol is kept as a disambiguated unwrapped local
   * variable in its defining chunk.
   */
  boolean hasUnwrappedLocalNameInDefiningChunk(String name) {
    return unwrappedLocalNamesInDefiningChunk.containsKey(name);
  }

  /**
   * Returns the local variable name to use in the defining chunk for {@code name} (returning the
   * disambiguated name such as {@code _a} if {@code isGlobalDeclaration} is true and {@code name}
   * is an unwrapped wrapped symbol, or {@code name} otherwise).
   */
  String getLocalNameInDefiningChunk(String name, boolean isGlobalDeclaration) {
    return isGlobalDeclaration ? unwrappedLocalNamesInDefiningChunk.getOrDefault(name, name) : name;
  }

  /**
   * When {@code globalSymbolNamespace} is used as a local variable name, appends {@link
   * #DISAMBIGUATION_SUFFIX} ({@code $}) to {@code n} to avoid shadowing the namespace (and likewise
   * if the local variable name starts with {@code globalSymbolNamespace + "$"}).
   */
  void maybeDisambiguateLocalVar(Node n, Var var) {
    String name = n.getString();
    if (!var.isGlobal() && conflictsWithGlobalSymbolNamespace(name, globalSymbolNamespace)) {
      n.setString(getDisambiguatedLocalName(name));
      compiler.reportChangeToEnclosingScope(n);
    }
  }

  /**
   * Renames a global variable {@code n} in its defining chunk to its disambiguated unwrapped local
   * variable name (e.g. {@code _a}) if one was assigned, and returns the local name in the defining
   * chunk.
   */
  String maybeRenameGlobalInDefiningChunk(Node n) {
    String name = n.getString();
    String localName = unwrappedLocalNamesInDefiningChunk.getOrDefault(name, name);
    if (!localName.equals(name)) {
      n.setString(localName);
      compiler.reportChangeToEnclosingScope(n);
    }
    return localName;
  }
}
