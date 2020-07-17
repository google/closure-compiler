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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multiset;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * An AST traverser that keeps track of whether access to a generic resource are "guarded" or not. A
 * guarded use is one that will not cause runtime errors if the resource does not exist. The
 * resource (type {@code T} in the signature) is any value type computable from a node, such as a
 * qualified name or property name. It is up to the subclass to map the currently visited token to
 * the {@code T} in order to call {@link #isGuarded}, which depends entirely on the context from
 * ancestor nodes and previous calls to {@code #isGuarded} for the same resource.
 *
 * <p>More precisely, a resource may be guarded either <i>intrinsically</i> or <i>conditionally</i>,
 * as follows. A use is intrinsically guarded if it occurs in a context where its specific value is
 * immediately discarded, such as coercion to boolean or string. A use is conditionally guarded if
 * it occurs in a context conditioned on an intrinsically guarded use (such as the "then" or "else"
 * block of an "if", the second or third argument of a ternary operator, right-hand arguments of
 * logical operators, or later in a block in which an unconditional "throw" or "return" was found in
 * a guarded context).
 *
 * <p>For example, the following are all intrinsically guarded uses of {@code x}:
 *
 * <pre>{@code
 * // Coerced to boolean:
 * if (x);
 * x ? y : z;
 * x &amp;&amp; y;
 * Boolean(x);
 * !x;
 * x == y; x != y; x === y; x !== y;
 * x instanceof Foo;
 * typeof x === 'string';
 *
 * // Coerced to string:
 * String(x);
 * typeof x;
 *
 * // Immediately discarded (but doesn't make much sense in my contexts):
 * x = y;
 * }</pre>
 *
 * <p>The following uses of {@code x} are conditionally guarded:
 *
 * <pre>{@code
 * if (x) x();
 * !x ? null : x;
 * typeof x == 'function' ? x : () => {};
 * x &amp;&amp; x.y;
 * !x || x.y;
 * if (!x) return; x();
 * x ?? x = 3;
 * }</pre>
 *
 * Note that there is no logic to determine <i>which</i> branch is guarded, so any usages in either
 * branch will pass after such a check. As such, the following are also considered guarded, though a
 * human can easily see that this is spurious:
 *
 * <pre>{@code
 * if (!x) x();
 * if (x) { } else x();
 * !x &amp;&amp; x();
 * var y = x != null ? null : x;
 * }</pre>
 *
 * Note also that the call or property access is not necessary to make a use unguarded: the final
 * example immediately above would be unguarded if it weren't for the {@code x != null} condition,
 * since it allows the value of {@code x} to leak out in an uncontrolled way.
 *
 * <p>This class overrides the {@link Callback} API methods with final methods of its own, and
 * defines the template method {@link #visitGuarded} to perform the normal work for individual
 * nodes. The only other API is {@link #isGuarded}, which allows checking if a {@code T} in the
 * current node's context is guarded, either intrinsically or conditionally. If it is intrinsically
 * guarded, then it may be recorded as a condition for the purpose of guarding future contexts.
 */
abstract class GuardedCallback<T> implements Callback {
  // Compiler is needed for coding convention (isPropertyTestFunction).
  private final AbstractCompiler compiler;
  // Map from short-circuiting conditional nodes (AND, OR, COALESCE, IF, and HOOK) to
  // the set of resources each node guards.  This is saved separately from
  // just `guarded` because the guard must not go into effect until after
  // traversal of the first child is complete.  Before traversing the second
  // child any node, its values in this map are moved into `guarded` and
  // `installedGuards` (the latter allowing removal at the correct time).
  private final SetMultimap<Node, T> registeredGuards =
      MultimapBuilder.hashKeys().hashSetValues().build();
  // Set of currently-guarded resources.  Elements are added to this set
  // just before traversing the second or later (i.e. "then" or "else")
  // child of a short-circuiting conditional node, and then removed after
  // traversing the last child.  It is a multiset so that multiple adds
  // of the same resource require the same number of removals before the
  // resource becomes unguarded.
  private final Multiset<T> guarded = HashMultiset.create();
  // Resources that are currently installed as guarded but will need to
  // be removed from `guarded` after visiting all the key nodes' children.
  private final ListMultimap<Node, T> installedGuards =
      MultimapBuilder.hashKeys().arrayListValues().build();
  // A stack of `Context` objects describing the current node's context:
  // specifically, whether it is inherently safe, and a link to one or
  // more conditional nodes in the current statement directly above it
  // (for registering safe resources as guards).
  private final Deque<Context> contextStack = new ArrayDeque<>();

  GuardedCallback(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public final boolean shouldTraverse(NodeTraversal traversal, Node n, Node parent) {
    // Note that shouldTraverse() operates primarily on `parent`, while visit()
    // uses `n`.  This is intentional.  To see why, consider traversing the
    // following tree:
    //
    //    if (x) y; else z;
    //
    // 1. shouldTraverse(`if`):
    //    a. parent is null, so pushes an EMPTY onto the context stack.
    // 2. shouldTraverse(`x`):
    //    a. parent is `if`, so pushes Context(`if`, true); guards is empty.
    // 3. visit(`x`)
    //    a. guarded and installedGuards are both empty, so nothing is removed.
    //    b. visitGuarded(`x`) will call isGuarded("x"), which looks at the top
    //       of the stack and sees that the context is safe (true) and that
    //       there is a linked conditional node (the `if`); adds {`if`: "x"}
    //       to registeredGuards.
    //    b. Context(`if`, true) is popped off the stack.
    // 4. shouldTraverse(`y`):
    //    a. parent is still `if`, but since `y` is the second child it is
    //       no longer safe, so another EMPTY is pushed.
    //    b. the {`if`: "x"} guard is moved from registered to installed.
    // 5. visit(`y`):
    //    a. nothing is installed on `y` so no guards are removed.
    //    b. visitGuarded(`y`) will call isGuarded("y"), which will return
    //       false since "y" is neither intrinsically or conditionally guarded;
    //       if we'd called isResourceRequired("x"), it would return false
    //       because "x" is currently an element of guarded.
    //    c. one empty context is popped.
    // 6. shouldTraverse(`z`), visit(`z`)
    //    a. see steps 4-5, nothing really changes here.
    // 7. visit(`if`)
    //    a. the installed {`if`: "x"} guard is removed.
    //    c. pop the final empty context from the stack.

    if (parent == null) {
      // The first node gets an empty context.
      contextStack.push(Context.EMPTY);
    } else {
      // Before traversing any children, we update the stack
      contextStack.push(contextStack.peek().descend(compiler, parent, n));

      // If the parent has any guards registered on it, then add them to both
      // `guarded` and `installedGuards`.
      if (parent != null && CAN_HAVE_GUARDS.contains(parent.getToken())
          && registeredGuards.containsKey(parent)) {
        for (T resource : registeredGuards.removeAll(parent)) {
          guarded.add(resource);
          installedGuards.put(parent, resource);
        }
      }
    }
    return true;
  }

  @Override
  public final void visit(NodeTraversal traversal, Node n, Node parent) {
    // Remove any guards registered on this node by its children, which are no longer
    // relevant.  This happens first because these were registered on a "parent", but
    // now this is that parent (i.e. `n` here vs `parent` in isGuarded).
    if (parent != null && CAN_HAVE_GUARDS.contains(n.getToken())
        && installedGuards.containsKey(n)) {
      guarded.removeAll(installedGuards.removeAll(n));
    }

    // Check for abrupt returns (`return` and `throw`).
    if (isAbrupt(n)) {
      // If found, any guards installed on a parent IF should be promoted to the
      // grandparent.  This allows a small amount of flow-sensitivity, in that
      //   if (!x) return; x();
      // has the guard for `x` promoted from the `if` to the outer block, so that
      // it guards the next statement.
      promoteAbruptReturns(parent);
    }

    // Finally continue on to whatever the traversal would normally do.
    visitGuarded(traversal, n, parent);

    // After children have been traversed, pop the top of the conditional stack.
    contextStack.pop();
  }

  private void promoteAbruptReturns(Node parent) {
    // If the parent is a BLOCK (e.g. `if (x) { return; }`) then go up one level.
    if (parent.isBlock()) {
      parent = parent.getParent();
    }
    // If there were any guards registered the parent IF, then promote them up one level.
    if (parent.isIf() && installedGuards.containsKey(parent)) {
      Node grandparent = parent.getParent();
      if (grandparent.isBlock() || grandparent.isScript()) {
        registeredGuards.putAll(grandparent, installedGuards.get(parent));
      }
    }
  }

  /**
   * Performs specific traversal behavior. Should call {@link #isGuarded}
   * at least once.
   */
  abstract void visitGuarded(NodeTraversal traversal, Node n, Node parent);

  /**
   * Determines if the given resource is guarded, either intrinsically or
   * conditionally.  If the former, any ancestor conditional nodes are
   * registered as feature-testing the resource.
   */
  boolean isGuarded(T resource) {
    // Check if this polyfill is already guarded.  If so, return true right away.
    if (guarded.contains(resource)) {
      return true;
    }

    // If not, see if this is itself a feature check guard.  This is
    // defined as a usage of the polyfill in such a way that throws
    // away the actual value and only cares about its truthiness or
    // typeof.  We walk up the ancestor tree through a small set of
    // node types and if this is detected to be a guard, then the
    // conditional node is marked as a guard for this polyfill.
    Context context = contextStack.peek();
    if (!context.safe) {
      return false;
    }

    // Loop over all the linked conditionals and register this as a guard.
    while (context != null && context.conditional != null) {
      registeredGuards.put(context.conditional, resource);
      context = context.linked;
    }
    return true;
  }


  // The context of a node, keeping track of whether it is safe for
  // possibly-undefined values, and whether there are any conditionals
  // upstream in the tree.
  private static class Context {
    // An empty instance: unsafe and with no linked conditional nodes.
    static final Context EMPTY = new Context(null, false, null);

    // The most recent conditional.
    final Node conditional;
    // Whether this position is safe for an undefined type.
    final boolean safe;
    // A very naive linked list for storing additional conditional nodes.
    final Context linked;

    Context(Node conditional, boolean safe, Context linked) {
      this.conditional = conditional;
      this.safe = safe;
      this.linked = linked;
    }

    // Returns a new Context with a new conditional node and safety status.
    // If the current context already has a conditional, then it is linked
    // so that both can be marked when necessary.
    Context link(Node newConditional, boolean newSafe) {
      return new Context(newConditional, newSafe, this.conditional != null ? this : null);
    }

    // Returns a new Context with a different safety bit, but doesn't
    // change anything else.
    Context propagate(boolean newSafe) {
      return newSafe == safe ? this : new Context(conditional, newSafe, linked);
    }

    // Returns a new context given the current context and the next parent
    // node.  Child is only used to determine whether we're looking at the
    // first child or not.
    Context descend(AbstractCompiler compiler, Node parent, Node child) {
      boolean first = child == parent.getFirstChild();
      switch (parent.getToken()) {
        case CAST:
          // Casts are irrelevant.
          return this;
        case COMMA:
          // `Promise, whatever` is safe.
          // `whatever, Promise` is same as outer context.
          return child == parent.getLastChild() ? this : propagate(true);
        case AND:
          // `Promise && whatever` never returns Promise itself, so it is safe.
          // `whatever && Promise` may return Promise, so return outer context.
          return first ? link(parent, true) : this;
        case OR:
        case COALESCE:
          // `Promise || whatever` and `Promise ?? whatever`
          // may return Promise (unsafe), but is itself a conditional.
          // `whatever || Promise` and `whatever ?? Promise`
          // is same as outer context.
          return first ? link(parent, false) : this;
        case HOOK:
          // `Promise ? whatever : whatever` is a safe conditional.
          // `whatever ? Promise : whatever` (etc) is same as outer context.
          return first ? link(parent, true) : this;
        case IF:
          // `if (Promise) whatever` is a safe conditional.
          // `if (whatever) { ... }` is nothing.
          // TODO(sdh): Handle do/while/for/for-of/for-in?
          return first ? link(parent, true) : EMPTY;
        case INSTANCEOF:
        case ASSIGN:
          // `Promise instanceof whatever` is safe, `whatever instanceof Promise` is not.
          // `Promise = whatever` is a bad idea, but it's safe w.r.t. polyfills.
          return propagate(first);
        case TYPEOF:
        case NOT:
        case EQ:
        case NE:
        case SHEQ:
        case SHNE:
          // `typeof Promise` is always safe, as is `Promise == whatever`, etc.
          return propagate(true);
        case CALL:
          // `String(Promise)` is safe, `Promise(whatever)` or `whatever(Promise)` is not.
          return propagate(!first && isPropertyTestFunction(compiler, parent));
        case ROOT:
          // This case causes problems for isStatement() so handle it separately.
          return EMPTY;
        case OPTCHAIN_CALL:
        case OPTCHAIN_GETELEM:
        case OPTCHAIN_GETPROP:
          if (first) {
            // thisNode?.rest.of.chain
            // OR firstChild?.thisNode.rest.of.chain
            // For the first case `thisNode` should be considered intrinsically guarded.
            return link(parent, parent.isOptionalChainStart());
          } else {
            // `first?.(thisNode)`
            // or `first?.[thisNode]`
            // or `first?.thisNode`
            return propagate(false);
          }
        default:
          // Expressions propagate linked conditionals; statements do not.
          return NodeUtil.isStatement(parent) ? EMPTY : propagate(false);
      }
    }
  }

  private static boolean isAbrupt(Node n) {
    return n.isReturn() || n.isThrow();
  }

  // Extend the coding convention's idea of property test functions to also
  // include String() and Boolean().
  private static boolean isPropertyTestFunction(AbstractCompiler compiler, Node n) {
    if (compiler.getCodingConvention().isPropertyTestFunction(n)) {
      return true;
    }
    Node target = n.getFirstChild();
    return target.isName() && PROPERTY_TEST_FUNCTIONS.contains(target.getString());
  }

  // NOTE: we currently assume these are simple (unqualified) names.
  private static final ImmutableSet<String> PROPERTY_TEST_FUNCTIONS =
      ImmutableSet.of("String", "Boolean");

  // Tokens that are allowed to have guards on them (no point doing a hash lookup on
  // any other type of node).
  private static final ImmutableSet<Token> CAN_HAVE_GUARDS =
      Sets.immutableEnumSet(
          Token.AND,
          Token.OR,
          Token.COALESCE,
          Token.HOOK,
          Token.IF,
          Token.BLOCK,
          Token.SCRIPT,
          Token.OPTCHAIN_CALL,
          Token.OPTCHAIN_GETELEM,
          Token.OPTCHAIN_GETPROP);
}
