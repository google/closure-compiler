/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Checks that goog.provide statements are sorted and deduplicated, exposing the necessary
 * information to produce a suggested fix.
 */
public final class CheckProvidesSorted implements NodeTraversal.Callback {
  public static final DiagnosticType PROVIDES_NOT_SORTED =
      DiagnosticType.warning(
          "JSC_PROVIDES_NOT_SORTED",
          "goog.provide() statements are not sorted."
              + " The correct order is:\n\n{0}\n");

  /** Operation modes. */
  public enum Mode {
    /** Collect information to determine whether a fix is required, but do not report a warning. */
    COLLECT_ONLY,
    /** Additionally report a warning. */
    COLLECT_AND_REPORT
  };

  private final Mode mode;

  // The provided namespaces in the order they appear.
  private final List<String> originalProvides = new ArrayList<>();

  // The provided namespaces in canonical order.
  @Nullable private Node firstNode = null;
  @Nullable private Node lastNode = null;
  private boolean finished = false;

  @Nullable private String replacement = null;
  private boolean needsFix = false;

  public CheckProvidesSorted(Mode mode) {
    this.mode = mode;
  }

  /** Returns the node for the first recognized provide statement. */
  public Node getFirstNode() {
    return firstNode;
  }

  /** Returns the node for the last recognized provide statement. */
  public Node getLastNode() {
    return lastNode;
  }

  /** Returns a textual replacement yielding a canonical version of the provides. */
  public String getReplacement() {
    return replacement;
  }

  /**
   * Returns whether the provides need to be fixed, i.e., whether they are *not* already canonical.
   */
  public boolean needsFix() {
    return needsFix;
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    // Traverse top-level statements until a block of contiguous provides is found.
    return !finished
        && (parent == null || parent.isRoot() || parent.isScript() || parent.isModuleBody());
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      checkCanonical(t);
      return;
    }

    if (n.isExprResult() && isValidProvideCall(n.getFirstChild())) {
      originalProvides.add(getNamespace(n));
      if (firstNode == null) {
        firstNode = lastNode = n;
      } else {
        lastNode = n;
      }
    } else if (!originalProvides.isEmpty()) {
      finished = true;
    }
  }

  private static boolean isValidProvideCall(Node n) {
    return n.isCall()
        && n.hasTwoChildren()
        && n.getFirstChild().matchesQualifiedName("goog.provide")
        && n.getSecondChild().isStringLit();
  }

  private static String getNamespace(Node n) {
    return n.getFirstChild().getSecondChild().getString();
  }

  /** Returns the code for a correctly formatted provide call. */
  private static String formatProvide(String namespace) {
    StringBuilder sb = new StringBuilder();

    sb.append("goog.provide('");
    sb.append(namespace);
    sb.append("');");

    return sb.toString();
  }

  private void checkCanonical(NodeTraversal t) {
    @Nullable
    List<String> canonicalProvides =
        originalProvides.stream().distinct().sorted().collect(toImmutableList());
    if (!originalProvides.equals(canonicalProvides)) {
      needsFix = true;
      replacement =
          String.join(
              "\n", Iterables.transform(canonicalProvides, CheckProvidesSorted::formatProvide));
      if (mode == Mode.COLLECT_AND_REPORT) {
        t.report(firstNode, PROVIDES_NOT_SORTED, replacement);
      }
    }
  }
}
