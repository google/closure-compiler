/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Moves vars in blocks to the top of function scopes, if they are used outside of the block
 * they're in. For instance
 *
 * <pre>
 *
 *   if (someCondition) {
 *     var a = 1;
 *   }
 *   var b = a || 2;
 *
 * </pre>
 *
 * becomes
 *
 * <pre>
 *   var a;
 *   if (someCondition) {
 *     a = 1;
 *   }
 *   var b = a || 2;
 * <pre>
 *
 * This runs just before the Normalize pass to simplify some of the logic in Normalize. Note that
 * vars in for loops (e.g. {@code for (var x = 0; ; ) {} alert(x); }) are not hoisted because they
 * don't cause any issues for the Normalize pass.
 */
class HoistVarsOutOfBlocks extends AbstractPostOrderCallback
    implements ReferenceCollectingCallback.Behavior, CompilerPass {
  private final AbstractCompiler compiler;
  private ReferenceMap refMap;
  private final ScopeCreator scopeCreator;

  HoistVarsOutOfBlocks(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.scopeCreator = new Es6SyntacticScopeCreator(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    ReferenceCollectingCallback rcc = new ReferenceCollectingCallback(compiler, this, scopeCreator);
    rcc.process(root);
  }

  /**
   * Users of this class call {@link #process(Node, Node)} on it. That method executes a {@link
   * ReferenceCollectingCallback}. At the end of every scope, this method is executed and causes
   * another traversal of the scope, which does the hoisting.
   */
  @Override
  public void afterExitScope(NodeTraversal t, ReferenceMap refMap) {
    if (!t.isHoistScope()) {
      return;
    }
    this.refMap = refMap;
    (new NodeTraversal(compiler, this, scopeCreator)).traverseAtScope(t.getScope());
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isVar() && t.getScopeRoot() != t.getClosestHoistScopeRoot()) {
      hoistVarIfNeeded(t, n, parent);
    }
  }

  private void hoistVarIfNeeded(NodeTraversal t, Node varNode, Node parent) {
    if (NodeUtil.isAnyFor(parent)) {
      // These don't need to be hoisted.
      return;
    }

    Node block = t.getScopeRoot();

    for (Node lhs : varNode.children()) {
      if (!lhs.isName()) {
        continue;
      }
      Var var = t.getScope().getVar(lhs.getString());
      ReferenceCollection refs = this.refMap.getReferences(var);
      if (refs == null) {
        continue;
      }
      boolean hoist = false;
      for (Reference r : refs) {
        if (!r.getNode().isDescendantOf(block)) {
          hoist = true;
          break;
        }
      }

      if (!hoist) {
        continue;
      }

      Node rhs = lhs.removeFirstChild();
      Node hoistRoot = t.getClosestHoistScopeRoot();
      if (hoistRoot.isRoot()) {
        hoistRoot = NodeUtil.getEnclosingScript(varNode);
      }

      if (rhs == null) {
        // Note that lhs.getParent() may not be varNode, because of the way replaceDeclarationChild
        // splits var nodes up.
        NodeUtil.removeChild(lhs.getParent(), lhs);
      } else {
        Node exprAssign = IR.exprResult(IR.assign(lhs.cloneNode(), rhs));
        exprAssign.useSourceInfoIfMissingFromForTree(varNode);
        NodeUtil.replaceDeclarationChild(lhs, exprAssign);
      }
      hoistRoot.addChildToFront(IR.var(lhs.cloneNode()).useSourceInfoIfMissingFromForTree(lhs));
      t.reportCodeChange();
    }
  }
}
