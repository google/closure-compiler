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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;
import java.util.List;

/**
 * Reduces the size of common function expressions.
 *
 * This pass will rewrite:
 *
 * C.prototype.getA = function() { return this.a_ };
 * C.prototype.setA = function(newValue) { this.a_ = newValue };
 *
 * as:
 *
 * C.prototype.getA = JSCompiler_get("a_);
 * C.prototype.setA = JSCompiler_set("a_);
 *
 * if by doing so we will save bytes, after the helper functions are
 * added and renaming is done.
 *
 */
class FunctionRewriter implements CompilerPass {
  private final AbstractCompiler compiler;
  // Safety margin used to avoid growing simple programs by a few bytes.
  // Selected arbitrarily.
  private static final int SAVINGS_THRESHOLD = 16;

  FunctionRewriter(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    List<Reducer> reducers = ImmutableList.of(new ReturnConstantReducer(),
                                              new GetterReducer(),
                                              new SetterReducer(),
                                              new EmptyFunctionReducer(),
                                              new IdentityReducer());

    Multimap<Reducer, Reduction> reductionMap = HashMultimap.create();

    // Accumulate possible reductions in the reduction multi-map.  They
    // will be applied in the loop below.
    NodeTraversal.traverse(compiler, root,
                           new ReductionGatherer(reducers, reductionMap));

    // Apply reductions iff they will provide some savings.
    for (Reducer reducer : reducers) {
      Collection<Reduction> reductions = reductionMap.get(reducer);
      if (reductions.isEmpty()) {
        continue;
      }

      Node helperCode = parseHelperCode(reducer);
      if (helperCode == null) {
        continue;
      }

      int helperCodeCost = InlineCostEstimator.getCost(helperCode);

      // Estimate savings
      int savings = 0;
      for (Reduction reduction : reductions) {
        savings += reduction.estimateSavings();
      }

      // Compare estimated savings against the helper cost.  Apply
      // reductions if doing so will result in some savings.
      if (savings > (helperCodeCost + SAVINGS_THRESHOLD)) {
        for (Reduction reduction : reductions) {
          reduction.apply();
        }

        Node addingRoot = compiler.getNodeForCodeInsertion(null);
        addingRoot.addChildrenToFront(helperCode);
        compiler.reportCodeChange();
      }
    }
  }

  /**
   * Parse helper code needed by a reducer.
   *
   * @return Helper code root.  If parse fails, return null.
   */
  public Node parseHelperCode(Reducer reducer) {
    Node root = compiler.parseSyntheticCode(
        reducer.getClass().toString() + ":helper", reducer.getHelperSource());
    return (root != null) ? root.removeFirstChild() : null;
  }

  private static boolean isReduceableFunctionExpression(Node n) {
    return NodeUtil.isFunctionExpression(n)
        && !NodeUtil.isGetOrSetKey(n.getParent());
  }

  /**
   * Information needed to apply a reduction.
   */
  private class Reduction {
    private final Node parent;
    private final Node oldChild;
    private final Node newChild;

    Reduction(Node parent, Node oldChild, Node newChild) {
      this.parent = parent;
      this.oldChild = oldChild;
      this.newChild = newChild;
    }

    /**
     * Apply the reduction by replacing the old child with the new child.
     */
    void apply() {
      parent.replaceChild(oldChild, newChild);
      compiler.reportCodeChange();
    }

    /**
     * Estimate number of bytes saved by applying this reduction.
     */
    int estimateSavings() {
      return InlineCostEstimator.getCost(oldChild) -
          InlineCostEstimator.getCost(newChild);
    }
  }

  /**
   * Gathers a list of reductions to apply later by doing an in-order
   * AST traversal.  If a suitable reduction is found, stop traversal
   * in that branch.
   */
  private class ReductionGatherer implements Callback {
    private final List<Reducer> reducers;
    private final Multimap<Reducer, Reduction> reductions;

