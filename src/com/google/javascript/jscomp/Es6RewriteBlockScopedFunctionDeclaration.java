/*
 * Copyright 2017 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Rewrite block-scoped function declarations as "let"s.  This pass must happen before
 * Es6RewriteBlockScopedDeclaration, which rewrites "let" to "var".
 */
public final class Es6RewriteBlockScopedFunctionDeclaration extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.BLOCK_SCOPED_FUNCTION_DECLARATION);

  public Es6RewriteBlockScopedFunctionDeclaration(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isFunction()
        && parent != null
        && parent.isBlock()
        && !parent.getParent().isFunction()) {
      // Only consider declarations (all expressions have non-block parents) that are not directly
      // within a function or top-level.
      visitBlockScopedFunctionDeclaration(t, n, parent);
    }
  }

  /**
   * Rewrite the function declaration from:
   *
   * <pre>
   *   function f() {}
   *   FUNCTION
   *     NAME x
   *     PARAM_LIST
   *     BLOCK
   * </pre>
   *
   * to
   *
   * <pre>
   *   let f = function() {};
   *   LET
   *     NAME f
   *       FUNCTION
   *         NAME (w/ empty string)
   *         PARAM_LIST
   *         BLOCK
   * </pre>
   *
   * This is similar to {@link Normalize.NormalizeStatements#rewriteFunctionDeclaration} but
   * rewrites to "let" instead of "var".
   */
  private void visitBlockScopedFunctionDeclaration(NodeTraversal t, Node n, Node parent) {
    // Prepare a spot for the function.
    Node oldNameNode = n.getFirstChild();
    Node fnNameNode = oldNameNode.cloneNode();
    Node let = IR.declaration(fnNameNode, Token.LET).srcref(n);
    NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.LET_DECLARATIONS);

    // Prepare the function.
    oldNameNode.setString("");
    compiler.reportChangeToEnclosingScope(oldNameNode);

    // Move the function to the front of the parent.
    parent.removeChild(n);
    parent.addChildToFront(let);
    compiler.reportChangeToEnclosingScope(let);
    fnNameNode.addChildToFront(n);
  }
}
