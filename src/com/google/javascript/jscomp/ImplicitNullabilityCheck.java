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
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.List;

/**
 * Warn about types in JSDoc that are implicitly nullable.
 */
public final class ImplicitNullabilityCheck extends AbstractPostOrderCallback
    implements CompilerPass {

  public static final DiagnosticType IMPLICITLY_NULLABLE_JSDOC =
    DiagnosticType.disabled(
        "JSC_IMPLICITLY_NULLABLE_JSDOC",
        "Name {0} in JSDoc is implicitly nullable, and is discouraged by the style guide.\n"
        + "Please add a '!' to make it non-nullable, or a '?' to make it explicitly nullable.");

  private final AbstractCompiler compiler;

  public ImplicitNullabilityCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, this, externs, root);
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
    final JSTypeRegistry registry = compiler.getTypeRegistry();

    final List<Node> thrownTypes =
        transform(
            info.getThrownTypes(),
            new Function<JSTypeExpression, Node>() {
              @Override
              public Node apply(JSTypeExpression expr) {
                return expr.getRoot();
              }
            });

    final Scope scope = t.getScope();
    for (Node typeRoot : info.getTypeNodes()) {
      NodeUtil.visitPreOrder(
          typeRoot,
          new NodeUtil.Visitor() {
            @Override
            public void visit(Node node) {
              if (!node.isString()) {
                return;
              }
              if (thrownTypes.contains(node)) {
                return;
              }
              Node parent = node.getParent();
              if (parent != null) {
                switch (parent.getToken()) {
                  case BANG:
                  case QMARK:
                  case THIS: // The names inside function(this:Foo) and
                  case NEW: // function(new:Bar) are already non-null.
                  case TYPEOF: // Names after 'typeof' don't have nullability.
                    return;
                  case PIPE:
                    { // Inside a union
                      Node gp = parent.getParent();
                      if (gp != null && gp.getToken() == Token.QMARK) {
                        return; // Inside an explicitly nullable union
                      }
                      for (Node child : parent.children()) {
                        if ((child.isString() && child.getString().equals("null"))
                            || child.getToken() == Token.QMARK) {
                          return; // Inside a union that contains null or nullable type
                        }
                      }
                      break;
                    }
                  default:
                    break;
                }
              }
              String typeName = node.getString();
              if (typeName.equals("null") || registry.getType(scope, typeName) == null) {
                return;
              }
              JSType type = registry.createTypeFromCommentNode(node);
              if (type.isNullable()) {
                compiler.report(JSError.make(node, IMPLICITLY_NULLABLE_JSDOC, typeName));
              }
            }
          },
          Predicates.alwaysTrue());
    }
  }
}
