/*
 * Copyright 2018 The Closure Compiler Authors.
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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.ClosureCheckModule.INCORRECT_SHORTNAME_CAPITALIZATION;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_SCOPE_ERROR;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_GET_CALL_SCOPE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MODULE_USES_GOOG_MODULE_GET;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_GET_ALIAS;

import com.google.javascript.jscomp.NodeTraversal.AbstractModuleCallback;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Checks all goog.requires, goog.module.gets, goog.forwardDeclares, and goog.requireTypes in all
 * files.
 *
 * <p>Checks that these dependency calls both contain a valid Closure namespace and are in an
 * acceptable location (e.g. a goog.require cannot be within a function).
 */
final class CheckClosureImports implements CompilerPass {

  private enum ClosureImport {
    REQUIRE {
      @Override
      boolean allowDestructuring() {
        return true;
      }

      @Override
      boolean aliasMustBeConstant() {
        return true;
      }

      @Override
      boolean mustBeOrdered() {
        return true;
      }

      @Override
      String callName() {
        return "goog.require";
      }
    },

    FORWARD_DECLARE {
      @Override
      boolean allowDestructuring() {
        return false;
      }

      @Override
      boolean aliasMustBeConstant() {
        return false;
      }

      @Override
      boolean mustBeOrdered() {
        return false;
      }

      @Override
      String callName() {
        return "goog.forwardDeclare";
      }
    },

    REQUIRE_TYPE {
      @Override
      boolean allowDestructuring() {
        return true;
      }

      @Override
      boolean aliasMustBeConstant() {
        return true;
      }

      @Override
      boolean mustBeOrdered() {
        return false;
      }

      @Override
      String callName() {
        return "goog.requireType";
      }
    },

    REQUIRE_DYNAMIC {
      @Override
      boolean allowDestructuring() {
        return true;
      }

      @Override
      boolean aliasMustBeConstant() {
        return true;
      }

      @Override
      boolean mustBeOrdered() {
        return false;
      }

      @Override
      String callName() {
        return "goog.requireDynamic";
      }
    };

    /**
     * Whether or not a destructuring alias is allowed, e.g. {@code const {Class} =
     * goog.require('namespace');}.
     */
    abstract boolean allowDestructuring();

    /**
     * True if the alias of the call must be constant, false if it can be {@code let}.
     *
     * <p>Note: since not every goog.module is using ES6, {@code const} is only checked in ES6
     * modules. {@code let} will always be disallowed if this is false. But {@code var} will be
     * allowed in goog.module files even if this returns true (since it assumes the file is
     * non-ES6).
     */
    abstract boolean aliasMustBeConstant();

    /**
     * True if the providing symbol must appear before this call in the program.
     *
     * <p>A {@code goog.require} for something can only appear after it is defined (e.g. {@code
     * goog.provide}, {@code goog.module}, or {@code goog.declareModuleId} call). But the same is
     * not true for {@code goog.requireType} and {@code goog.forwardDeclare}, which can appear
     * before the definition.
     */
    abstract boolean mustBeOrdered();

    /** Human readable name of this call, used in error reporting. */
    abstract String callName();
  }

  static final DiagnosticType INVALID_CLOSURE_IMPORT_DESTRUCTURING =
      DiagnosticType.error(
          "JSC_INVALID_CLOSURE_IMPORT_DESTRUCTURING",
          "Destructuring {0} must be a simple object pattern.");

  static final DiagnosticType ONE_CLOSURE_IMPORT_PER_DECLARATION =
      DiagnosticType.error(
          "JSC_ONE_CLOSURE_IMPORT_PER_DECLARATION",
          "There may only be one {0} per var/let/const declaration.");

  static final DiagnosticType INVALID_CLOSURE_IMPORT_CALL =
      DiagnosticType.error(
          "JSC_INVALID_CLOSURE_IMPORT_CALL", "{0} parameter must be a string literal.");

  static final DiagnosticType LATE_PROVIDE_ERROR =
      DiagnosticType.error(
          "JSC_LATE_PROVIDE_ERROR", "Required namespace \"{0}\" not provided yet.");

  static final DiagnosticType LET_CLOSURE_IMPORT =
      DiagnosticType.disabled(
          "JSC_LET_CLOSURE_IMPORT",
          "Module imports must be constant. Please use ''const'' instead of ''let''.");

  static final DiagnosticType NO_CLOSURE_IMPORT_DESTRUCTURING =
      DiagnosticType.error(
          "JSC_NO_CLOSURE_IMPORT_DESTRUCTURING", "Cannot destructure the return value of {0}");

