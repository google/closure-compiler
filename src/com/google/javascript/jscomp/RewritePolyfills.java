/*
 * Copyright 2015 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfill;
import com.google.javascript.jscomp.PolyfillUsageFinder.PolyfillUsage;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfills;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.resources.ResourceLoader;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Injects polyfill libraries to ensure that ES6+ library functions are available.
 *
 * <p>Also runs if polyfill isolation is enabled, even if polyfill injection is disabled, in order
 * to prevent deletion of a required library function by dead code elimination.
 *
 * <p>TODO(b/120486392): consider merging this pass with {@link InjectRuntimeLibraries} and {@link
 * InjectTranspilationRuntimeLibraries}.
 */
public class RewritePolyfills implements CompilerPass {

  static final DiagnosticType INSUFFICIENT_OUTPUT_VERSION_ERROR =
      DiagnosticType.disabled(
          "JSC_INSUFFICIENT_OUTPUT_VERSION",
          "Built-in ''{0}'' not supported in output version {1}");

  private final AbstractCompiler compiler;
  private final Polyfills polyfills;
  private final boolean injectPolyfills;
  private final boolean isolatePolyfills;
  private Set<String> libraries;

  /**
   * @param injectPolyfills if true, injects $jscomp.polyfill initializations into the first input.
   *     if false, no polyfills are injected.
   * @param isolatePolyfills if true, adds externs for library functions used by {@link
   *     IsolatePolyfills} to prevent their deletion.
   */
  public RewritePolyfills(
      AbstractCompiler compiler, boolean injectPolyfills, boolean isolatePolyfills) {
    this(
        compiler,
        Polyfills.fromTable(
            ResourceLoader.loadTextResource(RewritePolyfills.class, "js/polyfills.txt")),
        injectPolyfills,
        isolatePolyfills);
  }

  @VisibleForTesting
  RewritePolyfills(
      AbstractCompiler compiler,
      Polyfills polyfills,
      boolean injectPolyfills,
      boolean isolatePolyfills) {
    this.compiler = compiler;
    this.polyfills = polyfills;
    this.injectPolyfills = injectPolyfills;
    this.isolatePolyfills = isolatePolyfills;
  }

  @Override
  public void process(Node externs, Node root) {
    if (this.isolatePolyfills) {
      // Polyfill isolation requires a pass to run near the end of optimizations. That pass may call
      // into a library method injected in this pass. Adding an externs declaration of that library
      // method prevents it from being dead-code-elimiated before polyfill isolation runs.
      Node jscompLookupMethodDecl = IR.var(IR.name("$jscomp$lookupPolyfilledValue"));
      final Node synthesizedExternsAstRoot =
          compiler.getSynthesizedExternsInput().getAstRoot(compiler);
      jscompLookupMethodDecl.srcrefTree(synthesizedExternsAstRoot);
      synthesizedExternsAstRoot.addChildToBack(jscompLookupMethodDecl);
      compiler.reportChangeToEnclosingScope(jscompLookupMethodDecl);
    }

    if (!this.injectPolyfills) {
      // Nothing left to do. Probably this pass only needed to run because --isolate_polyfills is
      // enabled but not --rewrite_polyfills.
      return;
    }

    this.libraries = new LinkedHashSet<>();
    new PolyfillUsageFinder(compiler, polyfills).traverseExcludingGuarded(root, this::inject);

    if (libraries.isEmpty()) {
      return;
    }

    Node lastNode = null;
    for (String library : libraries) {
      lastNode = compiler.ensureLibraryInjected(library, false);
    }
    if (lastNode != null) {
      Node parent = lastNode.getParent();
      removeUnneededPolyfills(parent, lastNode.getNext());
      compiler.reportChangeToEnclosingScope(parent);
    }
  }

  // Remove any $jscomp.polyfill calls whose 3rd parameter (the language version
  // that already contains the library) is the same or lower than languageOut.
  private void removeUnneededPolyfills(Node parent, Node runtimeEnd) {
    Node node = parent.getFirstChild();
    // The target environment is assumed to support the features in this set.
    final FeatureSet outputFeatureSet = compiler.getOptions().getOutputFeatureSet();
    while (node != null && node != runtimeEnd) {
      // look up the next node now, because we may be removing this one.
      Node next = node.getNext();
      FeatureSet polyfillSupportedFeatureSet = getPolyfillSupportedFeatureSet(node);
      if (polyfillSupportedFeatureSet != null
          && outputFeatureSet.contains(polyfillSupportedFeatureSet)) {
        NodeUtil.removeChild(parent, node);
        NodeUtil.markFunctionsDeleted(node, compiler);
      }
      node = next;
    }
  }

  /**
   * If the given `Node` is a polyfill definition, return the `FeatureSet` which should be
   * considered to already include that polyfill (making it unnecessary).
   *
   * <p>Otherwise, return `null`.
   */
  @Nullable
  private FeatureSet getPolyfillSupportedFeatureSet(Node maybePolyfill) {
    FeatureSet polyfillSupportFeatureSet = null;
    if (NodeUtil.isExprCall(maybePolyfill)) {
      Node call = maybePolyfill.getFirstChild();
      Node name = call.getFirstChild();
      if (name.matchesQualifiedName("$jscomp.polyfill")) {
        final String nativeVersionStr = name.getNext().getNext().getNext().getString();
        polyfillSupportFeatureSet = FeatureSet.valueOf(nativeVersionStr);
        // Safari has been really slow to implement these regex features, even though it has
        // kept on top of the features we polyfill, so we want to ignore the regex features
        // when deciding whether the polyfill should be considered "already supported" in the
        // target environment.
        // NOTE: This special case seems reasonable for now, but if further divergence occurs
        // we should consider doing a more direct solution by having the polyfill definitions
        // report names of `FeatureSet` values representing browser `FeatureSet` year instead of
        // spec release year.
        polyfillSupportFeatureSet =
            polyfillSupportFeatureSet.without(
                Feature.REGEXP_FLAG_S,
                Feature.REGEXP_LOOKBEHIND,
                Feature.REGEXP_NAMED_GROUPS,
                Feature.REGEXP_UNICODE_PROPERTY_ESCAPE);
      }
    }
    return polyfillSupportFeatureSet;
  }

  private void inject(PolyfillUsage polyfillUsage) {
    Polyfill polyfill = polyfillUsage.polyfill();
    final FeatureSet outputFeatureSet = compiler.getOptions().getOutputFeatureSet();
    final FeatureSet featuresRequiredByPolyfill = FeatureSet.valueOf(polyfill.polyfillVersion);
    if (polyfill.kind.equals(Polyfill.Kind.STATIC)
        && !outputFeatureSet.contains(featuresRequiredByPolyfill)) {
      compiler.report(
          JSError.make(
              polyfillUsage.node(),
              INSUFFICIENT_OUTPUT_VERSION_ERROR,
              polyfillUsage.name(),
              outputFeatureSet.version()));
    }

    // The question we want to ask here is:
    // "Does the target platform already have the symbol this polyfill provides?"
    // We approximate it by asking instead:
    // "Does the target platform support all of the features that existed in the language
    // version that introduced this symbol?"
    if (!outputFeatureSet.contains(FeatureSet.valueOf(polyfill.nativeVersion))
        && !polyfill.library.isEmpty()) {
      libraries.add(polyfill.library);
    }
  }
}
