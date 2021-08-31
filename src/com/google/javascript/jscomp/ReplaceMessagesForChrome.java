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

import static com.google.javascript.jscomp.AstFactory.type;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.Ordering;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.List;

/**
 * Replaces user-visible messages with appropriate calls to
 * chrome.i18n.getMessage. The first argument to getMessage is the id of the
 * message, as a string. If the message contains placeholders, the second
 * argument is an array of the values being used for the placeholders, sorted
 * by placeholder name.
 */
@GwtIncompatible("JsMessage")
class ReplaceMessagesForChrome extends JsMessageVisitor {

  private final AstFactory astFactory;

  ReplaceMessagesForChrome(
      AbstractCompiler compiler, JsMessage.IdGenerator idGenerator, JsMessage.Style style) {

    super(compiler, style, idGenerator);

    this.astFactory = compiler.createAstFactory();
  }

  private Node getChromeI18nGetMessageNode(String messageId) {
    Node getMessage = astFactory.createQNameWithUnknownType("chrome.i18n.getMessage");
    return astFactory.createCall(
        getMessage, type(StandardColors.STRING), astFactory.createString(messageId));
  }

  @Override
  protected void processJsMessage(
      JsMessage message, JsMessageDefinition definition) {
    try {
      Node msgNode = definition.getMessageNode();
      Node newValue = getNewValueNode(msgNode, message);
      newValue.srcrefTreeIfMissing(msgNode);

      msgNode.replaceWith(newValue);
      compiler.reportChangeToEnclosingScope(newValue);
    } catch (MalformedException e) {
      compiler.report(JSError.make(e.getNode(),
          MESSAGE_TREE_MALFORMED, e.getMessage()));
    }
  }

  private Node getNewValueNode(Node origNode, JsMessage message)
      throws MalformedException {
    Node newValueNode = getChromeI18nGetMessageNode(message.getId());

    boolean isHtml = isHtml(origNode);
    if (!message.placeholders().isEmpty() || isHtml) {
      Node placeholderValues = origNode.getChildAtIndex(2);
      checkNode(placeholderValues, Token.OBJECTLIT);

      // Output the placeholders, sorted alphabetically by placeholder name,
      // regardless of what order they appear in the original message.
      List<String> placeholderNames = Ordering.natural().sortedCopy(message.placeholders());

      Node placeholderValueArray = astFactory.createArraylit();
      for (String name : placeholderNames) {
        Node value = getPlaceholderValue(placeholderValues, name);
        if (value == null) {
          throw new MalformedException(
              "No value was provided for placeholder " + name,
              origNode);
        }
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

    newValueNode.srcrefTreeIfMissing(origNode);
    return newValueNode;
  }

  private boolean isHtml(Node node) throws MalformedException {
    if (node.getChildCount() > 3) {
      Node options = node.getChildAtIndex(3);
      checkNode(options, Token.OBJECTLIT);
      for (Node opt = options.getFirstChild(); opt != null; opt = opt.getNext()) {
        checkNode(opt, Token.STRING_KEY);
        if (opt.getString().equals("html")) {
          return opt.getFirstChild().isTrue();
        }
      }
    }
    return false;
  }

  private static Node getPlaceholderValue(
      Node placeholderValues, String placeholderName) {
    for (Node key = placeholderValues.getFirstChild(); key != null; key = key.getNext()) {
      if (key.getString().equals(placeholderName)) {
        return key.getFirstChild().cloneTree();
      }
    }
    return null;
  }
}
