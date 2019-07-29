/*
 * Copyright 2019 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.deps.JsFileFullParser.FileInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link JsFileFullParser}.
 *
 * <p>TODO(tjgq): Add more tests.
 */
@RunWith(JUnit4.class)
public final class JsFileFullParserTest {
  @Test
  public void testProvidesRequires() {
    String contents =
        lines(
            "goog.provide('providedSymbol');",
            "goog.require('stronglyRequiredSymbol');",
            "goog.requireType('weaklyRequiredSymbol');");

    FileInfo info = JsFileFullParser.parse(contents, "file.js", null);

    assertThat(info.provides).containsExactly("providedSymbol");
    assertThat(info.requires).containsExactly("stronglyRequiredSymbol");
    assertThat(info.typeRequires).containsExactly("weaklyRequiredSymbol");
  }

  private static String lines(String... lines) {
    return Joiner.on('\n').join(lines);
  }
}
