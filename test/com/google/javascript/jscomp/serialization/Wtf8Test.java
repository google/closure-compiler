/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.serialization;

import static com.google.common.truth.Truth.assertThat;

import com.google.protobuf.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Wtf8Test {

  @Test
  public void testAscii() {
    roundtrip("hello world");
  }

  @Test
  public void testTwoByteLatin1SupplementCharacter() {
    // Two-bytes in UTF-8: https://www.compart.com/en/unicode/U+00BC
    roundtrip("¼");
  }

  @Test
  public void testMultibyteBMPCharacters() {
    // Korean characters are 3 bytes in UTF-8. e.g. https://www.compart.com/en/unicode/U+C138
    roundtrip("세계님 안녕");
  }

  @Test
  public void testEscapedPairedSurrogate() {
    // This is the four-byte character "💅" in UTF-8: https://www.compart.com/en/unicode/U+1F485
    roundtrip("\ud83d\udc85");
  }

  @Test
  public void testUnpairedSurrogates() {
    roundtrip("Hello \ud800");
    roundtrip("\ud800, hello");
  }

  @Test
  public void testInvalidSurrogatePairings() {
    // This is an invalid pair: low-high
    roundtrip("\udc37\ud801");
    // This is an invalid pair: low-low
    roundtrip("\ud802\ud801");
    // This is an invalid pair: high-high
    roundtrip("\udc37\udc39");
  }

  @Test
  public void testUnescapedPairedSurrogates() {
    // Emojis are always 4 bytes in UTF-8 and thus represented as paired surrogates in UTF-16.
    roundtrip("💅🏽");
  }

  private final Wtf8.Decoder decoder = Wtf8.decoder(256);

  private void roundtrip(String s) {
    byte[] serialized = Wtf8.encodeToWtf8(s).toByteArray();
    String roundtripped = this.decoder.decode(ByteString.copyFrom(serialized));
    assertThat(roundtripped).isEqualTo(s);
  }
}
