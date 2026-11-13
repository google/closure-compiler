/*
 * Copyright 2026 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.javascript.rhino.Node;

/**
 * Syntactic rules for the order in which the children of an expression are evaluated, and which
 * children are only conditionally evaluated.
 *
 * <p>These predicates only describe expression-level evaluation within a single statement. They
 * deliberately know nothing about statement-level control flow (loops, {@code try}, {@code switch},
 * labels, etc.), which is handled by {@link BasicBlock} and the CFG.
 */
final class EvaluationOrder {

  private EvaluationOrder() {}

  /**
   * Returns whether evaluating {@code n}'s parent expression may skip evaluating {@code n}, even
   * when all of the parent's earlier-evaluated children are evaluated.
   *
   * <p>This is true for:
   *
   * <ul>
   *   <li>the right-hand side of {@code &&}, {@code ||}, and {@code ??}
   *   <li>the right-hand side of {@code &&=}, {@code ||=}, and {@code ??=}
   *   <li>both arms of {@code ?:}
   *   <li>every child of an optional chain node other than its receiver
   *   <li>the default value of a {@code DEFAULT_VALUE}
   * </ul>
   *
   * <p>This only looks at {@code n}'s immediate parent. An unconditional child of a conditional
   * child is also conditionally executed.
   */
  @SuppressWarnings("ReferenceEquality")
  static boolean isConditionalChild(Node n) {
    Node parent = n.getParent();
    if (parent == null) {
      return false;
    }
    return switch (parent.getToken()) {
      case AND,
          OR,
          COALESCE,
          ASSIGN_AND,
          ASSIGN_OR,
          ASSIGN_COALESCE,
          HOOK,
          OPTCHAIN_GETPROP,
          OPTCHAIN_GETELEM,
          OPTCHAIN_CALL,
          DEFAULT_VALUE ->
          n != parent.getFirstChild();
      default -> false;
    };
  }

  /**
   * Returns whether {@code parent} evaluates its last child before its other children, rather than
   * in source order.
   *
   * <p>This is true for:
   *
   * <ul>
   *   <li>{@code DESTRUCTURING_LHS} with an initializer: {@code const [a] = rhs}
   *   <li>{@code ASSIGN} with a destructuring pattern target: {@code [a] = rhs}
   *   <li>{@code DEFAULT_VALUE} whose target is a name or a destructuring pattern: {@code [a =
   *       dflt]} or {@code [[a] = dflt]}
   * </ul>
   *
   * <p>A {@code DEFAULT_VALUE} with a property target, such as {@code [o[k] = dflt]}, evaluates the
   * target reference ({@code o} and {@code k}) before the default value, so it uses source order.
   * The property write itself still happens after the default value is evaluated, just like the
   * write in {@code o[k] = rhs}.
   *
   * <p>A {@code DEFAULT_VALUE} with a name target is treated as evaluating the default first
   * because the name is only written, after the default value is evaluated.
   */
  static boolean evaluatesLastChildFirst(Node parent) {
    return switch (parent.getToken()) {
      case DESTRUCTURING_LHS -> parent.hasTwoChildren();
      case ASSIGN -> parent.getFirstChild().isDestructuringPattern();
      case DEFAULT_VALUE -> {
        Node target = parent.getFirstChild();
        // A name target is only written, and the write happens after the default value is
        // evaluated. Visiting the default first is also what lets VariableReferenceCheck report the
        // TDZ error in `function f(x = x) {}`.
        yield target.isName() || target.isDestructuringPattern();
      }
      default -> false;
    };
  }

  /**
   * Returns whether, within a single evaluation of their common parent, {@code first} is evaluated
   * before {@code second}.
   *
   * <p>This only describes evaluation order. It does not account for {@code second} being evaluated
   * even if {@code first} is not (e.g. the two arms of a {@code HOOK}, or a {@code CASE} body
   * reached by fallthrough).
   *
   * @throws IllegalArgumentException if the nodes are not siblings
   */
  @SuppressWarnings("ReferenceEquality")
  static boolean isEvaluatedBeforeSibling(Node first, Node second) {
    Node parent = first.getParent();
    checkArgument(
        parent != null && parent == second.getParent(), "not siblings: %s, %s", first, second);
    if (first == second) {
      return false;
    }
    if (evaluatesLastChildFirst(parent)) {
      // All the nodes that return true from evaluatesLastChildFirst have exactly two children.
      return first == parent.getLastChild();
    }
    for (Node sibling = first.getNext(); sibling != null; sibling = sibling.getNext()) {
      if (sibling == second) {
        return true;
      }
    }
    return false;
  }
}
