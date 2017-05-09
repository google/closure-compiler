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

import static com.google.common.base.MoreObjects.toStringHelper;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A helper class for passes that want to access all information about where a variable is
 * referenced and declared at once and then make a decision as to how it should be handled, possibly
 * inlining, reordering, or generating warnings. Callers do this by providing {@link Behavior} and
 * then calling {@link #process(Node, Node)}.
 *
 * @author kushal@google.com (Kushal Dave)
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
    this(compiler, behavior, creator, Predicates.<Var>alwaysTrue());
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
    this.narrowScope = scope;
    (new NodeTraversal(compiler, this, scopeCreator)).traverseAtScope(scope);
    this.narrowScope = null;
  }

  /**
   * Same as process but only runs on a part of AST associated to one script.
   */
  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
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
    return var.scope;
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
    if (n.isName() || (n.isStringKey() && !n.hasChildren())) {
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
    // CollapseProperties.
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
    if (t.inGlobalScope()) {
      // Update global scope reference lists when we are done with it.
      compiler.updateGlobalVarReferences(referenceMap, t.getScopeRoot());
      behavior.afterExitScope(t, compiler.getGlobalVarReferences());
    } else {
      behavior.afterExitScope(t, new ReferenceMapWrapper(referenceMap));
    }
  }

  /**
   * Updates block stack.
   */
  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
      Node parent) {
    // We automatically traverse a hoisted function body when that function
    // is first referenced, so that the reference lists are in the right order.
    //
    // TODO(nicksantos): Maybe generalize this to a continuation mechanism
    // like in RemoveUnusedVars.
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

  public interface ReferenceMap {
    ReferenceCollection getReferences(Var var);
  }

  private static class ReferenceMapWrapper implements ReferenceMap {
    private final Map<Var, ReferenceCollection> referenceMap;

    public ReferenceMapWrapper(Map<Var, ReferenceCollection> referenceMap) {
      this.referenceMap = referenceMap;
    }

    @Override
    public ReferenceCollection getReferences(Var var) {
      return referenceMap.get(var);
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

  /**
   * A collection of references. Can be subclassed to apply checks or
   * store additional state when adding.
   */
  public static final class ReferenceCollection implements Iterable<Reference> {

    List<Reference> references = new ArrayList<>();

    @Override
    public Iterator<Reference> iterator() {
      return references.iterator();
    }

    void add(Reference reference) {
      references.add(reference);
    }

    /**
     * Determines if the variable for this reference collection is
     * "well-defined." A variable is well-defined if we can prove at
     * compile-time that it's assigned a value before it's used.
     *
     * Notice that if this function returns false, this doesn't imply that the
     * variable is used before it's assigned. It just means that we don't
     * have enough information to make a definitive judgment.
     */
    protected boolean isWellDefined() {
      int size = references.size();
      if (size == 0) {
        return false;
      }

      // If this is a declaration that does not instantiate the variable,
      // it's not well-defined.
      Reference init = getInitializingReference();
      if (init == null) {
        return false;
      }

      checkState(references.get(0).isDeclaration());
      BasicBlock initBlock = init.getBasicBlock();
      for (int i = 1; i < size; i++) {
        if (!initBlock.provablyExecutesBefore(
                references.get(i).getBasicBlock())) {
          return false;
        }
      }

      return true;
    }

    /**
     * Whether the variable is escaped into an inner function.
     */
    boolean isEscaped() {
      Scope hoistScope = null;
      for (Reference ref : references) {
        if (hoistScope == null) {
          hoistScope = ref.getScope().getClosestHoistScope();
        } else if (hoistScope != ref.getScope().getClosestHoistScope()) {
          return true;
        }
      }
      return false;
    }

    /**
     * @param index The index into the references array to look for an
     * assigning declaration.
     *
     * This is either the declaration if a value is assigned (such as
     * "var a = 2", "function a()...", "... catch (a)...").
     */
    private boolean isInitializingDeclarationAt(int index) {
      Reference maybeInit = references.get(index);
      if (maybeInit.isInitializingDeclaration()) {
        // This is a declaration that represents the initial value.
        // Specifically, var declarations without assignments such as "var a;"
        // are not.
        return true;
      }
      return false;
    }

    /**
     * @param index The index into the references array to look for an
     * initialized assignment reference. That is, an assignment immediately
     * follow a variable declaration that itself does not initialize the
     * variable.
     */
    private boolean isInitializingAssignmentAt(int index) {
      if (index < references.size() && index > 0) {
        Reference maybeDecl = references.get(index - 1);
        if (maybeDecl.isVarDeclaration() || maybeDecl.isLetDeclaration()) {
          checkState(!maybeDecl.isInitializingDeclaration());
          Reference maybeInit = references.get(index);
          if (maybeInit.isSimpleAssignmentToName()) {
            return true;
          }
        }
      }
      return false;
    }

    /**
     * @return The reference that provides the value for the variable at the
     * time of the first read, if known, otherwise null.
     *
     * This is either the variable declaration ("var a = ...") or first
     * reference following the declaration if it is an assignment.
     */
    Reference getInitializingReference() {
      if (isInitializingDeclarationAt(0)) {
        return references.get(0);
      } else if (isInitializingAssignmentAt(1)) {
        return references.get(1);
      }
      return null;
    }

    /**
     * Constants are allowed to be defined after their first use.
     */
    Reference getInitializingReferenceForConstants() {
      int size = references.size();
      for (int i = 0; i < size; i++) {
        if (isInitializingDeclarationAt(i) || isInitializingAssignmentAt(i)) {
          return references.get(i);
        }
      }
      return null;
    }

    /**
     * @return Whether the variable is only assigned a value once for its
     *     lifetime.
     */
    boolean isAssignedOnceInLifetime() {
      Reference ref = getOneAndOnlyAssignment();
      if (ref == null) {
        return false;
      }

      // Make sure this assignment is not in a loop or an enclosing function.
      for (BasicBlock block = ref.getBasicBlock();
           block != null; block = block.getParent()) {
        if (block.isFunction()) {
          if (ref.getSymbol().getScope().getClosestHoistScope()
              != ref.getScope().getClosestHoistScope()) {
            return false;
          }
          break;
        } else if (block.isLoop()) {
          return false;
        }
      }

      return true;
    }

    /**
     * @return The one and only assignment. Returns null if the number of assignments is not
     *     exactly one.
     */
    private Reference getOneAndOnlyAssignment() {
      Reference assignment = null;
      int size = references.size();
      for (int i = 0; i < size; i++) {
        Reference ref = references.get(i);
        if (ref.isLvalue() || ref.isInitializingDeclaration()) {
          if (assignment == null) {
            assignment = ref;
          } else {
            return null;
          }
        }
      }
      return assignment;
    }

    /**
     * @return Whether the variable is never assigned a value.
     */
    boolean isNeverAssigned() {
      int size = references.size();
      for (int i = 0; i < size; i++) {
        Reference ref = references.get(i);
        if (ref.isLvalue() || ref.isInitializingDeclaration()) {
          return false;
        }
      }
      return true;
    }

    boolean firstReferenceIsAssigningDeclaration() {
      int size = references.size();
      return size > 0 && references.get(0).isInitializingDeclaration();
    }

    @Override
    public String toString() {
      return toStringHelper(this)
          .add("initRef", getInitializingReference())
          .add("references", references)
          .add("wellDefined", isWellDefined())
          .add("assignedOnce", isAssignedOnceInLifetime())
          .toString();
    }
  }

}
