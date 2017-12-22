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
package com.google.javascript.jscomp.ijs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.Collections;
import java.util.List;
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
public class ConvertToTypedInterface implements CompilerPass {

  static final DiagnosticType CONSTANT_WITHOUT_EXPLICIT_TYPE =
      DiagnosticType.warning(
          "JSC_CONSTANT_WITHOUT_EXPLICIT_TYPE",
          "Constants in top-level should have types explicitly specified.");

  private static final ImmutableSet<String> CALLS_TO_PRESERVE =
      ImmutableSet.of(
          "goog.define",
          "goog.forwardDeclare",
          "goog.module",
          "goog.module.declareLegacyNamespace",
          "goog.provide",
          "goog.require");

  private final AbstractCompiler compiler;

  public ConvertToTypedInterface(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private void removeUselessFiles(Node externs, Node root) {
    for (Node script : Iterables.concat(externs.children(), root.children())) {
      if (!script.hasChildren()) {
        script.detach();
        compiler.reportChangeToChangeScope(script);
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    removeUselessFiles(externs, root);
    for (Node script = root.getFirstChild(); script != null; script = script.getNext()) {
      processFile(script);
    }
  }

  public void processFile(Node scriptNode) {
    checkArgument(scriptNode.isScript());
    FileInfo currentFile = new FileInfo();
    NodeTraversal.traverseEs6(compiler, scriptNode, new RemoveNonDeclarations());
    NodeTraversal.traverseEs6(compiler, scriptNode, new PropagateConstJsdoc(currentFile));
    new SimplifyDeclarations(compiler, currentFile).simplifyAll();
  }

  @Nullable
  private static Var findNameDeclaration(Scope scope, Node rhs) {
    if (!rhs.isName()) {
      return null;
    }
    return scope.getVar(rhs.getString());
  }

  private static class RemoveNonDeclarations implements NodeTraversal.Callback {

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case FUNCTION:
          if (!ClassUtil.isConstructor(n)) {
            Node body = n.getLastChild();
            if (!body.isNormalBlock() || body.hasChildren()) {
              t.reportCodeChange(body);
              body.replaceWith(IR.block().srcref(body));
              NodeUtil.markFunctionsDeleted(body, t.getCompiler());
            }
          }
          return true;
        case EXPR_RESULT:
          Node expr = n.getFirstChild();
          switch (expr.getToken()) {
            case CALL:
              Node callee = expr.getFirstChild();
              checkState(!callee.matchesQualifiedName("goog.scope"));
              if (!CALLS_TO_PRESERVE.contains(callee.getQualifiedName())) {
                NodeUtil.deleteNode(n, t.getCompiler());
              }
              return false;
            case ASSIGN:
              Node lhs = expr.getFirstChild();
              if (!lhs.isQualifiedName()
                  || (lhs.isName() && !t.inGlobalScope() && !t.inModuleScope())
                  || (!ClassUtil.isThisProp(lhs)
                      && !t.inGlobalHoistScope()
                      && !t.inModuleHoistScope())) {
                NodeUtil.deleteNode(n, t.getCompiler());
                return false;
              }
              return true;
            case GETPROP:
              if (!expr.isQualifiedName() || expr.getJSDocInfo() == null) {
                NodeUtil.deleteNode(n, t.getCompiler());
                return false;
              }
              return true;
            default:
              NodeUtil.deleteNode(n, t.getCompiler());
              return false;
          }
        case THROW:
        case RETURN:
        case BREAK:
        case CONTINUE:
        case DEBUGGER:
        case EMPTY:
          if (NodeUtil.isStatementParent(parent)) {
            NodeUtil.deleteNode(n, t.getCompiler());
          }
          return false;
        case LABEL:
        case IF:
        case SWITCH:
        case CASE:
        case WHILE:
          // First child can't have declaration. Statement itself will be removed post-order.
          NodeUtil.deleteNode(n.getFirstChild(), t.getCompiler());
          return true;
        case TRY:
        case DO:
          // Second child can't have declarations. Statement itself will be removed post-order.
          NodeUtil.deleteNode(n.getSecondChild(), t.getCompiler());
          return true;
        case FOR:
          NodeUtil.deleteNode(n.getSecondChild(), t.getCompiler());
          // fall-through
        case FOR_OF:
        case FOR_IN:
          NodeUtil.deleteNode(n.getSecondChild(), t.getCompiler());
          Node initializer = n.removeFirstChild();
          if (initializer.isVar()) {
            n.getLastChild().addChildToFront(initializer);
          }
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
          checkState(!NodeUtil.isStatement(n), n.getToken());
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
          splitNameDeclarationsAndRemoveDestructuring(n, t);
          break;
        case BLOCK:
          if (!parent.isFunction()) {
            parent.addChildrenAfter(n.removeChildren(), n);
            n.detach();
            t.reportCodeChange(parent);
          }
          break;
        default:
          break;
      }
    }

    /**
     * Does two simplifications to const/let/var nodes.
     * 1. Splits them so that each declaration is a separate statement.
     * 2. Removes non-import destructuring statements, which we assume are not type declarations.
     */
    static void splitNameDeclarationsAndRemoveDestructuring(Node n, NodeTraversal t) {
      checkArgument(NodeUtil.isNameDeclaration(n));
      while (n.hasChildren()) {
        Node lhsToSplit = n.getLastChild();
        if (lhsToSplit.isDestructuringLhs() && !isImportRhs(lhsToSplit.getLastChild())) {
          // Remove destructuring statements, which we assume are not type declarations
          NodeUtil.markFunctionsDeleted(lhsToSplit, t.getCompiler());
          NodeUtil.removeChild(n, lhsToSplit);
          t.reportCodeChange();
          continue;
        }
        if (n.hasOneChild()) {
          return;
        }
        // A name declaration with more than one LHS is split into separate declarations.
        Node rhs = lhsToSplit.hasChildren() ? lhsToSplit.removeFirstChild() : null;
        Node newDeclaration = IR.declaration(lhsToSplit.detach(), rhs, n.getToken()).srcref(n);
        n.getParent().addChildAfter(newDeclaration, n);
        t.reportCodeChange();
      }
    }
  }

  private static class PropagateConstJsdoc extends NodeTraversal.AbstractPostOrderCallback {
    final FileInfo currentFile;

    PropagateConstJsdoc(FileInfo currentFile) {
      this.currentFile = currentFile;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case CLASS:
          if (NodeUtil.isStatementParent(parent)) {
            currentFile.recordNameDeclaration(n.getFirstChild(), t.getScope());
          }
          break;
        case FUNCTION:
          if (NodeUtil.isStatementParent(parent)) {
            currentFile.recordNameDeclaration(n.getFirstChild(), t.getScope());
          } else if (ClassUtil.isClassMethod(n)) {
            currentFile.recordMethod(n, t.getScope());
          }
          break;
        case EXPR_RESULT:
          Node expr = n.getFirstChild();
          switch (expr.getToken()) {
            case CALL:
              Node callee = expr.getFirstChild();
              checkState(CALLS_TO_PRESERVE.contains(callee.getQualifiedName()));
              if (callee.matchesQualifiedName("goog.provide")) {
                currentFile.markProvided(expr.getLastChild().getString());
              } else if (callee.matchesQualifiedName("goog.require")) {
                currentFile.recordImport(expr.getLastChild().getString());
              } else if (callee.matchesQualifiedName("goog.define")) {
                currentFile.recordDefine(expr, t.getScope());
              }
              break;
            case ASSIGN:
              Node lhs = expr.getFirstChild();
              propagateJsdocAtName(t, lhs);
              currentFile.recordNameDeclaration(lhs, t.getScope());
              break;
            case GETPROP:
              currentFile.recordNameDeclaration(expr, t.getScope());
              break;
            default:
              throw new RuntimeException("Unexpected declaration: " + expr);
          }
          break;
        case VAR:
        case CONST:
        case LET:
          checkState(n.hasOneChild());
          propagateJsdocAtName(t, n.getFirstChild());
          recordNameDeclaration(t, n);
          break;
        case STRING_KEY:
          if (n.hasOneChild()) {
            propagateJsdocAtName(t, n);
          }
          break;
        default:
          break;
      }
    }

    void recordNameDeclaration(NodeTraversal t, Node decl) {
      checkArgument(NodeUtil.isNameDeclaration(decl));
      Node rhs = decl.getFirstChild().getLastChild();
      boolean isImport = isImportRhs(rhs);
      for (Node name : NodeUtil.findLhsNodesInNode(decl)) {
        if (isImport) {
          currentFile.recordImport(name.getString());
        } else {
          currentFile.recordNameDeclaration(name, t.getScope());
        }
      }
    }

    private void propagateJsdocAtName(NodeTraversal t, Node nameNode) {
      checkArgument(
          nameNode.isQualifiedName() || nameNode.isStringKey() || nameNode.isDestructuringLhs(),
          nameNode);
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
      if (newJsdoc == null && ClassUtil.isThisProp(nameNode)) {
        Var decl = findNameDeclaration(t.getScope(), rhs);
        newJsdoc = JsdocUtil.getJSDocForName(decl, jsdoc);
      }
      if (newJsdoc != null) {
        jsdocNode.setJSDocInfo(newJsdoc);
        t.reportCodeChange();
      }
    }
  }

