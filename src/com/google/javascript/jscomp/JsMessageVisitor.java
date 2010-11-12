/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.base.CaseFormat;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.JsMessage.Builder;
import static com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.*;
import java.util.regex.*;

import javax.annotation.Nullable;

/**
 * Traverses across parsed tree and finds I18N messages. Then it passes it to
 * {@link JsMessageVisitor#processJsMessage(JsMessage, JsMessageDefinition)}.
 *
 * @author anatol@google.com (Anatol Pomazau)
 */
abstract class JsMessageVisitor extends AbstractPostOrderCallback
    implements CompilerPass {

  private static final String MSG_FUNCTION_NAME = "goog.getMsg";

  static final DiagnosticType MESSAGE_HAS_NO_DESCRIPTION =
      DiagnosticType.warning("JSC_MSG_HAS_NO_DESCRIPTION",
          "Message {0} has no description. Add @desc JsDoc tag.");

  static final DiagnosticType MESSAGE_HAS_NO_TEXT =
      DiagnosticType.warning("JSC_MSG_HAS_NO_TEXT",
          "Message value of {0} is just an empty string. "
              + "Empty messages are forbidden." );

  static final DiagnosticType MESSAGE_TREE_MALFORMED =
      DiagnosticType.error("JSC_MSG_TREE_MALFORMED",
          "Message parse tree malformed. {0}");

  static final DiagnosticType MESSAGE_HAS_NO_VALUE =
      DiagnosticType.error("JSC_MSG_HAS_NO_VALUE",
          "message node {0} has no value");

  static final DiagnosticType MESSAGE_DUPLICATE_KEY =
      DiagnosticType.error("JSC_MSG_KEY_DUPLICATED",
          "duplicate message variable name found for {0}, " +
              "initial definition {1}:{2}");

  static final DiagnosticType MESSAGE_NODE_IS_ORPHANED =
      DiagnosticType.warning("JSC_MSG_ORPHANED_NODE", MSG_FUNCTION_NAME +
          "() function could be used only with MSG_* property or variable");

  static final DiagnosticType MESSAGE_NOT_INITIALIZED_USING_NEW_SYNTAX =
      DiagnosticType.error("JSC_MSG_NOT_INITIALIZED_USING_NEW_SYNTAX",
          "message not initialized using " + MSG_FUNCTION_NAME);

  private static final String PH_JS_PREFIX = "{$";
  private static final String PH_JS_SUFFIX = "}";

  static final String MSG_PREFIX = "MSG_";

  /**
   * Pattern for unnamed messages.
   * <p>
   * All jsmessages in js code should have unique name but messages in generated
   * code (i.e. from soy template) could have duplicated message names. Later we
   * replace the message names with ids constructed as a hash of the message
   * content.
   * <p>
   * <link href="http://code.google.com/p/closure-templates/">
   * Soy</link> generates messages with names MSG_UNNAMED_<NUMBER> . This
   * pattern recognizes such messages.
   */
  private static final Pattern MSG_UNNAMED_PATTERN =
      Pattern.compile("MSG_UNNAMED_\\d+");

  private static final Pattern CAMELCASE_PATTERN =
      Pattern.compile("[a-z][a-zA-Z\\d]*[_\\d]*");

  static final String HIDDEN_DESC_PREFIX = "@hidden";

  // For old-style js messages
  private static final String DESC_SUFFIX = "_HELP";

  private final boolean needToCheckDuplications;
  private final JsMessage.Style style;
  private final JsMessage.IdGenerator idGenerator;
  final AbstractCompiler compiler;

  /**
   * The names encountered associated with their defining node and source. We
   * use it for tracking duplicated message ids in the source code.
   */
  private final Map<String, MessageLocation> messageNames =
      Maps.newHashMap();

  /**
   * List of found goog.getMsg call nodes.
   *
   * When we visit goog.getMsg() node we add a pair node:sourcename and later
   * when we visit its parent we remove this pair. All nodes that are left at
   * the end of traversing are orphaned nodes. It means have no corresponding
   * var or property node.
   */
  private final Map<Node, String> googMsgNodes = Maps.newHashMap();

  private final CheckLevel checkLevel;

  /**
   * Creates js message visitor.
   *
   * @param compiler the compiler instance
   * @param needToCheckDuplications whether to check duplicated messages in
   *        traversed
   * @param style style that should be used during parsing
   * @param idGenerator generator that used for creating unique ID for the
   *        message
   */
  JsMessageVisitor(AbstractCompiler compiler,
      boolean needToCheckDuplications,
      JsMessage.Style style, JsMessage.IdGenerator idGenerator) {

    this.compiler = compiler;
    this.needToCheckDuplications = needToCheckDuplications;
    this.style = style;
    this.idGenerator = idGenerator;

    checkLevel = (style == JsMessage.Style.CLOSURE)
        ? CheckLevel.ERROR : CheckLevel.WARNING;

    // TODO(anatol): add flag that decides whether to process UNNAMED messages.
    // Some projects would not want such functionality (unnamed) as they dont
    // use SOY templates.
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);

    for (Map.Entry<Node, String> msgNode : googMsgNodes.entrySet()) {
      compiler.report(JSError.make(msgNode.getValue(), msgNode.getKey(),
          checkLevel, MESSAGE_NODE_IS_ORPHANED));
    }
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {
    String messageKey;
    boolean isVar;
    Node msgNode, msgNodeParent;

    switch (node.getType()) {
      case Token.NAME:
        // var MSG_HELLO = 'Message'
        if ((parent != null) && (parent.getType() == Token.VAR)) {
          messageKey = node.getString();
          isVar = true;
        } else {
          return;
        }

        msgNode = node.getFirstChild();
        msgNodeParent = node;
        break;
      case Token.ASSIGN:
        // somenamespace.someclass.MSG_HELLO = 'Message'
        isVar = false;

        Node getProp = node.getFirstChild();
        if (getProp.getType() != Token.GETPROP) {
          return;
        }

        Node propNode = getProp.getLastChild();

        messageKey = propNode.getString();
        msgNode = node.getLastChild();
        msgNodeParent = node;
        break;
      case Token.CALL:
        // goog.getMsg()
        if (MSG_FUNCTION_NAME.equals(node.getFirstChild().getQualifiedName())) {
          googMsgNodes.put(node, traversal.getSourceName());
        }
        return;
      default:
        return;
    }

    // Is this a message name?
    boolean isNewStyleMessage =
        msgNode != null && msgNode.getType() == Token.CALL;
    if (!isMessageName(messageKey, isNewStyleMessage)) {
      return;
    }

    if (msgNode == null) {
      compiler.report(
          traversal.makeError(node, MESSAGE_HAS_NO_VALUE, messageKey));
      return;
    }

    // Just report a warning if a qualified messageKey that looks like a message
    // (e.g. "a.b.MSG_X") doesn't use goog.getMsg().
    if (isNewStyleMessage) {
      googMsgNodes.remove(msgNode);
    } else if (style != JsMessage.Style.LEGACY) {
      compiler.report(traversal.makeError(node, checkLevel,
          MESSAGE_NOT_INITIALIZED_USING_NEW_SYNTAX));
    }

    boolean isUnnamedMsg = isUnnamedMessageName(messageKey);

    Builder builder = new Builder(
        isUnnamedMsg ? null : messageKey);
    builder.setSourceName(traversal.getSourceName());

    try {
      if (isVar) {
        extractMessageFromVariable(builder, node, parent, parent.getParent());
      } else {
        extractMessageFromProperty(builder, node.getFirstChild(), node);
      }
    } catch (MalformedException ex) {
      compiler.report(traversal.makeError(ex.getNode(),
          MESSAGE_TREE_MALFORMED, ex.getMessage()));
      return;
    }

    JsMessage extractedMessage = builder.build(idGenerator);

    // If asked to check named internal messages.
    if (needToCheckDuplications 
        && !isUnnamedMsg 
        && !extractedMessage.isExternal()) {
      checkIfMessageDuplicated(traversal.getSourceName(), messageKey, msgNode);
    }    
    
    if (extractedMessage.isEmpty()) {
      // value of the message is an empty string. Translators do not like it.
      compiler.report(traversal.makeError(node, MESSAGE_HAS_NO_TEXT,
          messageKey));
    }

    // New-style messages must have descriptions. We don't emit a warning
    // for legacy-style messages, because there are thousands of
    // them in legacy code that are not worth the effort to fix, since they've
    // already been translated anyway.
    String desc = extractedMessage.getDesc();
    if (isNewStyleMessage
        && (desc == null || desc.trim().isEmpty())
        && !extractedMessage.isExternal()) {
      compiler.report(traversal.makeError(node, MESSAGE_HAS_NO_DESCRIPTION,
          messageKey));
    }

    JsMessageDefinition msgDefinition = new JsMessageDefinition(
        node, msgNode, msgNodeParent);
    processJsMessage(extractedMessage, msgDefinition);
  }

  /**
   * Checks if message already processed. If so - it generates 'message
   * duplicated' compiler error.
   *
   * @param sourceName the source file name
   * @param msgName the name of the message
   * @param msgNode the node that represents js message
   */
  private void checkIfMessageDuplicated(String sourceName, String msgName,
      Node msgNode) {
    if (messageNames.containsKey(msgName)) {
      MessageLocation location = messageNames.get(msgName);
      compiler.report(JSError.make(sourceName, msgNode, MESSAGE_DUPLICATE_KEY,
          msgName, location.sourceName, Integer.toString(location.lineNo)));
    } else {
      MessageLocation location =
          new MessageLocation(sourceName, msgNode.getLineno());
      messageNames.put(msgName, location);
    }
  }

  /**
   * Creates a {@link JsMessage} for a js message defined using a js variable
   * declaration (e.g <code>var MSG_X = ...;</code>).
   *
   * @param builder the message builder
   * @param nameNode a NAME node for a js message variable
   * @param parentNode a VAR node, parent of {@code nameNode}
   * @param grandParentNode the grandparent of {@code nameNode}. This node is
   *        only used to get meta data about the message that might be
   *        surrounding it (e.g. a message description). This argument may be
   *        null if the meta data is not needed.
   * @throws MalformedException if {@code varNode} does not
   *         correspond to a valid js message VAR node
   */
  private void extractMessageFromVariable(
      Builder builder, Node nameNode, Node parentNode,
      @Nullable Node grandParentNode) throws MalformedException {

    // Determine the message's value
    Node valueNode = nameNode.getFirstChild();
    switch (valueNode.getType()) {
      case Token.STRING:
      case Token.ADD:
        maybeInitMetaDataFromJsDocOrHelpVar(builder, parentNode,
            grandParentNode);
        builder.appendStringPart(extractStringFromStringExprNode(valueNode));
        break;
      case Token.FUNCTION:
        maybeInitMetaDataFromJsDocOrHelpVar(builder, parentNode,
            grandParentNode);
        extractFromFunctionNode(builder, valueNode);
        break;
      case Token.CALL:
        maybeInitMetaDataFromJsDoc(builder, parentNode);
        extractFromCallNode(builder, valueNode);
        break;
      default:
        throw new MalformedException("Cannot parse value of message "
            + builder.getKey(), valueNode);
    }
  }

  /**
   * Creates a {@link JsMessage} for a js message defined using an assignment to
   * a qualified name (e.g <code>a.b.MSG_X = goog.getMsg(...);</code>).
   *
   * @param builder the message builder
   * @param getPropNode a GETPROP node in a js message assignment
   * @param assignNode an ASSIGN node, parent of {@code getPropNode}.
   * @throws MalformedException if {@code getPropNode} does not
   *         correspond to a valid js message node
   */
  private void extractMessageFromProperty(
      Builder builder, Node getPropNode, Node assignNode)
      throws MalformedException {
    Node callNode = getPropNode.getNext();
    maybeInitMetaDataFromJsDoc(builder, assignNode);
    extractFromCallNode(builder, callNode);
  }

  /**
   * Initializes the meta data in a JsMessage by examining the nodes just before
   * and after a message VAR node.
   *
   * @param builder the message builder whose meta data will be initialized
   * @param varNode the message VAR node
   * @param parentOfVarNode {@code varNode}'s parent node
   */
  private void maybeInitMetaDataFromJsDocOrHelpVar(
      Builder builder, Node varNode, @Nullable Node parentOfVarNode)
      throws MalformedException {

    // First check description in @desc
    if (maybeInitMetaDataFromJsDoc(builder, varNode)) {
      return;
    }

    // Check the preceding node for meta data
    if ((parentOfVarNode != null) &&
        maybeInitMetaDataFromHelpVar(builder,
            parentOfVarNode.getChildBefore(varNode))) {
      return;
    }

    // Check the subsequent node for meta data
    maybeInitMetaDataFromHelpVar(builder, varNode.getNext());
  }

  /**
   * Initializes the meta data in a JsMessage by examining a node just before or
   * after a message VAR node.
   *
   * @param builder the message builder whose meta data will be initialized
   * @param sibling a node adjacent to the message VAR node
   * @return true iff message has corresponding description variable
   */
  private boolean maybeInitMetaDataFromHelpVar(Builder builder,
      @Nullable Node sibling) throws MalformedException {
    if ((sibling != null) && (sibling.getType() == Token.VAR)) {
      Node nameNode = sibling.getFirstChild();
      String name = nameNode.getString();
      if (name.equals(builder.getKey() + DESC_SUFFIX)) {
        Node valueNode = nameNode.getFirstChild();
        String desc = extractStringFromStringExprNode(valueNode);
        if (desc.startsWith(HIDDEN_DESC_PREFIX)) {
          builder.setDesc(desc.substring(HIDDEN_DESC_PREFIX.length()).trim());
          builder.setIsHidden(true);
        } else {
          builder.setDesc(desc);
        }
        return true;
      }
    }
    return false;
  }

  /**
   * Initializes the meta data in a message builder given a node that may
   * contain JsDoc properties.
   *
   * @param builder the message builder whose meta data will be initialized
   * @param node the node with the message's JSDoc properties
   * @return true if message has JsDoc with valid description in @desc
   *         annotation
   */
  private boolean maybeInitMetaDataFromJsDoc(Builder builder, Node node) {
    boolean messageHasDesc = false;
    JSDocInfo info = node.getJSDocInfo();
    if (info != null) {
      String desc = info.getDescription();
      if (desc != null) {
        builder.setDesc(desc);
        messageHasDesc = true;
      }
      if (info.isHidden()) {
        builder.setIsHidden(true);
      }
      if (info.getMeaning() != null) {
        builder.setMeaning(info.getMeaning());
      }
    }

    return messageHasDesc;
  }

  /**
   * Returns the string value associated with a node representing a js string or
   * several js strings added together (e.g. {@code 'str'} or {@code 's' + 't' +
   * 'r'}).
   *
   * @param node the node from where we extract the string
   * @return String representation of the node
   * @throws MalformedException if the parsed message is invalid
   */
  private static String extractStringFromStringExprNode(Node node)
      throws MalformedException {
    switch (node.getType()) {
      case Token.STRING:
        return node.getString();
      case Token.ADD:
        StringBuilder sb = new StringBuilder();
        for (Node child : node.children()) {
          sb.append(extractStringFromStringExprNode(child));
        }
        return sb.toString();
      default:
        throw new MalformedException("STRING or ADD node expected; found: " +
                getReadableTokenName(node), node);
    }
  }

  /**
   * Initializes a message builder from a FUNCTION node.
   * <p>
   * <pre>
   * The tree should look something like:
   *
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
   * @param builder the message builder
   * @param node the function node that contains a message
   * @throws MalformedException if the parsed message is invalid
   */
  private void extractFromFunctionNode(Builder builder, Node node)
      throws MalformedException {
    Set<String> phNames = Sets.newHashSet();

    for (Node fnChild : node.children()) {
      switch (fnChild.getType()) {
        case Token.NAME:
          // This is okay. The function has a name, but it is empty.
          break;
        case Token.LP:
          // Parse the placeholder names from the function argument list.
          for (Node argumentNode : fnChild.children()) {
            if (argumentNode.getType() == Token.NAME) {
              String phName = argumentNode.getString();
              if (phNames.contains(phName)) {
                throw new MalformedException("Duplicate placeholder name: "
                    + phName, argumentNode);
              } else {
                phNames.add(phName);
              }
            }
          }
          break;
        case Token.BLOCK:
          // Build the message's value by examining the return statement
          Node returnNode = fnChild.getFirstChild();
          if (returnNode.getType() != Token.RETURN) {
            throw new MalformedException("RETURN node expected; found: "
                + getReadableTokenName(returnNode), returnNode);
          }
          for (Node child : returnNode.children()) {
            extractFromReturnDescendant(builder, child);
          }

          // Check that all placeholders from the message text have appropriate
          // object literal keys
          for (String phName : builder.getPlaceholders()) {
            if(!phNames.contains(phName)) {
              throw new MalformedException(
                  "Unrecognized message placeholder referenced: " + phName,
                  returnNode);
            }
          }
          break;
        default:
          throw new MalformedException(
              "NAME, LP, or BLOCK node expected; found: "
                  + getReadableTokenName(node), fnChild);
      }
    }
  }

  /**
   * Appends value parts to the message builder by traversing the descendants
   * of the given RETURN node.
   *
   * @param builder the message builder
   * @param node the node from where we extract a message
   * @throws MalformedException if the parsed message is invalid
   */
  private void extractFromReturnDescendant(Builder builder, Node node)
      throws MalformedException {

    switch (node.getType()) {
      case Token.STRING:
        builder.appendStringPart(node.getString());
        break;
      case Token.NAME:
        builder.appendPlaceholderReference(node.getString());
        break;
      case Token.ADD:
        for (Node child : node.children()) {
          extractFromReturnDescendant(builder, child);
        }
        break;
      default:
        throw new MalformedException(
            "STRING, NAME, or ADD node expected; found: "
                + getReadableTokenName(node), node);
    }
  }

  /**
   * Initializes a message builder from a CALL node.
   * <p>
   * The tree should look something like:
   *
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
   * </pre>
   *
   * @param builder the message builder
   * @param node the call node from where we extract the message
   * @throws MalformedException if the parsed message is invalid
   */
  private void extractFromCallNode(Builder builder,
      Node node) throws MalformedException {
    // Check the function being called
    if (node.getType() != Token.CALL) {
      throw new MalformedException(
          "Message must be initialized using " + MSG_FUNCTION_NAME +
          " function.", node);
    }

    Node fnNameNode = node.getFirstChild();
    if (!MSG_FUNCTION_NAME.equals(fnNameNode.getQualifiedName())) {
      throw new MalformedException(
          "Message initialized using unrecognized function. " +
          "Please use " + MSG_FUNCTION_NAME + "() instead.", fnNameNode);
    }

    // Get the message string
    Node stringLiteralNode = fnNameNode.getNext();
    if (stringLiteralNode == null) {
      throw new MalformedException("Message string literal expected",
          stringLiteralNode);
    }
    // Parse the message string and append parts to the builder
    parseMessageTextNode(builder, stringLiteralNode);

    Node objLitNode = stringLiteralNode.getNext();
    Set<String> phNames = Sets.newHashSet();
    if (objLitNode != null) {
      // Register the placeholder names
      if (objLitNode.getType() != Token.OBJECTLIT) {
        throw new MalformedException("OBJLIT node expected", objLitNode);
      }
      for (Node aNode = objLitNode.getFirstChild(); aNode != null;
           aNode = aNode.getNext()) {
        if (aNode.getType() != Token.STRING) {
          throw new MalformedException("STRING node expected as OBJLIT key",
              aNode);
        }
        String phName = aNode.getString();
        if (!isLowerCamelCaseWithNumericSuffixes(phName)) {
          throw new MalformedException(
              "Placeholder name not in lowerCamelCase: " + phName, aNode);
        }

        if (phNames.contains(phName)) {
          throw new MalformedException("Duplicate placeholder name: "
              + phName, aNode);
        }

        phNames.add(phName);
      }
    }

    // Check that all placeholders from the message text have appropriate objlit
    // values
    Set<String> usedPlaceholders = builder.getPlaceholders();
    for (String phName : usedPlaceholders) {
      if(!phNames.contains(phName)) {
        throw new MalformedException(
            "Unrecognized message placeholder referenced: " + phName,
            objLitNode);
      }
    }

    // Check that objLiteral have only names that are present in the
    // message text
    for (String phName : phNames) {
      if(!usedPlaceholders.contains(phName)) {
        throw new MalformedException(
            "Unused message placeholder: " + phName,
            objLitNode);
      }
    }
  }

  /**
   * Appends the message parts in a JS message value extracted from the given
   * text node.
   *
   * @param builder the js message builder to append parts to
   * @param node the node with string literal that contains the message text
   * @throws MalformedException if {@code value} contains a reference to
   *         an unregistered placeholder
   */
  private void parseMessageTextNode(Builder builder, Node node)
      throws MalformedException {
    String value = extractStringFromStringExprNode(node);

    while(true) {
      int phBegin = value.indexOf(PH_JS_PREFIX);
      if (phBegin < 0) {
        // Just a string literal
        builder.appendStringPart(value);
        return;
      } else {
        if (phBegin > 0) {
          // A string literal followed by a placeholder
          builder.appendStringPart(value.substring(0, phBegin));
        }

        // A placeholder. Find where it ends
        int phEnd = value.indexOf(PH_JS_SUFFIX, phBegin);
        if (phEnd < 0) {
          throw new MalformedException(
              "Placeholder incorrectly formatted in: " + builder.getKey(),
              node);
        }

        String phName = value.substring(phBegin + PH_JS_PREFIX.length(),
            phEnd);
        builder.appendPlaceholderReference(phName);
        int nextPos = phEnd + PH_JS_SUFFIX.length();
        if (nextPos < value.length()) {
          // Iterate on the rest of the message value
          value = value.substring(nextPos);
        } else {
          // The message is parsed
          return;
        }
      }
    }
  }


  /**
   * Processes found js message. Several examples of "standard" processing
   * routines are:
   * <ol>
   * <li>extract all js messages
   * <li>replace js messages with localized versions for some specific language
   * <li>check that messages have correct syntax and present in localization
   *     bundle
   * </ol>
   *
   * @param message the found message
   * @param definition the definition of the object and usually contains all
   *        additional message information like message node/parent's node
   */
  abstract void processJsMessage(JsMessage message,
      JsMessageDefinition definition);

  /**
   * Returns whether the given js identifier is a valid js message name.
   */
  boolean isMessageName(String identifier, boolean isNewStyleMessage) {
    return identifier.startsWith(MSG_PREFIX) &&
        (style == JsMessage.Style.CLOSURE || isNewStyleMessage ||
         !identifier.endsWith(DESC_SUFFIX));
  }

  /**
   * Returns whether the given message name is in the unnamed namespace.
   */
  private static boolean isUnnamedMessageName(String identifier) {
    return MSG_UNNAMED_PATTERN.matcher(identifier).matches();
  }

  /**
   * Returns whether a string is nonempty, begins with a lowercase letter, and
   * contains only digits and underscores after the first underscore.
   */
  static boolean isLowerCamelCaseWithNumericSuffixes(String input) {
    return CAMELCASE_PATTERN.matcher(input).matches();
  }

  /**
   * Returns human-readable name of the given node's type.
   */
  private static String getReadableTokenName(Node node) {
    return Node.tokenToName(node.getType()).toUpperCase();
  }

  /**
   * Converts the given string from upper-underscore case to lower-camel case,
   * preserving numeric suffixes. For example: "NAME" -> "name" "A4_LETTER" ->
   * "a4Letter" "START_SPAN_1_23" -> "startSpan_1_23".
   */
  static String toLowerCamelCaseWithNumericSuffixes(String input) {
    // Determine where the numeric suffixes begin
    int suffixStart = input.length();
    while (suffixStart > 0) {
      char ch = '\0';
      int numberStart = suffixStart;
      while (numberStart > 0) {
        ch = input.charAt(numberStart - 1);
        if (Character.isDigit(ch)) {
          numberStart--;
        } else {
          break;
        }
      }
      if ((numberStart > 0) && (numberStart < suffixStart) && (ch == '_')) {
        suffixStart = numberStart - 1;
      } else {
        break;
      }
    }

    if (suffixStart == input.length()) {
      return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, input);
    } else {
      return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL,
          input.substring(0, suffixStart)) +
          input.substring(suffixStart);
    }
  }

  static class MalformedException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Node node;

    MalformedException(String message, Node node) {
      super(message);
      this.node = node;
    }

    Node getNode() {
      return node;
    }
  }

  private static class MessageLocation {
    private final String sourceName;
    private final int lineNo;

    private MessageLocation(String sourceName, int lineNo) {
      this.sourceName = sourceName;
      this.lineNo = lineNo;
    }
  }
}
