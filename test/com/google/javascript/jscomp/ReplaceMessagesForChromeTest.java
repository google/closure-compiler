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

import static com.google.javascript.jscomp.JsMessage.Style.RELAX;

import com.google.javascript.jscomp.JsMessage.Style;

/**
 * Test which checks that replacer works correctly.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public final class ReplaceMessagesForChromeTest extends CompilerTestCase {

  private Style style = RELAX;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ReplaceMessagesForChrome(compiler,
        new GoogleJsMessageIdGenerator(null), false, style);
  }

  @Override
  protected int getNumRepetitions() {
    // No longer valid on the second run.
    return 1;
  }

  @Override
  protected void setUp()  {
    style = RELAX;
    compareJsDoc = false;
  }

  public void testReplaceSimpleMessage() {
    test("/** @desc A simple message. */\n" +
         "var MSG_A = goog.getMsg('Hello world');",
         "var MSG_A=chrome.i18n.getMessage('8660696502365331902');");

    test("/** @desc A message attached to an object. */\n" +
        "foo.bar.MSG_B = goog.getMsg('Goodbye world');",
        "foo.bar.MSG_B=chrome.i18n.getMessage('2356086230621084760');");
  }

  public void testReplaceSinglePlaceholder() {
    test("/** @desc A message with one placeholder. */\n" +
         "var MSG_C = goog.getMsg('Hello, {$name}', {name: 'Tyler'});",
         "var MSG_C=chrome.i18n.getMessage('4985325380591528435', ['Tyler']);");
  }

  public void testReplaceTwoPlaceholders() {
    test("/** @desc A message with two placeholders. */\n" +
         "var MSG_D = goog.getMsg('{$greeting}, {$name}', " +
         "{greeting: 'Hi', name: 'Tyler'});",
         "var MSG_D=chrome.i18n.getMessage('3605047247574980322', " +
         "['Hi', 'Tyler']);");

    test("/** @desc A message with two placeholders, but their order is\n" +
         " * reversed in the object literal. (Shouldn't make a difference.)\n" +
         " */\n" +
         "var MSG_E = goog.getMsg('{$greeting}, {$name}!', " +
         "{name: 'Tyler', greeting: 'Hi'});",
         "var MSG_E=chrome.i18n.getMessage('691522386483664339', " +
         "['Hi', 'Tyler']);");
  }

  public void testReplacePlaceholderMissingValue() {
    testError("/** @desc A message with two placeholders, but one is missing. */\n" +
         "var MSG_F = goog.getMsg('{$greeting}, {$name}!', {name: 'Tyler'});",
         JsMessageVisitor.MESSAGE_TREE_MALFORMED);
  }

  public void testReplaceTwoPlaceholdersNonAlphaOrder() {
    test("/** @desc A message with two placeholders not in order .*/\n" +
         "var MSG_G = goog.getMsg('{$name}: {$greeting}', " +
         "{greeting: 'Salutations', name: 'Tyler'});",
         "var MSG_G=chrome.i18n.getMessage('7437383242562773138', " +
         "['Salutations', 'Tyler']);");
  }

  public void testReplaceExternalMessage() {
    test("/** @desc A message that was extracted with SoyMsgExtractor. */\n" +
         "var MSG_EXTERNAL_1357902468 = goog.getMsg('Hello world');",
         "var MSG_EXTERNAL_1357902468 = chrome.i18n.getMessage('1357902468');");
  }

  /**
   * Test that messages are handled correctly if they contain the same
   * placeholder twice.
   */
  public void testReplaceMessageWithDuplicatePlaceholders() {
    String original = "" +
        "/** @desc A message that contains two instances of the same placeholder. */\n" +
        "var MSG_EXTERNAL_987654321 = goog.getMsg(" +
        "'{$startDiv_1}You are signed in as{$endDiv}{$img}{$startDiv_2}{$name}{$endDiv}'," +
        "{'startDiv_1': '<div>'," +
        "'endDiv': '</div>'," +
        "'img': '<img src=\"http://example.com/photo.png\">'," +
        "'startDiv_2': '<div class=\"name\">'," +
        "'name': name});";

    String compiled = "" +
        "var MSG_EXTERNAL_987654321 = chrome.i18n.getMessage('987654321', " +
        "[" +
        "'</div>', " +  // endDiv, only included once, even though it appears twice in the message.
        "'<img src=\"http://example.com/photo.png\">', " +  // img
        "name, " +  // name
        "'<div>', " +  // startDiv_1
        "'<div class=\"name\">'" +  // startDiv_2
        "]);";

    test(original, compiled);
  }
}
