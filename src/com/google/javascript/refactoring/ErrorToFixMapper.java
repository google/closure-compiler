/*
 * Copyright 2014 The Closure Compiler Authors.
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
package com.google.javascript.refactoring;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps a JSError to a SuggestedFix.
 * TODO(tbreisacher): Move this into the compiler itself (i.e. into the jscomp package). This will
 *     make it easier for people adding new warnings to also add fixes for them.
 */
public final class ErrorToFixMapper {
  private ErrorToFixMapper() {} // All static

  private static final Pattern DID_YOU_MEAN = Pattern.compile(".*Did you mean (.*)\\?");
  private static final Pattern MISSING_REQUIRE =
      Pattern.compile("missing require: '([^']+)'");
  private static final Pattern EXTRA_REQUIRE =
      Pattern.compile("extra require: '([^']+)'");

  public static List<SuggestedFix> getFixesForJsError(JSError error, AbstractCompiler compiler) {
    SuggestedFix fix = getFixForJsError(error, compiler);
    if (fix != null) {
      return ImmutableList.of(fix);
    }
    switch (error.getType().key) {
      case "JSC_IMPLICITLY_NULLABLE_JSDOC":
        return getFixesForImplicitlyNullableJsDoc(error);
      default:
        return ImmutableList.of();
    }
  }

  /**
   * Creates a SuggestedFix for the given error. Note that some errors have multiple fixes
   * so getFixesForJsError should often be used instead of this.
   */
  public static SuggestedFix getFixForJsError(JSError error, AbstractCompiler compiler) {
    switch (error.getType().key) {
      case "JSC_MISSING_SEMICOLON":
        return getFixForMissingSemicolon(error);
      case "JSC_REQUIRES_NOT_SORTED":
        return getFixForUnsortedRequiresOrProvides("goog.require", error, compiler);
      case "JSC_PROVIDES_NOT_SORTED":
        return getFixForUnsortedRequiresOrProvides("goog.provide", error, compiler);
      case "JSC_DEBUGGER_STATEMENT_PRESENT":
      case "JSC_USELESS_EMPTY_STATEMENT":
        return removeNode(error);
      case "JSC_INEXISTENT_PROPERTY":
        return getFixForInexistentProperty(error);
      case "JSC_MISSING_CALL_TO_SUPER":
        return getFixForMissingSuper(error);
      case "JSC_INVALID_SUPER_CALL_WITH_SUGGESTION":
        return getFixForInvalidSuper(error, compiler);
      case "JSC_MISSING_REQUIRE_WARNING":
      case "JSC_MISSING_REQUIRE_CALL_WARNING":
        return getFixForMissingRequire(error, compiler);
      case "JSC_DUPLICATE_REQUIRE_WARNING":
      case "JSC_EXTRA_REQUIRE_WARNING":
        return getFixForExtraRequire(error, compiler);
      default:
        return null;
    }
  }

  private static List<SuggestedFix> getFixesForImplicitlyNullableJsDoc(JSError error) {
    SuggestedFix qmark = new SuggestedFix.Builder()
        .setOriginalMatchedNode(error.node)
        .insertBefore(error.node, "?")
        .setDescription("Make nullability explicit")
        .build();
    SuggestedFix bang = new SuggestedFix.Builder()
        .setOriginalMatchedNode(error.node)
        .insertBefore(error.node, "!")
        .setDescription("Make type non-nullable")
        .build();
    return ImmutableList.of(qmark, bang);
  }

  private static SuggestedFix removeNode(JSError error) {
    return new SuggestedFix.Builder()
        .setOriginalMatchedNode(error.node)
        .delete(error.node).build();
  }

  private static SuggestedFix getFixForMissingSemicolon(JSError error) {
    return new SuggestedFix.Builder()
        .setOriginalMatchedNode(error.node)
        .insertAfter(error.node, ";")
        .build();
  }

  private static SuggestedFix getFixForMissingSuper(JSError error) {
    Node body = NodeUtil.getFunctionBody(error.node);
    return new SuggestedFix.Builder()
        .setOriginalMatchedNode(error.node)
        .addChildToFront(body, "super();")
        .build();
  }

