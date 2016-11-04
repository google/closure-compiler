/*
 * Copyright 2016 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;

/**
 * Rewrites a JSON file to be a module export. So that the JSON file parses correctly, it is wrapped
 * in an EXPR_RESULT. The pass makes only basic checks that the file provided is valid JSON. It is
 * not a full JSON validator.
 *
 * <p>Looks for JSON files named "package.json" so that the "main" property can be used as an alias
 * in module name resolution. See https://docs.npmjs.com/files/package.json#main
 */
public class RewriteJsonToModule extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {
  public static final DiagnosticType JSON_UNEXPECTED_TOKEN =
      DiagnosticType.error("JSC_JSON_UNEXPECTED_TOKEN", "Unexpected JSON token");

  private final Map<String, String> packageJsonMainEntries;
  private final Compiler compiler;

  /**
   * Creates a new RewriteJsonToModule instance which can be used to rewrite JSON files to modules.
   *
   * @param compiler The compiler
   */
  public RewriteJsonToModule(Compiler compiler) {
    this.compiler = compiler;
    this.packageJsonMainEntries = new HashMap<>();
  }

  public ImmutableMap<String, String> getPackageJsonMainEntries() {
    return ImmutableMap.copyOf(packageJsonMainEntries);
  }

  /**
   * Module rewriting is done a on per-file basis prior to main compilation. The root node for each
   * file is a SCRIPT - not the typical jsRoot of other passes.
   */
  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(root.isScript());
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case SCRIPT:
        if (n.getChildCount() != 1) {
          compiler.report(t.makeError(n, JSON_UNEXPECTED_TOKEN));
        } else {
          visitScript(t, n, parent);
        }
        return;

      case OBJECTLIT:
      case ARRAYLIT:
      case NUMBER:
      case TRUE:
      case FALSE:
      case NULL:
      case STRING:
        break;

      case STRING_KEY:
        if (!n.isQuotedString() || !n.hasOneChild()) {
          compiler.report(t.makeError(n, JSON_UNEXPECTED_TOKEN));
        }
        break;

      case EXPR_RESULT:
        if (!parent.isScript()) {
          compiler.report(t.makeError(n, JSON_UNEXPECTED_TOKEN));
        }
        break;

      default:
        compiler.report(t.makeError(n, JSON_UNEXPECTED_TOKEN));
        break;
    }

    if (n.getLineno() == 1) {
      // We wrapped the expression in parens so our first-line columns are off by one.
      // We need to correct for this.
      n.setCharno(n.getCharno() - 1);
      compiler.reportCodeChange();
    }
  }

  /**
   * For script nodes of JSON objects, add a module variable assignment so the result is exported.
   *
   * <p>If the file path ends with "/package.json", look for a "main" key in the object literal and
   * track it as a module alias.
   */
  private void visitScript(NodeTraversal t, Node n, Node parent) {
    if (n.getChildCount() != 1 || !n.getFirstChild().isExprResult()) {
      compiler.report(t.makeError(n, JSON_UNEXPECTED_TOKEN));
      return;
    }

    Node jsonObject = n.getFirstFirstChild().detach();
    n.removeFirstChild();

    String moduleName = t.getInput().getPath().toModuleName();

    n.addChildToFront(
        IR.var(IR.name(moduleName).useSourceInfoFrom(jsonObject), jsonObject)
            .useSourceInfoFrom(jsonObject));

    n.addChildToFront(
        IR.exprResult(
                IR.call(IR.getprop(IR.name("goog"), IR.string("provide")), IR.string(moduleName)))
            .useSourceInfoIfMissingFromForTree(n));

    String inputPath = t.getInput().getSourceFile().getOriginalPath();
    if (inputPath.endsWith("/package.json") && jsonObject.isObjectLit()) {
      Node main = NodeUtil.getFirstPropMatchingKey(jsonObject, "main");
      if (main != null && main.isString()) {
        String dirName = inputPath.substring(0, inputPath.length() - "package.json".length());
        packageJsonMainEntries.put(inputPath, dirName + main.getString());
      }
    }

    compiler.reportCodeChange();
  }
}
