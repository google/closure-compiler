/*
 * Copyright 2004 The Closure Compiler Authors.
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

import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

/**
 * NodeTraversal allows an iteration through the nodes in the parse tree,
 * and facilitates the optimizations on the parse tree.
 *
 */
public class NodeTraversal {
  private final AbstractCompiler compiler;
  private final Callback callback;

  /** Contains the current node*/
  private Node curNode;

  public static final DiagnosticType NODE_TRAVERSAL_ERROR =
      DiagnosticType.error("JSC_NODE_TRAVERSAL_ERROR", "{0}");

  /**
   * Stack containing the Scopes that have been created. The Scope objects
   * are lazily created; so the {@code scopeRoots} stack contains the
   * Nodes for all Scopes that have not been created yet.
   */
  private final Deque<Scope> scopes = new ArrayDeque<>();

  /**
   * A stack of scope roots. All scopes that have not been created
   * are represented in this Deque.
   */
  private final Deque<Node> scopeRoots = new ArrayDeque<>();

  /**
   * A stack of scope roots that are valid cfg roots. All cfg roots that have not been created
   * are represented in this Deque.
   */
  private final Deque<Node> cfgRoots = new ArrayDeque<>();


  /**
   * Stack of control flow graphs (CFG). There is one CFG per scope. CFGs
   * are lazily populated: elements are {@code null} until requested by
   * {@link #getControlFlowGraph()}. Note that {@link ArrayDeque} does not allow
   * {@code null} elements, so {@link LinkedList} is used instead.
   */
  Deque<ControlFlowGraph<Node>> cfgs = new LinkedList<>();

  /** The current source file name */
  private String sourceName;

  /** The current input */
  private InputId inputId;

  /** The scope creator */
  private final ScopeCreator scopeCreator;
  private final boolean useBlockScope;

  /** Possible callback for scope entry and exist **/
  private ScopedCallback scopeCallback;

  /** Callback for passes that iterate over a list of functions */
  public interface FunctionCallback {
    void enterFunction(AbstractCompiler compiler, Node fnRoot);
  }

  /**
   * Callback for tree-based traversals
   */
  public interface Callback {
    /**
     * <p>Visits a node in pre order (before visiting its children) and decides
     * whether this node's children should be traversed. If children are
     * traversed, they will be visited by
     * {@link #visit(NodeTraversal, Node, Node)} in postorder.</p>
     * <p>Implementations can have side effects (e.g. modifying the parse
     * tree).</p>
     * @return whether the children of this node should be visited
     */
    boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent);

