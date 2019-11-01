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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.Ordering;
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
      newValue.useSourceInfoIfMissingFromForTree(msgNode);

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
      if (isHtml) {
        Node args = IR.arraylit(IR.string(message.getId()), placeholderValueArray);
        Node options = IR.arraylit(IR.objectlit(IR.stringKey("escapeLt", IR.trueNode())));
        Node regexp = IR.regexp(IR.string("Chrome\\/(\\d+)"));
        Node userAgent = NodeUtil.newQName(compiler, "navigator.userAgent");
        Node version =
            IR.getelem(
                IR.or(IR.call(IR.getprop(regexp, "exec"), userAgent), IR.arraylit()), IR.number(1));
        Node condition = IR.ge(version, IR.number(79));
        args = IR.call(IR.getprop(args, "concat"), IR.hook(condition, options, IR.arraylit()));
        newValueNode =
            IR.call(
                NodeUtil.newQName(compiler, "chrome.i18n.getMessage.apply"), IR.nullNode(), args);
      } else {
        newValueNode.addChildToBack(placeholderValueArray);
      }
    }

    newValueNode.useSourceInfoIfMissingFromForTree(origNode);
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
    for (Node key : placeholderValues.children()) {
      if (key.getString().equals(placeholderName)) {
        return key.getFirstChild().cloneTree();
      }
    }
    return null;
  }
}
