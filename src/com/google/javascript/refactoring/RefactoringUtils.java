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
package com.google.javascript.refactoring;

import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;

/** Utility methods for refactoring Java code. */
public class RefactoringUtils {
  private RefactoringUtils() {}

  /** Looks for a goog.require(), goog.provide() or goog.module() call in the node's file. */
  public static boolean isInClosurizedFile(Node node, NodeMetadata metadata) {
    Node script = NodeUtil.getEnclosingScript(node);

    if (script == null) {
      return false;
    }

    Node child = script.getFirstChild();
    while (child != null) {
      if (NodeUtil.isExprCall(child)) {
        if (Matchers.googRequire().matches(child.getFirstChild(), metadata)) {
          return true;
        }
        // goog.require or goog.module.
      } else if (child.isVar() && child.getBooleanProp(Node.IS_NAMESPACE)) {
        return true;
      }
      child = child.getNext();
    }
    return false;
  }
}
