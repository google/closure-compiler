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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.Collection;
import java.util.List;
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
 */
class InlineSimpleMethods extends MethodCompilerPass {

  private static final Logger logger =
      Logger.getLogger(InlineSimpleMethods.class.getName());

  InlineSimpleMethods(AbstractCompiler compiler) {
    super(compiler);
  }

  /**
   * For each method call, see if it is a candidate for inlining.
   * TODO(kushal): Cache the results of the checks
   */
  private class InlineTrivialAccessors extends InvocationsCallback {

    @Override
    void visit(NodeTraversal t, Node callNode, Node parent, String callName) {
      if (externMethods.contains(callName) ||
          nonMethodProperties.contains(callName)) {
        return;
      }

      Collection<Node> definitions = methodDefinitions.get(callName);
      if (definitions == null || definitions.size() == 0) {
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
              logger.fine("Inlining property accessor: " + callName);
              inlinePropertyReturn(parent, callNode, returned);
            } else if (NodeUtil.isLiteralValue(returned, false) &&
              !NodeUtil.mayHaveSideEffects(
                  callNode.getFirstChild(), compiler)) {
              logger.fine("Inlining constant accessor: " + callName);
              inlineConstReturn(parent, callNode, returned);
            }
          } else if (isEmptyMethod(firstDefinition) &&
              !NodeUtil.mayHaveSideEffects(
                  callNode.getFirstChild(), compiler)) {
            logger.fine("Inlining empty method: " + callName);
            inlineEmptyMethod(parent, callNode);
          }
        }
      } else {
        logger.fine("Method '" + callName + "' has conflicting definitions.");
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

    Node leftChild = expectedGetprop.getFirstChild();
    if (!leftChild.isThis() &&
        !isPropertyTree(leftChild)) {
      return false;
    }

    Node retVal = leftChild.getNext();
    if (NodeUtil.getStringValue(retVal) == null) {
      return false;
    }
    return true;
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
    Node expectedBlock = getMethodBlock(fn);
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

    return expectedReturn.getLastChild();
  }


  /**
   * Return whether the given FUNCTION node is an empty method definition.
   *
   * Must be private, or moved to NodeUtil.
   */
  private static boolean isEmptyMethod(Node fn) {
    Node expectedBlock = getMethodBlock(fn);
    return expectedBlock == null ?
        false : NodeUtil.isEmptyBlock(expectedBlock);
  }

  /**
   * Return a BLOCK node if the given FUNCTION node is a valid method
   * definition, null otherwise.
   *
   * Must be private, or moved to NodeUtil.
   */
  private static Node getMethodBlock(Node fn) {
    if (fn.getChildCount() != 3) {
      return null;
    }

    Node expectedBlock = fn.getLastChild();
    return  expectedBlock.isBlock() ?
        expectedBlock : null;
  }

  /**
   * Given a set of method definitions, verify they are the same.
   */
  private boolean allDefinitionsEquivalent(
      Collection<Node> definitions) {
    List<Node> list = Lists.newArrayList();
    list.addAll(definitions);
    Node node0 = list.get(0);
    for (int i = 1; i < list.size(); i++) {
      if (!compiler.areNodesEqualForInlining(list.get(i), node0)) {
        return false;
      }
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
  private void inlinePropertyReturn(Node parent, Node call,
      Node returnedValue) {
    Node getProp = returnedValue.cloneTree();
    replaceThis(getProp, call.getFirstChild().removeFirstChild());
    parent.replaceChild(call, getProp);
    compiler.reportCodeChange();
  }

  /**
   * Replace the provided object and its method call with the tree specified
   * in returnedValue. Should be called only if the object reference has
   * no side effects.
   */
  private void inlineConstReturn(Node parent, Node call,
      Node returnedValue) {
    Node retValue = returnedValue.cloneTree();
    parent.replaceChild(call, retValue);
    compiler.reportCodeChange();
  }

  /**
   * Remove the provided object and its method call.
   */
  private void inlineEmptyMethod(Node parent, Node call) {
    // If the return value of the method call is read,
    // replace it with "void 0". Otherwise, remove the call entirely.
    if (NodeUtil.isExprCall(parent)) {
      parent.getParent().replaceChild(parent, IR.empty());
    } else {
      Node srcLocation = call;
      parent.replaceChild(call, NodeUtil.newUndefinedNode(srcLocation));
    }
    compiler.reportCodeChange();
  }

  /**
   * Check whether the given method call's arguments have side effects.
   * @param call The call node of a method invocation.
   */
  private boolean argsMayHaveSideEffects(Node call) {
    for (Node currentChild = call.getFirstChild().getNext();
         currentChild != null;
         currentChild = currentChild.getNext()) {
      if (NodeUtil.mayHaveSideEffects(currentChild, compiler)) {
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
