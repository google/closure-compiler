/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

/**
 * Tests for ReplacedStringsDecoder.
 *
 */
public class ReplacedStringsDecoderTest extends TestCase {

  private void verifyDecoder(String original, String replacement) {
    verifyDecoder(original, replacement, "");
  }

  private void verifyDecoder(String original, String replacement, String args) {
    String encoding = "Xy"; // an arbitrary encoding for the test
    VariableMap variableMap = VariableMap.fromMap(ImmutableMap.of(
        encoding, replacement
    ));
    ReplacedStringsDecoder decoder = new ReplacedStringsDecoder(variableMap);
    assertEquals(original, decoder.decode(encoding + args));
  }

  public void testSimpleDecoding() {
    verifyDecoder("A Message.", "A Message.");
  }

  public void testDecodingWithArgs() {
    verifyDecoder("A foo B bar C.", "A ` B ` C.", "`foo`bar");
  }

  public void testDecodingWithExcessSlots() {
    verifyDecoder("A foo B bar C - D -.", "A ` B ` C ` D `.", "`foo`bar");
  }

  public void testDecodingWithExcessArgs() {
    verifyDecoder("A foo B bar C.baz-bam-", "A ` B ` C.", "`foo`bar`baz`bam");
  }

  public void testTrailingArg() {
    verifyDecoder("foo bar", "foo `", "`bar");
  }

  public void testEmptyArgs() {
    verifyDecoder("foo bar", "foo `bar``", "```");
  }

  public void testNullDecoder() {
    ReplacedStringsDecoder nullDecoder = ReplacedStringsDecoder.NULL_DECODER;
    assertEquals("foo", nullDecoder.decode("foo"));
  }

}
