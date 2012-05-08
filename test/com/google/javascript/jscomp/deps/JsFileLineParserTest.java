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

import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.PrintStreamErrorManager;

import junit.framework.TestCase;

import java.io.StringReader;

/**
 * Tests for {@link JsFileLineParser}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class JsFileLineParserTest extends TestCase {

  TestParser parser;
  private ErrorManager errorManager;

  @Override
  public void setUp() {
    errorManager = new PrintStreamErrorManager(System.err);
    parser = new TestParser(errorManager);
  }

  public void testSingleLine1() {
    assertStrip("2", "// 1\n2");
  }

  public void testSingleLine2() {
    assertStrip("2 ", "// 1\n2 // 3 // 4 \n");
  }

  public void testMultiLine1() {
    assertStrip("1", "/* hi */\n1");
  }

  public void testMultiLine2() {
    assertStrip("123", "1/* hi */2\n3");
  }

  public void testMultiLine3() {
    assertStrip("14", "1/* hi 2\n3*/4");
  }

  public void testMultiLine4() {
    assertStrip("15", "1/* hi x\ny\nz*/5");
  }

  public void testMultiLine5() {
    assertStrip("1234", "1/* hi */2/**/3/*\n/** bye */4");
  }

  public void testMultiLine6() {
    assertStrip("12", "1/*** hi *** 3 **/2");
  }

  public void testMixedLine1() {
    assertStrip("14", "1// /** 2 **/ 3\n4");
  }

  public void testMixedLine2() {
    assertStrip("1 34", "1/** // 2 **/ 3\n4");
  }

  private void assertStrip(String expected, String input) {
    parser.doParse("file", new StringReader(input));
    assertEquals(expected, parser.toString());
  }

  private static class TestParser extends JsFileLineParser {
    StringBuilder sb = new StringBuilder();

    TestParser(ErrorManager errorManager) {
      super(errorManager);
    }

    @Override
    boolean parseLine(String line) {
      sb.append(line);
      return true;
    }

    @Override
    public String toString() {
      return sb.toString();
    }
  }
}
