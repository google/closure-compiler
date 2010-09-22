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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
 * There are some constraints on when a function may be optmized.
 * Only functions which are declared in one of the following forms:<br>
 * <ul>
 * <li>function foo() {}</li>
 * <li>var foo = function() {}</li>
 * <li>var foo = function bar() {}</li>
 * </ul>
 * Also, functions are identified by their names. Therefore, if a name is not
 * used in a way that we know that is safe, it is blacklisted. A function whose
 * name is on the list cannot be optimized.
 *
 */
class OptimizeParametersAlt extends AbstractPostOrderCallback
    implements CompilerPass, SpecializationAwareCompilerPass {

  private final AbstractCompiler compiler;

  // maps names with declarations; sometimes more than one name might refer
  // to a single declaration, i.e. var foo = function f() {}
  private Map<String, Map<Scope, Declaration>> mappings =
      new HashMap<String, Map<Scope, Declaration>>();

  // list of all declarations
  private Set<Declaration> decls = new HashSet<Declaration>();

  // list of all function calls
  private List<Call> calls = new LinkedList<Call>();

  // list of all names which are not supposed to be optimized
  private Set<String> blacklist = new HashSet<String>();

  private SpecializeModule.SpecializationState specializationState;

  public void enableSpecialization(
      SpecializeModule.SpecializationState state) {
    this.specializationState = state;
  }

  OptimizeParametersAlt(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    //TODO (dhans): consider either switching to call graph and give up
    // support for local scopes - depending on actual test results.
    NodeTraversal.traverse(compiler, root, this);
    processCalls();
    removeUnsafeDeclarations();
    optimizeParameters();
  }

  /**
   * Represents a declared function.
   */
  private class Declaration {

    /*
     * AST node associated with the declaration.
     */
    public Node node;

    /*
     * List of formal parameters that defined for the function.
     */
    public LinkedList<FormalParameter> parameters;

    /*
     * List of function calls that refer to this declaration. This list may
     * be not exhaustive, but in this case doNotOptimize should be set to true.
     */
    public List<Call> calls;

    /*
     * Number of format parameters which are always passed actual values
     * for example, for the given function:
     * function foo(a, b, c, d, e) {}; f(1, 3, 2); f(2, 3); f(1, 3, 4, 5)
     * minParams = 2
     * All parameters up to this one can be inlined, provided the same value
     * is passed in each call:
     * function foo(a, c, d, e) {var b = 3;}; f(1, 2); f(2); f(1, 4, 5)
     */
    public int minParams;

    /*
     * Number of format parameters which are passed a value at least once
     * for example, for the given function:
     * function foo(a, b, c, d, e) {}; f(1, 3, 2); f(2, 3); f(1, 3, 4, 5)
     * maxParams = 4
     * All parameters after this number are never passed any values,
     * so there can be inlined:
     * function foo(a, b, c, d) {var e}; f(1, 3, 2); f(2, 3); f(1, 3, 4, 5)
     */
    public int maxParams;

    /*
     * Boolean property which indicates whether there is a reason not to
     * optimize this declaration.
     */
    public boolean doNotOptimize;

    Declaration(Node node, LinkedList<FormalParameter> parameters,
        boolean doNotOptimize) {
      this.node = node;
      this.parameters = parameters;
      this.doNotOptimize = doNotOptimize;
      this.calls = new LinkedList<Call>();
      this.maxParams = 0;
      this.minParams = parameters.size();
    }
  }

  /**
   * Represents a formal parameter of a function.
   */
  private class FormalParameter {
    private final Node arg;
    private boolean manyValues;
    private Node initialValue;

    FormalParameter(Node arg) {
      this.arg = arg;
      this.initialValue = null;
      this.manyValues = false;
    }
  }

  /**
   * Represents an actual parameter passed to a function call.
   */
  private class ActualParameter {
    private final Node value;

    ActualParameter(Node value) {
      this.value = value;
    }
  }

  /**
   * Represents a call.
   */
  private static class Call {
    private String name;
    private Node callSide;

    // Scope which the actual function is defined in
    private Scope scope;
    private List<ActualParameter> parameters;

    Call(String name, Node callSide, Scope scope,
        List<ActualParameter> parameters) {
      this.name = name;
      this.callSide = callSide;
      this.scope = scope;
      this.parameters = parameters;
    }
  }

  /**
   * Adds a new call to the list of all calls which should be taken into
   * account.
   *
   * @param name name of the function that is called
   * @param node AST node which represents the call or new node
   * @param scope scope that the function which is called belongs to
   */
  private void registerCall(String name, Node node, Scope scope) {
    Preconditions.checkState(NodeUtil.isCall(node) || NodeUtil.isNew(node));
    Preconditions.checkNotNull(node.getFirstChild());

    List<ActualParameter> params = Lists.newLinkedList();
    Node child = node.getFirstChild();

    if (NodeUtil.isFunctionObjectCall(node)) {
      // the first parameter is a "this" object
      // which is to be passed to the function
      // the rest of them represent actual parameters
      child = child.getNext();
    }

    // collect all actual parameters passed to the call
    while (child != null && (child = child.getNext()) != null) {
      params.add(new ActualParameter(child));
    }

    calls.add(new Call(name, node, scope, params));
  }

  /**
   * This function is called when all declarations and calls are gathered.
   * It iterates through all the calls and two important things happen
   * for each one:
   * - a corresponding Declaration instance is found
   * - all actual parameters of the call are processed
   * After all calls are processed, there is enough information to decide if
   * a particular formal parameter may be optimized.
   */
  private void processCalls() {
    for (Call call : calls) {
      // unqualified name of the function which is called
      String name = call.name;

      // get all function definitions with the name
      Map<Scope, Declaration> declarations = mappings.get(name);

      // function is not defined the the current module or should not
      // be optimized anyway
      if (declarations == null) {
        continue;
      }

      // find scope that the declaration belongs to
      Scope scope = call.scope;
      while (scope != null && !declarations.containsKey(scope)) {
        scope = scope.getParent();
      }
      Declaration declaration = declarations.get(scope);

      if (declaration == null) {
        continue;
      }

      // process parameters based on actual values passed as parameters
      processParameters(declaration, call);

      declaration.calls.add(call);
    }
  }

  /**
   * This function makes sure that functions with names that are
   * blacklisted are not optimized.
   */
  private void removeUnsafeDeclarations() {
    // do not optimize any functions whose names are blacklisted
    for (Entry<String, Map<Scope, Declaration>> entry : mappings.entrySet()) {
      String name = entry.getKey();
      if (blacklist.contains(name)) {
        for (Declaration declaration : entry.getValue().values()) {
          declaration.doNotOptimize = true;
        }
      }
    }
  }

  /**
   * This function iterates through a list of all actual parameters that
   * were passed to the corresponding declaration. It updates minParams
   * and maxParams values for the declaration and sets manyValues property
   * for a formal parameter.
   */
  private void processParameters(Declaration declaration, Call call) {
    List<FormalParameter> params = declaration.parameters;
    List<ActualParameter> actParams = call.parameters;

    int index = 0;
    ListIterator<FormalParameter> it = params.listIterator();
    ListIterator<ActualParameter> actIt = actParams.listIterator();
    while (it.hasNext() && actIt.hasNext()) {
      ++index;
      FormalParameter param = it.next();

      if (param.manyValues) {
        // do not bother to check anything if we already know that
        // the parameter cannot be optimized
        continue;
      }
      Node actValue = actIt.next().value;

      if (param.initialValue == null) {
        // this is the first call which passes a value to the parameter
        param.initialValue = actValue;
      } else if (!nodesAreEqual(param.initialValue, actValue)) {
        // this call passes a different value: the parameter cannot be touched
        param.manyValues = true;
      }
    }

    int maxParams = declaration.maxParams;
    declaration.maxParams = index > maxParams ? index : maxParams;
    int minParams = declaration.minParams;
    declaration.minParams = index < minParams ? index : minParams;
  }

  private void optimizeParameters() {

    for (Declaration decl : decls) {
      if (decl.doNotOptimize) {
        continue;
      }

      if (specializationState != null &&
          specializationState.canFixupFunction(decl.node)) {
        specializationState.reportSpecializedFunctionContainingNode(decl.node);
      }
      tryEliminateOptionalArgs(decl);
      tryEliminateConstantArgs(decl);
    }
  }

  void addDeclaration(String name, Scope scope, Declaration declaration) {
    // do not optimize anonymous function expressions or exported names
    // other cases when a function may be exported are covered as well
    // for example:
    // function foo() {}
    // tee(foo)
    // foo will not be aliased, as it is used in an unsafe way, as parameter
    if (name.isEmpty() || compiler.getCodingConvention().isExported(name)) {
      return;
    }

    Map<Scope, Declaration> declarations;
    if (!mappings.containsKey(name)) {
      declarations = new HashMap<Scope, Declaration>();
      mappings.put(name, declarations);
    } else {
      declarations = mappings.get(name);
    }

    if (!declarations.containsKey(scope)) {
      declarations.put(scope, declaration);
      decls.add(declaration);
    } else {
      // if one scope has more than one function with the same name,
      // do not optimize any of them
      declarations.get(scope).doNotOptimize = true;
    }
  }

  private boolean checkIfFunctionCannotBeOptimized(Node node, Node parent) {
    Preconditions.checkState(node.getType() == Token.FUNCTION);

    // check if function is defined in an array or an object
    if (parent.getType() == Token.ARRAYLIT ||
        parent.getType() == Token.OBJECTLIT) {
      return true;
    }

    return false;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // get current scope
    Scope scope = t.getScope();
    String name = null;
    switch (n.getType()) {
      case Token.FUNCTION:
        checkIfFunctionIsSafe(t, n, parent);
        break;
      case Token.NAME:
        checkIfNameIsSafe(t, n, parent);
        break;
      case Token.STRING:
        checkIfStringIsSafe(t, n, parent);
        break;
    }
  }

  private void checkIfFunctionIsSafe(NodeTraversal t, Node node, Node parent) {
    String name = null;
    Scope scope = t.getScope();

    // some functions are not optimized
    // what with something like:
    // array = [function foo() {}]
    // can this function be called by foo() ANYWHERE?
    if (checkIfFunctionCannotBeOptimized(node, parent)) {
      return;
    }
    // get function name
    Node child = node.getFirstChild();
    name = child.getString();

    // construct list of parameters
    LinkedList<FormalParameter> parameters =
        new LinkedList<FormalParameter>();
    Node paramNode = child.getNext().getFirstChild();
    while (paramNode != null) {
      parameters.add(new FormalParameter(paramNode));
      paramNode = paramNode.getNext();
    }

    // even if we already know that we will not optimize this function,
    // we want to save it, because if we encounter another function with
    // the same name, we want to know that we are dealing with a duplicate
    boolean doNotOptimize = parameters.isEmpty();
    Declaration declaration = new Declaration(node, parameters,
        doNotOptimize);

    addDeclaration(name, scope, declaration);

    // check if this function is a part of a VAR declaration
    // or a simple assignment
    if (parent != null) {
      Node grandparent = parent.getParent();
      if (grandparent != null && NodeUtil.isVar(grandparent)) {
        name = grandparent.getFirstChild().getString();
        addDeclaration(name, scope, declaration);
      }

      if (NodeUtil.isAssign(parent) && parent.getLastChild() == node) {
        // support only simple assignments - foo = function () {}
        if (NodeUtil.isName(parent.getFirstChild())) {
          name = parent.getFirstChild().getString();
          addDeclaration(name, scope, declaration);
        }
      }
    }
  }

  private void checkIfNameIsSafe(NodeTraversal t, Node node, Node parent) {
    Preconditions.checkState(node.getType() == Token.NAME);
    boolean isSafe = false;
    String name = node.getString();
    Scope scope = t.getScope();

    // check if the name is a part of a simple function call: f()
    if (parent.getType() == Token.CALL && parent.getFirstChild() == node) {
      // register new call
      registerCall(name, parent, scope);
      return;
    }

    // check if the name is a part of a new expression: new f()
    if (parent.getType() == Token.NEW && parent.getFirstChild() == node) {
      registerCall(name, parent, scope);
      return;
    }

    // check if the name is a part of a simple call expression: f.call()
    int type = parent.getType();
    if (NodeUtil.isGet(parent) && parent.getFirstChild() == node) {
      Preconditions.checkNotNull(node.getNext());
      Node grandparent = parent.getParent();
      Node prop = node.getNext();
      if (NodeUtil.isString(prop) && prop.getString().equals("call") &&
          NodeUtil.isCall(grandparent)) {
        registerCall(name, grandparent, scope);
        return;
      }
    }

    // check if the name is a part of a function declaration/expression
    // it is checked by another function
    if (NodeUtil.isFunction(parent) && parent.getFirstChild() == node) {
      // it represents function name
      return;
    }
    if (parent.getType() == Token.LP &&
        NodeUtil.isFunction(parent.getParent()) &&
        parent.getFirstChild().getNext() == node) {
      // it represents one of its formal parameters
      return;
    }

    // check if the name is a left side of var declaration
    if (NodeUtil.isVarDeclaration(node)) {
      return;
    }

    // check if the name is a left side of assign expression
    if (NodeUtil.isAssign(parent) && parent.getFirstChild() == node) {
      return;
    }

    // if we encountered "arguments", the enclosing
    // function should not be optimized
    if (name.equals("arguments")) {
      // function which uses "arguments" variable should not be optimized
      Node function = t.getEnclosingFunction();
      if (function != null) {
        // check if it is a named function
        // function foo() {}
        name = function.getFirstChild().getString();
        if (!name.isEmpty()) {
          blacklist.add(name);
        }

        // check if function is assigned to a variable
        // var foo = function() {}
        Node fparent = function.getParent();
        if (NodeUtil.isVarDeclaration(fparent)) {
          name = fparent.getString();
          blacklist.add(name);
        }
      }
      return;
    }

    // the name is potentially unsafe
    blacklist.add(node.getString());

  }

  private void checkIfStringIsSafe(NodeTraversal t, Node node, Node parent) {
    Preconditions.checkState(node.getType() == Token.STRING);

    // do not consider empty strings
    if (node.getString().isEmpty()) {
      return;
    }

    // a string node is only safe when it is used to invoke a method for
    // the current object, like this.foo() or this['foo']()
    // like obj['foo']() or obj.foo()
    if (NodeUtil.isGet(parent)) {
      Node obj = parent.getFirstChild();
      Node grandparent = parent.getParent();
      if (obj.getNext() == node &&
          NodeUtil.isThis(obj) &&
          NodeUtil.isCall(grandparent) &&
          grandparent.getFirstChild() == parent) {
        registerCall(node.getString(), grandparent, t.getScope());
        return;
      }
    }

    blacklist.add(node.getString());
  }

  /**
   * Removes any optional parameters if no callers specifies it as an argument.
   * @param declaration function to optimize
   */
  private void tryEliminateOptionalArgs(Declaration declaration) {
    Node formalArgs = declaration.node.getFirstChild().getNext();
    Node body = formalArgs.getNext();
    Iterator<FormalParameter> it = declaration.parameters.descendingIterator();

    // parameters which still does not have initial values assigned by any
    // calls can be safely removed
    int index = declaration.parameters.size() - 1;
    while (index >= declaration.maxParams) {
      FormalParameter param = it.next();

      // this parameter has a value - it cannot be eliminated
      // the rest of parameters cannot be eliminated as well
      if (param.initialValue != null) {
        break;
      }

      // no call specifies a value for this parameter, thus it can be removed
      // from the list of local parameters and replaced by a local var.
      formalArgs.removeChild(param.arg);
      Node var = new Node(Token.VAR, param.arg);
      body.addChildToFront(var);

      // this parameter should not be optimized again
      param.manyValues = true;
      compiler.reportCodeChange();
      --index;
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
   * @param declaration function to optimize
   */
  private void tryEliminateConstantArgs(Declaration declaration) {
    Node formalArgs = declaration.node.getFirstChild().getNext();
    Node body = formalArgs.getNext();
    Iterator<FormalParameter> it = declaration.parameters.descendingIterator();

    int index = declaration.parameters.size();//formalArgs.getChildCount();
    LinkedList<Integer> indexes = new LinkedList<Integer>();
    // parameters which are not marked with cannotRemove property
    // have the same initial value assigned in all calls
    while (it.hasNext()) {
      FormalParameter param = it.next();
      --index;

      // optimize only parameters which are passed a value in each call
      if (index >= declaration.minParams) {
        continue;
      }

      // check if this parameter can be removed
      if (param.manyValues) {
        continue;
      }

      if (!checkIsSafeToRemove(param)) {
        continue;
      }

      // there is exactly one value assigned to this parameters, thus it
      // can be removed from the list of formal parameters and replaced by
      // a local var
      formalArgs.removeChild(param.arg);

      Node newVar = NodeUtil.newVarNode(param.arg.getQualifiedName(),
          param.initialValue.cloneTree());
      body.addChildToFront(newVar);
      compiler.reportCodeChange();

      indexes.push(index);
    }

    // Remove actual parameters from all the calls
    for (Call call : declaration.calls) {
      int currentArg = 0;
      Node arg = call.callSide.getFirstChild().getNext();
      ListIterator<Integer> itr = indexes.listIterator();

      // in forms foo.call(this, a, b) do not consider the first parameter
      int rsh = NodeUtil.isFunctionObjectCall(call.callSide) ? 1 : 0;

      while (itr.hasNext() && arg != null) {
        index = itr.next() + rsh;
        while (currentArg < index && arg != null) {
          arg = arg.getNext();
          ++currentArg;
        }

        if (arg != null) {
          Node temp = arg.getNext();
          call.callSide.removeChild(arg);
          compiler.reportCodeChange();
          ++currentArg;
          arg = temp;
        }
      }
    }
  }

  /**
   * Only certain values may be removed. For example it is not safe to change:
   * var foo = function (a) {};
   * foo(bar);
   * into:
   * var foo - function () {var a = bar};
   * foo();
   */
  private boolean checkIsSafeToRemove(FormalParameter parameter) {
    return NodeUtil.isLiteralValue(parameter.initialValue, false);
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

}
