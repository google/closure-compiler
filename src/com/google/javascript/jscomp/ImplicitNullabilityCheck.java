/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.common.collect.Lists.transform;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSType;

import java.util.List;

/**
 * Warn about types in JSDoc that are implicitly nullable.
 * @author blickly@google.com (Ben Lickly)
 */
public final class ImplicitNullabilityCheck extends AbstractPostOrderCallback
    implements CompilerPass {

  public static final DiagnosticType IMPLICITLY_NULLABLE_JSDOC =
    DiagnosticType.warning(
        "JSC_IMPLICITLY_NULLABLE_JSDOC",
        "Name {0} in JSDoc is implicitly nullable.\n"
        + "Please add a '!' to make it non-nullable,"
        + " or a '?' to make it explicitly nullable.");

  private final AbstractCompiler compiler;

  public ImplicitNullabilityCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRootsEs6(compiler, this, externs, root);
  }

  /**
   * Crawls the JSDoc of the given node to find any names in JSDoc
   * that are implicitly null.
   */
  @Override
  public void visit(final NodeTraversal t, final Node n, final Node p) {
    final JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      return;
    }
    final TypeIRegistry registry = compiler.getTypeIRegistry();

    final List<Node> thrownTypes =
        transform(
            info.getThrownTypes(),
            new Function<JSTypeExpression, Node>() {
              public Node apply(JSTypeExpression expr) {
                return expr.getRoot();
              }
            });

    for (Node typeRoot : info.getTypeNodes()) {
      NodeUtil.visitPreOrder(typeRoot, new NodeUtil.Visitor() {
        public void visit(Node node) {
          if (!node.isString()) {
            return;
          }
          if (thrownTypes.contains(node)) {
            return;
          }
          Node parent = node.getParent();
          if (parent != null) {
            switch (parent.getType()) {
              case Token.BANG:
              case Token.QMARK:
              case Token.THIS:  // The names inside function(this:Foo) and
              case Token.NEW:   // function(new:Bar) are already non-null.
                return;
              case Token.PIPE: { // Inside a union
                Node gp = parent.getParent();
                if (gp != null && gp.getType() == Token.QMARK) {
                  return; // Inside an explicitly nullable union
                }
                for (Node child : parent.children()) {
                  if (child.isString() && child.getString().equals("null")
                      || child.getType() == Token.QMARK) {
                    return; // Inside a union that contains null or nullable type
                  }
                }
                break;
              }
            }
          }
          String typeName = node.getString();
          if (typeName.equals("null") || registry.getType(typeName) == null) {
            return;
          }
          JSType type = (JSType) registry.createTypeFromCommentNode(node, "[internal]", null);
          if (type.isNullable()) {
            reportWarning(t, node, typeName);
          }
        }
      }, Predicates.<Node>alwaysTrue());
    }
  }

  /**
   * Reports an implicitly nullable name in JSDoc warning.
   */
  void reportWarning(NodeTraversal t, Node n, String name) {
    compiler.report(t.makeError(n, IMPLICITLY_NULLABLE_JSDOC, name));
  }
}
