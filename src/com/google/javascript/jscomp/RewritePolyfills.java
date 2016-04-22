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

import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES3;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES6;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES6_IMPL;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.HashSet;
import java.util.Set;

/**
 * Rewrites calls to ES6 library functions to use compiler-provided polyfills,
 * e.g., <code>var m = new Map();</code> becomes
 * <code>$jscomp.Map$install(); var m = new $jscomp.Map();</code>
 */
public class RewritePolyfills implements HotSwapCompilerPass {

  static final DiagnosticType INSUFFICIENT_OUTPUT_VERSION_ERROR = DiagnosticType.warning(
      "JSC_INSUFFICIENT_OUTPUT_VERSION",
      "Built-in ''{0}'' not supported in output version {1}: set --language_out to at least {2}");

  // Also polyfill references to e.g. goog.global.Map or window.Map.
  private static final String GLOBAL = "goog.global.";
  private static final String WINDOW = "window.";

  /**
   * Represents a single polyfill: specifically, a native symbol
   * (either a qualified name or a property name) that can be
   * rewritten and/or installed to provide the functionality to
   * a lower version.  This is a simple value type.
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
     * Optional qualified name to drop-in replace for the native symbol.
     * May be empty if no direct rewriting is to take place.
     */
    final String rewrite;

    /**
     * Optional "installer" to insert (once) at the top of a source file.
     * If present, this should be a JavaScript statement, or empty if no
     * installer should be inserted.
     */
    final String installer;

    Polyfill(
        FeatureSet nativeVersion, FeatureSet polyfillVersion, String rewrite, String installer) {
      this.nativeVersion = nativeVersion;
      this.polyfillVersion = polyfillVersion;
      this.rewrite = rewrite;
      this.installer = installer;
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

    private Polyfills(Builder builder) {
      this.methods = builder.methodsBuilder.build();
      this.statics = builder.staticsBuilder.build();
    }

    /**
     * Provides a DSL for building a {@link Polyfills} object by calling
     * {@link #addStatics}, {@link #addMethods}, and {@link #addClasses}
     * to register the various polyfills and provide information about
     * the native and polyfilled versions, and how to use the polyfills.
     */
    static class Builder {
      private final ImmutableMultimap.Builder<String, Polyfill> methodsBuilder =
          ImmutableMultimap.builder();
      private final ImmutableMap.Builder<String, Polyfill> staticsBuilder = ImmutableMap.builder();

      /**
       * Registers one or more prototype method in a single namespace.
       * The pass is agnostic with regard to the class whose prototype
       * is being augmented.  The {@code base} parameter specifies the
       * qualified namespace where all the {@code methods} reside.  Each
       * method is expected to have a sibling named with the {@code $install}
       * suffix.  The method calls themselves are not rewritten, but
       * whenever one is detected, its installer(s) will be added to the
       * top of the source file whenever the output version is less than
       * {@code nativeVersion}.  For example, defining {@code
       *    addMethods(ES6, ES5, "$jscomp.string", "startsWith", "endsWith")}
       * will cause {@code $jscomp.string.startsWith$install();} to be
       * added to any source file that calls, e.g. {@code foo.startsWith}.
       *
       * <p>If {@code base} is blank, then no polyfills will be installed.
       * This is useful for documenting unimplemented polyfills.
       */
      Builder addMethods(
          FeatureSet nativeVersion, FeatureSet polyfillVersion, String base, String... methods) {
        if (!base.isEmpty()) {
          for (String method : methods) {
            methodsBuilder.put(
                method,
                new Polyfill(
                    nativeVersion, polyfillVersion, "", base + "." + method + "$install();"));
          }
        }
        // TODO(sdh): If base.isEmpty() then it means no polyfill is implemented.  Is there
        // any way we can warn if the output language is too low?  It's not likely, since
        // there's no good way to determine if it's actually intended as an ES6 method or
        // else is defined elsewhere.
        return this;
      }

      /**
       * Registers one or more static rewrite polyfill, which is a
       * simple rewrite of one qualified name to another.  For each
       * {@code name} in {@code statics}, {@code nativeBase + '.' + name}
       * will be replaced with {@code polyfillBase + '.' + name}
       * whenever the output version is less than {@code nativeVersion}.
       * For eaxmple, defining {@code
       *    addStatics(ES6, ES5, "$jscomp.math", "Math", "clz32", "imul")}
       * will cause {@code Math.clz32} to be rewritten as
       * {@code $jscomp.math.clz32}.
       *
       * <p>If {@code polyfillBase} is blank, then no polyfills will be
       * installed.  This is useful for documenting unimplemented polyfills,
       * and will trigger a warning if the language output mode is less than
       * the native version.
       */
      Builder addStatics(
          FeatureSet nativeVersion,
          FeatureSet polyfillVersion,
          String polyfillBase,
          String nativeBase,
          String... statics) {
        for (String item : statics) {
          String nativeName = nativeBase + "." + item;
          String polyfillName = !polyfillBase.isEmpty() ? polyfillBase + "." + item : "";
          Polyfill polyfill = new Polyfill(nativeVersion, polyfillVersion, polyfillName, "");
          staticsBuilder.put(nativeName, polyfill);
          staticsBuilder.put(GLOBAL + nativeName, polyfill);
          staticsBuilder.put(WINDOW + nativeName, polyfill);
        }
        return this;
      }

      /**
       * Registers one or more class polyfill.  Class polyfills
       * are both rewritten in place and also installed (so that
       * faster native versions may be preferred if available).
       * The {@code base} parameter is a qualified name prefix
       * added to the class name to get the polyfill's name.
       * A sibling method with the {@code $install} suffix should
       * also be present.
       * For example, defining {@code
       *    addClasses(ES6, ES5, "$jscomp", "Map", "Set")}
       * will cause {@code new Map()} to be rewritten as
       * {@code new $jscomp.Map()} and will insert {@code
       * $jscomp.Map$install();} at the top of the source
       * file whenever the output version is less than
       * {@code nativeVersion}.
       *
       * <p>If {@code base} is blank, then no polyfills will be
       * installed.  This is useful for documenting unimplemented
       * polyfills, and will trigger a warning if the language
       * output mode is less than the native version.
       */
      Builder addClasses(
          FeatureSet nativeVersion, FeatureSet polyfillVersion, String base, String... classes) {
        for (String className : classes) {
          String polyfillName = base + "." + className;
          Polyfill polyfill =
              !base.isEmpty()
                  ? new Polyfill(
                      nativeVersion, polyfillVersion, polyfillName, polyfillName + "$install();")
                  : new Polyfill(nativeVersion, polyfillVersion, "", "");
          staticsBuilder.put(className, polyfill);
          staticsBuilder.put(GLOBAL + className, polyfill);
          staticsBuilder.put(WINDOW + className, polyfill);
        }
        return this;
      }

      /** Builds the {@link Polyfills}. */
      Polyfills build() {
        return new Polyfills(this);
      }
    }
  }

