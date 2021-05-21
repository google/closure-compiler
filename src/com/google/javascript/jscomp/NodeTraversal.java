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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;

/**
 * NodeTraversal allows an iteration through the nodes in the parse tree, and facilitates the
 * optimizations on the parse tree.
 */
public class NodeTraversal {
  private final AbstractCompiler compiler;
  private final Callback callback;
  private final ScopedCallback scopeCallback;
  private final ScopeCreator scopeCreator;
  private final boolean obeyDestructuringAndDefaultValueExecutionOrder;

  /** Contains the current node */
  private Node currentNode;

  /** Contains the enclosing SCRIPT node if there is one, otherwise null. */
  private Node currentScript;

  /** The change scope for the current node being visiteds */
  private Node currentChangeScope;

  /**
   * Stack containing the Scopes that have been created. The Scope objects are lazily created; so
   * the {@code scopeRoots} stack contains the Nodes for all Scopes that have not been created yet.
   */
  private final Deque<AbstractScope<?, ?>> scopes = new ArrayDeque<>();

  /** A stack of scope roots. See #scopes. */
  private final List<Node> scopeRoots = new ArrayList<>();

  /**
   * Stack containing the control flow graphs (CFG) that have been created. There are fewer CFGs
   * than scopes, since block-level scopes are not valid CFG roots. The CFG objects are lazily
   * populated: elements are simply the CFG root node until requested by {@link
   * #getControlFlowGraph()}.
   */
  private final Deque<Object> cfgs = new ArrayDeque<>();

  /** The current source file name */
  private String sourceName;

  /** The current input */
  private InputId inputId;

  private CompilerInput compilerInput;

  /** Callback for tree-based traversals */
  public interface Callback {
    /**
     * Visits a node in preorder (before its children) and decides whether its children should be
     * traversed. If the children should be traversed, they will be visited by {@link
     * #shouldTraverse(NodeTraversal, Node, Node)} in preorder and by {@link #visit(NodeTraversal,
     * Node, Node)} in postorder.
     *
     * <p>Siblings are always visited left-to-right.
     *
     * <p>Implementations can have side-effects (e.g. modify the parse tree). Removing the current
     * node is legal, but removing or reordering nodes above the current node may cause nodes to be
     * visited twice or not at all.
     *
     * @param t The current traversal.
     * @param n The current node.
     * @param parent The parent of the current node.
     * @return whether the children of this node should be visited
     */
    boolean shouldTraverse(NodeTraversal t, Node n, Node parent);

    /**
     * Visits a node in postorder (after its children). A node is visited in postorder iff {@link
     * #shouldTraverse(NodeTraversal, Node, Node)} returned true for its parent. In particular, the
     * root node is never visited in postorder.
     *
     * <p>Siblings are always visited left-to-right.
     *
     * <p>Implementations can have side-effects (e.g. modify the parse tree). Removing the current
     * node is legal, but removing or reordering nodes above the current node may cause nodes to be
     * visited twice or not at all.
     *
     * @param t The current traversal.
     * @param n The current node.
     * @param parent The parent of the current node.
     */
    void visit(NodeTraversal t, Node n, Node parent);
  }

  /** Callback that also knows about scope changes */
  public interface ScopedCallback extends Callback {

    /**
     * Called immediately after entering a new scope. The new scope can be accessed through
     * t.getScope()
     */
    void enterScope(NodeTraversal t);

    /**
     * Called immediately before exiting a scope. The ending scope can be accessed through
     * t.getScope()
     */
    void exitScope(NodeTraversal t);
  }

