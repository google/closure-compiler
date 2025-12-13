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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.JsMessageVisitor.MESSAGE_NOT_INITIALIZED_CORRECTLY;
import static com.google.javascript.jscomp.JsMessageVisitor.MESSAGE_TREE_MALFORMED;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.JsMessage.Part;
import com.google.javascript.jscomp.JsMessage.PlaceholderReference;
import com.google.javascript.jscomp.JsMessage.StringPart;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.SideEffectFlags;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test which checks that replacer works correctly. */
@RunWith(JUnit4.class)
public final class ReplaceMessagesTest extends CompilerTestCase {

  // Generate IDs of the form `MEANING_PARTCOUNT[PARTCOUNT...]`
  // PARTCOUNT = 'sN' for a string part with N == string length
  // PARTCOUNT = 'pN' for a placeholder with N == length of the canonical placeholder name
  public static final JsMessage.IdGenerator TEST_ID_GENERATOR =
      new JsMessage.IdGenerator() {
        @Override
        public String generateId(String meaning, List<Part> messageParts) {
          StringBuilder idBuilder = new StringBuilder();
          idBuilder.append(meaning).append('_');
          for (Part messagePart : messageParts) {
            if (messagePart.isPlaceholder()) {
              idBuilder.append('p').append(messagePart.getCanonicalPlaceholderName().length());
            } else {
              idBuilder.append('s').append(messagePart.getString().length());
            }
          }

          return idBuilder.toString();
        }
      };

  /** Indicates which part of the replacement we're currently testing */
  enum TestMode {
    // Test full replacement from `goog.getMsg()` to final message values.
    FULL_REPLACE,
    // Test replacement of `goog.getMsg()` with the protected function call form.
    // e.g.
    // ```javascript
    // var MSG_G =
    //     __jscomp_define_msg__(
    //         {
    //           "alt_id": null,
    //           "key":    "MSG_G",
    //           "msg_text": "${$amount}"
    //         },
    //         {amount:x + ""});`
    // ```
    PROTECT_MSGS,
    // Test replacement of the protected function call form with the final message values.
    REPLACE_PROTECTED_MSGS
  }

