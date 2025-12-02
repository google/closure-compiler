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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Streams.stream;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.errorprone.annotations.InlineMe;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NonJSDocComment;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Object representing the fixes to apply to the source code to create the
 * refactoring CL. To create a class, use the {@link Builder} class and helper
 * functions.
 */
public final class SuggestedFix {
  static enum ImportType {
    REQUIRE,
    REQUIRE_TYPE;
  }

  private final MatchedNodeInfo matchedNodeInfo;
  // Multimap of filename to a modification to that file.
  private final SetMultimap<String, CodeReplacement> replacements;

  // An optional description of the fix, to distinguish between the various possible fixes
  // for errors that have multiple fixes.
  private final @Nullable String description;

  // Alternative fixes for the same problem. The fix itself is always the first entry in this list.
  // If you cannot ask the developer which fix is appropriate, apply the first fix instead of
  // any alternatives.
  private final ImmutableList<SuggestedFix> alternatives;

  private SuggestedFix(
      MatchedNodeInfo matchedNodeInfo,
      SetMultimap<String, CodeReplacement> replacements,
      @Nullable String description,
      ImmutableList<SuggestedFix> alternatives) {
    this.matchedNodeInfo = matchedNodeInfo;
    this.replacements = replacements;
    this.description = description;
    this.alternatives =
        ImmutableList.<SuggestedFix>builder().add(this).addAll(alternatives).build();
  }

  /**
   * Returns information about the original JS Compiler Node that caused this SuggestedFix to be
   * constructed.
   */
  public MatchedNodeInfo getMatchedNodeInfo() {
    return matchedNodeInfo;
  }

  /**
   * Returns a multimap from filename to all the replacements that should be
   * applied for this given fix.
   */
  public SetMultimap<String, CodeReplacement> getReplacements() {
    return replacements;
  }

  public @Nullable String getDescription() {
    return description;
  }

  /** Get all possible fixes for this problem, including this fix. */
  public ImmutableList<SuggestedFix> getAlternatives() {
    return alternatives;
  }

  /** Get all alternative fixes, excluding this fix. */
  public ImmutableList<SuggestedFix> getNonDefaultAlternatives() {
    return alternatives.subList(1, alternatives.size());
  }

  boolean isNoOp() {
    return replacements.isEmpty();
  }

  @Override
  public String toString() {
    if (this.isNoOp()) {
      return "<no-op SuggestedFix>";
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Collection<CodeReplacement>> entry : replacements.asMap().entrySet()) {
      sb.append("Replacements for file: ").append(entry.getKey()).append("\n");
      Joiner.on("\n\n").appendTo(sb, entry.getValue());
    }
    return sb.toString();
  }

  /**
   * Builder class for {@link SuggestedFix} that contains helper functions to manipulate JS nodes.
   */
  public static final class Builder {
    private @Nullable MatchedNodeInfo matchedNodeInfo = null;
    private final ImmutableSetMultimap.Builder<String, CodeReplacement> replacements =
        ImmutableSetMultimap.builder();
    private final ImmutableList.Builder<SuggestedFix> alternatives = ImmutableList.builder();
    private @Nullable String description = null;

    /**
     * Sets the node on this SuggestedFix that caused this SuggestedFix to be built in the first
     * place.
     */
    public Builder attachMatchedNodeInfo(Node node, AbstractCompiler compiler) {
      matchedNodeInfo =
          MatchedNodeInfo.create(
              node, RefactoringUtils.isInClosurizedFile(node, new NodeMetadata(compiler)));
      return this;
    }

    public Builder addAlternative(SuggestedFix alternative) {
      checkState(
          alternative.getNonDefaultAlternatives().isEmpty(),
          "Alternative SuggestedFix must have no alternatives of their own.");
      alternatives.add(alternative);
      return this;
    }

