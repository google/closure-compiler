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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.SyntacticScopeCreator.RedeclarationHandler;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Checks that all variables are declared, that file-private variables are accessed only in the file
 * that declares them, and that any var references that cross module boundaries respect declared
 * module dependencies.
 */
class VarCheck implements ScopedCallback, CompilerPass {

  static final DiagnosticType UNDEFINED_VAR_ERROR = DiagnosticType.error(
      "JSC_UNDEFINED_VARIABLE",
      "variable {0} is undeclared");

  static final DiagnosticType VIOLATED_MODULE_DEP_ERROR =
      DiagnosticType.error(
          "JSC_VIOLATED_MODULE_DEPENDENCY",
          "module {0} cannot reference {2}, defined in module {1}, since {1} loads after {0}");

  static final DiagnosticType MISSING_MODULE_DEP_ERROR =
      DiagnosticType.warning(
          "JSC_MISSING_MODULE_DEPENDENCY",
          "missing module dependency; module {0} should depend"
              + " on module {1} because it references {2}");

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
          "accessing name {0} in externs has no effect."
              + " Perhaps you forgot to add a var keyword?");

  static final DiagnosticType UNDEFINED_EXTERN_VAR_ERROR =
    DiagnosticType.warning(
      "JSC_UNDEFINED_EXTERN_VAR_ERROR",
      "name {0} is not defined in the externs.");

  static final DiagnosticType VAR_MULTIPLY_DECLARED_ERROR =
      DiagnosticType.error(
          "JSC_VAR_MULTIPLY_DECLARED_ERROR",
          "Variable {0} declared more than once. First occurrence: {1}");

  static final DiagnosticType BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR =
      DiagnosticType.error(
          "JSC_BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR",
          "Block-scoped variable {0} declared more than once. First occurrence: {1}");

  static final DiagnosticType VAR_ARGUMENTS_SHADOWED_ERROR =
    DiagnosticType.error(
        "JSC_VAR_ARGUMENTS_SHADOWED_ERROR",
        "Shadowing \"arguments\" is not allowed");

  // The arguments variable is special, in that it's declared in every local
  // scope, but not explicitly declared.
  private static final String ARGUMENTS = "arguments";

  private static final Node googForwardDeclare = IR.getprop(IR.name("goog"), "forwardDeclare");

  // Vars that were referenced in the externs without being declared in externs, even if they were
  // defined in code. These will be declared at the end of this pass.
  private final Set<String> undefinedNamesFromExterns = new LinkedHashSet<>();

  private final AbstractCompiler compiler;

  // Whether this is the post-processing validity check.
  private final boolean validityCheck;

  // Whether extern checks emit error.
  private final boolean strictExternCheck;

  private RedeclarationCheckHandler dupHandler;

  VarCheck(AbstractCompiler compiler) {
    this(compiler, false);
  }

  VarCheck(AbstractCompiler compiler, boolean validityCheck) {
    this.compiler = compiler;
    this.strictExternCheck = compiler.getErrorLevel(
        JSError.make("", 0, 0, UNDEFINED_EXTERN_VAR_ERROR)) == CheckLevel.ERROR;
    this.validityCheck = validityCheck;
  }

  /**
   * Creates the scope creator used by this pass. If not in validity check mode, use a {@link
   * RedeclarationCheckHandler} to check var redeclarations.
   */
  private SyntacticScopeCreator createScopeCreator() {
    if (validityCheck) {
      return new SyntacticScopeCreator(compiler);
    } else {
      dupHandler = new RedeclarationCheckHandler();
      return new SyntacticScopeCreator(compiler, dupHandler);
    }
  }

  @Override
  public void process(Node externs, Node root) {
    ScopeCreator scopeCreator = createScopeCreator();

    // Don't run externs-checking in sanity check mode. Normalization will
    // remove duplicate VAR declarations, which will make
    // externs look like they have assigns.
    if (!validityCheck) {
      NodeTraversal.builder()
          .setCompiler(compiler)
          .setCallback(new NameRefInExternsCheck())
          .setScopeCreator(scopeCreator)
          .traverse(externs);
    }

    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(this)
        .setScopeCreator(scopeCreator)
        .traverseRoots(externs, root);

    for (String varName : undefinedNamesFromExterns) {
      createSynthesizedExternVar(varName, /* isFromUndefinedCodeRef= */ false);
    }

    if (dupHandler != null) {
      dupHandler.removeDuplicates();
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isName()) {
      checkName(t, n, parent);
    }
  }

  /** Validates that a NAME node does not refer to an undefined name. */
  private void checkName(NodeTraversal t, Node n, Node parent) {
    String varName = n.getString();

    // Only a function can have an empty name.
    if (varName.isEmpty()) {
      // Name is optional for function expressions
      // x = function() {...}
      // Arrow functions are also expressions and cannot have a name
      // x = () => {...}
      // Member functions have an empty NAME node string, because the actual name is stored on the
      // MEMBER_FUNCTION_DEF object that contains the FUNCTION.
      // class C { foo() {...} }
      // x = { foo() {...} }
      checkState(NodeUtil.isFunctionExpression(parent) || NodeUtil.isMethodDeclaration(parent));
      return;
    }

    Scope scope = t.getScope();
    Var var = scope.getVar(varName);

    // Check that the var has been declared.
    if (var == null) {
      if ((NodeUtil.isFunctionExpression(parent) || NodeUtil.isClassExpression(parent))
          && n.isFirstChildOf(parent)) {
        // e.g. [ function foo() {} ], it's okay if "foo" isn't defined in the
        // current scope.
        return;
      }

      if (NodeUtil.isNonlocalModuleExportName(n)) {
        // e.g. "export {a as b}" or "import {b as a} from './foo.js'
        // where b is defined in a module's export entries but not in any module scope.
        return;
      }

      this.handleUndeclaredVariableRef(t, n);
      scope.getGlobalScope().declare(varName, n, compiler.getSynthesizedExternsInput());

      return;
    }

    if (var.isImplicitGoogNamespace()
        && var.getImplicitGoogNamespaceStrength().equals(SourceKind.WEAK)
        && strengthOf(n).equals(SourceKind.STRONG)) {
      // This use will be retained but its definition will be deleted.
      this.handleUndeclaredVariableRef(t, n);
      return;
    }

    CompilerInput currInput = t.getInput();
    CompilerInput varInput = var.getInput();
    if (currInput == varInput || currInput == null || varInput == null) {
      // The variable was defined in the same file. This is fine.
      return;
    }

    // Check module dependencies.
    JSChunk currModule = currInput.getChunk();
    JSChunk varModule = varInput.getChunk();
    JSChunkGraph moduleGraph = compiler.getModuleGraph();
    if (!validityCheck && varModule != currModule && varModule != null && currModule != null) {
      if (varModule.isWeak()) {
        this.handleUndeclaredVariableRef(t, n);
      }

      if (moduleGraph.dependsOn(currModule, varModule)) {
        // The module dependency was properly declared.
      } else {
        if (scope.isGlobal()) {
          if (moduleGraph.dependsOn(varModule, currModule)) {
            // The variable reference violates a declared module dependency.
            t.report(
                n, VIOLATED_MODULE_DEP_ERROR, currModule.getName(), varModule.getName(), varName);
          } else {
            // The variable reference is between two modules that have no dependency relationship.
            // This should probably be considered an error, but just issue a warning for now.
            t.report(
                n, MISSING_MODULE_DEP_ERROR, currModule.getName(), varModule.getName(), varName);
          }
        } else {
          t.report(n, STRICT_MODULE_DEP_ERROR, currModule.getName(), varModule.getName(), varName);
        }
      }
    }
  }

  private static final SourceKind strengthOf(Node n) {
    StaticSourceFile source = n.getStaticSourceFile();
    if (source == null) {
      return SourceKind.EXTERN;
    }

    return source.getKind();
  }

  private void handleUndeclaredVariableRef(NodeTraversal t, Node n) {
    checkState(n.isName());

    String varName = n.getString();

    if (n.getParent().isTypeOf()) {
      // `typeof` is used for existence checks.
    } else if (strictExternCheck && t.getInput().isExtern()) {
      // The extern checks are stricter, don't report a second error.
    } else {
      t.report(n, UNDEFINED_VAR_ERROR, varName);
    }

    if (validityCheck) {
      // When the code is initially traversed, any undeclared variables are treated as
      // externs. During this sanity check, we ensure that all variables have either been
      // declared or marked as an extern. A failure at this point means that we have created
      // some variable/generated some code with an undefined reference.
      throw new IllegalStateException("Unexpected variable " + varName);
    } else if (!undefinedNamesFromExterns.contains(varName)) {
      // Skip this case if the name is already going to be added as an "undefined name from extern"
      // That declaration must take priority, to avoid the RemoveUnnecessarySyntheticExterns pass
      // accidentally treating the synthetic extern as unnecessary.
      createSynthesizedExternVar(varName, /* isFromUndefinedCodeRef= */ true);
    }
  }

  @Override
  public void enterScope(NodeTraversal t) {}

  @Override
  public void exitScope(NodeTraversal t) {
    if (!validityCheck && t.inGlobalScope()) {
      Scope scope = t.getScope();
      // Add symbols that are known to be needed to the standard injected code (polyfills, etc).
      for (String requiredSymbol : REQUIRED_SYMBOLS) {
        Var var = scope.getVar(requiredSymbol);
        if (var == null) {
          undefinedNamesFromExterns.add(requiredSymbol);
        }
      }
    }
  }

  /**
   * List of symbols that must always be externed even if they are not referenced anywhere (yet).
   * These are used by runtime libraries that might not be present when the first VarCheck runs.
   */
  static final ImmutableSet<String> REQUIRED_SYMBOLS =
      ImmutableSet.of(
          "AggregateError",
          "Array",
          "Error",
          "Float32Array",
          "Function",
          "Infinity",
          "JSCompiler_renameProperty",
          "Map",
          "Math",
          "NaN",
          "Number",
          "Object",
          "Promise",
          "RangeError",
          "Reflect",
          "RegExp",
          "Set",
          "String",
          "Symbol",
          "TypeError",
          "WeakMap",
          "global",
          "globalThis",
          "isNaN",
          "parseFloat",
          "parseInt",
          "self",
          "undefined",
          "window");

  /**
   * Create a new variable in a synthetic script. This will prevent subsequent compiler passes from
   * crashing.
   */
  private void createSynthesizedExternVar(String varName, boolean isFromUndefinedCodeRef) {
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

    Node syntheticExternVar = IR.var(nameNode);
    syntheticExternVar.setIsSynthesizedUnfulfilledNameDeclaration(isFromUndefinedCodeRef);
    getSynthesizedExternsRoot(compiler).addChildToBack(syntheticExternVar);
    compiler.reportChangeToEnclosingScope(syntheticExternVar);
  }

  /**
   * A check for name references in the externs inputs. These used to prevent a variable from
   * getting renamed, but no longer have any effect.
   */
  private class NameRefInExternsCheck implements NodeTraversal.Callback {

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      // Type summaries are generated from code rather than hand-written,
      // so warning about name references there would usually not be helpful.
      return !n.isScript() || !NodeUtil.isFromTypeSummary(n);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        switch (parent.getToken()) {
          case VAR:
          case LET:
          case CONST:
          case FUNCTION:
          case CLASS:
          case PARAM_LIST:
          case DEFAULT_VALUE:
          case ITER_REST:
          case OBJECT_REST:
          case ARRAY_PATTERN:
            // These are okay.
            return;
          case STRING_KEY:
            if (parent.getParent().isObjectPattern()) {
              return;
            }
            break;
          case GETPROP:
            if (n == parent.getFirstChild()) {
              Scope scope = t.getScope();
              Var var = scope.getVar(n.getString());
              if (var != null) {
                return;
              }
              if (parent.matchesQualifiedName(googForwardDeclare)) {
                // Allow using `goog.forwardDeclare` in the externs without an externs definition
                // of goog.
                return;
              }
              t.report(n, UNDEFINED_EXTERN_VAR_ERROR, n.getString());
              undefinedNamesFromExterns.add(n.getString());
            }
            return;
          case ASSIGN:
            // Don't warn for the "window.foo = foo;" nodes added by
            // DeclaredGlobalExternsOnWindow, nor for alias declarations
            // of the form "/** @const */ ns.Foo = Bar;"
            if (n == parent.getLastChild()
                && n.isQualifiedName()
                && parent.getFirstChild().isQualifiedName()) {
              return;
            }
            break;
          case NAME:
            // Don't warn for simple var assignments "/** @const */ var foo = bar;"
            // They are used to infer the types of namespace aliases.
            if (NodeUtil.isNameDeclaration(parent.getParent())) {
              return;
            }
            break;
          case OR:
            // Don't warn for namespace declarations: "/** @const */ var ns = ns || {};"
            if (NodeUtil.isNamespaceDecl(parent.getParent())) {
              return;
            }
            break;
          default:
            break;
        }
        t.report(n, NAME_REFERENCE_IN_EXTERNS_ERROR, n.getString());
        Scope scope = t.getScope();
        Var var = scope.getVar(n.getString());
        if (var == null) {
          undefinedNamesFromExterns.add(n.getString());
        }
      }
    }
  }

  /** Returns true if duplication warnings are suppressed on either n or origVar. */
  static boolean hasDuplicateDeclarationSuppression(
      AbstractCompiler compiler, Node n, Node origVar) {
    // For VarCheck and VariableReferenceCheck, variables in externs do not generate duplicate
    // warnings.
    if (isExternNamespace(n)) {
      return true;
    }
    return TypeValidator.hasDuplicateDeclarationSuppression(compiler, origVar);
  }

  /** Returns true if n is the name of a variable that declares a namespace in an externs file. */
  static boolean isExternNamespace(Node n) {
    return n.getParent().isVar() && n.isFromExterns() && NodeUtil.isNamespaceDecl(n);
  }

  /**
   * The handler for duplicate declarations.
   */
  private class RedeclarationCheckHandler implements RedeclarationHandler {
    private final ArrayList<Node> dupDeclNodes = new ArrayList<>();

    @Override
    public void onRedeclaration(
        Scope s, String name, Node n, CompilerInput input) {
      Node parent = NodeUtil.getDeclaringParent(n);
      Var origVar = s.getVar(name);
      // origNode will be null for `arguments`, since there's no node that declares it.
      Node origNode = origVar.getNode();
      Node origParent = (origNode == null) ? null : NodeUtil.getDeclaringParent(origNode);

      switch (parent.getToken()) {
        case CLASS:
        case CONST:
        case LET:
          reportBlockScopedMultipleDeclaration(n, name, origNode);
          return;

        default:
          break;
      }

      if (origParent != null) {
        switch (origParent.getToken()) {
          case CLASS:
          case CONST:
          case LET:
            reportBlockScopedMultipleDeclaration(n, name, origNode);
            return;

          case FUNCTION:
            // Redeclarations of functions in global scope are fairly common, so allow them
            // (at least for now).
            if (!s.isGlobal() && parent.isFunction()) {
              reportBlockScopedMultipleDeclaration(n, name, origNode);
              return;
            }
            break;

          default:
            break;
        }
      }

      // Don't allow multiple variables to be declared at the top-level scope
      if (s.isGlobal()) {
        if (origParent.isCatch() && parent.isCatch()) {
          // Okay, both are 'catch(x)' variables.
          return;
        }

        boolean allowDupe = hasDuplicateDeclarationSuppression(compiler, n, origVar.getNameNode());
        if (VarCheck.isExternNamespace(n)) {
          this.dupDeclNodes.add(parent);
          return;
        }
        if (!allowDupe) {
          reportVarMultiplyDeclared(compiler, n, name, origNode);
        }
      } else if (name.equals(ARGUMENTS)
          && !(NodeUtil.isNameDeclaration(n.getParent()) && n.isName())) {
        // Disallow shadowing "arguments" as we can't handle with our current
        // scope modeling.
        compiler.report(JSError.make(n, VAR_ARGUMENTS_SHADOWED_ERROR));
      }
    }

    public void removeDuplicates() {
      for (Node n : dupDeclNodes) {
        Node parent = n.getParent();
        if (parent != null) {
          n.detach();
          compiler.reportChangeToEnclosingScope(parent);
        }
      }
    }
  }

  static void reportVarMultiplyDeclared(
      AbstractCompiler compiler, Node current, String name, @Nullable Node original) {
    compiler.report(JSError.make(current, VAR_MULTIPLY_DECLARED_ERROR, name, locationOf(original)));
  }

  private void reportBlockScopedMultipleDeclaration(
      Node current, String name, @Nullable Node original) {
    compiler.report(
        JSError.make(
            current, BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR, name, locationOf(original)));
  }

  private static String locationOf(@Nullable Node n) {
    return (n == null) ? "<unknown>" : n.getLocation();
  }

  /** Lazily create a "new" externs root for undeclared variables. */
  private static Node getSynthesizedExternsRoot(AbstractCompiler compiler) {
    return  compiler.getSynthesizedExternsInput().getAstRoot(compiler);
  }
}
