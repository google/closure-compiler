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

import static com.google.javascript.jscomp.AstFactory.type;

import com.google.javascript.jscomp.js.RuntimeJsLibManager.JsLibField;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.Token;

/** Converts REST parameters. */
public final class Es6RewriteRestParameters extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  private static final FeatureSet TRANSPILED_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.REST_PARAMETERS);

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final StaticScope namespace;
  private final JsLibField getRestArguments;

  public Es6RewriteRestParameters(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.namespace = compiler.getTranspilationNamespace();
    this.getRestArguments =
        compiler.getRuntimeJsLibManager().getJsLibField("$jscomp.getRestArguments");
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, TRANSPILED_FEATURES, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, root, TRANSPILED_FEATURES);
  }

  @Override
  public void visit(NodeTraversal traversal, Node current, Node parent) {
    if (current.getToken() == Token.ITER_REST) {
      visitRestParam(traversal, current, parent);
    }
  }

  /** Processes a rest parameter */
  private void visitRestParam(NodeTraversal t, Node restParam, Node paramList) {
    Node functionBody = paramList.getNext();
    int restIndex = paramList.getIndexOfChild(restParam);
    Node nameNode = restParam.getOnlyChild();
    String paramName = nameNode.getString();

    // Remove the existing param from the list, as it will be replaced with a declaration with the
    // same name.
    restParam.detach();

    if (!functionBody.hasChildren()) {
      // If function has no body, we are done!
      t.reportCodeChange();
      return;
    }

    // Now that the restParam is deleted, create a let declaration by making a new NAME node of the
    // same name `paramName`
    Node let =
        astFactory
            .createSingleLetNameDeclaration(
                paramName, // creates a new NAME node with name `paramName`
                astFactory.createCall(
                    astFactory.createGetPropWithUnknownType(
                        astFactory.createQName(this.namespace, getRestArguments), "apply"),
                    type(nameNode),
                    astFactory.createNumber(restIndex),
                    astFactory.createArgumentsReference()))
            .srcrefTreeIfMissing(functionBody);
    Node insertBeforePoint =
        NodeUtil.getInsertionPointAfterAllInnerFunctionDeclarations(functionBody);
    if (insertBeforePoint != null) {
      let.insertBefore(insertBeforePoint);
    } else {
      // functionBody only contains hoisted function declarations
      functionBody.addChildToBack(let);
    }
    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.LET_DECLARATIONS, compiler);
    t.reportCodeChange();
  }
}
