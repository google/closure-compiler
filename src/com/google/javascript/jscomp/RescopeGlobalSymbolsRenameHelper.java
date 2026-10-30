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

import com.google.javascript.rhino.Node;

/**
 * Helper class for disambiguating variable names in {@link RescopeGlobalSymbols} and {@link
 * RescopeGlobalSymbolsRewriteCallback}.
 *
 * <p>Non-global variables whose names match {@code globalSymbolNamespace} (e.g. {@code _}) or start
 * with {@code globalSymbolNamespace + "$"} (e.g. {@code _$}) have {@link #DISAMBIGUATION_SUFFIX}
 * ({@code $}) appended so they do not shadow the global symbol namespace parameter.
 */
final class RescopeGlobalSymbolsRenameHelper {

  /** Appended to non-global variable names that conflict with {@code globalSymbolNamespace}. */
  private static final String DISAMBIGUATION_SUFFIX = "$";

  private final AbstractCompiler compiler;
  private final String globalSymbolNamespace;

  RescopeGlobalSymbolsRenameHelper(AbstractCompiler compiler, String globalSymbolNamespace) {
    this.compiler = compiler;
    this.globalSymbolNamespace = globalSymbolNamespace;
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
}
