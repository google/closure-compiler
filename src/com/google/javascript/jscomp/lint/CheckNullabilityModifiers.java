/*
 * Copyright 2018 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.lint;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Checks for missing or redundant nullability modifiers.
 *
 * <p>Primitive and literal types should not be preceded by a `!` modifier. Reference types must be
 * preceded by a `?` or `!` modifier.
 */
public class CheckNullabilityModifiers extends AbstractPostOrderCallback implements CompilerPass {

  /** The diagnostic for a missing nullability modifier. */
  public static final DiagnosticType MISSING_NULLABILITY_MODIFIER_JSDOC =
      DiagnosticType.disabled(
          "JSC_MISSING_NULLABILITY_MODIFIER_JSDOC",
          "{0} is a reference type with no nullability modifier, "
              + "which is disallowed by the style guide.\n"
              + "Please add a '!' to make it explicitly non-nullable, "
              + "or a '?' to make it explicitly nullable.");

  /** The diagnostic for a redundant nullability modifier. */
  public static final DiagnosticType REDUNDANT_NULLABILITY_MODIFIER_JSDOC =
      DiagnosticType.disabled(
          "JSC_REDUNDANT_NULLABILITY_MODIFIER_JSDOC",
          "{0} is a non-reference type which is already non-nullable.\n"
              + "Please remove the redundant '!', which is disallowed by the style guide.");

  private static final ImmutableSet<String> PRIMITIVE_TYPE_NAMES =
      ImmutableSet.of("boolean", "number", "string", "symbol", "undefined", "null");

  private final AbstractCompiler compiler;

  public CheckNullabilityModifiers(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, this, externs, root);
  }

  @Override
  public void visit(final NodeTraversal t, final Node n, final Node p) {
    final JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      return;
    }
    if (info.hasType()) {
      checkTypeExpression(t, info.getType(), false);
    }
    for (String param : info.getParameterNames()) {
      if (info.hasParameterType(param)) {
        checkTypeExpression(t, info.getParameterType(param), false);
      }
    }
    if (info.hasReturnType()) {
      checkTypeExpression(t, info.getReturnType(), false);
    }
    if (info.hasEnumParameterType()) {
      // JSDocInfoParser wraps the @enum type in an artificial '!' if it's not a primitive type.
      // Thus a missing modifier warning can never trigger, but a redundant modifier warning can.
      checkTypeExpression(t, info.getEnumParameterType(), false);
    }
    if (info.hasTypedefType()) {
      checkTypeExpression(t, info.getTypedefType(), false);
    }
    if (info.hasThisType()) {
      // JSDocInfoParser wraps the @this type in an artificial '!'.
      // Thus a missing modifier warning can never trigger, but a top-level '!' should be ignored if
      // it precedes a primitive or literal type; otherwise it will trigger a spurious redundant
      // modifier warning.
      checkTypeExpression(t, info.getThisType(), true);
    }
    for (JSTypeExpression expr : info.getThrownTypes()) {
      checkTypeExpression(t, expr, false);
    }
    // JSDocInfoParser enforces the @extends and @implements types to be unqualified in the source
    // code, so we don't need to check them.
  }

  private static void checkTypeExpression(
      final NodeTraversal t, JSTypeExpression expr, boolean hasArtificialTopLevelBang) {
    final Node root = expr.getRoot();
    NodeUtil.visitPreOrder(
        root,
        node -> {
          Node parent = node.getParent();

          boolean isPrimitiveOrLiteral =
              isPrimitiveType(node) || isFunctionLiteral(node) || isRecordLiteral(node);
          boolean isReference = isReferenceType(node);

          // Whether the node is preceded by a '!' or '?'.
          boolean hasBang = parent != null && parent.getToken() == Token.BANG;
          boolean hasQmark = parent != null && parent.getToken() == Token.QMARK;

          // Whether the node is preceded by a '!' that wasn't artificially added.
          boolean hasNonArtificialBang = hasBang && !(hasArtificialTopLevelBang && parent == root);

          // Whether the node is the type in function(new:T) or function(this:T).
          boolean isNewOrThis = parent != null && (parent.isNew() || parent.isThis());

          if (isReference && !hasBang && !hasQmark && !isNewOrThis) {
            t.report(node, MISSING_NULLABILITY_MODIFIER_JSDOC, getReportedTypeName(node));
          } else if (isPrimitiveOrLiteral && hasNonArtificialBang) {
            t.report(parent, REDUNDANT_NULLABILITY_MODIFIER_JSDOC, getReportedTypeName(node));
          }
        });
  }

  private static boolean isPrimitiveType(Node node) {
    return node.isString() && PRIMITIVE_TYPE_NAMES.contains(node.getString());
  }

  private static boolean isReferenceType(Node node) {
    return node.isString() && !PRIMITIVE_TYPE_NAMES.contains(node.getString());
  }

  private static boolean isFunctionLiteral(Node node) {
    return node.isFunction();
  }

  private static boolean isRecordLiteral(Node node) {
    return node.getToken() == Token.LC;
  }

  private static String getReportedTypeName(Node node) {
    if (isFunctionLiteral(node)) {
      return "Function";
    }
    if (isRecordLiteral(node)) {
      return "Record literal";
    }
    Preconditions.checkState(isPrimitiveType(node) || isReferenceType(node));
    return node.getString();
  }
}
