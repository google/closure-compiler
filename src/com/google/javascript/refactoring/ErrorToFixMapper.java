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
import com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps a {@code JSError} to a list of {@code SuggestedFix}es, if possible.
 * TODO(tbreisacher): Move this into the compiler itself (i.e. into the jscomp package). This will
 *     make it easier for people adding new warnings to also add fixes for them.
 */
public final class ErrorToFixMapper {
  private ErrorToFixMapper() {} // All static

  private static final Pattern DID_YOU_MEAN = Pattern.compile(".*Did you mean (.*)\\?");
  private static final Pattern EARLY_REF =
      Pattern.compile("Variable referenced before declaration: (.*)");
  private static final Pattern MISSING_REQUIRE =
      Pattern.compile("missing require: '([^']+)'");
  private static final Pattern EXTRA_REQUIRE =
      Pattern.compile("extra require: '([^']+)'");
  private static final Pattern DUPLICATE_REQUIRE =
      Pattern.compile("'([^']+)' required more than once\\.");
  private static final Pattern USE_SHORT_NAME =
      Pattern.compile(".*Please use the short name '(.*)' instead.");

  public static List<SuggestedFix> getFixesForJsError(JSError error, AbstractCompiler compiler) {
    SuggestedFix fix = getFixForJsError(error, compiler);
    if (fix != null) {
      return ImmutableList.of(fix);
    }
    switch (error.getType().key) {
      case "JSC_IMPLICITLY_NULLABLE_JSDOC":
        return getFixesForImplicitlyNullableJsDoc(error, compiler);
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
      case "JSC_REDECLARED_VARIABLE":
        return getFixForRedeclaration(error, compiler);
      case "JSC_REFERENCE_BEFORE_DECLARE":
        return getFixForEarlyReference(error, compiler);
      case "JSC_MISSING_SEMICOLON":
        return getFixForMissingSemicolon(error, compiler);
      case "JSC_REQUIRES_NOT_SORTED":
        return getFixForUnsortedRequiresOrProvides("goog.require", error, compiler);
      case "JSC_PROVIDES_NOT_SORTED":
        return getFixForUnsortedRequiresOrProvides("goog.provide", error, compiler);
      case "JSC_DEBUGGER_STATEMENT_PRESENT":
      case "JSC_USELESS_EMPTY_STATEMENT":
        return removeNode(error, compiler);
      case "JSC_INEXISTENT_PROPERTY":
        return getFixForInexistentProperty(error, compiler);
      case "JSC_MISSING_CALL_TO_SUPER":
        return getFixForMissingSuper(error, compiler);
      case "JSC_INVALID_SUPER_CALL_WITH_SUGGESTION":
        return getFixForInvalidSuper(error, compiler);
      case "JSC_MISSING_REQUIRE_WARNING":
      case "JSC_MISSING_REQUIRE_CALL_WARNING":
        return getFixForMissingRequire(error, compiler);
      case "JSC_DUPLICATE_REQUIRE":
        return getFixForDuplicateRequire(error, compiler);
      case "JSC_EXTRA_REQUIRE_WARNING":
        return getFixForExtraRequire(error, compiler);
      case "JSC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME":
      case "JSC_JSDOC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME":
        return getFixForReferenceToShortImportByLongName(error, compiler);
      default:
        return null;
    }
  }

  private static SuggestedFix getFixForRedeclaration(JSError error, AbstractCompiler compiler) {
    Node name = error.node;
    Preconditions.checkState(name.isName(), name);
    Node parent = name.getParent();
    if (!NodeUtil.isNameDeclaration(parent)) {
      return null;
    }

    SuggestedFix.Builder fix = new SuggestedFix.Builder().attachMatchedNodeInfo(name, compiler);

    if (!name.hasChildren()) {
      Node nodeToDelete = parent.hasOneChild() ? parent : error.node;
      return fix.delete(nodeToDelete).build();
    }

    Node assign = IR.exprResult(
        IR.assign(name.cloneNode(), name.getFirstChild().cloneTree()));
    if (parent.hasOneChild()) {
      return fix.replace(parent, assign, compiler).build();
    }

    // Split the var statement into an assignment and up to two var statements.
    // var a = 0,
    //     b = 1,  // This is the one we're removing.
    //     c = 2;
    //
    // becomes
    //
    // var a = 0;  // This is the "added" var statement.
    // b = 1;
    // var c = 2;  // This is the original var statement.
    List<Node> childrenOfAddedVarStatement = new ArrayList<>();
    for (Node n : parent.children()) {
      if (n == name) {
        break;
      }
      childrenOfAddedVarStatement.add(n);
    }

    if (!childrenOfAddedVarStatement.isEmpty()) {
      Node var = new Node(parent.getToken());
      for (Node n : childrenOfAddedVarStatement) {
        var.addChildToBack(n.cloneTree());
      }
      // Use a sortKey of "1" to make sure this is applied before the statement below.
      fix.insertBefore(parent, var, compiler, "1");
    }

    if (name.getNext() != null) {
      // Keep the original var statement, just remove the names that will be put in the added one.
      for (Node n : childrenOfAddedVarStatement) {
        fix.delete(n);
      }
      fix.delete(name);
      // Use a sortKey of "2" to make sure this is applied after the statement above.
      fix.insertBefore(parent, assign, compiler, "2");
    } else {
      // Remove the original var statement.
      fix.replace(parent, assign, compiler);
    }

    return fix.build();
  }