    /**
     * Replaces text starting at the given node position.
     */
    Builder replaceText(Node node, int length, String newContent) {
      int startPosition = node.getSourceOffset();
      replacements.put(
          node.getSourceFileName(), CodeReplacement.create(startPosition, length, newContent));
      return this;
    }

    /**
     * Inserts a new node as the first child of the provided node.
     */
    public Builder addChildToFront(Node parentNode, String content) {
      checkState(
          parentNode.isBlock(), "addChildToFront is only supported for BLOCK statements.");
      int startPosition = parentNode.getSourceOffset() + 1;
      replacements.put(
          parentNode.getSourceFileName(), CodeReplacement.create(startPosition, 0, "\n" + content));
      return this;
    }

    /**
     * Inserts the text after the given node
     */
    public Builder insertAfter(Node node, String text) {
      int position = node.getSourceOffset() + node.getLength();
      replacements.put(node.getSourceFileName(), CodeReplacement.create(position, 0, text));
      return this;
    }

    /**
     * Inserts a new node before the provided node.
     */
    public Builder insertBefore(Node nodeToInsertBefore, Node n, AbstractCompiler compiler) {
      return insertBefore(nodeToInsertBefore, n, compiler, "");
    }

    Builder insertBefore(
        Node nodeToInsertBefore, Node n, AbstractCompiler compiler, String sortKey) {
      return insertBefore(nodeToInsertBefore, generateCode(compiler, n), sortKey);
    }

    /**
     * Inserts a string before the provided node. This is useful for inserting
     * comments into a file since the JS Compiler doesn't currently support
     * printing comments.
     */
    public Builder insertBefore(Node nodeToInsertBefore, String content) {
      return insertBefore(nodeToInsertBefore, content, "");
    }

    private Builder insertBefore(Node nodeToInsertBefore, String content, String sortKey) {
      int startPosition = getStartPositionForNodeConsideringComments(nodeToInsertBefore);
      Preconditions.checkNotNull(nodeToInsertBefore.getSourceFileName(),
          "No source file name for node: %s", nodeToInsertBefore);
      replacements.put(
          nodeToInsertBefore.getSourceFileName(),
          CodeReplacement.create(startPosition, 0, content, sortKey));
      return this;
    }

    /**
     * Deletes a node and its contents from the source file. If the node is a child of a
     * block or top level statement, this will also delete the whitespace before the node.
     */
    public Builder delete(Node n) {
      return delete(n, true);
    }

    /** Deletes a node and its contents from the source file. */
    private Builder delete(Node n, boolean deleteWhitespaceBefore) {
      int startPosition = getStartPositionForNodeConsideringComments(n);
      int startOffsetWithoutComments = n.getSourceOffset();
      int length = (startOffsetWithoutComments - startPosition) + n.getLength();

      if (n.getNext() != null
          && NodeUtil.getBestJSDocInfo(n.getNext()) == null
          && n.getNext().getNonJSDocComment() == null) {
        length = n.getNext().getSourceOffset() - startPosition;
      }

      // Variable declarations and string keys require special handling since the node doesn't
      // contain enough if it has a child. The NAME node in a var/let/const declaration doesn't
      // include its child in its length, and the code needs to know how to delete the commas.
      // The same is true for string keys in object literals and object destructuring patterns.
      // TODO(mknichel): Move this logic and the start position logic to a helper function
      // so that it can be reused in other methods.
      if ((n.isName() && NodeUtil.isNameDeclaration(n.getParent())) || n.isStringKey()) {
        if (n.getNext() != null) {
          length = getStartPositionForNodeConsideringComments(n.getNext()) - startPosition;
        } else if (n.hasChildren()) {
          Node child = n.getFirstChild();
          length = (child.getSourceOffset() + child.getLength()) - startPosition;
        }
        if (n.getParent().getLastChild() == n && n != n.getParent().getFirstChild()) {
          Node previousSibling = n.getPrevious();
          if (previousSibling.hasChildren()) {
            Node child = previousSibling.getFirstChild();
            int startPositionDiff = startPosition - (child.getSourceOffset() + child.getLength());
            startPosition -= startPositionDiff;
            length += startPositionDiff;
          } else {
            int startPositionDiff =
                startPosition - (previousSibling.getSourceOffset() + previousSibling.getLength());
            startPosition -= startPositionDiff;
            length += startPositionDiff;
          }
        }
      }

      Node parent = n.getParent();
      if (deleteWhitespaceBefore
          && parent != null
          && (parent.isScript() || parent.isBlock())) {
        Node previousSibling = n.getPrevious();
        if (previousSibling != null) {
          int previousSiblingEndPosition =
              previousSibling.getSourceOffset() + previousSibling.getLength();
          length += (startPosition - previousSiblingEndPosition);
          startPosition = previousSiblingEndPosition;
        }
      }
      replacements.put(n.getSourceFileName(), CodeReplacement.create(startPosition, length, ""));
      return this;
    }

