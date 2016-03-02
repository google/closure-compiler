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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

import java.util.HashSet;
import java.util.Set;

/**
 * External references of the form: "window['xx']" indicate names that must
 * be reserved when variable renaming to avoid conflicts.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class GatherRawExports extends AbstractPostOrderCallback
    implements CompilerPass {

  private final AbstractCompiler compiler;

  // TODO(johnlenz): "goog$global" should be part of a coding convention.
  // Note: GatherRawExports runs after property renaming and
  // collapse properties, so the two entries here protect goog.global in the
  // two common cases "collapse properties and renaming on" or both off
  // but not the case where only property renaming is on.
  private static final String[] GLOBAL_THIS_NAMES = {
    "window", "top", "goog$global", "goog.global" };

  private final Set<String> exportedVariables = new HashSet<>();

  GatherRawExports(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    Node sibling = n.getNext();
    if (sibling != null && sibling.isString() && NodeUtil.isGet(parent)
        && isGlobalThisObject(t, n)) {
      exportedVariables.add(sibling.getString());
    }
  }

  private static boolean isGlobalThisObject(NodeTraversal t, Node n) {
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
