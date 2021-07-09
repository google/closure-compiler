/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Filters warnings based on in-code {@code @suppress} annotations.
 *
 * <p>Works by looking at the AST node associated with the warning, and looking at parents of the
 * node until it finds a node declaring a symbol (class, function, variable, property, assignment,
 * object literal key) or a script. For this reason, it doesn't work for warnings without an
 * associated AST node, eg, the ones in parsing/IRFactory. They can be turned off with jscomp_off.
 */
class SuppressDocWarningsGuard extends WarningsGuard {
  private static final long serialVersionUID = 1L;

  /** Warnings guards for each suppressible warnings group, indexed by name. */
  private final ImmutableMap<String, DiagnosticGroup> suppressors;

  private final AbstractCompiler compiler;

  /** The suppressible groups, indexed by name. */
  SuppressDocWarningsGuard(AbstractCompiler compiler, Map<String, DiagnosticGroup> suppressors) {
    this.compiler = compiler;
    this.suppressors = createSuppressors(suppressors);
  }

  private static ImmutableMap<String, DiagnosticGroup> createSuppressors(
      Map<String, DiagnosticGroup> suppressors) {
    LinkedHashMap<String, DiagnosticGroup> builder = new LinkedHashMap<>(suppressors);

    // Hack: Allow "@suppress {missingProperties}" to mean
    // "@suppress {strictmissingProperties}".
    // TODO(johnlenz): Delete this when it is enabled with missingProperties
    builder.put(
        "missingProperties",
        new DiagnosticGroup(
            DiagnosticGroups.MISSING_PROPERTIES, DiagnosticGroups.STRICT_MISSING_PROPERTIES));

    // Hack: Allow "@suppress {checkTypes}" to include
    // "strictmissingProperties".
    // TODO(johnlenz): Delete this when it is enabled with missingProperties
    builder.put(
        "checkTypes",
        new DiagnosticGroup(DiagnosticGroups.CHECK_TYPES, DiagnosticGroups.STRICT_CHECK_TYPES));

    return ImmutableMap.copyOf(builder);
  }

  @Override
  public CheckLevel level(JSError error) {
    Node node = error.getNode();
    if (node == null) {
      node = getScriptNodeBySourceName(error);
    }
    if (node == null) {
      return null;
    }

    CheckLevel level = getCheckLevelFromAncestors(error, node);
    if (level != null) {
      return level;
    }

    // Some errors are on nodes that do not have the script as a parent.
    // Look up the script node by filename.
    Node scriptNode = getScriptNodeBySourceName(error);
    if (scriptNode != null) {
      JSDocInfo info = scriptNode.getJSDocInfo();
      if (info != null) {
        return getCheckLevelFromInfo(error, info);
      }
    }

    return null;
  }

  /**
   * Searches for @suppress tags on nodes introducing symbols:
   *
   * <p>class & function declarations, variables, assignments, object literal keys, and the top
   * level script node.
   */
  private CheckLevel getCheckLevelFromAncestors(JSError error, Node node) {
    for (Node current = node; current != null; current = current.getParent()) {
      JSDocInfo info = null;
      if (current.isFunction() || current.isClass()) {
        info = NodeUtil.getBestJSDocInfo(current);
      } else if (current.isScript()) {
        info = current.getJSDocInfo();
      } else if (NodeUtil.isNameDeclaration(current)
          || NodeUtil.mayBeObjectLitKey(current)
          || current.isComputedProp()
          || current.isMemberFieldDef()
          || current.isComputedFieldDef()
          || ((NodeUtil.isAssignmentOp(current) || current.isGetProp())
              && current.hasParent()
              && current.getParent().isExprResult())) {
        info = NodeUtil.getBestJSDocInfo(current);
      }

      if (info != null) {
        CheckLevel level = getCheckLevelFromInfo(error, info);
        if (level != null) {
          return level;
        }
      }
    }

    return null;
  }

  /** If the given JSDocInfo has an @suppress for the given JSError, returns the new level. */
  private CheckLevel getCheckLevelFromInfo(JSError error, JSDocInfo info) {
    for (String suppressor : info.getSuppressions()) {
      DiagnosticGroup group = this.suppressors.get(suppressor);
      if (group == null) {
        continue; // Some @suppress tags are for other tools, and may not have a group.
      }

      if (group.matches(error)) {
        return CheckLevel.OFF;
      }
    }

    return null;
  }

  @Nullable
  private final Node getScriptNodeBySourceName(JSError error) {
    if (error.getSourceName() == null) {
      return null;
    }

    Node scriptNode = this.compiler.getScriptNode(error.getSourceName());
    if (scriptNode == null) {
      return null;
    }

    checkState(scriptNode.isScript());
    return scriptNode;
  }

  @Override
  public int getPriority() {
    // Happens after path-based filtering, but before other times
    // of filtering.
    return WarningsGuard.Priority.SUPPRESS_DOC.value;
  }
}
