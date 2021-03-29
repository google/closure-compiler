/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.HashSet;
import java.util.Set;

/**
 * Collect known properties on the global windows object to avoid creating global variable names
 * that conflict with these names. This pass runs after property renaming but before variable
 * renaming.
 *
 * <p>This is a best effort pass and does not guarantee that there are no conflicts.
 *
 * <p>This is not required if the global variables are isolated from global scope
 * (isolation_mode=IIFE) or equivalent.
 */
class GatherRawExports extends AbstractPostOrderCallback implements CompilerPass {

  private final AbstractCompiler compiler;

  // TODO(johnlenz): "goog$global" should be part of a coding convention.
  // Note: GatherRawExports runs after property renaming and
  // collapse properties, so the two entries here protect goog.global in the
  // two common cases "collapse properties and renaming on" or both off
  // but not the case where only property renaming is on.
  private static final String[] GLOBAL_THIS_NAMES = {
    "window",
    "globalThis",
    "top",
    "self",
    "goog$global",
    "goog.global",
    "$jscomp.global",
    "$jscomp$global"
  };

  private final Set<String> exportedVariables = new HashSet<>();

  GatherRawExports(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (NodeUtil.isNormalOrOptChainGet(n) && isGlobalThisObject(t, n.getFirstChild())) {
      if (NodeUtil.isNormalOrOptChainGetProp(n)) {
        exportedVariables.add(n.getString());
      } else if (NodeUtil.isNormalOrOptChainGet(n) && n.getSecondChild().isStringLit()) {
        exportedVariables.add(n.getSecondChild().getString());
      }
    }
  }

  private static boolean isGlobalThisObject(NodeTraversal t, Node n) {
    // We should consider using scopes or types to check for references to the "global this".
    if (n.isThis()) {
      return t.inGlobalHoistScope();
    } else if (n.isQualifiedName()) {
      int items = GLOBAL_THIS_NAMES.length;
      for (int i = 0; i < items; i++) {
        if (n.matchesQualifiedName(GLOBAL_THIS_NAMES[i])) {
          return true;
        }
      }
    }
    return false;
  }

  public Set<String> getExportedVariableNames() {
    return exportedVariables;
  }
}
