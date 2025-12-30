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
    return new TranspileAndOptimizeClosureUnaware(compiler);
  }

  private int inputCount;
  private int outputCount;
  private Consumer<CompilerOptions> extraOptions;

  @Before
  public void setup() {
    disableCompareJsDoc();
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

  @Test
  public void testInlineLocalObject() {
    test(
        srcs(
            closureUnaware(
                """
                const obj = {x: 1, unused: 2};
                return obj.x;
                """)),
        expected(
            expectedClosureUnaware(
                """
                return 1;
                """)));
  }

  @Test
  public void testDoNotRenameOrFlattenEscapedObjects() {
    test(
        srcs(
            closureUnaware(
                """
                const obj = {x: 1, y: 2};
                foo(obj);
                return obj.x;
                """)),
        expected(
            expectedClosureUnaware(
                """
                const a = {x: 1, y: 2};
                foo(a);
                return a.x;
                """)));
  }

  @Test
  public void testAssumeAllPropertyReadsHaveSideEffects() {
    test(
        srcs(
            closureUnaware(
                """
                return function(obj) {
                  const x = obj.x;
                  // Assume that reading obj.y might have other side effects & affect obj.x.
                  const y = obj.y;
                  console.log(x);
                }
                """)),
        expected(
            expectedClosureUnaware(
                // TODO: b/421971366 - preserve the `obj.y` read. It could be a side-effectful
                // getter.
                """
                return function(a) {
                  console.log(a.x);
                }
                """)));
  }

  @Test
  public void testAssumeAllPropertyWritesHaveSideEffects() {
    test(
        closureUnaware(
            """
            return function(obj) {
              const x = obj.x;
              // Assume that writing to obj.y might have other side effects & affect obj.x.
              obj.y = 0;
              console.log(x);
            }
            """),
        expectedClosureUnaware(
            """
            return function(a) {
              const b = a.x;
              a.y = 0;
              console.log(b);
            }
            """));
  }

  @Test
  public void testNoClosureAssertsRemoval() {
    test(
        closureUnaware("goog.asserts.assert(true);"),
        expectedClosureUnaware("goog.asserts.assert(!0);"));
  }

  @Test
  public void doesntRenameExternRefs_shadowedLocally() {
    test(
        closureUnaware(
            """
            return function() {
              const maybeExternal = () => typeof External === 'undefined' ? null : External;
              globalThis.foo = function() {
                const External = maybeExternal();
                console.log(External);
              };
            };
            """),
        expectedClosureUnaware(
            // Regression test: the compiler used to incorrectly obfuscate 'typeof External'
            // to 'typeof a'.
            """
            return function() {
              globalThis.foo = function() {
                console.log(typeof External === "undefined" ? null : External);
              };
            };
            """));
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
            /** @closureUnaware */
            (function() {
              console.log(3);
            })();
            """));
  }

  private String closureUnaware(String... closureUnaware) {
    return buildCode(inputCount++, closureUnaware);
  }

  private String expectedClosureUnaware(String... closureUnaware) {
    return buildCode(outputCount++, closureUnaware);
  }

  private static String buildCode(int idx, String... closureUnaware) {
    String prefix =
        String.format(
            """
            /** @fileoverview @closureUnaware */
            goog.module('test%d');
            """,
            idx);
    StringBuilder output = new StringBuilder().append(prefix);
    for (String block : closureUnaware) {
      output.append(
          String.format(
              """
              /** @closureUnaware */
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
