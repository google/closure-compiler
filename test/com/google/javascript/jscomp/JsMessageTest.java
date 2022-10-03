/*
 * Copyright 2009 The Closure Compiler Authors.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JsMessageTest {

  @Test
  public void testIsLowerCamelCaseWithNumericSuffixes() {
    assertThat(JsMessage.isLowerCamelCaseWithNumericSuffixes("name")).isTrue();
    assertThat(JsMessage.isLowerCamelCaseWithNumericSuffixes("NAME")).isFalse();
    assertThat(JsMessage.isLowerCamelCaseWithNumericSuffixes("Name")).isFalse();

    assertThat(JsMessage.isLowerCamelCaseWithNumericSuffixes("a4Letter")).isTrue();
    assertThat(JsMessage.isLowerCamelCaseWithNumericSuffixes("A4_LETTER")).isFalse();

    assertThat(JsMessage.isLowerCamelCaseWithNumericSuffixes("startSpan_1_23")).isTrue();
    assertThat(JsMessage.isLowerCamelCaseWithNumericSuffixes("startSpan_1_23b")).isFalse();
    assertThat(JsMessage.isLowerCamelCaseWithNumericSuffixes("START_SPAN_1_23")).isFalse();

    assertThat(JsMessage.isLowerCamelCaseWithNumericSuffixes("")).isFalse();
  }

  @Test
  public void testToLowerCamelCaseWithNumericSuffixes() {
    assertThat(JsMessage.toLowerCamelCaseWithNumericSuffixes("NAME")).isEqualTo("name");
    assertThat(JsMessage.toLowerCamelCaseWithNumericSuffixes("A4_LETTER")).isEqualTo("a4Letter");
    assertThat(JsMessage.toLowerCamelCaseWithNumericSuffixes("START_SPAN_1_23"))
        .isEqualTo("startSpan_1_23");
  }

  @Test
  public void testIsEmpty() {
    assertThat(newTestMessageBuilder("MSG_KEY").build().isEmpty()).isTrue();
    assertThat(newTestMessageBuilder("MSG_KEY").appendStringPart("").build().isEmpty()).isTrue();
    assertThat(
            newTestMessageBuilder("MSG_KEY")
                .appendStringPart("")
                .appendStringPart("")
                .build()
                .isEmpty())
        .isTrue();
    assertThat(
            newTestMessageBuilder("MSG_KEY")
                .appendStringPart("s")
                .appendStringPart("")
                .build()
                .isEmpty())
        .isFalse();
    assertThat(
            newTestMessageBuilder("MSG_KEY").appendJsPlaceholderReference("ph").build().isEmpty())
        .isFalse();
  }

  /** Return a new message builder that uses the given string as both its key and its ID. */
  private JsMessage.Builder newTestMessageBuilder(String keyAndId) {
    return new JsMessage.Builder().setKey(keyAndId).setId(keyAndId);
  }

  @Test
  public void testHashValues() {
    final long answerString64 = 0x43ec5d9731515874L;
    assertThat(JsMessage.Hash.hash64("Hello, world")).isEqualTo(answerString64);

    final long answerEmpty64 = 0x468d9ea2c42361aaL;
    assertThat(JsMessage.Hash.hash64("")).isEqualTo(answerEmpty64);
  }

  @Test
  public void testNoAlternateId() {
    JsMessage msg = newTestMessageBuilder("MSG_SOME_KEY").setDesc("Hello.").build();
    assertThat(msg.getDesc()).isEqualTo("Hello.");
    assertThat(msg.getId()).isEqualTo("MSG_SOME_KEY");
    assertThat(msg.getAlternateId()).isNull();
  }

  @Test
  public void testSelfReferentialAlternateId() {
    JsMessage msg =
        newTestMessageBuilder("MSG_SOME_NAME")
            .setDesc("Hello.")
            .setAlternateId("MSG_SOME_NAME")
            .build();
    assertThat(msg.getDesc()).isEqualTo("Hello.");
    assertThat(msg.getId()).isEqualTo("MSG_SOME_NAME");
    assertThat(msg.getAlternateId()).isNull();
  }

  @Test
  public void testAlternateId() {
    JsMessage msg =
        new JsMessage.Builder()
            .setKey("MSG_KEY")
            .setDesc("Hello.")
            .setAlternateId("foo")
            .setMeaning("meaning")
            .setId("meaning")
            .appendJsPlaceholderReference("placeholder0")
            .appendJsPlaceholderReference("placeholder1")
            .appendStringPart("part0")
            .appendStringPart("part1")
            .appendStringPart("part2")
            .build();
    assertThat(msg.getDesc()).isEqualTo("Hello.");
    assertThat(msg.getId()).isEqualTo("meaning");
    assertThat(msg.getAlternateId()).isEqualTo("foo");
  }
}
