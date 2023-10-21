/*
 * Copyright 2014 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.deps;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for ClosureBundler */
@RunWith(JUnit4.class)
public final class SourceCodeEscapersTest {

  @Test
  public void testGoogModuleCode() throws IOException {
    String input = "goog.module('Foo');\nclass Foo {}";
    String escaped = SourceCodeEscapers.javascriptEscaper().escape(input);
    assertThat(escaped).isEqualTo("goog.module(\\x27Foo\\x27);\\nclass Foo {}");
  }

  @Test
  public void testExplicitCharacterEscaping() throws IOException {
    char[] input = {'\'', '"', '<', '=', '>', '&', '\b', '\t', '\n', '\f', '\r', '\\'};
    String escaped = SourceCodeEscapers.javascriptEscaper().escape(new String(input));
    assertThat(escaped).isEqualTo("\\x27\\x22\\x3c\\x3d\\x3e\\x26\\b\\t\\n\\f\\r\\\\");
  }

  @Test
  public void lowUnicodeValueEscaping() throws IOException {
    char[] input = {0x00, 0x10, 0x1F, 0x20, 0x7E, 0x7F, 0xC0, 0xFF};
    String escaped = SourceCodeEscapers.javascriptEscaper().escape(new String(input));
    assertThat(escaped).isEqualTo("\\x00\\x10\\x1f ~\\x7f\\xc0\\xff");
  }

  @Test
  public void highUnicodeValueEscaping() throws IOException {
    char[] input = {0x100, 0xF00, 0xFFFF};
    String escaped = SourceCodeEscapers.javascriptEscaper().escape(new String(input));
    assertThat(escaped).isEqualTo("\\u0100\\u0f00\\uffff");
  }
}
