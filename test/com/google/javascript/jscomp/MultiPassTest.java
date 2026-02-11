/*
 * Copyright 2013 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.Es6RewriteDestructuring.ObjectDestructuringRewriteMode;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This file contains the only tests that use the infrastructure in CompilerTestCase to run multiple
 * passes and do validity checks. The other files that use CompilerTestCase unit test a single pass.
 */
@RunWith(JUnit4.class)
public final class MultiPassTest extends CompilerTestCase {
  private List<PassFactory> passes;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    enableNormalize();
    enableGatherExternProperties();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null);
    phaseopt.consume(passes);
    phaseopt.setValidityCheck(
        PassFactory.builder()
            .setName("validityCheck")
            .setRunInFixedPointLoop(true)
            .setInternalFactory(ValidityCheck::new)
            .build());
    return phaseopt;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setPrintSourceAfterEachPass(true);
    return options;
  }

  @Test
  public void testInlineVarsAndPeephole() {
    passes = new ArrayList<>();
    addInlineVariables();
    addPeephole();
    test("function f() { var x = 1; return x + 5; }", "function f() { return 6; }");
  }

  @Test
  public void testInlineFunctionsAndPeephole() {
    passes = new ArrayList<>();
    addInlineFunctions();
    addPeephole();
    test(
        """
        function f() { return 1; }
        function g() { return f(); }
        function h() { return g(); } var n = h();
        """,
        "var n = 1");
  }

  @Test
  public void testInlineVarsAndDeadCodeElim() {
    passes = new ArrayList<>();
    addInlineVariables();
    addPeephole();
    test("function f() { var x = 1; return x; x = 3; }", "function f() { return 1; }");
  }

  @Test
  public void testCollapseObjectLiteralsScopeChange() {
    passes = new ArrayList<>();
    addCollapseObjectLiterals();
    test(
        """
        function f() {
          var obj = { x: 1 };
          var z = function() { return obj.x; }
        }
        """,
        """
        function f(){
          var JSCompiler_object_inline_x_0 = 1;
          var z = function(){
            return JSCompiler_object_inline_x_0;
          }
        }
        """);
  }

  @Test
  public void testRemoveUnusedClassPropertiesScopeChange() {
    passes = new ArrayList<>();
    addRemoveUnusedClassProperties();
    test(
        "/** @constructor */ function Foo() { this.a = 1; } Foo.baz = function() {};",
        "/** @constructor */ function Foo() {             }");
  }

  @Test
  public void testRemoveUnusedVariablesScopeChange() {
    passes = new ArrayList<>();
    addRemoveUnusedVars();
    test("function f() { var x; }", "function f() {}");
    test("function g() { function f(x, y) { return 1; } }", "function g() {}");
    test("function f() { var x = 123; }", "function f() {}");
  }

  @Test
  public void testTopScopeChange() {
    passes = new ArrayList<>();
    addInlineVariables();
    addPeephole();
    test("var x = 1, y = x, z = x + y;", "var z = 2;");
  }

  @Test
  public void testDestructuringResultOfArrowFunction() {
    setDestructuringArrowFunctionOptions();

    test(
        """
        var foo = (x,y) => x===y;
        var f = ({key: value}) => foo('v', value);
        f({key: 'v'})
        """,
        """
        var foo = function(x,y) {return x===y;};
        var f = function ($jscomp$destructuring$var0) {
           var value;
           var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
           value = $jscomp$destructuring$var1.key;
           return foo('v', value);
        };
        f({key:'v'})
        """);
  }

  @Test
  public void testArrayDestructuringAssign() {
    setDestructuringArrowFunctionOptions();

    test(
        externs(new TestExternsBuilder().addJSCompLibraries().build()),
        srcs(
            """
            var x, a, b;
            x = ([a,b] = [1,2])
            """),
        expected(
            """
            var x;
            var a;
            var b;
            x = function($jscomp$destructuring$var0) {
              var $jscomp$destructuring$var1 =
                  (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
              a = $jscomp$destructuring$var1.next().value;
              b = $jscomp$destructuring$var1.next().value;
              return $jscomp$destructuring$var0;
            }([1, 2]);
            """));
  }

  @Test
  public void testDestructuringInsideArrowFunction() {
    setDestructuringArrowFunctionOptions();

    test(
        externs(new TestExternsBuilder().addJSCompLibraries().addConsole().build()),
        srcs(
            """
            var x, a, b;
            x = (() => {console.log(); return [a,b] = [1,2];})()
            """),
        expected(
            """
            var x;
            var a;
            var b;
            x = function() {
              console.log();
              return function($jscomp$destructuring$var0) {
                var $jscomp$destructuring$var1 =
                    (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
                a = $jscomp$destructuring$var1.next().value;
                b = $jscomp$destructuring$var1.next().value;
                return $jscomp$destructuring$var0;
              }([1, 2]);
            }();
            """));
  }

  @Test
  public void testDestructuringInsideVanillaFunction() {
    setDestructuringArrowFunctionOptions();

    test(
        externs(new TestExternsBuilder().addJSCompLibraries().build()),
        srcs(
            """
            var foo = function () {
            var x, a, b;
            x = ([a,b] = [1,2]);
            }
            foo();
            """),
        expected(
            """
            var foo = function() {
              var x;
              var a;
              var b;
              x = function($jscomp$destructuring$var0) {
                var $jscomp$destructuring$var1 =
                    (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
                a = $jscomp$destructuring$var1.next().value;
                b = $jscomp$destructuring$var1.next().value;
                return $jscomp$destructuring$var0;
              }([1, 2]);
            };
            foo();
            """));
  }

  @Test
  public void testDestructuringInForLoopHeader() {
    setDestructuringArrowFunctionOptions();

    test(
        externs(new TestExternsBuilder().addJSCompLibraries().build()),
        srcs(
            """
            var prefix;
            for (;;[, prefix] = /** @type {!Array<string>} */ (/\\.?([^.]+)$/.exec(prefix))){
            }
            """),
        expected(
            """
            var prefix;
            for (;; function($jscomp$destructuring$var0) {
                   var $jscomp$destructuring$var1 =
                       (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
                   $jscomp$destructuring$var1.next();
                   prefix = $jscomp$destructuring$var1.next().value;
                   return $jscomp$destructuring$var0;
                 }(/\\.?([^.]+)$/.exec(prefix))) {
            }
            """));
  }

  @Test
  public void testDestructuringInForLoopHeaderUsedInBody() {
    setDestructuringArrowFunctionOptions();

    test(
        externs(new TestExternsBuilder().addJSCompLibraries().addConsole().build()),
        srcs(
            """
            var prefix;
            for (;;[, prefix] = /** @type {!Array<string>} */ (/\\.?([^.]+)$/.exec(prefix))){
               console.log(prefix);
            }
            """),
        expected(
            """
            var prefix;
            for (;; function($jscomp$destructuring$var0) {
                   var $jscomp$destructuring$var1 =
                       (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
                   $jscomp$destructuring$var1.next();
                   prefix = $jscomp$destructuring$var1.next().value;
                   return $jscomp$destructuring$var0;
                 }(/\\.?([^.]+)$/.exec(prefix))) {
              console.log(prefix);
            }
            """));
  }

  @Test
  public void testDestructuringInForLoopHeaderWithInitializer() {
    setDestructuringArrowFunctionOptions();

    test(
        externs("" + new TestExternsBuilder().addJSCompLibraries().build()),
        srcs(
            """
            for (var x = 1; x < 3; [x,] = [3,4]){
              x;
            }
            """),
        expected(
            """
            var x = 1;
            for (; x < 3; function($jscomp$destructuring$var0) {
                   var $jscomp$destructuring$var1 =
                       (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
                   x = $jscomp$destructuring$var1.next().value;
                   return $jscomp$destructuring$var0;
                 }([3, 4])) {
              x;
            }
            """));
  }

  @Test
  public void testObjectDestructuringNestedAssign() {
    setDestructuringArrowFunctionOptions();
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);

    test(
        "var b; var d;function foo(){ return {a:1, c:2};} var x = ({a: b, c: d} = foo());",
        """
        var b;
        var d;
        function foo() {
          return {a:1, c:2};
        }
        var x = function($jscomp$destructuring$var0) {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          b = $jscomp$destructuring$var1.a;
          d = $jscomp$destructuring$var1.c;
          return $jscomp$destructuring$var0;
        }(foo());
        """);
  }

  @Test
  public void testObjectDestructuringNestedAssignAndDeclaration() {
    setDestructuringArrowFunctionOptions();
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);

    test(
        "function foo(){return {a:1};} var x; var y = ({a: x} = foo());",
        """
        function foo() {
          return {a:1};
        }
        var x;
        var y = function($jscomp$destructuring$var0) {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          x = $jscomp$destructuring$var1.a;
          return $jscomp$destructuring$var0;
        }(foo());
        """);
  }

  @Test
  public void testArrowFunctionWithDestructuringInsideDeclaration() {
    setDestructuringArrowFunctionOptions();
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);

    test(
        """
        var a;
        var b;
        function foo() { return {a: 1, b: 2}; }
        var x;
        var y = (
          () => {
            return {a, b} = foo();
          }
        )();
        """,
        """
        var a;
        var b;
        function foo() {
          return {a:1, b:2};
        }
        var x;
        var y = function() {
          return function($jscomp$destructuring$var0) {
            var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
            a = $jscomp$destructuring$var1.a;
            b = $jscomp$destructuring$var1.b;
            return $jscomp$destructuring$var0;
          }(foo());
        }();
        """);
  }

  private void setDestructuringArrowFunctionOptions() {
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    disableNormalize();
    allowExternsChanges();
    enableTypeInfoValidation();
    replaceTypesWithColors();

    passes = new ArrayList<>();
    addNormalization(); // adding normalization triggers {@code ValidityCheck.checkVars}
    addDestructuringPass();
    addArrowFunctionPass();
  }

  private void addNormalization() {
    passes.add(
        PassFactory.builder()
            .setName("normalization")
            .setInternalFactory(Normalize::createNormalizeForOptimizations)
            .build());
  }

  private void addCollapseObjectLiterals() {
    passes.add(
        PassFactory.builder()
            .setName("collapseObjectLiterals")
            .setRunInFixedPointLoop(true)
            .setInternalFactory(
                (compiler) ->
                    new InlineObjectLiterals(compiler, compiler.getUniqueNameIdSupplier()))
            .build());
  }

  private void addInlineFunctions() {
    passes.add(
        PassFactory.builder()
            .setName("inlineFunctions")
            .setRunInFixedPointLoop(true)
            .setInternalFactory(
                (compiler) ->
                    new InlineFunctions(
                        compiler,
                        compiler.getUniqueNameIdSupplier(),
                        CompilerOptions.Reach.ALL,
                        true,
                        true,
                        CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING))
            .build());
  }

  private void addInlineVariables() {
    passes.add(
        PassFactory.builder()
            .setName("inlineVariables")
            .setRunInFixedPointLoop(true)
            .setInternalFactory(
                (compiler) -> new InlineVariables(compiler, InlineVariables.Mode.ALL))
            .build());
  }

  private void addPeephole() {
    passes.add(
        PassFactory.builder()
            .setName("peepholeOptimizations")
            .setRunInFixedPointLoop(true)
            .setInternalFactory(
                (compiler) -> {
                  final boolean late = false;
                  return new PeepholeOptimizationsPass(
                      compiler,
                      getName(),
                      new PeepholeMinimizeConditions(late),
                      new PeepholeSubstituteAlternateSyntax(late),
                      new PeepholeReplaceKnownMethods(late, /* useTypes= */ false),
                      new PeepholeRemoveDeadCode(),
                      new PeepholeFoldConstants(late, false /* useTypes */),
                      new PeepholeCollectPropertyAssignments());
                })
            .build());
  }

  private void addRemoveUnusedClassProperties() {
    passes.add(
        PassFactory.builder()
            .setName("removeUnusedClassProperties")
            .setRunInFixedPointLoop(true)
            .setInternalFactory(
                (compiler) ->
                    new RemoveUnusedCode.Builder(compiler)
                        .removeUnusedThisProperties(true)
                        .removeUnusedObjectDefinePropertiesDefinitions(true)
                        .build())
            .build());
  }

  private void addRemoveUnusedVars() {
    passes.add(
        PassFactory.builder()
            .setName("removeUnusedVars")
            .setRunInFixedPointLoop(true)
            .setInternalFactory(
                (compiler) -> new RemoveUnusedCode.Builder(compiler).removeLocalVars(true).build())
            .build());
  }

  private void addDestructuringPass() {
    passes.add(
        PassFactory.builder()
            .setName("destructuringPass")
            .setInternalFactory(
                (compiler) ->
                    new Es6RewriteDestructuring.Builder(compiler)
                        .setDestructuringRewriteMode(
                            ObjectDestructuringRewriteMode.REWRITE_ALL_OBJECT_PATTERNS)
                        .build())
            .build());
  }

  private void addArrowFunctionPass() {
    passes.add(
        PassFactory.builder()
            .setName("arrowFunctionPass")
            .setInternalFactory(Es6RewriteArrowFunction::new)
            .build());
  }
}
