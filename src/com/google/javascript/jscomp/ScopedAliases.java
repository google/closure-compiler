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

  private final AbstractCompiler compiler;

  // Errors
  static final DiagnosticType GOOG_SCOPE_USED_IMPROPERLY = DiagnosticType.error(
      "JSC_GOOG_SCOPE_USED_IMPROPERLY",
      "The call to goog.scope must be alone in a single statement.");

  static final DiagnosticType GOOG_SCOPE_HAS_BAD_PARAMETERS =
      DiagnosticType.error(
          "JSC_GOOG_SCOPE_HAS_BAD_PARAMETERS",
          "The call to goog.scope must take only a single parameter.  It must" +
              " be an anonymous function that itself takes no parameters.");

  static final DiagnosticType GOOG_SCOPE_REFERENCES_THIS = DiagnosticType.error(
      "JSC_GOOG_SCOPE_REFERENCES_THIS",
      "The body of a goog.scope function cannot reference 'this'.");

  static final DiagnosticType GOOG_SCOPE_USES_RETURN = DiagnosticType.error(
      "JSC_GOOG_SCOPE_USES_RETURN",
      "The body of a goog.scope function cannot use 'return'.");

  static final DiagnosticType GOOG_SCOPE_ALIAS_REDEFINED = DiagnosticType.error(
      "JSC_GOOG_SCOPE_ALIAS_REDEFINED",
      "The alias {0} is assigned a value more than once.");

  ScopedAliases(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    Traversal traversal = new Traversal();
    NodeTraversal.traverse(compiler, root, traversal);

    if (!traversal.hasErrors()) {
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

    private boolean hasErrors = false;

    List<Node> getAliasDefinitions() {
      return aliasDefinitions;
    }

    private List<AliasedNode> getAliasUsages() {
      return aliasUsages;
    }

    List<Node> getScopeCalls() {
      return scopeCalls;
    }

    boolean hasErrors() {
      return hasErrors;
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

    private void report(NodeTraversal t, Node n, DiagnosticType error,
        String... arguments) {
      compiler.report(t.makeError(n, error, arguments));
      hasErrors = true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (isCallToScopeMethod(n)) {
        if (!NodeUtil.isExpressionNode(parent)) {
          report(t, n, GOOG_SCOPE_USED_IMPROPERLY);
        }
        if (n.getChildCount() != 2) {
          // The goog.scope call should have exactly 1 parameter.  The first
          // child is the "goog.scope" and the second should be the parameter.
          report(t, n, GOOG_SCOPE_HAS_BAD_PARAMETERS);
        } else {
          Node anonymousFnNode = n.getChildAtIndex(1);
          if (!NodeUtil.isFunction(anonymousFnNode) ||
              NodeUtil.getFunctionName(anonymousFnNode) != null ||
              NodeUtil.getFnParameters(anonymousFnNode).hasChildren()) {
            report(t, anonymousFnNode, GOOG_SCOPE_HAS_BAD_PARAMETERS);
          } else {
            scopeCalls.add(n);
          }
        }
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
          if (NodeUtil.isAssignmentOp(parent)) {
            report(t, n, GOOG_SCOPE_ALIAS_REDEFINED, n.getString());
          }
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

        if (n.getType() == Token.RETURN) {
          report(t, n, GOOG_SCOPE_USES_RETURN);
        }

        if (n.getType() == Token.THIS) {
          report(t, n, GOOG_SCOPE_REFERENCES_THIS);
        }
      }
    }
  }
}
