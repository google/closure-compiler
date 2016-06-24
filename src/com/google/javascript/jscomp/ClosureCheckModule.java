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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractModuleCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashMap;
import java.util.Map;

/**
 * Checks that goog.module() is used correctly.
 *
 * <p>Note that this file only does checks that can be done per-file. Whole program checks happen
 * during goog.module rewriting, in {@link ClosureRewriteModule}.
 */
public final class ClosureCheckModule extends AbstractModuleCallback
    implements HotSwapCompilerPass {
  static final DiagnosticType AT_EXPORT_IN_GOOG_MODULE =
      DiagnosticType.error(
          "JSC_AT_EXPORT_IN_GOOG_MODULE",
          "@export has no effect here");

  static final DiagnosticType AT_EXPORT_IN_NON_LEGACY_GOOG_MODULE =
      DiagnosticType.disabled(
          "JSC_AT_EXPORT_IN_GOOG_MODULE",
          "@export is not allowed here in a non-legacy goog.module."
          + " Consider using goog.exportSymbol instead.");

  static final DiagnosticType GOOG_MODULE_REFERENCES_THIS = DiagnosticType.error(
      "JSC_GOOG_MODULE_REFERENCES_THIS",
      "The body of a goog.module cannot reference 'this'.");

  static final DiagnosticType GOOG_MODULE_USES_THROW = DiagnosticType.error(
      "JSC_GOOG_MODULE_USES_THROW",
      "The body of a goog.module cannot use 'throw'.");

  static final DiagnosticType LET_GOOG_REQUIRE =
      DiagnosticType.disabled(
          "JSC_LET_GOOG_REQUIRE",
          "Module imports must be constant. Please use 'const' instead of 'let'.");

  static final DiagnosticType MULTIPLE_MODULES_IN_FILE =
      DiagnosticType.error(
          "JSC_MULTIPLE_MODULES_IN_FILE",
          "There should only be a single goog.module() statement per file.");

  static final DiagnosticType MODULE_AND_PROVIDES =
      DiagnosticType.error(
          "JSC_MODULE_AND_PROVIDES",
          "A file using goog.module() may not also use goog.provide() statements.");

  static final DiagnosticType ONE_REQUIRE_PER_DECLARATION =
      DiagnosticType.error(
          "JSC_ONE_REQUIRE_PER_DECLARATION",
          "There may only be one goog.require() per var/let/const declaration.");

  static final DiagnosticType EXPORT_NOT_A_MODULE_LEVEL_STATEMENT =
      DiagnosticType.error(
          "JSC_EXPORT_NOT_A_MODULE_LEVEL_STATEMENT",
          "Exports must be a statement at the top-level of a module");

  static final DiagnosticType EXPORT_REPEATED_ERROR =
      DiagnosticType.error(
          "JSC_EXPORT_REPEATED_ERROR",
          "Name cannot be exported multiple times. Previous export on line {0}.");

  static final DiagnosticType REFERENCE_TO_MODULE_GLOBAL_NAME =
      DiagnosticType.error(
          "JSC_REFERENCE_TO_MODULE_GLOBAL_NAME",
          "References to the global name of a module are not allowed. Perhaps you meant exports?");

  static final DiagnosticType REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME =
      DiagnosticType.disabled(
          "JSC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME",
          "Reference to fully qualified import name ''{0}''. Please use the short name instead.");

  static final DiagnosticType REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME =
      DiagnosticType.disabled(
          "JSC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME",
          "Reference to fully qualified import name ''{0}''."
              + " Please use the short name ''{1}'' instead.");

  static final DiagnosticType REQUIRE_NOT_AT_TOP_LEVEL =
      DiagnosticType.error(
          "JSC_REQUIRE_NOT_AT_TOP_LEVEL",
          "goog.require() must be called at file scope.");

  private final AbstractCompiler compiler;

  private String currentModuleName = null;
  private Map<String, String> shortRequiredNamespaces = new HashMap<>();
  private Node defaultExportNode = null;

  public ClosureCheckModule(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  @Override
  public void enterModule(NodeTraversal t, Node scopeRoot) {
    Node firstStatement = scopeRoot.getFirstChild();
    if (NodeUtil.isExprCall(firstStatement)) {
      Node call = firstStatement.getFirstChild();
      Node callee = call.getFirstChild();
      if (callee.matchesQualifiedName("goog.module")) {
        Preconditions.checkState(currentModuleName == null);
        currentModuleName = extractFirstArgumentName(call);
      }
    }
  }

  @Override
  public void exitModule(NodeTraversal t, Node scopeRoot) {
    currentModuleName = null;
    shortRequiredNamespaces.clear();
    defaultExportNode = null;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (currentModuleName == null) {
      return;
    }
    switch (n.getType()) {
      case CALL:
        Node callee = n.getFirstChild();
        if (callee.matchesQualifiedName("goog.module")
            && !currentModuleName.equals(extractFirstArgumentName(n))) {
          t.report(n, MULTIPLE_MODULES_IN_FILE);
        } else if (callee.matchesQualifiedName("goog.provide")) {
          t.report(n, MODULE_AND_PROVIDES);
        } else if (callee.matchesQualifiedName("goog.require")) {
          checkRequireCall(t, n, parent);
        }
        break;
      case ASSIGN: {
        Node lhs = n.getFirstChild();
        if (lhs.isQualifiedName()
            && NodeUtil.getRootOfQualifiedName(lhs).matchesQualifiedName("exports")) {
          checkModuleExport(t, n, parent);
        }
        break;
      }
      case CLASS:
      case FUNCTION:
        if (!NodeUtil.isStatement(n)) {
          break;
        }
        // fallthrough
      case VAR:
      case LET:
      case CONST:
        if (t.inModuleHoistScope() && NodeUtil.getEnclosingClass(n) == null
            && NodeUtil.getEnclosingType(n, Token.OBJECTLIT) == null) {
          JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(n);
          if (jsdoc != null && jsdoc.isExport()) {
            t.report(n, AT_EXPORT_IN_GOOG_MODULE);
          }
        }
        break;
      case THIS:
        if (t.inModuleHoistScope()) {
          t.report(n, GOOG_MODULE_REFERENCES_THIS);
        }
        break;
      case THROW:
        if (t.inModuleHoistScope()) {
          t.report(n, GOOG_MODULE_USES_THROW);
        }
        break;
      case GETPROP:
        if (currentModuleName != null && n.matchesQualifiedName(currentModuleName)) {
          t.report(n, REFERENCE_TO_MODULE_GLOBAL_NAME);
        } else if (shortRequiredNamespaces.containsKey(n.getQualifiedName())) {
          String shortName = shortRequiredNamespaces.get(n.getQualifiedName());
          if (shortName == null) {
            t.report(n, REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME, n.getQualifiedName());
          } else {
            t.report(n, REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME,
                n.getQualifiedName(), shortName);
          }
        }
        break;
    }
  }

  private void checkModuleExport(NodeTraversal t, Node n, Node parent) {
    Preconditions.checkArgument(n.isAssign());
    Node lhs = n.getFirstChild();
    Preconditions.checkState(lhs.isQualifiedName());
    Preconditions.checkState(NodeUtil.getRootOfQualifiedName(lhs).matchesQualifiedName("exports"));
    if (lhs.isName()) {
      if  (defaultExportNode != null) {
        // Multiple exports
        t.report(n, EXPORT_REPEATED_ERROR, String.valueOf(defaultExportNode.getLineno()));
      } else if (!t.inModuleScope() || !parent.isExprResult()) {
        // Invalid export location.
        t.report(n, EXPORT_NOT_A_MODULE_LEVEL_STATEMENT);
      }
      defaultExportNode = lhs;
    }
    if ((lhs.isName() || !NodeUtil.isPrototypeProperty(lhs))
        && !NodeUtil.isLegacyGoogModuleFile(NodeUtil.getEnclosingScript(n))) {
      JSDocInfo jsDoc = n.getJSDocInfo();
      if (jsDoc != null && jsDoc.isExport()) {
        t.report(n, AT_EXPORT_IN_NON_LEGACY_GOOG_MODULE);
      }
    }
  }

  private String extractFirstArgumentName(Node callNode) {
    Node firstArg = callNode.getSecondChild();
    if (firstArg != null && firstArg.isString()) {
      return firstArg.getString();
    }
    return null;
  }

  private void checkRequireCall(NodeTraversal t, Node callNode, Node parent) {
    Preconditions.checkState(callNode.isCall());
    switch (parent.getType()) {
      case EXPR_RESULT:
        return;
      case NAME:
      case DESTRUCTURING_LHS:
        checkShortGoogRequireCall(t, callNode, parent.getParent());
        return;
    }
    t.report(callNode, REQUIRE_NOT_AT_TOP_LEVEL);
  }

  private void checkShortGoogRequireCall(NodeTraversal t, Node callNode, Node declaration) {
    if (declaration.isLet()) {
      t.report(declaration, LET_GOOG_REQUIRE);
    }
    if (declaration.getChildCount() != 1) {
      t.report(declaration, ONE_REQUIRE_PER_DECLARATION);
    }
    Node lhs = declaration.getFirstChild();
    String shortName = lhs.isName() ? lhs.getString() : null;
    shortRequiredNamespaces.put(extractFirstArgumentName(callNode), shortName);
  }
}
