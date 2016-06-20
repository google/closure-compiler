/*
 * Copyright 2015 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeI;

import java.util.Set;

/**
 * Lints against passing arrays to goog.object methods with the intention of
 * iterating over them as though with a for-in loop, which is discouraged with
 * arrays.
 *
 */
public final class CheckArrayWithGoogObject extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  final AbstractCompiler compiler;

  private static final Set<String> GOOG_OBJECT_METHODS =
      ImmutableSet.of(
          "goog.object.forEach",
          "goog.object.filter",
          "goog.object.map",
          "goog.object.some",
          "goog.object.every",
          "goog.object.getCount",
          "goog.object.getAnyKey",
          "goog.object.getAnyValue",
          "goog.object.contains",
          "goog.object.getValues",
          "goog.object.getKeys",
          "goog.object.findKey",
          "goog.object.findValue",
          "goog.object.isEmpty",
          "goog.object.clear",
          "goog.object.remove",
          "goog.object.equals",
          "goog.object.clone",
          "goog.object.unsafeClone",
          "goog.object.transpose");

  public static final DiagnosticType ARRAY_PASSED_TO_GOOG_OBJECT =
      DiagnosticType.warning(
          "JSC_ARRAY_PASSED_TO_GOOG_OBJECT",
          "{0} expects an object, not an array. Did you mean to use goog.array?");

  public CheckArrayWithGoogObject(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public boolean isGoogObjectIterationOverArray(Node n) {
    if (!n.isCall()) {
      return false;
    }
    if (!n.getFirstChild().isQualifiedName()) {
      return false;
    }

    String name = n.getFirstChild().getQualifiedName();
    if (!GOOG_OBJECT_METHODS.contains(name)) {
      return false;
    }

    Node firstArg = n.getSecondChild();
    if (firstArg == null) {
      return false;
    }
    TypeI type = firstArg.getTypeI();
    return type != null && type.containsArray();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isGoogObjectIterationOverArray(n)) {
      compiler.report(
          t.makeError(n, ARRAY_PASSED_TO_GOOG_OBJECT, n.getFirstChild().getQualifiedName()));
    }
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }
}
