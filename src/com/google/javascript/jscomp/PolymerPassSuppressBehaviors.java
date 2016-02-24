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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;

import java.util.List;

/**
 * For every Polymer Behavior, strip property type annotations and add suppress checktypes on
 * functions.
 */
final class PolymerPassSuppressBehaviors extends AbstractPostOrderCallback {

  private final AbstractCompiler compiler;

  PolymerPassSuppressBehaviors(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isBehavior(n)) {
      if (!n.isVar() && !n.isAssign()) {
        compiler.report(JSError.make(n, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
        return;
      }

      // Add @nocollapse.
      JSDocInfoBuilder newDocs = JSDocInfoBuilder.maybeCopyFrom(n.getJSDocInfo());
      newDocs.recordNoCollapse();
      n.setJSDocInfo(newDocs.build());

      Node behaviorValue = n.getSecondChild();
      if (n.isVar()) {
        behaviorValue = n.getFirstFirstChild();
      }
      suppressBehavior(behaviorValue);
    }
  }

  /**
   * Strip property type annotations and add suppress checkTypes and globalThis on functions.
   */
  private void suppressBehavior(Node behaviorValue) {
    if (behaviorValue == null) {
      compiler.report(JSError.make(behaviorValue, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
      return;
    }

    if (behaviorValue.isArrayLit()) {
      for (Node child : behaviorValue.children()) {
        suppressBehavior(child);
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

  private static void stripPropertyTypes(Node behaviorValue) {
    List<MemberDefinition> properties = PolymerPassStaticUtils.extractProperties(behaviorValue);
    for (MemberDefinition property : properties) {
      property.name.removeProp(Node.JSDOC_INFO_PROP);
    }
  }

  private static void suppressDefaultValues(Node behaviorValue) {
    for (MemberDefinition property : PolymerPassStaticUtils.extractProperties(behaviorValue)) {
      if (!property.value.isObjectLit()) {
        continue;
      }

      Node defaultValue = NodeUtil.getFirstPropMatchingKey(property.value, "value");
      if (defaultValue == null || !defaultValue.isFunction()) {
        continue;
      }
      Node defaultValueKey = defaultValue.getParent();
      JSDocInfoBuilder suppressDoc =
          JSDocInfoBuilder.maybeCopyFrom(defaultValueKey.getJSDocInfo());
      suppressDoc.addSuppression("checkTypes");
      suppressDoc.addSuppression("globalThis");
      defaultValueKey.setJSDocInfo(suppressDoc.build());
    }
  }

  private static void addBehaviorSuppressions(Node behaviorValue) {
    for (Node keyNode : behaviorValue.children()) {
      if (keyNode.getFirstChild().isFunction()) {
        keyNode.removeProp(Node.JSDOC_INFO_PROP);
        JSDocInfoBuilder suppressDoc = new JSDocInfoBuilder(true);
        suppressDoc.addSuppression("checkTypes");
        suppressDoc.addSuppression("globalThis");
        keyNode.setJSDocInfo(suppressDoc.build());
      }
    }
    suppressDefaultValues(behaviorValue);
  }
}
