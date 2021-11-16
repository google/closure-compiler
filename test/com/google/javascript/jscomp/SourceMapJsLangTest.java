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
package com.google.javascript.jscomp;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.debugging.sourcemap.SourceMapConsumer;
import com.google.debugging.sourcemap.SourceMapConsumerV3;
import com.google.debugging.sourcemap.SourceMapTestCase;
import com.google.javascript.jscomp.SourceMap.Format;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test runs through a set of basic JS language structures and ensures that sourcemap are
 * correct for these structures. The testcases are stores in testdata/SourceMapJsLangTest folder.
 * This test run compiler over each scenario without doing any obfuscation or changing AST tree.
 *
 * <p>Each test case uses following syntax: all identifiers that start and end with __ such as
 * __className__ are checked:
 *
 * <ul>
 *   <li>Identifier must be present in generated code.
 *   <li>Position of the identifier in generated code must map to the position of the identifier in
 *       the original code.
 *   <li>Mapping must contain a name equal to the identifier name.
 * </ul>
 *
 * Note that this can only test mappings for identifiers and doesn't test mappins for control tokens
 * like 'var', 'const', 'for', 'class'. It would nice to do but current version is good enough as
 * users usually care about identifiers.
 */
@RunWith(Parameterized.class)
public final class SourceMapJsLangTest extends SourceMapTestCase {

  private static final String DATA_DIR =
      "test/"
          + "com/google/javascript/jscomp/testdata/SourceMapJsLangTest";

  @Parameters(name = "{0}")
  public static ImmutableList<? extends Object> data() {
    ImmutableList.Builder<String> testFiles = ImmutableList.builder();
    for (File testFile : new File(DATA_DIR).listFiles()) {
      testFiles.add(testFile.getName());
    }
    return testFiles.build();
  }

  private final String fileContent;
  private final String fileName;
  private boolean prettyPrinted;
  private final ImmutableMap.Builder<String, SourceMapInput> inputMaps;

  public SourceMapJsLangTest(String fileName) throws Exception {
    this.fileName = fileName;
    this.fileContent = Files.asCharSource(new File(DATA_DIR, fileName), UTF_8).read();
    this.prettyPrinted = false;
    this.inputMaps = ImmutableMap.builder();
  }

  @Test
  public void testSourceMapsInCollapsedCodeWork() throws Exception {
    RunResult result = compile(fileContent, fileName);
    check(fileName, fileContent, result.generatedSource, result.sourceMapFileContent);
  }

  @Test
  public void testSourceMapsInPrettyPrintedCodeWork() throws Exception {
    this.prettyPrinted = true;
    RunResult result = compile(fileContent, fileName);
    // TODO(b/156856666): add logic to check() that verifies that for given identifier mapping
    // starts exactly at the position where identifiers starts and not earlier in generated code.
    check(fileName, fileContent, result.generatedSource, result.sourceMapFileContent);
  }

  @Test
  public void testRepeatedCompilation() throws Exception {
    // Run compiler twice feeding sourcemaps from the first run as input to the second run.
    // This way we ensure that compiler works fine with its own sourcemaps and doesn't lose
    // important information.
    RunResult firstCompilation = compile(fileContent, fileName);
    String newFileName = fileName + ".compiled";
    inputMaps.put(
        newFileName,
        new SourceMapInput(
            SourceFile.fromCode("sourcemap", firstCompilation.sourceMapFileContent)));
    // To make it more fun - pretty print code.
    this.prettyPrinted = true;

    RunResult secondCompilation = compile(firstCompilation.generatedSource, newFileName);
    check(
        fileName,
        fileContent,
        secondCompilation.generatedSource,
        secondCompilation.sourceMapFileContent);
  }

  @Override
  protected Format getSourceMapFormat() {
    return Format.V3;
  }

  @Override
  protected SourceMapConsumer getSourceMapConsumer() {
    return new SourceMapConsumerV3();
  }

  @Override
  protected CompilerOptions getCompilerOptions() {
    CompilerOptions options = super.getCompilerOptions();
    options.setPrettyPrint(this.prettyPrinted);
    if (!this.inputMaps.buildOrThrow().isEmpty()) {
      options.setApplyInputSourceMaps(true);
      options.setInputSourceMaps(this.inputMaps.buildOrThrow());
    }
    return options;
  }
}
