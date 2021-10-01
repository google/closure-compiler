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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

/** Warn about types in JSDoc that are implicitly nullable. */
public final class ImplicitNullabilityCheck extends AbstractPostOrderCallback
    implements CompilerPass {

  public static final DiagnosticType IMPLICITLY_NULLABLE_JSDOC =
    DiagnosticType.disabled(
        "JSC_IMPLICITLY_NULLABLE_JSDOC",
        "Name {0} in JSDoc is implicitly nullable, and is discouraged by the style guide.\n"
        + "Please add a '!' to make it non-nullable, or a '?' to make it explicitly nullable.");

  public static final DiagnosticType IMPLICITLY_NONNULL_JSDOC =
      DiagnosticType.disabled(
          "JSC_IMPLICITLY_NONNULL_JSDOC",
          "Name {0} in JSDoc is implicitly non-null, and is discouraged by the style guide.\n"
              + "Please add a '!' to make it explicit.");

  private static final ImmutableSet<String> NULLABILITY_OMITTED_TYPES =
      ImmutableSet.of(
          "*", //
          "?",
          "bigint",
          "boolean",
          "null",
          "number",
          "string",
          "symbol",
          "undefined",
          "void");

  private final AbstractCompiler compiler;

  public ImplicitNullabilityCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, this, externs, root);
  }

  /**
   * Represents the types of implicit nullability errors caught by this pass: a) implicitly nonnull
   * (missing a "!"), and b) implicitly nullable (missing a "?").
   */
  public enum Nullability {
    NONNULL,
    NULLABLE;

    public boolean isNullable() {
      return this == Nullability.NULLABLE;
    }
  }

  /**
   * Information to represent a single "implicit nullability result", including the JSDoc string
   * node that needs a "!" or "?" to be explicit, as well as an enum indicating which of the two
   * nullability cases were found ("!" or "?").
   */
  public static class Result {
    final Node node;
    final Nullability nullability;

    private Result(Node node, Nullability nullability) {
      checkArgument(node.isStringLit());
      this.node = node;
      this.nullability = nullability;
    }

    static Result create(Node node, Nullability nullability) {
      return new Result(node, nullability);
    }

    public Nullability getNullability() {
      return nullability;
    }

    public Node getNode() {
      return node;
    }
  }

  /**
   * Finds and returns all the JSDoc nodes inside the given JSDoc object whose nullability is not
   * explict, using the NodeTraversal the necessary state (current scope, etc.)
   */
  public static ImmutableList<Result> findImplicitNullabilityResults(
      final JSDocInfo info, final NodeTraversal t) {
    if (info == null) {
      return ImmutableList.of();
    }

    final ImmutableList.Builder<Result> builder = ImmutableList.builder();
    for (Node typeRoot : info.getTypeNodes()) {
      NodeUtil.visitPreOrder(
          typeRoot,
          new NodeUtil.Visitor() {
            @Override
            public void visit(Node node) {
              if (!node.isStringLit()) {
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
                      for (Node child = parent.getFirstChild();
                          child != null;
                          child = child.getNext()) {
                        if ((child.isStringLit() && child.getString().equals("null"))
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
              if (NULLABILITY_OMITTED_TYPES.contains(typeName)) {
                return;
              }
              JSTypeRegistry registry = t.getCompiler().getTypeRegistry();
              if (registry.getType(t.getScope(), typeName) == null) {
                return;
              }
              JSType type = registry.createTypeFromCommentNode(node);
              Nullability nullability =
                  type.isNullable() ? Nullability.NULLABLE : Nullability.NONNULL;
              builder.add(Result.create(node, nullability));
            }
          },
          Predicates.alwaysTrue());
    }
    return builder.build();
  }

  /** Crawls the JSDoc of the given node to find any names in JSDoc that are implicitly null. */
  @Override
  public void visit(final NodeTraversal t, final Node n, final Node p) {
    final JSDocInfo info = n.getJSDocInfo();
    for (Result r : findImplicitNullabilityResults(info, t)) {
      Node stringNode = r.getNode();
      DiagnosticType dt =
          r.getNullability().isNullable() ? IMPLICITLY_NULLABLE_JSDOC : IMPLICITLY_NONNULL_JSDOC;
      compiler.report(JSError.make(stringNode, dt, stringNode.getString()));
    }
  }
}
