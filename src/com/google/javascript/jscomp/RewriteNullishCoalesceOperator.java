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

import com.google.common.base.Supplier;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;

/** Replaces the ES2020 `??` operator with conditional (?:). */
public final class RewriteNullishCoalesceOperator implements NodeTraversal.Callback, CompilerPass {

  private static final String TEMP_VAR_NAME_PREFIX = "$jscomp$nullish$tmp";
  private static final FeatureSet TRANSPILED_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.NULL_COALESCE_OP);

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final Supplier<String> uniqueNameIdSuppier;

  public RewriteNullishCoalesceOperator(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.uniqueNameIdSuppier = compiler.getUniqueNameIdSupplier();
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, TRANSPILED_FEATURES, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, TRANSPILED_FEATURES);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isNullishCoalesce()) {
      visitNullishCoalesce(t, n);
      t.reportCodeChange();
    }
  }

  private void visitNullishCoalesce(NodeTraversal t, Node n) {
    // a() ?? b()
    // let temp;
    // (temp = a) != null) : temp ? b()
    String tempVarName = TEMP_VAR_NAME_PREFIX + uniqueNameIdSuppier.get();
    Node enclosingStatement = NodeUtil.getEnclosingStatement(n);

    Node left = n.removeFirstChild();
    Node right = n.getLastChild().detach();

    Node let = astFactory.createSingleLetNameDeclaration(tempVarName);
    Node assignName = astFactory.createName(tempVarName, left.getJSType());
    Node assign = astFactory.createAssign(assignName, left);
    Node ne = astFactory.createNe(assign, astFactory.createNull());
    Node hookName = astFactory.createName(tempVarName, left.getJSType());
    Node hook = astFactory.createHook(ne, hookName, right);

    let.srcrefTreeIfMissing(left);
    assignName.srcrefTreeIfMissing(left);
    assign.srcrefTreeIfMissing(left);
    ne.srcrefTreeIfMissing(left);
    hookName.srcrefTreeIfMissing(left);

    let.insertBefore(enclosingStatement);
    n.replaceWith(hook);

    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.LET_DECLARATIONS, compiler);
    compiler.reportChangeToEnclosingScope(hook);
  }
}
