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

import com.google.common.collect.Table;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/** Renames references in code. */
final class Es6RenameReferences extends AbstractPostOrderCallback {

  /**
   * Map from the root node of a scope + the original variable name to a new name for the variable.
   *
   * <p>An entry in this map means we should rename a variable that was originally declared in the
   * scope with the given root and had the original variable name to the new name.
   */
  private final Table<Node, String, String> renameTable;

  Es6RenameReferences(Table<Node, String, String> renameTable) {
    this.renameTable = renameTable;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (NodeUtil.isReferenceName(n)) {
      renameReference(t, n, n.getString());
    }
  }

  /**
   * @param t The NodeTraversal.
   * @param oldName The root name of a qualified name.
   */
  private void renameReference(NodeTraversal t, Node n, String oldName) {
    Scope current = t.getScope();
    // You should be wondering:
    //
    // Why are we searching up the stack of scopes here instead of just looking up the Var for
    // oldName and getting the scope from that?
    //
    // The answer is:
    //
    // When Es6RewriteBlockScopedDeclaration uses this class, it has already modified the scopes so
    // that `oldName` is no longer declared in its original scope.
    //
    // I don't know if the other users of this class are also modifying the scopes in advance.
    // - bradfordcsmith@google.com
    while (current != null) {
      String newName = renameTable.get(current.getRootNode(), oldName);
      if (newName != null) {
        n.setString(newName);
        t.reportCodeChange();
        return;
      } else if (current.hasOwnSlot(oldName)) {
        return;
      } else {
        current = current.getParent();
      }
    }
  }
}
