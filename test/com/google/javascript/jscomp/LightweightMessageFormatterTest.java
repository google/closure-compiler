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

import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

public class LightweightMessageFormatterTest extends TestCase {
  private static final DiagnosticType FOO_TYPE =
      DiagnosticType.error("TEST_FOO", "error description here");

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
    JSError error = JSError.make("javascript/complex.js",
        Node.newString("foobar", 5, 8), FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("    if (foobar) {");
    assertEquals("javascript/complex.js:5: ERROR - error description here\n" +
        "    if (foobar) {\n" +
        "        ^\n", formatter.formatError(error));
  }

  public void testFormatErrorTabs() throws Exception {
    JSError error = JSError.make("javascript/complex.js",
        Node.newString("foobar", 5, 6), FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("\t\tif (foobar) {");
    assertEquals("javascript/complex.js:5: ERROR - error description here\n" +
        "\t\tif (foobar) {\n" +
        "\t\t    ^\n", formatter.formatError(error));
  }

  private LightweightMessageFormatter formatter(String string) {
    return new LightweightMessageFormatter(source(string));
  }

  private SourceExcerptProvider source(final String source) {
    return new SourceExcerptProvider() {
      public String getSourceLine(String sourceName, int lineNumber) {
        return source;
      }
      public Region getSourceRegion(String sourceName, int lineNumber) {
        throw new UnsupportedOperationException();
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