    /** Deletes a node and its contents from the source file. */
    public Builder deleteWithoutRemovingWhitespaceBefore(Node n) {
      return delete(n, false);
    }

    /** Deletes a node without touching any surrounding whitespace. */
    public Builder deleteWithoutRemovingWhitespace(Node n) {
      replacements.put(
          n.getSourceFileName(), CodeReplacement.create(n.getSourceOffset(), n.getLength(), ""));
      return this;
    }

    /**
     * Renames a given node to the provided name.
     * @param n The node to rename.
     * @param name The new name for the node.
     */
    public Builder rename(Node n, String name) {
      return rename(n, name, false);
    }

    /**
     * Renames a given node to the provided name.
     *
     * @param n The node to rename.
     * @param name The new name for the node.
     * @param replaceNameSubtree True to replace the entire name subtree below the node. The default
     *     is to replace just the last property in the node with the new name. For instance, if
     *     {@code replaceNameSubtree} is false, then {@code this.foo()} will be renamed to {@code
     *     this.bar()}. However, if it is true, it will be renamed to {@code bar()}.
     */
    public Builder rename(Node n, String name, boolean replaceNameSubtree) {
      final Node range;
      switch (n.getToken()) {
        case CALL, TAGGED_TEMPLATELIT -> {
          return this.rename(n.getFirstChild(), name, replaceNameSubtree);
        }
        case GETPROP -> range = replaceNameSubtree ? subtreeRangeOfIdentifier(n) : n;
        case STRINGLIT -> {
          checkState(n.getParent().isGetProp(), n);
          range = n;
        }
        case STRING_KEY, NAME -> range = n;
        default ->
            throw new UnsupportedOperationException(
                "Rename is not implemented for this node type: " + n);
      }

      replacements.put(
          range.getSourceFileName(),
          CodeReplacement.create(range.getSourceOffset(), range.getLength(), name));
      return this;
    }

    /**
     * Replaces a range of nodes with the given content.
     */
    public Builder replaceRange(Node first, Node last, String newContent) {
      checkState(first.getParent() == last.getParent());

      int start = getStartPositionForNodeConsideringComments(first);
      if (start == 0) {
        // if there are file-level comments at the top of the file, we do not wish to remove them
        start = first.getSourceOffset();
      }
      int end = last.getSourceOffset() + last.getLength();
      int length = end - start;
      replacements.put(
          first.getSourceFileName(), CodeReplacement.create(start, length, newContent));
      return this;
    }

