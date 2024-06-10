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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.ThisAndArgumentsReferenceUpdater.ThisAndArgumentsContext;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Deque;

/** Converts ES6 arrow functions to standard anonymous ES3 functions. */
public class Es6RewriteArrowFunction implements NodeTraversal.Callback, CompilerPass {
  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final Deque<ThisAndArgumentsContext> contextStack;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.ARROW_FUNCTIONS);

  public Es6RewriteArrowFunction(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.contextStack = new ArrayDeque<>();
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, root, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case SCRIPT:
        contextStack.push(contextForScript(n, t.getInput()));
        break;
      case FUNCTION:
        if (!n.isArrowFunction()) {
          contextStack.push(contextForFunction(n, t.getInput()));
        }
        break;
      case SUPER:
        ThisAndArgumentsContext context = checkNotNull(contextStack.peek());
        // super(...) within a constructor.
        if (context.isConstructor && parent.isCall() && parent.getFirstChild() == n) {
          context.lastSuperStatement = getEnclosingStatement(parent, context.scopeBody);
        }
        break;
      default:
        break;
    }
    return true;
  }

  /**
   * @return The statement Node that is a child of block and contains n.
   */
  private Node getEnclosingStatement(Node n, Node block) {
    while (checkNotNull(n.getParent()) != block) {
      n = checkNotNull(NodeUtil.getEnclosingStatement(n.getParent()));
    }
    return n;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    ThisAndArgumentsContext context = contextStack.peek();

    if (n.isArrowFunction()) {
      visitArrowFunction(t, n, checkNotNull(context));
    } else if (context != null && context.scopeBody == n) {
      contextStack.pop();
      context.addVarDeclarations(compiler, astFactory, t.getCurrentScript());
    }
  }

  private void visitArrowFunction(NodeTraversal t, Node n, ThisAndArgumentsContext context) {
    n.setIsArrowFunction(false);

    Node body = n.getLastChild();
    checkState(body.isBlock(), "Arrow function body must be a block after normalization");

    ThisAndArgumentsReferenceUpdater updater =
        new ThisAndArgumentsReferenceUpdater(compiler, context, astFactory);
    NodeTraversal.traverse(compiler, body, updater);

    t.reportCodeChange();
  }

  private ThisAndArgumentsContext contextForFunction(Node functionNode, CompilerInput input) {
    Node scopeBody = functionNode.getLastChild();
    return new ThisAndArgumentsContext(
        scopeBody,
        NodeUtil.isEs6Constructor(functionNode),
        compiler.getUniqueIdSupplier().getUniqueId(input));
  }

  private ThisAndArgumentsContext contextForScript(Node scriptNode, CompilerInput input) {
    return new ThisAndArgumentsContext(
        scriptNode, /* isConstructor= */ false, compiler.getUniqueIdSupplier().getUniqueId(input));
  }

}
