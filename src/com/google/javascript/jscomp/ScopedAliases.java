/*
 * Copyright 2010 Google Inc.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.Map;

/**
 * Process aliases in goog.scope blocks.
 *
 * goog.scope(function() {
 *   var dom = goog.dom;
 *   var DIV = dom.TagName.DIV;
 *
 *   dom.createElement(DIV);
 * });
 *
 * should become
 *
 * goog.dom.createElement(goog.dom.TagName.DIV);
 *
 * @author robbyw@google.com (Robby Walker)
 */
class ScopedAliases implements CompilerPass {
  /** Name used to denote an scoped function block used for aliasing. */
  static final String SCOPING_METHOD_NAME = "goog.scope";

  final AbstractCompiler compiler;

  ScopedAliases(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    Traversal traversal = new Traversal();
    NodeTraversal.traverse(compiler, root, traversal);

    // Apply the aliases.
    for (AliasedNode entry : traversal.getAliasUsages()) {
      entry.getAliasReference().getParent().replaceChild(
          entry.getAliasReference(),
          entry.getAliasDefinition().cloneTree());
    }

    // Remove the alias definitions.
    for (Node aliasDefinition : traversal.getAliasDefinitions()) {
      if (aliasDefinition.getParent().getType() == Token.VAR &&
          aliasDefinition.getParent().getChildCount() == 1) {
        aliasDefinition.getParent().detachFromParent();
      } else {
        aliasDefinition.detachFromParent();
      }
    }

    // Collapse the scopes.
    for (Node scopeCall : traversal.getScopeCalls()) {
      Node expressionWithScopeCall = scopeCall.getParent();
      Node scopeClosureBlock = scopeCall.getLastChild().getLastChild();
      scopeClosureBlock.detachFromParent();
      expressionWithScopeCall.getParent().replaceChild(
          expressionWithScopeCall,
          scopeClosureBlock);
      NodeUtil.tryMergeBlock(scopeClosureBlock);
    }

    if (traversal.getAliasUsages().size() > 0 ||
        traversal.getAliasDefinitions().size() > 0 ||
        traversal.getScopeCalls().size() > 0) {
      compiler.reportCodeChange();
    }
  }

  private class AliasedNode {
    private final Node aliasReference;

    private final Node aliasDefinition;

    AliasedNode(Node aliasReference, Node aliasDefinition) {
      this.aliasReference = aliasReference;
      this.aliasDefinition = aliasDefinition;
    }

    public Node getAliasReference() {
      return aliasReference;
    }

    public Node getAliasDefinition() {
      return aliasDefinition;
    }
  }

  private class Traversal implements NodeTraversal.ScopedCallback {
    // The job of this class is to collect these three data sets.
    private List<Node> aliasDefinitions = Lists.newArrayList();

    private List<Node> scopeCalls = Lists.newArrayList();

    private List<AliasedNode> aliasUsages = Lists.newArrayList();

    // This map is temporary and cleared for each scope.
    private Map<String, Node> aliases = Maps.newHashMap();


    List<Node> getAliasDefinitions() {
      return aliasDefinitions;
    }

    private List<AliasedNode> getAliasUsages() {
      return aliasUsages;
    }

    List<Node> getScopeCalls() {
      return scopeCalls;
    }

    private boolean isCallToScopeMethod(Node n) {
      return n.getType() == Token.CALL &&
          SCOPING_METHOD_NAME.equals(n.getFirstChild().getQualifiedName());
    }

    @Override
    public void enterScope(NodeTraversal t) {
    }

    @Override
    public void exitScope(NodeTraversal t) {
      if (t.getScopeDepth() == 2) {
        aliases.clear();
      }
    }

    @Override
    public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.FUNCTION && t.inGlobalScope()) {
        // Do not traverse in to functions except for goog.scope functions.
        if (parent == null || !isCallToScopeMethod(parent)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (isCallToScopeMethod(n)) {
        // TODO(robbyw): Report an error if the call is not at the root of an
        // expression: NodeUtil.isExpressionNode(parent)
        // TODO(robbyw): Report an error if the parameter is not anonymous
        // or has extra parameters.
        // Node firstParam = n.getFirstChild().getNext();
        // NodeUtil.isFunction(firstParam);
        // NodeUtil.getFunctionName(firstParam).isEmpty();
        // NodeUtil.getFnParameters(firstParam).hasChildren();
        scopeCalls.add(n);
      }

      if (t.getScopeDepth() == 2) {
        if (n.getType() == Token.NAME && parent.getType() == Token.VAR) {
          if (n.hasChildren() && n.isQualifiedName()) {
            // TODO(robbyw): What other checks go here?

            aliases.put(n.getString(), n.getFirstChild());
            aliasDefinitions.add(n);

            // Undeclare the variable.
            t.getScope().undeclare(t.getScope().getVar(n.getString()));

            // If we found an alias, we are done.
            return;
          }
        }
      }

      if (t.getScopeDepth() >= 2) {
        if (n.getType() == Token.NAME) {
          // TODO(robbyw): Check if the name is overridden locally.
          // TODO(robbyw): Check if this is a place where the name is being set.
          Node aliasedNode = aliases.get(n.getString());
          // The variable should not exist since we undeclared it when we found
          // it.  If it does exist, it's because it's been overridden.
          if (t.getScope().getVar(n.getString()) == null &&
              aliasedNode != null) {
            // Note, to support the transitive case, it's important we don't
            // clone aliasedNode here.  For example,
            // var g = goog; var d = g.dom; d.createElement('DIV');
            // The node in aliasedNode (which is "g") will be replaced in the
            // changes pass above with "goog".  If we cloned here, we'd end up
            // with <code>g.dom.createElement('DIV')</code>.
            aliasUsages.add(new AliasedNode(n, aliasedNode));
          }
        }

        // TODO(robbyw): Disallow RETURN nodes and THIS nodes.
      }
    }
  }
}
