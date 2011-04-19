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

package com.google.debugging.sourcemap;

import com.google.debugging.sourcemap.SourceMapConsumerV1;
import com.google.debugging.sourcemap.SourceMapParseException;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;

import junit.framework.TestCase;

public class SourceMapConsumerV1Test extends TestCase {

  public SourceMapConsumerV1Test() {
  }

  public SourceMapConsumerV1Test(String name) {
    super(name);
  }

  public void testGetMappingForLine() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 2 }\n");
    sb.append("[0,,,,1,,2]\n");
    sb.append("[3,,,,,,,]\n");
    sb.append("/** Begin file information. **/\n");
    sb.append("['test.js']\n");
    sb.append("['foo.js']\n");
    sb.append("/** Begin mapping definitions. **/\n");
    sb.append("['test.js', 0, 1]\n");
    sb.append("['bleg.js', 5, 8, 'hello']\n");
    sb.append("['bleg.js', 12, 78]\n");
    sb.append("['foo.js', 15, 16, 'yo!']");

    SourceMapConsumerV1 sourceMap = new SourceMapConsumerV1();
    sourceMap.parse(sb.toString());

    OriginalMapping mapping = sourceMap.getMappingForLine(1, 1);

    assertNotNull(mapping);
    assertEquals("test.js", mapping.getOriginalFile());
    assertEquals(0, mapping.getLineNumber());
    assertEquals(1, mapping.getColumnPosition());
    assertEquals("", mapping.getIdentifier());

    mapping = sourceMap.getMappingForLine(1, 6);
    assertNotNull(mapping);
    assertEquals("bleg.js", mapping.getOriginalFile());
    assertEquals(5, mapping.getLineNumber());
    assertEquals(8, mapping.getColumnPosition());
    assertEquals("hello", mapping.getIdentifier());

    mapping = sourceMap.getMappingForLine(2, 4);
    assertNotNull(mapping);
    assertEquals("foo.js", mapping.getOriginalFile());
    assertEquals(15, mapping.getLineNumber());
    assertEquals(16, mapping.getColumnPosition());
    assertEquals("yo!", mapping.getIdentifier());

    assertNull(sourceMap.getMappingForLine(Integer.MAX_VALUE, 1));
    assertNotNull(sourceMap.getMappingForLine(1, Integer.MAX_VALUE));
  }

  public void testLineEdges() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 2 }\n");
    sb.append("[100]\n");
    sb.append("[200]\n");
    sb.append("/** Begin file information. **/\n");
    sb.append("['test.js']\n");
    sb.append("['foo.js']\n");
    sb.append("/** Begin mapping definitions. **/\n");
    for (int i = 0; i <= 200; i++) {
      sb.append("['foo.js', ").append(i).append(", 1]\n");
    }

    SourceMapConsumerV1 sourceMap = new SourceMapConsumerV1();
    sourceMap.parse(sb.toString());

    OriginalMapping mapping = sourceMap.getMappingForLine(-1, 1);
    assertNull(mapping);

    mapping = sourceMap.getMappingForLine(0, 1);
    assertNull(mapping);

    mapping = sourceMap.getMappingForLine(1, 1);
    assertEquals(100, mapping.getLineNumber());

    mapping = sourceMap.getMappingForLine(2, 1);
    assertEquals(200, mapping.getLineNumber());

    mapping = sourceMap.getMappingForLine(3, 1);
    assertNull(mapping);
  }

  public void testColumnEdges() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 1 }\n");
    sb.append("[100, 101]\n");
    sb.append("/** Begin file information. **/\n");
    sb.append("\n");
    sb.append("/** Begin mapping definitions. **/\n");
    for (int i = 0; i <= 200; i++) {
      sb.append("['foo.js', ").append(i).append(", 1]\n");
    }

    SourceMapConsumerV1 sourceMap = new SourceMapConsumerV1();
    sourceMap.parse(sb.toString());

    OriginalMapping mapping = sourceMap.getMappingForLine(1, -1);
    assertNull(mapping);

    mapping = sourceMap.getMappingForLine(1, 0);
    assertNull(mapping);

    mapping = sourceMap.getMappingForLine(1, 1);
    assertEquals(100, mapping.getLineNumber());

    mapping = sourceMap.getMappingForLine(1, 2);
    assertEquals(101, mapping.getLineNumber());

    // Columns beyond the end of the line are treated the same
    // as the last column.
    mapping = sourceMap.getMappingForLine(1, 3);
    assertEquals(101, mapping.getLineNumber());
  }

  public void testNegativeOneInLineMap() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 1 }\n");
    sb.append("[200,-1,199,-1]\n");
    sb.append("/** Begin file information. **/\n");
    sb.append("\n");
    sb.append("/** Begin mapping definitions. **/\n");
    for (int i = 0; i <= 200; i++) {
      sb.append("['foo.js', 1, ").append(i).append("]\n");
    }

    SourceMapConsumerV1 sourceMap = new SourceMapConsumerV1();
    sourceMap.parse(sb.toString());

    OriginalMapping mapping = sourceMap.getMappingForLine(1, 2);
    assertNull(mapping);

    mapping = sourceMap.getMappingForLine(1, 4);
    assertNull(mapping);
  }

  public void testSimpleParse() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 1 }\n");
    sb.append("[0,,,,1,2]\n");
    sb.append("/** Begin file information. **/\n");
    sb.append("['test.js']\n");
    sb.append("/** Begin mapping definitions. **/\n");
    sb.append("['test.js', 0, 1]\n");
    sb.append("['test.js', 0, 1, 'hello']\n");
    sb.append("['test.js', 0, 1]");

    SourceMapConsumerV1 sourceMap = new SourceMapConsumerV1();
    sourceMap.parse(sb.toString());
  }

  public void testBlankLine() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 1 }\n");
    sb.append("\n");
    sb.append("/** Begin file information. **/\n");
    sb.append("\n");
    sb.append("/** Begin mapping definitions. **/\n");

    SourceMapConsumerV1 sourceMap = new SourceMapConsumerV1();
    sourceMap.parse(sb.toString());
  }

  public void testCountFailure() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ counter : 1 }\n");

    assertException("Missing 'count'", sb);
  }

  public void testInvalidCountFailure() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 0 }\n");

    assertException("Count must be >= 1", sb);
  }

  public void testInvalidJSONFailure() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 2 }\n");
    sb.append("[0,,,,2\n");

    assertExceptionStartsWith("JSON parse exception: org.json.JSONException: " +
                    "Expected a ',' or ']' at ", sb);
  }

  public void testInvalidHeaderFailure() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 1 }\n");
    sb.append("[0,,,1]\n");
    sb.append("[3,,,4]\n");

    assertException(
        "Expected /** Begin file information. **/ got [3,,,4]", sb);
  }

  public void testInvalidPostHeaderToken() throws Exception {
     StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 1 }\n");
    sb.append("[0,,,1]\n");
    sb.append("/** Begin file information. **/f\n");

    assertException("Expected /** Begin file information. **/"
        + " got /** Begin file information. **/f", sb);
  }

  public void testInvalidMappingArrayFailure() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 1 }\n");
    sb.append("[0,,,,1,2]\n");
    sb.append("/** Begin file information. **/\n");
    sb.append("['test.js']\n");
    sb.append("/** Begin mapping definitions. **/\n");
    sb.append("['test.js', 0]\n");

    assertException("Invalid mapping array", sb);
  }

  public void testMultipleLineFragments() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 1 }\n");
    sb.append("[0,1,2,3,260,261,262]\n");
    sb.append("/** Begin file information. **/\n");
    sb.append("\n");
    sb.append("/** Begin mapping definitions. **/\n");
    for (int i = 0; i < 262; i++) {
      sb.append("['frog/test" + i + ".js', " + i + ", 1]\n");
    }
    sb.append("['frog/testigloo.js', 500, 1]");

    SourceMapConsumerV1 sourceMap = new SourceMapConsumerV1();
    sourceMap.parse(sb.toString());

    OriginalMapping mapping = sourceMap.getMappingForLine(1, 1);

    assertNotNull(mapping);
    assertEquals("frog/test0.js", mapping.getOriginalFile());
    assertEquals(0, mapping.getLineNumber());
    assertEquals(1, mapping.getColumnPosition());
    assertEquals("", mapping.getIdentifier());

    mapping = sourceMap.getMappingForLine(1, 6);
    assertNotNull(mapping);
    assertEquals("frog/test261.js", mapping.getOriginalFile());
    assertEquals(261, mapping.getLineNumber());
    assertEquals(1, mapping.getColumnPosition());
  }

  public void testMultipleMappingFragments() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("/** Begin line maps. **/{ count : 1 }\n");
    sb.append("[0,1,2,3,260,261,262,1023]\n");
    sb.append("/** Begin file information. **/\n");
    sb.append("\n");
    sb.append("/** Begin mapping definitions. **/\n");
    for (int i = 0; i < 2000; i++) {
      sb.append("['frog/test" + (i / 100) + ".js', " + i + ", 1]\n");
    }
    sb.append("['frog/testigloo.js', 500, 1]");

    SourceMapConsumerV1 sourceMap = new SourceMapConsumerV1();
    sourceMap.parse(sb.toString());

    OriginalMapping mapping = sourceMap.getMappingForLine(1, 1);

    assertNotNull(mapping);
    assertEquals("frog/test0.js", mapping.getOriginalFile());
    assertEquals(0, mapping.getLineNumber());
    assertEquals(1, mapping.getColumnPosition());
    assertEquals("", mapping.getIdentifier());

    mapping = sourceMap.getMappingForLine(1, 6);
    assertNotNull(mapping);
    assertEquals("frog/test2.js", mapping.getOriginalFile());
    assertEquals(261, mapping.getLineNumber());
    assertEquals(1, mapping.getColumnPosition());

    mapping = sourceMap.getMappingForLine(1, 8);
    assertNotNull(mapping);
    assertEquals("frog/test10.js", mapping.getOriginalFile());
    assertEquals(1023, mapping.getLineNumber());
    assertEquals(1, mapping.getColumnPosition());
  }

  private void assertException(String exception, StringBuilder sb) {
    boolean exceptionRaised = false;

    try {
      SourceMapConsumerV1 sourceMap = new SourceMapConsumerV1();
      sourceMap.parse(sb.toString());

    } catch (SourceMapParseException pe) {
      assertEquals(exception, pe.getMessage());
      exceptionRaised = true;
    }

    assertTrue(exceptionRaised);
  }

  private void assertExceptionStartsWith(String exception, StringBuilder sb) {
    boolean exceptionRaised = false;

    try {
      SourceMapConsumerV1 sourceMap = new SourceMapConsumerV1();
      sourceMap.parse(sb.toString());

    } catch (SourceMapParseException pe) {
      assertTrue(
        "expected <" + exception +"> but was <"+ pe.getMessage() +">",
        pe.getMessage().startsWith(exception));
      exceptionRaised = true;
    }

    assertTrue(exceptionRaised);
  }

}
