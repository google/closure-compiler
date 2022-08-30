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

/** Renames references in code and JSDoc when necessary. */
final class Es6RenameReferences extends AbstractPostOrderCallback {

  private final Table<Node, String, String> renameTable;
  private final boolean typesOnly;

  Es6RenameReferences(Table<Node, String, String> renameTable, boolean typesOnly) {
    this.renameTable = renameTable;
    this.typesOnly = typesOnly;
  }

  Es6RenameReferences(Table<Node, String, String> renameTable) {
    this(renameTable, /* typesOnly= */ false);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!typesOnly && NodeUtil.isReferenceName(n)) {
      renameNameReference(t, n);
    }

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

  private void renameNameReference(NodeTraversal t, Node n) {
    checkState(n.isName());
    String oldName = n.getString();
    renameReference(t, n, oldName, null, false);
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
    renameReference(t, n, rootName, rest, true);
  }

  /**
   * @param t The NodeTraversal.
   * @param oldName The root name of a qualified name.
   * @param rest The rest of the qualified name or null.
   * @param inJSDoc Whether the rewriting is occuring within JSDoc.
   */
  private void renameReference(
      NodeTraversal t, Node n, String oldName, @Nullable String rest, boolean inJSDoc) {
    Scope current = t.getScope();
    while (current != null) {
      String newName = renameTable.get(current.getRootNode(), oldName);
      if (newName != null) {
        String newFullName = rest == null ? newName : newName + rest;
        n.setString(newFullName);
        // Dont call reportCodeChange for jsdoc changes
        if (!inJSDoc) {
          t.reportCodeChange();
        }
        return;
      } else if (current.hasOwnSlot(oldName)) {
        return;
      } else {
        current = current.getParent();
      }
    }
  }
}
