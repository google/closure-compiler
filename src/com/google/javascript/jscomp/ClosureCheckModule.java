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

import static com.google.common.base.Ascii.isUpperCase;
import static com.google.common.base.Ascii.toLowerCase;
import static com.google.common.base.Ascii.toUpperCase;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_DESTRUCTURING_FORWARD_DECLARE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MODULE_USES_GOOG_MODULE_GET;

import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeTraversal.AbstractModuleCallback;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * Checks that goog.module() is used correctly.
 *
 * <p>Note that this file only does checks that can be done per-file. Whole program checks happen
 * during goog.module rewriting, in {@link ClosureRewriteModule}.
 */
public final class ClosureCheckModule extends AbstractModuleCallback implements CompilerPass {
  static final DiagnosticType AT_EXPORT_IN_GOOG_MODULE =
      DiagnosticType.error(
          "JSC_AT_EXPORT_IN_GOOG_MODULE",
          "@export has no effect on top-level names in a goog.module."
              + " Consider using goog.exportSymbol instead.");

  static final DiagnosticType AT_EXPORT_IN_NON_LEGACY_GOOG_MODULE =
      DiagnosticType.error(
          "JSC_AT_EXPORT_IN_NON_LEGACY_GOOG_MODULE",
          "@export is not allowed here in a non-legacy goog.module."
              + " Consider using goog.exportSymbol instead.");

