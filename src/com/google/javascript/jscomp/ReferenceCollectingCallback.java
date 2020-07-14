/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSymbolTable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A helper class for passes that want to access all information about where a variable is
 * referenced and declared at once and then make a decision as to how it should be handled, possibly
 * inlining, reordering, or generating warnings. Callers do this by providing {@link Behavior} and
 * then calling {@link #process(Node, Node)}.
 */
public final class ReferenceCollectingCallback
    implements ScopedCallback, HotSwapCompilerPass, StaticSymbolTable<Var, Reference> {

  /**
   * Maps a given variable to a collection of references to that name. Note that
   * Var objects are not stable across multiple traversals (unlike scope root or
   * name).
   */
  private final Map<Var, ReferenceCollection> referenceMap =
       new LinkedHashMap<>();

  /**
   * The stack of basic blocks and scopes the current traversal is in.
   */
  private List<BasicBlock> blockStack = new ArrayList<>();

  /**
   * Source of behavior at various points in the traversal.
   */
  private final Behavior behavior;

  private final ScopeCreator scopeCreator;

  /**
   * JavaScript compiler to use in traversing.
   */
  private final AbstractCompiler compiler;

  /**
   * Only collect references for filtered variables.
   */
  private final Predicate<Var> varFilter;

  /**
   * Traverse hoisted functions where they're referenced, not
   * where they're declared.
   */
  private final Set<Var> startedFunctionTraverse = new HashSet<>();
  private final Set<Var> finishedFunctionTraverse = new HashSet<>();
  private Scope narrowScope;

  /**
   * Constructor initializes block stack.
   */
  public ReferenceCollectingCallback(AbstractCompiler compiler, Behavior behavior,
      ScopeCreator creator) {
    this(compiler, behavior, creator, Predicates.alwaysTrue());
  }

  /**
   * Constructor only collects references that match the given variable.
   *
   * The test for Var equality uses reference equality, so it's necessary to
   * inject a scope when you traverse.
   */
  ReferenceCollectingCallback(AbstractCompiler compiler, Behavior behavior, ScopeCreator creator,
      Predicate<Var> varFilter) {
    this.compiler = compiler;
    this.behavior = behavior;
    this.scopeCreator = creator;
    this.varFilter = varFilter;
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
   * Targets reference collection to a particular scope.
   */
  void processScope(Scope scope) {
    boolean shouldAddToBlockStack = !scope.isHoistScope();
    this.narrowScope = scope;
    if (shouldAddToBlockStack) {
      blockStack.add(new BasicBlock(null, scope.getRootNode()));
    }
    (new NodeTraversal(compiler, this, scopeCreator)).traverseAtScope(scope);
    if (shouldAddToBlockStack) {
      pop(blockStack);
    }
    this.narrowScope = null;
  }

  /**
   * Same as process but only runs on a part of AST associated to one script.
   */
  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  /**
   * Gets the variables that were referenced in this callback.
   */
  @Override
  public Iterable<Var> getAllSymbols() {
    return referenceMap.keySet();
  }

  @Override
  public Scope getScope(Var var) {
    return var.getScope();
  }

  /**
   * Gets the reference collection for the given variable.
   */
  @Override
  public ReferenceCollection getReferences(Var v) {
    return referenceMap.get(v);
  }

  /**
   * For each node, update the block stack and reference collection
   * as appropriate.
   */
  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isName() || n.isImportStar()) {
      if ((parent.isImportSpec() && n != parent.getLastChild())
          || (parent.isExportSpec() && n != parent.getFirstChild())) {
        // The n in `import {n as x}` or `export {x as n}` are not references, even though
        // they are represented in the AST as NAME nodes.
        return;
      }

      Var v = t.getScope().getVar(n.getString());

      if (v != null) {
        if (varFilter.apply(v)) {
          addReference(v, new Reference(n, t, peek(blockStack)));
        }

        if (v.getParentNode() != null
            && NodeUtil.isHoistedFunctionDeclaration(v.getParentNode())
            // If we're only traversing a narrow scope, do not try to climb outside.
            && (narrowScope == null || narrowScope.getDepth() <= v.getScope().getDepth())) {
          outOfBandTraversal(v);
        }
      }
    }

    if (isBlockBoundary(n, parent)) {
      pop(blockStack);
    }
  }

  private void outOfBandTraversal(Var v) {
    if (startedFunctionTraverse.contains(v)) {
      return;
    }
    startedFunctionTraverse.add(v);

    Node fnNode = v.getParentNode();

    // Replace the block stack with a new one. This algorithm only works
    // because we know hoisted functions cannot be inside loops. It will have to
    // change if we ever do general function continuations.
    checkState(NodeUtil.isHoistedFunctionDeclaration(fnNode), fnNode);

    Scope containingScope = v.getScope();

    // This is tricky to compute because of the weird traverseAtScope call for
    // AggressiveInlineAliases.
    List<BasicBlock> newBlockStack = null;
    if (containingScope.isGlobal()) {
      newBlockStack = new ArrayList<>();
      newBlockStack.add(blockStack.get(0));
    } else {
      for (int i = 0; i < blockStack.size(); i++) {
        if (blockStack.get(i).getRoot() == containingScope.getRootNode()) {
          newBlockStack = new ArrayList<>(blockStack.subList(0, i + 1));
        }
      }
    }
    checkNotNull(newBlockStack);

    List<BasicBlock> oldBlockStack = blockStack;
    blockStack = newBlockStack;

    NodeTraversal outOfBandTraversal = new NodeTraversal(compiler, this, scopeCreator);
    outOfBandTraversal.traverseFunctionOutOfBand(fnNode, containingScope);

    blockStack = oldBlockStack;
    finishedFunctionTraverse.add(v);
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
    // are added in shouldTraverse() or processScope() and removed in visit().
    if (t.isHoistScope()) {
      blockStack.add(new BasicBlock(parent, n));
    }
  }

  /**
   * Updates block stack and invokes any additional behavior.
   */
  @Override
  public void exitScope(NodeTraversal t) {
    if (t.isHoistScope()) {
      pop(blockStack);
    }
    behavior.afterExitScope(t, new ReferenceMapWrapper(referenceMap));
  }

  /**
   * Updates block stack.
   */
  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    // We automatically traverse a hoisted function body when that function
    // is first referenced, so that the reference lists are in the right order.
    //
    // TODO(nicksantos): Maybe generalize this to a continuation mechanism
    // like in RemoveUnusedCode.
    if (NodeUtil.isHoistedFunctionDeclaration(n)) {
      Node nameNode = n.getFirstChild();
      Var functionVar = nodeTraversal.getScope().getVar(nameNode.getString());
      checkNotNull(functionVar);
      if (finishedFunctionTraverse.contains(functionVar)) {
        return false;
      }
      startedFunctionTraverse.add(functionVar);
    }

    // If node is a new basic block, put on basic block stack
    if (isBlockBoundary(n, parent)) {
      blockStack.add(new BasicBlock(peek(blockStack), n));
    }

    // Add the second x before the first one in "let [x] = x;". VariableReferenceCheck
    // relies on reference order to give a warning.
    if ((n.isDefaultValue() || n.isDestructuringLhs()) && n.hasTwoChildren()) {
      Scope scope = nodeTraversal.getScope();
      nodeTraversal.traverseInnerNode(n.getSecondChild(), n, scope);
      nodeTraversal.traverseInnerNode(n.getFirstChild(), n, scope);
      return false;
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
        case FOR_AWAIT_OF:
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
        case COALESCE:
        case OPTCHAIN_GETPROP:
        case OPTCHAIN_GETELEM:
        case OPTCHAIN_CALL:
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
    ReferenceCollection referenceInfo =
        referenceMap.computeIfAbsent(v, k -> new ReferenceCollection());

    // Add this particular reference
    referenceInfo.add(reference);
  }

  static class ReferenceMapWrapper implements ReferenceMap {
    private final Map<Var, ReferenceCollection> referenceMap;

    public ReferenceMapWrapper(Map<Var, ReferenceCollection> referenceMap) {
      this.referenceMap = referenceMap;
    }

    @Override
    public ReferenceCollection getReferences(Var var) {
      return referenceMap.get(var);
    }

    Map<Var, ReferenceCollection> getRawReferenceMap() {
      return referenceMap;
    }

    @Override
    public String toString() {
      return referenceMap.toString();
    }
  }

  /**
   * Way for callers to add specific behavior during traversal that
   * utilizes the built-up reference information.
   */
  public interface Behavior {
    /**
     * Called after we finish with a scope.
     */
    void afterExitScope(NodeTraversal t, ReferenceMap referenceMap);
  }

  static final Behavior DO_NOTHING_BEHAVIOR = new Behavior() {
    @Override
    public void afterExitScope(NodeTraversal t, ReferenceMap referenceMap) {}
  };

}
