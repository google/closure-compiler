/*
 * Copyright 2020 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;

/** Warns at any usage of Dynamic Import expressions that they are unable to be transpiled. */
public class ForbidDynamicImportUsage implements CompilerPass, NodeTraversal.Callback {
  private final AbstractCompiler compiler;

  static final DiagnosticType DYNAMIC_IMPORT_USAGE =
      DiagnosticType.error(
          "JSC_DYNAMIC_IMPORT_USAGE", "Dynamic import expressions cannot be transpiled.");

  public ForbidDynamicImportUsage(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    this.compiler.markFeatureNotAllowed(Feature.DYNAMIC_IMPORT);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      // We only want to traverse scripts that contain dynamic imports, so we can generate error
      // messages that point to them.
      return getFeatureSetOfScript(n).contains(Feature.DYNAMIC_IMPORT);
    } else {
      return true;
    }
  }

  private FeatureSet getFeatureSetOfScript(Node n) {
    final FeatureSet featureSet = NodeUtil.getFeatureSetOfScript(n);
    return (featureSet == null) ? FeatureSet.BARE_MINIMUM : featureSet;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case DYNAMIC_IMPORT:
        t.report(n, DYNAMIC_IMPORT_USAGE);
        break;
      default:
        break;
    }
  }
}
