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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;

/**
 * Performs correctness checks which are specific to J2CL-generated patterns.
 */
public class J2clChecksPass extends AbstractPostOrderCallback implements CompilerPass {

  static final DiagnosticType J2CL_REFERENCE_EQUALITY =
      DiagnosticType.warning(
          "JSC_J2CL_REFERENCE_EQUALITY",
          "Reference equality may not be used with the specified type: {0}");

  /** Types for which using reference equality is an error. Mapped from name to filename. */
  static final ImmutableMap<String, String> REFERENCE_EQUALITY_TYPE_PATTERNS =
      ImmutableMap.of(
          "java.lang.Integer", "java/lang/Integer.impl.java.js",
          "java.lang.Float", "java/lang/Float.impl.java.js",
          "goog.math.Long", "javascript/closure/math/long.js");

  private final AbstractCompiler compiler;

  J2clChecksPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void visit(NodeTraversal unused, Node n, Node parent) {
    for (String typeName : REFERENCE_EQUALITY_TYPE_PATTERNS.keySet()) {
      checkReferenceEquality(n, typeName, REFERENCE_EQUALITY_TYPE_PATTERNS.get(typeName));
    }
  }

  /** Reports an error if the node is a reference equality check of the specified type. */
  private void checkReferenceEquality(Node n, String typeName, String fileName) {
    if (n.getToken() == Token.SHEQ
        || n.getToken() == Token.EQ
        || n.getToken() == Token.SHNE
        || n.isNE()) {
      JSType firstJsType = n.getFirstChild().getJSType();
      JSType lastJsType = n.getLastChild().getJSType();
      boolean hasType = isType(firstJsType, fileName) || isType(lastJsType, fileName);
      boolean hasNullType = isNullType(firstJsType) || isNullType(lastJsType);
      if (hasType && !hasNullType) {
        compiler.report(JSError.make(n, J2CL_REFERENCE_EQUALITY, typeName));
      }
    }
  }

  private boolean isNullType(JSType jsType) {
    if (jsType == null) {
      return false;
    }
    return jsType.isNullType() || jsType.isVoidType();
  }

  private boolean isType(JSType jsType, String fileName) {
    if (jsType == null) {
      return false;
    }
    jsType = jsType.restrictByNotNullOrUndefined();
    if (jsType.toMaybeObjectType() == null) {
      return false;
    }
    String sourceName = getSourceName(jsType);
    return sourceName != null && sourceName.endsWith(fileName);
  }

  private String getSourceName(JSType jsType) {
    FunctionType constructor = jsType.toMaybeObjectType().getConstructor();
    if (constructor == null) {
      return "";
    }
    return NodeUtil.getSourceName(constructor.getSource());
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }
}
