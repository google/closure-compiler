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

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.resources.ResourceLoader;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Injects polyfill libraries to ensure that ES6 library functions are available.
 */
public class RewritePolyfills implements HotSwapCompilerPass {

  static final DiagnosticType INSUFFICIENT_OUTPUT_VERSION_ERROR = DiagnosticType.disabled(
      "JSC_INSUFFICIENT_OUTPUT_VERSION",
      "Built-in ''{0}'' not supported in output version {1}");

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
    // Set of suffixes of qualified names.
    private final ImmutableSet<String> suffixes;

    private Polyfills(
        ImmutableMultimap<String, Polyfill> methods, ImmutableMap<String, Polyfill> statics) {
      this.methods = methods;
      this.statics = statics;
      this.suffixes = ImmutableSet.copyOf(Iterables.transform(statics.keySet(), EXTRACT_SUFFIX));
    }

    /**
     * Builds a Polyfills instance from a polyfill table, which is a simple
     * text file with lines containing space-separated tokens:
     *   [NATIVE_SYMBOL] [NATIVE_VERSION] [POLYFILL_VERSION] [LIBRARY]
     * For example,
     *   Array.prototype.fill es6 es3 es6/array/fill
     *   Map es6 es3 es6/map
     *   WeakMap es6 es6
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
        }
      }
      return new Polyfills(methods.build(), statics.build());
    }

    /**
     * Given a qualified name {@code node}, checks whether the suffix
     * of the name could possibly match a static polyfill.
     */
    boolean checkSuffix(Node node) {
      return node.isGetProp() ? suffixes.contains(node.getLastChild().getString())
          : node.isName() ? suffixes.contains(node.getString())
          : false;
    }

    private static final Function<String, String> EXTRACT_SUFFIX =
        new Function<String, String>() {
          @Override
          public String apply(String arg) {
            return arg.substring(arg.lastIndexOf('.') + 1);
          }
        };
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
    NodeTraversal.traverse(compiler, scriptRoot, traverser);

    if (!traverser.libraries.isEmpty()) {
      Node lastNode = null;
      for (String library : traverser.libraries) {
        lastNode = compiler.ensureLibraryInjected(library, false);
      }
      if (lastNode != null) {
        Node parent = lastNode.getParent();
        removeUnneededPolyfills(parent, lastNode.getNext());
        compiler.reportChangeToEnclosingScope(parent);
      }
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

  private class Traverser extends GuardedCallback<String> {

    final Set<String> libraries = new LinkedHashSet<>();

    Traverser() {
      super(compiler);
    }

    @Override
    public void visitGuarded(NodeTraversal traversal, Node node, Node parent) {
      // Find qualified names that match static calls
      if (node.isQualifiedName() && polyfills.checkSuffix(node)) {
        String name = node.getQualifiedName();

        // TODO(sdh): We could reduce some work here by combining the global names
        // check with the root-in-scope check but it's not clear how to do so and
        // still keep the var lookup *after* the polyfill-existence check.
        boolean isExplicitGlobal = false;
        for (String global : GLOBAL_NAMES) {
          if (name.startsWith(global)) {
            name = name.substring(global.length());
            isExplicitGlobal = true;
            break;
          }
        }

        // If the name is known, then make sure it's either explicitly or implicitly global.
        Polyfill polyfill = polyfills.statics.get(name);
        if (polyfill != null && !isExplicitGlobal && isRootInScope(node, traversal)) {
          polyfill = null;
        }

        if (polyfill != null && !isGuarded(name)) {
          if (!languageOutIsAtLeast(polyfill.polyfillVersion)) {
            traversal.report(
                node,
                INSUFFICIENT_OUTPUT_VERSION_ERROR,
                name,
                compiler.getOptions().getOutputFeatureSet().version());
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
        String name = node.getLastChild().getString();
        Collection<Polyfill> methods = polyfills.methods.get(name);
        if (!methods.isEmpty() && !isGuarded("." + name)) {
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
      return;
    }

    private void inject(Polyfill polyfill) {
      if (!languageOutIsAtLeast(polyfill.nativeVersion) && !polyfill.library.isEmpty()) {
        libraries.add(polyfill.library);
      }
    }
  }

  private static final ImmutableSet<String> GLOBAL_NAMES =
      ImmutableSet.of("goog.global.", "window.");

  private boolean languageOutIsAtLeast(LanguageMode mode) {
    return compiler.getOptions().getOutputFeatureSet().contains(mode.toFeatureSet());
  }

  private boolean languageOutIsAtLeast(FeatureSet features) {
    switch (features.version()) {
      case "ts":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT6_TYPED);
      case "es9":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT_2018);
      case "es8":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT_2017);
      case "es7":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT_2016);
      case "es6":
      case "es6-impl": // TODO(sdh): support a separate language mode for es6-impl?
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT_2015);
      case "es5":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT5);
      case "es3":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT3);
      default:
        return false;
    }
  }

  private static boolean isRootInScope(Node node, NodeTraversal traversal) {
    Node root = NodeUtil.getRootOfQualifiedName(node);
    // NOTE: `this` and `super` are always considered "in scope" and thus shouldn't be polyfilled.
    return !root.isName() || traversal.getScope().getVar(root.getString()) != null;
  }
}
