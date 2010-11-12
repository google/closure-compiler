/*
 * Copyright 2004 The Closure Compiler Authors.
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

import javax.annotation.Nullable;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Iterator;

/**
 * ReplaceMessages replaces user-visible messages with alternatives.
 * It uses Google specific JsMessageVisitor implementation.
 *
 */
class ReplaceMessages extends JsMessageVisitor {
  private final MessageBundle bundle;
  private final boolean strictReplacement;

  static final DiagnosticType BUNDLE_DOES_NOT_HAVE_THE_MESSAGE =
      DiagnosticType.error("JSC_BUNDLE_DOES_NOT_HAVE_THE_MESSAGE",
          "Message with id = {0} cound not be found in replacement bundle");

  ReplaceMessages(AbstractCompiler compiler, MessageBundle bundle,
      boolean checkDuplicatedMessages, JsMessage.Style style,
      boolean strictReplacement) {

    super(compiler, checkDuplicatedMessages, style, bundle.idGenerator());

    this.bundle = bundle;
    this.strictReplacement = strictReplacement;
  }

  @Override
  protected void processJsMessage(JsMessage message,
      JsMessageDefinition definition) {

    // Get the replacement.
    JsMessage replacement = bundle.getMessage(message.getId());
    if (replacement == null) {
      if (strictReplacement) {
        compiler.report(JSError.make(message.getSourceName(),
            definition.getMessageNode(), BUNDLE_DOES_NOT_HAVE_THE_MESSAGE,
            message.getId()));
        // Fallback to the default message
        return;
      } else {
        // In case if it is not a strict replacement we could leave original
        // message.
        replacement = message;
      }
    }

    // Replace the message.
    Node newValue;
    Node msgNode = definition.getMessageNode();
    try {
      newValue = getNewValueNode(replacement, msgNode);
    } catch (MalformedException e) {
      compiler.report(JSError.make(message.getSourceName(),
          e.getNode(), MESSAGE_TREE_MALFORMED, e.getMessage()));
      newValue = msgNode;
    }

    if (newValue != msgNode) {
      definition.getMessageParentNode().replaceChild(msgNode, newValue);
    }
  }

  /**
   * Constructs a node representing a message's value, or, if possible, just
   * modifies {@code origValueNode} so that it accurately represents the
   * message's value.
   *
   * @param message  a message
   * @param origValueNode  the message's original value node
   * @return a Node that can replace {@code origValueNode}
   *
   * @throws MalformedException if the passed node's subtree structure is
   *   not as expected
   */
  private Node getNewValueNode(JsMessage message, Node origValueNode)
      throws MalformedException {
    switch (origValueNode.getType()) {
      case Token.FUNCTION:
        // The message is a function. Modify the function node.
        updateFunctionNode(message, origValueNode);
        return origValueNode;
      case Token.STRING:
        // The message is a simple string. Modify the string node.
        origValueNode.setString(message.toString());
        return origValueNode;
      case Token.ADD:
        // The message is a simple string. Create a string node.
        return Node.newString(message.toString());
      case Token.CALL:
        // The message is a function call. Replace it with a string expression.
        return replaceCallNode(message, origValueNode);
      default:
        throw new MalformedException(
            "Expected FUNCTION, STRING, or ADD node; found: " +
                origValueNode.getType(), origValueNode);
    }
  }

  /**
   * Updates the descendants of a FUNCTION node to represent a message's value.
   * <p>
   * The tree looks something like:
   * <pre>
   * function
   *  |-- name
   *  |-- lp
   *  |   |-- name <arg1>
   *  |    -- name <arg2>
   *   -- block
   *      |
   *       --return
   *           |
   *            --add
   *               |-- string foo
   *                -- name <arg1>
   * </pre>
   *
   * @param message  a message
   * @param functionNode  the message's original FUNCTION value node
   *
   * @throws MalformedException if the passed node's subtree structure is
   *         not as expected
   */
  private void updateFunctionNode(JsMessage message, Node functionNode)
      throws MalformedException {
    checkNode(functionNode, Token.FUNCTION);
    Node nameNode = functionNode.getFirstChild();
    checkNode(nameNode, Token.NAME);
    Node argListNode = nameNode.getNext();
    checkNode(argListNode, Token.LP);
    Node oldBlockNode = argListNode.getNext();
    checkNode(oldBlockNode, Token.BLOCK);

    Iterator<CharSequence> iterator = message.parts().iterator();
    Node valueNode = iterator.hasNext()
        ? constructAddOrStringNode(iterator, argListNode)
        : Node.newString("");
    Node newBlockNode = new Node(Token.BLOCK,
        new Node(Token.RETURN, valueNode));

    functionNode.replaceChild(oldBlockNode, newBlockNode);
  }

