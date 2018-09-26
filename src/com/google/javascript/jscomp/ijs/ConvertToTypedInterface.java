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
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType.Nullability;
import java.util.Comparator;
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
  static final DiagnosticType CONSTANT_WITH_SUGGESTED_TYPE =
      DiagnosticType.warning(
          "JSC_CONSTANT_WITH_SUGGESTED_TYPE",
          "Constants in top-level should have types explicitly specified.\n"
              + "You may want specify this type as:\t@const '{'{0}'}'");

  static final DiagnosticType CONSTANT_WITHOUT_EXPLICIT_TYPE =
      DiagnosticType.warning(
          "JSC_CONSTANT_WITHOUT_EXPLICIT_TYPE",
          "Constants in top-level should have types explicitly specified.");

  static final DiagnosticType GOOG_SCOPE_HIDDEN_TYPE =
      DiagnosticType.warning(
          "JSC_GOOG_SCOPE_HIDDEN_TYPE",
          "Please do not use goog.scope to hide declarations.\n"
      + "It is preferable to either create an @private namespaced declaration, or migrate "
              + "to goog.module.");

  private static final ImmutableSet<String> CALLS_TO_PRESERVE =
      ImmutableSet.of(
          "goog.addSingletonGetter",
          "goog.define",
          "goog.forwardDeclare",
          "goog.module",
          "goog.module.declareLegacyNamespace",
          // TODO(johnplaisted): Consolidate on declareModuleId / delete declareNamespace.
          "goog.declareModuleId",
          "goog.module.declareNamespace",
          "goog.provide",
          "goog.require");

  private final AbstractCompiler compiler;

  public ConvertToTypedInterface(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private static void maybeWarnForConstWithoutExplicitType(
      AbstractCompiler compiler, PotentialDeclaration decl) {
    if (decl.isConstToBeInferred()
        && !decl.getLhs().isFromExterns()
        && !JsdocUtil.isPrivate(decl.getJsDoc())) {
      Node nameNode = decl.getLhs();
      if (nameNode.getJSType() == null) {
        compiler.report(JSError.make(nameNode, CONSTANT_WITHOUT_EXPLICIT_TYPE));
      } else {
        compiler.report(
            JSError.make(
                nameNode,
                CONSTANT_WITH_SUGGESTED_TYPE,
                nameNode.getJSType().toAnnotationString(Nullability.EXPLICIT)));
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    for (Node script = root.getFirstChild(); script != null; script = script.getNext()) {
      processFile(script);
    }
  }

  private void processFile(Node scriptNode) {
    checkArgument(scriptNode.isScript());
    FileInfo currentFile = new FileInfo();
    NodeTraversal.traverse(compiler, scriptNode, new RemoveNonDeclarations());
    NodeTraversal.traverse(compiler, scriptNode, new PropagateConstJsdoc(currentFile));
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
          if (!ClassUtil.isConstructor(n) || !ClassUtil.hasNamedClass(n)) {
            Node body = n.getLastChild();
            if (!body.isBlock() || body.hasChildren()) {
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
        case COMPUTED_PROP:
          NodeUtil.deleteNode(n, t.getCompiler());
          return false;
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
     * Does three simplifications to const/let/var nodes.
     * 1. Splits them so that each declaration is a separate statement.
     * 2. Removes non-import destructuring statements, which we assume are not type declarations.
     * 3. Moves inline JSDoc annotations onto the declaration nodes.
     */
    static void splitNameDeclarationsAndRemoveDestructuring(Node n, NodeTraversal t) {
      checkArgument(NodeUtil.isNameDeclaration(n));
      JSDocInfo sharedJsdoc = n.getJSDocInfo();
      boolean isExport = n.getParent().isExport();
      Node statement = isExport ? n.getParent() : n;
      while (n.hasChildren()) {
        Node lhsToSplit = n.getLastChild();
        if (lhsToSplit.isDestructuringLhs()
            && !PotentialDeclaration.isImportRhs(lhsToSplit.getLastChild())) {
          // Remove destructuring statements, which we assume are not type declarations
          NodeUtil.markFunctionsDeleted(lhsToSplit, t.getCompiler());
          NodeUtil.removeChild(n, lhsToSplit);
          t.reportCodeChange();
          continue;
        }
        JSDocInfo nameJsdoc = lhsToSplit.getJSDocInfo();
        lhsToSplit.setJSDocInfo(null);
        JSDocInfo mergedJsdoc = JsdocUtil.mergeJsdocs(sharedJsdoc, nameJsdoc);
        if (n.hasOneChild()) {
          n.setJSDocInfo(mergedJsdoc);
          return;
        }
        // A name declaration with more than one LHS is split into separate declarations.
        Node rhs = lhsToSplit.hasChildren() ? lhsToSplit.removeFirstChild() : null;
        Node newDeclaration =
            NodeUtil.newDeclaration(lhsToSplit.detach(), rhs, n.getToken()).srcref(n);
        newDeclaration.setJSDocInfo(mergedJsdoc);
        if (isExport) {
          newDeclaration = IR.export(newDeclaration).srcref(statement);
        }
        statement.getParent().addChildAfter(newDeclaration, statement);
        t.reportCodeChange();
      }
    }
  }

  private static class PropagateConstJsdoc extends ProcessConstJsdocCallback {

    PropagateConstJsdoc(FileInfo currentFile) {
      super(currentFile);
    }

    @Override
    protected void processConstWithRhs(NodeTraversal t, Node nameNode) {
      checkArgument(
          nameNode.isQualifiedName() || nameNode.isStringKey() || nameNode.isDestructuringLhs(),
          nameNode);
      Node jsdocNode = NodeUtil.getBestJSDocInfoNode(nameNode);
      JSDocInfo originalJsdoc = jsdocNode.getJSDocInfo();
      Node rhs = NodeUtil.getRValueOfLValue(nameNode);
      JSDocInfo newJsdoc = JsdocUtil.getJSDocForRhs(rhs, originalJsdoc);
      if (newJsdoc == null && ClassUtil.isThisProp(nameNode)) {
        Var decl = findNameDeclaration(t.getScope(), rhs);
        newJsdoc = JsdocUtil.getJSDocForName(decl, originalJsdoc);
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

    /** Levels of JSDoc, starting from those most likely to be on the canonical declaration. */
    enum TypingLevel {
      TYPED_JSDOC_DECLARATION,
      UNTYPED_JSDOC_DECLARATION,
      NO_JSDOC,
    }

    static final Comparator<String> SHORT_TO_LONG =
        comparing(name -> name.replaceAll("[^.]", "").length());

    static final Comparator<PotentialDeclaration> DECLARATIONS_FIRST =
        comparing(
            decl -> {
              JSDocInfo jsdoc = decl.getJsDoc();
              if (jsdoc == null) {
                return TypingLevel.NO_JSDOC;
              }
              if (jsdoc.getTypeNodes().isEmpty()) {
                return TypingLevel.UNTYPED_JSDOC_DECLARATION;
              }
              return TypingLevel.TYPED_JSDOC_DECLARATION;
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
        declList.sort(DECLARATIONS_FIRST);
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
      Iterable<String> seenNames =
          currentFile.getDeclarations().keySet().stream().sorted(SHORT_TO_LONG)::iterator;

      for (String name : seenNames) {
        for (PotentialDeclaration decl : currentFile.getDeclarations().get(name)) {
          processDeclaration(name, decl);
        }
      }
    }

    private void processDeclaration(String name, PotentialDeclaration decl) {
      if (shouldRemove(name, decl)) {
        decl.remove(compiler);
        return;
      }
      if (decl.isAliasDefinition()) {
        return;
      }
      if (decl.getRhs() != null && decl.getRhs().isFunction()) {
        processFunction(decl.getRhs());
      } else if (decl.getRhs() != null && isClass(decl.getRhs())) {
        processClass(decl.getRhs());
      }
      setUndeclaredToUnusableType(decl);
      decl.simplify(compiler);
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
          Node rhs = arg.getLastChild();
          rhs.replaceWith(NodeUtil.newUndefinedNode(rhs));
          compiler.reportChangeToEnclosingScope(arg);
        }
      }
    }

    private static boolean isClass(Node n) {
      return n.isClass() || NodeUtil.isCallTo(n, "goog.defineClass");
    }

    private static String rootName(String qualifiedName) {
      int dotIndex = qualifiedName.indexOf('.');
      if (dotIndex == -1) {
        return qualifiedName;
      }
      return qualifiedName.substring(0, dotIndex);
    }

    private boolean shouldRemove(String name, PotentialDeclaration decl) {
      if ("$jscomp".equals(rootName(name))) {
        if (decl.isDetached()) {
          return true;
        }
        // These are created by goog.scope processing, but clash with each other
        // and should not be depended on.
        if (decl.getRhs() != null && decl.getRhs().isClass()
            || decl.getJsDoc() != null && decl.getJsDoc().containsTypeDefinition()) {
          compiler.report(JSError.make(decl.getLhs(), GOOG_SCOPE_HIDDEN_TYPE));
        }
        return true;
      }
      // This looks like an update rather than a declaration in this file.
      return !name.startsWith("this.")
          && !decl.isDefiniteDeclaration()
          && !currentFile.isPrefixProvided(name)
          && !currentFile.isStrictPrefixDeclared(name);
    }

    private void setUndeclaredToUnusableType(PotentialDeclaration decl) {
      Node nameNode = decl.getLhs();
      JSDocInfo jsdoc = decl.getJsDoc();
      if (decl.shouldPreserve()
          || NodeUtil.isNamespaceDecl(nameNode)
          || (decl.getRhs() != null && NodeUtil.isCallTo(decl.getRhs(), "Symbol"))
          || (jsdoc != null && jsdoc.containsDeclaration() && !decl.isConstToBeInferred())) {
        return;
      }
      maybeWarnForConstWithoutExplicitType(compiler, decl);
      Node jsdocNode = NodeUtil.getBestJSDocInfoNode(nameNode);
      jsdocNode.setJSDocInfo(JsdocUtil.getUnusableTypeJSDoc(jsdoc));
    }

  }

}
