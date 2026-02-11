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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.javascript.jscomp.AstFactory.type;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_FORWARD_DECLARE_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_GET_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_REQUIRE_DYNAMIC;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_REQUIRE_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_REQUIRE_TYPE_NAMESPACE;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Process aliases in goog.modules.
 *
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
 */
final class ClosureRewriteModule implements CompilerPass {

  static final DiagnosticType INVALID_MODULE_ID_ARG =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_MODULE_ID_ARG",
          "goog.module parameter must be a string literal");

  static final DiagnosticType INVALID_PROVIDE_NAMESPACE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_PROVIDE_NAMESPACE",
          "goog.provide parameter must be a string literal.");

  static final DiagnosticType INVALID_PROVIDE_CALL =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_PROVIDE_CALL", "goog.provide can not be called in goog.module.");

  static final DiagnosticType INVALID_GET_ALIAS =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_GET_ALIAS", "goog.module.get should not be aliased.");

  static final DiagnosticType INVALID_EXPORT_COMPUTED_PROPERTY =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_EXPORT_COMPUTED_PROPERTY",
          "Computed properties are not yet supported in goog.module exports.");

  static final DiagnosticType USELESS_USE_STRICT_DIRECTIVE =
      DiagnosticType.disabled(
          "JSC_USELESS_USE_STRICT_DIRECTIVE", "'use strict' is unnecessary in goog.module files.");

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

  static final DiagnosticType LOAD_MODULE_FN_MISSING_RETURN =
      DiagnosticType.error(
          "JSC_LOAD_MODULE_FN_MISSING_RETURN",
          "goog.loadModule function should end with 'return exports;'");

  static final DiagnosticType ILLEGAL_MODULE_RENAMING_CONFLICT =
      DiagnosticType.error(
          "JSC_ILLEGAL_MODULE_RENAMING_CONFLICT",
          "Internal compiler error: rewritten module global name {0} is already in use.\n"
              + "Original definition: {1}");

  static final DiagnosticType ILLEGAL_STMT_OF_GOOG_REQUIRE_DYNAMIC_IN_AWAIT =
      DiagnosticType.error(
          "ILLEGAL_STMT_OF_GOOG_REQUIRE_DYNAMIC_IN_AWAIT",
          "Illegal use of dynamic import: LHS of await goog.requireDynamic() must be a destructing"
              + " LHS or name, and it must be in a declaration statement.");

  static final String MODULE_EXPORTS_PREFIX = "module$exports$";

  private static final String MODULE_CONTENTS_PREFIX = "module$contents$";

  // Prebuilt Nodes to speed up Node.matchesQualifiedName() calls
  private static final Node GOOG_FORWARDDECLARE = IR.getprop(IR.name("goog"), "forwardDeclare");
  private static final Node GOOG_LOADMODULE = IR.getprop(IR.name("goog"), "loadModule");
  private static final Node GOOG_MODULE = IR.getprop(IR.name("goog"), "module");
  private static final Node GOOG_MODULE_DECLARELEGACYNAMESPACE =
      IR.getprop(GOOG_MODULE, "declareLegacyNamespace");
  private static final QualifiedName GOOG_MODULE_PREVENTMODULEEXPORTSEALING =
      QualifiedName.of("goog.module.preventModuleExportSealing");
  private static final Node GOOG_MODULE_GET = IR.getprop(GOOG_MODULE.cloneTree(), "get");
  private static final Node GOOG_PROVIDE = IR.getprop(IR.name("goog"), "provide");
  private static final Node GOOG_REQUIRE = IR.getprop(IR.name("goog"), "require");
  private static final Node GOOG_REQUIRETYPE = IR.getprop(IR.name("goog"), "requireType");
  private static final Node GOOG_REQUIREDYNAMIC = IR.getprop(IR.name("goog"), "requireDynamic");
  private static final String GOOG_REQUIREDYNAMIC_NAME = "goog.requireDynamic";
  private static final String IMPORT_HANDLER_NAME = "importHandler_";

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final PreprocessorSymbolTable preprocessorSymbolTable;
  private final boolean preserveSugar;
  private final LinkedHashMap<String, Node> syntheticExterns = new LinkedHashMap<>();

  private @Nullable Scope globalScope =
      null; // non-final because it must be set after process() is called

  /** Indicates where new nodes should be added in relation to some other node. */
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
    final String namespaceId;

    UnrecognizedRequire(Node requireNode, String namespaceId) {
      this.requireNode = requireNode;
      this.namespaceId = namespaceId;
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
        Sets.immutableEnumSet(Token.VAR, Token.CONST, Token.LET, Token.FUNCTION, Token.CLASS);

    static ExportDefinition newDefaultExport(NodeTraversal t, Node rhs) {
      return newNamedExport(t, null, rhs);
    }

    static ExportDefinition newNamedExport(NodeTraversal t, @Nullable String name, Node rhs) {
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
          || !INLINABLE_NAME_PARENTS.contains(nameDecl.getParentNode().getToken())
          || NodeUtil.isFunctionDeclaration(nameDecl.getParentNode())) {
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
      String name = method.getString();
      return !name.equals("require") && !name.equals("forwardDeclare") && !name.equals("getMsg");
    }

    @Nullable String getLocalName() {
      return nameDecl != null ? nameDecl.getName() : null;
    }
  }

  private static class AliasName {
    final String newName;
    final @Nullable String namespaceId; // non-null only if this is an alias of a module itself

    AliasName(String newName, @Nullable String namespaceId) {
      this.newName = newName;
      this.namespaceId = namespaceId;
    }
  }

  private static final class ScriptDescription {
    boolean isModule;
    boolean declareLegacyNamespace;
    String namespaceId; // "a.b.c"
    String contentsPrefix; // "module$contents$a$b$c_
    final Set<String> topLevelNames = new LinkedHashSet<>(); // For prefixed content renaming.
    final Deque<ScriptDescription> childScripts = new ArrayDeque<>();
    final Map<String, AliasName> namesToInlineByAlias =
        new LinkedHashMap<>(); // For alias inlining.

    /** Transient state. */
    boolean willCreateExportsObject;

    boolean hasCreatedExportObject;
    ExportDefinition defaultExport;
    @Nullable String defaultExportLocalName;
    final Set<String> namedExports = new LinkedHashSet<>();
    final Map<Var, ExportDefinition> exportsToInline = new LinkedHashMap<>();

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
      return getBinaryModuleNamespace(namespaceId);
    }

    @Nullable String getExportedNamespace() {
      if (this.declareLegacyNamespace) {
        return this.namespaceId;
      }
      return this.getBinaryNamespace();
    }
  }

  static String getBinaryModuleNamespace(String namespaceId) {
    return MODULE_EXPORTS_PREFIX + namespaceId.replace('.', '$');
  }

  private class ScriptPreprocessor extends NodeTraversal.AbstractPreOrderCallback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case ROOT, MODULE_BODY -> {
          return true;
        }
        case SCRIPT -> {
          if (NodeUtil.isGoogModuleFile(n)) {
            checkAndSetStrictModeDirective(t, n);
          }
          return true;
        }
        case NAME -> {
          preprocessExportDeclaration(t, n);
          return true;
        }
        default -> {
          // Don't traverse into non-module scripts.
          return !parent.isScript();
        }
      }
    }
  }

  private class ScriptRecorder implements NodeTraversal.Callback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case MODULE_BODY -> recordModuleBody(n);
        case CALL -> {
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
            recordGoogRequire(t, n);
          } else if (method.matchesQualifiedName(GOOG_REQUIRETYPE)) {
            recordGoogRequireType(t, n);
          } else if (method.matchesQualifiedName(GOOG_REQUIREDYNAMIC)) {
            recordGoogRequireDynamic(t, n);
          } else if (method.matchesQualifiedName(GOOG_FORWARDDECLARE) && !parent.isExprResult()) {
            recordGoogForwardDeclare(t, n);
          } else if (method.matchesQualifiedName(GOOG_MODULE_GET)) {
            recordGoogModuleGet(t, n);
          }
        }
        case CLASS, FUNCTION -> {
          if (isTopLevel(t, n, ScopeType.BLOCK)) {
            recordTopLevelClassOrFunctionName(n);
          }
        }
        case CONST, LET, VAR -> {
          if (isTopLevel(t, n, n.isVar() ? ScopeType.EXEC_CONTEXT : ScopeType.BLOCK)) {
            recordTopLevelVarNames(n);
          }
        }
        case GETPROP -> {
          if (isExportPropertyAssignment(t, n)) {
            recordExportsPropertyAssignment(t, n);
          }
        }
        case NAME -> maybeRecordExportDeclaration(t, n);
        default -> {}
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

  private class ScriptUpdater implements NodeTraversal.Callback {
    final Deque<ScriptDescription> scriptDescriptions;

    ScriptUpdater(Deque<ScriptDescription> scriptDescriptions) {
      this.scriptDescriptions = scriptDescriptions;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {

      switch (n.getToken()) {
        case SCRIPT -> {
          ScriptDescription currentDescription = scriptDescriptions.removeFirst();
          checkState(currentDescription.rootNode == n);
          if (n.isFromExterns() && !NodeUtil.isFromTypeSummary(n)) {
            return false;
          }

          checkState(scriptStack.isEmpty());
          pushScript(currentDescription);

          // Capture the scope before doing any rewriting to the scope.
          t.getScope();

          // Capture the global scope for later reference.
          if (globalScope == null) {
            globalScope = t.getScope().getGlobalScope();
          }
        }
        case MODULE_BODY -> {
          if (parent.getBooleanProp(Node.GOOG_MODULE)) {
            updateModuleBodyEarly(n);
          } else {
            return false;
          }
        }
        case CALL -> {
          Node method = n.getFirstChild();
          if (!method.isGetProp()) {
            break;
          }
          if (method.matchesQualifiedName(GOOG_MODULE)) {
            updateGoogModule(t, n);
          } else if (method.matchesQualifiedName(GOOG_MODULE_DECLARELEGACYNAMESPACE)) {
            updateGoogDeclareLegacyNamespace(n);
          } else if (method.matchesQualifiedName(GOOG_REQUIRE)
              || method.matchesQualifiedName(GOOG_REQUIRETYPE)) {
            updateGoogRequire(t, n);
          } else if (method.matchesQualifiedName(GOOG_FORWARDDECLARE) && !parent.isExprResult()) {
            updateGoogForwardDeclare(t, n);
          } else if (GOOG_MODULE_PREVENTMODULEEXPORTSEALING.matches(method)) {
            updateGoogPreventModuleExportsSealing(n);
          }
        }
        case GETPROP -> {
          if (isExportPropertyAssignment(t, n)) {
            updateExportsPropertyAssignment(n, t);
          }
        }
        default -> {}
      }

      if (n.getJSDocInfo() != null) {
        rewriteJsdoc(n.getJSDocInfo(), t.getScope());
      }

      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case MODULE_BODY -> updateModuleBody(n);
        case NAME -> {
          maybeUpdateTopLevelName(t, n);
          maybeUpdateExportDeclaration(t, n);
          t.getScope(); // Creating the scope here has load-bearing side-effects.
          maybeUpdateExportNameRef(t, n);
        }
        case SCRIPT -> {
          checkState(currentScript.rootNode == n);
          popScript();
        }
        default -> {}
      }
    }
  }

  /**
   * Rewrites JsDoc type references to match AST changes resulting from imported alias inlining,
   * module content renaming of top level constructor functions and classes, and module renaming
   * from fully qualified legacy namespace to its binary name.
   */
  private void rewriteJsdoc(JSDocInfo info, Scope scope) {
    ReplaceJsDocRefs replacer = new ReplaceJsDocRefs(scope);
    for (Node typeNode : info.getTypeNodes()) {
      NodeUtil.visitPreOrder(typeNode, replacer);
    }
  }

  /**
   * Rewrites JsDoc type references to match AST changes resulting from imported alias inlining,
   * module content renaming of top level constructor functions and classes, and module renaming
   * from fully qualified legacy namespace to its binary name.
   */
  private final class ReplaceJsDocRefs implements NodeUtil.Visitor {
    private final Scope scope;

    ReplaceJsDocRefs(Scope scope) {
      this.scope = scope;
    }

    @Override
    public void visit(Node typeRefNode) {
      if (!typeRefNode.isStringLit()) {
        return;
      }
      // A type name that might be simple like "Foo" or qualified like "foo.Bar".
      final String typeName = typeRefNode.getString();
      int dot = typeName.indexOf('.');
      String rootOfType = dot == -1 ? typeName : typeName.substring(0, dot);

      Var rootVar = this.scope.getVar(rootOfType);
      if (rootVar != null
          && !rootVar.getScope().getClosestHoistScope().isGlobal()
          && !rootVar.getScope().isModuleScope()) {
        // this is a variable inside a local scope, not a module local or imported alias.
        return;
      }
      // Rewrite the type node if any of the following hold, in priority order:
      //  - the root of the type name is in our set of aliases to inline
      //  - the root of the type name is a name defined in the module scope
      //  - a prefix of the type name matches a Closure namespace
      // AND the following is false:
      //  - the root of the type name is defined in an inner scope

      // If the name is an alias for an imported namespace rewrite from
      // "{Foo}" to
      // "{module$exports$bar$Foo}" or
      // "{bar.Foo}"
      if (currentScript.namesToInlineByAlias.containsKey(rootOfType)) {
        if (preprocessorSymbolTable != null) {
          // Jsdoc type node is a single STRING node that spans the whole type. For example
          // STRING node "bar.Foo". When rewriting modules potentially replace only "module"
          // part of the type: "bar.Foo" => "module$exports$bar$Foo". So we need to remember
          // that "bar" as alias. To do that we clone type node and make "bar" node from it.
          Node moduleOnlyNode = typeRefNode.cloneNode();
          safeSetString(moduleOnlyNode, rootOfType);
          moduleOnlyNode.setLength(rootOfType.length());
          maybeAddAliasToSymbolTable(moduleOnlyNode, currentScript.namespaceId);
        }

        String aliasedNamespace = currentScript.namesToInlineByAlias.get(rootOfType).newName;
        String remainder = dot == -1 ? "" : typeName.substring(dot);
        safeSetString(typeRefNode, aliasedNamespace + remainder);
      } else if (currentScript.isModule && currentScript.topLevelNames.contains(rootOfType)) {
        // If this is a module and the type name is the name of a top level var/function/class
        // defined in this script then that var will have been previously renamed from Foo to
        // module$contents$Foo_Foo. Update the JsDoc reference to match.
        safeSetString(typeRefNode, currentScript.contentsPrefix + typeName);
      } else if (currentScript.isModule && rootOfType.equals("exports")) {
        // rewrite a type reference to the implicit "exports" variable
        // e.g. /** @type {exports.Foo} */ -> /** @type {module$exports$my$mod.Foo} */
        String namespace = currentScript.getBinaryNamespace();
        String remainder = dot == -1 ? "" : typeName.substring(dot);
        safeSetString(typeRefNode, namespace + remainder);
      } else {
        rewriteIfClosureNamespaceRef(typeName, typeRefNode);
      }
    }

    /**
     * Tries to match the longest possible prefix of this type to a Closure namespace
     *
     * <p>If the longest prefix match is a legacy module or provide, this is a no-op. If the longest
     * prefix match is a non-legacy module, this method rewrites the type node.
     */
    private void rewriteIfClosureNamespaceRef(String typeName, Node typeRefNode) {
      // Tries to rename progressively shorter type prefixes like "foo.Bar.Baz", then "foo.Bar",
      // then "foo".
      String prefixTypeName = typeName;
      String suffix = "";

      while (true) {
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
        // Note: we may want to ban this pattern in the future. See b/133501660.
        if (binaryNamespaceIfModule != null) {
          safeSetString(typeRefNode, binaryNamespaceIfModule + suffix);
          return;
        }

        if (prefixTypeName.contains(".")) {
          prefixTypeName = prefixTypeName.substring(0, prefixTypeName.lastIndexOf('.'));
          suffix = typeName.substring(prefixTypeName.length());
        } else {
          break;
        }
      }
    }
  }

  // Per script state needed for rewriting.
  private final Deque<ScriptDescription> scriptStack = new ArrayDeque<>();
  private @Nullable ScriptDescription currentScript = null;

  // Global state tracking an association between the dotted names of goog.module()s and whether
  // the goog.module declares itself as a legacy namespace.
  // Allows for detecting duplicate goog.module()s and for rewriting fully qualified
  // JsDoc type references to goog.module() types in legacy scripts.
  private static final class GlobalRewriteState {
    private final Map<String, ScriptDescription> scriptDescriptionsByGoogModuleNamespace =
        new LinkedHashMap<>();
    private final Multimap<Node, String> namespaceIdsByScriptNode = HashMultimap.create();
    private final Set<String> providedNamespaces = new LinkedHashSet<>();

    boolean containsModule(String namespaceId) {
      return scriptDescriptionsByGoogModuleNamespace.containsKey(namespaceId);
    }

    boolean isLegacyModule(String namespaceId) {
      checkArgument(containsModule(namespaceId));
      return scriptDescriptionsByGoogModuleNamespace.get(namespaceId).declareLegacyNamespace;
    }

    @Nullable String getBinaryNamespace(String namespaceId) {
      ScriptDescription script = scriptDescriptionsByGoogModuleNamespace.get(namespaceId);
      return script == null ? null : script.getBinaryNamespace();
    }

    /** Returns the type of a goog.require of the given goog.module, or null if not a module. */
    @Nullable JSType getGoogModuleNamespaceType(String namespaceId) {
      ScriptDescription googModule = scriptDescriptionsByGoogModuleNamespace.get(namespaceId);
      return googModule == null ? null : googModule.rootNode.getJSType();
    }

    private @Nullable String getExportedNamespaceOrScript(String namespaceId) {
      if (providedNamespaces.contains(namespaceId)) {
        return namespaceId;
      }
      ScriptDescription script = scriptDescriptionsByGoogModuleNamespace.get(namespaceId);
      return script == null ? null : script.getExportedNamespace();
    }
  }

  private final GlobalRewriteState rewriteState = new GlobalRewriteState();
  // All prefix namespaces from goog.provides and legacy goog.modules.
  private final Set<String> legacyScriptNamespacesAndPrefixes = new LinkedHashSet<>();
  private final List<UnrecognizedRequire> unrecognizedRequires = new ArrayList<>();
  private final ArrayList<Node> googModuleGetCalls = new ArrayList<>();
  private final ArrayList<Node> googRequireDynamicCalls = new ArrayList<>();

  private final @Nullable TypedScope globalTypedScope;

  ClosureRewriteModule(
      AbstractCompiler compiler,
      PreprocessorSymbolTable preprocessorSymbolTable,
      @Nullable TypedScope globalTypedScope) {
    checkArgument(globalTypedScope == null || globalTypedScope.isGlobal());

    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.preprocessorSymbolTable = preprocessorSymbolTable;
    this.preserveSugar = compiler.getOptions().shouldPreserveGoogModule();
    this.globalTypedScope = globalTypedScope;
  }

  private class UnwrapGoogLoadModule extends NodeTraversal.AbstractPreOrderCallback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case ROOT, SCRIPT -> {
          return true;
        }
        case EXPR_RESULT -> {
          Node call = n.getFirstChild();
          if (NodeUtil.isCallTo(call, GOOG_LOADMODULE) && call.getLastChild().isFunction()) {
            parent.putBooleanProp(Node.GOOG_MODULE, true);
            Node functionNode = call.getLastChild();
            compiler.reportFunctionDeleted(functionNode);
            Node moduleBody = functionNode.getLastChild().detach();
            moduleBody.setToken(Token.MODULE_BODY);
            Node exportsParameter = NodeUtil.getFunctionParameters(functionNode).getOnlyChild();
            moduleBody.setJSType(exportsParameter.getJSType());
            n.replaceWith(moduleBody);
            Node returnNode = moduleBody.getLastChild();
            if (!returnNode.isReturn()) {
              compiler.report(JSError.make(moduleBody, LOAD_MODULE_FN_MISSING_RETURN));
            } else {
              returnNode.detach();
            }
            t.reportCodeChange();
          }
          return false;
        }
        default -> {
          return false;
        }
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    // Record all the scripts first so that the googModuleNamespaces global state can be complete
    // before doing any updating also queue up scriptDescriptions for later use in ScriptUpdater
    // runs.

    Deque<ScriptDescription> scriptDescriptions = new ArrayDeque<>();
    for (Node parent : ImmutableList.of(externs, root)) {
      for (Node script = parent.getFirstChild(); script != null; script = script.getNext()) {
        checkState(script.isScript(), script);
        NodeTraversal.traverse(compiler, script, new UnwrapGoogLoadModule());
        pushScript(new ScriptDescription()); // sets currentScript

        currentScript.rootNode = script;
        scriptDescriptions.addLast(currentScript);
        NodeTraversal.traverse(compiler, script, new ScriptPreprocessor());
        NodeTraversal.traverse(compiler, script, new ScriptRecorder());
        popScript();
      }
    }

    reportUnrecognizedRequires();
    if (compiler.hasHaltingErrors()) {
      return;
    }

    // Update scripts using the now complete googModuleNamespaces global state and unspool the
    // scriptDescriptions that were queued up by all the recording.

    NodeTraversal.traverseRoots(compiler, new ScriptUpdater(scriptDescriptions), externs, root);
    declareSyntheticExterns();
    this.googModuleGetCalls.forEach(this::updateGoogModuleGetCall);
    this.googRequireDynamicCalls.forEach(this::updateGoogRequireDynamicCall);
  }

  /**
   * Declares `var foo;` in the externs for all {@code synthetic_externs} names that aren't already
   * in the global scope.
   *
   * <p>Only add externs in the error case where there is an unrecognized goog.require or
   * goog.module.get. Clutz depends on the compiler deleting local name declarations that alias or
   * destructure the invalid call from this file. In order to preserve AST validity, we instead
   * declare the deleted name in the externs. (which is fine with Clutz, as it just ignores the
   * synthetic externs)
   */
  private void declareSyntheticExterns() {
    ImmutableList<Node> vars =
        syntheticExterns.values().stream()
            // Skip roots of goog.provide or goog.module.declareLegacyNamespace();
            .filter((lhs) -> !isNameInGlobalScope(lhs.getString()))
            .map(
                (lhs) ->
                    IR.var(astFactory.createNameWithUnknownType(lhs.getString())).srcrefTree(lhs))
            .collect(toImmutableList());

    if (vars.isEmpty()) {
      return;
    }

    Node root = compiler.getSynthesizedExternsInput().getAstRoot(compiler);
    vars.forEach(root::addChildToBack);
  }

  /**
   * Returns whether the name is declared in the global scope, either explicitly with var/const/let
   * or implicitly by a goog.provide or legacy goog.module.
   */
  private boolean isNameInGlobalScope(String name) {
    return legacyScriptNamespacesAndPrefixes.contains(name) || globalScope.getVar(name) != null;
  }

  /**
   * Rewrites object literal exports to the standard named exports style. i.e. exports = {Foo, Bar}
   * to exports.Foo = Foo; exports.Bar = Bar; This makes the module exports into a more standard
   * format for later passes.
   */
  private void preprocessExportDeclaration(NodeTraversal t, Node n) {
    if (!isGoogModuleExportsRef(t, n) || !isAssignTarget(n) || !n.getGrandparent().isExprResult()) {
      return;
    }

    checkState(currentScript.defaultExport == null);
    Node exportRhs = n.getNext();
    if (NodeUtil.isNamedExportsLiteral(exportRhs)) {
      Node insertionPoint = n.getGrandparent();
      for (Node key = exportRhs.getFirstChild(); key != null; key = key.getNext()) {
        String exportName = key.getString();
        JSDocInfo jsdoc = key.getJSDocInfo();
        Node rhs = key.removeFirstChild();
        Node lhs =
            astFactory
                .createGetProp(astFactory.createName("exports", type(n)), exportName, type(rhs))
                .srcrefTree(key);
        Node newExport =
            IR.exprResult(astFactory.createAssign(lhs, rhs).srcref(key).setJSDocInfo(jsdoc))
                .srcref(key);
        newExport.insertAfter(insertionPoint);
        insertionPoint = newExport;
      }
      n.getGrandparent().detach();
    }
  }

  private void recordModuleBody(Node moduleRoot) {
    pushScript(new ScriptDescription());

    currentScript.rootNode = moduleRoot;
    currentScript.isModule = true;
  }

  private void recordGoogModule(NodeTraversal t, Node call) {
    Node namespaceIdNode = call.getLastChild();
    if (!namespaceIdNode.isStringLit()) {
      t.report(namespaceIdNode, INVALID_MODULE_ID_ARG);
      return;
    }
    String namespaceId = namespaceIdNode.getString();

    currentScript.namespaceId = namespaceId;
    currentScript.contentsPrefix = toModuleContentsPrefix(namespaceId);

    Node scriptNode = NodeUtil.getEnclosingScript(currentScript.rootNode);
    rewriteState.scriptDescriptionsByGoogModuleNamespace.put(namespaceId, currentScript);
    rewriteState.namespaceIdsByScriptNode.put(scriptNode, namespaceId);
  }

  private void recordGoogDeclareLegacyNamespace() {
    currentScript.declareLegacyNamespace = true;
    updateLegacyScriptNamespacesAndPrefixes(currentScript.namespaceId);
  }

  private void updateLegacyScriptNamespacesAndPrefixes(String namespace) {
    legacyScriptNamespacesAndPrefixes.add(namespace);
    for (int dot = namespace.lastIndexOf('.'); dot != -1; dot = namespace.lastIndexOf('.')) {
      namespace = namespace.substring(0, dot);
      legacyScriptNamespacesAndPrefixes.add(namespace);
    }
  }

  private void recordGoogProvide(NodeTraversal t, Node call) {
    Node namespaceIdNode = call.getLastChild();
    if (!namespaceIdNode.isStringLit()) {
      t.report(namespaceIdNode, INVALID_PROVIDE_NAMESPACE);
      return;
    }
    String namespaceId = namespaceIdNode.getString();

    if (currentScript.isModule) {
      t.report(namespaceIdNode, INVALID_PROVIDE_CALL);
    }

    Node scriptNode = NodeUtil.getEnclosingScript(call);
    // Log legacy namespaces and prefixes.
    rewriteState.providedNamespaces.add(namespaceId);
    rewriteState.namespaceIdsByScriptNode.put(scriptNode, namespaceId);
    updateLegacyScriptNamespacesAndPrefixes(namespaceId);
  }

  private void recordGoogRequire(NodeTraversal t, Node call) {
    maybeSplitMultiVar(call);

    Node namespaceIdNode = call.getLastChild();
    if (!namespaceIdNode.isStringLit()) {
      t.report(namespaceIdNode, INVALID_REQUIRE_NAMESPACE);
      return;
    }
    String namespaceId = namespaceIdNode.getString();

    // Maybe report an error if there is an attempt to import something that is expected to be a
    // goog.module() but no such goog.module() has been defined.
    boolean targetIsAModule = rewriteState.containsModule(namespaceId);
    boolean targetIsALegacyScript = rewriteState.providedNamespaces.contains(namespaceId);
    if (currentScript.isModule && !targetIsAModule && !targetIsALegacyScript) {
      unrecognizedRequires.add(new UnrecognizedRequire(call, namespaceId));
    }
  }

  private void recordGoogRequireType(NodeTraversal t, Node call) {
    Node namespaceIdNode = call.getLastChild();
    if (!namespaceIdNode.isStringLit()) {
      t.report(namespaceIdNode, INVALID_REQUIRE_TYPE_NAMESPACE);
      return;
    }

    // For purposes of import collection, goog.requireType is the same as goog.require but
    // a goog.requireType call is not required to appear after the corresponding namespace
    // definition.
    recordGoogRequire(t, call);
  }

  private void recordGoogForwardDeclare(NodeTraversal t, Node call) {
    Node namespaceNode = call.getLastChild();
    if (!call.hasTwoChildren() || !namespaceNode.isStringLit()) {
      t.report(namespaceNode, INVALID_FORWARD_DECLARE_NAMESPACE);
      return;
    }

    // For purposes of import collection, goog.forwardDeclare is the same as goog.require.
    recordGoogRequire(t, call);
  }

  private void recordGoogRequireDynamic(NodeTraversal t, Node call) {
    Node namespaceIdNode = call.getLastChild();
    if (!namespaceIdNode.isStringLit()) {
      t.report(namespaceIdNode, INVALID_REQUIRE_DYNAMIC);
      return;
    }

    String namespaceId = namespaceIdNode.getString();

    if (!rewriteState.containsModule(namespaceId)) {
      unrecognizedRequires.add(new UnrecognizedRequire(call, namespaceId));
    }
    this.googRequireDynamicCalls.add(call);
  }

  private void recordGoogModuleGet(NodeTraversal t, Node call) {
    Node namespaceIdNode = call.getLastChild();
    if (!call.hasTwoChildren() || !namespaceIdNode.isStringLit()) {
      t.report(namespaceIdNode, INVALID_GET_NAMESPACE);
      return;
    }
    String namespaceId = namespaceIdNode.getString();

    if (!rewriteState.containsModule(namespaceId)) {
      unrecognizedRequires.add(new UnrecognizedRequire(call, namespaceId));
    }
    this.googModuleGetCalls.add(call);

    Node maybeAssign = call.getParent();
    boolean isFillingAnAlias =
        maybeAssign.isAssign()
            && maybeAssign.getFirstChild().isName()
            && maybeAssign.getParent().isExprResult();
    if (!isFillingAnAlias || !currentScript.isModule) {
      return;
    }

    String aliasName = call.getParent().getFirstChild().getString();

    // If the assignment isn't into a var in our scope then it's not ok.
    Var aliasVar = t.getScope().getVar(aliasName);
    if (aliasVar == null) {
      // Reported in CheckClosureImports
      return;
    }

    // Even if it was to a var in our scope it should still only rewrite if the var looked like:
    //   let x = goog.forwardDeclare('a.namespace');
    Node aliasVarNodeRhs = NodeUtil.getRValueOfLValue(aliasVar.getNode());
    if (aliasVarNodeRhs == null
        || !NodeUtil.isCallTo(aliasVarNodeRhs, GOOG_FORWARDDECLARE)
        || !namespaceId.equals(aliasVarNodeRhs.getLastChild().getString())) {
      // Reported in CheckClosureImports
      return;
    }

    // Each goog.module.get() calling filling an alias will have the alias importing logic
    // handled at the goog.forwardDeclare call, and the corresponding goog.module.get can simply
    // be removed.
    compiler.reportChangeToEnclosingScope(maybeAssign);
    maybeAssign.getParent().detach();
    this.googModuleGetCalls.remove(this.googModuleGetCalls.size() - 1);
  }

  private void recordTopLevelClassOrFunctionName(Node classOrFunctionNode) {
    Node nameNode = classOrFunctionNode.getFirstChild();
    if (nameNode.isName() && !Strings.isNullOrEmpty(nameNode.getString())) {
      String name = nameNode.getString();
      currentScript.topLevelNames.add(name);
    }
  }

  private void recordTopLevelVarNames(Node varNode) {
    NodeUtil.visitLhsNodesInNode(
        varNode, (lhs) -> currentScript.topLevelNames.add(lhs.getString()));
  }

  private void maybeRecordExportDeclaration(NodeTraversal t, Node n) {
    if (!currentScript.isModule || !isGoogModuleExportsRef(t, n) || !isAssignTarget(n)) {
      return;
    }

    // ClosureCheckModule reports an error for duplicate 'exports = ' assignments, but that error
    // may be suppressed. If so then use the final assignment as the canonical one.
    if (currentScript.defaultExport != null) {
      ExportDefinition previousExport = currentScript.defaultExport;
      String localName = previousExport.getLocalName();
      if (localName != null && currentScript.namesToInlineByAlias.containsKey(localName)) {
        currentScript.namesToInlineByAlias.remove(localName);
      }
      currentScript.defaultExportLocalName = null;
    }
    Node exportRhs = n.getNext();

    // Exports object should have already been converted in ScriptPreprocess step.
    checkState(
        !NodeUtil.isNamedExportsLiteral(exportRhs),
        "Exports object should have been converted already");

    currentScript.willCreateExportsObject = true;
    ExportDefinition defaultExport = ExportDefinition.newDefaultExport(t, exportRhs);
    currentScript.defaultExport = defaultExport;
    if (!currentScript.declareLegacyNamespace
        && defaultExport.hasInlinableName(currentScript.exportsToInline.keySet())) {
      String localName = defaultExport.getLocalName();
      currentScript.defaultExportLocalName = localName;
      recordExportToInline(defaultExport);
    }
  }

  private void updateModuleBodyEarly(Node moduleScopeRoot) {
    pushScript(currentScript.removeFirstChildScript());
    currentScript.rootNode = moduleScopeRoot;
  }

  private void updateGoogModule(NodeTraversal t, Node call) {
    if (!currentScript.isModule) {
      compiler.reportChangeToEnclosingScope(call);
      Node undefined = astFactory.createVoid(astFactory.createNumber(0)).srcrefTree(call);
      call.replaceWith(undefined);
      return;
    }

    // If it's a goog.module() with a legacy namespace.
    if (currentScript.declareLegacyNamespace) {
      // Rewrite "goog.module('Foo');" as "goog.provide('Foo');".
      call.getFirstChild().setString("provide");
      compiler.reportChangeToEnclosingScope(call);
    }

    // If this script file isn't going to eventually create it's own exports object, then we know
    // we'll need to do it ourselves, and so we might as well create it as early as possible to
    // avoid ordering issues with goog.define().
    if (!currentScript.willCreateExportsObject) {
      checkState(!currentScript.hasCreatedExportObject, currentScript);
      exportTheEmptyBinaryNamespaceAt(NodeUtil.getEnclosingStatement(call), AddAt.AFTER, t);
    }

    if (!currentScript.declareLegacyNamespace && !preserveSugar) {
      // Otherwise it's a regular module and the goog.module() line can be removed.
      compiler.reportChangeToEnclosingScope(call);
      NodeUtil.getEnclosingStatement(call).detach();
    }
  }

  private static void updateGoogDeclareLegacyNamespace(Node call) {
    NodeUtil.getEnclosingStatement(call).detach();
  }

  private static void updateGoogPreventModuleExportsSealing(Node call) {
    NodeUtil.getEnclosingStatement(call).detach();
  }

  private void updateGoogRequire(NodeTraversal t, Node call) {
    Node namespaceIdNode = call.getLastChild();
    Node statementNode = NodeUtil.getEnclosingStatement(call);
    String namespaceId = namespaceIdNode.getString();

    boolean targetIsNonLegacyGoogModule =
        rewriteState.containsModule(namespaceId) && !rewriteState.isLegacyModule(namespaceId);
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
      String exportedNamespace = rewriteState.getExportedNamespaceOrScript(namespaceId);
      if (exportedNamespace == null) {
        // There's nothing to inline. The missing provide/module will be reported elsewhere.
      } else if (lhs.isName()) {
        // `var Foo` case
        String aliasName = statementNode.getFirstChild().getString();
        recordNameToInline(aliasName, exportedNamespace, namespaceId);
        maybeAddAliasToSymbolTable(statementNode.getFirstChild(), currentScript.namespaceId);
      } else if (lhs.isDestructuringLhs() && lhs.getFirstChild().isObjectPattern()) {
        // `const {Foo}` case
        maybeWarnForInvalidDestructuring(t, lhs.getParent(), namespaceId);
        for (Node importSpec = lhs.getFirstFirstChild();
            importSpec != null;
            importSpec = importSpec.getNext()) {
          checkState(importSpec.hasChildren(), importSpec);
          String importedProperty = importSpec.getString();
          Node aliasNode = importSpec.getFirstChild();
          String aliasName = aliasNode.getString();
          String fullName = exportedNamespace + "." + importedProperty;
          recordNameToInline(aliasName, fullName, /* namespaceId= */ null);

          // Record alias before we rename node.
          maybeAddAliasToSymbolTable(aliasNode, currentScript.namespaceId);
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
        checkState(
            isTopLevel(t, statementNode, ScopeType.EXEC_CONTEXT),
            "Unexpected non-top-level require at %s",
            call);
        if (importHasAlias || !rewriteState.isLegacyModule(namespaceId)) {
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
    if (importedModule.defaultExport != null) {
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
    // namespaceId import categorization, goog.forwardDeclare is much the same as goog.require.
    updateGoogRequire(t, call);
  }

  private void updateGoogRequireDynamicCall(Node call) {
    final Node parent = call.getParent();
    if (parent == null) {
      // This call has been detached from the AST in unrecognized require handling. Nothing else to
      // do here.
      return;
    }
    checkState(
        parent.isAwait() || (parent.isGetProp() && parent.getString().equals("then")),
        "goog.requireDynamic() in only allowed in await/then expression");

    if (parent.isAwait()) {
      updateGoogRequireDynamicCallInAwait(call);
    } else {
      updateGoogRequireDynamicCallInThen(call);
    }
  }

  // Rewrite
  //   goog.requireDynamic('a.b.c').then( ({Foo}) => { new Foo().render(); });
  //    to
  //   goog.importHandler_('h4sh').then(() => {
  //        const {Foo} = module$exports$a$b$c;
  //        new Foo().render();
  //   });
  // Note that rewriting works for both destructuring pattern and name, i.e., `{Foo}` or `foo`.
  private void updateGoogRequireDynamicCallInThen(Node call) {
    Node namespaceIdNode = call.getSecondChild();
    String namespaceId = namespaceIdNode.getString();
    String exportedNamespace = rewriteState.getExportedNamespaceOrScript(namespaceId);
    checkState(
        exportedNamespace != null, "Exported namespace for goog.requireDynamic() canot be null");

    // `goog.requireDynamic('a.b.c').then()`
    final Node getProp = call.getParent();
    final Node thenCallNode = getProp.getParent();
    checkState(thenCallNode != null && thenCallNode.isCall(), "must be a 'then' call expression");

    // Create a string literal containing the ID of the chunk containing the module we want
    Node argNode =
        this.astFactory.createString(namespaceIdToXid(namespaceId)).srcref(namespaceIdNode);

    // `goog.requireDynamic('a.b.c')` -> `goog.requireDynamic('chunkId')`
    namespaceIdNode.replaceWith(argNode);

    // `goog.requireDynamic` -> `goog.importHandler_`
    Node calleeNode = call.getFirstChild();
    checkState(calleeNode.matchesQualifiedName(GOOG_REQUIREDYNAMIC_NAME), calleeNode);
    calleeNode.setString(IMPORT_HANDLER_NAME);

    // `module$exports$a$b$c`
    Node exportedNamespaceNameNode =
        this.astFactory.createName(this.globalTypedScope, exportedNamespace).srcrefTree(call);
    exportedNamespaceNameNode.setOriginalName(namespaceId);

    // Get `{Foo}` from `goog.requireDynamic().then(   ({Foo}) => {  }   )`;
    Node functionNode = thenCallNode.getSecondChild();
    checkState(functionNode != null && functionNode.isFunction(), "must be a function in `then`");

    // Get param list of the callback function
    Node paramListNode = functionNode.getSecondChild();
    checkState(paramListNode.getChildCount() == 1, "function must have only one parameter");

    // Get the callback function body
    Node functionBody = paramListNode.getNext();

    // Get parameter, object pattern or name
    Node objectPatternOrNameNode = paramListNode.getOnlyChild();
    checkState(
        objectPatternOrNameNode.isObjectPattern() || objectPatternOrNameNode.isName(),
        "parameter of callback function must be object pattern or name");
    objectPatternOrNameNode.detach();

    // `({Foo}) => {}` -> `() => {}`
    paramListNode.removeChildren();

    // Dynamic require is not allowed for JS that is too old to have `const` (< ES6),
    // We don't expect to ever see `--language_in=ES5` in combination with dynamic require.
    final LanguageMode languageIn = compiler.getOptions().getLanguageIn();
    checkState(
        languageIn.toFeatureSet().contains(Feature.CONST_DECLARATIONS),
        "'%s' does not contain '%s'",
        languageIn,
        Feature.CONST_DECLARATIONS);

    Node enclosingScript = NodeUtil.getEnclosingScript(call);
    NodeUtil.addFeatureToScript(enclosingScript, Feature.CONST_DECLARATIONS, compiler);

    // Create `const {Foo} = module$exports$a$b$c;`
    // Or `const foo = module$exports$a$b$c;'
    Node declarationNode;
    if (objectPatternOrNameNode.isObjectPattern()) {
      declarationNode =
          this.astFactory
              .createSingleConstObjectPatternDeclaration(
                  objectPatternOrNameNode, exportedNamespaceNameNode)
              .srcrefTreeIfMissing(call);
    } else {
      declarationNode =
          this.astFactory
              .createSingleConstNameDeclaration(
                  objectPatternOrNameNode.getString(), exportedNamespaceNameNode)
              .srcrefTreeIfMissing(call);
    }

    // If right hand side of the callback arrow function is an expression instead of BLOCK,
    // e.g., `({Foo}) => Foo` instead of `() => { return Foo; }`, create a BLOCK to host the
    // expression.
    if (functionBody.isBlock()) {
      functionBody.addChildToFront(declarationNode);
      compiler.reportChangeToEnclosingScope(declarationNode);
    } else {
      Node returnStmt = this.astFactory.createReturn(functionBody.detach());
      Node newBlock = this.astFactory.createBlock(returnStmt).srcrefTree(call);
      newBlock.insertAfter(paramListNode);
      newBlock.addChildToFront(declarationNode);
      compiler.reportChangeToEnclosingScope(newBlock);
    }

    compiler.reportChangeToEnclosingScope(call);
  }

  // Rewrite
  //   const {Foo} = await goog.requireDynamic('a.b.c')`
  //    to
  //   await goog.importHandler_('tJJovc');
  //   const {Foo} = module$exports$a$b$c;
  private void updateGoogRequireDynamicCallInAwait(Node call) {
    Node namespaceIdNode = call.getSecondChild();
    String namespaceId = namespaceIdNode.getString();
    String exportedNamespace = rewriteState.getExportedNamespaceOrScript(namespaceId);
    checkState(
        exportedNamespace != null, "Exported namespace for goog.requireDynamic() cannot be null");

    // `await goog.requireDynamic('a.b.c')`
    final Node existingAwait = call.getParent();
    checkState(
        existingAwait.isAwait(), "Only goog.requireDynamic() in await expression is supported now");

    // `{Foo} = await goog.requireDynamic('a.b.c')`
    Node awaitParent = existingAwait.getParent();
    if (awaitParent == null || (!awaitParent.isDestructuringLhs() && !awaitParent.isName())) {
      compiler.report(JSError.make(call, ILLEGAL_STMT_OF_GOOG_REQUIRE_DYNAMIC_IN_AWAIT));
    }

    // `const {Foo} = await goog.requireDynamic('a.b.c')`
    Node declarationStatement = awaitParent.getParent();
    // Reject non-declarations or declarations in a for loop.
    if (!NodeUtil.isNameDeclaration(declarationStatement)
        || !NodeUtil.isStatement(declarationStatement)) {
      compiler.report(JSError.make(call, ILLEGAL_STMT_OF_GOOG_REQUIRE_DYNAMIC_IN_AWAIT));
    }

    // `module$exports$a$b$c`
    Node exportedNamespaceNameNode =
        this.astFactory.createQName(this.globalTypedScope, exportedNamespace).srcrefTree(call);
    exportedNamespaceNameNode.setJSType(rewriteState.getGoogModuleNamespaceType(namespaceId));
    exportedNamespaceNameNode.setOriginalName(namespaceId);

    // Create a string literal containing the ID of the chunk containing the module we want
    Node argNode =
        this.astFactory.createString(namespaceIdToXid(namespaceId)).srcref(namespaceIdNode);

    // `goog.requireDynamic('a.b.c')` -> `goog.requireDynamic('chunkId')`
    namespaceIdNode.replaceWith(argNode);

    // `goog.requireDynamic` -> `goog.importHandler_`
    Node calleeNode = call.getFirstChild();
    checkState(calleeNode.matchesQualifiedName(GOOG_REQUIREDYNAMIC_NAME), calleeNode);
    calleeNode.setString(IMPORT_HANDLER_NAME);

    // Replace the await with the module object
    // `const {Foo} = module$exports$a$b$c;`
    existingAwait.replaceWith(exportedNamespaceNameNode);

    // Insert the await as a statement before the declaration
    final Node awaitStatement = astFactory.exprResult(existingAwait).srcref(existingAwait);
    awaitStatement.insertBefore(declarationStatement);
    compiler.reportChangeToEnclosingScope(call);
  }

  private String namespaceIdToXid(String namespaceId) {
    Xid.HashFunction hashFunction = this.compiler.getOptions().getChunkIdHashFunction();
    Xid xid = hashFunction == null ? new Xid() : new Xid(hashFunction);
    return xid.get(namespaceId);
  }

  private void updateGoogModuleGetCall(Node call) {
    Node namespaceIdNode = call.getSecondChild();
    String namespaceId = namespaceIdNode.getString();

    // Remaining calls to goog.module.get() are not alias updates,
    // and should be replaced by a reference to the proper name.
    // Replace "goog.module.get('pkg.Foo')" with either "pkg.Foo" or "module$exports$pkg$Foo".
    String exportedNamespace = rewriteState.getExportedNamespaceOrScript(namespaceId);
    if (exportedNamespace != null) {
      compiler.reportChangeToEnclosingScope(call);
      Node exportedNamespaceName =
          this.astFactory
              .createQNameUsingJSTypeInfo(this.globalTypedScope, exportedNamespace)
              .srcrefTree(call);
      exportedNamespaceName.setJSType(rewriteState.getGoogModuleNamespaceType(namespaceId));
      exportedNamespaceName.setOriginalName(namespaceId);
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
      String exportName = getpropNode.getString();
      currentScript.namedExports.add(exportName);
      Node exportRhs = getpropNode.getNext();
      ExportDefinition namedExport = ExportDefinition.newNamedExport(t, exportName, exportRhs);
      if (!currentScript.declareLegacyNamespace
          && currentScript.defaultExport == null
          && namedExport.hasInlinableName(currentScript.exportsToInline.keySet())) {
        recordExportToInline(namedExport);
        parent.getParent().detach();
      }
    }
  }

  private void updateExportsPropertyAssignment(Node getpropNode, NodeTraversal t) {
    if (!currentScript.isModule) {
      return;
    }

    Node parent = getpropNode.getParent();
    checkState(parent.isAssign() || parent.isExprResult(), parent);

    // Update "exports.foo = Foo" to "module$exports$pkg$Foo.foo = Foo";
    Node exportsNameNode = getpropNode.getFirstChild();
    checkState(exportsNameNode.getString().equals("exports"));
    String exportedNamespace = currentScript.getExportedNamespace();
    safeSetMaybeQualifiedString(exportsNameNode, exportedNamespace, /* isModuleNamespace= */ false);

    Node jsdocNode = parent.isAssign() ? parent : getpropNode;
    markConstAndCopyJsDoc(jsdocNode, jsdocNode);

    // When seeing the first "exports.foo = ..." line put a "var module$exports$pkg$Foo = {};"
    // before it.
    if (!currentScript.hasCreatedExportObject) {
      exportTheEmptyBinaryNamespaceAt(NodeUtil.getEnclosingStatement(parent), AddAt.BEFORE, t);
    }
  }

  /**
   * Rewrites top level var names from "var foo; console.log(foo);" to "var module$contents$Foo_foo;
   * console.log(module$contents$Foo_foo);"
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
      if (NodeUtil.isCallTo(destructuringLhsNode.getLastChild(), GOOG_REQUIRE)
          || NodeUtil.isCallTo(destructuringLhsNode.getLastChild(), GOOG_REQUIRETYPE)) {
        return;
      }
    }

    // If the name is an alias for an imported namespace or an exported local, rewrite from
    // "new Foo;" to "new module$exports$Foo;" or "new Foo" to "new module$contents$bar$Foo".
    boolean nameIsAnAlias = currentScript.namesToInlineByAlias.containsKey(name);
    if (nameIsAnAlias && var.getNode() != nameNode) {
      maybeAddAliasToSymbolTable(nameNode, currentScript.namespaceId);

      AliasName inline = currentScript.namesToInlineByAlias.get(name);
      String namespaceToInline = inline.newName;
      if (namespaceToInline.equals(currentScript.getBinaryNamespace())) {
        currentScript.hasCreatedExportObject = true;
      }
      boolean isModuleNamespace =
          inline.namespaceId != null
              && rewriteState.scriptDescriptionsByGoogModuleNamespace.containsKey(
                  inline.namespaceId)
              && !rewriteState.scriptDescriptionsByGoogModuleNamespace.get(inline.namespaceId)
                  .willCreateExportsObject;
      safeSetMaybeQualifiedString(nameNode, namespaceToInline, isModuleNamespace);

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
   * For exports like "exports = {prop: value}" update the declarations to enforce &#64;const ness
   * (and typedef exports).
   *
   * <p>TODO(blickly): Remove as much of this functionality as possible, now that these style of
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
            JSDocInfo.Builder builder = JSDocInfo.Builder.copyFrom(info);
            target.setJSDocInfo(builder.build());
            return;
          }
        }
      }
    }

    markConstAndCopyJsDoc(target, target);
  }

  /** In module "foo.Bar", rewrite "exports = Bar" to "var module$exports$foo$Bar = Bar". */
  private void maybeUpdateExportDeclaration(NodeTraversal t, Node n) {
    if (!currentScript.isModule || !isGoogModuleExportsRef(t, n) || !isAssignTarget(n)) {
      return;
    }

    Node assignNode = n.getParent();
    Node rhs = assignNode.getLastChild();
    if (rhs != currentScript.defaultExport.rhs) {
      // This script has duplicate 'exports = ' assignments. Preserve the rhs as an expression but
      // don't declare it as a global variable.
      assignNode.replaceWith(rhs.detach());
      return;
    }

    if (!currentScript.declareLegacyNamespace && currentScript.defaultExportLocalName != null) {
      assignNode.getParent().detach();

      Node binaryNamespaceName = astFactory.createName(currentScript.getBinaryNamespace(), type(n));
      this.declareGlobalVariable(binaryNamespaceName, t);
      return;
    }

    // Rewrite "exports = ..." as "var module$exports$foo$Bar = ..."
    Node jsdocNode;
    if (currentScript.declareLegacyNamespace) {
      Node legacyQname =
          this.astFactory
              .createQNameUsingJSTypeInfo(this.globalTypedScope, currentScript.namespaceId)
              .srcrefTree(n);
      legacyQname.setJSType(n.getJSType());
      n.replaceWith(legacyQname);
      jsdocNode = assignNode;
    } else {
      rhs.detach();
      Node exprResultNode = assignNode.getParent();
      Node binaryNamespaceName = astFactory.createName(currentScript.getBinaryNamespace(), type(n));
      binaryNamespaceName.setOriginalName("exports");
      this.declareGlobalVariable(binaryNamespaceName, t);

      Node exportsObjectCreationNode = IR.var(binaryNamespaceName, rhs);
      exportsObjectCreationNode.srcrefTreeIfMissing(exprResultNode);
      exportsObjectCreationNode.putBooleanProp(Node.IS_NAMESPACE, true);
      exprResultNode.replaceWith(exportsObjectCreationNode);
      jsdocNode = exportsObjectCreationNode;
      currentScript.hasCreatedExportObject = true;
    }
    markConstAndCopyJsDoc(assignNode, jsdocNode);
    compiler.reportChangeToEnclosingScope(jsdocNode);

    maybeUpdateExportObjectLiteral(t, rhs);
  }

  private void maybeUpdateExportNameRef(NodeTraversal t, Node n) {
    if (!currentScript.isModule || !isGoogModuleExportsRef(t, n) || n.getParent() == null) {
      return;
    }
    if (n.getParent().isParamList()) {
      return;
    }

    if (currentScript.declareLegacyNamespace) {
      Node legacyQname =
          this.astFactory
              .createQName(this.globalTypedScope, currentScript.namespaceId)
              .srcrefTree(n);
      legacyQname.setJSType(n.getJSType());
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

    for (ExportDefinition export : currentScript.exportsToInline.values()) {
      Node nameNode = export.nameDecl.getNameNode();
      safeSetMaybeQualifiedString(
          nameNode, currentScript.getBinaryNamespace() + export.getExportPostfix(), false);
    }
    checkState(currentScript.isModule, currentScript);
    checkState(
        currentScript.declareLegacyNamespace || currentScript.hasCreatedExportObject,
        currentScript);

    popScript();
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

  /** Add the missing "var module$exports$pkg$Foo = {};" line. */
  private void exportTheEmptyBinaryNamespaceAt(Node atNode, AddAt addAt, NodeTraversal t) {
    if (currentScript.declareLegacyNamespace) {
      return;
    }

    String binaryNamespaceString = currentScript.getBinaryNamespace();
    AstFactory.Type moduleType = type(currentScript.rootNode);
    Node binaryNamespaceName = astFactory.createName(binaryNamespaceString, moduleType);
    binaryNamespaceName.setOriginalName(currentScript.namespaceId);
    this.declareGlobalVariable(binaryNamespaceName, t);

    Node binaryNamespaceExportNode = IR.var(binaryNamespaceName, astFactory.createObjectLit());
    if (addAt == AddAt.BEFORE) {
      binaryNamespaceExportNode.insertBefore(atNode);
    } else if (addAt == AddAt.AFTER) {
      binaryNamespaceExportNode.insertAfter(atNode);
    }
    binaryNamespaceExportNode.putBooleanProp(Node.IS_NAMESPACE, true);
    binaryNamespaceExportNode.srcrefTree(atNode);
    markConst(binaryNamespaceExportNode);
    compiler.reportChangeToEnclosingScope(binaryNamespaceExportNode);
    currentScript.hasCreatedExportObject = true;
  }

  static void checkAndSetStrictModeDirective(NodeTraversal t, Node n) {
    checkState(n.isScript(), n);

    if (n.isUseStrict()) {
      t.report(n, USELESS_USE_STRICT_DIRECTIVE);
    } else {
      n.setUseStrict(true);
    }
  }

  private static void markConst(Node n) {
    JSDocInfo.Builder builder = JSDocInfo.Builder.maybeCopyFrom(n.getJSDocInfo());
    builder.recordConstancy();
    n.setJSDocInfo(builder.build());
  }

  private static void maybeSplitMultiVar(Node rhsNode) {
    Node statementNode = rhsNode.getGrandparent();
    if (!statementNode.isVar() || !statementNode.hasMoreThanOneChild()) {
      return;
    }

    Node nameNode = rhsNode.getParent();
    nameNode.detach();
    rhsNode.detach();
    IR.var(nameNode, rhsNode).insertBefore(statementNode);
  }

  private static void markConstAndCopyJsDoc(Node from, Node target) {
    JSDocInfo info = from.getJSDocInfo();
    JSDocInfo.Builder builder = JSDocInfo.Builder.maybeCopyFrom(info);
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
        "Already found a mapping for inlining export: %s",
        exportDefinition.nameDecl);
    String localName = exportDefinition.getLocalName();
    String fullExportedName =
        currentScript.getBinaryNamespace() + exportDefinition.getExportPostfix();
    recordNameToInline(localName, fullExportedName, /* namespaceId= */ null);
  }

  private void recordNameToInline(String aliasName, String newName, @Nullable String namespaceId) {
    checkNotNull(aliasName);
    checkNotNull(newName);
    // This intentionally overwrites a possibly pre-existing alias of the same name.
    // User code might import the same name twice, with the same variable name. That's an error
    // (duplicate variable definition, reported in TypeValidator), but this code still shouldn't
    // crash on it.
    currentScript.namesToInlineByAlias.put(aliasName, new AliasName(newName, namespaceId));
  }

  /**
   * Examines queue'ed unrecognizedRequires to categorize and report them as either missing module,
   * missing namespace or late provide.
   */
  private void reportUnrecognizedRequires() {
    for (UnrecognizedRequire unrecognizedRequire : unrecognizedRequires) {
      String namespaceId = unrecognizedRequire.namespaceId;

      Node requireNode = unrecognizedRequire.requireNode;
      boolean targetGoogModuleExists = rewriteState.containsModule(namespaceId);
      boolean targetLegacyScriptExists = rewriteState.providedNamespaces.contains(namespaceId);

      if (targetGoogModuleExists || targetLegacyScriptExists) {
        // The required thing actually was available somewhere in the program but just wasn't
        // available as early as the require statement would have liked.
        continue;
      }

      // Remove the require node so this problem isn't reported again in ProcessClosurePrimitives.
      if (preserveSugar) {
        continue;
      }

      if (NodeUtil.getEnclosingScript(requireNode) == null) {
        continue; // It's already been removed; nothing to do.
      }

      compiler.reportChangeToEnclosingScope(requireNode);
      Node enclosingStatement = NodeUtil.getEnclosingStatement(requireNode);

      // To make compilation with partial source information work for Clutz, delete any name
      // declarations in the enclosing statement completely. For non-declarations, simply replace
      // the invalid require with null.
      if (!NodeUtil.isNameDeclaration(enclosingStatement)) {
        requireNode.replaceWith(astFactory.createNull().srcref(requireNode));
        continue;
      }

      enclosingStatement.detach();
      NodeUtil.visitLhsNodesInNode(
          enclosingStatement, (lhs) -> syntheticExterns.putIfAbsent(lhs.getString(), lhs));
    }
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
    Node changeScope = ChangeTracker.getEnclosingChangeScopeRoot(n);
    if (changeScope != null) {
      compiler.reportChangeToChangeScope(changeScope);
    }
  }

  /** Replaces an identifier with a potentially qualified name */
  private void safeSetMaybeQualifiedString(
      Node nameNode, String newString, boolean isModuleNamespace) {
    if (!newString.contains(".")) {
      safeSetString(nameNode, newString);
      Node parent = nameNode.getParent();
      if (isModuleNamespace
          && parent.isGetProp()
          && nameNode.getGrandparent().isCall()
          && parent.isFirstChildOf(nameNode.getGrandparent())) {
        // In cases where we're calling a function off a module namespace, don't pass the module
        // namespace as `this`.
        nameNode.getGrandparent().putBooleanProp(Node.FREE_CALL, true);
      }
      return;
    }

    // When replacing with a dotted fully qualified name it's already better than an original
    // name.
    Node nameParent = nameNode.getParent();
    Node newQualifiedName =
        this.astFactory
            .createQNameUsingJSTypeInfo(this.globalTypedScope, newString)
            .srcrefTree(nameNode);
    // Sometimes the typechecker gave `nameNode` the correct type, but we can't infer the right type
    // for `newQualifiedName`. If so, giving `newQualifiedName` the same type typechecking used for
    // `nameNode` is less confusing.
    newQualifiedName.setJSType(nameNode.getJSType());

    boolean replaced = safeSetStringIfDeclaration(nameParent, nameNode, newQualifiedName);
    if (replaced) {
      return;
    }

    nameNode.replaceWith(newQualifiedName);
    // Given import "var Bar = goog.require('foo.Bar');" here we replace a usage of Bar with
    // foo.Bar if Bar is goog.provided. 'foo' node is generated and never visible to user.
    // Because of that we should mark all such nodes as non-indexable leaving only Bar indexable.
    // Given that replacement is GETPROP node, prefix is first child. It's also possible that
    // replacement is single-part namespace. Like goog.provide('Bar') in that case replacement
    // won't have children.
    if (newQualifiedName.hasChildren()) {
      newQualifiedName.getFirstChild().makeNonIndexableRecursive();
    }
    compiler.reportChangeToEnclosingScope(newQualifiedName);
  }

  /** Sets the string if given a declaration, return whether or not the name was changed */
  private static boolean safeSetStringIfDeclaration(
      Node nameParent, Node nameNode, Node newQualifiedName) {

    JSDocInfo jsdoc = nameParent.getJSDocInfo();

    switch (nameParent.getToken()) {
      case FUNCTION, CLASS -> {
        if (!NodeUtil.isStatement(nameParent) || nameParent.getFirstChild() != nameNode) {
          return false;
        }

        Node placeholder = IR.empty();
        nameParent.replaceWith(placeholder);
        Node newDeclaration =
            NodeUtil.getDeclarationFromName(newQualifiedName, nameParent, Token.VAR, jsdoc);
        if (NodeUtil.isExprAssign(newDeclaration)) {
          Node assign = newDeclaration.getOnlyChild();
          assign.setJSType(nameNode.getJSType());
          updateSourceInfoForExportedTopLevelVariable(assign, nameNode);
        }
        nameParent.setJSDocInfo(null);
        newDeclaration.srcrefTreeIfMissing(nameParent);
        placeholder.replaceWith(newDeclaration);
        NodeUtil.removeName(nameParent);
        return true;
      }
      case VAR, LET, CONST -> {
        Node rhs = nameNode.hasChildren() ? nameNode.getLastChild().detach() : null;
        if (jsdoc == null) {
          // Get inline JSDocInfo if there is no JSDoc on the actual declaration.
          jsdoc = nameNode.getJSDocInfo();
        }
        Node newStatement =
            NodeUtil.getDeclarationFromName(newQualifiedName, rhs, Token.VAR, jsdoc);
        if (NodeUtil.isExprAssign(newStatement)) {
          Node assign = newStatement.getOnlyChild();
          assign.setJSType(nameNode.getJSType());
          updateSourceInfoForExportedTopLevelVariable(assign, nameNode);
          if (nameParent.isConst()) {
            // When replacing `const name = ...;` with `some.prop = ...`, ensure that `some.prop`
            // is annotated @const.
            JSDocInfo.Builder jsdocBuilder = JSDocInfo.Builder.maybeCopyFrom(jsdoc);
            jsdocBuilder.recordConstancy();
            jsdoc = jsdocBuilder.build();
            assign.setJSDocInfo(jsdoc);
          }
        }
        newStatement.srcrefTreeIfMissing(nameParent);
        NodeUtil.replaceDeclarationChild(nameNode, newStatement);
        return true;
      }
      case OBJECT_PATTERN, ARRAY_PATTERN, PARAM_LIST -> throw new RuntimeException("Not supported");
      default -> {}
    }
    return false;
  }

  /**
   * If we had something like `const FOO = "text"` and we export `FOO`, change the source location
   * information for the rewritten FOO. The replacement should be something like MOD.FOO = "text",
   * so we look for MOD.FOO and replace the source location for FOO to the original location of FOO.
   */
  private static void updateSourceInfoForExportedTopLevelVariable(Node assign, Node sourceName) {
    checkState(assign.isAssign());
    checkState(sourceName.isName());

    // ASSIGN always has two children.
    Node getProp = assign.getFirstChild();
    if (!getProp.isGetProp()) {
      return;
    }

    String name = sourceName.getOriginalName();
    if (name == null) {
      name = sourceName.getString();
    }

    // The source range of this NAME includes its declared value, which we don't want.
    sourceName = sourceName.cloneNode();
    sourceName.setLength(name.length());

    // Receiver and prop string should both use the position of the source name.
    getProp.srcrefTree(sourceName);
  }

  private boolean isTopLevel(NodeTraversal t, Node n, ScopeType scopeType) {
    if (scopeType == ScopeType.EXEC_CONTEXT) {
      return t.inGlobalScope() || t.getClosestHoistScopeRoot() == currentScript.rootNode;
    } else {
      // Must be ScopeType.BLOCK;
      return n.getParent() == currentScript.rootNode;
    }
  }

  private static String toModuleContentsPrefix(String namespaceId) {
    return MODULE_CONTENTS_PREFIX + namespaceId.replace('.', '$') + "_";
  }

  public static boolean isModuleExport(String name) {
    return name.startsWith(MODULE_EXPORTS_PREFIX);
  }

  public static boolean isModuleContent(String name) {
    return name.startsWith(MODULE_CONTENTS_PREFIX);
  }

  /**
   * Returns whether this is a) a reference to the name "exports" and b) based on scoping, actually
   * refers to the implicit goog.module exports object.
   */
  private static boolean isGoogModuleExportsRef(NodeTraversal t, Node target) {
    if (!target.isName() || !target.matchesName("exports")) {
      return false;
    }
    Var exportsVar = t.getScope().getVar("exports");
    // note: exportsVar may be null for third-party code using UMD patterns like
    //   if ("object"==typeof exports) { [...]
    // and if null, is not in a goog.module => not a goog.module exports var
    return exportsVar != null && exportsVar.isGoogModuleExports();
  }

  /**
   * @return Whether the getprop is used as an assignment target, and that target represents a
   *     module export. Note: that "export.name = value" is an export, while "export.name.foo =
   *     value" is not (it is an assignment to a property of an exported value).
   */
  private static boolean isExportPropertyAssignment(NodeTraversal t, Node n) {
    Node target = n.getFirstChild();
    return (isAssignTarget(n) || isTypedefTarget(n)) && isGoogModuleExportsRef(t, target);
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
   * Add alias nodes to the symbol table as they going to be removed by rewriter. Example aliases:
   *
   * <p>const Foo = goog.require('my.project.Foo'); const bar = goog.require('my.project.baz');
   * const {baz} = goog.require('my.project.utils');
   */
  private void maybeAddAliasToSymbolTable(Node n, String module) {
    if (preprocessorSymbolTable != null) {
      n.putBooleanProp(Node.MODULE_ALIAS, true);
      // Alias can be used in js types. Types have node type STRING and not NAME so we have to
      // use their name as string.
      String nodeName =
          n.isStringLit() ? n.getString() : preprocessorSymbolTable.getQualifiedName(n);
      // We need to include module as part of the name because aliases are local to current module.
      // Aliases with the same name from different module should be completely different entities.
      String name = "alias_" + module + "_" + nodeName;
      preprocessorSymbolTable.addReference(n, name);
    }
  }

  private void declareGlobalVariable(Node n, NodeTraversal t) {
    checkState(n.isName());
    if (this.globalTypedScope == null) {
      return;
    }

    String name = n.getString();
    if (this.globalTypedScope.hasOwnSlot(name)) {
      Node original = globalTypedScope.getOwnSlot(name).getNode();
      t.report(
          t.getCurrentScript(),
          ILLEGAL_MODULE_RENAMING_CONFLICT,
          name,
          original != null ? original.toString() : "<unknown>");
    } else {
      JSType type = checkNotNull(n.getJSType());
      this.globalTypedScope.declare(name, n, type, t.getInput(), false);
    }
  }
}
