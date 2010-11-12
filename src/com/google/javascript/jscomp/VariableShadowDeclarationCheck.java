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

import com.google.javascript.jscomp.CheckLevel;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;

import java.util.Iterator;
import java.util.Set;


/**
 * Warn the user if a local variable declaration that shadows a global variable
 * that has been marked with {@code @noshadow}.
 *
 * <p>Browser global variables such as {@code self} shouldn't be shadowed
 * because it is highly error prone to do so.
 *
 */
class VariableShadowDeclarationCheck implements CompilerPass {

  static final DiagnosticType SHADOW_VAR_ERROR = DiagnosticType.error(
      "JSC_REDECL_NOSHADOW_VARIABLE",
      "Highly error prone shadowing of variable name {0}." +
      "Consider using a different local variable name.");

  private final AbstractCompiler compiler;
  private final CheckLevel checkLevel;
  private final Set<String> externalNoShadowVariableNames;


  VariableShadowDeclarationCheck(AbstractCompiler compiler,
      CheckLevel checkLevel) {
    this.compiler = compiler;
    this.checkLevel = checkLevel;
    this.externalNoShadowVariableNames = Sets.newHashSet();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs,
                           new NoShadowAnnotationGatheringCallback());
    NodeTraversal.traverse(compiler, root,
                           new ShadowDeclarationCheckingCallback());
  }

  /**
   * Callback that gathers @noshadow annotations that appear in the
   * externs tree.
   */
  private class NoShadowAnnotationGatheringCallback implements ScopedCallback {
    @Override
    public void enterScope(NodeTraversal t) {
      Scope scope = t.getScope();
      for (Iterator<Var> vars = scope.getVars(); vars.hasNext();) {
        Var var = vars.next();
        if (var.isNoShadow()) {
          externalNoShadowVariableNames.add(var.getName());
        }
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
    }

    @Override
    public boolean shouldTraverse(
        NodeTraversal nodeTraversal, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
    }
  }

  /**
   * Callback that emits warnings for shadowed global variables
   * with @noshadow annotations, and shadowed local variables.
   */
  private class ShadowDeclarationCheckingCallback implements ScopedCallback {
    @Override
    public void enterScope(NodeTraversal t) {
      if (t.inGlobalScope()) {
        return;
      }

      Scope scope = t.getScope();
      Scope parentScope = scope.getParent();
      for (Iterator<Var> vars = scope.getVars(); vars.hasNext();) {
        Var var = vars.next();

        if (externalNoShadowVariableNames.contains(var.getName())) {
          compiler.report(
              t.makeError(var.nameNode, checkLevel,
                  SHADOW_VAR_ERROR, var.getName()));
          continue;
        }

        Var shadowedVar = parentScope.getVar(var.getName());
        if ((shadowedVar != null) &&
            (shadowedVar.isNoShadow() || shadowedVar.isLocal())) {
          compiler.report(
              t.makeError(var.nameNode, checkLevel,
                  SHADOW_VAR_ERROR, var.getName()));
          continue;
        }
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
    }

    @Override
    public boolean shouldTraverse(
        NodeTraversal nodeTraversal, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
    }
  }
}
