/*
 * Copyright 2021 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.testing;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;
import java.util.function.Predicate;

/** Represents a subtree of the output from a compilation. */
public final class CodeSubTree {

  private final Node rootNode;

  private CodeSubTree(Node rootNode) {
    this.rootNode = rootNode;
  }

  public Node getRootNode() {
    return rootNode;
  }

  /** Returns the SubTree rooted at the first class definition found with the given name. */
  public CodeSubTree findClassDefinition(String wantedClassName) {
    Node classNode =
        findFirstNode(
            rootNode, (node) -> node.isClass() && wantedClassName.equals(NodeUtil.getName(node)));
    return new CodeSubTree(classNode);
  }

  /**
   * Returns a CodeSubTree for the first definition of the given class name in the output or externs
   * from the last compile.
   */
  public static CodeSubTree findClassDefinition(AbstractCompiler compiler, String wantedClassName) {
    return new CodeSubTree(compiler.getRoot()).findClassDefinition(wantedClassName);
  }

  /** Returns the first class method definiton found with the given name. */
  public CodeSubTree findMethodDefinition(String wantedMethodName) {
    Node methodDefinitionNode =
        findFirstNode(
            rootNode,
            (node) -> node.isMemberFunctionDef() && wantedMethodName.equals(node.getString()));

    return new CodeSubTree(methodDefinitionNode);
  }

  /** Returns the first function method definition found with the given name. */
  public static CodeSubTree findFunctionDefinition(
      AbstractCompiler compiler, String wantedMethodName) {
    Node functionDefinitionNode =
        findFirstNode(
            compiler.getRoot(),
            (node) ->
                node.isFunction()
                    && node.getFirstChild().isName()
                    && wantedMethodName.equals(node.getFirstChild().getString()));

    return new CodeSubTree(functionDefinitionNode);
  }

  /** Executes an action for every instance of a given qualified name. */
  public ImmutableList<Node> findMatchingQNameReferences(final String wantedQName) {
    return findNodesAllowEmpty(rootNode, (node) -> node.matchesQualifiedName(wantedQName));
  }

  /** Return a list of all Nodes matching the given predicate starting at the given root. */
  public static ImmutableList<Node> findNodesAllowEmpty(Node rootNode, Predicate<Node> predicate) {
    ImmutableList.Builder<Node> listBuilder = ImmutableList.builder();
    NodeUtil.visitPreOrder(
        rootNode,
        node -> {
          if (predicate.test(node)) {
            listBuilder.add(node);
          }
        });
    return listBuilder.build();
  }

  /** Return a list of all Nodes matching the given predicate starting at the given root. */
  public static ImmutableList<Node> findNodesNonEmpty(Node rootNode, Predicate<Node> predicate) {
    ImmutableList<Node> results = findNodesAllowEmpty(rootNode, predicate);
    checkState(!results.isEmpty(), "no nodes found");
    return results;
  }

  /**
   * Return the shallowest and earliest of all Nodes matching the given predicate starting at the
   * given root.
   *
   * <p>Throws an exception if none found.
   */
  public static Node findFirstNode(Node rootNode, Predicate<Node> predicate) {
    ImmutableList<Node> allMatchingNodes = findNodesNonEmpty(rootNode, predicate);
    return allMatchingNodes.get(0);
  }
}
