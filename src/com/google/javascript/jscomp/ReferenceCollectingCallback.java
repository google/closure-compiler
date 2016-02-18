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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticRef;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.StaticSymbolTable;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A helper class for passes that want to access all information about where a
 * variable is referenced and declared at once and then make a decision as to
 * how it should be handled, possibly inlining, reordering, or generating
 * warnings. Callers do this by providing {@link Behavior} and then
 * calling {@link #process(Node, Node)}.
 *
 * @author kushal@google.com (Kushal Dave)
 */
class ReferenceCollectingCallback implements ScopedCallback,
    HotSwapCompilerPass,
    StaticSymbolTable<Var, ReferenceCollectingCallback.Reference> {

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
  ReferenceCollectingCallback(AbstractCompiler compiler, Behavior behavior) {
    this(compiler, behavior, Predicates.<Var>alwaysTrue());
  }

  /**
   * Constructor only collects references that match the given variable.
   *
   * The test for Var equality uses reference equality, so it's necessary to
   * inject a scope when you traverse.
   */
  ReferenceCollectingCallback(AbstractCompiler compiler, Behavior behavior,
      Predicate<Var> varFilter) {
    this.compiler = compiler;
    this.behavior = behavior;
    this.varFilter = varFilter;
  }

  /**
   * Convenience method for running this pass over a tree with this
   * class as a callback.
   */
  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, this, externs, root);
  }

  /**
   * Targets reference collection to a particular scope.
   */
  void processScope(Scope scope) {
    this.narrowScope = scope;
    (new NodeTraversal(compiler, this)).traverseAtScope(scope);
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
      Var v;
      if (n.getString().equals("arguments")) {
        v = t.getScope().getArgumentsVar();
      } else {
        v = t.getScope().getVar(n.getString());
      }

      if (v != null) {
        if (varFilter.apply(v)) {
          addReference(v, new Reference(n, t, peek(blockStack)));
        }

        if (v.getParentNode() != null &&
            NodeUtil.isHoistedFunctionDeclaration(v.getParentNode()) &&
            // If we're only traversing a narrow scope, do not try to climb outside.
            (narrowScope == null || narrowScope.getDepth() <= v.getScope().getDepth())) {
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
    Preconditions.checkState(NodeUtil.isHoistedFunctionDeclaration(fnNode));

    Scope containingScope = v.getScope();

    // This is tricky to compute because of the weird traverseAtScope call for
    // CollapseProperties.
    List<BasicBlock> newBlockStack = null;
    if (containingScope.isGlobal()) {
      newBlockStack = new ArrayList<>();
      newBlockStack.add(blockStack.get(0));
    } else {
      for (int i = 0; i < blockStack.size(); i++) {
        if (blockStack.get(i).root == containingScope.getRootNode()) {
          newBlockStack = new ArrayList<>(blockStack.subList(0, i + 1));
        }
      }
    }
    Preconditions.checkNotNull(newBlockStack);

    List<BasicBlock> oldBlockStack = blockStack;
    blockStack = newBlockStack;

    NodeTraversal outOfBandTraversal = new NodeTraversal(compiler, this);
    outOfBandTraversal.traverseFunctionOutOfBand(fnNode, containingScope);

    blockStack = oldBlockStack;
    finishedFunctionTraverse.add(v);
  }

  /**
   * Updates block stack and invokes any additional behavior.
   */
  @Override
  public void enterScope(NodeTraversal t) {
    Node n = t.getScope().getRootNode();
    BasicBlock parent = blockStack.isEmpty() ? null : peek(blockStack);
    blockStack.add(new BasicBlock(parent, n));
  }

  /**
   * Updates block stack and invokes any additional behavior.
   */
  @Override
  public void exitScope(NodeTraversal t) {
    pop(blockStack);
    if (t.getScope().isGlobal()) {
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
      if (functionVar != null) {
        if (finishedFunctionTraverse.contains(functionVar)) {
          return false;
        }
        startedFunctionTraverse.add(functionVar);
      }
    }

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
    return list.get(list.size() - 1);
  }

  /**
   * @return true if this node marks the start of a new basic block
   */
  private static boolean isBlockBoundary(Node n, Node parent) {
    if (parent != null) {
      switch (parent.getType()) {
        case Token.DO:
        case Token.FOR:
        case Token.FOR_OF:
        case Token.TRY:
        case Token.WHILE:
        case Token.WITH:
        case Token.CLASS:
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
        case Token.AND:
        case Token.HOOK:
        case Token.IF:
        case Token.OR:
          // The first child of a conditional is not a boundary,
          // but all the rest of the children are.
          return n != parent.getFirstChild();

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

  interface ReferenceMap {
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
  interface Behavior {
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
  static class ReferenceCollection implements Iterable<Reference> {

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

      Preconditions.checkState(references.get(0).isDeclaration());
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
     * Whether the variable is escaped into an inner scope.
     */
    boolean isEscaped() {
      Scope scope = null;
      for (Reference ref : references) {
        if (scope == null) {
          scope = ref.scope;
        } else if (scope != ref.scope) {
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
        if (maybeDecl.isVarDeclaration()) {
          Preconditions.checkState(!maybeDecl.isInitializingDeclaration());
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

      // Make sure this assignment is not in a loop.
      for (BasicBlock block = ref.getBasicBlock();
           block != null; block = block.getParent()) {
        if (block.isFunction) {
          if (ref.getSymbol().getScope() != ref.scope) {
            return false;
          }
          break;
        } else if (block.isLoop) {
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
  }

  /**
   * Represents a single declaration or reference to a variable.
   */
  static final class Reference implements StaticRef {

    private static final Set<Integer> DECLARATION_PARENTS =
        ImmutableSet.of(Token.VAR, Token.LET, Token.CONST, Token.PARAM_LIST,
            Token.FUNCTION, Token.CLASS, Token.CATCH, Token.REST);

    private final Node nameNode;
    private final BasicBlock basicBlock;
    private final Scope scope;
    private final InputId inputId;
    private final StaticSourceFile sourceFile;

    Reference(Node nameNode, NodeTraversal t,
        BasicBlock basicBlock) {
      this(nameNode, basicBlock, t.getScope(), t.getInput().getInputId());
    }

    @Override
    public String toString() {
      return nameNode.toString();
    }

    /**
     * Creates a variable reference in a given script file name, used in tests.
     *
     * @return The created reference.
     */
    @VisibleForTesting
    static Reference createRefForTest(CompilerInput input) {
      return new Reference(new Node(Token.NAME), null, null,
          input.getInputId());
    }

    private Reference(Node nameNode,
        BasicBlock basicBlock, Scope scope, InputId inputId) {
      this.nameNode = nameNode;
      this.basicBlock = basicBlock;
      this.scope = scope;
      this.inputId = inputId;
      this.sourceFile = nameNode.getStaticSourceFile();
    }

    /**
     * Makes a copy of the current reference using a new Scope instance.
     */
    Reference cloneWithNewScope(Scope newScope) {
      return new Reference(nameNode, basicBlock, newScope, inputId);
    }

    @Override
    public Var getSymbol() {
      return scope.getVar(nameNode.getString());
    }

    @Override
    public Node getNode() {
      return nameNode;
    }

    public InputId getInputId() {
      return inputId;
    }

    @Override
    public StaticSourceFile getSourceFile() {
      return sourceFile;
    }

    boolean isDeclaration() {
      return isDeclarationHelper(nameNode);
    }

    private static boolean isDeclarationHelper(Node node) {
      Node parent = node.getParent();

      // Special case for class B extends A, A is not a declaration.
      if (parent.isClass() && node != parent.getFirstChild()) {
        return false;
      }

      // This condition can be true during InlineVariables.
      if (parent.getParent() == null) {
        return false;
      }

      if (NodeUtil.isNameDeclaration(parent.getParent())
          && node == parent.getLastChild()) {
        // Unless it is something like "for (var/let/const a of x){}",
        // this is the RHS of a var/let/const and thus not a declaration.
        if (parent.getGrandparent() == null
            || !parent.getGrandparent().isForOf()) {
          return false;
        }
      }

      // Special cases for destructuring patterns.
      if (parent.isDestructuringPattern()
          || (parent.isStringKey() && parent.getParent().isObjectPattern())
          || (parent.isComputedProp() && parent.getParent().isObjectPattern()
              && node == parent.getLastChild())
          || (parent.isDefaultValue() && node == parent.getFirstChild())) {
        return isDeclarationHelper(parent);
      }

      // Special case for arrow function
      if (parent.isArrowFunction()) {
        return node == parent.getFirstChild();
      }

      return DECLARATION_PARENTS.contains(parent.getType());
    }

    boolean isVarDeclaration() {
      return getParent().isVar();
    }

    boolean isLetDeclaration() {
      return getParent().isLet();
    }

    boolean isConstDeclaration() {
      return getParent().isConst();
    }

    boolean isHoistedFunction() {
      return NodeUtil.isHoistedFunctionDeclaration(getParent());
    }

    /**
     * Determines whether the variable is initialized at the declaration.
     */
    boolean isInitializingDeclaration() {
      // VAR and LET are the only types of variable declarations that may not initialize
      // their variables. Catch blocks, named functions, and parameters all do.
      return (isDeclaration() && !getParent().isVar() && !getParent().isLet())
        || nameNode.getFirstChild() != null;
    }

   /**
    * @return For an assignment, variable declaration, or function declaration
    * return the assigned value, otherwise null.
    */
    Node getAssignedValue() {
      return NodeUtil.getRValueOfLValue(nameNode);
    }

    BasicBlock getBasicBlock() {
      return basicBlock;
    }

    Node getParent() {
      return getNode().getParent();
    }

    Node getGrandparent() {
      Node parent = getParent();
      return parent == null ? null : parent.getParent();
    }

    private static boolean isLhsOfEnhancedForExpression(Node n) {
      Node parent = n.getParent();
      if (NodeUtil.isNameDeclaration(parent)) {
        return isLhsOfEnhancedForExpression(parent);
      }
      return NodeUtil.isEnhancedFor(parent) && parent.getFirstChild() == n;
    }

    boolean isSimpleAssignmentToName() {
      Node parent = getParent();
      return parent.isAssign()
          && parent.getFirstChild() == nameNode;
    }

    /**
     * Returns whether the name node for this reference is an lvalue.
     * TODO(tbreisacher): This method disagrees with NodeUtil#isLValue for
     * "var x;" and "let x;". Consider updating it to match.
     */
    boolean isLvalue() {
      Node parent = getParent();
      int parentType = parent.getType();
      return (parentType == Token.VAR && nameNode.getFirstChild() != null)
          || (parentType == Token.LET && nameNode.getFirstChild() != null)
          || (parentType == Token.CONST && nameNode.getFirstChild() != null)
          || (parentType == Token.DEFAULT_VALUE && parent.getFirstChild() == nameNode)
          || parentType == Token.INC
          || parentType == Token.DEC
          || parentType == Token.CATCH
          || (NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == nameNode)
          || isLhsOfEnhancedForExpression(nameNode)
          || NodeUtil.isLhsByDestructuring(nameNode);
    }

    Scope getScope() {
      return scope;
    }
  }

  /**
   * Represents a section of code that is uninterrupted by control structures
   * (conditional or iterative logic).
   */
  static final class BasicBlock {

    private final BasicBlock parent;

    private final Node root;

    /**
     * Whether this block denotes a function scope.
     */
    private final boolean isFunction;

    /**
     * Whether this block denotes a loop.
     */
    private final boolean isLoop;

    /**
     * Creates a new block.
     * @param parent The containing block.
     * @param root The root node of the block.
     */
    BasicBlock(BasicBlock parent, Node root) {
      this.parent = parent;
      this.root = root;

      this.isFunction = root.isFunction();

      if (root.getParent() != null) {
        int pType = root.getParent().getType();
        this.isLoop = pType == Token.DO ||
            pType == Token.WHILE ||
            pType == Token.FOR;
      } else {
        this.isLoop = false;
      }
    }

    BasicBlock getParent() {
      return parent;
    }

    /**
     * Determines whether this block is equivalent to the very first block that
     * is created when reference collection traversal enters global scope. Note
     * that when traversing a single script in a hot-swap fashion a new instance
     * of {@code BasicBlock} is created.
     *
     * @return true if this is global scope block.
     */
    boolean isGlobalScopeBlock() {
      return getParent() == null;
    }

    /**
     * Determines whether this block is guaranteed to begin executing before
     * the given block does.
     */
    boolean provablyExecutesBefore(BasicBlock thatBlock) {
      // If thatBlock is a descendant of this block, and there are no hoisted
      // blocks between them, then this block must start before thatBlock.
      BasicBlock currentBlock;
      for (currentBlock = thatBlock;
           currentBlock != null && currentBlock != this;
           currentBlock = currentBlock.getParent()) { }

      if (currentBlock == this) {
        return true;
      }
      return isGlobalScopeBlock() && thatBlock.isGlobalScopeBlock();
    }
  }
}