  /**
   * Abstract callback to visit all nodes in postorder. Note: Do not create anonymous subclasses of
   * this. Instead, write a lambda expression which will be interpreted as an
   * AbstractPostOrderCallbackInterface.
   */
  public abstract static class AbstractPostOrderCallback implements Callback {
    @Override
    public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return true;
    }
  }

  /**
   * Abstract callback to visit all non-extern nodes in postorder. Note: Even though type-summary
   * nodes are included under the externs roots, they are traversed by this callback.
   */
  public abstract static class ExternsSkippingCallback implements Callback {
    @Override
    public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isScript() || !n.isFromExterns() || NodeUtil.isFromTypeSummary(n);
    }
  }

  /** Abstract callback to visit all nodes in postorder. */
  @FunctionalInterface
  public static interface AbstractPostOrderCallbackInterface {
    void visit(NodeTraversal t, Node n, Node parent);
  }

  /** Abstract callback to visit all nodes in preorder. */
  public abstract static class AbstractPreOrderCallback implements Callback {
    @Override
    public final void visit(NodeTraversal t, Node n, Node parent) {}
  }

  /** Abstract scoped callback to visit all nodes in postorder. */
  public abstract static class AbstractScopedCallback implements ScopedCallback {
    @Override
    public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return true;
    }

    @Override
    public void enterScope(NodeTraversal t) {}

    @Override
    public void exitScope(NodeTraversal t) {}
  }

  /** Abstract callback to visit all nodes but not traverse into function bodies. */
  public abstract static class AbstractShallowCallback implements Callback {
    @Override
    public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      // We do want to traverse the name of a named function, but we don't
      // want to traverse the arguments or body.
      return parent == null || !parent.isFunction() || n == parent.getFirstChild();
    }
  }

  /**
   * Abstract callback to visit all structure and statement nodes but doesn't traverse into
   * functions or expressions.
   */
  public abstract static class AbstractShallowStatementCallback implements Callback {
    @Override
    public final boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return parent == null
          || NodeUtil.isControlStructure(parent)
          || NodeUtil.isStatementBlock(parent);
    }
  }

  /**
   * Abstract callback that knows when a global script, goog.provide file, goog.module,
   * goog.loadModule, ES module or CommonJS module is entered or exited. This includes both whole
   * file modules and bundled modules, as well as files in the global scope.
   */
  public abstract static class AbstractModuleCallback implements Callback {
    protected final AbstractCompiler compiler;
    private final ModuleMetadataMap moduleMetadataMap;

    @Nullable private ModuleMetadata currentModule;
    @Nullable private Node scopeRoot;
    private boolean inLoadModule;

    AbstractModuleCallback(AbstractCompiler compiler, ModuleMetadataMap moduleMetadataMap) {
      this.compiler = compiler;
      this.moduleMetadataMap = moduleMetadataMap;
    }

    /**
     * Called when the traversal enters a global file or module.
     *
     * @param currentModule The entered global file or module.
     * @param moduleScopeRoot The root scope for the entered module or SCRIPT for global files.
     */
    protected void enterModule(ModuleMetadata currentModule, Node moduleScopeRoot) {}

    /**
     * Called when the traversal exits a global file or module.
     *
     * @param oldModule The exited global file or module.
     * @param moduleScopeRoot The root scope for the exited module or SCRIPT for global files.
     */
    protected void exitModule(ModuleMetadata oldModule, Node moduleScopeRoot) {}

    @Override
    public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case SCRIPT:
          currentModule =
              moduleMetadataMap.getModulesByPath().get(t.getInput().getPath().toString());
          checkNotNull(currentModule);
          scopeRoot = n.hasChildren() && n.getFirstChild().isModuleBody() ? n.getFirstChild() : n;
          enterModule(currentModule, scopeRoot);
          break;
        case BLOCK:
          if (NodeUtil.isBundledGoogModuleScopeRoot(n)) {
            scopeRoot = n;
            inLoadModule = true;
          }
          break;
        case CALL:
          if (inLoadModule && n.getFirstChild().matchesQualifiedName("goog.module")) {
            ModuleMetadata newModule =
                moduleMetadataMap.getModulesByGoogNamespace().get(n.getLastChild().getString());
            checkNotNull(newModule);
            // In the event of multiple goog.module statements (an error), don't call enterModule
            // more than once.
            if (!identical(newModule, currentModule)) {
              currentModule = newModule;
              enterModule(currentModule, scopeRoot);
            }
          }
          break;
        default:
          break;
      }
      return shouldTraverse(t, n, currentModule, scopeRoot);
    }

    /**
     * See {@link Callback#shouldTraverse}.
     *
     * @param t The current traversal.
     * @param n The current node.
     * @param currentModule The current module, or null if not inside a module (e.g. AST root).
     * @param moduleScopeRoot The root scope for the current module, or null if not inside a module
     *     (e.g. AST root).
     * @return whether the children of this node should be visited
     */
    protected boolean shouldTraverse(
        NodeTraversal t,
        Node n,
        @Nullable ModuleMetadata currentModule,
        @Nullable Node moduleScopeRoot) {
      return true;
    }

    @Override
    public final void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case SCRIPT:
          checkNotNull(currentModule);
          exitModule(currentModule, scopeRoot);
          currentModule = null;
          scopeRoot = null;
          break;
        case BLOCK:
          if (NodeUtil.isBundledGoogModuleScopeRoot(n)) {
            checkNotNull(currentModule);
            exitModule(currentModule, scopeRoot);
            scopeRoot = n.getGrandparent().getGrandparent();
            inLoadModule = false;
            currentModule =
                moduleMetadataMap.getModulesByPath().get(t.getInput().getPath().toString());
            checkNotNull(currentModule);
          }
          break;
        default:
          break;
      }

      visit(t, n, currentModule, scopeRoot);
    }

    /**
     * See {@link Callback#visit}.
     *
     * @param t The current traversal.
     * @param n The current node.
     * @param currentModule The current module, or null if not inside a module (e.g. AST root).
     * @param moduleScopeRoot The root scope for the current module, or null if not inside a module
     *     (e.g. AST root).
     */
    protected void visit(
        NodeTraversal t,
        Node n,
        @Nullable ModuleMetadata currentModule,
        @Nullable Node moduleScopeRoot) {}
  }

  /** Callback that fires on changed scopes. */
  public abstract static class AbstractChangedScopeCallback extends AbstractPreOrderCallback {
    abstract void enterChangedScopeRoot(AbstractCompiler compiler, Node root);

    @Override
    public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isChangeScopeRoot(n) && t.getCompiler().hasScopeChanged(n)) {
        this.enterChangedScopeRoot(t.getCompiler(), n);
      }
      return true;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder */
  public static final class Builder {
    private Callback callback;
    private AbstractCompiler compiler;
    private ScopeCreator scopeCreator;
    private boolean obeyDestructuringAndDefaultValueExecutionOrder = false;

    private Builder() {}

    public Builder setCallback(Callback x) {
      this.callback = x;
      return this;
    }

    public Builder setCallback(AbstractPostOrderCallbackInterface x) {
      this.callback =
          new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              x.visit(t, n, parent);
            }
          };
      return this;
    }

    public Builder setCompiler(AbstractCompiler x) {
      this.compiler = x;
      return this;
    }

    public Builder setScopeCreator(ScopeCreator x) {
      this.scopeCreator = x;
      return this;
    }

    public Builder setObeyDestructuringAndDefaultValueExecutionOrder(boolean x) {
      this.obeyDestructuringAndDefaultValueExecutionOrder = x;
      return this;
    }

    public NodeTraversal build() {
      return new NodeTraversal(this);
    }

    public void traverse(Node root) {
      this.build().traverse(root);
    }

    void traverseAtScope(AbstractScope<?, ?> scope) {
      this.build().traverseAtScope(scope);
    }

    void traverseRoots(Node externs, Node root) {
      this.build().traverseRoots(externs, root);
    }

    void traverseWithScope(Node root, AbstractScope<?, ?> s) {
      this.build().traverseWithScope(root, s);
    }
  }

  private NodeTraversal(Builder builder) {
    this.compiler = checkNotNull(builder.compiler);
    this.callback = checkNotNull(builder.callback);
    this.scopeCallback =
        (this.callback instanceof ScopedCallback) ? (ScopedCallback) this.callback : null;
    this.scopeCreator =
        (builder.scopeCreator == null)
            ? new SyntacticScopeCreator(this.compiler)
            : builder.scopeCreator;
    this.obeyDestructuringAndDefaultValueExecutionOrder =
        builder.obeyDestructuringAndDefaultValueExecutionOrder;
  }

  private void throwUnexpectedException(Throwable unexpectedException) {
    // If there's an unexpected exception, try to get the
    // line number of the code that caused it.
    String message = unexpectedException.getMessage();

    // TODO(user): It is possible to get more information if currentNode or
    // its parent is missing. We still have the scope stack in which it is still
    // very useful to find out at least which function caused the exception.
    if (currentScript != null) {
      message =
          unexpectedException.getMessage()
              + "\n"
              + formatNodeContext("Node", currentNode)
              + (currentNode == null ? "" : formatNodeContext("Parent", currentNode.getParent()));
    }
    compiler.throwInternalError(message, unexpectedException);
  }

  private String formatNodeContext(String label, Node n) {
    if (n == null) {
      return "  " + label + ": NULL";
    }
    return "  " + label + "(" + n.toString(false, false, false) + "): " + formatNodePosition(n);
  }

  /** Traverses a parse tree recursively. */
  private void traverse(Node root) {
    try {
      initTraversal(root);
      currentNode = root;
      pushScope(root);
      // null parent ensures that the shallow callbacks will traverse root
      traverseBranch(root, null);
      popScope();
    } catch (Error | Exception unexpectedException) {
      throwUnexpectedException(unexpectedException);
    }
  }

  /** Traverses using the SyntacticScopeCreator */
  public static void traverse(AbstractCompiler compiler, Node root, Callback cb) {
    NodeTraversal.builder().setCompiler(compiler).setCallback(cb).traverse(root);
  }

  private void traverseRoots(Node externs, Node root) {
    try {
      Node scopeRoot = externs.getParent();
      checkNotNull(scopeRoot);

      initTraversal(scopeRoot);
      currentNode = scopeRoot;
      pushScope(scopeRoot);

      traverseBranch(externs, scopeRoot);
      checkState(root.getParent() == scopeRoot);
      traverseBranch(root, scopeRoot);

      popScope();
    } catch (Error | Exception unexpectedException) {
      throwUnexpectedException(unexpectedException);
    }
  }

  public static void traverseRoots(
      AbstractCompiler compiler, Callback cb, Node externs, Node root) {
    NodeTraversal.builder().setCompiler(compiler).setCallback(cb).traverseRoots(externs, root);
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
    return sourceFileName + ":" + lineNumber + ":" + columnNumber + "\n" + src + "\n";
  }

  /**
   * Traverses a parse tree recursively with a scope, starting with the given root. This should only
   * be used in the global scope or module scopes. Otherwise, use {@link #traverseAtScope}.
   */
  private void traverseWithScope(Node root, AbstractScope<?, ?> s) {
    checkState(s.isGlobal() || s.isModuleScope(), s);
    try {
      initTraversal(root);
      currentNode = root;
      pushScope(s);
      traverseBranch(root, null);
      popScope();
    } catch (Error | Exception unexpectedException) {
      throwUnexpectedException(unexpectedException);
    }
  }

  /**
   * Traverses a parse tree recursively with a scope, starting at that scope's root. Omits children
   * of the scope root that are traversed in the outer scope (specifically, non-bleeding function
   * and class name nodes, class extends clauses, and computed property keys).
   */
  private void traverseAtScope(AbstractScope<?, ?> s) {
    Node n = s.getRootNode();
    initTraversal(n);
    currentNode = n;
    Deque<AbstractScope<?, ?>> parentScopes = new ArrayDeque<>();
    AbstractScope<?, ?> temp = s.getParent();
    while (temp != null) {
      parentScopes.push(temp);
      temp = temp.getParent();
    }
    while (!parentScopes.isEmpty()) {
      pushScope(parentScopes.pop(), true);
    }
    if (n.isFunction()) {
      if (callback.shouldTraverse(this, n, null)) {
        pushScope(s);

        Node fnName = n.getFirstChild();
        Node args = fnName.getNext();
        Node body = args.getNext();
        if (!NodeUtil.isFunctionDeclaration(n)) {
          // Only traverse the function name if it's a bleeding function expression name.
          traverseBranch(fnName, n);
        }
        traverseBranch(args, n);
        traverseBranch(body, n);

        popScope();
        callback.visit(this, n, null);
      }
    } else if (n.isClass()) {
      if (callback.shouldTraverse(this, n, null)) {
        pushScope(s);

        Node className = n.getFirstChild();
        Node body = n.getLastChild();

        if (NodeUtil.isClassExpression(n)) {
          // Only traverse the class name if it's a bleeding class expression name.
          traverseBranch(className, n);
        }
        // Omit the extends node, which is in the outer scope. Computed property keys are already
        // excluded by handleClassMembers.
        traverseBranch(body, n);

        popScope();
        callback.visit(this, n, null);
      }
    } else if (n.isBlock()) {
      if (callback.shouldTraverse(this, n, null)) {
        pushScope(s);

        // traverseBranch is not called here to avoid re-creating the block scope.
        traverseChildren(n);

        popScope();
        callback.visit(this, n, null);
      }
    } else if (NodeUtil.isAnyFor(n)) {
      if (callback.shouldTraverse(this, n, null)) {
        pushScope(s);

        Node forAssignmentParam = n.getFirstChild();
        Node forIterableParam = forAssignmentParam.getNext();
        Node forBodyScope = forIterableParam.getNext();
        traverseBranch(forAssignmentParam, n);
        traverseBranch(forIterableParam, n);
        traverseBranch(forBodyScope, n);

        popScope();
        callback.visit(this, n, null);
      }
    } else if (n.isSwitch()) {
      if (callback.shouldTraverse(this, n, null)) {
        pushScope(s);

        traverseChildren(n);

        popScope();
        callback.visit(this, n, null);
      }
    } else {
      checkState(s.isGlobal() || s.isModuleScope(), "Expected global or module scope. Got:", s);
      traverseWithScope(n, s);
    }
  }

  /**
   * Traverses *just* the contents of provided scope nodes (and optionally scopes nested within
   * them) but will fall back on traversing the entire AST from root if a null scope nodes list is
   * provided.
   *
   * @param root If scopeNodes is null, this method will just traverse 'root' instead. If scopeNodes
   *     is not null, this parameter is ignored.
   */
  public static void traverseScopeRoots(
      AbstractCompiler compiler,
      @Nullable Node root,
      @Nullable List<Node> scopeNodes,
      Callback cb,
      boolean traverseNested) {
    if (scopeNodes == null) {
      NodeTraversal.traverse(compiler, root, cb);
      return;
    }

    class TraverseScopeRootsCallback implements ScopedCallback {
      boolean insideScopeNode = false;
      Node scopeNode = null;

      @Override
      public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
        if (scopeNode == n) {
          insideScopeNode = true;
        }
        return (traverseNested || scopeNode == n || !NodeUtil.isChangeScopeRoot(n))
            && cb.shouldTraverse(t, n, parent);
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (scopeNode == n) {
          insideScopeNode = false;
        }
        cb.visit(t, n, parent);
      }

      @Override
      public void enterScope(NodeTraversal t) {
        if (insideScopeNode && cb instanceof ScopedCallback) {
          ((ScopedCallback) cb).enterScope(t);
        }
      }

      @Override
      public void exitScope(NodeTraversal t) {
        if (insideScopeNode && cb instanceof ScopedCallback) {
          ((ScopedCallback) cb).exitScope(t);
        }
      }
    }

    TraverseScopeRootsCallback scb = new TraverseScopeRootsCallback();
    MemoizedScopeCreator scopeCreator =
        new MemoizedScopeCreator(new SyntacticScopeCreator(compiler));

    for (final Node scopeNode : scopeNodes) {
      scb.scopeNode = scopeNode;
      NodeTraversal.builder()
          .setCompiler(compiler)
          .setCallback(scb)
          .setScopeCreator(scopeCreator)
          .build()
          .traverseScopeRoot(scopeNode);
    }
  }

  private void traverseScopeRoot(Node scopeRoot) {
    try {
      initTraversal(scopeRoot);
      currentNode = scopeRoot;
      initScopeRoots(scopeRoot.getParent());
      traverseBranch(scopeRoot, scopeRoot.getParent());
    } catch (Error | Exception unexpectedException) {
      throwUnexpectedException(unexpectedException);
    }
  }

  public AbstractCompiler getCompiler() {
    return compiler;
  }

  /**
   * Gets the current line number, or zero if it cannot be determined. The line number is retrieved
   * lazily as a running time optimization.
   */
  public int getLineNumber() {
    Node cur = currentNode;
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
   * Gets the current char number, or zero if it cannot be determined. The line number is retrieved
   * lazily as a running time optimization.
   */
  public int getCharno() {
    Node cur = currentNode;
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
    if (sourceName == null) {
      sourceName = currentScript != null ? currentScript.getSourceFileName() : "";
    }
    return sourceName;
  }

  /** Gets the current input source. */
  public CompilerInput getInput() {
    InputId inputId = getInputId();
    if (compilerInput == null && inputId != null) {
      compilerInput = compiler.getInput(inputId);
    }
    return compilerInput;
  }

  /** Gets the current input module. */
  public JSChunk getChunk() {
    CompilerInput input = getInput();
    return input == null ? null : input.getChunk();
  }

  /** Returns the node currently being traversed. */
  public Node getCurrentNode() {
    return currentNode;
  }

  private void handleScript(Node n, Node parent) {
    if (Platform.isThreadInterrupted()) {
      throw new RuntimeException(new InterruptedException());
    }
    setChangeScope(n);

    currentNode = n;
    currentScript = n;
    clearScriptState();
    if (callback.shouldTraverse(this, n, parent)) {
      traverseChildren(n);
      currentNode = n;
      callback.visit(this, n, parent);
    }
    setChangeScope(null);
  }

  private void handleFunction(Node n, Node parent) {
    Node changeScope = this.currentChangeScope;
    setChangeScope(n);
    currentNode = n;
    if (callback.shouldTraverse(this, n, parent)) {
      traverseFunction(n, parent);
      currentNode = n;
      callback.visit(this, n, parent);
    }
    setChangeScope(changeScope);
  }

  /** Traverses a module. */
  private void handleModule(Node n, Node parent) {
    pushScope(n);
    currentNode = n;
    if (callback.shouldTraverse(this, n, parent)) {
      currentNode = n;
      traverseChildren(n);
      callback.visit(this, n, parent);
    }
    popScope();
  }

  private void handleDestructuringOrDefaultValue(Node n, Node parent) {
    currentNode = n;
    if (callback.shouldTraverse(this, n, parent)) {
      Node first = n.getFirstChild();
      Node second = first.getNext();

      if (second != null) {
        checkState(second.getNext() == null, second);
        traverseBranch(second, n);
      }
      traverseBranch(first, n);

      currentNode = n;
      callback.visit(this, n, parent);
    }
  }

  /** Traverses a branch. */
  private void traverseBranch(Node n, Node parent) {
    switch (n.getToken()) {
      case SCRIPT:
        handleScript(n, parent);
        return;
      case FUNCTION:
        handleFunction(n, parent);
        return;
      case MODULE_BODY:
        handleModule(n, parent);
        return;
      case CLASS:
        handleClass(n, parent);
        return;
      case CLASS_MEMBERS:
        handleClassMembers(n, parent);
        return;
      case DEFAULT_VALUE:
      case DESTRUCTURING_LHS:
        // TODO(nickreid): Handle ASSIGN with destructuring target.
        if (this.obeyDestructuringAndDefaultValueExecutionOrder) {
          handleDestructuringOrDefaultValue(n, parent);
          return;
        }
        break;
      default:
        break;
    }

    currentNode = n;
    if (!callback.shouldTraverse(this, n, parent)) {
      return;
    }

    if (NodeUtil.createsBlockScope(n)) {
      pushScope(n);
      traverseChildren(n);
      popScope();
    } else {
      traverseChildren(n);
    }

    currentNode = n;
    callback.visit(this, n, parent);
  }

  /** Traverses a function. */
  private void traverseFunction(Node n, Node parent) {
    final Node fnName = n.getFirstChild();
    // NOTE: If a function declaration is the root of a traversal, then we will treat it as a
    // function expression (since 'parent' is null, even though 'n' actually has a parent node) and
    // traverse the function name before entering the scope, rather than afterwards. Removing the
    // null check for 'parent' seems safe, but causes a rare crash when traverseScopeRoots is called
    // by PeepholeOptimizationsPass on a function that somehow doesn't actually have a parent at all
    // (presumably because it's already been removed from the AST?) so that doesn't actually work.
    // This does not actually change anything, though, since rooting a traversal at a function node
    // causes the function scope to be entered twice (unless using #traverseAtScope, which doesn't
    // call this method), so that the name node is just always traversed inside the scope anyway.
    boolean isFunctionDeclaration = parent != null && NodeUtil.isFunctionDeclaration(n);

    if (isFunctionDeclaration) {
      // Function declarations are in the scope containing the declaration.
      traverseBranch(fnName, n);
    }

    currentNode = n;
    pushScope(n);

    if (!isFunctionDeclaration) {
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

  /**
   * Traverses a class. Note that we traverse some of the child nodes slightly out of order to
   * ensure children are visited in the correct scope. The following children are in the outer
   * scope: (1) the 'extends' clause, (2) any computed method keys, (3) the class name for class
   * declarations only (class expression names are traversed in the class scope). This requires that
   * we visit the extends node (second child) and any computed member keys (grandchildren of the
   * last, body, child) before visiting the name (first child) or body (last child).
   */
  private void handleClass(Node n, Node parent) {
    this.currentNode = n;
    if (!callback.shouldTraverse(this, n, parent)) {
      return;
    }

    final Node className = n.getFirstChild();
    final Node extendsClause = className.getNext();
    final Node body = extendsClause.getNext();

    boolean isClassExpression = NodeUtil.isClassExpression(n);

    traverseBranch(extendsClause, n);

    for (Node child = body.getFirstChild(); child != null; ) {
      Node next = child.getNext(); // see traverseChildren
      if (child.isComputedProp()) {
        traverseBranch(child.getFirstChild(), child);
      }
      child = next;
    }

    if (!isClassExpression) {
      // Class declarations are in the scope containing the declaration.
      traverseBranch(className, n);
    }

    currentNode = n;
    pushScope(n);

    if (isClassExpression) {
      // Class expression names are only accessible within the function
      // scope.
      traverseBranch(className, n);
    }

    // Body
    traverseBranch(body, n);

    popScope();

    this.currentNode = n;
    callback.visit(this, n, parent);
  }

  /** Traverse class members, excluding keys of computed props. */
  private void handleClassMembers(Node n, Node parent) {
    this.currentNode = n;
    if (!callback.shouldTraverse(this, n, parent)) {
      return;
    }

    for (Node child = n.getFirstChild(); child != null; ) {
      Node next = child.getNext(); // see traverseChildren
      if (child.isComputedProp()) {
        currentNode = n;
        if (callback.shouldTraverse(this, child, n)) {
          traverseBranch(child.getLastChild(), child);
          currentNode = n;
          callback.visit(this, child, n);
        }
      } else {
        traverseBranch(child, n);
      }
      child = next;
    }

    this.currentNode = n;
    callback.visit(this, n, parent);
  }

  private void traverseChildren(Node n) {
    for (Node child = n.getFirstChild(); child != null; ) {
      // child could be replaced, in which case our child node
      // would no longer point to the true next
      Node next = child.getNext();
      traverseBranch(child, n);
      child = next;
    }
  }

  /**
   * Examines the functions stack for the last instance of a function node. When possible, prefer
   * this method over NodeUtil.getEnclosingFunction() because this in general looks at less nodes.
   */
  public Node getEnclosingFunction() {
    Node root = getCfgRoot();
    return root.isFunction() ? root : null;
  }

  /** Sets the given node as the current scope and pushes the relevant frames on the CFG stacks. */
  private void recordScopeRoot(Node node) {
    if (NodeUtil.isValidCfgRoot(node)) {
      cfgs.push(node);
    }
  }

  /** Creates a new scope (e.g. when entering a function). */
  private void pushScope(Node node) {
    checkNotNull(currentNode);
    checkNotNull(node);
    scopeRoots.add(node);
    recordScopeRoot(node);
    if (scopeCallback != null) {
      scopeCallback.enterScope(this);
    }
  }

  /** Creates a new scope (e.g. when entering a function). */
  private void pushScope(AbstractScope<?, ?> s) {
    pushScope(s, false);
  }

  /**
   * Creates a new scope (e.g. when entering a function).
   *
   * @param quietly Don't fire an enterScope callback.
   */
  private void pushScope(AbstractScope<?, ?> s, boolean quietly) {
    checkNotNull(currentNode);
    scopes.push(s);
    recordScopeRoot(s.getRootNode());
    if (!quietly && scopeCallback != null) {
      scopeCallback.enterScope(this);
    }
  }

  private void popScope() {
    popScope(false);
  }

  /**
   * Pops back to the previous scope (e.g. when leaving a function).
   *
   * @param quietly Don't fire the exitScope callback.
   */
  private void popScope(boolean quietly) {
    if (!quietly && scopeCallback != null) {
      scopeCallback.exitScope(this);
    }
    Node scopeRoot;
    int roots = scopeRoots.size();
    if (roots > 0) {
      scopeRoot = scopeRoots.remove(roots - 1);
    } else {
      scopeRoot = scopes.pop().getRootNode();
    }
    if (NodeUtil.isValidCfgRoot(scopeRoot)) {
      cfgs.pop();
    }
  }

  /** Gets the current scope. */
  public AbstractScope<?, ?> getAbstractScope() {
    AbstractScope<?, ?> scope = scopes.peek();

    // NOTE(dylandavidson): Use for-each loop to avoid slow ArrayList#get performance.
    for (Node scopeRoot : scopeRoots) {
      scope = scopeCreator.createScope(scopeRoot, scope);
      scopes.push(scope);
    }
    scopeRoots.clear();
    return scope;
  }

  /**
   * Instantiate some, but not necessarily all, scopes from stored roots.
   *
   * <p>NodeTraversal instantiates scopes lazily when getScope() or similar is called, by iterating
   * over a stored list of not-yet-instantiated scopeRoots. When a not-yet-instantiated parent scope
   * is requested, it doesn't make sense to instantiate <i>all</i> pending scopes. Instead, we count
   * the number that are needed to ensure the requested parent is instantiated and call this
   * function to instantiate only as many scopes as are needed, shifting their roots off the queue,
   * and returning the deepest scope actually created.
   */
  private AbstractScope<?, ?> instantiateScopes(int count) {
    checkArgument(count <= scopeRoots.size());
    AbstractScope<?, ?> scope = scopes.peek();

    for (int i = 0; i < count; i++) {
      scope = scopeCreator.createScope(scopeRoots.get(i), scope);
      scopes.push(scope);
    }
    scopeRoots.subList(0, count).clear();
    return scope;
  }

  public boolean isHoistScope() {
    return isHoistScopeRootNode(getScopeRoot());
  }

  public Node getClosestHoistScopeRoot() {
    int roots = scopeRoots.size();
    for (int i = roots; i > 0; i--) {
      Node rootNode = scopeRoots.get(i - 1);
      if (isHoistScopeRootNode(rootNode)) {
        return rootNode;
      }
    }

    return scopes.peek().getClosestHoistScope().getRootNode();
  }

  public AbstractScope<?, ?> getClosestContainerScope() {
    for (int i = scopeRoots.size(); i > 0; i--) {
      if (!NodeUtil.createsBlockScope(scopeRoots.get(i - 1))) {
        return instantiateScopes(i);
      }
    }
    return scopes.peek().getClosestContainerScope();
  }

  public AbstractScope<?, ?> getClosestHoistScope() {
    for (int i = scopeRoots.size(); i > 0; i--) {
      if (isHoistScopeRootNode(scopeRoots.get(i - 1))) {
        return instantiateScopes(i);
      }
    }
    return scopes.peek().getClosestHoistScope();
  }

  private static boolean isHoistScopeRootNode(Node n) {
    switch (n.getToken()) {
      case FUNCTION:
      case MODULE_BODY:
      case ROOT:
      case SCRIPT:
        return true;
      default:
        return NodeUtil.isFunctionBlock(n);
    }
  }

  public Scope getScope() {
    return getAbstractScope().untyped();
  }

  public TypedScope getTypedScope() {
    return getAbstractScope().typed();
  }

  /** Gets the control flow graph for the current JS scope. */
  @SuppressWarnings("unchecked") // The type is always ControlFlowGraph<Node>
  public ControlFlowGraph<Node> getControlFlowGraph() {
    ControlFlowGraph<Node> result;
    Object o = cfgs.peek();
    if (o instanceof Node) {
      Node cfgRoot = (Node) o;
      ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, true);
      cfa.process(null, cfgRoot);
      result = cfa.getCfg();
      cfgs.pop();
      cfgs.push(result);
    } else {
      result = (ControlFlowGraph<Node>) o;
    }
    return result;
  }

  /** Returns the current scope's root. */
  public Node getScopeRoot() {
    int roots = scopeRoots.size();
    if (roots > 0) {
      return scopeRoots.get(roots - 1);
    } else {
      AbstractScope<?, ?> s = scopes.peek();
      return s != null ? s.getRootNode() : null;
    }
  }

  @SuppressWarnings("unchecked") // The type is always ControlFlowGraph<Node>
  private Node getCfgRoot() {
    Node result;
    Object o = cfgs.peek();
    if (o instanceof Node) {
      result = (Node) o;
    } else {
      result = ((ControlFlowGraph<Node>) o).getEntry().getValue();
    }
    return result;
  }

  public ScopeCreator getScopeCreator() {
    return scopeCreator;
  }

  /**
   * Determines whether the traversal is currently in the global scope. Note that this returns false
   * in a global block scope.
   */
  public boolean inGlobalScope() {
    return getScopeDepth() == 0;
  }

  /**
   * Determines whether the traversal is currently in the global scope. Note that this returns false
   * in a global block scope.
   */
  public boolean inModuleScope() {
    return NodeUtil.isModuleScopeRoot(getScopeRoot());
  }

  public boolean inGlobalOrModuleScope() {
    return this.inGlobalScope() || inModuleScope();
  }

  /** Determines whether the traversal is currently in the scope of the block of a function. */
  public boolean inFunctionBlockScope() {
    return NodeUtil.isFunctionBlock(getScopeRoot());
  }

  /** Determines whether the hoist scope of the current traversal is global. */
  public boolean inGlobalHoistScope() {
    Node cfgRoot = getCfgRoot();
    checkState(
        cfgRoot.isScript()
            || cfgRoot.isRoot()
            || cfgRoot.isBlock()
            || cfgRoot.isFunction()
            || cfgRoot.isModuleBody(),
        cfgRoot);
    return cfgRoot.isScript() || cfgRoot.isRoot() || cfgRoot.isBlock();
  }

  /** Determines whether the hoist scope of the current traversal is global. */
  public boolean inModuleHoistScope() {
    Node moduleRoot = getCfgRoot();
    if (moduleRoot.isFunction()) {
      // For wrapped modules, the function block is the module scope root.
      moduleRoot = moduleRoot.getLastChild();
    }
    return NodeUtil.isModuleScopeRoot(moduleRoot);
  }

  int getScopeDepth() {
    int sum = scopes.size() + scopeRoots.size();
    checkState(sum > 0);
    return sum - 1; // Use 0-based scope depth to be consistent within the compiler
  }

  /** Reports a diagnostic (error or warning) */
  public void report(Node n, DiagnosticType diagnosticType, String... arguments) {
    JSError error = JSError.make(n, diagnosticType, arguments);
    compiler.report(error);
  }

  public void reportCodeChange() {
    Node changeScope = this.currentChangeScope;
    checkNotNull(changeScope);
    checkState(NodeUtil.isChangeScopeRoot(changeScope), changeScope);
    compiler.reportChangeToChangeScope(changeScope);
  }

  public void reportCodeChange(Node n) {
    compiler.reportChangeToEnclosingScope(n);
  }

  /**
   * Returns the SCRIPT node enclosing the current scope, or `null` if unknown
   *
   * <p>e.g. returns null if {@link #traverseInnerNode(Node, Node, AbstractScope)} was used
   */
  @Nullable
  Node getCurrentScript() {
    return currentScript;
  }

  /** @param n The current change scope, should be null when the traversal is complete. */
  private void setChangeScope(Node n) {
    this.currentChangeScope = n;
  }

  private Node getEnclosingScript(Node n) {
    while (n != null && !n.isScript()) {
      n = n.getParent();
    }
    return n;
  }

  private void initTraversal(Node traversalRoot) {
    if (Platform.isThreadInterrupted()) {
      throw new RuntimeException(new InterruptedException());
    }
    Node changeScope = NodeUtil.getEnclosingChangeScopeRoot(traversalRoot);
    setChangeScope(changeScope);
    Node script = getEnclosingScript(changeScope);
    currentScript = script;
    clearScriptState();
  }

  /**
   * Prefills the scopeRoots stack up to a given spot in the AST. Allows for starting traversal at
   * any spot while still having correct scope state.
   */
  private void initScopeRoots(Node n) {
    Deque<Node> queuedScopeRoots = new ArrayDeque<>();
    while (n != null) {
      if (isScopeRoot(n)) {
        queuedScopeRoots.addFirst(n);
      }
      n = n.getParent();
    }
    for (Node queuedScopeRoot : queuedScopeRoots) {
      pushScope(queuedScopeRoot);
    }
  }

  private boolean isScopeRoot(Node n) {
    if (n.isRoot() && n.getParent() == null) {
      return true;
    } else if (n.isFunction()) {
      return true;
    } else if (NodeUtil.createsBlockScope(n)) {
      return true;
    }
    return false;
  }

  /**
   * This is used to clear any cached state with regard to the current script and should be called
   *
   * <p>before traversing a SCRIPT rootedß subtree.
   */
  private void clearScriptState() {
    inputId = null;
    sourceName = null;
    compilerInput = null;
  }

  InputId getInputId() {
    if (currentScript != null && inputId == null) {
      inputId = currentScript.getInputId();
    }
    return inputId;
  }

  private String getBestSourceFileName(Node n) {
    return n == null ? getSourceName() : n.getSourceFileName();
  }
}
