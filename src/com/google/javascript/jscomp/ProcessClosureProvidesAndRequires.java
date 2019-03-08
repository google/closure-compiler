/*
 * Copyright 2006 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_SCOPE_ERROR;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
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
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Replaces goog.provide calls, removes goog.{require,requireType} calls, verifies that each
 * goog.{require,requireType} has a corresponding goog.provide, and performs some Closure-pecific
 * simplifications.
 *
 * @author chrisn@google.com (Chris Nokleberg)
 */
class ProcessClosureProvidesAndRequires {

  /** The root Closure namespace */
  static final String GOOG = "goog";

  private final AbstractCompiler compiler;
  private final JSModuleGraph moduleGraph;

  // The goog.provides must be processed in a deterministic order.
  private final Map<String, ProvidedName> providedNames = new LinkedHashMap<>();

  private final List<UnrecognizedRequire> unrecognizedRequires = new ArrayList<>();
  private final CheckLevel requiresLevel;
  private final PreprocessorSymbolTable preprocessorSymbolTable;
  // If this is true, rewriting will not remove any goog.provide or goog.require calls
  private final boolean preserveGoogProvidesAndRequires;
  private final List<Node> requiresToBeRemoved = new ArrayList<>();
  // Set of nodes to report changed at the end of rewriting
  private final Set<Node> maybeTemporarilyLiveNodes = new HashSet<>();
  private boolean hasRewritingOccurred = false;

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

