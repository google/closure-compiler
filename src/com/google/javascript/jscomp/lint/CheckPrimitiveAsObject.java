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
package com.google.javascript.jscomp.lint;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

/**
 * Check for explicit creation of the object equivalents of primitive types
 * (e.g. Boolean instead of boolean) and their use in type declarations.
 *
 * <p>Using these is confusing and gives no benefit.
 * For example, the result of
 * {@code typeof (new Boolean(true))} is {@code "object"}.
 * and the result of
 * {@code (new Boolean(false)) ? "true" : "false"} is {@code "true"}.
 */
public final class CheckPrimitiveAsObject extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  public static final DiagnosticType NEW_PRIMITIVE_OBJECT =
      DiagnosticType.warning("JSC_PRIMITIVE_OBJECT", "Explicit creation of a {0} object.");

  public static final DiagnosticType PRIMITIVE_OBJECT_DECLARATION =
      DiagnosticType.warning(
          "JSC_PRIMITIVE_OBJECT_DECLARATION",
          "Declaration of {0} object instead of primitive type.");

  private static final ImmutableSet<String> PRIMITIVE_OBJECT_CONSTRUCTORS =
      ImmutableSet.of("Boolean", "Number", "String");

  private final AbstractCompiler compiler;

  public CheckPrimitiveAsObject(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    checkForPrimitiveObjectConstructor(t, n);
    checkForPrimitiveObjectDeclaration(t, n);
  }

  private void checkForPrimitiveObjectDeclaration(NodeTraversal t, Node n) {
    JSDocInfo jsDocInfo = n.getJSDocInfo();

    if (jsDocInfo != null) {
      for (Node typeRoot : jsDocInfo.getTypeNodes()) {
        checkTypeNodeForPrimitiveObjectDeclaration(t, typeRoot);
      }
    }
  }

  private void checkTypeNodeForPrimitiveObjectDeclaration(final NodeTraversal t, Node typeRoot) {
    NodeUtil.visitPreOrder(
        typeRoot,
        new NodeUtil.Visitor() {
          @Override
          public void visit(Node node) {
            if (node.isString()) {
              String typeName = node.getString();
              if (PRIMITIVE_OBJECT_CONSTRUCTORS.contains(typeName)) {
                t.report(node, PRIMITIVE_OBJECT_DECLARATION, typeName);
              }
            }
          }
        },
        Predicates.<Node>alwaysTrue());
  }

  private void checkForPrimitiveObjectConstructor(NodeTraversal t, Node n) {
    if (n.isNew()) {
      Node constructorFunction = n.getFirstChild();
      if (constructorFunction.isName()) {
        String constructorName = constructorFunction.getString();
        if (PRIMITIVE_OBJECT_CONSTRUCTORS.contains(constructorName)) {
          t.report(n, NEW_PRIMITIVE_OBJECT, constructorName);
        }
      }
    }
  }
}
