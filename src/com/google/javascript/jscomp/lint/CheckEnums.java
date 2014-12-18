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

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.HashSet;
import java.util.Set;

/**
 * Check for duplicate values in enums.
 */
public final class CheckEnums extends AbstractPostOrderCallback implements CompilerPass {
  public static final DiagnosticType DUPLICATE_ENUM_VALUE = DiagnosticType.warning(
      "JSC_DUPLICATE_ENUM_VALUE",
      "The value {0} is duplicated in this enum.");

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
        checkDuplicateEnumValues(t, n);
      }
    }
  }

  private void checkDuplicateEnumValues(NodeTraversal t, Node n) {
    Set<String> values = new HashSet<>();
    for (Node child : n.children()) {
      Node valueNode = child.getFirstChild();
      String value;
      if (valueNode.isString()) {
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