  // Messages returned from fake bundle, keyed by `JsMessage.id`.
  private Map<String, JsMessage> messages;
  // If `true` report errors for messages that are not found in the bundle.
  private boolean strictReplacement;
  // If `true` pass TEST_ID_GENERATOR in to ReplaceMessages via the fake bundle, so it will be
  // used to calculate the message IDs from the meaning and parts instead of just using the message
  // key as its id.
  private boolean useTestIdGenerator;
  private TestMode testMode = TestMode.FULL_REPLACE;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    final ReplaceMessages replaceMessages =
        new ReplaceMessages(compiler, new SimpleMessageBundle(), strictReplacement);
    return switch (testMode) {
      case FULL_REPLACE -> replaceMessages.getFullReplacementPass();
      case PROTECT_MSGS -> replaceMessages.getMsgProtectionPass();
      case REPLACE_PROTECTED_MSGS -> replaceMessages.getReplacementCompletionPass();
    };
  }

  /**
   * The primary test method to use in this file.
   *
   * @param originalJs The original, input JS code
   * @param protectedJs What the code should look like after message definitions are is protected
   *     from mangling during optimizations, but before they are replaced with the localized message
   *     data.
   * @param expectedJs What the code should look like after full replacement with localized messages
   *     has been done.
   */
  private void multiPhaseTest(String originalJs, String protectedJs, String expectedJs) {
    // The PROTECT_MSGS mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.FULL_REPLACE;
    test(originalJs, expectedJs);
    testMode = TestMode.PROTECT_MSGS;
    test(originalJs, protectedJs);
    testMode = TestMode.REPLACE_PROTECTED_MSGS;
    test(protectedJs, expectedJs);
  }

  /**
   * Test for warnings that apply to both the full replace and the initial protection of messages.
   *
   * @param originalJs The original, input JS code
   * @param protectedJs What the code should look like after message definitions are is protected
   *     from mangling during optimizations, but before they are replaced with the localized message
   *     data.
   * @param expectedJs What the code should look like after full replacement with localized messages
   * @param diagnosticType expected warning
   */
  private void multiPhaseTestWarning(
      String originalJs, String protectedJs, String expectedJs, DiagnosticType diagnosticType) {
    // The PROTECT_MSGS mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.FULL_REPLACE;
    testWarning(originalJs, diagnosticType);
    testMode = TestMode.PROTECT_MSGS;
    testWarning(originalJs, diagnosticType);
    testMode = TestMode.REPLACE_PROTECTED_MSGS;
    test(protectedJs, expectedJs);
  }

  /**
   * Test for errors that are detected before attempting to look up the messages in the bundle.
   *
   * @param originalJs The original, input JS code
   * @param diagnosticType expected error
   */
  private void multiPhaseTestPreLookupError(String originalJs, DiagnosticType diagnosticType) {
    // The PROTECT_MSGS mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.FULL_REPLACE;
    testError(originalJs, diagnosticType);
    testMode = TestMode.PROTECT_MSGS;
    testError(originalJs, diagnosticType);
  }

  /**
   * Test for errors that are detected after attempting to look up the messages in the bundle.
   *
   * @param originalJs The original, input JS code
   * @param protectedJs What the code should look like after message definitions are is protected
   *     from mangling during optimizations, but before they are replaced with the localized message
   *     data.
   * @param diagnosticType expected error
   */
  private void multiPhaseTestPostLookupError(
      String originalJs, String protectedJs, DiagnosticType diagnosticType) {
    // The PROTECT_MSGS mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.FULL_REPLACE;
    testError(originalJs, diagnosticType);
    testMode = TestMode.PROTECT_MSGS;
    test(originalJs, protectedJs);
    testMode = TestMode.REPLACE_PROTECTED_MSGS;
    testError(protectedJs, diagnosticType);
  }

  /**
   * Test for errors that are detected before attempting to look up the messages in the bundle.
   *
   * @param originalJs The original, input JS code
   * @param protectedJs What the code should look like after message definitions are is protected
   *     from mangling during optimizations, but before they are replaced with the localized message
   *     data.
   * @param diagnosticType expected error
   * @param description text expected to be in the error message
   */
  private void multiPhaseTestPostLookupError(
      String originalJs, String protectedJs, DiagnosticType diagnosticType, String description) {
    // The PROTECT_MSGS mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.FULL_REPLACE;
    testError(originalJs, diagnosticType, description);
    testMode = TestMode.PROTECT_MSGS;
    test(originalJs, protectedJs);
    testMode = TestMode.REPLACE_PROTECTED_MSGS;
    testError(protectedJs, diagnosticType, description);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    messages = new HashMap<>();
    strictReplacement = false;
    useTestIdGenerator = false;
    enableTypeCheck();
    replaceTypesWithColors();
    enableTypeInfoValidation();
    ignoreWarnings(DiagnosticGroups.MISSING_PROPERTIES);
  }

  @Test
  public void testProtectedMessagesAndFallbackAreRemovable() {
    // Externs for the protection functions will be added.
    allowExternsChanges();
    testMode = TestMode.PROTECT_MSGS;
    test(
        """
        /** @desc d */
        var MSG_A = goog.getMsg('asdf');
        /** @desc d */
        var MSG_B = goog.getMsg('qwerty');
        var x = goog.getMsgWithFallback(MSG_A, MSG_B);
        """,
        """
        /** @desc d */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":    "MSG_A",
                  "msg_text":"asdf",
                });
        /** @desc d */
        var MSG_B =
            __jscomp_define_msg__(
                {
                  "key":    "MSG_B",
                  "msg_text":"qwerty",
              });
        var x = __jscomp_msg_fallback__("MSG_A", MSG_A, "MSG_B", MSG_B);
        """);
    // It's important that all of the protective function calls be marked as having no side effects,
    // so they will be removed during optimizations if they are unused.
    final Node jsRoot = getLastCompiler().getJsRoot();
    final Node script = jsRoot.getFirstChild();
    final Node msgAVarNode = script.getFirstChild();

    final Node msgANameNode = msgAVarNode.getOnlyChild();
    assertNode(msgANameNode).isName("MSG_A");
    final Node msgACallNode = msgANameNode.getOnlyChild();
    assertThat(msgACallNode.getSideEffectFlags()).isEqualTo(SideEffectFlags.NO_SIDE_EFFECTS);

    final Node msgBVarNode = msgAVarNode.getNext();
    final Node msgBNameNode = msgBVarNode.getOnlyChild();
    assertNode(msgBNameNode).isName("MSG_B");
    final Node msgBCallNode = msgANameNode.getOnlyChild();
    assertThat(msgBCallNode.getSideEffectFlags()).isEqualTo(SideEffectFlags.NO_SIDE_EFFECTS);

    final Node xVarNode = msgBVarNode.getNext();
    final Node xNameNode = xVarNode.getOnlyChild();
    assertNode(xNameNode).isName("x");
    final Node xCallNode = xNameNode.getOnlyChild();
    assertThat(xCallNode.getSideEffectFlags()).isEqualTo(SideEffectFlags.NO_SIDE_EFFECTS);
  }

  @Test
  public void testReplaceSimpleMessage() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("Hi\nthere").build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_A = goog.getMsg('asdf');
        """,
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":    "MSG_A",
                  "msg_text":"asdf",
                });
        """,
        """
        /** @desc d */
        var MSG_A='Hi\\nthere'
        """);
  }

  @Test
  public void testReplaceExternalMessage() {
    registerMessage(getTestMessageBuilder("12345").appendStringPart("Saluton!").build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_EXTERNAL_12345 = goog.getMsg('Hello!');
        """,
        """
        /**
         * @desc d
         */
        var MSG_EXTERNAL_12345 =
            __jscomp_define_msg__(
                {
                  "key":    "MSG_EXTERNAL_12345",
                  "msg_text":"Hello!",
                });
        """,
        """
        /** @desc d */
        var MSG_EXTERNAL_12345='Saluton!'
        """);
  }

  @Test
  public void testReplaceIcuTemplateMessageWithBundleAndJsPlaceholders() {
    // This unit test contains an ICU template with placeholders ("{EMAIL}"). We cannot treat this
    // message as a single string part, because it has multiple parts. Otherwise, we will generate
    // the wrong message id and this unit test will fail.
    useTestIdGenerator = true;
    strictReplacement = true;

    String meaning = "MSG_SHOW_EMAIL";
    Part originalStringPart = StringPart.create("Email: ");
    Part originalPlaceholerPart = PlaceholderReference.createForCanonicalName("EMAIL");
    String expectedMessageId =
        TEST_ID_GENERATOR.generateId(
            meaning, ImmutableList.of(originalStringPart, originalPlaceholerPart));

    // Create and register the translation we expect to find in the message bundle
    final JsMessage showEmailTranslatedMsg =
        new JsMessage.Builder()
            .setKey(meaning)
            .setMeaning(meaning)
            .appendStringPart("Retpoŝtadreso: ") // translated string
            .appendPart(originalPlaceholerPart) // placeholder is the same as original
            .setId(expectedMessageId) // message ID was calculated from the original
            .build();
    registerMessage(showEmailTranslatedMsg);

    multiPhaseTest(
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        const MSG_SHOW_EMAIL =
            declareIcuTemplate(
                'Email: {EMAIL}',
                {
                  description: 'Labeled email address',
        // The example text is dropped, since it is only used for XMB extraction.
        // However, it does cause the JsMessage read from the JS code to have a placeholder
        // in it.
                  example: {
                    'EMAIL': 'me@foo.com'
                   }
                });
        """,
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        const MSG_SHOW_EMAIL =
            __jscomp_define_msg__(
                {
                  "key":    "MSG_SHOW_EMAIL",
                  "icu_placeholder_names": ["EMAIL"],
                  "msg_text": "Email: {EMAIL}",
                  "isIcuTemplate": ""
                });
        """,
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        const MSG_SHOW_EMAIL = 'Retpo\u015dtadreso: {EMAIL}';
        """);
  }

  @Test
  public void testReplaceIcuTemplateMessageWithoutJsPlaceholders() {
    // Message in the bundle has a placeholder and is NOT in ICU selector format.
    //
    // (i.e. it does not start with "{WORD,").
    //
    // Here we want to make sure that messages created with declareIcuTemplate()
    // get treated as ICU messages even without that distinguishing feature.
    registerMessage(
        getTestMessageBuilder("MSG_SHOW_EMAIL")
            .appendStringPart("Retpoŝtadreso: ")
            .appendCanonicalPlaceholderReference("EMAIL")
            .build());

    multiPhaseTest(
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        // Note that no placeholder information is specified here, so the JsMessage as it is
        // read from the JS code will have no placeholders.
        // In this test case we've put a placeholder in the bundle above, but if the bundle
        // were created based on this code, it would not have a placeholder.
        // This situation could occur with an externally-produced message bundle.
        const MSG_SHOW_EMAIL = declareIcuTemplate(
            'Email: {EMAIL}', { description: 'Labeled email address' });
        """,
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        const MSG_SHOW_EMAIL =
            __jscomp_define_msg__(
                {
                  "key":    "MSG_SHOW_EMAIL",
                  "msg_text": "Email: {EMAIL}",
                  "isIcuTemplate": ""
                });
        """,
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        const MSG_SHOW_EMAIL = 'Retpo\u015dtadreso: {EMAIL}';
        """);
  }

  @Test
  public void testReplaceIcuTemplateMessageWithJsPlaceholders() {
    // Make sure ICU messages with multiple parts are handled correctly.
    // We cannot treat this ICU message as a single string part, because it has two placeholders
    // (EMAIL1 and EMAIL2) that cause the message to be split into multiple parts.
    registerMessage(
        getTestMessageBuilder("MSG_SHOW_EMAIL")
            .appendStringPart("Retpoŝtadreso: ")
            .appendCanonicalPlaceholderReference("EMAIL_1")
            .appendStringPart(" aŭ ")
            .appendCanonicalPlaceholderReference("EMAIL_2")
            .build());

    multiPhaseTest(
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        const MSG_SHOW_EMAIL =
            declareIcuTemplate(
                'Email Options: {EMAIL_1} or {EMAIL_2}',
                {
                  description: 'Labeled email address',
                  example: {
                    'EMAIL_1': 'me1@foo.com',
                    'EMAIL_2': 'me2@foo.com'
                   }
                });
        """,
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        const MSG_SHOW_EMAIL =
            __jscomp_define_msg__(
                {
                  "key":    "MSG_SHOW_EMAIL",
                  "icu_placeholder_names": ["EMAIL_1", "EMAIL_2"],
                  "msg_text": "Email Options: {EMAIL_1} or {EMAIL_2}",
                  "isIcuTemplate": ""
                });
        """,
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        const MSG_SHOW_EMAIL = 'Retpo\u015dtadreso: {EMAIL_1} a\u016d {EMAIL_2}';
        """);
  }

  @Test
  public void testMissingIcuTemplateMessage() {
    // We don't registerMessage() here, so there are no messages in the bundle used by this test.

    multiPhaseTest(
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        const MSG_SHOW_EMAIL =
            declareIcuTemplate(
                'Email: {EMAIL}',
                {
                  description: 'Labeled email address',
        // The example text is dropped, since it is only used for XMB extraction.
        // However, it does cause the JsMessage read from the JS code to have a placeholder
        // in it.
        // We add this placeholder in the "icu_placeholder_names" field to keep track of how the
        // message has multiple parts, which is necessary for the message ID to be generated
        // correctly.
        // The purpose of this test is to:
        // 1. make sure the message template is properly put back together.
        // 2. make sure the "icu_placeholder_names" field is populated with `EMAIL`
                  example: {
                    'EMAIL': 'me@foo.com'
                   }
                });
        """,
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        const MSG_SHOW_EMAIL =
            __jscomp_define_msg__(
                {
                  "key":    "MSG_SHOW_EMAIL",
                  "icu_placeholder_names": ["EMAIL"],
                  "msg_text": "Email: {EMAIL}",
                  "isIcuTemplate": ""
                });
        """,
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        const MSG_SHOW_EMAIL = 'Email: {EMAIL}';
        """);
  }

  @Test
  public void testReplaceExternalIcuSelectorMessageWithPlaceholders() {
    // Message in the bundle is in ICU selector format with has placeholders with explicit
    // placeholders.
    // The JS code treats the message as a simple string without placeholders.
    // The compiler should join the placeholder names together with the string parts in order to
    // get the runtime string value.
    registerMessage(
        getTestMessageBuilder("123456")
            .appendStringPart("{USER_GENDER,select,female{Saluton ")
            .appendCanonicalPlaceholderReference("USER_IDENTIFIER")
            .appendStringPart(".}male{Saluton ")
            .appendCanonicalPlaceholderReference("USER_IDENTIFIER")
            .appendStringPart(".}other{Saluton ")
            .appendCanonicalPlaceholderReference("USER_IDENTIFIER")
            .appendStringPart(".}}")
            .build());

    multiPhaseTest(
        """
        /** @desc ICU gender-sensitive greeting */
        // Message in the JS code does not define placeholders for the compiler.
        const MSG_EXTERNAL_123456 = goog.getMsg(
            '{USER_GENDER,select,' +
            'female{Hello {USER_IDENTIFIER}.}' +
            'male{Hello {USER_IDENTIFIER}.}' +
            'other{Hello {USER_IDENTIFIER}.}}');
        """,
"""
/** @desc ICU gender-sensitive greeting */
const MSG_EXTERNAL_123456 =
    __jscomp_define_msg__(
        {
          "key":    "MSG_EXTERNAL_123456",
          "msg_text":
    '{USER_GENDER,select,female{Hello {USER_IDENTIFIER}.}male{Hello {USER_IDENTIFIER}.}other{Hello {USER_IDENTIFIER}.}}',
        });
""",
"""
/** @desc ICU gender-sensitive greeting */
const MSG_EXTERNAL_123456 =
    '{USER_GENDER,select,female{Saluton {USER_IDENTIFIER}.}male{Saluton {USER_IDENTIFIER}.}other{Saluton {USER_IDENTIFIER}.}}';
""");
  }

  @Test
  public void testReplaceSimpleMessageDefinedWithAdd() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("Hi\nthere").build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_A = goog.getMsg('abcd' + 'efgh');
        """,
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"abcdefgh",
                });
        """,
        """
        /** @desc d */
        var MSG_A='Hi\\nthere'
        """);
  }

  @Test
  public void testMissingAlternateMessage() {
    multiPhaseTest(
        """
        /**
         * @desc d
         * @alternateMessageId 1984
         */
        var MSG_A = goog.getMsg('asdf');
        """,
        """
        /**
         * @desc d
         * @alternateMessageId 1984
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "alt_id":"1984",
                  "msg_text":"asdf",
                });
        """,
        """
        /**
         * @desc d
         * @alternateMessageId 1984
         */
        var MSG_A='asdf'
        """);
  }

  @Test
  public void testAlternateMessageWithMismatchedParts() {
    registerMessage(
        getTestMessageBuilder("1984")
            .setDesc("B desc")
            .setMeaning("B meaning")
            .appendStringPart("Hello!")
            .appendStringPart(" Welcome!")
            .build());

    multiPhaseTest(
        """
        /**
         * @desc d
         * @alternateMessageId 1984
         */
        var MSG_A = goog.getMsg('asdf');
        """,
        """
        /**
         * @desc d
         * @alternateMessageId 1984
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "alt_id":"1984",
                  "msg_text":"asdf",
                });
        """,
        """
        /**
         * @desc d
         * @alternateMessageId 1984
         */
        var MSG_A = 'Hello! Welcome!';
        """);
  }

  @Test
  public void testAlternateIcuSelectorMessageWithPlaceholders() {
    // Message in the bundle is in ICU selector format with has placeholders with explicit
    // placeholders.
    // The JS code treats the message as a simple string without placeholders.
    // The compiler should join the placeholder names together with the string parts in order to
    // get the runtime string value.
    // Note that we are not putting a translation for the actual message from the JS Code into the
    // bundle here. Instead, we are providing the alternate message.
    registerMessage(
        getTestMessageBuilder("1984")
            .appendStringPart("{USER_GENDER,select,female{Saluton ")
            .appendCanonicalPlaceholderReference("USER_IDENTIFIER")
            .appendStringPart(".}male{Saluton ")
            .appendCanonicalPlaceholderReference("USER_IDENTIFIER")
            .appendStringPart(".}other{Saluton ")
            .appendCanonicalPlaceholderReference("USER_IDENTIFIER")
            .appendStringPart(".}}")
            .build());

    multiPhaseTest(
        """
        /**
         * @desc ICU gender-sensitive greeting
         * @alternateMessageId 1984
         */
        // Message in the JS code does not define placeholders for the compiler.
        const MSG_ICU_SELECT = goog.getMsg(
            '{USER_GENDER,select,' +
            'female{Hello {USER_IDENTIFIER}.}' +
            'male{Hello {USER_IDENTIFIER}.}' +
            'other{Hello {USER_IDENTIFIER}.}}');
        """,
"""
/**
 * @desc ICU gender-sensitive greeting
 * @alternateMessageId 1984
 */