    /**
     * <p>Visits a node in postorder (after its children have been visited).
     * A node is visited only if all its parents should be traversed
     * ({@link #shouldTraverse(NodeTraversal, Node, Node)}).</p>
     * <p>Implementations can have side effects (e.g. modifying the parse
     * tree).</p>
     */
    void visit(NodeTraversal t, Node n, Node parent);
  }

  /**
   * Callback that also knows about scope changes
   */
  public interface ScopedCallback extends Callback {

    /**
     * Called immediately after entering a new scope. The new scope can
     * be accessed through t.getScope()
     */
    void enterScope(NodeTraversal t);

    /**
     * Called immediately before exiting a scope. The ending scope can
     * be accessed through t.getScope()
     */
    void exitScope(NodeTraversal t);
  }

  /**
   * Abstract callback to visit all nodes in postorder.
   */
  public abstract static class AbstractPostOrderCallback implements Callback {
    @Override
    public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
        Node parent) {
      return true;
    }
  }

  /** Abstract callback to visit all nodes in preorder. */
  public abstract static class AbstractPreOrderCallback implements Callback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {}
  }

  /**
   * Abstract scoped callback to visit all nodes in postorder.
   */
  public abstract static class AbstractScopedCallback
      implements ScopedCallback {
    @Override
    public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
        Node parent) {
      return true;
    }

    @Override
    public void enterScope(NodeTraversal t) {}

    @Override
    public void exitScope(NodeTraversal t) {}
  }

  /**
   * Abstract callback to visit all nodes but not traverse into function
   * bodies.
   */
  public abstract static class AbstractShallowCallback implements Callback {
    @Override
    public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
        Node parent) {
      // We do want to traverse the name of a named function, but we don't
      // want to traverse the arguments or body.
      return parent == null || !parent.isFunction() ||
          n == parent.getFirstChild();
    }
  }

  /**
   * Abstract callback to visit all structure and statement nodes but doesn't
   * traverse into functions or expressions.
   */
  public abstract static class AbstractShallowStatementCallback
      implements Callback {
    @Override
    public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
        Node parent) {
      return parent == null || NodeUtil.isControlStructure(parent)
         || NodeUtil.isStatementBlock(parent);
    }
  }

  /**
   * Abstract callback to visit a pruned set of nodes.
   */
  public abstract static class AbstractNodeTypePruningCallback
        implements Callback {
    private final Set<Integer> nodeTypes;
    private final boolean include;

    /**
     * Creates an abstract pruned callback.
     * @param nodeTypes the nodes to include in the traversal
     */
    public AbstractNodeTypePruningCallback(Set<Integer> nodeTypes) {
      this(nodeTypes, true);
    }

    /**
     * Creates an abstract pruned callback.
     * @param nodeTypes the nodes to include/exclude in the traversal
     * @param include whether to include or exclude the nodes in the traversal
     */
    public AbstractNodeTypePruningCallback(Set<Integer> nodeTypes,
          boolean include) {
      this.nodeTypes = nodeTypes;
      this.include = include;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
        Node parent) {
      return include == nodeTypes.contains(n.getType());
    }
  }

  /**
   * Creates a node traversal using the specified callback interface.
   */
  public NodeTraversal(AbstractCompiler compiler, Callback cb) {
    this(compiler, cb, compiler.getLanguageMode().isEs6OrHigher()
        ? new Es6SyntacticScopeCreator(compiler)
        : SyntacticScopeCreator.makeUntyped(compiler));
  }

  /**
   * Creates a node traversal using the specified callback interface
   * and the scope creator.
   */
  public NodeTraversal(AbstractCompiler compiler, Callback cb,
      ScopeCreator scopeCreator) {
    this.callback = cb;
    if (cb instanceof ScopedCallback) {
      this.scopeCallback = (ScopedCallback) cb;
    }
    this.compiler = compiler;
    this.inputId = null;
    this.sourceName = "";
    this.scopeCreator = scopeCreator;
    this.useBlockScope = scopeCreator.hasBlockScope();
  }

  private void throwUnexpectedException(Exception unexpectedException) {
    // If there's an unexpected exception, try to get the
    // line number of the code that caused it.
    String message = unexpectedException.getMessage();

    // TODO(user): It is possible to get more information if curNode or
    // its parent is missing. We still have the scope stack in which it is still
    // very useful to find out at least which function caused the exception.
    if (inputId != null) {
      message =
          unexpectedException.getMessage() + "\n" +
          formatNodeContext("Node", curNode) +
          (curNode == null ?
              "" :
              formatNodeContext("Parent", curNode.getParent()));
    }
    compiler.throwInternalError(message, unexpectedException);
  }

  private String formatNodeContext(String label, Node n) {
    if (n == null) {
      return "  " + label + ": NULL";
    }
    return "  " + label + "(" + n.toString(false, false, false) + "): "
        + formatNodePosition(n);
  }

  /**
   * Traverses a parse tree recursively.
   */
  public void traverse(Node root) {
    try {
      inputId = NodeUtil.getInputId(root);
      sourceName = "";
      curNode = root;
      pushScope(root);
      // null parent ensures that the shallow callbacks will traverse root
      traverseBranch(root, null);
      popScope();
    } catch (Exception unexpectedException) {
      throwUnexpectedException(unexpectedException);
    }
  }

  void traverseRoots(Node externs, Node root) {
    try {
      Node scopeRoot = externs.getParent();
      Preconditions.checkNotNull(scopeRoot);

      inputId = NodeUtil.getInputId(scopeRoot);
      sourceName = "";
      curNode = scopeRoot;
      pushScope(scopeRoot);

      traverseBranch(externs, scopeRoot);
      Preconditions.checkState(root.getParent() == scopeRoot);
      traverseBranch(root, scopeRoot);

      popScope();
    } catch (Exception unexpectedException) {
      throwUnexpectedException(unexpectedException);
    }
  }

  private static final String MISSING_SOURCE = "[source unknown]";

  private String formatNodePosition(Node n) {
    String sourceFileName = getBestSourceFileName(n);
    if (sourceFileName == null) {
      return MISSING_SOURCE + "\n";
    }

    int lineNumber = n.getLineno();
    int columnNumber = n.getCharno();
    String src = compiler.getSourceLine(sourceFileName, lineNumber);
    if (src == null) {
      src = MISSING_SOURCE;
    }
    return sourceFileName + ":" + lineNumber + ":" + columnNumber + "\n"
        + src + "\n";
  }

  /**
   * Traverses a parse tree recursively with a scope, starting with the given
   * root. This should only be used in the global scope. Otherwise, use
   * {@link #traverseAtScope}.
   */
  void traverseWithScope(Node root, Scope s) {
    Preconditions.checkState(s.isGlobal());
    try {
      inputId = null;
      sourceName = "";
      curNode = root;
      pushScope(s);
      traverseBranch(root, null);
      popScope();
    } catch (Exception unexpectedException) {
      throwUnexpectedException(unexpectedException);
    }
  }

  /**
   * Traverses a parse tree recursively with a scope, starting at that scope's
   * root.
   */
  void traverseAtScope(Scope s) {
    Node n = s.getRootNode();
    if (n.isFunction()) {
      // We need to do some extra magic to make sure that the scope doesn't
      // get re-created when we dive into the function.
      if (inputId == null) {
        inputId = NodeUtil.getInputId(n);
      }
      sourceName = getSourceName(n);
      curNode = n;
      pushScope(s);

      Node args = n.getSecondChild();
      Node body = args.getNext();
      traverseBranch(args, n);
      traverseBranch(body, n);

      popScope();
    } else if (n.isBlock()) {
      if (inputId == null) {
        inputId = NodeUtil.getInputId(n);
      }
      sourceName = getSourceName(n);
      curNode = n;
      pushScope(s);

      // traverseBranch is not called here to avoid re-creating the block scope.
      for (Node child = n.getFirstChild(); child != null; ) {
        Node next = child.getNext();
        traverseBranch(child, n);
        child = next;
      }

      popScope();
    } else {
      Preconditions.checkState(s.isGlobal(), "Expected global scope. Got:", s);
      traverseWithScope(n, s);
    }
  }

  /**
   * Traverse a function out-of-band of normal traversal.
   *
   * @param node The function node.
   * @param scope The scope the function is contained in. Does not fire enter/exit
   *     callback events for this scope.
   */
  public void traverseFunctionOutOfBand(Node node, Scope scope) {
    Preconditions.checkNotNull(scope);
    Preconditions.checkState(node.isFunction());
    Preconditions.checkState(scope.getRootNode() != null);
    if (inputId == null) {
      inputId = NodeUtil.getInputId(node);
    }
    curNode = node.getParent();
    pushScope(scope, true /* quietly */);
    traverseBranch(node, curNode);
    popScope(true /* quietly */);
  }

  /**
   * Traverses an inner node recursively with a refined scope. An inner node may
   * be any node with a non {@code null} parent (i.e. all nodes except the
   * root).
   *
   * @param node the node to traverse
   * @param parent the node's parent, it may not be {@code null}
   * @param refinedScope the refined scope of the scope currently at the top of
   *     the scope stack or in trivial cases that very scope or {@code null}
   */
  void traverseInnerNode(Node node, Node parent, Scope refinedScope) {
    Preconditions.checkNotNull(parent);
    if (inputId == null) {
      inputId = NodeUtil.getInputId(node);
    }
    if (refinedScope != null && getScope() != refinedScope) {
      curNode = node;
      pushScope(refinedScope);
      traverseBranch(node, parent);
      popScope();
    } else {
      traverseBranch(node, parent);
    }
  }

  public AbstractCompiler getCompiler() {
    return compiler;
  }

  /**
   * Gets the current line number, or zero if it cannot be determined. The line
   * number is retrieved lazily as a running time optimization.
   */
  public int getLineNumber() {
    Node cur = curNode;
    while (cur != null) {
      int line = cur.getLineno();
      if (line >= 0) {
        return line;
      }
      cur = cur.getParent();
    }
    return 0;
  }

  /**
   * Gets the current char number, or zero if it cannot be determined. The line
   * number is retrieved lazily as a running time optimization.
   */
  public int getCharno() {
    Node cur = curNode;
    while (cur != null) {
      int line = cur.getCharno();
      if (line >= 0) {
        return line;
      }
      cur = cur.getParent();
    }
    return 0;
  }

  /**
   * Gets the current input source name.
   *
   * @return A string that may be empty, but not null
   */
  public String getSourceName() {
    return sourceName;
  }

  /**
   * Gets the current input source.
   */
  public CompilerInput getInput() {
    return compiler.getInput(inputId);
  }

  /**
   * Gets the current input module.
   */
  public JSModule getModule() {
    CompilerInput input = getInput();
    return input == null ? null : input.getModule();
  }

  /** Returns the node currently being traversed. */
  public Node getCurrentNode() {
    return curNode;
  }

  /**
   * Traversal for passes that work only on changed functions.
   * Suppose a loopable pass P1 uses this traversal.
   * Then, if a function doesn't change between two runs of P1, it won't look at
   * the function the second time.
   * (We're assuming that P1 runs to a fixpoint, o/w we may miss optimizations.)
   *
   * <p>Most changes are reported with calls to Compiler.reportCodeChange(), which
   * doesn't know which scope changed. We keep track of the current scope by
   * calling Compiler.setScope inside pushScope and popScope.
   * The automatic tracking can be wrong in rare cases when a pass changes scope
   * w/out causing a call to pushScope or popScope. It's very hard to find the
   * places where this happens unless a bug is triggered.
   * Passes that do cross-scope modifications call
   * Compiler.reportChangeToEnclosingScope(Node n).
   */
  public static void traverseChangedFunctions(
      AbstractCompiler compiler, FunctionCallback callback) {
    final AbstractCompiler comp = compiler;
    final FunctionCallback cb = callback;
    final Node jsRoot = comp.getJsRoot();
    NodeTraversal.traverseEs6(comp, jsRoot,
        new AbstractPreOrderCallback() {
          @Override
          public final boolean shouldTraverse(NodeTraversal t, Node n, Node p) {
            if ((n == jsRoot || n.isFunction()) && comp.hasScopeChanged(n)) {
              cb.enterFunction(comp, n);
            }
            return true;
          }
        });
  }

  /**
   * Traverses a node recursively.
   * @deprecated Use traverseEs6 whenever possible.
   */
  @Deprecated
  public static void traverse(AbstractCompiler compiler, Node root, Callback cb) {
    NodeTraversal t = new NodeTraversal(compiler, cb);
    t.traverse(root);
  }

  /**
   * Traverses using the ES6SyntacticScopeCreator
   */
  // TODO (stephshi): rename to "traverse" when the old traverse method is no longer used
  public static void traverseEs6(AbstractCompiler compiler, Node root, Callback cb) {
    NodeTraversal t = new NodeTraversal(compiler, cb, new Es6SyntacticScopeCreator(compiler));
    t.traverse(root);
  }

  public static void traverseTyped(AbstractCompiler compiler, Node root, Callback cb) {
    NodeTraversal t = new NodeTraversal(compiler, cb, SyntacticScopeCreator.makeTyped(compiler));
    t.traverse(root);
  }

  @Deprecated
  public static void traverseRoots(
      AbstractCompiler compiler, Callback cb, Node externs, Node root) {
    NodeTraversal t = new NodeTraversal(compiler, cb);
    t.traverseRoots(externs, root);
  }

  public static void traverseRootsEs6(
      AbstractCompiler compiler, Callback cb, Node externs, Node root) {
    NodeTraversal t = new NodeTraversal(compiler, cb, new Es6SyntacticScopeCreator(compiler));
    t.traverseRoots(externs, root);
  }

  public static void traverseRootsTyped(
      AbstractCompiler compiler, Callback cb, Node externs, Node root) {
    NodeTraversal t = new NodeTraversal(compiler, cb, SyntacticScopeCreator.makeTyped(compiler));
    t.traverseRoots(externs, root);
  }

  /**
   * Traverses a branch.
   */
  private void traverseBranch(Node n, Node parent) {
    int type = n.getType();
    if (type == Token.SCRIPT) {
      inputId = n.getInputId();
      sourceName = getSourceName(n);
    }

    curNode = n;
    if (!callback.shouldTraverse(this, n, parent)) {
      return;
    }

    if (type == Token.FUNCTION) {
      traverseFunction(n, parent);
    } else if (type == Token.CLASS) {
      traverseClass(n, parent);
    } else if (useBlockScope && NodeUtil.createsBlockScope(n)) {
      traverseBlockScope(n);
    } else {
      for (Node child = n.getFirstChild(); child != null; ) {
        // child could be replaced, in which case our child node
        // would no longer point to the true next
        Node next = child.getNext();
        traverseBranch(child, n);
        child = next;
      }
    }

    curNode = n;
    callback.visit(this, n, parent);
  }

  /** Traverses a function. */
  private void traverseFunction(Node n, Node parent) {
    final Node fnName = n.getFirstChild();
    boolean isFunctionExpression = (parent != null)
        && NodeUtil.isFunctionExpression(n);

    if (!isFunctionExpression) {
      // Function declarations are in the scope containing the declaration.
      traverseBranch(fnName, n);
    }

    curNode = n;
    pushScope(n);

    if (isFunctionExpression) {
      // Function expression names are only accessible within the function
      // scope.
      traverseBranch(fnName, n);
    }

    final Node args = fnName.getNext();
    final Node body = args.getNext();

    // Args
    traverseBranch(args, n);

    // Body
    // ES6 "arrow" function may not have a block as a body.
    traverseBranch(body, n);

    popScope();
  }

  /** Traverses a class. */
  private void traverseClass(Node n, Node parent) {
    final Node className = n.getFirstChild();
    boolean isClassExpression = NodeUtil.isClassExpression(n);

    if (!isClassExpression) {
      // Class declarations are in the scope containing the declaration.
      traverseBranch(className, n);
    }

    curNode = n;
    pushScope(n);

    if (isClassExpression) {
      // Class expression names are only accessible within the function
      // scope.
      traverseBranch(className, n);
    }

    final Node extendsClause = className.getNext();
    final Node body = extendsClause.getNext();

    // Extends
    traverseBranch(extendsClause, n);

    // Body
    traverseBranch(body, n);

    popScope();
  }

  /** Traverses a non-function block. */
  private void traverseBlockScope(Node n) {
    pushScope(n);
    for (Node child : n.children()) {
      traverseBranch(child, n);
    }
    popScope();
  }

  /** Examines the functions stack for the last instance of a function node. When possible, prefer
   *  this method over NodeUtil.getEnclosingFunction() because this in general looks at less nodes.
   */
  public Node getEnclosingFunction() {
    Node root = getCfgRoot();
    return root.isFunction() ? root : null;
  }

  /** Creates a new scope (e.g. when entering a function). */
  private void pushScope(Node node) {
    Preconditions.checkState(curNode != null);
    Preconditions.checkState(node != null);
    compiler.setScope(node);
    scopeRoots.push(node);
    if (NodeUtil.isValidCfgRoot(node)) {
      cfgRoots.push(node);
      cfgs.push(null);
    }
    if (scopeCallback != null) {
      scopeCallback.enterScope(this);
    }
  }

  /** Creates a new scope (e.g. when entering a function). */
  private void pushScope(Scope s) {
    pushScope(s, false);
  }

  /**
   * Creates a new scope (e.g. when entering a function).
   * @param quietly Don't fire an enterScope callback.
   */
  private void pushScope(Scope s, boolean quietly) {
    Preconditions.checkState(curNode != null);
    compiler.setScope(s.getRootNode());
    scopes.push(s);
    if (NodeUtil.isValidCfgRoot(s.getRootNode())) {
      cfgRoots.push(s.getRootNode());
      cfgs.push(null);
    }
    if (!quietly && scopeCallback != null) {
      scopeCallback.enterScope(this);
    }
  }

  private void popScope() {
    popScope(false);
  }

  /**
   * Pops back to the previous scope (e.g. when leaving a function).
   * @param quietly Don't fire the exitScope callback.
   */
  private void popScope(boolean quietly) {
    if (!quietly && scopeCallback != null) {
      scopeCallback.exitScope(this);
    }
    Node scopeRoot;
    if (scopeRoots.isEmpty()) {
      scopeRoot = scopes.pop().getRootNode();
    } else {
      scopeRoot = scopeRoots.pop();
    }
    if (NodeUtil.isValidCfgRoot(scopeRoot)) {
      Preconditions.checkState(!cfgRoots.isEmpty());
      Preconditions.checkState(cfgRoots.pop() == scopeRoot);
      cfgs.pop();
    }
    if (hasScope()) {
      compiler.setScope(getScopeRoot());
    }
  }

  /** Gets the current scope. */
  public Scope getScope() {
    Scope scope = scopes.isEmpty() ? null : scopes.peek();
    if (scopeRoots.isEmpty()) {
      return scope;
    }

    Iterator<Node> it = scopeRoots.descendingIterator();
    while (it.hasNext()) {
      scope = scopeCreator.createScope(it.next(), scope);
      scopes.push(scope);
    }
    scopeRoots.clear();
    // No need to call compiler.setScope; the top scopeRoot is now the top scope
    return scope;
  }

  public Scope getClosestHoistScope() {
    // TODO(moz): This should not call getScope(). We should find the root of the closest hoist
    // scope and effectively getScope() from there, which avoids scanning inner scopes that might
    // not be needed.
    return getScope().getClosestHoistScope();
  }

  public TypedScope getTypedScope() {
    Scope s = getScope();
    Preconditions.checkState(s instanceof TypedScope,
        "getTypedScope called for untyped traversal");
    return (TypedScope) s;
  }

  /** Gets the control flow graph for the current JS scope. */
  public ControlFlowGraph<Node> getControlFlowGraph() {
    if (cfgs.peek() == null) {
      ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, true);
      cfa.process(null, getCfgRoot());
      cfgs.pop();
      cfgs.push(cfa.getCfg());
    }
    return cfgs.peek();
  }

  /** Returns the current scope's root. */
  public Node getScopeRoot() {
    if (scopeRoots.isEmpty()) {
      return scopes.peek().getRootNode();
    } else {
      return scopeRoots.peek();
    }
  }

  private Node getCfgRoot() {
    return cfgRoots.peek();
  }

  /**
   * Determines whether the traversal is currently in the global scope. Note that this returns false
   * in a global block scope.
   */
  public boolean inGlobalScope() {
    return getScopeDepth() == 0;
  }

  /**
   * Determines whether the hoist scope of the current traversal is global.
   */
  public boolean inGlobalHoistScope() {
    return !getCfgRoot().isFunction();
  }

  int getScopeDepth() {
    int sum = scopes.size() + scopeRoots.size();
    Preconditions.checkState(sum > 0);
    return sum - 1; // Use 0-based scope depth to be consistent within the compiler
  }

  public boolean hasScope() {
    return !(scopes.isEmpty() && scopeRoots.isEmpty());
  }

  /** Reports a diagnostic (error or warning) */
  public void report(Node n, DiagnosticType diagnosticType,
      String... arguments) {
    JSError error = JSError.make(n, diagnosticType, arguments);
    compiler.report(error);
  }

  private static String getSourceName(Node n) {
    String name = n.getSourceFileName();
    return nullToEmpty(name);
  }

  InputId getInputId() {
    return inputId;
  }

  /**
   * Creates a JSError during NodeTraversal.
   *
   * @param n Determines the line and char position within the source file name
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public JSError makeError(Node n, CheckLevel level, DiagnosticType type,
      String... arguments) {
    return JSError.make(n, level, type, arguments);
  }

  /**
   * Creates a JSError during NodeTraversal.
   *
   * @param n Determines the line and char position within the source file name
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public JSError makeError(Node n, DiagnosticType type, String... arguments) {
    return JSError.make(n, type, arguments);
  }

  private String getBestSourceFileName(Node n) {
    return n == null ? sourceName : n.getSourceFileName();
  }
}