    /**
     * Replaces the provided node with new node in the source file.
     */
    public Builder replace(Node original, Node newNode, AbstractCompiler compiler) {
      Node parent = original.getParent();
      // EXPR_RESULT nodes will contain the trailing semicolons, but the child node
      // will not. Replace the EXPR_RESULT node to ensure that the semicolons are
      // correct in the final output.
      if (parent != null && parent.isExprResult()) {
        original = parent;
      }
      // TODO(mknichel): Move this logic to CodePrinter.
      String newCode = generateCode(compiler, newNode);
      // The generated code may contain a trailing newline but that is never wanted.
      if (newCode.endsWith("\n")) {
        newCode = newCode.substring(0, newCode.length() - 1);
      }

      // Most replacements don't need the semicolon in the new generated code - however, some
      // statements that are blocks or expressions will need the semicolon.
      boolean needsSemicolon =
          parent != null
              && (parent.isExprResult()
                  || parent.isBlock()
                  || parent.isScript()
                  || parent.isModuleBody());
      if (newCode.endsWith(";") && !needsSemicolon) {
        newCode = newCode.substring(0, newCode.length() - 1);
      }

      // If the replacement has lower precedence then we may need to add parentheses.
      if (parent != null && IR.mayBeExpression(parent)) {
        Node replacement = newNode;
        while ((replacement.isBlock() || replacement.isScript() || replacement.isModuleBody())
            && replacement.hasOneChild()) {
          replacement = replacement.getOnlyChild();
        }
        if (replacement.isExprResult()) {
          replacement = replacement.getOnlyChild();
        }
        if (IR.mayBeExpression(replacement)) {
          int outer = NodeUtil.precedence(parent.getToken());
          int inner = NodeUtil.precedence(original.getToken());
          int newInner = NodeUtil.precedence(replacement.getToken());
          if (newInner < NodeUtil.precedence(Token.CALL) && newInner <= outer && inner >= outer) {
            newCode = "(" + newCode + ")";
          }
        }
      }

      Node range = original;
      if (original.isGetProp()) {
        range = subtreeRangeOfIdentifier(original);
      }

      replacements.put(
          range.getSourceFileName(),
          CodeReplacement.create(range.getSourceOffset(), range.getLength(), newCode));
      return this;
    }

    /**
     * Adds a cast of the given type to the provided node.
     */
    public Builder addCast(Node n, AbstractCompiler compiler, String type) {
      // TODO(mknichel): Figure out the best way to output the typecast.
      replacements.put(
          n.getSourceFileName(),
          CodeReplacement.create(
              n.getSourceOffset(),
              n.getLength(),
              "/** @type {" + type + "} */ (" + generateCode(compiler, n) + ")"));
      return this;
    }

    /**
     * Removes a cast from the given node.
     */
    public Builder removeCast(Node n, AbstractCompiler compiler) {
      checkArgument(n.isCast());
      JSDocInfo jsDoc = n.getJSDocInfo();
      replacements.put(
          n.getSourceFileName(),
          CodeReplacement.create(
              jsDoc.getOriginalCommentPosition(),
              n.getFirstChild().getSourceOffset() - jsDoc.getOriginalCommentPosition(),
              ""));
      replacements.put(
          n.getSourceFileName(),
          CodeReplacement.create(n.getSourceOffset() + n.getLength() - 1, /* length= */ 1, ""));
      return this;
    }

    /**
     * Adds or replaces the JS Doc for the given node.
     */
    public Builder addOrReplaceJsDoc(Node n, String newJsDoc) {
      int offset = n.getSourceOffset();
      int length = 0;

      if (n.isGetProp()) {
        offset = subtreeRangeOfIdentifier(n).getSourceOffset();
      }

      JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);
      if (jsDoc != null) {
        offset = jsDoc.getOriginalCommentPosition();
        length = jsDoc.getOriginalCommentString().length();
      }

      replacements.put(n.getSourceFileName(), CodeReplacement.create(offset, length, newJsDoc));
      return this;
    }