const MSG_ICU_SELECT =
    __jscomp_define_msg__(
        {
          "key":    "MSG_ICU_SELECT",
          "alt_id": "1984",
          "msg_text":
    '{USER_GENDER,select,female{Hello {USER_IDENTIFIER}.}male{Hello {USER_IDENTIFIER}.}other{Hello {USER_IDENTIFIER}.}}',
        });
""",
"""
/**
 * @desc ICU gender-sensitive greeting
 * @alternateMessageId 1984
 */
const MSG_ICU_SELECT =
    '{USER_GENDER,select,female{Saluton {USER_IDENTIFIER}.}male{Saluton {USER_IDENTIFIER}.}other{Saluton {USER_IDENTIFIER}.}}';
""");
  }

  /**
   * Returns a message builder that will use the same string as both the key and ID of the message.
   */
  private JsMessage.Builder getTestMessageBuilder(String keyAndId) {
    return new JsMessage.Builder().setKey(keyAndId).setId(keyAndId);
  }

  @Test
  public void testAlternateMessageWithMismatchedPlaceholders() {
    registerMessage(
        getTestMessageBuilder("1984")
            .setDesc("B desc")
            .setMeaning("B meaning")
            .appendStringPart("Hello, ")
            .appendJsPlaceholderReference("firstName")
            .appendStringPart("!")
            .build());

    multiPhaseTestPostLookupError(
        """
        /**
         * @desc B desc
         * @meaning B meaning
         * @alternateMessageId 1984
         */
        var MSG_A = goog.getMsg('Hello, {$name}!', {name: name});
        """,
        """
        /**
         * @desc B desc
         * @meaning B meaning
         * @alternateMessageId 1984
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":    "MSG_A",
                  "alt_id": "1984",
                  "meaning":"B meaning",
                  "msg_text":"Hello, {$name}!"
                },
                {'name': name});
        """,
        ReplaceMessages.INVALID_ALTERNATE_MESSAGE_PLACEHOLDERS);
  }

  @Test
  public void testReplaceValidAlternateMessage() {
    registerMessage(getTestMessageBuilder("1984").appendStringPart("Howdy\npardner").build());

    multiPhaseTest(
        """
        /**
         * @desc B desc
         * @alternateMessageId 1984
         */
        var MSG_A = goog.getMsg('asdf');
        """,
        """
        /**
         * @desc B desc
         * @alternateMessageId 1984
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "alt_id":"1984",
                  "msg_text":"asdf",
                });
        """,
        """
        /**
         * @desc B desc
         * @alternateMessageId 1984
         */
        var MSG_A='Howdy\\npardner'
        """);
  }

  @Test
  public void testIgnoreUnnecessaryAlternateMessage() {
    registerMessage(getTestMessageBuilder("1984").appendStringPart("Howdy\npardner").build());
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .setDesc("Greeting.")
            .setAlternateId("1984")
            .appendStringPart("Hi\nthere")
            .build());

    multiPhaseTest(
        """
        /**
         * @desc d
         * @alternateMessageId 1984
         */
        var MSG_A = goog.getMsg('asdf');
        """,
        """
        /**
         * @desc d
         * @alternateMessageId 1984
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "alt_id":"1984",
                  "msg_text":"asdf",
                });
        """,
        """
        /**
         * @desc d
         * @alternateMessageId 1984
         */
        var MSG_A = 'Hi\\nthere';
        """);
  }

  @Test
  public void testAlternateTrumpsFallback() {
    registerMessage(getTestMessageBuilder("1984").appendStringPart("Howdy\npardner").build());

    registerMessage(getTestMessageBuilder("MSG_B").appendStringPart("Good\nmorrow, sir").build());

    multiPhaseTest(
        """
        /**
         * @desc d
         * @alternateMessageId 1984
        */
        var MSG_A = goog.getMsg('asdf');
        /**
         * @desc d
        */
        var MSG_B = goog.getMsg('ghjk');
        var x = goog.getMsgWithFallback(MSG_A, MSG_B);
        """,
        """
        /**
         * @desc d
         * @alternateMessageId 1984
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "alt_id":"1984",
                  "msg_text":"asdf",
                });
        /**
         * @desc d
         */
        var MSG_B =
            __jscomp_define_msg__(
                {
                  "key":"MSG_B",
                  "msg_text":"ghjk",
                });
        var x = __jscomp_msg_fallback__("MSG_A", MSG_A, "MSG_B", MSG_B);
        """,
        """
        /**
            @desc d
            @alternateMessageId 1984
        */
        var MSG_A = 'Howdy\\npardner';
        /**
            @desc d
        */
        var MSG_B = 'Good\\nmorrow, sir';
        var x = MSG_A;
        """);
  }

  @Test
  public void testFallbackWithAlternate() {
    registerMessage(getTestMessageBuilder("1984").appendStringPart("Howdy\npardner").build());

    multiPhaseTest(
        """
        /**
            @desc d
        */
        var MSG_A = goog.getMsg('asdf');
        /**
            @desc d
            @alternateMessageId 1984
        */
        var MSG_B = goog.getMsg('ghjk');
        var x = goog.getMsgWithFallback(MSG_A, MSG_B);
        """,
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"asdf",
                });
        /**
         * @desc d
            @alternateMessageId 1984     */
        var MSG_B =
            __jscomp_define_msg__(
                {
                  "key":"MSG_B",
                  "alt_id":"1984",
                  "msg_text":"ghjk",
                });
        var x = __jscomp_msg_fallback__("MSG_A", MSG_A, "MSG_B", MSG_B);
        """,
        """
        /**
            @desc d
        */
        var MSG_A = 'asdf';
        /**
            @desc d
            @alternateMessageId 1984
        */
        var MSG_B = 'Howdy\\npardner';
        var x = MSG_B;
        """);
  }

  @Test
  public void testNameReplacement() {
    registerMessage(
        getTestMessageBuilder("MSG_B")
            .appendStringPart("One ")
            .appendJsPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_B=goog.getMsg('asdf {$measly}', {measly: x});
        """,
        """
        /**
         * @desc d
         */
        var MSG_B =
            __jscomp_define_msg__(
                {
                  "key":"MSG_B",
                  "msg_text":"asdf {$measly}",
                }, {'measly': x});
        """,
        """
        /** @desc d */
        var MSG_B = 'One ' + x + ' ph';
        """);
  }

  @Test
  public void testNameReplacementWithFullOptionsBag() {
    registerMessage(
        getTestMessageBuilder("MSG_B")
            .appendStringPart("One ")
            .appendJsPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_B =
            goog.getMsg(
                'asdf {$measly}',
                {measly: x},
                {
        // use all allowed options
                  html: true,
                  unescapeHtmlEntities: true,
        // original_code and example get dropped, because they're only used
        // when generating the XMB file.
                  original_code: {
                    'measly': 'getMeasley()'
                  },
                  example: {
                    'measly': 'very little'
                  },
                });
        """,
        """
        /**
         * @desc d
         */
        var MSG_B =
            __jscomp_define_msg__(
                {
                  "key":"MSG_B",
                  "msg_text":"asdf {$measly}",
                  "escapeLessThan":"",
                  "unescapeHtmlEntities":""
                },
                {'measly': x});
        """,
        """
        /** @desc d */
        var MSG_B = 'One ' + x + ' ph';
        """);
  }

  @Test
  public void testGetPropReplacement() {
    registerMessage(getTestMessageBuilder("MSG_C").appendJsPlaceholderReference("amount").build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_C = goog.getMsg('${$amount}', {amount: a.b.amount});
        """,
        """
        /**
         * @desc d
         */
        var MSG_C =
            __jscomp_define_msg__(
                {
                  "key":"MSG_C",
                  "msg_text":"${$amount}",
                }, {'amount': a.b.amount});
        """,
        """
        /** @desc d */
        var MSG_C=a.b.amount
        """);
  }

  @Test
  public void testFunctionCallReplacement() {
    registerMessage(getTestMessageBuilder("MSG_D").appendJsPlaceholderReference("amount").build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_D = goog.getMsg('${$amount}', {amount: getAmt()});
        """,
        """
        /**
         * @desc d
         */
        var MSG_D =
            __jscomp_define_msg__(
                {
                  "key":"MSG_D",
                  "msg_text":"${$amount}",
                }, {'amount': getAmt()});
        """,
        """
        /** @desc d */
        var MSG_D=getAmt()
        """);
  }

  @Test
  public void testMethodCallReplacement() {
    registerMessage(getTestMessageBuilder("MSG_E").appendJsPlaceholderReference("amount").build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_E = goog.getMsg('${$amount}', {amount: obj.getAmt()});
        """,
        """
        /**
         * @desc d
         */
        var MSG_E =
            __jscomp_define_msg__(
                {
                  "key":"MSG_E",
                  "msg_text":"${$amount}",
                }, {'amount': obj.getAmt()});
        """,
        """
        /** @desc d */
        var MSG_E=obj.getAmt()
        """);
  }

  @Test
  public void testMethodCallReplacementEmptyMessage() {
    registerMessage(getTestMessageBuilder("MSG_M").build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_M = goog.getMsg('${$amount}', {amount: obj.getAmt()});
        """,
        """
        /**
         * @desc d
         */
        var MSG_M =
            __jscomp_define_msg__(
                {
                  "key":"MSG_M",
                  "msg_text":"${$amount}",
                }, {'amount': obj.getAmt()});
        """,
        "/** @desc d */\n var MSG_M=''");
  }

  @Test
  public void testHookReplacement() {
    registerMessage(
        getTestMessageBuilder("MSG_F")
            .appendStringPart("#")
            .appendJsPlaceholderReference("amount")
            .appendStringPart(".")
            .build());

    multiPhaseTest(
        "/** @desc d */\n var MSG_F = goog.getMsg('${$amount}', {amount: (a ? b : c)});",
        """
        /**
         * @desc d
         */
        var MSG_F =
            __jscomp_define_msg__(
                {
                  "key":"MSG_F",
                  "msg_text":"${$amount}",
                }, {'amount': a ? b : c});
        """,
        """
        /** @desc d */
        var MSG_F = '#' + (a?b:c) + '.';
        """);
  }

  @Test
  public void testAddReplacement() {
    registerMessage(getTestMessageBuilder("MSG_G").appendJsPlaceholderReference("amount").build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_G = goog.getMsg('${$amount}', {amount: x + ''});
        """,
        """
        /** @desc d */
        var MSG_G =
            __jscomp_define_msg__(
                {
                  'key':'MSG_G',
                  "msg_text":"${$amount}",
                },
                {'amount': x + ''});
        """,
        """
        /** @desc d */
        var MSG_G=x+''
        """);
  }

  @Test
  public void testPlaceholderValueReferencedTwice() {
    registerMessage(
        getTestMessageBuilder("MSG_H")
            .appendJsPlaceholderReference("dick")
            .appendStringPart(", ")
            .appendJsPlaceholderReference("dick")
            .appendStringPart(" and ")
            .appendJsPlaceholderReference("jane")
            .build());

    multiPhaseTest(
        "/** @desc d */\n var MSG_H = goog.getMsg('{$dick}{$jane}', {jane: x, dick: y});",
        """
        /**
         * @desc d
         */
        var MSG_H =
            __jscomp_define_msg__(
                {
                  "key":"MSG_H",
                  "msg_text":"{$dick}{$jane}",
                }, {'jane': x, 'dick': y});
        """,
        """
        /** @desc d */
        var MSG_H = y + ', ' + y + ' and ' + x;
        """);
  }

  @Test
  public void testInvalidMessageStringType() {
    multiPhaseTestPreLookupError(
        """
        /** @desc d */
        const MSG_H = goog.getMsg(10);
        """,
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testPlaceholderValueDefinedTwice() {
    multiPhaseTestPreLookupError(
        """
        /** @desc d */
        const MSG_H = goog.getMsg(
            '{$dick}{$jane}',
            {jane: x, dick: y, jane: x});
        """,
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testInvalidPlaceholderArgument() {
    multiPhaseTestPreLookupError(
        """
        /** @desc d */
        const MSG_H = goog.getMsg(
            '{$dick}{$jane}',
            'this should be an object literal');
        """,
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testInvalidOptionsArgumentType() {
    multiPhaseTestPreLookupError(
        """
        /** @desc d */
        const MSG_H = goog.getMsg(
            '{$dick}{$jane}',
            {jane: x, dick: y},
            'should be an object literal');
        """,
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testComputedKeyInOptions() {
    multiPhaseTestPreLookupError(
        """
        /** @desc d */
        const MSG_H = goog.getMsg(
            '{$dick}{$jane}',
            {jane: x, dick: y},
            {[computedOpt]: true});
        """,
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testPlaceholderNameInLowerCamelCase() {
    registerMessage(
        getTestMessageBuilder("MSG_I")
            .appendStringPart("Sum: $")
            .appendJsPlaceholderReference("amtEarned")
            .build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_I = goog.getMsg('${$amtEarned}', {amtEarned: x});
        """,
        """
        /**
         * @desc d
         */
        var MSG_I =
            __jscomp_define_msg__(
                {
                  "key":"MSG_I",
                  "msg_text":"${$amtEarned}",
                }, {'amtEarned': x});
        """,
        """
        /** @desc d */
        var MSG_I='Sum: $'+x
        """);
  }

  @Test
  public void testQualifiedMessageName() {
    registerMessage(
        getTestMessageBuilder("MSG_J")
            .appendStringPart("One ")
            .appendJsPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    multiPhaseTest(
        """
        /** @desc d */
        a.b.c.MSG_J = goog.getMsg('asdf {$measly}', {measly: x});
        """,
        """
        /**
         * @desc d
         */
            a.b.c.MSG_J =
            __jscomp_define_msg__(
                {
                  "key":   'MSG_J',
                  "msg_text":"asdf {$measly}",
                },
                {'measly': x});
        """,
        """
        /** @desc d */
        a.b.c.MSG_J = 'One ' + x + ' ph';
        """);
  }

  @Test
  public void testPlaceholderInPlaceholderValue() {
    registerMessage(
        getTestMessageBuilder("MSG_L")
            .appendJsPlaceholderReference("a")
            .appendStringPart(" has ")
            .appendJsPlaceholderReference("b")
            .build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_L = goog.getMsg('{$a} has {$b}', {a: '{$b}', b: 1});
        """,
        """
        /**
         * @desc d
         */
        var MSG_L =
            __jscomp_define_msg__(
                {
                  "key":"MSG_L",
                  "msg_text":"{$a} has {$b}"
                }, {'a': "{$b}", 'b': 1});
        """,
        """
        /** @desc d */
        var MSG_L = '{$b} has ' + 1;
        """);
  }

  @Test
  public void testSimpleMessageReplacementMissing() {
    multiPhaseTestWarning(
        """
        /** @desc d */
        var MSG_E = 'd*6a0@z>t';
        """, //
        """
        /** @desc d */
        var MSG_E =
            __jscomp_define_msg__({"key":"MSG_E", "msg_text":"d*6a0@z\\x3et"});
        """,
        """
        /** @desc d */
        var MSG_E = 'd*6a0@z>t'
        """,
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testSimpleMessageReplacementMissingWithNewStyle() {
    multiPhaseTest(
        "/** @desc d */\n var MSG_E = goog.getMsg('missing');",
        """
        /**
         * @desc d
         */
        var MSG_E =
            __jscomp_define_msg__(
                {
                  "key":"MSG_E",
                  "msg_text":"missing",
                });
        """,
        "/** @desc d */\n var MSG_E = 'missing'");
  }

  @Test
  public void testStrictModeAndMessageReplacementAbsentInBundle() {
    strictReplacement = true;
    multiPhaseTestPostLookupError(
        """
        /** @desc d */
        var MSG_E = goog.getMsg('Hello');
        """,
        """
        /** @desc d */
        var MSG_E = __jscomp_define_msg__({"key":"MSG_E", "msg_text":"Hello"});
        """,
        ReplaceMessages.BUNDLE_DOES_NOT_HAVE_THE_MESSAGE);
  }

  @Test
  public void testStrictModeAndMessageReplacementAbsentInNonEmptyBundle() {
    registerMessage(
        getTestMessageBuilder("MSG_J")
            .appendStringPart("One ")
            .appendJsPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    strictReplacement = true;
    multiPhaseTestPostLookupError(
        """
        /** @desc d */
        var MSG_E = goog.getMsg('Hello');
        """,
        """
        /** @desc d */
        var MSG_E = __jscomp_define_msg__({"key":"MSG_E", "msg_text":"Hello"});
        """,
        ReplaceMessages.BUNDLE_DOES_NOT_HAVE_THE_MESSAGE);
  }

  @Test
  public void testFunctionReplacementMissing() {
    multiPhaseTestWarning(
        "var MSG_F = function() {return 'asdf'};", //
        """
        var MSG_F = function() {
          return __jscomp_define_msg__(
              {
                "key":"MSG_F",
                "msg_text":"asdf"
              },
              {});
        };
        """,
        "var MSG_F = function() {return'asdf'}",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testFunctionWithParamReplacementMissing() {
    multiPhaseTestWarning(
        "var MSG_G = function(measly) { return 'asdf' + measly};",
        """
        var MSG_G = function(measly) {
            return __jscomp_define_msg__(
                {
                  "key":"MSG_G",
                  "msg_text":"asdf{$measly}"
                },
                {"measly":measly});
            };
        """,
        "var MSG_G = function(measly) { return 'asdf' + measly}",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testPlaceholderNameInLowerUnderscoreCase() {
    multiPhaseTestPreLookupError(
        "var MSG_J = goog.getMsg('${$amt_earned}', {amt_earned: x});", MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testBadPlaceholderReferenceInReplacement() {
    registerMessage(getTestMessageBuilder("MSG_K").appendJsPlaceholderReference("amount").build());

    multiPhaseTestPostLookupError(
        """
        /** @desc d */
        var MSG_K = goog.getMsg('Hi {$jane}', {jane: x});
        """,
        """
        /** @desc d */
        var MSG_K =
            __jscomp_define_msg__(
                { "key":"MSG_K", "msg_text":"Hi {$jane}" },
                {'jane': x});
        """,
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedMessageWithPlaceholdersForGoogGetMsgWithoutAny() {
    registerMessage(
        getTestMessageBuilder("MSG_E")
            .appendStringPart("You have purchased ")
            .appendJsPlaceholderReference("amount")
            .appendStringPart(" items.")
            .build());

    multiPhaseTestPostLookupError(
        """
        /** @desc d */
        var MSG_E = goog.getMsg('no placeholders');
        """,
        """
        /** @desc d */
        var MSG_E =
            __jscomp_define_msg__({"key":"MSG_E", "msg_text":"no placeholders"});
        """,
        MESSAGE_TREE_MALFORMED,
        "Message parse tree malformed. The translated message has placeholders, but the definition"
            + " in the JS code does not.");
  }

  @Test
  public void testLegacyStyleNoPlaceholdersVarSyntaxConcat() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("Hi\nthere").build());
    multiPhaseTestWarning(
        "var MSG_A = 'abc' + 'def';", //
        "var MSG_A = __jscomp_define_msg__({\"key\":\"MSG_A\", \"msg_text\":\"abcdef\"});",
        "var MSG_A = 'Hi\\nthere'",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStyleNoPlaceholdersVarSyntax() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("Hi\nthere").build());
    multiPhaseTestWarning(
        "var MSG_A = 'd*6a0@z>t';", //
        "var MSG_A = __jscomp_define_msg__({\"key\":\"MSG_A\", \"msg_text\":\"d*6a0@z\\x3et\"});",
        "var MSG_A='Hi\\nthere'",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStyleNoPlaceholdersFunctionSyntax() {
    registerMessage(getTestMessageBuilder("MSG_B").appendStringPart("Hi\nthere").build());
    multiPhaseTestWarning(
        "var MSG_B = function() {return 'asdf'};", //
        """
        var MSG_B = function() {
            return __jscomp_define_msg__(
                {
                  "key":"MSG_B",
                  "msg_text":"asdf"
                },
                {});
        };
        """,
        "var MSG_B=function(){return'Hi\\nthere'}",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStyleOnePlaceholder() {
    registerMessage(
        getTestMessageBuilder("MSG_C")
            .appendStringPart("One ")
            .appendJsPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());
    multiPhaseTestWarning(
        "var MSG_C = function(measly) {return 'asdf' + measly};",
        """
        var MSG_C = function(measly) {
            return __jscomp_define_msg__(
                {
                  "key":"MSG_C",
                  "msg_text":"asdf{$measly}"
                },
                {"measly":measly});
        };
        """,
        "var MSG_C=function(measly){ return 'One ' + measly + ' ph'; }",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStyleTwoPlaceholders() {
    registerMessage(
        getTestMessageBuilder("MSG_D")
            .appendJsPlaceholderReference("dick")
            .appendStringPart(" and ")
            .appendJsPlaceholderReference("jane")
            .build());
    multiPhaseTestWarning(
        "var MSG_D = function(jane, dick) {return jane + dick};", //
        """
        var MSG_D = function(jane, dick) {
            return __jscomp_define_msg__(
                {
                  "key":"MSG_D",
                  "msg_text":"{$jane}{$dick}"
                },
                {"jane":jane, "dick":dick});
        };
        """,
        "var MSG_D = function(jane,dick) { return dick + ' and ' + jane; }",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testLegacyStylePlaceholderNameInLowerCamelCase() {
    registerMessage(
        getTestMessageBuilder("MSG_E")
            .appendStringPart("Sum: $")
            .appendJsPlaceholderReference("amtEarned")
            .build());
    multiPhaseTestWarning(
        "var MSG_E = function(amtEarned) {return amtEarned + 'x'};",
        """
        var MSG_E = function(amtEarned) {
            return __jscomp_define_msg__(
                {
                  "key":"MSG_E",
                  "msg_text":"{$amtEarned}x"
                },
                {"amtEarned":amtEarned});
        };
        """,
        "var MSG_E=function(amtEarned){return'Sum: $'+amtEarned}",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testInvalidRhs() {
    // If the RHS of a variable named `MSG_*` is not a function call, just report a warning.
    multiPhaseTestWarning(
        "var MSG_A = 'string value';",
        "var MSG_A = 'string value';",
        "var MSG_A = 'string value';",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);

    multiPhaseTestWarning(
        "var MSG_A = 15 * 12;",
        "var MSG_A = 15 * 12;",
        "var MSG_A = 15 * 12;",
        MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testTranslatedPlaceHolderMissMatch() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendJsPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError("var MSG_A = goog.getMsg('{$a}');", MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedBadBooleanOptionValue() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendJsPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        // used an object when a boolean is required
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { html: {} });",
        MESSAGE_TREE_MALFORMED);
    multiPhaseTestPreLookupError(
        // used an object when a boolean is required
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { unescapeHtmlEntities: {} });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedMisspelledExamples() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendJsPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        // mistakenly used "examples" instead of "example"
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { examples: { 'a': 'example a' } });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedMisspelledOriginalCode() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendJsPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        // mistakenly used "original" instead of "original_code"
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { original: { 'a': 'code' } });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedExampleWithUnknownPlaceholder() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendJsPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { example: { 'b': 'example a' } });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedExampleWithNonStringPlaceholderValue() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendJsPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { example: { 'a': 1 } });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedExampleWithBadValue() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendJsPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { example: 'bad value' });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedExampleWithComputedProperty() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendJsPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    multiPhaseTestPreLookupError(
        // computed property is not allowed for examples
        "var MSG_A = goog.getMsg('{$a}', {'a': 'something'}, { example: { ['a']: 'wrong' } });",
        MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testBadFallbackSyntax1() {
    multiPhaseTestPreLookupError(
        """
        /** @desc d */

        var MSG_A = goog.getMsg('asdf');
        var x = goog.getMsgWithFallback(MSG_A);
        """,
        JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax2() {
    multiPhaseTestPreLookupError(
        "var x = goog.getMsgWithFallback('abc', 'bcd');", JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax3() {
    multiPhaseTestPreLookupError(
        """
        /** @desc d */

        var MSG_A = goog.getMsg('asdf');var x = goog.getMsgWithFallback(MSG_A, NOT_A_MESSAGE);
        """,
        JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax4() {
    multiPhaseTestPreLookupError(
        """
        /** @desc d */

        var MSG_A = goog.getMsg('asdf');var x = goog.getMsgWithFallback(NOT_A_MESSAGE, MSG_A);
        """,
        JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax5() {
    multiPhaseTestPreLookupError(
        """
        /** @desc d */

        var MSG_A = goog.getMsg('asdf');var x = goog.getMsgWithFallback(MSG_A, MSG_DOES_NOT_EXIST);
        """,
        JsMessageVisitor.FALLBACK_ARG_ERROR);
  }

  @Test
  public void testBadFallbackSyntax6() {
    multiPhaseTestPreLookupError(
        """
        /** @desc d */

        var MSG_A = goog.getMsg('asdf');var x = goog.getMsgWithFallback(MSG_DOES_NOT_EXIST, MSG_A);
        """,
        JsMessageVisitor.FALLBACK_ARG_ERROR);
  }

  @Test
  public void testUseFallback() {
    registerMessage(getTestMessageBuilder("MSG_B").appendStringPart("translated").build());
    multiPhaseTest(
        """
        /** @desc d */
        var MSG_A = goog.getMsg('msg A');
        /** @desc d */
        var MSG_B = goog.getMsg('msg B');
        var x = goog.getMsgWithFallback(MSG_A, MSG_B);
        """,
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"msg A",
                });
        /**
         * @desc d
         */
        var MSG_B =
            __jscomp_define_msg__(
                {
                  "key":"MSG_B",
                  "msg_text":"msg B",
                });
        var x = __jscomp_msg_fallback__("MSG_A", MSG_A, "MSG_B", MSG_B);
        """,
        """
        /** @desc d */
        var MSG_A = 'msg A';
        /** @desc d */
        var MSG_B = 'translated';
        var x = MSG_B;
        """);
  }

  @Test
  public void testFallbackEmptyBundle() {
    multiPhaseTest(
        """
        /** @desc d */
        var MSG_A = goog.getMsg('msg A');
        /** @desc d */
        var MSG_B = goog.getMsg('msg B');
        var x = goog.getMsgWithFallback(MSG_A, MSG_B);
        """,
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"msg A",
                });
        /**
         * @desc d
         */
        var MSG_B =
            __jscomp_define_msg__(
                {
                  "key":"MSG_B",
                  "msg_text":"msg B",
                });
        var x = __jscomp_msg_fallback__("MSG_A", MSG_A, "MSG_B", MSG_B);
        """,
        """
        /** @desc d */
        var MSG_A = 'msg A';
        /** @desc d */
        var MSG_B = 'msg B';
        var x = MSG_A;
        """);
  }

  @Test
  public void testNoUseFallback() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("translated").build());
    multiPhaseTest(
        """
        /** @desc d */
        var MSG_A = goog.getMsg('msg A');
        /** @desc d */
        var MSG_B = goog.getMsg('msg B');
        var x = goog.getMsgWithFallback(MSG_A, MSG_B);
        """,
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"msg A",
                });
        /**
         * @desc d
         */
        var MSG_B =
            __jscomp_define_msg__(
                {
                  "key":"MSG_B",
                  "msg_text":"msg B",
                });
        var x = __jscomp_msg_fallback__("MSG_A", MSG_A, "MSG_B", MSG_B);
        """,
        """
        /** @desc d */
        var MSG_A = 'translated';
        /** @desc d */
        var MSG_B = 'msg B';
        var x = MSG_A;
        """);
  }

  @Test
  public void testNoUseFallback2() {
    registerMessage(getTestMessageBuilder("MSG_C").appendStringPart("translated").build());
    multiPhaseTest(
        """
        /** @desc d */
        var MSG_A = goog.getMsg('msg A');
        /** @desc d */
        var MSG_B = goog.getMsg('msg B');
        var x = goog.getMsgWithFallback(MSG_A, MSG_B);
        """,
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"msg A",
                });
        /**
         * @desc d
         */
        var MSG_B =
            __jscomp_define_msg__(
                {
                  "key":"MSG_B",
                  "msg_text":"msg B",
                });
        var x = __jscomp_msg_fallback__("MSG_A", MSG_A, "MSG_B", MSG_B);
        """,
        """
        /** @desc d */
        var MSG_A = 'msg A';
        /** @desc d */
        var MSG_B = 'msg B';
        var x = MSG_A;
        """);
  }

  @Test
  public void testTemplateLiteralSimple() {
    registerMessage(getTestMessageBuilder("MSG_A").appendStringPart("Hi\nthere").build());

    multiPhaseTest(
        "/** @desc d */\n var MSG_A = goog.getMsg(`asdf`);",
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"asdf",
                });
        """,
        "/** @desc d */\n var MSG_A='Hi\\nthere'");
  }

  @Test
  public void testTemplateLiteralNameReplacement() {
    registerMessage(
        getTestMessageBuilder("MSG_B")
            .appendStringPart("One ")
            .appendJsPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    multiPhaseTest(
        "/** @desc d */\n var MSG_B=goog.getMsg(`asdf {$measly}`, {measly: x});",
        """
        /**
         * @desc d
         */
        var MSG_B =
            __jscomp_define_msg__(
                {
                  "key":"MSG_B",
                  "msg_text":"asdf {$measly}",
                },
                {'measly': x});
        """,
        """
        /** @desc d */
        var MSG_B = 'One ' + x + ' ph';
        """);
  }

  @Test
  public void testTemplateLiteralSubstitutions() {
    // Only allow template literals that are constant strings
    registerMessage(getTestMessageBuilder("MSG_C").appendStringPart("Hi\nthere").build());

    multiPhaseTestPreLookupError(
        "/** @desc d */\n var MSG_C = goog.getMsg(`asdf ${42}`);",
        JsMessageVisitor.MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testReplaceUnescapeHtmlEntitiesMessage() {
    multiPhaseTest(
        "/** @desc d */\n var MSG_A = goog.getMsg('A', {}, {unescapeHtmlEntities: true});",
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"A",
                  "unescapeHtmlEntities":""
                },
                {});
        """,
        "/** @desc d */\n var MSG_A = 'A';");
    multiPhaseTest(
"""
/** @desc d */

var MSG_A = goog.getMsg('User&apos;s &lt; email &amp; address &gt; are &quot;correct&quot;', {}, {unescapeHtmlEntities: true});
""",
"""
/**
 * @desc d
 */
var MSG_A =
    __jscomp_define_msg__(
        {
          "key":"MSG_A",
          "msg_text":"User\\x26apos;s \\x26lt; email \\x26amp; address \\x26gt; are \\x26quot;correct\\x26quot;",
          "unescapeHtmlEntities":""
        },
        {});

""",
        "/** @desc d */\n var MSG_A = 'User\\'s < email & address > are \"correct\"';");
    multiPhaseTest(
"""
/** @desc d */

var MSG_A = goog.getMsg('&lt; {$startSpan}email &amp; address{$endSpan} &gt;', {'startSpan': '<span title="&lt;info&gt;">', 'endSpan': '</span>'}, {unescapeHtmlEntities: true});
""",
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"\\x26lt; {$startSpan}email \\x26amp; address{$endSpan} \\x26gt;",
                  "unescapeHtmlEntities":""
                },
                {
                  "startSpan":'\\x3cspan title\\x3d"\\x26lt;info\\x26gt;"\\x3e',
                  "endSpan":"\\x3c/span\\x3e"
                });
        """,
        """
        /** @desc d */
        var MSG_A =
            '< <span title="&lt;info&gt;">email & address</span> >';
        """);
    multiPhaseTest(
"""
/** @desc d */

var MSG_A = goog.getMsg('&amp;lt;double &amp;amp; escaping&amp;gt;', {}, {unescapeHtmlEntities: true});
""",
        """
        /**
        * @desc d
        */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"\\x26amp;lt;double \\x26amp;amp; escaping\\x26amp;gt;",
                  "unescapeHtmlEntities":""
                },
                {});
        """,
        "/** @desc d */\n var MSG_A = '&lt;double &amp; escaping&gt;';");
  }

  @Test
  public void testReplaceUnescapeHtmlEntitiesMessageWithReplacement() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendStringPart("User")
            .appendStringPart("&")
            .appendStringPart("apos;s &")
            .appendStringPart("lt;")
            .appendStringPart(" email &a")
            .appendStringPart("mp; address &gt")
            .appendStringPart("; are &quot;correct")
            .appendStringPart("&quot;")
            .build());
    multiPhaseTest(
        "/** @desc d */\n var MSG_A = goog.getMsg('A', {}, {unescapeHtmlEntities: true});",
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"A",
                  "unescapeHtmlEntities":""
                },
                {});
        """,
        """
        /** @desc d */
        var MSG_A = 'User\\'s < email & address > are "correct"';
        """);

    registerMessage(
        getTestMessageBuilder("MSG_B")
            .appendStringPart("User")
            .appendStringPart("&apos;")
            .appendStringPart("s ")
            .appendStringPart("&lt;")
            .appendStringPart(" email ")
            .appendStringPart("&amp;")
            .appendStringPart(" address ")
            .appendStringPart("&gt;")
            .appendStringPart(" are ")
            .appendStringPart("&quot;")
            .appendStringPart("correct")
            .appendStringPart("&quot;")
            .build());
    multiPhaseTest(
        "/** @desc d */\n var MSG_B = goog.getMsg('B', {}, {unescapeHtmlEntities: true});",
        """
        /**
         * @desc d
         */
        var MSG_B =
            __jscomp_define_msg__(
                {
                  "key":"MSG_B",
                  "msg_text":"B",
                  "unescapeHtmlEntities":""
                },
                {});
        """,
        "/** @desc d */\n var MSG_B = 'User\\'s < email & address > are \"correct\"';");

    registerMessage(
        getTestMessageBuilder("MSG_C")
            .appendJsPlaceholderReference("br")
            .appendStringPart("&")
            .appendStringPart("amp;")
            .appendJsPlaceholderReference("x")
            .appendJsPlaceholderReference("y")
            .appendStringPart("&ap")
            .appendJsPlaceholderReference("z")
            .appendStringPart("os;")
            .build());
    multiPhaseTest(
"""
/** @desc d */

