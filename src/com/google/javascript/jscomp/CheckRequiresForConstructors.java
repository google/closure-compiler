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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;

import java.util.List;
import java.util.Set;

/**
 * This pass walks the AST to create a Collection of 'new' nodes and
 * 'goog.require' nodes. It reconciles these Collections, creating a
 * warning for each discrepancy.
 *
 */
class CheckRequiresForConstructors implements HotSwapCompilerPass {
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

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    Callback callback = new CheckRequiresForConstructorsCallback();
    new NodeTraversal(compiler, callback).traverseWithScope(scriptRoot,
        SyntacticScopeCreator.generateUntypedTopScope(compiler));
  }

  // Return true if the name is a class name (starts with an uppercase
  // character, but is not in all caps).
  private static boolean isClassName(String name) {
    return (name != null && name.length() > 1
            && Character.isUpperCase(name.charAt(0))
            && !name.equals(name.toUpperCase()));
  }

  // Return the shortest prefix of the className that refers to a class,
  // or null if no part refers to a class.
  private static String getOutermostClassName(String className) {
    for (String part : className.split("\\.")) {
      if (isClassName(part)) {
        return className.substring(0, className.indexOf(part) +
                                   part.length());
      }
    }

    return null;
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
      return parent == null || !parent.isScript() ||
          !t.getInput().isExtern();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.ASSIGN:
        case Token.VAR:
          maybeAddConstructor(t, n);
          break;
        case Token.FUNCTION:
          // Exclude function expressions.
          if (NodeUtil.isStatement(n)) {
            maybeAddConstructor(t, n);
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
        String outermostClassName = getOutermostClassName(className);
        boolean notProvidedByConstructors =
            (constructors == null || !constructors.contains(className));
        boolean notProvidedByRequires =
            (requires == null || (!requires.contains(className)
                                  && !requires.contains(outermostClassName)));
        if (notProvidedByConstructors && notProvidedByRequires
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

      // If the ctor is something other than a qualified name, ignore it.
      if (!qNameNode.isQualifiedName()) {
        return;
      }

      // Grab the root ctor namespace.
      Node nameNode = qNameNode;
      for (; nameNode.hasChildren(); nameNode = nameNode.getFirstChild()) {}

      // We only consider programmer-defined constructors that are
      // global variables, or are defined on global variables.
      if (!nameNode.isName()) {
        return;
      }

      String name = nameNode.getString();
      Scope.Var var = t.getScope().getVar(name);
      if (var == null || var.isLocal() || var.isExtern()) {
        return;
      }
      newNodes.add(n);
    }

    private void maybeAddConstructor(NodeTraversal t, Node n) {
      JSDocInfo info = (JSDocInfo) n.getProp(Node.JSDOC_INFO_PROP);
      if (info != null) {
        String ctorName = n.getFirstChild().getQualifiedName();
        if (info.isConstructor()) {
          constructors.add(ctorName);
        } else {
          JSTypeExpression typeExpr = info.getType();
          if (typeExpr != null) {
            JSType type = typeExpr.evaluate(t.getScope(), compiler.getTypeRegistry());
            if (type.isConstructor()) {
              constructors.add(ctorName);
            }
          }
        }
      }
    }
  }
}
