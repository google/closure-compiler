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
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowStatementCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

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

  private final AbstractCompiler compiler;

  ConvertToTypedInterface(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private void unhoistExternsToCode(Node externs, Node root) {
    root.removeChildren();
    while (externs.hasChildren()) {
      Node extern = externs.removeFirstChild();
      root.addChildToBack(extern);
      compiler.reportChangeToChangeScope(extern);
    }
  }

  @Override
  public void process(Node externs, Node root) {
    if (!root.hasChildren() || (root.hasOneChild() && !root.getFirstChild().hasChildren())) {
      unhoistExternsToCode(externs, root);
      return;
    }
    NodeTraversal.traverseEs6(compiler, root, new RemoveNonDeclarations());
    NodeTraversal.traverseEs6(compiler, root, new PropagateConstJsdoc());
    SimplifyDeclarations simplify = new SimplifyDeclarations(compiler);
    NodeTraversal.traverseEs6(compiler, root, simplify);
  }

  private static @Nullable Var findNameDeclaration(NodeTraversal t, Node rhs) {
    if (!rhs.isName()) {
      return null;
    }
    return t.getScope().getVar(rhs.getString());
  }

  private static class RemoveNonDeclarations implements NodeTraversal.Callback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case FUNCTION:
          if (!isConstructor(n)) {
            Node body = n.getLastChild();
            if (!body.isNormalBlock() || body.hasChildren()) {
              t.reportCodeChange(body);
              body.replaceWith(IR.block().srcref(body));
            }
          }
          return true;
        case EXPR_RESULT:
          Node expr = n.getFirstChild();
          switch (expr.getToken()) {
            case CALL:
              Node callee = expr.getFirstChild();
              Preconditions.checkState(!callee.matchesQualifiedName("goog.scope"));
              if (!callee.matchesQualifiedName("goog.provide")
                  && !callee.matchesQualifiedName("goog.define")
                  && !callee.matchesQualifiedName("goog.require")
                  && !callee.matchesQualifiedName("goog.module")) {
                n.detach();
                t.reportCodeChange();
              }
              return false;
            case ASSIGN:
              if (expr.getFirstChild().isName() && !t.inGlobalScope() && !t.inModuleScope()) {
                NodeUtil.removeChild(parent, n);
                t.reportCodeChange(parent);
                return false;
              }
              return true;
            case GETPROP:
              return true;
            default:
              n.detach();
              t.reportCodeChange();
              return false;
          }
        case THROW:
        case RETURN:
        case BREAK:
        case CONTINUE:
        case DEBUGGER:
        case EMPTY:
          if (NodeUtil.isStatementParent(parent)) {
            NodeUtil.removeChild(parent, n);
            t.reportCodeChange();
          }
          return false;
        case LABEL:
        case IF:
        case SWITCH:
        case CASE:
        case WHILE:
          // First child can't have declaration. Statement itself will be removed post-order.
          n.removeFirstChild();
          return true;
        case TRY:
        case DO:
          // Second child can't have declarations. Statement itself will be removed post-order.
          n.getSecondChild().detach();
          return true;
        case FOR:
          n.getSecondChild().detach();
          // fall-through
        case FOR_OF:
        case FOR_IN:
          n.getSecondChild().detach();
          Node initializer = n.removeFirstChild();
          if (initializer.isVar()) {
            parent.addChildBefore(initializer, n);
          }
          t.reportCodeChange(parent);
          return true;
        case CONST:
        case LET:
          if (!t.inGlobalScope() && !t.inModuleScope()) {
            NodeUtil.removeChild(parent, n);
            t.reportCodeChange(parent);
            return false;
          }
          return true;
        case VAR:
          if (!t.inGlobalHoistScope() && !t.inModuleHoistScope()) {
            NodeUtil.removeChild(parent, n);
            t.reportCodeChange(parent);
            return false;
          }
          return true;
        case MODULE_BODY:
        case CLASS:
        case DEFAULT_CASE:
        case BLOCK:
        case EXPORT:
        case IMPORT:
          return true;
        default:
          Preconditions.checkState(!NodeUtil.isStatement(n), n.getToken());
          return true;
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case TRY:
        case LABEL:
        case DEFAULT_CASE:
        case CASE:
        case DO:
        case WHILE:
        case FOR:
        case FOR_IN:
        case FOR_OF:
        case IF:
        case SWITCH:
          if (n.getParent() != null) {
            Node children = n.removeChildren();
            parent.addChildrenAfter(children, n);
            NodeUtil.removeChild(parent, n);
            t.reportCodeChange();
          }
          break;
        case VAR:
        case LET:
        case CONST:
          while (n.hasMoreThanOneChild()) {
            Node nameToSplit = n.getLastChild().detach();
            Node rhs = nameToSplit.hasChildren() ? nameToSplit.removeFirstChild() : null;
            Node newDeclaration = IR.declaration(nameToSplit, rhs, n.getToken()).srcref(n);
            parent.addChildAfter(newDeclaration, n);
            t.reportCodeChange();
          }
          break;
        default:
          break;
      }
    }
  }

  private static class PropagateConstJsdoc extends NodeTraversal.AbstractPostOrderCallback {
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
      if (!isConstToBeInferred(jsdoc, nameNode, false)) {
        return;
      }
      Node rhs = NodeUtil.getRValueOfLValue(nameNode);
      if (rhs == null) {
        return;
      }
      JSDocInfo newJsdoc = JsdocUtil.getJSDocForRhs(rhs, jsdoc);
      if (newJsdoc == null && nameNode.isGetProp() && nameNode.getFirstChild().isThis()) {
        Var decl = findNameDeclaration(t, rhs);
        newJsdoc = JsdocUtil.getJSDocForName(decl, jsdoc);
      }
      if (newJsdoc != null) {
        jsdocNode.setJSDocInfo(newJsdoc);
        t.reportCodeChange();
      }
    }

  }

  /**
   * Class to keep track of what has been seen so far in a given file.
   *
   * This is cleared after each file to make sure that the analysis is working on a per-file basis.
   */
  private static class FileInfo {
    private final Set<String> providedNamespaces = new HashSet<>();
    private final Set<String> requiredLocalNames = new HashSet<>();
    private final Set<String> seenNames = new HashSet<>();
    private final List<Node> constructorsToProcess = new ArrayList<>();

    boolean isNameProcessed(String fullyQualifiedName) {
      return seenNames.contains(fullyQualifiedName);
    }

    boolean isPrefixProvided(String fullyQualifiedName) {
      for (String prefix : Iterables.concat(seenNames, providedNamespaces)) {
        if (fullyQualifiedName.startsWith(prefix)) {
          return true;
        }
      }
      return false;
    }

    boolean isRequiredName(String fullyQualifiedName) {
      return requiredLocalNames.contains(fullyQualifiedName);
    }

    void markConstructorToProcess(Node ctorNode) {
      Preconditions.checkArgument(ctorNode.isFunction());
      constructorsToProcess.add(ctorNode);
    }

    void markNameProcessed(String fullyQualifiedName) {
      seenNames.add(fullyQualifiedName);
    }

    void markProvided(String providedName) {
      providedNamespaces.add(providedName);
    }

    void markImportedName(String requiredLocalName) {
      requiredLocalNames.add(requiredLocalName);
    }

    void clear() {
      providedNamespaces.clear();
      seenNames.clear();
      constructorsToProcess.clear();
    }

  }

  private static class SimplifyDeclarations implements NodeTraversal.Callback {
    private final AbstractCompiler compiler;
    private final FileInfo currentFile = new FileInfo();

    SimplifyDeclarations(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    private void processConstructors(List<Node> constructorNodes) {
      for (Node ctorNode : constructorNodes) {
        processConstructor(ctorNode);
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case SCRIPT:
          currentFile.clear();
          break;
        case CLASS:
          if (NodeUtil.isStatementParent(parent)) {
            currentFile.markNameProcessed(n.getFirstChild().getString());
          }
          break;
        case FUNCTION:
          {
            if (NodeUtil.isStatementParent(parent)) {
              currentFile.markNameProcessed(n.getFirstChild().getString());
            }
            processFunctionParameters(n.getSecondChild());
            if (isConstructor(n) && n.getLastChild().hasChildren()) {
              currentFile.markConstructorToProcess(n);
            }
            return false;
          }
        case EXPR_RESULT:
          Node expr = n.getFirstChild();
          switch (expr.getToken()) {
            case CALL:
              Node callee = expr.getFirstChild();
              if (callee.matchesQualifiedName("goog.provide")) {
                currentFile.markProvided(expr.getLastChild().getString());
              } else if (callee.matchesQualifiedName("goog.define")) {
                expr.getLastChild().detach();
                t.reportCodeChange();
              } else if (callee.matchesQualifiedName("goog.require")) {
                processRequire(expr);
              } else {
                Preconditions.checkState(callee.matchesQualifiedName("goog.module"));
              }
              break;
            case ASSIGN:
              processName(t, expr.getFirstChild(), n);
              break;
            case GETPROP:
              processName(t, expr, n);
              break;
            default:
              throw new RuntimeException("Unexpected declaration: " + expr);
          }
          break;
        case VAR:
        case CONST:
        case LET:
          if (n.hasOneChild() && NodeUtil.isStatement(n)) {
            Node lhs = n.getFirstChild();
            Node rhs = lhs.getLastChild();
            if (rhs != null && isImportRhs(rhs)) {
              processRequire(rhs);
            } else {
              processName(t, lhs, n);
            }
          }
          break;
        default:
          break;
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        processConstructors(currentFile.constructorsToProcess);
      }
    }

    private void processRequire(Node requireNode) {
      Preconditions.checkArgument(requireNode.isCall());
      Preconditions.checkArgument(requireNode.getLastChild().isString());
      Node parent = requireNode.getParent();
      if (parent.isExprResult()) {
        currentFile.markImportedName(requireNode.getLastChild().getString());
      } else {
        for (Node importedName : NodeUtil.getLhsNodesOfDeclaration(parent.getParent())) {
          currentFile.markImportedName(importedName.getString());
        }
      }
    }

    private void processFunctionParameters(Node paramList) {
      Preconditions.checkArgument(paramList.isParamList());
      for (Node arg = paramList.getFirstChild(); arg != null; arg = arg.getNext()) {
        if (arg.isDefaultValue()) {
          Node replacement = arg.getFirstChild().detach();
          arg.replaceWith(replacement);
          arg = replacement;
          compiler.reportChangeToEnclosingScope(replacement);
        }
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
                if (currentFile.isNameProcessed(fullyQualifiedName)) {
                  return;
                }
                JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(name);
                if (jsdoc == null) {
                  jsdoc = JsdocUtil.getAllTypeJSDoc();
                } else if (isConstToBeInferred(jsdoc, name, false)) {
                  jsdoc = JsdocUtil.pullJsdocTypeFromAst(compiler, jsdoc, name);
                }
                Node newProtoAssignStmt =
                    NodeUtil.newQNameDeclaration(compiler, fullyQualifiedName, null, jsdoc);
                newProtoAssignStmt.useSourceInfoIfMissingFromForTree(expr);
                // TODO(blickly): Preserve the declaration order of the this properties.
                insertionPoint.getParent().addChildAfter(newProtoAssignStmt, insertionPoint);
                compiler.reportChangeToEnclosingScope(newProtoAssignStmt);
                currentFile.markNameProcessed(fullyQualifiedName);
              }
            }
          });
      final Node functionBody = function.getLastChild();
      Preconditions.checkState(functionBody.isNormalBlock());
      functionBody.removeChildren();
      compiler.reportChangeToEnclosingScope(functionBody);
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

    private RemovalType shouldRemove(NodeTraversal t, Node nameNode) {
      Node jsdocNode = NodeUtil.getBestJSDocInfoNode(nameNode);
      JSDocInfo jsdoc = jsdocNode.getJSDocInfo();
      Node rhs = NodeUtil.getRValueOfLValue(nameNode);
      boolean isExport = isExportLhs(nameNode);
      if (rhs == null
          || rhs.isFunction()
          || rhs.isClass()
          || NodeUtil.isCallTo(rhs, "goog.defineClass")
          || isImportRhs(rhs)
          || (isExport && (rhs.isQualifiedName() || rhs.isObjectLit()))
          || (rhs.isQualifiedName() && rhs.matchesQualifiedName("goog.abstractMethod"))
          || (rhs.isQualifiedName() && rhs.matchesQualifiedName("goog.nullFunction"))
          || (jsdoc != null && jsdoc.isConstructor() && rhs.isQualifiedName())
          || (rhs.isObjectLit()
              && !rhs.hasChildren()
              && (jsdoc == null || !JsdocUtil.hasAnnotatedType(jsdoc)))) {
        return RemovalType.PRESERVE_ALL;
      }
      if (!isExport
          && (jsdoc == null || !jsdoc.containsDeclaration())) {
        String fullyQualifiedName = nameNode.getQualifiedName();
        if (currentFile.isNameProcessed(fullyQualifiedName)) {
          return RemovalType.REMOVE_ALL;
        }
        if (isDeclaration(nameNode) || currentFile.isPrefixProvided(fullyQualifiedName)) {
          jsdocNode.setJSDocInfo(JsdocUtil.getAllTypeJSDoc());
          return RemovalType.REMOVE_RHS;
        }
        return RemovalType.REMOVE_ALL;
      }
      if (isConstToBeInferred(jsdoc, nameNode, isExport)) {
        if (rhs.isQualifiedName() && currentFile.isRequiredName(rhs.getQualifiedName())) {
          return RemovalType.PRESERVE_ALL;
        }

        Var originalDecl = findNameDeclaration(t, rhs);
        if (originalDecl != null) {
          if (originalDecl.isClass() || JsdocUtil.hasAnnotatedType(originalDecl.getJSDocInfo())) {
            return RemovalType.PRESERVE_ALL;
          }
        }

        jsdocNode.setJSDocInfo(JsdocUtil.pullJsdocTypeFromAst(compiler, jsdoc, nameNode));
      }
      return RemovalType.REMOVE_RHS;
    }

    private void processName(NodeTraversal t, Node nameNode, Node statement) {
     Preconditions.checkState(NodeUtil.isStatement(statement), statement);
      if (!nameNode.isQualifiedName()) {
        // We don't track these. We can just remove them.
        removeNode(statement);
        return;
      }
      Node jsdocNode = NodeUtil.getBestJSDocInfoNode(nameNode);
      switch (shouldRemove(t, nameNode)) {
        case REMOVE_ALL:
          removeNode(statement);
          break;
        case PRESERVE_ALL:
          break;
        case REMOVE_RHS:
          maybeRemoveRhs(t, nameNode, statement, jsdocNode.getJSDocInfo());
          break;
      }
      currentFile.markNameProcessed(nameNode.getQualifiedName());
    }

    private void removeNode(Node n) {
      compiler.reportChangeToEnclosingScope(n);
      if (NodeUtil.isStatement(n)) {
        n.detach();
      } else {
        n.replaceWith(IR.empty().srcref(n));
      }
    }

    private void maybeRemoveRhs(NodeTraversal t, Node nameNode, Node statement, JSDocInfo jsdoc) {
      if (jsdoc != null && jsdoc.hasEnumParameterType()) {
        removeEnumValues(t, NodeUtil.getRValueOfLValue(nameNode));
        return;
      }
      if (nameNode.matchesQualifiedName("exports")) {
        replaceRhsWithUnknown(nameNode);
        t.reportCodeChange();
        return;
      }
      Node newStatement =
          NodeUtil.newQNameDeclaration(compiler, nameNode.getQualifiedName(), null, jsdoc);
      newStatement.useSourceInfoIfMissingFromForTree(nameNode);
      statement.replaceWith(newStatement);
      t.reportCodeChange();
    }

    private void removeEnumValues(NodeTraversal t, Node objLit) {
      if (objLit.isObjectLit() && objLit.hasChildren()) {
        for (Node key : objLit.children()) {
          Node value = key.getFirstChild();
          Node replacementValue = IR.number(0).srcrefTree(value);
          key.replaceChild(value, replacementValue);
        }
        t.reportCodeChange();
      }
    }
  }

  private static void replaceRhsWithUnknown(Node lhs) {
    Node rhs = NodeUtil.getRValueOfLValue(lhs);
    rhs.replaceWith(IR.cast(IR.number(0), JsdocUtil.getQmarkTypeJSDoc()).srcrefTree(rhs));
  }

  // TODO(blickly): Move to NodeUtil if it makes more sense there.
  private static boolean isDeclaration(Node nameNode) {
    Preconditions.checkArgument(nameNode.isQualifiedName());
    Node parent = nameNode.getParent();
    switch (parent.getToken()) {
      case VAR:
      case LET:
      case CONST:
      case CLASS:
      case FUNCTION:
        return true;
      default:
        return false;
    }
  }

  private static boolean isConstToBeInferred(
      JSDocInfo jsdoc, Node nameNode, boolean isImpliedConst) {
    boolean isConst =
        isImpliedConst
            || nameNode.getParent().isConst()
            || (jsdoc != null && jsdoc.hasConstAnnotation());
    return isConst
        && !JsdocUtil.hasAnnotatedType(jsdoc)
        && !NodeUtil.isNamespaceDecl(nameNode);
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
      Node defineClassCall = parent.getGrandparent();
      Preconditions.checkState(defineClassCall.isCall());
      return NodeUtil.getBestLValue(defineClassCall).getQualifiedName();
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

  private static class JsdocUtil {

    private static JSDocInfo pullJsdocTypeFromAst(
        AbstractCompiler compiler, JSDocInfo oldJSDoc, Node nameNode) {
      Preconditions.checkArgument(nameNode.isQualifiedName());
      if (!nameNode.isFromExterns() && !isPrivate(oldJSDoc)) {
        compiler.report(JSError.make(nameNode, CONSTANT_WITHOUT_EXPLICIT_TYPE));
      }
      return getConstJSDoc(oldJSDoc, new Node(Token.STAR));
    }

    private static boolean isPrivate(@Nullable JSDocInfo jsdoc) {
      return jsdoc != null && jsdoc.getVisibility().equals(Visibility.PRIVATE);
    }

    private static JSDocInfo getAllTypeJSDoc() {
      return getConstJSDoc(null, new Node(Token.STAR));
    }

    private static JSDocInfo getQmarkTypeJSDoc() {
      return getConstJSDoc(null, new Node(Token.QMARK));
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

    private static JSDocInfo getJSDocForRhs(Node rhs, JSDocInfo oldJSDoc) {
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
        default:
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

}
