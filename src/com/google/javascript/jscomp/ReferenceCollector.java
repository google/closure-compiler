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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSymbolTable;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.jspecify.nullness.Nullable;

/**
 * A helper class for passes that want to access all information about where a variable is
 * referenced and declared at once and then make a decision as to how it should be handled, possibly
 * inlining, reordering, or generating warnings. Callers do this by providing {@link Behavior} and
 * then calling {@link #process(Node, Node)}.
 */
public final class ReferenceCollector implements CompilerPass, StaticSymbolTable<Var, Reference> {

  /**
   * Maps a given variable to a collection of references to that name. Note that
   * Var objects are not stable across multiple traversals (unlike scope root or
   * name).
   */
  private final Map<Var, ReferenceCollection> referenceMap =
       new LinkedHashMap<>();

  /** The stack of basic blocks and scopes the current traversal is in. */
  private ArrayDeque<BasicBlock> blockStack = new ArrayDeque<>();

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

  private final CollectorCallback callback = new CollectorCallback();

  private final LinkedHashSet<Node> collectedHoistedFunctions = new LinkedHashSet<>();

  private @Nullable Scope narrowScope;

  /** Constructor initializes block stack. */
  public ReferenceCollector(AbstractCompiler compiler, Behavior behavior, ScopeCreator creator) {
    this(compiler, behavior, creator, Predicates.alwaysTrue());
  }

  /**
   * Constructor only collects references that match the given variable.
   *
   * <p>The test for Var equality uses reference equality, so it's necessary to inject a scope when
   * you traverse.
   */
  ReferenceCollector(
      AbstractCompiler compiler,
      Behavior behavior,
      ScopeCreator creator,
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
    this.createTraversalBuilder().traverseRoots(externs, root);
  }

  public void process(Node root) {
    this.createTraversalBuilder().traverse(root);
  }

  /**
   * Targets reference collection to a particular scope.
   */
  void processScope(Scope scope) {
    boolean shouldAddToBlockStack = !scope.isHoistScope();
    this.narrowScope = scope;
    if (shouldAddToBlockStack) {
      this.pushNewBlock(scope.getRootNode());
    }
    this.createTraversalBuilder().traverseAtScope(scope);
    if (shouldAddToBlockStack) {
      this.popLastBlock(scope.getRootNode());
    }
    this.narrowScope = null;
  }

  /**
   * Same as process but only runs on a part of AST associated to one script.
   */
  private NodeTraversal.Builder createTraversalBuilder() {
    return NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(this.callback)
        .setScopeCreator(scopeCreator)
        .setObeyDestructuringAndDefaultValueExecutionOrder(true);
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

  private void maybeJumpToHoistedFunction(Var var, NodeTraversal t) {
    Node fnNode = var.getParentNode();
    Scope varScope = var.getScope();

    if (fnNode == null
        || !NodeUtil.isHoistedFunctionDeclaration(fnNode)
        // If we're only traversing a narrow scope, do not try to climb outside.
        || (narrowScope != null && narrowScope.getDepth() > varScope.getDepth())
        || this.collectedHoistedFunctions.contains(fnNode)) {
      return;
    }

    /**
     * Replace the block stack with a new one matching the hoist position.
     *
     * <p>This algorithm only works because we know hoisted functions cannot be inside loops. It
     * will have to change if we ever do general function continuations.
     *
     * <p>This is tricky to compute because of the weird traverseAtScope call for
     * AggressiveInlineAliases.
     */
    ArrayDeque<BasicBlock> oldBlockStack = this.blockStack;
    this.blockStack = new ArrayDeque<>();

    if (varScope.isGlobal()) {
      this.blockStack.addLast(oldBlockStack.getFirst());
    } else {
      for (BasicBlock b : oldBlockStack) {
        this.blockStack.addLast(b);
        if (b.getRoot() == varScope.getRootNode()) {
          break;
        }
      }
    }

    /**
     * Record the function declaration reference explicitly because it is a bleeding name. The
     * reference must be recorded with the containing scope, and traverseAtScope skips bleeding
     * function NAME nodes
     */
    this.addReference(var, fnNode.getFirstChild(), t);
    this.createTraversalBuilder().traverseAtScope(scopeCreator.createScope(fnNode, varScope));

    this.blockStack = oldBlockStack;
  }

  private final class CollectorCallback implements ScopedCallback {
    /** Updates block stack. */
    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      // We automatically traverse a hoisted function body when that function
      // is first referenced, so that the reference lists are in the right order.
      //
      // TODO(nicksantos): Maybe generalize this to a continuation mechanism
      // like in RemoveUnusedCode.
      if (NodeUtil.isHoistedFunctionDeclaration(n)) {
        if (!collectedHoistedFunctions.add(n)) {
          return false;
        }
      }

      // If node is a new basic block, put on basic block stack
      if (isBlockBoundary(n, parent)) {
        pushNewBlock(n);
      }

      return true;
    }

    /** Updates block stack and invokes any additional behavior. */
    @Override
    public void enterScope(NodeTraversal t) {
      // Don't add all ES6 scope roots to blockStack, only those that are also scopes according to
      // the ES5 scoping rules. Other nodes that ought to be considered the root of a BasicBlock
      // are added in shouldTraverse() or processScope() and removed in visit().
      if (t.isHoistScope()) {
        pushNewBlock(t.getScopeRoot());
      }
    }

    /** Updates block stack and invokes any additional behavior. */
    @Override
    public void exitScope(NodeTraversal t) {
      if (t.isHoistScope()) {
        popLastBlock(t.getScopeRoot());
      }
      behavior.afterExitScope(t, new ReferenceMapWrapper(referenceMap));
    }

    /** For each node, update the block stack and reference collection as appropriate. */
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
          addReference(v, n, t);
          maybeJumpToHoistedFunction(v, t);
        }
      }

      if (isBlockBoundary(n, parent)) {
        popLastBlock(n);
      }
    }
  }

  private void pushNewBlock(Node root) {
    this.blockStack.addLast(new BasicBlock(this.blockStack.peekLast(), root));
  }

  private void popLastBlock(Node root) {
    BasicBlock last = this.blockStack.removeLast();
    // Verfiy that the stack is unwound correctly.
    checkState(identical(root, last.getRoot()), root);
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
        case DEFAULT_VALUE:
          // The first child of a conditional is not a boundary,
          // but all the rest of the children are.
          return n != parent.getFirstChild();

        default:
          break;
      }
    }

    return n.isCase();
  }

  private void addReference(Var v, Node n, NodeTraversal t) {
    if (!this.varFilter.apply(v)) {
      return;
    }

    // Create collection if none already
    ReferenceCollection collection =
        referenceMap.computeIfAbsent(v, k -> new ReferenceCollection());

    // Add this particular reference
    collection.add(new Reference(n, t, blockStack.getLast()));
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
