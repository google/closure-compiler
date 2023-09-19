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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jspecify.nullness.Nullable;

/**
 * NodeTraversal allows an iteration through the nodes in the parse tree, and facilitates the
 * optimizations on the parse tree.
 */
public class NodeTraversal {
  private final AbstractCompiler compiler;
  private final Callback callback;
  private final @Nullable ScopedCallback scopeCallback;
  private final ScopeCreator scopeCreator;
  private final boolean obeyDestructuringAndDefaultValueExecutionOrder;

  /** Contains the current node */
  private Node currentNode;

  private @Nullable Node currentHoistScopeRoot;

  /** Contains the current FUNCTION node if there is one, otherwise null. */
  private Node currentFunction;

  /** Contains the enclosing SCRIPT node if there is one, otherwise null. */
  private Node currentScript;

  /** The change scope for the current node being visiteds */
  private Node currentChangeScope;

  /**
   * The chain scope for the currentNode being visited. Scopes are relatively expensive to build so
   * they are built lazily. The list contains instanciated `AbstractScope` or the `Node`
   * representing the root of the scope.
   *
   * <p>If `AbstractScope` is seen all previous entries will also have been instanciated due to the
   * heirarchical nature of scopes.
   */
  private final ArrayList<Object> scopes = new ArrayList<>();

  /** The current source file name */
  private @Nullable String sourceName;

  /** The current input */
  private @Nullable InputId inputId;

  private @Nullable CompilerInput compilerInput;

  /** Callback for tree-based traversals */
  public interface Callback {
    /**
     * Visits a node in preorder (before its children) and decides whether the node and its children
     * should be traversed.
     *
     * <p>If this method returns true, the node will be visited by {@link #visit(NodeTraversal,
     * Node, Node)} in postorder and its children will be visited by both {@link
     * #shouldTraverse(NodeTraversal, Node, Node)} in preorder and by {@link #visit(NodeTraversal,
     * Node, Node)} in postorder.
     *
     * <p>If this method returns false, the node will not be visited by {@link #visit(NodeTraversal,
     * Node, Node)} and its children will neither be visited by {@link
     * #shouldTraverse(NodeTraversal, Node, Node)} nor {@link #visit(NodeTraversal, Node, Node)}.
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
    boolean shouldTraverse(NodeTraversal t, Node n, @Nullable Node parent);

    /**
     * Visits a node in postorder (after its children). A node is visited in postorder iff {@link
     * #shouldTraverse(NodeTraversal, Node, Node)} returned true for its parent and itself. In
     * particular, the root node is never visited in postorder.
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
    void visit(NodeTraversal t, Node n, @Nullable Node parent);
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

  /**
   * An traversal base class that tracks and caches the ControlFlowGraph (CFG) during the traversal.
   * The CFGs are constructed lazily.
   */
  public abstract static class AbstractCfgCallback implements ScopedCallback {

    private final ArrayDeque<Object> cfgs = new ArrayDeque<>();

    /** Gets the control flow graph for the current JS scope. */
    @SuppressWarnings("unchecked") // The type is always ControlFlowGraph<Node>
    public ControlFlowGraph<Node> getControlFlowGraph(AbstractCompiler compiler) {
      ControlFlowGraph<Node> result;
      Object o = cfgs.peek();
      checkState(o != null);
      if (o instanceof Node) {
        Node cfgRoot = (Node) o;
        result =
            ControlFlowAnalysis.builder()
                .setCompiler(compiler)
                .setCfgRoot(cfgRoot)
                .setIncludeEdgeAnnotations(true)
                .computeCfg();
        cfgs.pop();
        cfgs.push(result);
      } else {
        result = (ControlFlowGraph<Node>) o;
      }
      return result;
    }

    @Override
    public final void enterScope(NodeTraversal t) {
      Node currentScopeRoot = t.getScopeRoot();
      if (NodeUtil.isValidCfgRoot(currentScopeRoot)) {
        cfgs.push(currentScopeRoot);
      }
      enterScopeWithCfg(t);
    }

    @Override
    public final void exitScope(NodeTraversal t) {
      exitScopeWithCfg(t);
      Node currentScopeRoot = t.getScopeRoot();
      if (NodeUtil.isValidCfgRoot(currentScopeRoot)) {
        cfgs.pop();
      }
    }

    public void enterScopeWithCfg(NodeTraversal t) {}

    public void exitScopeWithCfg(NodeTraversal t) {}

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {}
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

    private @Nullable ModuleMetadata currentModule;
    private @Nullable Node scopeRoot;
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