  /**
   * Creates a parse tree corresponding to the remaining message parts in
   * an iteration. The result will contain only STRING nodes, NAME nodes
   * (corresponding to placeholder references), and/or ADD nodes used to
   * combine the other two types.
   *
   * @param partsIterator  an iterator over message parts
   * @param argListNode  an LP node whose children are valid placeholder names
   * @return the root of the constructed parse tree
   *
   * @throws MalformedException if {@code partsIterator} contains a
   *   placeholder reference that does not correspond to a valid argument in
   *   the arg list
   */
  private Node constructAddOrStringNode(Iterator<CharSequence> partsIterator,
                                        Node argListNode)
      throws MalformedException {
    CharSequence part = partsIterator.next();
    Node partNode = null;
    if (part instanceof JsMessage.PlaceholderReference) {
      JsMessage.PlaceholderReference phRef =
          (JsMessage.PlaceholderReference) part;

      for (Node node : argListNode.children()) {
        if (node.getType() == Token.NAME) {
          String arg = node.getString();

          // We ignore the case here because the transconsole only supports
          // uppercase placeholder names, but function arguments in javascript
          // code can have mixed case.
          if (arg.equalsIgnoreCase(phRef.getName())) {
            partNode = Node.newString(Token.NAME, arg);
          }
        }
      }

      if (partNode == null) {
        throw new MalformedException(
            "Unrecognized message placeholder referenced: " + phRef.getName(),
            argListNode);
      }
    } else {
      // The part is just a string literal.
      partNode = Node.newString(part.toString());
    }

    if (partsIterator.hasNext()) {
      return new Node(Token.ADD, partNode,
                      constructAddOrStringNode(partsIterator, argListNode));
    } else {
      return partNode;
    }
  }

  /**
   * Replaces a CALL node with an inlined message value.
   *  <p>
   * The call tree looks something like:
   * <pre>
   * call
   *  |-- getprop
   *  |   |-- name 'goog'
   *  |   +-- string 'getMsg'
   *  |
   *  |-- string 'Hi {$userName}! Welcome to {$product}.'
   *  +-- objlit
   *      |-- string 'userName'
   *      |-- name 'someUserName'
   *      |-- string 'product'
   *      +-- call
   *          +-- name 'getProductName'
   * <pre>
   * <p>
   * For that example, we'd return:
   * <pre>
   * add
   *  |-- string 'Hi '
   *  +-- add
   *      |-- name someUserName
   *      +-- add
   *          |-- string '! Welcome to '
   *          +-- add
   *              |-- call
   *              |   +-- name 'getProductName'
   *              +-- string '.'
   * </pre>
   * @param message  a message
   * @param callNode  the message's original CALL value node
   * @return a STRING node, or an ADD node that does string concatenation, if
   *   the message has one or more placeholders
   *
   * @throws MalformedException if the passed node's subtree structure is
   *   not as expected
   */
  private Node replaceCallNode(JsMessage message, Node callNode)
      throws MalformedException {
    checkNode(callNode, Token.CALL);
    Node getPropNode = callNode.getFirstChild();
    checkNode(getPropNode, Token.GETPROP);
    Node stringExprNode = getPropNode.getNext();
    checkStringExprNode(stringExprNode);
    Node objLitNode = stringExprNode.getNext();

    // Build the replacement tree.
    return constructStringExprNode(message.parts().iterator(), objLitNode);
  }

  /**
   * Creates a parse tree corresponding to the remaining message parts in an
   * iteration. The result consists of one or more STRING nodes, placeholder
   * replacement value nodes (which can be arbitrary expressions), and ADD
   * nodes.
   *
   * @param parts  an iterator over message parts
   * @param objLitNode  an OBJLIT node mapping placeholder names to values
   * @return the root of the constructed parse tree
   *
   * @throws MalformedException if {@code parts} contains a placeholder
   *   reference that does not correspond to a valid placeholder name
   */
  private Node constructStringExprNode(Iterator<CharSequence> parts,
      Node objLitNode) throws MalformedException {

    CharSequence part = parts.next();
    Node partNode = null;
    if (part instanceof JsMessage.PlaceholderReference) {
      JsMessage.PlaceholderReference phRef =
          (JsMessage.PlaceholderReference) part;

      for (Node key = objLitNode.getFirstChild(); key != null;
           key = key.getNext()) {
        if (key.getString().equals(phRef.getName())) {
          Node valueNode = key.getFirstChild();
          partNode = valueNode.cloneTree();
        }
      }

      if (partNode == null) {
        throw new MalformedException(
            "Unrecognized message placeholder referenced: " + phRef.getName(),
            objLitNode);
      }
    } else {
      // The part is just a string literal.
      partNode = Node.newString(part.toString());
    }

    if (parts.hasNext()) {
      return new Node(Token.ADD, partNode,
          constructStringExprNode(parts, objLitNode));
    } else {
      return partNode;
    }
  }

  /**
   * Checks a node's type.
   *
   * @throws MalformedException if the node is null or the wrong type
   */
  private void checkNode(@Nullable Node node, int type) throws MalformedException {
    if (node == null) {
      throw new MalformedException(
          "Expected node type " + type + "; found: null", node);
    }
    if (node.getType() != type) {
      throw new MalformedException(
          "Expected node type " + type + "; found: " + node.getType(), node);
    }
  }

  /**
   * Checks that a node is a valid string expression (either a string literal
   * or a concatenation of string literals).
   *
   * @throws IllegalArgumentException if the node is null or the wrong type
   */
  private void checkStringExprNode(@Nullable Node node) {
    if (node == null) {
      throw new IllegalArgumentException("Expected a string; found: null");
    }
    switch (node.getType()) {
      case Token.STRING:
        break;
      case Token.ADD:
        Node c = node.getFirstChild();
        checkStringExprNode(c);
        checkStringExprNode(c.getNext());
        break;
      default:
        throw new IllegalArgumentException(
            "Expected a string; found: " + node.getType());
    }
  }
}
