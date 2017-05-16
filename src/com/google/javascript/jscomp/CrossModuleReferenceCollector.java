/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Collects global variable references for use by {@link CrossModuleCodeMotion}. */
public final class CrossModuleReferenceCollector implements ScopedCallback, CompilerPass {

  /** Maps global variable name to the corresponding {@link Var} object. */
  private final Map<String, Var> varsByName = new HashMap<>();

  /**
   * Maps a given variable to a collection of references to that name. Note that
   * Var objects are not stable across multiple traversals (unlike scope root or
   * name).
   */
  private final Map<Var, ReferenceCollection> referenceMap =
       new LinkedHashMap<>();

  /** The stack of basic blocks and scopes the current traversal is in. */
  private final List<BasicBlock> blockStack = new ArrayList<>();

  private final ScopeCreator scopeCreator;

  /**
   * JavaScript compiler to use in traversing.
   */
  private final AbstractCompiler compiler;

  /**
   * Constructor initializes block stack.
   */
  CrossModuleReferenceCollector(AbstractCompiler compiler, ScopeCreator creator) {
    this.compiler = compiler;
    this.scopeCreator = creator;
  }

  /**
   * Convenience method for running this pass over a tree with this
   * class as a callback.
   */
  @Override
  public void process(Node externs, Node root) {
    NodeTraversal t = new NodeTraversal(compiler, this, scopeCreator);
    t.traverseRoots(externs, root);
  }

  public void process(Node root) {
    NodeTraversal t = new NodeTraversal(compiler, this, scopeCreator);
    t.traverse(root);
  }

  /**
   * Gets the variables that were referenced in this callback.
   */
  Iterable<Var> getAllSymbols() {
    return referenceMap.keySet();
  }

  /**
   * Gets the reference collection for the given variable.
   */
  ReferenceCollection getReferences(Var v) {
    return referenceMap.get(v);
  }

  ImmutableMap<String, Var> getGlobalVariableNamesMap() {
    return ImmutableMap.copyOf(varsByName);
  }

  /**
   * For each node, update the block stack and reference collection
   * as appropriate.
   */
  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isName() || (n.isStringKey() && !n.hasChildren())) {
      String varName = n.getString();
      Var v = t.getScope().getVar(varName);

      if (v != null) {
        // Only global, non-exported names can be moved
        if (v.isGlobal() && !compiler.getCodingConvention().isExported(v.getName())) {
          if (varsByName.containsKey(varName)) {
            checkState(Objects.equals(varsByName.get(varName), v));
          } else {
            varsByName.put(varName, v);
          }
          addReference(v, new Reference(n, t, peek(blockStack)));
        }
      }
    }

    if (isBlockBoundary(n, parent)) {
      pop(blockStack);
    }
  }

  /**
   * Updates block stack and invokes any additional behavior.
   */
  @Override
  public void enterScope(NodeTraversal t) {
    Node n = t.getScopeRoot();
    BasicBlock parent = blockStack.isEmpty() ? null : peek(blockStack);
    // Don't add all ES6 scope roots to blockStack, only those that are also scopes according to
    // the ES5 scoping rules. Other nodes that ought to be considered the root of a BasicBlock
    // are added in shouldTraverse() and removed in visit().
    if (t.getScope().isHoistScope()) {
      blockStack.add(new BasicBlock(parent, n));
    }
  }

  /**
   * Updates block stack and invokes any additional behavior.
   */
  @Override
  public void exitScope(NodeTraversal t) {
    if (t.getScope().isHoistScope()) {
      pop(blockStack);
    }
  }

  /**
   * Updates block stack.
   */
  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
      Node parent) {
    // If node is a new basic block, put on basic block stack
    if (isBlockBoundary(n, parent)) {
      blockStack.add(new BasicBlock(peek(blockStack), n));
    }
    return true;
  }

  private static <T> T pop(List<T> list) {
    return list.remove(list.size() - 1);
  }

  private static <T> T peek(List<T> list) {
    return Iterables.getLast(list);
  }

  /**
   * @return true if this node marks the start of a new basic block
   */
  private static boolean isBlockBoundary(Node n, Node parent) {
    if (parent != null) {
      switch (parent.getToken()) {
        case DO:
        case FOR:
        case FOR_IN:
        case FOR_OF:
        case TRY:
        case WHILE:
        case WITH:
        case CLASS:
          // NOTE: TRY has up to 3 child blocks:
          // TRY
          //   BLOCK
          //   BLOCK
          //     CATCH
          //   BLOCK
          // Note that there is an explicit CATCH token but no explicit
          // FINALLY token. For simplicity, we consider each BLOCK
          // a separate basic BLOCK.
          return true;
        case AND:
        case HOOK:
        case IF:
        case OR:
        case SWITCH:
          // The first child of a conditional is not a boundary,
          // but all the rest of the children are.
          return n != parent.getFirstChild();

        default:
          break;
      }
    }

    return n.isCase();
  }

  private void addReference(Var v, Reference reference) {
    // Create collection if none already
    ReferenceCollection referenceInfo = referenceMap.get(v);
    if (referenceInfo == null) {
      referenceInfo = new ReferenceCollection();
      referenceMap.put(v, referenceInfo);
    }

    // Add this particular reference
    referenceInfo.add(reference);
  }
}
