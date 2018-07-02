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

import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES8_MODULES;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import java.util.ArrayList;
import java.util.List;

/**
 * This file contains the only tests that use the infrastructure in
 * CompilerTestCase to run multiple passes and do validity checks. The other files
 * that use CompilerTestCase unit test a single pass.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */

public final class MultiPassTest extends CompilerTestCase {
  private List<PassFactory> passes;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    enableNormalize();
    enableGatherExternProperties();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null);
    phaseopt.consume(passes);
    phaseopt.setValidityCheck(
        new PassFactory("validityCheck", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new ValidityCheck(compiler);
          }

          @Override
          protected FeatureSet featureSet() {
            return ES8_MODULES;
          }
        });
    compiler.setPhaseOptimizer(phaseopt);
    return phaseopt;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setPrintSourceAfterEachPass(true);
    return options;
  }

  public void testInlineVarsAndPeephole() {
    passes = new ArrayList<>();
    addInlineVariables();
    addPeephole();
    test("function f() { var x = 1; return x + 5; }",
        "function f() { return 6; }");
  }

  public void testInlineFunctionsAndPeephole() {
    passes = new ArrayList<>();
    addInlineFunctions();
    addPeephole();
    test("function f() { return 1; }" +
        "function g() { return f(); }" +
        "function h() { return g(); } var n = h();",
        "var n = 1");
  }

  public void testInlineVarsAndDeadCodeElim() {
    passes = new ArrayList<>();
    addDeadCodeElimination();
    addInlineVariables();
    test("function f() { var x = 1; return x; x = 3; }",
        "function f() { return 1; }");
  }

  public void testCollapseObjectLiteralsScopeChange() {
    passes = new ArrayList<>();
    addCollapseObjectLiterals();
    test("function f() {" +
        "  var obj = { x: 1 };" +
        "  var z = function() { return obj.x; }" +
        "}",
        "function f(){" +
        "  var JSCompiler_object_inline_x_0 = 1;" +
        "  var z = function(){" +
        "    return JSCompiler_object_inline_x_0;" +
        "  }" +
        "}");
  }

  public void testRemoveUnusedClassPropertiesScopeChange() {
    passes = new ArrayList<>();
    addRemoveUnusedClassProperties();
    test(
        "/** @constructor */ function Foo() { this.a = 1; } Foo.baz = function() {};",
        "/** @constructor */ function Foo() {             } Foo.baz = function() {};");
  }

  public void testRemoveUnusedVariablesScopeChange() {
    passes = new ArrayList<>();
    addRemoveUnusedVars();
    test("function f() { var x; }",
        "function f() {}");
    test("function g() { function f(x, y) { return 1; } }",
        "function g() {}");
    test("function f() { var x = 123; }",
        "function f() {}");
  }

  public void testTopScopeChange() {
    passes = new ArrayList<>();
    addInlineVariables();
    addPeephole();
    test("var x = 1, y = x, z = x + y;", "var z = 2;");
  }

  public void testDestructuringAndArrowFunction() {
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    disableNormalize();
    allowExternsChanges();

    passes = new ArrayList<>();
    addRenameVariablesInParamListsPass();
    addSplitVariableDeclarationsPass();
    addDestructuringPass();
    addArrowFunctionPass();

    test(
        lines(
            "var foo = (x,y) => x===y;",
            "var f = ({key: value}) => foo('v', value);",
            "f({key: 'v'})"),
        lines(
            "var foo = function(x,y) {return x===y;};",
            "var f = function ($jscomp$destructuring$var0) {",
            "   var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "   var value = $jscomp$destructuring$var1.key;",
            "   return foo('v', value);",
            "};",
            "f({key:'v'})"));

    test(
        lines("var x, a, b;", "x = ([a,b] = [1,2])"),
        lines(
            "var x, a, b;",
            "x = function () {",
            "   let $jscomp$destructuring$var0 = [1,2];",
            "   var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "   a = $jscomp$destructuring$var1.next().value;",
            "   b = $jscomp$destructuring$var1.next().value;",
            "   return $jscomp$destructuring$var0;",
            "} ();"));

    test(
        lines("var x, a, b;", "x = (() => {console.log(); return [a,b] = [1,2];})()"),
        lines(
            "var x, a, b;",
            "x = function () {",
            "   console.log();",
            "   return function () {",
            "       let $jscomp$destructuring$var0 = [1,2];",
            "       var $jscomp$destructuring$var1 = ",
            "$jscomp.makeIterator($jscomp$destructuring$var0);",
            "       a = $jscomp$destructuring$var1.next().value;",
            "       b = $jscomp$destructuring$var1.next().value;",
            "       return $jscomp$destructuring$var0;",
            "       } ();",
            "} ();"));

    test(
        lines(
            "var foo = function () {", "var x, a, b;", "x = ([a,b] = [1,2]);", "}", "foo();"),
        lines(
            "var foo = function () {",
            "var x, a, b;",
            " x = function () {",
            "   let $jscomp$destructuring$var0 = [1,2];",
            "   var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "   a = $jscomp$destructuring$var1.next().value;",
            "   b = $jscomp$destructuring$var1.next().value;",
            "   return $jscomp$destructuring$var0;",
            " } ();",
            "}",
            "foo();"));

    test(
        lines("var prefix;", "for (;;[, prefix] = /\\.?([^.]+)$/.exec(prefix)){", "}"),
        lines(
            "var prefix;",
            "for (;;function () {",
            "   let $jscomp$destructuring$var0 = /\\.?([^.]+)$/.exec(prefix)",
            "   var $jscomp$destructuring$var1 = ",
            "$jscomp.makeIterator($jscomp$destructuring$var0);",
            "   $jscomp$destructuring$var1.next();",
            "   prefix = $jscomp$destructuring$var1.next().value;",
            "   return $jscomp$destructuring$var0;",
            " }()){",
            "}"));

    test(
        lines(
            "var prefix;",
            "for (;;[, prefix] = /\\.?([^.]+)$/.exec(prefix)){",
            "   console.log(prefix);",
            "}"),
        lines(
            "var prefix;",
            "for (;;function () {",
            "   let $jscomp$destructuring$var0 = /\\.?([^.]+)$/.exec(prefix)",
            "   var $jscomp$destructuring$var1 = ",
            "$jscomp.makeIterator($jscomp$destructuring$var0);",
            "   $jscomp$destructuring$var1.next();",
            "   prefix = $jscomp$destructuring$var1.next().value;",
            "   return $jscomp$destructuring$var0;",
            " } ()){",
            " console.log(prefix);",
            "}"));

    test(
        lines("for (var x = 1; x < 3; [x,] = [3,4]){", "   console.log(x);", "}"),
        lines(
            "for (var x = 1; x < 3; function () {",
            "   let $jscomp$destructuring$var0 = [3,4]",
            "   var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "   x = $jscomp$destructuring$var1.next().value;",
            "   return $jscomp$destructuring$var0;",
            " } ()){",
            "console.log(x);",
            "}"));

    test(
        "var x = ({a: b, c: d} = foo());",
        lines(
            "var x = function () {",
            "   let $jscomp$destructuring$var0 = foo();",
            "   var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "   b = $jscomp$destructuring$var1.a;",
            "   d = $jscomp$destructuring$var1.c;",
            "   return $jscomp$destructuring$var0;",
            "} ();"));

    test(
        "var x = ({a: b, c: d} = foo());",
        lines(
            "var x = function () {",
            "   let $jscomp$destructuring$var0 = foo();",
            "   var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "   b = $jscomp$destructuring$var1.a;",
            "   d = $jscomp$destructuring$var1.c;",
            "   return $jscomp$destructuring$var0;",
            "} ();"));

    test(
        "var x; var y = ({a: x} = foo());",
        lines(
            "var x;",
            "var y = function () {",
            "   let $jscomp$destructuring$var0 = foo();",
            "   var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "   x = $jscomp$destructuring$var1.a;",
            "   return $jscomp$destructuring$var0;",
            "} ();"));

    test(
        "var x; var y = (() => {return {a,b} = foo();})();",
        lines(
            "var x;",
            "var y = function () {",
            "   return function () {",
            "       let $jscomp$destructuring$var0 = foo();",
            "       var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "       a = $jscomp$destructuring$var1.a;",
            "       b = $jscomp$destructuring$var1.b;",
            "       return $jscomp$destructuring$var0;",
            "   } ();",
            "} ();"));
  }

  private void addCollapseObjectLiterals() {
    passes.add(
        new PassFactory("collapseObjectLiterals", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new InlineObjectLiterals(
                compiler, compiler.getUniqueNameIdSupplier());
          }

          @Override
          protected FeatureSet featureSet() {
            return ES8_MODULES;
          }
        });
  }

  private void addDeadCodeElimination() {
    passes.add(
        new PassFactory("removeUnreachableCode", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new UnreachableCodeElimination(compiler);
          }

          @Override
          protected FeatureSet featureSet() {
            return ES8_MODULES;
          }
        });
  }

  private void addInlineFunctions() {
    passes.add(
        new PassFactory("inlineFunctions", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new InlineFunctions(
                compiler,
                compiler.getUniqueNameIdSupplier(),
                CompilerOptions.Reach.ALL,
                true,
                true,
                CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING);
          }

          @Override
          protected FeatureSet featureSet() {
            return ES8_MODULES;
          }
        });
  }

  private void addInlineVariables() {
    passes.add(
        new PassFactory("inlineVariables", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new InlineVariables(
                compiler, InlineVariables.Mode.ALL, true);
          }

          @Override
          protected FeatureSet featureSet() {
            return ES8_MODULES;
          }
        });
  }

  private void addPeephole() {
    passes.add(
        new PassFactory("peepholeOptimizations", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            final boolean late = false;
            return new PeepholeOptimizationsPass(
                compiler,
                getName(),
                new PeepholeMinimizeConditions(late),
                new PeepholeSubstituteAlternateSyntax(late),
                new PeepholeReplaceKnownMethods(late, false /* useTypes */),
                new PeepholeRemoveDeadCode(),
                new PeepholeFoldConstants(late, false /* useTypes */),
                new PeepholeCollectPropertyAssignments());
          }

          @Override
          protected FeatureSet featureSet() {
            return ES8_MODULES;
          }
        });
  }

  private void addRemoveUnusedClassProperties() {
    passes.add(
        new PassFactory("removeUnusedClassProperties", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new RemoveUnusedCode.Builder(compiler)
                .removeUnusedThisProperties(true)
                .removeUnusedObjectDefinePropertiesDefinitions(true)
                .build();
          }

          @Override
          protected FeatureSet featureSet() {
            return ES8_MODULES;
          }
        });
  }

  private void addRemoveUnusedVars() {
    passes.add(
        new PassFactory("removeUnusedVars", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new RemoveUnusedCode.Builder(compiler).removeLocalVars(true).build();
          }

          @Override
          protected FeatureSet featureSet() {
            return ES8_MODULES;
          }
        });
  }

  private void addDestructuringPass() {
    passes.add(
        new PassFactory("destructuringPass", true) {
          @Override
          protected CompilerPass create(final AbstractCompiler compiler) {
            return new Es6RewriteDestructuring(compiler);
          }

          @Override
          protected FeatureSet featureSet() {
            return FeatureSet.ES8_MODULES;
          }
        });
  }

  private void addArrowFunctionPass() {
    passes.add(
        new PassFactory("arrowFunctionPass", true) {
          @Override
          protected CompilerPass create(final AbstractCompiler compiler) {
            return new Es6RewriteArrowFunction(compiler);
          }

          @Override
          protected FeatureSet featureSet() {
            return FeatureSet.ES8_MODULES;
          }
        });
  }

  private void addSplitVariableDeclarationsPass() {
    passes.add(
        new PassFactory("splitVariableDeclarationsPass", true) {
          @Override
          protected CompilerPass create(final AbstractCompiler compiler) {
            return new Es6SplitVariableDeclarations(compiler);
          }

          @Override
          protected FeatureSet featureSet() {
            return FeatureSet.ES8_MODULES;
          }
        });
  }

  private void addRenameVariablesInParamListsPass() {
    passes.add(
        new PassFactory("renameVariablesInParamListsPass", true) {
          @Override
          protected CompilerPass create(final AbstractCompiler compiler) {
            return new Es6RenameVariablesInParamLists(compiler);
          }

          @Override
          protected FeatureSet featureSet() {
            return FeatureSet.ES8_MODULES;
          }
        });
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }
}
