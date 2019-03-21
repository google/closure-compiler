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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.SideEffectFlags;
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
    NameBasedDefinitionProvider defFinder = new NameBasedDefinitionProvider(compiler, false);
    defFinder.process(externs, root);

    // Gather the list of function nodes that have @nosideeffects annotations.
    // For use by SetNoSideEffectCallProperty.
    NodeTraversal.traverse(
        compiler, externs, new GatherNoSideEffectFunctions());
    NodeTraversal.traverse(
        compiler, root, new GatherNoSideEffectFunctions());

    NodeTraversal.traverse(compiler, root, new SetNoSideEffectCallProperty(defFinder));
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

    switch (rhs.getToken()) {
      case ASSIGN:
      case AND:
      case CALL:
      case GETPROP:
      case GETELEM:
      case FUNCTION:
      case HOOK:
      case NAME:
      case NEW:
      case OR:
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
    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
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
    private final NameBasedDefinitionProvider defFinder;

    SetNoSideEffectCallProperty(NameBasedDefinitionProvider defFinder) {
      this.defFinder = defFinder;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      if (!NodeUtil.isCallOrNew(node)) {
        return;
      }
      Node nameNode = node.getFirstChild();
      // This is the result of an anonymous function execution. function() {}();
      if (!nameNode.isName() && !nameNode.isGetProp()) {
        return;
      }

      Collection<Definition> definitions = defFinder.getDefinitionsReferencedAt(nameNode);
      if (definitions == null) {
        return;
      }

      boolean maybeFunction = false;
      for (Definition def : definitions) {
        Node lValue = def.getLValue();
        checkNotNull(lValue);
        if (definitionTypeContainsFunctionType(def)) {
          maybeFunction = true;
          if (!noSideEffectFunctionNames.contains(lValue)) {
            return;
          }
        }
      }

      if (maybeFunction) {
        if (node.getSideEffectFlags() != SideEffectFlags.NO_SIDE_EFFECTS) {
          node.setSideEffectFlags(SideEffectFlags.NO_SIDE_EFFECTS);
          compiler.reportChangeToEnclosingScope(node);
        }
      }
    }
  }
}
