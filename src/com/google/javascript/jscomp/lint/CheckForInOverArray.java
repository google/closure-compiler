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

import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.TemplatizedType;

import java.util.Set;

/**
 * Checks when the pattern for (x in arr) { ... } where arr is an array,
 * or an union type containing an array. Also checks several goog.object methods
 * that are essentially for/in loops.
 *
 */
public final class CheckForInOverArray extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  final AbstractCompiler compiler;
  final JSTypeRegistry typeRegistry;

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

  public static final DiagnosticType FOR_IN_OVER_ARRAY =
      DiagnosticType.warning(
          "JSC_FOR_IN_OVER_ARRAY",
          "For..in over array is discouraged.");

  public static final DiagnosticType ARRAY_PASSED_TO_GOOG_OBJECT =
      DiagnosticType.warning(
          "JSC_ARRAY_PASSED_TO_GOOG_OBJECT",
          "{0} expects an object, not an array. Did you mean to use goog.array?");

  public CheckForInOverArray(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.typeRegistry = compiler.getTypeRegistry();
  }

  public boolean isForInOverArray(Node n) {
    if (NodeUtil.isForIn(n)) {
      Preconditions.checkState(n.getChildCount() == 3, n);
      // get the second child, which represents
      // B in construct "for (A in B) { C }"
      Node child = n.getSecondChild();
      JSType type = child.getJSType();
      if (type != null && containsArray(type)) {
        return true;
      }
    }
    return false;
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
    JSType type = firstArg.getJSType();
    return type != null && containsArray(type);
  }

  private boolean isArray(JSType type) {
    if (type.isArrayType()) {
      return true;
    }
    TemplatizedType templatizedType = type.toMaybeTemplatizedType();
    return templatizedType != null && templatizedType.getReferencedType().isArrayType();
  }

  private boolean containsArray(JSType type) {
    if (isArray(type)) {
      return true;
    }
    if (type.isUnionType()) {
      JSType arrayType = typeRegistry.getNativeType(ARRAY_TYPE);
      for (JSType alternate : type.toMaybeUnionType().getAlternates()) {
        if (alternate.isSubtype(arrayType)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isForInOverArray(n)) {
      compiler.report(t.makeError(n, FOR_IN_OVER_ARRAY));
    } else if (isGoogObjectIterationOverArray(n)) {
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
