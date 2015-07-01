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
package com.google.javascript.jscomp.lint;

import com.google.common.collect.Ordering;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that goog.require() and goog.provide() calls are sorted alphabetically.
 * TODO(tbreisacher): Add automatic fixes for these.
 */
public final class CheckRequiresAndProvidesSorted extends AbstractShallowCallback
    implements CompilerPass {
  public static final DiagnosticType REQUIRES_NOT_SORTED =
      DiagnosticType.warning(
          "JSC_REQUIRES_NOT_SORTED", "goog.require() statements are not sorted.");

  public static final DiagnosticType PROVIDES_NOT_SORTED =
      DiagnosticType.warning(
          "JSC_PROVIDES_NOT_SORTED", "goog.provide() statements are not sorted.");

  public static final DiagnosticType PROVIDES_AFTER_REQUIRES =
      DiagnosticType.warning(
          "JSC_PROVIDES_AFTER_REQUIRES",
          "goog.provide() statements should be before goog.require() statements.");

  private List<String> requiredNamespaces;
  private List<String> providedNamespaces;
  private final AbstractCompiler compiler;

  public CheckRequiresAndProvidesSorted(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.requiredNamespaces = new ArrayList<>();
    this.providedNamespaces = new ArrayList<>();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.SCRIPT:
        if (!Ordering.natural().isOrdered(requiredNamespaces)) {
          t.report(n, REQUIRES_NOT_SORTED);
        }
        if (!Ordering.natural().isOrdered(providedNamespaces)) {
          t.report(n, PROVIDES_NOT_SORTED);
        }

        requiredNamespaces.clear();
        providedNamespaces.clear();
        break;
      case Token.CALL:
        String req = compiler.getCodingConvention().extractClassNameIfRequire(n, parent);
        if (req != null) {
          requiredNamespaces.add(req);
        }
        String prov = compiler.getCodingConvention().extractClassNameIfProvide(n, parent);
        if (prov != null) {
          if (!requiredNamespaces.isEmpty()) {
            t.report(n, PROVIDES_AFTER_REQUIRES);
          }
          providedNamespaces.add(prov);
        }
        break;
    }
  }
}
