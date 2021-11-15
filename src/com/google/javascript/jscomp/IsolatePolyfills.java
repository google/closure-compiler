/*
 * Copyright 2020 The Closure Compiler Authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfill;
import com.google.javascript.jscomp.PolyfillUsageFinder.PolyfillUsage;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfills;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.resources.ResourceLoader;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rewrites potential polyfill usages to use the hidden JSCompiler polyfills instead of the global.
 *
 * <p>When $jscomp.ISOLATE_POLYFILLS is enabled, the $jscomp.polyfill library function does not add
 * polyfills to the global scope or as properties on the native type. Instead, classes like Map are
 * added to the $jscomp.polyfills object. Methods like `String.prototype.includes` are defined under
 * a unique Symbol on String.prototype.
 *
 * <p>This pass rewrites polyfill usages so that they access the actual polyfills instead of trying
 * to access the native types. For example, <code>new Map()</code> becomes <code>
 * new $jscomp.polyfills['Map']</code>.
 *
 * <p>Limitations of this pass: a) it ignores destructuring and b) it does not support rewriting
 * writes to polyfilled methods.
 */
class IsolatePolyfills implements CompilerPass {

  private final AbstractCompiler compiler;
  private final Polyfills polyfills;
  private final Node jscompPolyfillsObject;

  private static final String POLYFILL_TEMP = "$jscomp$polyfillTmp";
  private final Node jscompLookupMethod = IR.name("$jscomp$lookupPolyfilledValue");

  private boolean usedPolyfillMethodLookup = false;
  private boolean isTempVarInitialized = false;

  IsolatePolyfills(AbstractCompiler compiler) {
    this(
        compiler,
        Polyfills.fromTable(
            ResourceLoader.loadTextResource(IsolatePolyfills.class, "js/polyfills.txt")));
  }

  @VisibleForTesting
  IsolatePolyfills(AbstractCompiler compiler, Polyfills polyfills) {
    this.compiler = compiler;
    this.polyfills = polyfills;
    boolean hasPropertyCollapsingRun =
        compiler.getOptions().getPropertyCollapseLevel().equals(PropertyCollapseLevel.ALL);
    jscompPolyfillsObject =
        hasPropertyCollapsingRun ? createCollapsedName() : createJSCompPolyfillsAccess();
  }

  /** Returns a name `$jscomp$polyfills` */
  private static Node createCollapsedName() {
    Node collapsedName = IR.name("$jscomp$polyfills");
    collapsedName.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    return collapsedName;
  }

  /** Returns a getprop `$jscomp.polyfills` */
  private static Node createJSCompPolyfillsAccess() {
    Node jscomp = IR.name("$jscomp");
    jscomp.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    return IR.getprop(jscomp, "polyfills");
  }

  @Override
  public void process(Node externs, Node root) {
    // Calculate the set of polyfills that are actually present in the AST. It may be a subset of
    // the potential polyfills which PolyfillFindingCallback finds (it's fine if it's a superset.)
    Set<String> injectedPolyfills = findAllInjectedPolyfills();

    List<PolyfillUsage> polyfillUsages = new ArrayList<>();
    new PolyfillUsageFinder(compiler, this.polyfills)
        .traverseIncludingGuarded(root, polyfillUsages::add);

    LinkedHashSet<Node> visitedNodes = new LinkedHashSet<>();
    for (PolyfillUsage usage : polyfillUsages) {
      if (
      // Some nodes map to more than one polyfill usage. For example, `x.includes` maps to both
      // Array.prototype.includes and String.prototype.includes, but only needs to be isolated once.
      visitedNodes.contains(usage.node())
          // Skip visiting nodes whose 'polyfill.library' is empty. This is true for language
          // features like `Proxy` and `String.raw` that have no associated polyfill, and hence are
          // unnecessary to isolate.
          || usage.polyfill().library.isEmpty()
          // The PolyfillFindingCallback may detect possible polyfill usages that are not
          // in fact injected. (possibly because RemoveUnusedCode deleted the polyfill.)
          || !injectedPolyfills.contains(usage.polyfill().nativeSymbol)) {
        continue;
      }
      this.rewritePolyfill(usage);
      visitedNodes.add(usage.node());
    }

    cleanUpJscompLookupPolyfilledValue();
  }

