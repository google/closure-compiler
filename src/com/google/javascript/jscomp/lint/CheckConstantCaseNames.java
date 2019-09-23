/*
 * Copyright 2019 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

/**
 * This pass looks for module-level variable declarations that use CONSTANT_CASE, according to the
 * Google style guide, and verifies that they are also annotated @const or are in a const clause.
 *
 * <p>This pass could be extended to check CONSTANT_CASE properties in the future.
 *
 * <p>Non-module-level variables should always use camel case according to the Google style guide.
 * In order to not confuse users, this pass does not warn that they should be @const. (A more
 * correct lint check could warn that non-module-locals should not be constant case.)
 */
public class CheckConstantCaseNames extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  public static final DiagnosticType MISSING_CONST_PROPERTY =
      DiagnosticType.disabled(
          "JSC_MISSING_CONST_ON_CONSTANT_CASE",
          "CONSTANT_CASE {0} is constant-by-convention, so must be explicitly `const` or @const");

  private final AbstractCompiler compiler;
  private final CodingConvention convention;

  public CheckConstantCaseNames(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.convention = compiler.getCodingConvention();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!t.inModuleScope()) {
      return;
    }
    switch (n.getToken()) {
      case VAR:
      case LET:
        // Skip CONST as it automatically meets the criteria.
        JSDocInfo info = n.getJSDocInfo();
        if (info != null && info.hasConstAnnotation()) {
          break;
        }
        for (Node name : NodeUtil.findLhsNodesInNode(n)) {
          if (convention.isConstant(name.getString())) {
            t.report(name, MISSING_CONST_PROPERTY, name.getString());
          }
        }
        break;

      default:
        break;
    }
  }
}
