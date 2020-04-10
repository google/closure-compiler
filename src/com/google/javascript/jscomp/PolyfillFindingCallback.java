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

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Detects all potential usages of polyfilled classes or methods */
final class PolyfillFindingCallback {

  /**
   * Represents a single polyfill: specifically, for a native symbol, a set of native and polyfill
   * versions, and a library to ensure is injected if the output version is less than the native
   * version.
   */
  static final class Polyfill {
    /** The full name of the polyfill, e.g `Map` or `String.prototype.includes` */
    final String nativeSymbol;

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

    final Kind kind;

    enum Kind {
      STATIC, // Map or Array.of
      METHOD // String.prototype.includes
    }

    Polyfill(
        String nativeSymbol,
        FeatureSet nativeVersion,
        FeatureSet polyfillVersion,
        String library,
        Kind kind) {
      this.nativeSymbol = nativeSymbol;
      this.nativeVersion = nativeVersion;
      this.polyfillVersion = polyfillVersion;
      this.library = library;
      this.kind = kind;
    }
  }

  /** Maps from polyfill names to the actual Polyfill object. */
  static final class Polyfills {
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
      this.suffixes =
          ImmutableSet.copyOf(
              statics.keySet().stream().map(EXTRACT_SUFFIX).collect(Collectors.toList()));
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
        boolean isPrototypeMethod = symbol.contains(".prototype.");
        Polyfill polyfill =
            new Polyfill(
                symbol,
                FeatureSet.valueOf(tokens.get(1)),
                FeatureSet.valueOf(tokens.get(2)),
                tokens.size() > 3 ? tokens.get(3) : "",
                isPrototypeMethod ? Polyfill.Kind.METHOD : Polyfill.Kind.STATIC);
        if (isPrototypeMethod) {
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
        arg -> arg.substring(arg.lastIndexOf('.') + 1);
  }

  @AutoValue
  abstract static class PolyfillUsage {
    abstract Polyfill polyfill();

    abstract Node node();

    abstract String name();

    abstract boolean isExplicitGlobal();

    private static PolyfillUsage create(
        Polyfill polyfill, Node node, String name, boolean isExplicitGlobal) {
      return new AutoValue_PolyfillFindingCallback_PolyfillUsage(
          polyfill, node, name, isExplicitGlobal);
    }
  }

  private final AbstractCompiler compiler;
  private final Polyfills polyfills;

  PolyfillFindingCallback(AbstractCompiler compiler, Polyfills polyfills) {
    this.polyfills = polyfills;
    this.compiler = compiler;
  }

  /**
   * Passes all polyfill usages found, in postorder, to the given polyfillConsumer
   *
   * <p>Excludes polyfill usages behind a guard, like {@code if (Promise) return
   * Promise.resolve('ok');}
   */
  void traverseExcludingGuarded(Node root, Consumer<PolyfillUsage> polyfillConsumer) {
    NodeTraversal.traverse(compiler, root, new Traverser(this.compiler, polyfillConsumer, false));
  }

  /**
   * Passes all polyfill usages found, in postorder, to the given polyfillConsumer
   *
   * <p>Includes polyfill usages that are behind a guard, like {@code if (Promise) return
   * Promise.resolve('ok');}
   */
  void traverseIncludingGuarded(Node root, Consumer<PolyfillUsage> polyfillConsumer) {
    NodeTraversal.traverse(compiler, root, new Traverser(this.compiler, polyfillConsumer, true));
  }

  private class Traverser extends GuardedCallback<String> {

    private final Consumer<PolyfillUsage> polyfillConsumer;
    // Whether to emit usages like Promise in `if (Promise) return Promise.resolve('ok');}`
    private final boolean includeGuardedUsages;

    Traverser(
        AbstractCompiler compiler,
        Consumer<PolyfillUsage> polyfillConsumer,
        boolean includeGuardedUsages) {
      super(compiler);
      this.polyfillConsumer = polyfillConsumer;
      this.includeGuardedUsages = includeGuardedUsages;
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

        if (polyfill != null && (includeGuardedUsages || !isGuarded(name))) {
          emit(polyfill, node, name, isExplicitGlobal);
          // Bail out because isGetProp overlaps below
          return;
        }
      }

      // Inject anything that *might* match method calls - these may be removed later.
      if (node.isGetProp()) {
        String methodName = node.getLastChild().getString();
        Collection<Polyfill> methods = polyfills.methods.get(methodName);
        if (!methods.isEmpty() && (includeGuardedUsages || !isGuarded("." + methodName))) {
          for (Polyfill polyfill : methods) {
            emit(polyfill, node, methodName, /* rootIsKnownGlobal= */ false);
          }
        }
      }
    }

    private void emit(Polyfill polyfill, Node node, String name, boolean rootIsKnownGlobal) {
      this.polyfillConsumer.accept(PolyfillUsage.create(polyfill, node, name, rootIsKnownGlobal));
    }
  }

  private static final ImmutableSet<String> GLOBAL_NAMES =
      ImmutableSet.of("goog.global.", "window.", "goog$global.", "globalThis.");

  private static boolean languageOutIsAtLeast(LanguageMode mode, FeatureSet outputFeatureSet) {
    return outputFeatureSet.contains(mode.toFeatureSet());
  }

  static boolean languageOutIsAtLeast(FeatureSet features, FeatureSet outputFeatureSet) {
    switch (features.version()) {
      case "ts":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT6_TYPED, outputFeatureSet);
      case "es_2020":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT_2020, outputFeatureSet);
      case "es_2019":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT_2019, outputFeatureSet);
      case "es9":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT_2018, outputFeatureSet);
      case "es8":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT_2017, outputFeatureSet);
      case "es7":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT_2016, outputFeatureSet);
      case "es6":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT_2015, outputFeatureSet);
      case "es5":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT5, outputFeatureSet);
      case "es3":
        return languageOutIsAtLeast(LanguageMode.ECMASCRIPT3, outputFeatureSet);
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
