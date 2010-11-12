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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.Set;


/**
 * This pass walks the AST to create a Collection of 'new' nodes and
 * 'goog.require' nodes. It reconciles these Collections, creating a
 * warning for each discrepancy.
 *
 */
class CheckRequiresForConstructors implements CompilerPass {
  private final AbstractCompiler compiler;
  private final CodingConvention codingConvention;
  private final CheckLevel level;

  // Warnings
  static final DiagnosticType MISSING_REQUIRE_WARNING = DiagnosticType.disabled(
      "JSC_MISSING_REQUIRE_WARNING",
      "''{0}'' used but not goog.require''d");

  CheckRequiresForConstructors(AbstractCompiler compiler,
      CheckLevel level) {
    this.compiler = compiler;
    this.codingConvention = compiler.getCodingConvention();
    this.level = level;
  }

  /**
   * Uses Collections of new and goog.require nodes to create a compiler warning
   * for each new class name without a corresponding goog.require().
   */
  @Override
  public void process(Node externs, Node root) {
    Callback callback = new CheckRequiresForConstructorsCallback();
    new NodeTraversal(compiler, callback).traverseRoots(externs, root);
  }

  /**
   * This class "records" each constructor and goog.require visited and creates
   * a warning for each new node without an appropriate goog.require node.
   *
   */
  private class CheckRequiresForConstructorsCallback implements Callback {
    private final List<String> constructors = Lists.newArrayList();
    private final List<String> requires = Lists.newArrayList();
    private final List<Node> newNodes = Lists.newArrayList();

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return parent == null || parent.getType() != Token.SCRIPT ||
          !t.getInput().isExtern();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo info;
      switch (n.getType()) {
        case Token.ASSIGN:
          info = (JSDocInfo) n.getProp(Node.JSDOC_INFO_PROP);
          if (info != null && info.isConstructor()) {
            String qualifiedName = n.getFirstChild().getQualifiedName();
            constructors.add(qualifiedName);
          }
          break;
        case Token.FUNCTION:
          if (NodeUtil.isFunctionExpression(n)) {
            if (parent.getType() == Token.NAME) {
              String functionName = parent.getString();
              info = (JSDocInfo) parent.getProp(Node.JSDOC_INFO_PROP);
              if (info != null && info.isConstructor()) {
                constructors.add(functionName);
              } else {
                Node gramps = parent.getParent();
                Preconditions.checkState(
                    gramps != null && gramps.getType() == Token.VAR);
                info = (JSDocInfo) gramps.getProp(Node.JSDOC_INFO_PROP);
                if (info != null && info.isConstructor()) {
                  constructors.add(functionName);
                }
              }
            }
          } else {
            info = (JSDocInfo) n.getProp(Node.JSDOC_INFO_PROP);
            if (info != null && info.isConstructor()) {
              String functionName = n.getFirstChild().getString();
              constructors.add(functionName);
            }
          }
          break;
        case Token.CALL:
          visitCallNode(n, parent);
          break;
        case Token.SCRIPT:
          visitScriptNode(t);
          break;
        case Token.NEW:
          visitNewNode(t, n);
      }
    }

    private void visitScriptNode(NodeTraversal t) {
      Set<String> classNames = Sets.newHashSet();
      for (Node node : newNodes) {
        String className = node.getFirstChild().getQualifiedName();
        if ((constructors == null || !constructors.contains(className))
            && (requires == null || !requires.contains(className))
            && !classNames.contains(className)) {
          compiler.report(
              t.makeError(node, level, MISSING_REQUIRE_WARNING, className));
          classNames.add(className);
        }
      }
      // for the next script, if there is one, we don't want the new, ctor, and
      // require nodes to spill over.
      this.newNodes.clear();
      this.requires.clear();
      this.constructors.clear();
    }

    private void visitCallNode(Node n, Node parent) {
      String required = codingConvention.extractClassNameIfRequire(n, parent);
      if (required != null) {
        requires.add(required);
      }
    }

    private void visitNewNode(NodeTraversal t, Node n) {
      Node qNameNode = n.getFirstChild();
      String qName = qNameNode.getQualifiedName();

      // If the ctor is something other than a qualified name, ignore it.
      if (qName == null || qName.isEmpty()) {
        return;
      }

      // Grab the root ctor namespace.
      Node nameNode = qNameNode;
      for (; nameNode.hasChildren(); nameNode = nameNode.getFirstChild()) {}

      // We only consider programmer-defined constructors that are
      // global variables, or are defined on global variables.
      if (nameNode.getType() != Token.NAME) {
        return;
      }

      String name = nameNode.getString();
      Scope.Var var = t.getScope().getVar(name);
      if (var == null || var.isLocal() || var.isExtern()) {
        return;
      }
      newNodes.add(n);
    }
  }
}
