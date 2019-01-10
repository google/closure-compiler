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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_FORWARD_DECLARE_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_GET_CALL_SCOPE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_GET_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_REQUIRE_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_REQUIRE_TYPE_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

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
 * @author stalcup@google.com (John Stalcup)
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

  static final DiagnosticType INVALID_PROVIDE_CALL =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_PROVIDE_CALL",
          "goog.provide can not be called in goog.module.");

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

  static final DiagnosticType LATE_PROVIDE_ERROR =
      DiagnosticType.error(
          "JSC_LATE_PROVIDE_ERROR",
          "Required namespace \"{0}\" not provided yet.");

  static final DiagnosticType IMPORT_INLINING_SHADOWS_VAR =
      DiagnosticType.error(
          "JSC_IMPORT_INLINING_SHADOWS_VAR",
          "Inlining of reference to import \"{1}\" shadows var \"{0}\".");

  static final DiagnosticType ILLEGAL_DESTRUCTURING_DEFAULT_EXPORT =
      DiagnosticType.error(
          "JSC_ILLEGAL_DESTRUCTURING_DEFAULT_EXPORT",
          "Destructuring import only allowed for importing module with named exports.\n"
              + "See https://github.com/google/closure-compiler/wiki/goog.module-style");

  static final DiagnosticType ILLEGAL_DESTRUCTURING_NOT_EXPORTED =
      DiagnosticType.error(
          "JSC_ILLEGAL_DESTRUCTURING_NOT_EXPORTED",
          "Destructuring import reference to name \"{0}\" was not exported in module {1}");

  private static final ImmutableSet<String> USE_STRICT_ONLY = ImmutableSet.of("use strict");

  private static final String MODULE_EXPORTS_PREFIX = "module$exports$";

  private static final String MODULE_CONTENTS_PREFIX = "module$contents$";

  // Prebuilt Nodes to speed up Node.matchesQualifiedName() calls
  private static final Node GOOG_FORWARDDECLARE =
      IR.getprop(IR.name("goog"), IR.string("forwardDeclare"));
  private static final Node GOOG_LOADMODULE = IR.getprop(IR.name("goog"), IR.string("loadModule"));
  private static final Node GOOG_MODULE = IR.getprop(IR.name("goog"), IR.string("module"));
  private static final Node GOOG_MODULE_DECLARELEGACYNAMESPACE =
      IR.getprop(GOOG_MODULE, IR.string("declareLegacyNamespace"));
  private static final Node GOOG_MODULE_GET = IR.getprop(GOOG_MODULE.cloneTree(), IR.string("get"));
  private static final Node GOOG_PROVIDE = IR.getprop(IR.name("goog"), IR.string("provide"));
  private static final Node GOOG_REQUIRE = IR.getprop(IR.name("goog"), IR.string("require"));
  private static final Node GOOG_REQUIRETYPE =
      IR.getprop(IR.name("goog"), IR.string("requireType"));

  private final AbstractCompiler compiler;
  private final PreprocessorSymbolTable preprocessorSymbolTable;
  private final boolean preserveSugar;

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
    // A goog.require() call, or a goog.module.get() call.
    final Node requireNode;
    final String legacyNamespace;
    final boolean mustBeOrdered;

    UnrecognizedRequire(Node requireNode, String legacyNamespace, boolean mustBeOrdered) {
      this.requireNode = requireNode;
      this.legacyNamespace = legacyNamespace;
      this.mustBeOrdered = mustBeOrdered;
    }
  }

  private static final class ExportDefinition {
    // Null if the export is a default export (exports = expr)
    @Nullable String exportName;
    // Null if the export is of a @typedef
    @Nullable Node rhs;
    // Null if the export is of anything other than a name
    @Nullable Var nameDecl;

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("exportName", exportName)
          .add("rhs", rhs)
          .add("nameDecl", nameDecl)
          .omitNullValues()
          .toString();
    }

    private static final ImmutableSet<Token> INLINABLE_NAME_PARENTS =
        ImmutableSet.of(Token.VAR, Token.CONST, Token.LET, Token.FUNCTION, Token.CLASS);

    static ExportDefinition newDefaultExport(NodeTraversal t, Node rhs) {
      return newNamedExport(t, null, rhs);
    }

    static ExportDefinition newNamedExport(NodeTraversal t, String name, Node rhs) {
      ExportDefinition newExport = new ExportDefinition();
      newExport.exportName = name;
      newExport.rhs = rhs;
      if (rhs != null && (rhs.isName() || rhs.isStringKey())) {
        newExport.nameDecl = t.getScope().getVar(rhs.getString());
      }
      return newExport;
    }

    String getExportPostfix() {
      if (exportName == null) {
        return "";
      }
      return "." + exportName;
    }

    boolean hasInlinableName(Set<Var> exportedNames) {
      if (nameDecl == null
          || exportedNames.contains(nameDecl)
          || !INLINABLE_NAME_PARENTS.contains(nameDecl.getParentNode().getToken())) {
        return false;
      }
      Node initialValue = nameDecl.getInitialValue();
      if (initialValue == null || !initialValue.isCall()) {
        return true;
      }
      Node method = initialValue.getFirstChild();
      if (!method.isGetProp()) {
        return true;
      }
      Node maybeGoog = method.getFirstChild();
      if (!maybeGoog.isName() || !maybeGoog.getString().equals("goog")) {
        return true;
      }
      String name = maybeGoog.getNext().getString();
      return !name.equals("require") && !name.equals("forwardDeclare") && !name.equals("getMsg");
    }

    String getLocalName() {
      return nameDecl.getName();
    }
  }

  private static final class ScriptDescription {
    boolean isModule;
    boolean declareLegacyNamespace;
    String legacyNamespace; // "a.b.c"
    String contentsPrefix; // "module$contents$a$b$c_
    final Set<String> topLevelNames = new HashSet<>(); // For prefixed content renaming.
    final Deque<ScriptDescription> childScripts = new ArrayDeque<>();
    final Map<String, String> namesToInlineByAlias = new HashMap<>(); // For alias inlining.

    /**
     * Transient state.
     */
    boolean willCreateExportsObject;
    boolean hasCreatedExportObject;
    Node defaultExportRhs;
    String defaultExportLocalName;
    Set<String> namedExports = new HashSet<>();
    Map<Var, ExportDefinition> exportsToInline = new HashMap<>();

    // The root of the module. The MODULE_BODY node that contains the module contents.
    // For recognizing top level names.
    Node rootNode;

    public void addChildScript(ScriptDescription childScript) {
      childScripts.addLast(childScript);
    }

    public ScriptDescription removeFirstChildScript() {
      return childScripts.removeFirst();
    }

    // "module$exports$a$b$c" for non-legacy modules
    @Nullable String getBinaryNamespace() {
      if (!this.isModule || this.declareLegacyNamespace) {
        return null;
      }
      return getBinaryModuleNamespace(legacyNamespace);
    }

    @Nullable
    String getExportedNamespace() {
      if (this.declareLegacyNamespace) {
        return this.legacyNamespace;
      }
      return this.getBinaryNamespace();
    }
  }

  static String getBinaryModuleNamespace(String legacyNamespace) {
    return MODULE_EXPORTS_PREFIX + legacyNamespace.replace('.', '$');
  }

  private class ScriptPreprocessor extends NodeTraversal.AbstractPreOrderCallback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case ROOT:
        case MODULE_BODY:
          return true;

        case SCRIPT:
          if (NodeUtil.isGoogModuleFile(n)) {
            checkAndSetStrictModeDirective(t, n);
          }
          return true;

        case NAME:
          preprocessExportDeclaration(n);
          return true;

        default:
          // Don't traverse into non-module scripts.
          return !parent.isScript();
      }
    }
  }

  private class ScriptRecorder implements Callback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case MODULE_BODY:
          recordModuleBody(n);
          break;
        case CALL:
          Node method = n.getFirstChild();
          if (!method.isGetProp()) {
            break;
          }
          if (method.matchesQualifiedName(GOOG_MODULE)) {
            recordGoogModule(t, n);
          } else if (method.matchesQualifiedName(GOOG_MODULE_DECLARELEGACYNAMESPACE)) {
            recordGoogDeclareLegacyNamespace();
          } else if (method.matchesQualifiedName(GOOG_PROVIDE)) {
            recordGoogProvide(t, n);
          } else if (method.matchesQualifiedName(GOOG_REQUIRE)) {
            recordGoogRequire(t, n, /* mustBeOrdered= */ true);
          } else if (method.matchesQualifiedName(GOOG_REQUIRETYPE)) {
            recordGoogRequireType(t, n);
          } else if (method.matchesQualifiedName(GOOG_FORWARDDECLARE) && !parent.isExprResult()) {
            recordGoogForwardDeclare(t, n);
          } else if (method.matchesQualifiedName(GOOG_MODULE_GET)) {
            recordGoogModuleGet(t, n);
          }
          break;

        case CLASS:
        case FUNCTION:
          if (isTopLevel(t, n, ScopeType.BLOCK)) {
            recordTopLevelClassOrFunctionName(n);
          }
          break;

        case CONST:
        case LET:
        case VAR:
          if (isTopLevel(t, n, n.isVar() ? ScopeType.EXEC_CONTEXT : ScopeType.BLOCK)) {
            recordTopLevelVarNames(n);
          }
          break;

        case GETPROP:
          if (isExportPropertyAssignment(n)) {
            recordExportsPropertyAssignment(t, n);
          }
          break;

        case NAME:
          maybeRecordExportDeclaration(t, n);
          break;

        default:
          break;
      }

      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isModuleBody()) {
        popScript();
      }
    }
  }

  private class ScriptUpdater implements ScopedCallback {
    @Override
    public void enterScope(NodeTraversal t) {
      // Capture the scope before doing any rewriting to the scope.
      t.getScope();
    }

    @Override
    public void exitScope(NodeTraversal t) {
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case MODULE_BODY:
          if (parent.getBooleanProp(Node.GOOG_MODULE)) {
            updateModuleBodyEarly(n);
          } else {
            return false;
          }
          break;

        case CALL:
          Node method = n.getFirstChild();
          if (!method.isGetProp()) {
            break;
          }
          if (method.matchesQualifiedName(GOOG_MODULE)) {
            updateGoogModule(n);
          } else if (method.matchesQualifiedName(GOOG_MODULE_DECLARELEGACYNAMESPACE)) {
            updateGoogDeclareLegacyNamespace(n);
          } else if (method.matchesQualifiedName(GOOG_REQUIRE)
              || method.matchesQualifiedName(GOOG_REQUIRETYPE)) {
            updateGoogRequire(t, n);
          } else if (method.matchesQualifiedName(GOOG_FORWARDDECLARE) && !parent.isExprResult()) {
            updateGoogForwardDeclare(t, n);
          } else if (method.matchesQualifiedName(GOOG_MODULE_GET)) {
            updateGoogModuleGetCall(n);
          }
          break;

        case GETPROP:
          if (isExportPropertyAssignment(n)) {
            updateExportsPropertyAssignment(n);
          }
          break;

        default:
          break;
      }

      if (n.getJSDocInfo() != null) {
        rewriteJsdoc(n.getJSDocInfo());
      }

      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case MODULE_BODY:
          updateModuleBody(n);
          break;

        case NAME:
          maybeUpdateTopLevelName(t, n);
          maybeUpdateExportDeclaration(t, n);
          maybeUpdateExportNameRef(n);
          break;
        default:
          break;
      }
    }
  }

  /**
   * Rewrites JsDoc type references to match AST changes resulting from imported alias inlining,
   * module content renaming of top level constructor functions and classes, and module renaming
   * from fully qualified legacy namespace to its binary name.
   */
  private void rewriteJsdoc(JSDocInfo info) {
    for (Node typeNode : info.getTypeNodes()) {
      NodeUtil.visitPreOrder(typeNode, replaceJsDocRefs);
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
                currentScript.namesToInlineByAlias.containsKey(prefixTypeName);
            if (nameIsAnAlias) {
              if (preprocessorSymbolTable != null) {
                // Jsdoc type node is a single STRING node that spans the whole type. For example
                // STRING node "bar.Foo". When rewriting modules potentially replace only "module"
                // part of the type: "bar.Foo" => "module$exports$bar$Foo". So we need to remember
                // that "bar" as alias. To do that we clone type node and make "bar" node from it.
                Node moduleOnlyNode = typeRefNode.cloneNode();
                safeSetString(moduleOnlyNode, prefixTypeName);
                moduleOnlyNode.setLength(prefixTypeName.length());
                maybeAddAliasToSymbolTable(moduleOnlyNode, currentScript.legacyNamespace);
              }

              String aliasedNamespace = currentScript.namesToInlineByAlias.get(prefixTypeName);
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


            String binaryNamespaceIfModule = rewriteState.getBinaryNamespace(prefixTypeName);
            if (legacyScriptNamespacesAndPrefixes.contains(prefixTypeName)
                && binaryNamespaceIfModule == null) {
              // This thing is definitely coming from a legacy script and so the fully qualified
              // type name will always resolve as is.
              return;
            }

            // If the typeName is a reference to a fully qualified legacy namespace like
            // "foo.bar.Baz" of something that is actually a module then rewrite the JsDoc reference
            // to "module$exports$Bar".
            if (binaryNamespaceIfModule != null) {
              safeSetString(typeRefNode, binaryNamespaceIfModule + suffix);
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

  // Per script state needed for rewriting.
  private final Deque<ScriptDescription> scriptStack = new ArrayDeque<>();
  private ScriptDescription currentScript = null;

  // Global state tracking an association between the dotted names of goog.module()s and whether
  // the goog.module declares itself as a legacy namespace.
  // Allows for detecting duplicate goog.module()s and for rewriting fully qualified
  // JsDoc type references to goog.module() types in legacy scripts.
  static class GlobalRewriteState {
    private final Map<String, ScriptDescription> scriptDescriptionsByGoogModuleNamespace =
        new HashMap<>();
    private final Multimap<Node, String> legacyNamespacesByScriptNode = HashMultimap.create();
    private final Set<String> legacyScriptNamespaces = new HashSet<>();

    boolean containsModule(String legacyNamespace) {
      return scriptDescriptionsByGoogModuleNamespace.containsKey(legacyNamespace);
    }

    boolean isLegacyModule(String legacyNamespace) {
      checkArgument(containsModule(legacyNamespace));
      return scriptDescriptionsByGoogModuleNamespace.get(legacyNamespace).declareLegacyNamespace;
    }

    @Nullable String getBinaryNamespace(String legacyNamespace) {
      ScriptDescription script = scriptDescriptionsByGoogModuleNamespace.get(legacyNamespace);
      return script == null ? null : script.getBinaryNamespace();
    }

    @Nullable
    private String getExportedNamespaceOrScript(String legacyNamespace) {
      if (legacyScriptNamespaces.contains(legacyNamespace)) {
        return legacyNamespace;
      }
      ScriptDescription script = scriptDescriptionsByGoogModuleNamespace.get(legacyNamespace);
      return script == null ? null : script.getExportedNamespace();
    }

    void removeRoot(Node toRemove) {
      if (legacyNamespacesByScriptNode.containsKey(toRemove)) {
        scriptDescriptionsByGoogModuleNamespace
            .keySet()
            .removeAll(legacyNamespacesByScriptNode.removeAll(toRemove));
      }
    }
  }

  private final GlobalRewriteState rewriteState;
  private final Set<String> legacyScriptNamespacesAndPrefixes = new HashSet<>();
  private final List<UnrecognizedRequire> unrecognizedRequires = new ArrayList<>();

  ClosureRewriteModule(
      AbstractCompiler compiler,
      PreprocessorSymbolTable preprocessorSymbolTable,
      GlobalRewriteState moduleRewriteState) {
    this.compiler = compiler;
    this.preprocessorSymbolTable = preprocessorSymbolTable;
    this.rewriteState = moduleRewriteState != null ? moduleRewriteState : new GlobalRewriteState();
    this.preserveSugar = compiler.getOptions().shouldPreserveGoogModule();
  }

  private class UnwrapGoogLoadModule extends NodeTraversal.AbstractPreOrderCallback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case ROOT:
        case SCRIPT:
          return true;
        case EXPR_RESULT:
          Node call = n.getFirstChild();
          if (isCallTo(call, GOOG_LOADMODULE) && call.getLastChild().isFunction()) {
            parent.putBooleanProp(Node.GOOG_MODULE, true);
            Node functionNode = call.getLastChild();
            compiler.reportFunctionDeleted(functionNode);
            Node moduleBody = functionNode.getLastChild().detach();
            moduleBody.setToken(Token.MODULE_BODY);
            n.replaceWith(moduleBody);
            Node returnNode = moduleBody.getLastChild();
            checkState(returnNode.isReturn(), returnNode);
            returnNode.detach();
          }
          return false;
        default:
          return false;
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    Deque<ScriptDescription> scriptDescriptions = new ArrayDeque<>();
    processAllFiles(scriptDescriptions, Iterables.concat(externs.children(), root.children()));
  }

  private void processAllFiles(
      Deque<ScriptDescription> scriptDescriptions, Iterable<Node> scriptNodes) {
    // Record all the scripts first so that the googModuleNamespaces global state can be complete
    // before doing any updating also queue up scriptDescriptions for later use in ScriptUpdater
    // runs.
    for (Node c : scriptNodes) {
      checkState(c.isScript(), c);
      NodeTraversal.traverse(compiler, c, new UnwrapGoogLoadModule());
      pushScript(new ScriptDescription());
      currentScript.rootNode = c;
      scriptDescriptions.addLast(currentScript);
      NodeTraversal.traverse(compiler, c, new ScriptPreprocessor());
      NodeTraversal.traverse(compiler, c, new ScriptRecorder());
      popScript();
    }

    reportUnrecognizedRequires();
    if (compiler.hasHaltingErrors()) {
      return;
    }

    // Update scripts using the now complete googModuleNamespaces global state and unspool the
    // scriptDescriptions that were queued up by all the recording.
    for (Node c : scriptNodes) {
      pushScript(scriptDescriptions.removeFirst());
      if (!c.isFromExterns() || NodeUtil.isFromTypeSummary(c)) {
        NodeTraversal.traverse(compiler, c, new ScriptUpdater());
      }
      popScript();
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    checkState(scriptRoot.isScript(), scriptRoot);
    NodeTraversal.traverse(compiler, scriptRoot, new UnwrapGoogLoadModule());

    rewriteState.removeRoot(originalRoot);

    pushScript(new ScriptDescription());
    currentScript.rootNode = scriptRoot;
    NodeTraversal.traverse(compiler, scriptRoot, new ScriptPreprocessor());
    NodeTraversal.traverse(compiler, scriptRoot, new ScriptRecorder());

    if (compiler.hasHaltingErrors()) {
      return;
    }

    NodeTraversal.traverse(compiler, scriptRoot, new ScriptUpdater());
    popScript();

    reportUnrecognizedRequires();
  }

  /**
   * Rewrites object literal exports to the standard named exports style. i.e.
   *    exports = {Foo, Bar}
   * to
   *    exports.Foo = Foo;
   *    exports.Bar = Bar;
   * This makes the module exports into a more standard format for later passes.
   */
  private void preprocessExportDeclaration(Node n) {
    if (!n.getString().equals("exports")
        || !isAssignTarget(n)
        || !n.getGrandparent().isExprResult()) {
      return;
    }

    checkState(currentScript.defaultExportRhs == null);
    Node exportRhs = n.getNext();
    if (isNamedExportsLiteral(exportRhs)) {
      Node insertionPoint = n.getGrandparent();
      for (Node key = exportRhs.getFirstChild(); key != null; key = key.getNext()) {
        String exportName = key.getString();
        JSDocInfo jsdoc = key.getJSDocInfo();
        Node rhs = key.hasChildren() ? key.removeFirstChild() : IR.name(exportName).srcref(key);
        Node lhs = IR.getprop(IR.name("exports"), IR.string(exportName)).srcrefTree(key);
        Node newExport =
            IR.exprResult(IR.assign(lhs, rhs).srcref(key).setJSDocInfo(jsdoc)).srcref(key);
        insertionPoint.getParent().addChildAfter(newExport, insertionPoint);
        insertionPoint = newExport;
      }
      n.getGrandparent().detach();
    }
  }

  private static boolean isNamedExportsLiteral(Node objLit) {
    if (!objLit.isObjectLit() || !objLit.hasChildren()) {
      return false;
    }
    for (Node key = objLit.getFirstChild(); key != null; key = key.getNext()) {
      if (!key.isStringKey() || key.isQuotedString()) {
        return false;
      }
      if (key.hasChildren() && !key.getFirstChild().isName()) {
        return false;
      }
    }
    return true;
  }

  private void recordModuleBody(Node moduleRoot) {
    pushScript(new ScriptDescription());

    currentScript.rootNode = moduleRoot;
    currentScript.isModule = true;
  }

  private void recordGoogModule(NodeTraversal t, Node call) {
    Node legacyNamespaceNode = call.getLastChild();
    if (!legacyNamespaceNode.isString()) {
      t.report(legacyNamespaceNode, INVALID_MODULE_NAMESPACE);
      return;
    }
    String legacyNamespace = legacyNamespaceNode.getString();

    currentScript.legacyNamespace = legacyNamespace;
    currentScript.contentsPrefix = toModuleContentsPrefix(legacyNamespace);

    // If some other script is advertising itself as a goog.module() with this same namespace.
    if (rewriteState.containsModule(legacyNamespace)) {
      t.report(call, DUPLICATE_MODULE, legacyNamespace);
    }
    if (rewriteState.legacyScriptNamespaces.contains(legacyNamespace)) {
      t.report(call, DUPLICATE_NAMESPACE, legacyNamespace);
    }

    Node scriptNode = NodeUtil.getEnclosingScript(currentScript.rootNode);
    rewriteState.scriptDescriptionsByGoogModuleNamespace.put(legacyNamespace, currentScript);
    rewriteState.legacyNamespacesByScriptNode.put(scriptNode, legacyNamespace);
  }

  private void recordGoogDeclareLegacyNamespace() {
    currentScript.declareLegacyNamespace = true;
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
    if (rewriteState.containsModule(legacyNamespace)) {
      t.report(call, DUPLICATE_NAMESPACE, legacyNamespace);
    }

    Node scriptNode = NodeUtil.getEnclosingScript(call);
    // Log legacy namespaces and prefixes.
    rewriteState.legacyScriptNamespaces.add(legacyNamespace);
    rewriteState.legacyNamespacesByScriptNode.put(scriptNode, legacyNamespace);
    LinkedList<String> parts = Lists.newLinkedList(Splitter.on('.').split(legacyNamespace));
    while (!parts.isEmpty()) {
      legacyScriptNamespacesAndPrefixes.add(Joiner.on('.').join(parts));
      parts.removeLast();
    }
  }

  private void recordGoogRequire(NodeTraversal t, Node call, boolean mustBeOrdered) {
    maybeSplitMultiVar(call);

    Node legacyNamespaceNode = call.getLastChild();
    if (!legacyNamespaceNode.isString()) {
      t.report(legacyNamespaceNode, INVALID_REQUIRE_NAMESPACE);
      return;
    }
    String legacyNamespace = legacyNamespaceNode.getString();

    // Maybe report an error if there is an attempt to import something that is expected to be a
    // goog.module() but no such goog.module() has been defined.
    boolean targetIsAModule = rewriteState.containsModule(legacyNamespace);
    boolean targetIsALegacyScript = rewriteState.legacyScriptNamespaces.contains(legacyNamespace);
    if (currentScript.isModule && !targetIsAModule && !targetIsALegacyScript) {
      unrecognizedRequires.add(new UnrecognizedRequire(call, legacyNamespace, mustBeOrdered));
    }
  }

  private void recordGoogRequireType(NodeTraversal t, Node call) {
    Node legacyNamespaceNode = call.getLastChild();
    if (!legacyNamespaceNode.isString()) {
      t.report(legacyNamespaceNode, INVALID_REQUIRE_TYPE_NAMESPACE);
      return;
    }

    // A goog.requireType call is not required to appear after the corresponding namespace
    // definition.
    boolean mustBeOrdered = false;

    // For purposes of import collection, goog.requireType is the same as goog.require.
    recordGoogRequire(t, call, mustBeOrdered);
  }

  private void recordGoogForwardDeclare(NodeTraversal t, Node call) {
    Node namespaceNode = call.getLastChild();
    if (!call.hasTwoChildren() || !namespaceNode.isString()) {
      t.report(namespaceNode, INVALID_FORWARD_DECLARE_NAMESPACE);
      return;
    }

    // modules already require that goog.forwardDeclare() and goog.module.get() occur in matched
    // pairs. If a "missing module" error were to occur here it would also occur in the matching
    // goog.module.get(). To avoid reporting the error twice suppress it here.
    boolean mustBeOrdered = false;

    // For purposes of import collection, goog.forwardDeclare is the same as goog.require.
    recordGoogRequire(t, call, mustBeOrdered);
  }

  private void recordGoogModuleGet(NodeTraversal t, Node call) {
    Node legacyNamespaceNode = call.getLastChild();
    if (!call.hasTwoChildren() || !legacyNamespaceNode.isString()) {
      t.report(legacyNamespaceNode, INVALID_GET_NAMESPACE);
      return;
    }
    if (!currentScript.isModule
        && t.inGlobalScope()
        && compiler.getOptions().moduleResolutionMode != ResolutionMode.WEBPACK) {
      t.report(legacyNamespaceNode, INVALID_GET_CALL_SCOPE);
      return;
    }
    String legacyNamespace = legacyNamespaceNode.getString();

    if (!rewriteState.containsModule(legacyNamespace)) {
      unrecognizedRequires.add(
          new UnrecognizedRequire(
              call,
              legacyNamespace,
              false /* mustBeOrdered */));
    }

    String aliasName = null;
    Node maybeAssign = call.getParent();
    boolean isFillingAnAlias = maybeAssign.isAssign() && maybeAssign.getFirstChild().isName()
        && maybeAssign.getParent().isExprResult();
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
      if (aliasVarNodeRhs == null || !isCallTo(aliasVarNodeRhs, GOOG_FORWARDDECLARE)) {
        t.report(call, INVALID_GET_ALIAS);
        return;
      }
      if (!legacyNamespace.equals(aliasVarNodeRhs.getLastChild().getString())) {
        t.report(call, INVALID_GET_ALIAS);
        return;
      }

      // Each goog.module.get() calling filling an alias will have the alias importing logic
      // handled at the goog.forwardDeclare call, and the corresponding goog.module.get can simply
      // be removed.
      compiler.reportChangeToEnclosingScope(maybeAssign);
      maybeAssign.getParent().detach();
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
    for (Node lhs : NodeUtil.findLhsNodesInNode(varNode)) {
      String name = lhs.getString();
      currentScript.topLevelNames.add(name);
    }
  }

  private void maybeRecordExportDeclaration(NodeTraversal t, Node n) {
    if (!currentScript.isModule
        || !n.getString().equals("exports")
        || !isAssignTarget(n)) {
      return;
    }

    checkState(currentScript.defaultExportRhs == null, currentScript.defaultExportRhs);
    Node exportRhs = n.getNext();
    if (isNamedExportsLiteral(exportRhs)) {
      boolean areAllExportsInlinable = true;
      List<ExportDefinition> inlinableExports = new ArrayList<>();
      for (Node key = exportRhs.getFirstChild(); key != null; key = key.getNext()) {
        String exportName = key.getString();
        Node rhs = key.hasChildren() ? key.getFirstChild() : key;
        ExportDefinition namedExport = ExportDefinition.newNamedExport(t, exportName, rhs);
        currentScript.namedExports.add(exportName);
        if (currentScript.declareLegacyNamespace
            || !namedExport.hasInlinableName(currentScript.exportsToInline.keySet())) {
          areAllExportsInlinable = false;
        } else {
          inlinableExports.add(namedExport);
        }
      }
      if (areAllExportsInlinable) {
        for (ExportDefinition export : inlinableExports) {
          recordExportToInline(export);
        }
        NodeUtil.removeChild(n.getGrandparent(), n.getParent());
      } else {
        currentScript.willCreateExportsObject = true;
      }
      return;
    }

    // Exports object should have already been converted in ScriptPreprocess step.
    checkState(!isNamedExportsLiteral(exportRhs),
        "Exports object should have been converted already");

    currentScript.defaultExportRhs = exportRhs;
    currentScript.willCreateExportsObject = true;
    ExportDefinition defaultExport = ExportDefinition.newDefaultExport(t, exportRhs);
    if (!currentScript.declareLegacyNamespace
        && defaultExport.hasInlinableName(currentScript.exportsToInline.keySet())) {
      String localName = defaultExport.getLocalName();
      currentScript.defaultExportLocalName = localName;
      recordExportToInline(defaultExport);
    }

    return;
  }

  private void updateModuleBodyEarly(Node moduleScopeRoot) {
    pushScript(currentScript.removeFirstChildScript());
    currentScript.rootNode = moduleScopeRoot;
  }

  private void updateGoogModule(Node call) {
    checkState(currentScript.isModule, currentScript);

    // If it's a goog.module() with a legacy namespace.
    if (currentScript.declareLegacyNamespace) {
      // Rewrite "goog.module('Foo');" as "goog.provide('Foo');".
      call.getFirstChild().getLastChild().setString("provide");
      compiler.reportChangeToEnclosingScope(call);
    }

    // If this script file isn't going to eventually create it's own exports object, then we know
    // we'll need to do it ourselves, and so we might as well create it as early as possible to
    // avoid ordering issues with goog.define().
    if (!currentScript.willCreateExportsObject) {
      checkState(!currentScript.hasCreatedExportObject, currentScript);
      exportTheEmptyBinaryNamespaceAt(NodeUtil.getEnclosingStatement(call), AddAt.AFTER);
    }

    if (!currentScript.declareLegacyNamespace && !preserveSugar) {
      // Otherwise it's a regular module and the goog.module() line can be removed.
      compiler.reportChangeToEnclosingScope(call);
      NodeUtil.getEnclosingStatement(call).detach();
    }
    Node callee = call.getFirstChild();
    Node arg = callee.getNext();
    maybeAddToSymbolTable(callee);
    maybeAddToSymbolTable(createNamespaceNode(arg));
  }

  private void updateGoogDeclareLegacyNamespace(Node call) {
    NodeUtil.getEnclosingStatement(call).detach();
  }

  private void updateGoogRequire(NodeTraversal t, Node call) {
    Node legacyNamespaceNode = call.getLastChild();
    Node statementNode = NodeUtil.getEnclosingStatement(call);
    String legacyNamespace = legacyNamespaceNode.getString();

    boolean targetIsNonLegacyGoogModule =
        rewriteState.containsModule(legacyNamespace)
            && !rewriteState.isLegacyModule(legacyNamespace);
    boolean importHasAlias = NodeUtil.isNameDeclaration(statementNode);
    boolean isDestructuring = statementNode.getFirstChild().isDestructuringLhs();

    // If the current script is a module or the require statement has a return value that is stored
    // in an alias then the require is goog.module() style.
    boolean currentScriptIsAModule = currentScript.isModule;
    // "var Foo = goog.require("bar.Foo");" or "const {Foo} = goog.require('bar');" style.
    boolean requireDirectlyStoredInAlias = NodeUtil.isNameDeclaration(call.getGrandparent());
    if (currentScriptIsAModule
        && requireDirectlyStoredInAlias
        && isTopLevel(t, statementNode, ScopeType.EXEC_CONTEXT)) {
      // Record alias -> exportedNamespace associations for later inlining.
      Node lhs = call.getParent();
      String exportedNamespace = rewriteState.getExportedNamespaceOrScript(legacyNamespace);
      if (exportedNamespace == null) {
        // There's nothing to inline. The missing provide/module will be reported elsewhere.
      } else if (lhs.isName()) {
        // `var Foo` case
        String aliasName = statementNode.getFirstChild().getString();
        recordNameToInline(aliasName, exportedNamespace);
        maybeAddAliasToSymbolTable(statementNode.getFirstChild(), currentScript.legacyNamespace);
      } else if (lhs.isDestructuringLhs() && lhs.getFirstChild().isObjectPattern()) {
        // `const {Foo}` case
        maybeWarnForInvalidDestructuring(t, lhs.getParent(), legacyNamespace);
        for (Node importSpec : lhs.getFirstChild().children()) {
          checkState(importSpec.hasChildren(), importSpec);
          String importedProperty = importSpec.getString();
          Node aliasNode = importSpec.getFirstChild();
          String aliasName = aliasNode.getString();
          String fullName = exportedNamespace + "." + importedProperty;
          recordNameToInline(aliasName, fullName);

          // Record alias before we rename node.
          maybeAddAliasToSymbolTable(aliasNode, currentScript.legacyNamespace);
          // Need to rename node otherwise it will stay global and messes up index if there are
          // other files that use the same destructuring alias.
          safeSetString(aliasNode, currentScript.contentsPrefix + aliasName);
        }
      } else {
        throw new RuntimeException("Illegal goog.module import: " + lhs);
      }
    }

    if (currentScript.isModule || targetIsNonLegacyGoogModule) {
      if (isDestructuring) {
        if (!preserveSugar) {
          // Delete the goog.require() because we're going to inline its alias later.
          compiler.reportChangeToEnclosingScope(statementNode);
          statementNode.detach();
        }
      } else if (targetIsNonLegacyGoogModule) {
        if (!isTopLevel(t, statementNode, ScopeType.EXEC_CONTEXT)) {
          // Rewrite
          //   "function() {var Foo = goog.require("bar.Foo");}" to
          //   "function() {var Foo = module$exports$bar$Foo;}"
          Node binaryNamespaceName = IR.name(rewriteState.getBinaryNamespace(legacyNamespace));
          binaryNamespaceName.setOriginalName(legacyNamespace);
          call.replaceWith(binaryNamespaceName);
          compiler.reportChangeToEnclosingScope(binaryNamespaceName);
        } else if (importHasAlias || !rewriteState.isLegacyModule(legacyNamespace)) {
          if (!preserveSugar) {
            // Delete the goog.require() because we're going to inline its alias later.
            compiler.reportChangeToEnclosingScope(statementNode);
            statementNode.detach();
          }
        }
      } else {
        // TODO(bangert): make this compatible with preserveSugar. const B = goog.require('b') runs
        // into problems because the type checker cannot handle const.
        // Rewrite
        //   "var B = goog.require('B');" to
        //   "goog.require('B');"
        // because even though we're going to inline the B alias,
        // ProcessClosurePrimitives is going to want to see this legacy require.
        call.detach();
        statementNode.replaceWith(IR.exprResult(call));
        compiler.reportChangeToEnclosingScope(call);
      }
      if (targetIsNonLegacyGoogModule && !preserveSugar) {
        // Add goog.require() and namespace name to preprocessor table because they're removed
        // by current pass. If target is not a module then goog.require() is retained for
        // ProcessClosurePrimitives pass and symbols will be added there instead.
        Node callee = call.getFirstChild();
        Node arg = callee.getNext();
        maybeAddToSymbolTable(callee);
        maybeAddToSymbolTable(createNamespaceNode(arg));
      }
    }
  }

  // These restrictions are in place to make it easier to migrate goog.modules to ES6 modules,
  // by structuring the imports/exports in a consistent way.
  private void maybeWarnForInvalidDestructuring(
      NodeTraversal t, Node importNode, String importedNamespace) {
    checkArgument(importNode.getFirstChild().isDestructuringLhs(), importNode);
    ScriptDescription importedModule =
        rewriteState.scriptDescriptionsByGoogModuleNamespace.get(importedNamespace);
    if (importedModule == null) {
      // Don't know enough to give a good warning here.
      return;
    }
    if (importedModule.defaultExportRhs != null) {
      t.report(importNode, ILLEGAL_DESTRUCTURING_DEFAULT_EXPORT);
      return;
    }
    Node objPattern = importNode.getFirstFirstChild();
    for (Node key = objPattern.getFirstChild(); key != null; key = key.getNext()) {
      String exportName = key.getString();
      if (!importedModule.namedExports.contains(exportName)) {
        t.report(importNode, ILLEGAL_DESTRUCTURING_NOT_EXPORTED, exportName, importedNamespace);
      }
    }
  }

  private void updateGoogForwardDeclare(NodeTraversal t, Node call) {
    // For import rewriting purposes and when taking into account previous moduleAlias versus
    // legacyNamespace import categorization, goog.forwardDeclare is much the same as goog.require.
    updateGoogRequire(t, call);
  }

  private void updateGoogModuleGetCall(Node call) {
    Node legacyNamespaceNode = call.getSecondChild();
    String legacyNamespace = legacyNamespaceNode.getString();

    // Remaining calls to goog.module.get() are not alias updates,
    // and should be replaced by a reference to the proper name.
    // Replace "goog.module.get('pkg.Foo')" with either "pkg.Foo" or "module$exports$pkg$Foo".
    String exportedNamespace = rewriteState.getExportedNamespaceOrScript(legacyNamespace);
    if (exportedNamespace != null) {
      compiler.reportChangeToEnclosingScope(call);
      Node exportedNamespaceName = NodeUtil.newQName(compiler, exportedNamespace).srcrefTree(call);
      exportedNamespaceName.setOriginalName(legacyNamespace);
      call.replaceWith(exportedNamespaceName);
    }
  }

  private void recordExportsPropertyAssignment(NodeTraversal t, Node getpropNode) {
    if (!currentScript.isModule) {
      return;
    }

    Node parent = getpropNode.getParent();
    checkState(parent.isAssign() || parent.isExprResult(), parent);

    Node exportsNameNode = getpropNode.getFirstChild();
    checkState(exportsNameNode.getString().equals("exports"), exportsNameNode);

    if (t.inModuleScope()) {
      String exportName = getpropNode.getLastChild().getString();
      currentScript.namedExports.add(exportName);
      Node exportRhs = getpropNode.getNext();
      ExportDefinition namedExport = ExportDefinition.newNamedExport(t, exportName, exportRhs);
      if (!currentScript.declareLegacyNamespace
          && currentScript.defaultExportRhs == null
          && namedExport.hasInlinableName(currentScript.exportsToInline.keySet())) {
        recordExportToInline(namedExport);
        parent.getParent().detach();
      }
    }
  }

  private void updateExportsPropertyAssignment(Node getpropNode) {
    if (!currentScript.isModule) {
      return;
    }

    Node parent = getpropNode.getParent();
    checkState(parent.isAssign() || parent.isExprResult(), parent);

    // Update "exports.foo = Foo" to "module$exports$pkg$Foo.foo = Foo";
    Node exportsNameNode = getpropNode.getFirstChild();
    checkState(exportsNameNode.getString().equals("exports"));
    String exportedNamespace = currentScript.getExportedNamespace();
    safeSetMaybeQualifiedString(exportsNameNode, exportedNamespace);

    Node jsdocNode = parent.isAssign() ? parent : getpropNode;
    markConstAndCopyJsDoc(jsdocNode, jsdocNode);

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

    // If the name is part of a destructuring import, the import rewriting will take care of it
    if (var.getNameNode() == nameNode
        && nameNode.getParent().isStringKey()
        && nameNode.getGrandparent().isObjectPattern()) {
      Node destructuringLhsNode = nameNode.getGrandparent().getParent();
      if (isCallTo(destructuringLhsNode.getLastChild(), GOOG_REQUIRE)
          || isCallTo(destructuringLhsNode.getLastChild(), GOOG_REQUIRETYPE)) {
        return;
      }
    }

    // If the name is an alias for an imported namespace rewrite from
    // "new Foo;" to "new module$exports$Foo;"
    boolean nameIsAnAlias = currentScript.namesToInlineByAlias.containsKey(name);
    if (nameIsAnAlias && var.getNode() != nameNode) {
      maybeAddAliasToSymbolTable(nameNode, currentScript.legacyNamespace);

      String namespaceToInline = currentScript.namesToInlineByAlias.get(name);
      if (namespaceToInline.equals(currentScript.getBinaryNamespace())) {
        currentScript.hasCreatedExportObject = true;
      }
      safeSetMaybeQualifiedString(nameNode, namespaceToInline);

      // Make sure this action won't shadow a local variable.
      if (namespaceToInline.indexOf('.') != -1) {
        String firstQualifiedName = namespaceToInline.substring(0, namespaceToInline.indexOf('.'));
        Var shadowedVar = t.getScope().getVar(firstQualifiedName);
        if (shadowedVar == null
            || shadowedVar.isGlobal()
            || shadowedVar.getScope().isModuleScope()) {
          return;
        }
        t.report(
            shadowedVar.getNode(),
            IMPORT_INLINING_SHADOWS_VAR,
            shadowedVar.getName(),
            namespaceToInline);
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
   * TODO(blickly): Remove as much of this functionality as possible, now that these style of
   * exports are rewritten in ScriptPreprocess step.
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
        AbstractScope<?, ?> varScope = v.getScope();
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

    markConstAndCopyJsDoc(target, target);
  }

  /**
   * In module "foo.Bar", rewrite "exports = Bar" to "var module$exports$foo$Bar = Bar".
   */
  private void maybeUpdateExportDeclaration(NodeTraversal t, Node n) {
    if (!currentScript.isModule
        || !n.getString().equals("exports")
        || !isAssignTarget(n)) {
      return;
    }

    Node assignNode = n.getParent();
    if (!currentScript.declareLegacyNamespace
        && currentScript.defaultExportLocalName != null) {
      assignNode.getParent().detach();
      return;
    }

    // Rewrite "exports = ..." as "var module$exports$foo$Bar = ..."
    Node rhs = assignNode.getLastChild();
    Node jsdocNode;
    if (currentScript.declareLegacyNamespace) {
      Node legacyQname = NodeUtil.newQName(compiler, currentScript.legacyNamespace).srcrefTree(n);
      assignNode.replaceChild(n, legacyQname);
      jsdocNode = assignNode;
    } else {
      rhs.detach();
      Node exprResultNode = assignNode.getParent();
      Node binaryNamespaceName = IR.name(currentScript.getBinaryNamespace());
      binaryNamespaceName.setOriginalName(currentScript.legacyNamespace);
      Node exportsObjectCreationNode = IR.var(binaryNamespaceName, rhs);
      exportsObjectCreationNode.useSourceInfoIfMissingFromForTree(exprResultNode);
      exportsObjectCreationNode.putBooleanProp(Node.IS_NAMESPACE, true);
      exprResultNode.replaceWith(exportsObjectCreationNode);
      jsdocNode = exportsObjectCreationNode;
      currentScript.hasCreatedExportObject = true;
    }
    markConstAndCopyJsDoc(assignNode, jsdocNode);
    compiler.reportChangeToEnclosingScope(jsdocNode);

    maybeUpdateExportObjectLiteral(t, rhs);
    return;
  }

  private void maybeUpdateExportNameRef(Node n) {
    if (!currentScript.isModule || !"exports".equals(n.getString()) || n.getParent() == null) {
      return;
    }
    if (n.getParent().isParamList()) {
      return;
    }

    if (currentScript.declareLegacyNamespace) {
      Node legacyQname = NodeUtil.newQName(compiler, currentScript.legacyNamespace).srcrefTree(n);
      n.replaceWith(legacyQname);
      compiler.reportChangeToEnclosingScope(legacyQname);
      return;
    }

    safeSetString(n, currentScript.getBinaryNamespace());

    // Either this module is going to create it's own exports object at some point or else if it's
    // going to be defensively created automatically then that should have occurred at the top of
    // the file and been done by now.
    checkState(currentScript.willCreateExportsObject || currentScript.hasCreatedExportObject);
  }

  void updateModuleBody(Node moduleBody) {
    checkArgument(
        moduleBody.isModuleBody() && moduleBody.getParent().getBooleanProp(Node.GOOG_MODULE),
        moduleBody);
    moduleBody.setToken(Token.BLOCK);
    NodeUtil.tryMergeBlock(moduleBody, true);

    updateEndModule();
    popScript();
  }

  private void updateEndModule() {
    for (ExportDefinition export : currentScript.exportsToInline.values()) {
      Node nameNode = export.nameDecl.getNameNode();
      safeSetMaybeQualifiedString(
          nameNode, currentScript.getBinaryNamespace() + export.getExportPostfix());
    }
    checkState(currentScript.isModule, currentScript);
    checkState(
        currentScript.declareLegacyNamespace || currentScript.hasCreatedExportObject,
        currentScript);
  }

  /**
   * Record the provided script as the current script at top of the script stack and add it as a
   * child of the previous current script if there was one.
   *
   * <p>Keeping track of the current script facilitates aggregation of accurate script state so that
   * rewriting can run properly. Handles scripts and nested goog.modules.
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
    if (currentScript.declareLegacyNamespace) {
      return;
    }

    Node binaryNamespaceName = IR.name(currentScript.getBinaryNamespace());
    binaryNamespaceName.setOriginalName(currentScript.legacyNamespace);
    Node binaryNamespaceExportNode = IR.var(binaryNamespaceName, IR.objectlit());
    if (addAt == AddAt.BEFORE) {
      atNode.getParent().addChildBefore(binaryNamespaceExportNode, atNode);
    } else if (addAt == AddAt.AFTER) {
      atNode.getParent().addChildAfter(binaryNamespaceExportNode, atNode);
    }
    binaryNamespaceExportNode.putBooleanProp(Node.IS_NAMESPACE, true);
    binaryNamespaceExportNode.srcrefTree(atNode);
    markConst(binaryNamespaceExportNode);
    compiler.reportChangeToEnclosingScope(binaryNamespaceExportNode);
    currentScript.hasCreatedExportObject = true;
  }

  static void checkAndSetStrictModeDirective(NodeTraversal t, Node n) {
    checkState(n.isScript(), n);

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
    Node statementNode = rhsNode.getGrandparent();
    if (!statementNode.isVar() || !statementNode.hasMoreThanOneChild()) {
      return;
    }

    Node nameNode = rhsNode.getParent();
    nameNode.detach();
    rhsNode.detach();
    statementNode.getParent().addChildBefore(IR.var(nameNode, rhsNode), statementNode);
  }

  private static void markConstAndCopyJsDoc(Node from, Node target) {
    JSDocInfo info = from.getJSDocInfo();
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(info);
    builder.recordConstancy();
    target.setJSDocInfo(builder.build());
  }

  private void recordExportToInline(ExportDefinition exportDefinition) {
    checkState(
        exportDefinition.hasInlinableName(currentScript.exportsToInline.keySet()),
        "exportDefinition: %s\n\nexportsToInline keys: %s",
        exportDefinition,
        currentScript.exportsToInline.keySet());
    checkState(
        null == currentScript.exportsToInline.put(exportDefinition.nameDecl, exportDefinition),
        "Already found a mapping for inlining export: %s", exportDefinition.nameDecl);
    String localName = exportDefinition.getLocalName();
    String fullExportedName =
        currentScript.getBinaryNamespace() + exportDefinition.getExportPostfix();
    recordNameToInline(localName, fullExportedName);
  }

  private void recordNameToInline(String aliasName, String legacyNamespace) {
    checkNotNull(aliasName);
    checkNotNull(legacyNamespace);
    checkState(
        null == currentScript.namesToInlineByAlias.put(aliasName, legacyNamespace),
        "Already found a mapping for inlining short name: %s", aliasName);
  }

  /**
   * Examines queue'ed unrecognizedRequires to categorize and report them as either missing module,
   * missing namespace or late provide.
   */
  private void reportUnrecognizedRequires() {
    for (UnrecognizedRequire unrecognizedRequire : unrecognizedRequires) {
      String legacyNamespace = unrecognizedRequire.legacyNamespace;

      Node requireNode = unrecognizedRequire.requireNode;
      boolean targetGoogModuleExists = rewriteState.containsModule(legacyNamespace);
      boolean targetLegacyScriptExists =
          rewriteState.legacyScriptNamespaces.contains(legacyNamespace);

      if (!targetGoogModuleExists && !targetLegacyScriptExists) {
        // The required thing was free to be either a goog.module() or a legacy script but neither
        // flavor of file provided the required namespace, so report a vague error.
        compiler.report(
            JSError.make(
                requireNode,
                MISSING_MODULE_OR_PROVIDE,
                legacyNamespace));
        // Remove the require node so this problem isn't reported again in ProcessClosurePrimitives.
        if (!preserveSugar) {
          Node changeScope = NodeUtil.getEnclosingChangeScopeRoot(requireNode);
          if (changeScope == null) {
            // It's already been removed; nothing to do.
          } else {
            compiler.reportChangeToChangeScope(changeScope);
            NodeUtil.getEnclosingStatement(requireNode).detach();
          }
        }
        continue;
      }

      // The required thing actually was available somewhere in the program but just wasn't
      // available as early as the require statement would have liked.
      if (unrecognizedRequire.mustBeOrdered) {
        compiler.report(JSError.make(requireNode, LATE_PROVIDE_ERROR, legacyNamespace));
      }
    }

    // Clear the queue so that repeated reportUnrecognizedRequires() invocations in hotswap compiles
    // only report new problems.
    unrecognizedRequires.clear();
  }

  private void safeSetString(Node n, String newString) {
    if (n.getString().equals(newString)) {
      return;
    }

    String originalName = n.getString();
    n.setString(newString);
    if (n.getOriginalName() == null) {
      n.setOriginalName(originalName);
    }
    // TODO(blickly): It would be better not to be renaming detached nodes
    Node changeScope = NodeUtil.getEnclosingChangeScopeRoot(n);
    if (changeScope != null) {
      compiler.reportChangeToChangeScope(changeScope);
    }
  }

  private void safeSetMaybeQualifiedString(Node nameNode, String newString) {
    if (!newString.contains(".")) {
      safeSetString(nameNode, newString);
      return;
    }
    // When replacing with a dotted fully qualified name it's already better than an original
    // name.
    Node nameParent = nameNode.getParent();
    JSDocInfo jsdoc = nameParent.getJSDocInfo();
    switch (nameParent.getToken()) {
      case FUNCTION:
      case CLASS:
        if (NodeUtil.isStatement(nameParent) && nameParent.getFirstChild() == nameNode) {
          Node statementParent = nameParent.getParent();
          Node placeholder = IR.empty();
          statementParent.replaceChild(nameParent, placeholder);
          Node newStatement = NodeUtil.newQNameDeclaration(compiler, newString, nameParent, jsdoc);
          nameParent.setJSDocInfo(null);
          newStatement.useSourceInfoIfMissingFromForTree(nameParent);
          replaceStringNodeLocationForExportedTopLevelVariable(
              newStatement, nameNode.getSourcePosition(), nameNode.getLength());
          statementParent.replaceChild(placeholder, newStatement);
          NodeUtil.removeName(nameParent);
          return;
        }
        break;
      case VAR:
      case LET:
      case CONST:
        {
          Node rhs = nameNode.hasChildren() ? nameNode.getLastChild().detach() : null;
          Node newStatement = NodeUtil.newQNameDeclaration(compiler, newString, rhs, jsdoc);
          newStatement.useSourceInfoIfMissingFromForTree(nameParent);
          int nameLength =
              nameNode.getOriginalName() != null
                  ? nameNode.getOriginalName().length()
                  : nameNode.getString().length();
          // We want the final property name to have the correct length (that of the property
          // name, not of the entire nameNode).
          replaceStringNodeLocationForExportedTopLevelVariable(
              newStatement, nameNode.getSourcePosition(), nameLength);
          NodeUtil.replaceDeclarationChild(nameNode, newStatement);
          return;
        }
      case OBJECT_PATTERN:
      case ARRAY_PATTERN:
      case PARAM_LIST:
        throw new RuntimeException("Not supported");
      default:
        break;
    }
    Node newQualifiedNameNode = NodeUtil.newQName(compiler, newString);
    newQualifiedNameNode.srcrefTree(nameNode);
    nameParent.replaceChild(nameNode, newQualifiedNameNode);
    // Given import "var Bar = goog.require('foo.Bar');" here we replace a usage of Bar with
    // foo.Bar if Bar is goog.provided. 'foo' node is generated and never visible to user.
    // Because of that we should mark all such nodes as non-indexable leaving only Bar indexable.
    // Given that replacement is GETPROP node, prefix is first child. It's also possible that
    // replacement is single-part namespace. Like goog.provide('Bar') in that case replacement
    // won't have children.
    if (newQualifiedNameNode.getFirstChild() != null) {
      newQualifiedNameNode.getFirstChild().makeNonIndexableRecursive();
    }
    compiler.reportChangeToEnclosingScope(newQualifiedNameNode);
  }

  /**
   * If we had something like const FOO = "text" and we export FOO, change the source location
   * information for the rewritten FOO. The replacement should be something like MOD.FOO = "text",
   * so we look for MOD.FOO and replace the source location for FOO to the original location of FOO.
   *
   * @param n node tree to modify
   * @param sourcePosition position to set for the start of the STRING node.
   * @param length length to set for STRING node.
   */
  private void replaceStringNodeLocationForExportedTopLevelVariable(
      Node n, int sourcePosition, int length) {
    if (n.hasOneChild()) {
      Node assign = n.getFirstChild();
      if (assign != null && assign.isAssign()) {
        // ASSIGN always has two children.
        Node getProp = assign.getFirstChild();
        if (getProp != null && getProp.isGetProp()) {
          // GETPROP always has two children: a name node and a string node. They should both take
          // on the source range of the original variable.
          for (Node child : getProp.children()) {
            child.setSourceEncodedPosition(sourcePosition);
            child.setLength(length);
          }
        }
      }
    }
  }

  private boolean isTopLevel(NodeTraversal t, Node n, ScopeType scopeType) {
    if (scopeType == ScopeType.EXEC_CONTEXT) {
      return t.getClosestHoistScopeRoot() == currentScript.rootNode;
    } else {
      // Must be ScopeType.BLOCK;
      return n.getParent() == currentScript.rootNode;
    }
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
   * Add alias nodes to the symbol table as they going to be removed by rewriter. Example aliases:
   *
   * const Foo = goog.require('my.project.Foo');
   * const bar = goog.require('my.project.baz');
   * const {baz} = goog.require('my.project.utils');
   */
  private void maybeAddAliasToSymbolTable(Node n, String module) {
    if (preprocessorSymbolTable != null) {
      n.putBooleanProp(Node.MODULE_ALIAS, true);
      // Alias can be used in js types. Types have node type STRING and not NAME so we have to
      // use their name as string.
      String nodeName =
          n.getToken() == Token.STRING
              ? n.getString()
              : preprocessorSymbolTable.getQualifiedName(n);
      // We need to include module as part of the name because aliases are local to current module.
      // Aliases with the same name from different module should be completely different entities.
      String name = "alias_" + module + "_" + nodeName;
      preprocessorSymbolTable.addReference(n, name);
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

  /**
   * A faster version of NodeUtil.isCallTo() for methods in the GETPROP form.
   *
   * @param n The CALL node to be checked.
   * @param targetMethod A prebuilt GETPROP node representing a target method.
   * @return Whether n is a call to the target method.
   */
  private static boolean isCallTo(Node n, Node targetMethod) {
    if (!n.isCall()) {
      return false;
    }
    Node method = n.getFirstChild();
    return method.isGetProp() && method.matchesQualifiedName(targetMethod);
  }
}
