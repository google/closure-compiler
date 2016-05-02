/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Set the NoSideEffects property for function and constructor calls
 * that refer to functions that are known to have no side effects.
 * Current implementation relies on @nosideeffects annotations at
 * function definition sites; eventually we should traverse function
 * bodies to determine if they have side effects.
 *
 */
class MarkNoSideEffectCalls implements CompilerPass {
  private final AbstractCompiler compiler;

  // Left hand side expression associated with a function node that
  // has a @nosideeffects annotation.
  private final Set<Node> noSideEffectFunctionNames;

  MarkNoSideEffectCalls(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.noSideEffectFunctionNames = new HashSet<>();
  }

  @Override
  public void process(Node externs, Node root) {
    SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
    defFinder.process(externs, root);

    // Gather the list of function nodes that have @nosideeffects annotations.
    // For use by SetNoSideEffectCallProperty.
    NodeTraversal.traverseEs6(
        compiler, externs, new GatherNoSideEffectFunctions(true));
    NodeTraversal.traverseEs6(
        compiler, root, new GatherNoSideEffectFunctions(false));

    NodeTraversal.traverseEs6(compiler, root,
                           new SetNoSideEffectCallProperty(defFinder));
  }

  /**
   * Determines if the type of the value of the RHS expression can
   * be a function node.
   */
  private static boolean definitionTypeContainsFunctionType(Definition def) {
    Node rhs = def.getRValue();
    if (rhs == null) {
      return true;
    }

    switch (rhs.getType()) {
      case Token.ASSIGN:
      case Token.AND:
      case Token.CALL:
      case Token.GETPROP:
      case Token.GETELEM:
      case Token.FUNCTION:
      case Token.HOOK:
      case Token.NAME:
      case Token.NEW:
      case Token.OR:
        return true;
      default:
        return false;
    }
  }

  /**
   * Get the value of the @nosideeffects annotation stored in the
   * doc info.
   */
  private static boolean hasNoSideEffectsAnnotation(Node node) {
    JSDocInfo docInfo = node.getJSDocInfo();
    return docInfo != null && docInfo.isNoSideEffects();
  }

  /**
   * Gather function nodes that have @nosideeffects annotations.
   */
  private class GatherNoSideEffectFunctions extends AbstractPostOrderCallback {
    private final boolean inExterns;

    GatherNoSideEffectFunctions(boolean inExterns) {
      this.inExterns = inExterns;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      if (!inExterns && hasNoSideEffectsAnnotation(node)) {
        traversal.report(node, PureFunctionIdentifier.INVALID_NO_SIDE_EFFECT_ANNOTATION);
      }

      if (node.isGetProp()) {
        if (parent.isExprResult() &&
            hasNoSideEffectsAnnotation(node)) {
          noSideEffectFunctionNames.add(node);
        }
      } else if (node.isFunction()) {

        // The annotation may attached to the function node, the
        // variable declaration or assignment expression.
        boolean hasAnnotation = hasNoSideEffectsAnnotation(node);
        List<Node> nameNodes = new ArrayList<>();
        nameNodes.add(node.getFirstChild());

        if (parent.isName()) {
          Node gramp = parent.getParent();
          if (gramp.isVar() &&
              gramp.hasOneChild() &&
              hasNoSideEffectsAnnotation(gramp)) {
            hasAnnotation = true;
          }

          nameNodes.add(parent);
        } else if (parent.isAssign()) {
          if (hasNoSideEffectsAnnotation(parent)) {
            hasAnnotation = true;
          }

          nameNodes.add(parent.getFirstChild());
        }

        if (hasAnnotation) {
          noSideEffectFunctionNames.addAll(nameNodes);
        }
      }
    }
  }

  /**
   * Set the no side effects property for CALL and NEW nodes that
   * refer to function names that are known to have no side effects.
   */
  private class SetNoSideEffectCallProperty extends AbstractPostOrderCallback {
    private final SimpleDefinitionFinder defFinder;

    SetNoSideEffectCallProperty(SimpleDefinitionFinder defFinder) {
      this.defFinder = defFinder;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      if (!node.isCall() && !node.isNew()) {
        return;
      }

      Collection<Definition> definitions =
          defFinder.getDefinitionsReferencedAt(node.getFirstChild());
      if (definitions == null) {
        return;
      }

      boolean maybeFunction = false;
      for (Definition def : definitions) {
        Node lValue = def.getLValue();
        Preconditions.checkNotNull(lValue);
        if (definitionTypeContainsFunctionType(def)) {
          maybeFunction = true;
          if (!noSideEffectFunctionNames.contains(lValue)) {
            return;
          }
        }
      }

      if (maybeFunction) {
        node.setSideEffectFlags(Node.NO_SIDE_EFFECTS);
      }
    }
  }
}