  /**
   * This fix is not ideal. It trades one warning (JSC_REFERENCE_BEFORE_DECLARE) for another
   * (JSC_REDECLARED_VARIABLE). But after running the fixer once, you can then run it again and
   * #getFixForRedeclaration will take care of the JSC_REDECLARED_VARIABLE warning.
   */
  private static SuggestedFix getFixForEarlyReference(JSError error, AbstractCompiler compiler) {
    Matcher m = EARLY_REF.matcher(error.description);
    if (m.matches()) {
      String name = m.group(1);
      Node stmt = NodeUtil.getEnclosingStatement(error.node);
      return new SuggestedFix.Builder()
          .attachMatchedNodeInfo(error.node, compiler)
          .insertBefore(stmt, "var " + name + ";\n")
          .build();
    }
    return null;
  }

  private static SuggestedFix getFixForReferenceToShortImportByLongName(
      JSError error, AbstractCompiler compiler) {
    Matcher m = USE_SHORT_NAME.matcher(error.description);
    if (m.matches()) {
      return new SuggestedFix.Builder()
          .attachMatchedNodeInfo(error.node, compiler)
          .replace(error.node, NodeUtil.newQName(compiler, m.group(1)), compiler)
          .build();
    }
    return null;
  }

  private static List<SuggestedFix> getFixesForImplicitlyNullableJsDoc(
      JSError error, AbstractCompiler compiler) {
    SuggestedFix qmark =
        new SuggestedFix.Builder()
            .attachMatchedNodeInfo(error.node, compiler)
            .insertBefore(error.node, "?")
            .setDescription("Make nullability explicit")
            .build();
    SuggestedFix bang =
        new SuggestedFix.Builder()
            .attachMatchedNodeInfo(error.node, compiler)
            .insertBefore(error.node, "!")
            .setDescription("Make type non-nullable")
            .build();
    return ImmutableList.of(bang, qmark);
  }

  private static SuggestedFix removeNode(JSError error, AbstractCompiler compiler) {
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.node, compiler)
        .delete(error.node)
        .build();
  }

  private static SuggestedFix getFixForMissingSemicolon(JSError error, AbstractCompiler compiler) {
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.node, compiler)
        .insertAfter(error.node, ";")
        .build();
  }

  private static SuggestedFix getFixForMissingSuper(JSError error, AbstractCompiler compiler) {
    Node body = NodeUtil.getFunctionBody(error.node);
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.node, compiler)
        .addChildToFront(body, "super();")
        .build();
  }

  private static SuggestedFix getFixForInvalidSuper(JSError error, AbstractCompiler compiler) {
    Matcher m = DID_YOU_MEAN.matcher(error.description);
    if (m.matches()) {
      return new SuggestedFix.Builder()
          .attachMatchedNodeInfo(error.node, compiler)
          .replace(error.node, NodeUtil.newQName(compiler, m.group(1)), compiler)
          .build();
    }
    return null;
  }

  private static SuggestedFix getFixForInexistentProperty(
      JSError error, AbstractCompiler compiler) {
    Matcher m = DID_YOU_MEAN.matcher(error.description);
    if (m.matches()) {
      String suggestedPropName = m.group(1);
      return new SuggestedFix.Builder()
          .attachMatchedNodeInfo(error.node, compiler)
          .rename(error.node, suggestedPropName)
          .build();
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
    SuggestedFix.Builder fix = new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.node, compiler)
        .addGoogRequire(match, namespaceToRequire);
    if (NodeUtil.getEnclosingType(error.node, Token.MODULE_BODY) != null) {
      Node nodeToReplace = null;
      if (error.node.isNew()) {
        nodeToReplace = error.node.getFirstChild();
      } else if (error.node.isCall()) {
        nodeToReplace = error.node.getFirstFirstChild();
      } else if (error.node.isQualifiedName()) {
        nodeToReplace = error.node;
      }

      if (nodeToReplace != null) {
        String shortName = namespaceToRequire.substring(namespaceToRequire.lastIndexOf('.') + 1);
        fix.replace(nodeToReplace, IR.name(shortName), compiler);
      }
    }
    return fix.build();
  }

  private static SuggestedFix getFixForDuplicateRequire(JSError error, AbstractCompiler compiler) {
    if (!error.node.isExprResult()) {
      return null;
    }
    Matcher regexMatcher = DUPLICATE_REQUIRE.matcher(error.description);
    Preconditions.checkState(
        regexMatcher.matches(), "Unexpected error description: %s", error.description);
    String namespace = regexMatcher.group(1);
    NodeMetadata metadata = new NodeMetadata(compiler);
    Match match = new Match(error.node, metadata);
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.node, compiler)
        .removeGoogRequire(match, namespace)
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
        .attachMatchedNodeInfo(error.node, compiler)
        .removeGoogRequire(match, namespace)
        .build();
  }

  private static SuggestedFix getFixForUnsortedRequiresOrProvides(
      String closureFunction, JSError error, AbstractCompiler compiler) {
    SuggestedFix.Builder fix = new SuggestedFix.Builder();
    fix.attachMatchedNodeInfo(error.node, compiler);
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
      } else if (NodeUtil.isNameDeclaration(parent)
          && n.hasChildren()
          && n.getLastChild().isCall()
          && n.getLastChild().getFirstChild().matchesQualifiedName(closureFunction)) {
        Preconditions.checkState(n.isName() || n.isDestructuringLhs());
        calls.add(parent);
      }
    }

    public void sortCallsAlphabetically() {
      Collections.sort(calls, this);
    }

    @Override
    public int compare(Node n1, Node n2) {
      String namespace1 = CheckRequiresAndProvidesSorted.getSortKey.apply(n1);
      String namespace2 = CheckRequiresAndProvidesSorted.getSortKey.apply(n2);
      return namespace1.compareTo(namespace2);
    }
  }
}
