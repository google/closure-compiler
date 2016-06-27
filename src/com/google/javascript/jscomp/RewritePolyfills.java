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

import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES6;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Injects polyfill libraries to ensure that ES6 library functions are available.
 */
public class RewritePolyfills implements HotSwapCompilerPass {

  static final DiagnosticType INSUFFICIENT_OUTPUT_VERSION_ERROR = DiagnosticType.warning(
      "JSC_INSUFFICIENT_OUTPUT_VERSION",
      "Built-in ''{0}'' not supported in output version {1}: set --language_out to at least {2}");

  // Also polyfill references to e.g. goog.global.Map or window.Map.
  private static final String GLOBAL = "goog.global.";
  private static final String WINDOW = "window.";

  /**
   * Represents a single polyfill: specifically, for a native symbol
   * (not part of this object, but stored as the key to the map
   * containing the Polyfill instance), a set of native and polyfill
   * versions, and a library to ensure is injected if the output version
   * is less than the native version.  This is a simple value type.
   */
  private static class Polyfill {
    /**
     * The language version at (or above) which the native symbol is
     * available and sufficient.  If the language out flag is at least
     * as high as {@code nativeVersion} then no rewriting will happen.
     */
    final FeatureSet nativeVersion;

    /**
     * The required language version for the polyfill to work.  This
     * should not be higher than {@code nativeVersion}, but may be the same
     * in cases where there is no polyfill provided.  This is used to
     * emit a warning if the language out flag is too low.
     */
    final FeatureSet polyfillVersion;

    /**
     * Runtime library to inject for the polyfill, e.g. "es6/map".
     */
    final String library;

    Polyfill(FeatureSet nativeVersion, FeatureSet polyfillVersion, String library) {
      this.nativeVersion = nativeVersion;
      this.polyfillVersion = polyfillVersion;
      this.library = library;
    }
  }

  /**
   * Describes all the available polyfills, including native and
   * required versions, and how to use them.
   */
  static class Polyfills {
    // Map of method polyfills, keyed by native method name.
    private final ImmutableMultimap<String, Polyfill> methods;
    // Map of static polyfills, keyed by fully-qualified native name.
    private final ImmutableMap<String, Polyfill> statics;

    private Polyfills(
        ImmutableMultimap<String, Polyfill> methods, ImmutableMap<String, Polyfill> statics) {
      this.methods = methods;
      this.statics = statics;
    }

    /**
     * Builds a Polyfills instance from a polyfill table, which is a simple
     * text file with lines containing space-separated tokens:
     *   [NATIVE_SYMBOL] [NATIVE_VERSION] [POLYFILL_VERSION] [LIBRARY]
     * For example,
     *   Array.prototype.fill es6-impl es3 es6/array/fill
     *   Map es6-impl es3 es6/map
     *   WeakMap es6-impl es6-impl
     * The last line, WeakMap, does not have a polyfill available, so the
     * library token is empty.
     */
    static Polyfills fromTable(String table) {
      ImmutableMultimap.Builder<String, Polyfill> methods = ImmutableMultimap.builder();
      ImmutableMap.Builder<String, Polyfill> statics = ImmutableMap.builder();
      for (String line : Splitter.on('\n').omitEmptyStrings().split(table)) {
        List<String> tokens = Splitter.on(' ').omitEmptyStrings().splitToList(line.trim());
        if (tokens.size() == 1 && tokens.get(0).isEmpty()) {
          continue;
        } else if (tokens.size() < 3) {
          throw new IllegalArgumentException("Invalid table: too few tokens on line: " + line);
        }
        String symbol = tokens.get(0);
        Polyfill polyfill =
            new Polyfill(
                FeatureSet.valueOf(tokens.get(1)),
                FeatureSet.valueOf(tokens.get(2)),
                tokens.size() > 3 ? tokens.get(3) : "");
        if (symbol.contains(".prototype.")) {
          methods.put(symbol.replaceAll(".*\\.prototype\\.", ""), polyfill);
        } else {
          statics.put(symbol, polyfill);
          statics.put(GLOBAL + symbol, polyfill);
          statics.put(WINDOW + symbol, polyfill);
        }
      }
      return new Polyfills(methods.build(), statics.build());
    }
  }

  private final AbstractCompiler compiler;
  private final Polyfills polyfills;

  public RewritePolyfills(AbstractCompiler compiler) {
    this(
        compiler,
        Polyfills.fromTable(
            ResourceLoader.loadTextResource(RewritePolyfills.class, "js/polyfills.txt")));
  }