var MSG_C = goog.getMsg('{$br}{$x}{$y}{$z}', {'br': '<br>', 'x': 'X', 'y': 'Y', 'z': 'Z'}, {unescapeHtmlEntities: true});
""",
        """
        /**
         * @desc d
         */
        var MSG_C =
            __jscomp_define_msg__(
                {
                  "key":"MSG_C",
                  "msg_text":"{$br}{$x}{$y}{$z}",
                  "unescapeHtmlEntities":""
                },
                {
                  "br":"\\x3cbr\\x3e",
                  "x":"X",
                  "y":"Y",
                  "z":"Z"
                });
        """,
        """
        /** @desc d */
        var MSG_C = '<br>&XY&apZos;';
        """);
  }

  @Test
  public void testReplaceHtmlMessageWithPlaceholder() {
    registerMessage(
        getTestMessageBuilder("MSG_A")
            .appendStringPart("Hello <") // html option changes `<` to `&lt;
            .appendJsPlaceholderReference("br")
            .appendStringPart("&gt;")
            .build());

    multiPhaseTest(
        "/** @desc d */\n var MSG_A = goog.getMsg('{$br}', {'br': '<br>'}, {html: true});",
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"{$br}",
                  "escapeLessThan":"",
                },
                {"br":"\\x3cbr\\x3e"});
        """,
        """
        /** @desc d */
        var MSG_A = 'Hello &lt;<br>&gt;';
        """);

    // Confirm that the default behavior is to leave `<` unchanged
    multiPhaseTest(
        "/** @desc d */\n var MSG_A = goog.getMsg('{$br}', {'br': '<br>'});",
        """
        /**
         * @desc d
         */
        var MSG_A =
            __jscomp_define_msg__(
                {
                  "key":"MSG_A",
                  "msg_text":"{$br}",
                },
                {"br":"\\x3cbr\\x3e"});
        """,
        """
        /** @desc d */
        var MSG_A = 'Hello <<br>&gt;';
        """);
  }

  @Test
  public void testReplaceGenderedMessagesWithoutPlaceholders() {
    registerMessage(
        getTestMessageBuilder("MSG_E")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.MASCULINE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, "Bienvenido!")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.FEMININE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.FEMININE, "Bienvenida!")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.NEUTER)
            .appendStringPart(JsMessage.GrammaticalGenderCase.NEUTER, "Les damos la bienvenida!")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.OTHER)
            .appendStringPart(
                JsMessage.GrammaticalGenderCase.OTHER, "Les damos la bienvenida! - OTHER")
            .build());

    multiPhaseTest(
        "/** @desc d */\nvar MSG_E = goog.getMsg('Welcome!');",
        """
        /**
         * @desc d
         */
        var MSG_E = __jscomp_define_msg__({"key":"MSG_E", "msg_text":"Welcome!"});
        """,
        """
        /** @desc d */
        var MSG_E = goog.msgKind.MASCULINE ? 'Bienvenido!' :
                    goog.msgKind.FEMININE  ? 'Bienvenida!' :
                    goog.msgKind.NEUTER  ? 'Les damos la bienvenida!' :
                    'Les damos la bienvenida! - OTHER';
        """);
  }

  @Test
  public void testReplaceGenderedMessagesWithoutPlaceholdersNoNeuter() {
    registerMessage(
        getTestMessageBuilder("MSG_E")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.MASCULINE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, "Bienvenido!")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.FEMININE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.FEMININE, "Bienvenida!")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.OTHER)
            .appendStringPart(JsMessage.GrammaticalGenderCase.OTHER, "Les damos la bienvenida!")
            .build());

    multiPhaseTest(
        "/** @desc d */\nvar MSG_E = goog.getMsg('Welcome!');",
        """
        /**
         * @desc d
         */
        var MSG_E = __jscomp_define_msg__({"key":"MSG_E", "msg_text":"Welcome!"});
        """,
        """
        /** @desc d */
        var MSG_E = goog.msgKind.MASCULINE ? 'Bienvenido!' :
                    goog.msgKind.FEMININE  ? 'Bienvenida!' :
                    'Les damos la bienvenida!';
        """);
  }

  @Test
  public void testReplaceGenderedMessagesWithoutPlaceholdersDifferentOrder() {
    registerMessage(
        getTestMessageBuilder("MSG_E")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.OTHER)
            .appendStringPart(
                JsMessage.GrammaticalGenderCase.OTHER, "Les damos la bienvenida! - OTHER")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.NEUTER)
            .appendStringPart(JsMessage.GrammaticalGenderCase.NEUTER, "Les damos la bienvenida!")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.FEMININE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.FEMININE, "Bienvenida!")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.MASCULINE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, "Bienvenido!")
            .build());

    multiPhaseTest(
        "/** @desc d */\nvar MSG_E = goog.getMsg('Welcome!');",
        """
        /**
         * @desc d
         */
        var MSG_E = __jscomp_define_msg__({"key":"MSG_E", "msg_text":"Welcome!"});
        """,
        """
        /** @desc d */
        var MSG_E = goog.msgKind.NEUTER  ? 'Les damos la bienvenida!' :
                    goog.msgKind.FEMININE  ? 'Bienvenida!' :
                    goog.msgKind.MASCULINE ? 'Bienvenido!' :
                    'Les damos la bienvenida! - OTHER';
        """);
  }

  @Test
  public void testReplaceGenderedMessagesWithoutPlaceholdersDifferentOrder2() {

    registerMessage(
        getTestMessageBuilder("MSG_E")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.NEUTER)
            .appendStringPart(JsMessage.GrammaticalGenderCase.NEUTER, "Les damos la bienvenida!")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.OTHER)
            .appendStringPart(
                JsMessage.GrammaticalGenderCase.OTHER, "Les damos la bienvenida! - OTHER")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.FEMININE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.FEMININE, "Bienvenida!")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.MASCULINE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, "Bienvenido!")
            .build());

    multiPhaseTest(
        "/** @desc d */\nvar MSG_E = goog.getMsg('Welcome!');",
        """
        /**
         * @desc d
         */
        var MSG_E = __jscomp_define_msg__({"key":"MSG_E", "msg_text":"Welcome!"});
        """,
        """
        /** @desc d */
        var MSG_E = goog.msgKind.NEUTER  ? 'Les damos la bienvenida!' :
                    goog.msgKind.FEMININE ? 'Bienvenida!' :
                    goog.msgKind.MASCULINE  ? 'Bienvenido!' :
                    'Les damos la bienvenida! - OTHER';
        """);
  }

  @Test
  public void testReplaceGenderedMessagesWithoutPlaceholdersDifferentOrder3() {

    registerMessage(
        getTestMessageBuilder("MSG_E")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.NEUTER)
            .appendStringPart(JsMessage.GrammaticalGenderCase.NEUTER, "Les damos la bienvenida!")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.FEMININE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.FEMININE, "Bienvenida!")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.OTHER)
            .appendStringPart(
                JsMessage.GrammaticalGenderCase.OTHER, "Les damos la bienvenida! - OTHER")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.MASCULINE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, "Bienvenido!")
            .build());

    multiPhaseTest(
        "/** @desc d */\nvar MSG_E = goog.getMsg('Welcome!');",
        """
        /**
         * @desc d
         */
        var MSG_E = __jscomp_define_msg__({"key":"MSG_E", "msg_text":"Welcome!"});
        """,
        """
        /** @desc d */
        var MSG_E = goog.msgKind.NEUTER  ? 'Les damos la bienvenida!' :
                    goog.msgKind.FEMININE  ? 'Bienvenida!' :
                    goog.msgKind.MASCULINE  ? 'Bienvenido!' :
                    'Les damos la bienvenida! - OTHER';
        """);
  }

  @Test
  public void testReplaceGenderedMessageWithPlaceholders() {
    registerMessage(
        getTestMessageBuilder("MSG_E")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.MASCULINE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, "Bienvenido ")
            .appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.MASCULINE, "name")
            .appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, ", a tu nuevo ")
            .appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.MASCULINE, "device")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.FEMININE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.FEMININE, "Bienvenida ")
            .appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.FEMININE, "name")
            .appendStringPart(JsMessage.GrammaticalGenderCase.FEMININE, ", a tu nuevo ")
            .appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.FEMININE, "device")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.NEUTER)
            .appendStringPart(JsMessage.GrammaticalGenderCase.NEUTER, "Les damos la bienvenida ")
            .appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.NEUTER, "name")
            .appendStringPart(JsMessage.GrammaticalGenderCase.NEUTER, ", a tu nuevo ")
            .appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.NEUTER, "device")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.OTHER)
            .appendStringPart(
                JsMessage.GrammaticalGenderCase.OTHER, "Les damos la bienvenida! - OTHER ")
            .appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.OTHER, "name")
            .appendStringPart(JsMessage.GrammaticalGenderCase.OTHER, ", a tu nuevo ")
            .appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.OTHER, "device")
            .build());

    multiPhaseTest(
        """
        /** @desc d */
        var MSG_E = goog.getMsg(
            'Welcome, {$name}, to your new {$device}.',
            {name: user.getName(), device: user.getDevice()});
        """,
        """
        /**
         * @desc d
         */
         var MSG_E = __jscomp_define_msg__({
          "key": "MSG_E",
          "msg_text": "Welcome, {$name}, to your new {$device}."
        }, {
          "name": user.getName(),
          "device": user.getDevice()
        });
        """,
"""
/** @desc d */
var MSG_E = function(namem1146332801$0, devicem1146332801$0) {
  return goog.msgKind.MASCULINE ? 'Bienvenido ' + namem1146332801$0 + ', a tu nuevo ' + devicem1146332801$0 :
         goog.msgKind.FEMININE  ? 'Bienvenida ' + namem1146332801$0 + ', a tu nuevo ' + devicem1146332801$0 :
         goog.msgKind.NEUTER  ? 'Les damos la bienvenida ' + namem1146332801$0 + ', a tu nuevo ' + devicem1146332801$0 :
         'Les damos la bienvenida! - OTHER ' + namem1146332801$0 + ', a tu nuevo ' + devicem1146332801$0;
}(user.getName(), user.getDevice());
""");
  }

  @Test
  public void testReplaceIcuTemplateGenderedMessageWithBundleAndJsPlaceholders() {

    registerMessage(
        getTestMessageBuilder("MSG_E")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.MASCULINE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, "Bienvenido ")
            .appendCanonicalPlaceholderReference(JsMessage.GrammaticalGenderCase.MASCULINE, "NAME")
            .appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, ", a tu nuevo ")
            .appendCanonicalPlaceholderReference(JsMessage.GrammaticalGenderCase.MASCULINE, "PHONE")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.FEMININE)
            .appendStringPart(JsMessage.GrammaticalGenderCase.FEMININE, "Bienvenida ")
            .appendCanonicalPlaceholderReference(JsMessage.GrammaticalGenderCase.FEMININE, "NAME")
            .appendStringPart(JsMessage.GrammaticalGenderCase.FEMININE, ", a tu nuevo ")
            .appendCanonicalPlaceholderReference(JsMessage.GrammaticalGenderCase.FEMININE, "PHONE")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.NEUTER)
            .appendStringPart(JsMessage.GrammaticalGenderCase.NEUTER, "Les damos la bienvenida ")
            .appendCanonicalPlaceholderReference(JsMessage.GrammaticalGenderCase.NEUTER, "NAME")
            .appendStringPart(JsMessage.GrammaticalGenderCase.NEUTER, ", a tu nuevo ")
            .appendCanonicalPlaceholderReference(JsMessage.GrammaticalGenderCase.NEUTER, "PHONE")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.OTHER)
            .appendStringPart(
                JsMessage.GrammaticalGenderCase.OTHER, "Les damos la bienvenida! - OTHER ")
            .appendCanonicalPlaceholderReference(JsMessage.GrammaticalGenderCase.OTHER, "NAME")
            .appendStringPart(JsMessage.GrammaticalGenderCase.OTHER, ", a tu nuevo ")
            .appendCanonicalPlaceholderReference(JsMessage.GrammaticalGenderCase.OTHER, "PHONE")
            .build());

    multiPhaseTest(
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        var MSG_E =
            declareIcuTemplate(
                'Welcome {NAME}, to your new {PHONE}',
                {
                  description: 'Welcome message',
                  example: {
                    'NAME': 'John Doe',
                    'PHONE': 'Pixel'
                   }
                });
        """,
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        var MSG_E =
            __jscomp_define_msg__(
                {
                  "key":    "MSG_E",
                  "icu_placeholder_names": ["NAME", "PHONE"],
                  "msg_text": "Welcome {NAME}, to your new {PHONE}",
                  "isIcuTemplate": ""
                });
        """,
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        var MSG_E = goog.msgKind.MASCULINE ? 'Bienvenido {NAME}, a tu nuevo {PHONE}' :
                    goog.msgKind.FEMININE  ? 'Bienvenida {NAME}, a tu nuevo {PHONE}' :
                    goog.msgKind.NEUTER  ? 'Les damos la bienvenida {NAME}, a tu nuevo {PHONE}' :
                    'Les damos la bienvenida! - OTHER {NAME}, a tu nuevo {PHONE}';
        """);
  }

  @Test
  public void testReplaceGenderedMessageWithIcuPlaceholderAtStart() {
    registerMessage(
        getTestMessageBuilder("MSG_StartWithPlaceholder")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.MASCULINE)
            .appendCanonicalPlaceholderReference(JsMessage.GrammaticalGenderCase.MASCULINE, "PH")
            .appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, " suffix")
            .addGenderedMessageKey(JsMessage.GrammaticalGenderCase.OTHER)
            .appendCanonicalPlaceholderReference(JsMessage.GrammaticalGenderCase.OTHER, "PH")
            .appendStringPart(JsMessage.GrammaticalGenderCase.OTHER, " suffix")
            .build());

    // We expect this to fail with NPE before the fix
    multiPhaseTest(
        """
        /** @desc d */
        var MSG_StartWithPlaceholder = goog.getMsg('{PH} suffix');
        """,
        """
        /**
         * @desc d
         */
        var MSG_StartWithPlaceholder =
            __jscomp_define_msg__(
                {
                  "key": "MSG_StartWithPlaceholder",
                  "msg_text": "{PH} suffix"
                });
        """,
        """
        /** @desc d */
        var MSG_StartWithPlaceholder = goog.msgKind.MASCULINE ? '{PH} suffix' : '{PH} suffix';
        """);
  }

  private void registerMessage(JsMessage message) {
    messages.put(message.getId(), message);
  }

  private class SimpleMessageBundle implements MessageBundle {
    @Override
    public JsMessage getMessage(String id) {
      return messages.get(id);
    }

    @Override
    public Iterable<JsMessage> getAllMessages() {
      return messages.values();
    }

    @Override
    public JsMessage.IdGenerator idGenerator() {
      return useTestIdGenerator ? TEST_ID_GENERATOR : null;
    }
  }

  @Test
  public void testLeakingPlaceholderRegression() {
    // Message A: Gendered, has placeholder "name".
    JsMessage.Builder builderA = new JsMessage.Builder().setKey("MSG_A").setId("MSG_A");
    builderA.addGenderedMessageKey(JsMessage.GrammaticalGenderCase.MASCULINE);
    builderA.appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, "A male ");
    builderA.appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.MASCULINE, "name");
    builderA.addGenderedMessageKey(JsMessage.GrammaticalGenderCase.OTHER);
    builderA.appendStringPart(JsMessage.GrammaticalGenderCase.OTHER, "A other ");
    builderA.appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.OTHER, "name");
    registerMessage(builderA.build());

    // Message B: Gendered, has placeholder "name" in the translation parts (simulating ICU mixed),
    // but will NOT have it in the goog.getMsg placeholders map.
    JsMessage.Builder builderB = new JsMessage.Builder().setKey("MSG_B").setId("MSG_B");
    builderB.addGenderedMessageKey(JsMessage.GrammaticalGenderCase.MASCULINE);
    builderB.appendStringPart(JsMessage.GrammaticalGenderCase.MASCULINE, "B male {name}");
    // This part is a placeholder, but since it's not in the goog.getMsg args, it should be treated
    // as literal
    builderB.appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.MASCULINE, "name");

    builderB.addGenderedMessageKey(JsMessage.GrammaticalGenderCase.OTHER);
    builderB.appendStringPart(JsMessage.GrammaticalGenderCase.OTHER, "B other {name}");
    builderB.appendJsPlaceholderReference(JsMessage.GrammaticalGenderCase.OTHER, "name");

    registerMessage(builderB.build());

    // Note: We use test() directly instead of multiPhaseTest because we want to test interaction
    // between two messages in the same pass run, and we are specifically targeting the bug in
    // ReplacementCompletionPass logic where state leaked.
    // However, multiPhaseTest does separate compilations for each phase.
    // If we use FULL_REPLACE mode (default), it runs getFullReplacementPass() which does it all in
    // one go (for legacy reasons).
    // The bug was in ReplaceMessages class structure (field vs local), which is shared if the same
    // instance is used.
    // getProcessor() creates a new instance.
    // But getFullReplacementPass() returns an inner class instance.
    // So if we run FULL_REPLACE, we exercise the logic.

    testMode = TestMode.FULL_REPLACE;
    test(
        """
        /** @desc d */
        var MSG_A = goog.getMsg('A {$name}', {name: 'User'});
        /** @desc d */
        var MSG_B = goog.getMsg('B {name}');
        """,
        """
        /** @desc d */
        var MSG_A = function(namem1146332801$0) {
          return goog.msgKind.MASCULINE ? "A male " + namem1146332801$0 : "A other " + namem1146332801$0;
        }("User");
        /** @desc d */
        var MSG_B = goog.msgKind.MASCULINE ? 'B male {name}{NAME}' : 'B other {name}{NAME}';
        """);
  }
}
