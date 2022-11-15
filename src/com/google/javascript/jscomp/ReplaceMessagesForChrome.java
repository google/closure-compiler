/*
 * Copyright 2012 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.List;

/**
 * Replaces user-visible messages with appropriate calls to chrome.i18n.getMessage. The first
 * argument to getMessage is the id of the message, as a string. If the message contains
 * placeholders, the second argument is an array of the values being used for the placeholders,
 * sorted by placeholder name.
 */
@GwtIncompatible("JsMessage")
class ReplaceMessagesForChrome extends JsMessageVisitor {

  static final DiagnosticType DECLARE_ICU_TEMPLATE_NOT_SUPPORTED =
      DiagnosticType.error(
          "JSC_DECLARE_ICU_TEMPLATE",
          "goog.i18n.messages.declareIcuTemplate() is not supported for Chrome i18n.");

  private final AstFactory astFactory;

  ReplaceMessagesForChrome(AbstractCompiler compiler, JsMessage.IdGenerator idGenerator) {
    super(compiler, idGenerator);
    this.astFactory = compiler.createAstFactory();
  }

  private Node getChromeI18nGetMessageNode(String messageId) {
    Node getMessage = astFactory.createQNameWithUnknownType("chrome.i18n.getMessage");
    return astFactory.createCall(
        getMessage, type(StandardColors.STRING), astFactory.createString(messageId));
  }

  @Override
  protected void processIcuTemplateDefinition(IcuTemplateDefinition definition) {
    // TODO(bradfordcsmith): Add support for this.
    compiler.report(JSError.make(definition.getMessageNode(), DECLARE_ICU_TEMPLATE_NOT_SUPPORTED));
  }

  @Override
  protected void processJsMessageDefinition(JsMessageDefinition definition) {
    Node newValue = getNewValueNode(definition.getMessage(), definition);
    definition.getMessageNode().replaceWith(newValue);
    compiler.reportChangeToEnclosingScope(newValue);
  }

  private Node getNewValueNode(JsMessage message, JsMessageDefinition definition) {
    Node newValueNode = getChromeI18nGetMessageNode(message.getId());

    boolean isHtml = definition.shouldEscapeLessThan();
    if (!message.jsPlaceholderNames().isEmpty() || isHtml) {
      // Output the placeholders, sorted alphabetically by placeholder name,
      // regardless of what order they appear in the original message.
      List<String> placeholderNames = Ordering.natural().sortedCopy(message.jsPlaceholderNames());

      Node placeholderValueArray = astFactory.createArraylit();
      ImmutableMap<String, Node> placeholderValueMap = definition.getPlaceholderValueMap();
      for (String name : placeholderNames) {
        // JsMessageVisitor ensures that every placeholder name appearing in the message string
        // has a corresponding value node. It will report an error and avoid passing any messages
        // violating this invariant to processMessage().
        Node value = checkNotNull(placeholderValueMap.get(name)).cloneTree();
        placeholderValueArray.addChildToBack(value);
      }
      if (isHtml) {
        Node args =
            astFactory.createArraylit(
                astFactory.createString(message.getId()), placeholderValueArray);
        Node options =
            astFactory.createArraylit(
                astFactory.createObjectLit(
                    astFactory.createStringKey("escapeLt", astFactory.createBoolean(true))));
        Node regexp =
            IR.regexp(astFactory.createString("Chrome\\/(\\d+)")).setColor(StandardColors.UNKNOWN);
        Node userAgent = astFactory.createQNameWithUnknownType("navigator.userAgent");
        Node version =
            astFactory.createGetElem(
                astFactory.createOr(
                    astFactory.createCall(
                        astFactory.createGetPropWithUnknownType(regexp, "exec"),
                        type(StandardColors.ARRAY_ID),
                        userAgent),
                    astFactory.createArraylit()),
                astFactory.createNumber(1));
        Node condition =
            IR.ge(version, astFactory.createNumber(79)).setColor(StandardColors.BOOLEAN);
        args =
            astFactory.createCall(
                astFactory.createGetPropWithUnknownType(args, "concat"),
                type(StandardColors.ARRAY_ID),
                astFactory.createHook(condition, options, astFactory.createArraylit()));
        newValueNode =
            astFactory.createCallWithUnknownType(
                astFactory.createQNameWithUnknownType("chrome.i18n.getMessage.apply"),
                astFactory.createNull(),
                args);
      } else {
        newValueNode.addChildToBack(placeholderValueArray);
      }
    }

    newValueNode.srcrefTreeIfMissing(definition.getMessageNode());
    return newValueNode;
  }
}
