/*
 * Copyright 2009 Google Inc.
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
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;


import java.util.Collection;

/**
 * A compiler pass for optimize function return results.  Currently this
 * pass looks for results that are complete unused and rewrite then to be:
 *   "return x()" -->"x(); return"
 * , but it can easily be
 * expanded to look for use context to avoid unneed type coersion:
 *   - "return x.toString()" --> "return x"
 *   - "return !!x" --> "return x"
 * @author johnlenz@google.com (John Lenz)
 */
public class OptimizeReturns
    implements OptimizeCallGraph.CallGraphCompilerPass, CompilerPass {

  private AbstractCompiler compiler;

  OptimizeReturns(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  @VisibleForTesting
  public void process(Node externs, Node root) {
    SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
    defFinder.process(externs, root);
    process(defFinder);
  }

  @Override
  public void process(SimpleDefinitionFinder definitions) {
    for (DefinitionSite defSite : definitions.getDefinitionSites()) {
      optimizeResultsIfEligible(defSite, definitions);
    }
  }

  /**
   * Rewrites method results sites if the method results are never used.
   *
   * Definition and use site information is provided by the
   * {@link SimpleDefinitionFinder} passed in as an argument.
   *
   * @param defSite definition site to process.
   * @param defFinder structure that hold Node -> Definition and
   * Definition -> [UseSite] maps.
   */
  private void optimizeResultsIfEligible(
      DefinitionSite defSite, SimpleDefinitionFinder defFinder) {

    if (defSite.inExterns || callResultsMaybeUsed(defFinder, defSite)) {
      return;
    }

    rewriteReturns(defSite.definition.getRValue());
  }

  /**
   * Determines if a function result might be used.  A result might be use if:
   * - Function must is exported.
   * - The definition is never accessed outside a function call context.
   */
  private boolean callResultsMaybeUsed(
      SimpleDefinitionFinder defFinder, DefinitionSite definitionSite) {

    Definition definition = definitionSite.definition;

    // Assume non-function definitions results are used.
    Node rValue = definition.getRValue();
    if (rValue == null || !NodeUtil.isFunction(rValue)) {
      return true;
    }

    // Be conservative, don't try to optimize any declaration that isn't as
    // simple function declaration or assignment.
    if (!isSimpleFunctionDeclaration(rValue)) {
      return true;
    }

    // Assume an exported method result is used.
    if (maybeExported(compiler, definition)) {
      return true;
    }

    Collection<UseSite> useSites = defFinder.getUseSites(definition);
    for (UseSite site : useSites) {
      // Assume indirect definitions references use the result
      Node useNodeParent = site.node.getParent();
      if (isCall(site)) {
        Node callNode = useNodeParent;
        Preconditions.checkState(callNode.getType() == Token.CALL);
        if (isValueUsed(callNode)) {
          return true;
        }
      } else {
        // Allow a standalone name reference.
        //     var a;
        if (!NodeUtil.isVar(useNodeParent)) {
          return true;
        }
      }

      // TODO(johnlenz): Add specialization support.
    }

    // No possible use of the definition result
    return false;
  }

  static boolean maybeExported(
      AbstractCompiler compiler, Definition definition) {
    // Assume an exported method result is used.
    Node lValue = definition.getLValue();
    if (lValue == null) {
      return true;
    }

    String partialName;
    if (NodeUtil.isGetProp(lValue)) {
      partialName = lValue.getLastChild().getString();
    } else if (NodeUtil.isName(lValue)) {
      partialName = lValue.getString();
    } else {
      // GETELEM is assumed to be an export or other expression are unknown
      // uses.
      return true;
    }

    CodingConvention codingConvention = compiler.getCodingConvention();
    if (codingConvention.isExported(partialName)) {
      return true;
    }

    return false;
  }

  static boolean isSimpleFunctionDeclaration(Node fn) {
    Node parent = fn.getParent();
    Node gramps = parent.getParent();

    // Simple definition finder doesn't provide useful results in some
    // cases, specifically:
    //  - functions with recursive definitions
    //  - functions defined in object literals
    //  - functions defined in array litersals
    // Here we defined a set of known function declaration that are 'ok'.

    // example: function a(){};
    if (NodeUtil.isFunctionDeclaration(fn)) {
      return true;
    }

    // example: a = function(){};
    // example: var a = function(){};
    if (fn.getFirstChild().getString().isEmpty()
        && (NodeUtil.isExprAssign(gramps) || NodeUtil.isName(parent))) {
      return true;
    }

    return false;
  }

  /**
   * Determines if the name node acts as the function name in a call expression.
   */
  private static boolean isValueUsed(Node node) {
    // TODO(johnlenz): consider sharing some code with trySimpleUnusedResult.
    Node parent = node.getParent();
    switch (parent.getType()) {
      case Token.EXPR_RESULT:
        return false;
      case Token.HOOK:
      case Token.AND:
      case Token.OR:
        return (node == parent.getFirstChild()) ? true : isValueUsed(parent);
      case Token.COMMA:
        return (node == parent.getFirstChild()) ? false : isValueUsed(parent);
      case Token.FOR:
        if (NodeUtil.isForIn(parent)) {
          return true;
        } else {
          // Only an expression whose result is in the condition part of the
          // expression is used.
          return (parent.getChildAtIndex(1) == node);
        }
      default:
        return true;
    }
  }

  /**
   * For the supplied function node, rewrite all the return expressions so that:
   *    return foo();
   * becomes:
   *    foo(); return;
   * Useless return will be removed later by the peephole optimization passes.
   */
  private void rewriteReturns(Node fnNode) {
    Preconditions.checkState(NodeUtil.isFunction(fnNode));
    NodeUtil.visitPostOrder(
      fnNode.getLastChild(),
      new NodeUtil.Visitor() {
        @Override
        public void visit(Node node) {
          if (node.getType() == Token.RETURN && node.hasOneChild()) {
            Node result = node.removeFirstChild();
            if (NodeUtil.mayHaveSideEffects(result, compiler)) {
              node.getParent().addChildBefore(
                new Node(
                  Token.EXPR_RESULT, result).copyInformationFrom(result), node);
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
    return (parent.getFirstChild() == node) && NodeUtil.isCall(parent);
  }
}
