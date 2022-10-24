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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.jscomp.JsMessage.Hash;
import com.google.javascript.jscomp.JsMessage.Part;
import com.google.javascript.jscomp.JsMessage.PlaceholderFormatException;
import com.google.javascript.jscomp.JsMessage.PlaceholderReference;
import com.google.javascript.jscomp.JsMessage.StringPart;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.base.format.SimpleFormat;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.nullness.Nullable;

/**
 * Traverses across parsed tree and finds I18N messages. Then it passes it to {@link
 * JsMessageVisitor#processJsMessage(JsMessage, JsMessageDefinition)}.
 */
@GwtIncompatible("JsMessage, java.util.regex")
public abstract class JsMessageVisitor extends AbstractPostOrderCallback implements CompilerPass {

  private static final String MSG_FUNCTION_NAME = "goog.getMsg";
  private static final String MSG_FALLBACK_FUNCTION_NAME = "goog.getMsgWithFallback";

  /**
   * Identifies a message with a specific ID which doesn't get extracted from the JS code containing
   * its declaration.
   *
   * <pre><code>
   *   // A message with ID 1234 whose original definition comes from some source other than this
   *   // JS code.
   *   const MSG_EXTERNAL_1234 = goog.getMsg("Some message.");
   * </code></pre>
   */
  private static final String MSG_EXTERNAL_PREFIX = "MSG_EXTERNAL_";

  static final DiagnosticType MESSAGE_HAS_NO_DESCRIPTION =
      DiagnosticType.warning(
          "JSC_MSG_HAS_NO_DESCRIPTION", "Message {0} has no description. Add @desc JsDoc tag.");

  static final DiagnosticType MESSAGE_HAS_NO_TEXT =
      DiagnosticType.warning(
          "JSC_MSG_HAS_NO_TEXT",
          "Message value of {0} is just an empty string. " + "Empty messages are forbidden.");

  public static final DiagnosticType MESSAGE_TREE_MALFORMED =
      DiagnosticType.error("JSC_MSG_TREE_MALFORMED", "Message parse tree malformed. {0}");

  static final DiagnosticType MESSAGE_HAS_NO_VALUE =
      DiagnosticType.error("JSC_MSG_HAS_NO_VALUE", "message node {0} has no value");

  static final DiagnosticType MESSAGE_DUPLICATE_KEY =
      DiagnosticType.error(
          "JSC_MSG_KEY_DUPLICATED",
          "duplicate message variable name found for {0}, " + "initial definition {1}:{2}");

  static final DiagnosticType MESSAGE_NODE_IS_ORPHANED =
      DiagnosticType.error(
          "JSC_MSG_ORPHANED_NODE",
          MSG_FUNCTION_NAME + "() function could be used only with MSG_* property or variable");

  public static final DiagnosticType MESSAGE_NOT_INITIALIZED_CORRECTLY =
      DiagnosticType.warning(
          "JSC_MSG_NOT_INITIALIZED_CORRECTLY",
          "message not initialized using " + MSG_FUNCTION_NAME);

  public static final DiagnosticType BAD_FALLBACK_SYNTAX =
      DiagnosticType.error(
          "JSC_MSG_BAD_FALLBACK_SYNTAX",
          SimpleFormat.format(
              "Bad syntax. " + "Expected syntax: %s(MSG_1, MSG_2)", MSG_FALLBACK_FUNCTION_NAME));

  public static final DiagnosticType FALLBACK_ARG_ERROR =
      DiagnosticType.error(
          "JSC_MSG_FALLBACK_ARG_ERROR", "Could not find message entry for fallback argument {0}");

  static final String MSG_PREFIX = "MSG_";

  /**
   * ScopedAliases pass transforms the goog.scope declarations to have a unique id as prefix.
   *
   * <p>After '$jscomp$scope$' expect some sequence of digits and dollar signs ending in '$MSG_
   */
  private static final Pattern SCOPED_ALIASES_PREFIX_PATTERN =
      Pattern.compile("\\$jscomp\\$scope\\$\\S+\\$MSG_");

  /**
   * Pattern for unnamed messages.
   *
   * <p>All JS messages in JS code should have unique name but messages in generated code (i.e. from
   * soy template) could have duplicated message names. Later we replace the message names with ids
   * constructed as a hash of the message content.
   *
   * <p><a href="https://github.com/google/closure-templates">Soy</a> generates messages with names
   * MSG_UNNAMED.* . This pattern recognizes such messages.
   */
  private static final Pattern MSG_UNNAMED_PATTERN = Pattern.compile("MSG_UNNAMED.*");

  private final JsMessage.IdGenerator idGenerator;
  final AbstractCompiler compiler;

  /**
   * The names encountered associated with their defining node and source. We use it for tracking
   * duplicated message ids in the source code.
   */
  private final Map<String, MessageLocation> messageNames = new LinkedHashMap<>();

  /** Track unnamed messages by Var, not string, as they are not guaranteed to be globally unique */
  private final Map<Var, JsMessage> unnamedMessages = new LinkedHashMap<>();

  /**
   * List of found goog.getMsg call nodes.
   *
   * <p>When we visit goog.getMsg() node we add it, and later when we visit its parent we remove it.
   * All nodes that are left at the end of traversing are orphaned nodes. It means have no
   * corresponding var or property node.
   */
  private final Set<Node> googMsgNodes = new LinkedHashSet<>();

