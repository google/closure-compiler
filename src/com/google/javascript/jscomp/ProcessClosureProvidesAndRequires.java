/*
 * Copyright 2019 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Replaces `goog.provide` calls and removes goog.{require,requireType,forwardDeclare} calls.
 *
 * <p>We expect all `goog.modules` and `goog.require`s in modules to have been rewritten. This is
 * why all remaining require/requireType calls must refer to a `goog.provide`, although the original
 * JS code may contain `goog.require`s of a `goog.module`.
 *
 * <p>This also annotates all provided namespace definitions `a.b = {};` with {@link
 * Node#IS_NAMESPACE} so that later passes know they refer to a `goog.provide`d namespace.
 *
 * <p>If modules have not been rewritten, this pass also includes legacy Closure module namespaces
 * in the list of {@link ProvidedName}s.
 */
class ProcessClosureProvidesAndRequires implements CompilerPass {

  static final DiagnosticType TYPEDEF_CHILD_OF_PROVIDE =
      DiagnosticType.error(
          "JSC_TYPEDEF_CHILD_OF_PROVIDE",
          "invalid @typedef goog.provide {0}\n"
              + "Parent namespace {1} is goog.provided and initialized in the same file");

  // The root Closure namespace
  private static final String GOOG = "goog";

  private final AbstractCompiler compiler;
  private final JSChunkGraph chunkGraph;

  // Use a LinkedHashMap because the goog.provides must be processed in a deterministic order.
  private final Map<String, ProvidedName> providedNames = new LinkedHashMap<>();

  private final Set<String> exportedVariables = new LinkedHashSet<>();

  // If this is true, rewriting will not remove any goog.provide or goog.require calls
  private final boolean preserveGoogProvidesAndRequires;
  private final List<Node> requiresToBeRemoved = new ArrayList<>();
  // Whether this instance has already rewritten goog.provides, which can only happen once
  private boolean hasRewritingOccurred = false;
  private final Set<Node> forwardDeclaresToRemove = new LinkedHashSet<>();
  private final AstFactory astFactory;

  ProcessClosureProvidesAndRequires(
      AbstractCompiler compiler,
      boolean preserveGoogProvidesAndRequires) {
    this.compiler = compiler;
    this.chunkGraph = compiler.getChunkGraph();
    this.preserveGoogProvidesAndRequires = preserveGoogProvidesAndRequires;
    this.astFactory = compiler.createAstFactory();
  }

  /** When invoked as compiler pass, we rewrite all provides and requires. */
  @Override
  public void process(Node externs, Node root) {
    rewriteProvidesAndRequires(externs, root);
  }

  Set<String> getExportedVariableNames() {
    return exportedVariables;
  }

  /** Collects all `goog.provide`s in the given namespace and warns on invalid code */
  Map<String, ProvidedName> collectProvidedNames(Node externs, Node root) {
    if (this.providedNames.isEmpty()) {
      // goog is special-cased because it is provided in Closure's base library.
      providedNames.put(
          GOOG, new ProvidedNameBuilder().setNamespace(GOOG).setNode(null).setChunk(null).build());
      NodeTraversal.traverseRoots(compiler, new CollectDefinitions(), externs, root);
    }
    return this.providedNames;
  }

  /**
   * Rewrites all provides and requires in the given namespace.
   *
   * <p>Call this instead of {@link #collectProvidedNames(Node, Node)} if you want rewriting.
   */
  void rewriteProvidesAndRequires(Node externs, Node root) {
    checkState(!hasRewritingOccurred, "Cannot call rewriteProvidesAndRequires twice per instance");
    hasRewritingOccurred = true;

    collectProvidedNames(externs, root);

    for (ProvidedName pn : providedNames.values()) {
      pn.replace(preserveGoogProvidesAndRequires, providedNames);
    }

    for (Node closureRequire : requiresToBeRemoved) {
      compiler.reportChangeToEnclosingScope(closureRequire);
      closureRequire.detach();
    }
    for (Node forwardDeclare : forwardDeclaresToRemove) {
      NodeUtil.deleteNode(forwardDeclare, compiler);
    }
  }

