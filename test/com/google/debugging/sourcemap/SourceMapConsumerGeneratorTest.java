/*
 * Copyright 2020 The Closure Compiler Authors.
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
import static java.util.stream.Collectors.joining;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This is a semi-integration test that verifies that Generator and Consumer work well together. The
 * test creates simple file in a format described below, generates sourcemap using the Generator and
 * finally parses sourcemap using Consumer verifying that all mappings exist and correct.
 *
 * <p>As test file we just concatenate three test files together separating using // comments. Each
 * word should map back to its position in its original file. Concatenated file:
 *
 * <pre>
 * // file_one.txt
 * content of file one
 * // file_two.txt
 * content of
 * file two
 * // file_three.txt
 * even
 *   more
 *     content
 * </pre>
 */
@RunWith(JUnit4.class)
public final class SourceMapConsumerGeneratorTest {

  @Test
  public void test() throws Exception {
    ImmutableMap<String, String> sourceFiles =
        ImmutableMap.of(
            "file_one.txt", "content of file one",
            "file_two.txt", "content of\nfile two",
            "file_three.txt", "even\n  more\n    content");
    String concatenatedFile =
        sourceFiles.entrySet().stream()
            .map((entry) -> String.format("// %s\n%s", entry.getKey(), entry.getValue()))
            .collect(joining("\n"));

    String sourcemap = generateSourceMap(concatenatedFile);

    validateSourceMap(sourceFiles, concatenatedFile, sourcemap);
  }

  /**
   * Takes a file content in the format described in the test overview and returns its sourcemap.
   */
  private String generateSourceMap(String file) throws Exception {
    SourceMapGenerator generator = SourceMapGeneratorFactory.getInstance(SourceMapFormat.V3);
    List<String> lines = Splitter.on("\n").splitToList(file);
    String currentSourceFile = "";
    int sourceFileLineNumber = 0;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.startsWith("// ")) {
        currentSourceFile = line.substring("// ".length());
        sourceFileLineNumber = 0;
        continue;
      }

      // Iterate through line and add mapping for each word. Word is a sequence of characters with
      // exception of spaces.
      StringBuilder currentWord = new StringBuilder();
      for (int j = 0; j < line.length(); j++) {
        char c = line.charAt(j);
        if (c == ' ') {
          if (currentWord.length() > 0) {
            int startPos = j - currentWord.length();
            generator.addMapping(
                currentSourceFile,
                currentWord.toString(),
                new FilePosition(sourceFileLineNumber, startPos),
                new FilePosition(i, startPos),
                new FilePosition(i, j - 1));
            currentWord = new StringBuilder();
          }
        } else {
          currentWord.append(c);
        }
      }
      // Don't forget to add mapping for word if there is no spaces after it.
      if (currentWord.length() > 0) {
        int startPos = line.length() - currentWord.length();
        generator.addMapping(
            currentSourceFile,
            currentWord.toString(),
            new FilePosition(sourceFileLineNumber, startPos),
            new FilePosition(i, startPos),
            new FilePosition(i, line.length() - 1));
      }
      sourceFileLineNumber++;
    }
    StringBuilder sourcemap = new StringBuilder();
    generator.appendTo(sourcemap, "test");
    return sourcemap.toString();
  }

  private void validateSourceMap(
      ImmutableMap<String, String> sourceFiles, String concatenatedFile, String sourcemap)
      throws Exception {
    SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
    consumer.parse(sourcemap);
    AtomicInteger mappingsCount = new AtomicInteger();
    Set<String> seenSymbols = new HashSet<>();
    consumer.visitMappings(
        (String sourceName,
            String symbolName,
            FilePosition sourceStartPosition,
            FilePosition startPosition,
            FilePosition endPosition) -> {
          mappingsCount.incrementAndGet();
          seenSymbols.add(symbolName);
          String sourceFileContent = sourceFiles.get(sourceName);
          // Check that in source file at given position we see symbol name.
          assertThat(getRestOfLineThatStartsAtPosition(sourceStartPosition, sourceFileContent))
              .startsWith(symbolName);
          // Check that in concatenated/generated file at given position we see symbol name.
          assertThat(getRestOfLineThatStartsAtPosition(startPosition, concatenatedFile))
              .startsWith(symbolName);
        });
    // Ensure that all words are mapped.
    assertThat(mappingsCount.intValue()).isEqualTo(11);
    assertThat(seenSymbols).containsExactly("content", "of", "file", "one", "two", "even", "more");
  }

  private static String getRestOfLineThatStartsAtPosition(FilePosition position, String text) {
    return Splitter.on("\n")
        .splitToList(text)
        .get(position.getLine())
        .substring(position.getColumn());
  }
}