    /**
     * Changes the JS Doc Type of the given node.
     */
    public Builder changeJsDocType(Node n, AbstractCompiler compiler, String type) {
      Node typeNode = JsDocInfoParser.parseTypeString(type);
      Preconditions.checkNotNull(typeNode, "Invalid type: %s", type);
      JSTypeExpression typeExpr = new JSTypeExpression(typeNode, "jsflume");
      JSType newJsType = typeExpr.evaluate(null, compiler.getTypeRegistry());
      if (newJsType == null) {
        throw new RuntimeException("JS Compiler does not recognize type: " + type);
      }

      // TODO(mknichel): Use the JSDocInfoParser to find the end of the type declaration. This
      // would also handle multiple lines, and record types (which contain '{')

      // Only "@type" allows type names without "{}"
      replaceTypePattern(n, type, Pattern.compile(
          "@(type) *\\{?[^@\\s}]+\\}?"));

      // Text following other annotations may be a comment, not a type.
      replaceTypePattern(n, type, Pattern.compile(
          "@(export|package|private|protected|public|const|return) *\\{[^}]+\\}"));

      return this;
    }

    // The pattern supplied here should have one matching group, the annotation with
    // associated the type expression, the entire pattern should match the annotation and
    // the type expression to be replaced.
    private void replaceTypePattern(Node n, String type, Pattern pattern) {
      JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
      Preconditions.checkNotNull(info, "Node %s does not have JS Doc associated with it.", n);
      String originalComment = info.getOriginalCommentString();
      int originalPosition = info.getOriginalCommentPosition();
      if (originalComment != null) {
        Matcher m = pattern.matcher(originalComment);
        while (m.find()) {
          replacements.put(
              n.getSourceFileName(),
              CodeReplacement.create(
                  originalPosition + m.start(),
                  m.end() - m.start(),
                  "@" + m.group(1) + " {" + type + "}"));
        }
      }
    }

    /**
     * Inserts arguments into an existing function call.
     */
    public Builder insertArguments(Node n, int position, String... args) {
      checkArgument(n.isCall(), "insertArguments is only applicable to function call nodes.");
      int startPosition;
      Node argument = n.getSecondChild();
      int i = 0;
      while (argument != null && i < position) {
        argument = argument.getNext();
        i++;
      }
      if (argument == null) {
        checkArgument(
            position == i, "The specified position must be less than the number of arguments.");
        startPosition = n.getSourceOffset() + n.getLength() - 1;
      } else {
        startPosition = getStartPositionForNodeConsideringComments(argument);
      }

      String newContent = Joiner.on(", ").join(args);
      if (argument != null) {
        newContent += ", ";
      } else if (i > 0) {
        newContent = ", " + newContent;
      }
      replacements.put(n.getSourceFileName(), CodeReplacement.create(startPosition, 0, newContent));

      return this;
    }

    /**
     * Deletes an argument from an existing function call, including any JS doc that precedes it.
     * WARNING: If jsdoc erroneously follows the argument, it will not be removed as the parser
     *     considers the comment to belong to the next argument.
     */
    public Builder deleteArgument(Node n, int position) {
      checkArgument(
          n.isCall() || n.isNew(), "deleteArgument is only applicable to function call nodes.");

      // A CALL node's first child is the name of the function being called, and subsequent children
      // are the arguments being passed to that function.
      int numArguments = n.getChildCount() - 1;
      checkState(
          numArguments > 0, "deleteArgument() cannot be used on a function call with no arguments");
      checkArgument(
          position >= 0 && position < numArguments,
          "The specified position must be less than the number of arguments.");
      Node argument = n.getSecondChild();

      // Points at the first position in the code we will remove.
      int startOfArgumentToRemove = -1;
      // Points one past the last position in the code we will remove.
      int endOfArgumentToRemove = -1;
      int i = 0;
      while (argument != null) {
        // If we are removing the first argument, we remove from the start of it (including any
        // jsdoc).  Otherwise, we remove from the end of the previous argument (to remove the comma
        // and any whitespace).

        // If we are removing the first argument and it's not the only argument, we remove to the
        // beginning of the next argument (to remove the comma and any whitespace).  Otherwise we
        // remove to the end of the argument.
        if (i < position) {
          startOfArgumentToRemove = argument.getSourceOffset() + argument.getLength();
        } else if (i == position) {
          if (position == 0) {
            startOfArgumentToRemove = getStartPositionForNodeConsideringComments(argument);
          }
          endOfArgumentToRemove = argument.getSourceOffset() + argument.getLength();
        } else if (i > position) {
          if (position == 0) {
            endOfArgumentToRemove = argument.getSourceOffset();
          }
          // We have all the information we need to remove the argument, break early.
          break;
        }

        argument = argument.getNext();
        i++;
      }

      // Remove the argument by replacing it with an empty string.
      int lengthOfArgumentToRemove = endOfArgumentToRemove - startOfArgumentToRemove;
      replacements.put(
          n.getSourceFileName(),
          CodeReplacement.create(startOfArgumentToRemove, lengthOfArgumentToRemove, ""));
      return this;
    }

