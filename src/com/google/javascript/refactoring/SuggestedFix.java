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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;


/**
 * Object representing the fixes to apply to the source code to create the
 * refactoring CL. To create a class, use the {@link Builder} class and helper
 * functions.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
public final class SuggestedFix {

  private final MatchedNodeInfo matchedNodeInfo;
  // Multimap of filename to a modification to that file.
  private final SetMultimap<String, CodeReplacement> replacements;

  // An optional description of the fix, to distinguish between the various possible fixes
  // for errors that have multiple fixes.
  @Nullable private final String description;

  private SuggestedFix(
      MatchedNodeInfo matchedNodeInfo,
      SetMultimap<String, CodeReplacement> replacements,
      @Nullable String description) {
    this.matchedNodeInfo = matchedNodeInfo;
    this.replacements = replacements;
    this.description = description;
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

  @Nullable public String getDescription() {
    return description;
  }

  @Override public String toString() {
    if (replacements.isEmpty()) {
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
   * Builder class for {@link SuggestedFix} that contains helper functions to
   * manipulate JS nodes.
   */
  public static final class Builder {
    private MatchedNodeInfo matchedNodeInfo = null;
    private final ImmutableSetMultimap.Builder<String, CodeReplacement> replacements =
        ImmutableSetMultimap.builder();
    private String description = null;

    /**
     * Sets the node on this SuggestedFix that caused this SuggestedFix to be built in the first
     * place.
     */
    public Builder attachMatchedNodeInfo(Node node, AbstractCompiler compiler) {
      matchedNodeInfo =
          new MatchedNodeInfo(
              NodeUtil.getSourceName(node),
              node.getLineno(),
              node.getCharno(),
              isInClosurizedFile(node, new NodeMetadata(compiler)));
      return this;
    }

    /**
     * Inserts a new node as the first child of the provided node.
     */
    public Builder addChildToFront(Node parentNode, String content) {
      Preconditions.checkState(parentNode.isBlock(),
          "addChildToFront is only supported for BLOCK statements.");
      int startPosition = parentNode.getSourceOffset() + 1;
      replacements.put(
          parentNode.getSourceFileName(),
          new CodeReplacement(startPosition, 0, "\n" + content));
      return this;
    }

    /**
     * Inserts the text after the given node
     */
    public Builder insertAfter(Node node, String text) {
      int position = node.getSourceOffset() + node.getLength();
      replacements.put(
          node.getSourceFileName(),
          new CodeReplacement(position, 0, text));
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
      int startPosition = nodeToInsertBefore.getSourceOffset();
      JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(nodeToInsertBefore);
      if (jsDoc != null) {
        startPosition = jsDoc.getOriginalCommentPosition();
      }
      Preconditions.checkNotNull(nodeToInsertBefore.getSourceFileName(),
          "No source file name for node: %s", nodeToInsertBefore);
      replacements.put(
          nodeToInsertBefore.getSourceFileName(),
          new CodeReplacement(startPosition, 0, content, sortKey));
      return this;
    }

    /**
     * Deletes a node and its contents from the source file. If the node is a child of a
     * block or top level statement, this will also delete the whitespace before the node.
     */
    public Builder delete(Node n) {
      return delete(n, true);
    }

    /**
     * Deletes a node and its contents from the source file.
     */
    public Builder deleteWithoutRemovingWhitespaceBefore(Node n) {
      return delete(n, false);
    }

    /**
     * Deletes a node and its contents from the source file.
     */
    private Builder delete(Node n, boolean deleteWhitespaceBefore) {
      int startPosition = n.getSourceOffset();
      int length;
      if (n.getNext() != null && NodeUtil.getBestJSDocInfo(n.getNext()) == null) {
        length = n.getNext().getSourceOffset() - startPosition;
      } else {
        length = n.getLength();
      }
      JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);
      if (jsDoc != null) {
        length += (startPosition - jsDoc.getOriginalCommentPosition());
        startPosition = jsDoc.getOriginalCommentPosition();
      }
      // Variable declarations require special handling since the NAME node doesn't contain enough
      // information if the variable is declared in a multi-variable declaration. The NAME node
      // in a VAR declaration doesn't include its child in its length if there is an inline
      // assignment, and the code needs to know how to delete the commas. See SuggestedFixTest for
      // more information.
      // TODO(mknichel): Move this logic and the start position logic to a helper function
      // so that it can be reused in other methods.
      if (n.isName() && n.getParent().isVar()) {
        if (n.getNext() != null) {
          length = n.getNext().getSourceOffset() - startPosition;
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
            int startPositionDiff = startPosition - (
                previousSibling.getSourceOffset() + previousSibling.getLength());
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
      replacements.put(n.getSourceFileName(), new CodeReplacement(startPosition, length, ""));
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
     * @param n The node to rename.
     * @param name The new name for the node.
     * @param replaceEntireName True to replace the entire name of the node. The
     *     default is to replace just the last property in the node with the new
     *     name. For instance, if {@code replaceEntireName} is false, then
     *     {@code this.foo()} will be renamed to {@code this.bar()}. However, if
     *     it is true, it will be renamed to {@code bar()}.
     */
    public Builder rename(Node n, String name, boolean replaceEntireName) {
      Node nodeToRename = null;
      if (n.isCall() || n.isTaggedTemplateLit()) {
        Node child = n.getFirstChild();
        nodeToRename = child;
        if (!replaceEntireName && child.isGetProp()) {
          nodeToRename = child.getLastChild();
        }
      } else if (n.isGetProp()) {
        nodeToRename = n.getLastChild();
        if (replaceEntireName) {
          // Trace up from the property access to the root.
          while (nodeToRename.getParent().isGetProp()) {
            nodeToRename = nodeToRename.getParent();
          }
        }
      } else if (n.isStringKey()) {
        nodeToRename = n;
      } else if (n.isString()) {
        Preconditions.checkState(n.getParent().isGetProp(), n);
        nodeToRename = n;
      } else {
        // TODO(mknichel): Implement the rest of this function.
        throw new UnsupportedOperationException(
            "Rename is not implemented for this node type: " + n);
      }
      replacements.put(
          nodeToRename.getSourceFileName(),
          new CodeReplacement(nodeToRename.getSourceOffset(), nodeToRename.getLength(), name));
      return this;
    }

    /**
     * Replaces a range of nodes with the given content.
     */
    public Builder replaceRange(Node first, Node last, String newContent) {
      Preconditions.checkState(first.getParent() == last.getParent());

      int start;
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(first);
      if (jsdoc == null) {
        start = first.getSourceOffset();
      } else {
        start = jsdoc.getOriginalCommentPosition();
      }

      int end = last.getSourceOffset() + last.getLength();
      int length = end - start;
      replacements.put(first.getSourceFileName(), new CodeReplacement(start, length, newContent));
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
          parent != null && (parent.isExprResult() || parent.isBlock() || parent.isScript());
      if (newCode.endsWith(";") && !needsSemicolon) {
        newCode = newCode.substring(0, newCode.length() - 1);
      }
      replacements.put(
          original.getSourceFileName(),
          new CodeReplacement(original.getSourceOffset(), original.getLength(), newCode));
      return this;
    }

    /**
     * Adds a cast of the given type to the provided node.
     */
    public Builder addCast(Node n, AbstractCompiler compiler, String type) {
      // TODO(mknichel): Figure out the best way to output the typecast.
      replacements.put(
          n.getSourceFileName(),
          new CodeReplacement(
              n.getSourceOffset(),
              n.getLength(),
              "/** @type {" + type + "} */ (" + generateCode(compiler, n) + ")"));
      return this;
    }

    /**
     * Removes a cast from the given node.
     */
    public Builder removeCast(Node n, AbstractCompiler compiler) {
      Preconditions.checkArgument(n.isCast());
      JSDocInfo jsDoc = n.getJSDocInfo();
      replacements.put(
          n.getSourceFileName(),
          new CodeReplacement(
              jsDoc.getOriginalCommentPosition(),
              n.getFirstChild().getSourceOffset() - jsDoc.getOriginalCommentPosition(),
              ""));
      replacements.put(
          n.getSourceFileName(),
          new CodeReplacement(
              n.getSourceOffset() + n.getLength() - 1,
              1 /* length */,
              ""));
      return this;
    }

    /**
     * Adds or replaces the JS Doc for the given node.
     */
    public Builder addOrReplaceJsDoc(Node n, String newJsDoc) {
      int startPosition = n.getSourceOffset();
      int length = 0;
      JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);
      if (jsDoc != null) {
        startPosition = jsDoc.getOriginalCommentPosition();
        length = n.getSourceOffset() - jsDoc.getOriginalCommentPosition();
      }
      replacements.put(n.getSourceFileName(), new CodeReplacement(startPosition, length, newJsDoc));
      return this;
    }

    /**
     * Changes the JS Doc Type of the given node.
     */
    public Builder changeJsDocType(Node n, AbstractCompiler compiler, String type) {
      JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
      Preconditions.checkNotNull(info, "Node %s does not have JS Doc associated with it.", n);
      Node typeNode = JsDocInfoParser.parseTypeString(type);
      Preconditions.checkNotNull(typeNode, "Invalid type: %s", type);
      JSTypeExpression typeExpr = new JSTypeExpression(typeNode, "jsflume");
      JSType newJsType = typeExpr.evaluate(null, compiler.getTypeRegistry());
      if (newJsType == null) {
        throw new RuntimeException("JS Compiler does not recognize type: " + type);
      }

      String originalComment = info.getOriginalCommentString();
      int originalPosition = info.getOriginalCommentPosition();

      // TODO(mknichel): Support multiline @type annotations.
      Pattern typeDocPattern = Pattern.compile(
          "@(type|private|protected|public|const|return) *\\{?[^\\s}]+\\}?");
      Matcher m = typeDocPattern.matcher(originalComment);
      while (m.find()) {
        replacements.put(
            n.getSourceFileName(),
            new CodeReplacement(
                originalPosition + m.start(),
                m.end() - m.start(),
                "@" + m.group(1) + " {" + type + "}"));
      }

      return this;
    }

    /**
     * Inserts arguments into an existing function call.
     */
    public Builder insertArguments(Node n, int position, String... args) {
      Preconditions.checkArgument(
          n.isCall(), "insertArguments is only applicable to function call nodes.");
      int startPosition;
      Node argument = n.getSecondChild();
      int i = 0;
      while (argument != null && i < position) {
        argument = argument.getNext();
        i++;
      }
      if (argument == null) {
        Preconditions.checkArgument(
            position == i, "The specified position must be less than the number of arguments.");
        startPosition = n.getSourceOffset() + n.getLength() - 1;
      } else {
        JSDocInfo jsDoc = argument.getJSDocInfo();
        if (jsDoc != null) {
          // Remove any cast or associated JS Doc if it exists.
          startPosition = jsDoc.getOriginalCommentPosition();
        } else {
          startPosition = argument.getSourceOffset();
        }
      }

      String newContent = Joiner.on(", ").join(args);
      if (argument != null) {
        newContent += ", ";
      } else if (i > 0) {
        newContent = ", " + newContent;
      }
      replacements.put(n.getSourceFileName(), new CodeReplacement(startPosition, 0, newContent));

      return this;
    }

    /**
     * Deletes an argument from an existing function call, including any JS doc that precedes it.
     * WARNING: If jsdoc erroneously follows the argument, it will not be removed as the parser
     *     considers the comment to belong to the next argument.
     */
    public Builder deleteArgument(Node n, int position) {
      Preconditions.checkArgument(
          n.isCall(), "deleteArgument is only applicable to function call nodes.");

      // A CALL node's first child is the name of the function being called, and subsequent children
      // are the arguments being passed to that function.
      int numArguments = n.getChildCount() - 1;
      Preconditions.checkState(numArguments > 0,
          "deleteArgument() cannot be used on a function call with no arguments");
      Preconditions.checkArgument(position >= 0 && position < numArguments,
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
            startOfArgumentToRemove = argument.getSourceOffset();

            // If we have a prefix jsdoc, back up further and remove that too.
            JSDocInfo jsDoc = argument.getJSDocInfo();
            if (jsDoc != null) {
              int jsDocPosition = jsDoc.getOriginalCommentPosition();
              if (jsDocPosition < startOfArgumentToRemove) {
                startOfArgumentToRemove = jsDocPosition;
              }
            }
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
      replacements.put(n.getSourceFileName(),
          new CodeReplacement(startOfArgumentToRemove, lengthOfArgumentToRemove, ""));
      return this;
    }

    /**
     * Adds a goog.require for the given namespace to the file if it does not
     * already exist.
     */
    public Builder addGoogRequire(Match m, String namespace) {
      Node node = m.getNode();
      NodeMetadata metadata = m.getMetadata();
      Node existingNode = findGoogRequireNode(m.getNode(), metadata, namespace);
      if (existingNode != null) {
        return this;
      }

      // Find the right goog.require node to insert this after.
      Node script = NodeUtil.getEnclosingScript(node);
      if (script == null) {
        return this;
      }
      if (script.getFirstChild().isModuleBody()) {
        script = script.getFirstChild();
      }

      Node googRequireNode = IR.call(
          IR.getprop(IR.name("goog"), IR.string("require")),
          IR.string(namespace));

      // The name that will be used on the LHS, if the require is added using the shorthand form.
      String shortName = namespace.substring(namespace.lastIndexOf('.') + 1);

      if (script.isModuleBody()) {
        googRequireNode = IR.constNode(IR.name(shortName), googRequireNode);
      } else {
        googRequireNode = IR.exprResult(googRequireNode);
      }

      Node lastModuleOrProvideNode = null;
      Node lastGoogRequireNode = null;
      Node nodeToInsertBefore = null;
      Node child = script.getFirstChild();
      while (child != null) {
        if (NodeUtil.isExprCall(child)) {
          // TODO(mknichel): Replace this logic with a function argument
          // Matcher when it exists.
          Node grandchild = child.getFirstChild();
          if (Matchers.googModuleOrProvide().matches(grandchild, metadata)) {
            lastModuleOrProvideNode = grandchild;
          } else if (Matchers.googRequire().matches(grandchild, metadata)) {
            lastGoogRequireNode = grandchild;
            if (grandchild.getLastChild().isString()
                && namespace.compareTo(grandchild.getLastChild().getString()) < 0) {
              nodeToInsertBefore = child;
              break;
            }
          }
        } else if (NodeUtil.isNameDeclaration(child)
            && Matchers.googRequire().matches(child.getFirstFirstChild(), metadata)) {
          if (shortName.compareTo(child.getFirstChild().getString()) < 0) {
            nodeToInsertBefore = child;
            break;
          }
        }
        child = child.getNext();
      }
      if (nodeToInsertBefore == null) {
        // The file has goog.provide or goog.require nodes but they come before
        // the new goog.require node alphabetically.
        if (lastModuleOrProvideNode != null || lastGoogRequireNode != null) {
          Node nodeToInsertAfter =
              lastGoogRequireNode != null ? lastGoogRequireNode : lastModuleOrProvideNode;
          int startPosition =
              nodeToInsertAfter.getSourceOffset() + nodeToInsertAfter.getLength() + 2;
          replacements.put(nodeToInsertAfter.getSourceFileName(), new CodeReplacement(
              startPosition,
              0,
              generateCode(m.getMetadata().getCompiler(), googRequireNode)));
          return this;
        } else {
          // The file has no goog.provide or goog.require nodes.
          if (script.getFirstChild() != null) {
            nodeToInsertBefore = script.getFirstChild();
          } else {
            replacements.put(script.getSourceFileName(), new CodeReplacement(
                0, 0, generateCode(m.getMetadata().getCompiler(), googRequireNode)));
            return this;
          }
        }
      }

      return insertBefore(
          nodeToInsertBefore, googRequireNode, m.getMetadata().getCompiler(), namespace);
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

    private Node findGoogRequireNode(Node n, NodeMetadata metadata, String namespace) {
      Node script = NodeUtil.getEnclosingScript(n);
      if (script.getFirstChild().isModuleBody()) {
        script = script.getFirstChild();
      }

      if (script != null) {
        Node child = script.getFirstChild();
        while (child != null) {
          if ((NodeUtil.isExprCall(child)
                  && Matchers.googRequire(namespace).matches(child.getFirstChild(), metadata))
              || (NodeUtil.isNameDeclaration(child)
                  && child.getFirstFirstChild() != null && Matchers.googRequire(namespace)
                      .matches(child.getFirstFirstChild(), metadata))) {
            return child;
          }
          child = child.getNext();
        }
      }
      return null;
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
      compilerOptions.setLineLengthThreshold(80);
      compilerOptions.setUseOriginalNamesInOutput(true);
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
      return new SuggestedFix(matchedNodeInfo, replacements.build(), description);
    }

    /** Looks for a goog.require(), goog.provide() or goog.module() call in the fix's file. */
    private static boolean isInClosurizedFile(Node node, NodeMetadata metadata) {
      Node script = NodeUtil.getEnclosingScript(node);

      if (script == null) {
        return false;
      }

      Node child = script.getFirstChild();
      while (child != null) {
        if (NodeUtil.isExprCall(child)) {
          if (Matchers.googRequire().matches(child.getFirstChild(), metadata)) {
            return true;
          }
          // goog.require or goog.module.
        } else if (child.isVar() && child.getBooleanProp(Node.IS_NAMESPACE)) {
          return true;
        }
        child = child.getNext();
      }
      return false;
    }
  }

  /**
   * Information about the node that was matched for the suggested fix. This information can be
   * used later on when processing the SuggestedFix.
   *
   * <p>NOTE: Since this class can be retained for a long time when running refactorings over large
   * blobs of code, it's important that it does not contain any memory intensive objects in order to
   * keep memory to a reasonable amount.
   */
  public static class MatchedNodeInfo {
    private final String sourceFilename;
    private final int lineno;
    private final int charno;
    private final boolean isInClosurizedFile;

    MatchedNodeInfo(String sourceFilename, int lineno, int charno, boolean isInClosurizedFile) {
      this.sourceFilename = sourceFilename;
      this.lineno = lineno;
      this.charno = charno;
      this.isInClosurizedFile = isInClosurizedFile;
    }

    public String getSourceFilename() {
      return sourceFilename;
    }

    public int getLineno() {
      return lineno;
    }

    public int getCharno() {
      return charno;
    }

    public boolean isInClosurizedFile() {
      return isInClosurizedFile;
    }
  }
}
