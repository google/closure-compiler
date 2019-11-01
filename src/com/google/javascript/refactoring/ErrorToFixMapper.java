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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.refactoring.SuggestedFix.getShortNameForRequire;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.lint.CheckProvidesSorted;
import com.google.javascript.jscomp.lint.CheckRequiresSorted;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
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
  private static final Pattern FULLY_QUALIFIED_NAME =
      Pattern.compile("Reference to fully qualified import name '([^']+)'.*");
  private static final Pattern USE_SHORT_NAME =
      Pattern.compile(".*Please use the short name '(.*)' instead.");

  public static ImmutableList<SuggestedFix> getFixesForJsError(
      JSError error, AbstractCompiler compiler) {
    SuggestedFix fix = getFixForJsError(error, compiler);
    if (fix != null) {
      return ImmutableList.of(fix);
    }
    switch (error.getType().key) {
      case "JSC_IMPLICITLY_NULLABLE_JSDOC":
      case "JSC_MISSING_NULLABILITY_MODIFIER_JSDOC":
      case "JSC_NULL_MISSING_NULLABILITY_MODIFIER_JSDOC":
        return getFixesForImplicitNullabilityErrors(error, compiler);
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
        return getFixForUnsortedRequires(error, compiler);
      case "JSC_PROVIDES_NOT_SORTED":
        return getFixForUnsortedProvides(error, compiler);
      case "JSC_DEBUGGER_STATEMENT_PRESENT":
        return new SuggestedFix.Builder()
            .attachMatchedNodeInfo(error.getNode(), compiler)
            .setDescription("Remove debugger statement")
            .delete(error.getNode())
            .build();
      case "JSC_USELESS_EMPTY_STATEMENT":
        return removeEmptyStatement(error, compiler);
      case "JSC_INEXISTENT_PROPERTY_WITH_SUGGESTION":
      case "JSC_STRICT_INEXISTENT_PROPERTY_WITH_SUGGESTION":
        return getFixForInexistentProperty(error, compiler);
      case "JSC_MISSING_CALL_TO_SUPER":
        return getFixForMissingSuper(error, compiler);
      case "JSC_INVALID_SUPER_CALL_WITH_SUGGESTION":
        return getFixForInvalidSuper(error, compiler);
      case "JSC_MISSING_REQUIRE_WARNING":
      case "JSC_MISSING_REQUIRE_STRICT_WARNING":
        return getFixForMissingRequire(error, compiler);
      case "JSC_EXTRA_REQUIRE_WARNING":
        return getFixForExtraRequire(error, compiler);
      case "JSC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME":
      case "JSC_JSDOC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME":
      case "JSC_REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME":
        // TODO(tbreisacher): Apply this fix for JSC_JSDOC_REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME.
        return getFixForReferenceToShortImportByLongName(error, compiler);
      case "JSC_REDUNDANT_NULLABILITY_MODIFIER_JSDOC":
        return getFixForRedundantNullabilityModifierJsDoc(error, compiler);
      case "JSC_MISSING_CONST_ON_CONSTANT_CASE":
        return getFixForConstantCaseErrors(error, compiler);
      default:
        return null;
    }
  }

  private static SuggestedFix getFixForRedeclaration(JSError error, AbstractCompiler compiler) {
    Node name = error.getNode();
    checkState(name.isName(), name);
    Node parent = name.getParent();
    if (!NodeUtil.isNameDeclaration(parent)) {
      return null;
    }

    SuggestedFix.Builder fix = new SuggestedFix.Builder().attachMatchedNodeInfo(name, compiler);

    if (!name.hasChildren()) {
      Node nodeToDelete = parent.hasOneChild() ? parent : error.getNode();
      return fix.setDescription("Remove redundant declaration").delete(nodeToDelete).build();
    }

    fix.setDescription("Convert redundant declaration to assignment");

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
    Matcher m = EARLY_REF.matcher(error.getDescription());
    if (m.matches()) {
      String name = m.group(1);
      Node stmt = NodeUtil.getEnclosingStatement(error.getNode());
      return new SuggestedFix.Builder()
          .attachMatchedNodeInfo(error.getNode(), compiler)
          .setDescription("Insert var declaration statement")
          .insertBefore(stmt, "var " + name + ";\n")
          .build();
    }
    return null;
  }

  private static SuggestedFix getFixForReferenceToShortImportByLongName(
      JSError error, AbstractCompiler compiler) {
    SuggestedFix.Builder fix =
        new SuggestedFix.Builder().attachMatchedNodeInfo(error.getNode(), compiler);
    NodeMetadata metadata = new NodeMetadata(compiler);
    Match match = new Match(error.getNode(), metadata);

    Matcher fullNameMatcher = FULLY_QUALIFIED_NAME.matcher(error.getDescription());
    checkState(fullNameMatcher.matches(), error.getDescription());
    String fullName = fullNameMatcher.group(1);

    Matcher shortNameMatcher = USE_SHORT_NAME.matcher(error.getDescription());
    String shortName;
    if (shortNameMatcher.matches()) {
      shortName = shortNameMatcher.group(1);
    } else {
      shortName = fullName.substring(fullName.lastIndexOf('.') + 1);
      fix.addLhsToGoogRequire(match, fullName);
    }

    String oldName =
        error.getNode().isQualifiedName()
            ? error.getNode().getQualifiedName()
            : error.getNode().getString();

    return fix.replace(
            error.getNode(),
            NodeUtil.newQName(compiler, oldName.replace(fullName, shortName)),
            compiler)
        .build();
  }

  private static ImmutableList<SuggestedFix> getFixesForImplicitNullabilityErrors(
      JSError error, AbstractCompiler compiler) {

    if (error.getNode().getSourceFileName() == null) {
      // If we don't have a source location we can't generate a valid fix.
      return ImmutableList.of();
    }

    SuggestedFix qmark =
        new SuggestedFix.Builder()
            .attachMatchedNodeInfo(error.getNode(), compiler)
            .insertBefore(error.getNode(), "?")
            .setDescription("Make nullability explicit")
            .build();
    SuggestedFix bang =
        new SuggestedFix.Builder()
            .attachMatchedNodeInfo(error.getNode(), compiler)
            .insertBefore(error.getNode(), "!")
            .setDescription("Make type non-nullable")
            .build();
    switch (error.getType().key) {
      case "JSC_NULL_MISSING_NULLABILITY_MODIFIER_JSDOC":
        // When initializer was null, we can be confident about nullability
        return ImmutableList.of(qmark);
      case "JSC_MISSING_NULLABILITY_MODIFIER_JSDOC":
        // Otherwise, the linter should assume ! is preferred over ?.
        return ImmutableList.of(bang, qmark);
      case "JSC_IMPLICITLY_NULLABLE_JSDOC":
        // The type-based check prefers ? over ! since it only warns for names that are nullable.
        return ImmutableList.of(qmark, bang);
      default:
        throw new IllegalArgumentException("Unexpected JSError Type: " + error.getType().key);
    }
  }

  private static SuggestedFix removeEmptyStatement(JSError error, AbstractCompiler compiler) {
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.getNode(), compiler)
        .setDescription("Remove empty statement")
        .deleteWithoutRemovingWhitespace(error.getNode())
        .build();
  }

  private static SuggestedFix getFixForMissingSemicolon(JSError error, AbstractCompiler compiler) {
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.getNode(), compiler)
        .insertAfter(error.getNode(), ";")
        .build();
  }

  private static SuggestedFix getFixForMissingSuper(JSError error, AbstractCompiler compiler) {
    Node constructorFunction = error.getNode().getFirstChild();
    Node body = NodeUtil.getFunctionBody(constructorFunction);
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.getNode(), compiler)
        .addChildToFront(body, "super();")
        .build();
  }

  private static SuggestedFix getFixForInvalidSuper(JSError error, AbstractCompiler compiler) {
    Matcher m = DID_YOU_MEAN.matcher(error.getDescription());
    if (m.matches()) {
      String superDotSuggestion = checkNotNull(m.group(1));
      return new SuggestedFix.Builder()
          .attachMatchedNodeInfo(error.getNode(), compiler)
          .setDescription("Call '" + superDotSuggestion + "' instead")
          .replace(error.getNode(), NodeUtil.newQName(compiler, superDotSuggestion), compiler)
          .build();
    }
    return null;
  }

  private static SuggestedFix getFixForInexistentProperty(
      JSError error, AbstractCompiler compiler) {
    Matcher m = DID_YOU_MEAN.matcher(error.getDescription());
    if (m.matches()) {
      String suggestedPropName = m.group(1);
      return new SuggestedFix.Builder()
          .attachMatchedNodeInfo(error.getNode(), compiler)
          .setDescription("Change property name to '" + suggestedPropName + "'")
          .rename(error.getNode(), suggestedPropName)
          .build();
    }
    return null;
  }

  private static SuggestedFix getFixForMissingRequire(JSError error, AbstractCompiler compiler) {
    Matcher regexMatcher = MISSING_REQUIRE.matcher(error.getDescription());
    checkState(regexMatcher.matches(), "Unexpected error description: %s", error.getDescription());
    String namespaceToRequire = regexMatcher.group(1);
    NodeMetadata metadata = new NodeMetadata(compiler);
    Match match = new Match(error.getNode(), metadata);
    SuggestedFix.Builder fix =
        new SuggestedFix.Builder()
            .attachMatchedNodeInfo(error.getNode(), compiler)
            .addGoogRequire(match, namespaceToRequire);
    if (NodeUtil.getEnclosingType(error.getNode(), Token.MODULE_BODY) != null) {
      Node nodeToReplace = null;
      if (error.getNode().isNew()) {
        nodeToReplace = error.getNode().getFirstChild();
      } else if (error.getNode().isCall()) {
        nodeToReplace = error.getNode().getFirstFirstChild();
      } else if (error.getNode().isQualifiedName()) {
        nodeToReplace = error.getNode();
      }

      if (nodeToReplace != null && nodeToReplace.matchesQualifiedName(namespaceToRequire)) {
        String shortName = getShortNameForRequire(namespaceToRequire);
        fix.replace(nodeToReplace, IR.name(shortName), compiler);
      }
    }
    return fix.build();
  }

  private static SuggestedFix getFixForExtraRequire(JSError error, AbstractCompiler compiler) {
    Node node = error.getNode();

    SuggestedFix.Builder fix = new SuggestedFix.Builder().attachMatchedNodeInfo(node, compiler);
    boolean destructuring = NodeUtil.getEnclosingType(node, Token.OBJECT_PATTERN) != null;
    if (destructuring) {
      fix.setDescription("Delete unused symbol");
      if (node.isStringKey()) {
        fix.delete(node);
      } else {
        checkState(node.getParent().isStringKey(), node.getParent());
        fix.delete(node.getParent());
      }
    } else {
      fix.setDescription("Delete extra require");
      fix.deleteWithoutRemovingWhitespaceBefore(NodeUtil.getEnclosingStatement(node));
    }
    return fix.build();
  }

  private static SuggestedFix getFixForUnsortedRequires(JSError error, AbstractCompiler compiler) {
    // TODO(tjgq): Encode enough information in the error to avoid the need to run a traversal in
    // order to produce the fix.
    Node script = NodeUtil.getEnclosingScript(error.getNode());
    CheckRequiresSorted callback = new CheckRequiresSorted(CheckRequiresSorted.Mode.COLLECT_ONLY);
    NodeTraversal.traverse(compiler, script, callback);

    if (!callback.needsFix()) {
      return null;
    }

    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(callback.getFirstNode(), compiler)
        .replaceRange(callback.getFirstNode(), callback.getLastNode(), callback.getReplacement())
        .build();
  }

  private static SuggestedFix getFixForUnsortedProvides(JSError error, AbstractCompiler compiler) {
    // TODO(tjgq): Encode enough information in the error to avoid the need to run a traversal in
    // order to produce the fix.
    Node script = NodeUtil.getEnclosingScript(error.getNode());
    CheckProvidesSorted callback = new CheckProvidesSorted(CheckProvidesSorted.Mode.COLLECT_ONLY);
    NodeTraversal.traverse(compiler, script, callback);

    if (!callback.needsFix()) {
      return null;
    }

    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(callback.getFirstNode(), compiler)
        .replaceRange(callback.getFirstNode(), callback.getLastNode(), callback.getReplacement())
        .build();
  }

  private static SuggestedFix getFixForRedundantNullabilityModifierJsDoc(
      JSError error, AbstractCompiler compiler) {
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.getNode(), compiler)
        .replaceText(error.getNode(), 1, "")
        .build();
  }
  /**
   * Suggests a fix for a constant case error.
   *
   * <p>If the variable is in a let clause, suggest adding a const. If the variable is not, suggest
   * adding a @const annotation. Don't try to adjust the JSDoc, because that can produce invalid
   * output.
   */
  private static SuggestedFix getFixForConstantCaseErrors(
      JSError error, AbstractCompiler compiler) {
    Node n = error.getNode();
    Node parent = n.getParent();
    if (!n.isName() || !NodeUtil.isNameDeclaration(parent)) {
      return null;
    }

    if (parent.isLet()) {
      // Convert to a `const` declaration
      return new SuggestedFix.Builder()
          .setDescription("Make explicitly constant")
          .attachMatchedNodeInfo(parent, compiler)
          .replaceText(parent, 3, "const")
          .build();
    }
    // Don't convert a `var` to a `const` to avoid breaking variable scoping.
    checkState(parent.isVar(), parent);
    JSDocInfo info = parent.getJSDocInfo();
    if (info == null) {
      return new SuggestedFix.Builder()
          .setDescription("Make explicitly constant")
          .attachMatchedNodeInfo(parent, compiler)
          .addOrReplaceJsDoc(parent, "/** @const */\n")
          .build();
    }
    return null;
  }
}
