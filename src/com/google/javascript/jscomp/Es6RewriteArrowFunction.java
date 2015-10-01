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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Converts ES6 arrow functions to standard anonymous ES3 functions.
 */
public class Es6RewriteArrowFunction extends NodeTraversal.AbstractPreOrderCallback
    implements HotSwapCompilerPass {

  static final DiagnosticType THIS_REFERENCE_IN_ARROWFUNC_OF_OBJLIT = DiagnosticType.warning(
      "JSC_THIS_REFERENCE_IN_ARROWFUNC_OF_OBJLIT",
      "You have 'this' reference in an arrow function inside an object literal. "
      + "The reference may refer to an unintended target after rewrite.");

  private final AbstractCompiler compiler;

  // The name of the vars that capture 'this' and 'arguments'
  // for converting arrow functions.
  private static final String ARGUMENTS_VAR = "$jscomp$arguments";
  private static final String THIS_VAR = "$jscomp$this";

  public Es6RewriteArrowFunction(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, externs, this);
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.FUNCTION:
        if (n.isArrowFunction()) {
          visitArrowFunction(t, n);
        }
        break;
    }
    return true;
  }

  private void visitArrowFunction(NodeTraversal t, Node n) {
    if (n.getParent().isStringKey() && NodeUtil.referencesThis(n)) {
      compiler.report(JSError.make(n, THIS_REFERENCE_IN_ARROWFUNC_OF_OBJLIT));
    }

    n.setIsArrowFunction(false);
    Node body = n.getLastChild();
    if (!body.isBlock()) {
      body.detachFromParent();
      body = IR.block(IR.returnNode(body)).useSourceInfoIfMissingFromForTree(body);
      n.addChildToBack(body);
    }

    UpdateThisAndArgumentsReferences updater = new UpdateThisAndArgumentsReferences();
    NodeTraversal.traverseEs6(compiler, body, updater);
    addVarDecls(t, updater.changedThis, updater.changedArguments);

    compiler.reportCodeChange();
  }

  private void addVarDecls(NodeTraversal t, boolean addThis, boolean addArguments) {
    Scope scope = t.getScope();
    if (scope.isDeclared(THIS_VAR, false)) {
      addThis = false;
    }
    if (scope.isDeclared(ARGUMENTS_VAR, false)) {
      addArguments = false;
    }

    Node parent = t.getScopeRoot();
    if (parent.isFunction()) {
      // Add the new node at the beginning of the function body.
      parent = parent.getLastChild();
    }
    if (parent.isSyntheticBlock() && parent.getFirstChild().isScript()) {
      // Add the new node inside the SCRIPT node instead of the
      // synthetic block that contains it.
      parent = parent.getFirstChild();
    }

    CompilerInput input = compiler.getInput(parent.getInputId());
    if (addArguments) {
      Node name = IR.name(ARGUMENTS_VAR);
      Node argumentsVar = IR.declaration(name, IR.name("arguments"), Token.CONST);
      JSDocInfoBuilder jsdoc = new JSDocInfoBuilder(false);
      jsdoc.recordType(
          new JSTypeExpression(
              new Node(Token.BANG, IR.string("Arguments")), "<Es6RewriteArrowFunction>"));
      argumentsVar.setJSDocInfo(jsdoc.build());
      argumentsVar.useSourceInfoIfMissingFromForTree(parent);
      parent.addChildToFront(argumentsVar);
      scope.declare(ARGUMENTS_VAR, name, input);
    }
    if (addThis) {
      Node name = IR.name(THIS_VAR);
      Node thisVar = IR.declaration(name, IR.thisNode(), Token.CONST);
      thisVar.useSourceInfoIfMissingFromForTree(parent);
      parent.addChildToFront(thisVar);
      scope.declare(THIS_VAR, name, input);
    }
  }

  private static class UpdateThisAndArgumentsReferences implements NodeTraversal.Callback {
    private boolean changedThis = false;
    private boolean changedArguments = false;

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isThis()) {
        Node name = IR.name(THIS_VAR).srcref(n);
        parent.replaceChild(n, name);
        changedThis = true;
      } else if (n.isName() && n.getString().equals("arguments")) {
        Node name = IR.name(ARGUMENTS_VAR).srcref(n);
        parent.replaceChild(n, name);
        changedArguments = true;
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isFunction() || n.isArrowFunction();
    }
  }
}
