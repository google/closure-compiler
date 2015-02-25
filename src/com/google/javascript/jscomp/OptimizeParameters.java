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
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.rhino.IR;
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
 *     ignores the parameter.</li>
 * <li>Inline a parameter if the function is always called with that constant.
 *     </li>
 * </ul>
 *
 * @author johnlenz@google.com (John Lenz)
 */
class OptimizeParameters
    implements CompilerPass, OptimizeCalls.CallGraphCompilerPass {

  private final AbstractCompiler compiler;
  private final List<Node> removedNodes = Lists.newArrayList();

  OptimizeParameters(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  @VisibleForTesting
  public void process(Node externs, Node root) {
    Preconditions.checkState(
        compiler.getLifeCycleStage() == LifeCycleStage.NORMALIZED);
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

    // Remove any references or definitions that have been removed to keep it
    // in a consistent state for the next pass.
    for (Node n : removedNodes) {
      definitions.removeReferences(n);
    }
  }

  /**
   * @return Whether the definitionSite represents a function whose call
   *      signature can be modified.
   */
  private static boolean canChangeSignature(
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
        !rValue.isFunction() ||
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
    if (!defFinder.canModifyDefinition(definition)) {
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
    boolean continueLooking = false;
    for (UseSite site : useSites) {
      Preconditions.checkState(SimpleDefinitionFinder.isCallOrNewSite(site));
      Node call = site.node.getParent();

      Node cur = call.getFirstChild();
      if (firstCall) {
        // Use the first call to construct a list of parameters of the
        // function.
        continueLooking = buildParameterList(parameters, cur, site.scope);
        firstCall = false;
      } else {
        continueLooking = findFixedParameters(parameters, cur);
      }
      if (!continueLooking) {
        return;
      }
    }

    continueLooking = adjustForSideEffects(parameters);
    if (!continueLooking) {
      return;
    }

    // Remove the constant parameters in all the calls
    for (UseSite site : useSites) {
      Preconditions.checkState(SimpleDefinitionFinder.isCallOrNewSite(site));
      Node call = site.node.getParent();

      optimizeCallSite(defFinder, parameters, call);
    }

    // Remove the constant parameters in the definitions and add it as a local
    // variable.
    Node function = definition.getRValue();
    if (function.isFunction()) {
      optimizeFunctionDefinition(parameters, function);
    }
  }

  /**
   * Adjust the parameters to move based on the side-effects seen.
   * @return Whether there are any movable parameters.
   */
  private static boolean adjustForSideEffects(List<Parameter> parameters) {
    // If a parameter is moved, that has side-effect no parameters that
    // can be effected by side-effects can be left.

    // A parameter can be moved if it can't be side-effected (immutable),
    // or there are no following side-effects, that aren't moved.

    boolean anyMovable = false;
    boolean seenUnmovableSideEffects = false;
    boolean seenUnmoveableSideEfffected = false;
    for (int i = parameters.size() - 1; i >= 0; i--) {
      Parameter current = parameters.get(i);

      // Preserve side-effect ordering, don't move this parameter if:
      // * the current parameter has side-effects and a following
      // parameters that will not be move can be effected.
      // * the current parameter can be effected and a following
      // parameter that will not be moved has side-effects

      if (current.shouldRemove
          && ((seenUnmovableSideEffects && current.canBeSideEffected())
          || (seenUnmoveableSideEfffected && current.hasSideEffects()))) {
        current.shouldRemove = false;
      }

      if (current.shouldRemove) {
        anyMovable = true;
      } else {
        if (current.canBeSideEffected) {
          seenUnmoveableSideEfffected = true;
        }

        if (current.hasSideEffects) {
          seenUnmovableSideEffects = true;
        }
      }
    }
    return anyMovable;
  }

  /**
   * Determine which parameters use the same expression.
   * @return Whether any parameter was found that can be updated.
   */
  private boolean findFixedParameters(List<Parameter> parameters, Node cur) {
    boolean anyMovable = false;
    int index = 0;
    while ((cur = cur.getNext()) != null) {
      Parameter p;
      if (index >= parameters.size()) {
        p = new Parameter(cur, false);
        parameters.add(p);
      } else {
        p = parameters.get(index);
        if (p.shouldRemove()) {
          Node value = p.getArg();
          if (!cur.isEquivalentTo(value)) {
            p.setShouldRemove(false);
          } else {
            anyMovable = true;
          }
        }
      }

      setParameterSideEffectInfo(p, cur);
      index++;
    }

    for (; index < parameters.size(); index++) {
      parameters.get(index).setShouldRemove(false);
    }

    return anyMovable;
  }

  /**
   * @return Whether any parameter was movable.
   */
  private boolean buildParameterList(
      List<Parameter> parameters, Node cur, Scope s) {
    boolean anyMovable = false;
    while ((cur = cur.getNext()) != null) {
      boolean movable = isMovableValue(cur, s);
      Parameter p = new Parameter(cur, movable);
      setParameterSideEffectInfo(p, cur);
      parameters.add(p);
      if (movable) {
        anyMovable = true;
      }
    }
    return anyMovable;
  }

  private void setParameterSideEffectInfo(Parameter p, Node value) {
    if (!p.hasSideEffects()) {
      p.setHasSideEffects(NodeUtil.mayHaveSideEffects(value, compiler));
    }

    if (!p.canBeSideEffected()) {
      p.setCanBeSideEffected(NodeUtil.canBeSideEffected(value));
    }
  }


  /**
   * @return Whether the expression can be safely moved to another function
   *   in another scope.
   */
  private static boolean isMovableValue(Node n, Scope s) {
    // Things that can change value or are inaccessible can't be moved, these
    // are "this", "arguments", local names, and functions that capture local
    // values.
    switch (n.getType()) {
      case Token.THIS:
        return false;
      case Token.FUNCTION:
        // Don't move function closures.
        // TODO(johnlenz): Closure that only contain global reference can be
        // moved.
        return false;
      case Token.NAME:
        if (n.getString().equals("arguments")) {
          return false;
        } else {
          Var v = s.getVar(n.getString());
          // Make sure that the variable is global. A caught exception, while
          // it is in the global scope object in the compiler, it is not a
          // global variable.
          if (v != null &&
              (v.isLocal() ||
               v.nameNode.getParent().isCatch())) {
            return false;
          }
        }
        break;
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (!isMovableValue(c, s)) {
        return false;
      }
    }
    return true;
  }

  private void optimizeFunctionDefinition(List<Parameter> parameters,
      Node function) {
    for (int index = parameters.size() - 1; index >= 0; index--) {
      if (parameters.get(index).shouldRemove()) {
        Node paramName = eliminateFunctionParamAt(function, index);
        addVariableToFunction(function, paramName,
            parameters.get(index).getArg());
      }
    }
  }

  private void optimizeCallSite(
      SimpleDefinitionFinder defFinder, List<Parameter> parameters, Node call) {
    boolean mayMutateArgs = call.mayMutateArguments();
    boolean mayMutateGlobalsOrThrow = call.mayMutateGlobalStateOrThrow();
    for (int index = parameters.size() - 1; index >= 0; index--) {
      Parameter p = parameters.get(index);
      if (p.shouldRemove()) {
        eliminateCallParamAt(defFinder, p, call, index);

        if (mayMutateArgs && !mayMutateGlobalsOrThrow &&
            // We want to cover both global-state arguments, and
            // expressions that might throw exceptions.
            // We're deliberately conservative here b/c it's
            // difficult to test all the edge cases.
            !NodeUtil.isImmutableValue(p.getArg())) {
          mayMutateGlobalsOrThrow = true;
          call.setSideEffectFlags(
              new Node.SideEffectFlags(call.getSideEffectFlags())
              .setMutatesGlobalState());
        }
      }
    }
  }

  /**
   * Simple container class that keeps tracks of a parameter and whether it
   * should be removed.
   */
  private static class Parameter {
    private final Node arg;
    private boolean shouldRemove;
    private boolean hasSideEffects;
    private boolean canBeSideEffected;

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

    public void setHasSideEffects(boolean hasSideEffects) {
      this.hasSideEffects = hasSideEffects;
    }

    public boolean hasSideEffects() {
      return hasSideEffects;
    }

    public void setCanBeSideEffected(boolean canBeSideEffected) {
      this.canBeSideEffected = canBeSideEffected;
    }

    public boolean canBeSideEffected() {
      return canBeSideEffected;
    }
  }

  /**
   * Adds a variable to the top of a function block.
   * @param function A function node.
   * @param varName The name of the variable.
   * @param value The initial value of the variable.
   */
  private void addVariableToFunction(Node function, Node varName, Node value) {
    Preconditions.checkArgument(function.isFunction(),
        "Node must be a function.");

    Node block = function.getLastChild();
    Preconditions.checkArgument(block.isBlock(),
        "Node must be a block.");

    Preconditions.checkState(value.getParent() == null);
    Node stmt;
    if (varName != null) {
      stmt = NodeUtil.newVarNode(varName.getString(), value);
    } else {
      stmt = IR.exprResult(value);
    }
    block.addChildToFront(stmt);
    compiler.reportCodeChange();
  }

  /**
   * Removes all formal parameters starting at argIndex.
   * @return true if a parameter has been removed.
   */
  private boolean eliminateParamsAfter(Node function, int argIndex) {
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
      Node var = IR.var(argNode).copyInformationFrom(argNode);
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
  private static Node eliminateFunctionParamAt(Node function, int argIndex) {
    Preconditions.checkArgument(function.isFunction(),
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
   * @param defFinder
   * @param p
   * @param call The function call node
   * @param argIndex The index of the the argument to remove.
   * @return The Node of the argument removed.
   */
  private Node eliminateCallParamAt(
      SimpleDefinitionFinder defFinder, Parameter p, Node call, int argIndex) {
    Preconditions.checkArgument(
        NodeUtil.isCallOrNew(call), "Node must be a call or new.");

    Node formalArgPtr = NodeUtil.getArgumentForCallOrNew(
        call, argIndex);

    if (formalArgPtr != null) {
      call.removeChild(formalArgPtr);
      // The value in the parameter object is the one that is being moved into
      // function definition leave that one's references.  For everything else,
      // remove any references.
      if (p.getArg() != formalArgPtr) {
        removedNodes.add(formalArgPtr);
      }
      compiler.reportCodeChange();
    }
    return formalArgPtr;
  }
}
