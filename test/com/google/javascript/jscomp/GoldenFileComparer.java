/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assert_;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test compiler by comparing output to data files aka golden files.
 * Always start with the options object from this class.
 */
public class GoldenFileComparer {

  private static final String DATA_DIR =
      "test/"
          + "com/google/javascript/jscomp/testdata/";

  private static List<SourceFile> coverageExterns() {
      SourceFile externs = SourceFile.fromCode("externs", "function Symbol() {}; var window;");
      return ImmutableList.of(externs);
  }

  private static String compile(
      List<SourceFile> externs, List<SourceFile> inputs, CompilerOptions options) {
    Compiler compiler = new Compiler();

    List<SourceFile> allExterns = new ArrayList<>();
    allExterns.addAll(externs);
    allExterns.addAll(coverageExterns());

    compiler.compile(allExterns, inputs, options);
    return compiler.toSource();
  }

  private static String readFile(String filePath) throws IOException {
    return Files.toString(new File(filePath), UTF_8);
  }

  private static SourceFile readSource(String fileName) throws IOException {
    String source = readFile(toFullPath(fileName));
   return SourceFile.fromCode(fileName, source);
  }

  private static boolean compare(String compiledSource, String goldenSource) {
    String[] compiledLines = compiledSource.split("\n");
    String[] goldenLines = goldenSource.split("\n");

    assert_()
        .withFailureMessage("Num lines of compiled code do not match that of expected")
        .that(compiledLines.length)
        .isEqualTo(goldenLines.length);

    // Loop through each line to make it convenient to pin-point the faulty one
    for (int i = 0; i < compiledLines.length; i++) {
      assert_()
          .withFailureMessage(
              "Instrumented code does not match expected at line no: "
                  + (i + 1)
                  + "\nExpected:\n"
                  + goldenLines[i]
                  + "\nFound:\n"
                  + compiledLines[i]
                  + "\n")
          .that(compiledLines[i])
          .isEqualTo(goldenLines[i]);
    }

    return true;
  }

  private static String toFullPath(String fileName) {
    return DATA_DIR + fileName;
  }

  private static void compileAndCompare(
      String goldenFileName,
      CompilerOptions options,
      List<SourceFile> sourceFiles,
      List<SourceFile> externsFiles)
      throws Exception {
    String compiledSource = compile(externsFiles, sourceFiles, options);
    String referenceSource = readFile(toFullPath(goldenFileName));
    compare(compiledSource, referenceSource);
  }

  /**
   * Always use these options, they set --pretty_print option for easy verification.
   */
  public static CompilerOptions options() {
    CompilerOptions options = new CompilerOptions();
    // Instrumentation done
    options.setPrettyPrint(true);
    return options;
  }

  /**
   * Compile one input file and throw if the result does not match golden.
   * Pass options from this class, mutated with desired options
   */
  public static void compileAndCompare(
      String goldenFileName, CompilerOptions options, String sourceFileName) throws Exception {
    List<SourceFile> sourceFiles =
        ImmutableList.of(readSource(sourceFileName));
    List<SourceFile> externsFiles = ImmutableList.of();
    compileAndCompare(goldenFileName, options, sourceFiles, externsFiles);
  }

  /**
   * Compile two input files and throw if the result does not match golden.
   * Pass options from this class, mutated with desired options
   */
  public static void compileAndCompare(
      String goldenFileName,
      CompilerOptions options,
      String sourceFileName1,
      String sourceFileName2,
      String externsFileName)
      throws Exception {
    // Prepare sources
    List<SourceFile> sourceFiles = ImmutableList.of(
        readSource(sourceFileName1),
        readSource(sourceFileName2));

    List<SourceFile> externsFiles =
        ImmutableList.of(SourceFile.fromFile(toFullPath(externsFileName)));

    compileAndCompare(goldenFileName, options, sourceFiles, externsFiles);
  }
}
