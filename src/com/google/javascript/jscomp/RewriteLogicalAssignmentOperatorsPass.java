/*
 * Copyright 2021 The Closure Compiler Authors.
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

/** Replaces the ES2020 `||=`, `&&=`, and `??=` operators. */
public final class RewriteLogicalAssignmentOperatorsPass
    implements NodeTraversal.Callback, CompilerPass {

  private final AbstractCompiler compiler;
  private final RewriteLogicalAssignmentOperatorsHelper rewriteLogicalAssignmentOperatorsHelper;

  public RewriteLogicalAssignmentOperatorsPass(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.rewriteLogicalAssignmentOperatorsHelper =
        new RewriteLogicalAssignmentOperatorsHelper(
            compiler, compiler.createAstFactory(), compiler.getUniqueIdSupplier());
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, Feature.LOGICAL_ASSIGNMENT);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(n);
      return scriptFeatures == null || scriptFeatures.contains(Feature.LOGICAL_ASSIGNMENT);
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node logicalAssignment, Node parent) {
    switch (logicalAssignment.getToken()) {
      case ASSIGN_OR:
      case ASSIGN_AND:
      case ASSIGN_COALESCE:
        rewriteLogicalAssignmentOperatorsHelper.visitLogicalAssignmentOperator(
            t, logicalAssignment);
        break;
      default:
        break;
    }
  }
}
