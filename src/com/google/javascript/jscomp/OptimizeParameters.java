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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;
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
 */
class OptimizeParameters
    implements CompilerPass, OptimizeCalls.CallGraphCompilerPass {

  private final AbstractCompiler compiler;

  OptimizeParameters(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  // TODO(johnlenz): Remove this.
  OptimizeParameters(AbstractCompiler compiler, NameReferenceGraph unused) {
    this(compiler);
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
    for (DefinitionSite defSite : definitions.getDefinitionSites()) {
      if (canChangeSignature(defSite, definitions)) {
        tryEliminateConstantArgs(defSite, definitions);
        tryEliminateOptionalArgs(defSite, definitions);
      }
    }
  }

  /**
   * @return Whether the definitionSite represents a function whose call
   *      signature can be modified.
   */
  private boolean canChangeSignature(
      DefinitionSite definitionSite, SimpleDefinitionFinder defFinder) {
    Definition definition = definitionSite.definition;

    if (definitionSite.inExterns) {
      return false;
    }

    // Only functions may be rewritten.
    // Functions that access "arguments" are not eligible since
    // rewrite changes the structure of this object.
    Node rValue = definition.getRValue();
    if (rValue == null ||
        !NodeUtil.isFunction(rValue) ||
        NodeUtil.isVarArgsFunction(rValue)) {
      return false;
    }

    // TODO(johnlenz): support rewriting methods defined as part of
    // object literals (they are generally problematic because they may be
    // maps of functions use in for-in expressions, etc).
    // Be conservative, don't try to optimize any declaration that isn't as
    // simple function declaration or assignment.
    if (!SimpleDefinitionFinder.isSimpleFunctionDeclaration(rValue)) {
      return false;
    }

    // Assume an exported method result is used.
    if (SimpleDefinitionFinder.maybeExported(compiler, definition)) {
      return false;
    }

    Collection<UseSite> useSites = defFinder.getUseSites(definition);

    if (useSites.isEmpty()) {
      return false;
    }

    for (UseSite site : useSites) {
      // Any non-call reference maybe introducing an alias. Don't try to
      // change the function signature, if all the aliases can't also be
      // changed.
      // TODO(johnlenz): Support .call signature changes.
      if (!SimpleDefinitionFinder.isCallOrNewSite(site)) {
        return false;
      }

      // TODO(johnlenz): support specialization

      // Multiple definitions prevent rewrite.
      // TODO(johnlenz): Allow rewrite all definitions are valid.
      Node nameNode = site.node;
      Collection<Definition> singleSiteDefinitions =
          defFinder.getDefinitionsReferencedAt(nameNode);
      if (singleSiteDefinitions.size() > 1) {
        return false;
      }
      Preconditions.checkState(!singleSiteDefinitions.isEmpty());
      Preconditions.checkState(singleSiteDefinitions.contains(definition));
    }

    return true;
  }

  /**
   * Removes any optional parameters if no callers specifies it as an argument.
   */
  private void tryEliminateOptionalArgs(
      DefinitionSite defSite, SimpleDefinitionFinder defFinder) {
    // Count the maximum number of arguments passed into this function all
    // all points of the program.
    int maxArgs = -1;

    Definition definition = defSite.definition;
    Collection<UseSite> useSites = defFinder.getUseSites(definition);
    for (UseSite site : useSites) {
      Preconditions.checkState(SimpleDefinitionFinder.isCallOrNewSite(site));
      Node call = site.node.getParent();

      int numArgs = call.getChildCount() - 1;
      if (numArgs > maxArgs) {
        maxArgs = numArgs;
      }
    }

    eliminateParamsAfter(definition.getRValue(), maxArgs);
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
   */
  private void tryEliminateConstantArgs(
      DefinitionSite defSite, SimpleDefinitionFinder defFinder) {

    List<Parameter> parameters = Lists.newArrayList();
    boolean firstCall = true;

    // Build a list of parameters to remove
    Definition definition = defSite.definition;
    Collection<UseSite> useSites = defFinder.getUseSites(definition);
    for (UseSite site : useSites) {
      Preconditions.checkState(SimpleDefinitionFinder.isCallOrNewSite(site));
      Node call = site.node.getParent();

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

    // Remove the constant parameters in all the calls
    for (UseSite site : useSites) {
      Preconditions.checkState(SimpleDefinitionFinder.isCallOrNewSite(site));
      Node call = site.node.getParent();

      optimizeCallSite(parameters, call);
    }

    // Remove the constant parameters in the definitions and add it as a local
    // variable.
    Node function = definition.getRValue();
    if (NodeUtil.isFunction(function)) {
      optimizeFunctionDefinition(parameters, function);
    }
  }

  private void findConstantParameters(List<Parameter> parameters, Node cur) {
    int index = 0;
    while ((cur = cur.getNext()) != null) {
      if (index >= parameters.size()) {
        parameters.add(new Parameter(cur, false));
      } else if (parameters.get(index).shouldRemove()) {
        Node value = parameters.get(index).getArg();
        if (!nodesAreEqual(cur, value)) {
          parameters.get(index).setShouldRemove(false);
        }
      }
      index++;
    }

    for (;index < parameters.size(); index++) {
      parameters.get(index).setShouldRemove(false);
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

    return eliminateParamsAfter(function, formalArgPtr);
  }

  private boolean eliminateParamsAfter(Node fnNode, Node argNode) {
    if (argNode != null) {
      // Keep the args in the same order, do the last first.
      eliminateParamsAfter(fnNode, argNode.getNext());
      argNode.detachFromParent();
      Node var = new Node(Token.VAR, argNode).copyInformationFrom(argNode);
      fnNode.getLastChild().addChildrenToFront(var);
      compiler.reportCodeChange();
      return true;
    }
    return false;
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

    Node formalArgPtr = NodeUtil.getArgumentForFunction(
        function, argIndex);

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
    Preconditions.checkArgument(
        NodeUtil.isCallOrNew(call), "Node must be a call or new.");

    Node formalArgPtr = NodeUtil.getArgumentForCallOrNew(
        call, argIndex);

    if (formalArgPtr != null) {
      call.removeChild(formalArgPtr);
      compiler.reportCodeChange();
    }
    return formalArgPtr;
  }
}