  private class CollectDefinitions implements NodeTraversal.Callback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      // Don't recurse into modules, which cannot have goog.provides.  We do need to handle legacy
      // goog.modules, but we do that quickly here rather than descending all the way into them.
      // We completely ignore ES modules and CommonJS modules.
      if ((n.isModuleBody() && n.getParent().getBooleanProp(Node.GOOG_MODULE))
          || NodeUtil.isBundledGoogModuleScopeRoot(n)) {
        Node googModuleCall = n.getFirstChild();
        String closureNamespace = googModuleCall.getFirstChild().getSecondChild().getString();
        Node maybeLegacyNamespaceCall = googModuleCall.getNext();
        if (maybeLegacyNamespaceCall != null
            && NodeUtil.isGoogModuleDeclareLegacyNamespaceCall(maybeLegacyNamespaceCall)) {
          processLegacyModuleCall(closureNamespace, googModuleCall, t.getChunk());
        }
        return false;
      }
      return !n.isModuleBody();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case CALL -> {
          Node left = n.getFirstChild();
          if (left.isGetProp()) {
            Node name = left.getFirstChild();
            if (name.matchesName(GOOG)) {
              visitGoogMethodCall(t, parent, n, left.getString());
            }
          }
        }
        case ASSIGN, NAME ->
            // If this is an assignment to a provided name, remove the provided object.
            handleCandidateProvideDefinition(t, n, parent);
        case EXPR_RESULT -> handleStubDefinition(t, n);
        default -> {}
      }
    }
  }

  private void visitGoogMethodCall(NodeTraversal t, Node parent, Node n, String methodName) {
    // For the sake of simplicity, we report code changes
    // when we see a provides/requires, and don't worry about
    // reporting the change when we actually do the replacement.
    switch (methodName) {
      case "exportSymbol" -> {
        // Note: exportSymbol is allowed in local scope
        Node arg = n.getSecondChild();
        if (arg.isStringLit()) {
          String argString = arg.getString();
          int dot = argString.indexOf('.');
          if (dot == -1) {
            exportedVariables.add(argString);
          } else {
            exportedVariables.add(argString.substring(0, dot));
          }
        }
      }
      case "require", "requireType" -> {
        if (isValidPrimitiveCall(t, n)) {
          processRequireCall(n, parent);
        }
      }
      case "provide" -> {
        if (isValidPrimitiveCall(t, n)) {
          processProvideCall(t, n, parent);
        }
      }
      case "forwardDeclare" -> {
        if (isValidPrimitiveCall(t, n)) {
          processForwardDeclare(n, parent);
        }
      }
      default -> {}
    }
  }

  private boolean isValidPrimitiveCall(NodeTraversal t, Node n) {
    // Ignore invalid primitives if we didn't strip module sugar.
    if (compiler.getOptions().shouldPreserveGoogModule()) {
      return true;
    }

    return t.inGlobalHoistScope() && n.getParent().isExprResult();
  }

  /** Handles a goog.require or goog.requireType call. */
  private void processRequireCall(Node call, Node parent) {
    if (!verifyOnlyArgumentIsString(call)) {
      return;
    }

    if (!preserveGoogProvidesAndRequires) {
      requiresToBeRemoved.add(parent);
    }
  }

  /** Handles a goog.module that is a legacy namespace. */
  private void processLegacyModuleCall(String namespace, Node googModuleCall, JSChunk chunk) {
    registerAnyProvidedPrefixes(namespace, googModuleCall, chunk);
    providedNames.put(
        namespace,
        new ProvidedNameBuilder()
            .setNamespace(namespace)
            .setNode(googModuleCall)
            .setChunk(chunk)
            .setExplicit(true)
            .setFromLegacyModule(true)
            .build());
  }

  /** Handles a goog.provide call. */
  private void processProvideCall(NodeTraversal t, Node call, Node parent) {
    checkState(call.isCall());
    if (!verifyOnlyArgumentIsString(call)) {
      return;
    }
    Node left = call.getFirstChild();
    Node arg = left.getNext();
    String ns = arg.getString();

    JSDocInfo info = NodeUtil.getBestJSDocInfo(call);
    boolean isImplicitlyInitialized = info != null && info.isProvideAlreadyProvided();

    if (providedNames.containsKey(ns)) {
      ProvidedName previouslyProvided = providedNames.get(ns);
      if (!previouslyProvided.isExplicitlyProvided()) {
        previouslyProvided.addProvide(parent, t.getChunk(), /* explicit= */ true, chunkGraph);
      }
    } else {
      registerAnyProvidedPrefixes(ns, parent, t.getChunk());
      providedNames.put(
          ns,
          new ProvidedNameBuilder()
              .setNamespace(ns)
              .setNode(parent)
              .setChunk(t.getChunk())
              .setExplicit(true)
              .setHasImplicitInitialization(isImplicitlyInitialized)
              .build());
    }
  }

  /**
   * Handles a stub definition for a goog.provided name (e.g. a @typedef or a definition from
   * externs)
   *
   * @param exprResult EXPR_RESULT node.
   */
  private void handleStubDefinition(NodeTraversal t, Node exprResult) {
    if (!t.inGlobalHoistScope()) {
      return;
    }
    boolean isExternStub = exprResult.isFromExterns();
    boolean isTypedefStub = isTypedefStubDeclaration(exprResult);
    // recognize @typedefs only if using this pass for typechecking via
    // collectProvidedNames. We don't want rewriting to depend on @typedef annotations.
    boolean isValidTypedefStubDefinition = isTypedefStub && !this.hasRewritingOccurred;

    if (isValidTypedefStubDefinition || isExternStub) {
      if (exprResult.getFirstChild().isQualifiedName()) {
        String name = exprResult.getFirstChild().getQualifiedName();
        ProvidedName pn = providedNames.get(name);
        if (pn != null) {
          pn.addDefinition(exprResult, t.getChunk(), chunkGraph);
        }
      }
    }
    if (isTypedefStub) {
      checkNestedTypedefProvide(exprResult);
    }
  }

  /**
   * Checks that code doesn't use goog.provides in a way that will cause broken output code.
   *
   * @param exprResult an EXPR_RESULT node with an @typedef type
   */
  private void checkNestedTypedefProvide(Node exprResult) {
    // forbid this pattern:
    //   goog.provide('my.parent');
    //   goog.provide('my.parent.ChildTypedef');
    //   my.parent = [...]; // some initialization, doesn't matter what
    //   /** @typedef {...} */
    //   my.parent.ChildType;
    // at one point, this pattern was supported. now it would produce code that crashes at
    // runtime because the compiler would initializer `my.parent.ChildType = {}` before
    // `my.parent = [...]`, so report an error.

    String name = exprResult.getFirstChild().getQualifiedName();
    if (name == null || !name.contains(".")) {
      // @typedefs on simple names are okay.
      return;
    }
    if (!providedNames.containsKey(name)) {
      // non-provided names don't matter.
      return;
    }
    String parentName = name.substring(0, name.lastIndexOf("."));
    ProvidedName parent = providedNames.get(parentName);
    Node parentDefinition = parent.getCandidateDefinition();
    if (parentDefinition == null
        || !parentDefinition.getStaticSourceFile().equals(exprResult.getStaticSourceFile())
        || isTypedefStubDeclaration(parentDefinition)) {
      return;
    }
    compiler.report(JSError.make(exprResult, TYPEDEF_CHILD_OF_PROVIDE, name, parentName));
  }

  private boolean isTypedefStubDeclaration(Node statement) {
    if (!statement.isExprResult()) {
      return false;
    }
    JSDocInfo info = NodeUtil.getBestJSDocInfo(statement);
    return info != null && info.hasTypedefType();
  }

  /** Handles a candidate definition for a goog.provided name. */
  private void handleCandidateProvideDefinition(NodeTraversal t, Node n, Node parent) {
    if (!t.inGlobalHoistScope()) {
      return;
    }
    String name =
        switch (n.getParent().getToken()) {
          case LET, CONST -> t.inGlobalScope() ? n.getString() : null;
          case VAR -> n.getString();
          case EXPR_RESULT -> n.isAssign() ? n.getFirstChild().getQualifiedName() : null;
          case CLASS, FUNCTION ->
              // Class and function provides are forbidden; see ProcessClosurePrimitives's
              // CLASS_NAMESPACE_ERROR and FUNCTION_NAMESPACE_ERROR.
              null;
          default -> null;
        };

    if (name == null) {
      return;
    }

    ProvidedName pn = providedNames.get(name);
    if (pn != null) {
      pn.addDefinition(parent, t.getChunk(), chunkGraph);
    }
  }

  /** Marks a goog.forwardDeclare call for removal. */
  private void processForwardDeclare(Node n, Node parent) {
    CodingConvention convention = compiler.getCodingConvention();

    List<String> typeDeclarations = convention.identifyTypeDeclarationCall(n);

    if (!preserveGoogProvidesAndRequires
        && typeDeclarations != null
        && typeDeclarations.size() == 1) {
      // Forward declaration was recorded and we can remove the call.
      Node toRemove = parent.isExprResult() ? parent : parent.getParent();
      forwardDeclaresToRemove.add(toRemove);
    }
  }

  /**
   * Verifies that a method call has exactly one argument, and that it's a string literal.
   *
   * @return Whether the argument checked out okay
   */
  private boolean verifyOnlyArgumentIsString(Node call) {
    Node arg = call.getSecondChild();
    return arg != null && arg.isStringLit() && arg.getNext() == null;
  }

  /**
   * Registers ProvidedNames for prefix namespaces if they haven't already been defined. The prefix
   * namespaces must be registered in order from shortest to longest.
   *
   * @param ns The namespace whose prefixes may need to be provided.
   * @param node The EXPR of the provide call.
   * @param module The current module.
   */
  private void registerAnyProvidedPrefixes(String ns, Node node, JSChunk chunk) {
    int pos = ns.indexOf('.');
    while (pos != -1) {
      String prefixNs = ns.substring(0, pos);
      pos = ns.indexOf('.', pos + 1);
      if (providedNames.containsKey(prefixNs)) {
        providedNames.get(prefixNs).addProvide(node, chunk, /* explicit= */ false, chunkGraph);
      } else {
        providedNames.put(
            prefixNs,
            new ProvidedNameBuilder()
                .setNamespace(prefixNs)
                .setNode(node)
                .setChunk(chunk)
                .setExplicit(false)
                .build());
      }
    }
  }

  private class ProvidedNameBuilder {
    private String namespace;
    private Node node;
    private JSChunk chunk;
    private boolean explicit;
    private boolean fromLegacyModule;
    private boolean hasImplicitInitialization;

    @CanIgnoreReturnValue
    ProvidedNameBuilder setNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    /**
     * @param node Can be null (for GOOG or an implicit name), an EXPR_RESULT for a goog.provide, or
     *     an EXPR_RESULT or name declaration for a previously provided name.
     */
    @CanIgnoreReturnValue
    ProvidedNameBuilder setNode(@Nullable Node node) {
      this.node = node;
      return this;
    }

    @CanIgnoreReturnValue
    ProvidedNameBuilder setChunk(@Nullable JSChunk chunk) {
      this.chunk = chunk;
      return this;
    }

    /**
     * @param explicit Whether this came from an actual goog.provide('a.b.c'); call
     */
    @CanIgnoreReturnValue
    ProvidedNameBuilder setExplicit(boolean explicit) {
      this.explicit = explicit;
      return this;
    }

    /**
     * @param alreadyInitialized Whether this came from an actual goog.provide('a.b.c'); call
     */
    @CanIgnoreReturnValue
    ProvidedNameBuilder setHasImplicitInitialization(boolean alreadyInitialized) {
      this.hasImplicitInitialization = alreadyInitialized;
      return this;
    }

    /** Whether this comes from a legacy goog.module */
    @CanIgnoreReturnValue
    ProvidedNameBuilder setFromLegacyModule(boolean fromLegacyModule) {
      this.fromLegacyModule = fromLegacyModule;
      return this;
    }

    ProvidedName build() {
      return new ProvidedName(this, chunkGraph, compiler, astFactory);
    }
  }

  // -------------------------------------------------------------------------

  /**
   * Stores information about a Closure namespace created by a goog.provide
   *
   * <p>There are three ways that we find these namespaces in the AST:
   *
   * <ul>
   *   <li>An explicit goog.provide. `goog.provide('a.b.c');` creates a ProvidedName for 'a.b.c'.
   *   <li>An implicit parent namespace. `goog.provide('a.b.c');` creates a ProvidedName for 'a.b'.
   *   <li>A provide definition processed by an earlier run. `a.b.c = {};` when annotated
   *       IS_NAMESPACE
   * </ul>
   */
  static class ProvidedName {
    // The Closure namespace this name represents, e.g. `a.b` for `goog.provide('a.b');`
    private final String namespace;

    // The first node in the AST that creates this ProvidedName.
    // This is always a goog.provide('a.b'), null (for implicit namespaces and 'goog'), or an
    // assignment or declaration for a 'previously provided' name or parent namespace of such.
    // This should only be used for source info and a place to hang namespace definitions.
    private final Node firstNode;
    // The chunk where this namespace was first goog.provided, if chunks exist. */
    private final JSChunk firstChunk;

    // When set the namespace will not have an initialization added, even if the namespace
    // initialization is not otherwise visibile to this pass.  This allows for trivial aliasing of
    // a tree of namespaces.
    private final boolean hasImplicitInitialization;

    // The node where the call was explicitly goog.provided. Null if the namespace is implicit.
    // If this is previously provided, this will instead be the expression or declaration marked
    // as IS_NAMESPACE.
    private @Nullable Node explicitNode = null;

    // The candidate definition for this namespace. For example, given
    //      goog.provide('a.b');
    //      /** @constructor * /
    //      a.b = function() {};
    // the 'candidate definition' of 'a.b' is the GETPROP 'a.b' from the constructor declaration.
    private @Nullable Node candidateDefinition = null;

    // The minimum chunk where the provide namespace definition must appear. If child namespaces of
    // this provide appear in multiple chunks, this chunk must be earlier than all child
    // namespace's chunks.
    private @Nullable JSChunk minimumChunk = null;

    // The replacement declaration. Null until replace() has been called.
    private @Nullable Node replacementNode = null;

    // Whether this comes from a goog.module with declareLegacyNamespace.
    private final boolean fromLegacyModule;

    private final AbstractCompiler compiler;
    private final AstFactory astFactory;

    private ProvidedName(
        ProvidedNameBuilder builder,
        JSChunkGraph chunkGraph,
        AbstractCompiler compiler,
        AstFactory astFactory) {
      this.compiler = compiler;
      this.astFactory = astFactory;
      Node node = builder.node;
      Preconditions.checkArgument(
          node == null
              || NodeUtil.isExprCall(node)
              || (!builder.explicit
                  && (NodeUtil.isExprAssign(node)
                      || NodeUtil.isNameDeclaration(node)
                      || (node.isExprResult() && node.getFirstChild().isQualifiedName()))),
          node);
      this.namespace = builder.namespace;
      this.firstNode = builder.node;
      this.firstChunk = builder.chunk;
      this.fromLegacyModule = builder.fromLegacyModule;
      this.hasImplicitInitialization = builder.hasImplicitInitialization;

      addProvide(node, builder.chunk, builder.explicit, chunkGraph);
    }

    /**
     * Adds an implicit or explicit provide.
     *
     * <p>Every provided name can have multiple implicit provides but a maximum of one explicit
     * provide.
     *
     * @param node the EXPR_RESULT representing this provide or possible a VAR for a previously
     *     provided name. null if implicit.
     */
    void addProvide(Node node, JSChunk chunk, boolean explicit, JSChunkGraph chunkGraph) {
      if (explicit) {
        // goog.provide('name.space');
        checkState(explicitNode == null);
        checkArgument(node.isExprResult(), node);
        explicitNode = node;
      }
      updateMinimumChunk(chunk, chunkGraph);
    }

    /** Whether there existed a `goog.provide('a.b');` for this name 'a.b' */
    boolean isExplicitlyProvided() {
      return explicitNode != null;
    }

    boolean hasImplicitInitialization() {
      return hasImplicitInitialization;
    }

    boolean isFromLegacyModule() {
      return this.fromLegacyModule;
    }

    private boolean hasCandidateDefinition() {
      return candidateDefinition != null;
    }

    /**
     * Returns the `goog.provide` or legacy namespace `goog.module` call that created this name, if
     * any, or otherwise the first 'previous provide' assignment that created this name.
     */
    Node getFirstProvideCall() {
      return firstNode;
    }

    /**
     * Returns the definition of this provided namespace in the input code, if any, or null.
     *
     * <p>For example, this returns `a.b = class {};` given 'a.b' in
     *
     * <pre>
     *   goog.provide('a.b');
     *   a.b = class {};
     * </pre>
     *
     * Note: this method will only return candidate definitions that count towards provide
     * rewriting. If a name is defined, then provided, the candidate definition will not be the
     * early definition. This doesn't completely mimic uncompiled behavior, but supports some legacy
     * code. Externs definitions never count.
     */
    Node getCandidateDefinition() {
      return candidateDefinition;
    }

    /** Returns the Closure namespace of this provide, e.g. "a.b" for `goog.provide('a.b');` */
    String getNamespace() {
      return namespace;
    }

    /**
     * Records function declaration, variable declarations, and assignments that refer to this
     * provided namespace.
     *
     * <p>This pass gives preference to declarations. If no declaration exists, records a reference
     * to an assignment so it can be repurposed later into a declaration.
     */
    private void addDefinition(Node node, JSChunk chunk, JSChunkGraph chunkGraph) {
      Preconditions.checkArgument(
          node.isExprResult() // assign
              || node.isFunction()
              || NodeUtil.isNameDeclaration(node));
      checkArgument(explicitNode != node);
      if ((candidateDefinition == null) || !node.isExprResult()) {
        candidateDefinition = node;
        updateMinimumChunk(chunk, chunkGraph);
      }
    }

    private void updateMinimumChunk(JSChunk newChunk, JSChunkGraph chunkGraph) {
      if (minimumChunk == null) {
        minimumChunk = newChunk;
      } else if (chunkGraph.getChunkCount() > 1) {
        minimumChunk = chunkGraph.getDeepestCommonDependencyInclusive(minimumChunk, newChunk);
      } else {
        // If there is no module graph, then there must be exactly one
        // module in the program.
        checkState(newChunk == minimumChunk, "Missing module graph");
      }
    }

    /**
     * Replace the provide statement.
     *
     * <p>If we're providing a name with no definition, then create one. If we're providing a name
     * with a duplicate definition, then make sure that definition becomes a declaration.
     */
    private void replace(
        boolean preserveGoogProvidesAndRequires, Map<String, ProvidedName> providedNames) {
      checkState(
          !this.isFromLegacyModule(),
          "Cannot rewrite provides without having rewritten goog.modules, found %s",
          firstNode);
      if (firstNode == null) {
        // Don't touch the base case ('goog').
        replacementNode = candidateDefinition;
        return;
      }

      // Handle the case where there is a duplicate definition for an explicitly
      // provided symbol.
      if (hasCandidateDefinition() && explicitNode != null) {
        // Does this need a VAR keyword?
        replacementNode = candidateDefinition;
        if (candidateDefinition.isExprResult()) {
          Node exprNode = candidateDefinition.getOnlyChild();
          if (exprNode.isAssign()) {
            Node nameNode = exprNode.getFirstChild();
            if (nameNode.isName()) {
              // In the case of a simple name, `name = value;`, we need to ensure the name is
              // actually declared with `var`.
              convertProvideAssignmentToVarDeclaration(exprNode, nameNode);
            } else {
              // `some.provided.namespace = value;`
              // We don't need to change the definition, but mark it as 'IS_NAMESPACE' so that
              // future passes know this was originally provided.
              candidateDefinition.putBooleanProp(Node.IS_NAMESPACE, true);
            }
          }
        }
      } else {
        // Handle the case where there's not an existing definition.
        if (!hasImplicitInitialization) {
          createNamespaceInitialization(
              createDeclarationNode(astFactory.createObjectLit()), providedNames);
        }
      }

      // Remove the `goog.provide('a.b.c');` call.
      if (explicitNode != null) {
        if (preserveGoogProvidesAndRequires) {
          return;
        }
        compiler.reportChangeToEnclosingScope(explicitNode);
        explicitNode.detach();
      }
    }

    private void convertProvideAssignmentToVarDeclaration(Node assignNode, Node nameNode) {
      // Convert `providedName = value;` into `var providedName = value;`.
      checkArgument(assignNode.isAssign(), assignNode);
      checkArgument(nameNode.isName(), nameNode);
      Node valueNode = nameNode.getNext();
      nameNode.detach();
      valueNode.detach();

      Node varNode = IR.var(nameNode, valueNode).srcref(candidateDefinition);
      varNode.setJSDocInfo(assignNode.getJSDocInfo());
      varNode.putBooleanProp(Node.IS_NAMESPACE, true);

      candidateDefinition.replaceWith(varNode);
      replacementNode = varNode;
      compiler.reportChangeToEnclosingScope(varNode);
    }

    /** Adds an assignment or declaration to this namespace to the AST, using the provided value */
    private void createNamespaceInitialization(
        Node replacement, Map<String, ProvidedName> providedNames) {
      replacementNode = replacement;
      if (firstChunk == minimumChunk) {
        replacementNode.insertBefore(firstNode);
      } else {
        // In this case, the name was implicitly provided by two independent
        // modules. We need to move this code up to a common module.
        int indexOfDot = namespace.lastIndexOf('.');
        if (indexOfDot == -1) {
          // Any old place is fine.
          compiler.getNodeForCodeInsertion(minimumChunk).addChildToBack(replacementNode);
        } else {
          // Add it after the parent namespace.
          ProvidedName parentName = providedNames.get(namespace.substring(0, indexOfDot));
          checkNotNull(parentName);
          checkNotNull(parentName.replacementNode);
          replacementNode.insertAfter(parentName.replacementNode);
        }
      }
      compiler.reportChangeToEnclosingScope(replacementNode);
    }

    /**
     * Create the declaration node for this name, without inserting it into the AST.
     *
     * @param value the object literal namespace, possibly in a CAST
     */
    private Node createDeclarationNode(Node value) {
      checkArgument(value.isObjectLit() || value.isCast(), value);
      if (namespace.indexOf('.') == -1) {
        return makeVarDeclNode(value);
      } else {
        return makeAssignmentExprNode(value);
      }
    }

    /** Creates a simple namespace variable declaration (e.g. <code>var foo = {};</code>). */
    private Node makeVarDeclNode(Node value) {
      Node name = IR.name(namespace);
      name.addChildToFront(value);

      Node decl = IR.var(name);
      decl.putBooleanProp(Node.IS_NAMESPACE, true);

      if (compiler.getCodingConvention().isConstant(namespace)) {
        name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }
      if (!hasCandidateDefinition()) {
        decl.setJSDocInfo(NodeUtil.createConstantJsDoc());
      }

      checkState(isNamespacePlaceholder(decl));
      setSourceInfo(decl);
      return decl;
    }

    /** Creates a dotted namespace assignment expression (e.g. <code>foo.bar = {};</code>). */
    private Node makeAssignmentExprNode(Node value) {
      // Note: as of May 2021, using the unknown type vs. the actual inferred type both produced the
      // same optimized JS after type-based optimizations. So the lack of type info is intentional.
      Node lhs = astFactory.createQNameWithUnknownType(namespace).srcrefTree(firstNode);
      Node decl = IR.exprResult(astFactory.createAssign(lhs, value));
      decl.putBooleanProp(Node.IS_NAMESPACE, true);
      if (!hasCandidateDefinition()) {
        decl.getFirstChild().setJSDocInfo(NodeUtil.createConstantJsDoc());
      }
      checkState(isNamespacePlaceholder(decl));
      setSourceInfo(decl);
      // This function introduces artifical nodes and we don't need them for indexing.
      // Marking all but the last one as non-indexable. So if this function adds:
      // foo.bar.baz = {};
      // then we mark foo and bar as non-indexable.
      lhs.getFirstChild().makeNonIndexableRecursive();
      return decl;
    }

    /** Copy source info to the new node. */
    private void setSourceInfo(Node newNode) {
      Node sourceInfoNode = firstNode;

      Node provideStringNode = getProvideStringNode();
      if (provideStringNode != null) {
        // Given namespace "foo.bar.baz" we create node for "baz" here and need to calculate
        // length and start of the last component which is "baz".
        int firstCharIndex = namespace.lastIndexOf('.') + 1; // If no dots, then 0.

        sourceInfoNode = provideStringNode.cloneNode();
        sourceInfoNode.setLinenoCharno(
            sourceInfoNode.getLineno(),
            sourceInfoNode.getCharno() + firstCharIndex + 1); // +1 for quote
        sourceInfoNode.setLength(namespace.length() - firstCharIndex);
      }

      newNode.srcrefTree(sourceInfoNode);
    }

    private @Nullable Node getProvideStringNode() {
      return (firstNode.hasChildren() && NodeUtil.isExprCall(firstNode))
          ? firstNode.getFirstChild().getLastChild()
          : null;
    }

    @Override
    public String toString() {
      String explicitOrImplicit = isExplicitlyProvided() ? "explicit" : "implicit";
      return String.format("ProvidedName: %s, %s", namespace, explicitOrImplicit);
    }
  }

  /**
   * Returns whether the node initializes a goog.provide'd namespace (e.g. `a.b = {};`) with a
   * simple namespace object literal (e.g. not `a.b = class {}`;)
   */
  private static boolean isNamespacePlaceholder(Node n) {
    if (!n.getBooleanProp(Node.IS_NAMESPACE)) {
      return false;
    }

    Node value = null;
    if (n.isExprResult()) {
      Node assign = n.getFirstChild();
      value = assign.getLastChild();
    } else if (n.isVar()) {
      Node name = n.getFirstChild();
      value = name.getFirstChild();
    }

    if (value == null) {
      return false;
    }
    if (value.isCast()) {
      // There may be a cast to unknown type wrapped around the value.
      value = value.getOnlyChild();
    }
    return value.isObjectLit() && !value.hasChildren();
  }
}
