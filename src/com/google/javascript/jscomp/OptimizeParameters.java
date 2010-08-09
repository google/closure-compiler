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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NameReferenceGraph.Name;
import com.google.javascript.jscomp.NameReferenceGraph.Reference;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;

/**
 * Optimize function calls and function signatures.
 *
 * <ul>
 * <li>Removes optional parameters if no caller specifies it as argument.</li>
 * <li>Removes arguments at call site to function that
 *     ignores the parameter. (Not implemented) </li>
 * <li>Inline a parameter if the function is always called with that constant.
 *     </li>
 * </ul>
 *
 *
 */
class OptimizeParameters implements CompilerPass {

  private final AbstractCompiler compiler;
  private NameReferenceGraph nameGraph;

  OptimizeParameters(AbstractCompiler compiler, NameReferenceGraph nameGraph) {
    this.compiler = compiler;
    this.nameGraph = nameGraph;
  }

  OptimizeParameters(AbstractCompiler compiler) {
    this(compiler, null);
  }

  @Override
  public void process(Node externs, Node root) {
    if (nameGraph == null) {
      NameReferenceGraphConstruction c =
          new NameReferenceGraphConstruction(compiler);
      c.process(externs, root);
      nameGraph = c.getNameReferenceGraph();
    }

    for (DiGraphNode<Name, Reference> node :
        nameGraph.getDirectedGraphNodes()) {
      Name name = node.getValue();
      if (name.canChangeSignature()) {
        List<DiGraphEdge<Name, Reference>> edges = node.getInEdges();
        tryEliminateConstantArgs(name, edges);
        tryEliminateOptionalArgs(name, edges);
      }
    }
  }

  /**
   * Removes any optional parameters if no callers specifies it as an argument.
   * @param name The name of the function to optimize.
   * @param edges All the references to this name.
   */
  private void tryEliminateOptionalArgs(Name name,
      List<DiGraphEdge<Name, Reference>> edges) {

    // Count the maximum number of arguments passed into this function all
    // all points of the program.
    int maxArgs = -1;

    for (DiGraphEdge<Name, Reference> refEdge : edges) {
      Reference ref = refEdge.getValue();
      Node call = ref.parent;

      if (isCallSite(ref)) {
        int numArgs = call.getChildCount() - 1;
        if (numArgs > maxArgs) {
          maxArgs = numArgs;
        }
      } // else this is a definition or a dereference, ignore it.
    }

    for (Definition definition : name.getDeclarations()) {
      eliminateParamsAfter(definition.getRValue(), maxArgs);
    }
  }

  /**
   * Eliminate parameters if they are always constant.
   *
   * function foo(a, b) {...}
   * foo(1,2);
   * foo(1,3)
   * becomes
   * function foo(b) { var a = 1 ... }
   * foo(2);
   * foo(3);
   *
   * @param name The name of the function to optimize.
   * @param edges All the references to this name.
   */
  private void tryEliminateConstantArgs(Name name,
      List<DiGraphEdge<Name, Reference>> edges) {

    List<Parameter> parameters = Lists.newArrayList();
    boolean firstCall = true;

    // Build a list of parameters to remove
    for (DiGraphEdge<Name, Reference> refEdge : edges) {
      Reference ref = refEdge.getValue();
      Node call = ref.parent;

      if (isCallSite(ref)) {
        Node cur = call.getFirstChild();
        if (firstCall) {
          // Use the first call to construct a list of parameters of the
          // function.
          buildParameterList(parameters, cur);
          firstCall = false;
        } else {
          findConstantParameters(parameters, cur);
        }
      }
    }

    // Remove the constant parameters in all the calls
    for (DiGraphEdge<Name, Reference> refEdge : edges) {
      Reference ref = refEdge.getValue();
      Node call = ref.parent;

      if (isCallSite(ref)) {
        optimizeCallSite(parameters, call);
      }
    }

    // Remove the constant parameters in the definitions and add it as a local
    // variable.
    for (Definition definition : name.getDeclarations()) {
      Node function = definition.getRValue();
      if (NodeUtil.isFunction(function)) {
        optimizeFunctionDefinition(parameters, function);
      }
    }
  }

  private void findConstantParameters(List<Parameter> parameters, Node cur) {
    for (int index = 0; (cur = cur.getNext()) != null; index++) {
      if (index >= parameters.size()) {
        parameters.add(new Parameter(cur, false));
      } else if (parameters.get(index).shouldRemove()){
        Node value = parameters.get(index).getArg();
        if (!nodesAreEqual(cur, value)) {
          parameters.get(index).setShouldRemove(false);
        }
      }
    }
  }

  private void buildParameterList(List<Parameter> parameters, Node cur) {
    while ((cur = cur.getNext()) != null) {
      parameters.add(new Parameter(cur, NodeUtil.isLiteralValue(cur, false)));
    }
  }

  private void optimizeFunctionDefinition(List<Parameter> parameters,
      Node function) {
    for (int index = parameters.size() - 1; index >= 0; index--) {
      if (parameters.get(index).shouldRemove()) {
        Node paramName = eliminateFunctionParamAt(function, index);
        if (paramName != null) {
          addVariableToFunction(function, paramName,
              parameters.get(index).getArg());
        }
      }
    }
  }

