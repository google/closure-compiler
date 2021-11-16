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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Detects all potential usages of polyfilled classes or methods */
final class PolyfillUsageFinder {

  /**
   * Represents a single polyfill: specifically, for a native symbol, a set of native and polyfill
   * versions, and a library to ensure is injected if the output version is less than the native
   * version.
   */
  static final class Polyfill {
    /** The full name of the polyfill, e.g `Map` or `String.prototype.includes` */
    final String nativeSymbol;

    /**
     * The language version at (or above) which the native symbol is available and sufficient. If
     * the language out flag is at least as high as {@code nativeVersion} then the polyfill is not
     * needed. This string should be one of those returned by FeatureSet.version().
     */
    final String nativeVersion;

    /**
     * The required language version for the polyfill to work. This should not be higher than {@code
     * nativeVersion}, but may be the same in cases where there is no polyfill provided. This is
     * used to emit a warning if the language out flag is too low. This string should be one of
     * those returned by FeatureSet.version().
     */
    final String polyfillVersion;

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
        String nativeVersion,
        String polyfillVersion,
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
              statics.keySet().stream()
                  .map(arg -> arg.substring(arg.lastIndexOf('.') + 1))
                  .collect(Collectors.toList()));
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
        final String nativeVersionStr = tokens.get(1);
        final String polyfillVersionStr = tokens.get(2);
        Polyfill polyfill =
            new Polyfill(
                symbol,
                nativeVersionStr,
                polyfillVersionStr,
                tokens.size() > 3 ? tokens.get(3) : "",
                isPrototypeMethod ? Polyfill.Kind.METHOD : Polyfill.Kind.STATIC);
        if (isPrototypeMethod) {
          methods.put(symbol.replaceAll(".*\\.prototype\\.", ""), polyfill);
        } else {
          statics.put(symbol, polyfill);
        }
      }
      return new Polyfills(methods.build(), statics.buildOrThrow());
    }

  }

  @AutoValue
  abstract static class PolyfillUsage {
    abstract Polyfill polyfill();

    abstract Node node();

    abstract String name();

    abstract boolean isExplicitGlobal();

    private static PolyfillUsage createExplicit(Polyfill polyfill, Node node, String name) {
      return new AutoValue_PolyfillUsageFinder_PolyfillUsage(
          polyfill, node, name, /* isExplicitGlobal= */ true);
    }

    private static PolyfillUsage createNonExplicit(Polyfill polyfill, Node node, String name) {
      return new AutoValue_PolyfillUsageFinder_PolyfillUsage(
          polyfill, node, name, /* isExplicitGlobal= */ false);
    }
  }

  private final AbstractCompiler compiler;
  private final Polyfills polyfills;

  PolyfillUsageFinder(AbstractCompiler compiler, Polyfills polyfills) {
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
    NodeTraversal.traverse(
        compiler, root, new Traverser(this.compiler, polyfillConsumer, Guard.ONLY_UNGUARDED));
  }

  /**
   * Passes all polyfill usages found, in postorder, to the given polyfillConsumer
   *
   * <p>Includes polyfill usages that are behind a guard, like {@code if (Promise) return
   * Promise.resolve('ok');}
   */
  void traverseIncludingGuarded(Node root, Consumer<PolyfillUsage> polyfillConsumer) {
    NodeTraversal.traverse(
        compiler, root, new Traverser(this.compiler, polyfillConsumer, Guard.ALL));
  }

  /**
   * Passes all polyfill usages found, in postorder, to the given polyfillConsumer
   *
   * <p>Only includes polyfill usages that are behind a guard, like {@code if (Promise) return
   * Promise.resolve('ok');}
   */
  void traverseOnlyGuarded(Node root, Consumer<PolyfillUsage> polyfillConsumer) {
    NodeTraversal.traverse(
        compiler, root, new Traverser(this.compiler, polyfillConsumer, Guard.ONLY_GUARDED));
  }

  private enum Guard {
    ONLY_GUARDED,
    ONLY_UNGUARDED,
    ALL;

    boolean shouldInclude(boolean isGuarded) {
      switch (this) {
        case ALL:
          return true;
        case ONLY_GUARDED:
          return isGuarded;
        case ONLY_UNGUARDED:
          return !isGuarded;
      }
      throw new AssertionError();
    }
  };

  private class Traverser extends GuardedCallback<String> {

    private final Consumer<PolyfillUsage> polyfillConsumer;
    // Whether to emit usages like Promise in `if (Promise) return Promise.resolve('ok');}`
    private final Guard includeGuardedUsages;

    Traverser(
        AbstractCompiler compiler,
        Consumer<PolyfillUsage> polyfillConsumer,
        Guard includeGuardedUsages) {
      super(compiler);
      this.polyfillConsumer = polyfillConsumer;
      this.includeGuardedUsages = includeGuardedUsages;
    }

    @Override
    public void visitGuarded(NodeTraversal traversal, Node node, Node parent) {
      switch (node.getToken()) {
        case NAME:
          visitName(traversal, node);
          break;
        case GETPROP:
        case OPTCHAIN_GETPROP:
          visitGetPropChain(traversal, node);
          break;
        default:
          // nothing to do
      }
    }

    private void visitName(NodeTraversal traversal, Node nameNode) {
      String name = nameNode.getString();
      Polyfill polyfill = polyfills.statics.get(name);
      if (polyfill == null) {
        // no polyfill exists for this name
        return;
      }

      if (isPolyfillVisibleInScope(traversal, name)
          && includeGuardedUsages.shouldInclude(isGuarded(name))) {
        this.polyfillConsumer.accept(PolyfillUsage.createNonExplicit(polyfill, nameNode, name));
      }
    }

    private void visitGetPropChain(NodeTraversal traversal, Node getPropNode) {
      // First see if we have a usage that matches a full static polyfill name.
      // e.g. `Array.from` or `globalThis.Promise.allSettled`
      PolyfillUsage staticPolyfillUsage =
          maybeCreateStaticPolyfillUsageForGetPropChain(traversal, getPropNode);
      if (staticPolyfillUsage != null) {
        if (includeGuardedUsages.shouldInclude(isGuarded(staticPolyfillUsage.name()))) {
          this.polyfillConsumer.accept(staticPolyfillUsage);
        }
      } else {
        // We don't have a static polyfill usage, but this could still be a reference to one of
        // several possible method polyfills.
        // e.g. `obj.includes(x)` could be a usage of `Array.prototype.includes` or
        // `String.prototype.includes`.
        final String propertyName = getPropNode.getString();
        Collection<Polyfill> methodPolyfills = polyfills.methods.get(propertyName);
        // Note that we use ".foo" as the guard check for methods to keep them distinct in case
        // there is also a static "foo" polyfill.
        if (!methodPolyfills.isEmpty()
            && includeGuardedUsages.shouldInclude(isGuarded("." + propertyName))) {
          for (Polyfill polyfill : methodPolyfills) {
            this.polyfillConsumer.accept(
                PolyfillUsage.createNonExplicit(polyfill, getPropNode, propertyName));
          }
        }
      }
    }
  }

  @Nullable
  private PolyfillUsage maybeCreateStaticPolyfillUsageForGetPropChain(
      NodeTraversal traversal, final Node getPropNode) {
    checkArgument(getPropNode.isGetProp() || getPropNode.isOptChainGetProp(), getPropNode);
    final String lastComponent = getPropNode.getString();
    if (!polyfills.suffixes.contains(lastComponent)) {
      // Save execution time by bailing out early if the property name at the end of the chain
      // doesn't match any of the known polyfills.
      return null;
    }
    // NOTE: We are not using isQualifiedName() and getQualifiedName() here, because we want to
    // locate the owner node and also have this code work for optional chains.
    final ArrayDeque<String> components = new ArrayDeque<>();
    components.addFirst(lastComponent);
    Node ownerNode;
    for (ownerNode = getPropNode.getFirstChild();
        ownerNode.isGetProp() || ownerNode.isOptChainGetProp();
        ownerNode = ownerNode.getFirstChild()) {
      components.addFirst(ownerNode.getString());
    }
    if (!ownerNode.isName()) {
      // Static polyfills are always fully qualified names beginning with a NAME node.
      // e.g. `Array.from` or `globalThis.Promise`
      return null;
    }
    final String rootName = ownerNode.getString();
    components.addFirst(rootName);
    final String fullName = String.join(".", components);
    final String globalPrefix = findGlobalPrefix(fullName);
    if (globalPrefix != null) {
      // The full name starts with a known global value, like `goog.global.` or `globalThis.`.
      // We must strip that off before matching with the known polyfill names.
      // (Note that the connecting '.' is included and will also be stripped.)
      // Also, the presence of the explicit global value name means we don't have to check the
      // scope for a shadowing variable as we do below.
      Polyfill polyfill = polyfills.statics.get(fullName.substring(globalPrefix.length()));
      if (polyfill != null) {
        return PolyfillUsage.createExplicit(polyfill, getPropNode, polyfill.nativeSymbol);
      }
    } else {
      Polyfill polyfill = polyfills.statics.get(fullName);
      if (polyfill != null) {
        // If we see a declaration of the name, then it is defined by the source code,
        // and we won't count it as a reference to our polyfill.
        // Checking the scope is relatively expensive, so we don't want to do it until
        // we've confirmed that this node looks like it could be a polyfill reference.
        if (isPolyfillVisibleInScope(traversal, rootName)) {
          return PolyfillUsage.createNonExplicit(polyfill, getPropNode, polyfill.nativeSymbol);
        }
      }
    }
    return null;
  }

  /** @return Whether the Polyfill name is visible in the current scope. */
  private boolean isPolyfillVisibleInScope(NodeTraversal t, String globalName) {
    // This class is only supposed to traverse over actual sources, not externs,
    // so it shouldn't see the declarations of the things being polyfilled.

    // If the AST is normalized we can avoid building the scope (which is relatively expensive)
    // and rely on the normalization guarantee that every name in AST is unique.

    // NOTE: there is an edge case that we are purposely ignoring here.  This pass expects
    // the traverse the AST without externs, this creates a distinction between undeclared variables
    // (externs) and symbols in the global scope (internal globals).  So then against the
    // un-normalized AST for detecting whether to inject Polyfills, it won't inject,  if there is a
    // global declaration of the same name.  But when removing Polyfills (which happens when the
    // AST is normalized), it won't try to remove these same Polyfills if they were present,
    // because the scope distinction isn't used.  This doesn't change behavior because they
    // wouldn't have been injected in the first place.

    return (compiler.getLifeCycleStage() == LifeCycleStage.NORMALIZED
        || t.getScope().getVar(globalName) == null);
  }

  @Nullable
  private static String findGlobalPrefix(String qualifiedName) {
    for (String global : GLOBAL_NAMES) {
      if (qualifiedName.startsWith(global)) {
        return global;
      }
    }
    return null;
  }

  private static final ImmutableSet<String> GLOBAL_NAMES =
      ImmutableSet.of("goog.global.", "window.", "goog$global.", "globalThis.");

}
