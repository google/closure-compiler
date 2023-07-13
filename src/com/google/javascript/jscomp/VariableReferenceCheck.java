/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.AbstractScope.ImplicitVar;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.ReferenceCollector.Behavior;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * Checks variables to see if they are referenced before their declaration, or if they are
 * redeclared in a way that is suspicious (i.e. not dictated by control structures). This is a more
 * aggressive version of {@link VarCheck}, but it lacks the cross-module checks.
 */
class VariableReferenceCheck implements CompilerPass {

  static final DiagnosticType EARLY_REFERENCE =
      DiagnosticType.warning(
          "JSC_REFERENCE_BEFORE_DECLARE", "Variable referenced before declaration: {0}");

  static final DiagnosticType EARLY_EXPORTS_REFERENCE =
      DiagnosticType.error(
          "JSC_EXPORTS_REFERENCE_BEFORE_ASSIGN",
          "Illegal reference to `exports` before assignment `exports = ...`");

  static final DiagnosticType REDECLARED_VARIABLE =
      DiagnosticType.warning("JSC_REDECLARED_VARIABLE", "Redeclared variable: {0}");

  static final DiagnosticType EARLY_REFERENCE_ERROR =
      DiagnosticType.error(
          "JSC_REFERENCE_BEFORE_DECLARE_ERROR",
          "Illegal variable reference before declaration: {0}");

  static final DiagnosticType REASSIGNED_CONSTANT =
      DiagnosticType.error("JSC_REASSIGNED_CONSTANT", "Constant reassigned: {0}");

  static final DiagnosticType REDECLARED_VARIABLE_ERROR =
      DiagnosticType.error("JSC_REDECLARED_VARIABLE_ERROR", "Illegal redeclared variable: {0}");

  static final DiagnosticType DECLARATION_NOT_DIRECTLY_IN_BLOCK =
      DiagnosticType.error(
          "JSC_DECLARATION_NOT_DIRECTLY_IN_BLOCK",
          "Block-scoped declaration not directly within block: {0}");

  static final DiagnosticType UNUSED_LOCAL_ASSIGNMENT =
      DiagnosticType.disabled(
          "JSC_UNUSED_LOCAL_ASSIGNMENT", "Value assigned to local variable {0} is never read");

  private static final QualifiedName GOOG_REQUIRE = QualifiedName.of("goog.require");
  private static final QualifiedName GOOG_REQUIRE_TYPE = QualifiedName.of("goog.requireType");
  private static final QualifiedName GOOG_FORWARD_DECLARE = QualifiedName.of("goog.forwardDeclare");

  private final AbstractCompiler compiler;

  private final boolean checkUnusedLocals;

  // NOTE(nicksantos): It's a lot faster to use a shared Set that
  // we clear after each method call, because the Set never gets too big.
  private final Set<BasicBlock> blocksWithDeclarations = new LinkedHashSet<>();

  // These types do not permit a block-scoped declaration inside them without an explicit block.
  // e.g. if (b) let x;
  // This list omits Token.LABEL intentionally. It's handled differently in IRFactory.
  private static final ImmutableSet<Token> BLOCKLESS_DECLARATION_FORBIDDEN_STATEMENTS =
      Sets.immutableEnumSet(
          Token.IF, Token.FOR, Token.FOR_IN, Token.FOR_OF, Token.FOR_AWAIT_OF, Token.WHILE);

  private static final QualifiedName GOOG_SCOPE = QualifiedName.of("goog.scope");

