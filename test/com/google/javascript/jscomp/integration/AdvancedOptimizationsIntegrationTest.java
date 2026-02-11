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

package com.google.javascript.jscomp.integration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.CodingConventions;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DiagnosticGroupWarningsGuard;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import com.google.javascript.jscomp.WarningLevel;
import com.google.javascript.jscomp.js.RuntimeJsLibManager;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.NodeSubject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for the compiler running with {@link CompilationLevel#ADVANCED_OPTIMIZATIONS}.
 */
@RunWith(JUnit4.class)
public final class AdvancedOptimizationsIntegrationTest extends IntegrationTestCase {

  @Test
  public void testGoogProvideAndRequire_used() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);

    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addClosureExterns()
                .buildExternsFile("externs.js"));
    test(
        options,
        new String[] {
          """
          goog.provide('goog.string.Const');
          /** @constructor */goog.string.Const = function() {};
          goog.string.Const.from = function(x) { console.log(x)};
          """,
          """
          goog.module('test');
          const Const = goog.require('goog.string.Const');
          Const.from('foo');
          """
        },
        new String[] {
          """
          goog.string = {};
          goog.string.Const = function() {};
          goog.string.Const.from = function() { console.log('foo')};
          """,
          "goog.string.Const.from();"
        });

    test(
        options,
        new String[] {
          """
          goog.provide('goog.string.Const');
          /** @constructor */goog.string.Const = function() {};
          goog.string.Const.from = function(x) { console.log(x)};
          """,
          """
          goog.module('test');
          const {from} = goog.require('goog.string.Const');
          from('foo');
          """
        },
        new String[] {
          """
          goog.string = {};
          goog.string.Const = function() {};
          goog.string.Const.from = function() { console.log('foo')};
          """,
          "(0,goog.string.Const.from)();"
        });
  }

  @Test
  public void testObjectDestructuring_forLoopInitializer_doesNotCrash() {
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);

    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    externs =
        ImmutableList.of(
            new TestExternsBuilder().addObject().addConsole().buildExternsFile("externs.js"));
    test(
        options,
        """
        /** @suppress {uselessCode} */
        function isDeclaredInLoop(path) {
          for (let {
              parentPath,
              key
            } = path;
            parentPath; {
              parentPath,
              key
            } = parentPath) {
            return isDeclaredInLoop(parentPath);
          }
          return false;
        }
        isDeclaredInLoop({parentPath: 'jh', key: 2});
        """,
        """
        /** @suppress {uselessCode} */
        function isDeclaredInLoop(path) {
          var $jscomp$loop$98447280$2 = {parentPath:void 0, key:void 0};
          for (function($jscomp$loop$98447280$2) {
            return function($jscomp$destructuring$var1) {
              $jscomp$loop$98447280$2.parentPath = $jscomp$destructuring$var1.parentPath;
              $jscomp$loop$98447280$2.key = $jscomp$destructuring$var1.key;
              return $jscomp$destructuring$var1;
            };
          }($jscomp$loop$98447280$2)(path);
          $jscomp$loop$98447280$2.parentPath;
          $jscomp$loop$98447280$2 = {
            parentPath:$jscomp$loop$98447280$2.parentPath,
            key:$jscomp$loop$98447280$2.key
          },
          function($jscomp$loop$98447280$2) {
            return function($jscomp$destructuring$var3) {
              $jscomp$loop$98447280$2.parentPath = $jscomp$destructuring$var3.parentPath;
              $jscomp$loop$98447280$2.key = $jscomp$destructuring$var3.key;
              return $jscomp$destructuring$var3;
            };
          }($jscomp$loop$98447280$2)($jscomp$loop$98447280$2.parentPath)) {
            return isDeclaredInLoop($jscomp$loop$98447280$2.parentPath);
           }
          return !1;
         }
        isDeclaredInLoop({parentPath:"jh", key:2});
        """);
  }

  @Test
  public void testVariableUsedAsArgumentMultipleTimes() {
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addObject().addConsole().buildExternsFile("externs.js"));
    test(
        options,
        """
        let value = 10;
        function add(a,b) {
         return (value = 0)+a+b;
        }
        let f = function() {
          return add(++value, ++value);
        };
        console.log(f());
        """,
        """
        let a = 10;
        var b = console, c = b.log, d, e = ++a, f = ++a;
        d = (a = 0) + e + f;
        c.call(b, d);
        """);
  }

  @Test
  public void testTSVariableReassignmentAndAliasingDueToDecoration() {
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addObject()
                .addConsole()
                .addClosureExterns()
                .addExtra(
                    // simulate "const tslib_1 = goog.require('tslib');",
                    """
                    var tslib_1 = {
                      __decorate: function(decorators, clazz) {}
                    };
                    """)
                .buildExternsFile("externs.js"));
    test(
        options,
        """
        /* output - 1387 characters long */
        goog.module('main');
        var module = module || { id: 'main.ts' };
        var Foo_1;
        /**
         * @fileoverview added by tsickle
         * Generated from: main.ts
         * @suppress {checkTypes} added by tsickle
         * @suppress {extraRequire} added by tsickle
         * @suppress {missingRequire} added by tsickle
         * @suppress {uselessCode} added by tsickle
         * @suppress {missingReturn} added by tsickle
         * @suppress {missingOverride} added by tsickle
         * @suppress {const} added by tsickle
         */
        /**
         * @param {?} arg
         * @return {?}
         */
        function noopDecorator(arg) { return arg; }
        let Foo = Foo_1 = class Foo {
            /**
             * @public
             * @return {void}
             */
            static foo() {
                console.log('Hello');
            }
            /**
             * @public
             * @return {void}
             */
            bar() {
                Foo_1.foo();
                console.log('ID: ' + Foo_1.ID + '');
            }
        };
        Foo.ID = 'original';
        Foo.ID2 = Foo_1.ID;
        (() => {
            Foo_1.foo();
            console.log('ID: ' + Foo_1.ID + '');
        })();
        Foo = Foo_1 = tslib_1.__decorate([
            noopDecorator
        ], Foo);
        /* istanbul ignore if */
        if (false) {
            /**
             * @type {string}
             * @public
             */
            Foo.ID;
            /**
             * @type {string}
             * @public
             */
            Foo.ID2;
            /* Skipping unhandled member: static {
                Foo.foo();
                console.log('ID: ' + Foo.ID + '');
              }*/
        }
        new Foo().bar();
        """,
        """
        var a, b = a = function() {
        };
        b.a = 'original';
        b.c = a.a;
        // TODO: b/299055739 - a.b() is not defined because its definition was removed
        a.b();
        console.log('ID: ' + a.a);
        b = a = tslib_1.__decorate([function(c) {
          return c;
        }], b);
        new b();
        // TODO: b/299055739 - a.b() is not defined because its definition was removed
        a.b();
        console.log('ID: ' + a.a);
        """);
  }

  @Test
  public void testFoldSpread() {
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);
    externs =
        ImmutableList.of(
            new TestExternsBuilder().addObject().addConsole().buildExternsFile("externs.js"));
    test(
        options,
        "function foo(x) {console.log(x);} foo(...(false ? [0] : [1]))",
        // tests that we do not generate (function(a) { console.log(a); })(...[1]);
        "console.log(1)");
  }

  @Test
  public void testBug303058080() {
    // Avoid including the transpilation library
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRewritePolyfills(true);
    options.setPrettyPrint(true);

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addExtra(
                    "/**",
                    " * @template T",
                    " * @record",
                    " */",
                    "var Foo = function() {};",
                    "",
                    "/**",
                    " * @return {!Foo<!Array<T>>}",
                    " */",
                    "Foo.prototype.partition = function() {};",
                    "",
                    "/**",
                    " * @return {!Foo<?>}",
                    " */",
                    "Foo.prototype.flatten = function() {};",
                    "",
                    "/**",
                    " * @extends {Foo}",
                    " * @template T",
                    " * @record",
                    " */",
                    "var Bar = function() {};",
                    "",
                    "/**",
                    " * @return {!Foo<T>}",
                    " */",
                    "Bar.prototype.flatten = function() {};")
                .buildExternsFile("externs.js"));
    // The timeout happened while processing the externs, so there's no need to have any input
    // code.
    test(options, "", "");
  }

  @Test
  public void testBug123583793() {
    // Avoid including the transpilation library
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRewritePolyfills(true);
    options.setPrettyPrint(true);

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addObject().addConsole().buildExternsFile("externs.js"));
    test(
        options,
        """
        function foo() {
          const {a, ...rest} = {a: 1, b: 2, c: 3};
          return {a, rest};
        };
        console.log(foo());
        """,
        """
        var a = console,
            b = a.log,
            c = {a:1, b:2, c:3},
            d = Object.assign({}, c),
            e = c.a,
            f = (delete d.a, d);
        b.call(a, {a:e, d:f});
        """);
    assertThat(lastCompiler.getRuntimeJsLibManager().getInjectedLibraries())
        .contains("es6/object/assign");
  }

  @Test
  public void testBug173319540() {
    // Avoid including the transpilation library
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setPrettyPrint(true);
    options.setGeneratePseudoNames(true);

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addAsyncIterable()
                .addConsole()
                .addExtra(
                    // fake async iterator to use in the test code
                    "/** @type {!AsyncIterator<Array<String>>} */",
                    "var asyncIterator;",
                    "",
                    // Externs to take the place of the injected library code
                    "const $jscomp = {};",
                    "",
                    "/**",
                    " * @param {",
                    " *     string|!AsyncIterable<T>|!Iterable<T>|!Iterator<T>|!Arguments",
                    " *   } iterable",
                    " * @return {!AsyncIteratorIterable<T>}",
                    " * @template T",
                    " * @suppress {reportUnknownTypes}",
                    " */",
                    "$jscomp.makeAsyncIterator = function(iterable) {};",
                    "")
                .buildExternsFile("externs.js"));
    test(
        options,
        """
        async function foo() {
          for await (const [key, value] of asyncIterator) {
            console.log(key,value);
          }
        }
        foo();
        """,
"""
(async function() {
  var $$jscomp$forAwait$retFn0$$;
  try {
    for (var $$jscomp$forAwait$tempIterator0$$ = (0, $jscomp.makeAsyncIterator)(asyncIterator);;) {
      var $$jscomp$forAwait$tempResult0$$ = await $$jscomp$forAwait$tempIterator0$$.next();
      if ($$jscomp$forAwait$tempResult0$$.done) {
        break;
      }
      const [$key$$, $value$jscomp$2$$] = $$jscomp$forAwait$tempResult0$$.value;
      console.log($key$$, $value$jscomp$2$$);
}
  } catch ($$jscomp$forAwait$catchErrParam0$$) {
    var $$jscomp$forAwait$errResult0$$ = {$error$:$$jscomp$forAwait$catchErrParam0$$};
  } finally {
    try {
      $$jscomp$forAwait$tempResult0$$ && !$$jscomp$forAwait$tempResult0$$.done && ($$jscomp$forAwait$retFn0$$ = $$jscomp$forAwait$tempIterator0$$.return) && await $$jscomp$forAwait$retFn0$$.$call$($$jscomp$forAwait$tempIterator0$$);
    } finally {
      if ($$jscomp$forAwait$errResult0$$) {
        throw $$jscomp$forAwait$errResult0$$.$error$;
      }
    }
  }
})();
""");
  }

  @Test
  public void testBug196083761() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_2020);
    options.setPrettyPrint(true);
    options.setGeneratePseudoNames(true);

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addClosureExterns()
                .buildExternsFile("externs.js"));
    test(
        options,
        new String[] {
          """
          goog.module('base');
          class Base {
            constructor({paramProblemFunc = problemFunc} = {}) {
              /** @public */
              this.prop = paramProblemFunc();
            }
          }

          const problemFunc = () => 1;
          Base.problemFunc = problemFunc;

          exports = {Base};
          """,
          """
          goog.module('child');

          const {Base} = goog.require('base');

          class Child extends Base {
            constructor({paramProblemFunc = Base.problemFunc} = {}) {
              super({paramProblemFunc});
            }
          }

          exports = {Child};
          """,
          """
          goog.module('grandchild');

          const {Child} = goog.require('child');

          class GrandChild extends Child {
            constructor() {
              super({paramProblemFunc: () => GrandChild.problemFunc() + 1});
            }
          }

          console.log(new GrandChild().prop);
          """,
        },
        new String[] {
          "",
          "",
          """
          const $module$contents$base_problemFunc$$ = () => 1;
          var $module$exports$base$Base$$ = class {
            constructor(
                {
                  $paramProblemFunc$:$paramProblemFunc$$ =
                      $module$contents$base_problemFunc$$
                } = {}) {
              this.$a$ = $paramProblemFunc$$();
            }
          }, $module$exports$child$Child$$ = class extends $module$exports$base$Base$$ {
            constructor(
                {
                  $paramProblemFunc$:$paramProblemFunc$jscomp$1$$ =
                      $module$contents$base_problemFunc$$
                } = {}) {
              super({$paramProblemFunc$:$paramProblemFunc$jscomp$1$$});
            }
          };
          class $module$contents$grandchild_GrandChild$$
              extends $module$exports$child$Child$$ {
            constructor() {
          // TODO(b/196083761): Fix this!
          // NOTE the call to `null()` here!
              super({$paramProblemFunc$:() => null() + 1});
            }
          }
          console.log((new $module$contents$grandchild_GrandChild$$).$a$);
          """
        });
  }

  @Test
  public void testDisambiguationOfForwardReferencedAliasedInterface() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);
    options.setDisambiguateProperties(true);
    options.setPrettyPrint(true);
    options.setPreserveTypeAnnotations(true);
    externs =
        ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs.js"));
    test(
        options,
        """
        // base class doesn't say it implements AliasedInterface,
        // but it does have a matching `foo()` method.
        class MyBaseClass {
            /** @return {void} */
            foo() {
                console.log('I should exist');
            }
        }

        // subclass claims to implement the interface, relying on the superclass implementation
        // of `foo()`.
        // Note that `AliasedInterface` isn't defined yet.
        /** @implements {AliasedInterface} */
        class MyClass extends MyBaseClass {
        }

        // AliasedInterface is originally defined using a different name.
        // This can happen due to module rewriting even if the original
        // code doesn't appear to do this.
        /** @record */
        function OriginalInterface() { }
        /** @return {void} */
        OriginalInterface.prototype.foo = function () { };

        // AliasedInterface is defined after it was used above.
        // Because of this the compiler previously failed to connect the `foo()` property
        // on `OriginalInterface` with the one on `MyBaseClass`, leading to a disambiguation
        // error.
        /** @const */
        const AliasedInterface = OriginalInterface;

        // Factory function ensures that the call to `foo()` below
        // is seen as `AliasedInterface.prototype.foo` rather than
        // `MyClass.prototype.foo`.
        /** @return {!AliasedInterface} */
        function magicFactory() {
            return new MyClass();
        }

        magicFactory().foo();
        """,
        // Compiler correctly recognises the call to `foo()` as referencing
        // the implementation in MyBaseClass, so that implementation ends
        // up inlined as the only output statement.
        "console.log('I should exist');");
  }

  @Test
  public void testDisambiguationOfForwardReferenedTemplatizedAliasedInterface() {
    // This test case is nearly identical to the one above.
    // The only difference here is the interfaces are templatized to makes sure
    // resolving the templates doesn't interfere with handling the aliasing
    // of the interface.
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);
    options.setDisambiguateProperties(true);
    options.setPrettyPrint(true);
    options.setPreserveTypeAnnotations(true);
    externs =
        ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs.js"));
    test(
        options,
        """
        // base class doesn't say it implements AliasedInterface,
        // but it does have a matching `foo()` method.
        class MyBaseClass {
            /** @return {string} */
            foo() {
                console.log('I should exist');
                return 'return value';
            }
        }

        // subclass claims to implement the interface, relying on the superclass implementation
        // of `foo()`.
        // Note that `AliasedInterface` isn't defined yet.
        /** @implements {AliasedInterface<string>} */
        class MyClass extends MyBaseClass {
        }

        // AliasedInterface is originally defined using a different name.
        // This can happen due to module rewriting even if the original
        // code doesn't appear to do this.
        /**
         * @record
         * @template T
         */
        function OriginalInterface() { }
        /** @return {T} */
        OriginalInterface.prototype.foo = function () { };

        // AliasedInterface is defined after it was used above.
        // Because of this the compiler previously failed to connect the `foo()` property
        // on `OriginalInterface` with the one on `MyBaseClass`, leading to a disambiguation
        // error.
        /** @const */
        const AliasedInterface = OriginalInterface;

        // Factory function ensures that the call to `foo()` below
        // is seen as `AliasedInterface.prototype.foo` rather than
        // `MyClass.prototype.foo`.
        /** @return {!AliasedInterface<string>} */
        function magicFactory() {
            return new MyClass();
        }

        magicFactory().foo();
        """,
        // Compiler correctly recognises the call to `foo()` as referencing
        // the implementation in MyBaseClass, so that implementation ends
        // up inlined as the only output statement.
        "console.log('I should exist');");
  }

  @Test
  public void testGithubIssue3940_logicalOrAssignCallee() {

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);

    test(
        options,
        "window['bug'] = function() { var arr; (arr || (arr = [])).push(0); return arr; }",
        "window.bug = function() { var a; (a ||= []).push(0); return a; }");
  }

  @Test
  public void testBigInt() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    // Confirm that the native BigInt type has been implemented, so the BigInt externs don't cause
    // errors.
    externs = ImmutableList.of(new TestExternsBuilder().addBigInt().buildExternsFile("externs.js"));
    testSame(options, "BigInt(1)");
  }

  @Test
  public void testFunctionThatAcceptsAndReturnsBigInt() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);
    externs =
        ImmutableList.of(
            new TestExternsBuilder().addBigInt().addConsole().buildExternsFile("externs.js"));
    test(
        options,
        """
        /**
         * @param {bigint} value
         * @return {bigint}
         */
        function bigintTimes3(value){
          return value * 3n;
        }
        console.log(bigintTimes3(5n));
        """,
        "console.log(15n);");
  }

  @Test
  public void testBigIntStringConcatenation() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);
    externs = ImmutableList.of(new TestExternsBuilder().addBigInt().buildExternsFile("externs.js"));
    test(options, "1n + 'asdf'", "'1nasdf'");
  }

  @Test
  public void testUnusedTaggedTemplateLiteralGetsRemoved() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    options.setPrettyPrint(true);

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addArray()
                .addExtra(
                    "var goog;",
                    "",
                    "/**",
                    " * @param {string} msg",
                    " * @return {string}",
                    " * @nosideeffects",
                    " */",
                    "goog.getMsg = function(msg) {};",
                    "",
                    "/**",
                    " * @param {...*} template_args",
                    " * @return {string}",
                    " */",
                    "function $localize(template_args){}",
                    "/**",
                    " * @constructor",
                    " * @extends {Array<string>}",
                    " */",
                    "var ITemplateArray = function() {};",
                    "")
                .buildExternsFile("externs.js"));
    test(
        options,
        """
        var i18n_7;
        var ngI18nClosureMode = true;
        if (ngI18nClosureMode) {
            /**
             * @desc Some message
             */
            const MSG_A = goog.getMsg("test");
            i18n_7 = MSG_A;
        }
        else {
            i18n_7 = $localize `...`;
        }
        console.log(i18n_7);
        """,
        // Tagged template literal was correctly removed.
        "console.log(goog.getMsg('test'));");
  }

  @Test
  public void testClassConstructorSuperCallIsNeverRemoved() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.OFF);

    testSame(
        options,
        """
        function Super() { } // A pure function that *might* be used as a constructor.

        class Sub extends Super {
          constructor() {
        // We can't delete this call despite being pointless. It's syntactically required.
            super();
        // NOTE: this alert is here to prevent the entire constructor from being removed. Any
        // statement after the super call will do.
            alert('hi');
          }
        }

        // We have to use the class somehow or the entire this is removable.
        alert(new Sub());
        """);
  }

  @Test
  public void testForOfDoesNotFoolSideEffectDetection() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);

    // we don't want to see injected library code in the output
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);

    test(
        options,
        """
        class C {
          constructor(elements) {
            this.elements = elements;
            // This call will be preserved, but inlined, because it has side effects.
            this.m1();
          }
          // m1() must be considered to have side effects because it taints a non-local object
          // through basically this.elements[i].sompProp.
          m1() {
            for (const element of this.elements) {
              element.someProp = 1;
            }
          }
        }
        new C([]);
        """,
        """
        class C {
          constructor(        ) {
            this.elements =       [];
            for (const element of this.elements) {
              element.someProp = 1;
            }
          }
        }
        new C(  );
        """);
  }

  @Test
  public void testIssue2822() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        """
        function classCallCheck(obj, ctor) {
          if (!(obj instanceof ctor)) {
            throw new Error('cannot call a class as a function');
          }
        }
        /** @constructor */
        var C = function InnerC() {
        // Before inlining happens RemoveUnusedCode sees one use of InnerC,
        // which prevents its removal.
        // After inlining it sees `this instanceof InnerC` as the only use of InnerC.
        // Make sure RemoveUnusedCode recognizes that the value of InnerC escapes.
          classCallCheck(this, InnerC);
        };
        // This creates an instance of InnerC, so RemoveUnusedCode should not replace
        // `this instanceof InnerC` with `false`.
        alert(new C());
        """,
        """
        alert(
            new function a() {
              if (!(this instanceof a)) {
                throw Error("cannot call a class as a function");
              }
            })
        """);
  }

  @Test
  public void testNoInline() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        """
        var namespace = {};
        /** @noinline */ namespace.foo = function() { alert('foo'); };
        namespace.foo();
        """,
        """
        function a() { alert('foo'); }
        a();
        """);
    test(
        options,
        """
        var namespace = {};
        namespace.foo = function() { alert('foo'); };
        namespace.foo();
        """,
        "alert('foo');");
  }

  @Test
  public void testIssue2365() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.addWarningsGuard(
        new DiagnosticGroupWarningsGuard(DiagnosticGroups.CHECK_TYPES, CheckLevel.OFF));

    // With type checking disabled we should assume that extern functions (even ones known not to
    // have any side effects) return a non-local value, so it isn't safe to remove assignments to
    // properties on them.
    // noSideEffects() and the property 'value' are declared in the externs defined in
    // IntegrationTestCase.
    testSame(options, "noSideEffects().value = 'something';");
  }

  @Test
  public void testAdvancedModeRemovesLocalTypeAndAlias() {
    // An extra pass of RemoveUnusedCode used to be conditional.  This specific code pattern
    // wasn't removed without it.  Verify it is removed.
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        """
        (function() {
          /** @constructor} */
          function Bar() {}
          var y = Bar;
          new y();
        })();
        """,
        "");
  }

  @Test
  public void testBug139862607() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        new String[] {
          "var x = 1;",
          """
          import * as y from './i0.js';
          const z = y.x
          """
        },
        // Property x never defined on module$i0
        DiagnosticGroups.MISSING_PROPERTIES);
  }

  @Test
  public void testReferenceToInternalClassName() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);
    options.setEmitUseStrict(false); // 'use strict'; is just noise here
    options.setPrettyPrint(true);
    options.setGeneratePseudoNames(true);

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addExtra("function use(x) {}").buildExternsFile("externs"));
    test(
        options,
        new String[] {
          TestExternsBuilder.getClosureExternsAsSource(),
          """
          goog.module('a.b.c');
          exports = class Foo {
          // Reference to static property via inner class name must be changed
          // to reference via global name (AggressiveInlineAliases), then collapsed
          // (CollapseProperties) to get full optimization and avoid generating broken code.
            method() { return Foo.EventType.E1; }
          };

          /** @enum {number} */
          exports.EventType = {
            E1: 1,
          };
          """,
          """
          goog.module('a.b.d');
          const Foo = goog.require('a.b.c');
          use(new Foo().method());
          """
        },
        new String[] {"", "", "use(1)"});
  }

  @Test
  public void testSourceInfoForReferenceToImportedSymbol() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);
    options.setEmitUseStrict(false); // 'use strict'; is just noise here
    options.setPrettyPrint(true);
    options.setPrintSourceAfterEachPass(true);

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addExtra("function use(x) {}").buildExternsFile("externs"));
    test(
        options,
        new String[] {
          TestExternsBuilder.getClosureExternsAsSource(),
          """
          goog.module('a.b.c');
          /** @noinline */
          function foo() {}
          exports.foo = foo;
          """,
          """
          goog.module('a.b.d');
          const {foo} = goog.require('a.b.c');
          use(foo());
          """
        },
        new String[] {"", "", "function a() {} use(a())"});

    // first child is externs tree, second is sources tree
    final Node srcsRoot = lastCompiler.getRoot().getLastChild();
    final Node closureExternsAsSourceScriptNode = srcsRoot.getFirstChild();
    final Node definitionScript = closureExternsAsSourceScriptNode.getNext();
    final Node usageScript = definitionScript.getNext();

    // Find the `a` node of `use(a());` which originally was `use(foo())`
    final Node referenceToFoo =
        usageScript
            .getLastChild() // `use(a());` EXPR_RESULT statement
            .getOnlyChild() // `use(a())` CALL expression
            .getSecondChild() // `a()` CALL expression
            .getFirstChild(); // `a`

    // Original usageScript line 4: `foo` of `use(foo());`
    // Compiled usageScript statement: `a` of `use(a());`
    assertNode(referenceToFoo)
        .isName("a")
        .hasSourceFileName(usageScript.getSourceFileName())
        .hasLineno(3)
        .hasCharno(4); // `use(` is 4 chars
  }

  @Test
  public void testClassExpressionExtendingSuperclassSideEffectingConstructor() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addExtra("function use(x) {}").buildExternsFile("externs"));

    test(
        options,
        "class Parent { constructor() { use(42); } } new class extends Parent {}",
        // we could do better simplifying this code, but at least the output is correct.
        "class a { constructor() { use(42); } } new class extends a {}");
  }

  @Test
  public void testArrayValuesIsPolyfilledForEs2015Out() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setRewritePolyfills(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);

    compile(options, new String[] {"for (const x of [1, 2, 3].values()) { alert(x); }"});

    assertThat(lastCompiler.getResult().errors).isEmpty();
    assertThat(lastCompiler.getResult().warnings).isEmpty();
    assertThat(lastCompiler.getRuntimeJsLibManager().getInjectedLibraries())
        .containsExactly("es6/array/values");
  }

  @Test
  public void testWindowIsTypedEs6() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setCheckTypes(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);
    test(
        options,
        """
        for (var x of []); // Force injection of es6_runtime.js
        var /** number */ y = window;
        """,
        DiagnosticGroups.CHECK_TYPES);
  }

  @Test
  public void testRemoveUnusedClass() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    String code =
        """
        /** @constructor */ function Foo() { this.bar(); }
        Foo.prototype.bar = function() { return new Foo(); };
        """;
    test(options, code, "");
  }

  @Test
  public void testClassWithGettersIsRemoved() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "class Foo { get xx() {}; set yy(v) {}; static get init() {}; static set prop(v) {} }";

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(options, code, "");
  }

  @Test
  public void testExportOnClassGetter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    test(
        options,
        "class C { /** @export @return {string} */ get exportedName() {} }; (new C).exportedName;",
        "class a {get exportedName(){}} (new a).exportedName;");
  }

  @Test
  public void testExportOnClassSetter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    test(
        options,
        "class C { /** @export @return {string} */ set exportedName(x) {} }; (new C).exportedName;",
        "class a {set exportedName(b){}} (new a).exportedName;");
  }

  @Test
  public void testExportOnStaticGetter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    test(
        options,
        """
        /** @export */
        class C {
          /** @export @return {string} */ static get exportedName() {}
        };
        alert(C.exportedName);
        """,
        "class a {static get exportedName(){}} goog.exportSymbol('C', a); alert(a.exportedName)");
  }

  @Test
  public void testExportOnStaticSetter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPrettyPrint(true);
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);

    test(
        options,
        """
        /** @const */
        var $jscomp = { global: window };

        class C {
          /** @export @param {number} x */ static set exportedName(x) {}
        };
        C.exportedName = 0;
        """,
        """
        var a = window;
        function b() {}
        b.exportedName;
        a.Object.defineProperties(
            b,
            {
              exportedName: { configurable:!0, enumerable:!0, set:function() {} }
            });
        b.exportedName = 0;
        """);
  }

  // Github issue #1540: https://github.com/google/closure-compiler/issues/1540
  @Test
  public void testFlowSensitiveInlineVariablesUnderAdvanced() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    options.setCheckSymbols(false);
    test(
        options,
        """
        function f(x) {
          var a = x + 1;
          var b = x + 1;
          window.c = x > 5 ? a : b;
        }
        f(g);
        """,
        "window.a = g + 1;");
  }

  // http://blickly.github.io/closure-compiler-issues/#724
  @Test
  public void testIssue724() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setCheckSymbols(false);
    options.setCheckTypes(false);
    String code =
        """
        isFunction = function(functionToCheck) {
          var getType = {};
          return functionToCheck &&
              getType.toString.apply(functionToCheck) === '[object Function]';
        };
        """;
    String result =
        """
        isFunction = function(a){
          var b={};
          return a && b.toString.apply(a) === '[object Function]';
        }
        """;

    test(options, code, result);
  }

  // http://blickly.github.io/closure-compiler-issues/#730
  @Test
  public void testIssue730() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    String code =
        """
        /** @constructor */function A() {this.foo = 0; Object.seal(this);}
        /** @constructor */function B() {this.a = new A();}
        B.prototype.dostuff = function() {this.a.foo++;alert('hi');}
        new B().dostuff();
        """;

    test(
        options,
        code,
        """
        function a() {
          this.b = 0;
          Object.seal(this);
        }
        (new function () {
          this.a = new a;
        }).a.b++;
        alert('hi');
        """);

    options.setRemoveUnusedClassProperties(true);

    // This is still not a problem when removeUnusedClassProperties is enabled.
    test(
        options,
        code,
        """
        function a() {
          this.b = 0;
          Object.seal(this);
        }
        (new function () {
          this.a = new a;
        }).a.b++;
        alert('hi');
        """);
  }

  @Test
  public void testAddFunctionProperties1() {
    String source =
        """
        var Foo = {};
        var addFuncProp = function(o) {
          o.f = function() {}
        };
        addFuncProp(Foo);
        alert(Foo.f());
        """;
    String expected = "alert(void 0);";
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    test(options, source, expected);
  }

  @Test
  public void testAddFunctionProperties2a() {
    String source =
        """
        /** @constructor */ function F() {}
        var x = new F();
        /** @this {F} */
        function g() { this.bar = function() { alert(3); }; }
        g.call(x);
        x.bar();
        """;
    String expected =
        """
        var x = new function() {};
        /** @this {F} */
        (function () { this.bar = function() { alert(3); }; }).call(x);
        x.bar();
        """;

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setStrictModeInput(false);
    options.setAssumeStrictThis(false);
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    test(options, source, expected);
  }

  @Test
  public void testAddFunctionProperties2b() {
    String source =
        """
        /** @constructor */ function F() {}
        var x = new F();
        /** @this {F} */
        function g() { this.bar = function() { alert(3); }; }
        g.call(x);
        x.bar();
        """;
    String expected =
        """
        var x = new function() {};
        x.bar=function(){alert(3)};x.bar()
        """;

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    test(options, source, expected);
  }

  @Test
  public void testAddFunctionProperties3() {
    String source =
        """
        /** @constructor */ function F() {}
        var x = new F();
        /** @this {F} */
        function g(y) { y.bar = function() { alert(3); }; }
        g(x);
        x.bar();
        """;
    String expected =
        """
        var x = new function() {};
        /** @this {F} */
        (function (y) { y.bar = function() { alert(3); }; })(x);
        x.bar();
        """;

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.OFF);
    test(options, source, expected);
  }

  @Test
  public void testAddFunctionProperties4() {
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addAlert()
                .buildExternsFile("externs.js")); // add Closure base.js as srcs
    String source =
        TestExternsBuilder.getClosureExternsAsSource()
            + """
            /** @constructor */
            var Foo = function() {};
            goog.addSingletonGetter = function(o) {
              o.f = function() {
                return o.i || (o.i = new o);
              };
            };
            goog.addSingletonGetter(Foo);
            alert(Foo.f());
            """;
    String expected =
        """
        function Foo(){}
        Foo.f = function() { return Foo.i || (Foo.i = new Foo()); };
        alert(Foo.f());
        """;

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    test(options, source, expected);
  }

  /**
   * Make sure this doesn't compile to code containing
   *
   * <pre>a: break a;</pre>
   *
   * which doesn't work properly on some versions of Edge. See b/72667630.
   */
  @Test
  public void testNoSelfReferencingBreakWithSyntheticBlocks() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.UNDEFINED_VARIABLES, CheckLevel.OFF);
    options.setSyntheticBlockStartMarker("START");
    options.setSyntheticBlockEndMarker("END");
    test(
        options,
        """
        const D = false;
        /**
         * @param {string} m
         */
        function b(m) {
         if (!D) return;

         START('debug');
         alert('Shouldnt be here' + m);
         END('debug');
        }
        /**
         * @param {string} arg
         */
        function a(arg) {
          if (arg == 'log') {
            b('logging 1');
            b('logging 2');
          } else {
            alert('Hi!');
          }
        }

        a(input);
        """,
        "input != 'log' && alert('Hi!')");
  }

  @Test
  public void testSyntheticBlockCrashAroundModuleExport() {
    // Regression test for b/417822645
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setClosurePass(true);
    options.setWarningLevel(DiagnosticGroups.UNDEFINED_VARIABLES, CheckLevel.OFF);
    options.setSyntheticBlockStartMarker("START");
    options.setSyntheticBlockEndMarker("END");
    Compiler result =
        compile(
            options,
            """
            goog.module('foo.bar');
            START('hi');
            const Foo = {A: 1, B: 2};
            exports.Foo = Foo;
            Foo[1] = 'A';
            Foo[2] = 'B';

            exports.fn = function() {
              return Foo.A + Foo.B;
            }
            console.log(exports.fn());
            END('hi');
            """);
    assertThat(result.toSource())
        .isEqualTo(
            """
            START("hi");
            var a = {a:1, b:2, 1:"A", 2:"B"};
            console.c(a.a + a.b);
            END("hi");
            """);
  }

  // http://blickly.github.io/closure-compiler-issues/#1131
  @Test
  public void testIssue1131() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    // TODO(tbreisacher): Re-enable dev mode and fix test failure.
    options.setDevMode(DevMode.OFF);

    test(
        options,
        "function f(k) { return k(k); } alert(f(f));",
        "function a(b) { return b(b); } alert(a(a));");
  }

  // http://blickly.github.io/closure-compiler-issues/#284
  @Test
  public void testIssue284() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        """
        var ns = {};
        /**
         * @constructor
         * @extends {ns.PageSelectionModel.FooEvent}
         */
        ns.PageSelectionModel.ChangeEvent = function() {};
        /** @constructor */
        ns.PageSelectionModel = function() {};
        /** @constructor */
        ns.PageSelectionModel.FooEvent = function() {};
        /** @constructor */
        ns.PageSelectionModel.SelectEvent = function() {};
        goog.inherits(ns.PageSelectionModel.ChangeEvent,
            ns.PageSelectionModel.FooEvent);
        """,
        "");
  }

  // http://blickly.github.io/closure-compiler-issues/#1204
  @Test
  public void testIssue1204() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    test(
        options,
        """
        goog.scope(function () {
          /** @constructor */ function F(x) { this.x = x; }
          alert(new F(1));
        });
        """,
        "alert(new function(){}(1));");
  }

  @Test
  public void testSuppressEs5StrictWarning() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.WARNING);
    test(options, "/** @suppress{es5Strict} */ function f() { var arguments; }", "");
  }

  @Test
  public void testBrokenNameSpace() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    String code =
        """
        goog.provide('i.am.on.a.Horse');
        i.am.on.a.Horse = function() {};
        i.am.on.a.Horse.prototype.x = function() {};
        i.am.on.a.Boat = function() {};
        i.am.on.a.Boat.prototype.y = function() {}
        """;
    test(options, code, "");
  }

  @Test
  public void testNamelessParameter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.OFF);
    String code =
        """
        var impl_0;
        $load($init());
        function $load(){
          window['f'] = impl_0;
        }
        function $init() {
          impl_0 = {};
        }
        """;
    String result = "window.f = {};";
    test(options, code, result);
  }

  @Test
  public void testHiddenSideEffect() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    String code = "window.offsetWidth;";
    String result = "window.offsetWidth;";
    test(options, code, result);
  }

  @Test
  public void testNegativeZero() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setCheckSymbols(false);
    test(
        options,
        """
        function bar(x) { return x; }
        function foo(x) { print(x / bar(0));
                         print(x / bar(-0)); }
        foo(3);
        """,
        "print(3/0);print(3/-0);");
  }

  @Test
  public void testSingletonGetter1() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setCodingConvention(new ClosureCodingConvention());
    externs = ImmutableList.of();
    test(
        options,
        TestExternsBuilder.getClosureExternsAsSource()
            + """
            goog.addSingletonGetter = function(ctor) {
              ctor.getInstance = function() {
                return ctor.instance_ || (ctor.instance_ = new ctor());
              };
            };
            function Foo() {}
            goog.addSingletonGetter(Foo);
            Foo.prototype.bar = 1;
            function Bar() {}
            goog.addSingletonGetter(Bar);
            Bar.prototype.bar = 1;
            """,
        "");
  }

  @Test
  public void testInlineProperties() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    level.setTypeBasedOptimizationOptions(options);

    String code =
        """
        var ns = {};
        /** @constructor */
        ns.C = function () {this.someProperty = 1}
        alert(new ns.C().someProperty + new ns.C().someProperty);
        """;
    assertThat(options.shouldInlineProperties()).isTrue();
    assertThat(options.shouldCollapseProperties()).isTrue();
    // CollapseProperties used to prevent inlining this property.
    test(options, code, "alert(2);");
  }

  // Due to JsFileParse not being supported in the JS version, the dependency parsing delegates to
  // the {@link CompilerInput$DepsFinder} class which is incompatible with the
  // DefaultCodingConvention due to it throwing on methods such as extractIsModuleFile which is
  // needed in {@link CompilerInput$DepsFinder#visitSubtree}. Disable this test in the JsVersion.
  // TODO(tdeegan): DepsFinder should error out early if run with DefaultCodingConvention.
  @Test
  public void testES6UnusedClassesAreRemovedDefaultCodingConvention() {
    testES6UnusedClassesAreRemoved(CodingConventions.getDefault());
  }

  @Test
  public void testNoDuplicateClassNameForLocalBaseClasses() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        """
        // Previously ConcretizeStaticInheritanceForInlining failed to take scope into account,
        // so it produced DUPLICATE_CLASS errors for code like this.
        function f1() {
          class Base {}
          class Sub extends Base {}
        }
        function f2() {
          class Base {}
          class Sub extends Base {}
        }
        alert(1);
        """,
        "alert(1)");
  }

  // Tests that unused classes are removed, even if they are passed to $jscomp.inherits.
  private void testES6UnusedClassesAreRemoved(CodingConvention convention) {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setCodingConvention(convention);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        """
        class Base {}
        class Sub extends Base {}
        alert(1);
        """,
        "alert(1)");
  }

  @Test
  public void testES6UnusedClassesAreRemoved() {
    testES6UnusedClassesAreRemoved(new ClosureCodingConvention());
    testES6UnusedClassesAreRemoved(new GoogleCodingConvention());
  }

  /**
   * @param js A snippet of JavaScript in which alert('No one ever calls me'); is called in a method
   *     which is never called. Verifies that the method is stripped out by asserting that the
   *     result does not contain the string 'No one ever calls me'.
   */
  private void testES6StaticsAreRemoved(String js) {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    Compiler compiler = compile(options, js);
    assertThat(compiler.getErrors()).isEmpty();
    String result = compiler.toSource(compiler.getRoot().getSecondChild());
    assertThat(result).isNotEmpty();
    assertThat(result).doesNotContain("No one ever calls me");
  }

  @Test
  public void testES6StaticsAreRemoved1() {
    testES6StaticsAreRemoved(
        """
        class Base {
          static called() { alert('I am called'); }
          static notCalled() { alert('No one ever calls me'); }
        }
        class Sub extends Base {
          static called() { super.called(); alert('I am called too'); }
          static notCalled() { alert('No one ever calls me'); }
        }
        Sub.called();
        """);
  }

  @Test
  public void testES6StaticsAreRemoved2() {
    testES6StaticsAreRemoved(
        """
        class Base {
          static calledInSubclassOnly() { alert('No one ever calls me'); }
        }
        class Sub extends Base {
          static calledInSubclassOnly() { alert('I am called'); }
        }
        Sub.calledInSubclassOnly();
        """);
  }

  @Test
  public void testExports() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code =
        """
        /** @constructor */ var X = function() {
        /** @export */ this.abc = 1;};
        /** @constructor */ var Y = function() {
        /** @export */ this.abc = 1;};
        alert(new X().abc + new Y().abc);
        """;

    options.setExportLocalPropertyDefinitions(false);

    // exports enabled, but not local exports
    compile(
        options,
        """
        /** @constructor */ var X = function() {
        /** @export */ this.abc = 1;};
        """);
    assertThat(lastCompiler.getErrors()).hasSize(1);

    options.setExportLocalPropertyDefinitions(true);

    // property name preserved due to export
    test(
        options,
        code,
        "alert((new function(){this.abc = 1}).abc + (new function(){this.abc = 1}).abc);");

    // unreferenced property not removed due to export.
    test(
        options,
        """
        /** @constructor */ var X = function() {
          /** @export */ this.abc = 1;
        };

        /** @constructor */ var Y = function() {
          /** @export */ this.abc = 1;
        };

        alert(new X() + new Y());
        """,
        "alert((new function(){this.abc = 1}) + (new function(){this.abc = 1}));");

    // disambiguate and ambiguate properties respect the exports.
    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setAmbiguateProperties(true);

    test(
        options,
        code,
        "alert((new function(){this.abc = 1}).abc + (new function(){this.abc = 1}).abc);");

    // unreferenced property not removed due to export.
    test(
        options,
        """
        /** @constructor */ var X = function() {
        /** @export */ this.abc = 1;};
        /** @constructor */ var Y = function() {
        /** @export */ this.abc = 1;};
        alert(new X() + new Y());
        """,
        "alert((new function(){this.abc = 1}) + (new function(){this.abc = 1}));");

    options.setPropertiesThatMustDisambiguate(ImmutableSet.of("abc"));

    test(options, code, DiagnosticGroups.TYPE_INVALIDATION);
  }

  // GitHub issue #250: https://github.com/google/closure-compiler/issues/250
  @Test
  public void testInlineStringConcat() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        """
        function f() {
          var x = '';
          x += '1';
          x += '2';
          x += '3';
          x += '4';
          x += '5';
          x += '6';
          x += '7';
          return x;
        }
        window.f = f;
        """,
        "window.a = function() { return '1234567'; }");
  }

  // GitHub issue #1234: https://github.com/google/closure-compiler/issues/1234
  @Test
  public void testOptimizeSwitchGithubIssue1234() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setCheckSymbols(false);
    test(
        options,
        """
        var exit;
        switch ('a') {
          case 'a':
            break;
          default:
            exit = 21;
            break;
        }
        switch(exit) {
          case 21: throw 'x';
          default : console.log('good');
        }
        """,
        "console.a('good');");
  }

  // GitHub issue #2122: https://github.com/google/closure-compiler/issues/2122
  @Test
  public void testNoRemoveSuperCallWithWrongArgumentCount() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    // skip the default externs b/c they include Closure base.js,
    // which we want in the srcs.
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addFunction()
                .addExtra("var window;")
                .buildExternsFile("externs.js"));
    test(
        options,
        TestExternsBuilder.getClosureExternsAsSource()
            + """
            /** @constructor */
            var Foo = function() {}
            /**
             * @param {number=} width
             * @param {number=} height
             */
            Foo.prototype.resize = function(width, height) {
              window.size = width * height;
            }
            /**
             * @constructor
             * @extends {Foo}
             */
            var Bar = function() {}
            goog.inherits(Bar, Foo);
            /** @override */
            Bar.prototype.resize = function(width, height) {
              Bar.base(this, 'resize', width);
            };
            (new Bar).resize(100, 200);
            """,
        """
        function a(){}a.prototype.a=function(b,e){window.c=b*e};
        function d(){}d.b=a.prototype;d.prototype.a=function(b){d.b.a.call(this,b)};
        (new d).a(100, 200);
        """);
  }

  // GitHub issue #2203: https://github.com/google/closure-compiler/issues/2203
  @Test
  public void testPureFunctionIdentifierWorksWithMultipleNames() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        """
        var SetCustomData1 = function SetCustomData2(element, dataName, dataValue) {
            var x = element['_customData'];
            x[dataName] = dataValue;
        }
        SetCustomData1(window, 'foo', 'bar');
        """,
        "window._customData.foo='bar';");
  }

  @Test
  public void testSpreadArgumentsPassedToSuperDoesNotPreventRemoval() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    // Avoid comparing all the polyfill code.
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);

    // include externs definitions for the stuff that would have been injected
    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "extraExterns", new TestExternsBuilder().addJSCompLibraries().build()));

    test(
        options,
        """
        class A {
          constructor(a, b) {
            this.a = a;
            this.b = b;
          }
        }
        class B extends A {
          constructor() {
            super(...arguments);
          }
        }
        var b = new B();
        """,
        "");
  }

  @Test
  public void testAngularPropertyNameRestrictions() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setAngularPass(true);

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 250; i++) {
      sb.append("window.foo").append(i).append("=true;\n");
    }

    Compiler compiler = compile(options, sb.toString());
    assertWithMessage(
            """
            Expected no warnings or errors
            Errors:
            %s
            Warnings:
            %s
            """,
            Joiner.on("\n").join(compiler.getErrors()),
            Joiner.on("\n").join(compiler.getWarnings()))
        .that(compiler.getErrors().size() + compiler.getWarnings().size())
        .isEqualTo(0);

    Node root = compiler.getRoot().getLastChild();
    assertThat(root).isNotNull();
    Node script = root.getFirstChild();
    assertThat(script).isNotNull();
    ImmutableSet<Character> restrictedChars =
        CompilerOptions.getAngularPropertyReservedFirstChars();
    for (Node expr = script.getFirstChild(); expr != null; expr = expr.getNext()) {
      NodeSubject.assertNode(expr).hasType(Token.EXPR_RESULT);
      NodeSubject.assertNode(expr.getFirstChild()).hasType(Token.ASSIGN);
      NodeSubject.assertNode(expr.getFirstFirstChild()).hasType(Token.GETPROP);
      Node getProp = expr.getFirstFirstChild();
      String propName = getProp.getString();
      assertThat(restrictedChars).doesNotContain(propName.charAt(0));
    }
  }

  @Test
  public void testCheckVarsOnInAdvancedMode() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(options, "var x = foobar;", DiagnosticGroups.UNDEFINED_VARIABLES);
  }

  @Test
  public void testInlineRestParam() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addAlert()
                .addFunction()
                .addExtra(
                    // Externs to take the place of the injected library code
                    "const $jscomp = {};",
                    "",
                    "/**",
                    " * @this {number}",
                    " * @noinline",
                    " */",
                    "$jscomp.getRestArguments = function() {};",
                    "")
                .buildExternsFile("externs.js"));

    test(
        options,
        """
        function foo() {
          var f = (...args) => args[0];
          return f(8);
        }
        alert(foo());
        """,
        """
        alert(function() {
          return $jscomp.getRestArguments.apply(0, arguments)[0];
        }(8))
        """);
  }

  @Test
  public void testInlineRestParamNonTranspiling() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        """
        function foo() {
          var f = (...args) => args[0];
          return f(8);
        }
        alert(foo());
        """,
        "alert(8)");
  }

  @Test
  public void testDefaultParameters() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        options,
        """
        function foo(a, b = {foo: 5}) {
          return a + b.foo;
        }
        alert(foo(3, {foo: 9}));
        """,
        "var a={a:9}; a=a===void 0?{a:5}:a;alert(3+a.a)");
  }

  @Ignore("b/78345133")
  // TODO(b/78345133): Re-enable if/when InlineFunctions supports inlining default parameters
  @Test
  public void testDefaultParametersNonTranspiling() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);

    test(
        options,
        """
        function foo(a, b = {foo: 5}) {
          return a + b.foo;
        }
        alert(foo(3, {foo: 9}));
        """,
        "alert(12);");
  }

  @Test
  public void testRestObjectPatternParameters() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addAlert()
                .addArray()
                .addFunction()
                .addExtra(
                    // Externs to take the place of the injected library code
                    "const $jscomp = {};",
                    "",
                    "/**",
                    " * @this {number}",
                    " * @noinline",
                    " */",
                    "$jscomp.getRestArguments = function() {};",
                    "")
                .buildExternsFile("externs.js"));

    test(
        options,
        """
        function countArgs(x, ...{length}) {
          return length;
        }
        alert(countArgs(1, 1, 1, 1, 1));
        """,
        """
        alert(function (a) {
          return $jscomp.getRestArguments.apply(1, arguments).length;
        }(1,1,1,1,1))
        """);
  }

  @Ignore
  @Test
  // TODO(tbreisacher): Re-enable if/when InlineFunctions supports rest parameters that are
  // object patterns.
  public void testRestObjectPatternParametersNonTranspiling() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    externs = DEFAULT_EXTERNS;

    test(
        options,
        """
        function countArgs(x, ...{length}) {
          return length;
        }
        alert(countArgs(1, 1, 1, 1, 1));
        """,
        "alert(4);");
  }

  @Test
  public void testQuotedDestructuringNotRenamed() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);

    test(options, "const {'x': x} = window; alert(x);", "const {x:a} = window; alert(a);");
    test(
        options,
        "const {x: {'log': y}} = window; alert(y);",
        "const {a: {log: a}} = window; alert(a);");
  }

  @Test
  public void testObjectLiteralPropertyNamesUsedInDestructuringAssignment() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addMath().addConsole().buildExternsFile("externs.js"));
    test(
        options,
        """
        const X = { a: 1, b: 2 };
        const { a, b } = X;
        console.log(a, b);
        """,
        "console.log(1,2);");

    test(
        options,
        """
        const X = { a: 1, b: 2 };
        let { a, b } = X;
        const Y = { a: 4, b: 5 };
        ({ a, b } = Y);
        console.log(a, b);
        """,
        """
        let {a, b} = {a:1, b:2};
        ({a, b} = {a:4, b:5});
        console.log(a, b);
        """);

    // Demonstrates https://github.com/google/closure-compiler/issues/3671
    test(
        options,
        """
        const X = { a: 1, b: 2 };
        const Y = { a: 1, b: 2 };
        // Conditional destructuring assignment makes it unsafe to collapse
        // the properties on X and Y.
        const { a, b } = Math.random() ? X : Y;
        console.log(a, b);
        """,
        """
        const a = { a: 1, b: 2},
              b = { a: 1, b: 2},
              {a:c, b:d} = Math.random() ? a : b;
        console.log(c, d);
        """);
  }

  /** Creates a CompilerOptions object with google coding conventions. */
  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setRenamePrefixNamespaceAssumeCrossChunkNames(true);
    options.setAssumeGettersArePure(false);
    // Make the test mismatch results easier to read
    options.setPrettyPrint(true);
    // Starting the output with `'use strict';` is just noise.
    options.setEmitUseStrict(false);
    return options;
  }

  @Test
  public void testCastInLhsOfAssign() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    test(
        options,
        // it's kind of weird that we allow casts on the lhs, but some existing code uses this
        // feature. this is just a regression test so we won't accidentally break this feature.
        "var /** string */ str = 'foo'; (/** @type {?} */ (str)) = 3;",
        "");
  }

  @Test
  public void testRestDoesntBlockPropertyDisambiguation() {
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    // TODO(b/116532470): the compiler should compile this down to nothing.
    test(
        options,
        """
        class C { f() {} }
        (new C()).f();
        const obj = {f: 3};
        const {f, ...rest} = obj;
        """,
        """
        function a() {}
        a.prototype.a = function() {};
        (new a).a();
        delete Object.assign({}, {a: 3}).a
        """);
  }

  @Test
  public void testObjectSpreadAndRest_optimizeAndTypecheck() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    test(
        options,
        new String[] {
          """
          /**
           * @param {{a: number, b: string}} x
           * @return {*}
           */
          function fun({a, b, ...c}) {
            const /** !{a: string, b: number} */ x = {a, b, ...c};
            const y = {...x};
            y['a'] = 5;

            let {...z} = y;
            return z;
          }

          alert(fun({a: 1, b: 'hello', c: null}));
          """
        },
        new String[] {
          """
          alert(function({b:a, c:b,...c}) {
          // TODO(b/123102446): We'd really like to collapse these chained assignments.
            a = {b:a, c:b, ...c, a:5};
            ({...a} = a);
            return a;
          }({b:1, c:'hello', d:null}));
          """
        },
        DiagnosticGroups.CHECK_TYPES);
  }

  @Test
  public void testObjectSpreadAndRest_inlineAndCollapseProperties() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);
    options.setPrettyPrint(true);
    options.setGeneratePseudoNames(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    // This is just a melange of various global namespace operations. The exact sequence of values
    // aren't important so much as trying out lots of combinations.
    test(
        options,
        """
        const a = {
          aa: 2,
          ab: 'hello',
        };

        a.ac = {
          aca: true,
          ...a,
          acb: false,
        };

        const {ab, ac,...c} = a;

        const d = ac;

        ({aa: d.acc} = a);

        alert(d.acc);
        """,
        """
        const a = {
          $aa$: 2,
          $ab$: 'hello',
        };

        a.$ac$ = {
          $aca$: !0,
          ...a,
          $acb$: !1,
        };

        const {$ab$: $ab$$, $ac$: $ac$$, ...$c$$} = a;

        ({$aa$: $ac$$.$acc$} = a);

        alert($ac$$.$acc$);
        """);
  }

  @Test
  public void testArrayDestructuringAndAwait_inlineAndCollapseProperties() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPrettyPrint(true);
    options.setGeneratePseudoNames(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addPromise()
                .addExtra("var window; function bar() {}")
                .buildExternsFile("externs.js"));

    // Test that the compiler can optimize out most of this code and that
    // InlineAndCollapseProperties does not report
    // "JSC_PARTIAL_NAMESPACE. Partial alias created for namespace $jscomp"
    test(
        options,
        """
        window['Foo'] = class Foo {
        async method() {
                  const [resultA, resultB] = await Promise.all([
                    bar(),
                    bar(),
                  ]);
          }

        }
        """,
        "window.Foo = function() {};");
  }

  @Test
  public void testOptionalCatchBinding_optimizeAndTypecheck() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    testSame(
        options,
        new String[] {
          """
          try {
            alert('try');
          } catch {
            alert('caught');
          }
          """
        });

    test(
        options,
        new String[] {
          """
          function doNothing() {}
          try {
           doNothing();
          } catch {
            alert('caught');
          }
          """
        },
        new String[] {});

    test(
        options,
        new String[] {
          """
          function doNothing() {}
          try {
           alert('try');
          } catch {
            doNothing();
          }
          """
        },
        new String[] {
          """
          try {
           alert('try');
          } catch {
          }
          """
        });
  }

  @Test
  public void testImportMeta() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);

    Compiler compiler = compile(options, "import.meta");
    assertThat(compiler.getResult().success).isFalse();
    assertThat(compiler.getErrors())
        .comparingElementsUsing(JSCompCorrespondences.DESCRIPTION_EQUALITY)
        .containsExactly(
            "This code cannot be transpiled. import.meta. Use --chunk_output_type=ES_MODULES to"
                + " allow passthrough support.");
  }

  @Test
  public void nullishCoalesceSimple() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    test(options, "var x = 0; var y = {}; alert(x ?? y)", "alert(0)");
  }

  @Test
  public void nullishCoalesceChain() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    test(options, "var x, y; alert(x ?? y ?? 'default string')", "alert('default string')");
  }

  @Test
  public void nullishCoalesceWithAnd() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    test(options, "var x, y, z; alert(x ?? (y && z))", "alert(void 0)");
  }

  @Test
  public void nullishCoalesceWithAssign() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    test(options, "var x, y; var z = 1; x ?? (y = z); alert(y)", "alert(1)");
  }

  @Test
  public void nullishCoalesceTranspiledOutput() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    externs =
        ImmutableList.of(new TestExternsBuilder().addExtra("var x, y").buildExternsFile("externs"));

    test(options, "x ?? y", "let a; (a = x) != null ? a : y");
  }

  @Test
  public void nullishCoalesceChainTranspiledOutput() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addExtra("var x, y, z").buildExternsFile("externs"));

    test(options, "x ?? y ?? z", "let a, b; (b = (a = x) != null ? a : y) != null ? b : z");
  }

  @Test
  public void nullishCoalescePassThroughExterns() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);

    externs =
        ImmutableList.of(new TestExternsBuilder().addExtra("var x, y").buildExternsFile("externs"));

    testSame(options, "x ?? y");
  }

  @Test
  public void nullishCoalescePassThroughLhsNull() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);

    test(options, "var x; var y = 1; x ?? y", "1");
  }

  @Test
  public void nullishCoalescePassThroughLhsNonNull() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);

    test(options, "var x = 0; var y = 1; x ?? y", "0");
  }

  @Test
  public void nullishCoalesceFromTranspiledTs38_inModule() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);

    // Ensure the compiler can optimize TS 3.8's transpilation of nullish coalescing. NOTE: it's
    // possible a future TSC version will change how nullish coalescing is transpiled, making this
    // test out-of-date.
    test(
        options,
        """
        goog.module('mod');
        function alwaysNull() { return null; }
        // transpiled form of `const y = alwaysNull() ? 42;`
        var _a;
        const y = (_a = alwaysNull()) !== null && _a !== void 0 ? _a : 42;
        alert(y);
        """,
        "alert(42);");
  }

  @Test
  public void nullishCoalesceFromTranspiledTs38_inFunction() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);

    // Ensure the compiler can optimize TS 3.8's transpilation of nullish coalescing.
    test(
        options,
        """
        goog.module('mod');
        function alwaysNull() { return null; }
        function getDefaultValue(maybeNull) {
        // transpiled form of `return maybeNull ?? 42;`
          var _a;
          return (_a = maybeNull) !== null && _a !== void 0 ? _a : 42;
        }
        alert(getDefaultValue(alwaysNull()));
        """,
        "alert(42);");
  }

  @Test
  public void testRewritePolyfills_noAddedCodeForUnusedPolyfill() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.STABLE_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRewritePolyfills(true);
    options.setGeneratePseudoNames(true);

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "testExterns.js",
                new TestExternsBuilder().addArray().addString().addObject().build()));
    test(options, "const unused = 'foo'.startsWith('bar');", "");
  }

  @Test
  public void testInjectPolyfillsNewerThan_noDeadCodeRemovalForUnusedPolyfills() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.STABLE_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setInjectPolyfillsNewerThan(LanguageMode.ECMASCRIPT_2018);
    options.setGeneratePseudoNames(true);

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "testExterns.js",
                new TestExternsBuilder().addConsole().addArray().addString().addObject().build()));
    compile(options, "console.log('hello world');");

    assertThat(lastCompiler.getErrors()).isEmpty();
    // String.prototype.trimEnd is in ES2019, so we expect the polyfill to be injected.
    assertThat(lastCompiler.toSource()).contains("String.prototype.trimEnd");
    // String.prototype.startsWith is in ES2015, so we expect do not expect the polyfill
    assertThat(lastCompiler.toSource()).doesNotContain("String.prototype.startsWith");
  }

  @Test
  public void
      testInjectPolyfillsNewerThan_noDeadCodeRemovalForUnusedPolyfills_withForceInjectLibrary() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.STABLE_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setInjectPolyfillsNewerThan(LanguageMode.ECMASCRIPT_2018);
    options.setGeneratePseudoNames(true);
    options.setForceLibraryInjection(ImmutableList.of("es6/string/startswith"));

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "testExterns.js",
                new TestExternsBuilder().addConsole().addArray().addString().addObject().build()));
    compile(options, "console.log('hello world');");

    assertThat(lastCompiler.getErrors()).isEmpty();
    // String.prototype.trimEnd is in ES2019, so we expect the polyfill to be injected.
    assertThat(lastCompiler.toSource()).contains("String.prototype.trimEnd");
    // String.prototype.startsWith is injected because of the force inject library flag, even though
    // it is in ES2015.
    assertThat(lastCompiler.toSource()).contains("String.prototype.startsWith");
    // String.prototype.endsWith is in ES2015, so we do not expect the polyfill (since
    // unlike String.prototype.startsWith, it is not forced to be injected)
    assertThat(lastCompiler.toSource()).doesNotContain("String.prototype.endsWith");
  }

  @Test
  public void testIsolatePolyfills_worksWithoutRewritePolyfills() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.STABLE_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRewritePolyfills(false);
    options.setIsolatePolyfills(true);
    options.setForceLibraryInjection(ImmutableList.of("es6/string/startswith"));
    options.setGeneratePseudoNames(true);

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "testExterns.js",
                new TestExternsBuilder().addArray().addString().addObject().build()));

    compile(options, "alert('foo'.startsWith('bar'));");
  }

  @Test
  public void testIsolatePolyfills_noAddedCodeForUnusedPolyfill() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.STABLE_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRewritePolyfills(true);
    options.setIsolatePolyfills(true);
    options.setGeneratePseudoNames(true);

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "testExterns.js",
                new TestExternsBuilder().addArray().addString().addObject().build()));

    test(
        options,
        "const unused = 'foo'.startsWith('bar');",
        "var $$jscomp$propertyToPolyfillSymbol$$={};");
  }

  @Test
  public void testIsolatePolyfills_noAddedCodeForUnusedSymbol() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.STABLE_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRewritePolyfills(true);
    options.setIsolatePolyfills(true);
    options.setGeneratePseudoNames(true);

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "testExterns.js",
                new TestExternsBuilder().addArray().addString().addObject().build()));

    test(
        options, "const unusedOne = Symbol('bar');", "var $$jscomp$propertyToPolyfillSymbol$$={};");
  }

  @Test
  public void testIsolatePolyfills_noPolyfillsUsed() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.STABLE_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRewritePolyfills(true);
    options.setIsolatePolyfills(true);
    options.setGeneratePseudoNames(true);

    externs =
        ImmutableList.of(
            SourceFile.fromCode("testExterns.js", new TestExternsBuilder().addAlert().build()));

    testSame(options, "alert('hi');");
  }

  @Test
  public void testModififyingDestructuringParamWithTranspilation() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    externs =
        ImmutableList.of(
            SourceFile.fromCode("testExterns.js", new TestExternsBuilder().addAlert().build()));

    test(
        options,
        """
        function installBaa({ obj }) {
         obj.baa = 'foo';
        }

        const obj = {};
        installBaa({ obj });
        alert(obj.baa);
        """,
        "alert('foo');");
  }

  @Test
  public void testModififyingDestructuringParamWithoutTranspilation() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);

    externs =
        ImmutableList.of(
            SourceFile.fromCode("testExterns.js", new TestExternsBuilder().addAlert().build()));

    test(
        options,
        """
        function installBaa({ obj }) {
         obj.baa = 'foo';
        }

        const obj = {};
        installBaa({ obj });
        alert(obj.baa);
        """,
        """
        const a = {};
        (function({b}){
          b.a = 'foo';
        })({b: a});
        alert(a.a);
        """);
  }

  @Test
  public void testAmbiguateClassInstanceProperties() {
    CompilerOptions options = createCompilerOptions();
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    test(
        options,
        """
        class Foo {
          constructor(foo) {
            this.foo = foo;
          }
        }
        class Bar {
          constructor(bar) {
            this.bar = bar;
          }
        }
        window['Foo'] = Foo;
        window['Bar'] = Bar;
        /**
         * @param {!Foo} foo
         * @param {!Bar} bar
         */
        window['foobar'] = function(foo, bar) {
          return foo.foo + bar.bar;
        }
        """,
        """
        class b {
          constructor(a) {
            this.a = a;
          }
        }
        class c {
          constructor(a) {
            this.a = a;
          }
        }
        window.Foo = b;
        window.Bar = c
        window.foobar = function(a, d) {
        // Property ambiguation renames both ".foo" and ".bar" to ".a".
          return a.a + d.a;
        }
        """);
  }

  @Test
  public void testDisambiguationWithInvalidCastDownAndCommonInterface() {
    // test for a legacy-compat behavior of the disambiguator
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDisambiguateProperties(true);

    test(
        options,
        """
        /** @interface */
        class Speaker {
          speak() {}
        }
        /** @implements {Speaker} */
        class SpeakerImpl {
          speak() { alert('Speaker'); }
        }
        /** @interface @extends {Speaker} */
        class SpeakerChild {}
        /** @param {!Speaker} speaker */
        function speak(speaker) {
        // cast is invalid: a SpeakerImpl is passed. Note that this function is needed because
        // the compiler is smart enough to detect invalid casts in
        // /** @type {!SpeakerImpl} (/** @type {!Speaker} */ (new SpeakerImpl()));
          /** @type {!SpeakerChild} */ (speaker).speak();
        }
        speak(new SpeakerImpl());
        class Other {
          speak() { alert('other'); }
        }
        new Other().speak();
        """,
        // Compiler calls SpeakerImpl.prototype.speak even though it's called off SpeakerChild.
        "alert('Speaker'); alert('other');");
  }

  @Test
  public void testNameReferencedInExternsDefinedInCodeNotRenamedButIsInlined() {
    // NOTE(lharker): I added this test as a regression test for existing compiler behavior. I'm
    // not sure it makes sense conceptually to have 'foobar' be unrenamable but still optimized away
    // below.
    CompilerOptions options = createCompilerOptions();

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addAlert()
                .addExtra("/** @fileoverview @suppress {externsValidation} */ foobar")
                .buildExternsFile("externs.js"));

    // with just variable renaming on, the code is unchanged becasue of the 'foobar' externs ref.
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(options, "var foobar = {x: 1}; alert(foobar.x);", "var foobar = {x:1}; alert(foobar.x);");

    // with inlining + other advanced optimizations, foobar is still deleted
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(options, "var foobar = {x: 1}; alert(foobar.x);", "alert(1);");
  }

  @Test
  public void testUndefinedNameReferencedInCodeAndExterns() {
    // NOTE(lharker): I added this test as a regression test for existing compiler behavior.
    CompilerOptions options = createCompilerOptions();

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addAlert()
                .addExtra("/** @fileoverview @suppress {externsValidation} */ foobar")
                .buildExternsFile("externs.js"));

    // with just variable renaming on, the code is unchanged because of the 'foobar' externs ref.
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(options, "foobar = {x: 1}; alert(foobar.x);", "foobar = {x:1}; alert(foobar.x);");

    // with inlining + other advanced optimizations, foobar.x is renamed
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(options, "foobar = {x: 1}; alert(foobar.x);", "foobar = {a: 1}; alert(foobar.a);");
  }

  /**
   * Validates that a private interface implemented by two classes will merge their disambiguation
   * clusters even if it is DCE'd.
   */
  @Test
  public void testHiddenInterfacesDisambiguateProperties() {

    CompilerOptions options = createCompilerOptions();
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    test(
        options,
        """
        /** @interface */
        class Interface {
          /** @return {number} */
          getField() {}
        }
        /** @interface */
        class Interface2 {
          /** @return {number} */
          getOtherField() {}
        }
        /**
         * @implements {Interface}
         * @implements {Interface2}
         */
        class Foo {
          constructor(/** number */ bar) {
            this.bar = bar;
          }
          /** @override @noinline */
          getOtherField() {
            return this.bar + this.bar;
          }
          /** @override @noinline */
          getField() {
            return this.bar;
          }
        }
        /** @implements {Interface} */
        class Bar {
          constructor(baz) {
            this.baz = baz;
          }
          /** @override @noinline */
          getField() {
            return this.baz;
          }
        }
        // Needed or jscomp will devirtualize getOtherField().
        window['Interface2'] = Interface2;
        window['Foo'] = Foo;
        window['Bar'] = Bar;
        /**
         * @param {!Foo} foo
         * @param {!Bar} bar
         */
        window['foobar'] = function(foo, bar) {
          return foo.getField() + foo.getOtherField() + bar.getField();
        };
        """,
        """
        class b {
          c() {}
        }
        class c {
          constructor(a) {
            this.a = a;
          }
          c() {
            return this.a + this.a;
          }
          b() {
            return this.a;
          }
        }
        class d {
          constructor(a) {
            this.a = a;
          }
          b() {
            return this.a;
          }
        }
        window.Interface2 = b;
        window.Foo = c;
        window.Bar = d;
        window.foobar = function(a, e) {
        // Property disambiguation renames both ".getField()" calls to .b().
          return a.b() + a.c() + e.b();
        };
        """);
  }

  /**
   * Validates that a private interface implemented by two classes allows for devirtualization when
   * the interface is DCE'd.
   */
  @Test
  public void testHiddenInterfacesDoNotInhibitDevirtualization() {

    CompilerOptions options = createCompilerOptions();
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    test(
        options,
        """
        /** @interface */
        class Interface {
          /** @return {number} */
          getField() {}
        }
        /** @interface */
        class Interface2 {
          /** @return {number} */
          getOtherField() {}
        }
        /**
         * @implements {Interface}
         * @implements {Interface2}
         */
        class Foo {
          constructor(/** number */ bar) {
            this.bar = bar;
          }
          /** @override @noinline */
          getOtherField() {
            return this.bar + this.bar;
          }
          /** @override @noinline */
          getField() {
            return this.bar;
          }
        }
        /** @implements {Interface} */
        class Bar {
          constructor(baz) {
            this.baz = baz;
          }
          /** @override @noinline */
          getField() {
            return this.baz;
          }
        }
        // Needed or jscomp will devirtualize getOtherField().
        window['Foo'] = Foo;
        /**
         * @param {!Foo} foo
         * @param {!Bar} bar
         */
        window['foobar'] = function(foo, bar) {
          return foo.getField() + foo.getOtherField() + bar.getField();
        };
        """,
        """
        function b(a) {
          return a.a + a.a;
        }
        function c(a) {
          return a.a;
        }
        class d {
          constructor(a) {
            this.a = a;
          }
        }
        window.Foo = d;
        window.foobar = function(a, e) {
          return c(a) + b(a) + c(e);
        };
        """);
  }

  @Test
  public void testRetainSideeffectCallInBuilder() {
    CompilerOptions options = createCompilerOptions();
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2020);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addClosureExterns()
                .addExtra("/** @type {function(): string} */", "var retrieveElementRef;")
                .buildExternsFile("externs.js"));

    // regression test for bad dead code elimination:
    // compiler used to keep the first 'retrieveElementRef();' call but then delete the second
    // one called via `Selector.create().build();`
    test(
        options,
        """
        goog.module('main');

        retrieveElementRef();
        class SelectorBuilder {
            /** @return {!Selector} */
            build() {
                return new Selector(retrieveElementRef());
            }
        }
        class Selector {
            /** @param {string} s */
            constructor(s) {
                /** @type {string} */
                this.s = s;
            }
            /** @return {!SelectorBuilder} */
            static create() {
                return new SelectorBuilder();
            }
        }
        Selector.create().build();
        """,
        "retrieveElementRef(); class a { constructor() { retrieveElementRef(); } } new a();");
  }

  @Test
  public void testRequireAndEncourageInlining_complexCase() {
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addClosureExterns()
                .buildExternsFile("externs.js"));
    CompilerOptions options = createCompilerOptions();
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2020);
    options.setCollapseProperties(true);
    options.setInlineConstantVars(true);
    options.setInlineFunctions(CompilerOptions.Reach.ALL);
    options.setInlineProperties(true);
    options.setMaxOptimizationLoopIterations(2);
    options.setRemoveClosureAsserts(true);
    options.setSmartNameRemoval(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    // @requireInlining should inline through multiple references and aliases.
    test(
        options,
        """
        /** @requireInlining */ function foo() {console.log('hi');console.log('bye');}
        const bar = foo;
        /** @requireInlining */ function baz() {bar();bar();}
        /** @requireInlining */ function qux() {baz();}
        qux();qux();qux();
        """,
        """
        console.log("hi");
        console.log("bye");
        console.log("hi");
        console.log("bye");
        console.log("hi");
        console.log("bye");
        console.log("hi");
        console.log("bye");
        console.log("hi");
        console.log("bye");
        console.log("hi");
        console.log("bye");
        """);

    // @encourageInlining should do the same.
    test(
        options,
        """
        /** @encourageInlining */ function foo() {console.log('hi');console.log('bye');}
        const bar = foo;
        /** @encourageInlining */ function baz() {bar();bar();}
        /** @encourageInlining */ function qux() {baz();}
        qux();qux();qux();
        """,
        """
        console.log("hi");
        console.log("bye");
        console.log("hi");
        console.log("bye");
        console.log("hi");
        console.log("bye");
        console.log("hi");
        console.log("bye");
        console.log("hi");
        console.log("bye");
        console.log("hi");
        console.log("bye");
        """);

    // but failures are okay for @encourageInlining
    test(
        options,
        """
        /** @encourageInlining */ function foo(q) { console.log(q); console.log('bye'); }
        console.log(/** @type {?} */ (foo).name);
        """,
        """
        console.log(function(a) {
          console.log(a);
          console.log("bye");
        }.a)
        """);

    // and not for @requireInlining
    var thrown =
        assertThrows(
            RuntimeException.class,
            () -> {
              test(
                  options,
                  """
                  /** @requireInlining */ function foo(q) { console.log(q); console.log('bye'); }
                  console.log(/** @type {?} */ (foo).name);
                  """,
                  "");
            });
    assertThat(thrown).hasMessageThat().contains("Validity check failed for earlyInlineVariables");
    assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
    assertThat(thrown)
        .hasCauseThat()
        .hasCauseThat()
        .hasMessageThat()
        .contains("@requireInlining node failed to be inlined");
  }

  @Test
  public void
      testShouldValidateRequiredInlings_throwsIfSetWithoutSufficientlyAdvancedOptimizations() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setValidateRequiredInlinings(true);

    var thrown =
        assertThrows(
            IllegalStateException.class,
            () -> {
              test(options, "", "");
            });
    assertThat(thrown).hasMessageThat().contains("shouldValidateRequiredInlinings");
    assertThat(thrown).hasMessageThat().contains("missing property collapsing");
  }

  @Test
  public void testShouldValidateRequiredInlings_okIfUsingAdvancedOptimizations() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setValidateRequiredInlinings(true);

    test(options, "", "");
  }

  @Test
  public void testObjectDefaultDestructuring_passesValidityChecks() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.OFF);
    options.setGeneratePseudoNames(true);
    // Regression test: this code used to cause a ValidityCheck crash because function inlining was
    // not properly marking "x" from "{x = 2}" as constant.
    test(
        options,
        """
        function testFunctionDefault1(a) {
          var x = 1;
          function f({x = 2}) {
            assertEquals(2, x);
          }
          f({x: a});
        }
        window['foo'] = testFunctionDefault1;
        """,
        """
        window.foo = function($a$$) {
          assertEquals(2, $a$$ === void 0 ? 2 : $a$$);
        };
        """);
  }

  @Test
  public void testDeadAssignmentEliminationForLocalVariables() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setGeneratePseudoNames(true);
    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                new TestExternsBuilder()
                    .addExtra("var foo; var bar; var baz;")
                    .buildExternsFile("extra_externs.js"))
            .build();

    test(
        options,
        """
        window['fn'] = function () {
          let x = foo();
          x = bar();
          x = baz();
          return x;
        }
        """,
        """
        window.fn = function () {
          foo();
          bar();
          return baz();
        }
        """);
  }

  @Test
  public void testDeadAssignmentEliminationForPropertiesOnLocalNames() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setGeneratePseudoNames(true);

    test(
        options,
        """
        window['fn'] = function (o) {
          o.x = 1;
          o.x = 2;
          return o;
        }
        """,
        """
        window.fn = function ($o$$) {
          $o$$.$x$ = 2;
          return $o$$;
        }
        """);
  }
}
