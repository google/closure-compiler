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
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Process aliases in goog.modules.
 * <pre>
 * goog.module('foo.Bar');
 * var Baz = goog.require('foo.Baz');
 * class Bar extends Baz {}
 * exports = Bar;
 * </pre>
 *
 * becomes
 *
 * <pre>
 * class module$contents$foo$Bar_Bar extends module$exports$foo$Baz {}
 * var module$exports$foo$Bar = module$contents$foo$Bar_Bar;
 * </pre>
 *
 * and
 *
 * <pre>
 * goog.loadModule(function(exports) {
 *   goog.module('foo.Bar');
 *   var Baz = goog.require('foo.Baz');
 *   class Bar extends Baz {}
 *   exports = Bar;
 *   return exports;
 * })
 * </pre>
 *
 * becomes
 *
 * <pre>
 * class module$contents$foo$Bar_Bar extends module$exports$foo$Baz {}
 * var module$exports$foo$Bar = module$contents$foo$Bar_Bar;
 * </pre>
 *
 * @author johnlenz@google.com (John Lenz)
 */
final class ClosureRewriteModule implements HotSwapCompilerPass {

  // TODO(johnlenz): handle non-namespace module identifiers aka 'foo/bar'

  static final DiagnosticType INVALID_MODULE_NAMESPACE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_MODULE_NAMESPACE",
          "goog.module parameter must be string literals");

  static final DiagnosticType INVALID_PROVIDE_NAMESPACE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_PROVIDE_NAMESPACE",
          "goog.provide parameter must be a string literal.");

  static final DiagnosticType INVALID_REQUIRE_NAMESPACE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_REQUIRE_NAMESPACE",
          "goog.require parameter must be a string literal.");

  static final DiagnosticType INVALID_FORWARD_DECLARE_NAMESPACE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_FORWARD_DECLARE_NAMESPACE",
          "goog.forwardDeclare parameter must be a string literal.");

  static final DiagnosticType INVALID_GET_NAMESPACE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_GET_NAMESPACE",
          "goog.module.get parameter must be a string literal.");

  static final DiagnosticType INVALID_PROVIDE_CALL =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_PROVIDE_CALL",
          "goog.provide can not be called in goog.module.");

  static final DiagnosticType INVALID_GET_CALL_SCOPE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_GET_CALL_SCOPE",
          "goog.module.get can not be called in global scope.");

  static final DiagnosticType INVALID_GET_ALIAS =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_GET_ALIAS",
          "goog.module.get should not be aliased.");

  static final DiagnosticType INVALID_EXPORT_COMPUTED_PROPERTY =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_EXPORT_COMPUTED_PROPERTY",
          "Computed properties are not yet supported in goog.module exports.");

  static final DiagnosticType USELESS_USE_STRICT_DIRECTIVE =
      DiagnosticType.disabled(
          "JSC_USELESS_USE_STRICT_DIRECTIVE",
          "'use strict' is unnecessary in goog.module files.");

  static final DiagnosticType DUPLICATE_MODULE =
      DiagnosticType.error(
          "JSC_DUPLICATE_MODULE",
          "Duplicate module: {0}");

  static final DiagnosticType DUPLICATE_NAMESPACE =
      DiagnosticType.error(
          "JSC_DUPLICATE_NAMESPACE",
          "Duplicate namespace: {0}");

  static final DiagnosticType MISSING_MODULE =
      DiagnosticType.error(
          "JSC_MISSING_MODULE",
          "Required module \"{0}\" never defined.");

  static final DiagnosticType MISSING_MODULE_OR_PROVIDE =
      DiagnosticType.error(
          "JSC_MISSING_MODULE_OR_PROVIDE",
          "Required namespace \"{0}\" never defined.");

  static final DiagnosticType LATE_PROVIDE_ERROR =
      DiagnosticType.error(
          "JSC_LATE_PROVIDE_ERROR",
          "Required namespace \"{0}\" not provided yet.");

  static final DiagnosticType IMPORT_INLINING_SHADOWS_VAR =
      DiagnosticType.error(
          "JSC_IMPORT_INLINING_SHADOWS_VAR",
          "Inlining of reference to import \"{1}\" shadows var \"{0}\".");

  static final DiagnosticType QUALIFIED_REFERENCE_TO_GOOG_MODULE =
      DiagnosticType.error(
          "JSC_QUALIFIED_REFERENCE_TO_GOOG_MODULE",
          "Fully qualified reference to name ''{0}'' provided by a goog.module.\n"
              + "Either use short import syntax or"
              + " convert module to use goog.module.declareLegacyNamespace.");

  private static final ImmutableSet<String> USE_STRICT_ONLY = ImmutableSet.of("use strict");

  private static final String MODULE_EXPORTS_PREFIX = "module$exports$";

  private static final String MODULE_CONTENTS_PREFIX = "module$contents$";

  private final AbstractCompiler compiler;
  private final PreprocessorSymbolTable preprocessorSymbolTable;

  /**
   * Indicates where new nodes should be added in relation to some other node.
   */
  private static enum AddAt {
    BEFORE,
    AFTER
  }

  private static enum ScopeType {
    EXEC_CONTEXT,
    BLOCK
  }

  /**
   * Describes the context of an "unrecognized require" scenario so that it will be possible to
   * categorize and report it as either a "not provided yet" or "not provided at all" error at the
   * end.
   */
  private static final class UnrecognizedRequire {
    final Node requireNode;
    final String legacyNamespace;
    final boolean mustBeGoogModule;

    UnrecognizedRequire(Node requireNode, String legacyNamespace, boolean mustBeGoogModule) {
      this.requireNode = requireNode;
      this.legacyNamespace = legacyNamespace;
      this.mustBeGoogModule = mustBeGoogModule;
    }
  }

  private final class ScriptDescription {
    boolean isModule;
    boolean declareLegacyNamespace;
    String legacyNamespace; // "a.b.c"
    String binaryNamespace; // "module$exports$a$b$c
    String contentsPrefix; // "module$contents$a$b$c_
    final Set<String> topLevelNames = new HashSet<>(); // For prefixed content renaming.
    final Deque<ScriptDescription> childScripts = new LinkedList<>(); // For goog.loadModule()
    final Set<String> googModuleGettedNamespaces = new HashSet<>(); // {"some.goog.module", ...}
    final Map<String, String> legacyNamespacesByAlias = new HashMap<>(); // For alias inlining.

    /**
     * Transient state.
     */
    boolean willCreateExportsObject;
    boolean hasCreatedExportObject;
    Node rootNode; // For recognizing top level names. Changes when unwrapping goog.scope()s.

    public void addChildScript(ScriptDescription childScript) {
      childScripts.addLast(childScript);
    }

    public ScriptDescription removeFirstChildScript() {
      return childScripts.removeFirst();
    }
  }

  private class ScriptRecorder extends AbstractPreOrderCallback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isModuleFile(n)) {
        checkAndSetStrictModeDirective(t, n);
      }

      switch (n.getType()) {
        case Token.CALL:
          if (isCallTo(n, "goog.loadModule")) {
            recordGoogLoadModule(n);
          }
          if (isCallTo(n, "goog.module")) {
            recordGoogModule(t, n);
          }
          if (isCallTo(n, "goog.module.declareLegacyNamespace")) {
            recordGoogDeclareLegacyNamespace();
          }
          if (isCallTo(n, "goog.provide")) {
            recordGoogProvide(t, n);
          }
          if (isCallTo(n, "goog.require")) {
            recordGoogRequire(t, n, true);
          }
          if (isCallTo(n, "goog.forwardDeclare")) {
            recordGoogForwardDeclare(t, n);
          }
          if (isCallTo(n, "goog.module.get")) {
            recordGoogModuleGet(t, n);
          }
          break;

        case Token.CLASS:
        case Token.FUNCTION:
          if (isTopLevel(t, n, ScopeType.BLOCK)) {
            recordTopLevelClassOrFunctionName(n);
          }
          break;

        case Token.CONST:
        case Token.LET:
        case Token.VAR:
          if (isTopLevel(t, n, n.isVar() ? ScopeType.EXEC_CONTEXT : ScopeType.BLOCK)) {
            recordTopLevelVarNames(n);
          }
          break;

        case Token.NAME:
          maybeRecordExportDeclaration(n);
          break;

        case Token.RETURN:
          if (isTopLevel(t, n, ScopeType.BLOCK)) {
            recordModuleReturn();
          }
          break;
      }

      return true;
    }
  }

  private class ScriptUpdater extends AbstractPreOrderCallback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.CALL:
          if (isCallTo(n, "goog.loadModule")) {
            updateGoogLoadModule(n);
          }
          if (isCallTo(n, "goog.module")) {
            updateGoogModule(n);
          }
          if (isCallTo(n, "goog.module.declareLegacyNamespace")) {
            updateGoogDeclareLegacyNamespace(n);
          }
          if (isCallTo(n, "goog.require")) {
            updateGoogRequire(t, n);
          }
          if (isCallTo(n, "goog.forwardDeclare")) {
            updateGoogForwardDeclare(t, n);
          }
          if (isCallTo(n, "goog.module.get")) {
            updateGoogModuleGetCall(t, n);
          }
          break;

        case Token.GETPROP:
          if (isExportPropertyAssignment(n)) {
            updateExportsPropertyAssignment(n);
          } else if (n.isQualifiedName()) {
            checkQualifiedName(t, n);
          }
          break;

        case Token.NAME:
          maybeUpdateTopLevelName(t, n);
          maybeUpdateExportDeclaration(t, n);
          maybeUpdateExportNameRef(n);
          break;

        case Token.RETURN:
          if (isTopLevel(t, n, ScopeType.EXEC_CONTEXT)) {
            updateModuleReturn(n);
          }
          break;
      }

      if (n.getJSDocInfo() != null) {
        rewriteJsdoc(n.getJSDocInfo());
      }

      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.SCRIPT:
          updateEndScript(n);
          break;
      }
    }
  }

  /**
   * Checks that imports of goog.module provided files are used correctly.
   */
  private void checkQualifiedName(NodeTraversal t, Node qnameNode) {
    String qname = qnameNode.getQualifiedName();
    if (isLegacyByGoogModuleNamespace.containsKey(qname)
        && !isLegacyByGoogModuleNamespace.get(qname)) {
      t.report(qnameNode, QUALIFIED_REFERENCE_TO_GOOG_MODULE, qname);
    }
  }

  /**
   * Rewrites JsDoc type references to match AST changes resulting from imported alias inlining,
   * module content renaming of top level constructor functions and classes, and module renaming
   * from fully qualified legacy namespace to its binary name.
   */
  private void rewriteJsdoc(JSDocInfo info) {
    for (Node typeNode : info.getTypeNodes()) {
      NodeUtil.visitPreOrder(typeNode, replaceJsDocRefs, Predicates.<Node>alwaysTrue());
    }
  }

  /**
   * Rewrites JsDoc type references to match AST changes resulting from imported alias inlining,
   * module content renaming of top level constructor functions and classes, and module renaming
   * from fully qualified legacy namespace to its binary name.
   */
  private final NodeUtil.Visitor replaceJsDocRefs =
      new NodeUtil.Visitor() {
        @Override
        public void visit(Node typeRefNode) {
          if (!typeRefNode.isString()) {
            return;
          }
          // A type name that might be simple like "Foo" or qualified like "foo.Bar".
          String typeName = typeRefNode.getString();

          // Tries to rename progressively shorter type prefixes like "foo.Bar.Baz", "foo.Bar",
          // "foo".
          String prefixTypeName = typeName;
          String suffix = "";
          do {
            // If the name is an alias for an imported namespace rewrite from
            // "{Foo}" to
            // "{module$exports$bar$Foo}" or
            // "{bar.Foo}"
            boolean nameIsAnAlias =
                currentScript.legacyNamespacesByAlias.containsKey(prefixTypeName);
            if (nameIsAnAlias) {
              String legacyNamespace = currentScript.legacyNamespacesByAlias.get(prefixTypeName);
              boolean targetNamespaceIsAGoogModule =
                  isLegacyByGoogModuleNamespace.containsKey(legacyNamespace);
              String aliasedNamespace =
                  targetNamespaceIsAGoogModule
                      ? toBinaryNamespace(legacyNamespace)
                      : legacyNamespace;
              safeSetString(typeRefNode, aliasedNamespace + suffix);
              return;
            }

            // If this is a module and the type name is the name of a top level var/function/class
            // defined in this script then that var will have been previously renamed from Foo to
            // module$contents$Foo_Foo. Update the JsDoc reference to match.
            if (currentScript.isModule && currentScript.topLevelNames.contains(prefixTypeName)) {
              safeSetString(typeRefNode, currentScript.contentsPrefix + typeName);
              return;
            }

            boolean targetIsAGoogModule = isLegacyByGoogModuleNamespace.containsKey(prefixTypeName);
            if (legacyScriptNamespacesAndPrefixes.contains(prefixTypeName)
                && !targetIsAGoogModule) {
              // This thing is definitely coming from a legacy script and so the fully qualified
              // type name will always resolve as is.
              return;
            }

            // If the typeName is a reference to a fully qualified legacy namespace like
            // "foo.bar.Baz" of something that is actually a module then rewrite the JsDoc reference
            // to "module$exports$Bar".
            if (targetIsAGoogModule) {
              safeSetString(typeRefNode, toBinaryNamespace(prefixTypeName) + suffix);
              return;
            }

            if (prefixTypeName.contains(".")) {
              prefixTypeName = prefixTypeName.substring(0, prefixTypeName.lastIndexOf('.'));
              suffix = typeName.substring(prefixTypeName.length(), typeName.length());
            } else {
              return;
            }
          } while (true);
        }
      };

  // Per script state needed for rewriting including nested goog.loadModule() calls.
  private Deque<ScriptDescription> scriptStack = new LinkedList<>();
  private ScriptDescription currentScript = null;
  private List<Node> loadModuleStatements = new ArrayList<>(); // Statements to be inlined.

  // Global state tracking an association between the dotted names of goog.module()s and whether
  // the goog.module declares itself as a legacy namespace.
  // Allows for detecting duplicate goog.module()s and for rewriting fully qualified
  // JsDoc type references to goog.module() types in legacy scripts.
  private Map<String, Boolean> isLegacyByGoogModuleNamespace = new HashMap<>();
  private Set<String> legacyScriptNamespaces = new HashSet<>();
  private Set<String> legacyScriptNamespacesAndPrefixes = new HashSet<>();
  private List<UnrecognizedRequire> unrecognizedRequires = new ArrayList<>();

  ClosureRewriteModule(AbstractCompiler compiler, PreprocessorSymbolTable preprocessorSymbolTable) {
    this.compiler = compiler;
    this.preprocessorSymbolTable = preprocessorSymbolTable;
  }

  @Override
  public void process(Node externs, Node root) {
    Deque<ScriptDescription> scriptDefinitions = new LinkedList<>();

    // Record all the scripts first so that the googModuleNamespaces global state can be complete
    // before doing any updating also queue up ScriptDefinitions for later use in ScriptUpdater
    // runs.
    for (Node c = root.getFirstChild(); c != null; c = c.getNext()) {
      pushScript(new ScriptDescription());
      currentScript.rootNode = c;
      scriptDefinitions.addLast(currentScript);
      NodeTraversal.traverseEs6(compiler, c, new ScriptRecorder());
      popScript();
    }

    if (compiler.hasHaltingErrors()) {
      return;
    }

    // Update scripts using the now complete googModuleNamespaces global state and unspool the
    // ScriptDefinitions that were queued up by all the recording.
    for (Node c = root.getFirstChild(); c != null; c = c.getNext()) {
      pushScript(scriptDefinitions.removeFirst());
      NodeTraversal.traverseEs6(compiler, c, new ScriptUpdater());
      popScript();

      inlineGoogLoadModuleCalls();
    }

    reportUnrecognizedRequires();
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    Preconditions.checkState(scriptRoot.isScript());

    pushScript(new ScriptDescription());
    currentScript.rootNode = scriptRoot;
    NodeTraversal.traverseEs6(compiler, scriptRoot, new ScriptRecorder());

    if (compiler.hasHaltingErrors()) {
      return;
    }

    NodeTraversal.traverseEs6(compiler, scriptRoot, new ScriptUpdater());
    popScript();

    inlineGoogLoadModuleCalls();

    reportUnrecognizedRequires();
  }

  private void recordGoogLoadModule(Node call) {
    pushScript(new ScriptDescription());

    currentScript.rootNode = NodeUtil.getEnclosingStatement(call).getParent();
    currentScript.isModule = true;
  }

  private void recordGoogModule(NodeTraversal t, Node call) {
    Node legacyNamespaceNode = call.getLastChild();
    if (!legacyNamespaceNode.isString()) {
      t.report(legacyNamespaceNode, INVALID_MODULE_NAMESPACE);
      return;
    }
    String legacyNamespace = legacyNamespaceNode.getString();

    currentScript.isModule = true;
    currentScript.rootNode = NodeUtil.getEnclosingStatement(call).getParent();
    currentScript.legacyNamespace = legacyNamespace;
    currentScript.binaryNamespace = toBinaryNamespace(legacyNamespace);
    currentScript.contentsPrefix = toModuleContentsPrefix(legacyNamespace);

    // If some other script is advertising itself as a goog.module() with this same namespace.
    if (isLegacyByGoogModuleNamespace.containsKey(legacyNamespace)) {
      t.report(call, DUPLICATE_MODULE, legacyNamespace);
    }
    if (legacyScriptNamespaces.contains(legacyNamespace)) {
      t.report(call, DUPLICATE_NAMESPACE, legacyNamespace);
    }

    // Assume goog.modules don't declare legacy namespaces until we see otherwise.
    isLegacyByGoogModuleNamespace.put(legacyNamespace, false);
  }

  private void recordGoogDeclareLegacyNamespace() {
    currentScript.declareLegacyNamespace = true;
    isLegacyByGoogModuleNamespace.put(currentScript.legacyNamespace, true);
  }

  private void recordGoogProvide(NodeTraversal t, Node call) {
    Node legacyNamespaceNode = call.getLastChild();
    if (!legacyNamespaceNode.isString()) {
      t.report(legacyNamespaceNode, INVALID_PROVIDE_NAMESPACE);
      return;
    }
    String legacyNamespace = legacyNamespaceNode.getString();

    if (currentScript.isModule) {
      t.report(legacyNamespaceNode, INVALID_PROVIDE_CALL);
    }
    if (isLegacyByGoogModuleNamespace.containsKey(legacyNamespace)) {
      t.report(call, DUPLICATE_NAMESPACE, legacyNamespace);
    }

    // Log legacy namespaces and prefixes.
    legacyScriptNamespaces.add(legacyNamespace);
    LinkedList<String> parts = Lists.newLinkedList(Splitter.on('.').split(legacyNamespace));
    while (!parts.isEmpty()) {
      legacyScriptNamespacesAndPrefixes.add(Joiner.on('.').join(parts));
      parts.removeLast();
    }
  }

  private void recordGoogRequire(NodeTraversal t, Node call, boolean reportBadRequireErrors) {
    maybeSplitMultiVar(call);

    Node legacyNamespaceNode = call.getLastChild();
    Node statementNode = NodeUtil.getEnclosingStatement(call);
    if (!legacyNamespaceNode.isString()) {
      t.report(legacyNamespaceNode, INVALID_REQUIRE_NAMESPACE);
      return;
    }
    String legacyNamespace = legacyNamespaceNode.getString();

    // If the current script is a module or the require statement has a return value that is stored
    // in an alias then the require is goog.module() style.
    boolean currentScriptIsAModule = currentScript.isModule;
    // "var Foo = goog.require("bar.Foo");" style.
    boolean requireStoredInSimpleAlias =
        call.getParent().isName() && NodeUtil.isNameDeclaration(call.getGrandparent());
    if (currentScriptIsAModule
        && requireStoredInSimpleAlias
        && isTopLevel(t, statementNode, ScopeType.EXEC_CONTEXT)) {
      // Record alias -> legacyNamespace associations for possible later inlining.
      String aliasName = statementNode.getFirstChild().getString();
      recordImportAlias(aliasName, legacyNamespace);
    }

    // Maybe report an error if there is an attempt to import something that is expected to be a
    // goog.module() but no such goog.module() has been defined.
    boolean targetIsAModule = isLegacyByGoogModuleNamespace.containsKey(legacyNamespace);
    boolean targetIsALegacyScript = legacyScriptNamespaces.contains(legacyNamespace);
    if (reportBadRequireErrors
        && currentScript.isModule
        && !targetIsAModule
        && !targetIsALegacyScript) {
      unrecognizedRequires.add(
          new UnrecognizedRequire(call, legacyNamespace, false /* mustBeGoogModule */));
    }
  }

  private void recordGoogForwardDeclare(NodeTraversal t, Node call) {
    Node namespaceNode = call.getLastChild();
    if (call.getChildCount() != 2 || !namespaceNode.isString()) {
      t.report(namespaceNode, INVALID_FORWARD_DECLARE_NAMESPACE);
      return;
    }

    // modules already require that goog.forwardDeclare() and goog.module.get() occur in matched
    // pairs. If a "missing module" error were to occur here it would also occur in the matching
    // goog.module.get(). To avoid reporting the error twice suppress it here.
    boolean reportBadRequireErrors = false;

    // For purposes of import collection goog.forwardDeclare is the same as goog.require;
    recordGoogRequire(t, call, reportBadRequireErrors);
  }

  private void recordGoogModuleGet(NodeTraversal t, Node call) {
    Node legacyNamespaceNode = call.getLastChild();
    if (call.getChildCount() != 2 || !legacyNamespaceNode.isString()) {
      t.report(legacyNamespaceNode, INVALID_GET_NAMESPACE);
      return;
    }
    if (!currentScript.isModule && t.inGlobalScope()) {
      t.report(legacyNamespaceNode, INVALID_GET_CALL_SCOPE);
      return;
    }
    String legacyNamespace = legacyNamespaceNode.getString();

    if (!isLegacyByGoogModuleNamespace.containsKey(legacyNamespace)) {
      if (!currentScript.isModule) {
        // goog.module.get() is only allowed to reference goog.module() files (and not
        // goog.provide() files) when used inside of a goog.provide() file, but for consistency
        // with goog.require() it is allowed to reference either when used within a goog.module()
        // file.
        boolean mustBeGoogModule = true;

        unrecognizedRequires.add(new UnrecognizedRequire(call, legacyNamespace, mustBeGoogModule));
      }
    }
    currentScript.googModuleGettedNamespaces.add(legacyNamespace);

    String aliasName = null;
    boolean isFillingAnAlias =
        call.getParent().isAssign() && call.getParent().getFirstChild().isName();
    if (isFillingAnAlias && currentScript.isModule) {
      aliasName = call.getParent().getFirstChild().getString();

      // If the assignment isn't into a var in our scope then it's not ok.
      Var aliasVar = t.getScope().getVar(aliasName);
      if (aliasVar == null) {
        t.report(call, INVALID_GET_ALIAS);
        return;
      }

      // Even if it was to a var in our scope it should still only rewrite if the var looked like:
      //   let x = goog.forwardDeclare('a.namespace');
      Node aliasVarNodeRhs = NodeUtil.getRValueOfLValue(aliasVar.getNode());
      if (aliasVarNodeRhs == null || !isCallTo(aliasVarNodeRhs, "goog.forwardDeclare")) {
        t.report(call, INVALID_GET_ALIAS);
        return;
      }
      if (!legacyNamespace.equals(aliasVarNodeRhs.getLastChild().getString())) {
        t.report(call, INVALID_GET_ALIAS);
        return;
      }
    }

    if (aliasName != null
        && isTopLevel(t, NodeUtil.getEnclosingStatement(call), ScopeType.EXEC_CONTEXT)) {
      // Record alias -> binaryNamespace associations for later inlining.
      recordImportAlias(aliasName, legacyNamespace);
    }
  }

  private void recordTopLevelClassOrFunctionName(Node classOrFunctionNode) {
    Node nameNode = classOrFunctionNode.getFirstChild();
    if (nameNode.isName() && !Strings.isNullOrEmpty(nameNode.getString())) {
      String name = nameNode.getString();
      currentScript.topLevelNames.add(name);
    }
  }

  private void recordTopLevelVarNames(Node varNode) {
    for (Node lhs : NodeUtil.getLhsNodesOfDeclaration(varNode)) {
      String name = lhs.getString();
      currentScript.topLevelNames.add(name);
      // Rewrite `const {x, y} = ...` syntax to `const {x: x, y: y} = ...` for easier processing
      if (lhs.isStringKey()) {
        lhs.addChildToFront(IR.name(name).srcref(lhs));
      }
    }
  }

  private void maybeRecordExportDeclaration(Node n) {
    if (!currentScript.isModule || !n.getString().equals("exports") || !isAssignTarget(n)) {
      return;
    }

    currentScript.willCreateExportsObject = true;
    return;
  }

  private void recordModuleReturn() {
    popScript();
  }

  private void updateGoogLoadModule(Node call) {
    pushScript(currentScript.removeFirstChildScript());

    currentScript.rootNode = call.getLastChild().getLastChild();
    // These should be removed and replaced with the block in the function being passed as a
    // parameter, but it's not safe to do this in the middle of AST traversal.
    loadModuleStatements.add(NodeUtil.getEnclosingStatement(call));
  }

  private void updateGoogModule(Node call) {
    Preconditions.checkState(currentScript.isModule);

    // If it's a goog.module() with a legacy namespace.
    if (currentScript.declareLegacyNamespace) {
      // Rewrite "goog.module('Foo');" as "goog.provide('Foo');".
      call.getFirstChild().getLastChild().setString("provide");
    }

    // If this script file isn't going to eventually create it's own exports object, then we know
    // we'll need to do it ourselves, and so we might as well create it as early as possible to
    // avoid ordering issues with goog.define().
    if (!currentScript.willCreateExportsObject) {
      Preconditions.checkState(!currentScript.hasCreatedExportObject);
      exportTheEmptyBinaryNamespaceAt(NodeUtil.getEnclosingStatement(call), AddAt.AFTER);
    }

    if (!currentScript.declareLegacyNamespace) {
      // Otherwise it's a regular module and the goog.module() line can be removed.
      NodeUtil.getEnclosingStatement(call).detachFromParent();
    }
    Node callee = call.getFirstChild();
    Node arg = callee.getNext();
    maybeAddToSymbolTable(callee);
    maybeAddToSymbolTable(createNamespaceNode(arg));

    compiler.reportCodeChange();
  }

  private void updateGoogDeclareLegacyNamespace(Node call) {
    NodeUtil.getEnclosingStatement(call).detachFromParent();
  }

  private void updateGoogRequire(NodeTraversal t, Node call) {
    Node legacyNamespaceNode = call.getLastChild();
    Node statementNode = NodeUtil.getEnclosingStatement(call);
    String legacyNamespace = legacyNamespaceNode.getString();

    boolean targetIsGoogModule = isLegacyByGoogModuleNamespace.containsKey(legacyNamespace);
    boolean targetIsAGoogModuleGetted =
        currentScript.googModuleGettedNamespaces.contains(legacyNamespaceNode.getString());
    boolean importHasAlias = currentScript.legacyNamespacesByAlias.containsValue(legacyNamespace);
    boolean isDestructuring = statementNode.getFirstChild().isDestructuringLhs();
    // Is "(.*)goog.require("bar.Foo").Foo;" style?.
    boolean immediatePropertyAccess =
        call.getParent().isGetProp()
            && call.getGrandparent().isName()
            && NodeUtil.isNameDeclaration(call.getGrandparent().getParent());

    if (currentScript.isModule || (targetIsGoogModule && targetIsAGoogModuleGetted)) {
      if (isDestructuring) {
        // Rewrite
        //   "var {a, b} = goog.require('foo.Bar');" to
        //   "var {a, b} = module$exports$foo$Bar;" or
        //   "var {a, b} = foo.Bar;"
        Node replacementNamespaceName =
            NodeUtil.newQName(
                compiler,
                targetIsGoogModule ? toBinaryNamespace(legacyNamespace) : legacyNamespace);
        replacementNamespaceName.putProp(Node.ORIGINALNAME_PROP, legacyNamespace);
        replacementNamespaceName.putBooleanProp(Node.GOOG_MODULE_REQUIRE, true);
        replacementNamespaceName.srcrefTree(call);
        call.getParent().replaceChild(call, replacementNamespaceName);
        markConst(statementNode);
      } else if (targetIsGoogModule) {
        if (immediatePropertyAccess || !isTopLevel(t, statementNode, ScopeType.EXEC_CONTEXT)) {
          // Rewrite
          //   "var Foo = goog.require("bar.Foo").Foo;" to
          //   "var Foo = module$exports$bar$Foo.Foo;"
          // or
          //   "function() {var Foo = goog.require("bar.Foo");}" to
          //   "function() {var Foo = module$exports$bar$Foo;}"
          Node binaryNamespaceName = IR.name(toBinaryNamespace(legacyNamespace));
          binaryNamespaceName.putProp(Node.ORIGINALNAME_PROP, legacyNamespace);
          call.getParent().replaceChild(call, binaryNamespaceName);
        } else if (importHasAlias || !isLegacyByGoogModuleNamespace.get(legacyNamespace)) {
          // Delete the goog.require() because we're going to inline its alias later.
          statementNode.detachFromParent();
        }
      } else {
        if (immediatePropertyAccess) {
          // Rewrite
          //   "var B = goog.require('B').B;" to
          //   "goog.require('B'); var B = B.B;"
          // because ProcessClosurePrimitives only wants to see simple goog.require() statements.
          Node legacyNamespaceName = NodeUtil.newQName(compiler, legacyNamespace);
          legacyNamespaceName.srcrefTree(call);
          call.getParent().replaceChild(call, legacyNamespaceName);
          Node callStatement = IR.exprResult(call);
          callStatement.srcref(call);
          statementNode.getParent().addChildBefore(callStatement, statementNode);
          markConst(statementNode);
        } else {
          // Rewrite
          //   "var B = goog.require('B');" to
          //   "goog.require('B');"
          // because even though we're going to inline the B alias,
          // ProcessClosurePrimitives is going to want to see this legacy require.
          call.detachFromParent();
          statementNode.getParent().replaceChild(statementNode, IR.exprResult(call));
        }
      }
      if (targetIsGoogModule) {
        // Add goog.require() and namespace name to preprocessor table because they're removed
        // by current pass. If target is not a module then goog.require() is retained for
        // ProcessClosurePrimitives pass and symbols will be added there instead.
        Node callee = call.getFirstChild();
        Node arg = callee.getNext();
        maybeAddToSymbolTable(callee);
        maybeAddToSymbolTable(createNamespaceNode(arg));
      }
      compiler.reportCodeChange();
    }
  }

  private void updateGoogForwardDeclare(NodeTraversal t, Node call) {
    // For import rewriting purposes and when taking into account previous moduleAlias versus
    // legacyNamespace import categorization, goog.forwardDeclare is much the same as goog.require.
    updateGoogRequire(t, call);
  }

  private void updateGoogModuleGetCall(NodeTraversal t, Node call) {
    Node legacyNamespaceNode = call.getSecondChild();
    String legacyNamespace = legacyNamespaceNode.getString();

    if (currentScript.isModule) {
      // In a module each goog.module.get() will have a paired goog.forwardDeclare() and the module
      // alias importing will be handled there so that the alias is available in the entire module
      // scope.
      NodeUtil.getEnclosingStatement(call).detachFromParent();
    } else {
      // In a non-module script goog.module.get() is used inside of a goog.scope() and it's
      // imported alias need only be made available within that scope.
      // Replace "goog.module.get('pkg.Foo');" with "module$exports$pkg$Foo;".
      Node binaryNamespaceName = IR.name(toBinaryNamespace(legacyNamespace));
      binaryNamespaceName.putProp(Node.ORIGINALNAME_PROP, legacyNamespace);
      call.getParent().replaceChild(call, binaryNamespaceName);
    }
    compiler.reportCodeChange();
  }

  private void updateExportsPropertyAssignment(Node getpropNode) {
    if (!currentScript.isModule) {
      return;
    }

    Node parent = getpropNode.getParent();

    // Update "exports.foo = Foo" to "module$exports$pkg$Foo.foo = Foo";
    Node exportsNameNode = parent.getFirstFirstChild();
    safeSetString(exportsNameNode, currentScript.binaryNamespace);

    Node jsdocNode = parent.isAssign() ? parent : getpropNode;
    markConstAndCopyJsDoc(jsdocNode, jsdocNode, parent.getLastChild());

    // When seeing the first "exports.foo = ..." line put a "var module$exports$pkg$Foo = {};"
    // before it.
    if (!currentScript.hasCreatedExportObject) {
      exportTheEmptyBinaryNamespaceAt(NodeUtil.getEnclosingStatement(parent), AddAt.BEFORE);
    }
  }

  /**
   * Rewrites top level var names from
   * "var foo; console.log(foo);" to
   * "var module$contents$Foo_foo; console.log(module$contents$Foo_foo);"
   */
  private void maybeUpdateTopLevelName(NodeTraversal t, Node nameNode) {
    String name = nameNode.getString();
    if (!currentScript.isModule || !currentScript.topLevelNames.contains(name)) {
      return;
    }
    Var var = t.getScope().getVar(name);
    // If the name refers to a var that is not from the top level scope.
    if (var == null || var.getScope().getRootNode() != currentScript.rootNode) {
      // Then it shouldn't be renamed.
      return;
    }

    // If the name is an alias for an imported namespace rewrite from
    // "new Foo;" to "new module$exports$Foo;"
    boolean nameIsAnAlias = currentScript.legacyNamespacesByAlias.containsKey(name);
    if (nameIsAnAlias && var.getNode() != nameNode) {
      String legacyNamespace = currentScript.legacyNamespacesByAlias.get(name);
      boolean targetNamespaceIsAGoogModule =
          isLegacyByGoogModuleNamespace.containsKey(legacyNamespace);
      String namespaceToInline =
          targetNamespaceIsAGoogModule ? toBinaryNamespace(legacyNamespace) : legacyNamespace;
      safeSetMaybeQualifiedString(nameNode, namespaceToInline);

      // Make sure this action won't shadow a local variable.
      if (namespaceToInline.indexOf('.') != -1) {
        String firstQualifiedName = namespaceToInline.substring(0, namespaceToInline.indexOf('.'));
        Var shadowedVar = t.getScope().getVar(firstQualifiedName);
        if (shadowedVar != null && shadowedVar.isLocal()) {
          t.report(
              shadowedVar.getNode(),
              IMPORT_INLINING_SHADOWS_VAR,
              shadowedVar.getName(),
              legacyNamespace);
        }
      }
      return;
    }

    // For non-import alias names rewrite from
    // "var foo; console.log(foo);" to
    // "var module$contents$Foo_foo; console.log(module$contents$Foo_foo);"
    safeSetString(nameNode, currentScript.contentsPrefix + name);
  }

  /**
   * For exports like "exports = {prop: value}" update the declarations to enforce
   * @const ness (and typedef exports).
   */
  private void maybeUpdateExportObjectLiteral(NodeTraversal t, Node n) {
    if (!currentScript.isModule) {
      return;
    }

    Node parent = n.getParent();
    Node rhs = parent.getLastChild();

    if (rhs.isObjectLit()) {
      for (Node c = rhs.getFirstChild(); c != null; c = c.getNext()) {
        if (c.isComputedProp()) {
          t.report(c, INVALID_EXPORT_COMPUTED_PROPERTY);
        } else if (c.isStringKey()) {
          if (!c.hasChildren()) {
            c.addChildToBack(IR.name(c.getString()).useSourceInfoFrom(c));
          }
          Node value = c.getFirstChild();
          maybeUpdateExportDeclToNode(t, c, value);
        }
      }
    }
  }

  private void maybeUpdateExportDeclToNode(NodeTraversal t, Node target, Node value) {
    if (!currentScript.isModule) {
      return;
    }

    // If the RHS is a typedef, clone the declaration.
    // Hack alert: clone the typedef declaration if one exists
    // this is a simple attempt that covers the common case of the
    // exports being in the same scope as the typedef declaration.
    // Otherwise the type name might be invalid.
    if (value.isName()) {
      Scope currentScope = t.getScope();
      Var v = t.getScope().getVar(value.getString());
      if (v != null) {
        Scope varScope = v.getScope();
        if (varScope.getDepth() == currentScope.getDepth()) {
          JSDocInfo info = v.getJSDocInfo();
          if (info != null && info.hasTypedefType()) {
            JSDocInfoBuilder builder = JSDocInfoBuilder.copyFrom(info);
            target.setJSDocInfo(builder.build());
            return;
          }
        }
      }
    }

    markConstAndCopyJsDoc(target, target, value);
  }

  /**
   * In module "foo.Bar", rewrite "exports = Bar" to "var module$exports$foo$Bar = Bar".
   */
  private void maybeUpdateExportDeclaration(NodeTraversal t, Node n) {
    if (!currentScript.isModule || !n.getString().equals("exports") || !isAssignTarget(n)) {
      return;
    }

    // Rewrite "exports = ..." as "var module$exports$foo$Bar = ..."
    Node assignNode = n.getParent();
    Node exprResultNode = assignNode.getParent();
    Node rhs = assignNode.getLastChild();
    rhs.detachFromParent();
    Node binaryNamespaceName = IR.name(currentScript.binaryNamespace);
    binaryNamespaceName.putProp(Node.ORIGINALNAME_PROP, currentScript.legacyNamespace);
    Node exportsObjectCreationNode = IR.var(binaryNamespaceName, rhs);
    exportsObjectCreationNode.srcrefTree(exprResultNode);
    exportsObjectCreationNode.putBooleanProp(Node.IS_NAMESPACE, true);
    exprResultNode.getParent().replaceChild(exprResultNode, exportsObjectCreationNode);
    compiler.reportCodeChange();
    currentScript.hasCreatedExportObject = true;

    markConstAndCopyJsDoc(assignNode, exportsObjectCreationNode, rhs);
    maybeExportLegacyNamespaceAfter(exportsObjectCreationNode, assignNode);
    maybeUpdateExportObjectLiteral(t, rhs);
    return;
  }

  private void maybeUpdateExportNameRef(Node n) {
    if (!currentScript.isModule || !"exports".equals(n.getString())) {
      return;
    }
    if (n.getParent().isParamList()) {
      return;
    }

    safeSetString(n, currentScript.binaryNamespace);

    // Either this module is going to create it's own exports object at some point or else if it's
    // going to be defensively created automatically then that should have occurred at the top of
    // the file and been done by now.
    Preconditions.checkState(
        currentScript.willCreateExportsObject || currentScript.hasCreatedExportObject);
  }

  private void updateModuleReturn(Node returnNode) {
    if (!currentScript.isModule) {
      return;
    }

    Node returnStatementNode = NodeUtil.getEnclosingStatement(returnNode);

    if (!currentScript.hasCreatedExportObject) {
      exportTheEmptyBinaryNamespaceAt(returnStatementNode, AddAt.BEFORE);
    }

    returnStatementNode.detachFromParent();
    popScript();
  }

  private void updateEndScript(Node scriptNode) {
    if (!currentScript.isModule) {
      return;
    }

    updateEndModule();
  }

  private void updateEndModule() {
    Preconditions.checkState(currentScript.isModule);
    Preconditions.checkState(currentScript.hasCreatedExportObject);
  }

  /**
   * Record the provided script as the current script at top of the script stack and add it as a
   * child of the previous current script if there was one.
   *
   * <p>Keeping track of the current script facilitates aggregation of accurate script state so that
   * rewriting can run properly. Handles scripts, modules, and nested goog.loadModule() modules.
   */
  private void pushScript(ScriptDescription newCurrentScript) {
    currentScript = newCurrentScript;
    if (!scriptStack.isEmpty()) {
      ScriptDescription parentScript = scriptStack.peek();
      parentScript.addChildScript(currentScript);
    }
    scriptStack.addFirst(currentScript);
  }

  private void popScript() {
    scriptStack.removeFirst();
    currentScript = scriptStack.peekFirst();
  }

  /**
   * Add the missing "var module$exports$pkg$Foo = {};" line.
   */
  private void exportTheEmptyBinaryNamespaceAt(Node atNode, AddAt addAt) {
    Node binaryNamespaceName = IR.name(currentScript.binaryNamespace);
    binaryNamespaceName.putProp(Node.ORIGINALNAME_PROP, currentScript.legacyNamespace);
    Node binaryNamespaceExportNode = IR.var(binaryNamespaceName, IR.objectlit());
    if (addAt == AddAt.BEFORE) {
      atNode.getParent().addChildBefore(binaryNamespaceExportNode, atNode);
    } else if (addAt == AddAt.AFTER) {
      atNode.getParent().addChildAfter(binaryNamespaceExportNode, atNode);
    }
    binaryNamespaceExportNode.putBooleanProp(Node.IS_NAMESPACE, true);
    binaryNamespaceExportNode.srcrefTree(atNode);
    markConst(binaryNamespaceExportNode);
    compiler.reportCodeChange();
    currentScript.hasCreatedExportObject = true;

    maybeExportLegacyNamespaceAfter(binaryNamespaceExportNode, null);
  }

  /**
   * Maybe add a "foo.Bar = module$exports$foo$Bar;" statement to satisfy legacy namespace refs.
   */
  private void maybeExportLegacyNamespaceAfter(Node afterNode, Node jsDocSourceNode) {
    if (!currentScript.declareLegacyNamespace) {
      return;
    }

    Node binaryNamespaceName = IR.name(currentScript.binaryNamespace);
    binaryNamespaceName.putProp(Node.ORIGINALNAME_PROP, currentScript.legacyNamespace);
    Node assignmentNode =
        IR.assign(NodeUtil.newQName(compiler, currentScript.legacyNamespace), binaryNamespaceName);
    Node binaryToLegacyBridgeStatement = IR.exprResult(assignmentNode);
    binaryToLegacyBridgeStatement.srcrefTree(afterNode);
    if (jsDocSourceNode != null) {
      markConstAndCopyJsDoc(jsDocSourceNode, assignmentNode, binaryNamespaceName);
    } else {
      markConst(assignmentNode);
    }
    // Do NOT mark this statement 'IS_NAMESPACE' because that would prevent
    // ProcessClosurePrimitives from properly desugaring goog.provide().
    afterNode.getParent().addChildAfter(binaryToLegacyBridgeStatement, afterNode);
    compiler.reportCodeChange();
  }

  /**
   * Rewrite "goog.loadModule(function(exports) { asdf; });" to "{ asdf; }"
   *
   * Can't be done in updateGoogLoadModule() because it runs in the middle of AST traversal and
   * performing a block merge in the middle of AST traversal will either hide the block contents
   * from the traversal or will screw up the NodeTraversal.getScope() creation.
   */
  private void inlineGoogLoadModuleCalls() {
    for (Node loadModuleStatement : loadModuleStatements) {
      Node moduleBlockNode = loadModuleStatement.getFirstChild().getLastChild().getLastChild();
      moduleBlockNode.detachFromParent();
      loadModuleStatement.getParent().replaceChild(loadModuleStatement, moduleBlockNode);
      NodeUtil.tryMergeBlock(moduleBlockNode);
      compiler.reportCodeChange();
    }
    loadModuleStatements.clear();
  }

  static void checkAndSetStrictModeDirective(NodeTraversal t, Node n) {
    Preconditions.checkState(n.isScript(), n);

    Set<String> directives = n.getDirectives();
    if (directives != null && directives.contains("use strict")) {
      t.report(n, USELESS_USE_STRICT_DIRECTIVE);
    } else {
      if (directives == null) {
        n.setDirectives(USE_STRICT_ONLY);
      } else {
        ImmutableSet.Builder<String> builder = new ImmutableSet.Builder<String>().add("use strict");
        builder.addAll(directives);
        n.setDirectives(builder.build());
      }
    }
  }

  private void markConst(Node n) {
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(n.getJSDocInfo());
    builder.recordConstancy();
    n.setJSDocInfo(builder.build());
  }

  private void maybeSplitMultiVar(Node rhsNode) {
    Node statementNode = rhsNode.getParent().getParent();
    if (!statementNode.isVar() || !statementNode.hasMoreThanOneChild()) {
      return;
    }

    Node nameNode = rhsNode.getParent();
    nameNode.detachFromParent();
    rhsNode.detachFromParent();

    statementNode.getParent().addChildBefore(IR.var(nameNode, rhsNode), statementNode);
  }

  /**
   * Will skip const if the target is a class definition as that has a different meaning.
   */
  private void markConstAndCopyJsDoc(Node from, Node target, Node value) {
    JSDocInfo info = from.getJSDocInfo();
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(info);

    // Don't add @const on class declarations because in that context it means "not subclassable".
    if (!isCallTo(value, "goog.defineClass")
        && !(info != null && info.isConstructorOrInterface())) {
      builder.recordConstancy();
      compiler.reportCodeChange();
    }

    target.setJSDocInfo(builder.build());
  }

  private void recordImportAlias(String aliasName, String legacyNamespace) {
    currentScript.legacyNamespacesByAlias.put(aliasName, legacyNamespace);
  }

  /**
   * Examines queue'ed unrecognizedRequires to categorize and report them as either missing module,
   * missing namespace or late provide.
   */
  private void reportUnrecognizedRequires() {
    for (UnrecognizedRequire unrecognizedRequire : unrecognizedRequires) {
      String legacyNamespace = unrecognizedRequire.legacyNamespace;

      Node requireNode = unrecognizedRequire.requireNode;
      boolean targetMustBeGoogModule = unrecognizedRequire.mustBeGoogModule;
      boolean targetGoogModuleExists = isLegacyByGoogModuleNamespace.containsKey(legacyNamespace);
      boolean targetLegacyScriptExists = legacyScriptNamespaces.contains(legacyNamespace);

      if (targetMustBeGoogModule && !targetGoogModuleExists) {
        // The required thing had to be a goog.module() but no such goog.module() was in the
        // compile, so report a missing module error.
        compiler.report(
            JSError.make(requireNode, MISSING_MODULE.level, MISSING_MODULE, legacyNamespace));
        continue;
      }

      if (!targetMustBeGoogModule && !targetGoogModuleExists && !targetLegacyScriptExists) {
        // The required thing was free to be either a goog.module() or a legacy script but neither
        // flavor of file provided the required namespace, so report a vague error.
        compiler.report(
            JSError.make(
                requireNode,
                MISSING_MODULE_OR_PROVIDE.level,
                MISSING_MODULE_OR_PROVIDE,
                legacyNamespace));
        // Remove the require node so this problem isn't reported all over again in
        // ProcessClosurePrimitives.
        NodeUtil.getEnclosingStatement(requireNode).detachFromParent();
        continue;
      }

      // The required thing actually was available somewhere in the program but just wasn't
      // available as early as the require statement would have liked.
      compiler.report(
          JSError.make(requireNode, LATE_PROVIDE_ERROR.level, LATE_PROVIDE_ERROR, legacyNamespace));
    }

    // Clear the queue so that repeated reportUnrecognizedRequires() invocations in hotswap compiles
    // only report new problems.
    unrecognizedRequires.clear();
  }

  private void safeSetString(Node n, String newString) {
    String originalName = n.getString();
    n.setString(newString);
    n.putProp(Node.ORIGINALNAME_PROP, originalName);
    compiler.reportCodeChange();
  }

  private void safeSetMaybeQualifiedString(Node nameNode, String newString) {
    if (newString.contains(".")) {
      // When replacing with a dotted fully qualified name it's already better than an original
      // name.
      Node newQualifiedNameNode = NodeUtil.newQName(compiler, newString);
      newQualifiedNameNode.srcrefTree(nameNode);
      nameNode.getParent().replaceChild(nameNode, newQualifiedNameNode);
    } else {
      String originalName = nameNode.getString();
      nameNode.setString(newString);
      nameNode.putProp(Node.ORIGINALNAME_PROP, originalName);
    }
    compiler.reportCodeChange();
  }

  private static boolean isCallTo(Node n, String qualifiedName) {
    return n.isCall() && n.getFirstChild().matchesQualifiedName(qualifiedName);
  }

  private boolean isTopLevel(NodeTraversal t, Node n, ScopeType scopeType) {
    if (scopeType == ScopeType.EXEC_CONTEXT) {
      return t.getClosestHoistScope().getRootNode() == currentScript.rootNode;
    } else {
      // Must be ScopeType.BLOCK;
      return n.getParent() == currentScript.rootNode;
    }
  }

  private static String toBinaryNamespace(String legacyNamespace) {
    return MODULE_EXPORTS_PREFIX + legacyNamespace.replace('.', '$');
  }

  private static String toModuleContentsPrefix(String legacyNamespace) {
    return MODULE_CONTENTS_PREFIX + legacyNamespace.replace('.', '$') + "_";
  }

  public static boolean isModuleExport(String name) {
    return name.startsWith(MODULE_EXPORTS_PREFIX);
  }

  public static boolean isModuleContent(String name) {
    return name.startsWith(MODULE_CONTENTS_PREFIX);
  }

  /**
   * @return Whether the getprop is used as an assignment target, and that
   *     target represents a module export.
   * Note: that "export.name = value" is an export, while "export.name.foo = value"
   *     is not (it is an assignment to a property of an exported value).
   */
  private static boolean isExportPropertyAssignment(Node n) {
    Node target = n.getFirstChild();
    return (isAssignTarget(n) || isTypedefTarget(n))
        && target.isName()
        && target.getString().equals("exports");
  }

  private static boolean isAssignTarget(Node n) {
    Node parent = n.getParent();
    return parent.isAssign() && parent.getFirstChild() == n;
  }

  private static boolean isTypedefTarget(Node n) {
    Node parent = n.getParent();
    return parent.isExprResult() && parent.getFirstChild() == n;
  }

  /**
   * Add the given qualified name node to the symbol table.
   */
  private void maybeAddToSymbolTable(Node n) {
    if (preprocessorSymbolTable != null) {
      preprocessorSymbolTable.addReference(n);
    }
  }

  /**
   * @param n String node containing goog.module namespace.
   * @return A NAMESPACE node with the same name and source info as provided node.
   */
  private static Node createNamespaceNode(Node n) {
    Node node = Node.newString(n.getString()).useSourceInfoFrom(n);
    node.putBooleanProp(Node.IS_MODULE_NAME, true);
    return node;
  }
}
