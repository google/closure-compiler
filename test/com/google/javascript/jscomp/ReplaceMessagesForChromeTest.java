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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test which checks that replacer works correctly. */
@RunWith(JUnit4.class)
public final class ReplaceMessagesForChromeTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ReplaceMessagesForChrome(compiler, new GoogleJsMessageIdGenerator(null));
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    replaceTypesWithColors();
    enableTypeInfoValidation();
    ignoreWarnings(DiagnosticGroups.MISSING_PROPERTIES);
  }

  @Test
  public void testReplaceSimpleMessage() {
    test(
        """
        /** @desc A simple message. */

        var MSG_A = goog.getMsg('Hello world');
        """,
        """
        /** @desc A simple message. */

        var MSG_A=chrome.i18n.getMessage('8660696502365331902');
        """);

    test(
        """
        /** @desc A message attached to an object. */

        foo.bar.MSG_B = goog.getMsg('Goodbye world');
        """,
        """
        /** @desc A message attached to an object. */

        foo.bar.MSG_B=chrome.i18n.getMessage('2356086230621084760');
        """);
  }

  @Test
  public void testReplaceSinglePlaceholder() {
    test(
        """
        /** @desc A message with one placeholder. */

        var MSG_C = goog.getMsg('Hello, {$name}', {name: 'Tyler'});
        """,
        """
        /** @desc A message with one placeholder. */

        var MSG_C=chrome.i18n.getMessage('4985325380591528435', ['Tyler']);
        """);
  }

  @Test
  public void testReplaceTwoPlaceholders() {
    test(
        """
        /** @desc A message with two placeholders. */

        var MSG_D = goog.getMsg('{$greeting}, {$name}',
        {greeting: 'Hi', name: 'Tyler'});
        """,
        """
        /** @desc A message with two placeholders. */

        var MSG_D=chrome.i18n.getMessage('3605047247574980322',
        ['Hi', 'Tyler']);
        """);

    test(
        """
        /** @desc A message with two placeholders, but their order is

         * reversed in the object literal. (Shouldn't make a difference.)

         */

        var MSG_E = goog.getMsg('{$greeting}, {$name}!',
        {name: 'Tyler', greeting: 'Hi'});
        """,
        """
        /** @desc A message with two placeholders, but their order is

         * reversed in the object literal. (Shouldn't make a difference.)

         */

        var MSG_E=chrome.i18n.getMessage('691522386483664339',
        ['Hi', 'Tyler']);
        """);
  }

  @Test
  public void testReplacePlaceholderMissingValue() {
    testError(
        """
        /** @desc A message with two placeholders, but one is missing. */
        var MSG_F = goog.getMsg('{$greeting}, {$name}!', {name: 'Tyler'});
        """,
        JsMessageVisitor.MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testReplaceTwoPlaceholdersNonAlphaOrder() {
    test(
        """
        /** @desc A message with two placeholders not in order .*/

        var MSG_G = goog.getMsg('{$name}: {$greeting}',
        {greeting: 'Salutations', name: 'Tyler'});
        """,
        """
        /** @desc A message with two placeholders not in order .*/

        var MSG_G=chrome.i18n.getMessage('7437383242562773138',
        ['Salutations', 'Tyler']);
        """);
  }

  @Test
  public void testReplaceSinglePlaceholderComputedProp() {
    testError(
        """
        /** @desc A message with one placeholder. */

        var MSG_H = goog.getMsg('Hello, {$name}', {['name']: 'Tyler'});
        """,
        JsMessageVisitor.MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testReplaceSimpleMessageWithLet() {
    test(
        """
        /** @desc A simple message. */

        let MSG_I = goog.getMsg('Hello world');
        """,
        """
        /** @desc A simple message. */

        let MSG_I = chrome.i18n.getMessage('987871171253827787');
        """);
  }

  @Test
  public void testReplaceSimpleMessageWithConst() {
    test(
        """
        /** @desc A simple message. */

        const MSG_J = goog.getMsg('Hello world');
        """,
        """
        /** @desc A simple message. */

        const MSG_J =chrome.i18n.getMessage('3477894568604521782');
        """);
  }

  @Test
  public void testReplaceTemplatedMessage() {
    testError(
        """
        const greeting = '{$greeting}'
        /** @desc A simple message */

        var MSG_K = goog.getMsg(`${greeting}, Tyler`, {greeting: 'Hello'});
        """,
        JsMessageVisitor.MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testReplaceExternalMessage() {
    test(
        """
        /** @desc A message that was extracted with SoyMsgExtractor. */

        var MSG_EXTERNAL_1357902468 = goog.getMsg('Hello world');
        """,
        """
        /** @desc A message that was extracted with SoyMsgExtractor. */

        var MSG_EXTERNAL_1357902468 = chrome.i18n.getMessage('1357902468');
        """);
  }

  /** Test that messages are handled correctly if they contain the same placeholder twice. */
  @Test
  public void testReplaceMessageWithDuplicatePlaceholders() {
    test(
        """
        /** @desc A message that contains two instances of the same placeholder. */

        var MSG_EXTERNAL_987654321 = goog.getMsg(
        '{$startDiv_1}You are signed in as{$endDiv}{$img}{$startDiv_2}{$name}{$endDiv}',
        {'startDiv_1': '<div>',
        'endDiv': '</div>',
        'img': '<img src="http://example.com/photo.png">',
        'startDiv_2': '<div class="name">',
        'name': name});
        """,
        """
        /** @desc A message that contains two instances of the same placeholder. */

        var MSG_EXTERNAL_987654321 = chrome.i18n.getMessage('987654321',
        [
        '</div>',  // endDiv, only included once even though it appears twice in the message.
        '<img src="http://example.com/photo.png">',  // img
        name,  // name
        '<div>',  // startDiv_1
        '<div class="name">' // startDiv_2
        ]);
        """);
  }

  @Test
  public void testReplaceMessageWithHtml() {
    test(
        """
        /** @desc A message with one placeholder. */

        var MSG_C = goog.getMsg('Hello, {$name}', {name: 'Tyler'}, {html: true});
        """,
"""
/** @desc A message with one placeholder. */

var MSG_C=chrome.i18n.getMessage.apply(null, ['4985325380591528435', ['Tyler']].concat((/Chrome\\/(\\d+)/.exec(navigator.userAgent) || [])[1] >= 79 ? [{escapeLt: true}] : []));
""");

    test(
        """
        /** @desc A simple message. */

        var MSG_A = goog.getMsg('Hello world', {}, {html: true});
        """,
"""
/** @desc A simple message. */

var MSG_A=chrome.i18n.getMessage.apply(null, ['8660696502365331902', []].concat((/Chrome\\/(\\d+)/.exec(navigator.userAgent) || [])[1] >= 79 ? [{escapeLt: true}] : []));
""");

    test(
        """
        /** @desc A simple message. */

        var MSG_A = goog.getMsg('Hello world', {}, {html: false});
        """,
        """
        /** @desc A simple message. */

        var MSG_A=chrome.i18n.getMessage('8660696502365331902');
        """);

    test(
        """
        /** @desc A simple message. */

        var MSG_A = goog.getMsg('Hello world', {}, {});
        """,
        """
        /** @desc A simple message. */

        var MSG_A=chrome.i18n.getMessage('8660696502365331902');
        """);
  }
}