  // TODO(sdh): ES6 output is still incomplete, so it's reasonable to use
  // --language_out=ES5 even if targetting ES6 browsers - we need to find a way
  // to distinguish this case and not give warnings for implemented features.

  private static final Polyfills POLYFILLS = new Polyfills.Builder()
      // Polyfills not (yet) implemented.
      .addClasses(ES6, ES6, "", "Proxy", "Reflect")
      .addClasses(ES6_IMPL, ES6_IMPL, "", "WeakMap", "WeakSet")
      // TODO(sdh): typed arrays??? these are implemented everywhere except in IE9,
      //            and introducing warnings would be problematic.
      .addStatics(ES6_IMPL, ES6_IMPL, "", "Object",
          "getOwnPropertySymbols", "setPrototypeOf")
      .addStatics(ES6_IMPL, ES6_IMPL, "", "String", "raw")
      .addMethods(ES6_IMPL, ES6_IMPL, "", "normalize")

      // Implemented elsewhere (so no rewrite here)
      .addClasses(ES6_IMPL, ES3, "", "Symbol")

      // NOTE: The following polyfills will be implemented ASAP.  Once each is implemented,
      // its output language will be changed from ES6 to ES3 and the polyfill namespace
      // ($jscomp or $jscomp.*) will replace the empty string argument indicating that the
      // polyfill should actually be used.

      // Implemented classes.
      .addClasses(ES6_IMPL, ES3, "$jscomp", "Map", "Set")
      // Math methods.
      .addStatics(ES6_IMPL, ES3, "$jscomp.math", "Math",
          "clz32", "imul", "sign", "log2", "log10", "log1p", "expm1", "cosh", "sinh", "tanh",
          "acosh", "asinh", "atanh", "hypot", "trunc", "cbrt")
      // Number methods.
      .addStatics(ES6_IMPL, ES3, "$jscomp.number", "Number",
          "isFinite", "isInteger", "isNaN", "isSafeInteger",
          "EPSILON", "MAX_SAFE_INTEGER", "MIN_SAFE_INTEGER")
      // Object methods.
      .addStatics(ES6_IMPL, ES3, "$jscomp.object", "Object", "assign", "is")
      // String methods.
      .addStatics(ES6_IMPL, ES3, "$jscomp.string", "String", "fromCodePoint")
      .addMethods(ES6_IMPL, ES3, "$jscomp.string",
          "repeat", "codePointAt", "includes", "startsWith", "endsWith")
      // Array methods.
      .addStatics(ES6_IMPL, ES3, "$jscomp.array", "Array", "from", "of")
      .addMethods(ES6_IMPL, ES3, "$jscomp.array",
          "entries", "keys", "values", "copyWithin", "fill", "find", "findIndex")
      .build();

