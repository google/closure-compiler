/*
 * Copyright 2004 The Closure Compiler Authors.
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
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.SyntacticScopeCreator.RedeclarationHandler;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Set;

/**
 * Checks that all variables are declared, that file-private variables are
 * accessed only in the file that declares them, and that any var references
 * that cross module boundaries respect declared module dependencies.
 *
 */
class VarCheck extends AbstractPostOrderCallback implements
    HotSwapCompilerPass {

  static final DiagnosticType UNDEFINED_VAR_ERROR = DiagnosticType.error(
      "JSC_UNDEFINED_VARIABLE",
      "variable {0} is undeclared");

  static final DiagnosticType VIOLATED_MODULE_DEP_ERROR = DiagnosticType.error(
      "JSC_VIOLATED_MODULE_DEPENDENCY",
      "module {0} cannot reference {2}, defined in " +
      "module {1}, since {1} loads after {0}");

  static final DiagnosticType MISSING_MODULE_DEP_ERROR = DiagnosticType.warning(
      "JSC_MISSING_MODULE_DEPENDENCY",
      "missing module dependency; module {0} should depend " +
      "on module {1} because it references {2}");

  static final DiagnosticType STRICT_MODULE_DEP_ERROR = DiagnosticType.disabled(
      "JSC_STRICT_MODULE_DEPENDENCY",
      // The newline below causes the JS compiler not to complain when the
      // referenced module's name changes because, for example, it's a
      // synthetic module.
      "cannot reference {2} because of a missing module dependency\n"
      + "defined in module {1}, referenced from module {0}");

  static final DiagnosticType NAME_REFERENCE_IN_EXTERNS_ERROR =
    DiagnosticType.warning(
      "JSC_NAME_REFERENCE_IN_EXTERNS",
      "accessing name {0} in externs has no effect. " +
      "Perhaps you forgot to add a var keyword?");

  static final DiagnosticType UNDEFINED_EXTERN_VAR_ERROR =
    DiagnosticType.warning(
      "JSC_UNDEFINED_EXTERN_VAR_ERROR",
      "name {0} is not defined in the externs.");

  static final DiagnosticType VAR_MULTIPLY_DECLARED_ERROR =
      DiagnosticType.error(
          "JSC_VAR_MULTIPLY_DECLARED_ERROR",
          "Variable {0} first declared in {1}");

  static final DiagnosticType VAR_ARGUMENTS_SHADOWED_ERROR =
    DiagnosticType.error(
        "JSC_VAR_ARGUMENTS_SHADOWED_ERROR",
        "Shadowing \"arguments\" is not allowed");

  // The arguments variable is special, in that it's declared in every local
  // scope, but not explicitly declared.
  private static final String ARGUMENTS = "arguments";

  // Vars that still need to be declared in externs. These will be declared
  // at the end of the pass, or when we see the equivalent var declared
  // in the normal code.
  private final Set<String> varsToDeclareInExterns = Sets.newHashSet();

  private final AbstractCompiler compiler;

  // Whether this is the post-processing sanity check.
  private final boolean sanityCheck;

  // Whether extern checks emit error.
  private final boolean strictExternCheck;

  VarCheck(AbstractCompiler compiler) {
    this(compiler, false);
  }

  VarCheck(AbstractCompiler compiler, boolean sanityCheck) {
    this.compiler = compiler;
    this.strictExternCheck = compiler.getErrorLevel(
        JSError.make("", 0, 0, UNDEFINED_EXTERN_VAR_ERROR)) == CheckLevel.ERROR;
    this.sanityCheck = sanityCheck;
  }

  /**
   * Create a SyntacticScopeCreator. If not in sanity check mode, use a
   * {@link RedeclarationCheckHandler} to check var redeclarations.
   * @return the SyntacticScopeCreator
   */
  private ScopeCreator createScopeCreator() {
    if (compiler.getLanguageMode().isEs6OrHigher()) {
      // Redeclaration check is handled in VariableReferenceCheck for ES6
      return new Es6SyntacticScopeCreator(compiler);
    } else if (sanityCheck) {
      return SyntacticScopeCreator.makeUntyped(compiler);
    } else {
      return SyntacticScopeCreator.makeUntypedWithRedeclHandler(
          compiler, new RedeclarationCheckHandler());
    }
  }

  @Override
  public void process(Node externs, Node root) {
    ScopeCreator scopeCreator = createScopeCreator();
    // Don't run externs-checking in sanity check mode. Normalization will
    // remove duplicate VAR declarations, which will make
    // externs look like they have assigns.
    if (!sanityCheck) {
      NodeTraversal traversal = new NodeTraversal(
          compiler, new NameRefInExternsCheck(), scopeCreator);
      traversal.traverse(externs);
    }

    NodeTraversal t = new NodeTraversal(compiler, this, scopeCreator);
    t.traverseRoots(externs, root);
    for (String varName : varsToDeclareInExterns) {
      createSynthesizedExternVar(varName);
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    Preconditions.checkState(scriptRoot.isScript());
    ScopeCreator scopeCreator = createScopeCreator();
    NodeTraversal t = new NodeTraversal(compiler, this, scopeCreator);
    // Note we use the global scope to prevent wrong "undefined-var errors" on
    // variables that are defined in other JS files.
    Scope topScope = scopeCreator.createScope(compiler.getRoot(), null);
    t.traverseWithScope(scriptRoot, topScope);
    // TODO(bashir) Check if we need to createSynthesizedExternVar like process.
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!n.isName()) {
      return;
    }

    String varName = n.getString();

    // Only a function can have an empty name.
    if (varName.isEmpty()) {
      Preconditions.checkState(parent.isFunction());
      Preconditions.checkState(NodeUtil.isFunctionExpression(parent));
      return;
    }

    // Check if this is a declaration for a var that has been declared
    // elsewhere. If so, mark it as a duplicate.
    if ((parent.isVar() ||
         NodeUtil.isFunctionDeclaration(parent)) &&
        varsToDeclareInExterns.contains(varName)) {
      createSynthesizedExternVar(varName);

      n.addSuppression("duplicate");
    }

    // Check that the var has been declared.
    Scope scope = t.getScope();
    Var var = scope.getVar(varName);
    if (var == null) {
      if (NodeUtil.isFunctionExpression(parent)) {
        // e.g. [ function foo() {} ], it's okay if "foo" isn't defined in the
        // current scope.
      } else {
        boolean isArguments = scope.isLocal() && ARGUMENTS.equals(varName);
        // The extern checks are stricter, don't report a second error.
        if (!isArguments && !(strictExternCheck && t.getInput().isExtern())) {
          t.report(n, UNDEFINED_VAR_ERROR, varName);
        }

        if (sanityCheck) {
          throw new IllegalStateException("Unexpected variable " + varName);
        } else {
          createSynthesizedExternVar(varName);
          scope.getGlobalScope().declare(varName, n, compiler.getSynthesizedExternsInput());
        }
      }
      return;
    }

    CompilerInput currInput = t.getInput();
    CompilerInput varInput = var.input;
    if (currInput == varInput || currInput == null || varInput == null) {
      // The variable was defined in the same file. This is fine.
      return;
    }

    // Check module dependencies.
    JSModule currModule = currInput.getModule();
    JSModule varModule = varInput.getModule();
    JSModuleGraph moduleGraph = compiler.getModuleGraph();
    if (!sanityCheck &&
        varModule != currModule && varModule != null && currModule != null) {
      if (moduleGraph.dependsOn(currModule, varModule)) {
        // The module dependency was properly declared.
      } else {
        if (scope.isGlobal()) {
          if (moduleGraph.dependsOn(varModule, currModule)) {
            // The variable reference violates a declared module dependency.
            t.report(n, VIOLATED_MODULE_DEP_ERROR,
                     currModule.getName(), varModule.getName(), varName);
          } else {
            // The variable reference is between two modules that have no
            // dependency relationship. This should probably be considered an
            // error, but just issue a warning for now.
            t.report(n, MISSING_MODULE_DEP_ERROR,
                     currModule.getName(), varModule.getName(), varName);
          }
        } else {
          t.report(n, STRICT_MODULE_DEP_ERROR,
                   currModule.getName(), varModule.getName(), varName);
        }
      }
    }
  }

  /**
   * Create a new variable in a synthetic script. This will prevent
   * subsequent compiler passes from crashing.
   */
  private void createSynthesizedExternVar(String varName) {
    Node nameNode = IR.name(varName);

    // Mark the variable as constant if it matches the coding convention
    // for constant vars.
    // NOTE(nicksantos): honestly, I'm not sure how much this matters.
    // AFAIK, all people who use the CONST coding convention also
    // compile with undeclaredVars as errors. We have some test
    // cases for this configuration though, and it makes them happier.
    if (compiler.getCodingConvention().isConstant(varName)) {
      nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }

    getSynthesizedExternsRoot().addChildToBack(
        IR.var(nameNode));
    varsToDeclareInExterns.remove(varName);
    compiler.reportCodeChange();
  }

  /**
   * A check for name references in the externs inputs. These used to prevent
   * a variable from getting renamed, but no longer have any effect.
   */
  private class NameRefInExternsCheck extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        switch (parent.getType()) {
          case Token.VAR:
          case Token.FUNCTION:
          case Token.PARAM_LIST:
            // These are okay.
            break;
          case Token.GETPROP:
            if (n == parent.getFirstChild()) {
              Scope scope = t.getScope();
              Var var = scope.getVar(n.getString());
              if (var == null) {
                t.report(n, UNDEFINED_EXTERN_VAR_ERROR, n.getString());
                varsToDeclareInExterns.add(n.getString());
              }
            }
            break;
         case Token.ASSIGN:
            // Don't warn for the "window.foo = foo;" nodes added by
            // DeclaredGlobalExternsOnWindow.
            if (n == parent.getLastChild() && parent.getFirstChild().isGetProp()
                && parent.getFirstChild().getLastChild().getString().equals(n.getString())) {
              break;
            }
            // fall through
          default:
            t.report(n, NAME_REFERENCE_IN_EXTERNS_ERROR, n.getString());

            Scope scope = t.getScope();
            Var var = scope.getVar(n.getString());
            if (var == null) {
              varsToDeclareInExterns.add(n.getString());
            }
            break;
        }
      }
    }
  }


  /**
   * @param n The name node to check.
   * @param origVar The associated Var.
   * @return Whether duplicated declarations warnings should be suppressed
   *     for the given node.
   */
  static boolean hasDuplicateDeclarationSuppression(Node n, Var origVar) {
    Preconditions.checkState(n.isName() || n.isRest() || n.isStringKey());
    Node parent = n.getParent();
    Node origParent = origVar.getParentNode();

    JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      info = parent.getJSDocInfo();
    }
    if (info != null && info.getSuppressions().contains("duplicate")) {
      return true;
    }

    info = origVar.nameNode.getJSDocInfo();
    if (info == null) {
      info = origParent.getJSDocInfo();
    }
    return (info != null && info.getSuppressions().contains("duplicate"));
  }

  /**
   * The handler for duplicate declarations.
   */
  private class RedeclarationCheckHandler implements RedeclarationHandler {
    @Override
    public void onRedeclaration(
        Scope s, String name, Node n, CompilerInput input) {
      Node parent = n.getParent();

      // Don't allow multiple variables to be declared at the top-level scope
      if (s.isGlobal()) {
        Var origVar = s.getVar(name);
        Node origParent = origVar.getParentNode();
        if (origParent.isCatch() &&
            parent.isCatch()) {
          // Okay, both are 'catch(x)' variables.
          return;
        }

        boolean allowDupe = hasDuplicateDeclarationSuppression(n, origVar);

        if (!allowDupe) {
          compiler.report(
              JSError.make(n,
                           VAR_MULTIPLY_DECLARED_ERROR,
                           name,
                           (origVar.input != null
                            ? origVar.input.getName()
                            : "??")));
        }
      } else if (name.equals(ARGUMENTS) && !NodeUtil.isVarDeclaration(n)) {
        // Disallow shadowing "arguments" as we can't handle with our current
        // scope modeling.
        compiler.report(
            JSError.make(n, VAR_ARGUMENTS_SHADOWED_ERROR));
      }
    }
  }

  /** Lazily create a "new" externs root for undeclared variables. */
  private Node getSynthesizedExternsRoot() {
    return  compiler.getSynthesizedExternsInput().getAstRoot(compiler);
  }
}
