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
import java.util.HashSet;

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

  /** The diagnostic for a missing nullability modifier, where the value is clearly nullable. */
  public static final DiagnosticType NULL_MISSING_NULLABILITY_MODIFIER_JSDOC =
      DiagnosticType.disabled(
          "JSC_NULL_MISSING_NULLABILITY_MODIFIER_JSDOC",
          "{0} is a reference type with no nullability modifier that is explicitly set to null.\n"
              + "Add a '?' to make it explicitly nullable.");

  /** The diagnostic for a redundant nullability modifier. */
  public static final DiagnosticType REDUNDANT_NULLABILITY_MODIFIER_JSDOC =
      DiagnosticType.disabled(
          "JSC_REDUNDANT_NULLABILITY_MODIFIER_JSDOC",
          "{0} is a non-reference type which is already non-nullable.\n"
              + "Please remove the redundant '!', which is disallowed by the style guide.");

  /** The set of primitive type names. Note that `void` is a synonym for `undefined`. */
  private static final ImmutableSet<String> PRIMITIVE_TYPE_NAMES =
      ImmutableSet.of("boolean", "number", "string", "symbol", "undefined", "void", "null");

  private final AbstractCompiler compiler;

  // Store the candidate warnings and template types found while traversing a single script node.
  private final HashSet<Node> redundantCandidates = new HashSet<>();
  private final HashSet<Node> missingCandidates = new HashSet<>();
  private final HashSet<Node> nullMissingCandidates = new HashSet<>();
  private final HashSet<String> templateTypeNames = new HashSet<>();

  public CheckNullabilityModifiers(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, this, externs, root);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    JSDocInfo info = n.getJSDocInfo();
    if (info != null) {
      templateTypeNames.addAll(info.getTemplateTypeNames());
      if (info.hasType()) {
        handleHasType(info.getType(), n);
      }
      for (String param : info.getParameterNames()) {
        if (info.hasParameterType(param)) {
          visitTypeExpression(info.getParameterType(param), false);
        }
      }
      if (info.hasReturnType()) {
        visitTypeExpression(info.getReturnType(), false);
      }
      if (info.hasEnumParameterType()) {
        // JSDocInfoParser wraps the @enum type in an artificial '!' if it's not a primitive type.
        // Thus a missing modifier warning can never trigger, but a redundant modifier warning can.
        visitTypeExpression(info.getEnumParameterType(), false);
      }
      if (info.hasTypedefType()) {
        visitTypeExpression(info.getTypedefType(), false);
      }
      if (info.hasThisType()) {
        // JSDocInfoParser wraps the @this type in an artificial '!'. Thus a missing modifier
        // warning can never trigger, but a top-level '!' should be ignored if it precedes a
        // primitive or literal type; otherwise it will trigger a spurious redundant modifier
        // warning.
        visitTypeExpression(info.getThisType(), true);
      }
      for (JSTypeExpression expr : info.getThrownTypes()) {
        visitTypeExpression(expr, false);
      }
      // JSDocInfoParser enforces the @extends and @implements types to be unqualified in the source
      // code, so we don't need to check them.
    }

    if (n.isScript()) {
      // If exiting a script node, report applicable warnings and clear the maps.
      report(t);
      redundantCandidates.clear();
      missingCandidates.clear();
      nullMissingCandidates.clear();
      templateTypeNames.clear();
      return;
    }
  }

  private void handleHasType(JSTypeExpression expr, Node n) {
    // Check if the type is explicitly set to null, if so use NULL_MISSING_NULLABILITY diagnostic.
    if (NodeUtil.isNameDeclOrSimpleAssignLhs(n.getFirstChild(), n)) {
      Node rValue = NodeUtil.getRValueOfLValue(n.getFirstChild());
      if (rValue != null && rValue.isNull()) {
        visitTypeExpression(expr, false, rValue);
        return;
      }
    }
    visitTypeExpression(expr, false);
  }

  private void report(NodeTraversal t) {
    for (Node n : missingCandidates) {
      if (shouldReport(n)) {
        t.report(n, MISSING_NULLABILITY_MODIFIER_JSDOC, getReportedTypeName(n));
      }
    }
    for (Node n : nullMissingCandidates) {
      if (shouldReport(n)) {
        t.report(n, NULL_MISSING_NULLABILITY_MODIFIER_JSDOC, getReportedTypeName(n));
      }
    }
    for (Node n : redundantCandidates) {
      if (shouldReport(n)) {
        // Report on parent so that modifier is also highlighted.
        t.report(n.getParent(), REDUNDANT_NULLABILITY_MODIFIER_JSDOC, getReportedTypeName(n));
      }
    }
  }

  private boolean shouldReport(Node n) {
    // Ignore type names that appear in @template clauses in the same source file. In rare cases,
    // this may cause a false negative (if the name is used in a non-template capacity in the same
    // file) or a false positive (if the name is used in a template capacity in a separate file),
    // but it makes this check possible in the absence of type information. If the style guide ever
    // mandates template types (and nothing else) to be all-caps, we can use that assumption to make
    // this check more precise.
    return !n.isString() || !templateTypeNames.contains(n.getString());
  }

  private void visitTypeExpression(JSTypeExpression expr, boolean hasArtificialTopLevelBang) {
    visitTypeExpression(expr, hasArtificialTopLevelBang, /* rValue= */ null);
  }

  private void visitTypeExpression(
      JSTypeExpression expr, boolean hasArtificialTopLevelBang, Node rValue) {
    Node root = expr.getRoot();
    NodeUtil.visitPreOrder(
        root,
        (Node node) -> {
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

          // Whether the node is the type name in a typeof expression.
          boolean isTypeOfType = parent != null && parent.isTypeOf();

          if (isReference && !hasBang && !hasQmark && !isNewOrThis && !isTypeOfType) {
            if (rValue != null && rValue.isNull()) {
              nullMissingCandidates.add(node);
            } else {
              missingCandidates.add(node);
            }
          } else if (isPrimitiveOrLiteral && hasNonArtificialBang) {
            redundantCandidates.add(node);
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
