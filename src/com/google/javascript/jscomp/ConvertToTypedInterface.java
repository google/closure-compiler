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
import com.google.javascript.rhino.jstype.JSType;

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
class ConvertToTypedInterface extends AbstractPreOrderCallback implements CompilerPass {

  private final AbstractCompiler compiler;

  private final Set<String> seenNames = new HashSet<>();

  ConvertToTypedInterface(AbstractCompiler compiler) {
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
      case FUNCTION: {
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
      case EXPR_RESULT:
        Node expr = n.getFirstChild();
        switch (expr.getType()) {
          case NUMBER:
          case STRING:
            n.detachFromParent();
            compiler.reportCodeChange();
            break;
          case CALL:
            Node callee = expr.getFirstChild();
            Preconditions.checkState(!callee.matchesQualifiedName("goog.scope"), n);
            Preconditions.checkState(!callee.matchesQualifiedName("goog.forwardDeclare"), n);
            if (callee.matchesQualifiedName("goog.provide")) {
              Node childBefore;
              while (null != (childBefore = parent.getChildBefore(n))
                  && childBefore.getBooleanProp(Node.IS_NAMESPACE)) {
                parent.removeChild(childBefore);
                compiler.reportCodeChange();
              }
            } else if (!callee.matchesQualifiedName("goog.require")) {
              n.detachFromParent();
              compiler.reportCodeChange();
            }
            break;
          case ASSIGN:
            processName(expr.getFirstChild(), n);
            break;
          case GETPROP:
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
      case VAR:
      case CONST:
      case LET:
        if (n.getChildCount() == 1) {
          processName(n.getFirstChild(), n);
        }
        break;
      case THROW:
      case RETURN:
      case BREAK:
      case CONTINUE:
      case DEBUGGER:
        n.detachFromParent();
        compiler.reportCodeChange();
        break;
      case FOR_OF:
      case DO:
      case WHILE:
      case FOR: {
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
      case LABEL:
        parent.replaceChild(n, n.getSecondChild().detachFromParent());
        compiler.reportCodeChange();
        break;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case TRY:
      case DEFAULT_CASE:
        parent.replaceChild(n, n.getFirstChild().detachFromParent());
        compiler.reportCodeChange();
        break;
      case IF:
      case SWITCH:
      case CASE:
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
              JSType type = name.getJSType();
              String pname = name.getLastChild().getString();
              JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(name);
              // Don't use an initializer unless we need it for the non-typechecked case.
              Node initializer = null;
              if (type == null) {
                initializer = NodeUtil.getRValueOfLValue(name).detachFromParent();
              } else if (isInferrableConst(jsdoc)) {
                jsdoc = maybeUpdateJSDocInfoWithType(jsdoc, name);
              }
              Node newProtoAssignStmt = NodeUtil.newQNameDeclaration(
                  compiler, className + ".prototype." + pname, initializer, jsdoc);
              newProtoAssignStmt.useSourceInfoIfMissingFromForTree(expr);
              // TODO(blickly): Preserve the declaration order of the this properties.
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
      jsdocNode.setJSDocInfo(getAllTypeJSDoc());
      return RemovalType.REMOVE_RHS;
    }
    if (isInferrableConst(jsdoc) && !NodeUtil.isNamespaceDecl(nameNode)) {
      if (nameNode.getJSType() == null) {
        return RemovalType.PRESERVE_ALL;
      }
      jsdocNode.setJSDocInfo(maybeUpdateJSDocInfoWithType(jsdoc, nameNode));
    }
    return RemovalType.REMOVE_RHS;
  }

  private static boolean isInferrableConst(JSDocInfo jsdoc) {
    return jsdoc != null && jsdoc.hasConstAnnotation() && !jsdoc.hasType();
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

  private static JSDocInfo maybeUpdateJSDocInfoWithType(JSDocInfo oldJSDoc, Node nameNode) {
    Preconditions.checkArgument(nameNode.isQualifiedName());
    JSType type = nameNode.getJSType();
    if (type == null) {
      return oldJSDoc;
    }
    return getTypeJSDoc(oldJSDoc, type.toNonNullAnnotationString());
  }

  private static JSDocInfo getTypeJSDoc(JSDocInfo oldJSDoc, String contents) {
    JSDocInfoBuilder builder = JSDocInfoBuilder.copyFrom(oldJSDoc);
    builder.recordType(new JSTypeExpression(Node.newString(contents), ""));
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
    if (jsdoc != null && jsdoc.hasEnumParameterType()) {
      removeEnumValues(NodeUtil.getRValueOfLValue(nameNode));
      return;
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