  private static class SimplifyDeclarations {
    private final AbstractCompiler compiler;
    private final FileInfo currentFile;

    static final Ordering<String> SHORT_TO_LONG =
        Ordering.natural()
            .onResultOf(
                new Function<String, Integer>() {
                  @Override
                  public Integer apply(String name) {
                    return name.replaceAll("[^.]", "").length();
                  }
                });

    static final Ordering<PotentialDeclaration> DECLARATIONS_FIRST =
        Ordering.natural()
            .onResultOf(
                new Function<PotentialDeclaration, Boolean>() {
                  @Override
                  public Boolean apply(PotentialDeclaration decl) {
                    return decl.getJsDoc() == null;
                  }
                });

    SimplifyDeclarations(AbstractCompiler compiler, FileInfo currentFile) {
      this.compiler = compiler;
      this.currentFile = currentFile;
    }

    private void removeDuplicateDeclarations() {
      for (String name : currentFile.getDeclarations().keySet()) {
        if (name.startsWith("this.")) {
          continue;
        }
        List<PotentialDeclaration> declList = currentFile.getDeclarations().get(name);
        Collections.sort(declList, DECLARATIONS_FIRST);
        while (declList.size() > 1) {
          // Don't remove the first declaration (at index 0)
          PotentialDeclaration decl = declList.remove(1);
          decl.remove(compiler);
        }
      }
    }

