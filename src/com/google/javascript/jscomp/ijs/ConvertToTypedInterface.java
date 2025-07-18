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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.RewriteCallerCodeLocation;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.jstype.JSType.Nullability;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The goal of this pass is to shrink the AST, preserving only typing, not behavior.
 *
 * <p>To do this, it does things like removing function/method bodies, rvalues that are not needed,
 * expressions that are not declarations, etc.
 *
 * <p>This is conceptually similar to the ijar tool[1] that bazel uses to shrink jars into minimal
 * versions that can be used equivalently for compilation of downstream dependencies.
 *
 * <p>[1] https://github.com/bazelbuild/bazel/blob/master/third_party/ijar/README.txt
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
          "Found a goog.scope local type declaration.\n"
              + "If you can't yet migrate this file to goog.module, use a /** @private */"
              + " declaration within the goog.provide namespace.");

  private static final ImmutableSet<String> CALLS_TO_PRESERVE =
      ImmutableSet.of(
          "Polymer",
          "goog.addSingletonGetter",
          "goog.define",
          "goog.forwardDeclare",
          "goog.module",
          "goog.module.declareLegacyNamespace",
          "goog.declareModuleId",
          "goog.provide",
          "goog.require",
          "goog.requireType");

  private final AbstractCompiler compiler;

  public ConvertToTypedInterface(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private static void maybeReport(
      AbstractCompiler compiler, Node node, DiagnosticType diagnostic, String... fillers) {
    String sourceName = NodeUtil.getSourceName(node);
    if (sourceName.endsWith("_test.js")
        || sourceName.endsWith("_test.closure.js")
        || sourceName.endsWith("_test.tsx.cl.js")) {
      // Allow _test.js files and their tsickle generated
      // equivalents to avoid emitting errors at .i.js generation time.
      // We expect these files to not be consumed by any other downstream libraries.
      return;
    }
    compiler.report(JSError.make(node, diagnostic, fillers));
  }

  private static void maybeWarnForConstWithoutExplicitType(
      AbstractCompiler compiler, PotentialDeclaration decl) {
    if (decl.isConstToBeInferred()
        && !decl.getLhs().isFromExterns()
        && !JsdocUtil.isPrivate(decl.getJsDoc())) {

      Node nameNode = decl.getLhs();
      if (nameNode.getJSType() == null) {
        maybeReport(compiler, nameNode, CONSTANT_WITHOUT_EXPLICIT_TYPE);
      } else {
        maybeReport(
            compiler,
            nameNode,
            CONSTANT_WITH_SUGGESTED_TYPE,
            nameNode.getJSType().toAnnotationString(Nullability.EXPLICIT));
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
    String sourceFileName = scriptNode.getSourceFileName();
    if (AbstractCompiler.isFillFileName(sourceFileName)) {
      scriptNode.detach();
      return;
    }

    JSDocInfo scriptJsDoc = scriptNode.getJSDocInfo();
    if (scriptJsDoc != null && scriptJsDoc.isClosureUnawareCode()) {
      // If we are generating type summary files, then those files won't contain the method content
      // from within the closure-unaware section, and the entire file effectively becomes
      // closure-aware again (as it is generated code that describes a module shape).
      JSDocInfo.Builder scriptJsDocBuilder = scriptJsDoc.toBuilder();
      scriptJsDocBuilder.removeClosureUnawareCode();
      scriptNode.setJSDocInfo(scriptJsDocBuilder.build());
    }

    FileInfo currentFile = new FileInfo();
    NodeTraversal.traverse(compiler, scriptNode, new RemoveNonDeclarations());
    NodeTraversal.traverse(compiler, scriptNode, new PropagateConstJsdoc(currentFile));
    new SimplifyDeclarations(compiler, currentFile).simplifyAll();
  }

  private static @Nullable Var findNameDeclaration(Scope scope, Node rhs) {
    if (!rhs.isName()) {
      return null;
    }
    return scope.getVar(rhs.getString());
  }

  private static class RemoveNonDeclarations implements NodeTraversal.Callback {

    private static final QualifiedName GOOG_SCOPE = QualifiedName.of("goog.scope");

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
        case MEMBER_FIELD_DEF:
          if (ClassUtil.isMemberFieldDefInsideClassWithName(n)) {
            return true;
          }
          // We will remove fields in anonymous classes
          NodeUtil.deleteNode(n, t.getCompiler());
          return false;
        case EXPR_RESULT:
          Node expr = n.getFirstChild();
          switch (expr.getToken()) {
            case CALL:
              Node callee = expr.getFirstChild();
              checkState(!GOOG_SCOPE.matches(callee));
              if (CALLS_TO_PRESERVE.contains(callee.getQualifiedName())) {
                return true;
              }
              NodeUtil.deleteNode(n, t.getCompiler());
              return false;
            case ASSIGN:
              if (shouldPreserveAssignment(expr, t)) {
                return true;
              }
              NodeUtil.deleteNode(n, t.getCompiler());
              return false;
            case GETPROP:
              if (!expr.isQualifiedName() || expr.getJSDocInfo() == null) {
                NodeUtil.deleteNode(n, t.getCompiler());
                return false;
              }
              return true;
            case GETELEM:
              if (isSymbolProp(expr.getSecondChild()) && expr.getJSDocInfo() != null) {
                return true;
              }

              NodeUtil.deleteNode(n, t.getCompiler());
              return false;
            default:
              NodeUtil.deleteNode(n, t.getCompiler());
              return false;
          }
        case COMPUTED_PROP:
          if (ClassUtil.isComputedMemberInsideClassWithName(n)) {
            return true;
          }
          if (!NodeUtil.isLhsByDestructuring(n.getSecondChild())) {
            NodeUtil.deleteNode(n, t.getCompiler());
          }
          return false;
        case COMPUTED_FIELD_DEF:
          if (ClassUtil.isComputedMemberInsideClassWithName(n)) {
            return true;
          }
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
        case FOR_AWAIT_OF:
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
        case FOR_AWAIT_OF:
        case IF:
          if (n.hasParent()) {
            Node children = n.removeChildren();
            parent.addChildrenAfter(children, n);
            // We don't need the special valid-AST-preserving behavior of `NodeUtil.removeChild()`
            // here. We always want to remove exactly and only `n`, so we use `n.detach()`.
            //
            // Also, the shouldTraverse() method intentionally puts the AST in an invalid state
            // by moving children to parents where they are not valid temporarily.
            // `NodeUtil.removeChild()` would throw an exception here if it noticed the invalid AST
            // state.
            n.detach();
            t.reportCodeChange();
          }
          break;
        case SWITCH:
          // shouldTraverse() removed the switch condition already, so we just need to handle the
          // cases.
          if (n.hasParent()) {
            checkState(n.hasOneChild(), "malfrmed sWITCH %s", n.toStringTree());
            Node children = n.getFirstChild().removeChildren();
            parent.addChildrenAfter(children, n);
            n.detach();
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
     *
     * <ul>
     *   <li>Splits them so that each declaration is a separate statement.
     *   <li>Removes non-import and non-alias destructuring statements, which we assume are not type
     *       declarations.
     *   <li>Moves inline JSDoc annotations onto the declaration nodes.
     * </ul>
     */
    static void splitNameDeclarationsAndRemoveDestructuring(Node n, NodeTraversal t) {
      checkArgument(NodeUtil.isNameDeclaration(n));
      JSDocInfo sharedJsdoc = n.getJSDocInfo();
      boolean isExport = n.getParent().isExport();
      Node statement = isExport ? n.getParent() : n;
      while (n.hasChildren()) {
        Node lhsToSplit = n.getLastChild();
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
        newDeclaration.insertAfter(statement);
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
          nameNode.isQualifiedName()
              || nameNode.isStringKey()
              || nameNode.isDestructuringLhs()
              || nameNode.isMemberFieldDef(),
          nameNode);
      Node jsdocNode = NodeUtil.getBestJSDocInfoNode(nameNode);
      JSDocInfo originalJsdoc = jsdocNode.getJSDocInfo();
      Node rhs = NodeUtil.getRValueOfLValue(nameNode);
      JSDocInfo newJsdoc = JsdocUtil.getJSDocForRhs(rhs, originalJsdoc);
      if (newJsdoc == null && ClassUtil.isThisPropInsideClassWithName(nameNode)) {
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

    static int countDots(String name) {
      int count = 0;
      for (int i = 0; i < name.length(); i++) {
        if (name.charAt(i) == '.') {
          count++;
        }
      }
      return count;
    }

    static final Comparator<String> SHORT_TO_LONG = comparing(SimplifyDeclarations::countDots);

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
      ImmutableList<String> seenNames =
          ImmutableList.sortedCopyOf(SHORT_TO_LONG, currentFile.getDeclarations().keySet());

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

      if (decl.breakDownDestructure(compiler)) {
        maybeReport(compiler, decl.getLhs(), CONSTANT_WITHOUT_EXPLICIT_TYPE);
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
      for (Node member = n.getLastChild().getFirstChild(); member != null; ) {
        Node next = member.getNext();
        switch (member.getToken()) {
            // MEMBER_FIELD_DEF's are handled in ProcessConstJsdocCallback which is called in the
            // traversal by its subclass PropogateConstJsdoc.
            // If no jsdoc is present on the MEMBER_FIELD_DEF, we will be add a Jsdoc in
            // `setUndeclaredToUnusableType()`.
            // No further simplification is needed for MEMBER_FIELD_DEF's when we call
            // `simplifyAll()` so we will break.
          case MEMBER_FIELD_DEF:
          case COMPUTED_FIELD_DEF:
            break;
          case EMPTY:
            // a lonely `;` in a class body can just be deleted.
            NodeUtil.deleteNode(member, compiler);
            break;
          case MEMBER_FUNCTION_DEF:
          case GETTER_DEF:
          case SETTER_DEF:
            processFunction(member.getLastChild());
            break;
          case COMPUTED_PROP:
            checkState(
                member.getSecondChild().isFunction(),
                "Non-function computed class member: %s",
                member);
            processFunction(member.getSecondChild());
            break;
          default:
            throw new AssertionError(member.getToken() + " should not be handled by processClass");
        }
        member = next;
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
          if (rhs.isCall()
              && RewriteCallerCodeLocation.GOOG_CALLER_LOCATION_QUALIFIED_NAME.matches(
                  rhs.getFirstChild())) {
            continue;
          }
          rhs.replaceWith(NodeUtil.newUndefinedNode(rhs));
          compiler.reportChangeToEnclosingScope(arg);
        }
      }
    }

    private static boolean isClass(Node n) {
      return n.isClass();
    }

    private static String rootName(String qualifiedName) {
      int dotIndex = qualifiedName.indexOf('.');
      if (dotIndex == -1) {
        return qualifiedName;
      }
      return qualifiedName.substring(0, dotIndex);
    }

    private boolean shouldRemove(String name, PotentialDeclaration decl) {
      if (decl.isDetached()) {
        return true;
      }
      if (rootName(name).startsWith("$jscomp")) {
        // These are created by goog.scope processing, but clash with each other
        // and should not be depended on.
        if ((decl.getRhs() != null && decl.getRhs().isClass())
            || (decl.getJsDoc() != null && decl.getJsDoc().containsTypeDefinition())) {
          maybeReport(compiler, decl.getLhs(), GOOG_SCOPE_HIDDEN_TYPE);
        }
        return true;
      }
      // This looks like an update rather than a declaration in this file.
      return !name.startsWith("this.")
          && !decl.isDefiniteDeclaration(compiler)
          && !decl.getLhs().isMemberFieldDef()
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

  static boolean isSymbolProp(Node lhs) {
    return lhs.isGetProp() && lhs.getFirstChild().matchesName("Symbol");
  }

  private static boolean shouldPreserveAssignment(Node expr, NodeTraversal t) {
    Node lhs = expr.getFirstChild();
    // Ignore assignments in function bodies, unless they're also a constructor with a this. prop.
    if (!t.inGlobalHoistScope() && !t.inModuleHoistScope()) {
      return ClassUtil.isThisPropInsideClassWithName(lhs);
    }

    // Well-known symbol properties, like Foo.prototype[Symbol.iterator] = function() {};
    if (lhs.isGetElem() && isSymbolProp(lhs.getSecondChild())) {
      return lhs.getFirstChild().isQualifiedName();
    }
    // Assignments to names don't have global typechecking side-effects even within the 'hoist
    // scope'
    if (lhs.isName()) {
      return t.inGlobalScope() || t.inModuleScope();
    }
    return lhs.isQualifiedName();
  }
}
