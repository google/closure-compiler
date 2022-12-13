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
import static org.junit.Assert.assertThrows;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit test for {@link JsMessageExtractor}. */
@RunWith(JUnit4.class)
public final class JsMessageExtractorTest {

  private Collection<JsMessage> extractMessages(String... js) {
    String sourceCode = Joiner.on("\n").join(js);
    return new JsMessageExtractor(null)
        .extractMessages(SourceFile.fromCode("testcode", sourceCode));
  }

  private JsMessage extractMessage(String... js) {
    Collection<JsMessage> messages = extractMessages(js);
    assertThat(messages).hasSize(1);
    return messages.iterator().next();
  }

  @Test
  public void testSyntaxError1() {
    RuntimeException e =
        assertThrows(RuntimeException.class, () -> extractMessage("if (true) {}}"));

    assertThat(e).hasMessageThat().contains("JSCompiler errors\n");
    assertThat(e).hasMessageThat().contains("testcode:1:13: ERROR - [JSC_PARSE_ERROR] Parse error");
    assertThat(e).hasMessageThat().contains("if (true) {}}\n");
  }

  @Test
  public void testSyntaxError2() {
    RuntimeException e =
        assertThrows(RuntimeException.class, () -> extractMessage("", "if (true) {}}"));

    assertThat(e).hasMessageThat().contains("JSCompiler errors\n");
    assertThat(e).hasMessageThat().contains("testcode:2:13: ERROR - [JSC_PARSE_ERROR] Parse error");
    assertThat(e).hasMessageThat().contains("if (true) {}}\n");
  }

  @Test
  public void testExtractNewStyleMessage1() {
    // A simple message with no description.
    assertEquals(
        new JsMessage.Builder()
            .setKey("MSG_SILLY")
            .setId("MSG_SILLY")
            .appendStringPart("silly test message")
            .build(),
        extractMessage("var MSG_SILLY = goog.getMsg('silly test message');"));
  }

  @Test
  public void testOriginalCodeAndExampleMaps() {
    // A message with placeholders and original code annotations.
    assertEquals(
        new JsMessage.Builder()
            .setKey("MSG_WELCOME")
            .setId("MSG_WELCOME")
            .appendStringPart("Hi ")
            .appendJsPlaceholderReference("interpolation_0")
            .appendStringPart("! Welcome to ")
            .appendJsPlaceholderReference("interpolation_1")
            .appendStringPart(".")
            .setPlaceholderNameToOriginalCodeMap(
                ImmutableMap.of(
                    "interpolation_0", "foo.getUserName()",
                    "interpolation_1", "bar.getProductName()"))
            .setPlaceholderNameToExampleMap(
                ImmutableMap.of(
                    "interpolation_0", "Ginny Weasley",
                    "interpolation_1", "Google Muggle Finder"))
            .setDesc("The welcome message.")
            .build(),
        extractMessage(
            "/** @desc The welcome message. */",
            "var MSG_WELCOME = goog.getMsg(",
            "    'Hi {$interpolation_0}! Welcome to {$interpolation_1}.',",
            "    {",
            "        'interpolation_0': 'magic-string-0',",
            "        'interpolation_1': 'magic-string-1',",
            "    },",
            "    {",
            "        original_code: {",
            "            'interpolation_0': 'foo.getUserName()',",
            "            'interpolation_1': 'bar.getProductName()',",
            "        },",
            "        example: {",
            "            'interpolation_0': 'Ginny Weasley',",
            "            'interpolation_1': 'Google Muggle Finder',",
            "        },",
            "    },",
            ");"));
  }

  @Test
  public void testExtractNewStyleMessage2() {
    // A message with placeholders and meta data.
    assertEquals(
        new JsMessage.Builder()
            .setKey("MSG_WELCOME")
            .setId("MSG_WELCOME")
            .appendStringPart("Hi ")
            .appendJsPlaceholderReference("userName")
            .appendStringPart("! Welcome to ")
            .appendJsPlaceholderReference("product")
            .appendStringPart(".")
            .setDesc("The welcome message.")
            .build(),
        extractMessage(
            "/**",
            " * @desc The welcome",
            " *   message.",
            " */",
            "var MSG_WELCOME = goog.getMsg(",
            "    'Hi {$userName}! Welcome to {$product}.',",
            "    {userName: someUserName, product: getProductName()});"));
  }

  @Test
  public void testDuplicateUnnamedVariables() {
    // Make sure that duplicate unnamed variables don't get swallowed when using
    // a Google-specific ID generator.
    Collection<JsMessage> msgs =
        extractMessages(
            "function a() {",
            "  var MSG_UNNAMED_2 = goog.getMsg('foo');",
            "}",
            "function b() {",
            "  var MSG_UNNAMED_2 = goog.getMsg('bar');",
            "}");

    assertThat(msgs).hasSize(2);
    final Iterator<JsMessage> iter = msgs.iterator();
    assertThat(iter.next().asJsMessageString()).isEqualTo("foo");
    assertThat(iter.next().asJsMessageString()).isEqualTo("bar");
  }

  @Test
  public void testMeaningAnnotation() {
    List<JsMessage> msgs =
        new ArrayList<>(
            extractMessages(
                "var MSG_UNNAMED_1 = goog.getMsg('foo');",
                "var MSG_UNNAMED_2 = goog.getMsg('foo');"));
    assertThat(msgs).hasSize(2);
    assertThat(msgs.get(0).getId()).isEqualTo(msgs.get(1).getId());
    assertEquals(msgs.get(0), msgs.get(1));

    msgs =
        new ArrayList<>(
            extractMessages(
                "var MSG_UNNAMED_1 = goog.getMsg('foo');",
                "/** @meaning bar */ var MSG_UNNAMED_2 = goog.getMsg('foo');"));
    assertThat(msgs).hasSize(2);
    assertThat(msgs.get(0).getId().equals(msgs.get(1).getId())).isFalse();
  }

  private void assertEquals(JsMessage expected, JsMessage actual) {
    assertThat(actual.getId()).isEqualTo(expected.getId());
    assertThat(actual.getKey()).isEqualTo(expected.getKey());
    assertThat(actual.getParts()).isEqualTo(expected.getParts());
    assertThat(actual.jsPlaceholderNames()).isEqualTo(expected.jsPlaceholderNames());
    assertThat(actual.getDesc()).isEqualTo(expected.getDesc());
    assertThat(actual.getMeaning()).isEqualTo(expected.getMeaning());
  }
}
