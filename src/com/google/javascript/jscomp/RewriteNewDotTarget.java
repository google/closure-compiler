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
package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.Es6ToEs3Util.cannotConvertYet;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/** Transpiles away `new.target`. */
final class RewriteNewDotTarget implements CompilerPass {
  private static final FeatureSet TRANSPILED_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.NEW_TARGET);

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;

  RewriteNewDotTarget(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
  }

  private class RewriteNewDotTargetCallback extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getToken() == Token.NEW_TARGET) {
        final Node enclosingNonArrowFunction = NodeUtil.getEnclosingNonArrowFunction(n);
        if (enclosingNonArrowFunction != null
            && NodeUtil.isEs6Constructor(enclosingNonArrowFunction)) {
          // Within an ES6 class constructor that we're about to transpile.
          // `new.target` -> `this.constructor`
          n.replaceWith(
              createThisDotConstructorForFunction(enclosingNonArrowFunction).srcrefTree(n));
          t.reportCodeChange();
        } else {
          // Getting new.target correct in functions other than transpiled ES6 class constructors
          // requires determining whether the function was called with `new` or not, which is more
          // hassle than its worth. There's no good reason to use `new.target` in such places
          // anyway.
          cannotConvertYet(compiler, n, "new.target");
        }
      }
    }
  }

  private Node createThisDotConstructorForFunction(Node functionNode) {
    return astFactory.createGetProp(astFactory.createThisForFunction(functionNode), "constructor");
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(
        compiler, root, TRANSPILED_FEATURES, new RewriteNewDotTargetCallback());
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, TRANSPILED_FEATURES);
  }
}