    private static Node createImportNode(
        ImportType importType, @Nullable String alias, String namespace) {
      final String requireFlavor =
          switch (importType) {
            case REQUIRE -> "require";
            case REQUIRE_TYPE -> "requireType";
          };

      Node callNode = IR.call(IR.getprop(IR.name("goog"), requireFlavor), IR.string(namespace));

      if (alias != null) {
        return IR.constNode(IR.name(alias), callNode);
      } else {
        return IR.exprResult(callNode);
      }
    }

    public Builder addGoogRequire(Match m, String namespace, ScriptMetadata scriptMetadata) {
      return addImport(m, namespace, ImportType.REQUIRE, scriptMetadata);
    }

    public Builder addGoogRequireType(Match m, String namespace, ScriptMetadata scriptMetadata) {
      return addImport(m, namespace, ImportType.REQUIRE_TYPE, scriptMetadata);
    }

    /** Adds a goog.require/requireType for the given namespace if it does not already exist. */
    public Builder addImport(
        Match m, String namespace, ImportType importType, ScriptMetadata scriptMetadata) {
      final String alias;
      if (scriptMetadata.supportsRequireAliases()) {
        String existingAlias = scriptMetadata.getAlias(namespace);
        if (existingAlias != null) {
          /*
           * Each fix muct be independently valid, so go through the steps of adding a require even
           * if one may already exist or have been added by another fix.
           */
          alias = existingAlias;
        } else if (namespace.indexOf('.') == -1) {
          /*
           * For unqualified names, the exisiting references will still be valid so long as we keep
           * the same name for the alias.
           */
          alias = namespace;
        } else {
          alias =
              stream(RequireAliasGenerator.over(namespace))
                  .filter((a) -> !scriptMetadata.usesName(a))
                  .findFirst()
                  .orElseThrow(AssertionError::new);
        }
        scriptMetadata.addAlias(namespace, alias);
      } else {
        alias = null;
      }

      NodeMetadata metadata = m.getMetadata();
      Node existingNode = findGoogRequireNode(m.getNode(), metadata, namespace);

      if (existingNode != null) {
        // TODO(b/139953612): Destructured goog.requires are not supported.

        // Add an alias to a naked require if allowed in this file.
        if (existingNode.isExprResult() && alias != null) {
          Node newNode;
          // Replace goog.forwardDeclare with the appropriate alternative
          if (NodeUtil.isCallTo(existingNode.getFirstChild(), "goog.forwardDeclare")) {
            newNode = createImportNode(importType, alias, namespace);
          } else {
            newNode = IR.constNode(IR.name(alias), existingNode.getFirstChild().cloneTree());
          }
          replace(existingNode, newNode, m.getMetadata().getCompiler());
          scriptMetadata.addAlias(namespace, alias);
        }

        return this;
      }

      // Find the right goog.require node to insert this after.
      Node script = scriptMetadata.getScript();
      if (script.getFirstChild().isModuleBody()) {
        script = script.getFirstChild();
      }

      Node lastModuleOrProvideNode = null;
      Node lastGoogRequireNode = null;
      Node nodeToInsertBefore = null;
      Node child = script.getFirstChild();
      while (child != null) {
        if (Matchers.googModule().matches(child, metadata)) {
          lastModuleOrProvideNode = child;
        }
        if (NodeUtil.isExprCall(child)) {
          // TODO(mknichel): Replace this logic with a function argument
          // Matcher when it exists.
          Node grandchild = child.getFirstChild();
          if (Matchers.googModuleOrProvide().matches(grandchild, metadata)) {
            lastModuleOrProvideNode = grandchild;
          } else if (Matchers.googRequirelike().matches(grandchild, metadata)) {
            lastGoogRequireNode = grandchild;
            if (grandchild.getLastChild().isStringLit()
                && namespace.compareTo(grandchild.getLastChild().getString()) < 0) {
              nodeToInsertBefore = child;
              break;
            }
          }
        } else if (NodeUtil.isNameDeclaration(child)
            && child.getFirstFirstChild() != null
            && Matchers.googRequirelike().matches(child.getFirstFirstChild(), metadata)) {
          lastGoogRequireNode = child.getFirstFirstChild();
          String requireName = child.getFirstChild().getString();
          String originalName = child.getFirstChild().getOriginalName();
          if (originalName != null) {
            requireName = originalName;
          }
          if (alias.compareTo(requireName) < 0) {
            nodeToInsertBefore = child;
            break;
          }
        }
        child = child.getNext();
      }

      Node newImportNode = createImportNode(importType, alias, namespace);
      if (nodeToInsertBefore == null) {
        // The file has goog.provide or goog.require nodes but they come before
        // the new goog.require node alphabetically.
        if (lastModuleOrProvideNode != null || lastGoogRequireNode != null) {
          Node nodeToInsertAfter =
              lastGoogRequireNode != null ? lastGoogRequireNode : lastModuleOrProvideNode;
          int startPosition =
              nodeToInsertAfter.getSourceOffset() + nodeToInsertAfter.getLength() + 2;
          replacements.put(
              nodeToInsertAfter.getSourceFileName(),
              CodeReplacement.create(
                  startPosition,
                  0,
                  generateCode(m.getMetadata().getCompiler(), newImportNode),
                  namespace));
          return this;
        } else {
          // The file has no goog.provide or goog.require nodes.
          if (script.hasChildren()) {
            nodeToInsertBefore = script.getFirstChild();
          } else {
            replacements.put(
                script.getSourceFileName(),
                CodeReplacement.create(
                    0, 0, generateCode(m.getMetadata().getCompiler(), newImportNode), namespace));
            return this;
          }
        }
      }

      return insertBefore(
          nodeToInsertBefore, newImportNode, m.getMetadata().getCompiler(), namespace);
    }

