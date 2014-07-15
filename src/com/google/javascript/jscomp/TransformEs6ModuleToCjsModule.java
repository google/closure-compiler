/*
 * Copyright 2014 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rewrites a ES6 module into a CommonJS module.
 *
 * @author moz@google.com (Michael Zhou)
 */
public class TransformEs6ModuleToCjsModule extends AbstractPostOrderCallback
    implements CompilerPass {
  private final Compiler compiler;
  private int scriptNodeCount = 0;
  private Map<String, String> exportMap = new LinkedHashMap<>();

  TransformEs6ModuleToCjsModule(Compiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isImport()) {
      visitImport(t, n, parent);
    } else if (n.isExport()) {
      visitExport(t, n, parent);
    } else if (n.isScript()) {
      scriptNodeCount++;
      visitScript(t, n);
    }
  }

  private void visitImport(NodeTraversal t, Node n, Node parent) {
    String module = n.getLastChild().getString();
    Map<String, String> nameMap = new LinkedHashMap<>();
    for (Node child : n.children()) {
      if (child.isEmpty() || child.isString()) {
        continue;
      } else if (child.isName()) { // import a from "mod"
        nameMap.put(child.getString(), child.getString());
      } else {
        for (Node grandChild : child.children()) {
          Node origName = grandChild.getFirstChild();
          nameMap.put(origName.getString(), grandChild.getChildCount() == 2
              ? grandChild.getLastChild().getString() // import {a as foo} from "mod"
              : origName.getString()); // import {a} from "mod"
        }
      }
    }
    for (Map.Entry<String, String> entry : nameMap.entrySet()) {
      Node call = IR.call(IR.name("require"), IR.string(module));
      call.putBooleanProp(Node.FREE_CALL, true);
      parent.addChildBefore(
          IR.var(
              IR.name(entry.getValue()),
              IR.getprop(call, IR.string(entry.getKey())))
              .useSourceInfoIfMissingFromForTree(n),
          n);
    }
    parent.removeChild(n);
    compiler.reportCodeChange();
  }

  private void visitExport(NodeTraversal t, Node n, Node parent) {
    if (n.getBooleanProp(Node.EXPORT_DEFAULT)) {
      // TODO(moz): Handle default export: export default foo = 2
      compiler.report(JSError.make(n, Es6ToEs3Converter.CANNOT_CONVERT_YET,
          "Default export"));
    } else if (n.getBooleanProp(Node.EXPORT_ALL_FROM)) {
      // TODO(moz): Maybe support wildcard: export * from "mod"
      compiler.report(JSError.make(n, Es6ToEs3Converter.CANNOT_CONVERT_YET,
          "Wildcard export"));
    } else {
      if (n.getChildCount() == 2) {
        // TODO(moz): Support export FromClause.
        compiler.report(JSError.make(n, Es6ToEs3Converter.CANNOT_CONVERT_YET,
            "Export with FromClause"));
        return;
      }

      if (n.getFirstChild().getType() == Token.EXPORT_SPECS) {
        for (Node grandChild : n.getFirstChild().children()) {
          Node origName = grandChild.getFirstChild();
          exportMap.put(
              grandChild.getChildCount() == 2
                  ? grandChild.getLastChild().getString()
                  : origName.getString(),
              origName.getString());
        }
        parent.removeChild(n);
      } else {
        String name = n.getFirstChild().getFirstChild().getString();
        Var v = t.getScope().getVar(name);
        if (v == null || v.isGlobal()) {
          exportMap.put(name, name);
        }
        parent.replaceChild(n, n.removeFirstChild());
      }
      compiler.reportCodeChange();
    }
  }

  private void visitScript(NodeTraversal t, Node script) {
    Preconditions.checkArgument(scriptNodeCount == 1,
        "ProcessEs6Modules supports only one invocation per "
        + "CompilerInput / script node");

    if (exportMap.isEmpty()) {
      return;
    }

    Node objectlit = IR.objectlit();
    for (String name : exportMap.keySet()) {
      objectlit.addChildToBack(
          IR.stringKey(name, IR.name(exportMap.get(name))));
    }
    script.addChildToBack(IR.exprResult(IR.assign(
        IR.getprop(IR.name("module"), IR.string("exports")), objectlit))
        .useSourceInfoIfMissingFromForTree(script));

    compiler.reportCodeChange();
    exportMap.clear();
  }
}
