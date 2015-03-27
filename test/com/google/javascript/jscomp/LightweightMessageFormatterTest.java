/*
 * Copyright 2007 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.LightweightMessageFormatter.LineNumberingFormatter;

import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

public final class LightweightMessageFormatterTest extends TestCase {
  private static final DiagnosticType FOO_TYPE =
      DiagnosticType.error("TEST_FOO", "error description here");
  private static final String ORIGINAL_SOURCE_FILE = "original/source.html";
  private static final OriginalMapping ORIGINAL_SOURCE = OriginalMapping.newBuilder()
      .setOriginalFile(ORIGINAL_SOURCE_FILE)
      .setLineNumber(3)
      .setColumnPosition(15)
      .build();

  public void testNull() throws Exception {
    assertNull(format(null));
  }

  public void testOneLineRegion() throws Exception {
    assertEquals("  5| hello world", format(region(5, 5, "hello world")));
  }

  public void testTwoLineRegion() throws Exception {
    assertEquals("  5| hello world\n" +
            "  6| foo bar", format(region(5, 6, "hello world\nfoo bar")));
  }

  public void testThreeLineRegionAcrossNumberRange() throws Exception {
    String region = format(region(9, 11, "hello world\nfoo bar\nanother one"));
    assertEquals("   9| hello world\n" +
            "  10| foo bar\n" +
            "  11| another one", region);
  }

  public void testThreeLineRegionEmptyLine() throws Exception {
    String region = format(region(7, 9, "hello world\n\nanother one"));
    assertEquals("  7| hello world\n" +
            "  8| \n" +
            "  9| another one", region);
  }

  public void testOnlyOneEmptyLine() throws Exception {
    assertNull(format(region(7, 7, "")));
  }

  public void testTwoEmptyLines() throws Exception {
    assertEquals("  7| ", format(region(7, 8, "\n")));
  }

  public void testThreeLineRemoveLastEmptyLine() throws Exception {
    String region = format(region(7, 9, "hello world\nfoobar\n"));
    assertEquals("  7| hello world\n" +
            "  8| foobar", region);
  }

  public void testFormatErrorSpaces() throws Exception {
    Node n = Node.newString("foobar", 5, 8);
    n.setSourceFileForTesting("javascript/complex.js");
    JSError error = JSError.make(n, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("    if (foobar) {");
    assertEquals("javascript/complex.js:5: ERROR - error description here\n" +
        "    if (foobar) {\n" +
        "        ^\n", formatter.formatError(error));
  }

  public void testFormatErrorTabs() throws Exception {
    Node n = Node.newString("foobar", 5, 6);
    n.setSourceFileForTesting("javascript/complex.js");
    JSError error = JSError.make(n, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("\t\tif (foobar) {");
    assertEquals("javascript/complex.js:5: ERROR - error description here\n" +
        "\t\tif (foobar) {\n" +
        "\t\t    ^\n", formatter.formatError(error));
  }

  public void testFormatErrorSpaceEndOfLine1() throws Exception {
    JSError error = JSError.make("javascript/complex.js",
        1, 10, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("assert (1;");
    assertEquals("javascript/complex.js:1: ERROR - error description here\n" +
        "assert (1;\n" +
        "          ^\n", formatter.formatError(error));
  }

  public void testFormatErrorSpaceEndOfLine2() throws Exception {
    JSError error = JSError.make("javascript/complex.js",
        6, 7, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("if (foo");
    assertEquals("javascript/complex.js:6: ERROR - error description here\n" +
        "if (foo\n" +
        "       ^\n", formatter.formatError(error));
  }

  public void testFormatErrorOriginalSource() throws Exception {
    Node n = Node.newString("foobar", 5, 8);
    n.setSourceFileForTesting("javascript/complex.js");
    JSError error = JSError.make(n, FOO_TYPE);
    LightweightMessageFormatter formatter =
        formatter("    if (foobar) {", "<div ng-show='(foo'>");
    assertEquals(
        "javascript/complex.js:5: \n" +
        "Originally at:\n" +
        "original/source.html:3: ERROR - error description here\n" +
        "<div ng-show='(foo'>\n" +
        "               ^\n",
        formatter.formatError(error));
  }

  private LightweightMessageFormatter formatter(String string) {
    return formatter(string, null);
  }

  private LightweightMessageFormatter formatter(String string,
                                                String originalSource) {
    return new LightweightMessageFormatter(source(string, originalSource));
  }

  private SourceExcerptProvider source(final String source,
                                       final String originalSource) {
    return new SourceExcerptProvider() {
      @Override
      public String getSourceLine(String sourceName, int lineNumber) {
        if (sourceName.equals(ORIGINAL_SOURCE_FILE)) {
          return originalSource;
        }
        return source;
      }
      @Override
      public Region getSourceRegion(String sourceName, int lineNumber) {
        throw new UnsupportedOperationException();
      }
      @Override
      public OriginalMapping getSourceMapping(String sourceName,
          int lineNumber, int columnNumber) {
        if (originalSource != null) {
          return ORIGINAL_SOURCE;
        }
        return null;
      }
    };
  }

  private String format(Region region) {
    return new LineNumberingFormatter().formatRegion(region);
  }

  private Region region(final int startLine, final int endLine,
      final String source) {
    return new SimpleRegion(startLine, endLine, source);
  }
}
