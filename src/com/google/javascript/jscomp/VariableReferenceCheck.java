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

import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ReferenceCollectingCallback.BasicBlock;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Behavior;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Reference;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceMap;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Checks variables to see if they are referenced before their declaration, or
 * if they are redeclared in a way that is suspicious (i.e. not dictated by
 * control structures). This is a more aggressive version of {@link VarCheck},
 * but it lacks the cross-module checks.
 *
 * @author kushal@google.com (Kushal Dave)
 */
class VariableReferenceCheck implements HotSwapCompilerPass {

  static final DiagnosticType UNDECLARED_REFERENCE = DiagnosticType.warning(
      "JSC_REFERENCE_BEFORE_DECLARE",
      "Variable referenced before declaration: {0}");

  static final DiagnosticType REDECLARED_VARIABLE = DiagnosticType.warning(
      "JSC_REDECLARED_VARIABLE",
      "Redeclared variable: {0}");

  static final DiagnosticType AMBIGUOUS_FUNCTION_DECL =
    DiagnosticType.disabled("AMBIGUOUS_FUNCTION_DECL",
        "Ambiguous use of a named function: {0}.");

  private final AbstractCompiler compiler;
  private final CheckLevel checkLevel;

  // NOTE(nicksantos): It's a lot faster to use a shared Set that
  // we clear after each method call, because the Set never gets too big.
  private final Set<BasicBlock> blocksWithDeclarations = Sets.newHashSet();

  public VariableReferenceCheck(AbstractCompiler compiler,
      CheckLevel checkLevel) {
    this.compiler = compiler;
    this.checkLevel = checkLevel;
  }

  @Override
  public void process(Node externs, Node root) {
    ReferenceCollectingCallback callback = new ReferenceCollectingCallback(
        compiler, new ReferenceCheckingBehavior());
    callback.process(externs, root);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    ReferenceCollectingCallback callback = new ReferenceCollectingCallback(
        compiler, new ReferenceCheckingBehavior());
    callback.hotSwapScript(scriptRoot, originalRoot);
  }

  /**
   * Behavior that checks variables for redeclaration or early references
   * just after they go out of scope.
   */
  private class ReferenceCheckingBehavior implements Behavior {

    @Override
    public void afterExitScope(NodeTraversal t, ReferenceMap referenceMap) {
      // TODO(bashir) In hot-swap version this means that for global scope we
      // only go through all global variables accessed in the modified file not
      // all global variables. This should be fixed.

      // Check all vars after finishing a scope
      for (Iterator<Var> it = t.getScope().getVars(); it.hasNext();) {
        Var v = it.next();
        checkVar(v, referenceMap.getReferences(v).references);
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
      boolean isDeclaredInScope = false;
      boolean isUnhoistedNamedFunction = false;
      Reference hoistedFn = null;

      // Look for hoisted functions.
      for (Reference reference : references) {
        if (reference.isHoistedFunction()) {
          blocksWithDeclarations.add(reference.getBasicBlock());
          isDeclaredInScope = true;
          hoistedFn = reference;
          break;
        } else if (NodeUtil.isFunctionDeclaration(
            reference.getNode().getParent())) {
          isUnhoistedNamedFunction = true;
        }
      }

      for (Reference reference : references) {
        if (reference == hoistedFn) {
          continue;
        }

        BasicBlock basicBlock = reference.getBasicBlock();
        boolean isDeclaration = reference.isDeclaration();

        boolean allowDupe =
            VarCheck.hasDuplicateDeclarationSuppression(
                reference.getNode(), v);
        if (isDeclaration && !allowDupe) {
          // Look through all the declarations we've found so far, and
          // check if any of them are before this block.
          for (BasicBlock declaredBlock : blocksWithDeclarations) {
            if (declaredBlock.provablyExecutesBefore(basicBlock)) {
              // TODO(johnlenz): Fix AST generating clients that so they would
              // have property StaticSourceFile attached at each node. Or
              // better yet, make sure the generated code never violates
              // the requirement to pass aggressive var check!
              String filename = NodeUtil.getSourceName(reference.getNode());
              compiler.report(
                  JSError.make(filename,
                      reference.getNode(),
                      checkLevel,
                      REDECLARED_VARIABLE, v.name));
              break;
            }
          }
        }

        if (isUnhoistedNamedFunction && !isDeclaration && isDeclaredInScope) {
          // Only allow an unhoisted named function to be used within the
          // block it is declared.
          for (BasicBlock declaredBlock : blocksWithDeclarations) {
            if (!declaredBlock.provablyExecutesBefore(basicBlock)) {
              String filename = NodeUtil.getSourceName(reference.getNode());
              compiler.report(
                  JSError.make(filename,
                      reference.getNode(),
                      AMBIGUOUS_FUNCTION_DECL, v.name));
              break;
            }
          }
        }

        if (!isDeclaration && !isDeclaredInScope) {
          // Don't check the order of refer in externs files.
          if (!reference.getNode().isFromExterns()) {
            // Special case to deal with var goog = goog || {}
            Node grandparent = reference.getGrandparent();
            if (grandparent.isName()
                && grandparent.getString().equals(v.name)) {
              continue;
            }

            // Only generate warnings if the scopes do not match in order
            // to deal with possible forward declarations and recursion
            if (reference.getScope() == v.scope) {
              String filename = NodeUtil.getSourceName(reference.getNode());
              compiler.report(
                  JSError.make(filename,
                               reference.getNode(),
                               checkLevel,
                               UNDECLARED_REFERENCE, v.name));
            }
          }
        }

        if (isDeclaration) {
          blocksWithDeclarations.add(basicBlock);
          isDeclaredInScope = true;
        }
      }
    }
  }
}
