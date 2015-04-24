/*
 * Copyright 2011 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.SourceMap.DetailLevel;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author johnlenz@google.com (John Lenz)
 */
public abstract class SourceMapTestCase extends TestCase {

  private boolean validateColumns = true;

  public SourceMapTestCase() {
  }

  void disableColumnValidation() {
    validateColumns = false;
  }


  static final List<SourceFile> EXTERNS = ImmutableList.of(
      SourceFile.fromCode("externs", ""));

  protected DetailLevel detailLevel = SourceMap.DetailLevel.ALL;

  protected static class RunResult {
      String generatedSource;
      SourceMap sourceMap;
      public String sourceMapFileContent;
    }

  protected static class Token {
      final String tokenName;
      final String inputName;
      final FilePosition position;
      Token(String tokenName, String inputName, FilePosition position) {
        this.tokenName = tokenName;
        this.inputName = inputName;
        this.position = position;
      }
    }

  @Override
  public void setUp() {
    detailLevel = SourceMap.DetailLevel.ALL;
  }

  /**
   * Creates a source map for the given JS code and asserts it is
   * equal to the expected golden map.
   */
  protected void checkSourceMap(String js, String expectedMap)
      throws IOException {
    checkSourceMap("testcode", js, expectedMap);
  }

  protected String getSourceMap(RunResult result) throws IOException {
    StringBuilder sb = new StringBuilder();
    result.sourceMap.appendTo(sb, "testcode");
    return sb.toString();
  }

  protected void checkSourceMap(String fileName, String js, String expectedMap)
      throws IOException {
    RunResult result = compile(js, fileName);
    assertThat(result.sourceMapFileContent).isEqualTo(expectedMap);
    assertThat(getSourceMap(result)).isEqualTo(result.sourceMapFileContent);
  }

  /**
   * Finds the all the __XX__ tokens in the given JavaScript
   * string.
   */
  private Map<String, Token> findTokens(Map<String, String> inputs) {
    Map<String, Token> tokens = new LinkedHashMap<>();

    for (Entry<String, String> entry : inputs.entrySet()) {
      findTokens(tokens, entry.getKey(), entry.getValue());
    }

    return tokens;
  }

  /**
   * Finds the all the __XX__ tokens in the given JavaScript
   * string.
   */
  private Map<String, Token> findTokens(String src) {
    Map<String, Token> tokens = new LinkedHashMap<>();

    findTokens(tokens, "", src);

    return tokens;
  }

  /**
   * Finds the all the __XX__ tokens in the given JavaScript
   * string.
   */
  private Map<String, Token> findTokens(
    Map<String, Token> tokens, String inputName, String js) {

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
          int currentPosition = i - positionOffset;
          Token token = new Token(
              tokenName, inputName,
              new FilePosition(currentLine, currentPosition));
          tokens.put(tokenName, token);
        }

        i = j;
      }
    }

    return tokens;
  }

  protected abstract SourceMap.Format getSourceMapFormat();

  protected abstract SourceMapConsumer getSourceMapConsumer();

  protected void compileAndCheck(String js) {
    String inputName = "testcode";
    RunResult result = compile(js, inputName);
    check(inputName, js, result.generatedSource, result.sourceMapFileContent);
  }

  protected void check(
      String inputName, String input, String output,
      String sourceMapFileContent) {
    Map<String, String> inputMap = new LinkedHashMap<>();
    inputMap.put(inputName, input);
    check(inputMap, output, sourceMapFileContent);
  }

  protected void check(
      Map<String, String> originalInputs, String generatedSource,
      String sourceMapFileContent) {
    check(originalInputs, generatedSource, sourceMapFileContent, null);
  }

  protected void check(
      Map<String, String> originalInputs, String generatedSource,
      String sourceMapFileContent, SourceMapSupplier supplier) {
    // Find all instances of the __XXX__ pattern in the original
    // source code.
    Map<String, Token> originalTokens = findTokens(originalInputs);

    // Find all instances of the __XXX__ pattern in the generated
    // source code.
    Map<String, Token> resultTokens = findTokens(generatedSource);

    // Ensure that the generated instances match via the source map
    // to the original source code.

    // Ensure the token counts match.
    assertThat(resultTokens).hasSize(originalTokens.size());

    SourceMapping reader;
    try {
      reader = SourceMapConsumerFactory.parse(sourceMapFileContent, supplier);
    } catch (SourceMapParseException e) {
      throw new RuntimeException("unexpected exception", e);
    }

    // Map the tokens from the generated source back to the
    // input source and ensure that the map is correct.
    for (Token token : resultTokens.values()) {
      OriginalMapping mapping = reader.getMappingForLine(
          token.position.getLine() + 1,
          token.position.getColumn() + 1);

      assertThat(mapping).isNotNull();

      // Find the associated token in the input source.
      Token inputToken = originalTokens.get(token.tokenName);
      assertThat(inputToken).isNotNull();
      assertThat(inputToken.inputName).isEqualTo(mapping.getOriginalFile());

      // Ensure that the map correctly points to the token (we add 1
      // to normalize versus the Rhino line number indexing scheme).
      assertThat(inputToken.position.getLine() + 1).isEqualTo(mapping.getLineNumber());

      int start = inputToken.position.getColumn() + 1;
      if (inputToken.tokenName.startsWith("STR")) {
        // include the preceding quote.
        start--;
      }

      if (validateColumns) {
        assertThat(mapping.getColumnPosition()).isEqualTo(start);
      }

      // Ensure that if the token name does not being with an 'STR' (meaning a
      // string) it has an original name.
      if (!inputToken.tokenName.startsWith("STR")) {
        assertFalse("missing name for " + inputToken.tokenName, mapping.getIdentifier().isEmpty());
      }

      // Ensure that if the mapping has a name, it matches the token.
      if (!mapping.getIdentifier().isEmpty()) {
        assertThat("__" + inputToken.tokenName + "__").isEqualTo(mapping.getIdentifier());
      }
    }
  }

  protected RunResult compile(String js, String fileName) {
    return compile(js, fileName, null, null);
  }

  protected CompilerOptions getCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setSourceMapOutputPath("testcode_source_map.out");
    options.setSourceMapFormat(getSourceMapFormat());
    options.setSourceMapDetailLevel(detailLevel);
    return options;
  }

  protected RunResult compile(
      String js1, String fileName1, String js2, String fileName2) {
    Compiler compiler = new Compiler();
    CompilerOptions options = getCompilerOptions();

    // Turn on IDE mode to get rid of optimizations.
    options.ideMode = true;

    List<SourceFile> inputs =
        ImmutableList.of(SourceFile.fromCode(fileName1, js1));

    if (js2 != null && fileName2 != null) {
      inputs = ImmutableList.of(
          SourceFile.fromCode(fileName1, js1),
          SourceFile.fromCode(fileName2, js2));
    }

    Result result = compiler.compile(EXTERNS, inputs, options);

    assertTrue("compilation failed", result.success);
    String source = compiler.toSource();

    StringBuilder sb = new StringBuilder();
    try {
      result.sourceMap.validate(true);
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

}
