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
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

/**
 * Checks when a mutable property is assigned to a prototype. This is
 * generally undesirable because it can lead to the following unexpected
 * situation.
 *
 * <pre>
 * /** @constructor * /
 * function MyClass() {}
 * MyClass.prototype.prop = [];
 * x = new MyClass;
 * y = new MyClass;
 * x.prop.push(1);
 * console.log(y.prop) // [1]
 * </pre>
 */
public final class CheckPrototypeProperties implements HotSwapCompilerPass, NodeTraversal.Callback {
  public static final DiagnosticType ILLEGAL_PROTOTYPE_MEMBER =
      DiagnosticType.disabled(
          "JSC_ILLEGAL_PROTOTYPE_MEMBER",
          "Prototype property {0} should be a primitive, not an Array or Object.");

  final AbstractCompiler compiler;

  public CheckPrototypeProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, originalRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (NodeUtil.isPrototypePropertyDeclaration(n)) {
      Node assign = n.getFirstChild();
      Node rhs = assign.getLastChild();
      if (rhs.isArrayLit() || rhs.isObjectLit()) {
        JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(rhs);
        if (jsDoc != null && jsDoc.hasEnumParameterType()) {
          // Don't report for @enum's on the prototype. Sometimes this is necessary, for example,
          // to expose the enum values to an Angular template.
          return;
        }
        String propName = assign.getFirstChild().getLastChild().getString();
        compiler.report(t.makeError(assign, ILLEGAL_PROTOTYPE_MEMBER, propName));
      }
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    return true;
  }
}

