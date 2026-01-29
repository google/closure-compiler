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
import static org.junit.Assert.assertThrows;

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
    return new TranspileAndOptimizeClosureUnaware(compiler, mode);
  }

  private int inputCount;
  private int outputCount;
  private Consumer<CompilerOptions> extraOptions;
  private NestedCompilerRunner.Mode mode;

  @Before
  public void setup() {
    disableCompareJsDoc();
    this.inputCount = 0;
    this.outputCount = 0;
    this.extraOptions = options -> {};
    this.mode = NestedCompilerRunner.Mode.TRANSPILE_AND_OPTIMIZE;
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
  public void testFoldConstants_regressionTest_crashInNodeUtilAddFeatureToScript() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);

    test(
        closureUnaware(
            """
            return function f() {
              function referenceX() { return x; }

              return 1;

              // Regression test for a crash in PeepholeRemoveDeadCode.
              let x = 0;
              referenceX();
              x++;
            }
            """),
        expectedClosureUnaware(
            """
            return function() {
              return 1;
            }
            """));
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
                """
                return function(a) {
                  const b = a.x;
                  a.y;
                  console.log(b);
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
              const a = typeof External === "undefined" ? null : External;
              console.log(a);
              };
            };
            """));
  }

  @Test
  public void testAssumeRegexpMayHaveGlobalSideEffects() {
    // References to the global RegExp object make regular expression calls unoptimizable. In the
    // main AST the CheckRegExp pass looks for these references. But in @closureUnaware compilation,
    // we cannot assume that the CheckRegexpPass will see all possible Regexp usages.
    // Hence we cannot remove the seemingly unused `/abc/.match(str)` call.
    // Note that we still remove completely unused regex literals.
    test(closureUnaware("/abc/.match(str); /unused/"), expectedClosureUnaware("/abc/.match(str);"));
  }

  @Test
  public void testDontAssumeToStringAndValueOfArePure() {
    testSame(closureUnaware("x.toString(); x.valueOf();"));
  }

  @Test
  public void transpilesExponentialOperator_ifTargetingES2015() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    test(
        srcs(closureUnaware("console.log(x ** y);")),
        expected(expectedClosureUnaware("console.log(Math.pow(x, y));")));
  }

  @Test
  public void doesNotTranspileExponentialOperator_ifTargetingES2017() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2017);
    test(
        srcs(closureUnaware("console.log(x ** y);")),
        expected(expectedClosureUnaware("console.log(x ** y);")));
  }

  @Test
  public void transpileOnlyMode_transpilesExponentialOperator_ifTargettingES2015() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    this.mode = NestedCompilerRunner.Mode.TRANSPILE_ONLY;
    disableCompareJsDoc();
    test(
        srcs(closureUnaware("console.log(x ** y);")),
        expected(expectedClosureUnaware("console.log(Math.pow(x, y));")));
  }

  @Test
  public void transpileOnlyMode_doesNotTranspileExponentialOperator_ifTargetingES2017() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2017);
    this.mode = NestedCompilerRunner.Mode.TRANSPILE_ONLY;
    disableCompareJsDoc();
    test(
        srcs(closureUnaware("console.log(x ** y);")),
        expected(expectedClosureUnaware("console.log(x ** y);")));
  }

  @Test
  public void transpileOnlyMode_doesNotRunConstantFolding() {
    this.mode = NestedCompilerRunner.Mode.TRANSPILE_ONLY;
    disableCompareJsDoc();
    test(
        srcs(closureUnaware("console.log(1 + 2);")),
        expected(expectedClosureUnaware("console.log(1 + 2);")));
  }

  @Test
  public void classInheritance_injectsRequiredRuntimeLibraries() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);

    test(
        srcs(
            """
            /** @fileoverview @closureUnaware */
            goog.module('test');
            /** @closureUnaware */
            (function() {
              class Parent {
                method() {
                  return 10;
                }
              }
              class Child extends Parent {}
              return new Child();
            }).call(globalThis);
            """),
        // NOTE: we could make transpilation smart enough to detect that we don't need the
        // $jscomp.construct call here. Currently it only attempts to analyze superclasses
        // defined in the global scope, and @closureUnaware code is never global. We could
        // easily make the pass look up the definition of 'Parent' in the function-local scope, but
        // decided not to as of Jan 2026, as it isn't clearly worth the added complexity.
        expected(
            """
            /** @fileoverview @closureUnaware */
            goog.module("test");
            /** @closureUnaware */
            (function(c, d) {
              /** @constructor */
              var a = function() {};
              a.prototype.method = function() {
                return 10;
              };
              /** @constructor */
              var b = function() {
                return c(a, arguments, this.constructor);
              };
              d(b, a);
              return new b();
            }).call(globalThis, $jscomp.construct, $jscomp.inherits);
            """));

    assertThat(getLastCompiler().getRuntimeJsLibManager().getInjectedLibraries())
        .containsExactly("es6/util/construct", "es6/util/inherits", "es6/util/arrayfromiterable");
  }

  @Test
  public void runtimeLibraryInjection_injectsOnlyWhatsNeededPerScript() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);

    test(
        srcs(
            closureUnaware("return {...obj};"),
            closureUnaware("return [...arr];"),
            closureUnaware(
                """
                class Parent {}
                class Child extends Parent {}
                return new Child();
                """)),
        expected(
            // Note that Object.assign doesn't need to be injected because it's a polyfill, not a
            // runtime library field.
            """
            /** @fileoverview @closureUnaware */
            goog.module("test0");
            /** @closureUnaware */
            (function() {
              return Object.assign({}, obj);
            }).call(undefined);
            """,
            """
            /** @fileoverview @closureUnaware */
            goog.module("test1");
            /** @closureUnaware */
            (function(a) {
              return [].concat(a(arr));
            }).call(undefined, $jscomp.arrayFromIterable);
            """,
            """
            /** @fileoverview @closureUnaware */
            goog.module("test2");
            /** @closureUnaware */
            (function(a, d) {
              var b = function() {}, c = function() {
                return a(b, arguments, this.constructor);
              };
              d(c, b);
              return new c();
            }).call(undefined, $jscomp.construct, $jscomp.inherits);
            """));

    assertThat(getLastCompiler().getRuntimeJsLibManager().getInjectedLibraries())
        .containsExactly(
            "es6/util/construct",
            "es6/util/inherits",
            "es6/util/arrayfromiterable",
            "es6/object/assign");
  }

  @Test
  public void classInheritance_usesReflectConstructForUnknownSuperclass() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);

    test(
        srcs(
            """
            /** @fileoverview @closureUnaware */
            goog.module('test');
            /** @closureUnaware */
            (function() {
              class Child extends UnknownParent {}
              return new Child();
            }).call(globalThis);
            """),
        expected(
            """
            /** @fileoverview @closureUnaware */
            goog.module("test");
            /** @closureUnaware */
            (function(b, c) {
              /** @constructor */
              var a = function() {
                return b(UnknownParent, arguments, this.constructor);
              };
              c(a, UnknownParent);
              return new a();
            }).call(globalThis, $jscomp.construct, $jscomp.inherits);
            """));

    assertThat(getLastCompiler().getRuntimeJsLibManager().getInjectedLibraries())
        .containsExactly("es6/util/construct", "es6/util/inherits", "es6/util/arrayfromiterable");
  }

  @Test
  public void runtimeFieldInjection_injectsAfterExistingParameters() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);
    test(
        srcs(
            """
            /** @fileoverview @closureUnaware */
            goog.module('test');

            /** @closureUnaware */
            (function(foo, bar, rest) {
              return [foo, bar, ...rest];
            }).call(undefined, 1, 2, [3, 4, 5]);
            """),
        expected(
            """
            /** @fileoverview @closureUnaware */
            goog.module("test");
            /** @closureUnaware */
            (function(a, b, c, d) {
              return [a, b].concat(d(c));
            }).call(undefined, 1, 2, [3, 4, 5], $jscomp.arrayFromIterable);
            """));
  }

  @Test
  public void runtimeFieldInjection_incompatibleWithRestArg() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2015);

    // To make injecting runtime library parameters simpler, assume that closure unaware functions
    // never have rest parameters.
    assertThrows(
        IllegalStateException.class,
        () ->
            test(
                srcs(
                    """
                    /** @fileoverview @closureUnaware */
                    goog.module('test');

                    /** @closureUnaware */
                    (function(...rest) {
                      return async function foo() {};
                    }).call(undefined, 1, 2);
                    """)));
  }

  @Test
  public void doesNotAutomaticallyInjectPolyfills() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2019);
    // Use `globalThis` inside @closureUnaware code. The compiler will typically detect that
    // `globalThis` is defined in ES2020 so is not present in the ES2019 output, and then add
    // a polyfill for `globalThis`.
    // But we do not support this automatic polyfill detection in @closureUnaware code, as it's
    // more difficult to statically analyze. (Particularly in the case of polyfilled methods).
    testSame(
        srcs(
            """
            /** @fileoverview @closureUnaware */
            goog.module('test');
            /** @closureUnaware */
            (function() {
              return globalThis.FOO;
            }).call(globalThis);
            """));
  }

  @Test
  public void taggedTemplateLitTranspilation() {
    extraOptions = (options) -> options.setGeneratePseudoNames(true);
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);

    test(
        srcs(
            closureUnaware(
                """
                function tag(strings, ...values) {
                  return {strings, values};
                }
                tag`a${42}b`;
                tag`a${42}b`; // identical template lit
                tag`a${-42}b`; // identical template strings, different substitution content
                """)),
        expected(
            """
            /** @fileoverview @closureUnaware */
            goog.module('test0');
            /** @closureUnaware */
            (function($$jscomp_getRestArguments$$, $$jscomp_createTemplateTagFirstArg$$) {
              function $tag$$($strings$$) {
                var $values$$ = $$jscomp_getRestArguments$$.apply(1, arguments);
                return {strings:$strings$$, values:$values$$};
              }
              /** @noinline */
              var $$jscomp$templatelit$m854121055$0$$ =
                      $$jscomp_createTemplateTagFirstArg$$(["a", "b"]),
                  $$jscomp$templatelit$m854121055$1$$ =
                      $$jscomp_createTemplateTagFirstArg$$(["a", "b"]),
                  $$jscomp$templatelit$m854121055$2$$ =
                      $$jscomp_createTemplateTagFirstArg$$(["a", "b"]);
              $tag$$($$jscomp$templatelit$m854121055$0$$, 42);
              $tag$$($$jscomp$templatelit$m854121055$1$$, 42);
              $tag$$($$jscomp$templatelit$m854121055$2$$, -42);
            }).call(undefined, $jscomp.getRestArguments, $jscomp.createTemplateTagFirstArg);
            """));
  }

  @Test
  public void bigintLiteral_passedThroughUntranspiled() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);

    test(closureUnaware("return 123n + 456n;"), expectedClosureUnaware("return 579n;"));
  }

  @Test
  public void untranspilableRegexFeature_passedThroughUntranspiled() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2015);

    // ES2018 feature: https://github.com/tc39/proposal-regexp-dotall-flag
    // ES2022 feature: https://github.com/tc39/proposal-regexp-match-indices
    testSame(closureUnaware("return /a/sd"));
  }

  @Test
  public void computedGetterSetter_inEs5Out_passedThroughUntranspiled() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);
    // JSCompiler does not support transpiling computed getters/setters in object literals down to
    // ES5. For closureUnaware, verify they are passed through unchanged.
    testSame(
        closureUnaware(
            """
            return {
              get [x()]() {
                return 1;
              },
              set [x()](a) {}
            };
            """));
  }

  @Test
  public void generator_cannotConvertCaseStatementContainingYield_errors() {
    setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);
    testError(
        closureUnaware("function *f(b, i) {switch (i) { case yield: return b; }} return f;"),
        TranspilationUtil.CANNOT_CONVERT_YET);
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
