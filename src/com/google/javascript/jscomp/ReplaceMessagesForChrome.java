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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.JsMessage.PlaceholderReference;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collections;
import java.util.List;


/**
 * Replaces user-visible messages with appropriate calls to
 * chrome.i18n.getMessage. The first argument to getMessage is the id of the
 * message, as a string. If the message contains placeholders, the second
 * argument is an array of the values being used for the placeholders, in the
 * order they appear in the source code.
 *
 */
class ReplaceMessagesForChrome extends JsMessageVisitor {

  ReplaceMessagesForChrome(AbstractCompiler compiler,
      JsMessage.IdGenerator idGenerator,
      boolean checkDuplicatedMessages, JsMessage.Style style) {

    super(compiler, checkDuplicatedMessages, style, idGenerator);
  }

  private static Node getChromeI18nGetMessageNode(String messageId) {
    Node chromeI18n = IR.getprop(IR.name("chrome"), IR.string("i18n"));
    Node getMessage =  IR.getprop(chromeI18n, IR.string("getMessage"));
    return IR.call(getMessage, IR.string(messageId));
  }

  @Override
  protected void processJsMessage(
      JsMessage message, JsMessageDefinition definition) {
    try {
      Node msgNode = definition.getMessageNode();
      Node newValue = getNewValueNode(msgNode, message);
      newValue.copyInformationFromForTree(msgNode);

      definition.getMessageParentNode().replaceChild(msgNode, newValue);
      compiler.reportCodeChange();
    } catch (MalformedException e) {
      compiler.report(JSError.make(message.getSourceName(), e.getNode(),
          MESSAGE_TREE_MALFORMED, e.getMessage()));
    }
  }

  private Node getNewValueNode(Node origNode, JsMessage message)
      throws MalformedException {
    Node newValueNode = getChromeI18nGetMessageNode(message.getId());

    if (!message.placeholders().isEmpty()) {
      Node placeholderValues = origNode.getLastChild();
      checkNode(placeholderValues, Token.OBJECTLIT);

      // Output the placeholders, sorted alphabetically by placeholder name,
      // regardless of what order they appear in the original message.
      List<String> placeholderNames = Lists.newArrayList();
      for (CharSequence cs : message.parts()) {
        if (cs instanceof PlaceholderReference) {
          String placeholderName = ((PlaceholderReference) cs).getName();
          placeholderNames.add(placeholderName);
        }
      }
      Collections.sort(placeholderNames);

      Node placeholderValueArray = IR.arraylit();
      for (String name : placeholderNames) {
        Node value = getPlaceholderValue(placeholderValues, name);
        if (value == null) {
          throw new MalformedException(
              "No value was provided for placeholder " + name,
              origNode);
        }
        placeholderValueArray.addChildToBack(value);
      }
      newValueNode.addChildToBack(placeholderValueArray);
    }

    newValueNode.copyInformationFromForTree(origNode);
    return newValueNode;
  }

  private Node getPlaceholderValue(
      Node placeholderValues, String placeholderName) {
    for (Node key : placeholderValues.children()) {
      if (key.getString().equals(placeholderName)) {
        return key.getFirstChild().cloneTree();
      }
    }
    return null;
  }
}
