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

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Converts ES6 code to valid ES3 code.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public class Es6ToEs3Converter implements NodeTraversal.Callback, CompilerPass {
  private final AbstractCompiler compiler;

  private final LanguageMode languageIn;
  private final LanguageMode languageOut;

  static final DiagnosticType CANNOT_CONVERT = DiagnosticType.error(
      "JSC_CANNOT_CONVERT",
      "Conversion of ''{0}'' to ES3 is not yet implemented.");

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

  /**
   * Arrow functions must be visited pre-order in order to rewrite the
   * references to {@code this} correctly.
   * Everything else is translated post-order in {@link #visit}.
   */
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

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.STRING_KEY:
        visitStringKey(n);
        break;
      case Token.CLASS:
        visitClass(n);
        break;
      case Token.SUPER:
        cannotConvert(n, "super");
        break;
    }
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

  private void visitClass(Node classNode) {
    Node className = classNode.getFirstChild();
    Node superClassName = className.getNext();
    Node classMembers = classNode.getLastChild();

    if (!NodeUtil.isStatement(classNode)) {
      cannotConvert(classNode, "class expression");
      return;
    }
    if (!superClassName.isEmpty()) {
      cannotConvert(classNode, "extends");
      return;
    }

    className.detachFromParent();

    Node constructor = null;
    JSDocInfo ctorJSDocInfo = null;
    Node insertionPoint = classNode;
    for (Node member : classMembers.children()) {
      if (member.isStaticMember()) {
        cannotConvert(member, "static member");
      }
      if (member.getString().equals("constructor")) {
        ctorJSDocInfo = member.getJSDocInfo();
        constructor = member.getFirstChild().detachFromParent();
        constructor.replaceChild(constructor.getFirstChild(), className);
      } else {
        String qualName = Joiner.on('.').join(
            className.getString(),
            "prototype",
            member.getString());
        Node assign = IR.assign(
            NodeUtil.newQualifiedNameNode(
                compiler.getCodingConvention(),
                qualName,
                /* basis node */ member,
                /* original name */ member.getString()),
            member.getFirstChild().detachFromParent());
        assign.srcref(member);

        JSDocInfo info = member.getJSDocInfo();
        if (info != null) {
          info.setAssociatedNode(assign);
          assign.setJSDocInfo(info);
        }

        Node newNode = NodeUtil.newExpr(assign);
        insertionPoint.getParent().addChildAfter(newNode, insertionPoint);
        insertionPoint = newNode;
      }
    }

    if (constructor == null) {
      constructor = IR.function(
          className,
          IR.paramList().srcref(classNode),
          IR.block().srcref(classNode));
    }
    JSDocInfo classJSDoc = classNode.getJSDocInfo();
    JSDocInfoBuilder newInfo = (classJSDoc != null) ?
        JSDocInfoBuilder.copyFrom(classJSDoc) :
        new JSDocInfoBuilder(true);

    newInfo.recordConstructor();
    // TODO(tbreisacher): Add @extends if there is a superclass.

    // Classes are @struct by default.
    if (!newInfo.isUnrestrictedRecorded() && !newInfo.isDictRecorded() &&
        !newInfo.isStructRecorded()) {
      newInfo.recordStruct();
    }

    if (ctorJSDocInfo != null) {
      newInfo.recordSuppressions(ctorJSDocInfo.getSuppressions());
      for (String param : ctorJSDocInfo.getParameterNames()) {
        newInfo.recordParameter(param, ctorJSDocInfo.getParameterType(param));
      }
    }
    constructor.setJSDocInfo(newInfo.build(constructor));
    classNode.getParent().replaceChild(classNode, constructor);

    compiler.reportCodeChange();
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

  /**
   * Warns the user that the given ES6 feature cannot be converted to ES3.
   */
  private void cannotConvert(Node n, String feature) {
    compiler.report(JSError.make(n, CANNOT_CONVERT, feature));
  }
}
