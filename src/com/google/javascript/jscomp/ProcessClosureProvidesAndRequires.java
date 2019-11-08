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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Replaces goog.provide calls, removes goog.{require,requireType,forwardDeclare} calls and verifies
 * that each goog.{require,requireType} has a corresponding goog.provide.
 *
 * <p>We expect all goog.modules and goog.requires in modules to have been rewritten. This is why
 * all remaining require/requireType calls must refer to a goog.provide, although the original JS
 * code may contain goog.requires of a goog.module.
 *
 * <p>This is designed to work during a hotswap pass, under the assumption that no rewriting has
 * been done of goog.provides and definitions other than this pass.
 *
 * <p>This also annotates all provided namespace definitions `a.b = {};` with {@link
 * Node#IS_NAMESPACE} so that later passes know they refer to a goog.provide'd namespace.
 *
 * <p>If modules have not been rewritten, this pass also includes legacy Closure module namespaces
 * in the list of {@link ProvidedName}s.
 */
class ProcessClosureProvidesAndRequires implements HotSwapCompilerPass {

  // The root Closure namespace
  private static final String GOOG = "goog";

  private final AbstractCompiler compiler;
  private final JSModuleGraph moduleGraph;

  // Use a LinkedHashMap because the goog.provides must be processed in a deterministic order.
  private final Map<String, ProvidedName> providedNames = new LinkedHashMap<>();

  private final CheckLevel requiresLevel;
  private final PreprocessorSymbolTable preprocessorSymbolTable;
  // If this is true, rewriting will not remove any goog.provide or goog.require calls
  private final boolean preserveGoogProvidesAndRequires;
  private final List<Node> requiresToBeRemoved = new ArrayList<>();
  // Whether this instance has already rewritten goog.provides, which can only happen once
  private boolean hasRewritingOccurred = false;
  private final Set<Node> forwardDeclaresToRemove = new HashSet<>();
  private final Set<Node> previouslyProvidedDefinitions = new HashSet<>();

  ProcessClosureProvidesAndRequires(
      AbstractCompiler compiler,
      @Nullable PreprocessorSymbolTable preprocessorSymbolTable,
      CheckLevel requiresLevel,
      boolean preserveGoogProvidesAndRequires) {
    this.compiler = compiler;
    this.preprocessorSymbolTable = preprocessorSymbolTable;
    this.moduleGraph = compiler.getModuleGraph();
    this.requiresLevel = requiresLevel;
    this.preserveGoogProvidesAndRequires = preserveGoogProvidesAndRequires;
  }

  /** When invoked as compiler pass, we rewrite all provides and requires. */
  @Override
  public void process(Node externs, Node root) {
    rewriteProvidesAndRequires(externs, root);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    // TODO(bashir): Implement a real hot-swap version instead and make it fully
    // consistent with the full version.
    this.compiler.process(this);
  }

  /** Collects all goog.provides in the given namespace and warns on invalid code */
  Map<String, ProvidedName> collectProvidedNames(Node externs, Node root) {
    if (this.providedNames.isEmpty()) {
      // goog is special-cased because it is provided in Closure's base library.
      providedNames.put(GOOG, new ProvidedName(GOOG, null, null, false /* implicit */, false));
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
      pn.replace();
    }
    deleteNamespaceInitializationsFromPreviousProvides();

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
          processLegacyModuleCall(closureNamespace, googModuleCall, t.getModule());
        }
        return false;
      }
      return !n.isModuleBody();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case CALL:
          Node left = n.getFirstChild();
          if (left.isGetProp()) {
            Node name = left.getFirstChild();
            if (name.isName() && GOOG.equals(name.getString())) {
              // For the sake of simplicity, we report code changes
              // when we see a provides/requires, and don't worry about
              // reporting the change when we actually do the replacement.
              String methodName = name.getNext().getString();
              switch (methodName) {
                case "require":
                case "requireType":
                  if (isValidPrimitiveCall(t, n)) {
                    processRequireCall(t, n, parent);
                  }
                  break;
                case "provide":
                  if (isValidPrimitiveCall(t, n)) {
                    processProvideCall(t, n, parent);
                  }
                  break;
                case "forwardDeclare":
                  if (isValidPrimitiveCall(t, n)) {
                    processForwardDeclare(n, parent);
                  }
                  break;
              }
            }
          }
          break;

        case ASSIGN:
        case NAME:
          // If this is an assignment to a provided name, remove the provided object.
          handleCandidateProvideDefinition(t, n, parent);
          break;

        case EXPR_RESULT:
          handleStubDefinition(t, n);
          break;

        case CLASS:
          if (t.inGlobalHoistScope() && !NodeUtil.isClassExpression(n)) {
            String name = n.getFirstChild().getString();
            ProvidedName pn = providedNames.get(name);
            if (pn != null) {
              compiler.report(
                  JSError.make(n, ProcessClosurePrimitives.CLASS_NAMESPACE_ERROR, name));
            }
          }
          break;

        case FUNCTION:
          // If this is a declaration of a provided named function, this is an
          // error. Hoisted functions will explode if they're provided.
          if (t.inGlobalHoistScope() && NodeUtil.isFunctionDeclaration(n)) {
            String name = n.getFirstChild().getString();
            ProvidedName pn = providedNames.get(name);
            if (pn != null) {
              compiler.report(
                  JSError.make(n, ProcessClosurePrimitives.FUNCTION_NAMESPACE_ERROR, name));
            }
          }
          break;

        default:
          break;
      }
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
  private void processRequireCall(NodeTraversal t, Node n, Node parent) {
    Node left = n.getFirstChild();
    Node arg = left.getNext();
    String method = left.getFirstChild().getNext().getString();
    if (verifyLastArgumentIsString(left, arg)) {
      String ns = arg.getString();
      ProvidedName provided = providedNames.get(ns);
      if (provided == null || !provided.isExplicitlyProvided()) {
      } else {
        JSModule providedModule = provided.explicitModule;

        if (!provided.isFromExterns()) {
          checkNotNull(providedModule, n);

          JSModule module = t.getModule();
          // A cross-chunk goog.require must match a goog.provide in an earlier chunk. However, a
          // cross-chunk goog.requireType is allowed to match a goog.provide in a later chunk.
          // TODO(b/142571318): move this into CheckClosureImports
          if (module != providedModule
              && !moduleGraph.dependsOn(module, providedModule)
              && !method.equals("requireType")) {
            compiler.report(
                JSError.make(
                    n,
                    ProcessClosurePrimitives.XMODULE_REQUIRE_ERROR,
                    ns,
                    providedModule.getName(),
                    module.getName()));
          }
        }
      }

      maybeAddNameToSymbolTable(left);
      maybeAddStringToSymbolTable(arg);

      // Requires should be removed before further processing.
      // Some clients run closure pass multiple times, first with
      // the checks for broken requires turned off. In these cases, we
      // allow broken requires to be preserved by the first run to
      // let them be caught in the subsequent run.
      if (!preserveGoogProvidesAndRequires && (provided != null || requiresLevel.isOn())) {
        requiresToBeRemoved.add(parent);
      }
    }
  }

  /** Handles a goog.module that is a legacy namespace. */
  private void processLegacyModuleCall(String namespace, Node googModuleCall, JSModule module) {
    registerAnyProvidedPrefixes(namespace, googModuleCall, module);
    providedNames.put(
        namespace,
        new ProvidedName(
            namespace,
            googModuleCall,
            module,
            /* explicit= */ true,
            /* fromPreviousProvide= */ false));
  }

  /** Handles a goog.provide call. */
  private void processProvideCall(NodeTraversal t, Node n, Node parent) {
    checkState(n.isCall());
    Node left = n.getFirstChild();
    Node arg = left.getNext();
    if (verifyProvide(left, arg)) {
      String ns = arg.getString();

      maybeAddNameToSymbolTable(left);
      maybeAddStringToSymbolTable(arg);

      if (providedNames.containsKey(ns)) {
        ProvidedName previouslyProvided = providedNames.get(ns);
        if (!previouslyProvided.isExplicitlyProvided() || previouslyProvided.isPreviouslyProvided) {
          previouslyProvided.addProvide(parent, t.getModule(), true);
        }
      } else {
        registerAnyProvidedPrefixes(ns, parent, t.getModule());
        providedNames.put(
            ns,
            new ProvidedName(
                ns, parent, t.getModule(), /* explicit= */ true, /* fromPreviousProvide= */ false));
      }
    }
  }

  /**
   * Handles a stub definition for a goog.provided name (e.g. a @typedef or a definition from
   * externs)
   *
   * @param n EXPR_RESULT node.
   */
  private void handleStubDefinition(NodeTraversal t, Node n) {
    if (!t.inGlobalHoistScope()) {
      return;
    }
    JSDocInfo info = n.getFirstChild().getJSDocInfo();
    boolean hasStubDefinition = info != null && (n.isFromExterns() || info.hasTypedefType());
    if (hasStubDefinition) {
      if (n.getFirstChild().isQualifiedName()) {
        String name = n.getFirstChild().getQualifiedName();
        ProvidedName pn = providedNames.get(name);
        if (pn != null) {
          n.putBooleanProp(Node.WAS_PREVIOUSLY_PROVIDED, true);
          pn.addDefinition(n, t.getModule());
        } else if (n.getBooleanProp(Node.WAS_PREVIOUSLY_PROVIDED)) {
          // We didn't find it in the providedNames, but it was previously marked as provided.
          // This implies we're in hotswap pass and the current typedef is a provided namespace.
          ProvidedName provided = new ProvidedName(name, n, t.getModule(), true, true);
          providedNames.put(name, provided);
          provided.addDefinition(n, t.getModule());
        }
      }
    }
  }

  /** Handles a candidate definition for a goog.provided name. */
  private void handleCandidateProvideDefinition(NodeTraversal t, Node n, Node parent) {
    if (t.inGlobalHoistScope()) {
      String name = null;
      if (n.isName() && NodeUtil.isNameDeclaration(parent)) {
        name = n.getString();
      } else if (n.isAssign() && parent.isExprResult()) {
        name = n.getFirstChild().getQualifiedName();
      }

      if (name != null) {
        if (parent.getBooleanProp(Node.IS_NAMESPACE)) {
          // TODO(b/128361464): stop including goog.module exports in this case.
          processProvideFromPreviousPass(t, name, parent);
        } else {
          ProvidedName pn = providedNames.get(name);
          if (pn != null) {
            pn.addDefinition(parent, t.getModule());
          }
        }
      }
    }
  }

  /**
   * Removes duplicate initializations of goog.provided namespaces created by previous passes
   *
   * <p>Should be called after calling {@link ProvidedName#replace()} on all provided names..
   */
  private void deleteNamespaceInitializationsFromPreviousProvides() {
    for (Node name : previouslyProvidedDefinitions) {
      name.detach();
    }
  }

  /**
   * Processes the output of processed-provide from a previous pass. This will update our data
   * structures in the same manner as if the provide had been processed in this pass.
   *
   * <p>TODO(b/128120127): delete this method
   */
  private void processProvideFromPreviousPass(NodeTraversal t, String name, Node parent) {
    JSModule module = t.getModule();
    if (providedNames.containsKey(name)) {
      ProvidedName provided = providedNames.get(name);
      provided.addDefinition(parent, module);
      if (isNamespacePlaceholder(parent)) {
        // Remove this later if it is a simple object literal. Replacing the corresponding
        // ProvidedName will create a new definition.
        // Don't add this as a 'definition' of the provided name to support pushing provides
        // into earlier modules.
        previouslyProvidedDefinitions.add(parent);
      }
    } else {
      // Record this provide created on a previous pass. This can happen if the previous pass had
      // goog.provide('foo.bar');, but all we have now is the rewritten `foo.bar = {};`.
      registerAnyProvidedPrefixes(name, parent, module);

      ProvidedName provided = new ProvidedName(name, parent, module, true, true);
      providedNames.put(name, provided);
      provided.addDefinition(parent, module);
    }
  }

  /**
   * Verifies that a provide method call has exactly one argument, and that it's a string literal
   * and that the contents of the string are valid JS tokens. Reports a compile error if it doesn't.
   *
   * @return Whether the argument checked out okay
   */
  private boolean verifyProvide(Node methodName, Node arg) {
    if (!verifyLastArgumentIsString(methodName, arg)) {
      return false;
    }

    if (!NodeUtil.isValidQualifiedName(
        compiler.getOptions().getLanguageIn().toFeatureSet(), arg.getString())) {
      compiler.report(
          JSError.make(
              arg,
              ProcessClosurePrimitives.INVALID_PROVIDE_ERROR,
              arg.getString(),
              compiler.getOptions().getLanguageIn().toString()));
      return false;
    }

    return true;
  }

  /** Marks a goog.forwardDeclare call for removal. */
  private void processForwardDeclare(Node n, Node parent) {
    CodingConvention convention = compiler.getCodingConvention();

    List<String> typeDeclarations = convention.identifyTypeDeclarationCall(n);

    if (typeDeclarations != null && typeDeclarations.size() == 1) {
      // Forward declaration was recorded and we can remove the call.
      Node toRemove = parent.isExprResult() ? parent : parent.getParent();
      forwardDeclaresToRemove.add(toRemove);
    }
  }

  /**
   * Verifies that a method call has exactly one argument, and that it's a string literal. Reports a
   * compile error if it doesn't.
   *
   * @return Whether the argument checked out okay
   */
  private boolean verifyLastArgumentIsString(Node methodName, Node arg) {
    return verifyNotNull(methodName, arg)
        && verifyOfType(methodName, arg, Token.STRING)
        && verifyIsLast(methodName, arg);
  }

  /** @return Whether the argument checked out okay */
  private boolean verifyNotNull(Node methodName, Node arg) {
    if (arg == null) {
      compiler.report(
          JSError.make(
              methodName,
              ProcessClosurePrimitives.NULL_ARGUMENT_ERROR,
              methodName.getQualifiedName()));
      return false;
    }
    return true;
  }

  /** @return Whether the argument checked out okay */
  private boolean verifyOfType(Node methodName, Node arg, Token desiredType) {
    if (arg.getToken() != desiredType) {
      compiler.report(
          JSError.make(
              methodName,
              ProcessClosurePrimitives.INVALID_ARGUMENT_ERROR,
              methodName.getQualifiedName()));
      return false;
    }
    return true;
  }

  /** @return Whether the argument checked out okay */
  private boolean verifyIsLast(Node methodName, Node arg) {
    if (arg.getNext() != null) {
      compiler.report(
          JSError.make(
              methodName,
              ProcessClosurePrimitives.TOO_MANY_ARGUMENTS_ERROR,
              methodName.getQualifiedName()));
      return false;
    }
    return true;
  }

  /**
   * Registers ProvidedNames for prefix namespaces if they haven't already been defined. The prefix
   * namespaces must be registered in order from shortest to longest.
   *
   * @param ns The namespace whose prefixes may need to be provided.
   * @param node The EXPR of the provide call.
   * @param module The current module.
   */
  private void registerAnyProvidedPrefixes(String ns, Node node, JSModule module) {
    int pos = ns.indexOf('.');
    while (pos != -1) {
      String prefixNs = ns.substring(0, pos);
      pos = ns.indexOf('.', pos + 1);
      if (providedNames.containsKey(prefixNs)) {
        providedNames.get(prefixNs).addProvide(node, module, /* explicit= */ false);
      } else {
        providedNames.put(
            prefixNs,
            new ProvidedName(
                prefixNs, node, module, /* explicit= */ false, /* fromPreviousProvide= */ false));
      }
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
  class ProvidedName {
    // The Closure namespace this name represents, e.g. `a.b` for `goog.provide('a.b');`
    private final String namespace;

    // The first node in the AST that creates this ProvidedName.
    // This is always a goog.provide('a.b'), null (for implicit namespaces and 'goog'), or an
    // assignment or declaration for a 'previously provided' name or parent namespace of such.
    // This should only be used for source info and a place to hang namespace definitions.
    private final Node firstNode;
    // The module where this namespace was first goog.provided, if modules exist. */
    private final JSModule firstModule;

    // The node where the call was explicitly goog.provided. Null if the namespace is implicit.
    // If this is previously provided, this will instead be the expression or declaration marked
    // as IS_NAMESPACE.
    private Node explicitNode = null;
    // The JSModule of explicitNode, null if this is not explicit or there are no input modules.
    private JSModule explicitModule = null;
    // Whether there are child namespaces of this one.
    private boolean hasAChildNamespace = false;

    // The candidate definition for this namespace. For example, given
    //      goog.provide('a.b');
    //      /** @constructor * /
    //      a.b = function() {};
    // the 'candidate definition' of 'a.b' is the GETPROP 'a.b' from the constructor declaration.
    private Node candidateDefinition = null;

    // The minimum module where the provide namespace definition must appear. If child namespaces of
    // this provide appear in multiple modules, this module must be earlier than all child
    // namespace's modules.
    private JSModule minimumModule = null;

    // The replacement declaration. Null until replace() has been called.
    private Node replacementNode = null;

    // Whether this comes not from a goog.provide, but from a leftover rewritten goog.provide from a
    // previous pass run. Implies this is during a hotswap pass.
    private final boolean isPreviouslyProvided;

    /**
     * Initializes a ProvidedName with some basic information, potentially to be updated later.
     *
     * @param node Can be null (for GOOG or an implicit name), an EXPR_RESULT for a goog.provide, or
     *     an EXPR_RESULT or name declaration for a previously provided name.
     * @param explicit Whether this came from an actual goog.provide('a.b.c'); call
     * @param fromPreviousProvide Whether this came from a namespace created by a previous iteration
     */
    ProvidedName(
        String namespace,
        Node node,
        JSModule module,
        boolean explicit,
        boolean fromPreviousProvide) {
      Preconditions.checkArgument(
          node == null
              || NodeUtil.isExprCall(node)
              || ((fromPreviousProvide || !explicit)
                  && (NodeUtil.isExprAssign(node)
                      || NodeUtil.isNameDeclaration(node)
                      || node.isExprResult() && node.getFirstChild().isQualifiedName())),
          node);
      this.namespace = namespace;
      this.firstNode = node;
      this.firstModule = module;
      this.isPreviouslyProvided = fromPreviousProvide;

      addProvide(node, module, explicit);
    }

    /**
     * Adds an implicit or explicit provide.
     *
     * <p>Every provided name can have multiple implicit provides but a maximum of one explicit
     * provide.
     *
     * <p>We may also consider a namespace definition leftover from a previous pass to be a
     * 'provide' if in hotswap mode.
     *
     * @param node the EXPR_RESULT representing this provide or possible a VAR for a previously
     *     provided name. null if implicit.
     */
    void addProvide(Node node, JSModule module, boolean explicit) {
      if (explicit) {
        // goog.provide('name.space');
        checkState(explicitNode == null || isPreviouslyProvided);
        checkArgument(
            node.isExprResult() || (NodeUtil.isNameDeclaration(node) && isPreviouslyProvided),
            node);
        explicitNode = node;
        explicitModule = module;
      } else {
        // goog.provide('name.space.some.child');
        hasAChildNamespace = true;
      }
      updateMinimumModule(module);
    }

    /** Whether there existed a `goog.provide('a.b');` for this name 'a.b' */
    boolean isExplicitlyProvided() {
      return explicitNode != null;
    }

    boolean isFromExterns() {
      return explicitNode.isFromExterns();
    }

    private boolean hasCandidateDefinitionNotFromPreviousPass() {
      // Exclude 'candidate definitions' that were added by previous pass runs because when
      // rewriting provides, we can erase definitions that the compiler itself added, but not
      // definitions that a user added.
      return candidateDefinition != null
          && !previouslyProvidedDefinitions.contains(candidateDefinition);
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
    private void addDefinition(Node node, JSModule module) {
      Preconditions.checkArgument(
          node.isExprResult() // assign
              || node.isFunction()
              || NodeUtil.isNameDeclaration(node));
      checkArgument(explicitNode != node || isPreviouslyProvided);
      if ((candidateDefinition == null) || !node.isExprResult()) {
        candidateDefinition = node;
        updateMinimumModule(module);
      }
    }

    private void updateMinimumModule(JSModule newModule) {
      if (minimumModule == null) {
        minimumModule = newModule;
      } else if (moduleGraph.getModuleCount() > 1) {
        minimumModule = moduleGraph.getDeepestCommonDependencyInclusive(minimumModule, newModule);
      } else {
        // If there is no module graph, then there must be exactly one
        // module in the program.
        checkState(newModule == minimumModule, "Missing module graph");
      }
    }

    /**
     * Replace the provide statement.
     *
     * <p>If we're providing a name with no definition, then create one. If we're providing a name
     * with a duplicate definition, then make sure that definition becomes a declaration.
     */
    private void replace() {
      if (firstNode == null) {
        // Don't touch the base case ('goog').
        replacementNode = candidateDefinition;
        return;
      }

      // Handle the case where there is a duplicate definition for an explicitly
      // provided symbol.
      if (hasCandidateDefinitionNotFromPreviousPass() && explicitNode != null) {
        JSDocInfo info;
        if (candidateDefinition.isExprResult()) {
          info = candidateDefinition.getFirstChild().getJSDocInfo();
        } else {
          info = candidateDefinition.getJSDocInfo();
        }

        // Validate that the namespace is not declared as a generic object type.
        if (info != null) {
          JSTypeExpression expr = info.getType();
          if (expr != null) {
            Node n = expr.getRoot();
            if (n.getToken() == Token.BANG) {
              n = n.getFirstChild();
            }
            if (n.isString()
                && !n.hasChildren() // templated object types are ok.
                && n.getString().equals("Object")) {
              compiler.report(
                  JSError.make(candidateDefinition, ProcessClosurePrimitives.WEAK_NAMESPACE_TYPE));
            }
          }
        }

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
          } else {
            // /** @typedef {something} */ name.space.Type;
            checkState(exprNode.isQualifiedName(), exprNode);
            // If this namespace has child namespaces, we still need to add an object to hang them
            // on to avoid creating broken code.
            // We must cast the type of the literal to unknown, because the type checker doesn't
            // expect the namespace to have a value.
            if (hasAChildNamespace) {
              createNamespaceInitialization(
                  createDeclarationNode(
                      IR.cast(IR.objectlit(), createUnknownTypeJsDocInfo(exprNode))));
            }
          }
        }
      } else {
        // Handle the case where there's not an existing definition.
        createNamespaceInitialization(createDeclarationNode(IR.objectlit()));
      }

      // Remove the `goog.provide('a.b.c');` call.
      if (explicitNode != null && !isPreviouslyProvided) {
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
      assignNode.removeChild(nameNode);
      assignNode.removeChild(valueNode);

      Node varNode = IR.var(nameNode, valueNode).useSourceInfoFrom(candidateDefinition);
      varNode.setJSDocInfo(assignNode.getJSDocInfo());
      varNode.putBooleanProp(Node.IS_NAMESPACE, true);

      candidateDefinition.replaceWith(varNode);
      replacementNode = varNode;
      compiler.reportChangeToEnclosingScope(varNode);
    }

    /** Adds an assignment or declaration to this namespace to the AST, using the provided value */
    private void createNamespaceInitialization(Node replacement) {
      replacementNode = replacement;
      if (firstModule == minimumModule) {
        firstNode.getParent().addChildBefore(replacementNode, firstNode);
      } else {
        // In this case, the name was implicitly provided by two independent
        // modules. We need to move this code up to a common module.
        int indexOfDot = namespace.lastIndexOf('.');
        if (indexOfDot == -1) {
          // Any old place is fine.
          compiler.getNodeForCodeInsertion(minimumModule).addChildToBack(replacementNode);
        } else {
          // Add it after the parent namespace.
          ProvidedName parentName = providedNames.get(namespace.substring(0, indexOfDot));
          checkNotNull(parentName);
          checkNotNull(parentName.replacementNode);
          parentName
              .replacementNode
              .getParent()
              .addChildAfter(replacementNode, parentName.replacementNode);
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
      if (!hasCandidateDefinitionNotFromPreviousPass()) {
        decl.setJSDocInfo(NodeUtil.createConstantJsDoc());
      }

      checkState(isNamespacePlaceholder(decl));
      setSourceInfo(decl);
      return decl;
    }

    /** Creates a dotted namespace assignment expression (e.g. <code>foo.bar = {};</code>). */
    private Node makeAssignmentExprNode(Node value) {
      Node lhs =
          NodeUtil.newQName(
              compiler,
              namespace,
              firstNode /* real source info will be filled in below */,
              namespace);
      Node decl = IR.exprResult(IR.assign(lhs, value));
      decl.putBooleanProp(Node.IS_NAMESPACE, true);
      if (!hasCandidateDefinitionNotFromPreviousPass()) {
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
      Node provideStringNode = getProvideStringNode();
      int offset = provideStringNode == null ? 0 : getSourceInfoOffset();
      Node sourceInfoNode = provideStringNode == null ? firstNode : provideStringNode;
      newNode.useSourceInfoIfMissingFromForTree(sourceInfoNode);
      if (offset != 0) {
        newNode.setSourceEncodedPositionForTree(sourceInfoNode.getSourcePosition() + offset);
        // Given namespace "foo.bar.baz" we create node for "baz" here and need to calculate
        // length of the last component which is "baz".
        int lengthOfLastComponent = namespace.length() - (namespace.lastIndexOf(".") + 1);
        newNode.setLengthForTree(lengthOfLastComponent);
      }
    }

    /** Get the offset into the provide node where the symbol appears. */
    private int getSourceInfoOffset() {
      int indexOfLastDot = namespace.lastIndexOf('.');

      // +1 for the opening quote
      // +1 for the dot
      // if there's no dot, then the -1 index cancels it out
      // so elegant!
      return 2 + indexOfLastDot;
    }

    private Node getProvideStringNode() {
      return (firstNode.getFirstChild() != null && NodeUtil.isExprCall(firstNode))
          ? firstNode.getFirstChild().getLastChild()
          : null;
    }

    @Override
    @GwtIncompatible("Unnecessary") // This is just for debugging in an IDE.
    public String toString() {
      String explicitOrImplicit = isExplicitlyProvided() ? "explicit" : "implicit";
      return String.format("ProvidedName: %s, %s", namespace, explicitOrImplicit);
    }
  }

  private JSDocInfo createUnknownTypeJsDocInfo(Node sourceNode) {
    JSDocInfoBuilder castToUnknownBuilder = new JSDocInfoBuilder(true);
    castToUnknownBuilder.recordType(
        new JSTypeExpression(
            new Node(Token.QMARK).srcref(sourceNode), "<ProcessClosurePrimitives.java>"));
    return castToUnknownBuilder.build();
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

  /** Add the given qualified name node to the symbol table. */
  private void maybeAddStringToSymbolTable(Node string) {
    if (preprocessorSymbolTable != null) {
      preprocessorSymbolTable.addStringNode(string, compiler);
    }
  }

  /** Add the given qualified name node to the symbol table. */
  private void maybeAddNameToSymbolTable(Node name) {
    if (preprocessorSymbolTable != null) {
      preprocessorSymbolTable.addReference(name);
    }
  }
}