  static final DiagnosticType LHS_OF_CLOSURE_IMPORT_MUST_BE_CONST_IN_ES_MODULE =
      DiagnosticType.error(
          "JSC_LHS_OF_CLOUSRE_IMPORT_MUST_BE_CONST_IN_ES_MODULE",
          "The left side of a {0} must use ''const'' (not ''let'' or ''var'') in an ES module.");

  static final DiagnosticType CROSS_CHUNK_REQUIRE_ERROR =
      DiagnosticType.warning(
          "JSC_XMODULE_REQUIRE_ERROR",
          "namespace \"{0}\" is required in chunk {2} but provided in chunk {1}."
              + " Is chunk {2} missing a dependency on chunk {1}?");

  private static final Node GOOG_REQUIRE = IR.getprop(IR.name("goog"), "require");
  private static final Node GOOG_MODULE_GET = IR.getprop(IR.name("goog"), "module", "get");
  private static final Node GOOG_FORWARD_DECLARE = IR.getprop(IR.name("goog"), "forwardDeclare");
  private static final Node GOOG_REQUIRE_TYPE = IR.getprop(IR.name("goog"), "requireType");
  private static final Node GOOG_REQUIRE_DYNAMIC = IR.getprop(IR.name("goog"), "requireDynamic");

  private final AbstractCompiler compiler;
  private final Checker checker;
  private final Set<String> namespacesSeen;
  private final JSChunkGraph chunkGraph;

