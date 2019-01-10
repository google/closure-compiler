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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit test for {@link JsMessageExtractor}.
 *
 */
@RunWith(JUnit4.class)
public final class JsMessageExtractorTest {

  private JsMessage.Style mode;

  @Before
  public void setUp() throws Exception {
    mode = JsMessage.Style.LEGACY;
  }

  private Collection<JsMessage> extractMessages(String... js) {
    try {
      String sourceCode = Joiner.on("\n").join(js);
      return new JsMessageExtractor(null, mode)
          .extractMessages(SourceFile.fromCode("testcode", sourceCode));
    } catch (IOException e) {
      assertWithMessage(e.getMessage()).fail();
      return null;
    }
  }

  private JsMessage extractMessage(String... js) {
    Collection<JsMessage> messages = extractMessages(js);
    assertThat(messages).hasSize(1);
    return messages.iterator().next();
  }

  @Test
  public void testSyntaxError1() {
    try {
      extractMessage("if (true) {}}");
      assertWithMessage("Expected exception").fail();
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().contains("JSCompiler errors\n");
      assertThat(e).hasMessageThat().contains("testcode:1: ERROR - Parse error");
      assertThat(e).hasMessageThat().contains("if (true) {}}\n");
    }
  }

  @Test
  public void testSyntaxError2() {
    try {
      extractMessage("", "if (true) {}}");
      assertWithMessage("Expected exception").fail();
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().contains("JSCompiler errors\n");
      assertThat(e).hasMessageThat().contains("testcode:2: ERROR - Parse error");
      assertThat(e).hasMessageThat().contains("if (true) {}}\n");
    }
  }

  @Test
  public void testExtractNewStyleMessage1() {
    // A simple message with no description.
    assertEquals(
        new JsMessage.Builder("MSG_SILLY")
            .appendStringPart("silly test message")
            .build(),
        extractMessage("var MSG_SILLY = goog.getMsg('silly test message');"));
  }

  @Test
  public void testExtractNewStyleMessage2() {
    // A message with placeholders and meta data.
    assertEquals(
        new JsMessage.Builder("MSG_WELCOME")
            .appendStringPart("Hi ")
            .appendPlaceholderReference("userName")
            .appendStringPart("! Welcome to ")
            .appendPlaceholderReference("product")
            .appendStringPart(".")
            .setDesc("The welcome message.")
            .setIsHidden(true)
            .build(),
        extractMessage(
            "/**",
            " * @desc The welcome",
            " *   message.",
            " *",
            " * @hidden",
            " */",
            "var MSG_WELCOME = goog.getMsg(",
            "    'Hi {$userName}! Welcome to {$product}.',",
            "    {userName: someUserName, product: getProductName()});"));
  }

  @Test
  public void testExtractOldStyleMessage1() {
    // Description before the message.
    assertEquals(
        new JsMessage.Builder("MSG_SILLY")
            .appendStringPart("silly test message")
            .setDesc("Description.")
            .build(),
        extractMessage(
            "var MSG_SILLY_HELP = 'Description.';",
            "var MSG_SILLY = 'silly test message';"));
  }

  @Test
  public void testExtractOldStyleMessage2() {
    // Description after the message, broken into parts.
    assertEquals(
        new JsMessage.Builder("MSG_SILLY")
            .appendStringPart("silly test message")
            .setDesc("Description.")
            .build(),
        extractMessage(
            "var MSG_SILLY = 'silly test message';",
            "var MSG_SILLY_HELP = 'Descrip' + 'tion.';"));
  }

  @Test
  public void testExtractOldStyleMessage3() {
    // Function-style message with two placeholders and no description.
    assertEquals(
        new JsMessage.Builder("MSG_SILLY")
            .appendPlaceholderReference("one")
            .appendStringPart(", ")
            .appendPlaceholderReference("two")
            .appendStringPart(", buckle my shoe")
            .build(),
        extractMessage(
            "var MSG_SILLY = function(one, two) {",
            "  return one + ', ' + two + ', buckle my shoe';",
            "};"));
  }

  @Test
  public void testExtractMixedMessages() {
    // Several mixed-style messages in succession, one containing newlines.
    Iterator<JsMessage> msgs = extractMessages(
        "var MSG_MONEY = function(amount) {",
        "  return 'You owe $' + amount +",
        "         ' to the credit card company.';",
        "};",
        "var MSG_TIME = goog.getMsg('You need to finish your work in ' +",
        "                           '{$duration} hours.', {'duration': d});",
        "var MSG_NAG = 'Clean your room.\\n\\nWash your clothes.';",
        "var MSG_NAG_HELP = 'Just some ' +",
        "                   'nags.';").iterator();

    assertEquals(
        new JsMessage.Builder("MSG_MONEY")
            .appendStringPart("You owe $")
            .appendPlaceholderReference("amount")
            .appendStringPart(" to the credit card company.")
            .build(),
        msgs.next());
    assertEquals(
        new JsMessage.Builder("MSG_TIME")
            .appendStringPart("You need to finish your work in ")
            .appendPlaceholderReference("duration")
            .appendStringPart(" hours.")
            .build(),
        msgs.next());
    assertEquals(
        new JsMessage.Builder("MSG_NAG")
            .appendStringPart("Clean your room.\n\nWash your clothes.")
            .setDesc("Just some nags.")
            .build(),
        msgs.next());
  }

  @Test
  public void testDuplicateUnnamedVariables() {
    // Make sure that duplicate unnamed variables don't get swallowed when using
    // a Google-specific ID generator.
    Collection<JsMessage> msgs = extractMessages(
        "function a() {",
        "  var MSG_UNNAMED_2 = goog.getMsg('foo');",
        "}",
        "function b() {",
        "  var MSG_UNNAMED_2 = goog.getMsg('bar');",
        "}");

    assertThat(msgs).hasSize(2);
    final Iterator<JsMessage> iter = msgs.iterator();
    assertThat(iter.next().toString()).isEqualTo("foo");
    assertThat(iter.next().toString()).isEqualTo("bar");
  }

  @Test
  public void testMeaningAnnotation() {
    List<JsMessage> msgs = new ArrayList<>(
        extractMessages(
            "var MSG_UNNAMED_1 = goog.getMsg('foo');",
            "var MSG_UNNAMED_2 = goog.getMsg('foo');"));
    assertThat(msgs).hasSize(2);
    assertThat(msgs.get(0).getId()).isEqualTo(msgs.get(1).getId());
    assertEquals(msgs.get(0), msgs.get(1));

    msgs = new ArrayList<>(
        extractMessages(
            "var MSG_UNNAMED_1 = goog.getMsg('foo');",
            "/** @meaning bar */ var MSG_UNNAMED_2 = goog.getMsg('foo');"));
    assertThat(msgs).hasSize(2);
    assertThat(msgs.get(0).getId().equals(msgs.get(1).getId())).isFalse();
  }

  private void assertEquals(JsMessage expected, JsMessage actual) {
    assertThat(actual.getId()).isEqualTo(expected.getId());
    assertThat(actual.getKey()).isEqualTo(expected.getKey());
    assertThat(actual.parts()).isEqualTo(expected.parts());
    assertThat(actual.placeholders()).isEqualTo(expected.placeholders());
    assertThat(actual.getDesc()).isEqualTo(expected.getDesc());
    assertThat(actual.isHidden()).isEqualTo(expected.isHidden());
    assertThat(actual.getMeaning()).isEqualTo(expected.getMeaning());
  }
}