    /**
     * @param reducers List of reducers to apply during traversal.
     * @param reductions Reducer -> Reduction multimap,
     *                   populated during traversal.
     */
    ReductionGatherer(List<Reducer> reducers,
                      Multimap<Reducer, Reduction> reductions) {
      this.reducers = reducers;
      this.reductions = reductions;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal raversal,
                                  Node node,
                                  Node parent) {
      for (Reducer reducer : reducers) {
        Node replacement = reducer.reduce(node);
        if (replacement != node) {
          reductions.put(reducer, new Reduction(parent, node, replacement));
          return false;
        }
      }
      return true;
    }


    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
    }
  }

  /**
   * Interface implemented by the strength-reduction optimizers below.
   */
  abstract static class Reducer {
    /**
     * @return JS source for helper methods used by this reduction.
     */
    abstract String getHelperSource();

    /**
     * @return root of the reduced subtree if a reduction was applied;
     *         otherwise returns the node argument.
     */
    abstract Node reduce(Node node);

    /**
     * Builds a method call based on the the given method name,
     * argument and history.
     *
     * @param methodName Method to call.
     * @param argumentNode Method argument.
     */
    protected final Node buildCallNode(String methodName, Node argumentNode,
                                       Node srcref) {
      Node call = IR.call(IR.name(methodName)).srcref(srcref);
      call.putBooleanProp(Node.FREE_CALL, true);
      if (argumentNode != null) {
        call.addChildToBack(argumentNode.cloneTree());
      }
      return call;
    }
  }

  /**
   * Reduces return immutable constant literal methods declarations
   * with calls to a constant return method factory.
   *
   * Example:
   *   a.prototype.b = function() {}
   * is reduced to:
   *   a.prototype.b = emptyFn();
   */
  private static class EmptyFunctionReducer extends Reducer {
    static final String FACTORY_METHOD_NAME = "JSCompiler_emptyFn";
    static final String HELPER_SOURCE =
        "function " + FACTORY_METHOD_NAME + "() {" +
        "  return function() {}" +
        "}";

    @Override
    public String getHelperSource() {
      return HELPER_SOURCE;
    }

    @Override
    public Node reduce(Node node) {
      if (NodeUtil.isEmptyFunctionExpression(node)) {
        return buildCallNode(FACTORY_METHOD_NAME, null, node);
      } else {
        return node;
      }
    }
  }

  /**
   * Base class for reducers that match functions that contain a
   * single return statement.
   */
  abstract static class SingleReturnStatementReducer extends Reducer {

    /**
     * @return function return value node if function body contains a
     * single return statement.  Otherwise, null.
     */
    protected final Node maybeGetSingleReturnRValue(Node functionNode) {
      Node body = functionNode.getLastChild();
      if (!body.hasOneChild()) {
        return null;
      }

      Node statement = body.getFirstChild();
      if (statement.isReturn()) {
        return statement.getFirstChild();
      }
      return null;
    }
  }

  /**
   * Reduces property getter method declarations with calls to a
   * getter method factory.
   *
   * Example:
   *   a.prototype.b = function(a) {return a}
   * is reduced to:
   *   a.prototype.b = getter(a);
   */
  private static class IdentityReducer extends SingleReturnStatementReducer {
    static final String FACTORY_METHOD_NAME = "JSCompiler_identityFn";
    static final String HELPER_SOURCE =
        "function " + FACTORY_METHOD_NAME + "() {" +
        "  return function(" + FACTORY_METHOD_NAME + "_value) {" +
             "return " + FACTORY_METHOD_NAME + "_value}" +
        "}";

    @Override
    public String getHelperSource() {
      return HELPER_SOURCE;
    }

    @Override
    public Node reduce(Node node) {
      if (!isReduceableFunctionExpression(node)) {
        return node;
      }

      if (isIdentityFunction(node)) {
        return buildCallNode(FACTORY_METHOD_NAME, null, node);
      } else {
        return node;
      }
    }

    /**
     * Checks if the function matches the pattern:
     *   function(<value>, <rest>) {return <value>}
     *
     * @return Whether the function matches the pattern.
     */
    private boolean isIdentityFunction(Node functionNode) {
      Node argList = functionNode.getFirstChild().getNext();
      Node paramNode = argList.getFirstChild();
      if (paramNode == null) {
        return false;
      }

      Node value = maybeGetSingleReturnRValue(functionNode);
      if (value != null &&
          value.isName() &&
          value.getString().equals(paramNode.getString())) {
        return true;
      }
      return false;
    }
  }

  /**
   * Reduces return immutable constant literal methods declarations
   * with calls to a constant return method factory.
   *
   * Example:
   *   a.prototype.b = function() {return 10}
   * is reduced to:
   *   a.prototype.b = returnconst(10);
   */
  private static class ReturnConstantReducer
      extends SingleReturnStatementReducer {
    static final String FACTORY_METHOD_NAME = "JSCompiler_returnArg";
    static final String HELPER_SOURCE =
        "function " + FACTORY_METHOD_NAME +
        "(" + FACTORY_METHOD_NAME + "_value) {" +
        "  return function() {return " + FACTORY_METHOD_NAME + "_value}" +
        "}";

    @Override
    public String getHelperSource() {
      return HELPER_SOURCE;
    }

    @Override
    public Node reduce(Node node) {
      if (!isReduceableFunctionExpression(node)) {
        return node;
      }

      Node valueNode = getValueNode(node);
      if (valueNode != null) {
        return buildCallNode(FACTORY_METHOD_NAME, valueNode, node);
      } else {
        return node;
      }
    }

    /**
     * Checks if the function matches the pattern:
     *   function(<args>) {return <immutable value>}
     * and returns <immutable value> if a match is found.
     *
     * @return the immutable value node; or null.
     */
    private Node getValueNode(Node functionNode) {
      Node value = maybeGetSingleReturnRValue(functionNode);
      if (value != null &&
          NodeUtil.isImmutableValue(value)) {
        return value;
      }
      return null;
    }
  }

  /**
   * Reduces property getter method declarations with calls to a
   * getter method factory.
   *
   * Example:
   *   a.prototype.b = function() {return this.b_}
   * is reduced to:
   *   a.prototype.b = getter("b_");
   */
  private static class GetterReducer extends SingleReturnStatementReducer {
    static final String FACTORY_METHOD_NAME = "JSCompiler_get";
    static final String HELPER_SOURCE =
        "function " + FACTORY_METHOD_NAME + "(" +
        FACTORY_METHOD_NAME + "_name) {" +
        "  return function() {return this[" + FACTORY_METHOD_NAME + "_name]}" +
        "}";

    @Override
    public String getHelperSource() {
      return HELPER_SOURCE;
    }

    @Override
    public Node reduce(Node node) {
      if (!isReduceableFunctionExpression(node)) {
        return node;
      }

      Node propName = getGetPropertyName(node);
      if (propName != null) {
        if (!propName.isString()) {
          throw new IllegalStateException(
              "Expected STRING, got " + Token.name(propName.getType()));
        }

        return buildCallNode(FACTORY_METHOD_NAME, propName, node);
      } else {
        return node;
      }
    }

    /**
     * Checks if the function matches the pattern:
     *   function(<args>) {return this.<name>}
     * and returns <name> if a match is found.
     *
     * @return STRING node that is the RHS of a this property get; or null.
     */
    private Node getGetPropertyName(Node functionNode) {
      Node value = maybeGetSingleReturnRValue(functionNode);
      if (value != null &&
          value.isGetProp() &&
          value.getFirstChild().isThis()) {
        return value.getLastChild();
      }
      return null;
    }
  }

  /**
   * Reduces property setter method declarations with calls to a
   * setter method factory.
   *
   * Example:
   *   a.prototype.setB = function(value) {this.b_ = value}
   * reduces to:
   *   a.prototype.setB = getter("b_");
   */
  private static class SetterReducer extends Reducer {
    static final String FACTORY_METHOD_NAME = "JSCompiler_set";
    static final String HELPER_SOURCE =
        "function " + FACTORY_METHOD_NAME + "(" +
        FACTORY_METHOD_NAME + "_name) {" +
        "  return function(" + FACTORY_METHOD_NAME + "_value) {" +
        "this[" + FACTORY_METHOD_NAME + "_name] = " +
        FACTORY_METHOD_NAME + "_value}" +
        "}";

    @Override
    public String getHelperSource() {
      return HELPER_SOURCE;
    }

    @Override
    public Node reduce(Node node) {
      if (!isReduceableFunctionExpression(node)) {
        return node;
      }

      Node propName = getSetPropertyName(node);
      if (propName != null) {
        if (!propName.isString()) {
          throw new IllegalStateException(
              "Expected STRING, got " + Token.name(propName.getType()));
        }

        return buildCallNode(FACTORY_METHOD_NAME, propName, node);
      } else {
        return node;
      }
    }

    /**
     * Checks if the function matches the pattern:
     *   function(<value>, <rest>) {this.<name> = <value>}
     * and returns <name> if a match is found.
     *
     * @return STRING node that is the RHS of a this property get; or null.
     */
    private Node getSetPropertyName(Node functionNode) {
      Node body = functionNode.getLastChild();
      if (!body.hasOneChild()) {
        return null;
      }

      Node argList = functionNode.getFirstChild().getNext();
      Node paramNode = argList.getFirstChild();
      if (paramNode == null) {
        return null;
      }

      Node statement = body.getFirstChild();
      if (!NodeUtil.isExprAssign(statement)) {
        return null;
      }

      Node assign = statement.getFirstChild();
      Node lhs = assign.getFirstChild();
      if (lhs.isGetProp() && lhs.getFirstChild().isThis()) {
        Node rhs = assign.getLastChild();
        if (rhs.isName() &&
            rhs.getString().equals(paramNode.getString())) {
          Node propertyName = lhs.getLastChild();
          return propertyName;
        }
      }
      return null;
    }
  }
}
