/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Converts ES6 code to valid ES3 code.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public class Es6ToEs3Converter extends NodeTraversal.AbstractPreOrderCallback
    implements CompilerPass {
  private final AbstractCompiler compiler;

  private final LanguageMode languageIn;
  private final LanguageMode languageOut;

  // The name of the var that captures 'this' for converting arrow functions.
  private static final String THIS_VAR = "$jscomp$this";

  public Es6ToEs3Converter(AbstractCompiler compiler, CompilerOptions options) {
    this.compiler = compiler;
    this.languageIn = options.getLanguageIn();
    this.languageOut = options.getLanguageOut();
  }

  public void process(Node externs, Node root) {
    if (languageOut != languageIn &&
        languageIn.isEs6OrHigher() && !languageOut.isEs6OrHigher()) {
      convert(root);
    }

    compiler.setLanguageMode(languageOut);
  }

  private void convert(Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.STRING_KEY:
        visitStringKey(n);
        break;
      case Token.FUNCTION:
        if (n.isArrowFunction()) {
          visitArrowFunction(t, n);
        }
        break;
    }
    return true;
  }

  /**
   * Converts extended object literal {a} to {a:a}.
   */
  private void visitStringKey(Node n) {
    if (!n.hasChildren()) {
      Node name = IR.name(n.getString());
      name.copyInformationFrom(n);
      n.addChildToBack(name);
      compiler.reportCodeChange();
    }
  }

  /**
   * Converts arrow functions to standard anonymous functions.
   */
  private void visitArrowFunction(NodeTraversal t, Node n) {
    n.setIsArrowFunction(false);
    Node body = n.getLastChild();
    if (!body.isBlock()) {
      body.detachFromParent();
      Node newBody = IR.block(IR.returnNode(body).srcref(body)).srcref(body);
      n.addChildToBack(newBody);
    }

    UpdateThisNodes thisUpdater = new UpdateThisNodes();
    NodeTraversal.traverse(compiler, body, thisUpdater);
    if (thisUpdater.changed) {
      addThisVar(t);
    }

    compiler.reportCodeChange();
  }

  private void addThisVar(NodeTraversal t) {
    Scope scope = t.getScope();
    if (scope.isDeclared(THIS_VAR, false)) {
      return;
    }

    Node parent = t.getScopeRoot();
    if (parent.isFunction()) {
      // Add the new node at the beginning of the function body.
      parent = parent.getLastChild();
    }
    if (parent.isSyntheticBlock()) {
      // Add the new node inside the SCRIPT node instead of the
      // synthetic block that contains it.
      parent = parent.getFirstChild();
    }

    Node name = IR.name(THIS_VAR).srcref(parent);
    Node thisVar = IR.var(name, IR.thisNode().srcref(parent));
    thisVar.srcref(parent);
    parent.addChildToFront(thisVar);
    scope.declare(THIS_VAR, name, null, compiler.getInput(parent.getInputId()));
  }

  private static class UpdateThisNodes implements NodeTraversal.Callback {
    private boolean changed = false;

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isThis()) {
        Node name = IR.name(THIS_VAR).srcref(n);
        parent.replaceChild(n, name);
        changed = true;
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isFunction() || n.isArrowFunction();
    }
  }
}
