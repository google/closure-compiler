/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.collect.Table;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import org.jspecify.nullness.Nullable;

/** Renames references in JSDoc. */
final class Es6RenameTypeReferences extends AbstractPostOrderCallback {

  /**
   * Map from the root node of a scope + the original variable name to a new name for the variable.
   *
   * <p>An entry in this map means we should rename a variable that was originally declared in the
   * scope with the given root and had the original variable name to the new name.
   */
  private final Table<Node, String, String> renameTable;

  Es6RenameTypeReferences(Table<Node, String, String> renameTable) {
    this.renameTable = renameTable;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // Remove this branch after module rewriting is always done
    // after type checking.
    JSDocInfo info = n.getJSDocInfo();
    if (info != null) {
      for (Node root : info.getTypeNodes()) {
        renameTypeNodeRecursive(t, root);
      }
    }
  }

  private void renameTypeNodeRecursive(NodeTraversal t, Node n) {
    if (n.isStringLit()) {
      renameTypeReference(t, n);
    }

    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      renameTypeNodeRecursive(t, child);
    }
  }

  private void renameTypeReference(NodeTraversal t, Node n) {
    checkState(n.isStringLit());
    String fullName = n.getString();
    String rootName;
    String rest;
    int endPos = fullName.indexOf('.');
    if (endPos == -1) {
      rootName = fullName;
      rest = null;
    } else {
      rootName = fullName.substring(0, endPos);
      rest = fullName.substring(endPos);
    }
    renameReference(t, n, rootName, rest);
  }

  /**
   * @param t The NodeTraversal.
   * @param oldName The root name of a qualified name.
   * @param rest The rest of the qualified name or null.
   */
  private void renameReference(NodeTraversal t, Node n, String oldName, @Nullable String rest) {
    Scope current = t.getScope();

    // You should be wondering:
    //
    // Why are we searching up the stack of scopes here instead of just looking up the Var for
    // oldName and getting the scope from that?
    //
    // The answer is:  The Var no longer exists.
    //
    // Es6RewriteModules (the only client for this class) actually deletes the module-level
    // declaration for the old name but still uses the scope rooted at the MODULE_BODY (for
    // goog.module() files), SCRIPT, or function body (for wrapped goog.loadModule() goog.module()s)
    // in the rename map.
    //
    // If we're in some local scope where there's a shadowing local variable, we don't want to do
    // the renaming.
    //
    // Otherwise, we need to use the MODULE_BODY or SCRIPT or goog.loadModule body scope to look up
    // the new name.
    // - bradfordcsmith@google.com
    while (current != null) {
      Node currentRootNode = current.getRootNode();
      String newName = renameTable.get(currentRootNode, oldName);
      if (newName != null) {
        checkState(
            currentRootNode.isModuleBody()
                || currentRootNode.isScript()
                || NodeUtil.isFunctionBlock(currentRootNode),
            "Not a MODULE_BODY or SCRIPT or goog.loadModule root: %s",
            currentRootNode);
        String newFullName = rest == null ? newName : newName + rest;
        n.setString(newFullName);
        return;
      } else if (current.hasOwnSlot(oldName)) {
        // This is a reference to some local variable whose name shadows the module-scope variable
        // we actually want to rename.
        return;
      } else {
        current = current.getParent();
      }
    }
  }
}