  private static SuggestedFix getFixForInvalidSuper(JSError error, AbstractCompiler compiler) {
    Matcher m = DID_YOU_MEAN.matcher(error.description);
    if (m.matches()) {
      return new SuggestedFix.Builder()
          .setOriginalMatchedNode(error.node)
          .replace(error.node, NodeUtil.newQName(compiler, m.group(1)), compiler)
          .build();
    }
    return null;
  }

  private static SuggestedFix getFixForInexistentProperty(JSError error) {
    Matcher m = DID_YOU_MEAN.matcher(error.description);
    if (m.matches()) {
      String suggestedPropName = m.group(1);
      return new SuggestedFix.Builder()
          .setOriginalMatchedNode(error.node)
          .rename(error.node, suggestedPropName).build();
    }
    return null;
  }

  private static SuggestedFix getFixForMissingRequire(JSError error, AbstractCompiler compiler) {
    Matcher regexMatcher = MISSING_REQUIRE.matcher(error.description);
    Preconditions.checkState(regexMatcher.matches(),
        "Unexpected error description: %s", error.description);
    String namespaceToRequire = regexMatcher.group(1);
    NodeMetadata metadata = new NodeMetadata(compiler);
    Match match = new Match(error.node, metadata);
    return new SuggestedFix.Builder()
        .setOriginalMatchedNode(error.node)
        .addGoogRequire(match, namespaceToRequire)
        .build();
  }

  private static SuggestedFix getFixForExtraRequire(JSError error, AbstractCompiler compiler) {
    Matcher regexMatcher = EXTRA_REQUIRE.matcher(error.description);
    Preconditions.checkState(regexMatcher.matches(),
        "Unexpected error description: %s", error.description);
    String namespace = regexMatcher.group(1);
    NodeMetadata metadata = new NodeMetadata(compiler);
    Match match = new Match(error.node, metadata);
    return new SuggestedFix.Builder()
        .setOriginalMatchedNode(error.node)
        .removeGoogRequire(match, namespace)
        .build();
  }

  private static SuggestedFix getFixForUnsortedRequiresOrProvides(
      String closureFunction, JSError error, AbstractCompiler compiler) {
    SuggestedFix.Builder fix = new SuggestedFix.Builder();
    fix.setOriginalMatchedNode(error.node);
    Node script = NodeUtil.getEnclosingScript(error.node);
    RequireProvideSorter cb = new RequireProvideSorter(closureFunction);
    NodeTraversal.traverseEs6(compiler, script, cb);
    Node first = cb.calls.get(0);
    Node last = Iterables.getLast(cb.calls);

    cb.sortCallsAlphabetically();
    StringBuilder sb = new StringBuilder();
    for (Node n : cb.calls) {
      String statement = fix.generateCode(compiler, n);
      JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);
      if (jsDoc != null) {
        statement = jsDoc.getOriginalCommentString() + "\n" + statement;
      }
      sb.append(statement);
    }
    // Trim to remove the newline after the last goog.require/provide.
    String newContent = sb.toString().trim();
    return fix.replaceRange(first, last, newContent).build();
  }

  private static String getNamespaceFromClosureNode(Node exprResult) {
    Preconditions.checkState(exprResult.isExprResult());
    return exprResult.getFirstChild().getLastChild().getString();
  }

  private static class RequireProvideSorter extends NodeTraversal.AbstractShallowCallback
      implements Comparator<Node> {
    private final String closureFunction;
    private final List<Node> calls = new ArrayList<>();

    RequireProvideSorter(String closureFunction) {
      this.closureFunction = closureFunction;
    }

    @Override
    public final void visit(NodeTraversal nodeTraversal, Node n, Node parent) {
      if (n.isCall()
          && parent.isExprResult()
          && n.getFirstChild().matchesQualifiedName(closureFunction)) {
        calls.add(parent);
      }
    }

    public void sortCallsAlphabetically() {
      Collections.sort(calls, this);
    }

    @Override
    public int compare(Node n1, Node n2) {
      String namespace1 = getNamespaceFromClosureNode(n1);
      String namespace2 = getNamespaceFromClosureNode(n2);
      return namespace1.compareTo(namespace2);
    }
  }
}
