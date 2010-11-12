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

package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.SourceMap.DetailLevel;
import com.google.javascript.jscomp.SourceMap2.LineMapDecoder;
import com.google.javascript.jscomp.SourceMap2.LineMapEncoder;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link SourceMap}.
 *
 */
public class SourceMap2Test extends TestCase {
  private static final JSSourceFile[] EXTERNS = {
      JSSourceFile.fromCode("externs", "")
  };

  private DetailLevel detailLevel = SourceMap.DetailLevel.ALL;

  @Override
  public void setUp() {
    detailLevel = SourceMap.DetailLevel.ALL;
  }

  public void testBasicMapping() throws Exception {
    compileAndCheck("function __BASIC__() { }");
  }

  public void testBasicMappingGoldenOutput() throws Exception {
    // Empty source map test
    checkSourceMap("function __BASIC__() { }",

                   //"/** Source Map **/\n" +
                   "{\n" +
                   "\"version\":2,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"lineMaps\":[\"cAkBEBEB\"],\n" +
                   "\"sources\":[\"testcode\"],\n" +
                   "\"mappings\":[[0,1,9,\"__BASIC__\"],\n" +
                   "[0,1,9,\"__BASIC__\"],\n" +
                   "[0,1,18],\n" +
                   "[0,1,21],\n" +
                   "]\n" +
                   "}\n");
  }

