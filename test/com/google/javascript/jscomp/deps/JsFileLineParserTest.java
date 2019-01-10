/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.PrintStreamErrorManager;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link JsFileLineParser}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class JsFileLineParserTest {

  @Test
  public void testSingleLine1() {
    assertStrip("2", "// 1\n2");
  }

  @Test
  public void testSingleLine2() {
    assertStrip("2 ", "// 1\n2 // 3 // 4 \n");
  }

  @Test
  public void testMultiLine1() {
    assertStrip("1", "/* hi */\n1");
  }

  @Test
  public void testMultiLine2() {
    assertStrip("123", "1/* hi */2\n3");
  }

  @Test
  public void testMultiLine3() {
    assertStrip("14", "1/* hi 2\n3*/4");
  }

  @Test
  public void testMultiLine4() {
    assertStrip("15", "1/* hi x\ny\nz*/5");
  }

  @Test
  public void testMultiLine5() {
    assertStrip("1234", "1/* hi */2/**/3/*\n/** bye */4");
  }

  @Test
  public void testMultiLine6() {
    assertStrip("12", "1/*** hi *** 3 **/2");
  }

  @Test
  public void testMixedLine1() {
    assertStrip("14", "1// /** 2 **/ 3\n4");
  }

  @Test
  public void testMixedLine2() {
    assertStrip("1 34", "1/** // 2 **/ 3\n4");
  }

  @Test
  public void testBlockComment() {
    assertBlocks("/** one line */", "/** one line */");
  }

  @Test
  public void testInlineBlockComment() {
    assertBlocks("/** one line */", "var x; /** one line */");
    assertBlocks("/** one line */", "/** one line */ var y;");
    assertBlocks("/** one line */", "var x; /** one line */ var y;");
  }

  @Test
  public void testMultipleBlockComments() {
    assertBlocks("/** first *//** * second */", "/** first */\n/**\n * second\n */");
  }

  private void assertStrip(String expected, String input) {
    ErrorManager errorManager = new PrintStreamErrorManager(System.err);
    TestParser parser = new TestParser(errorManager);
    parser.doParse("file", new StringReader(input));
    assertThat(parser.toString()).isEqualTo(expected);
  }

  private void assertBlocks(String expected, String input) {
    ErrorManager errorManager = new PrintStreamErrorManager(System.err);
    TestParser parser = new TestParser(errorManager);
    parser.doParse("file", new StringReader(input));
    assertThat(parser.comments.toString()).isEqualTo(expected);
  }

  private static class TestParser extends JsFileLineParser {
    StringBuilder sb = new StringBuilder();
    StringBuilder comments = new StringBuilder();

    TestParser(ErrorManager errorManager) {
      super(errorManager);
    }

    @Override
    boolean parseLine(String line) {
      sb.append(line);
      return true;
    }

    @Override
    boolean parseBlockCommentLine(String line) {
      comments.append(line);
      return true;
    }

    @Override
    public String toString() {
      return sb.toString();
    }
  }
}