  public VariableReferenceCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.checkUnusedLocals =
        compiler.getOptions().enables(DiagnosticGroup.forType(UNUSED_LOCAL_ASSIGNMENT));
  }

  @Override
  public void process(Node externs, Node root) {
    new ReferenceCollector(
            compiler, new ReferenceCheckingBehavior(), new SyntacticScopeCreator(compiler))
        .process(externs, root);
  }

  /**
   * Behavior that checks variables for redeclaration or early references just after they go out of
   * scope.
   */
  private class ReferenceCheckingBehavior implements Behavior {

    private final Set<String> varsInFunctionBody;

    private ReferenceCheckingBehavior() {
      varsInFunctionBody = new LinkedHashSet<>();
    }

    @Override
    public void afterExitScope(NodeTraversal t, ReferenceMap referenceMap) {
      // Check all vars after finishing a scope
      Scope scope = t.getScope();
      if (scope.isFunctionBlockScope()) {
        varsInFunctionBody.clear();
        for (Var v : scope.getVarIterable()) {
          varsInFunctionBody.add(v.getName());
        }
      }
      for (Var v : scope.getVarIterable()) {
        ReferenceCollection referenceCollection = referenceMap.getReferences(v);
        // TODO(moz): Figure out why this could be null
        if (referenceCollection != null) {
          if (scope.getRootNode().isFunction() && v.isDefaultParam()) {
            checkDefaultParam(v, scope, varsInFunctionBody);
          }
          if (scope.getRootNode().isFunction()) {
            checkShadowParam(v, scope, referenceCollection.references);
          }
          checkVar(v, referenceCollection.references);
        }
      }
      if (scope.hasOwnImplicitSlot(ImplicitVar.EXPORTS)) {
        checkGoogModuleExports(scope.makeImplicitVar(ImplicitVar.EXPORTS), referenceMap);
      }
    }

    private void checkDefaultParam(
        Var param, final Scope scope, final Set<String> varsInFunctionBody) {
      NodeTraversal.traverse(
          compiler,
          param.getParentNode().getSecondChild(),
          /*
           * Do a shallow check since cases like: {@code
           *   function f(y = () => x, x = 5) { return y(); }
           * } is legal. We are going to miss cases like: {@code
           *   function f(y = (() => x)(), x = 5) { return y(); }
           * } but this should be rare.
           */
          new AbstractShallowCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (!NodeUtil.isReferenceName(n)) {
                return;
              }
              String refName = n.getString();
              if (varsInFunctionBody.contains(refName) && !scope.hasSlot(refName)) {
                compiler.report(JSError.make(n, EARLY_REFERENCE_ERROR, refName));
              }
            }
          });
    }

    private void checkShadowParam(Var v, Scope functionScope, List<Reference> references) {
      Var maybeParam = functionScope.getVar(v.getName());
      if (maybeParam != null && maybeParam.isParam() && maybeParam.getScope() == functionScope) {
        for (Reference r : references) {
          if ((r.isVarDeclaration() || r.isHoistedFunction())
              && r.getNode() != v.getNameNode()) {
            compiler.report(JSError.make(r.getNode(), REDECLARED_VARIABLE, v.getName()));
          }
        }
      }
    }

    private void checkGoogModuleExports(Var exportsVar, ReferenceMap referenceMap) {
      ReferenceCollection references = referenceMap.getReferences(exportsVar);
      if (references == null || references.isNeverAssigned()) {
        return;
      }

      for (Reference reference : references.references) {
        if (reference.isLvalue()) {
          break;
        }
        checkEarlyReference(exportsVar, reference, reference.getNode());
      }
    }

    /**
     * If the variable is declared more than once in a basic block, generate a
     * warning. Also check if a variable is used in a given scope before it is
     * declared, which suggest a likely error. Relies on the fact that
     * references is in parse-tree order.
     */
    private void checkVar(Var v, List<Reference> references) {
      blocksWithDeclarations.clear();
      boolean hasSeenDeclaration = false;
      boolean hasErrors = false;
      boolean isRead = false;
      Reference unusedAssignment = null;

      Reference hoistedFn = lookForHoistedFunction(references);
      if (hoistedFn != null) {
        hasSeenDeclaration = true;
      }

      for (Reference reference : references) {
        if (reference == hoistedFn) {
          continue;
        }

        Node referenceNode = reference.getNode();
        BasicBlock basicBlock = reference.getBasicBlock();
        boolean isDeclaration = reference.isDeclaration();
        boolean isAssignment = isDeclaration || reference.isLvalue();

        if (isDeclaration) {
          // Checks for declarations
          hasSeenDeclaration = true;
          hasErrors = checkRedeclaration(v, reference, referenceNode, hoistedFn, basicBlock);
          // Add the current basic block after checking redeclarations
          blocksWithDeclarations.add(basicBlock);
          checkBlocklessDeclaration(v, reference, referenceNode);

          if (reference.getGrandparent().isExport()) {
            isRead = true;
          }
        } else {
          // Checks for references
          if (!hasSeenDeclaration) {
            hasErrors = checkEarlyReference(v, reference, referenceNode);
          }

          if (!hasErrors && v.isConst() && reference.isLvalue()) {
            compiler.report(JSError.make(referenceNode, REASSIGNED_CONSTANT, v.getName()));
          }

          // Check for temporal dead zone of let / const declarations in for-in and for-of loops
          // TODO(b/111441110): Fix this check. it causes spurious warnings on `b = a` in
          //   for (const [a, b = a] of []) {}
          if ((v.isLet() || v.isConst())
              && v.getScope() == reference.getScope()
              && NodeUtil.isEnhancedFor(reference.getScope().getRootNode())) {
            compiler.report(JSError.make(referenceNode, EARLY_REFERENCE_ERROR, v.getName()));
          }
        }

        if (isAssignment) {
          Reference decl = references.get(0);
          Node declNode = decl.getNode();
          Node gp = declNode.getGrandparent();
          boolean lhsOfForInLoop = gp.isForIn() && gp.getFirstFirstChild() == declNode;

          if (decl.getScope().isLocal()
              && (decl.isVarDeclaration() || decl.isLetDeclaration() || decl.isConstDeclaration())
              && !decl.getNode().isFromExterns()
              && !lhsOfForInLoop) {
            unusedAssignment = reference;
          }
          if ((reference.getParent().isDec() || reference.getParent().isInc()
              || NodeUtil.isCompoundAssignmentOp(reference.getParent()))
              && NodeUtil.isExpressionResultUsed(reference.getNode())) {
            isRead = true;
          }
        } else {
          isRead = true;
        }
      }

      if (checkUnusedLocals && unusedAssignment != null && !isRead && !hasErrors) {
        checkForUnusedLocalVar(v, unusedAssignment);
      }
    }
  }

  /**
   * @return The reference to the hoisted function, if the variable is one
   */
  private @Nullable Reference lookForHoistedFunction(List<Reference> references) {
    for (Reference reference : references) {
      if (reference.isHoistedFunction()) {
        blocksWithDeclarations.add(reference.getBasicBlock());
        return reference;
      }
    }
    return null;
  }

  private void checkBlocklessDeclaration(Var v, Reference reference, Node referenceNode) {
    if (!reference.isVarDeclaration() && reference.getGrandparent().isAddedBlock()
        && BLOCKLESS_DECLARATION_FORBIDDEN_STATEMENTS.contains(
        reference.getGrandparent().getParent().getToken())) {
      compiler.report(JSError.make(referenceNode, DECLARATION_NOT_DIRECTLY_IN_BLOCK, v.getName()));
    }
  }

  /**
   * @return If a redeclaration error has been found
   */
  private boolean checkRedeclaration(
      Var v, Reference reference, Node referenceNode, Reference hoistedFn, BasicBlock basicBlock) {
    boolean letConstShadowsVar = v.getParentNode().isVar()
        && (reference.isLetDeclaration() || reference.isConstDeclaration());
    boolean isVarNodeSameAsReferenceNode = v.getNode() == reference.getNode();
    // We disallow redeclaration of caught exceptions
    boolean shadowCatchVar = v.getParentNode().isCatch() && !isVarNodeSameAsReferenceNode;

    if (isRedeclaration(basicBlock)
        && !VarCheck.hasDuplicateDeclarationSuppression(compiler, referenceNode, v.getNameNode())) {
      final DiagnosticType diagnosticType;
      Node warningNode = referenceNode;
      boolean shadowParam =
          v.isParam()
              && NodeUtil.isBlockScopedDeclaration(referenceNode)
              && v.getScope() == reference.getScope().getParent();

      boolean isFunctionDecl =
          (v.getParentNode() != null
              && v.getParentNode().isFunction()
              && v.getParentNode().getFirstChild() == referenceNode);

      if (v.isLet()
          || v.isConst()
          || v.isClass()
          || letConstShadowsVar
          || shadowCatchVar
          || shadowParam
          || v.isImport()
          || isFunctionDecl) {
        // These cases are all hard errors that violate ES6 semantics
        diagnosticType = REDECLARED_VARIABLE_ERROR;
      } else if (reference.getNode().getParent().isCatch()) {
        return false;
      } else {
        // These diagnostics are for valid, but suspicious, code, and are suppressible.
        // For vars defined in the global scope, give the same error as VarCheck
        diagnosticType =
            v.getScope().isGlobal() ? VarCheck.VAR_MULTIPLY_DECLARED_ERROR : REDECLARED_VARIABLE;
        // Since we skip hoisted functions, we would have the wrong warning node in cases
        // where the redeclaration is a function declaration. Check for that case.
        if (isVarNodeSameAsReferenceNode
            && hoistedFn != null
            && v.getName().equals(hoistedFn.getNode().getString())) {
          warningNode = hoistedFn.getNode();
        }
      }
      compiler.report(
          JSError.make(warningNode, diagnosticType, v.getName(), locationOf(v.getNode())));
      return true;
    }

    if ((letConstShadowsVar || shadowCatchVar) && v.getScope() == reference.getScope()) {
      compiler.report(JSError.make(referenceNode, REDECLARED_VARIABLE_ERROR, v.getName()));
      return true;
    }

    return false;
  }

  /**
   * Returns whether the given block executes after any known declarations of the variable being
   * visited.
   */
  private boolean isRedeclaration(BasicBlock newDeclaration) {
    for (BasicBlock previousDeclaration : blocksWithDeclarations) {
      if (previousDeclaration.provablyExecutesBefore(newDeclaration)) {
        return true;
      }
    }
    return false;
  }

  private static String locationOf(@Nullable Node n) {
    return (n == null) ? "<unknown>" : n.getLocation();
  }

  /**
   * @return If an early reference has been found
   */
  private boolean checkEarlyReference(Var v, Reference reference, Node referenceNode) {
    // Don't check the order of references in externs files.
    if (referenceNode.isFromExterns() || v.isImplicitGoogNamespace()) {
      return false;
    }
    // Special case to deal with var goog = goog || {}. Note that
    // let x = x || {} is illegal, just like var y = x || {}; let x = y;
    if (v.isVar()) {
      Node curr = reference.getParent();
      while (curr.isOr() && curr.getParent().getFirstChild() == curr) {
        curr = curr.getParent();
      }
      if (curr.isName() && curr.getString().equals(v.getName())) {
        return false;
      }
    }

    // RHS of public fields are not early references
    Node referenceScopeRoot = reference.getScope().getRootNode();
    if (referenceScopeRoot.isMemberFieldDef() && !referenceScopeRoot.isStaticMember()) {
      return false;
    }

    // Only generate warnings for early references in the same function scope/global scope in
    // order to deal with possible forward declarations and recursion
    // e.g. don't warn on:
    //   function f() { return x; } f(); let x = 5;
    // We don't track where `f` is called, just where it's defined, and don't want to warn for
    //     function f() { return x; } let x = 5; f();
    // TODO(moz): See if we can remove the bypass for "goog"
    if (reference.getScope().hasSameContainerScope(v.getScope()) && !v.getName().equals("goog")) {
      compiler.report(
          JSError.make(
              reference.getNode(),
              v.isGoogModuleExports()
                  ? EARLY_EXPORTS_REFERENCE
                  : (v.isLet() || v.isConst() || v.isClass() || v.isParam())
                      ? EARLY_REFERENCE_ERROR
                      : EARLY_REFERENCE,
              v.getName()));
      return true;
    }

    return false;
  }

  // Only check for unused local if not in a goog.scope function.
  // TODO(tbreisacher): Consider moving UNUSED_LOCAL_ASSIGNMENT into its own check pass, so
  // that we can run it after goog.scope processing, and get rid of the inGoogScope check.
  private void checkForUnusedLocalVar(Var v, Reference unusedAssignment) {
    if (!v.isLocal()) {
      return;
    }
    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(unusedAssignment.getNode());
    if (jsDoc != null && jsDoc.hasTypedefType()) {
      return;
    }

    boolean inGoogScope = false;
    Scope s = v.getScope();
    if (s.isFunctionBlockScope()) {
      Node function = s.getRootNode().getParent();
      Node callee = function.getPrevious();
      inGoogScope = callee != null && GOOG_SCOPE.matches(callee);
    }

    if (inGoogScope) {
      // No warning.
      return;
    }

    if (s.isModuleScope()) {
      Node statement = NodeUtil.getEnclosingStatement(v.getNode());
      if (NodeUtil.isNameDeclaration(statement)) {
        Node lhs = statement.getFirstChild();
        Node rhs = lhs.getFirstChild();
        if (rhs != null
            && (NodeUtil.isCallTo(rhs, GOOG_FORWARD_DECLARE)
                || NodeUtil.isCallTo(rhs, GOOG_REQUIRE_TYPE)
                || NodeUtil.isCallTo(rhs, GOOG_REQUIRE)
                || rhs.isQualifiedName())) {
          // No warning. module imports will be caught by the unused-require check, and if the
          // right side is a qualified name then this is likely an alias used in type annotations.
          return;
        }
      }
    }

    compiler.report(JSError.make(unusedAssignment.getNode(), UNUSED_LOCAL_ASSIGNMENT, v.getName()));
  }
}
