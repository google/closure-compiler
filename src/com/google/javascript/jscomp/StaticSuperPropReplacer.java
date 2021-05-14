/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Replaces references to `super` properties in static class methods with the superclass, for cases
 * where the superclass in the extends clause is a qualified name.
 *
 * <p>This runs during {@link InlineAndCollapseProperties.AggressiveInlineAliases} in order to avoid
 * breaking references to superclass properties during {@link
 * InlineAndCollapseProperties.CollapseProperties}. For example: replaces
 *
 * <p>`class Bar extends Foo { static m() { super.m() {} }}`
 *
 * <p>with
 *
 * <p>`class Bar extends Foo { static m() { Foo.m() {} }}`
 *
 * <p>This makes the following assumptions:
 *
 * <ul>
 *   <li>The superclass never changes after the class definition (e.g. using Object.setPrototypeOf)
 *   <li>The superclass (if a qualified name) is effectively constant after the class definition
 *   <li>Evaluating the qualified name of the superclass has no side effects (i.e. is not a getter
 *       with side effects)
 * </ul>
 */
final class StaticSuperPropReplacer implements NodeTraversal.Callback {
  private final AbstractCompiler compiler;
  /**
   * Stores the Node for a superclass for static ES6 class methods only; otherwise an empty object
   *
   * <p>This uses Optional instead of just Node because ArrayDeque rejects nulls.
   */
  private final Deque<Optional<Node>> superclasses = new ArrayDeque<>();

  StaticSuperPropReplacer(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  /** Runs this pass over the entire provided subtree */
  void replaceAll(Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (!n.isFunction()) {
      return true;
    }

    // keep track of stack of current superclass, if known
    if (parent.isStaticMember() && parent.getParent().isClassMembers()) {
      Node classNode = parent.getGrandparent();
      Node superclassNode = classNode.getSecondChild();
      if (superclassNode.isEmpty()) {
        superclasses.push(Optional.empty());
      } else {
        superclasses.push(Optional.of(superclassNode));
      }
    } else if (!n.isArrowFunction()) {
      // arrow functions keep the same `super` reference, but non-arrow functions should not be
      // accessing super.
      superclasses.push(Optional.empty());
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isSuper()) {
      tryReplaceSuper(n);
    } else if (n.isFunction() && !n.isArrowFunction()) {
      superclasses.pop();
    }
  }

  /** Replaces `super` with `Super.Class` if in a static class method with a qname superclass */
  private void tryReplaceSuper(Node superNode) {
    checkState(!superclasses.isEmpty(), "`super` cannot appear outside a function");
    Optional<Node> currentSuperclass = superclasses.peek();
    if (!currentSuperclass.isPresent() || !currentSuperclass.get().isQualifiedName()) {
      // either if a) we're in a static class fn without an 'extends' clause or b) we're not in
      // a static class function
      return;
    }

    Node fullyQualifiedSuperRef = currentSuperclass.get().cloneTree();
    superNode.replaceWith(fullyQualifiedSuperRef);
    compiler.reportChangeToEnclosingScope(fullyQualifiedSuperRef);
  }
}
