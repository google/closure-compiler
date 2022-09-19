/*
 * Copyright 2021 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.common.annotations.GwtIncompatible;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Contains compiler passes to protect `goog.LOCALE` from optimization during the main optimizations
 * phase of compilation, then replace it with the specific destination locale near the end of
 * compilation.
 */
@GwtIncompatible("Unnecessary")
final class LocaleDataPasses {

  private LocaleDataPasses() {}

  // Replacements for values that needed to be protected from optimizations.
  static final String GOOG_LOCALE_REPLACEMENT = "__JSC_LOCALE__";

  static class ProtectGoogLocale implements CompilerPass {
    private final AbstractCompiler compiler;

    ProtectGoogLocale(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      // Create the extern symbols
      NodeUtil.createSynthesizedExternsSymbol(compiler, GOOG_LOCALE_REPLACEMENT);
      ProtectCurrentLocale protectLocaleCallback = new ProtectCurrentLocale(compiler);
      NodeTraversal.traverse(compiler, root, protectLocaleCallback);
    }
  }

  /** Protect `goog.LOCALE` by replacing it with an extern-defined name. */
  private static class ProtectCurrentLocale extends AbstractPostOrderCallback {
    private final AbstractCompiler compiler;
    private final AstFactory astFactory;

    ProtectCurrentLocale(AbstractCompiler compiler) {
      this.compiler = compiler;
      this.astFactory = compiler.createAstFactory();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (isGoogDotLocaleReference(n)) {
        // We will replace the RHS of `goog.LOCALE = goog.define(...);`, but everywhere
        // else we will replace `goog.LOCALE` itself, so we don't have to waste time
        // inlining it later.
        final Node nodeToReplace = NodeUtil.isLhsOfAssign(n) ? n.getNext() : n;
        Node replacement = astFactory.createName(GOOG_LOCALE_REPLACEMENT, type(nodeToReplace));
        replacement.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        nodeToReplace.replaceWith(replacement);
        compiler.reportChangeToEnclosingScope(parent);
      }
    }
  }

  // matching against an actual Node is faster than matching against the string "goog.LOCALE"
  private static final Node QNAME_FOR_GOOG_LOCALE = IR.getprop(IR.name("goog"), "LOCALE");

  private static boolean isGoogDotLocaleReference(Node n) {
    // NOTE: Theoretically there could be a local variable named `goog`, but it's not worth checking
    // for that.
    return n.matchesQualifiedName(QNAME_FOR_GOOG_LOCALE);
  }

  /** This class replaces `__JSC_LOCALE__` with the actual locale string. */
  static class LocaleSubstitutions extends AbstractPostOrderCallback implements CompilerPass {

    private static final String DEFAULT_LOCALE = "en";
    private final Node qnameForLocale = IR.name(GOOG_LOCALE_REPLACEMENT);

    private final AbstractCompiler compiler;
    private final AstFactory astFactory;
    private final String locale;

    LocaleSubstitutions(AbstractCompiler compiler, String locale) {
      this.compiler = compiler;
      this.astFactory = compiler.createAstFactory();
      // Use the "default" locale if not otherwise set.
      this.locale = locale == null ? DEFAULT_LOCALE : locale;
    }

    @Override
    public void process(Node externs, Node root) {
      // Create the extern symbol
      NodeTraversal.traverse(compiler, root, this);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.matchesName(this.qnameForLocale)) {
        Node replacement = astFactory.createString(checkNotNull(locale)).srcref(n);
        n.replaceWith(replacement);
        compiler.reportChangeToEnclosingScope(replacement);
      }
    }
  }
}
