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
import com.google.javascript.rhino.Node;

/** Finds the externs for the PolymerElement base class and all of its properties in the externs. */
final class PolymerPassFindExterns implements NodeTraversal.Callback {

  private static final String POLYMER_ELEMENT_NAME = "PolymerElement";

  private Node polymerElementExterns;
  private final ImmutableList.Builder<Node> polymerElementProps;

  PolymerPassFindExterns() {
    polymerElementProps = ImmutableList.builder();
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    /**
     * The externs subtree also includes @typeSummary files, which are slightly different from
     * normal externs. The Polymer externs may exist in either file type. However,
     * some @typeSummaries are goog.modules, which definitely don't contain Polymer externs, and
     * which we don't want to search.
     */
    return !n.isModuleBody();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isPolymerElementExterns(n)) {
      polymerElementExterns = n;
    } else if (isPolymerElementPropExpr(n)) {
      polymerElementProps.add(n);
    }
  }

  /**
   * @return All properties of the {@code PolymerElement} class in externs.
   */
  ImmutableList<Node> getPolymerElementProps() {
    return polymerElementProps.build();
  }

  /**
   * @return The root {@link Node} {@code PolymerElement} class in externs.
   */
  Node getPolymerElementExterns() {
    return polymerElementExterns;
  }

  /**
   * @return Whether the node is the declaration of PolymerElement.
   */
  private static boolean isPolymerElementExterns(Node value) {
    // Explicitly allow var, let or const.
    return NodeUtil.isNameDeclaration(value)
        && value.getFirstChild().matchesQualifiedName(POLYMER_ELEMENT_NAME);
  }

  /**
   * @return Whether the node is an expression result of an assignment to a property of
   * PolymerElement.
   */
  private static boolean isPolymerElementPropExpr(Node value) {
    return value != null
        && value.isExprResult()
        && value.getFirstFirstChild() != null
        && value.getFirstFirstChild().isGetProp()
        && value.getFirstFirstChild().isQualifiedName()
        && NodeUtil.getRootOfQualifiedName(
            value.getFirstFirstChild()).matchesQualifiedName(POLYMER_ELEMENT_NAME);
  }
}