  private void optimizeCallSite(List<Parameter> parameters, Node call) {
    for (int index = parameters.size() - 1; index >= 0; index--) {
      if (parameters.get(index).shouldRemove()) {
        eliminateCallParamAt(call, index);
      }
    }
  }

  /**
   * @param ref A reference to a function.
   * @return true, if it's safe to optimize this function.
   */
  private boolean isCallSite(Reference ref) {
    Node call = ref.parent;
    // We need to make sure we're dealing with a call to the function we're
    // optimizing. If the the first child of the parent is not the site, this
    // is a nested call and it's a call to another function.
    return isCallOrNew(call) && call.getFirstChild() == ref.site;
  }

  /**
   * Return true if the node can be considered a call. For the purpose of this
   * class, the new operator is considered a call since it can be optimized
   * in the same way.
   * @param node A node
   * @return True if the node is a call.
   */
  private boolean isCallOrNew(Node node) {
    return NodeUtil.isCall(node) || NodeUtil.isNew(node);
  }

  /**
   * Node equality as intended by the this pass.
   * @param n1 A node
   * @param n2 A node
   * @return true if both node are considered equal for the purposes of this
   * class, false otherwise.
   */
  private boolean nodesAreEqual(Node n1, Node n2) {
    return NodeUtil.isImmutableValue(n1) && NodeUtil.isImmutableValue(n2) &&
        n1.checkTreeEqualsSilent(n2);
  }

  /**
   * Simple container class that keeps tracks of a parameter and whether it
   * should be removed.
   */
  private static class Parameter {
    private final Node arg;
    private boolean shouldRemove;

    public Parameter(Node arg, boolean shouldRemove) {
      this.shouldRemove = shouldRemove;
      this.arg = arg;
    }

    public Node getArg() {
      return arg;
    }

    public boolean shouldRemove() {
      return shouldRemove;
    }

    public void setShouldRemove(boolean value) {
      shouldRemove = value;
    }
  }

  /**
   * Adds a variable to the top of a function block.
   * @param function A function node.
   * @param varName The name of the variable.
   * @param value The initial value of the variable.
   */
  private void addVariableToFunction(Node function, Node varName, Node value) {
    Preconditions.checkArgument(NodeUtil.isFunction(function),
        "Node must be a function.");

    Node block = function.getLastChild();
    Preconditions.checkArgument(block.getType() == Token.BLOCK,
        "Node must be a block.");

    Node newVar = NodeUtil.newVarNode(varName.getQualifiedName(),
        value.cloneTree());
    block.addChildToFront(newVar);
    compiler.reportCodeChange();
  }

  /**
   * Removes all formal parameters starting at argIndex.
   * @return true if a parameter has been removed.
   */
  private boolean eliminateParamsAfter(Node function, int argIndex) {

    boolean paramRemoved = false;

    Node formalArgPtr = function.getFirstChild().getNext().getFirstChild();
    while (argIndex != 0 && formalArgPtr != null) {
      formalArgPtr = formalArgPtr.getNext();
      argIndex--;
    }

    while (formalArgPtr != null) {
      Node next = formalArgPtr.getNext();
      function.getFirstChild().getNext().removeChild(formalArgPtr);
      Node var = new Node(Token.VAR, formalArgPtr);
      function.getLastChild().addChildrenToFront(var);
      compiler.reportCodeChange();
      paramRemoved = true;
      formalArgPtr = next;
    }

    return paramRemoved;
  }

  /**
   * Given the first argument of a function or call, this removes the nth
   * argument of the function or call.
   * @param firstArg The first arg of the call or function.
   * @param argIndex the index of the arg to remove.
   * @return the node of the removed argument.
   */
  private Node getArgumentAtIndex(Node firstArg, int argIndex) {
    Node formalArgPtr = firstArg;
    while (argIndex != 0 && formalArgPtr != null) {
      formalArgPtr = formalArgPtr.getNext();
      argIndex--;
    }
    return formalArgPtr;
  }

  /**
   * Eliminates the parameter from a function definition.
   * @param function The function node
   * @param argIndex The index of the the argument to remove.
   * @return The Node of the argument removed.
   */
  private Node eliminateFunctionParamAt(Node function, int argIndex) {
    Preconditions.checkArgument(NodeUtil.isFunction(function),
        "Node must be a function.");

    Node formalArgPtr = getArgumentAtIndex(
        function.getFirstChild().getNext().getFirstChild(), argIndex);

    if (formalArgPtr != null) {
      function.getFirstChild().getNext().removeChild(formalArgPtr);
    }
    return formalArgPtr;
  }

  /**
   * Eliminates the parameter from a function call.
   * @param call The function call node
   * @param argIndex The index of the the argument to remove.
   * @return The Node of the argument removed.
   */
  private Node eliminateCallParamAt(Node call, int argIndex) {
    Preconditions.checkArgument(isCallOrNew(call), "Node must be a call.");

    Node formalArgPtr = getArgumentAtIndex(
        call.getFirstChild().getNext(), argIndex);

    if (formalArgPtr != null) {
      call.removeChild(formalArgPtr);
      compiler.reportCodeChange();
    }
    return formalArgPtr;
  }
}
