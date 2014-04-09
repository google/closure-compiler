/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.Collection;
import java.util.List;

/**
 * A compiler pass for optimize function return results.  Currently this
 * pass looks for results that are complete unused and rewrite then to be:
 *   "return x()" -->"x(); return"
 * , but it can easily be
 * expanded to look for use context to avoid unneeded type coercion:
 *   - "return x.toString()" --> "return x"
 *   - "return !!x" --> "return x"
 * @author johnlenz@google.com (John Lenz)
 */
class OptimizeReturns
    implements OptimizeCalls.CallGraphCompilerPass, CompilerPass {

  private AbstractCompiler compiler;

  OptimizeReturns(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  @VisibleForTesting
  public void process(Node externs, Node root) {
    SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
    defFinder.process(externs, root);
    process(externs, root, defFinder);
  }

  @Override
  public void process(
      Node externs, Node root, SimpleDefinitionFinder definitions) {
    // Find all function nodes whose callers ignore the return values.
    List<Node> toOptimize = Lists.newArrayList();
    for (DefinitionSite defSite : definitions.getDefinitionSites()) {
      if (!defSite.inExterns && !callResultsMaybeUsed(definitions, defSite)) {
        toOptimize.add(defSite.definition.getRValue());
      }
    }
    // Optimize the return statements.
    for (Node node : toOptimize) {
      rewriteReturns(definitions, node);
    }
  }

  /**
   * Determines if a function result might be used.  A result might be use if:
   * - Function must is exported.
   * - The definition is never accessed outside a function call context.
   */
  private static boolean callResultsMaybeUsed(
      SimpleDefinitionFinder defFinder, DefinitionSite definitionSite) {

    Definition definition = definitionSite.definition;

    // Assume non-function definitions results are used.
    Node rValue = definition.getRValue();
    if (rValue == null || !rValue.isFunction()) {
      return true;
    }

    // Be conservative, don't try to optimize any declaration that isn't as
    // simple function declaration or assignment.
    if (!SimpleDefinitionFinder.isSimpleFunctionDeclaration(rValue)) {
      return true;
    }

    if (!defFinder.canModifyDefinition(definition)) {
      return true;
    }

    Collection<UseSite> useSites = defFinder.getUseSites(definition);
    for (UseSite site : useSites) {
      // Assume indirect definitions references use the result
      Node useNodeParent = site.node.getParent();
      if (isCall(site)) {
        Node callNode = useNodeParent;
        Preconditions.checkState(callNode.isCall());
        if (NodeUtil.isExpressionResultUsed(callNode)) {
          return true;
        }
      } else {
        // Allow a standalone name reference.
        //     var a;
        if (!useNodeParent.isVar()) {
          return true;
        }
      }

      // TODO(johnlenz): Add specialization support.
    }

    // No possible use of the definition result
    return false;
  }

  /**
   * For the supplied function node, rewrite all the return expressions so that:
   *    return foo();
   * becomes:
   *    foo(); return;
   * Useless return will be removed later by the peephole optimization passes.
   */
  private void rewriteReturns(
      final SimpleDefinitionFinder defFinder, Node fnNode) {
    Preconditions.checkState(fnNode.isFunction());
    NodeUtil.visitPostOrder(
      fnNode.getLastChild(),
      new NodeUtil.Visitor() {
        @Override
        public void visit(Node node) {
          if (node.isReturn() && node.hasOneChild()) {
            boolean keepValue = NodeUtil.mayHaveSideEffects(
                node.getFirstChild(), compiler);
            if (!keepValue) {
              defFinder.removeReferences(node.getFirstChild());
            }
            Node result = node.removeFirstChild();
            if (keepValue) {
              node.getParent().addChildBefore(
                IR.exprResult(result).srcref(result), node);
            }
            compiler.reportCodeChange();
          }
        }
      },
      new NodeUtil.MatchShallowStatement());
  }

  /**
   * Determines if the name node acts as the function name in a call expression.
   */
  private static boolean isCall(UseSite site) {
    Node node = site.node;
    Node parent = node.getParent();
    return (parent.getFirstChild() == node) && parent.isCall();
  }
}