    /**
     * Removes a goog.require for the given namespace to the file if it
     * already exists.
     */
    public Builder removeGoogRequire(Match m, String namespace) {
      Node googRequireNode = findGoogRequireNode(m.getNode(), m.getMetadata(), namespace);
      if (googRequireNode != null) {
        return deleteWithoutRemovingWhitespaceBefore(googRequireNode);
      }
      return this;
    }

    /**
     * Find the goog.require node for the given namespace (or null if there isn't one). If there is
     * more than one:
     *
     * <ul>
     *   <li>If there is at least one standalone goog.require, this will return the first standalone
     *       goog.require.
     *   <li>If not, this will return the first goog.require.
     * </ul>
     */
    private static @Nullable Node findGoogRequireNode(
        Node n, NodeMetadata metadata, String namespace) {
      Node script = metadata.getCompiler().getScriptNode(n.getSourceFileName());
      if (script.getFirstChild().isModuleBody()) {
        script = script.getFirstChild();
      }

      for (Node child = script.getFirstChild(); child != null; child = child.getNext()) {
        if (NodeUtil.isExprCall(child)
            && Matchers.googRequirelike(namespace).matches(child.getFirstChild(), metadata)) {
          return child;
        }
      }

      for (Node child = script.getFirstChild(); child != null; child = child.getNext()) {
        if (NodeUtil.isNameDeclaration(child)
            // TODO(b/139953612): respect destructured goog.requires
            && !child.getFirstChild().isDestructuringLhs()
            && child.getFirstChild().getLastChild() != null
            && Matchers.googRequirelike(namespace)
                .matches(child.getFirstChild().getLastChild(), metadata)) {
          return child;
        }
      }
      return null;
    }

