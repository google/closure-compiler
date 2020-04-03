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
import com.google.javascript.jscomp.PolyfillFindingCallback.Polyfill;
import com.google.javascript.jscomp.PolyfillFindingCallback.PolyfillUsage;
import com.google.javascript.jscomp.PolyfillFindingCallback.Polyfills;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.resources.ResourceLoader;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Injects polyfill libraries to ensure that ES6 library functions are available.
 */
public class RewritePolyfills implements HotSwapCompilerPass {

  static final DiagnosticType INSUFFICIENT_OUTPUT_VERSION_ERROR = DiagnosticType.disabled(
      "JSC_INSUFFICIENT_OUTPUT_VERSION",
      "Built-in ''{0}'' not supported in output version {1}");

  private final AbstractCompiler compiler;
  private final Polyfills polyfills;
  private final boolean isolatePolyfills;
  private Set<String> libraries;

  public RewritePolyfills(AbstractCompiler compiler, boolean isolatePolyfills) {
    this(
        compiler,
        Polyfills.fromTable(
            ResourceLoader.loadTextResource(RewritePolyfills.class, "js/polyfills.txt")),
        isolatePolyfills);
  }

  @VisibleForTesting
  RewritePolyfills(AbstractCompiler compiler, Polyfills polyfills, boolean isolatePolyfills) {
    this.compiler = compiler;
    this.polyfills = polyfills;
    this.isolatePolyfills = isolatePolyfills;
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    this.libraries = new LinkedHashSet<>();
    new PolyfillFindingCallback(compiler, polyfills).traverse(scriptRoot, this::inject);

    if (this.isolatePolyfills) {
      // Polyfill isolation requires a pass to run near the end of optimizations. That pass may call
      // into a library method injected in this pass. Adding an externs declaration of that library
      // method prevents it from being dead-code-elimiated before polyfill isolation runs.
      Node jscompLookupMethodDecl = IR.var(IR.name("$jscomp$lookupPolyfilledValue"));
      compiler
          .getSynthesizedExternsInputAtEnd()
          .getAstRoot(compiler)
          .addChildToBack(jscompLookupMethodDecl);
    }

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
    while (node != null && node != runtimeEnd) {
      Node next = node.getNext();
      if (NodeUtil.isExprCall(node)) {
        Node call = node.getFirstChild();
        Node name = call.getFirstChild();
        if (name.matchesQualifiedName("$jscomp.polyfill")) {
          FeatureSet nativeVersion =
              FeatureSet.valueOf(name.getNext().getNext().getNext().getString());
          if (languageOutIsAtLeast(nativeVersion)) {
            NodeUtil.removeChild(parent, node);
          }
        }
      }
      node = next;
    }
  }

  @Override
  public void process(Node externs, Node root) {
    hotSwapScript(root, null);
  }

  private void inject(PolyfillUsage polyfillUsage) {
    Polyfill polyfill = polyfillUsage.polyfill();
    if (polyfill.kind.equals(Polyfill.Kind.STATIC)
        && !languageOutIsAtLeast(polyfill.polyfillVersion)) {
      compiler.report(
          JSError.make(
              polyfillUsage.node(),
              INSUFFICIENT_OUTPUT_VERSION_ERROR,
              polyfillUsage.name(),
              compiler.getOptions().getOutputFeatureSet().version()));
    }

    if (!languageOutIsAtLeast(polyfill.nativeVersion) && !polyfill.library.isEmpty()) {
      libraries.add(polyfill.library);
    }
  }

  private boolean languageOutIsAtLeast(FeatureSet featureSet) {
    return PolyfillFindingCallback.languageOutIsAtLeast(
        featureSet, compiler.getOptions().getOutputFeatureSet());
  }
}
