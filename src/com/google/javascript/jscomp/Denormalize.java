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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Behavior;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * The goal with this pass is to reverse the simplifications done in the normalization pass that are
 * not handled by other passes (such as CollapseVariableDeclarations) to avoid making the resulting
 * code larger.
 *
 * <p>Currently this pass only does a few things:
 *
 * <p>1. Push statements into for-loop initializer. This: var a = 0; for(;a<0;a++) {} becomes:
 * for(var a = 0;a<0;a++) {}
 *
 * <p>2. Fold assignments like x = x + 1 into x += 1
 *
 * <p>3. Inline 'var' keyword. For instance: <code>
 *   var x;
 *   if (y) { x = 0; }
 * </code> becomes <code>if (y) { var x = 0; }</code>, effectively undoing what {@link
 * HoistVarsOutOfBlocks} does.
 *
 * @author johnlenz@google.com (johnlenz)
 */
class Denormalize implements CompilerPass, Callback, Behavior {

  private final AbstractCompiler compiler;

  Denormalize(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    // Don't inline the VAR declaration if this compilation involves old-style ctemplates.
    if (compiler.getOptions().syntheticBlockStartMarker == null) {
      (new ReferenceCollectingCallback(compiler, this, new Es6SyntacticScopeCreator(compiler)))
          .process(root);
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  /**
   * Implements step 3 (inlining the var keyword).
   */
  @Override
  public void afterExitScope(NodeTraversal t, ReferenceMap referenceMap) {
    Node scopeRoot = t.getScopeRoot();
    if (scopeRoot.isBlock() && scopeRoot.getParent().isFunction()) {
      boolean changed = false;
      for (Var v : t.getScope().getVarIterable()) {
        ReferenceCollection references = referenceMap.getReferences(v);
        Reference declaration = null;
        Reference assign = null;
        for (Reference r : references) {
          if (r.isVarDeclaration()
              && NodeUtil.isStatement(r.getNode().getParent())
              && !r.isInitializingDeclaration()) {
            declaration = r;
          } else if (assign == null
              && r.isSimpleAssignmentToName()
              && r.getScope().getClosestHoistScope().equals(t.getScope())) {
            assign = r;
          }
        }
        if (declaration != null && assign != null) {
          Node lhs = assign.getNode();
          Node assignNode = lhs.getParent();
          if (assignNode.getParent().isExprResult()) {
            Node rhs = lhs.getNext();
            assignNode
                .getGrandparent()
                .replaceChild(assignNode.getParent(), IR.var(lhs.detach(), rhs.detach()));

            Node var = declaration.getNode().getParent();
            checkState(var.isVar(), var);
            NodeUtil.removeChild(var, declaration.getNode());

            changed = true;
          }
        }
      }

      if (changed) {
        t.reportCodeChange();
      }
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    maybeCollapseIntoForStatements(n, parent);
    maybeCollapseAssignShorthand(n, parent);
  }

  /**
   * Collapse VARs and EXPR_RESULT node into FOR loop initializers where
   * possible.
   */
  private void maybeCollapseIntoForStatements(Node n, Node parent) {
    // Only SCRIPT, BLOCK, and LABELs can have FORs that can be collapsed into.
    // LABELs are not supported here.
    if (parent == null || !NodeUtil.isStatementBlock(parent)) {
      return;
    }

    // Is the current node something that can be in a for loop initializer?
    if (!n.isExprResult() && !n.isVar()) {
      return;
    }

    // Is the next statement a valid FOR?
    Node nextSibling = n.getNext();
    if (nextSibling == null) {
      return;
    } else if (nextSibling.isForIn() || nextSibling.isForOf()) {
      Node forNode = nextSibling;
      Node forVar = forNode.getFirstChild();
      if (forVar.isName()
          && n.isVar() && n.hasOneChild()) {
        Node name = n.getFirstChild();
        if (!name.hasChildren()
            && forVar.getString().equals(name.getString())) {
          // OK, the names match, and the var declaration does not have an
          // initializer. Move it into the loop.
          parent.removeChild(n);
          forNode.replaceChild(forVar, n);
          compiler.reportChangeToEnclosingScope(parent);
        }
      }
    } else if (nextSibling.isVanillaFor() && nextSibling.getFirstChild().isEmpty()) {

      // Does the current node contain an in operator?  If so, embedding
      // the expression in a for loop can cause some JavaScript parsers (such
      // as the PlayStation 3's browser based on Access's NetFront
      // browser) to fail to parse the code.
      // See bug 1778863 for details.
      if (NodeUtil.containsType(n, Token.IN)) {
        return;
      }

      // Move the current node into the FOR loop initializer.
      Node forNode = nextSibling;
      Node oldInitializer = forNode.getFirstChild();
      parent.removeChild(n);

      Node newInitializer;
      if (n.isVar()) {
        newInitializer = n;
      } else {
        // Extract the expression from EXPR_RESULT node.
        checkState(n.hasOneChild(), n);
        newInitializer = n.getFirstChild();
        n.removeChild(newInitializer);
      }

      forNode.replaceChild(oldInitializer, newInitializer);

      compiler.reportChangeToEnclosingScope(forNode);
    }
  }

  private void maybeCollapseAssignShorthand(Node n, Node parent) {
    if (n.isAssign() && n.getFirstChild().isName()
        && NodeUtil.hasCorrespondingAssignmentOp(n.getLastChild())
        && n.getLastChild().getFirstChild().isName()) {
      Node op = n.getLastChild();
      Token assignOp = NodeUtil.getAssignOpFromOp(op);
      if (n.getFirstChild().getString().equals(op.getFirstChild().getString())) {
        op.setToken(assignOp);
        Node opDetached = op.detach();
        opDetached.setJSDocInfo(n.getJSDocInfo());
        parent.replaceChild(n, opDetached);
        compiler.reportChangeToEnclosingScope(parent);
      }
    }
  }
}