  public void testLiteralMappings() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) { " +
                    "var __VAR__ = '__STR__'; }");
  }

  public void testLiteralMappingsGoldenOutput() throws Exception {
    // Empty source map test
    checkSourceMap("function __BASIC__(__PARAM1__, __PARAM2__) { " +
                   "var __VAR__ = '__STR__'; }",

                   //"/** Source Map **/\n" +
                   "{\n" +
                   "\"version\":2,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"lineMaps\":[\"cAkBABkBA/kCA+ADMBcBgBA9\"],\n" +
                   "\"sources\":[\"testcode\"],\n" +
                   "\"mappings\":[[0,1,9,\"__BASIC__\"],\n" +
                   "[0,1,9,\"__BASIC__\"],\n" +
                   "[0,1,18],\n" +
                   "[0,1,19,\"__PARAM1__\"],\n" +
                   "[0,1,31,\"__PARAM2__\"],\n" +
                   "[0,1,43],\n" +
                   "[0,1,45],\n" +
                   "[0,1,49,\"__VAR__\"],\n" +
                   "[0,1,59],\n" +
                   "]\n" +
                   "}\n");
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

  public void testGoldenOutput0() throws Exception {
    // Empty source map test
    checkSourceMap("",

                   "{\n" +
                   "\"version\":2,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"lineMaps\":[\"\"],\n" +
                   "\"sources\":[\"testcode\"],\n" +
                   "\"mappings\":[]\n" +
                   "}\n");
  }

  public void testGoldenOutput1() throws Exception {
    detailLevel = SourceMap.DetailLevel.ALL;

    checkSourceMap("function f(foo, bar) { foo = foo + bar + 2; return foo; }",

                   "{\n" +
                   "\"version\":2,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"lineMaps\":" +
                       "[\"cAEBABIBA/ICA+ADICA/ICA+IDA9AEYBMBA5\"],\n" +
                   "\"sources\":[\"testcode\"],\n" +
                   "\"mappings\":[[0,1,9,\"f\"],\n" +
                   "[0,1,9,\"f\"],\n" +
                   "[0,1,10],\n" +
                   "[0,1,11,\"foo\"],\n" +
                   "[0,1,16,\"bar\"],\n" +
                   "[0,1,21],\n" +
                   "[0,1,23],\n" +
                   "[0,1,23,\"foo\"],\n" +
                   "[0,1,29,\"foo\"],\n" +
                   "[0,1,35,\"bar\"],\n" +
                   "[0,1,41],\n" +
                   "[0,1,44],\n" +
                   "[0,1,51,\"foo\"],\n" +
                   "]\n" +
                   "}\n");

    detailLevel = SourceMap.DetailLevel.SYMBOLS;

    checkSourceMap("function f(foo, bar) { foo = foo + bar + 2; return foo; }",

                   "{\n" +
                   "\"version\":2,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"lineMaps\":[\"cAEBA/ICA+IDE9IEA8IFA7IGg6MHA5\"],\n" +
                   "\"sources\":[\"testcode\"],\n" +
                   "\"mappings\":[[0,1,9,\"f\"],\n" +
                   "[0,1,9,\"f\"],\n" +
                   "[0,1,11,\"foo\"],\n" +
                   "[0,1,16,\"bar\"],\n" +
                   "[0,1,23,\"foo\"],\n" +
                   "[0,1,29,\"foo\"],\n" +
                   "[0,1,35,\"bar\"],\n" +
                   "[0,1,51,\"foo\"],\n" +
                   "]\n" +
                   "}\n");
  }

  public void testGoldenOutput2() throws Exception {
    checkSourceMap("function f(foo, bar) {\r\n\n\n\nfoo = foo + bar + foo;" +
                   "\nreturn foo;\n}",

                   "{\n" +
                   "\"version\":2,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"lineMaps\":" +
                       "[\"cAEBABIBA/ICA+ADICA/ICA+IDA9IEYBMBA5\"],\n" +
                   "\"sources\":[\"testcode\"],\n" +
                   "\"mappings\":[[0,1,9,\"f\"],\n" +
                   "[0,1,9,\"f\"],\n" +
                   "[0,1,10],\n" +
                   "[0,1,11,\"foo\"],\n" +
                   "[0,1,16,\"bar\"],\n" +
                   "[0,1,21],\n" +
                   "[0,5,0],\n" +
                   "[0,5,0,\"foo\"],\n" +
                   "[0,5,6,\"foo\"],\n" +
                   "[0,5,12,\"bar\"],\n" +
                   "[0,5,18,\"foo\"],\n" +
                   "[0,6,0],\n" +
                   "[0,6,7,\"foo\"],\n" +
                   "]\n" +
                   "}\n");
  }

  public void testGoldenOutput3() throws Exception {
    checkSourceMap("c:\\myfile.js",
                   "foo;",

                   "{\n" +
                   "\"version\":2,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"lineMaps\":[\"IA\"],\n" +
                   "\"sources\":[\"c:\\myfile.js\"],\n" +
                   "\"mappings\":[[0,1,0,\"foo\"],\n" +
                   "]\n" +
                   "}\n");
  }

  public void testGoldenOutput4() throws Exception {
    checkSourceMap("c:\\myfile.js",
                   "foo;   boo;   goo;",

                   "{\n" +
                   "\"version\":2,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"lineMaps\":[\"IAMBMB\"],\n" +
                   "\"sources\":[\"c:\\myfile.js\"],\n" +
                   "\"mappings\":[[0,1,0,\"foo\"],\n" +
                   "[0,1,7,\"boo\"],\n" +
                   "[0,1,14,\"goo\"],\n" +
                   "]\n" +
                   "}\n");
  }

  public void testGoldenOutput5() throws Exception {
    detailLevel = SourceMap.DetailLevel.ALL;

    checkSourceMap("c:\\myfile.js",
                   "/** @preserve\n" +
                   " * this is a test.\n" +
                   " */\n" +
                   "var foo=a + 'this is a really long line that will force the"
                   + " mapping to span multiple lines 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + "' + c + d + e;",

                   "{\n" +
                   "\"version\":2,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":6,\n" +
                   "\"lineMaps\":[\"\",\n" +
                   "\"\",\n" +
                   "\"\",\n" +
                   "\"\",\n" +
                   "\"MAMBABA/!!AUSC\",\n" +
                   "\"AEA9AEA8AF\"],\n" +
                   "\"sources\":[\"c:\\myfile.js\"],\n" +
                   "\"mappings\":[[0,4,0],\n" +
                   "[0,4,4,\"foo\"],\n" +
                   "[0,4,8,\"a\"],\n" +
                   "[0,4,12],\n" +
                   "[0,4,1314,\"c\"],\n" +
                   "[0,4,1318,\"d\"],\n" +
                   "[0,4,1322,\"e\"],\n" +
                   "]\n" +
                   "}\n");

    detailLevel = SourceMap.DetailLevel.SYMBOLS;

    checkSourceMap("c:\\myfile.js",
        "/** @preserve\n" +
        " * this is a test.\n" +
        " */\n" +
        "var foo=a + 'this is a really long line that will force the"
        + " mapping to span multiple lines 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + "' + c + d + e;",

        "{\n" +
        "\"version\":2,\n" +
        "\"file\":\"testcode\",\n" +
        "\"lineCount\":6,\n" +
        "\"lineMaps\":[\"\",\n" +
        "\"\",\n" +
        "\"\",\n" +
        "\"\",\n" +
        "\"M/MBAB\",\n" +
        "\"ACA+ADA9AE\"],\n" +
        "\"sources\":[\"c:\\myfile.js\"],\n" +
        "\"mappings\":[[0,4,4,\"foo\"],\n" +
        "[0,4,8,\"a\"],\n" +
        "[0,4,1314,\"c\"],\n" +
        "[0,4,1318,\"d\"],\n" +
        "[0,4,1322,\"e\"],\n" +
        "]\n" +
        "}\n");
  }

  public void testBasicDeterminism() throws Exception {
    RunResult result1 = compile("file1", "foo;", "file2", "bar;");
    RunResult result2 = compile("file2", "foo;", "file1", "bar;");

    String map1 = getSourceMap(result1);
    String map2 = getSourceMap(result2);

    // Assert that the files section of the maps are the same. The actual
    // entries will differ, so we cannot do a simple full comparison.

    // Line 5 has the file information.
    String files1 = map1.split("\n")[4];
    String files2 = map2.split("\n")[4];

    assertEquals(files1, files2);
  }

  private int getRelativeId(int id, int lastId) {
    int length = LineMapEncoder.getRelativeMappingIdLength(id, lastId);
    int result = LineMapEncoder.getRelativeMappingId(id, length, lastId);
    int inverse = LineMapDecoder.getIdFromRelativeId(result, length, lastId);
    assertEquals(id, inverse);
    return result;
  }

  public void testEncodingRelativeId() {
    assertEquals(0, getRelativeId(0, 0));
    assertEquals(64 + (-1), getRelativeId(-1, 0));
    assertEquals(64 + (-32), getRelativeId(0, 32));
    assertEquals(31, getRelativeId(31, 0));
    assertEquals(4096 + (-33), getRelativeId(0, 33));
    assertEquals(32, getRelativeId(32, 0));
  }

  public void testEncodingIdLength() {
    assertEquals(1, LineMapEncoder.getRelativeMappingIdLength(0, 0));
    assertEquals(1, LineMapEncoder.getRelativeMappingIdLength(-1, 0));
    assertEquals(1, LineMapEncoder.getRelativeMappingIdLength(0, 32));
    assertEquals(1, LineMapEncoder.getRelativeMappingIdLength(31, 0));
    assertEquals(2, LineMapEncoder.getRelativeMappingIdLength(0, 33));
    assertEquals(2, LineMapEncoder.getRelativeMappingIdLength(32, 0));

    assertEquals(2, LineMapEncoder.getRelativeMappingIdLength(2047, 0));
    assertEquals(3, LineMapEncoder.getRelativeMappingIdLength(2048, 0));
    assertEquals(2, LineMapEncoder.getRelativeMappingIdLength(0, 2048));
    assertEquals(3, LineMapEncoder.getRelativeMappingIdLength(0, 2049));
  }

  private String getEntry(int id, int lastId, int reps) throws IOException {
    StringBuilder sb = new StringBuilder();
    LineMapEncoder.encodeEntry(sb, id, lastId, reps);
    return sb.toString();
  }

  public void testEncoding() throws IOException {
    assertEquals("AA", getEntry(0, 0, 1));
    assertEquals("EA", getEntry(0, 0, 2));
    assertEquals("8A", getEntry(0, 0, 16));
    assertEquals("!AQA", getEntry(0, 0, 17));
    assertEquals("!ARA", getEntry(0, 0, 18));
    assertEquals("!A+A", getEntry(0, 0, 63));
    assertEquals("!A/A", getEntry(0, 0, 64));
    assertEquals("!!ABAA", getEntry(0, 0, 65));
    assertEquals("!!A//A", getEntry(0, 0, 4096));
    assertEquals("!!!ABAAA", getEntry(0, 0, 4097));

    assertEquals("Af", getEntry(31, 0, 1));
    assertEquals("BAg", getEntry(32, 0, 1));
    assertEquals("AB", getEntry(32, 31, 1));

    assertEquals("!AQf", getEntry(31, 0, 17));
    assertEquals("!BQAg", getEntry(32, 0, 17));
    assertEquals("!AQB", getEntry(32, 31, 17));

    assertEquals("!A/B", getEntry(32, 31, 64));
    assertEquals("!!ABAB", getEntry(32, 31, 65));
  }

  /**
   * Creates a source map for the given JS code and asserts it is
   * equal to the expected golden map.
   */
  private void checkSourceMap(String js, String expectedMap)
      throws IOException {
    checkSourceMap("testcode", js, expectedMap);
  }

  private String getSourceMap(RunResult result) throws IOException {
    StringBuilder sb = new StringBuilder();
    result.sourceMap.appendTo(sb, "testcode");
    return sb.toString();
  }

  private void checkSourceMap(String fileName, String js, String expectedMap)
      throws IOException {
    RunResult result = compile(js, fileName);
    assertEquals(expectedMap, result.sourceMapFileContent);
    assertEquals(result.sourceMapFileContent, getSourceMap(result));
  }

  private static class RunResult {
    String generatedSource;
    SourceMap sourceMap;
    public String sourceMapFileContent;
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
    Map<String, Token> tokens = Maps.newLinkedHashMap();

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

    // Find all instances of the __XXX__ pattern in the original
    // source code.
    Map<String, Token> originalTokens = findTokens(js);

    // Find all instances of the __XXX__ pattern in the generated
    // source code.
    Map<String, Token> resultTokens = findTokens(result.generatedSource);

    // Ensure that the generated instances match via the source map
    // to the original source code.

    // Ensure the token counts match.
    assertEquals(originalTokens.size(), resultTokens.size());

    SourceMapReader reader = new SourceMapReader();
    try {
      reader.parse(result.sourceMapFileContent);
    } catch (SourceMapParseException e) {
      throw new RuntimeException("unexpected exception", e);
    } catch (IOException e) {
      throw new RuntimeException("unexpected exception", e);
    }

    // Map the tokens from the generated source back to the
    // input source and ensure that the map is correct.
    for (Token token : resultTokens.values()) {
      OriginalMapping mapping = reader.getMappingForLine(
          token.position.getLineNumber() + 1,
          token.position.getCharacterIndex() + 1);

      assertNotNull(mapping);

      // Find the associated token in the input source.
      Token inputToken = originalTokens.get(token.tokenName);
      assertNotNull(inputToken);

      // Ensure that the map correctly points to the token (we add 1
      // to normalize versus the Rhino line number indexing scheme).
      assertEquals(mapping.position.getLineNumber(),
                   inputToken.position.getLineNumber() + 1);

      // Ensure that if the token name does not being with an 'STR' (meaning a
      // string) it has an original name.
      if (!inputToken.tokenName.startsWith("STR")) {
        assertTrue(!mapping.originalName.isEmpty());
      }

      // Ensure that if the mapping has a name, it matches the token.
      if (!mapping.originalName.isEmpty()) {
        assertEquals(mapping.originalName, "__" + inputToken.tokenName + "__");
      }
    }
  }

  private RunResult compile(String js) {
    return compile(js, "testcode");
  }

  private RunResult compile(String js, String fileName) {
    return compile(js, fileName, null, null);
  }

  private RunResult compile(String js1, String fileName1, String js2,
      String fileName2) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.sourceMapOutputPath = "testcode_source_map.out";
    options.sourceMapFormat = SourceMap.Format.EXPERIMENTIAL;
    options.sourceMapDetailLevel = detailLevel;

    // Turn on IDE mode to get rid of optimizations.
    options.ideMode = true;

    JSSourceFile[] inputs = { JSSourceFile.fromCode(fileName1, js1) };

    if (js2 != null && fileName2 != null) {
      JSSourceFile[] multiple =  { JSSourceFile.fromCode(fileName1, js1),
                                   JSSourceFile.fromCode(fileName2, js2) };
      inputs = multiple;
    }

    Result result = compiler.compile(EXTERNS, inputs, options);

    assertTrue(result.success);
    String source = compiler.toSource();

    StringBuilder sb = new StringBuilder();
    try {
      ((SourceMap2)result.sourceMap).validate(true);
      result.sourceMap.appendTo(sb, "testcode");
    } catch (IOException e) {
      throw new RuntimeException("unexpected exception", e);
    }

    RunResult rr = new RunResult();
    rr.generatedSource = source;
    rr.sourceMap = result.sourceMap;
    rr.sourceMapFileContent = sb.toString();
    return rr;
  }

  public static class SourceMapParseException extends IOException {
    private static final long serialVersionUID = 1L;

    public SourceMapParseException(String message) {
      super(message);
    }

    public SourceMapParseException(String message, Exception ex) {
      super(message, ex);
    }
  }

  public static class OriginalMapping {
    public final String srcfile;
    public final Position position;
    public final String originalName;

    OriginalMapping(String srcfile, int line, int column, String name) {
      this.srcfile = srcfile;
      this.position = new Position(line, column);
      this.originalName = name;
    }
  }

  /**
   * Class for parsing and representing a SourceMap
   * TODO(johnlenz): This would be best as a seperate open-source component.
   *     Remove this when it is.
   */
  public class SourceMapReader {
    private List<List<Integer>> characterMap = null;

    /**
     * Map of Mapping IDs to the actual mapping object.
     */
    private List<OriginalMapping> mappings;
    private List<String> sources;
    private List<String> names;

    public SourceMapReader() {
    }

    /**
     * Parses the given contents containing a source map.
     * @throws IOException
     */
    public void parse(String contents) throws IOException {
      characterMap = null;
      mappings = null;
      sources = null;
      names = null;

      try {
        JSONObject sourceMapRoot = new JSONObject(contents);

        int version = sourceMapRoot.getInt("version");
        if (version != 2) {
          throw new SourceMapParseException("unknown version");
        }

        String file = sourceMapRoot.getString("file");
        if (file.isEmpty()) {
          throw new SourceMapParseException("file entry is missing or empty");
        }

        int lineCount = sourceMapRoot.getInt("lineCount");
        JSONArray lineMaps = sourceMapRoot.getJSONArray("lineMaps");
        if (lineCount != lineMaps.length()) {
          throw new SourceMapParseException(
              "lineMaps lenght does not match lineCount");
        }

        characterMap = Lists.newArrayListWithCapacity(lineCount);

        for (int i=0; i< lineMaps.length(); i++) {
          String lineEntry = lineMaps.getString(i);
          List<Integer> entries = SourceMap2.LineMapDecoder.decodeLine(
              lineEntry);
          String msg = "line: " + entries;
          System.err.println(msg);
          characterMap.add(entries);
        }

        sources = jsonArrayToJavaArray(
            sourceMapRoot.getJSONArray("sources"));

        if (sourceMapRoot.has("names")) {
          names = jsonArrayToJavaArray(
              sourceMapRoot.getJSONArray("names"));
        } else {
          names = Collections.emptyList();
        }

        JSONArray jsonMappings = sourceMapRoot.getJSONArray("mappings");
        mappings = Lists.newArrayListWithCapacity(lineCount);
        for (int i = 0; i < jsonMappings.length(); i++) {
          JSONArray entry = jsonMappings.getJSONArray(i);

          String name;
          try {
            int nameIndex = entry.getInt(3);
            name = names.get(nameIndex);
          } catch (JSONException e) {
            name = entry.optString(3, "");
          }

          OriginalMapping mapping = new OriginalMapping(
            sources.get(entry.getInt(0)), // srcfile
            entry.getInt(1),    // line
            entry.getInt(2),    // column
            name); // identifier
          mappings.add(mapping);
        }
      } catch (JSONException ex) {
        throw new SourceMapParseException("JSON parse exception", ex);
      }
    }

    private List<String> jsonArrayToJavaArray(JSONArray jsonArray)
        throws JSONException {
      List<String> result = Lists.newArrayListWithCapacity(jsonArray.length());
      for (int i=0; i< jsonArray.length(); i++) {
        String source = jsonArray.getString(i);
        result.add(source);
      }
      return result;
    }

    public OriginalMapping getMappingForLine(int lineNumber, int columnIndex) {
      Preconditions.checkNotNull(characterMap, "parse() must be called first");

      List<Integer> mapIds = characterMap.get(lineNumber - 1);
      if (mapIds == null) {
        return null;
      }

      int columnPosition = columnIndex - 1;
      if (columnPosition >= mapIds.size() || columnPosition < 0) {
        return null;
      }

      int mapId = mapIds.get(columnPosition);

      return mappings.get(mapId);
    }
  }
}
