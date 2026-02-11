/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.ExternsSkippingCallback;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

/**
 * For every Polymer Behavior, strip property type annotations and add suppress checktypes on
 * functions.
 *
 * <p>Also find listener and hostAttribute keys in the behavior, and mark them as quoted. This
 * protects the keys from being renamed.
 */
final class PolymerPassSuppressBehaviorsAndProtectKeys extends ExternsSkippingCallback {

  private final AbstractCompiler compiler;

  PolymerPassSuppressBehaviorsAndProtectKeys(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isBehavior(n)) {
      if (!NodeUtil.isNameDeclaration(n) && !n.isAssign()) {
        compiler.report(JSError.make(n, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
        return;
      }

      // Find listener and hostAttribute keys in the behavior, and mark them as quoted.
      // This ensures that the keys are not renamed.
      traverseBehaviorsAndProtectKeys(n);

      // Add @nocollapse.
      JSDocInfo.Builder newDocs = JSDocInfo.Builder.maybeCopyFrom(n.getJSDocInfo());
      newDocs.recordNoCollapse();
      n.setJSDocInfo(newDocs.build());

      Node behaviorValue = n.getSecondChild();
      if (NodeUtil.isNameDeclaration(n)) {
        behaviorValue = n.getFirstFirstChild();
      }
      suppressBehavior(behaviorValue, n);
    }
  }

  /** Traverse the behavior, find listener and hostAttribute keys, and mark them as quoted. */
  private void traverseBehaviorsAndProtectKeys(Node n) {
    if (n.isObjectLit()) {
      PolymerPassStaticUtils.quoteListenerAndHostAttributeKeys(n, compiler);
      PolymerPassStaticUtils.protectObserverAndPropertyFunctionKeys(n);
    }

    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      traverseBehaviorsAndProtectKeys(child);
    }
  }

  /** Strip property type annotations and add suppressions on functions. */
  private void suppressBehavior(Node behaviorValue, Node reportNode) {
    if (behaviorValue == null) {
      compiler.report(JSError.make(reportNode, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
      return;
    }

    if (behaviorValue.isArrayLit()) {
      for (Node child = behaviorValue.getFirstChild(); child != null; child = child.getNext()) {
        suppressBehavior(child, behaviorValue);
      }
    } else if (behaviorValue.isObjectLit()) {
      stripPropertyTypes(behaviorValue);
      addBehaviorSuppressions(behaviorValue);
    }
  }

  /**
   * @return Whether the node is the declaration of a Behavior.
   */
  private static boolean isBehavior(Node value) {
    return value.getJSDocInfo() != null && value.getJSDocInfo().isPolymerBehavior();
  }

  private void stripPropertyTypes(Node behaviorValue) {
    ImmutableList<MemberDefinition> properties =
        PolymerPassStaticUtils.extractProperties(
            behaviorValue,
            PolymerClassDefinition.DefinitionType.ObjectLiteral,
            compiler,
            /* constructor= */ null);
    for (MemberDefinition property : properties) {
      property.name.setJSDocInfo(null);
    }
  }

  private void suppressDefaultValues(Node behaviorValue) {
    for (MemberDefinition property :
        PolymerPassStaticUtils.extractProperties(
            behaviorValue,
            PolymerClassDefinition.DefinitionType.ObjectLiteral,
            compiler,
            /* constructor= */ null)) {
      if (!property.value.isObjectLit()) {
        continue;
      }

      Node defaultValue = NodeUtil.getFirstPropMatchingKey(property.value, "value");
      if (defaultValue == null || !defaultValue.isFunction()) {
        continue;
      }
      Node defaultValueKey = defaultValue.getParent();
      addSuppressionJsDocAnnotations(defaultValueKey);
    }
  }

  private void addBehaviorSuppressions(Node behaviorValue) {
    for (Node keyNode = behaviorValue.getFirstChild();
        keyNode != null;
        keyNode = keyNode.getNext()) {
      if (keyNode.getFirstChild().isFunction()) {
        addSuppressionJsDocAnnotations(keyNode);
      }
    }
    suppressDefaultValues(behaviorValue);
  }

  private void addSuppressionJsDocAnnotations(Node node) {
    JSDocInfo.Builder suppressDoc = JSDocInfo.Builder.maybeCopyFrom(node.getJSDocInfo());
    suppressDoc.recordSuppressions(ImmutableSet.of("checkTypes", "globalThis", "visibility"));
    node.setJSDocInfo(suppressDoc.build());
  }
}