    private static Node subtreeRangeOfIdentifier(Node n) {
      checkState(n.isGetProp(), "Support other identifier nodes");

      Node leftmost = n;
      while (leftmost.hasChildren()) {
        leftmost = leftmost.getFirstChild();
      }

      Node result = IR.empty();
      result.setStaticSourceFile(n.getStaticSourceFile());
      result.setLinenoCharno(leftmost.getLineno(), leftmost.getCharno());
      result.setLength(n.getLength() + (n.getSourceOffset() - leftmost.getSourceOffset()));
      return result;
    }

    public String generateCode(AbstractCompiler compiler, Node node) {
      // TODO(mknichel): Fix all the formatting problems with this code.
      // How does this play with goog.scope?
      if (node.isBlock()) {
        // Avoid printing the {}'s
        node.setToken(Token.SCRIPT);
      }
      CompilerOptions compilerOptions = new CompilerOptions();
      compilerOptions.setPreferSingleQuotes(true);
      compilerOptions.setUseOriginalNamesInOutput(true);
      compilerOptions.setPreserveNonJSDocComments(true);
      // We're refactoring existing code, so no need to escape values inside strings.
      compilerOptions.setTrustedStrings(true);
      return new CodePrinter.Builder(node)
          .setCompilerOptions(compilerOptions)
          .setTypeRegistry(compiler.getTypeRegistry())
          .setPrettyPrint(true)
          .setLineBreak(true)
          .setOutputTypes(true)
          .build();
    }

    public Builder setDescription(String description) {
      this.description = description;
      return this;
    }

    public SuggestedFix build() {
      return new SuggestedFix(
          matchedNodeInfo, replacements.build(), description, alternatives.build());
    }

  }

  /**
   * Information about the node that was matched for the suggested fix. This information can be used
   * later on when processing the SuggestedFix.
   *
   * <p>NOTE: Since this class can be retained for a long time when running refactorings over large
   * blobs of code, it's important that it does not contain any memory intensive objects in order to
   * keep memory to a reasonable amount.
   */
  public record MatchedNodeInfo(
      String sourceFilename, int lineno, int charno, boolean inClosurizedFile) {
    public MatchedNodeInfo {
      requireNonNull(sourceFilename, "sourceFilename");
    }

    @InlineMe(replacement = "this.sourceFilename()")
    public String getSourceFilename() {
      return sourceFilename();
    }

    @InlineMe(replacement = "this.lineno()")
    public int getLineno() {
      return lineno();
    }

    @InlineMe(replacement = "this.charno()")
    public int getCharno() {
      return charno();
    }

    @InlineMe(replacement = "this.inClosurizedFile()")
    public boolean isInClosurizedFile() {
      return inClosurizedFile();
    }

    static MatchedNodeInfo create(Node node, boolean closurized) {
      return new MatchedNodeInfo(
          NodeUtil.getSourceName(node), node.getLineno(), node.getCharno(), closurized);
    }
  }

  /**
   * Helper function to return the source offset of this node considering that JSDoc comments,
   * non-JDDoc comments, or both may or may not be attached.
   */
  private static int getStartPositionForNodeConsideringComments(Node node) {
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(node);
    NonJSDocComment associatedNonJSDocComment = node.getNonJSDocComment();
    int start = node.getSourceOffset();
    if (jsdoc != null) {
      start = jsdoc.getOriginalCommentPosition();
    }
    if (associatedNonJSDocComment != null) {
      start = min(start, associatedNonJSDocComment.getStartPosition().getOffset());
    }
    return start;
  }
}