    private static final QualifiedName GOOG_MODULE = QualifiedName.of("goog.module");

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
          if (inLoadModule && GOOG_MODULE.matches(n.getFirstChild())) {
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

    @CanIgnoreReturnValue
    public Builder setCallback(Callback x) {
      this.callback = x;
      return this;
    }

    @CanIgnoreReturnValue
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

    @CanIgnoreReturnValue
    public Builder setCompiler(AbstractCompiler x) {
      this.compiler = x;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setScopeCreator(ScopeCreator x) {
      this.scopeCreator = x;
      return this;
    }

    @CanIgnoreReturnValue
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

    switch (n.getToken()) {
      case FUNCTION:
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
        break;
      case CLASS:
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
        break;
      case BLOCK:
      case SWITCH:
        if (callback.shouldTraverse(this, n, null)) {
          pushScope(s);

          // traverseBranch is not called here to avoid re-creating the block scope.
          traverseChildren(n);

          popScope();
          callback.visit(this, n, null);
        }
        break;
      case MEMBER_FIELD_DEF:
        pushScope(s);
        if (callback.shouldTraverse(this, n, null)) {

          // traverseBranch is not called here to avoid re-creating the MemberFieldDef scope.
          traverseChildren(n);

          callback.visit(this, n, null);
        }
        popScope();
        break;
      case COMPUTED_FIELD_DEF:
        pushScope(s);
        if (callback.shouldTraverse(this, n, null)) {

          traverseBranch(n.getLastChild(), n);

          callback.visit(this, n, null);
        }
        popScope();
        break;
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
      case FOR:
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
        break;
      default:
        checkState(
            s.isGlobal() || s.isModuleScope(), "Expected global or module scope. Got: (%s)", s);
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
      @Nullable Node scopeNode = null;

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

  /** Gets the current input chunk. */
  public @Nullable JSChunk getChunk() {
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
    currentHoistScopeRoot = n;
    pushScope(n);
    currentNode = n;
    if (callback.shouldTraverse(this, n, parent)) {
      currentNode = n;
      traverseChildren(n);
      callback.visit(this, n, parent);
    }
    popScope();
    // Module bodies don't nest
    currentHoistScopeRoot = null;
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
  private void traverseBranch(Node n, @Nullable Node parent) {
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

    boolean createsBlockScope = NodeUtil.createsBlockScope(n);
    Node previousHoistScopeRoot = currentHoistScopeRoot;
    if (createsBlockScope) {
      pushScope(n);
      if (NodeUtil.isClassStaticBlock(n)) {
        currentHoistScopeRoot = n;
      }
    }

    /*
     * Intentionally inlined call to traverseChildren.
     *
     * <p>Calling traverseChildren here would double the maximum stack depth seen during
     * compilation. That can cause stack overflows on some very deep ASTs (e.g. b/188616350).
     * Inlining side-steps the issue.
     */
    for (Node child = n.getFirstChild(); child != null; ) {
      // child could be replaced, in which case our child node
      // would no longer point to the true next
      Node next = child.getNext();
      traverseBranch(child, n);
      child = next;
    }

    if (createsBlockScope) {
      popScope();
      currentHoistScopeRoot = previousHoistScopeRoot;
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

    // may nest within class static blocks
    Node previousHoistScopeRoot = currentHoistScopeRoot;
    currentHoistScopeRoot = n;
    Node previousFunction = currentFunction;
    currentFunction = n;
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
    currentFunction = previousFunction;
    currentHoistScopeRoot = previousHoistScopeRoot;
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
      if (child.isComputedProp() || child.isComputedFieldDef()) {
        traverseBranch(child.getFirstChild(), child);
      }
      child = next;
    }

    for (Node child = n.getFirstChild(); child != null; ) {
      Node next = child.getNext(); // see traverseChildren

      switch (child.getToken()) {
        case COMPUTED_PROP:
          currentNode = n;

          if (callback.shouldTraverse(this, child, n)) {
            traverseBranch(child.getLastChild(), child);
            currentNode = n;
            callback.visit(this, child, n);
          }
          break;
        case COMPUTED_FIELD_DEF:
          currentNode = n;
          pushScope(child);

          if (callback.shouldTraverse(this, child, n)) {
            if (child.hasTwoChildren()) { // No RHS to traverse in `[x];` computed field case
              traverseBranch(child.getLastChild(), child);
            }
            currentNode = n;
            callback.visit(this, child, n);
          }

          popScope();
          break;
        case MEMBER_FIELD_DEF:
          handleMemberFieldDef(n, child);
          break;
        case BLOCK:
        case MEMBER_FUNCTION_DEF:
        case MEMBER_VARIABLE_DEF:
        case GETTER_DEF:
        case SETTER_DEF:
          traverseBranch(child, n);
          break;
        default:
          throw new IllegalStateException("Invalid class member: " + child.getToken());
      }
      child = next;
    }

    this.currentNode = n;
    callback.visit(this, n, parent);
  }

  private void handleMemberFieldDef(Node n, Node child) {
    Node previousHoistScopeRoot = currentHoistScopeRoot;
    currentHoistScopeRoot = n;
    pushScope(child);
    traverseBranch(child, n);
    popScope();
    currentHoistScopeRoot = previousHoistScopeRoot;
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
  public @Nullable Node getEnclosingFunction() {
    return currentFunction;
  }

  /** Creates a new scope (e.g. when entering a function). */
  private void pushScope(Node node) {
    checkNotNull(currentNode);
    checkNotNull(node);
    scopes.add(node);
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
    scopes.add(s);
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
    scopes.remove(scopes.size() - 1);
  }

  /**
   * Scopes are stored in a list of objects. These can either be instanciated `AbstractScope` or the
   * `Node` representing the root of the scope.
   */
  private Node getNodeRootFromScopeObj(Object root) {
    return root instanceof Node ? (Node) root : ((AbstractScope) root).getRootNode();
  }

  /** Returns the current scope's root. */
  public @Nullable Node getScopeRoot() {
    int roots = scopes.size();
    if (roots > 0) {
      return getNodeRootFromScopeObj(scopes.get(roots - 1));
    } else {
      return null;
    }
  }

  int getScopeDepth() {
    int depth = scopes.size();
    checkState(depth > 0);
    return depth - 1; // Use 0-based scope depth to be consistent within the compiler
  }

  public Scope getScope() {
    return getAbstractScope().untyped();
  }

  public TypedScope getTypedScope() {
    return getAbstractScope().typed();
  }

  /** Gets the current scope. */
  public AbstractScope<?, ?> getAbstractScope() {
    return getAbstractScope(scopes.size() - 1);
  }

  /**
   * Recusively visit uninstanciated scopes and instanciate and store any necessary scopes to return
   * the requested scope.
   */
  private AbstractScope<?, ?> getAbstractScope(int rootDepth) {

    Object o = scopes.get(rootDepth);
    if (o instanceof Node) {
      // The root scope has a null parent.
      AbstractScope<?, ?> parentScope = (rootDepth > 0) ? getAbstractScope(rootDepth - 1) : null;
      AbstractScope<?, ?> scope = scopeCreator.createScope((Node) o, parentScope);
      scopes.set(rootDepth, scope);
      return scope;
    } else {
      return (AbstractScope<?, ?>) o;
    }
  }

  public boolean isHoistScope() {
    return isHoistScopeRootNode(getScopeRoot());
  }

  public @Nullable Node getClosestHoistScopeRoot() {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      Node rootNode = getNodeRootFromScopeObj(scopes.get(i));
      if (isHoistScopeRootNode(rootNode)) {
        return rootNode;
      }
    }

    return null;
  }

  public @Nullable AbstractScope<?, ?> getClosestContainerScope() {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      Node rootNode = getNodeRootFromScopeObj(scopes.get(i));
      if (!NodeUtil.createsBlockScope(rootNode)) {
        return getAbstractScope(i);
      }
    }
    return null;
  }

  public @Nullable AbstractScope<?, ?> getClosestHoistScope() {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      Node rootNode = getNodeRootFromScopeObj(scopes.get(i));
      if (isHoistScopeRootNode(rootNode)) {
        return getAbstractScope(i);
      }
    }
    return null;
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

  /** Returns the closest scope binding the `this` or `super` keyword */
  public @Nullable Node getClosestScopeRootNodeBindingThisOrSuper() {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      Node rootNode = getNodeRootFromScopeObj(scopes.get(i));
      switch (rootNode.getToken()) {
        case FUNCTION:
          if (rootNode.isArrowFunction()) {
            continue;
          }
          return rootNode;
        case MEMBER_FIELD_DEF:
        case COMPUTED_FIELD_DEF:
        case CLASS:
        case MODULE_BODY:
        case ROOT:
          return rootNode;
        case BLOCK:
          if (NodeUtil.isClassStaticBlock(rootNode)) {
            return rootNode;
          }
          continue;
        default:
          continue;
      }
    }
    return null;
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

  /** Determines whether the traversal is currently in a module scope. */
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
    return currentHoistScopeRoot == null;
  }

  /** Determines whether the hoist scope of the current traversal is global. */
  public boolean inModuleHoistScope() {
    Node moduleRoot = currentHoistScopeRoot;
    if (moduleRoot == null) {
      // in global hoist scope
      return false;
    }
    if (moduleRoot.isFunction()) {
      // For wrapped modules, the function block is the module scope root.
      moduleRoot = moduleRoot.getLastChild();
    }
    return NodeUtil.isModuleScopeRoot(moduleRoot);
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
  @Nullable Node getCurrentScript() {
    return currentScript;
  }

  /**
   * @param n The current change scope, should be null when the traversal is complete.
   */
  private void setChangeScope(@Nullable Node n) {
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
    Node hoistScopeRoot = NodeUtil.getEnclosingHoistScopeRoot(traversalRoot);
    this.currentHoistScopeRoot = hoistScopeRoot;
    // "null" is the global hoist scope root, but we want the current script if any
    // for the "change scope"
    Node changeScope =
        NodeUtil.getEnclosingChangeScopeRoot(
            hoistScopeRoot != null ? hoistScopeRoot : traversalRoot);
    setChangeScope(changeScope);

    Node enclosingFunction =
        hoistScopeRoot != null ? NodeUtil.getEnclosingFunction(hoistScopeRoot) : null;
    this.currentFunction = enclosingFunction;

    Node script = getEnclosingScript(changeScope);
    currentScript = script;
    clearScriptState();
  }

  /**
   * Prefills the scopes stack up to a given spot in the AST. Allows for starting traversal at any
   * spot while still having correct scope state.
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
