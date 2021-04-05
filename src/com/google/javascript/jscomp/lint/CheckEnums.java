/*
 * Copyright 2014 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks the following:
 *
 * <ol>
 *   <li>Whether there are duplicate values in enums.
 *   <li>Whether enum type (and its initializer values) are string/number or not.
 *   <li>Whether string enum values are statically initialized or not.
 * </ol>
 */
public final class CheckEnums extends AbstractPostOrderCallback implements CompilerPass {
  public static final DiagnosticType DUPLICATE_ENUM_VALUE = DiagnosticType.disabled(
      "JSC_DUPLICATE_ENUM_VALUE",
      "The value {0} is duplicated in this enum.");
  public static final DiagnosticType COMPUTED_PROP_NAME_IN_ENUM = DiagnosticType.disabled(
      "JSC_COMPUTED_PROP_NAME_IN_ENUM",
      "Computed property name used in enum.");
  public static final DiagnosticType SHORTHAND_ASSIGNMENT_IN_ENUM = DiagnosticType.disabled(
      "JSC_SHORTHAND_ASSIGNMENT_IN_ENUM",
      "Shorthand assignment used in enum.");
  public static final DiagnosticType ENUM_PROP_NOT_CONSTANT = DiagnosticType.disabled(
      "JSC_ENUM_PROP_NOT_CONSTANT",
      "enum key {0} must be in ALL_CAPS.");

  public static final DiagnosticType ENUM_TYPE_NOT_STRING_OR_NUMBER =
      DiagnosticType.disabled(
          "JSC_ENUM_VALUE_NOT_STRING_OR_NUMBER",
          "enum type must be either string or number."
          );

  public static final DiagnosticType NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM =
      DiagnosticType.disabled(
          "JSC_NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM",
          "Enum string values must be statically initialized as per the style guide."
          );
  private final AbstractCompiler compiler;

  public CheckEnums(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isObjectLit()) {
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(n);
      if (jsdoc != null && jsdoc.hasEnumParameterType()) {
        checkNamingAndAssignmentUsage(t, n);
        checkDuplicateEnumValues(t, n);
        checkEnumTypeAndInitializerValues(t, n, jsdoc);
      }
    }
  }

  private static void checkEnumTypeAndInitializerValues(
      NodeTraversal t, Node n, JSDocInfo jsDocInfo) {
    checkArgument(n.isObjectLit(), n);
    JSTypeExpression enumTypeExpr = jsDocInfo.getEnumParameterType();

    Node enumType = enumTypeExpr.getRoot();
    boolean isStringEnum = enumType.isStringLit() && enumType.getString().equals("string");
    boolean isNumberEnum = enumType.isStringLit() && enumType.getString().equals("number");
    if (!isStringEnum && !isNumberEnum) {
      // warn on `@enum {?}`, `@enum {boolean}`, `@enum {Some|Another}`, `@enum {SomeName}` etc`
      t.report(n, ENUM_TYPE_NOT_STRING_OR_NUMBER);
    }
    if (isStringEnum) {
      checkStringEnumInitializerValues(t, n);
    }
  }

  // Reports a warning if the string enum value is not statically initialized
  private static void checkStringEnumInitializerValues(NodeTraversal t, Node enumNode) {
    checkArgument(enumNode.isObjectLit(), enumNode);
    for (Node prop = enumNode.getFirstChild(); prop != null; prop = prop.getNext()) {
      // valueNode is guaranteed to exist by this time, as shorthand `{A}`s are converted to `{A:A}`
      Node valueNode = prop.getLastChild();
      if (!valueNode.isStringLit() && !(valueNode.isTemplateLit() && valueNode.hasOneChild())) {
        // neither string nor substitution-free template literal; report finding.
        t.report(valueNode, NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM);
      }
    }
  }

  private void checkNamingAndAssignmentUsage(NodeTraversal t, Node objLit) {
    for (Node child = objLit.getFirstChild(); child != null; child = child.getNext()) {
      checkName(t, child);
    }
  }

  private void checkName(NodeTraversal t, Node prop) {
    if (prop.isComputedProp()) {
      t.report(prop, COMPUTED_PROP_NAME_IN_ENUM);
      return;
    }

    if (prop.isStringKey() && prop.isShorthandProperty()) {
      t.report(prop, SHORTHAND_ASSIGNMENT_IN_ENUM);
    }

    if (!compiler.getCodingConvention().isValidEnumKey(prop.getString())) {
      t.report(prop, ENUM_PROP_NOT_CONSTANT);
    }
  }

  private static void checkDuplicateEnumValues(NodeTraversal t, Node enumNode) {
    Set<String> values = new HashSet<>();
    for (Node prop = enumNode.getFirstChild(); prop != null; prop = prop.getNext()) {
      Node valueNode = prop.getLastChild();
      String value;
      if (valueNode == null) {
        return;
      } else if (valueNode.isStringLit()) {
        value = valueNode.getString();
      } else if (valueNode.isNumber()) {
        value = Double.toString(valueNode.getDouble());
      } else {
        return;
      }

      if (!values.add(value)) {
        t.report(valueNode, DUPLICATE_ENUM_VALUE, value);
      }
    }
  }
}
