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
 * Checks for redundant nullability modifiers.
 *
 * <p>A nullability modifier '!' is redundant if it precedes a value type or a record literal.
 */
public class CheckRedundantNullabilityModifier extends AbstractPostOrderCallback
    implements CompilerPass {

  /** The diagnostic for a redundant nullability modifier. */
  public static final DiagnosticType REDUNDANT_NULLABILITY_MODIFIER_JSDOC =
      DiagnosticType.warning(
          "JSC_REDUNDANT_NULLABILITY_MODIFIER_JSDOC",
          "Type is already not null. Please remove the redundant '!'.");

  private static final ImmutableSet<String> PRIMITIVE_TYPE_NAMES =
      ImmutableSet.of("boolean", "number", "string", "symbol", "undefined");

  private final AbstractCompiler compiler;

  public CheckRedundantNullabilityModifier(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRootsEs6(compiler, this, externs, root);
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
      checkTypeExpression(t, info.getEnumParameterType(), false);
    }
    if (info.hasTypedefType()) {
      checkTypeExpression(t, info.getTypedefType(), false);
    }
    // JSDocInfoParser automatically prepends a '!' to the type associated with a @this annotation.
    // It should be ignored since it doesn't actually exist in the source file.
    if (info.hasThisType()) {
      checkTypeExpression(t, info.getThisType(), true);
    }
    // We don't need to check @extends and @implements annotations since they never refer to a
    // primitive type.
    // TODO(tjgq): Should we check @throws annotations? The Google style guide forbids throwing a
    // primitive type, so the warning would be somewhat misguided.
  }

  private static void checkTypeExpression(
      final NodeTraversal t, JSTypeExpression expr, boolean isTopLevelBangAllowed) {
    NodeUtil.visitPreOrder(
        expr.getRoot(),
        node -> {
          Node parent = node.getParent();
          Node grandparent = parent != null ? parent.getParent() : null;

          boolean hasModifier = parent != null && parent.getToken() == Token.BANG;
          boolean isModifierRedundant =
              isPrimitiveType(node) || node.isFunction() || isRecordLiteral(node);
          boolean isRedundantModifierAllowed = isTopLevelBangAllowed && grandparent == null;

          if (hasModifier && isModifierRedundant && !isRedundantModifierAllowed) {
            t.report(parent, REDUNDANT_NULLABILITY_MODIFIER_JSDOC);
          }
        });
  }

  private static boolean isPrimitiveType(Node node) {
    return node.isString() && PRIMITIVE_TYPE_NAMES.contains(node.getString());
  }

  private static boolean isRecordLiteral(Node node) {
    return node.getToken() == Token.LC;
  }
}
