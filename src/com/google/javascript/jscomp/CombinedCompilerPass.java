/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;

import java.util.List;

/**
 * <p>A compiler pass combining multiple {@link Callback}
 * and {@link ScopedCallback} objects. This pass can be used to separate
 * logically different verifications without incurring any additional traversal
 * and CFG generation costs.</p>
 *
 * <p>Due to this compiler pass' nature, none of the callbacks may mutate
 * the parse tree.</p>
 *
 * <p>TODO(user):
 * This combined pass is currently limited in the type of callbacks it can
 * combine due to the difficulty of handling NodeTraversal's methods that
 * initiate more recursion (e.g., {@link NodeTraversal#traverse(Node)} and
 * {@link NodeTraversal#traverseInnerNode(Node, Node, Scope)}). The
 * {@link NodeTraversal} object passed to the individual callbacks should
 * be instrumented to emulate the correct behavior. For instance,
 * one could create a {@link NodeTraversal} whose
 * {@link NodeTraversal#traverseInnerNode(Node, Node, Scope)} ties
 * back into this compiler pass to give it context about what combined
 * passes are doing.</p>
 *
 */
final class CombinedCompilerPass implements HotSwapCompilerPass,
    ScopedCallback {

  /** The callbacks that this pass combines. */
  private final CallbackWrapper[] callbacks;
  private final AbstractCompiler compiler;

  /**
   * Creates a combined compiler pass.
   * @param compiler the compiler
   */
  CombinedCompilerPass(
      AbstractCompiler compiler, Callback... callbacks) {
    this(compiler, Lists.<Callback>newArrayList(callbacks));
  }

  CombinedCompilerPass(
      AbstractCompiler compiler, List<Callback> callbacks) {
    this.compiler = compiler;
    this.callbacks = new CallbackWrapper[callbacks.size()];
    for (int i = 0; i < callbacks.size(); i++) {
      this.callbacks[i] = new CallbackWrapper(callbacks.get(i));
    }
  }

  static void traverse(AbstractCompiler compiler, Node root,
      List<Callback> callbacks) {
    if (callbacks.size() == 1) {
      NodeTraversal.traverse(compiler, root, callbacks.get(0));
    } else {
      (new CombinedCompilerPass(compiler, callbacks)).process(null, root);
    }
  }

  /**
   * Maintains information about a callback in order to simulate it being the
   * exclusive client of the shared {@link NodeTraversal}. In particular, this
   * class simulates abbreviating the traversal when the wrapped callback
   * returns false for
   * {@link Callback#shouldTraverse(NodeTraversal, Node, Node)}.
   * The callback becomes inactive (i.e., traversal messages are not sent to it)
   * until the main traversal revisits the node during the post-order visit.
   *
   */
  private static class CallbackWrapper {
    /** The callback being wrapped. Never null. */
    private final Callback callback;
    /**
     * if (callback instanceof ScopedCallback), then scopedCallback points
     * to an instance of ScopedCallback, otherwise scopedCallback points to null
     */
    private final ScopedCallback scopedCallback;

    /**
     * The node that {@link Callback#shouldTraverse(NodeTraversal, Node, Node)}
     * returned false for. The wrapped callback doesn't receive messages until
     * after this node is revisited in the post-order traversal.
     */
    private Node waiting = null;

    private CallbackWrapper(Callback callback) {
      this.callback = callback;
      if (callback instanceof ScopedCallback) {
        scopedCallback = (ScopedCallback) callback;
      } else {
        scopedCallback = null;
      }
    }

    /**
     * Visits the node unless the wrapped callback is inactive. Activates the
     * callback if appropriate.
     */
    void visitOrMaybeActivate(NodeTraversal t, Node n, Node parent) {
      if (isActive()) {
        callback.visit(t, n, parent);
      } else if (waiting == n) {
        waiting = null;
      }
    }

    void shouldTraverseIfActive(NodeTraversal t, Node n, Node parent) {
      if (isActive() && !callback.shouldTraverse(t, n, parent)) {
        waiting = n;
      }
    }

    void enterScopeIfActive(NodeTraversal t) {
      if (isActive() && scopedCallback != null) {
        scopedCallback.enterScope(t);
      }
    }

    void exitScopeIfActive(NodeTraversal t) {
      if (isActive() && scopedCallback != null) {
        scopedCallback.exitScope(t);
      }
    }

    boolean isActive() {
      return waiting == null;
    }
  }

  @Override
  public final void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    for (CallbackWrapper callback : callbacks) {
      callback.shouldTraverseIfActive(t, n, parent);
    }
    // Note that this method could return false if all callbacks are inactive.
    // This apparent optimization would make this method more expensive
    // in the typical case where not all nodes are inactive. It is
    // very unlikely that many all callbacks would be inactive at the same
    // time (indeed, there are several checking passes that never return false).
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    for (CallbackWrapper callback : callbacks) {
      callback.visitOrMaybeActivate(t, n, parent);
    }
  }

  @Override
  public void enterScope(NodeTraversal t) {
    for (CallbackWrapper callback : callbacks) {
      callback.enterScopeIfActive(t);
    }
  }

  @Override
  public void exitScope(NodeTraversal t) {
    for (CallbackWrapper callback : callbacks) {
      callback.exitScopeIfActive(t);
    }
  }
}
