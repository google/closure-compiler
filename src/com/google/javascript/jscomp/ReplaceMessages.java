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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.JsMessage.Part;
import com.google.javascript.jscomp.JsMessage.PlaceholderFormatException;
import com.google.javascript.jscomp.JsMessage.StringPart;
import com.google.javascript.jscomp.JsMessageVisitor.ExtractedIcuTemplateParts;
import com.google.javascript.jscomp.JsMessageVisitor.IcuMessageTemplateString;
import com.google.javascript.jscomp.JsMessageVisitor.MalformedException;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.SideEffectFlags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * ReplaceMessages replaces user-visible messages with alternatives. It uses Google specific
 * JsMessageVisitor implementation.
 */
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
  private final boolean collapsePropertiesHasRun;
  private final AstFactory astFactory;

  ReplaceMessages(AbstractCompiler compiler, MessageBundle bundle, boolean strictReplacement) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.bundle = bundle;
    this.strictReplacement = strictReplacement;
    this.collapsePropertiesHasRun =
        compiler.getOptions().getPropertyCollapseLevel() == PropertyCollapseLevel.ALL
            && compiler.getOptions().doLateLocalization();
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

  record MsgProtectionData(
      JsMessage message,
      Node messageNode,
      Node templateTextNode,
      @Nullable Node placeholderValuesNode,
      MsgOptions messageOptions) {}

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
          new MsgProtectionData(
              /* message= */ definition.getMessage(),
              /* messageNode= */ definition.getMessageNode(),
              /* templateTextNode= */ definition.getTemplateTextNode(),
              // There are no compile-time placeholder replacements for ICU templates
              /* placeholderValuesNode= */ null,
              /* messageOptions= */ ICU_MSG_OPTIONS));
    }

    @Override
    protected void processJsMessageDefinition(JsMessageDefinition definition) {
      // This is the currently preferred form.
      // `MSG_A = goog.getMsg('hello, {$name}', {name: getName()}, {html: true})`
      performMessageProtection(
          new MsgProtectionData(
              /* message= */ definition.getMessage(),
              /* messageNode= */ definition.getMessageNode(),
              /* templateTextNode= */ definition.getTemplateTextNode(),
              /* placeholderValuesNode= */ definition.getPlaceholderValuesNode(),
              /* messageOptions= */ getMsgOptionsFromDefinition(definition)));
    }

    private void performMessageProtection(MsgProtectionData msgProtectionData) {
      final JsMessage message = msgProtectionData.message();
      final Node callNode = msgProtectionData.messageNode();
      final Node originalMessageString = msgProtectionData.templateTextNode();
      final Node placeholdersNode = msgProtectionData.placeholderValuesNode();
      final MsgOptions msgOptions = msgProtectionData.messageOptions();

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
    return new MsgOptions(
        /* isIcuTemplate= */ false,
        /* escapeLessThan= */ definition.shouldEscapeLessThan(),
        /* unescapeHtmlEntities= */ definition.shouldUnescapeHtmlEntities());
  }

  private Node createMsgPropertiesNode(JsMessage message, MsgOptions msgOptions) {
    QuotedKeyObjectLitBuilder msgPropsBuilder = new QuotedKeyObjectLitBuilder();
    msgPropsBuilder.addString("key", message.getKey());
    if (msgOptions.isIcuTemplate() && !message.canonicalPlaceholderNames().isEmpty()) {
      // ICU messages created using `declareIcuTemplate` can get stored into the XMB file as
      // multiple parts if necessary to record example or original code text.
      // `icu_placeholder_names` stores these parts of the ICU message, which allows us to
      // correctly calculate the message ID in the protected message.
      final Node namesArrayLit = astFactory.createArraylit();
      for (String name : message.canonicalPlaceholderNames()) {
        namesArrayLit.addChildToBack(astFactory.createString(name));
      }
      // Example:
      // declareIcuTemplate('blah blah {PH1} blah {PH2}', ... );
      // icu_placeholder_names: ['PH1', 'PH2']
      msgPropsBuilder.addNode("icu_placeholder_names", namesArrayLit);
    }
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

    @CanIgnoreReturnValue
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
    final Set<String> translatedMsgKeys = new LinkedHashSet<>();

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

  record FullReplacementMsgData(
      JsMessage message,
      Node messageNode,
      MsgOptions messageOptions,
      ImmutableMap<String, Node> placeholderValueMap) {}

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
      Node changeScope = ChangeTracker.getEnclosingChangeScopeRoot(replacementNode);
      if (changeScope != null) {
        compiler.reportChangeToChangeScope(changeScope);
      }
    }

    @Override
    protected void processIcuTemplateDefinition(IcuTemplateDefinition definition) {
      processFullReplacement(
          new FullReplacementMsgData(
              /* message= */ definition.getMessage(),
              /* messageNode= */ definition.getMessageNode(),
              /* messageOptions= */ ICU_MSG_OPTIONS,
              // There are no compile-time placeholder replacements for an ICU template message.
              /* placeholderValueMap= */ ImmutableMap.of()));
    }

    @Override
    protected void processJsMessageDefinition(JsMessageDefinition definition) {
      processFullReplacement(
          new FullReplacementMsgData(
              /* message= */ definition.getMessage(),
              /* messageNode= */ definition.getMessageNode(),
              /* messageOptions= */ getMsgOptionsFromDefinition(definition),
              /* placeholderValueMap= */ definition.getPlaceholderValueMap()));
    }

    private void processFullReplacement(FullReplacementMsgData fullReplacementMsgData) {
      final JsMessage message = fullReplacementMsgData.message();
      final Node msgNode = fullReplacementMsgData.messageNode();
      final MsgOptions options = fullReplacementMsgData.messageOptions();
      final ImmutableMap<String, Node> placeholderValueMap =
          fullReplacementMsgData.placeholderValueMap();

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
   * Outputs a message with a hook expression that returns the correct variant based on the value of
   * `goog.viewerGrammaticalGender` from XTB files with gendered messages.
   *
   * <p>With placeholders:
   *
   * <pre>{@code
   * var WELCOME_MSG = function(name) {
   *    return goog.msgKind.MASCULINE ? "Bienvenido " + name :
   *           goog.msgKind.FEMININE ? "Bienvenida " + name :
   *           goog.msgKind.NEUTER ? "Les damos la bienvenida " + name :
   *           "Les damos la bienvenida " + name;
   * }(user.getName());
   * }</pre>
   */
  private Node createNodeForGenderedMsgString(
      JsMessage message, Map<String, Node> placeholderMap, MsgOptions options, Node nodeToReplace)
      throws MalformedException {

    // Dynamically build parameter list with unique ids for each placeholder
    Node paramList = astFactory.createParamList();
    AstFactory.Type type = type(nodeToReplace);
    String uniqueId =
        compiler
            .getUniqueIdSupplier()
            .getUniqueId(compiler.getInput(NodeUtil.getInputId(nodeToReplace)));
    Map<String, String> placeholderMapIds = new LinkedHashMap<>();
    for (String placeholderName : placeholderMap.keySet()) {
      String placeholderId = placeholderName + uniqueId;
      paramList.addChildToBack(astFactory.createName(placeholderId, type));
      placeholderMapIds.put(placeholderName, placeholderId);
    }

    Node hookExpression =
        createHookExpressionForGenderedMsg(
            type, message, options, nodeToReplace, placeholderMapIds);

    if (placeholderMap.isEmpty()) {
      // The translated message read from the bundle is one of the following:
      // 1. has gendered variants and no placeholders
      // 2. has gendered variants and is an icu template because icu templates do not have
      // placeholders

      // Create the hook expression for the gendered message. This will be used to create the call
      // node if there are placeholders, or returned directly if there are no placeholders.
      return hookExpression;
    }

    Node function =
        astFactory.createFunction("", paramList, IR.block(IR.returnNode(hookExpression)), type);
    function.setColor(nodeToReplace.getColor());
    compiler.reportChangeToChangeScope(function);

    Node callNode = astFactory.createCall(function, type);

    // Add the placeholder values to the call
    for (Node node : placeholderMap.values()) {
      callNode.addChildToBack(node.cloneTree());
    }
    return callNode;
  }

  /**
   * Creates a node representing the grammatical gender condition.
   *
   * <p>For example:
   *
   * <pre>{@code
   * goog.msgKind.MASCULINE ? "Bienvenido + name" :
   * goog.msgKind.FEMININE ? "Bienvenida + name" :
   * goog.msgKind.NEUTER ? "Les damos la bienvenida + name" :
   * "Les damos la bienvenida + name";
   * }</pre>
   */
  private Node createConditionForGrammaticalGender(
      AstFactory.Type type, JsMessage.GrammaticalGenderCase grammaticalGender, Node nodeToReplace) {

    // NOTE: Collapse properties isn't guarantee to collapse any given property but if
    // we get a partial collapse of "goog.msgKind" then something has gone very wrong
    // so this seems reasonable rather than the alternative (traversing the AST to find the values).
    if (collapsePropertiesHasRun) {
      String referenceName =
          switch (grammaticalGender) {
            case MASCULINE -> "goog$msgKind$MASCULINE";
            case FEMININE -> "goog$msgKind$FEMININE";
            case NEUTER -> "goog$msgKind$NEUTER";
            default -> "goog$msgKind$OTHER";
          };
      Node result = astFactory.createName(referenceName, type(nodeToReplace));
      result.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      return result;
    } else {
      Node googNode = astFactory.createName("goog", type);
      googNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      return astFactory.createGetProp(
          astFactory.createGetProp(googNode, "msgKind", type(nodeToReplace)),
          grammaticalGender.toString(),
          type(nodeToReplace));
    }
  }

  /**
   * Creates a ternary expression with the gendered message variants.
   *
   * <p>For example:
   *
   * <pre>{@code
   * goog.msgKind.MASCULINE ? "Bienvenido + name" :
   * goog.msgKind.FEMININE ? "Bienvenida + name" :
   * goog.msgKind.NEUTER ? "Les damos la bienvenida + name" :
   * "Les damos la bienvenida + name";
   * }</pre>
   */
  private Node createHookExpressionForGenderedMsg(
      AstFactory.Type type,
      JsMessage msg,
      MsgOptions options,
      Node nodeToReplace,
      Map<String, String> placeholderMapIds)
      throws MalformedException {

    Node hookHead = IR.hook(IR.name(""), IR.string(""), IR.string(""));
    hookHead.setColor(nodeToReplace.getColor());
    // The previous hook condition needing to be replaced
    Node placeholderHook = hookHead;

    PlaceholderNodeProvider placeholderNodeProvider =
        (placeholderName) -> {
          String placeholderId = placeholderMapIds.get(placeholderName);
          return placeholderId != null
              ? astFactory.createName(placeholderId, type(nodeToReplace))
              : null;
        };

    for (JsMessage.GrammaticalGenderCase grammaticalGender : msg.getGenderedMessageVariants()) {
      // Skip the OTHER case as it should be the last case in the hook expression
      if (grammaticalGender.equals(JsMessage.GrammaticalGenderCase.OTHER)) {
        continue;
      }
      // Hook condition ex: `goog.msgKind.MASCULINE?` or `goog.msgKind.FEMININE ?` or
      // `goog.msgKind.NEUTER ?`
      Node condition = createConditionForGrammaticalGender(type, grammaticalGender, nodeToReplace);

      // The last child of the hook expression will be replaced with the next currentHook
      Node currentHook =
          IR.hook(
              condition,
              buildMessageNode(
                  msg.getGenderedMessageParts(grammaticalGender),
                  options,
                  nodeToReplace,
                  placeholderNodeProvider,
                  /* isGenderedMsg= */ true),
              astFactory.createString(""));
      currentHook.setColor(nodeToReplace.getColor());

      if (placeholderHook.getParent() != null) {
        // Replace the previous hook condition with the current hook condition
        Node parent = placeholderHook.getParent();
        parent.getLastChild().replaceWith(currentHook);
        placeholderHook = currentHook.getLastChild();
      } else {
        hookHead = currentHook;
        placeholderHook = currentHook.getLastChild();
      }
    }
    // The OTHER is always the last case in the hook expression
    placeholderHook
        .getParent()
        .getLastChild()
        .replaceWith(
            buildMessageNode(
                msg.getGenderedMessageParts(JsMessage.GrammaticalGenderCase.OTHER),
                options,
                nodeToReplace,
                placeholderNodeProvider,
                /* isGenderedMsg= */ true));
    return hookHead;
  }

  interface PlaceholderNodeProvider {
    @Nullable Node get(String placeholderName);
  }

  /**
   * Creates a parse tree corresponding to a list of message parts. The result consists of one or
   * more STRING nodes, placeholder replacement value nodes (which can be arbitrary expressions),
   * and ADD nodes.
   *
   * @param msgParts the parts of the message to build
   * @param options message escaping options
   * @param nodeToReplace the original node being replaced (for context)
   * @param placeholderNodeProvider a function that provides the value Node for a given placeholder
   *     name
   * @param isGenderedMsg whether this message is part of a gendered message structure
   * @return the root of the constructed parse tree
   */
  private Node buildMessageNode(
      List<Part> msgParts,
      MsgOptions options,
      Node nodeToReplace,
      PlaceholderNodeProvider placeholderNodeProvider,
      boolean isGenderedMsg)
      throws MalformedException {

    // A message might have normal string parts that are consecutive.
    // Join them together before processing them further. This allows split HTML entities
    // to be escaped.
    List<Part> parts = mergeStringParts(msgParts);
    if (parts.isEmpty()) {
      return astFactory.createString("");
    }
    Node message = null;
    for (Part msgPart : parts) {
      final Node partNode;
      if (msgPart.isPlaceholder()) {
        String jsPlaceholderName = msgPart.getJsPlaceholderName();
        Node valueNode = placeholderNodeProvider.get(jsPlaceholderName);
        if (valueNode == null) {
          if (isGenderedMsg) {
            // Add the ICU placeholder directly to the message ex: 'Hello {NAME}'
            partNode = astFactory.createString("{" + msgPart.getCanonicalPlaceholderName() + "}");
          } else {
            throw new MalformedException(
                "Unrecognized message placeholder referenced: " + jsPlaceholderName, nodeToReplace);
          }
        } else {
          partNode = valueNode.cloneTree();
        }
      } else {
        // The part is just a string literal.
        partNode = createNodeForMsgString(options, msgPart.getString());
      }

      if (message == null) {
        message = partNode;
      } else if (partNode.isString() && message.isString()) {
        message = astFactory.createString(message.getString() + partNode.getString());
      } else {
        message = astFactory.createAdd(message, partNode);
      }
    }
    return message;
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
      JsMessage message, Map<String, Node> placeholderMap, MsgOptions options, Node nodeToReplace)
      throws MalformedException {

    // If the message has gendered variants, create a hook expression containing the gendered
    // variants.
    if (!message.getGenderedMessageVariants().isEmpty()) {
      return createNodeForGenderedMsgString(message, placeholderMap, options, nodeToReplace);
    } else {
      return constructNonGenderedMsgNode(message, placeholderMap, options, nodeToReplace);
    }
  }

  private Node constructNonGenderedMsgNode(
      JsMessage message, Map<String, Node> placeholderMap, MsgOptions options, Node nodeToReplace)
      throws MalformedException {
    if (placeholderMap.isEmpty()) {
      // The compiler does not expect to do any placeholder substitution, because the message
      // definition in the JS code doesn't have any placeholders.
      if (options.isIcuTemplate()) {
        // This message was declared as an ICU template. Its placeholders, if any, will not be
        // replaced during compilation, but rather at runtime by a special localization method.
        // We just need to put the string back together again, if it was broken up to include
        // placeholders.
        return createNodeForMsgString(options, message.asIcuMessageString());
      } else if (message.jsPlaceholderNames().isEmpty()) {
        // The translated message read from the bundle also has no placeholders.
        // It doesn't really matter which asXMessageString() call we make, since they return
        // the same thing when there are no placeholders.
        return createNodeForMsgString(options, message.asJsMessageString());
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
        final String icuMsgString = message.asIcuMessageString();
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

    return buildMessageNode(
        message.getParts(),
        options,
        nodeToReplace,
        placeholderMap::get,
        /* isGenderedMsg= */ false);
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
  record MsgOptions(
      /** True if the message is defined using the ICU template declaration method. */
      boolean isIcuTemplate,
      /** Replace `'<'` with `'&lt;'` in the message. */
      boolean escapeLessThan,
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
      boolean unescapeHtmlEntities) {}

  private static final MsgOptions ICU_MSG_OPTIONS =
      new MsgOptions(
          /* isIcuTemplate= */ true,
          /* escapeLessThan= */ false,
          /* unescapeHtmlEntities= */ false);

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
      Set<String> icuPlaceholderNames = new LinkedHashSet<>();
      String messageText = null;
      Node messageTextNode = null;
      for (Node strKey = propertiesNode.getFirstChild();
          strKey != null;
          strKey = strKey.getNext()) {
        checkState(strKey.isStringKey(), strKey);
        String key = strKey.getString();
        Node valueNode = strKey.getOnlyChild();
        if (key.equals("icu_placeholder_names")) {
          checkState(valueNode.isArrayLit(), "icu_placeholder_names must be an array");
          // If the message is an ICU template and `icu_placeholder_names` is present, then there
          // are placeholders in the message. These placeholders will be replaced at runtime, but
          // it is important that we keep track of these placeholders because it means that the
          // ICU template CANNOT be treated as a single string part, because having placeholders
          // means that the message has multiple parts.
          // When a message is a `declareIcuTemplate` with multiple parts, we generate a `msg id`
          // in the XMB, which is sent to the Translation Console so that the translators can
          // translate this. We generate the deterministic `msg id` using an algorithm that takes
          // into account how many parts the message has.
          // Now during JSCompiler compilation process, we protect the message by wrapping it in a
          // `__jscomp_define_msg__` (for safety because we don't want any of our optimization
          // passes to change the message).
          // Later in this method, we generate an ID (using `idGenerator.generateId()`) and use
          // this to lookup a message in the translated XTB file. As I mentioned earlier, the
          // algorithm for generating an ID needs to know the correct parts of the message, so we
          // fail to generate the same ID we did when we added the message to the XMB.
          // This `icu_placeholder_names` field is necessary to help us figure out the correct
          // parts of the message, in order to generate the correct message ID that matches the ID
          // we generated when we added the message to the XMB (which is the same ID in the XTB).
          for (Node valueNodeChild : valueNode.children()) {
            icuPlaceholderNames.add(valueNodeChild.getString());
          }
          continue;
        }
        checkState(valueNode.isStringLit(), valueNode);
        String value = valueNode.getString();
        switch (key) {
          case "key" -> {
            jsMessageBuilder.setKey(value);
            msgKey = value;
          }
          case "meaning" -> {
            jsMessageBuilder.setMeaning(value);
            meaning = value;
          }
          case "alt_id" -> jsMessageBuilder.setAlternateId(value);
          case "msg_text" -> {
            // This may be an ICU template that also has the `icu_placeholder_names` property, which
            // means we need to append multiple parts of the message to `jsMessageBuilder`. For now,
            // we will save the message text and current node, and we'll parse it once we know if
            // this is an ICU template with multiple parts (after this loop to run through all the
            // properties is finished).
            messageText = value;
            messageTextNode = valueNode;
          }
          case "isIcuTemplate" -> isIcuTemplate = true;
          case "escapeLessThan" ->
              // Just being present enables this option
              escapeLessThanOption = true;
          case "unescapeHtmlEntities" ->
              // just being present enables this option
              unescapeHtmlEntitiesOption = true;
          default -> throw new IllegalStateException("unknown protected message key: " + strKey);
        }
      }

      try {
        if (!icuPlaceholderNames.isEmpty()) {
          checkState(
              isIcuTemplate,
              "Found icu_placeholder_names for a message that is not an ICU template.");
          // This is an ICU template with placeholders ("{$placeholderName}"). We cannot treat this
          // as a single string part, because it has multiple parts. Otherwise, we will generate the
          // wrong message id and we will not be able to find the correct translated message in the
          // XTB file (because when the XMB message was created during message extraction, we
          // treated this ICU template as having multiple parts).
          final IcuMessageTemplateString icuMessageTemplateString =
              new IcuMessageTemplateString(messageText);
          final ExtractedIcuTemplateParts extractedIcuTemplateParts =
              icuMessageTemplateString.extractParts(icuPlaceholderNames);

          // Append the parts of the ICU template to the jsMessageBuilder.
          jsMessageBuilder.appendParts(extractedIcuTemplateParts.extractedParts);
        } else {
          // This message is a single string part. It may be an ICU template without placeholders,
          // or it may be a normal `goog.getMsg()` message.
          jsMessageBuilder.appendParts(JsMessageVisitor.parseJsMessageTextIntoParts(messageText));
        }
      } catch (PlaceholderFormatException unused) {
        // Somehow we stored the protected message text incorrectly, which should never
        // happen 🙏
        throw new IllegalStateException(
            messageTextNode.getLocation()
                + ": Placeholder incorrectly formatted: >"
                + messageText
                + "<");
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
          /* jsMessage= */ jsMessageBuilder.build(),
          /* definitionNode= */ node,
          /* substitutionsNode= */ substitutionsNode,
          /* isIcuTemplate= */ isIcuTemplate,
          /* escapeLessThan= */ escapeLessThanOption,
          /* unescapeHtmlEntities= */ unescapeHtmlEntitiesOption);
    }

    MsgOptions getMsgOptions() {
      return new MsgOptions(
          /* isIcuTemplate= */ isIcuTemplate,
          /* escapeLessThan= */ escapeLessThan,
          /* unescapeHtmlEntities= */ unescapeHtmlEntities);
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