    // goog is special-cased because it is provided in Closure's base library.
    providedNames.put(GOOG, new ProvidedName(GOOG, null, null, false /* implicit */));
  }

  /** Collects all goog.provides and goog.require namespace */
  void rewriteProvidesAndRequires(Node externs, Node root) {
    checkState(!hasRewritingOccurred, "Cannot call rewriteProvidesAndRequires twice per instance");
    hasRewritingOccurred = true;

    // TODO(b/124920011): split this into one method to collect provides/requires and a second
    // method to rewrite them.
    NodeTraversal.traverseRoots(compiler, new CollectDefinitions(), externs, root);

    for (ProvidedName pn : providedNames.values()) {
      pn.replace();
    }

    if (requiresLevel.isOn()) {
      for (UnrecognizedRequire r : unrecognizedRequires) {
        checkForLateOrMissingProvide(r);
      }
    }

    for (Node closureRequire : requiresToBeRemoved) {
      compiler.reportChangeToEnclosingScope(closureRequire);
      closureRequire.detach();
    }
    for (Node liveNode : maybeTemporarilyLiveNodes) {
      compiler.reportChangeToEnclosingScope(liveNode);
    }
  }

  private void checkForLateOrMissingProvide(UnrecognizedRequire r) {
    // Both goog.require and goog.requireType must have a matching goog.provide.
    // However, goog.require must match an earlier goog.provide, while goog.requireType is allowed
    // to match a later goog.provide.
    DiagnosticType error;
    ProvidedName expectedName = providedNames.get(r.namespace);
    if (expectedName != null && expectedName.firstNode != null) {
      if (r.isRequireType) {
        return;
      }
      error = ProcessClosurePrimitives.LATE_PROVIDE_ERROR;
    } else {
      error = ProcessClosurePrimitives.MISSING_PROVIDE_ERROR;
    }
    compiler.report(JSError.make(r.requireNode, requiresLevel, error, r.namespace));
  }

  private class CollectDefinitions extends AbstractPostOrderCallback {
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
                  if (validateAliasiablePrimitiveCall(t, n, methodName)) {
                    processRequireCall(t, n, parent);
                  }
                  break;
                case "provide":
                  if (validateUnaliasablePrimitiveCall(t, n, methodName)) {
                    processProvideCall(t, n, parent);
                  }
                  break;
                case "forwardDeclare":
                  if (validateAliasiablePrimitiveCall(t, n, methodName)) {
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

  /**
   * Verifies that a) the call is in the global scope and b) the return value is unused
   *
   * <p>This method is for primitives that never return a value.
   */
  private boolean validateUnaliasablePrimitiveCall(NodeTraversal t, Node n, String methodName) {
    return validatePrimitiveCallWithMessage(
        t, n, methodName, ProcessClosurePrimitives.CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR);
  }

  /**
   * Verifies that a) the call is in the global scope and b) the return value is unused
   *
   * <p>This method is for primitives that do return a value in modules, but not in scripts/
   * goog.provide files
   */
  private boolean validateAliasiablePrimitiveCall(NodeTraversal t, Node n, String methodName) {
    return validatePrimitiveCallWithMessage(
        t,
        n,
        methodName,
        ProcessClosurePrimitives.CLOSURE_CALL_CANNOT_BE_ALIASED_OUTSIDE_MODULE_ERROR);
  }

  /**
   * @param methodName list of primitve types classed together with this one
   * @param invalidAliasingError which DiagnosticType to emit if this call is aliased. this depends
   *     on whether the primitive is sometimes aliasiable in a module or never aliasable.
   */
  private boolean validatePrimitiveCallWithMessage(
      NodeTraversal t, Node n, String methodName, DiagnosticType invalidAliasingError) {
    // Ignore invalid primitives if we didn't strip module sugar.
    if (compiler.getOptions().shouldPreserveGoogModule()) {
      return true;
    }

    if (!t.inGlobalHoistScope()) {
      compiler.report(JSError.make(n, INVALID_CLOSURE_CALL_SCOPE_ERROR));
      return false;
    } else if (!n.getParent().isExprResult()) {
      // If the call is in the global hoist scope, but the result is used
      compiler.report(JSError.make(n, invalidAliasingError, GOOG + "." + methodName));
      return false;
    }
    return true;
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
        unrecognizedRequires.add(new UnrecognizedRequire(n, ns, method.equals("requireType")));
      } else {
        JSModule providedModule = provided.explicitModule;

        if (!provided.isFromExterns()) {
          checkNotNull(providedModule, n);

          JSModule module = t.getModule();
          // A cross-chunk goog.require must match a goog.provide in an earlier chunk. However, a
          // cross-chunk goog.requireType is allowed to match a goog.provide in a later chunk.
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
        if (!previouslyProvided.isExplicitlyProvided()) {
          previouslyProvided.addProvide(parent, t.getModule(), true);
        } else {
          String explicitSourceName = previouslyProvided.explicitNode.getSourceFileName();
          compiler.report(
              JSError.make(
                  n, ProcessClosurePrimitives.DUPLICATE_NAMESPACE_ERROR, ns, explicitSourceName));
        }
      } else {
        registerAnyProvidedPrefixes(ns, parent, t.getModule());
        providedNames.put(ns, new ProvidedName(ns, parent, t.getModule(), true));
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
          ProvidedName provided = new ProvidedName(name, n, t.getModule(), true);
          providedNames.put(name, provided);
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
   * Processes the output of processed-provide from a previous pass. This will update our data
   * structures in the same manner as if the provide had been processed in this pass.
   */
  private void processProvideFromPreviousPass(NodeTraversal t, String name, Node parent) {
    if (!providedNames.containsKey(name)) {
      // Record this provide created on a previous pass, and create a dummy
      // EXPR node as a placeholder to simulate an explicit provide.
      Node expr = new Node(Token.EXPR_RESULT);
      expr.useSourceInfoIfMissingFromForTree(parent);
      parent.getParent().addChildBefore(expr, parent);
      /**
       * 'expr' has been newly added to the AST, but it might be removed again before this pass
       * finishes. Keep it in a list for later change reporting if it doesn't get removed again
       * before the end of the pass.
       */
      maybeTemporarilyLiveNodes.add(expr);

      JSModule module = t.getModule();
      registerAnyProvidedPrefixes(name, expr, module);

      // If registerAnyProvidedPrefixes didn't add any children, add a no-op child so that
      // the AST is valid.
      if (!expr.hasChildren()) {
        expr.addChildToBack(NodeUtil.newUndefinedNode(parent));
      }

      ProvidedName provided = new ProvidedName(name, expr, module, true);
      providedNames.put(name, provided);
      provided.addDefinition(parent, module);
    } else {
      // Remove this provide if it came from a previous pass since we have an
      // replacement already.
      if (isNamespacePlaceholder(parent)) {
        compiler.reportChangeToEnclosingScope(parent);
        parent.detach();
      }
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

  /** Process a goog.forwardDeclare() call and record the specified forward declaration. */
  private void processForwardDeclare(Node n, Node parent) {
    CodingConvention convention = compiler.getCodingConvention();

    String typeDeclaration = null;
    try {
      typeDeclaration = Iterables.getOnlyElement(convention.identifyTypeDeclarationCall(n));
    } catch (NullPointerException | NoSuchElementException | IllegalArgumentException e) {
      compiler.report(
          JSError.make(
              n,
              ProcessClosurePrimitives.INVALID_FORWARD_DECLARE,
              "A single type could not identified for the goog.forwardDeclare statement"));
    }

    if (typeDeclaration != null) {
      compiler.forwardDeclareType(typeDeclaration);
      // Forward declaration was recorded and we can remove the call.
      Node toRemove = parent.isExprResult() ? parent : parent.getParent();
      NodeUtil.deleteNode(toRemove, compiler);
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
        providedNames.get(prefixNs).addProvide(node, module, false /* implicit */);
      } else {
        providedNames.put(prefixNs, new ProvidedName(prefixNs, node, module, false /* implicit */));
      }
    }
  }

  // -------------------------------------------------------------------------

  /** Information required to replace a goog.provide call later in the traversal. */
  private class ProvidedName {
    private final String namespace;

    // The node and module where the call was explicitly or implicitly
    // goog.provided.
    private final Node firstNode;
    private final JSModule firstModule;

    // The node where the call was explicitly goog.provided. May be null
    // if the namespace is always provided implicitly.
    private Node explicitNode = null;
    private JSModule explicitModule = null;

    // There are child namespaces of this one.
    private boolean hasAChildNamespace = false;

    // The candidate definition.
    private Node candidateDefinition = null;

    // The minimum module where the provide must appear.
    private JSModule minimumModule = null;

    // The replacement declaration.
    private Node replacementNode = null;

    ProvidedName(String namespace, Node node, JSModule module, boolean explicit) {
      Preconditions.checkArgument(node == null /* The base case */ || node.isExprResult());
      this.namespace = namespace;
      this.firstNode = node;
      this.firstModule = module;

      addProvide(node, module, explicit);
    }

    /** Add an implicit or explicit provide. */
    void addProvide(Node node, JSModule module, boolean explicit) {
      if (explicit) {
        // goog.provide('name.space');
        checkState(explicitNode == null);
        checkArgument(node.isExprResult());
        explicitNode = node;
        explicitModule = module;
      } else {
        // goog.provide('name.space.some.child');
        hasAChildNamespace = true;
      }
      updateMinimumModule(module);
    }

    boolean isExplicitlyProvided() {
      return explicitNode != null;
    }

    boolean isFromExterns() {
      return explicitNode.isFromExterns();
    }

    /**
     * Record function declaration, variable declaration or assignment that refers to the same name
     * as the provide statement. Give preference to declarations; if no declaration exists, record a
     * reference to an assignment so it repurposed later.
     */
    void addDefinition(Node node, JSModule module) {
      Preconditions.checkArgument(
          node.isExprResult() // assign
              || node.isFunction()
              || NodeUtil.isNameDeclaration(node));
      checkArgument(explicitNode != node);
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
    void replace() {
      if (firstNode == null) {
        // Don't touch the base case ('goog').
        replacementNode = candidateDefinition;
        return;
      }

      // Handle the case where there is a duplicate definition for an explicitly
      // provided symbol.
      if (candidateDefinition != null && explicitNode != null) {
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
            // namespace = value;
            candidateDefinition.putBooleanProp(Node.IS_NAMESPACE, true);
            Node nameNode = exprNode.getFirstChild();
            if (nameNode.isName()) {
              // Need to convert this assign to a var declaration.
              Node valueNode = nameNode.getNext();
              exprNode.removeChild(nameNode);
              exprNode.removeChild(valueNode);
              nameNode.addChildToFront(valueNode);
              Node varNode = IR.var(nameNode);
              varNode.useSourceInfoFrom(candidateDefinition);
              candidateDefinition.replaceWith(varNode);
              varNode.setJSDocInfo(exprNode.getJSDocInfo());
              compiler.reportChangeToEnclosingScope(varNode);
              replacementNode = varNode;
            }
          } else {
            // /** @typedef {something} */ name.space.Type;
            checkState(exprNode.isQualifiedName(), exprNode);
            // If this namespace has child namespaces, we still need to add an object to hang them
            // on to avoid creating broken code.
            // We must cast the type of the literal to unknown, because the type checker doesn't
            // expect the namespace to have a value.
            if (hasAChildNamespace) {
              replaceWith(
                  createDeclarationNode(IR.cast(IR.objectlit(), createUnknownTypeJsDocInfo())));
            }
          }
        }
      } else {
        // Handle the case where there's not a duplicate definition.
        replaceWith(createDeclarationNode(IR.objectlit()));
      }
      if (explicitNode != null) {
        if (preserveGoogProvidesAndRequires && explicitNode.hasChildren()) {
          return;
        }
        /*
         * If 'explicitNode' was added earlier in this pass then don't bother to report its removal
         * right here as a change (since the original AST state is being restored). Also remove
         * 'explicitNode' from the list of "possibly live" nodes so that it does not get reported as
         * a change at the end of the pass.
         */
        if (!maybeTemporarilyLiveNodes.remove(explicitNode)) {
          compiler.reportChangeToEnclosingScope(explicitNode);
        }
        explicitNode.detach();
      }
    }

    private void replaceWith(Node replacement) {
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

    /** Create the declaration node for this name, without inserting it into the AST. */
    private Node createDeclarationNode(Node value) {
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
      if (candidateDefinition == null) {
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
      if (candidateDefinition == null) {
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
  }

  private JSDocInfo createUnknownTypeJsDocInfo() {
    JSDocInfoBuilder castToUnknownBuilder = new JSDocInfoBuilder(true);
    castToUnknownBuilder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("?"), "<ProcessClosurePrimitives.java>"));
    return castToUnknownBuilder.build();
  }

  /** @return Whether the node is namespace placeholder. */
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

  // -------------------------------------------------------------------------

  /**
   * Information required to create a {@link ProcessClosurePrimitives#MISSING_PROVIDE_ERROR}
   * warning.
   */
  private static class UnrecognizedRequire {
    final Node requireNode;
    final String namespace;
    final boolean isRequireType;

    UnrecognizedRequire(Node requireNode, String namespace, boolean isRequireType) {
      this.requireNode = requireNode;
      this.namespace = namespace;
      this.isRequireType = isRequireType;
    }
  }
}
