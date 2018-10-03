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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Table;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.List;

/**
 * Renames references in code and JSDoc when necessary.
 *
 * @author moz@google.com (Michael Zhou)
 */
final class Es6RenameReferences extends AbstractPostOrderCallback {

  private static final Splitter SPLIT_ON_DOT = Splitter.on('.');
  private static final Joiner JOIN_ON_DOT = Joiner.on('.');

  private final AbstractCompiler compiler;
  private final Table<Node, String, String> renameTable;
  private final boolean typesOnly;

  /**
   * @param renameTable table from (scope root, old name) to new name. Both the old and new name can
   *     be qualified.
   * @param typesOnly true if only type annotations should be renamed, false if type annotations and
   *     code references should be renamed
   */
  Es6RenameReferences(
      AbstractCompiler compiler, Table<Node, String, String> renameTable, boolean typesOnly) {
    this.compiler = compiler;
    this.renameTable = renameTable;
    this.typesOnly = typesOnly;
  }

  Es6RenameReferences(AbstractCompiler compiler, Table<Node, String, String> renameTable) {
    this(compiler, renameTable, /* typesOnly= */ false);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!typesOnly && NodeUtil.isReferenceName(n)) {
      renameReference(t, n, false);
    }

    JSDocInfo info = n.getJSDocInfo();
    if (info != null) {
      renameTypeNode(t, info.getTypeNodes());
    }
  }

  private void renameTypeNode(NodeTraversal t, Iterable<Node> typeNodes) {
    for (Node type : typeNodes) {
      if (type.isString()) {
        renameReference(t, type, true);
      }
      renameTypeNode(t, type.children());
    }
  }

  private void renameReference(NodeTraversal t, Node n, boolean isType) {
    String fullName = n.getString();

    List<String> split = SPLIT_ON_DOT.splitToList(fullName);

    // Rename most specific (longest) to least specific (shortest).
    for (int i = split.size(); i > 0; i--) {
      String name = JOIN_ON_DOT.join(split.subList(0, i));
      if (renameTable.containsColumn(name)) {
        String rest = JOIN_ON_DOT.join(split.subList(i, split.size()));
        if (!rest.isEmpty()) {
          rest = "." + rest;
        }
        renameReference(t, n, isType, name, rest);
      }
    }
  }

  private void renameReference(
      NodeTraversal t, Node n, boolean isType, String oldName, String rest) {
    Scope current = t.getScope();
    while (current != null) {
      String newName = renameTable.get(current.getRootNode(), oldName);
      if (newName != null) {
        String newFullName = newName + rest;

        if (isType || n.isName()) {
          n.setOriginalName(n.getString());
          n.setString(newFullName);
          n.setLength(newFullName.length());

          if (!isType) {
            t.reportCodeChange();
          }
        } else {
          Node newNode = NodeUtil.newQName(compiler, newFullName);
          newNode.setOriginalName(n.getString());
          newNode.srcrefTree(n);
          newNode.addChildrenToBack(n.removeChildren());
          n.replaceWith(newNode);
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
