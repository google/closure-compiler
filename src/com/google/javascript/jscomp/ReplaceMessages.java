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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.AstFactory.type;
import static com.google.javascript.jscomp.JsMessageVisitor.MESSAGE_TREE_MALFORMED;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.JsMessage.Part;
import com.google.javascript.jscomp.JsMessage.PlaceholderFormatException;
import com.google.javascript.jscomp.JsMessage.StringPart;
import com.google.javascript.jscomp.JsMessageVisitor.MalformedException;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.SideEffectFlags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * ReplaceMessages replaces user-visible messages with alternatives. It uses Google specific
 * JsMessageVisitor implementation.
 */
@GwtIncompatible("JsMessage")
public final class ReplaceMessages {
  public static final DiagnosticType BUNDLE_DOES_NOT_HAVE_THE_MESSAGE =
      DiagnosticType.error(
          "JSC_BUNDLE_DOES_NOT_HAVE_THE_MESSAGE",
          "Message with id = {0} could not be found in replacement bundle");

  public static final DiagnosticType INVALID_ALTERNATE_MESSAGE_PLACEHOLDERS =
      DiagnosticType.error(
          "JSC_INVALID_ALTERNATE_MESSAGE_PLACEHOLDERS",
          "Alternate message ID={0} placeholders ({1}) differs from {2} placeholders ({3}).");

  private final AbstractCompiler compiler;
  private final MessageBundle bundle;
  private final boolean strictReplacement;
  private final AstFactory astFactory;

