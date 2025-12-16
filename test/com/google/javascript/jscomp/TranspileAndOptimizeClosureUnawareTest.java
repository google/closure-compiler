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

import static com.google.javascript.jscomp.CompilerTestCase.srcs;

import com.google.javascript.rhino.Node;
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

  @Before
  public void setup() {
    disableValidateAstChangeMarking();
    allowExternsChanges();
    this.inputCount = 0;
    this.outputCount = 0;
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
