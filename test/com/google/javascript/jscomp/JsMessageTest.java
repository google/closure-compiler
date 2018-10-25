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

/** @author anatol@google.com (Anatol Pomazau) */
@RunWith(JUnit4.class)
public final class JsMessageTest {

  @Test
  public void testIsEmpty() {
    assertThat(new JsMessage.Builder().build().isEmpty()).isTrue();
    assertThat(new JsMessage.Builder().appendStringPart("").build().isEmpty()).isTrue();
    assertThat(new JsMessage.Builder().appendStringPart("").appendStringPart("").build().isEmpty())
        .isTrue();
    assertThat(new JsMessage.Builder().appendStringPart("s").appendStringPart("").build().isEmpty())
        .isFalse();
    assertThat(new JsMessage.Builder().appendPlaceholderReference("3").build().isEmpty()).isFalse();
  }

  @Test
  public void testMeaningChangesId() {
    String id1 = new JsMessage.Builder()
        .appendStringPart("foo").build().getId();
    String id2 = new JsMessage.Builder()
        .appendStringPart("foo").setMeaning("bar").build().getId();
    assertThat(id1.equals(id2)).isFalse();
  }

  @Test
  public void testHashValues() {
    final String EMPTY = "";
    final String VAL = "Hello, world";
    final long   ANSWER_STRING_64 = 0x43ec5d9731515874L;
    final long   ANSWER_EMPTY_64 = 0x468d9ea2c42361aaL;

    assertThat(JsMessage.Hash.hash64(VAL)).isEqualTo(ANSWER_STRING_64);
    assertThat(JsMessage.Hash.hash64(EMPTY)).isEqualTo(ANSWER_EMPTY_64);
  }
}