  CheckClosureImports(AbstractCompiler compiler, ModuleMetadataMap moduleMetadataMap) {
    this.compiler = compiler;
    this.checker = new Checker(compiler, moduleMetadataMap);
    this.namespacesSeen = new LinkedHashSet<>();
    this.chunkGraph = compiler.getChunkGraph();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, checker);
    NodeTraversal.traverse(compiler, root, checker);
  }

  private class Checker extends AbstractModuleCallback {
    private final ModuleMetadataMap moduleMetadataMap;

    Checker(AbstractCompiler compiler, ModuleMetadataMap moduleMetadataMap) {
      super(compiler, moduleMetadataMap);
      this.moduleMetadataMap = moduleMetadataMap;
    }

    @Override
    protected void enterModule(ModuleMetadata currentModule, Node moduleScopeRoot) {
      namespacesSeen.addAll(currentModule.googNamespaces().elementSet());
    }

    @Override
    public void visit(
        NodeTraversal t,
        Node n,
        @Nullable ModuleMetadata currentModule,
        @Nullable Node moduleScopeRoot) {
      // currentModule is null on ROOT nodes.
      if (currentModule == null) {
        return;
      }

      Node parent = n.getParent();

      if (n.isCall()) {
        ClosureImport kindOfCall = kindOfCall(n);
        if (kindOfCall != null) {
          checkImport(t, n, parent, currentModule, kindOfCall);
        } else if (n.getFirstChild().matchesQualifiedName(GOOG_MODULE_GET)) {
          checkGoogModuleGet(t, n, currentModule);
        }
      } else if (n.isName()) {
        checkValidImportCodeReference(t, n);
      }
    }

    /**
     * Given a call node determines what kind of {@link ClosureImport} it is. Returns null if not a
     * {@link ClosureImport}.
     */
    private @Nullable ClosureImport kindOfCall(Node callNode) {
      checkState(callNode.isCall());
      if (callNode.getFirstChild().matchesQualifiedName(GOOG_REQUIRE)) {
        return ClosureImport.REQUIRE;
      } else if (callNode.getFirstChild().matchesQualifiedName(GOOG_FORWARD_DECLARE)) {
        return ClosureImport.FORWARD_DECLARE;
      } else if (callNode.getFirstChild().matchesQualifiedName(GOOG_REQUIRE_TYPE)) {
        return ClosureImport.REQUIRE_TYPE;
      } else if (callNode.getFirstChild().matchesQualifiedName(GOOG_REQUIRE_DYNAMIC)) {
        return ClosureImport.REQUIRE_DYNAMIC;
      }
      return null;
    }

    private void checkValidImportCodeReference(NodeTraversal t, Node nameNode) {
      Var var = t.getScope().getVar(nameNode.getString());

      if (var == null || var.getNameNode() == nameNode) {
        return;
      }

      Node declarationNameNode = var.getNameNode();
      if (declarationNameNode == null || !NodeUtil.isDeclarationLValue(declarationNameNode)) {
        return;
      }

      Node decl =
          declarationNameNode.getParent().isStringKey()
              ? declarationNameNode.getGrandparent().getGrandparent()
              : declarationNameNode.getParent();
      if (!NodeUtil.isNameDeclaration(decl)
          || !decl.hasOneChild()
          || !decl.getFirstChild().hasChildren()
          || !decl.getFirstChild().getLastChild().isCall()) {
        return;
      }

      ClosureImport kindOfCall = kindOfCall(decl.getFirstChild().getLastChild());

      if (kindOfCall == null) {
        return;
      }
    }

    private void checkGoogModuleGet(NodeTraversal t, Node call, ModuleMetadata currentModule) {
      if (t.inModuleHoistScope()) {
        t.report(call, MODULE_USES_GOOG_MODULE_GET);
        return;
      }

      if (!currentModule.isEs6Module()
          && !currentModule.isGoogModule()
          && compiler.getOptions().getModuleResolutionMode() != ResolutionMode.WEBPACK) {
        // Here we are making a heuristic check of the use of goog.module.get.  There are many
        // different ways to deliberately subvert these checks.  What we are trying to avoid
        // is accidential use of a `goog.provide` file as module scoped. So we want to avoid:
        //   `const Foo = goog.module.get('a.b.Foo');
        // and similar patterns, but we won't try to make this a perfect check.

        // Anything not in global scope is ok.
        if (t.inGlobalScope()) {
          // Find the root of expressions like `goog.module.get().export`
          Node getSubExprRoot = call;
          while (NodeUtil.isNormalOrOptChainGetProp(getSubExprRoot.getParent())) {
            getSubExprRoot = getSubExprRoot.getParent();
          }

          boolean invalid = false;
          Node parent = getSubExprRoot.getParent();
          if (parent.isName() || parent.isDestructuringLhs()) {
            invalid = true;
          } else if (parent.isAssign()) {
            Node target = parent.getFirstChild();
            if (target.isName() || target.isObjectPattern()) {
              invalid = true;
            }
          }

          if (invalid) {
            t.report(call, INVALID_GET_CALL_SCOPE);
          }
        }
      }

      if (!call.hasTwoChildren() || !call.getSecondChild().isStringLit()) {
        t.report(call, INVALID_CLOSURE_IMPORT_CALL, "goog.module.get");
        return;
      }

      String namespace = call.getSecondChild().getString();
      ModuleMetadata requiredModule = moduleMetadataMap.getModulesByGoogNamespace().get(namespace);

      if (requiredModule == null) {
        t.report(call, MISSING_MODULE_OR_PROVIDE, namespace);
        return;
      }

      Node maybeAssign = call.getParent();
      boolean isFillingAnAlias = maybeAssign.isAssign() && maybeAssign.getFirstChild().isName();
      boolean isModule = currentModule.isGoogModule() || currentModule.isEs6Module();
      if (isFillingAnAlias && isModule) {
        String aliasName = call.getParent().getFirstChild().getString();

        // If the assignment isn't into a var in our scope then it's not ok.
        Var aliasVar = t.getScope().getVar(aliasName);
        if (aliasVar == null) {
          t.report(call, INVALID_GET_ALIAS);
          return;
        }

        // Even if it was to a var in our scope it should still only rewrite if the var looked like:
        //   let x = goog.forwardDeclare('a.namespace');
        Node aliasVarNodeRhs = NodeUtil.getRValueOfLValue(aliasVar.getNode());

        if (aliasVarNodeRhs == null || !NodeUtil.isCallTo(aliasVarNodeRhs, GOOG_FORWARD_DECLARE)) {
          t.report(call, INVALID_GET_ALIAS);
          return;
        }

        if (!namespace.equals(aliasVarNodeRhs.getLastChild().getString())) {
          t.report(call, INVALID_GET_ALIAS);
          return;
        }
      }
    }

    private boolean isValidDestructuringImport(Node destructuringLhs) {
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
        }
        if (!stringKey.getFirstChild().isName()) {
          return false;
        }
      }
      return true;
    }

    private void checkShortName(NodeTraversal t, Node shortNameNode, String namespace) {
      String shortName = shortNameNode.getString();
      String lastSegment = namespace.substring(namespace.lastIndexOf('.') + 1);
      if (shortName.equals(lastSegment) || lastSegment.isEmpty()) {
        return;
      }

      if (isUpperCase(shortName.charAt(0)) != isUpperCase(lastSegment.charAt(0))) {
        char newStartChar =
            isUpperCase(shortName.charAt(0))
                ? toLowerCase(shortName.charAt(0))
                : toUpperCase(shortName.charAt(0));
        String correctedName = newStartChar + shortName.substring(1);
        t.report(shortNameNode, INCORRECT_SHORTNAME_CAPITALIZATION, shortName, correctedName);
      }
    }

    private void checkImport(
        NodeTraversal t,
        Node call,
        Node parent,
        ModuleMetadata currentModule,
        ClosureImport importType) {
      boolean atTopLevelScope = t.inGlobalHoistScope() || t.inModuleScope();
      boolean isModule = currentModule.isModule();
      boolean validAssignment = isModule || parent.isExprResult();
      boolean isAliased = NodeUtil.isNameDeclaration(parent.getParent());

      // goog.requireDynamic() can be in non-top scope.
      if ((!atTopLevelScope || !validAssignment) && importType != ClosureImport.REQUIRE_DYNAMIC) {
        t.report(call, INVALID_CLOSURE_CALL_SCOPE_ERROR);
        return;
      }

      if (!call.hasTwoChildren() || !call.getSecondChild().isStringLit()) {
        t.report(call, INVALID_CLOSURE_IMPORT_CALL, importType.callName());
        return;
      }

      if (isModule && isAliased) {
        Node declaration = parent.getParent();

        if (!declaration.hasOneChild()) {
          t.report(call, ONE_CLOSURE_IMPORT_PER_DECLARATION, importType.callName());
          return;
        }

        if (importType.aliasMustBeConstant()) {
          if (declaration.isLet()) {
            t.report(call, LET_CLOSURE_IMPORT, importType.callName());
          } else if (!declaration.isConst() && currentModule.isEs6Module()) {
            t.report(call, LHS_OF_CLOSURE_IMPORT_MUST_BE_CONST_IN_ES_MODULE, importType.callName());
          }
        }

        Node lhs = declaration.getFirstChild();
        if (lhs.isDestructuringLhs()) {
          if (importType.allowDestructuring()) {
            if (!isValidDestructuringImport(lhs)) {
              t.report(declaration, INVALID_CLOSURE_IMPORT_DESTRUCTURING, importType.callName());
            }
          } else {
            t.report(declaration, NO_CLOSURE_IMPORT_DESTRUCTURING, importType.callName());
          }
        } else {
          checkShortName(t, lhs, call.getLastChild().getString());
        }
      }

      String namespace = call.getSecondChild().getString();
      ModuleMetadata requiredModule = moduleMetadataMap.getModulesByGoogNamespace().get(namespace);

      if (requiredModule == null) {
        if (importType == ClosureImport.FORWARD_DECLARE) {
          // Ok to forwardDeclare any global, sadly. This is some bad legacy behavior and people
          // ought to use externs.
          if (isAliased && isModule) {
            // Special case `const Foo = goog.forwardDeclare('a.Foo');`. For legacy reasons, we
            // allow js_library to be less strict about missing forwardDeclares vs. other missing
            // Closure imports.
            t.report(
                call,
                ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE_FOR_FORWARD_DECLARE,
                namespace);
          }
          return;
        }
        t.report(call, MISSING_MODULE_OR_PROVIDE, namespace);
        return;
      }

      if (importType.mustBeOrdered()) {
        verifyRequireOrder(namespace, call, t, requiredModule);
      }

      if (importType == ClosureImport.REQUIRE
          && currentModule.isEs6Module()
          && requiredModule.isEs6Module()) {
        t.report(call, Es6RewriteModules.SHOULD_IMPORT_ES6_MODULE);
      }
    }
  }

  private void verifyRequireOrder(
      String namespace, Node call, NodeTraversal t, ModuleMetadata requiredModule) {
    if (!namespacesSeen.contains(namespace)) {
      t.report(call, LATE_PROVIDE_ERROR, namespace);
      return;
    }

    if (requiredModule.rootNode() == null || requiredModule.rootNode().isFromExterns()) {
      return; // synthetic metadata for tests may have no root node.
    }

    InputId requiredInputId = NodeUtil.getInputId(requiredModule.rootNode());
    CompilerInput requiredInput =
        checkNotNull(
            compiler.getInput(requiredInputId), "Cannot find CompilerInput for %s", requiredModule);

    JSChunk requiredChunk = requiredInput.getChunk();
    JSChunk currentChunk = t.getChunk();
    if (currentChunk != requiredChunk && !chunkGraph.dependsOn(currentChunk, requiredChunk)) {
      compiler.report(
          JSError.make(
              call,
              CROSS_CHUNK_REQUIRE_ERROR,
              namespace,
              requiredChunk.getName(),
              currentChunk.getName()));
    }
  }
}
