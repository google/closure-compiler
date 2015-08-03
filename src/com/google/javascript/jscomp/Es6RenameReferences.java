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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.Map;

/**
 * Renames references in code and JSDoc when necessary.
 *
 * @author moz@google.com (Michael Zhou)
 */
final class Es6RenameReferences extends AbstractPostOrderCallback {

  private final Map<Node, Map<String, String>> renameMap;

  Es6RenameReferences(Map<Node, Map<String, String>> renameMap) {
    this.renameMap = renameMap;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (NodeUtil.isReferenceName(n)) {
      renameReference(t, n);
    }

    JSDocInfo info = n.getJSDocInfo();
    if (info != null) {
      renameTypeNode(t, info.getTypeNodes());
    }
  }

  private void renameTypeNode(NodeTraversal t, Iterable<Node> typeNodes) {
    for (Node type : typeNodes) {
      if (type.isString()) {
        renameReference(t, type);
      }
      renameTypeNode(t, type.children());
    }
  }

  private void renameReference(NodeTraversal t, Node n) {
    Scope referencedIn = t.getScope();
    String oldName = n.getString();
    Scope current = referencedIn;
    boolean doRename = false;
    String newName = null;
    while (current != null) {
      Map<String, String> renamesAtCurrentLevel = renameMap.get(current.getRootNode());
      if (renamesAtCurrentLevel != null && renamesAtCurrentLevel.containsKey(oldName)) {
        doRename = true;
        newName = renamesAtCurrentLevel.get(oldName);
        break;
      } else if (current.isDeclared(oldName, false)) {
        return;
      } else {
        current = current.getParent();
      }
    }
    if (doRename) {
      n.setString(newName);
      t.getCompiler().reportCodeChange();
    }
  }
}