  static final DiagnosticType GOOG_MODULE_IN_NON_MODULE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_IN_NON_MODULE",
          "goog.module() call must be the first statement in a module.");

  static final DiagnosticType GOOG_MODULE_MISPLACED =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_MISPLACED", "goog.module() call must be the first statement in a file.");

  static final DiagnosticType DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE =
      DiagnosticType.error(
          "JSC_DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE",
          "goog.module.declareLegacyNamespace may only be called in a goog.module.");

  static final DiagnosticType GOOG_MODULE_REFERENCES_THIS =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_REFERENCES_THIS", "The body of a goog.module cannot reference 'this'.");

  static final DiagnosticType GOOG_MODULE_USES_THROW =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_USES_THROW", "The body of a goog.module cannot use 'throw'.");
  static final DiagnosticType DUPLICATE_NAME_SHORT_REQUIRE =
      DiagnosticType.error(
          "JSC_DUPLICATE_NAME_SHORT_REQUIRE",
          "Found multiple goog.require statements importing identifier ''{0}''.");

  static final DiagnosticType INVALID_DESTRUCTURING_REQUIRE =
      DiagnosticType.error(
          "JSC_INVALID_DESTRUCTURING_REQUIRE",
          "Destructuring goog.require must be a simple object pattern.");

  static final DiagnosticType AWAIT_GOOG_REQUIRE_CALLS =
      DiagnosticType.error(
          "JSC_AWAIT_GOOG_REQUIRE_CALLS",
          "goog.require(Type) and goog.forwardDeclare can not be in an 'await' expression.");

  static final DiagnosticType LET_GOOG_REQUIRE =
      DiagnosticType.disabled(
          "JSC_LET_GOOG_REQUIRE",
          "Module imports must be constant. Please use ''const'' instead of ''let''.");

  static final DiagnosticType MULTIPLE_MODULES_IN_FILE =
      DiagnosticType.error(
          "JSC_MULTIPLE_MODULES_IN_FILE",
          "There should only be a single goog.module() statement per file.");

  static final DiagnosticType ONE_REQUIRE_PER_DECLARATION =
      DiagnosticType.error(
          "JSC_ONE_REQUIRE_PER_DECLARATION",
          "There may only be one goog.require() per var/let/const declaration.");

  static final DiagnosticType INCORRECT_SHORTNAME_CAPITALIZATION =
      DiagnosticType.disabled(
          "JSC_INCORRECT_SHORTNAME_CAPITALIZATION",
          "The capitalization of short name {0} is incorrect; it should be {1}.");

  static final DiagnosticType EXPORT_NOT_AT_MODULE_SCOPE =
      DiagnosticType.error(
          "JSC_EXPORT_NOT_AT_MODULE_SCOPE", "Exports must be at the top-level of a module");

  static final DiagnosticType EXPORT_NOT_A_STATEMENT =
      DiagnosticType.error("JSC_EXPORT_NOT_A_STATEMENT", "Exports should be a statement.");

  static final DiagnosticType EXPORT_REPEATED_ERROR =
      DiagnosticType.error(
          "JSC_EXPORT_REPEATED_ERROR",
          "Name cannot be exported multiple times. Previous export on line {0}.");

  static final DiagnosticType REFERENCE_TO_MODULE_GLOBAL_NAME =
      DiagnosticType.error(
          "JSC_REFERENCE_TO_MODULE_GLOBAL_NAME",
          "References to the global name of a module are not allowed. Perhaps you meant exports?");

  static final DiagnosticType REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME =
      DiagnosticType.disabled(
          "JSC_REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME",
          "Reference to fully qualified import name ''{0}''."
              + " Imports in goog.module should use the return value of"
              + " goog.require / goog.forwardDeclare instead.");

  public static final DiagnosticType REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME =
      DiagnosticType.disabled(
          "JSC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME",
          "Reference to fully qualified import name ''{0}''."
              + " Please use the short name ''{1}'' instead.");

  static final DiagnosticType USE_OF_GOOG_PROVIDE =
      DiagnosticType.disabled(
          "JSC_USE_OF_GOOG_PROVIDE",
          "goog.provide is deprecated in favor of goog.module."
          );

  static final DiagnosticType REQUIRE_NOT_AT_TOP_LEVEL =
      DiagnosticType.error(
          "JSC_REQUIRE_NOT_AT_TOP_LEVEL", "goog.require() must be called at file scope.");

  static final DiagnosticType LEGACY_NAMESPACE_NOT_AT_TOP_LEVEL =
      DiagnosticType.error(
          "JSC_LEGACY_NAMESPACE_NOT_AT_TOP_LEVEL",
          "goog.module.declareLegacyNamespace() does not return a value");

  static final DiagnosticType LEGACY_NAMESPACE_NOT_AFTER_GOOG_MODULE =
      DiagnosticType.error(
          "JSC_LEGACY_NAMESPACE_NOT_AT_TOP_LEVEL",
          "goog.module.declareLegacyNamespace() must be immediately after the"
              + " goog.module('...'); call");

  static final DiagnosticType LEGACY_NAMESPACE_ARGUMENT =
      DiagnosticType.error(
          "JSC_LEGACY_NAMESPACE_ARGUMENT",
          "goog.module.declareLegacyNamespace() takes no arguments");

  private static final QualifiedName GOOG_MODULE = QualifiedName.of("goog.module");
  private static final QualifiedName GOOG_PROVIDE = QualifiedName.of("goog.provide");
  private static final QualifiedName GOOG_REQUIRE = QualifiedName.of("goog.require");
  private static final QualifiedName GOOG_REQUIRE_TYPE = QualifiedName.of("goog.requireType");
  private static final QualifiedName GOOG_MODULE_GET = QualifiedName.of("goog.module.get");
  private static final QualifiedName GOOG_FORWARD_DECLARE = QualifiedName.of("goog.forwardDeclare");
  private static final QualifiedName GOOG_MODULE_DECLARE_LEGACY_NAMESPACE =
      QualifiedName.of("goog.module.declareLegacyNamespace");

  private static class ModuleInfo {
    // Name of the module in question (i.e. the argument to goog.module)
    private final String name;
    // Mapping from fully qualified goog.required names to the import LHS node.
    // For standalone goog.require()s the value is the EXPR_RESULT node.
    private final Map<String, Node> importsByLongRequiredName = new HashMap<>();
    // Module-local short names for goog.required symbols.
    private final Set<String> shortImportNames = new HashSet<>();
    // A map from the export names "exports.name" to the nodes of those named exports.
    // The default export is keyed with just "exports"
    private final Map<String, Node> exportNodesByName = new HashMap<>();

    ModuleInfo(String moduleName) {
      this.name = moduleName;
    }
  }

  private @Nullable ModuleInfo currentModuleInfo = null;

  public ClosureCheckModule(AbstractCompiler compiler, ModuleMetadataMap moduleMetadataMap) {
    super(compiler, moduleMetadataMap);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void enterModule(ModuleMetadata currentModule, Node moduleScopeRoot) {
    if (!currentModule.isGoogModule()) {
      return;
    }

    checkState(currentModuleInfo == null);
    checkState(!currentModule.googNamespaces().isEmpty());
    currentModuleInfo = new ModuleInfo(Iterables.getFirst(currentModule.googNamespaces(), ""));
  }

  @Override
  public void exitModule(ModuleMetadata currentModule, Node moduleScopeRoot) {
    currentModuleInfo = null;
  }

  @Override
  protected void visit(
      NodeTraversal t,
      Node n,
      @Nullable ModuleMetadata currentModule,
      @Nullable Node moduleScopeRoot) {
    Node parent = n.getParent();
    if (currentModuleInfo == null) {
      if (NodeUtil.isCallTo(n, GOOG_MODULE)) {
        t.report(n, GOOG_MODULE_IN_NON_MODULE);
      } else if (NodeUtil.isGoogModuleDeclareLegacyNamespaceCall(n)) {
        t.report(n, DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE);
      } else if (NodeUtil.isCallTo(n, GOOG_PROVIDE)) {
        // This error is reported here, rather than in a provide-specific pass, because it
        // must be reported prior to ClosureRewriteModule converting legacy goog.modules into
        // goog.provides.
        t.report(n, USE_OF_GOOG_PROVIDE);
      }
      return;
    }
    JSDocInfo jsDoc = n.getJSDocInfo();
    if (jsDoc != null) {
      checkJSDoc(t, jsDoc);
    }
    switch (n.getToken()) {
      case CALL:
        Node callee = n.getFirstChild();
        if (GOOG_MODULE.matches(callee)) {
          if (!currentModuleInfo.name.equals(extractFirstArgumentName(n))) {
            t.report(n, MULTIPLE_MODULES_IN_FILE);
          } else if (!isFirstExpressionInGoogModule(parent)) {
            t.report(n, GOOG_MODULE_MISPLACED);
          }
        } else if (GOOG_REQUIRE.matches(callee)
            || GOOG_REQUIRE_TYPE.matches(callee)
            || GOOG_FORWARD_DECLARE.matches(callee)) {
          checkRequireCall(t, n, parent);
        } else if (GOOG_MODULE_GET.matches(callee) && t.inModuleHoistScope()) {
          t.report(n, MODULE_USES_GOOG_MODULE_GET);
        } else if (GOOG_MODULE_DECLARE_LEGACY_NAMESPACE.matches(callee)) {
          checkLegacyNamespaceCall(t, n, parent);
        }
        break;
      case ASSIGN:
        {
          if (isExportLhs(n.getFirstChild())) {
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
        if (t.inModuleHoistScope()
            && (n.isClass() || NodeUtil.getEnclosingClass(n) == null)
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
      case NAME:
      case GETPROP:
        if (n.matchesQualifiedName(currentModuleInfo.name)) {
          if (n.isGetProp()) {
            // This warning makes sense on NAME nodes as well,
            // but it would require a cleanup to land.
            t.report(n, REFERENCE_TO_MODULE_GLOBAL_NAME);
          }
        } else {
          checkImproperReferenceToImport(
              t, n, n.getQualifiedName(), parent.isGetProp() ? parent.getString() : null);
        }
        break;
      default:
        break;
    }
  }

  private void checkJSDoc(NodeTraversal t, JSDocInfo jsDoc) {
    for (Node typeNode : jsDoc.getTypeNodes()) {
      checkTypeExpression(t, typeNode);
    }
  }

  private void checkTypeExpression(NodeTraversal t, Node typeNode) {
    NodeUtil.visitPreOrder(
        typeNode,
        new NodeUtil.Visitor() {
          @Override
          public void visit(Node node) {
            if (!node.isStringLit()) {
              return;
            }

            String qname = node.getString();
            String nextQnamePart = null;

            while (true) {
              checkImproperReferenceToImport(t, node, qname, nextQnamePart);

              int lastDot = qname.lastIndexOf('.');
              if (lastDot < 0) {
                return;
              }
              nextQnamePart = qname.substring(lastDot + 1);
              qname = qname.substring(0, lastDot);
            }
          }
        });
  }

  private static boolean isLocalVar(Var v) {
    if (v == null) {
      return false;
    }
    return v.isLocal();
  }

  private void checkImproperReferenceToImport(
      NodeTraversal t, Node n, String qname, @Nullable String nextQnamePart) {
    if (qname == null) {
      return;
    }

    Node importLhs = currentModuleInfo.importsByLongRequiredName.get(qname);
    if (importLhs == null) {
      return;
    }

    if (!qname.contains(".") && isLocalVar(t.getScope().getVar(qname))) {
      return;
    }

    if (importLhs.isName()) {
      this.compiler.report(
          JSError.make(
              n,
              REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME,
              qname,
              importLhs.getString()));
      return;
    } else if (importLhs.isDestructuringLhs() && nextQnamePart != null) {
      Node objPattern = importLhs.getFirstChild();
      checkState(objPattern.isObjectPattern(), objPattern);
      for (Node strKey = objPattern.getFirstChild(); strKey != null; strKey = strKey.getNext()) {
        // const {foo: barFoo} = goog.require('ns.bar');
        // Should use the short name "barFoo" instead of "ns.bar.foo".
        if (strKey.hasOneChild() && strKey.getString().equals(nextQnamePart)) {
          Node parent = n.getParent();
          this.compiler.report(
              JSError.make(
                  (parent != null && parent.isGetProp()) ? parent : n,
                  REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME,
                  qname + "." + nextQnamePart,
                  strKey.getFirstChild().getString()));
          return;
        }
      }
    }

    this.compiler.report(JSError.make(n, REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME, qname));
  }

  /** Is this the LHS of a goog.module export? i.e. Either "exports" or "exports.name" */
  private static boolean isExportLhs(Node lhs) {
    if (!lhs.isQualifiedName()) {
      return false;
    }
    return lhs.matchesName("exports")
        || (lhs.isGetProp() && lhs.getFirstChild().matchesName("exports"));
  }

  private void checkModuleExport(NodeTraversal t, Node n, Node parent) {
    checkArgument(n.isAssign());
    Node lhs = n.getFirstChild();
    checkState(isExportLhs(lhs));
    // Check multiple exports of the same name
    Node previousDefinition = currentModuleInfo.exportNodesByName.get(lhs.getQualifiedName());
    if (previousDefinition != null) {
      int previousLine = previousDefinition.getLineno();
      t.report(n, EXPORT_REPEATED_ERROR, String.valueOf(previousLine));
    }
    // Check exports in invalid program position
    Node defaultExportNode = currentModuleInfo.exportNodesByName.get("exports");
    // If we have never seen an `exports =` default export assignment, or this is the
    // default export, then treat this assignment as an export and do the checks it is well formed.
    if (defaultExportNode == null || lhs.matchesName("exports")) {
      currentModuleInfo.exportNodesByName.put(lhs.getQualifiedName(), lhs);
      if (!t.inModuleScope()) {
        t.report(n, EXPORT_NOT_AT_MODULE_SCOPE);
      } else if (!parent.isExprResult()) {
        t.report(n, EXPORT_NOT_A_STATEMENT);
      }
    }
    // Check @export on a module local name
    if (!NodeUtil.isPrototypeProperty(lhs)
        && !NodeUtil.isLegacyGoogModuleFile(NodeUtil.getEnclosingScript(n))) {
      JSDocInfo jsDoc = n.getJSDocInfo();
      if (jsDoc != null && jsDoc.isExport()) {
        t.report(n, AT_EXPORT_IN_NON_LEGACY_GOOG_MODULE);
      }
    }
  }

  private static @Nullable String extractFirstArgumentName(Node callNode) {
    Node firstArg = callNode.getSecondChild();
    if (firstArg != null && firstArg.isStringLit()) {
      return firstArg.getString();
    }
    return null;
  }

  private void checkRequireCall(NodeTraversal t, Node callNode, Node parent) {
    checkState(callNode.isCall());
    checkState(callNode.getLastChild().isStringLit());
    switch (parent.getToken()) {
      case EXPR_RESULT:
        String key = extractFirstArgumentName(callNode);
        currentModuleInfo.importsByLongRequiredName.putIfAbsent(key, parent);
        return;
      case NAME:
      case DESTRUCTURING_LHS:
        checkShortGoogRequireCall(t, callNode, parent.getParent());
        return;
      case AWAIT:
        Token grandParentToken = parent.getParent().getToken();
        if (grandParentToken.equals(Token.DESTRUCTURING_LHS)
            || grandParentToken.equals(Token.NAME)) {
          checkShortGoogRequireCall(t, callNode, parent.getGrandparent());
        }
        return;
      default:
        break;
    }
    t.report(callNode, REQUIRE_NOT_AT_TOP_LEVEL);
  }

  private void checkShortGoogRequireCall(NodeTraversal t, Node callNode, Node declaration) {
    if (declaration.isLet() && !GOOG_FORWARD_DECLARE.matches(callNode.getFirstChild())) {
      t.report(declaration, LET_GOOG_REQUIRE);
    }
    if (!declaration.hasOneChild()) {
      t.report(declaration, ONE_REQUIRE_PER_DECLARATION);
      return;
    }
    Node lhs = declaration.getFirstChild();
    if (lhs.isDestructuringLhs()) {
      if (!isValidDestructuringImport(lhs)) {
        t.report(declaration, INVALID_DESTRUCTURING_REQUIRE);
      }
      if (GOOG_FORWARD_DECLARE.matches(callNode.getFirstChild())) {
        t.report(lhs, INVALID_DESTRUCTURING_FORWARD_DECLARE);
      }
    } else {
      checkState(lhs.isName());
      checkShortName(t, lhs, callNode.getLastChild().getString());
    }
    currentModuleInfo.importsByLongRequiredName.put(extractFirstArgumentName(callNode), lhs);
    NodeUtil.visitLhsNodesInNode(
        declaration,
        (nameNode) -> {
          String name = nameNode.getString();
          if (!currentModuleInfo.shortImportNames.add(name)) {
            t.report(nameNode, DUPLICATE_NAME_SHORT_REQUIRE, name);
          }
        });
  }

  private static void checkShortName(NodeTraversal t, Node shortNameNode, String namespace) {
    String nextQnamePart = shortNameNode.getString();
    String lastSegment = namespace.substring(namespace.lastIndexOf('.') + 1);
    if (nextQnamePart.equals(lastSegment) || lastSegment.isEmpty()) {
      return;
    }

    if (isUpperCase(nextQnamePart.charAt(0)) != isUpperCase(lastSegment.charAt(0))) {
      char newStartChar =
          isUpperCase(nextQnamePart.charAt(0))
              ? toLowerCase(nextQnamePart.charAt(0))
              : toUpperCase(nextQnamePart.charAt(0));
      String correctedName = newStartChar + nextQnamePart.substring(1);
      t.report(shortNameNode, INCORRECT_SHORTNAME_CAPITALIZATION, nextQnamePart, correctedName);
    }
  }

  private static boolean isValidDestructuringImport(Node destructuringLhs) {
    checkArgument(destructuringLhs.isDestructuringLhs());
    Node objectPattern = destructuringLhs.getFirstChild();
    if (!objectPattern.isObjectPattern()) {
      return false;
    }
    for (Node stringKey = objectPattern.getFirstChild();
        stringKey != null;
        stringKey = stringKey.getNext()) {
      if (!stringKey.isStringKey()) {
        return false;
      } else if (!stringKey.getFirstChild().isName()) {
        return false;
      }
    }
    return true;
  }

  /** Validates the position of a goog.module.declareLegacyNamespace(); call */
  private static void checkLegacyNamespaceCall(NodeTraversal t, Node callNode, Node parent) {
    checkArgument(callNode.isCall());
    if (callNode.getChildCount() > 1) {
      t.report(callNode, LEGACY_NAMESPACE_ARGUMENT);
    }
    if (!parent.isExprResult()) {
      t.report(callNode, LEGACY_NAMESPACE_NOT_AT_TOP_LEVEL);
      return;
    }
    Node prev = parent.getPrevious();
    if (prev == null || !NodeUtil.isGoogModuleCall(prev)) {
      t.report(callNode, LEGACY_NAMESPACE_NOT_AFTER_GOOG_MODULE);
    }
  }

  /** Whether this is the first statement in a module */
  private static boolean isFirstExpressionInGoogModule(Node node) {
    if (!node.isExprResult() || node.getPrevious() != null) {
      return false;
    }
    Node statementBlock = node.getParent();
    return statementBlock.isModuleBody() || NodeUtil.isBundledGoogModuleScopeRoot(statementBlock);
  }
}