  /**
   * Searches the AST for all calls to $jscomp.polyfill and returns the polyfilled symbol names.
   *
   * <p>At the moment we don't track anywhere the set of all polyfills that have been injected. That
   * set may be modified by RewritePolyfills, Es6InjectRuntimeLibraries, and RemoveUnusedCode. If
   * desired, we could delete this method by making any passes that add/remove polyfill calls
   * responsible for tracking their presence.
   *
   * <p>Note: this set cannot be injected into the constructor because it is not known until the
   * polyfill injection pass actually runs.
   */
  private ImmutableSet<String> findAllInjectedPolyfills() {
    ImmutableSet.Builder<String> actualPolyfills = ImmutableSet.builder();

    Node lastInjectedNode = compiler.getNodeForCodeInsertion(null);

    NodeTraversal.traverse(
        compiler,
        lastInjectedNode,
        new NodeTraversal.AbstractShallowCallback() {
          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (isJSCompPolyfillCall(n)) {
              // CALL
              //  GETPROP/NAME $jscomp.polyfill
              //  STRING NativeSymbol.prototype.method
              //  [...]
              String polyfilledSymbol = n.getSecondChild().getString();
              actualPolyfills.add(polyfilledSymbol);
            }
          }
        });

    return actualPolyfills.build();
  }

  private boolean isJSCompPolyfillCall(Node call) {
    if (!call.isCall()) {
      return false;
    }
    String jscompPolyfillName =
        compiler.getOptions().getPropertyCollapseLevel().equals(PropertyCollapseLevel.ALL)
            ? "$jscomp$polyfill"
            : "$jscomp.polyfill";
    return call.getFirstChild().matchesQualifiedName(jscompPolyfillName);
  }

  /**
   * Rewrites a potential access of a polyfilled class or method to first look for the non-global,
   * polyfilled version.
   */
  private void rewritePolyfill(PolyfillUsage polyfillUsage) {
    final Polyfill polyfill = polyfillUsage.polyfill();

    // If the output FeatureSet includes all of the features of the language version in which
    // the polyfilled symbol was introduced, we can assume that the intended platform has
    // the symbol defined natively, so the compiler won't include the polyfill in its output and it
    // won't need to be isolated.
    if (compiler
        .getOptions()
        .getOutputFeatureSet()
        .contains(FeatureSet.valueOf(polyfill.nativeVersion))) {
      return;
    }

    final Node polyfillAccess = polyfillUsage.node();
    final Node parent = polyfillUsage.node().getParent();

    if (FILES_ALLOWED_UNQUALIFIED_POLYFILL_ACCESSES.contains(polyfillAccess.getSourceFileName())) {
      // Special-case code in the compiler runtime libraries that executes before any polyfills are
      // injected.
      //   - the definition of $jscomp.global needs to look for a global variable globalThis
      //   - the $jscomp.polyfill function needs to check whether Symbol is native
      // If desired we could programatically detect these cases instead of having this allowlist of
      // polyfill accesses, but that might silently allow other usages of third-party polyfills
      // into the codebase.
      // TODO(b/156776817): crash on early references to polyfills that are not in our allowlist.
      return;
    }

    // For now we need to assume that these lvalues are unrelated to the polyfills, as we are not
    // using any type information. If we wanted, we could support assignments to properties that are
    // already present, e.g.
    //   Array.includes = intercept;
    // to
    //   tmp = Array;
    //   tmp[$maybePolyfillProp(tmp, 'includes)] = intercept;
    if (NodeUtil.isLValue(polyfillAccess)
        || (parent.isAssign() && polyfillAccess.isFirstChildOf(parent))) {
      return;
    }

    final String name = polyfillUsage.name();
    boolean isGlobalClass = name.indexOf('.') == -1 && polyfill.kind.equals(Polyfill.Kind.STATIC);

    if (isGlobalClass) {
      // e.g. Symbol, Map, window.Map, or goog.global.Map
      polyfillAccess.replaceWith(
          IR.getelem(jscompPolyfillsObject.cloneTree(), IR.string(name)) // $jscomp.polyfills['Map']
              .srcrefTree(polyfillAccess));
    } else if (parent.isCall() && polyfillAccess.isFirstChildOf(parent)) {
      // e.g. getStr().includes('x')
      rewritePolyfillInCall(polyfillAccess);
    } else {
      // e.g. [].includes.call(myIter, 0)
      Node methodName = IR.string(polyfillAccess.getString()).srcref(polyfillAccess);
      Node receiver = polyfillAccess.removeFirstChild();
      polyfillAccess.replaceWith(
          createPolyfillMethodLookup(receiver, methodName).srcrefTree(polyfillAccess));
    }

    compiler.reportChangeToEnclosingScope(parent);
  }

  private static final ImmutableSet<String> FILES_ALLOWED_UNQUALIFIED_POLYFILL_ACCESSES =
      ImmutableSet.of(
          AbstractCompiler.RUNTIME_LIB_DIR + "util/global.js",
          AbstractCompiler.RUNTIME_LIB_DIR + "util/shouldpolyfill.js");

  /**
   * Rewrites a call where the receiver is a potential polyfilled method
   *
   * <p>Before: <code>receiver.method(arg)</code>
   *
   * <p>After: <code>lookupPolyfilledValue(receiver, 'method').call(receiver, arg)</code>
   *
   * <p>Or, if evaluating the receiver may have side effects, we store the receiver in a temporary
   * variable to avoid evaluating it twice:
   *
   * <p>After: <code>(tmpNode = receiver, lookupPolyfilledValue(tmpNode, 'method'))
   *                      .call(tmpNode, arg)</code>
   */
  private void rewritePolyfillInCall(Node callee) {
    final Node methodName = IR.string(callee.getString()).srcref(callee);
    final Node receiver = callee.removeFirstChild();

    boolean requiresTemp = compiler.getAstAnalyzer().mayEffectMutableState(receiver);

    final Node polyfilledMethod;
    final Node thisNode;

    if (requiresTemp) {
      // e.g. `sideEffects().includes(arg)`
      thisNode = createTempName(callee);
      // (tmpNode = sideEffects(), lookupMethod(tmpNode, 'includes'))
      polyfilledMethod =
          IR.comma(
              IR.assign(thisNode.cloneTree(), receiver),
              createPolyfillMethodLookup(thisNode.cloneTree(), methodName));
    } else {
      thisNode = receiver;
      polyfilledMethod = createPolyfillMethodLookup(receiver.cloneTree(), methodName);
    }

    // Fix the `this` type by using .call:
    //   lookupMethod(receiver, 'includes').call(receiver, arg)
    Node receiverDotCall = IR.getprop(polyfilledMethod, "call").srcrefTree(callee);
    callee.replaceWith(receiverDotCall);
    thisNode.insertAfter(receiverDotCall);
  }

  private Node createTempName(Node srcref) {
    if (!isTempVarInitialized) {
      isTempVarInitialized = true;
      Node decl = IR.var(IR.name(POLYFILL_TEMP)).srcrefTree(srcref);
      compiler.getNodeForCodeInsertion(null).addChildToFront(decl);
    }
    // The same temporary variable is always used for every polyfill invocation. This is believed
    // to be safe and makes the code easier to generate and smaller. There's a change it will make
    // it harder for V8 to optimize, though. If proves to be a problem we could introduce unique tmp
    // variables.
    return IR.name(POLYFILL_TEMP).srcref(srcref);
  }

  /** Returns a call <code>$jscomp$lookupPolyfilledValue(receiver, 'methodName')</code> */
  private Node createPolyfillMethodLookup(Node receiver, Node methodName) {
    usedPolyfillMethodLookup = true;
    Node call = IR.call(jscompLookupMethod.cloneTree(), receiver, methodName);
    call.putBooleanProp(Node.FREE_CALL, true);
    return call;
  }

  /**
   * Deletes the dummy externs declaration of $jscomp$lookupPolyfilledValue and the function itself
   * if unused.
   *
   * <p>The RewritePolyfills pass injected a definition of $jscomp$lookupPolyfilledValue into the
   * externs. This prevented dead code elimination, since the function is never unused until this
   * pass runs. However, now we need to delete the externs definition so that variable renaming can
   * actually rename $jscomp$lookupPolyfilledValue.
   */
  private void cleanUpJscompLookupPolyfilledValue() {
    Node syntheticExternsRoot = compiler.getSynthesizedExternsInput().getAstRoot(compiler);
    Scope syntheticExternsScope =
        new SyntacticScopeCreator(compiler).createScope(syntheticExternsRoot, /* parent= */ null);
    Var externVar =
        checkNotNull(
            syntheticExternsScope.getVar(jscompLookupMethod.getString()),
            "Failed to find synthetic $jscomp$lookupPolyfilledValue extern");
    NodeUtil.deleteNode(externVar.getParentNode(), compiler);

    if (usedPolyfillMethodLookup) {
      return;
    }

    Scope syntheticCodeScope =
        new SyntacticScopeCreator(compiler)
            .createScope(compiler.getNodeForCodeInsertion(/* module= */ null), /* parent= */ null);
    Var syntheticVar = syntheticCodeScope.getVar(jscompLookupMethod.getString());
    // Don't error if we can't find a definition for jscompLookupMethod. It's possible that we are
    // running in transpileOnly mode and are not injecting runtime libraries.
    if (syntheticVar != null) {
      NodeUtil.deleteNode(syntheticVar.getParentNode(), compiler);
    }
  }
}