  // Visible for testing
  RewritePolyfills(AbstractCompiler compiler, Polyfills polyfills) {
    this.compiler = compiler;
    this.polyfills = polyfills;
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    Traverser traverser = new Traverser();
    NodeTraversal.traverseEs6(compiler, scriptRoot, traverser);

    if (!traverser.libraries.isEmpty()) {
      Node lastNode = null;
      for (String library : traverser.libraries) {
        lastNode = compiler.ensureLibraryInjected(library, false);
      }
      if (lastNode != null) {
        removeUnneededPolyfills(lastNode.getParent(), lastNode);
      }
      compiler.reportCodeChange();
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
    if (languageOutIsAtLeast(ES6) || !compiler.getOptions().rewritePolyfills) {
      return; // no rewriting in this case.
    }
    hotSwapScript(root, null);
  }

  private class Traverser extends AbstractPostOrderCallback {

    final Set<String> libraries = new LinkedHashSet<>();

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {

      // Find qualified names that match static calls
      if (node.isQualifiedName()) {
        String name = node.getQualifiedName();
        Polyfill polyfill = null;

        if (polyfills.statics.containsKey(name)) {
          polyfill = polyfills.statics.get(name);
        }

        if (polyfill != null) {
          // Check the scope to make sure it's a global name.
          if (isRootInScope(node, traversal) || NodeUtil.isVarOrSimpleAssignLhs(node, parent)) {
            return;
          }

          if (!languageOutIsAtLeast(polyfill.polyfillVersion)) {
            traversal.report(
                node,
                INSUFFICIENT_OUTPUT_VERSION_ERROR,
                name,
                compiler.getOptions().getLanguageOut().toString(),
                polyfill.polyfillVersion.toLanguageModeString());
          }
          inject(polyfill);

          // TODO(sdh): consider warning if language_in is too low?  it's not really any
          // harm, and we can't do it consistently for the prototype methods, so maybe
          // it's not worth doing here, either.

          return; // isGetProp (below) overlaps, so just bail out now
        }
      }

      // Inject anything that *might* match method calls - these may be removed later.
      if (node.isGetProp() && node.getLastChild().isString()) {
        Collection<Polyfill> methods = polyfills.methods.get(node.getLastChild().getString());
        if (!methods.isEmpty() && !isStaticFunction(node, traversal)) {
          for (Polyfill polyfill : methods) {
            inject(polyfill);
          }
          // NOTE(sdh): To correctly support IE8, we would need to rewrite the call site to
          // e.g. $jscomp.method(foo, 'bar').call or $jscomp.call(foo, 'bar', ...args),
          // which would be defined in the runtime to first check for existence (note that
          // this means we can't rename that property) and then fall back on a map of
          // polyfills populated by $jscomp.polyfill.  This means we'd need a later
          // version of this compiler pass, since the rewrite should ideally happen after
          // typechecking (so that the rewrite doesn't mess it up, and we can also optionally
          // not do it).  For now we will pass on this, until we see concrete need.  Note that
          // this will not work at all in uncompiled mode, so this may be a non-starter.
        }
      }
    }

    private boolean isStaticFunction(Node node, NodeTraversal traversal) {
      if (!node.isQualifiedName()) {
        return false;
      }
      String qname = node.getQualifiedName();
      return qname.startsWith("goog.string") || qname.startsWith("goog.array");
    }

    private void inject(Polyfill polyfill) {
      if (!languageOutIsAtLeast(polyfill.nativeVersion) && !polyfill.library.isEmpty()) {
        libraries.add(polyfill.library);
      }
    }
  }

  private boolean languageOutIsAtLeast(LanguageMode mode) {
    return compiler.getOptions().getLanguageOut().compareTo(mode) >= 0;
  }

  private boolean languageOutIsAtLeast(FeatureSet features) {
    switch (features.version()) {
      case "ts":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT6_TYPED);
      case "es6":
      case "es6-impl": // TODO(sdh): support a separate language mode for es6-impl?
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT6);
      case "es5":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT5);
      case "es3":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT3);
      default:
        return false;
    }
  }

  private static boolean isRootInScope(Node node, NodeTraversal traversal) {
    String rootName = NodeUtil.getRootOfQualifiedName(node).getQualifiedName();
    return traversal.getScope().getVar(rootName) != null;
  }
}
