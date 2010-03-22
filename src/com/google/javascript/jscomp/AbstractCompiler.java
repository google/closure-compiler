/*
 * Copyright 2009 Google Inc.
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

import com.google.common.base.Supplier;
import com.google.javascript.jscomp.mozilla.rhino.ErrorReporter;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

/**
 * An abstract compiler, to help remove the circular dependency of
 * passes on JSCompiler.
 *
 * This is an abstract class, so that we can make the methods package-private.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public abstract class AbstractCompiler implements SourceExcerptProvider {
  static final DiagnosticType READ_ERROR = DiagnosticType.error(
      "JSC_READ_ERROR", "Cannot read: {0}");

  // TODO(nicksantos): Decide if all of these are really necessary.
  // Many of them are just accessors that should be passed to the
  // CompilerPass's constructor.

  /**
   * Looks up an input (possibly an externs input) by name. May return null.
   */
  public abstract CompilerInput getInput(String sourceName);

  /**
   * Creates a new externs file.
   * @param name A name for the new externs file.
   * @throws IllegalArgumentException If the name of the externs file conflicts
   *     with a pre-existing externs file.
   */
  abstract CompilerInput newExternInput(String name);

  /**
   * Gets the module graph. May return null if there are no modules.
   */
  abstract JSModuleGraph getModuleGraph();

  /**
   * Gets a central registry of type information from the compiled JS.
   */
  public abstract JSTypeRegistry getTypeRegistry();

  /**
   * Gets a memoized scope creator.
   */
  abstract ScopeCreator getScopeCreator();

  /**
   * Gets the top scope.
   */
  public abstract Scope getTopScope();

  /**
   * Report an error or warning.
   */
  public abstract void report(JSError error);

  /**
   * Report an internal error.
   */
  abstract void throwInternalError(String msg, Exception cause);

  /**
   * Gets the current coding convention.
   */
  public abstract CodingConvention getCodingConvention();

  /**
   * Report code changes.
   */
  public abstract void reportCodeChange();

  /**
   * Logs a message under a central logger.
   */
  abstract void addToDebugLog(String message);

  /**
   * Sets the CssRenamingMap.
   */
  abstract void setCssRenamingMap(CssRenamingMap map);

  /**
   * Gets the CssRenamingMap.
   */
  abstract CssRenamingMap getCssRenamingMap();

  /**
   * Gets a suitable SCRIPT node to serve as a parent for code insertion. If
   * {@code module} contains any inputs, the returned node will be the SCRIPT
   * node corresponding to its first input. If {@code module} is empty, on the
   * other hand, then the returned node will be the first SCRIPT node in a
   * non-empty module that {@code module} depends on (the deepest one possible).
   *
   * @param module A module. If null, will return the first SCRIPT node of all
   *     modules.
   * @return A SCRIPT node (never null).
   */
  abstract Node getNodeForCodeInsertion(JSModule module);

  /**
   * Gets the central registry of type violations.
   */
  abstract TypeValidator getTypeValidator();

  /**
   * Parses code for injecting.
   */
  abstract Node parseSyntheticCode(String code);

  /**
   * Parses code for injecting, and associate it with a given source file.
   */
  abstract Node parseSyntheticCode(String filename, String code);

  /**
   * Prints a node to source code.
   */
  abstract String toSource(Node root);

  /**
   * Gets a default error reporter for injecting into Rhino.
   */
  abstract ErrorReporter getDefaultErrorReporter();

  /**
   * Get an interpreter for type analysis.
   */
  public abstract ReverseAbstractInterpreter getReverseAbstractInterpreter();

  /**
   * @return Whether the normalization pass has been run.
   */
  abstract boolean isNormalized();

  /**
   * Generates unique ids.
   */
  abstract Supplier<String> getUniqueNameIdSupplier();

  /**
   * @return Whether any errors have been encountered that
   *     should stop the compilation process.
   */
  abstract boolean hasHaltingErrors();

  /**
   * Register a listener for code change events.
   */
  abstract void addChangeHandler(CodeChangeHandler handler);

  /**
   * Remove a listener for code change events.
   */
  abstract void removeChangeHandler(CodeChangeHandler handler);

  /**
   * Returns true if compiling in IDE mode.
   */
  abstract boolean isIdeMode();

  /**
   * Returns the parser configuration.
   */
  abstract Config getParserConfig();

  /**
   * Returns true if type checking is enabled.
   */
  abstract boolean isTypeCheckingEnabled();

  /**
   * Normalizes the types of AST nodes in the given tree, and
   * annotates any nodes to which the coding convention applies so that passes
   * can read the annotations instead of using the coding convention.
   */
  abstract void prepareAst(Node root);

  /**
   * Acquires the symbol table.
   */
  abstract SymbolTable acquireSymbolTable();

  /**
   * Gets the error manager.
   */
  abstract public ErrorManager getErrorManager();

  /**
   * Set if the normalization pass has been done.
   * Note: non-private to enable test cases that require the Normalize pass.
   */
  abstract void setNormalized();

  /**
   * Set once unnormalizing passes have been start.
   * Note: non-private to enable test cases that require the Normalize pass.
   */
  abstract void setUnnormalized();

  /**
   * Are the nodes equal for the purpose of inlining?
   * If type aware optimizations are on, type equality is checked.
   */
  abstract boolean areNodesEqualForInlining(Node n1, Node n2);
}
