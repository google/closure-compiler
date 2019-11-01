/*
 * Copyright 2007 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inlines methods that take no arguments and have only a return statement
 * returning a property. Because it works on method names rather than type
 * inference, a method with multiple definitions will be inlined if each
 * definition is identical.
 *
 * <pre>
 * A.prototype.foo = function() { return this.b; }
 * B.prototype.foo = function() { return this.b; }
 * </pre>
 *
 * will inline foo, but
 *
 * <pre>
 * A.prototype.foo = function() { return this.b; }
 * B.prototype.foo = function() { return this.c; }
 * </pre>
 *
 * will not.
 *
 * Declarations are not removed because we do not find all possible
 * call sites. For examples, calls of the form foo["bar"] are not
 * detected.
 *
 * This pass is not on by default because it is not safe in simple mode.
 * If the prototype method is mutated and we don't detect that, inlining it is
 * unsafe.
 * We enable it whenever function inlining is enabled.
 */
class InlineSimpleMethods extends MethodCompilerPass {

  private static final Logger logger =
      Logger.getLogger(InlineSimpleMethods.class.getName());

  private final AstAnalyzer astAnalyzer;

  InlineSimpleMethods(AbstractCompiler compiler) {
    super(compiler);
    astAnalyzer = compiler.getAstAnalyzer();
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage().isNormalized(), compiler.getLifeCycleStage());
    super.process(externs, root);
  }

  /**
   * For each method call, see if it is a candidate for inlining.
   * TODO(kushal): Cache the results of the checks
   */
  private class InlineTrivialAccessors extends InvocationsCallback {

    @Override
    void visit(NodeTraversal t, Node callNode, Node parent, String callName) {
      if (externMethods.contains(callName) || nonMethodProperties.contains(callName)) {
        return;
      }

      Collection<Node> definitions = methodDefinitions.get(callName);
      if (definitions == null || definitions.isEmpty()) {
        return;
      }

      // Do check of arity, complexity, and consistency in what we think is
      // the order from least to most complex
      Node firstDefinition = definitions.iterator().next();

      // Check any multiple definitions
      if (definitions.size() == 1 || allDefinitionsEquivalent(definitions)) {

        if (!argsMayHaveSideEffects(callNode)) {
          // Verify this is a trivial return
          Node returned = returnedExpression(firstDefinition);
          if (returned != null) {
            if (isPropertyTree(returned)) {
              if (logger.isLoggable(Level.FINE)) {
                logger.fine("Inlining property accessor: " + callName);
              }
              inlinePropertyReturn(parent, callNode, returned);
            } else if (NodeUtil.isLiteralValue(returned, false)
                && !astAnalyzer.mayHaveSideEffects(callNode.getFirstChild())) {
              if (logger.isLoggable(Level.FINE)) {
                logger.fine("Inlining constant accessor: " + callName);
              }
              inlineConstReturn(parent, callNode, returned);
            }
          } else if (isEmptyMethod(firstDefinition)
              && !astAnalyzer.mayHaveSideEffects(callNode.getFirstChild())) {
            if (logger.isLoggable(Level.FINE)) {
              logger.fine("Inlining empty method: " + callName);
            }
            inlineEmptyMethod(t, parent, callNode);
          }
        }
      } else {
        if (logger.isLoggable(Level.FINE)) {
          logger.fine("Method '" + callName + "' has conflicting definitions.");
        }
      }
    }
  }

  @Override
  Callback getActingCallback() {
    return new InlineTrivialAccessors();
  }

  /**
   * Returns true if the provided node is a getprop for
   * which the left child is this or a valid property tree
   * and for which the right side is a string.
   */
  private static boolean isPropertyTree(Node expectedGetprop) {
    if (!expectedGetprop.isGetProp()) {
      return false;
    }

    Node getpropLhs = expectedGetprop.getFirstChild();
    return getpropLhs.isThis() || isPropertyTree(getpropLhs);
  }

  /**
   * Finds the occurrence of "this" in the provided property tree and replaces
   * it with replacement
   */
  private static void replaceThis(Node expectedGetprop, Node replacement) {
    Node leftChild = expectedGetprop.getFirstChild();
    if (leftChild.isThis()) {
      expectedGetprop.replaceChild(leftChild, replacement);
    } else {
      replaceThis(leftChild, replacement);
    }
  }

  /**
   * Return the node that represents the expression returned
   * by the method, given a FUNCTION node.
   */
  private static Node returnedExpression(Node fn) {
    Node expectedBlock = NodeUtil.getFunctionBody(fn);
    if (!expectedBlock.hasOneChild()) {
      return null;
    }

    Node expectedReturn = expectedBlock.getFirstChild();
    if (!expectedReturn.isReturn()) {
      return null;
    }

    if (!expectedReturn.hasOneChild()) {
      return null;
    }

    return expectedReturn.getOnlyChild();
  }


  /**
   * Return whether the given FUNCTION node is an empty method definition.
   *
   * Must be private, or moved to NodeUtil.
   */
  private static boolean isEmptyMethod(Node fn) {
    return NodeUtil.isEmptyBlock(NodeUtil.getFunctionBody(fn));
  }

  /** Given a set of method definitions, verify they are the same. */
  private boolean allDefinitionsEquivalent(Collection<Node> definitions) {
    Node first = null;
    for (Node n : definitions) {
      if (first == null) {
        first = n;
      } else if (!compiler.areNodesEqualForInlining(first, n)) {
        return false;
      } // else continue
    }
    return true;
  }

  /**
   * Replace the provided method call with the tree specified in returnedValue
   *
   * Parse tree of a call is
   * name
   *   call
   *     getprop
   *       obj
   *       string
   */
  private void inlinePropertyReturn(Node parent, Node call, Node returnedValue) {
    Node getProp = returnedValue.cloneTree();
    replaceThis(getProp, call.getFirstChild().removeFirstChild());
    parent.replaceChild(call, getProp);
    compiler.reportChangeToEnclosingScope(getProp);
  }

  /**
   * Replace the provided object and its method call with the tree specified
   * in returnedValue. Should be called only if the object reference has
   * no side effects.
   */
  private void inlineConstReturn(Node parent, Node call, Node returnedValue) {
    Node retValue = returnedValue.cloneTree();
    parent.replaceChild(call, retValue);
    compiler.reportChangeToEnclosingScope(retValue);
  }

  /**
   * Remove the provided object and its method call.
   */
  private void inlineEmptyMethod(NodeTraversal t, Node parent, Node call) {
    // If the return value of the method call is read,
    // replace it with "void 0". Otherwise, remove the call entirely.

    if (NodeUtil.isExprCall(parent)) {
      parent.replaceWith(IR.empty());
      NodeUtil.markFunctionsDeleted(parent, compiler);
    } else {
      Node srcLocation = call;
      parent.replaceChild(call, NodeUtil.newUndefinedNode(srcLocation));
      NodeUtil.markFunctionsDeleted(call, compiler);
    }
    t.reportCodeChange();
  }

  /**
   * Check whether the given method call's arguments have side effects.
   * @param call The call node of a method invocation.
   */
  private boolean argsMayHaveSideEffects(Node call) {
    for (Node currentChild = call.getSecondChild();
         currentChild != null;
         currentChild = currentChild.getNext()) {
      if (astAnalyzer.mayHaveSideEffects(currentChild)) {
        return true;
      }
    }

    return false;
  }

  /**
   * A do-nothing signature store.
   */
  static final MethodCompilerPass.SignatureStore DUMMY_SIGNATURE_STORE =
      new MethodCompilerPass.SignatureStore() {
        @Override
        public void addSignature(
            String functionName, Node functionNode, String sourceFile) {
        }

        @Override
        public void removeSignature(String functionName) {
        }

        @Override
        public void reset() {
        }
      };

  @Override
  SignatureStore getSignatureStore() {
    return DUMMY_SIGNATURE_STORE;
  }
}