  ReplaceMessages(AbstractCompiler compiler, MessageBundle bundle, boolean strictReplacement) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.bundle = bundle;
    this.strictReplacement = strictReplacement;
  }

  /**
   * When the returned pass is executed, the original `goog.getMsg()` etc. calls will be replaced
   * with a form that will survive unchanged through optimizations unless eliminated as unused.
   *
   * <p>After all optimizations are complete, the pass returned by `getReplacementCompletionPass()`.
   */
  public CompilerPass getMsgProtectionPass() {
    return new MsgProtectionPass();
  }

  interface MsgProtectionData {
    JsMessage getMessage();

    Node getMessageNode();

    Node getTemplateTextNode();

    @Nullable Node getPlaceholderValuesNode();

    MsgOptions getMessageOptions();
  }

  class MsgProtectionPass extends JsMessageVisitor {

    public MsgProtectionPass() {
      super(ReplaceMessages.this.compiler, bundle.idGenerator());
    }

    @Override
    public void process(Node externs, Node root) {
      // Add externs declarations for the function names we use in our replacements.
      NodeUtil.createSynthesizedExternsSymbol(compiler, ReplaceMessagesConstants.DEFINE_MSG_CALLEE);
      NodeUtil.createSynthesizedExternsSymbol(
          compiler, ReplaceMessagesConstants.FALLBACK_MSG_CALLEE);

      // JsMessageVisitor.process() does the traversal that calls the processX() methods below.
      super.process(externs, root);
    }

    @Override
    protected void processIcuTemplateDefinition(IcuTemplateDefinition definition) {
      performMessageProtection(
          new MsgProtectionData() {
            @Override
            public JsMessage getMessage() {
              return definition.getMessage();
            }

            @Override
            public Node getMessageNode() {
              return definition.getMessageNode();
            }

            @Override
            public Node getTemplateTextNode() {
              return definition.getTemplateTextNode();
            }

            @Override
            public @Nullable Node getPlaceholderValuesNode() {
              // There are no compile-time placeholder replacements for ICU templates
              return null;
            }

            @Override
            public MsgOptions getMessageOptions() {
              return ICU_MSG_OPTIONS;
            }
          });
    }

    @Override
    protected void processJsMessageDefinition(JsMessageDefinition definition) {
      // This is the currently preferred form.
      // `MSG_A = goog.getMsg('hello, {$name}', {name: getName()}, {html: true})`
      performMessageProtection(
          new MsgProtectionData() {
            @Override
            public JsMessage getMessage() {
              return definition.getMessage();
            }

            @Override
            public Node getMessageNode() {
              return definition.getMessageNode();
            }

            @Override
            public Node getTemplateTextNode() {
              return definition.getTemplateTextNode();
            }

            @Override
            public @Nullable Node getPlaceholderValuesNode() {
              return definition.getPlaceholderValuesNode();
            }

            @Override
            public MsgOptions getMessageOptions() {
              return getMsgOptionsFromDefinition(definition);
            }
          });
    }

    private void performMessageProtection(MsgProtectionData msgProtectionData) {
      final JsMessage message = msgProtectionData.getMessage();
      final Node callNode = msgProtectionData.getMessageNode();
      final Node originalMessageString = msgProtectionData.getTemplateTextNode();
      final Node placeholdersNode = msgProtectionData.getPlaceholderValuesNode();
      final MsgOptions msgOptions = msgProtectionData.getMessageOptions();

      checkState(callNode.isCall(), callNode);
      // `goog.getMsg('message string', {<substitutions>}, {<options>})`
      final Node googGetMsg = callNode.getFirstChild();

      // Construct
      // `__jscomp_define_msg__({<msg properties>}, {<substitutions>})`
      final String protectionFunctionName = ReplaceMessagesConstants.DEFINE_MSG_CALLEE;
      final Node newCallee =
          createProtectionFunctionCallee(protectionFunctionName).srcref(googGetMsg);
      final Node msgPropertiesNode =
          createMsgPropertiesNode(message, msgOptions).srcrefTree(originalMessageString);
      Node newCallNode =
          astFactory.createCall(newCallee, type(callNode), msgPropertiesNode).srcref(callNode);
      // If the result of this call (the message) is unused, there is no reason for optimizations
      // to preserve it.
      newCallNode.setSideEffectFlags(SideEffectFlags.NO_SIDE_EFFECTS);
      if (placeholdersNode != null) {
        checkState(placeholdersNode.isObjectLit(), placeholdersNode);
        // put quotes around the keys so they won't get renamed.
        for (Node strKey = placeholdersNode.getFirstChild();
            strKey != null;
            strKey = strKey.getNext()) {
          checkState(strKey.isStringKey(), strKey);
          strKey.setQuotedStringKey();
        }
        newCallNode.addChildToBack(placeholdersNode.detach());
      }
      callNode.replaceWith(newCallNode);
      compiler.reportChangeToEnclosingScope(newCallNode);
    }

    private Node createProtectionFunctionCallee(String protectionFunctionName) {
      final Node callee = astFactory.createNameWithUnknownType(protectionFunctionName);
      // The name is declared constant in the externs definition we created, so all references
      // to it must also be marked as constant for consistency's sake.
      callee.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      return callee;
    }

    @Override
    void processMessageFallback(Node callNode, JsMessage message1, JsMessage message2) {
      final Node originalCallee = checkNotNull(callNode.getFirstChild(), callNode);
      final Node fallbackCallee =
          createProtectionFunctionCallee(ReplaceMessagesConstants.FALLBACK_MSG_CALLEE)
              .srcref(originalCallee);

      final Node originalFirstArg = checkNotNull(originalCallee.getNext(), callNode);
      final Node firstMsgKey = astFactory.createString(message1.getKey()).srcref(originalFirstArg);

      final Node originalSecondArg = checkNotNull(originalFirstArg.getNext(), callNode);
      final Node secondMsgKey =
          astFactory.createString(message2.getKey()).srcref(originalSecondArg);

      // `__jscomp_msg_fallback__('MSG_ONE', MSG_ONE, 'MSG_TWO', MSG_TWO)`
      final Node newCallNode =
          astFactory
              .createCall(
                  fallbackCallee,
                  type(callNode),
                  firstMsgKey,
                  originalFirstArg.detach(),
                  secondMsgKey,
                  originalSecondArg.detach())
              .srcref(callNode);
      // If the result of this call (the message) is unused, there is no reason for optimizations
      // to preserve it.
      newCallNode.setSideEffectFlags(SideEffectFlags.NO_SIDE_EFFECTS);
      callNode.replaceWith(newCallNode);
      compiler.reportChangeToEnclosingScope(newCallNode);
    }
  }

  private MsgOptions getMsgOptionsFromDefinition(JsMessageDefinition definition) {
    return new MsgOptions() {
      @Override
      public boolean isIcuTemplate() {
        return false;
      }

      @Override
      public boolean escapeLessThan() {
        return definition.shouldEscapeLessThan();
      }

      @Override
      public boolean unescapeHtmlEntities() {
        return definition.shouldUnescapeHtmlEntities();
      }
    };
  }

  private Node createMsgPropertiesNode(JsMessage message, MsgOptions msgOptions) {
    QuotedKeyObjectLitBuilder msgPropsBuilder = new QuotedKeyObjectLitBuilder();
    msgPropsBuilder.addString("key", message.getKey());
    String altId = message.getAlternateId();
    if (altId != null) {
      msgPropsBuilder.addString("alt_id", altId);
    }
    final String meaning = message.getMeaning();
    if (meaning != null) {
      msgPropsBuilder.addString("meaning", meaning);
    }
    if (msgOptions.isIcuTemplate()) {
      msgPropsBuilder.addString("msg_text", message.asIcuMessageString());
      // Just being present is what records this option as true
      msgPropsBuilder.addString("isIcuTemplate", "");
    } else {
      msgPropsBuilder.addString("msg_text", message.asJsMessageString());
    }
    if (msgOptions.escapeLessThan()) {
      // Just being present is what records this option as true
      msgPropsBuilder.addString("escapeLessThan", "");
    }
    if (msgOptions.unescapeHtmlEntities()) {
      // Just being present is what records this option as true
      msgPropsBuilder.addString("unescapeHtmlEntities", "");
    }
    return msgPropsBuilder.build();
  }

  private final class QuotedKeyObjectLitBuilder {
    // LinkedHashMap to keep the keys in the order we set them so our output is deterministic.
    private final LinkedHashMap<String, Node> keyToValueNodeMap = new LinkedHashMap<>();

    private QuotedKeyObjectLitBuilder addString(String key, String value) {
      return addNode(key, astFactory.createString(value));
    }

    @CanIgnoreReturnValue
    private QuotedKeyObjectLitBuilder addNode(String key, Node node) {
      checkState(!keyToValueNodeMap.containsKey(key), "repeated key: %s", key);
      keyToValueNodeMap.put(key, node);
      return this;
    }

    private Node build() {
      final Node result = astFactory.createObjectLit();
      for (Entry<String, Node> entry : keyToValueNodeMap.entrySet()) {
        result.addChildToBack(astFactory.createQuotedStringKey(entry.getKey(), entry.getValue()));
      }
      return result;
    }
  }
  /**
   * When the returned pass is executed, the protected messages created by `getMsgProtectionPass()`
   * will be replaced by the final message form read from the appropriate message bundle.
   */
  public CompilerPass getReplacementCompletionPass() {
    return new ReplacementCompletionPass();
  }

  class ReplacementCompletionPass implements CompilerPass {
    // Keep track of which messages actually got translated, so we know what do do when we
    // see a message fallback call.
    final Set<String> translatedMsgKeys = new HashSet<>();

    @Override
    public void process(Node externs, Node root) {
      // for each `__jscomp_define_msg__` call in post-order traversal
      // replace it with the appropriate expression
      NodeTraversal.traverse(
          compiler,
          root,
          new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              ProtectedJsMessage protectedJsMessage =
                  ProtectedJsMessage.fromAstNode(n, bundle.idGenerator());
              if (protectedJsMessage != null) {
                visitMsgDefinition(protectedJsMessage);
              } else {
                ProtectedMsgFallback protectedMsgFallback = ProtectedMsgFallback.fromAstNode(n);
                if (protectedMsgFallback != null) {
                  visitMsgFallback(protectedMsgFallback);
                }
              }
            }
          });
    }

    void visitMsgDefinition(ProtectedJsMessage protectedJsMessage) {
      try {
        final JsMessage originalMsg = protectedJsMessage.jsMessage;
        final Node nodeToReplace = protectedJsMessage.definitionNode;
        final JsMessage translatedMsg =
            lookupMessage(protectedJsMessage.definitionNode, bundle, originalMsg);
        final JsMessage msgToUse;
        if (translatedMsg != null) {
          msgToUse = translatedMsg;
          // Remember that this one got translated in case it is used in a fallback.
          translatedMsgKeys.add(originalMsg.getKey());
        } else {
          if (strictReplacement) {
            compiler.report(
                JSError.make(nodeToReplace, BUNDLE_DOES_NOT_HAVE_THE_MESSAGE, originalMsg.getId()));
          }
          msgToUse = originalMsg;
        }
        final MsgOptions msgOptions = protectedJsMessage.getMsgOptions();
        final Map<String, Node> placeholderMap =
            extractPlaceholderValuesMapOrThrow(protectedJsMessage.substitutionsNode);
        final Node finalMsgConstructionExpression =
            constructStringExprNode(msgToUse, placeholderMap, msgOptions, nodeToReplace);
        finalMsgConstructionExpression.srcrefTreeIfMissing(nodeToReplace);
        nodeToReplace.replaceWith(finalMsgConstructionExpression);
        compiler.reportChangeToEnclosingScope(finalMsgConstructionExpression);
      } catch (MalformedException e) {
        compiler.report(JSError.make(e.getNode(), MESSAGE_TREE_MALFORMED, e.getMessage()));
      }
    }

    private void visitMsgFallback(ProtectedMsgFallback protectedMsgFallback) {
      final Node valueNodeToUse;
      if (translatedMsgKeys.contains(protectedMsgFallback.firstMsgKey)) {
        // Obviously use the first message, if it is translated.
        valueNodeToUse = protectedMsgFallback.firstMsgValue;
      } else if (translatedMsgKeys.contains(protectedMsgFallback.secondMsgKey)) {
        // Fallback to the second message if it has a translation.
        valueNodeToUse = protectedMsgFallback.secondMsgValue;
      } else {
        // If neither is translated, then use the first message as it is defined in the source code.
        valueNodeToUse = protectedMsgFallback.firstMsgValue;
      }
      valueNodeToUse.detach();
      protectedMsgFallback.callNode.replaceWith(valueNodeToUse);
      compiler.reportChangeToEnclosingScope(valueNodeToUse);
    }
  }

  static ImmutableMap<String, Node> extractPlaceholderValuesMapOrThrow(Node valuesObjLit) {
    try {
      return JsMessageVisitor.extractObjectLiteralMap(valuesObjLit).extractAsValueMap();
    } catch (MalformedException e) {
      throw new IllegalStateException(e);
    }
  }

  private static class ProtectedMsgFallback {
    final Node callNode;
    final String firstMsgKey;
    final Node firstMsgValue;
    final String secondMsgKey;
    final Node secondMsgValue;

    ProtectedMsgFallback(
        Node callNode,
        String firstMsgKey,
        Node firstMsgValue,
        String secondMsgKey,
        Node secondMsgValue) {
      this.callNode = callNode;
      this.firstMsgKey = firstMsgKey;
      this.firstMsgValue = firstMsgValue;
      this.secondMsgKey = secondMsgKey;
      this.secondMsgValue = secondMsgValue;
    }

    static @Nullable ProtectedMsgFallback fromAstNode(Node n) {
      if (!n.isCall()) {
        return null;
      }
      final Node callee = n.getFirstChild();
      if (!callee.matchesName(ReplaceMessagesConstants.FALLBACK_MSG_CALLEE)) {
        return null;
      }
      checkState(n.hasXChildren(5), "bad message fallback call: %s", n);
      final Node firstMsgKeyNode = callee.getNext();
      final String firstMsgKey = firstMsgKeyNode.getString();
      final Node firstMsgValue = firstMsgKeyNode.getNext();
      final Node secondMsgKeyNode = firstMsgValue.getNext();
      final String secondMsgKey = secondMsgKeyNode.getString();
      final Node secondMsgValue = secondMsgKeyNode.getNext();
      return new ProtectedMsgFallback(n, firstMsgKey, firstMsgValue, secondMsgKey, secondMsgValue);
    }
  }

  private @Nullable JsMessage lookupMessage(
      Node callNode, MessageBundle bundle, JsMessage message) {
    JsMessage translatedMessage = bundle.getMessage(message.getId());
    if (translatedMessage != null) {
      return translatedMessage;
    }

    String alternateId = message.getAlternateId();
    if (alternateId == null) {
      return null;
    }

    JsMessage alternateMessage = bundle.getMessage(alternateId);
    if (alternateMessage != null) {
      // Validate that the alternate message is compatible with this message. Ideally we'd also
      // check meaning and description, but they're not populated by `MessageBundle.getMessage`.
      final ImmutableSet<String> jsCodePlaceholderNames = message.jsPlaceholderNames();
      final ImmutableSet<String> alternateMsgPlaceholderNames =
          alternateMessage.jsPlaceholderNames();
      if (!Objects.equals(jsCodePlaceholderNames, alternateMsgPlaceholderNames)) {
        // The JS code definition has no placeholders, but the message we got from the translation
        // bundle does have placeholders.
        //
        // This can happen for ICU - formatted messages. They can contain placeholders in the
        // message string in the form "{PH_NAME}", which this compiler ignores, because they will
        // be replaced at runtime by passing the whole string to a message formatter method.
        // However, sometimes the translation bundle will represent the "{PH_NAME}" substring as
        // an actual placeholder reference, because it was generated by some tool other than
        // closure-compiler. In that case, we need to join all the parts of the message back
        // together as a single string with the "{PH_NAME}" references back in place.
        //
        // Messages with this form always start with a particular formula, which we can test for
        // here to make sure this isn't actually a case of something being broken in the translation
        // pipeline.
        if (jsCodePlaceholderNames.isEmpty() && isStartOfIcuMessage(message.asIcuMessageString())) {
          return alternateMessage;
        } else {
          compiler.report(
              JSError.make(
                  callNode,
                  INVALID_ALTERNATE_MESSAGE_PLACEHOLDERS,
                  alternateId,
                  String.valueOf(alternateMsgPlaceholderNames),
                  message.getKey(),
                  String.valueOf(jsCodePlaceholderNames)));
          return null;
        }
      }
    }
    return alternateMessage;
  }

  /**
   * When the returned pass is executed, the original `goog.getMsg()` etc. calls will all be
   * replaced with the final message form read from the message bundle.
   *
   * <p>This is the original way of running this pass as a single operation.
   */
  public CompilerPass getFullReplacementPass() {
    return new FullReplacementPass();
  }

  interface FullReplacementMsgData {
    JsMessage getMessage();

    Node getMessageNode();

    MsgOptions getMessageOptions();

    ImmutableMap<String, Node> getPlaceholderValueMap();
  }

  class FullReplacementPass extends JsMessageVisitor {

    public FullReplacementPass() {
      super(ReplaceMessages.this.compiler, bundle.idGenerator());
    }

    @Override
    void processMessageFallback(Node callNode, JsMessage message1, JsMessage message2) {
      boolean isFirstMessageTranslated = (lookupMessage(callNode, bundle, message1) != null);
      boolean isSecondMessageTranslated = (lookupMessage(callNode, bundle, message2) != null);
      Node replacementNode =
          (isSecondMessageTranslated && !isFirstMessageTranslated)
              ? callNode.getChildAtIndex(2)
              : callNode.getSecondChild();
      callNode.replaceWith(replacementNode.detach());
      Node changeScope = NodeUtil.getEnclosingChangeScopeRoot(replacementNode);
      if (changeScope != null) {
        compiler.reportChangeToChangeScope(changeScope);
      }
    }

    @Override
    protected void processIcuTemplateDefinition(IcuTemplateDefinition definition) {
      processFullReplacement(
          new FullReplacementMsgData() {
            @Override
            public JsMessage getMessage() {
              return definition.getMessage();
            }

            @Override
            public Node getMessageNode() {
              return definition.getMessageNode();
            }

            @Override
            public MsgOptions getMessageOptions() {
              return ICU_MSG_OPTIONS;
            }

            @Override
            public ImmutableMap<String, Node> getPlaceholderValueMap() {
              // There are no compile-time placeholder replacements for an ICU template message.
              return ImmutableMap.of();
            }
          });
    }

    @Override
    protected void processJsMessageDefinition(JsMessageDefinition definition) {
      processFullReplacement(
          new FullReplacementMsgData() {
            @Override
            public JsMessage getMessage() {
              return definition.getMessage();
            }

            @Override
            public Node getMessageNode() {
              return definition.getMessageNode();
            }

            @Override
            public MsgOptions getMessageOptions() {
              return getMsgOptionsFromDefinition(definition);
            }

            @Override
            public ImmutableMap<String, Node> getPlaceholderValueMap() {
              return definition.getPlaceholderValueMap();
            }
          });
    }

    private void processFullReplacement(FullReplacementMsgData fullReplacementMsgData) {
      final JsMessage message = fullReplacementMsgData.getMessage();
      final Node msgNode = fullReplacementMsgData.getMessageNode();
      final MsgOptions options = fullReplacementMsgData.getMessageOptions();
      final ImmutableMap<String, Node> placeholderValueMap =
          fullReplacementMsgData.getPlaceholderValueMap();

      // Get the replacement.
      JsMessage replacement = lookupMessage(msgNode, bundle, message);
      if (replacement == null) {
        if (strictReplacement) {
          compiler.report(JSError.make(msgNode, BUNDLE_DOES_NOT_HAVE_THE_MESSAGE, message.getId()));
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
      try {
        // Build the replacement tree.
        newValue = constructStringExprNode(replacement, placeholderValueMap, options, msgNode);
      } catch (MalformedException e) {
        compiler.report(JSError.make(e.getNode(), MESSAGE_TREE_MALFORMED, e.getMessage()));
        newValue = msgNode;
      }

      if (newValue != msgNode) {
        newValue.srcrefTreeIfMissing(msgNode);
        msgNode.replaceWith(newValue);
        compiler.reportChangeToEnclosingScope(newValue);
      }
    }
  }

  /**
   * Creates a parse tree corresponding to the remaining message parts in an iteration. The result
   * consists of one or more STRING nodes, placeholder replacement value nodes (which can be
   * arbitrary expressions), and ADD nodes.
   *
   * @param placeholderMap map from placeholder names to value Nodes
   * @return the root of the constructed parse tree
   */
  private Node constructStringExprNode(
      JsMessage msgToUse, Map<String, Node> placeholderMap, MsgOptions options, Node nodeToReplace)
      throws MalformedException {

    if (placeholderMap.isEmpty()) {
      // The compiler does not expect to do any placeholder substitution, because the message
      // definition in the JS code doesn't have any placeholders.
      if (options.isIcuTemplate()) {
        // This message was declared as an ICU template. Its placeholders, if any, will not be
        // replaced during compilation, but rather at runtime by a special localization method.
        // We just need to put the string back together again, if it was broken up to include
        // placeholders.
        return createNodeForMsgString(options, msgToUse.asIcuMessageString());
      } else if (msgToUse.jsPlaceholderNames().isEmpty()) {
        // The translated message read from the bundle also has no placeholders.
        // It doesn't really matter which asXMessageString() call we make, since they return
        // the same thing when there are no placeholders.
        return createNodeForMsgString(options, msgToUse.asJsMessageString());
      } else {
        // The JS code definition has no placeholders, but the message we got from the translation
        // bundle does have placeholders.
        //
        // This can happen for ICU - formatted messages. They can contain placeholders in the
        // message string in the form "{PH_NAME}", which this compiler ignores, because they will
        // be replaced at runtime by passing the whole string to a message formatter method.
        // However, sometimes the translation bundle will represent the "{PH_NAME}" substring as
        // an actual placeholder reference, because it was generated by some tool other than
        // closure-compiler. In that case, we need to join all the parts of the message back
        // together as a single string with the "{PH_NAME}" references back in place.
        //
        // Messages with this form always start with a particular formula, which we can test for
        // here to make sure this isn't actually a case of something being broken in the translation
        // pipeline.
        final String icuMsgString = msgToUse.asIcuMessageString();
        if (isStartOfIcuMessage(icuMsgString)) {
          return createNodeForMsgString(options, icuMsgString);
        } else {
          throw new MalformedException(
              "The translated message has placeholders, but the definition in the JS code does"
                  + " not.",
              nodeToReplace);
        }
      }
    }
    // In some edge cases a message might have normal string parts that are consecutive.
    // Join them together before processing them further.
    // TODO(bradfordcsmith): There's a test case with an "&amp;" escape broken across 2 string
    // parts. It fails if we stop doing this merging in advance, but I suspect there's no real
    // life case where this merging is really necessary. I think it's a left-over from when the
    // bundle-reading code would automatically convert ICU message placeholders into string parts
    // without actually merging them with the neighboring string parts.
    List<Part> msgParts = mergeStringParts(msgToUse.getParts());
    if (msgParts.isEmpty()) {
      return astFactory.createString("");
    } else {
      Node resultNode = null;
      for (Part msgPart : msgParts) {
        final Node partNode;
        if (msgPart.isPlaceholder()) {
          final String jsPlaceholderName = msgPart.getJsPlaceholderName();
          final Node valueNode = placeholderMap.get(jsPlaceholderName);
          if (valueNode == null) {
            throw new MalformedException(
                "Unrecognized message placeholder referenced: " + jsPlaceholderName, nodeToReplace);
          }
          partNode = valueNode.cloneTree();
        } else {
          // The part is just a string literal.
          partNode = createNodeForMsgString(options, msgPart.getString());
        }
        resultNode = (resultNode == null) ? partNode : astFactory.createAdd(resultNode, partNode);
      }
      return resultNode;
    }
  }

  private Node createNodeForMsgString(MsgOptions options, String s) {
    if (options.escapeLessThan()) {
      // Note that "&" is not replaced because the translation can contain HTML entities.
      s = s.replace("<", "&lt;");
    }
    if (options.unescapeHtmlEntities()) {
      // Unescape entities that need to be escaped when embedding HTML or XML in data/attributes
      // of an HTML/XML document. See https://www.w3.org/TR/xml/#sec-predefined-ent.
      // Note that "&amp;" must be the last to avoid "creating" new entities.
      // To print an html entity in the resulting message, double-escape: `&amp;amp;`.
      s =
          s.replace("&lt;", "<")
              .replace("&gt;", ">")
              .replace("&apos;", "'")
              .replace("&quot;", "\"")
              .replace("&amp;", "&");
    }
    return astFactory.createString(s);
  }

  /** Options for escaping characters in translated messages. */
  interface MsgOptions {

    /** True if the message is defined using the ICU template declaration method. */
    boolean isIcuTemplate();

    /** Replace `'<'` with `'&lt;'` in the message. */
    boolean escapeLessThan();

    /**
     * Replace these escaped entities with their literal characters in the message (Overrides
     * escapeLessThan)
     *
     * <pre>
     * '&lt;' -> '<'
     * '&gt;' -> '>'
     * '&apos;' -> "'"
     * '&quot;' -> '"'
     * '&amp;' -> '&'
     * </pre>
     */
    boolean unescapeHtmlEntities();
  }

  private static final MsgOptions ICU_MSG_OPTIONS =
      new MsgOptions() {
        @Override
        public boolean isIcuTemplate() {
          return true;
        }

        @Override
        public boolean escapeLessThan() {
          // The compiler doesn't do escaping for ICU messages.
          return false;
        }

        @Override
        public boolean unescapeHtmlEntities() {
          // The compiler doesn't do unescaping for ICU messages.
          return false;
        }
      };

  /** Merges consecutive string parts in the list of message parts. */
  private static List<Part> mergeStringParts(List<Part> parts) {
    List<Part> result = new ArrayList<>();
    for (Part part : parts) {
      if (part.isPlaceholder()) {
        result.add(part);
      } else {
        Part lastPart = result.isEmpty() ? null : Iterables.getLast(result);
        if (lastPart == null || lastPart.isPlaceholder()) {
          result.add(part);
        } else {
          result.set(result.size() - 1, StringPart.create(lastPart.getString() + part.getString()));
        }
      }
    }
    return result;
  }

  /**
   * Holds information about the protected form of a translatable message that appears in the AST.
   *
   * <p>The original translatable messages are replaced with this protected form by the logic in
   * `ReplaceMessages` to protect the message information through the optimization passes.
   *
   * <pre><code>
   *   // original
   *   var MSG_GREETING = goog.getMsg('Hello, {$name}!', {name: person.getName()}, {html: false});
   *   // protected form
   *   var MSG_GREETING = __jscomp_define_msg__(
   *       {
   *         'key': 'MSG_GREETING',
   *         'msg_text': 'Hello, {$name}!',
   *         'escapeLessThan': ''
   *       },
   *       {'name': person.getName()});
   * </code></pre>
   */
  public static class ProtectedJsMessage {

    private final JsMessage jsMessage;
    // The expression Node that defines the message and should be replaced with the localized
    // message.
    private final Node definitionNode;
    // e.g. `{ name: x.getName(), age: x.getAgeString() }`
    private final @Nullable Node substitutionsNode;
    // The message was defined in JS code using the ICU template method.
    private final boolean isIcuTemplate;
    // Replace `'<'` with `'&lt;'` in the message.
    private final boolean escapeLessThan;
    // Replace these escaped entities with their literal characters in the message
    // (Overrides escapeLessThan)
    // '&lt;' -> '<'
    // '&gt;' -> '>'
    // '&apos;' -> "'"
    // '&quot;' -> '"'
    // '&amp;' -> '&'
    private final boolean unescapeHtmlEntities;

    private ProtectedJsMessage(
        JsMessage jsMessage,
        Node definitionNode,
        @Nullable Node substitutionsNode,
        boolean isIcuTemplate,
        boolean escapeLessThan,
        boolean unescapeHtmlEntities) {
      this.jsMessage = jsMessage;
      this.definitionNode = definitionNode;
      this.substitutionsNode = substitutionsNode;
      this.isIcuTemplate = isIcuTemplate;
      this.escapeLessThan = escapeLessThan;
      this.unescapeHtmlEntities = unescapeHtmlEntities;
    }

    public static @Nullable ProtectedJsMessage fromAstNode(
        Node node, JsMessage.IdGenerator idGenerator) {
      if (!node.isCall()) {
        return null;
      }
      final Node calleeNode = checkNotNull(node.getFirstChild(), node);
      if (!calleeNode.matchesName(ReplaceMessagesConstants.DEFINE_MSG_CALLEE)) {
        return null;
      }
      final Node propertiesNode = checkNotNull(calleeNode.getNext(), calleeNode);
      final Node substitutionsNode = propertiesNode.getNext();
      boolean escapeLessThanOption = false;
      boolean unescapeHtmlEntitiesOption = false;
      boolean isIcuTemplate = false;
      JsMessage.Builder jsMessageBuilder = new JsMessage.Builder();
      checkState(propertiesNode.isObjectLit(), propertiesNode);
      String msgKey = null;
      String meaning = null;
      for (Node strKey = propertiesNode.getFirstChild();
          strKey != null;
          strKey = strKey.getNext()) {
        checkState(strKey.isStringKey(), strKey);
        String key = strKey.getString();
        Node valueNode = strKey.getOnlyChild();
        checkState(valueNode.isStringLit(), valueNode);
        String value = valueNode.getString();
        switch (key) {
          case "key":
            jsMessageBuilder.setKey(value);
            msgKey = value;
            break;
          case "meaning":
            jsMessageBuilder.setMeaning(value);
            meaning = value;
            break;
          case "alt_id":
            jsMessageBuilder.setAlternateId(value);
            break;
          case "msg_text":
            try {
              // NOTE: If the text is for an ICU template, then it will not contain any
              // placeholders ("{$placeholderName}"), so it will be treated as a single string
              // part.
              jsMessageBuilder.appendParts(JsMessageVisitor.parseJsMessageTextIntoParts(value));
            } catch (PlaceholderFormatException unused) {
              // Somehow we stored the protected message text incorrectly, which should never
              // happen.
              throw new IllegalStateException(
                  valueNode.getLocation() + ": Placeholder incorrectly formatted: >" + value + "<");
            }
            break;
          case "isIcuTemplate":
            isIcuTemplate = true;
            break;
          case "escapeLessThan":
            // Just being present enables this option
            escapeLessThanOption = true;
            break;
          case "unescapeHtmlEntities":
            // just being present enables this option
            unescapeHtmlEntitiesOption = true;
            break;
          default:
            throw new IllegalStateException("unknown protected message key: " + strKey);
        }
      }
      final String externalMessageId = JsMessageVisitor.getExternalMessageId(msgKey);
      if (externalMessageId != null) {
        // MSG_EXTERNAL_12345 = ...
        jsMessageBuilder.setIsExternalMsg(true).setId(externalMessageId);
      } else {
        // NOTE: If the message was anonymous (assigned to a variable or property named
        // MSG_UNNAMED_XXX), the key we have here will be the one generated from the message
        // text, and we won't end up setting the isAnonymous flag. Nothing seems to use that
        // flag...
        // TODO(bradfordcsmith): Maybe remove the isAnonymous flag for jsMessage objects?
        final String meaningForIdGeneration =
            meaning != null ? meaning : JsMessageVisitor.removeScopedAliasesPrefix(msgKey);
        if (idGenerator != null) {
          jsMessageBuilder.setId(
              idGenerator.generateId(meaningForIdGeneration, jsMessageBuilder.getParts()));
        } else {
          jsMessageBuilder.setId(meaningForIdGeneration);
        }
      }
      return new ProtectedJsMessage(
          jsMessageBuilder.build(),
          node,
          substitutionsNode,
          isIcuTemplate,
          escapeLessThanOption,
          unescapeHtmlEntitiesOption);
    }

    MsgOptions getMsgOptions() {
      return new MsgOptions() {
        @Override
        public boolean isIcuTemplate() {
          return isIcuTemplate;
        }

        @Override
        public boolean escapeLessThan() {
          return escapeLessThan;
        }

        @Override
        public boolean unescapeHtmlEntities() {
          return unescapeHtmlEntities;
        }
      };
    }
  }

  /**
   * Detects an ICU-formatted plural or select message. Any placeholders occurring inside these
   * messages must be rewritten in ICU format.
   */
  static boolean isStartOfIcuMessage(String part) {
    // ICU messages start with a '{' followed by an identifier, followed by a ',' and then 'plural'
    // or 'select' followed by another comma.
    // the 'startsWith' check is redundant but should allow us to skip using the matcher
    if (!part.startsWith("{")) {
      return false;
    }
    int commaIndex = part.indexOf(',', 1);
    // if commaIndex == 1 that means the identifier is empty, which isn't allowed.
    if (commaIndex <= 1) {
      return false;
    }
    int nextBracketIndex = part.indexOf('{', 1);
    return (nextBracketIndex == -1 || nextBracketIndex > commaIndex)
        && (part.startsWith("plural,", commaIndex + 1)
            || part.startsWith("select,", commaIndex + 1));
  }
}
