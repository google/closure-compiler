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

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This pass looks for properties that are not modified and ensures they use the @const annotation.
 */
public class CheckConstPrivateProperties extends AbstractPostOrderCallback implements CompilerPass {

  public static final DiagnosticType MISSING_CONST_PROPERTY =
      DiagnosticType.disabled(
          "JSC_MISSING_CONST_PROPERTY",
          "Private property {0} is never modified, use the @const annotation");

  private final AbstractCompiler compiler;
  private final List<Node> candidates = new ArrayList<>();
  private final Set<String> modified = new HashSet<>();
  private final HashSet<String> constructorsAndInterfaces = new HashSet<>();

  public CheckConstPrivateProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  /** Reports the property definitions that should use the @const annotation. */
  private void reportMissingConst(NodeTraversal t) {
    for (Node n : candidates) {
      String propName = n.getString();
      if (!modified.contains(propName)) {
        t.report(n, MISSING_CONST_PROPERTY, propName);
      }
    }
    candidates.clear();
    modified.clear();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case SCRIPT:
        // Exiting the script, report any non-const privates not modified in the file.
        reportMissingConst(t);
        break;

      case GETELEM:
      case GETPROP:
        final Node propNode;
        if (n.isGetProp()) {
          propNode = n;
        } else if (n.getLastChild().isStringLit()) {
          propNode = n.getLastChild();
        } else {
          return;
        }

        // Only consider non-const @private class properties as candidates
        if (isCandidatePropertyDefinition(n)) {
          candidates.add(propNode);
        } else if (isModificationOp(n)) {
          // Mark any other modification operation as a modified property, to deal with lambdas, etc
          modified.add(propNode.getString());
        }
        break;

      case FUNCTION:
        JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
        if (info != null && (info.isConstructor() || info.isInterface())) {
          recordConstructorOrInterface(n);
        }
        break;

      case CLASS:
        recordConstructorOrInterface(n);
        break;

      default:
        break;
    }
  }

  private void recordConstructorOrInterface(Node n) {
    String className = NodeUtil.getBestLValueName(NodeUtil.getBestLValue(n));
    if (className != null) {
      // If className is null, then this pass won't report any diagnostics on static members of this
      // class.
      constructorsAndInterfaces.add(className);
    }
  }

  /**
   * @return Whether the given node is a @private property declaration that is not marked constant.
   */
  private boolean isCandidatePropertyDefinition(Node n) {
    if (!NodeUtil.isLhsOfAssign(n)) {
      return false;
    }

    Node target = n.getFirstChild();
    // Check whether the given property access is on 'this' or a static property on a class.
    if (!(target.isThis() || constructorsAndInterfaces.contains(target.getQualifiedName()))) {
      return false;
    }

    JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
    return info != null
        && info.getVisibility() == Visibility.PRIVATE
        && !info.isConstant()
        && !info.hasTypedefType()
        && !info.hasEnumParameterType()
        && !info.isInterface()
        && !isFunctionProperty(n);
  }

  /** @return Whether the given property declaration is assigned to a function. */
  private boolean isFunctionProperty(Node n) {
    // TODO(dylandavidson): getAssignedValue does not support GETELEM.
    if (n.isGetElem()) {
      return false;
    }
    Node assignedValue = NodeUtil.getAssignedValue(n);
    return assignedValue != null && assignedValue.isFunction();
  }

  /**
   * @return Whether the given property is modified in any way (assignment, increment/decrement, or
   *     'delete' property).
   */
  private boolean isModificationOp(Node n) {
    Node parent = n.getParent();

    if (n != parent.getFirstChild()) {
      return false;
    }

    return NodeUtil.isAssignmentOp(parent)
        || parent.isInc()
        || parent.isDec()
        || parent.isDelProp();
  }
}
