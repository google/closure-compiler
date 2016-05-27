/*
 * Copyright 2016 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowStatementCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashSet;
import java.util.Set;

/**
 * The goal of this pass is to shrink the AST, preserving only typing, not behavior.
 *
 * <p>To do this, it does things like removing function/method bodies, rvalues that are not needed,
 * expressions that are not declarations, etc.
 *
 * <p>This is conceptually similar to the ijar tool[1] that bazel uses to shrink jars into minimal
 * versions that can be used equivalently for compilation of downstream dependencies.

 * [1] https://github.com/bazelbuild/bazel/blob/master/third_party/ijar/README.txt
 *
 * @author blickly@google.com (Ben Lickly)
 */
class RemoveBodies extends AbstractPreOrderCallback implements CompilerPass {

  private final AbstractCompiler compiler;

  private final Set<String> seenNames = new HashSet<>();

  RemoveBodies(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, externs, this);
    NodeTraversal.traverseEs6(compiler, root, this);
    seenNames.clear();
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.FUNCTION: {
        if (parent.isCall()) {
          Preconditions.checkState(!parent.getFirstChild().matchesQualifiedName("goog.scope"),
              parent);
        }
        Node body = n.getLastChild();
        if (body.isBlock() && body.hasChildren()) {
          if (isConstructor(n)) {
            processConstructor(n);
          }
          n.getLastChild().removeChildren();
          compiler.reportCodeChange();
        }
        break;
      }
      case Token.EXPR_RESULT:
        Node expr = n.getFirstChild();
        switch (expr.getType()) {
          case Token.NUMBER:
          case Token.STRING:
            n.detachFromParent();
            compiler.reportCodeChange();
            break;
          case Token.CALL:
            Preconditions.checkState(!n.getFirstChild().matchesQualifiedName("goog.scope"), n);
            Preconditions.checkState(
                !n.getFirstChild().matchesQualifiedName("goog.forwardDeclare"), n);
            n.detachFromParent();
            compiler.reportCodeChange();
            break;
          case Token.ASSIGN:
            processName(expr.getFirstChild(), n);
            break;
          case Token.GETPROP:
            processName(expr, n);
            break;
          default:
            if (expr.getJSDocInfo() == null) {
              n.detachFromParent();
              compiler.reportCodeChange();
            }
            break;
        }
        break;
      case Token.VAR:
      case Token.CONST:
      case Token.LET:
        if (n.getChildCount() == 1) {
          processName(n.getFirstChild(), n);
        }
        break;
      case Token.THROW:
      case Token.RETURN:
      case Token.BREAK:
      case Token.CONTINUE:
      case Token.DEBUGGER:
        n.detachFromParent();
        compiler.reportCodeChange();
        break;
      case Token.FOR_OF:
      case Token.DO:
      case Token.WHILE:
      case Token.FOR: {
        Node body = NodeUtil.getLoopCodeBlock(n);
        parent.replaceChild(n, body.detachFromParent());
        Node initializer = n.isFor() ? n.getFirstChild() : IR.empty();
        if (NodeUtil.isNameDeclaration(initializer)) {
          parent.addChildBefore(initializer.detachFromParent(), body);
          processName(initializer.getFirstChild(), initializer);
        }
        compiler.reportCodeChange();
        break;
      }
      case Token.LABEL:
        parent.replaceChild(n, n.getSecondChild().detachFromParent());
        compiler.reportCodeChange();
        break;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.TRY:
      case Token.DEFAULT_CASE:
        parent.replaceChild(n, n.getFirstChild().detachFromParent());
        compiler.reportCodeChange();
        break;
      case Token.IF:
      case Token.SWITCH:
      case Token.CASE:
        parent.addChildrenAfter(n.removeChildren().getNext(), n);
        n.detachFromParent();
        compiler.reportCodeChange();
        break;
    }
  }

  private void processConstructor(final Node function) {
    final String className = getClassName(function);
    if (className == null) {
      return;
    }
    final Node insertionPoint = NodeUtil.getEnclosingStatement(function);
    NodeTraversal.traverseEs6(
        compiler, function.getLastChild(), new AbstractShallowStatementCallback() {
          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (n.isExprResult()) {
              Node expr = n.getFirstChild();
              Node name = expr.isAssign() ? expr.getFirstChild() : expr;
              if (!name.isGetProp() || !name.getFirstChild().isThis()) {
                return;
              }
              String pname = name.getLastChild().getString();
              JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(name);
              Node newProtoAssignStmt = NodeUtil.newQNameDeclaration(
                  compiler, className + ".prototype." + pname, null, jsdoc);
              newProtoAssignStmt.useSourceInfoIfMissingFromForTree(expr);
              insertionPoint.getParent().addChildAfter(newProtoAssignStmt, insertionPoint);
              compiler.reportCodeChange();
            }
          }
        });
  }

  enum RemovalType {
    PRESERVE_ALL,
    REMOVE_RHS,
    REMOVE_ALL,
    UNDECLARED,
  }

  private static boolean isClassMemberFunction(Node functionNode) {
    Preconditions.checkArgument(functionNode.isFunction());
    return functionNode.getParent().isMemberFunctionDef()
        && functionNode.getGrandparent().isClassMembers();
  }

  private static String getClassName(Node functionNode) {
    if (isClassMemberFunction(functionNode)) {
      Node classNode = functionNode.getGrandparent().getParent();
      Preconditions.checkState(classNode.isClass());
      return NodeUtil.getName(classNode);
    }
    return NodeUtil.getName(functionNode);
  }

  private static boolean isConstructor(Node functionNode) {
    if (isClassMemberFunction(functionNode)) {
      return "constructor".equals(functionNode.getParent().getString());
    }
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(functionNode);
    return jsdoc != null && jsdoc.isConstructor();
  }

  private RemovalType shouldRemove(Node nameNode) {
    Node jsdocNode = NodeUtil.getBestJSDocInfoNode(nameNode);
    JSDocInfo jsdoc = jsdocNode.getJSDocInfo();
    if (jsdoc != null && jsdoc.isConstructorOrInterface()) {
      return RemovalType.PRESERVE_ALL;
    }
    Node rhs = NodeUtil.getRValueOfLValue(nameNode);
    // System.err.println("RHS of " + nameNode + " is " + rhs);
    if (rhs == null
        || rhs.isFunction()
        || rhs.isQualifiedName() && rhs.matchesQualifiedName("goog.abstractMethod")
        || rhs.isQualifiedName() && rhs.matchesQualifiedName("goog.nullFunction")
        // || rhs.isQualifiedName() && NodeUtil.isPrototypeProperty(rhs)
        || rhs.isObjectLit() && !rhs.hasChildren()
           && (jsdoc == null || jsdoc.getTypeNodes().isEmpty())
        ) {
      return RemovalType.PRESERVE_ALL;
    }
    if (jsdoc == null
        || !jsdoc.containsDeclaration()) {
      if (seenNames.contains(nameNode.getQualifiedName())) {
        return RemovalType.REMOVE_ALL;
      }
      return RemovalType.UNDECLARED;
    }
    return RemovalType.REMOVE_RHS;
  }

  private void processName(Node nameNode, Node statement) {
    Preconditions.checkState(NodeUtil.isStatement(statement), statement);
    if (!nameNode.isQualifiedName()) {
      // We don't track these. We can just remove them.
      removeNode(statement);
      return;
    }
    Node jsdocNode = NodeUtil.getBestJSDocInfoNode(nameNode);
    switch (shouldRemove(nameNode)) {
      case REMOVE_ALL:
        removeNode(statement);
        break;
      case PRESERVE_ALL:
        break;
      case UNDECLARED:
        jsdocNode.setJSDocInfo(getAllTypeJSDoc());
        // Fall-through
      case REMOVE_RHS:
        maybeRemoveRhs(nameNode, statement, jsdocNode.getJSDocInfo());
        break;
    }
    seenNames.add(nameNode.getQualifiedName());
  }

  private static JSDocInfo getAllTypeJSDoc() {
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordType(new JSTypeExpression(new Node(Token.STAR), ""));
    return builder.build();
  }

  private void removeNode(Node n) {
    if (NodeUtil.isStatement(n)) {
      n.detachFromParent();
    } else {
      n.getParent().replaceChild(n, IR.empty().srcref(n));
    }
    compiler.reportCodeChange();
  }

  private void maybeRemoveRhs(Node nameNode, Node statement, JSDocInfo jsdoc) {
    if (jsdoc != null) {
      if (jsdoc.hasConstAnnotation() && !jsdoc.hasType() && !NodeUtil.isNamespaceDecl(nameNode)) {
        // Inferred @const types require the RHS to be left in.
        return;
      }
      if (jsdoc.hasEnumParameterType()) {
        removeEnumValues(NodeUtil.getRValueOfLValue(nameNode));
        return;
      }
    }
    Node newStatement =
        NodeUtil.newQNameDeclaration(compiler, nameNode.getQualifiedName(), null, jsdoc);
    newStatement.useSourceInfoIfMissingFromForTree(nameNode);
    statement.getParent().replaceChild(statement, newStatement);
    compiler.reportCodeChange();
  }

  private void removeEnumValues(Node objLit) {
    if (objLit.isObjectLit() && objLit.hasChildren()) {
      for (Node key : objLit.children()) {
        Node value = key.getFirstChild();
        Node replacementValue = IR.number(0).srcrefTree(value);
        key.replaceChild(value, replacementValue);
      }
      compiler.reportCodeChange();
    }
  }
}
