/*
 * Copyright 2009 Google Inc.
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

import com.google.common.collect.Maps;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Map;

/**
 * Tests for {@link SourceMap}.
 *
*
 */
public class SourceMapTest extends TestCase {
  private static final JSSourceFile[] EXTERNS = {
      JSSourceFile.fromCode("externs", "")
  };

  public void testBasicMapping() throws Exception {
    compileAndCheck("function __BASIC__() { }");
  }

  public void testLiteralMappings() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) { " +
                    "var __VAR__ = '__STR__'; }");
  }

  public void testMultilineMapping() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) {\n" +
                    "var __VAR__ = '__STR__';\n" +
                    "var __ANO__ = \"__STR2__\";\n" +
                    "}");
  }

  public void testMultiFunctionMapping() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) {\n" +
                    "var __VAR__ = '__STR__';\n" +
                    "var __ANO__ = \"__STR2__\";\n" +
                    "}\n\n" +

                    "function __BASIC2__(__PARAM3__, __PARAM4__) {\n" +
                    "var __VAR2__ = '__STR2__';\n" +
                    "var __ANO2__ = \"__STR3__\";\n" +
                    "}\n\n");
  }

  public void testGoldenOutput1() throws Exception {
    checkSourceMap("function(foo, bar) { foo = foo + bar + 2; return foo; }",

                   "/** Begin line maps. **/{ \"file\" : \"testMap\", " +
                   "\"count\": 1 }\n" +

                   "[0,0,0,0,0,0,0,0,2,4,4,4,4,5,5,5,5,3,8,8,8,8,9,9,9,9," +
                   "10,10,10,10,11,11,12,12,12,12,12,12,13,13,13,13,13,6]\n" +

                   "/** Begin file information. **/\n" +
                   "[\"testcode\"]\n" +
                   "/** Begin mapping definitions. **/\n" +
                   "[\"testcode\",1,0]\n" +
                   "[\"testcode\",1,8]\n" +
                   "[\"testcode\",1,8,\"\"]\n" +
                   "[\"testcode\",1,8]\n" +
                   "[\"testcode\",1,9,\"foo\"]\n" +
                   "[\"testcode\",1,14,\"bar\"]\n" +
                   "[\"testcode\",1,19]\n" +
                   "[\"testcode\",1,21]\n" +
                   "[\"testcode\",1,21,\"foo\"]\n" +
                   "[\"testcode\",1,27,\"foo\"]\n" +
                   "[\"testcode\",1,33,\"bar\"]\n" +
                   "[\"testcode\",1,39]\n" +
                   "[\"testcode\",1,42]\n" +
                   "[\"testcode\",1,49,\"foo\"]\n");
  }

  public void testGoldenOutput2() throws Exception {
    checkSourceMap("function(foo, bar) {\r\n\n\n\nfoo = foo + bar + foo;" +
                   "\nreturn foo;\n}",

                   "/** Begin line maps. **/{ \"file\" : \"testMap\", " +
                   "\"count\": 1 }\n" +

                   "[0,0,0,0,0,0,0,0,2,4,4,4,4,5,5,5,5,3,8,8,8,8,9,9,9," +
                   "9,10,10,10,10,11,11,11,11,12,12,12,12,12,12,13,13,13," +
                   "13,13,6]\n" +

                   "/** Begin file information. **/\n" +
                   "[\"testcode\"]\n" +
                   "/** Begin mapping definitions. **/\n" +
                   "[\"testcode\",1,0]\n" +
                   "[\"testcode\",1,8]\n" +
                   "[\"testcode\",1,8,\"\"]\n" +
                   "[\"testcode\",1,8]\n" +
                   "[\"testcode\",1,9,\"foo\"]\n" +
                   "[\"testcode\",1,14,\"bar\"]\n" +
                   "[\"testcode\",1,19]\n" +
                   "[\"testcode\",5,0]\n" +
                   "[\"testcode\",5,0,\"foo\"]\n" +
                   "[\"testcode\",5,6,\"foo\"]\n" +
                   "[\"testcode\",5,12,\"bar\"]\n" +
                   "[\"testcode\",5,18,\"foo\"]\n" +
                   "[\"testcode\",6,0]\n" +
                   "[\"testcode\",6,7,\"foo\"]\n");
  }

  /**
   * Creates a source map for the given JS code and asserts it is
   * equal to the expected golden map.
   */
  private void checkSourceMap(String js, String expectedMap)
      throws IOException {
    RunResult result = compile(js);
    StringBuilder sb = new StringBuilder();
    result.sourceMap.appendTo(sb, "testMap");

    assertEquals(expectedMap, sb.toString());
  }

  private static class RunResult {
    String generatedSource;
    SourceMap sourceMap;
  }

  private static class Token {
    String tokenName;
    Position position;
  }

  /**
   * Finds the all the __XX__ tokens in the given Javascript
   * string.
   */
  private Map<String, Token> findTokens(String js) {
    Map<String, Token> tokens = Maps.newHashMap();

    int currentLine = 0;
    int positionOffset = 0;

    for (int i = 0; i < js.length(); ++i) {
      char current = js.charAt(i);

      if (current == '\n') {
        positionOffset = i + 1;
        currentLine++;
        continue;
      }

      if (current == '_' && (i < js.length() - 5)) {
        // Check for the _ token.
        if (js.charAt(i + 1) != '_') {
          continue;
        }

        // Loop until we have another _ token.
        String tokenName = "";

        int j = i + 2;
        for (; j < js.length(); ++j) {
          if (js.charAt(j) == '_') {
            break;
          }

          tokenName += js.charAt(j);
        }

        if (tokenName.length() > 0) {
          Token token = new Token();
          token.tokenName = tokenName;
          int currentPosition = i - positionOffset;
          token.position = new Position(currentLine, currentPosition);
          tokens.put(tokenName, token);
        }

        i = j;
      }
    }

    return tokens;
  }

  private void compileAndCheck(String js) {
    RunResult result = compile(js);

    // Find all instances of the __XXX__ pattern in the generated
    // source code.
    Map<String, Token> originalTokens = findTokens(js);

    // Ensure that the generated instances match via the source map
    // to the original source code.
    Map<String, Token> resultTokens = findTokens(result.generatedSource);

    // Ensure the token counts match.
    assertEquals(originalTokens.size(), resultTokens.size());

    // Map the tokens from the generated source back to the
    // input source and ensure that the map is correct.
    for (Token token : resultTokens.values()) {
      SourceMap.Mapping mapping =
          result.sourceMap.getMappingFor(token.position);

      assertNotNull(mapping);

      // Find the associated token in the input source.
      Token inputToken = originalTokens.get(token.tokenName);
      assertNotNull(inputToken);

      // Ensure that the map correctly points to the token (we add 1
      // to normalize versus the Rhino line number indexing scheme).
      assertEquals(mapping.originalPosition.getLineNumber(),
                   inputToken.position.getLineNumber() + 1);

      // Ensure that if the token name does not being with an 'STR' (meaning a
      // string) it has an original name.
      if (!inputToken.tokenName.startsWith("STR")) {
        assertNotNull(mapping.originalName);
      }

      // Ensure that if the mapping has a name, it matches the token.
      if (mapping.originalName != null) {
        assertEquals(mapping.originalName, "__" + inputToken.tokenName + "__");
      }
    }
  }

  private RunResult compile(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.sourceMapOutputPath = "testcode_source_map.out";

    // Turn on IDE mode to get rid of optimizations.
    options.ideMode = true;

    JSSourceFile[] inputs = { JSSourceFile.fromCode("testcode", js) };
    Result result = compiler.compile(EXTERNS, inputs, options);

    assertTrue(result.success);
    String source = compiler.toSource();

    RunResult rr = new RunResult();
    rr.generatedSource = source;
    rr.sourceMap = result.sourceMap;
    return rr;
  }
}
