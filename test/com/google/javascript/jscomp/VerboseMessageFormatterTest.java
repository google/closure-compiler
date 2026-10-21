/*
 * Copyright 2026 The Closure Compiler Authors.
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

import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class VerboseMessageFormatterTest {
  private static final DiagnosticType FOO_TYPE =
      DiagnosticType.error("TEST_FOO", "error description here");

  @Test
  public void testFormatError_validLineNumber() {
    VerboseMessageFormatter formatter = new VerboseMessageFormatter(emptySourceProvider());
    JSError error = JSError.make("test.js", 10, 5, FOO_TYPE);

    String formatted = formatter.formatError(error);

    assertThat(formatted).contains("line 10");
    assertThat(formatted).doesNotContain("(unknown line)");
    assertThat(formatted).isEqualTo("ERROR: error description here at test.js line 10 .");
  }

  @Test
  public void testFormatError_unknownLineNumber() {
    VerboseMessageFormatter formatter = new VerboseMessageFormatter(emptySourceProvider());
    JSError error = JSError.make("test.js", -1, -1, FOO_TYPE);

    String formatted = formatter.formatError(error);

    assertThat(formatted).contains("line (unknown line)");
    assertThat(formatted).doesNotContain("-1");
    assertThat(formatted)
        .isEqualTo("ERROR: error description here at test.js line (unknown line) .");
  }

  @Test
  public void testFormatError_unknownSourceName() {
    VerboseMessageFormatter formatter = new VerboseMessageFormatter(emptySourceProvider());
    JSError error = JSError.make(null, 10, 5, FOO_TYPE);

    String formatted = formatter.formatError(error);

    assertThat(formatted).contains("(unknown source)");
    assertThat(formatted).contains("line 10");
    assertThat(formatted).isEqualTo("ERROR: error description here at (unknown source) line 10 .");
  }

  @Test
  public void testFormatError_unknownSourceAndLineNumber() {
    VerboseMessageFormatter formatter = new VerboseMessageFormatter(emptySourceProvider());
    JSError error = JSError.make(null, -1, -1, FOO_TYPE);

    String formatted = formatter.formatError(error);

    assertThat(formatted).contains("(unknown source)");
    assertThat(formatted).contains("line (unknown line)");
    assertThat(formatted)
        .isEqualTo("ERROR: error description here at (unknown source) line (unknown line) .");
  }

  @Test
  public void testFormatError_withSourceRegion() {
    SourceExcerptProvider provider =
        new SourceExcerptProvider() {
          @Override
          public @Nullable String getSourceLine(String sourceName, int lineNumber) {
            return null;
          }

          @Override
          public @Nullable Region getSourceLines(String sourceName, int lineNumber, int length) {
            return null;
          }

          @Override
          public @Nullable Region getSourceRegion(String sourceName, int lineNumber) {
            if (Objects.equals(sourceName, "test.js") && lineNumber == 10) {
              return new SimpleRegion(10, 10, "const x = 1;");
            }
            return null;
          }

          @Override
          public @Nullable OriginalMapping getSourceMapping(
              String sourceName, int lineNumber, int columnNumber) {
            return null;
          }
        };

    VerboseMessageFormatter formatter = new VerboseMessageFormatter(provider);
    JSError error = JSError.make("test.js", 10, 0, FOO_TYPE);

    String formatted = formatter.formatError(error);

    assertThat(formatted)
        .isEqualTo("ERROR: error description here at test.js line 10 :\n\nconst x = 1;");
  }

  @Test
  public void testFormatWarning_validLineNumber() {
    VerboseMessageFormatter formatter = new VerboseMessageFormatter(emptySourceProvider());
    DiagnosticType warningType = DiagnosticType.warning("TEST_WARN", "warning description here");
    JSError warning = JSError.make("test.js", 42, 0, warningType);

    String formatted = formatter.formatWarning(warning);

    assertThat(formatted).startsWith("WARNING: ");
    assertThat(formatted).contains("line 42");
    assertThat(formatted).isEqualTo("WARNING: warning description here at test.js line 42 .");
  }

  private static SourceExcerptProvider emptySourceProvider() {
    return new SourceExcerptProvider() {
      @Override
      public @Nullable String getSourceLine(String sourceName, int lineNumber) {
        return null;
      }

      @Override
      public @Nullable Region getSourceLines(String sourceName, int lineNumber, int length) {
        return null;
      }

      @Override
      public @Nullable Region getSourceRegion(String sourceName, int lineNumber) {
        return null;
      }

      @Override
      public @Nullable OriginalMapping getSourceMapping(
          String sourceName, int lineNumber, int columnNumber) {
        return null;
      }
    };
  }
}