    void simplifyAll() {
      // Remove duplicate assignments to the same symbol
      removeDuplicateDeclarations();

      // Simplify all names in the top-level scope.
      List<String> seenNames =
          SHORT_TO_LONG.immutableSortedCopy(currentFile.getDeclarations().keySet());
      for (String name : seenNames) {
        for (PotentialDeclaration decl : currentFile.getDeclarations().get(name)) {
          processDeclaration(decl);
        }
      }
    }

    private void processDeclaration(PotentialDeclaration decl) {
      switch (shouldRemove(decl)) {
        case PRESERVE_ALL:
          if (decl.getRhs() != null && decl.getRhs().isFunction()) {
            processFunction(decl.getRhs());
          } else if (decl.getRhs() != null && isClass(decl.getRhs())) {
            processClass(decl.getRhs());
          }
          break;
        case SIMPLIFY_RHS:
          decl.simplify(compiler);
          break;
        case REMOVE_ALL:
          decl.remove(compiler);
          break;
      }
    }

    private void processClass(Node n) {
      checkArgument(isClass(n));
      for (Node member : n.getLastChild().children()) {
        if (member.isEmpty()) {
          NodeUtil.deleteNode(member, compiler);
          continue;
        }
        processFunction(member.getLastChild());
      }
    }

    private void processFunction(Node n) {
      checkArgument(n.isFunction());
      processFunctionParameters(n.getSecondChild());
    }

    private void processFunctionParameters(Node paramList) {
      checkArgument(paramList.isParamList());
      for (Node arg = paramList.getFirstChild(); arg != null; arg = arg.getNext()) {
        if (arg.isDefaultValue()) {
          Node replacement = arg.getFirstChild().detach();
          arg.replaceWith(replacement);
          arg = replacement;
          compiler.reportChangeToEnclosingScope(replacement);
        }
      }
    }

    enum RemovalType {
      PRESERVE_ALL,
      SIMPLIFY_RHS,
      REMOVE_ALL,
    }

    private static boolean isClass(Node n) {
      return n.isClass() || NodeUtil.isCallTo(n, "goog.defineClass");
    }

