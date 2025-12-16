/*
 * Copyright 2025 The Closure Compiler Authors.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerTestCase.srcs;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TranspileAndOptimizeClosureUnawareTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return this::process;
  }

  private void process(Node externs, Node root) {
    Compiler compiler = getLastCompiler();
    ManageClosureUnawareCode.wrap(compiler).process(externs, root);
    new TranspileAndOptimizeClosureUnaware(compiler).process(externs, root);
    ManageClosureUnawareCode.unwrap(compiler).process(externs, root);
  }

  private int inputCount;
  private int outputCount;
  private Consumer<CompilerOptions> extraOptions;

  @Before
  public void setup() {
    disableValidateAstChangeMarking();
    allowExternsChanges();
    this.inputCount = 0;
    this.outputCount = 0;
    this.extraOptions = options -> {};
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions baseOptions = super.getOptions();
    extraOptions.accept(baseOptions);
    return baseOptions;
  }

  String createPrettyPrinter(Node n) {
    return new CodePrinter.Builder(n)
        .setCompilerOptions(getLastCompiler().getOptions())
        .setPrettyPrint(true)
        .build();
  }

  @Test
  public void testEmptyClosureUnawareFn_unchanged() {
    test(srcs(closureUnaware("")), expected(expectedClosureUnaware("")));
  }

  @Test
  public void testFoldConstants() {
    test(
        srcs(closureUnaware("console.log(1 + 2);")),
        expected(expectedClosureUnaware("console.log(3);")));
  }

  @Test
  public void testFoldConstants_multipleFiles() {
    test(
        srcs(
            closureUnaware("console.log(1 + 2);"),
            closureUnaware("console.log(11 + 22);"),
            closureUnaware("console.log(111 + 222);")),
        expected(
            expectedClosureUnaware("console.log(3);"),
            expectedClosureUnaware("console.log(33);"),
            expectedClosureUnaware("console.log(333);")));
  }

  @Test
  public void testFoldConstants_multipleFiles_ignoresNonClosureUnaware() {
    test(
        srcs(
            closureUnaware("console.log(1 + 2);"),
            """
            goog.module('some.other.file');
            console.log(11 + 22);
            """),
        expected(
            expectedClosureUnaware("console.log(3);"),
            """
            goog.module('some.other.file');
            console.log(11 + 22);
            """));
  }

  @Test
  public void testFoldConstants_multipleClosureUnawareBlocksInFile() {
    test(
        srcs(
            closureUnaware(
                "console.log(1 + 2);", "console.log(11 + 22);", "console.log(111 + 222);")),
        expected(
            expectedClosureUnaware("console.log(3);", "console.log(33);", "console.log(333);")));
  }

  private static String loadFile(Path path) {
    try (Stream<String> lines = Files.lines(path)) {
      return lines.collect(joining("\n"));
    } catch (Exception e) {
      throw new AssertionError("Failed to load debug log at " + path, e);
    }
  }

  private ImmutableList<Path> listFiles(Path dir) {
    try (Stream<Path> files = Files.list(dir)) {
      return files.collect(toImmutableList());
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  @Test
  public void testSupportsDebugLogging() {
    enableDebugLogging(true);
    testNoWarning(closureUnaware("console.log(0 + 1);"));

    Path dir =
        Path.of(
            this.getLastCompiler().getOptions().getDebugLogDirectory().toString(),
            TranspileAndOptimizeClosureUnaware.class.getSimpleName());
    assertThat(listFiles(dir)).isNotEmpty();
  }

  @Test
  public void testSupportsPrintingSourceAfterEachPass() {
    enableDebugLogging(true);
    this.extraOptions =
        (CompilerOptions options) -> {
          options.setPrintSourceAfterEachPass(true);
          options.setInputDelimiter("~~test123~ : %name%");
          options.setPrintInputDelimiter(true);
        };

    testNoWarning(closureUnaware("console.log(0 + 1);"));

    Path dir =
        Path.of(
            this.getLastCompiler().getOptions().getDebugLogDirectory().toString(),
            TranspileAndOptimizeClosureUnaware.class.getSimpleName());
    var firstSource = loadFile(dir.resolve("Compiler/source_after_pass/000_parseInputs"));
    assertThat(firstSource)
        .isEqualTo(
            """
            ~~test123~ : synthetic_base
            'use strict';
            ~~test123~ : testcode.shadow0
            $jscomp_sink_closure_unaware_impl(function(){console.log(0+1)});\
            """);
  }

  @Test
  public void testSupportsPrintingSourceAfterEachPass_withRegexpFilter() {
    enableDebugLogging(true);
    this.extraOptions =
        (CompilerOptions options) -> {
          options.setPrintSourceAfterEachPass(true);
          options.setPrettyPrint(true);
          options.setFilesToPrintAfterEachPassRegexList(ImmutableList.of(".*shadow1"));
        };

    testNoWarning(
        srcs(
            closureUnaware("log('0.0');", "log('0.1');", "log('0.2');"),
            closureUnaware("log('1.0');", "log('1.1');", "log('1.2');")));

    Path dir =
        Path.of(
            this.getLastCompiler().getOptions().getDebugLogDirectory().toString(),
            TranspileAndOptimizeClosureUnaware.class.getSimpleName());
    var firstSource = loadFile(dir.resolve("Compiler/source_after_pass/000_parseInputs"));
    assertThat(firstSource)
        .isEqualTo(
            """
            // testcode0.shadow1
            $jscomp_sink_closure_unaware_impl(function() {
              log("0.1");
            });
            // testcode1.shadow1
            $jscomp_sink_closure_unaware_impl(function() {
              log("1.1");
            });
            """);
  }

  private String closureUnaware(String... closureUnaware) {
    String prefix =
        String.format(
            """
            /** @fileoverview @closureUnaware */
            goog.module('test%d');
            """,
            inputCount++);
    StringBuilder input = new StringBuilder().append(prefix);
    for (String block : closureUnaware) {
      input.append(
          String.format(
              """
              /** @closureUnaware */
              (function() {
                %s
              }).call(undefined);
              """,
              block));
    }
    return input.toString();
  }

  @Test
  public void testFoldConstants_directCallInsteadOfDotCall() {
    test(
        srcs(
            // Don't use the "closureUnaware()" wrapper because it always uses .call - we want to
            // specifically test a regular direct call.
            """
            /** @fileoverview @closureUnaware */
            goog.module('test');
            /** @closureUnaware */
            (function() {
              console.log(1 + 2);
            })();
            """),
        expected(
            """
            /** @fileoverview @closureUnaware */
            goog.module('test');
            (function() {
              console.log(3);
            })();
            """));
  }

  private String expectedClosureUnaware(String... closureUnaware) {
    String prefix =
        String.format(
            """
            /** @fileoverview @closureUnaware */
            goog.module('test%d');
            """,
            outputCount++);
    StringBuilder output = new StringBuilder().append(prefix);
    for (String block : closureUnaware) {
      output.append(
          String.format(
              """
              (function() {
                %s
              }).call(undefined);
              """,
              block));
    }
    return output.toString();
  }

  // TODO: b/421971366 - add a greater variety of test cases.
}