  /**
   * Creates JS message visitor.
   *
   * @param compiler the compiler instance
   * @param idGenerator generator that used for creating unique ID for the message
   */
  protected JsMessageVisitor(AbstractCompiler compiler, JsMessage.IdGenerator idGenerator) {

    this.compiler = compiler;
    this.idGenerator = idGenerator;

    // TODO(anatol): add flag that decides whether to process UNNAMED messages.
    // Some projects would not want such functionality (unnamed) as they don't
    // use SOY templates.
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);

    for (Node msgNode : googMsgNodes) {
      compiler.report(JSError.make(msgNode, MESSAGE_NODE_IS_ORPHANED));
    }
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node unused) {
    collectGetMsgCall(traversal, node);
    checkMessageInitialization(traversal, node);
  }

  /** This method is called for every Node in the sources AST. */
  private void checkMessageInitialization(NodeTraversal traversal, Node node) {
    final Node parent = node.getParent();

    final String originalMessageKey;
    String possiblyObfuscatedMessageKey;
    final Node msgNode;
    final JSDocInfo jsDocInfo;

    switch (node.getToken()) {
      case NAME:
        // Case: `var MSG_HELLO = 'Message';`
        if (parent == null || !NodeUtil.isNameDeclaration(parent)) {
          return;
        }

        possiblyObfuscatedMessageKey = node.getString();
        originalMessageKey = node.getOriginalName();
        msgNode = node.getFirstChild();
        jsDocInfo = parent.getJSDocInfo();
        break;

      case ASSIGN:
        // Case: `somenamespace.someclass.MSG_HELLO = 'Message';`
        Node getProp = node.getFirstChild();
        if (!getProp.isGetProp()) {
          return;
        }

        possiblyObfuscatedMessageKey = getProp.getString();
        originalMessageKey = getProp.getOriginalName();
        msgNode = node.getLastChild();
        jsDocInfo = node.getJSDocInfo();
        break;

      case STRING_KEY:
        // Case: `var t = {MSG_HELLO: 'Message'}`;
        if (node.isQuotedString() || !node.hasChildren() || parent.isObjectPattern()) {
          // Don't require goog.getMsg() for quoted keys
          // Case: `var msgs = { 'MSG_QUOTED': anything };`
          //
          // Don't try to require goog.getMsg() for destructuring assignment targets.
          // goog.getMsg() needs to be used in a direct assignment to a variable or property
          // only.
          // Case: `var {MSG_HELLO} = anything;
          // Case: `var {something: MSG_HELLO} = anything;
          return;
        }
        checkState(parent.isObjectLit(), parent);

        possiblyObfuscatedMessageKey = node.getString();
        originalMessageKey = node.getOriginalName();
        msgNode = node.getFirstChild();
        jsDocInfo = node.getJSDocInfo();
        break;

      default:
        return;
    }

    String messageKeyFromLhs =
        originalMessageKey != null ? originalMessageKey : possiblyObfuscatedMessageKey;

    // If we've reached this point, then messageKey is the name of a variable or a property that is
    // being assigned a value and msgNode is the Node representing the value being assigned.
    // However, we haven't actually determined yet that name looks like it should be a translatable
    // message or that the value is a call to goog.getMsg().

    // Is this a message name?
    boolean msgNodeIsACall = msgNode != null && msgNode.isCall();

    if (!isMessageName(messageKeyFromLhs)) {
      return;
    }

    if (msgNode == null) {
      compiler.report(JSError.make(node, MESSAGE_HAS_NO_VALUE, messageKeyFromLhs));
      return;
    }

    if (isLegalMessageVarAlias(msgNode)) {
      return;
    }

    // Report a warning if a qualified messageKey that looks like a message (e.g. "a.b.MSG_X")
    // doesn't use goog.getMsg().
    if (msgNodeIsACall) {
      googMsgNodes.remove(msgNode);
    } else {
      // TODO(bradfordcsmith): Make this an error if an `@desc` annotation is present to indicate
      // that the code author really expects translation to happen.
      compiler.report(JSError.make(node, MESSAGE_NOT_INITIALIZED_CORRECTLY));
      return;
    }

    OriginalMapping mapping =
        compiler.getSourceMapping(traversal.getSourceName(), node.getLineno(), node.getCharno());
    final String sourceName;
    if (mapping != null) {
      sourceName = mapping.getOriginalFile() + ":" + mapping.getLineNumber();
    } else {
      sourceName = traversal.getSourceName() + ":" + node.getLineno();
    }

    final String description;
    final String meaning;
    final String alternateMessageId;
    if (jsDocInfo != null) {
      description = jsDocInfo.getDescription();
      meaning = jsDocInfo.getMeaning();
      alternateMessageId = jsDocInfo.getAlternateMessageId();
    } else {
      description = null;
      meaning = null;
      alternateMessageId = null;
    }

    final CallNodeMsgData callNodeMsgData;
    try {
      callNodeMsgData = extractCallNodeMsgData(msgNode);
    } catch (MalformedException ex) {
      compiler.report(JSError.make(ex.getNode(), MESSAGE_TREE_MALFORMED, ex.getMessage()));
      return;
    }
    final String messageText = callNodeMsgData.getMessageText();
    final ImmutableList<Part> messageParts = callNodeMsgData.getMessageParts();

    // non-null for `MSG_EXTERNAL_12345`
    final String externalMessageId = getExternalMessageId(messageKeyFromLhs);

    final boolean isAnonymous;
    final boolean isExternal;
    final String messageId;
    final String messageKeyFinal;
    if (externalMessageId != null) {
      // MSG_EXTERNAL_12345 = ...
      isAnonymous = false;
      isExternal = true;
      messageId = externalMessageId;
      messageKeyFinal = messageKeyFromLhs;
    } else {
      isExternal = false;
      // We will need to generate the message ID from a combination of the message text and
      // its "meaning". If the code explicitly specifies a "meaning" string we'll use that,
      // otherwise we'll use the message key as the meaning
      String meaningForIdGeneration = meaning;
      if (isUnnamedMessageName(messageKeyFromLhs)) {
        // MSG_UNNAMED_XXXX = goog.getMsg(....);
        // JS code that is automatically generated uses this, since it is harder for it to create
        // message variable names that are guaranteed to be unique.
        isAnonymous = true;
        messageKeyFinal = generateKeyFromMessageText(messageText);
        if (meaningForIdGeneration == null) {
          meaningForIdGeneration = messageKeyFinal;
        }
      } else {
        isAnonymous = false;
        messageKeyFinal = messageKeyFromLhs;
        if (meaningForIdGeneration == null) {
          // Transpilation of goog.scope() may have added a prefix onto the variable name,
          // which we need to strip off when we treat it as a "meaning" for ID generation purposes.
          // Otherwise, the message ID would change if a goog.scope() call were added or removed.
          meaningForIdGeneration = removeScopedAliasesPrefix(messageKeyFromLhs);
        }
      }
      messageId =
          idGenerator == null
              ? meaningForIdGeneration
              : idGenerator.generateId(meaningForIdGeneration, messageParts);
    }

    JsMessage extractedMessage =
        new JsMessage.Builder()
            .appendParts(messageParts)
            .setPlaceholderNameToExampleMap(callNodeMsgData.getPlaceholderExampleMap())
            .setPlaceholderNameToOriginalCodeMap(callNodeMsgData.getPlaceholderOriginalCodeMap())
            .setIsAnonymous(isAnonymous)
            .setIsExternalMsg(isExternal)
            .setKey(messageKeyFinal)
            .setSourceName(sourceName)
            .setDesc(description)
            .setMeaning(meaning)
            .setAlternateId(alternateMessageId)
            .setId(messageId)
            .build();

    // If asked to check named internal messages.
    if (!extractedMessage.isAnonymous() && !extractedMessage.isExternal()) {
      checkIfMessageDuplicated(extractedMessage.getKey(), msgNode);
    }
    if (extractedMessage.isAnonymous()) {
      trackUnnamedMessage(traversal, extractedMessage, possiblyObfuscatedMessageKey);
    } else {
      trackNormalMessage(extractedMessage, extractedMessage.getKey(), msgNode);
    }

    if (extractedMessage.isEmpty()) {
      // value of the message is an empty string. Translators do not like it.
      compiler.report(JSError.make(node, MESSAGE_HAS_NO_TEXT, extractedMessage.getKey()));
    }

    // Messages must have descriptions.
    final String desc = extractedMessage.getDesc();
    if ((desc == null || desc.trim().isEmpty()) && !extractedMessage.isExternal()) {
      compiler.report(JSError.make(node, MESSAGE_HAS_NO_DESCRIPTION, extractedMessage.getKey()));
    }

    // NOTE: All of the methods defined below should return data that has already been computed.
    // Most importantly, none of them should throw a MalformedException.
    final JsMessageDefinition msgDefinition =
        new JsMessageDefinition() {
          @Override
          public Node getMessageNode() {
            return msgNode;
          }

          @Override
          public Node getTemplateTextNode() {
            return callNodeMsgData.getTemplateTextNode();
          }

          @Override
          public @Nullable Node getPlaceholderValuesNode() {
            return callNodeMsgData.getPlaceholderValuesNode();
          }

          @Override
          public ImmutableMap<String, Node> getPlaceholderValueMap() {
            return callNodeMsgData.getPlaceholderValueMap();
          }

          @Override
          public boolean shouldEscapeLessThan() {
            return callNodeMsgData.shouldEscapeLessThan();
          }

          @Override
          public boolean shouldUnescapeHtmlEntities() {
            return callNodeMsgData.shouldUnescapeHtmlEntities();
          }
        };
    processJsMessage(extractedMessage, msgDefinition);
  }

  /**
   * Extracts an external message ID from the message key, if it contains one.
   *
   * @param messageKey the message key (usually the variable or property name)
   * @return the external ID if it is found, otherwise `null`
   */
  public static @Nullable String getExternalMessageId(String messageKey) {
    if (messageKey.startsWith(MSG_EXTERNAL_PREFIX)) {
      int start = MSG_EXTERNAL_PREFIX.length();
      int end = start;
      for (; end < messageKey.length(); end++) {
        char c = messageKey.charAt(end);
        if (c > '9' || c < '0') {
          break;
        }
      }
      if (end > start) {
        return messageKey.substring(start, end);
      }
    }
    return null;
  }

  private String generateKeyFromMessageText(String msgText) {
    long nonnegativeHash = Long.MAX_VALUE & Hash.hash64(msgText);
    return MSG_PREFIX + Ascii.toUpperCase(Long.toString(nonnegativeHash, 36));
  }

  private void collectGetMsgCall(NodeTraversal traversal, Node call) {
    if (!call.isCall()) {
      return;
    }

    // goog.getMsg()
    if (call.getFirstChild().matchesQualifiedName(MSG_FUNCTION_NAME)) {
      googMsgNodes.add(call);
    } else if (call.getFirstChild().matchesQualifiedName(MSG_FALLBACK_FUNCTION_NAME)) {
      visitFallbackFunctionCall(traversal, call);
    }
  }

  /**
   * Track a message for later retrieval.
   *
   * <p>This is used for tracking duplicates, and for figuring out message fallback. Not all message
   * types are trackable, because that would require a more sophisticated analysis. e.g., function
   * f(s) { s.MSG_UNNAMED_X = 'Some untrackable message'; }
   */
  private void trackNormalMessage(JsMessage message, String msgName, Node msgNode) {
    MessageLocation location = new MessageLocation(message, msgNode);
    messageNames.put(msgName, location);
  }

  /**
   * Track an unnamed message for later retrieval.
   *
   * <p>This is used for figuring out message fallbacks. Message duplicates are allowed for unnamed
   * messages.
   */
  private void trackUnnamedMessage(NodeTraversal t, JsMessage message, String msgNameInScope) {
    Var var = t.getScope().getVar(msgNameInScope);
    if (var != null) {
      unnamedMessages.put(var, message);
    }
  }

  /**
   * Defines any special cases that are exceptions to what would otherwise be illegal message
   * assignments.
   *
   * <p>These exceptions are generally due to the pass being designed before new syntax was
   * introduced.
   *
   * @param msgNode Node representing the value assigned to the message variable or property
   */
  private static boolean isLegalMessageVarAlias(Node msgNode) {
    if (msgNode.isGetProp()
        && msgNode.isQualifiedName()
        && msgNode.getString().startsWith(MSG_PREFIX)) {
      // Case: `foo.Thing.MSG_EXAMPLE_ALIAS = bar.OtherThing.MSG_EXAMPLE;`
      //
      // This kind of construct is created by TypeScript code generation and
      // ConcretizeStaticInheritanceForInlining. Just ignore it; the message will have already been
      // extracted
      // from the base class.
      return true;
    }

    if (!msgNode.isName()) {
      return false;
    }

    String originalName =
        (msgNode.getOriginalName() != null) ? msgNode.getOriginalName() : msgNode.getString();

    if (originalName.startsWith(MSG_PREFIX)) {
      // Creating an alias for a message is also allowed, and sometimes happens in generated code,
      // including some of the code generated by this compiler's transpilations.
      // e.g.
      // `var MSG_EXAMPLE_ALIAS = MSG_EXAMPLE;`
      // `var {MSG_HELLO_ALIAS} = MSG_HELLO;
      // `var {MSG_HELLO_ALIAS: goog$module$my$module_MSG_HELLO} = x;`).
      // `exports = {MSG_FOO}`
      // or `exports = {MSG_FOO: MSG_FOO}` when used with declareLegacyNamespace.
      return true;
    }

    return false;
  }

  /** Get a previously tracked unnamed message */
  private @Nullable JsMessage getTrackedUnnamedMessage(NodeTraversal t, String msgNameInScope) {
    Var var = t.getScope().getVar(msgNameInScope);
    if (var != null) {
      return unnamedMessages.get(var);
    }
    return null;
  }

  /** Get a previously tracked message. */
  private @Nullable JsMessage getTrackedNormalMessage(String msgName) {
    MessageLocation location = messageNames.get(msgName);
    return location == null ? null : location.message;
  }

  /**
   * Checks if message already processed. If so - it generates 'message duplicated' compiler error.
   *
   * @param msgName the name of the message
   * @param msgNode the node that represents JS message
   */
  private void checkIfMessageDuplicated(String msgName, Node msgNode) {
    if (messageNames.containsKey(msgName)) {
      MessageLocation location = messageNames.get(msgName);
      compiler.report(
          JSError.make(
              msgNode,
              MESSAGE_DUPLICATE_KEY,
              msgName,
              location.messageNode.getSourceFileName(),
              Integer.toString(location.messageNode.getLineno())));
    }
  }

  /**
   * Returns the string value associated with a node representing a JS string or several JS strings
   * added together (e.g. {@code 'str'} or {@code 's' + 't' + 'r'}).
   *
   * @param node the node from where we extract the string
   * @return String representation of the node
   * @throws MalformedException if the node is not a string literal or concatenation of them
   */
  private static String extractStringFromStringExprNode(Node node) throws MalformedException {
    switch (node.getToken()) {
      case STRINGLIT:
        return node.getString();
      case TEMPLATELIT:
        if (node.hasOneChild()) {
          // Cooked string can be null only for tagged template literals.
          // A tagged template literal would hit the default case below.
          return checkNotNull(node.getFirstChild().getCookedString());
        } else {
          throw new MalformedException(
              "Template literals with substitutions are not allowed.", node);
        }
      case ADD:
        StringBuilder sb = new StringBuilder();
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
          sb.append(extractStringFromStringExprNode(child));
        }
        return sb.toString();
      default:
        throw new MalformedException("literal string or concatenation expected", node);
    }
  }

  private CallNodeMsgData extractCallNodeMsgData(Node node) throws MalformedException {
    checkState(node.isCall());
    Node fnNameNode = node.getFirstChild();
    if (fnNameNode.matchesQualifiedName(MSG_FUNCTION_NAME)) {
      // Extract data from a call to `goog.getMsg(...)`
      return extractGoogGetMsgData(node);
    } else {
      throw new MalformedException(
          "Message initialized using unrecognized function. "
              + "Please use "
              + MSG_FUNCTION_NAME
              + "() instead.",
          fnNameNode);
    }
  }

  /**
   * Extract data from a call to {@code goog.getMsg(...)}.
   *
   * <p>Here's an example with all possible arguments and options present.
   *
   * <pre><code>
   *   const MSG_HELLO =
   *       goog.getMsg(
   *           'Hello, {$name}', // message template string
   *           { name: getName() }, // placeholder replacement values
   *           { // options bag
   *             html: false,
   *             unescapeHtmlEntities: true,
   *             example: { name: 'George' },
   *             original_code: { name: 'getName()' },
   *           });
   * </code></pre>
   *
   * @throws MalformedException if the code does not match the expected format.
   */
  private CallNodeMsgData extractGoogGetMsgData(Node callNode) throws MalformedException {
    // first child of the call is the `goog.getMsg` name
    // second is the message string value
    final Node msgTextNode = callNode.getSecondChild();
    if (msgTextNode == null) {
      throw new MalformedException("Message string literal expected", callNode);
    }
    // third is the optional object literal mapping placeholder names to value expressions
    final Node valuesObjLit = msgTextNode.getNext();
    // fourth is the optional options-bag argument
    final Node optionsBagArgument = valuesObjLit == null ? null : valuesObjLit.getNext();

    // Extract and parse the message text
    // The message string can be a string literal or a concatenation of string literals.
    // We want the whole thing as a single string here.
    String msgTextString = extractStringFromStringExprNode(msgTextNode);
    final GoogGetMsgParsedText googGetMsgParsedText;
    try {
      googGetMsgParsedText = extractGoogGetMsgParsedText(msgTextString);
    } catch (PlaceholderFormatException e) {
      throw new MalformedException(e.getMessage(), msgTextNode);
    }

    // Extract the placeholder values object
    // NOTE: The extract method returns an effectively "empty" value for `null`
    final ObjectLiteralMap placeholderObjectLiteralMap = extractObjectLiteralMap(valuesObjLit);

    // Confirm that we can find a value map entry for every placeholder name referenced in the text.
    final ImmutableSet<String> placeholderNamesFromText =
        googGetMsgParsedText.getPlaceholderNames();
    placeholderObjectLiteralMap.checkForRequiredKeys(
        placeholderNamesFromText,
        placeholderName ->
            new MalformedException(
                "Unrecognized message placeholder referenced: " + placeholderName, msgTextNode));

    // Confirm that every placeholder name for which we have a value is actually referenced in the
    // text.
    placeholderObjectLiteralMap.checkForUnexpectedKeys(
        placeholderNamesFromText,
        placeholderName -> "Unused message placeholder: " + placeholderName);

    // Get a map from placeholder name to Node value
    final ImmutableMap<String, Node> placeholderValuesMap =
        placeholderObjectLiteralMap.extractValueMap();

    // Extract the Options bag data
    // NOTE: the extract method returns an "empty" value for `null`
    final JsMessageOptions jsMessageOptions = extractJsMessageOptions(optionsBagArgument);

    // Confirm that any example or orignal_code placeholder information we have refers only to
    // placeholder names that appear in the message text.
    jsMessageOptions.checkForUnknownPlaceholders(placeholderNamesFromText);

    // NOTE: All of the methods defined below should do little to no computation.
    // In particular, all checking for MalformedException cases must be done above,
    // so these methods can be called without needing to catch exceptions.
    return new CallNodeMsgData() {
      @Override
      public Node getTemplateTextNode() {
        return msgTextNode;
      }

      @Override
      public String getMessageText() {
        return googGetMsgParsedText.getText();
      }

      @Override
      public ImmutableList<Part> getMessageParts() {
        return googGetMsgParsedText.getParts();
      }

      @Override
      public Node getPlaceholderValuesNode() {
        return valuesObjLit;
      }

      @Override
      public ImmutableMap<String, Node> getPlaceholderValueMap() {
        return placeholderValuesMap;
      }

      @Override
      public ImmutableMap<String, String> getPlaceholderExampleMap() {
        return jsMessageOptions.getPlaceholderExampleMap();
      }

      @Override
      public ImmutableMap<String, String> getPlaceholderOriginalCodeMap() {
        return jsMessageOptions.getPlaceholderOriginalCodeMap();
      }

      @Override
      public boolean shouldEscapeLessThan() {
        return jsMessageOptions.isEscapeLessThan();
      }

      @Override
      public boolean shouldUnescapeHtmlEntities() {
        return jsMessageOptions.isUnescapeHtmlEntities();
      }
    };
  }

  private interface CallNodeMsgData {
    Node getTemplateTextNode();

    String getMessageText();

    ImmutableList<Part> getMessageParts();

    Node getPlaceholderValuesNode();

    ImmutableMap<String, Node> getPlaceholderValueMap();

    ImmutableMap<String, String> getPlaceholderExampleMap();

    ImmutableMap<String, String> getPlaceholderOriginalCodeMap();

    boolean shouldEscapeLessThan();

    boolean shouldUnescapeHtmlEntities();
  }

  private interface JsMessageOptions {
    // Replace `'<'` with `'&lt;'` in the message.
    boolean isEscapeLessThan();
    // Replace these escaped entities with their literal characters in the message
    // (Overrides escapeLessThan)
    // '&lt;' -> '<'
    // '&gt;' -> '>'
    // '&apos;' -> "'"
    // '&quot;' -> '"'
    // '&amp;' -> '&'
    boolean isUnescapeHtmlEntities();

    ImmutableMap<String, String> getPlaceholderExampleMap();

    ImmutableMap<String, String> getPlaceholderOriginalCodeMap();

    void checkForUnknownPlaceholders(Set<String> knownPlaceholders) throws MalformedException;
  }

  private static final ImmutableSet<String> MESSAGE_OPTION_NAMES =
      ImmutableSet.of("html", "unescapeHtmlEntities", "example", "original_code");

  private JsMessageOptions extractJsMessageOptions(@Nullable Node optionsBag)
      throws MalformedException {
    ObjectLiteralMap objectLiteralMap = extractObjectLiteralMap(optionsBag);
    objectLiteralMap.checkForUnexpectedKeys(
        MESSAGE_OPTION_NAMES, optionName -> "Unknown option: " + optionName);

    final boolean isEscapeLessThan = objectLiteralMap.getBooleanValueOrFalse("html");
    final boolean isUnescapeHtmlEntities =
        objectLiteralMap.getBooleanValueOrFalse("unescapeHtmlEntities");

    final Node exampleValueNode = objectLiteralMap.getValueNode("example");
    final ObjectLiteralMap exampleObjectLiteralMap = extractObjectLiteralMap(exampleValueNode);
    final ImmutableMap<String, String> placeholderExamplesMap =
        exampleObjectLiteralMap.extractStringToStringMap();

    final Node originalCodeValueNode = objectLiteralMap.getValueNode("original_code");
    final ObjectLiteralMap originalCodeObjectLiteralMap =
        extractObjectLiteralMap(originalCodeValueNode);
    final ImmutableMap<String, String> placeholderOriginalCodeMap =
        originalCodeObjectLiteralMap.extractStringToStringMap();

    // NOTE: The getX() methods below should all do little to no computation.
    // In particular, all checking for MalformedExceptions must be done before creating this object.
    return new JsMessageOptions() {
      @Override
      public boolean isEscapeLessThan() {
        return isEscapeLessThan;
      }

      @Override
      public boolean isUnescapeHtmlEntities() {
        return isUnescapeHtmlEntities;
      }

      @Override
      public ImmutableMap<String, String> getPlaceholderExampleMap() {
        return placeholderExamplesMap;
      }

      @Override
      public ImmutableMap<String, String> getPlaceholderOriginalCodeMap() {
        return placeholderOriginalCodeMap;
      }

      @Override
      public void checkForUnknownPlaceholders(Set<String> knownPlaceholders)
          throws MalformedException {
        exampleObjectLiteralMap.checkForUnexpectedKeys(
            knownPlaceholders, unknownName -> "Unknown placeholder: " + unknownName);
        originalCodeObjectLiteralMap.checkForUnexpectedKeys(
            knownPlaceholders, unknownName -> "Unknown placeholder: " + unknownName);
      }
    };
  }

  private static boolean extractBooleanStringKeyValue(@Nullable Node stringKeyNode)
      throws MalformedException {
    if (stringKeyNode == null) {
      return false;
    } else {
      final Node valueNode = stringKeyNode.getOnlyChild();
      if (valueNode.isTrue()) {
        return true;
      } else if (valueNode.isFalse()) {
        return false;
      } else {
        throw new MalformedException(
            stringKeyNode.getString() + ": Literal true or false expected", valueNode);
      }
    }
  }

  /**
   * Represents the contents of a object literal Node in the AST.
   *
   * <p>The object literal may not have any computed keys or methods.
   *
   * <p>Use {@link #extractObjectLiteralMap(Node)} to get an instance.
   */
  public interface ObjectLiteralMap {
    boolean getBooleanValueOrFalse(String key) throws MalformedException;

    /**
     * Returns a map from object property names the Node values they have in the AST, building the
     * map first, if necessary.
     */
    ImmutableMap<String, Node> extractValueMap();

    /**
     * Returns a map from object property names to string values, building it first, if necessary.
     *
     * @throws MalformedException if any of the values are not actually simple string literals or
     *     concatenations of string literals.
     */
    ImmutableMap<String, String> extractStringToStringMap() throws MalformedException;

    /**
     * Get the value node for a key.
     *
     * <p>This method avoids the work of extracting the full value map.
     */
    @Nullable Node getValueNode(String key);

    /**
     * Throws a {@link MalformedException} if the object literal has keys not in the expected set.
     */
    void checkForUnexpectedKeys(
        Set<String> expectedKeys, Function<String, String> createErrorMessage)
        throws MalformedException;

    /**
     * Throws a {@link MalformedException} if the object literal does not have one all the keys in
     * the required set.
     */
    void checkForRequiredKeys(
        Set<String> requiredKeys, Function<String, MalformedException> createException)
        throws MalformedException;
  }

  private static class ObjectLiteralMapImpl implements ObjectLiteralMap {
    private final Map<String, Node> stringToStringKeyMap;
    // The result of extractValueMap(). It will be populated if requested.
    private ImmutableMap<String, Node> valueMap = null;
    // The result of extractStringMap(). It will be populated if requested.
    private ImmutableMap<String, String> stringMap = null;

    private ObjectLiteralMapImpl(Map<String, Node> stringToStringKeyMap) {
      this.stringToStringKeyMap = stringToStringKeyMap;
    }

    @Override
    public @Nullable Node getValueNode(String key) {
      final Node stringKeyNode = stringToStringKeyMap.get(key);
      return stringKeyNode == null ? null : stringKeyNode.getOnlyChild();
    }

    @Override
    public boolean getBooleanValueOrFalse(String key) throws MalformedException {
      final Node stringKeyNode = stringToStringKeyMap.get(key);
      return extractBooleanStringKeyValue(stringKeyNode);
    }

    @Override
    public ImmutableMap<String, Node> extractValueMap() {
      if (valueMap == null) {
        ImmutableMap.Builder<String, Node> builder = ImmutableMap.builder();
        for (Entry<String, Node> entry : stringToStringKeyMap.entrySet()) {
          builder.put(entry.getKey(), entry.getValue().getOnlyChild());
        }
        valueMap = builder.buildOrThrow();
      }
      return valueMap;
    }

    @Override
    public ImmutableMap<String, String> extractStringToStringMap() throws MalformedException {
      if (stringMap == null) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (Entry<String, Node> entry : stringToStringKeyMap.entrySet()) {
          builder.put(
              entry.getKey(), extractStringFromStringExprNode(entry.getValue().getOnlyChild()));
        }
        stringMap = builder.buildOrThrow();
      }
      return stringMap;
    }

    @Override
    public void checkForUnexpectedKeys(
        Set<String> expectedKeys, Function<String, String> createErrorMessage)
        throws MalformedException {
      for (String key : stringToStringKeyMap.keySet()) {
        if (!expectedKeys.contains(key)) {
          throw new MalformedException(
              createErrorMessage.apply(key), stringToStringKeyMap.get(key));
        }
      }
    }

    @Override
    public void checkForRequiredKeys(
        Set<String> requiredKeys, Function<String, MalformedException> createException)
        throws MalformedException {
      for (String requiredKey : requiredKeys) {
        if (!stringToStringKeyMap.containsKey(requiredKey)) {
          throw createException.apply(requiredKey);
        }
      }
    }
  }

  /**
   * Returns an object to represent an object literal Node from the AST.
   *
   * <p>The object literal's members must all be `STRING_KEY` nodes.
   *
   * <p>No duplicate string keys are allowed.
   *
   * <p>A `null` argument is treated as if it were an empty object literal.
   *
   * @throws MalformedException If the Node does not meet the above requirements.
   */
  public static ObjectLiteralMap extractObjectLiteralMap(@Nullable Node objLit)
      throws MalformedException {
    if (objLit == null) {
      return new ObjectLiteralMapImpl(ImmutableMap.of());
    }
    if (!objLit.isObjectLit()) {
      throw new MalformedException("object literal expected", objLit);
    }
    final LinkedHashMap<String, Node> stringToStringKeyMap = new LinkedHashMap<>();
    for (Node stringKey = objLit.getFirstChild();
        stringKey != null;
        stringKey = stringKey.getNext()) {
      if (!stringKey.isStringKey()) {
        throw new MalformedException("string key expected", stringKey);
      }
      final String key = stringKey.getString();
      if (stringToStringKeyMap.containsKey(key)) {
        throw new MalformedException("duplicate string key: " + key, stringKey);
      }
      stringToStringKeyMap.put(stringKey.getString(), stringKey);
    }
    return new ObjectLiteralMapImpl(stringToStringKeyMap);
  }

  public static ImmutableList<Part> parseJsMessageTextIntoParts(String originalMsgText)
      throws PlaceholderFormatException {
    GoogGetMsgParsedText googGetMsgParsedText = extractGoogGetMsgParsedText(originalMsgText);
    return googGetMsgParsedText.getParts();
  }

  private static GoogGetMsgParsedText extractGoogGetMsgParsedText(final String originalMsgText)
      throws PlaceholderFormatException {
    String msgText = originalMsgText;
    ImmutableList.Builder<Part> partsBuilder = ImmutableList.builder();
    ImmutableSet.Builder<String> placeholderNamesBuilder = ImmutableSet.builder();
    while (true) {
      int phBegin = msgText.indexOf(JsMessage.PH_JS_PREFIX);
      if (phBegin < 0) {
        // Just a string literal
        partsBuilder.add(StringPart.create(msgText));
        break;
      } else {
        if (phBegin > 0) {
          // A string literal followed by a placeholder
          partsBuilder.add(StringPart.create(msgText.substring(0, phBegin)));
        }

        // A placeholder. Find where it ends
        int phEnd = msgText.indexOf(JsMessage.PH_JS_SUFFIX, phBegin);
        if (phEnd < 0) {
          throw new PlaceholderFormatException("Placeholder incorrectly formatted");
        }

        String phName = msgText.substring(phBegin + JsMessage.PH_JS_PREFIX.length(), phEnd);
        if (!JsMessage.isLowerCamelCaseWithNumericSuffixes(phName)) {
          throw new PlaceholderFormatException("Placeholder name not in lowerCamelCase: " + phName);
        }
        placeholderNamesBuilder.add(phName);
        partsBuilder.add(PlaceholderReference.createForJsName(phName));
        int nextPos = phEnd + JsMessage.PH_JS_SUFFIX.length();
        if (nextPos < msgText.length()) {
          // Iterate on the rest of the message value
          msgText = msgText.substring(nextPos);
        } else {
          // The message is parsed
          break;
        }
      }
    }
    final ImmutableSet<String> placeholderNames = placeholderNamesBuilder.build();
    final ImmutableList<Part> parts = partsBuilder.build();
    // NOTE: The methods defined below should do little to no computation, and definitely should
    // not throw exceptions.
    return new GoogGetMsgParsedText() {
      @Override
      public String getText() {
        return originalMsgText;
      }

      @Override
      public ImmutableSet<String> getPlaceholderNames() {
        return placeholderNames;
      }

      @Override
      public ImmutableList<Part> getParts() {
        return parts;
      }
    };
  }

  private interface GoogGetMsgParsedText {
    String getText();

    ImmutableSet<String> getPlaceholderNames();

    ImmutableList<Part> getParts();
  }

  /** Visit a call to goog.getMsgWithFallback. */
  private void visitFallbackFunctionCall(NodeTraversal t, Node call) {
    // Check to make sure the function call looks like:
    // goog.getMsgWithFallback(MSG_1, MSG_2);
    // or:
    // goog.getMsgWithFallback(some.import.MSG_1, some.import.MSG_2);
    if (!call.hasXChildren(3)
        || !isMessageIdentifier(call.getSecondChild())
        || !isMessageIdentifier(call.getLastChild())) {
      compiler.report(JSError.make(call, BAD_FALLBACK_SYNTAX));
      return;
    }

    Node firstArg = call.getSecondChild();
    JsMessage firstMessage = getJsMessageFromNode(t, firstArg);
    if (firstMessage == null) {
      compiler.report(JSError.make(firstArg, FALLBACK_ARG_ERROR, firstArg.getQualifiedName()));
      return;
    }

    Node secondArg = firstArg.getNext();
    JsMessage secondMessage = getJsMessageFromNode(t, secondArg);
    if (secondMessage == null) {
      compiler.report(JSError.make(secondArg, FALLBACK_ARG_ERROR, secondArg.getQualifiedName()));
      return;
    }

    processMessageFallback(call, firstMessage, secondMessage);
  }

  /**
   * Processes found JS message. Several examples of "standard" processing routines are:
   *
   * <ol>
   *   <li>extract all JS messages
   *   <li>replace JS messages with localized versions for some specific language
   *   <li>check that messages have correct syntax and present in localization bundle
   * </ol>
   *
   * @param message the found message
   * @param definition the definition of the object and usually contains all additional message
   *     information like message node/parent's node
   */
  protected abstract void processJsMessage(JsMessage message, JsMessageDefinition definition);

  /**
   * Processes the goog.getMsgWithFallback primitive. goog.getMsgWithFallback(MSG_1, MSG_2);
   *
   * <p>By default, does nothing.
   */
  void processMessageFallback(Node callNode, JsMessage message1, JsMessage message2) {}

  /** Returns whether the given JS identifier is a valid JS message name. */
  boolean isMessageName(String identifier) {
    return identifier.startsWith(MSG_PREFIX) || isScopedAliasesPrefix(identifier);
  }

  private static boolean isMessageIdentifier(Node node) {
    String qname = node.getQualifiedName();
    return qname != null && qname.contains(MSG_PREFIX);
  }

  /**
   * Extracts a message name (e.g. MSG_FOO) from either a NAME node or a GETPROP node. This should
   * cover all of the following cases:
   *
   * <ol>
   *   <li>a NAME node (e.g. MSG_FOO)
   *   <li>a NAME node which is the product of renaming (e.g. $module$contents$MSG_FOO)
   *   <li>a GETPROP node (e.g. some.import.MSG_FOO)
   * </ol>
   */
  private @Nullable JsMessage getJsMessageFromNode(NodeTraversal t, Node node) {
    String messageName = node.getQualifiedName();
    if (messageName == null || !messageName.contains(MSG_PREFIX)) {
      return null;
    }

    String messageKey = messageName.substring(messageName.indexOf(MSG_PREFIX));
    if (isUnnamedMessageName(messageKey)) {
      return getTrackedUnnamedMessage(t, messageName);
    } else {
      return getTrackedNormalMessage(messageKey);
    }
  }

  /** Returns whether the given message name is in the unnamed namespace. */
  private static boolean isUnnamedMessageName(String identifier) {
    return MSG_UNNAMED_PATTERN.matcher(identifier).matches();
  }

  /**
   * Checks a node's type.
   *
   * @throws MalformedException if the node is null or the wrong type
   */
  protected void checkNode(@Nullable Node node, Token type) throws MalformedException {
    if (node == null) {
      throw new MalformedException("Expected node type " + type + "; found: null", node);
    }
    if (node.getToken() != type) {
      throw new MalformedException(
          "Expected node type " + type + "; found: " + node.getToken(), node);
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
    private final JsMessage message;
    private final Node messageNode;

    private MessageLocation(JsMessage message, Node messageNode) {
      this.message = message;
      this.messageNode = messageNode;
    }
  }

  public static boolean isScopedAliasesPrefix(String name) {
    return SCOPED_ALIASES_PREFIX_PATTERN.matcher(name).lookingAt();
  }

  public static String removeScopedAliasesPrefix(String name) {
    return SCOPED_ALIASES_PREFIX_PATTERN.matcher(name).replaceFirst("MSG_");
  }
}