    private static boolean isExportLhs(Node lhs) {
      return (lhs.isName() && lhs.matchesQualifiedName("exports"))
          || (lhs.isGetProp() && lhs.getFirstChild().matchesQualifiedName("exports"));
    }

    private static String rootName(String qualifiedName) {
      int dotIndex = qualifiedName.indexOf('.');
      if (dotIndex == -1) {
        return qualifiedName;
      }
      return qualifiedName.substring(0, dotIndex);
    }

    private RemovalType shouldRemove(PotentialDeclaration decl) {
      String fullyQualifiedName = decl.getFullyQualifiedName();
      if ("$jscomp".equals(rootName(fullyQualifiedName))) {
        // These are created by goog.scope processing, but clash with each other
        // and should not be depended on.
        return RemovalType.REMOVE_ALL;
      }
      Node nameNode = decl.getLhs();
      Node rhs = decl.getRhs();
      Node jsdocNode = NodeUtil.getBestJSDocInfoNode(nameNode);
      JSDocInfo jsdoc = jsdocNode.getJSDocInfo();
      boolean isExport = isExportLhs(nameNode);
      if (rhs == null) {
        return RemovalType.SIMPLIFY_RHS;
      }
      if (PotentialDeclaration.isTypedRhs(rhs)
          || NodeUtil.isCallTo(rhs, "goog.defineClass")
          || isImportRhs(rhs)
          || (isExport && (rhs.isQualifiedName() || rhs.isObjectLit()))
          || (jsdoc != null && jsdoc.isConstructor() && rhs.isQualifiedName())
          || isAliasDefinition(decl)
          || (rhs.isObjectLit()
              && !rhs.hasChildren()
              && (jsdoc == null || !JsdocUtil.hasAnnotatedType(jsdoc)))) {
        return RemovalType.PRESERVE_ALL;
      }
      if (NodeUtil.isNamespaceDecl(nameNode)) {
        return RemovalType.SIMPLIFY_RHS;
      }
      if (isConstToBeInferred(jsdoc, nameNode, isExport)) {
        jsdocNode.setJSDocInfo(JsdocUtil.pullJsdocTypeFromAst(compiler, jsdoc, nameNode));
        return RemovalType.SIMPLIFY_RHS;
      }
      if (jsdoc == null || !jsdoc.containsDeclaration()) {
        if (!isDeclaration(nameNode)
            && !currentFile.isPrefixProvided(fullyQualifiedName)
            && !currentFile.isStrictPrefixDeclared(fullyQualifiedName)) {
          // This looks like an update rather than a declaration in this file.
          return RemovalType.REMOVE_ALL;
        }
        jsdocNode.setJSDocInfo(JsdocUtil.getAllTypeJSDoc());
      }
      return RemovalType.SIMPLIFY_RHS;
    }

    private boolean isAliasDefinition(PotentialDeclaration decl) {
      boolean isExport = isExportLhs(decl.getLhs());
      if (isConstToBeInferred(decl.getJsDoc(), decl.getLhs(), isExport)
          && decl.getRhs().isQualifiedName()) {
        String aliasedName = decl.getRhs().getQualifiedName();
        return currentFile.isPrefixRequired(aliasedName) || currentFile.isNameDeclared(aliasedName);
      }
      return false;
    }
  }

  // TODO(blickly): Move to NodeUtil if it makes more sense there.
  private static boolean isDeclaration(Node nameNode) {
    checkArgument(nameNode.isQualifiedName());
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

  static boolean isConstToBeInferred(
      JSDocInfo jsdoc, Node nameNode, boolean isImpliedConst) {
    boolean isConst =
        isImpliedConst
            || nameNode.getParent().isConst()
            || (jsdoc != null && jsdoc.hasConstAnnotation());
    return isConst
        && !JsdocUtil.hasAnnotatedType(jsdoc)
        && !NodeUtil.isNamespaceDecl(nameNode);
  }

  private static boolean isImportRhs(@Nullable Node rhs) {
    if (rhs == null || !rhs.isCall()) {
      return false;
    }
    Node callee = rhs.getFirstChild();
    return callee.matchesQualifiedName("goog.require")
        || callee.matchesQualifiedName("goog.forwardDeclare");
  }

}
