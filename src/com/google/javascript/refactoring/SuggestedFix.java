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
import com.google.javascript.rhino.jstype.JSType;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Object representing the fixes to apply to the source code to create the
 * refactoring CL. To create a class, use the {@link Builder} class and helper
 * functions.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
public final class SuggestedFix {

  // Multimap of filename to a modification to that file.
  private final SetMultimap<String, CodeReplacement> replacements;

  private SuggestedFix(SetMultimap<String, CodeReplacement> replacements) {
    this.replacements = replacements;
  }

  /**
   * Returns a multimap from filename to all the replacements that should be
   * applied for this given fix.
   */
  public SetMultimap<String, CodeReplacement> getReplacements() {
    return replacements;
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Collection<CodeReplacement>> entry : replacements.asMap().entrySet()) {
      sb.append("Replacements for file: " + entry.getKey() + "\n");
      Joiner.on("\n").appendTo(sb, entry.getValue());
    }
    return sb.toString();
  }

  /**
   * Builder class for {@link SuggestedFix} that contains helper functions to
   * manipulate JS nodes.
   */
  public static final class Builder {
    private final ImmutableSetMultimap.Builder<String, CodeReplacement> replacements =
        ImmutableSetMultimap.builder();

    /**
     * Inserts a new node before the provided node.
     */
    public Builder insertBefore(Node nodeToInsertBefore, Node n, AbstractCompiler compiler) {
      return insertBefore(nodeToInsertBefore, generateCode(compiler, n));
    }

    /**
     * Inserts a string before the provided node. This is useful for inserting
     * comments into a file since the JS Compiler doesn't currently support
     * printing comments.
     */
    public Builder insertBefore(Node nodeToInsertBefore, String content) {
      int startPosition = nodeToInsertBefore.getSourceOffset();
      // TODO(mknichel): This case is not covered by NodeUtil.getBestJSDocInfo
      JSDocInfo jsDoc = nodeToInsertBefore.isExprResult()
          ? nodeToInsertBefore.getFirstChild().getJSDocInfo()
          : nodeToInsertBefore.getJSDocInfo();
      if (jsDoc != null) {
        startPosition = jsDoc.getOriginalCommentPosition();
      }
      Preconditions.checkNotNull(nodeToInsertBefore.getSourceFileName(),
          "No source file name for node: %s", nodeToInsertBefore);
      replacements.put(
          nodeToInsertBefore.getSourceFileName(),
          new CodeReplacement(startPosition, 0, content));
      return this;
    }

    /**
     * Deletes a node and its contents from the source file.
     */
    public Builder delete(Node n) {
      int startPosition = n.getSourceOffset();
      int length = n.getLength();
      // TODO(mknichel): This case is not covered by NodeUtil.getBestJSDocInfo
      JSDocInfo jsDoc = n.isExprResult() ? n.getFirstChild().getJSDocInfo() : n.getJSDocInfo();
      if (jsDoc != null) {
        length = n.getLength() + (startPosition - jsDoc.getOriginalCommentPosition());
        startPosition = jsDoc.getOriginalCommentPosition();
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
      if (n.isCall()) {
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
        Preconditions.checkState(n.getParent().isGetProp());
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
     * Replaces the provided node with new node in the source file.
     */
    public Builder replace(Node original, Node newNode, AbstractCompiler compiler) {
      Node parent = original.getParent();
      // EXPR_RESULT nodes will contain the trailing semicolons, but the child node
      // will not. Replace the EXPR_RESULT node to ensure that the semicolons are
      // correct in the final output.
      if (original.getParent().isExprResult()) {
        original = original.getParent();
      }
      // TODO(mknichel): Move this logic to CodePrinter.
      String newCode = generateCode(compiler, newNode);
      // The generated code may contain a trailing newline but that is never wanted.
      if (newCode.endsWith("\n")) {
        newCode = newCode.substring(0, newCode.length() - 1);
      }
      // Most replacements don't need the semicolon in the new generated code - however, some
      // statements that are blocks or expressions will need the semicolon.
      boolean needsSemicolon = parent.isExprResult() || parent.isBlock() || parent.isScript();
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
      Node child = n.getFirstChild();
      replacements.put(
          n.getSourceFileName(),
          new CodeReplacement(
              jsDoc.getOriginalCommentPosition(),
              n.getSourceOffset() + n.getLength() - jsDoc.getOriginalCommentPosition(),
              generateCode(compiler, child)));
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
      Node argument = n.getFirstChild().getNext();
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
        startPosition = argument.getSourceOffset();
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
      Node googRequireNode = IR.exprResult(IR.call(
          IR.getprop(IR.name("goog"), IR.string("require")),
          IR.string(namespace)));

      // Find the right goog.require node to insert this after.
      Node script = node.getParent();
      while (script != null && !script.isScript()) {
        script = script.getParent();
      }
      if (script == null) {
        return this;
      }
      Node lastGoogProvideNode = null;
      Node lastGoogRequireNode = null;
      Node nodeToInsertBefore = null;
      Node child = script.getFirstChild();
      while (child != null) {
        if (child.isExprResult() && child.getFirstChild().isCall()) {
          // TODO(mknichel): Replace this logic with a function argument
          // Matcher when it exists.
          Node grandchild = child.getFirstChild();
          if (Matchers.functionCall("goog.provide").matches(grandchild, metadata)) {
            lastGoogProvideNode = grandchild;
          } else if (Matchers.functionCall("goog.require").matches(grandchild, metadata)) {
            lastGoogRequireNode = grandchild;
            if (grandchild.getLastChild().isString()
                && namespace.compareTo(grandchild.getLastChild().getString()) < 0) {
              nodeToInsertBefore = child;
              break;
            }
          }
        }
        child = child.getNext();
      }
      if (nodeToInsertBefore == null) {
        // The file has goog.provide or goog.require nodes but they come before
        // the new goog.require node alphabetically.
        if (lastGoogProvideNode != null || lastGoogRequireNode != null) {
          Node nodeToInsertAfter =
              lastGoogRequireNode != null ? lastGoogRequireNode : lastGoogProvideNode;
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

      return insertBefore(nodeToInsertBefore, googRequireNode, m.getMetadata().getCompiler());
    }

    /**
     * Removes a goog.require for the given namespace to the file if it
     * already exists.
     */
    public Builder removeGoogRequire(Match m, String namespace) {
      Node googRequireNode = findGoogRequireNode(m.getNode(), m.getMetadata(), namespace);
      if (googRequireNode != null) {
        return delete(googRequireNode);
      }
      return this;
    }

    private Node findGoogRequireNode(Node n, NodeMetadata metadata, String namespace) {
      Node script = n.getParent();
      while (script != null && !script.isScript()) {
        script = script.getParent();
      }

      if (script != null) {
        Node child = script.getFirstChild();
        while (child != null) {
          if (child.isExprResult() && child.getFirstChild().isCall()) {
            // TODO(mknichel): Replace this logic with a function argument
            // Matcher when it exists.
            Node grandchild = child.getFirstChild();
            if (Matchers.functionCall("goog.require").matches(child.getFirstChild(), metadata)
                && grandchild.getLastChild().isString()
                && namespace.equals(grandchild.getLastChild().getString())) {
              return child;
            }
          }
          child = child.getNext();
        }
      }
      return null;
    }

    public String generateCode(AbstractCompiler compiler, Node node) {
      // TODO(mknichel): Fix all the formatting problems with this code.
      // How does this play with goog.scope?
      CompilerOptions compilerOptions = new CompilerOptions();
      compilerOptions.setPreferSingleQuotes(true);
      compilerOptions.setLineLengthThreshold(80);
      return new CodePrinter.Builder(node)
          .setCompilerOptions(compilerOptions)
          .setTypeRegistry(compiler.getTypeRegistry())
          .setPrettyPrint(true)
          .setLineBreak(true)
          .setOutputTypes(true)
          .build();
    }

    public SuggestedFix build() {
      return new SuggestedFix(replacements.build());
    }
  }
}
