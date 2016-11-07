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
import com.google.javascript.jscomp.NodeTraversal.AbstractModuleCallback;
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
class ConvertToTypedInterface implements CompilerPass {

  static final DiagnosticType CONSTANT_WITHOUT_EXPLICIT_TYPE =
      DiagnosticType.warning(
          "JSC_CONSTANT_WITHOUT_EXPLICIT_TYPE",
          "/** @const */-annotated values in library API should have types explicitly specified.");

  static final DiagnosticType UNSUPPORTED_GOOG_SCOPE =
      DiagnosticType.warning(
          "JSC_UNSUPPORTED_GOOG_SCOPE",
          "goog.scope is not supported inside .i.js files.");

  private final AbstractCompiler compiler;

  ConvertToTypedInterface(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, new PropagateConstJsdoc(compiler));
    NodeTraversal.traverseRootsEs6(compiler, new RemoveCode(compiler), externs, root);
  }

  private static class PropagateConstJsdoc extends NodeTraversal.AbstractPostOrderCallback {
    private final AbstractCompiler compiler;

    PropagateConstJsdoc(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case EXPR_RESULT:
          if (NodeUtil.isExprAssign(n)) {
            Node expr = n.getFirstChild();
            propagateJsdocAtName(t, expr.getFirstChild());
          }
          break;
        case VAR:
        case CONST:
        case LET:
          if (n.hasOneChild()) {
            propagateJsdocAtName(t, n.getFirstChild());
          }
          break;
        default:
          break;
      }
    }

    private void propagateJsdocAtName(NodeTraversal t, Node nameNode) {
      Node jsdocNode = NodeUtil.getBestJSDocInfoNode(nameNode);
      JSDocInfo jsdoc = jsdocNode.getJSDocInfo();
      if (!isInferrableConst(jsdoc, nameNode)) {
        return;
      }
      Node rhs = NodeUtil.getRValueOfLValue(nameNode);
      if (rhs == null) {
        return;
      }
      JSDocInfo newJsdoc = getJSDocForRhs(t, rhs, jsdoc);
      if (newJsdoc != null) {
        jsdocNode.setJSDocInfo(newJsdoc);
        compiler.reportCodeChange();
      }
    }

    private static JSDocInfo getJSDocForRhs(NodeTraversal t, Node rhs, JSDocInfo oldJSDoc) {
      switch (NodeUtil.getKnownValueType(rhs)) {
        case BOOLEAN:
          return getConstJSDoc(oldJSDoc, "boolean");
        case NUMBER:
          return getConstJSDoc(oldJSDoc, "number");
        case STRING:
          return getConstJSDoc(oldJSDoc, "string");
        case NULL:
          return getConstJSDoc(oldJSDoc, "null");
        case VOID:
          return getConstJSDoc(oldJSDoc, "void");
        case OBJECT:
          if (rhs.isRegExp()) {
            return getConstJSDoc(oldJSDoc, new Node(Token.BANG, IR.string("RegExp")));
          }
          break;
        case UNDETERMINED:
          if (rhs.isName()) {
            Var decl = t.getScope().getVar(rhs.getString());
            return getJSDocForName(decl, oldJSDoc);
          }
          break;
      }
      return null;
    }

    private static JSDocInfo getJSDocForName(Var decl, JSDocInfo oldJSDoc) {
      if (decl == null) {
        return null;
      }
      JSTypeExpression expr = NodeUtil.getDeclaredTypeExpression(decl.getNameNode());
      if (expr == null) {
        return null;
      }
      switch (expr.getRoot().getToken()) {
        case EQUALS:
          Node typeRoot = expr.getRoot().getFirstChild().cloneTree();
          if (!decl.isDefaultParam()) {
            typeRoot = new Node(Token.PIPE, typeRoot, IR.string("undefined"));
          }
          expr = asTypeExpression(typeRoot);
          break;
        case ELLIPSIS:
          {
            Node type = new Node(Token.BANG);
            Node array = IR.string("Array");
            type.addChildToBack(array);
            Node block = new Node(Token.BLOCK, expr.getRoot().getFirstChild().cloneTree());
            array.addChildToBack(block);
            expr = asTypeExpression(type);
            break;
          }
        default:
          break;
      }
      return getConstJSDoc(oldJSDoc, expr);
    }

  }

  private static class RemoveCode extends AbstractModuleCallback {
    private final AbstractCompiler compiler;
    private final Set<String> globalSeenNames = new HashSet<>();
    private Node currentModule = null;
    private Set<String> moduleSeenNames;

    RemoveCode(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void enterModule(NodeTraversal t, Node scopeRoot) {
      currentModule = scopeRoot;
      moduleSeenNames = new HashSet<>();
    }

    @Override
    public void exitModule(NodeTraversal t, Node scopeRoot) {
      currentModule = null;
      moduleSeenNames = null;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
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
          switch (expr.getToken()) {
            case NUMBER:
            case STRING:
              n.detach();
              compiler.reportCodeChange();
              break;
            case CALL:
              Node callee = expr.getFirstChild();
              if (callee.matchesQualifiedName("goog.scope")) {
                t.report(n, UNSUPPORTED_GOOG_SCOPE);
                return false;
              } else if (callee.matchesQualifiedName("goog.provide")) {
                Node childBefore;
                while (null != (childBefore = n.getPrevious())
                    && childBefore.getBooleanProp(Node.IS_NAMESPACE)) {
                  parent.removeChild(childBefore);
                  compiler.reportCodeChange();
                }
              } else if (!callee.matchesQualifiedName("goog.require")
                  && !callee.matchesQualifiedName("goog.module")) {
                n.detach();
                compiler.reportCodeChange();
              }
              break;
            case ASSIGN:
              if (t.inModuleScope() && expr.getFirstChild().matchesQualifiedName("exports")) {
                // Module exports shouldn't be renamed
                break;
              }
              processName(expr.getFirstChild(), n);
              break;
            case GETPROP:
              processName(expr, n);
              break;
            default:
              if (expr.getJSDocInfo() == null) {
                n.detach();
                compiler.reportCodeChange();
              }
              break;
          }
          break;
        case VAR:
        case CONST:
        case LET:
          if (n.hasOneChild() && NodeUtil.isStatement(n) && !isModuleImport(n)) {
            processName(n.getFirstChild(), n);
          }
          break;
        case THROW:
        case RETURN:
        case BREAK:
        case CONTINUE:
        case DEBUGGER:
          NodeUtil.removeChild(parent, n);
          compiler.reportCodeChange();
          break;
        default:
          break;
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case TRY:
        case DEFAULT_CASE:
          parent.replaceChild(n, n.getFirstChild().detach());
          compiler.reportCodeChange();
          break;
        case IF:
        case SWITCH:
        case CASE:
          n.removeFirstChild();
          Node children = n.removeChildren();
          parent.addChildrenAfter(children, n);
          NodeUtil.removeChild(parent, n);
          compiler.reportCodeChange();
          break;
        case FOR_OF:
        case DO:
        case WHILE:
        case FOR: {
          Node body = NodeUtil.getLoopCodeBlock(n);
          parent.addChildAfter(body.detach(), n);
          NodeUtil.removeChild(parent, n);
          Node initializer = n.isFor() ? n.getFirstChild() : IR.empty();
          if (initializer.isVar() && initializer.hasOneChild()) {
            parent.addChildBefore(initializer.detach(), body);
            processName(initializer.getFirstChild(), initializer);
          }
          compiler.reportCodeChange();
          break;
        }
        case LABEL:
          if (n.getParent() != null) {
            parent.replaceChild(n, n.getSecondChild().detach());
            compiler.reportCodeChange();
          }
          break;
        default:
          break;
      }
    }

    private boolean isNameProcessed(String fullyQualifiedName) {
      if (currentModule == null) {
        return globalSeenNames.contains(fullyQualifiedName);
      } else {
        return moduleSeenNames.contains(fullyQualifiedName);
      }
    }

    private void markNameProcessed(String fullyQualifiedName) {
      if (currentModule == null) {
        globalSeenNames.add(fullyQualifiedName);
      } else {
        moduleSeenNames.add(fullyQualifiedName);
      }
    }

    private void processConstructor(final Node function) {
      final String className = getClassName(function);
      if (className == null) {
        return;
      }
      final Node insertionPoint = NodeUtil.getEnclosingStatement(function);
      NodeTraversal.traverseEs6(
          compiler,
          function.getLastChild(),
          new AbstractShallowStatementCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.isExprResult()) {
                Node expr = n.getFirstChild();
                Node name = expr.isAssign() ? expr.getFirstChild() : expr;
                if (!name.isGetProp() || !name.getFirstChild().isThis()) {
                  return;
                }
                String pname = name.getLastChild().getString();
                String fullyQualifiedName = className + ".prototype." + pname;
                if (isNameProcessed(fullyQualifiedName)) {
                  return;
                }
                JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(name);
                if (jsdoc == null) {
                  jsdoc = getAllTypeJSDoc();
                } else if (isInferrableConst(jsdoc, name)) {
                  jsdoc = pullJsdocTypeFromAst(compiler, jsdoc, name);
                }
                Node newProtoAssignStmt =
                    NodeUtil.newQNameDeclaration(compiler, fullyQualifiedName, null, jsdoc);
                newProtoAssignStmt.useSourceInfoIfMissingFromForTree(expr);
                // TODO(blickly): Preserve the declaration order of the this properties.
                insertionPoint.getParent().addChildAfter(newProtoAssignStmt, insertionPoint);
                compiler.reportCodeChange();
                markNameProcessed(fullyQualifiedName);
              }
            }
          });
    }

    enum RemovalType {
      PRESERVE_ALL,
      REMOVE_RHS,
      REMOVE_ALL,
    }

    private static boolean isImportRhs(Node rhs) {
      if (!rhs.isCall()) {
        return false;
      }
      Node callee = rhs.getFirstChild();
      return callee.matchesQualifiedName("goog.require")
          || callee.matchesQualifiedName("goog.forwardDeclare");
    }

    private static boolean isExportLhs(Node lhs) {
      return (lhs.isName() && lhs.matchesQualifiedName("exports"))
          || (lhs.isGetProp() && lhs.getFirstChild().matchesQualifiedName("exports"));
    }

    private RemovalType shouldRemove(Node nameNode) {
      Node jsdocNode = NodeUtil.getBestJSDocInfoNode(nameNode);
      JSDocInfo jsdoc = jsdocNode.getJSDocInfo();
      Node rhs = NodeUtil.getRValueOfLValue(nameNode);
      if (rhs == null
          || rhs.isFunction()
          || rhs.isClass()
          || NodeUtil.isCallTo(rhs, "goog.defineClass")
          || isImportRhs(rhs)
          || isExportLhs(nameNode)
          || (rhs.isQualifiedName() && rhs.matchesQualifiedName("goog.abstractMethod"))
          || (rhs.isQualifiedName() && rhs.matchesQualifiedName("goog.nullFunction"))
          || (rhs.isObjectLit()
              && !rhs.hasChildren()
              && (jsdoc == null || !hasAnnotatedType(jsdoc)))) {
        return RemovalType.PRESERVE_ALL;
      }
      if (jsdoc == null
          || !jsdoc.containsDeclaration()) {
        if (isNameProcessed(nameNode.getQualifiedName())) {
          return RemovalType.REMOVE_ALL;
        }
        jsdocNode.setJSDocInfo(getAllTypeJSDoc());
        return RemovalType.REMOVE_RHS;
      }
      if (isInferrableConst(jsdoc, nameNode)) {
        jsdocNode.setJSDocInfo(pullJsdocTypeFromAst(compiler, jsdoc, nameNode));
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
        case REMOVE_RHS:
          maybeRemoveRhs(nameNode, statement, jsdocNode.getJSDocInfo());
          break;
      }
      markNameProcessed(nameNode.getQualifiedName());
    }

    private void removeNode(Node n) {
      if (NodeUtil.isStatement(n)) {
        n.detach();
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

  private static boolean isInferrableConst(JSDocInfo jsdoc, Node nameNode) {
    boolean isConst =
        nameNode.getParent().isConst() || (jsdoc != null && jsdoc.hasConstAnnotation());
    return isConst
        && !hasAnnotatedType(jsdoc)
        && !NodeUtil.isNamespaceDecl(nameNode);
  }

  private static boolean hasAnnotatedType(JSDocInfo jsdoc) {
    if (jsdoc == null) {
      return false;
    }
    return jsdoc.hasType()
        || jsdoc.hasReturnType()
        || jsdoc.getParameterCount() > 0
        || jsdoc.isConstructorOrInterface()
        || jsdoc.hasThisType()
        || jsdoc.hasEnumParameterType();
  }

  private static boolean isModuleImport(Node importNode) {
    Preconditions.checkArgument(NodeUtil.isNameDeclaration(importNode));
    Preconditions.checkArgument(importNode.hasOneChild());
    Node declarationLhs = importNode.getFirstChild();
    if (declarationLhs.hasChildren()) {
      Node importRhs = declarationLhs.getLastChild();
      return NodeUtil.isCallTo(importRhs, "goog.require")
          || NodeUtil.isCallTo(importRhs, "goog.forwardDeclare");
    }
    return false;
  }

  private static boolean isClassMemberFunction(Node functionNode) {
    Preconditions.checkArgument(functionNode.isFunction());
    Node parent = functionNode.getParent();
    if (parent.isMemberFunctionDef()
        && parent.getParent().isClassMembers()) {
      // ES6 class
      return true;
    }
    // goog.defineClass
    return parent.isStringKey()
        && parent.getParent().isObjectLit()
        && parent.getGrandparent().isCall()
        && parent.getGrandparent().getFirstChild().matchesQualifiedName("goog.defineClass");
  }

  private static String getClassName(Node functionNode) {
    if (isClassMemberFunction(functionNode)) {
      Node parent = functionNode.getParent();
      if (parent.isMemberFunctionDef()) {
        // ES6 class
        Node classNode = functionNode.getGrandparent().getParent();
        Preconditions.checkState(classNode.isClass());
        return NodeUtil.getName(classNode);
      }
      // goog.defineClass
      Preconditions.checkState(parent.isStringKey());
      return parent.getGrandparent().getParent().getString();
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

  private static JSDocInfo pullJsdocTypeFromAst(
      AbstractCompiler compiler, JSDocInfo oldJSDoc, Node nameNode) {
    Preconditions.checkArgument(nameNode.isQualifiedName());
    JSType type = nameNode.getJSType();
    if (type == null) {
      if (!nameNode.isFromExterns()) {
        compiler.report(JSError.make(nameNode, CONSTANT_WITHOUT_EXPLICIT_TYPE));
      }
      return getConstJSDoc(oldJSDoc, new Node(Token.STAR));
    } else {
      return getConstJSDoc(oldJSDoc, type.toNonNullAnnotationString());
    }
  }

  private static JSDocInfo getAllTypeJSDoc() {
    return getConstJSDoc(null, new Node(Token.STAR));
  }

  private static JSTypeExpression asTypeExpression(Node typeAst) {
    return new JSTypeExpression(typeAst, "<synthetic>");
  }

  private static JSDocInfo getConstJSDoc(JSDocInfo oldJSDoc, String contents) {
    return getConstJSDoc(oldJSDoc, Node.newString(contents));
  }

  private static JSDocInfo getConstJSDoc(JSDocInfo oldJSDoc, Node typeAst) {
    return getConstJSDoc(oldJSDoc, asTypeExpression(typeAst));
  }

  private static JSDocInfo getConstJSDoc(JSDocInfo oldJSDoc, JSTypeExpression newType) {
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(oldJSDoc);
    builder.recordType(newType);
    builder.recordConstancy();
    return builder.build();
  }
}