  private final AbstractCompiler compiler;
  private final Polyfills polyfills;
  private GlobalNamespace globals;

  public RewritePolyfills(AbstractCompiler compiler) {
    this(compiler, POLYFILLS);
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
    if (traverser.changed) {
      compiler.reportCodeChange();
    }
  }

  @Override
  public void process(Node externs, Node root) {
    if (languageOutIsAtLeast(ES6) || !compiler.getOptions().rewritePolyfills) {
      return; // no rewriting in this case.
    }
    this.globals = new GlobalNamespace(compiler, externs, root);
    hotSwapScript(root, null);
  }

  private class Traverser extends AbstractPostOrderCallback {

    Node injectedLibraryNode = null;
    Set<String> installers = new HashSet<>();
    boolean changed = false;

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {

      // Fix types in JSDoc.
      JSDocInfo doc = node.getJSDocInfo();
      if (doc != null) {
        fixJsdoc(traversal.getScope(), doc);
      }

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
          if (!languageOutIsAtLeast(polyfill.nativeVersion)) {

            if (!polyfill.installer.isEmpty()) {
              // Note: add the installer *before* replacing the node!
              addInstaller(polyfill.installer);
            }

            if (!polyfill.rewrite.isEmpty()) {
              changed = true;
              injectRuntime();
              Node replacement = NodeUtil.newQName(compiler, polyfill.rewrite);
              replacement.useSourceInfoIfMissingFromForTree(node);
              parent.replaceChild(node, replacement);
            }
          }

          // TODO(sdh): consider warning if language_in is too low?  it's not really any
          // harm, and we can't do it consistently for the prototype methods, so maybe
          // it's not worth doing here, either.

          return; // isGetProp (below) overlaps, so just bail out now
        }
      }

      // Add any requires that *might* match method calls (but don't rewrite anything)
      if (node.isGetProp() && node.getLastChild().isString()) {
        for (Polyfill polyfill : polyfills.methods.get(node.getLastChild().getString())) {
          if (!languageOutIsAtLeast(polyfill.nativeVersion) && !polyfill.installer.isEmpty()) {
            // Check if this is a global function.
            if (!isStaticFunction(node, traversal)) {
              addInstaller(polyfill.installer);
            }
          }
        }
      }
    }

    private boolean isStaticFunction(Node node, NodeTraversal traversal) {
      if (!node.isQualifiedName()) {
        return false;
      }
      String root = NodeUtil.getRootOfQualifiedName(node).getQualifiedName();
      if (globals == null) {
        return false;
      }
      GlobalNamespace.Name fullName = globals.getOwnSlot(node.getQualifiedName());
      GlobalNamespace.Name rootName = globals.getOwnSlot(root);
      if (fullName == null || rootName == null) {
        return false;
      }
      GlobalNamespace.Ref rootDecl = rootName.getDeclaration();
      if (rootDecl == null) {
        return false;
      }
      Node globalDeclNode = rootDecl.getNode();
      if (globalDeclNode == null) {
        return false; // don't know where the root came from so assume it could be anything
      }
      Var rootScope = traversal.getScope().getVar(root);
      if (rootScope == null) {
        return true; // root is not in the current scope, so it's a static function
      }
      Node scopeDeclNode = rootScope.getNode();
      return scopeDeclNode == globalDeclNode; // is the global name currently in scope?
    }

    // Fix all polyfill type references in any JSDoc.
    private void fixJsdoc(Scope scope, JSDocInfo doc) {
      for (Node node : doc.getTypeNodes()) {
        fixJsdocType(scope, node);
      }
    }

    private void fixJsdocType(Scope scope, Node node) {
      if (node.isString()) {
        Polyfill polyfill = polyfills.statics.get(node.getString());
        // Note: all classes are unqualified names, so we don't need to deal with dots
        if (polyfill != null
            && scope.getVar(node.getString()) == null
            && !languageOutIsAtLeast(polyfill.nativeVersion)) {
          node.setString(polyfill.rewrite);
        }
      }
      for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
        fixJsdocType(scope, child);
      }
    }

    private void addInstaller(String function) {
      if (installers.add(function)) {
        changed = true;
        Node installer = compiler.parseSyntheticCode(function).removeChildren();

        injectRuntime();

        if (injectedLibraryNode != null) {
          injectedLibraryNode.getParent().addChildrenAfter(installer, injectedLibraryNode);
        } else {
          compiler.getNodeForCodeInsertion(null).addChildrenToFront(installer);
        }
      }
    }

    private void injectRuntime() {
      if (injectedLibraryNode == null) {
        injectedLibraryNode = compiler.ensureLibraryInjected("es6_runtime", false);
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
