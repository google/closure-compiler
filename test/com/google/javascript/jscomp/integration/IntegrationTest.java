/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCommandLineRunner;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.AliasStringsMode;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.ExperimentalForceTranspile;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.CompilerOptions.Reach;
import com.google.javascript.jscomp.CompilerOptionsPreprocessor;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CrossChunkMethodMotion;
import com.google.javascript.jscomp.CustomPassExecutionTime;
import com.google.javascript.jscomp.DependencyOptions;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticGroupWarningsGuard;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.ModuleIdentifier;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.RenamingMap;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.StrictWarningsGuard;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import com.google.javascript.jscomp.WarningLevel;
import com.google.javascript.jscomp.js.RuntimeJsLibManager.RuntimeLibraryMode;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for the compiler.
 *
 * <p>Before adding a test to this file, consider whether it might belong in a more specific
 * *IntegrationTest.java files.
 */
@RunWith(JUnit4.class)
public final class IntegrationTest extends IntegrationTestCase {
  private static final String CLOSURE_BOILERPLATE = "";

  private static final String CLOSURE_COMPILED = "";

  @Test
  public void testBreakToStatement() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    test(options, "L: break L;", "");
    test(options, "L: { break L; }", "");
    test(options, "L: M: break L;", "");
    test(options, "L: M: break M;", "");
  }

  @Test
  public void testSubstituteEs6Syntax() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);

    externs =
        ImmutableList.of(
            SourceFile.fromCode("testExterns.js", new TestExternsBuilder().addConsole().build()));

    // This is a regression test to confirm both that SubstituteEs6Syntax switches to object
    // literal shorthand here and that it correctly reports that it has added that feature.
    // IntegrationTestCase runs `ValidityCheck` to report an error if a feature usage gets added to
    // the AST without that addition being recorded.
    test(
        options,
        "console.log({console: console});", //
        "console.log({console});");
  }

  @Test
  public void testNewDotTargetTranspilation() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);
    test(
        options,
        """
        class Foo {
          constructor() {
            use(new.target);
            use(() => new.target); // works in arrow functions, too
          }
        }
        """,
        """
        var Foo = function() {
          var a = this;
          use(this.constructor);
          use(function() { return a.constructor; }); // works in arrow functions, too
        }
        """);
  }

  @Test
  public void testNumericSeparator() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    test(options, "1_000", "1000");
  }

  @Test
  public void testCheckUntranspilable() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setChecksOnly(true);
    testNoWarnings(options, "var r = /xx/s;");
  }

  @Test
  public void testBug65688660() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setCoalesceVariableNames(true);
    test(
        options,
        """
        function f(param) {
          if (true) {
            const b1 = [];
            for (const [key, value] of []) {}
          }
          if (true) {
            const b2 = [];
            for (const kv of []) {
              const key2 = kv.key;
            }
          }
        }
        """,
        """
        function f(param) {
          if (true) {
            param = [];
            for (const [key, value] of []) {}
          }
          if (true) {
            param = [];
            for (const kv of []) {
              param = kv.key;
            }
          }
        }
        """);
  }

  @Test
  public void testBug65688660_pseudoNames() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setGeneratePseudoNames(true);
    options.setCoalesceVariableNames(true);
    test(
        options,
        """
        function f(param) {
          if (true) {
            const b1 = [];
            for (const [key, value] of []) {}
          }
          if (true) {
            const b2 = [];
            for (const kv of []) {
              const key2 = kv.key;
            }
          }
        }
        """,
        """
        function f(b1_b2_key2_param) {
          if (true) {
            b1_b2_key2_param = [];
            for (const [key, value] of []) {}
          }
          if (true) {
            b1_b2_key2_param = [];
            for (const kv of []) {
              b1_b2_key2_param = kv.key;
            }
          }
        }
        """);
  }

  @Test
  public void testObjDestructuringConst() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setCoalesceVariableNames(true);
    test(
        options,
        """
        function f(obj) {
          {
            const {foo} = obj;
            alert(foo);
          }
          {
            const {bar} = obj;
            alert(bar);
          }
        }
        """,
        """
        function f(obj) {
          {
            const {foo} = obj;
            alert(foo);
          }
          {
            ({bar: obj} = obj);
            alert(obj);
          }
        }
        """);
  }

  @Test
  public void testConstructorCycle() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    testNoWarnings(
        options,
        """
        /** @return {function()} */
        var AsyncTestCase = function() {};
        /**
         * @constructor
         */
        const Foo = /** @type {function(new:Foo)} */ (AsyncTestCase());
        """);
  }

  // b/27531865
  @Test
  public void testLetInSwitch() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.ERROR);
    String before =
        """
        var a = 0;
        switch (a) {
          case 0:
            let x = 1;
          case 1:
            x = 2;
        }
        """;
    String after =
        """
        var a = 0;
        switch (a) {
          case 0:
            var x = 1;
          case 1:
            x = 2;
        }
        """;
    test(options, before, after);

    before =
        """
        var a = 0;
        switch (a) {
          case 0:
          default:
            let x = 1;
        }
        """;
    after =
        """
        var a = 0;
        switch (a) {
          case 0:
          default:
            var x = 1;
        }
        """;
    test(options, before, after);
  }

  @Test
  public void testExplicitBlocksInSwitch() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.ERROR);
    String before =
        """
        var a = 0;
        switch (a) {
          case 0:
            { const x = 3; break; }
          case 1:
            { const x = 5; break; }
        }
        """;
    String after =
        """
        var a = 0;
        switch (a) {
          case 0:
            { var x = 3; break; }
          case 1:
            { var x$jscomp$1 = 5; break; }
        }
        """;
    test(options, before, after);
  }

  @Test
  public void testMultipleAliasesInlined_bug31437418() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    test(
        options,
        """
        class A { static z() {} }
        const B = {};
         B.A = A;
         const C = {};
         C.A = B.A;
        const D = {};
         D.A = C.A;
         D.A.z();
        """,
        """
        var A = function(){};
        var A$z = function(){};
        var B$A = null;
        var C$A = null;
        var D$A = null;
        A$z();
        """);
  }

  @Test
  public void testBug1956277() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setInlineVariables(true);
    test(
        options,
        """
        var CONST = {}; CONST.bar = null;
        function f(url) { CONST.bar = url; }
        """,
        "var CONST$bar = null; function f(url) { CONST$bar = url; }");
  }

  @Test
  public void testBug1962380() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setInlineVariables(true);
    options.setGenerateExports(true);
    test(
        options,
        """
        const o = {}; /** @export */ o.CONSTANT = 1;
        var x = o.CONSTANT;
        """,
        "goog.exportSymbol('o.CONSTANT', 1);");
  }

  // http://b/31448683
  @Test
  public void testBug31448683() {
    CompilerOptions options = createCompilerOptions();
    WarningLevel.QUIET.setOptionsForWarningLevel(options);
    options.setInlineFunctions(Reach.ALL);
    test(
        options,
        """
        function f() {
          x = x || 1
          var x;
          console.log(x);
        }
        for (var _ in [1]) {
          f();
        }
        """,
        """
        for(var _ in[1]) {
          {
             var x$jscomp$inline_0 = void 0;
             x$jscomp$inline_0 = x$jscomp$inline_0 || 1;
             console.log(x$jscomp$inline_0);
          }
        }
        """);
  }

  @Test
  public void testBug32660578() {
    testSame(createCompilerOptions(), "function f() { alert(x); for (var x in []) {} }");
  }

  // http://blickly.github.io/closure-compiler-issues/#90
  @Test
  public void testIssue90() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.setInlineVariables(true);
    test(options, "var x; x && alert(1);", "");
  }

  @Test
  public void testChromePass_noTranspile() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setChromePass(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    test(
        options,
        "var cr = {}; cr.define('my.namespace', function() { class X {} return {X: X}; });",
        """
        var cr = {},
            my = my || {};
        my.namespace = my.namespace || {};
        cr.define('my.namespace', function() {
          my.namespace.X = class {};
          return { X: my.namespace.X };
        });
        """);
  }

  @Test
  public void testChromePass_transpile() {
    CompilerOptions options = createCompilerOptions();
    options.setChromePass(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        "cr.define('my.namespace', function() { class X {} return {X: X}; });",
        """
        var my = my || {};
        my.namespace = my.namespace || {};
        cr.define('my.namespace', function() {
          /** @constructor */
          my.namespace.X = function() {};
          return { X: my.namespace.X };
        });
        """);
  }

  @Test
  public void testCheckStrictMode() {
    CompilerOptions options = createCompilerOptions();
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setChecksOnly(true);

    externs =
        ImmutableList.of(
            SourceFile.fromCode("externs", "var use; var arguments; arguments.callee;"));

    String code =
        """
        function App() {}
        App.prototype.method = function(){
          use(arguments.callee)
        };
        """;

    test(options, code, DiagnosticGroups.ES5_STRICT);
  }

  @Test
  public void testCheckProvideAlias() {
    CompilerOptions options = createCompilerOptions();

    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setChecksOnly(true);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setClosurePass(true);
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "externs",
                """
                var arguments;
                arguments.callee;
                """));

    String base = TestExternsBuilder.getClosureExternsAsSource();

    String code =
        """
        goog.provide('cycle.a.Widget');
        goog.provide('cycle.a.Widget.Item');

        cycle.a.Widget = class {};
        cycle.a.Widget.Item = class {};

        goog.provide('a.Widget');
        /** @provideAlreadyProvided */
        goog.provide('a.Widget.Item');

        /** @const */
        a.Widget = cycle.a.Widget;

        (() => {
          const Item = goog.module.get('a.Widget.Item');

          /** @const {typeof cycle.a.Widget.Item} */
          const x = a.Widget.Item;
        })();
        """;

    String result =
        """
        goog.provide('cycle.a.Widget');
        goog.provide('cycle.a.Widget.Item');

        cycle.a.Widget = class {};
        cycle.a.Widget.Item = class {};

        goog.provide('a.Widget');
        /** @provideAlreadyProvided */
        goog.provide('a.Widget.Item');

        a.Widget = cycle.a.Widget;

        (() => {
          const Item = a.Widget.Item;
          const x = a.Widget.Item;
        })();
        """;

    test(options, new String[] {base, code}, new String[] {base, result});
  }

  @Test
  public void testCheckProvideAlias2() {
    CompilerOptions options = createCompilerOptions();

    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setChecksOnly(true);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setClosurePass(true);
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "externs",
                """
                var arguments;
                arguments.callee;
                """));

    String base = TestExternsBuilder.getClosureExternsAsSource();
    String code =
        """
        goog.provide('cycle.a.Widget');
        goog.provide('cycle.a.Widget.Item');

        cycle.a.Widget = class {};
        cycle.a.Widget.Item = class {};

        goog.provide('a.Widget');
        /** @provideAlreadyProvided */
        goog.provide('a.Widget.Item');

        /** @const */
        a.Widget = cycle.a.Widget;

        /** @param {!null} a */ function fn(a) {}
        fn(goog.module.get('a.Widget.Item'));
        """;

    // Expect:
    // JSC_TYPE_MISMATCH. actual parameter 1 of fn does not match formal parameter
    // found   : (typeof cycle.a.Widget.Item)
    // required: None
    test(options, new String[] {base, code}, DiagnosticGroups.CHECK_TYPES);
    assertThat(lastCompiler.getErrors().get(0).description())
        .contains("found   : (typeof cycle.a.Widget.Item)");
  }

  @Test
  public void testCheckProvideAlias3() {
    CompilerOptions options = createCompilerOptions();

    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setChecksOnly(true);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setClosurePass(true);
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "externs",
                """
                var arguments;
                arguments.callee;
                """));

    String base = TestExternsBuilder.getClosureExternsAsSource();
    String code1 =
        """
        goog.provide('cycle.a.Widget');
        goog.provide('cycle.a.Widget.Item');

        cycle.a.Widget = class {};
        cycle.a.Widget.Item = class {};
        """;
    String code2 =
        """
        goog.provide('a.Widget');
        /** @provideAlreadyProvided */
        goog.provide('a.Widget.Item');

        /** @const */
        a.Widget = cycle.a.Widget;
        """;
    String code3 =
        """
        goog.module('usage');
        const Item = goog.require('a.Widget.Item');

        /** @param {!null} a */ function fn(a) {}
        fn(Item);
        """;

    // Expect:
    // JSC_TYPE_MISMATCH. actual parameter 1 of fn does not match formal parameter
    // found   : (typeof cycle.a.Widget.Item)
    // required: None

    test(options, new String[] {base, code1, code2, code3}, DiagnosticGroups.CHECK_TYPES);
    assertThat(lastCompiler.getErrors().get(0).description())
        .contains("found   : (typeof cycle.a.Widget.Item)");
  }

  @Test
  public void testCheckProvideAlias_requireTypedInModule() {
    CompilerOptions options = createCompilerOptions();

    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setChecksOnly(true);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setClosurePass(true);
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "externs",
                """
                var arguments;
                arguments.callee;
                """));

    String base = TestExternsBuilder.getClosureExternsAsSource();
    String code1 =
        """
        goog.module('usage');
        const Item = goog.requireType('a.Widget.Item');

        /** @param {!Item} a */ function fn(a) {}
        fn(null);
        """;
    String code2 =
        """
        goog.provide('cycle.a.Widget');
        goog.provide('cycle.a.Widget.Item');

        cycle.a.Widget = class {};
        cycle.a.Widget.Item = class {};
        """;
    String code3 =
        """
        goog.provide('a.Widget');
        /** @provideAlreadyProvided */
        goog.provide('a.Widget.Item');

        /** @const */
        a.Widget = cycle.a.Widget;
        """;

    // Expect:
    // JSC_TYPE_MISMATCH. actual parameter 1 of fn does not match formal parameter
    // found   : null
    // required: cycle.a.Widget.Item
    test(options, new String[] {base, code1, code2, code3}, DiagnosticGroups.CHECK_TYPES);
    assertThat(lastCompiler.getErrors().get(0).description())
        .contains("required: cycle.a.Widget.Item");
  }

  @Test
  public void testExportedNames() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(
        options,
        """
        /** @define {boolean} */ var COMPILED = false;
        const ns = {};
        goog.exportSymbol('b', ns);
        """,
        "var a = true; var c = {}; goog.exportSymbol('b', c);");
    test(
        options,
        """
        /** @define {boolean} */ var COMPILED = false;
        const ns = {};
        goog.exportSymbol('a', ns);
        """,
        "var b = true; var c = {}; goog.exportSymbol('a', c);");
  }

  @Test
  public void testCheckGlobalThisOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(true);
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.ERROR);
    compile(options, "function f() { this.y = 3; }");

    assertThat(lastCompiler.getErrors()).hasSize(1);
    assertThat(DiagnosticGroups.GLOBAL_THIS.matches(lastCompiler.getErrors().get(0))).isTrue();
  }

  @Test
  public void testSuspiciousCodeOff() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(false);
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.ERROR);
    compile(options, "function f() { this.y = 3; }");

    assertThat(lastCompiler.getErrors()).hasSize(1);
    assertThat(DiagnosticGroups.GLOBAL_THIS.matches(lastCompiler.getErrors().get(0))).isTrue();
  }

  @Test
  public void testCheckGlobalThisOff() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(true);
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.OFF);
    testSame(options, "function f() { this.y = 3; }");
  }

  @Test
  public void testCheckRequiresAndCheckProvidesOff() {
    testSame(
        createCompilerOptions(),
        new String[] {"/** @constructor */ function Foo() {}", "new Foo();"});
  }

  @Test
  public void testGenerateExportsOff() {
    CompilerOptions options = createCompilerOptions();
    options.setGenerateExports(false);

    testSame(options, "/** @export */ function f() {}");
  }

  @Test
  public void testExportTestFunctionsOn1() {
    CompilerOptions options = createCompilerOptions();
    options.setExportTestFunctions(true);
    test(
        options,
        "function testFoo() {}",
        """
        /** @export */ function testFoo() {}
        goog.exportSymbol('testFoo', testFoo);
        """);
  }

  @Test
  public void testExportTestFunctionsOn2() {
    CompilerOptions options = createCompilerOptions();
    options.setExportTestFunctions(true);
    options.setClosurePass(true);
    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setGeneratePseudoNames(true);

    Compiler compiler =
        compile(
            options,
            new String[] {
              """
              goog.provide('goog.testing.testSuite');
              goog.testing.testSuite = function(a) {};
              """,
              """
              goog.module('testing');
              var testSuite = goog.require('goog.testing.testSuite');
              testSuite({testMethod:function(){}});
              """
            });

    // Compare the exact expected source instead of the parsed AST because the free call
    // (0, [...])() doesn't parse as expected.
    assertThat(compiler.toSource())
        .isEqualTo(
            """
            goog.$testing$ = {};
            goog.$testing$.$testSuite$ = function($a$$) {
            };
            var $module$exports$testing$$ = {};
            (0,goog.$testing$.$testSuite$)({"testMethod":function() {
            }});
            """);
  }

  @Test
  public void testAngularPassOff() {
    testSame(
        createCompilerOptions(),
        """
        /** @ngInject */ function f() {}
        /** @ngInject */ function g(a){}
        /** @ngInject */ var b = function f(a) {}
        """);
  }

  @Test
  public void testAngularPassOn() {
    CompilerOptions options = createCompilerOptions();
    options.setAngularPass(true);
    test(
        options,
        """
        /** @ngInject */ function f() {}
        /** @ngInject */ function g(a){}
        /** @ngInject */ var b = function f(a, b, c) {}
        """,
        """
        function f() {}
        function g(a) {} g['$inject']=['a'];
        var b = function f(a, b, c) {}; b['$inject']=['a', 'b', 'c']
        """);
  }

  @Test
  public void testAngularPassOn_transpile() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setAngularPass(true);
    test(
        options,
        "class C { /** @ngInject */ constructor(x) {} }",
        "var C = function(x){}; C['$inject'] = ['x'];");
  }

  @Test
  public void testAngularPassOn_Es6Out() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setAngularPass(true);
    test(
        options,
        "class C { /** @ngInject */ constructor(x) {} }",
        "class C { constructor(x){} } C['$inject'] = ['x'];");
  }

  @Test
  public void testExportTestFunctionsOff() {
    testSame(createCompilerOptions(), "function testFoo() {}");
  }

  @Test
  public void testCheckSymbolsOff() {
    CompilerOptions options = createCompilerOptions();
    testSame(options, "x = 3;");
  }

  @Test
  public void testCheckSymbolsOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);
    compile(options, "x = 3;");

    assertThat(lastCompiler.getErrors()).hasSize(1);
    assertThat(DiagnosticGroups.UNDEFINED_VARIABLES.matches(lastCompiler.getErrors().get(0)))
        .isTrue();
  }

  @Test
  public void testNoTypeWarningForDupExternNamespace() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "externs",
                """
                /** @const */
                var ns = {};
                /** @type {number} */
                ns.prop1;
                /** @const */
                var ns = {};
                /** @type {number} */
                ns.prop2;
                """));
    testSame(options, "");
  }

  @Test
  public void testCheckReferencesOff() {
    CompilerOptions options = createCompilerOptions();
    testSame(options, "x = 3; var x = 5;");
  }

  @Test
  public void testCheckReferencesOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);

    test(options, "x = 3; var x = 5;", DiagnosticGroups.CHECK_VARIABLES);
  }

  @Test
  public void testInferTypes() {
    CompilerOptions options = createCompilerOptions();
    options.setInferTypes(true);
    options.setCheckTypes(false);
    options.setClosurePass(true);

    test(
        options,
        """
        /** @define {boolean} */
        var COMPILED = false;
        goog.provide('Foo');
        /** @enum */ Foo = {a: 3};
        """,
        "var COMPILED=true;var Foo={a:3}");
    assertThat(lastCompiler.getErrorManager().getTypedPercent()).isEqualTo(0.0);

    // This does not generate a warning.
    test(options, "/** @type {number} */ var n = window.name;", "var n = window.name;");
    assertThat(lastCompiler.getErrorManager().getTypedPercent()).isEqualTo(0.0);
  }

  @Test
  public void testTypeCheckAndInference() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    test(options, "/** @type {number} */ var n = window.name;", DiagnosticGroups.CHECK_TYPES);
    assertThat(lastCompiler.getErrorManager().getTypedPercent()).isGreaterThan(0.0);
  }

  @Test
  public void testTypeNameParser() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    test(options, "/** @type {n} */ var n = window.name;", DiagnosticGroups.CHECK_TYPES);
  }

  // This tests that the TypedScopeCreator is memoized so that it only creates a
  // Scope object once for each scope. If, when type inference requests a scope,
  // it creates a new one, then multiple JSType objects end up getting created
  // for the same local type, and ambiguate will rename the last statement to
  // o.a(o.a, o.a), which is bad.
  @Test
  public void testMemoizedTypedScopeCreator() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(
        options,
        """
        function someTest() {
          /** @constructor */
          function Foo() { this.instProp = 3; }
          Foo.prototype.protoProp = function(a, b) {};
          /** @constructor
         @extends Foo */
          function Bar() {}
          goog.inherits(Bar, Foo);
          var o = new Bar();
          o.protoProp(o.protoProp, o.instProp);
        }
        """,
        """
        function someTest() {
          function Foo() { this.b = 3; }
          function Bar() {}
          Foo.prototype.a = function(a, b) {};
          goog.inherits(Bar, Foo);
          var o = new Bar();
          o.a(o.a, o.b);
        }
        """);
  }

  @Test
  public void ambiguatePropertiesWithAliases() {
    // include externs definitions for the stuff that would have been injected
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "extraExterns",
            """
            /** @const */
            var $jscomp = {};

            /**
             * @param {?} subClass
             * @param {?} superClass
             * @return {?} newClass
             */
            $jscomp.inherits = function(subClass, superClass) {};
            """));
    externs = externsList.build();

    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    // Avoid injecting the polyfills, so we don't have to include them in the expected output.
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    test(
        options,
        """
        class A {
          constructor() {
            this.aProp = 'aProp';
          }
        }

        /**
         * @const
         */
        const AConstAlias = A;

        /**
         * @constructor
         */
        const AConstructorAlias = A;

        class B extends A {
          constructor() {
            super();
            this.bProp = 'bProp';
            this.aProp = 'originalAProp';
          }
        }

        class BConst extends AConstAlias {
          constructor() {
            super();
            this.bProp = 'bConstProp';
            this.aProp = 'constAliasAProp';
          }
        }

        class BConstructorAlias extends AConstructorAlias {
          constructor() {
            super();
            this.bProp = 'bConstructorProp';
            this.aProp = 'constructorAliasAProp';
          }
        }
        """,
        """
        var A = function() {
          this.a = 'aProp'; // gets a unique name
        };

        var AConstAlias = A;

        var AConstructorAlias = A;

        var B = function() {
          A.call(this);
          this.b = 'bProp'; // ambiguated with props from other classes
          this.a = 'originalAProp'; // matches A class property
        };
        $jscomp.inherits(B,A);

        var BConst = function() {
          A.call(this);
          this.b = 'bConstProp'; // ambiguated with props from other classes
          this.a = 'constAliasAProp'; // matches A class property
        };
        $jscomp.inherits(BConst,A);

        var BConstructorAlias = function() {
          A.call(this);
          this.b = 'bConstructorProp'; // ambiguated with props from other classes
          this.a = 'constructorAliasAProp'; // matches A class property
        };
        $jscomp.inherits(BConstructorAlias,A)
        """);
  }

  @Test
  public void ambiguatePropertiesWithMixins() {

    // include externs definitions for the stuff that would have been injected
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "extraExterns",
            """
            /** @const */
            var $jscomp = {};

            /**
             * @param {?} subClass
             * @param {?} superClass
             * @return {?} newClass
             */
            $jscomp.inherits = function(subClass, superClass) {};
            """));
    externs = externsList.build();

    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    // Avoid injecting the polyfills, so we don't have to include them in the expected output.
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);

    test(
        options,
        """
        class A {
          constructor() {
            this.aProp = 'aProp';
          }
        }

        /**
         * @template T
         * @param {function(new: T)} baseType
         * @return {?}
         */
        function mixinX(baseType) {
          return class extends baseType {
            constructor() {
              super();
              this.x = 'x';
            }
          };
        }
        /** @constructor */
        const BSuper = mixinX(A);

        class B extends BSuper {
          constructor() {
            super();
            this.bProp = 'bProp';
          }
        }
        """,
        """
        var A = function() {
          this.a = "aProp"; // unique property name
        };
        function mixinX(baseType) {
          var $jscomp$classDecl$98447280$0 = function() {
            var $jscomp$super$this$98447280$1 = baseType.call(this) || this;
            $jscomp$super$this$98447280$1.c = "x"; // unique property name
            return $jscomp$super$this$98447280$1;
          };
          $jscomp.inherits($jscomp$classDecl$98447280$0, baseType);
          return $jscomp$classDecl$98447280$0;
        }
        var BSuper = mixinX(A);
        var B = function() {
          var $jscomp$super$this$98447280$2 = BSuper.call(this) || this;
          $jscomp$super$this$98447280$2.b = "bProp"; // unique property name
          return $jscomp$super$this$98447280$2;
        };
        $jscomp.inherits(B, BSuper);
        """);
  }

  @Test
  public void ambiguatePropertiesWithEs5Mixins() {

    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(
        options,
        """
        /** @constructor */
        function A() {
           this.aProp = 'aProp';
        }

        /**
         * @template T
         * @param {function(new: T)} baseType
         * @return {?}
         */
        function mixinX(baseType) {
          /**
           * @constructor
           * @extends {baseType}
           */
          const newClass = function() {
            baseType.call(this);
            this.x = 'x';
          };
          goog.inherits(newClass, baseType)
          return newClass;
        }
        // "/** @type {function(new: ?)} */",
        /** @constructor */
        const BSuper = mixinX(A);

        /**
         * @constructor
         * @extends {BSuper}
         */
        function B() {
          BSuper.call(this);
          this.bProp = 'bProp';
        }
        goog.inherits(B, BSuper);
        """,
        """
        function A() {
          this.a = 'aProp'; // unique prop name
        }

        function mixinX(baseType) {
          var newClass = function() {
            baseType.call(this);
            this.c = 'x'; // unique prop name
          };
          goog.inherits(newClass,baseType);
          return newClass;
        }

        var BSuper = mixinX(A);

        function B() {
          BSuper.call(this);
          this.b = 'bProp'; // unique prop name
        }
        goog.inherits(B,BSuper)
        """);
  }

  @Test
  public void testCheckTypes() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    test(options, "var x = x || {}; x.f = function() {}; x.f(3);", DiagnosticGroups.CHECK_TYPES);
  }

  @Test
  public void testReplaceCssNames() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setGatherCssNames(true);
    test(
        options,
        """
        /** @define {boolean} */
        var COMPILED = false;
        goog.setCssNameMapping({'foo':'bar'});
        function getCss() {
          return goog.getCssName('foo');
        }
        """,
        """
        var COMPILED = true;
        function getCss() {
          return 'bar';
        }
        """);

    assertThat(lastCompiler.getResult().cssNames).containsExactly("foo");
  }

  @Test
  public void testReplaceIdGeneratorsTest() {
    CompilerOptions options = createCompilerOptions();
    options.setReplaceIdGenerators(true);

    options.setIdGenerators(
        ImmutableMap.<String, RenamingMap>of(
            "xid",
            new RenamingMap() {
              @Override
              public String get(String value) {
                return ":" + value + ":";
              }
            }));

    test(
        options,
        """
        /** @idGenerator {mapped} */
        var xid = function() {};
        function f() {
          return xid('foo');
        }
        """,
        """
        var xid = function() {};
        function f() {
          return ':foo:';
        }
        """);
  }

  @Test
  public void testRemoveClosureAsserts() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    testSame(options, "goog.asserts.assert(goog);");
    options.setRemoveClosureAsserts(true);
    test(options, "goog.asserts.assert(goog);", "");
  }

  @Test
  public void testDeprecation() {
    String code = "/** @deprecated */ function f() { } function g() { f(); }";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.DEPRECATED, CheckLevel.ERROR);
    testSame(options, code);

    options.setCheckTypes(true);
    test(options, code, DiagnosticGroups.DEPRECATED);
  }

  @Test
  public void testVisibility() {
    String[] code = {"/** @private */ function f() { }", "function g() { f(); }"};

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.VISIBILITY, CheckLevel.ERROR);
    testSame(options, code);

    options.setCheckTypes(true);
    test(options, code, DiagnosticGroups.VISIBILITY);
  }

  @Test
  public void testUnreachableCode() {
    String code = "function f() { return \n x(); }";

    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.ERROR);
    test(options, code, DiagnosticGroups.CHECK_USELESS_CODE);

    // http://b/194615750
    test(
        options,
        """
        function foo() {
          return;
          for (const n of [1, 2]) {
          }
        }
        """,
        DiagnosticGroups.CHECK_USELESS_CODE);
  }

  @Test
  public void testMissingReturn() {
    String code = "/** @return {number} */ function f() { if (f) { return 3; } }";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.MISSING_RETURN, CheckLevel.ERROR);
    testSame(options, code);

    options.setCheckTypes(true);
    test(options, code, DiagnosticGroups.MISSING_RETURN);
  }

  @Test
  public void testIdGenerators() {
    String code = "function f() {} f('id');";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setIdGenerators(ImmutableSet.of("f"));
    test(options, code, "function f() {} 'a';");
  }

  @Test
  public void testOptimizeParameters() {
    String code = "function f(a) {} f(true);";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setOptimizeCalls(true);
    test(options, code, "function f() { var a = true;} f();");
  }

  @Test
  public void testOptimizeReturns() {
    String code = "var x; function f() { return x; } f();";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setOptimizeCalls(true);
    test(options, code, "var x; function f() {x; return;} f();");
  }

  @Test
  public void testRemoveAbstractMethods() {
    String code = CLOSURE_BOILERPLATE + "var x = {}; x.foo = goog.abstractMethod; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setClosurePass(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setRemoveAbstractMethods(true);
    test(options, code, CLOSURE_COMPILED + " var x$bar = 3;");
  }

  @Test
  public void testCollapseProperties1() {
    String code = "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(options, code, "var x$FOO = 5; var x$bar = 3;");
  }

  @Test
  public void testCollapseProperties2() {
    String code = "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setCollapseObjectLiterals(true);
    test(options, code, "var x$FOO = 5; var x$bar = 3;");
  }

  @Test
  public void testCollapseObjectLiteral1() {
    // Verify collapseObjectLiterals does nothing in global scope
    String code = "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setCollapseObjectLiterals(true);
    testSame(options, code);
  }

  @Test
  public void testCollapseObjectLiteral2() {
    String code = "function f() {var x = {}; x.FOO = 5; x.bar = 3;}";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setCollapseObjectLiterals(true);
    test(
        options,
        code,
        """
        function f() {
          var JSCompiler_object_inline_FOO_0 = 5;
          var JSCompiler_object_inline_bar_1 = 3;
        }
        """);
  }

  @Test
  public void testDisambiguateProperties() {
    String code =
        """
        /** @constructor */
        function Foo() {}
        Foo.prototype.bar = 3;

        /** @constructor */
        function Baz() {}
        Baz.prototype.bar = 3;
        """;

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setDisambiguateProperties(true);
    options.setCheckTypes(true);
    test(
        options,
        code,
        """
        function Foo() {}
        Foo.prototype.JSC$47_bar = 3;

        function Baz() {}
        Baz.prototype.JSC$49_bar = 3;
        """);
  }

  // When closure-code-removal runs before disambiguate-properties, make sure
  // that removing abstract methods doesn't mess up disambiguation.
  @Test
  public void testDisambiguateProperties2() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setRemoveAbstractMethods(true);
    test(
        options,
        """
        goog.abstractMethod = function() {};
        /** @interface */ function I() {}
        I.prototype.a = function(x) {};
        /** @constructor @implements {I} */ function Foo() {}
        /** @override */ Foo.prototype.a = goog.abstractMethod;
        /** @constructor @extends Foo */ function Bar() {}
        /** @override */ Bar.prototype.a = function(x) {};
        """,
        """
        goog.abstractMethod = function() {};
        function I(){}
        I.prototype.a=function(x){};
        function Foo(){}
        function Bar(){}
        Bar.prototype.a=function(x){};
        """);
  }

  @Test
  public void testDisambiguatePropertiesWithPropertyInvalidationError() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setPropertiesThatMustDisambiguate(ImmutableSet.of("a"));
    options.setRemoveAbstractMethods(true);
    test(
        options,
        """
        function fn(x){return x.a;}
        /** @interface */ function I() {}
        I.prototype.a = function(x) {};
        /** @constructor @implements {I} */ function Foo() {}
        /** @override */ Foo.prototype.a = function(x) {};
        /** @constructor @extends Foo */ function Bar() {}
        /** @override */ Bar.prototype.a = function(x) {};
        """,
        """
        function fn(x){return x.a;}
        function I(){}
        I.prototype.a=function(x$jscomp$1){};
        function Foo(){}
        Foo.prototype.a = function(x$jscomp$2) {};
        function Bar(){}
        Bar.prototype.a=function(x$jscomp$3){};
        """,
        DiagnosticGroups.TYPE_INVALIDATION);
  }

  @Test
  public void testMarkPureCalls() {
    String testCode = "function foo() {} foo();";
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);

    testSame(options, testCode);

    options.setComputeFunctionSideEffects(true);
    test(options, testCode, "function foo() {}");
  }

  @Test
  public void testExtraAnnotationNames() {
    CompilerOptions options = createCompilerOptions();
    options.setExtraAnnotationNames(ImmutableSet.of("TagA", "TagB"));
    test(
        options,
        "/** @TagA */ var f = new Foo(); /** @TagB */ f.bar();",
        "var f = new Foo(); f.bar();");
  }

  @Test
  public void testDevirtualizeMethods() {
    CompilerOptions options = createCompilerOptions();
    options.setDevirtualizeMethods(true);
    test(
        options,
        """
        /** @constructor */
        var Foo = function() {};
        Foo.prototype.bar = function() {};
        (new Foo()).bar();
        """,
        """
        var Foo = function() {};
        var JSCompiler_StaticMethods_bar = function(JSCompiler_StaticMethods_bar$self) {};
        JSCompiler_StaticMethods_bar(new Foo());
        """);
  }

  @Test
  public void testCheckConsts() {
    CompilerOptions options = createCompilerOptions();
    options.setInlineConstantVars(true);
    test(options, "/** @const */ var FOO = true; FOO = false", DiagnosticGroups.CONST);
  }

  @Test
  public void testAllChecksOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.ERROR);
    options.setGenerateExports(true);
    options.setExportTestFunctions(true);
    options.setClosurePass(true);
    options.setSyntheticBlockStartMarker("synStart");
    options.setSyntheticBlockEndMarker("synEnd");
    options.setCheckSymbols(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(options, CLOSURE_BOILERPLATE, CLOSURE_COMPILED);
  }

  @Test
  public void testTypeCheckingWithSyntheticBlocks() {
    CompilerOptions options = createCompilerOptions();
    options.setSyntheticBlockStartMarker("synStart");
    options.setSyntheticBlockEndMarker("synEnd");
    options.setCheckTypes(true);

    // We used to have a bug where the CFG drew an
    // edge straight from synStart to f(progress).
    // If that happens, then progress will get type {number|undefined}.
    testNoWarnings(
        options,
        """
        /** @param {number} x */ function f(x) {}
        function g() {
          synStart('foo');
          var progress = 1;
          f(progress);
          synEnd('foo');
        }
        """);
  }

  @Test
  public void testCompilerDoesNotBlowUpIfUndefinedSymbols() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);

    // Disable the undefined variable check.
    options.setWarningLevel(DiagnosticGroups.UNDEFINED_VARIABLES, CheckLevel.OFF);

    // The compiler used to throw an IllegalStateException on this.
    testSame(options, "var x = {foo: y};");
  }

  // Make sure that if we change variables which are constant to have
  // $$constant appended to their names, we remove that tag before
  // we finish.
  @Test
  public void testConstantTagsMustAlwaysBeRemoved() {
    CompilerOptions options = createCompilerOptions();

    options.setVariableRenaming(VariableRenamingPolicy.LOCAL);
    String originalText =
        """
        var G_GEO_UNKNOWN_ADDRESS=1;
        function foo() {
          var localVar = 2;
          if (G_GEO_UNKNOWN_ADDRESS == localVar) {
            alert('A');
          }
        }
        """;
    String expectedText =
        """
        var G_GEO_UNKNOWN_ADDRESS = 1;
        function foo(){
          var a = 2;
          if (G_GEO_UNKNOWN_ADDRESS == a) {
            alert('A')
          }
        }
        """;
    test(options, originalText, expectedText);
  }

  @Test
  public void testCheckGlobalNames() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    test(options, "var x = {}; var y = x.z;", DiagnosticGroups.MISSING_PROPERTIES);
  }

  @Test
  public void testInlineSimpleMethods() {
    CompilerOptions options = createCompilerOptions();
    String code =
        """
        function Foo() {} Foo.prototype.bar = function() { return 3; };
        var x = new Foo(); x.bar();
        """;

    testSame(options, code);
    options.setRemoveUnusedPrototypeProperties(true);

    test(options, code, "function Foo() {} var x = new Foo(); 3;");
  }

  @Test
  public void testInlineSimpleMethodsWithAmbiguate() {
    CompilerOptions options = createCompilerOptions();

    String code =
        """
        /** @constructor */
        function Foo() {}
        /** @type {number} */ Foo.prototype.field;
        Foo.prototype.getField = function() { return this.field; };
        /** @constructor */
        function Bar() {}
        /** @type {string} */ Bar.prototype.field;
        Bar.prototype.getField = function() { return this.field; };
        new Foo().getField();
        new Bar().getField();
        """;

    testSame(options, code);

    options.setRemoveUnusedPrototypeProperties(true);

    test(
        options,
        code,
        """
        function Foo() {}
        Foo.prototype.field;
        function Bar() {}
        Bar.prototype.field;
        new Foo().field;
        new Bar().field;
        """);

    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);

    // Propagating the wrong type information may cause ambiguate properties
    // to generate bad code.
    testSame(options, code);
  }

  @Test
  public void testInlineVariables() {
    CompilerOptions options = createCompilerOptions();
    String code = "function foo() {} var x = 3; foo(x);";
    testSame(options, code);

    options.setInlineVariables(true);
    test(options, code, "(function() {})(3);");
  }

  @Test
  public void testInlineConstants() {
    CompilerOptions options = createCompilerOptions();
    String code = "function foo() {} var x = 3; foo(x); var YYY = 4; foo(YYY);";
    testSame(options, code);

    options.setInlineConstantVars(true);
    test(options, code, "function foo() {} foo(3); foo(4);");
  }

  @Test
  public void testMinimizeExits() {
    CompilerOptions options = createCompilerOptions();
    String code =
        """
        function f() {
          if (window.foo) return; window.h();
        }
        """;
    testSame(options, code);

    options.setFoldConstants(true);
    test(
        options,
        code,
        """
        function f() {
          window.foo || window.h();
        }
        """);
  }

  @Test
  public void testFoldConstants() {
    CompilerOptions options = createCompilerOptions();
    String code = "if (true) { window.foo(); }";
    testSame(options, code);

    options.setFoldConstants(true);
    test(options, code, "window.foo();");
  }

  @Test
  public void testRemoveUnreachableCode() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    String code = "function f() { return; f(); }";
    testSame(options, code);

    options.setFoldConstants(true);
    test(options, code, "function f() {}");
  }

  @Test
  public void testRemoveUnusedPrototypeProperties1() {
    CompilerOptions options = createCompilerOptions();
    String code =
        """
        function Foo() {}
        Foo.prototype.bar = function() { return new Foo(); };
        """;
    testSame(options, code);

    options.setRemoveUnusedPrototypeProperties(true);
    test(options, code, "function Foo() {}");
  }

  @Test
  public void testRemoveUnusedPrototypeProperties2() {
    CompilerOptions options = createCompilerOptions();
    String code =
        """
        function Foo() {}
        Foo.prototype.bar = function() {
          return new Foo();
        };
        function f(x) {
          x.bar();
        }
        """;
    testSame(options, code);

    options.setRemoveUnusedPrototypeProperties(true);
    testSame(options, code);

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "");
  }

  @Test
  public void testSmartNamePassBug11163486() {
    CompilerOptions options = createCompilerOptions();

    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setRemoveUnusedVariables(Reach.ALL);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setSmartNameRemoval(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);

    String code =
        """
        /** @constructor */ function A() {}
        A.prototype.foo = function() {
          window.console.log('A');
        };
        /** @constructor */ function B() {}
        B.prototype.foo = function() {
          window.console.log('B');
        };
        window['main'] = function() {
          var a = window['a'] = new A;
          a.foo();
          window['b'] = new B;
        };
        function notCalled() {
          var something = {};
          something.foo();
        }
        """;

    String expected =
        """
        function A() {}
        A.prototype.JSC$47_foo = function() {
          window.console.log('A');
        };
        function B() {}
        window['main'] = function() {
          var a = window['a'] = new A;
          a.JSC$47_foo();
          window['b'] = new B;
        }
        """;

    test(options, code, expected);
  }

  @Test
  public void testDeadCodeHasNoDisambiguationSideEffects() {
    // This test case asserts that unreachable code does not
    // confuse the disambiguation process and type inferencing.
    CompilerOptions options = createCompilerOptions();

    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setRemoveUnusedVariables(Reach.ALL);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setSmartNameRemoval(true);
    options.setFoldConstants(true);
    options.setInlineVariables(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);

    String code =
        """
        /** @constructor */ function A() {}
        A.prototype.always = function() {
          window.console.log('AA');
        };
        A.prototype.sometimes = function() {
          window.console.log('SA');
        };
        /** @constructor */ function B() {}
        B.prototype.always = function() {
          window.console.log('AB');
        };
        B.prototype.sometimes = function() {
          window.console.log('SB');
        };
        /** @constructor @struct @template T */ function C() {}
        /** @param {!T} x */ C.prototype.touch = function(x) {
          return x.sometimes();
        };
        window['main'] = function() {
          var a = window['a'] = new A;
          a.always();
          a.sometimes();
          var b = window['b'] = new B;
          b.always();
        };
        function notCalled() {
          var something = {};
          something.always();
          var c = new C;
          c.touch(something);
        }
        """;

    // B.prototype.sometimes should be stripped out, as it is not used, and the
    // type ambiguity in function notCalled is unreachable.
    String expected =
        """
        function A() {}
        A.prototype.JSC$47_always = function() {
          window.console.log('AA');
        };
        A.prototype.JSC$47_sometimes = function(){
          window.console.log('SA');
        };
        function B() {}
        B.prototype.JSC$49_always=function(){
          window.console.log('AB');
        };
        window['main'] = function() {
          var a = window['a'] = new A;
          a.JSC$47_always();
          a.JSC$47_sometimes();
          (window['b'] = new B).JSC$49_always();
        }
        """;

    test(options, code, expected);
  }

  @Test
  public void testQMarkTIsNullable() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);

    String code =
        """
        /** @constructor @template T */
        function F() {}

        /** @return {?T} */
        F.prototype.foo = function() {
          return null;
        }

        /** @type {F<string>} */
        var f = new F;
        /** @type {string} */
        var s = f.foo(); // Type error: f.foo() has type {?string}.
        """;

    test(options, code, DiagnosticGroups.CHECK_TYPES);
  }

  @Test
  public void testTIsNotNullable() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);

    String code =
        """
        /** @constructor @template T */
        function F() {}

        /** @param {T} t */
        F.prototype.foo = function(t) {
        }

        /** @type {F<string>} */
        var f = new F;
        /** @type {?string} */
        var s = null;
        f.foo(s); // Type error: f.foo() takes a {string}, not a {?string}
        """;

    test(options, code, DiagnosticGroups.CHECK_TYPES);
  }

  @Test
  public void testDeadAssignmentsElimination() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() { var x = 3; 4; x = 5; return x; } f(); ";
    testSame(options, code);

    options.setDeadAssignmentElimination(true);
    assertThrows(IllegalArgumentException.class, () -> testSame(options, code));

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "function f() { 3; 4; var x = 5; return x; } f();");
  }

  @Test
  public void testPreservesCastInformation() {
    // Only set the suffix instead of both prefix and suffix, because J2CL pass
    // looks for that exact suffix, and IntegrationTestCase adds an input
    // id number between prefix and suffix.
    inputFileNameSuffix = "vmbootstrap/Arrays.impl.java.js";
    String code =
        """
        /** @constructor */
        var Arrays = function() {};
        Arrays.$create = function() { return {}; }
        /** @constructor */
        function Foo() { this.myprop = 1; }
        /** @constructor */
        function Bar() { this.myprop = 2; }
        var x = /** @type {!Foo} */ (Arrays.$create()).myprop;
        """;

    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);

    test(
        options,
        code,
        """
        /** @constructor */
        var Arrays = function() {};
        Arrays.$create = function() { return {}; }
        /** @constructor */
        function Foo() { this.JSC$47_myprop = 1; }
        /** @constructor */
        function Bar() { this.JSC$49_myprop = 2; }
        var x = {}.JSC$47_myprop;
        """);
  }

  @Test
  public void testInliningLocalVarsPreservesCasts() {
    String code =
        """
        /** @constructor */
        function Foo() { this.myprop = 1; }
        /** @constructor */
        function Bar() { this.myprop = 2; }
        /** @return {Object} */
        function getSomething() {
          var x = new Bar();
          return new Foo();
        }
        (function someMethod() {
          var x = getSomething();
          var y = /** @type {Foo} */ (x).myprop;
          return 1 != y;
        })()
        """;

    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    options.setSmartNameRemoval(true);
    options.setFoldConstants(true);
    options.setInlineVariables(true);
    options.setDisambiguateProperties(true);

    // Verify that property disambiguation continues to work after the local
    // var and cast have been removed by inlining.
    test(
        options,
        code,
        """
        /** @constructor */
        function Foo() { this.JSC$46_myprop = 1; }
        /** @constructor */
        function Bar() { this.JSC$48_myprop = 2; }
        /** @return {Object} */
        function getSomething() {
          var x = new Bar();
          return new Foo();
        }
        (function someMethod() {
          return 1 != getSomething().JSC$46_myprop;
        })()
        """);
  }

  /**
   * Tests that inlining of local variables doesn't destroy type information when the cast is from a
   * non-nullable type to a nullable type.
   */
  @Test
  public void testInliningLocalVarsPreservesCastsNullable() {
    String code =
        """
        /** @constructor */
        function Foo() { this.myprop = 1; }
        /** @constructor */
        function Bar() { this.myprop = 2; }
        // Note that this method return a non-nullable type.
        /** @return {!Object} */
        function getSomething() {
          var x = new Bar();
          return new Foo();
        }
        (function someMethod() {
          var x = getSomething();
        // Note that this casts from !Object to ?Foo.
          var y = /** @type {Foo} */ (x).myprop;
          return 1 != y;
        })()
        """;

    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    options.setSmartNameRemoval(true);
    options.setFoldConstants(true);
    options.setInlineVariables(true);
    options.setDisambiguateProperties(true);

    // Verify that property disambiguation continues to work after the local
    // var and cast have been removed by inlining.
    test(
        options,
        code,
        """
        /** @constructor */
        function Foo() { this.JSC$46_myprop = 1; }
        /** @constructor */
        function Bar() { this.JSC$48_myprop = 2; }
        /** @return {Object} */
        function getSomething() {
          var x = new Bar();
          return new Foo();
        }
        (function someMethod() {
          return 1 != getSomething().JSC$46_myprop;
        })()
        """);
  }

  @Test
  public void testInlineFunctions() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() { return 3; } f(); ";
    testSame(options, code);

    options.setInlineFunctions(Reach.ALL);
    test(options, code, "3;");
  }

  @Test
  public void testRemoveUnusedVars1() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f(x) {} f();";
    testSame(options, code);

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "function f() {} f();");
  }

  @Test
  public void testRemoveUnusedVars2() {
    CompilerOptions options = createCompilerOptions();
    String code = "(function f(x) {})();var g = function() {}; g();";
    testSame(options, code);

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "(function() {})();var g = function() {}; g();");
  }

  @Test
  public void testCrossChunkCodeMotion() {
    CompilerOptions options = createCompilerOptions();
    String[] code =
        new String[] {
          "var x = 1;", "x;",
        };
    testSame(options, code);

    options.setCrossChunkCodeMotion(true);
    test(
        options,
        code,
        new String[] {
          "", "var x = 1; x;",
        });
  }

  @Test
  public void testCrossChunkCodeMotionWithPureOrBreakMyCode() {
    CompilerOptions options = createCompilerOptions();
    String[] code =
        new String[] {
          """
          class LowerCasePipe {}
          /** @nocollapse */
          LowerCasePipe.\u0275pipe = /** @pureOrBreakMyCode*/ i0.\u0275\u0275definePipe({
            name: "lowercase",
            type: LowerCasePipe,
            pure: true
          });
          """,
          "new LowerCasePipe();",
        };

    test(
        options,
        code,
        new String[] {
          """
          var LowerCasePipe=function(){};
          LowerCasePipe.\\u0275pipe=i0.\\u0275\\u0275definePipe({
            name:"lowercase",
            type:LowerCasePipe,
            pure:true
          });
          """,
          "new LowerCasePipe"
        });

    options.setCrossChunkCodeMotion(true);

    test(
        options,
        code,
        new String[] {
          "",
          """
          var LowerCasePipe = function(){};
          LowerCasePipe.\\u0275pipe=i0.\\u0275\\u0275definePipe({
            name:"lowercase",
            type:LowerCasePipe,
            pure:true});
          new LowerCasePipe
          """
        });
  }

  @Test
  public void testCrossChunkMethodMotion() {
    CompilerOptions options = createCompilerOptions();
    String[] code =
        new String[] {
          """
          var Foo = function() {};
          Foo.prototype.bar = function() {};
          var x = new Foo();
          """,
          "x.bar();",
        };
    testSame(options, code);

    options.setCrossChunkMethodMotion(true);
    test(
        options,
        code,
        new String[] {
          CrossChunkMethodMotion.STUB_DECLARATIONS
              + """
              var Foo = function() {};
              Foo.prototype.bar = JSCompiler_stubMethod(0);
              var x=new Foo;
              """,
          "Foo.prototype.bar = JSCompiler_unstubMethod(0,function(){});\nx.bar()",
        });
  }

  @Test
  public void testCrossChunkCodeMotion_staticMethod() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setCrossChunkCodeMotion(true);

    test(
        options,
        new String[] {
          """
          class Foo {
            static getY() {
              return 1;
            }
          }
          """,
          """
          alert(Foo.getY());
          alert(new Foo());
          """,
        },
        new String[] {
          "",
          // Note: Foo moved into the same chunk as the alert calls.
          """
          class Foo {
            static getY() {
              return 1;
            }
          }
          alert(Foo.getY());
          alert(new Foo());
          """,
        });
  }

  @Test
  public void testCrossChunkCodeMotion_staticField_noSideEffects() {
    CompilerOptions options = createCompilerOptions();
    options.setGeneratePseudoNames(true);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setCrossChunkCodeMotion(true);

    var srcs =
        new String[] {
          """
          class Foo {
            static y = () => 1;
          }
          """,
          """
          alert(Foo.y);
          alert(Foo.y);
          alert(new Foo());
          """,
        };

    test(
        options,
        srcs,
        new String[] {
          "",
          // Note: Foo moved into the same chunk as the alert calls.
          """
          class Foo {}
          Foo.y = () => 1;
          alert(Foo.y);
          alert(Foo.y);
          alert(new Foo());
          """,
        });

    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        srcs,
        new String[] {
          "",
          """
          class $Foo$$ {}
          var $Foo$y$$ = () => 1;
          alert($Foo$y$$);
          alert($Foo$y$$);
          alert(new $Foo$$());
          """,
        });
  }

  @Test
  public void testCrossChunkCodeMotion_staticField_withSideEffects() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setGeneratePseudoNames(true);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setCrossChunkCodeMotion(true);

    test(
        options,
        new String[] {
          """
          /** @return {number} */
          function getRandom() {
            const v = Math.random();
            alert('Picked ' + v);
            return v;
          }
          """,
          """
          class Foo {
            // Foo.x is never read but its initialization has side effects.
            static x = getRandom();
            static y = getRandom();
          }
          """,
          """
          alert(Foo.y);
          alert(new Foo());
          """,
        },
        new String[] {
          "",
          // Note: Foo.y stayed in the chunk where it had side effects.
          """
          function $getRandom$$() {
            const $v$$ = Math.random();
            alert("Picked " + $v$$);
            return $v$$;
          }
          $getRandom$$();
          var $Foo$y$$ = $getRandom$$();
          """,
          """
          class $Foo$$ {}
          alert($Foo$y$$);
          alert(new $Foo$$());
          """,
        });
  }

  @Test
  public void testCrossChunkCodeMotion_staticBlock_noSideEffects() {
    CompilerOptions options = createCompilerOptions();
    options.setGeneratePseudoNames(true);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setCrossChunkCodeMotion(true);

    var srcs =
        new String[] {
          """
          class Foo {
            static y;
            static {
              Foo.y = 1;
            }
          }
          """,
          """
          alert(Foo.y);
          alert(new Foo());
          """,
        };

    test(
        options,
        srcs,
        new String[] {
          // Note: Code motion was not applied because of the reassignment to Foo.y in the static
          // initialization block.
          """
          class Foo {}
          Foo.y = void 0;
          {
            Foo.y = 1;
          }
          """,
          """
          alert(Foo.y);
          alert(new Foo());
          """,
        });

    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        srcs,
        new String[] {
          "",
          // Note: Foo moved into the same chunk as the alert calls.
          """
          class $Foo$$ {}
          var $Foo$y$$ = void 0;
          $Foo$y$$ = 1;
          alert($Foo$y$$);
          alert(new $Foo$$());
          """,
        });
  }

  @Test
  public void testCrossChunkCodeMotion_staticBlock_withSideEffects() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setGeneratePseudoNames(true);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setCrossChunkCodeMotion(true);

    test(
        options,
        new String[] {
          """
          /** @return {number} */
          function getRandom() {
            const v = Math.random();
            alert('Picked ' + v);
            return v;
          }
          """,
          """
          class Foo {
            static x;
            static y;
            static {
              // Foo.x is never read but its initialization has side effects.
              Foo.x = getRandom();
              Foo.y = getRandom();
            }
          }
          """,
          """
          alert(Foo.y);
          alert(new Foo());
          """,
        },
        new String[] {
          "",
          """
          function $getRandom$$() {
            const $v$$ = Math.random();
            alert("Picked " + $v$$);
            return $v$$;
          }
          var $Foo$y$$ = void 0;
          $getRandom$$();
          $Foo$y$$ = $getRandom$$();
          """,
          """
          class $Foo$$ {}
          alert($Foo$y$$);
          alert(new $Foo$$());
          """,
        });
  }

  @Test
  public void testCrossChunkDepCheck() {
    CompilerOptions options = createCompilerOptions();
    String[] code =
        new String[] {
          """
          var o = {};
          new o.Foo();
          """,
          """
          /** @constructor */
          o.Foo = function() {};
          """,
        };

    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    // No warning for this pattern. See b/188807234.
    test(options, code, code);
  }

  @Test
  public void testLateDefinedQualifiedName() {
    CompilerOptions options = createCompilerOptions();
    String code = "var a = {}; a.b.c = 0; a.b = {};";

    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    // No warning for this pattern. See b/188807234.
    testSame(options, code);
  }

  @Test
  public void testFlowSensitiveInlineVariables1() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() { var x = 3; x = 5; return x; }";
    testSame(options, code);

    options.setInlineVariables(true);
    test(options, code, "function f() { var x = 3; return 5; }");

    String unusedVar = "function f() { var x; x = 5; return x; } f()";
    test(options, unusedVar, "(function() { var x; return 5; })()");

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, unusedVar, "(function () { return 5; })()");
  }

  @Test
  public void testFlowSensitiveInlineVariables2() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        """
        function f () {
            var ab = 0;
            ab += '-';
            alert(ab);
        }
        """,
        """
        function f () {
            alert('0-');
        }
        """);
  }

  @Test
  public void testCollapseAnonymousFunctions() {
    CompilerOptions options = createCompilerOptions();
    String code = "var f = function() {};";
    testSame(options, code);

    options.setCollapseAnonymousFunctions(true);
    test(options, code, "function f() {}");
  }

  @Test
  public void testRewriteGlobalDeclarationsForTryCatchWrapping() {
    CompilerOptions options = createCompilerOptions();
    String code = "var x = f(); function f() { return 3; }";
    testSame(options, code);

    options.setRewriteGlobalDeclarationsForTryCatchWrapping(true);
    test(options, code, "var f = function() { return 3; }; var x = f();");
  }

  @Test
  public void testExtractPrototypeMemberDeclarations() {
    CompilerOptions options = createCompilerOptions();
    String code = "var f = function() {};";
    String expected = "var a; var b = function() {}; a = b.prototype;";
    for (int i = 0; i < 10; i++) {
      code += "f.prototype.a = " + i + ";";
      expected += "a.a = " + i + ";";
    }
    testSame(options, code);

    options.setExtractPrototypeMemberDeclarations(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(options, code, expected);
  }

  @Test
  public void testDevirtualizationAndExtractPrototypeMemberDeclarations() {
    CompilerOptions options = createCompilerOptions();
    options.setDevirtualizeMethods(true);
    options.setCollapseAnonymousFunctions(true);
    options.setExtractPrototypeMemberDeclarations(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    String code = "var f = function() {};";
    String expected = "var a; function b() {} a = b.prototype;";
    for (int i = 0; i < 10; i++) {
      code += "f.prototype.argz = function() {arguments};";
      code += "f.prototype.devir" + i + " = function() {};";

      char letter = (char) ('d' + i);

      // skip i,j,o (reserved)
      if (letter >= 'i') {
        letter++;
      }
      if (letter >= 'j') {
        letter++;
      }
      if (letter >= 'o') {
        letter++;
      }

      expected += "a.argz = function() {arguments};";
      expected += "function " + letter + "(c){}";
    }

    code += "var F = new f(); F.argz();";
    expected += "var q = new b(); q.argz();";

    for (int i = 0; i < 10; i++) {
      code += "F.devir" + i + "();";

      char letter = (char) ('d' + i);

      // skip i,j,o (reserved)
      if (letter >= 'i') {
        letter++;
      }
      if (letter >= 'j') {
        letter++;
      }
      if (letter >= 'o') {
        letter++;
      }

      expected += letter + "(q);";
    }
    test(options, code, expected);
  }

  @Test
  public void testPropertyRenaming() {
    CompilerOptions options = createCompilerOptions();
    String code =
        """
        function f() {
          return this.foo + this['bar'] + this.Baz;
        }
        f.prototype.bar = 3;
        f.prototype.Baz = 3;
        """;
    String all =
        """
        function f() {
          return this.c + this['bar'] + this.a;
        }
        f.prototype.b = 3;
        f.prototype.a = 3;
        """;
    testSame(options, code);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(options, code, all);
  }

  @Test
  public void testConvertToDottedProperties() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() { return this['bar']; } f.prototype.bar = 3;";
    String expected = "function f() { return this.bar; } f.prototype.a = 3;";
    testSame(options, code);

    options.setConvertToDottedProperties(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(options, code, expected);
  }

  @Test
  public void testRewriteFunctionExpressions() {
    CompilerOptions options = createCompilerOptions();
    String code = "var a = function() {};";
    String expected =
        """
        function JSCompiler_emptyFn(){return function(){}}
        var a = JSCompiler_emptyFn();
        """;
    for (int i = 0; i < 10; i++) {
      code += "a = function() {};";
      expected += "a = JSCompiler_emptyFn();";
    }
    testSame(options, code);

    options.setRewriteFunctionExpressions(true);
    test(options, code, expected);
  }

  @Test
  public void testAliasAllStrings() {
    CompilerOptions options = createCompilerOptions();
    String code =
        """
        function f() {
          return 'aaaaaaaaaaaaaaaaaaaa' + 'aaaaaaaaaaaaaaaaaaaa';
        }
        """;
    String expected =
        """
        var $$S_aaaaaaaaaaaaaaaaaaaa = 'aaaaaaaaaaaaaaaaaaaa';
        function f() {
          return $$S_aaaaaaaaaaaaaaaaaaaa + $$S_aaaaaaaaaaaaaaaaaaaa;
        }
        """;
    testSame(options, code);

    options.setAliasStringsMode(AliasStringsMode.ALL);
    test(options, code, expected);
  }

  @Test
  public void testRenameVars1() {
    CompilerOptions options = createCompilerOptions();
    String code = "var abc = 3; function f() { var xyz = 5; return abc + xyz; }";
    String local = "var abc = 3; function f() { var a = 5; return abc + a; }";
    String all = "var a = 3; function c() { var b = 5; return a + b; }";
    testSame(options, code);

    options.setVariableRenaming(VariableRenamingPolicy.LOCAL);
    test(options, code, local);

    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(options, code, all);

    options.setReserveRawExports(true);
  }

  @Test
  public void testRenameVars2() {
    CompilerOptions options = createCompilerOptions();
    options.setVariableRenaming(VariableRenamingPolicy.ALL);

    String code = "var abc = 3; function f() { window['a'] = 5; }";
    String noexport = "var a = 3;   function b() { window['a'] = 5; }";
    String export = "var b = 3;   function c() { window['a'] = 5; }";

    options.setReserveRawExports(false);
    test(options, code, noexport);

    options.setReserveRawExports(true);
    test(options, code, export);
  }

  @Test
  public void testRenameLabels() {
    CompilerOptions options = createCompilerOptions();
    String code = "longLabel: for(;true;) { break longLabel; }";
    String expected = "a: for(;true;) { break a; }";
    testSame(options, code);

    options.setLabelRenaming(true);
    test(options, code, expected);
  }

  @Test
  public void testBadBreakStatementInContinueAfterErrorsMode() {
    // Ensure that type-checking doesn't crash, even if the CFG is malformed.
    CompilerOptions options = createCompilerOptions();
    options.setDevMode(DevMode.OFF);
    options.setChecksOnly(true);
    options.setContinueAfterErrors(true);
    options.setCheckTypes(true);
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    test(options, "function f() { try { } catch(e) { break; } }", DiagnosticGroups.PARSING);
  }

  // https://github.com/google/closure-compiler/issues/2388
  @Test
  public void testNoCrash_varInCatch() {
    CompilerOptions options = createCompilerOptions();
    options.setInlineFunctions(Reach.ALL);

    test(
        options,
        """
        (function() {
          try {
            x = 2;
          } catch (e) {
            var x = 1;
          }
        })();
        """,
        """
        { try {
            x$jscomp$inline_0=2
          } catch(e) {
            var x$jscomp$inline_0=1
          }
        }
        """);
  }

  // https://github.com/google/closure-compiler/issues/2364
  @Test
  public void testNoCrash_varInCatch2() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    testSame(
        options,
        """
        function foo() {
          var msg;
        }

        function bar() {
          msg;
          try {}
          catch(err) {
            var msg;
          }
        }
        """);
  }

  // http://blickly.github.io/closure-compiler-issues/#63
  @Test
  public void testIssue63SourceMap() {
    CompilerOptions options = createCompilerOptions();
    String code = "var a;";

    options.setSkipNonTranspilationPasses(true);
    options.setSourceMapOutputPath("./src.map");

    Compiler compiler = compile(options, code);
    compiler.toSource();
  }

  @Test
  public void testRegExp1() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);

    String code = "/(a)/.test('a');";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    String expected = "";

    test(options, code, expected);
  }

  @Test
  public void testRegExp2() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code = "/(a)/.test('a');var a = RegExp.$1";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    test(options, code, DiagnosticGroups.CHECK_REGEXP);

    options.setWarningLevel(DiagnosticGroups.CHECK_REGEXP, CheckLevel.OFF);

    testSame(options, code);
  }

  @Test
  public void testFoldLocals1() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    // An external object, whose constructor has no side-effects,
    // and whose method "go" only modifies the object.
    String code = "new Widget().go();";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    test(options, code, "");
  }

  @Test
  public void testFoldLocals2() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);
    options.setCheckTypes(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);

    // An external function that returns a local object such that the
    // method "go" that only modifies the object.
    String code = "widgetToken().go();";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    // Can't currently infer that go() is ineffectual
    testSame(options, code);
  }

  @Test
  public void testFoldLocals3() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    // A function "f" who returns a known local object, and a method that
    // modifies only modifies that.
    String definition = "function f(){return new Widget()}";
    String call = "f().go();";
    String code = definition + call;

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    // BROKEN
    // test(options, code, definition);
    testSame(options, code);
  }

  @Test
  public void testFoldLocals4() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        """
        /** @constructor */
        function InternalWidget(){this.x = 1;}
        InternalWidget.prototype.internalGo = function (){this.x = 2};
        new InternalWidget().internalGo();
        """;

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    String optimized =
        """
        function InternalWidget(){this.x = 1;}
        InternalWidget.prototype.internalGo = function (){this.x = 2};
        """;

    test(options, code, optimized);
  }

  @Test
  public void testFoldLocals5() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        """
        function fn(){var a={};a.x={};return a}
        fn().x.y = 1;
        """;

    // "fn" returns a unescaped local object, we should be able to fold it,
    // but we don't currently.
    String result =
        """
        function fn(){var a={x:{}};return a}
        fn().x.y = 1;
        """;

    test(options, code, result);

    options.setComputeFunctionSideEffects(true);

    test(options, code, result);
  }

  @Test
  public void testFoldLocals6() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        """
        function fn(){return {}}
        fn().x.y = 1;
        """;

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    testSame(options, code);
  }

  @Test
  public void testFoldLocals7() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        """
        function InternalWidget(){return [];}
        Array.prototype.internalGo = function (){this.x = 2};
        InternalWidget().internalGo();
        """;

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    // Removing `InternalWidget().internalGo()` would be safe because InternalWidget returns
    // a local value, but the optimizations don't track that information.
    testSame(options, code);
  }

  @Test
  public void testVarDeclarationsIntoFor() {
    CompilerOptions options = createCompilerOptions();

    options.setCollapseVariableDeclarations(false);

    String code = "var a = 1; for (var b = 2; ;) {}";

    testSame(options, code);

    options.setCollapseVariableDeclarations(true);

    test(options, code, "for (var a = 1, b = 2; ;) {}");
  }

  @Test
  public void testExploitAssigns() {
    CompilerOptions options = createCompilerOptions();

    options.setCollapseVariableDeclarations(false);

    String code = "a = 1; b = a; c = b";

    testSame(options, code);

    options.setCollapseVariableDeclarations(true);

    test(options, code, "c=b=a=1");
  }

  @Test
  public void testRecoverOnBadExterns() {
    // This test is for a bug in a very narrow set of circumstances:
    // 1) externs validation has to be off.
    // 2) aliasExternals has to be on.
    // 3) The user has to reference a "normal" variable in externs.
    // This case is handled at checking time by injecting a
    // synthetic extern variable, and adding a "@suppress {duplicate}" to
    // the normal code at compile time. But optimizations may remove that
    // annotation, so we need to make sure that the variable declarations
    // are de-duped before that happens.
    CompilerOptions options = createCompilerOptions();

    externs = ImmutableList.of(SourceFile.fromCode("externs", "extern.foo"));

    test(
        options,
        new String[] {
          """
          var extern;
          function f() { return extern + extern + extern + extern; }
          """
        },
        new String[] {
          """
          var extern;
          function f() { return extern + extern + extern + extern; }
          """
        },
        DiagnosticGroups.EXTERNS_VALIDATION);
  }

  @Test
  public void testDuplicateVariablesInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);
    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "externs", "var externs = {}; /** @suppress {duplicate} */ var externs = {};"));
    testSame(options, "");
  }

  @Test
  public void testEs6ClassInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode("extraExterns", "class ExternalClass { externalMethod() {} }"));
    externs = externsList.build();

    testSame(options, "(new ExternalClass).externalMethod();");
    test(options, "(new ExternalClass).nonexistentMethod();", DiagnosticGroups.MISSING_PROPERTIES);
  }

  @Test
  public void testGeneratorFunctionInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "extraExterns", "/** @return {!Iterable<number>} */ function* gen() {}"));
    externs = externsList.build();

    test(
        options,
        "for (let x of gen()) { x.call(); }",
        // Property call never defined on Number.
        DiagnosticGroups.MISSING_PROPERTIES);
  }

  @Test
  public void testAsyncFunctionInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "extraExterns", "/** @return {!Promise<number>} */ async function init() {}"));
    externs = externsList.build();

    test(
        options,
        "init().then((n) => { n.call(); });",
        // Property call never defined on Number.
        DiagnosticGroups.MISSING_PROPERTIES);
  }

  @Test
  public void testAsyncFunctionSuper() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    options.setPrettyPrint(true);

    // Avoid comparing all the polyfill code.
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);

    // include externs definitions for the stuff that would have been injected
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(SourceFile.fromCode("extraExterns", "/** @const */var $jscomp = {};"));
    externs = externsList.build();

    test(
        options,
        """
        class Foo {
          async bar() {
            console.log('bar');
          }
        }

        class Baz extends Foo {
          async bar() {
            await Promise.resolve();
            super.bar();
          }
        }
        """,
        """
        class Foo {
          bar() {
            return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(function*() {
              console.log("bar");
            });
          }
        }
        class Baz extends Foo {
          bar() {
            const $jscomp$async$this$98447280$3 = this, $jscomp$async$super$get$98447280$5$bar =
                () => super.bar;
            return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(function*() {
              yield Promise.resolve();
              $jscomp$async$super$get$98447280$5$bar().call($jscomp$async$this$98447280$3);
            });
          }
        }
        """);
  }

  @Test
  public void testAsyncIterationSuper() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    options.setPrettyPrint(true);

    // Avoid comparing all the polyfill code.
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);

    // include externs definitions for the stuff that would have been injected
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(SourceFile.fromCode("extraExterns", "/** @const */ var $jscomp = {};"));
    externs = externsList.build();

    test(
        options,
        """
        class Foo {
          async *bar() {
            console.log('bar');
          }
        }

        class Baz extends Foo {
          async *bar() {
            super.bar().next();
          }
        }
        """,
        """
        class Foo {
          bar() {
            return new $jscomp.AsyncGeneratorWrapper(function*() {
              console.log("bar");
            }());
          }
        }
        class Baz extends Foo {
          bar() {
            const $jscomp$asyncIter$this$98447280$1 = this,
                  $jscomp$asyncIter$super$get$bar =
                      () => super.bar;
            return new $jscomp.AsyncGeneratorWrapper(function*() {
              $jscomp$asyncIter$super$get$bar().call($jscomp$asyncIter$this$98447280$1).next();
            }());
          }
        }
        """);
  }

  @Test
  public void testInitSymbolIteratorInjection() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(SourceFile.fromCode("extraExterns", "/** @const */ var $jscomp = {};"));
    externs = externsList.build();
    test(
        options,
        """
        var itr = {
          next: function() { return { value: 1234, done: false }; },
        };
        itr[Symbol.iterator] = function() { return itr; }
        """,
        """
        var itr = {
          next: function() { return { value: 1234, done: false }; },
        };
        itr[Symbol.iterator] = function() { return itr; }
        """);
  }

  @Test
  public void testInitSymbolIteratorInjectionWithES6Syntax() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(SourceFile.fromCode("extraExterns", "/** @const */ var $jscomp = {};"));
    externs = externsList.build();
    test(
        options,
        """
        let itr = {
          next: function() { return { value: 1234, done: false }; },
        };
        itr[Symbol.iterator] = function() { return itr; }
        """,
        """
        var itr = {
          next: function() { return { value: 1234, done: false }; },
        };
        itr[Symbol.iterator] = function() { return itr; }
        """);
  }

  @Test
  public void testLanguageMode2() {
    CompilerOptions options = createCompilerOptions();

    String code = "var a  = 2; delete a;";
    test(options, new String[] {code}, new String[] {"", code}, DiagnosticGroups.ES5_STRICT);

    options.setStrictModeInput(false);
    testSame(options, code);

    options.setStrictModeInput(true);
    test(options, new String[] {code}, new String[] {"", code}, DiagnosticGroups.ES5_STRICT);
  }

  // http://blickly.github.io/closure-compiler-issues/#598
  @Test
  public void testIssue598() {
    CompilerOptions options = createCompilerOptions();
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    String code =
        """
        'use strict';
        function App() {}
        App.prototype = {
          get appData() { return this.appData_; },
          set appData(data) { this.appData_ = data; }
        };
        """;

    testSame(options, code);
  }

  @Test
  public void testCheckStrictModeGeneratorFunction() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.WARNING);

    externs = ImmutableList.of(SourceFile.fromCode("externs", "var arguments; arguments.callee;"));

    String code = "function *gen() { arguments.callee; }";

    test(options, new String[] {code}, null, DiagnosticGroups.ES5_STRICT);
  }

  // http://blickly.github.io/closure-compiler-issues/#701
  @Test
  public void testIssue701() {
    // Check ASCII art in license comments.
    String ascii =
        """
        /**
         * @preserve
           This
             is
               ASCII    ART
        */console.log("hello world");
        """;
    String result =
        """
        /*

           This
             is
               ASCII    ART
        */
        console.log("hello world");
        """;
    testSame(createCompilerOptions(), ascii);
    assertThat(lastCompiler.toSource()).isEqualTo(result);
  }

  @Test
  public void testCoalesceVariableNames() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() {var x = 3; var y = x; var z = y; return z;}";
    testSame(options, code);

    options.setCoalesceVariableNames(true);
    test(options, code, "function f() {var x = 3; x = x; x = x; return x;}");
  }

  @Test
  public void testCoalesceVariables() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);
    options.setFoldConstants(false);
    options.setCoalesceVariableNames(true);

    String code =
        """
        function f(a) {
          if (a) {
            return a;
          } else {
            var b = a;
            return b;
          }
          return a;
        }
        """;
    String expected =
        """
        function f(a) {
          if (a) {
            return a;
          } else {
            a = a;
            return a;
          }
          return a;
        }
        """;

    test(options, code, expected);

    options.setFoldConstants(true);
    options.setCoalesceVariableNames(false);

    code =
        """
        function f(a) {
          if (a) {
            return a;
          } else {
            var b = a;
            return b;
          }
          return a;
        }
        """;
    expected =
        """
        function f(a) {
          if (!a) {
            var b = a;
            return b;
          }
          return a;
        }
        """;

    test(options, code, expected);

    options.setFoldConstants(true);
    options.setCoalesceVariableNames(true);

    expected =
        """
        function f(a) {
          return a;
        }
        """;

    test(options, code, expected);
  }

  @Test
  public void testLateStatementFusion() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    test(options, "while(a){a();if(b){b();b()}}", "for(;a;)a(),b&&(b(),b())");
  }

  @Test
  public void testLateConstantReordering() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    test(
        options,
        "if (((x < 1 || x > 1) || 1 < x) || 1 > x) { alert(x) }",
        "   (((x < 1 || x > 1) || 1 < x) || 1 > x) && alert(x) ");
  }

  @Test
  public void testsyntheticBlockOnDeadAssignments() {
    CompilerOptions options = createCompilerOptions();
    options.setDeadAssignmentElimination(true);
    options.setRemoveUnusedVariables(Reach.ALL);
    options.setSyntheticBlockStartMarker("START");
    options.setSyntheticBlockEndMarker("END");
    test(
        options,
        "var x; x = 1; START(); x = 1;END();x()",
        "var x; x = 1;{START();{x = 1}END()}x()");
  }

  @Test
  public void testBug4152835() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.setSyntheticBlockStartMarker("START");
    options.setSyntheticBlockEndMarker("END");
    test(options, "START();END()", "{START();{}END()}");
  }

  @Test
  public void testNoFuseIntoSyntheticBlock() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.setSyntheticBlockStartMarker("START");
    options.setSyntheticBlockEndMarker("END");
    testSame(options, "for(;;) { x = 1; {START(); {z = 3} END()} }");
    testSame(options, "x = 1; y = 2; {START(); {z = 3} END()} f()");
  }

  @Test
  public void testBug5786871() {
    CompilerOptions options = createCompilerOptions();
    options.setContinueAfterErrors(true);
    options.setChecksOnly(true);
    testParseError(options, "function () {}");
  }

  // http://blickly.github.io/closure-compiler-issues/#378
  @Test
  public void testIssue378() {
    CompilerOptions options = createCompilerOptions();
    options.setInlineVariables(true);
    testSame(
        options,
        """
        function f(c) {var f = c; arguments[0] = this;
            f.apply(this, arguments); return this;}
        """);
  }

  // http://blickly.github.io/closure-compiler-issues/#550
  @Test
  public void testIssue550() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setFoldConstants(true);
    options.setInlineVariables(true);
    test(
        options,
        """
        function f(h) {
          var a = h;
          a = a + 'x';
          a = a + 'y';
          return a;
        }
        """,
        "function f(a) { return a += 'xy'; }");
  }

  // http://blickly.github.io/closure-compiler-issues/#1168
  @Test
  public void testIssue1168() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);
    test(
        options,
        """
        while (function () {
         function f(){};
         L: while (void(f += 3)) {}
        }) {}
        """,
        "for( ; ; );");
  }

  // http://blickly.github.io/closure-compiler-issues/#1198
  @Test
  public void testIssue1198() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    test(
        options,
        "function f(x) { return 1; do { x(); } while (true); }",
        "function f(a) { return 1; }");
  }

  // http://blickly.github.io/closure-compiler-issues/#772
  @Test
  public void testIssue772() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    test(
        options,
        """
        /** @const */ var a = {};
        /** @const */ a.b = {};
        /** @const */ a.b.c = {};
        goog.scope(function() {
          var b = a.b;
          var c = b.c;
          /** @typedef {string} */
          c.MyType;
          /** @param {c.MyType} x The variable. */
          c.myFunc = function(x) {};
        });
        """,
        """
        /** @const */ var a = {};
        /** @const */ a.b = {};
        /** @const */ a.b.c = {};
        a.b.c.MyType;
        a.b.c.myFunc = function(x) {};
        """);
  }

  @Test
  public void testCodingConvention() {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    assertThat(compiler.getCodingConvention()).isInstanceOf(ClosureCodingConvention.class);
  }

  @Test
  public void testJQueryStringSplitLoops() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    test(options, "var x=['1','2','3','4','5','6','7']", "var x='1234567'.split('')");

    options = createCompilerOptions();
    options.setFoldConstants(true);
    options.setComputeFunctionSideEffects(false);
    options.setRemoveUnusedVariables(Reach.ALL);

    // If we do splits too early, it would add a side-effect to x.
    test(options, "var x=['1','2','3','4','5','6','7']", "");
  }

  @Test
  public void testAlwaysRunSafetyCheck() {
    CompilerOptions options = createCompilerOptions();
    options.setDevMode(DevMode.OFF);
    options.setCheckSymbols(false);
    options.addCustomPass(
        CustomPassExecutionTime.BEFORE_OPTIMIZATIONS,
        new CompilerPass() {
          @Override
          public void process(Node externs, Node root) {
            Node var = root.getLastChild().getFirstChild();
            assertThat(var.getToken()).isEqualTo(Token.VAR);
            var.detach();
          }
        });

    try {
      test(options, "var x = 3; function f() { return x + z; }", "function f() { return x + z; }");
      assertWithMessage("Expected run-time exception").fail();
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().contains("Unexpected variable x");
    }
  }

  @Test
  public void testSuppressCastWarning() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);

    test(
        options,
        "function f() { var xyz = /** @type {string} */ (0); }",
        DiagnosticGroups.CHECK_TYPES);

    testNoWarnings(
        options,
        """
        /** @suppress {invalidCasts} */
        function f() { var xyz = /** @type {string} */ (0); }
        """);

    testNoWarnings(
        options,
        """
        /** @const */ var g = {};
        /** @suppress {invalidCasts} */
        g.a = g.b = function() {
        var xyz = /** @type {string} */ (0);
        }
        """);
  }

  @Test
  public void testLhsCast() {
    CompilerOptions options = createCompilerOptions();
    test(
        options,
        """
        /** @const */ var g = {};
        /** @type {number} */ g.foo = 3;
        """,
        """
        /** @const */ var g = {};
        g.foo = 3;
        """);
  }

  @Test
  public void testRenamePrefix() {
    String code = "var x = {}; function f(y) {}";
    CompilerOptions options = createCompilerOptions();
    options.setRenamePrefix("G_");
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(options, code, "var G_={}; function G_a(a) {}");
  }

  @Test
  public void testRenamePrefixNamespace() {
    String code = "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setRenamePrefixNamespace("_");
    test(options, code, "_.x$FOO = 5; _.x$bar = 3;");
  }

  @Test
  public void testRenamePrefixNamespaceAndBlockScopedFunctions_esnextin_es5out() {
    String code = "if (true) { function f() {} } if (true) { function f() {} }";

    CompilerOptions options = createCompilerOptions();
    options.setRenamePrefixNamespace("_");

    // In the ES6 spec: function declarations are always block-scoped.
    // In the ES5 spec, this changes between browsers & strict / sloppy mode.

    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        code,
        "if (true) { _.f = function() {}; } if (true) { _.f$jscomp$1 = function() {}; }");
  }

  @Test
  public void testRenamePrefixNamespaceAndBlockScopedFunctions_esnextin_es2015out() {
    String code = "if (true) { function f() {} } if (true) { function f() {} }";

    CompilerOptions options = createCompilerOptions();
    options.setRenamePrefixNamespace("_");

    // In the ES6 spec: function declarations are always block-scoped.
    // In the ES5 spec, this changes between browsers & strict / sloppy mode.

    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    test(options, code, "if (true) { function f() {} } if (true) { function f() {} }");
  }

  @Test
  public void testRenamePrefixNamespaceAndBlockScopedFunctions_es5in_es5out() {
    String code = "if (true) { function f() {} } if (true) { function f() {} }";

    CompilerOptions options = createCompilerOptions();
    options.setRenamePrefixNamespace("_");

    // In the ES6 spec: function declarations are always block-scoped.
    // In the ES5 spec, this changes between browsers & strict / sloppy mode.

    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    compile(options, code);

    assertThat(lastCompiler.getErrors())
        .comparingElementsUsing(JSCompCorrespondences.DESCRIPTION_EQUALITY)
        .containsExactly(
            """
            This language feature is only supported for ECMASCRIPT_2015 mode or better:\
             block-scoped function declaration.\
            """,
            """
            This language feature is only supported for ECMASCRIPT_2015 mode or better:\
             block-scoped function declaration.\
            """);
  }

  @Test
  public void testRenamePrefixNamespaceProtectSideEffects() {
    String code = "var x = null; try { +x.FOO; } catch (e) {}";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRenamePrefixNamespace("_");
    test(options, code, "_.x = null; try { +_.x.FOO; } catch (a) {}");
  }

  // https://github.com/google/closure-compiler/issues/1875
  @Test
  public void testNoProtectSideEffectsInChecksOnly() {
    String code = "x;";

    CompilerOptions options = createCompilerOptions();
    options.setChecksOnly(true);
    options.setProtectHiddenSideEffects(true);
    testSame(options, code);
  }

  @Test
  public void testRenameCollision() {
    String code =
        """
        /**
         * @fileoverview
         * @suppress {uselessCode}
         */
        var x = {};
        try {
        (0,use)(x.FOO);
        } catch (e) {}
        """;
    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRenamePrefixNamespace("a");
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    options.setRenamePrefixNamespaceAssumeCrossChunkNames(false);
    WarningLevel.DEFAULT.setOptionsForWarningLevel(options);

    test(options, code, "var b = {}; try { (0,use)(b.FOO); } catch (c) {}");
  }

  @Test
  public void testRenamePrefixNamespaceActivatesRewriteGlobalDeclarationsForTryCatchWrapping() {
    CompilerOptions options = createCompilerOptions();
    String code = "var x = f; function f() { return 3; }";
    testSame(options, code);
    assertThat(options.shouldRewriteGlobalDeclarationsForTryCatchWrapping()).isFalse();
    options.setRenamePrefixNamespace("_");
    test(options, code, "_.f = function() { return 3; }; _.x = _.f;");
  }

  @Test
  public void testContinueAfterErrorsMode_doesntCrashOnIncompleteFunctionInObjectLit() {
    CompilerOptions options = createCompilerOptions();
    options.setContinueAfterErrors(true);
    options.setChecksOnly(true);
    testParseError(options, "var foo = {bar: function(e) }", "var foo = {bar: function(e){}};");
  }

  @Test
  public void testContinueAfterErrorsMode_doesntCrashOnIncompleteFunctionMissingParams() {
    CompilerOptions options = createCompilerOptions();
    options.setContinueAfterErrors(true);
    options.setChecksOnly(true);
    testParseError(options, "function hi", "function hi() {}");
  }

  @Test
  public void testUnboundedArrayLiteralInfiniteLoop() {
    CompilerOptions options = createCompilerOptions();
    options.setContinueAfterErrors(true);
    options.setChecksOnly(true);
    testParseError(options, "var x = [1, 2", "var x = [1, 2]");
  }

  @Test
  public void testStrictWarningsGuard() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.addWarningsGuard(new StrictWarningsGuard());

    Compiler compiler = compile(options, "/** @return {number} */ function f() { return true; }");
    assertThat(compiler.getErrors()).hasSize(1);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testCheckConstants1() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.QUIET;
    warnings.setOptionsForWarningLevel(options);

    String code =
        """
        var foo; foo();
        /** @const */
        var x = 1; foo(); x = 2;
        """;
    test(options, code, code);
  }

  @Test
  public void testCheckConstants2() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code =
        """
        var foo;
        /** @const */
        var x = 1; foo(); x = 2;
        """;
    test(options, code, DiagnosticGroups.CONST);
  }

  // http://blickly.github.io/closure-compiler-issues/#937
  @Test
  public void testIssue937() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code =
        """
        console.log(
        /** @type {function():!string} */ ((new x())['abc'])());
        """;
    String result =
        """
        console.log((new x()).abc());
        """;
    test(options, code, result);
  }

  @Test
  public void testES5toES6() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    String code = "f = function(c) { for (var i = 0; i < c.length; i++) {} };";
    compile(options, code);
  }

  // http://blickly.github.io/closure-compiler-issues/#787
  @Test
  public void testIssue787() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code =
        """
        function some_function() {
          var fn1;
          var fn2;

          if (any_expression) {
            fn2 = external_ref;
            fn1 = function (content) {
              return fn2();
            }
          }

          return {
            method1: function () {
              if (fn1) fn1();
              return true;
            },
            method2: function () {
              return false;
            }
          }
        }
        """;

    String result =
        """
        function some_function() {
          if (any_expression) {
            var b = external_ref;
            var a = function(c) {
              return b()
            };
          }
          return {
            method1:function() {
              a && a();
              return !0
            },
            method2: function() {
              return !1
            }
          }
        }
        """;

    test(options, code, result);
  }

  @Test
  public void testMaxFunSizeAfterInliningUsage() {
    CompilerOptions options = new CompilerOptions();
    options.setInlineFunctions(Reach.NONE);
    options.setMaxFunctionSizeAfterInlining(1);
    assertThrows(
        CompilerOptionsPreprocessor.InvalidOptionsException.class, () -> test(options, "", ""));
  }

  // isEquivalentTo returns false for alpha-equivalent nodes
  @Test
  public void testIsEquivalentTo() {
    String[] input1 = {"function f(z) { return z; }"};
    String[] input2 = {"function f(y) { return y; }"};
    CompilerOptions options = new CompilerOptions();
    Node out1 = parse(input1, options);
    Node out2 = parse(input2, options);
    assertThat(out1.isEquivalentTo(out2)).isFalse();
  }

  @Test
  public void testEs6OutDoesntCrash() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    test(options, "function f(x) { if (x) var x=5; }", "function f(x) { if (x) x=5; }");
  }

  @Test
  public void testIjsWithGoogScopeWorks() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    test(
        options,
        """
        goog.provide('foo.baz');

        goog.scope(function() {

        var RESULT = 5;
        /** @return {number} */
        foo.baz = function() { return RESULT; }

        }); // goog.scope
        """,
        """
        var foo = {};
        /** @const */ var $jscomp$scope$98447280$0$RESULT = 5;
        /** @return {number} */ foo.baz = function() { return $jscomp$scope$98447280$0$RESULT; }
        """);
  }

  @Test
  public void testGenerateIjsWithGoogModuleAndGoogScopeWorks() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setIncrementalChecks(CompilerOptions.IncrementalCheckMode.GENERATE_IJS);

    testNoWarnings(
        options,
        new String[] {
          "goog.module('m');",
          """
          goog.scope(function() {
          var RESULT = 5;

          }); // goog.scope
          """
        });
  }

  @Test
  public void testGenerateIjsWithES2018() {
    CompilerOptions options = createCompilerOptions();
    options.setIncrementalChecks(CompilerOptions.IncrementalCheckMode.GENERATE_IJS);
    testNoWarnings(options, "const r = /hello/s;");
  }

  @Test
  public void testIjsWithDestructuringTypeWorks() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setChecksOnly(true);
    // To enable type-checking
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    testNoWarnings(
        options,
        """
        /** @typeSummary */
        const ns = {}
        /** @enum {number} */ ns.ENUM = {A:1};
        const {ENUM} = ns;
        /** @type {ENUM} */ let x = ENUM.A;
        /** @type {ns.ENUM} */ let y = ENUM.A;
        """);
  }

  @Test
  public void testTypeSummaryWithTypedefAliasWorks() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setChecksOnly(true);
    options.setClosurePass(true);
    // To enable type-checking
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        options,
        new String[] {
          """
          /** @typeSummary */
          goog.module('a.b.Foo');
          goog.module.declareLegacyNamespace();

          class Foo {}

          /** @typedef {number} */
          Foo.num;

          exports = Foo;
          """,
          """
          goog.module('x.y.z');

          const Foo = goog.require('a.b.Foo');

          /** @type {Foo.num} */
          var x = 'str';
          """,
        },
        DiagnosticGroups.CHECK_TYPES);
  }

  // GitHub issue #2079: https://github.com/google/closure-compiler/issues/2079
  @Test
  public void testStrictEqualityComparisonAgainstUndefined() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.setCheckTypes(true);
    options.setUseTypesForLocalOptimization(true);
    test(
        options,
        """
        if (/** @type {Array|undefined} */ (window['c']) === null) {
          window['d'] = 12;
        }
        """,
        "window['c']===null&&(window['d']=12)");
  }

  @Test
  public void testUnnecessaryBackslashInStringLiteral() {
    CompilerOptions options = createCompilerOptions();
    test(options, "var str = '\\q';", "var str = 'q';");
  }

  @Test
  public void testWarnUnnecessaryBackslashInStringLiteral() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);

    compile(options, "const str = '\\q';");
    assertThat(lastCompiler.getWarnings()).hasSize(1);
    assertThat(lastCompiler.getWarnings().get(0).description()).contains("Unnecessary escape");
  }

  // NOTE(dimvar): the jsdocs are ignored in the comparison of the before/after ASTs. It'd be nice
  // to test the jsdocs as well, but AFAICT we can only do that in CompilerTestCase, not here.
  @Test
  public void testRestParametersWithGenerics() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setCheckTypes(true);
    test(
        options,
        """
        /**
         * @constructor
         * @template T
         */
        function Foo() {}
        /**
         * @template T
         * @param {...function(!Foo<T>)} x
         */
        function f(...x) {
          return 123;
        }
        """,
        """
        var $jscomp=$jscomp||{};
        $jscomp.scope={};
        $jscomp.getRestArguments=function(){
          var startIndex=Number(this);
          var restArgs=[];
          for(var i=startIndex;i<arguments.length;i++) restArgs[i-startIndex]=arguments[i];
          return restArgs
        };
        function Foo() {}
        function f(){
          var x=$jscomp.getRestArguments.apply(0,arguments);
          return 123;
        }
        """);
  }

  @Test
  public void testTranspilingEs2016ToEs2015() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);

    // Only transpile away the ES2016 ** operator, not the ES2015 const declaration.
    test(options, "const n = 2 ** 5;", "const n = Math.pow(2, 5);");

    // Test skipNonTranspilationPasses separately because it has a different code path
    options.setSkipNonTranspilationPasses(true);
    test(options, "const n = 2 ** 5;", "const n = Math.pow(2, 5);");
    test(options, "class C {}", "class C {}");
  }

  @Test
  public void testMethodDestructuringInTranspiledAsyncFunction() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setOptimizeCalls(false);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);

    // Avoid comparing all the polyfill code.
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode("extraExterns", "/** @const */ var $jscomp = {}; Symbol.iterator;"));
    externs = externsList.build();

    test(
        options,
        """
        class A {
          static doSomething(i) { alert(i); }
        }
        async function foo() {
          A.doSomething(await 3);
        }
        foo();
        """,
        """
        var A = function() {};
        var A$doSomething = function(i) {
          alert(i);
        };
        function foo() {
          var JSCompiler_temp_const;
          var JSCompiler_temp_const$jscomp$0;
          return (0, $jscomp.asyncExecutePromiseGeneratorProgram)(
              function ($jscomp$generator$context$98447280$5) {
                if ($jscomp$generator$context$98447280$5.getNextAddressJsc() == 1) {
                  JSCompiler_temp_const = A;
                  JSCompiler_temp_const$jscomp$0 = A$doSomething;
                  return $jscomp$generator$context$98447280$5.yield(3, 2);
                }
                JSCompiler_temp_const$jscomp$0.call(
                    JSCompiler_temp_const,
                    $jscomp$generator$context$98447280$5.getYieldResultJsc());
                $jscomp$generator$context$98447280$5.jumpToEnd();
              });
        }
        foo();
        """);
  }

  @Test
  public void testGithubIssue2874() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);

    test(
        options,
        """
        var globalObj = {i0:0, i1: 0};
        function func(b) {
          var g = globalObj;
          var f = b;
          g.i0 = f.i0|0;
          g.i1 = f.i1|0;
          g = b;
          g.i0 = 0;
          g.i1 = 0;
        }
        console.log(globalObj);
        func({i0:2, i1: 3});
        console.log(globalObj);
        """,
        """
        var globalObj = {i0: 0, i1: 0};
        function func(b) {
          var g = globalObj;
          g.i0 = b.i0 | 0;
          g.i1 = b.i1 | 0;
          g = b;
          g.i0 = 0;
          g.i1 = 0;
        }
        console.log(globalObj);
        func({i0:2, i1: 3});
        console.log(globalObj);
        """);
  }

  @Test
  public void testDefaultParameterRemoval() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);

    test(
        options,
        "foo = { func: (params = {}) => { console.log(params); } }",
        "foo = { func: (params = {}) => { console.log(params); } }");

    // TODO(bradfordcsmith): Default params are removed if source code uses 2018 features because of
    // doesScriptHaveUnsupportedFeatures in processTranspile. This "sometimes-removal" of default
    // parameters in Es6RewriteDestructuring really needs to be avoided.
    test(
        options,
        "let {...foo} = { func: (params = {}) => { console.log(params); } }",
        """
        var $jscomp$destructuring$var0 = {
          func:(params)=>{
            params=params===void 0?{}:params;
            console.log(params);
          }};
        var $jscomp$destructuring$var1 = Object.assign({},$jscomp$destructuring$var0);
        let foo=$jscomp$destructuring$var1
        """);
  }

  @Test
  public void testAsyncIter() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "extraExterns",
            "/** @const */ var $jscomp = {}; Symbol.iterator; Symbol.asyncIterator;"));
    externs = externsList.build();

    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    testSame(options, "async function* foo() {}");
    testSame(options, "async () => { for await (a of b) {} }");

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    test(
        options,
        "async function* foo() {}",
        "function foo() { return new $jscomp.AsyncGeneratorWrapper((function*(){})()); }");

    test(
        options,
        "async function abc() { for await (a of foo()) { bar(); } }",
"""
'use strict';
async function abc() {
  var $jscomp$forAwait$retFn0;
  try {
    for (var $jscomp$forAwait$tempIterator0 = (0, $jscomp.makeAsyncIterator)(foo());;) {
      var $jscomp$forAwait$tempResult0 = await $jscomp$forAwait$tempIterator0.next();
      if ($jscomp$forAwait$tempResult0.done) {
        break;
      }
      a = $jscomp$forAwait$tempResult0.value;
      {
        bar();
      }
    }
  } catch ($jscomp$forAwait$catchErrParam0) {
    var $jscomp$forAwait$errResult0 = {error:$jscomp$forAwait$catchErrParam0};
  } finally {
    try {
      if ($jscomp$forAwait$tempResult0 && !$jscomp$forAwait$tempResult0.done && ($jscomp$forAwait$retFn0 = $jscomp$forAwait$tempIterator0.return)) {
        await $jscomp$forAwait$retFn0.call($jscomp$forAwait$tempIterator0);
      }
    } finally {
      if ($jscomp$forAwait$errResult0) {
        throw $jscomp$forAwait$errResult0.error;
      }
    }
  }
}
""");
  }

  @Test
  public void testDestructuringRest() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);

    test(options, "const {y} = {}", "const {y} = {}");

    test(
        options,
        "const {...y} = {}",
        """
        var $jscomp$destructuring$var0 = {};
        var $jscomp$destructuring$var1 = Object.assign({},$jscomp$destructuring$var0);
        const y = $jscomp$destructuring$var1
        """);

    test(
        options,
        "function foo({ a, b, ...c}) { try { foo() } catch({...m}) {} }",
        """
        function foo($jscomp$destructuring$var0){
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);
          var a=$jscomp$destructuring$var1.a;
          var b=$jscomp$destructuring$var1.b;
          var c= (delete $jscomp$destructuring$var2.a,
                  delete $jscomp$destructuring$var2.b,
                  $jscomp$destructuring$var2);
          try{ foo() }
          catch ($jscomp$destructuring$var3) {
            var $jscomp$destructuring$var4 = $jscomp$destructuring$var3;
            var $jscomp$destructuring$var5 = Object.assign({}, $jscomp$destructuring$var4);
            let m = $jscomp$destructuring$var5
          }
        }
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
    options.setPrettyPrint(true);
    return options;
  }

  @Test
  public void testNoCrashInliningObjectLiteral_propertyAccessOnReturnValue_conditionally() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    String src =
        """
        const b = () => ({ x: '' })
            function main() {
        let a;
        if (Math.random()) {
        a = b();
        alert(a.x);
        }
        }
        main();
        """;

    String expected = "'use strict'; let a; Math.random() && (a={a: ''}, alert(a.a));";

    test(options, src, expected);
  }

  @Test
  public void testNoCrashInliningObjectLiteral_propertyAccessOnReturnValue_unconditionally() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    String src =
        """
        const b = () => ({ x: '' })
        function main() {
          let a;
          a = b();
          alert(a.x);
        }
        main();
        """;
    String expected = "'use strict';alert(\"\")";

    test(options, src, expected);
  }

  @Test
  public void testNoDevirtualization_whenSingleDefinitionInSource_ifNameCollidesWithExtern() {
    // See https://github.com/google/closure-compiler/issues/3040
    //
    // We run this as an integration test because it depends on the extern properties having been
    // collected.

    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setDevirtualizeMethods(true);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "other_externs.js",
            """
            /** @constructor */
            var SomeExternType = function() {
              /** @type {function()} */
              this.restart;
            }
            """));
    externs = externsList.build();

    testSame(
        options,
        """
        /** @constructor */
        var X = function() { }

        X.prototype.restart = function(n) {
          console.log(n);
        }

        /** @param {SomeExternType} e */
        function f(e) {
        // Notice how `restart` has not been rewritten even though there is only one
        // definition in the sources. A single definition is not a sufficient condition. An
        // extern property may exist with the same name but no definition.
          new X().restart(5);
          e.restart();
        }
        """);
  }

  @Test
  public void testGetOriginalQualifiedNameAfterEs6RewriteClasses() {
    // A bug in Es6RewriteClasses meant we were putting the wrong `originalName` on some nodes.
    CompilerOptions options = createCompilerOptions();
    // force SourceInformationAnnotator to run
    options.setExternExportsPath("exports.js");
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setClosurePass(true);

    test(
        options,
        """
        goog.module('a');
        class Foo { static method() {} }
        class Bar { foo() { Foo.method(); } }
        """,
        """
        var module$exports$a = {};
        /** @constructor @struct */
        var module$contents$a_Foo = function() {};
        module$contents$a_Foo.method = function() {};

        /** @constructor @struct */
        var module$contents$a_Bar = function () {}
        module$contents$a_Bar.prototype.foo =
            function() { module$contents$a_Foo.method(); }
        """);

    // the second child of the externs & js ROOT is the js ROOT. Its first child is the first SCRIPT
    Node script = lastCompiler.getRoot().getSecondChild().getFirstChild();

    Node exprResult = script.getLastChild();
    Node anonFunction = exprResult.getFirstChild().getLastChild();
    assertNode(anonFunction).hasToken(Token.FUNCTION);
    Node body = anonFunction.getLastChild();

    Node callNode = body.getOnlyChild().getOnlyChild();
    assertNode(callNode).hasToken(Token.CALL);

    Node modulecontentsFooMethod = callNode.getFirstChild();
    // Verify this is actually "Foo.method" - it used to be "Foo.foo".
    assertThat(modulecontentsFooMethod.getOriginalQualifiedName()).isEqualTo("Foo.method");
    assertThat(modulecontentsFooMethod.getOriginalName()).isEqualTo("method");
  }

  @Test
  public void testStructuralSubtypeCheck_doesNotInfinitlyRecurse_onRecursiveTemplatizedTypes() {
    // See: https://github.com/google/closure-compiler/issues/3067.

    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);

    testNoWarnings(
        options,
        """
        /**
         * @record
         * @template PARAM_A
         */
        var RecordA = function() {};

        /**
         * @record
         * @template PARAM_B
         */
        var RecordB = function() {};

        // Then we need to give them both a property that:
        //  - could match
        //  - templates on one of the two types (Notice they can be mixed-and-matched since they
        //    might be structurally equal)
        //  - is a nested template (i.e. `Array<X>`) (This is what would explode the recursion)
        //  - uses each type's template parameter (So there's a variable to recur on)
        /** @type {!RecordA<!Array<PARAM_A>>} */
        RecordA.prototype.prop;

        /** @type {!RecordB<!Array<PARAM_B>>} */
        RecordB.prototype.prop;

        // Finally, we need to create a union that:
        //  - generated a raw-type for one of the structural template types (i.e. `RecordA')
        //    (`RecordA<number>` and `RecordA<boolean>` were being smooshed into a raw-type)
        //  - attempts a structural match on that raw-type against the other record type
        // For some reason this also needs to be a property of a forward referenced type, which
        // is why we omit the declaration of `Union`.
        /** @type {(!RecordA<number>|!RecordA<boolean>)|!RecordB<string>} */
        Union.anything;
        """);
  }

  @Test
  public void testOptionalCatchBinding_toEs5() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        options,
        new String[] {
          """
          function foo() {}
          function reportError() {}
          try {
           foo();
          } catch {
            reportError();
          }
          """
        },
        new String[] {
          """
          function foo() {}
          function reportError() {}
          try {
           foo();
          } catch ($jscomp$unused$catch$98447280$0) {
            reportError();
          }
          """
        });
  }

  @Test
  public void testOptionalCatchBinding_toEs2019() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);
    // trying to make sure the catch binding transpilation pass does not run when it should not be
    testSame(
        options,
        new String[] {
          """
          function foo() {}
          function reportError() {}
          try {
           foo();
          } catch {
            reportError();
          }
          """
        });
  }

  @Test
  public void testOptionalCatchBinding_noTranspile() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    test(
        options,
        new String[] {
          """
          function foo() {}
          function reportError() {}
          try {
           foo();
          } catch {
            reportError();
          }
          """
        },
        new String[] {
          """
          function foo() {}
          function reportError() {}
          try {
           foo();
          } catch {
            reportError();
          }
          """
        });
  }

  @Test
  public void testInvalidTTLDoesntCrash() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    compile(
        options,
        """
        /**
         * @template T := maprecord(
         *     record({a: 'number'}),
         *     (k, v) => record({[k]: 'string'}) =:
         */
        function f() {}
        """);

    assertThat(lastCompiler.getErrors())
        .comparingElementsUsing(JSCompCorrespondences.OWNING_DIAGNOSTIC_GROUP)
        .containsExactly(DiagnosticGroups.PARSING);

    assertThat(lastCompiler.getWarnings())
        .comparingElementsUsing(JSCompCorrespondences.OWNING_DIAGNOSTIC_GROUP)
        .containsExactly(DiagnosticGroups.CHECK_TYPES);
  }

  @Test
  public void testNoSpuriousWarningsOnGeneratedTypedef() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    testNoWarnings(options, "/** @typedef {number} */ export let Foo;");
  }

  @Test
  public void testEs6ModuleEntryPoint() throws Exception {
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("/index.js", "import foo from './foo.js'; foo('hello');"),
            SourceFile.fromCode("/foo.js", "export default (foo) => { alert(foo); }"));

    ImmutableList<ModuleIdentifier> entryPoints =
        ImmutableList.of(ModuleIdentifier.forFile("/index"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.pruneLegacyForEntryPoints(entryPoints));

    List<SourceFile> externs =
        AbstractCommandLineRunner.getBuiltinExterns(options.getEnvironment());

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.warnings).isEmpty();
    assertThat(result.errors).isEmpty();
  }

  @Test
  public void testEs6ModuleEntryPointWithSquareBracketsInFilename() throws Exception {
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("/index[0].js", "import foo from './foo.js'; foo('hello');"),
            SourceFile.fromCode("/foo.js", "export default (foo) => { alert(foo); }"));

    ImmutableList<ModuleIdentifier> entryPoints =
        ImmutableList.of(ModuleIdentifier.forFile("/index[0].js"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.pruneLegacyForEntryPoints(entryPoints));

    List<SourceFile> externs =
        AbstractCommandLineRunner.getBuiltinExterns(options.getEnvironment());

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.warnings).isEmpty();
    assertThat(result.errors).isEmpty();
  }

  @Test
  public void testBrowserFeaturesetYear2020() {
    CompilerOptions options = createCompilerOptions();
    options.setBrowserFeaturesetYear(2020);

    String googDefine =
        """
        /** @define {number} */
        goog.FEATURESET_YEAR = 2012;
        """;
    String googDefineOutput =
        """
        goog.FEATURESET_YEAR=2020;
        """;

    // async generators and for await ... of are emitted untranspiled.
    test(
        options,
        googDefine
            + """
            async function* foo() {yield 1; await 0; yield 2;}
            async function bar() {
              for await (const val of foo()) {
                console.log(val);
              }
            }
            bar();
            """,
        googDefineOutput
            + """
            async function* foo() {yield 1; await 0; yield 2}
            async function bar() {
              for await (const val of foo())
                console.log(val)
            }
            bar()
            """);

    // So is object rest and spread.
    test(
        options,
        googDefine
            + """
            const {foo, ...bar} = {foo: 10, bar: 20, ...{baz: 30}};
            console.log(foo);
            console.log(bar);
            """,
        googDefineOutput
            + """
            const {foo, ...bar} = {foo: 10, bar: 20, ...{baz: 30}};
            console.log(foo);
            console.log(bar)
            """);

    // But we won't emit ES 2018 regexp features.
    DiagnosticGroup untranspilable = DiagnosticGroups.UNTRANSPILABLE_FEATURES;
    test(options, googDefine + "/foo/s", untranspilable);
    test(options, googDefine + "/(?<foo>.)/", untranspilable);
    test(options, googDefine + "/(?<=foo)/", untranspilable);
    test(options, googDefine + "/(?<!foo)/", untranspilable);
    test(options, googDefine + "/\\p{Number}/u", untranspilable);
  }

  @Test
  public void testBrowserFeaturesetYear2021() {
    CompilerOptions options = createCompilerOptions();
    options.setBrowserFeaturesetYear(2021);

    String googDefine =
        """
        /** @define {number} */
        goog.FEATURESET_YEAR = 2012;
        """;
    String googDefineOutput = "goog.FEATURESET_YEAR=2021;";

    // bigints are emitted untranspiled.
    test(options, googDefine + "const big = 42n;", googDefineOutput + "const big = 42n;");

    // So is optional chaining
    test(
        options,
        googDefine + "document.querySelector('input')?.children?.[0];",
        googDefineOutput + "document.querySelector('input')?.children?.[0];");

    // We won't emit regexp lookbehind.
    DiagnosticGroup untranspilable = DiagnosticGroups.UNTRANSPILABLE_FEATURES;
    test(options, googDefine + "/(?<=oo)/", untranspilable);
    test(options, googDefine + "/(?<!foo)/", untranspilable);

    // But we will emit other ES2018 regexp features
    test(options, googDefine + "/foo/s", googDefineOutput + "/foo/s");
    test(options, googDefine + "/(?<foo>.)/", googDefineOutput + "/(?<foo>.)/");
    test(options, googDefine + "/\\p{Number}/u", googDefineOutput + "/\\p{Number}/u");
  }

  @Test
  public void testAccessControlsChecks_esClosureInterop_destructuringRequireModule() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.VISIBILITY, CheckLevel.WARNING);
    options.setCheckTypes(true);
    options.setChecksOnly(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    test(
        options,
        new String[] {
          """
          goog.module('my.Foo');
          /** @private */
          exports.fn = function() {}
          """,
          """
          const {fn} = goog.require('my.Foo');
          fn();
          export {};
          """
        },
        DiagnosticGroups.VISIBILITY);
  }

  @Test
  public void testAccessControlsChecks_esClosureInterop_requireModule() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.VISIBILITY, CheckLevel.WARNING);
    options.setCheckTypes(true);
    options.setChecksOnly(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    test(
        options,
        new String[] {
          """
          goog.module('my.Foo');
          class Foo {
            /** @private */
            static build() {}
          }
          exports = Foo;
          """,
          """
          const Foo = goog.require('my.Foo');
          Foo.build();
          export {};
          """
        },
        DiagnosticGroups.VISIBILITY);
  }

  @Test
  public void testIsolatePolyfills_transpileOnlyMode() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.STABLE_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRewritePolyfills(true);
    options.setIsolatePolyfills(true);
    options.setSkipNonTranspilationPasses(true);

    externs =
        ImmutableList.of(
            SourceFile.fromCode("testExterns.js", new TestExternsBuilder().addAlert().build()));

    // Polyfill isolation currently doesn't work with transpileOnly mode. It depends on runtime
    // library injection not being disabled.
    assertThrows(Exception.class, () -> compile(options, "alert('hi'.startsWith('h'));"));
  }

  @Test
  public void testArrayOfType() {
    CompilerOptions options = createCompilerOptions();
    options.addWarningsGuard(
        new DiagnosticGroupWarningsGuard(DiagnosticGroups.STRICT_CHECK_TYPES, CheckLevel.WARNING));
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "es6.js",
            """
            /**
             * @param {...T} var_args
             * @return {!Array<T>}
             * @template T
             */
            Array.of = function(var_args) {};
            """));
    externs = externsList.build();

    test(
        options,
        """
        const array = Array.of('1', '2', '3');
        if (array[0] - 1) {}
        """,
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS);
  }

  @Test
  public void testNewTarget() {
    // Repro case for Github issue 3607.  Don't crash with a reference to new.target
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    WarningLevel.QUIET.setOptionsForWarningLevel(options);
    test(
        options,
        """
        window.Class = class {
          constructor() {
            let newTarget = new.target;
            return Object.create(newTarget.prototype);
          }
        };
        """,
        """
        window.a = class {
          constructor() {
            let b = new.target;
            return Object.create(b.prototype);
          }
        };
        """);
  }

  @Test
  public void testGitHubIssue3637() {
    // Document that we break scoping even in Whitespace only mode when downleveling to ES5.
    // This is problematic because WHITESPACE_ONLY and SIMPLE are often done file-by-file,
    // so it is difficult to even pick a unique name for "foo" to avoid conflicts with other files.

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        """
        {
          function foo() {}
          console.log(foo());
        }
        """,
        """
        {
          var foo = function () {} // now global scope, may cause conflicts
          console.log(foo());
        }
        """);
  }

  @Test
  public void testGoogScopeWithAngular() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setAngularPass(true);

    test(
        options,
        """
        goog.scope(function() {
        /** @ngInject */
        function fn(a, b) {}
        });
        """,
        """
        var $jscomp$scope$98447280$0$fn = function(a, b) {};
        $jscomp$scope$98447280$0$fn["$inject"] = ["a", "b"];
        """);
  }

  @Test
  public void testRewriteCallerCodeLocation() {
    // This unit test tests the following:
    // (1) RewriteCallerCodeLocation pass adds the code location to the call-site of functions
    // that have goog.callerLocation as a default parameter.
    // (2) ReplaceIdGenerators pass replaces the code location with an obfuscated string.
    CompilerOptions options = createCompilerOptions();

    options.setReplaceIdGenerators(true);

    test(
        options,
        """
        /** @idGenerator {consistent} */
        goog.callerLocationIdInternalDoNotCallOrElse = function(id) {
          return /** @type {!goog.CodeLocation} */ (id);
        };
        function signal(here = goog.callerLocation()) {}
        const mySignal = signal();
        const mySignal2 = signal();
        const mySignal3 = signal();
        """,
        """
        goog.callerLocationIdInternalDoNotCallOrElse = function(id) {
          return id;
        };
        function signal(here) {
          here = here === void 0 ? goog.callerLocation() : here;
        }
        var mySignal = signal('a');
        var mySignal2 = signal('b');
        var mySignal3 = signal('c');
        """);
  }

  @Test
  public void testGitHubIssue3861() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);

    test(
        options,
        """
        (function () {
          arguments (628);
        })
        """,
        DiagnosticGroups.CHECK_TYPES);
  }

  @Test
  public void forceClassTranspilationKeepAsync_withNoTranspile() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);
    options.setExperimentalForceTranspiles(ExperimentalForceTranspile.CLASS);

    // test transpiling classes but leave async functions untranspiled
    test(
        options,
        "window['C'] = /** @dict */ class C { async f(p) { await p; return 0; } }",
        """
        const $jscomp$classDecl$98447280$0 = function() {};
        $jscomp$classDecl$98447280$0.prototype.f = async function(p) { await p; return 0 };
        window['C'] = $jscomp$classDecl$98447280$0
        """);
  }

  @Test
  public void forceLetConstTranspilationKeepAsync_doesNotKeepClass_withNoTranspile() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);
    options.setExperimentalForceTranspiles(ExperimentalForceTranspile.LET_CONST);

    // test transpiling classes but leave async functions untranspiled
    test(
        options,
        "window['C'] = /** @dict */ class C { async f(p) { await p; return 0; } }",
        """
        var $jscomp$classDecl$98447280$0 = function() {};
        $jscomp$classDecl$98447280$0.prototype.f = async function(p) { await p; return 0 };
        window['C'] = $jscomp$classDecl$98447280$0
        """);
  }

  @Test
  public void forceClassTranspilationKeepAsyncFunctions_withEs2021Out() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    options.setExperimentalForceTranspiles(ExperimentalForceTranspile.CLASS);

    // test transpiling classes but leave async functions untranspiled
    test(
        options,
        "window['C'] = /** @dict */ class C { async f(p) { await p; return 0; } }",
        """
        const $jscomp$classDecl$98447280$0 = function() {};
        $jscomp$classDecl$98447280$0.prototype.f = async function(p) { await p; return 0 };
        window['C'] = $jscomp$classDecl$98447280$0
        """);
  }

  @Test
  public void forceLetConstTranspilationKeepsAsyncFunctions_doesNotKeepClass_withEs2021Out() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    options.setExperimentalForceTranspiles(ExperimentalForceTranspile.LET_CONST);

    // test transpiling let/const, classes but leave async functions untranspiled
    test(
        options,
        "window['C'] = /** @dict */ class C { async f(p) { await p; return 0; } }",
        """
        var $jscomp$classDecl$98447280$0 = function() {};
        $jscomp$classDecl$98447280$0.prototype.f = async function(p) { await p; return 0 };
        window['C'] = $jscomp$classDecl$98447280$0
        """);
  }

  @Test
  public void forceTranspileExceptAsyncAwait_doesNotRemoveAsyncAwait() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(
        LanguageMode
            .ECMASCRIPT_2021); // does not matter when setForceTranspileExceptAsyncAwait(true)
    options.setExperimentalForceTranspiles(ExperimentalForceTranspile.ALL_EXCEPT_ASYNC_AWAIT);

    // test transpiling let/const and classes but leave async functions untranspiled
    test(
        options,
        "window['C'] = /** @dict */ class C { async f(p) { await p; return 0; } }",
        """
        var $jscomp$classDecl$98447280$0 = function() {};
        $jscomp$classDecl$98447280$0.prototype.f = async function(p) { await p; return 0 };
        window['C'] = $jscomp$classDecl$98447280$0
        """);
  }

  @Test
  public void forceTranspileExceptAsyncAwait_doesNotRemoveAsyncAwait2() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(
        LanguageMode
            .ECMASCRIPT_2021); // does not matter when setForceTranspileExceptAsyncAwait(true)
    options.setExperimentalForceTranspiles(ExperimentalForceTranspile.ALL_EXCEPT_ASYNC_AWAIT);

    // test transpiling let/const, opt-chain and classes but leave async functions untranspiled
    test(
        options,
        "class C { async f(p) { let obj = await p; return obj?.prop; } }",
        """
        var C=function(){};
        C.prototype.f=async function(p){var obj=await p;
          var $jscomp$optchain$tmp98447280$0;
          return($jscomp$optchain$tmp98447280$0=obj)==null?void 0:$jscomp$optchain$tmp98447280$0.prop}
        """);
  }

  @Test
  public void forceTranspileExceptAsyncAwait_withEs5Out() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setExperimentalForceTranspiles(ExperimentalForceTranspile.ALL_EXCEPT_ASYNC_AWAIT);

    // respects ES5 mode and lowers async await down to ES5 despite forcing `ALL_EXCEPT_ASYNC_AWAIT`
    testNoWarnings(options, "class C { async f(p) { let obj = await p; return obj?.prop; } }");
    assertThat(lastCompiler.toSource()).doesNotContain("async ");
    assertThat(lastCompiler.toSource()).doesNotContain("await ");
  }

  @Test
  public void forceClassTranspilationKeepDestructuring_withEs2015Out() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setExperimentalForceTranspiles(ExperimentalForceTranspile.CLASS);

    // check that we can transpile the ES2016 `**` + classes, but leave the ES2015 destructuring
    // parameter behind.
    test(
        options,
        "window['C'] = /** @dict */ class C { f({num}) { return num ** 3; } }",
        """
        const $jscomp$classDecl$98447280$0 = function() {};
        $jscomp$classDecl$98447280$0.prototype.f = function({num}) { return Math.pow(num, 3) };
        window['C'] = $jscomp$classDecl$98447280$0
        """);
  }

  @Test
  public void forceLetConstTranspilationAlsoLowersObjectDestructuringAndClasses_withEs2015Out() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setExperimentalForceTranspiles(ExperimentalForceTranspile.LET_CONST);
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_AND_VALIDATE_FIELDS);
    options.setPrettyPrint(true);
    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(new TestExternsBuilder().addJSCompLibraries().buildExternsFile("jscomp.js"))
            .build();

    // check that we transpile the ES2016 `**`, let/const, classes and the ES2015 destructuring
    // parameter
    test(
        options,
        """
        window['C'] = /** @dict */ class C {
          f({num}) {
            return num ** 3;
          }
        }
        """,
        """
        var $jscomp$classDecl$98447280$0 = function() {};
        $jscomp$classDecl$98447280$0.prototype.f = function($jscomp$destructuring$var0) {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          var num = $jscomp$destructuring$var1.num;
          return Math.pow(num, 3)
        };
        window['C'] = $jscomp$classDecl$98447280$0
        """);
  }

  @Test
  public void forceLetConstTranspilationAlsoLowersArrayDestructuring_withEs2015Out() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setExperimentalForceTranspiles(ExperimentalForceTranspile.LET_CONST);
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_AND_VALIDATE_FIELDS);
    options.setPrettyPrint(true);
    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(new TestExternsBuilder().addJSCompLibraries().buildExternsFile("jscomp.js"))
            .build();

    // This test is to document the current compiler behavior of
    // ExperimentalForceTranspile.LET_CONST also transpiling array destructuring parameters.
    // This isn't strictly necessary, but otherwise would require more refactoring of the
    // transpilation passes.
    test(
        options,
        """
        window['C'] = function([...strs]) {
          return `${strs}`;
        };
        """,
        """
        window["C"] = function($jscomp$destructuring$var0) {
          var $jscomp$destructuring$var1 = (0,$jscomp.makeIterator)($jscomp$destructuring$var0);
          var strs = (0,$jscomp.arrayFromIterator)($jscomp$destructuring$var1);
          return `${strs}`;
        };
        """);
  }

  @Test
  public void forceLetConstTranspilationAlsoLowersDefaultParameters_withEs2015Out() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setExperimentalForceTranspiles(ExperimentalForceTranspile.LET_CONST);
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_AND_VALIDATE_FIELDS);
    options.setPrettyPrint(true);
    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(new TestExternsBuilder().addJSCompLibraries().buildExternsFile("jscomp.js"))
            .build();

    // This test is to document the current compiler behavior of
    // ExperimentalForceTranspile.LET_CONST also transpiling default parameters.
    // This isn't strictly necessary, but otherwise would require more refactoring of the
    // transpilation passes.
    test(
        options,
        """
        window['C'] = function(str = '') {
          return `${str}`;
        };
        """,
        """
        window["C"] = function(str) {
          str = str === void 0 ? "" : str;
          return `${str}`;
        };
        """);
  }

  @Test
  public void testNoSideEffectsPropagationOnStaticMembers_withEs2018Out() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);

    test(
        options,
        """
        class ClazzWithStatic {
        constructor() {}

          /** @nosideeffects */
          static Create() {
            if (Math.random() > .5) {
              throw new Error('Bad input');
            }
            return new ClazzWithStatic();
          }
        }

        const xUnused = ClazzWithStatic.Create();
        const yUnused = ClazzWithStatic.Create();
        """,
        // This should optimize to nothing, because the two variables are unused and we are trying
        // to hide side-effects.
        "");
  }

  @Test
  public void test_b454591584_full() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);
    options.setGeneratePseudoNames(true);

    // Showcases the state of the code that led to b/453893252.
    test(
        options,
        new String[] {
          // i0.js
          """
          goog.provide('jspb$e.platypi$duck$parts$Event$Type');

          /** @enum {number} */
          jspb$e.platypi$duck$parts$Event$Type = {
            UNKNOWN: 0,
            TYPE_ONE: 1
          };
          """,
          // i1.js
          """
          goog.provide('jspb$platypi$duck$parts$MutableEvent');

          goog.requireType('jspb$e.platypi$duck$parts$Event$Type');

          /** @final */
          jspb$platypi$duck$parts$MutableEvent = class {
            /**
             * @usedViaDotConstructor
             * @param {!jspb$e.platypi$duck$parts$Event$Type} type
             */
            constructor(type) {
              /** @private {!jspb$e.platypi$duck$parts$Event$Type} */
              this.type_ = type;
            }

            /** @return {!jspb$e.platypi$duck$parts$Event$Type} */
            getType() {
              return this.type_;
            }
          };
          """,
          // i2.js
          """
          goog.provide('proto.platypi.duck.parts.Event');
          /** @provideAlreadyProvided */
          goog.provide('proto.platypi.duck.parts.Event.Type');

          goog.require('jspb$e.platypi$duck$parts$Event$Type');
          goog.require('jspb$platypi$duck$parts$MutableEvent');

          /** @const */
          proto.platypi.duck.parts.Event = jspb$platypi$duck$parts$MutableEvent;

          /** @const */
          jspb$platypi$duck$parts$MutableEvent.Type = jspb$e.platypi$duck$parts$Event$Type;
          """,
          // i3.js
          """
          /**
           * @fileoverview added by tsickle
           * @suppress {checkTypes} added by tsickle
           * @suppress {extraRequire} added by tsickle
           * @suppress {missingRequire} added by tsickle
           * @suppress {uselessCode} added by tsickle
           * @suppress {suspiciousCode} added by tsickle
           * @suppress {missingReturn} added by tsickle
           * @suppress {unusedLocalVariables} added by tsickle
           * @suppress {missingOverride} added by tsickle
           * @suppress {const} added by tsickle
           */
          goog.module('google3.third_party.javascript.angular2.rc.packages.core.src.render3.definition');

          /**
           * @record
           * @template T
           */
          function ComponentDefinition() { }
          /* istanbul ignore if */
          if (false) {
            /**
             * @type {function((T|?)): void}
             * @public
             */
            ComponentDefinition.prototype.template;
          }

          /**
           * @template T
           * @param {!ComponentDefinition<T>} componentDefinition
           * @return {!ComponentDefinition<T>}
           */
          function ɵɵdefineComponent(componentDefinition) {
            return componentDefinition;
          }

          exports.ɵɵdefineComponent = ɵɵdefineComponent;
          """,
          // i4.js
          """
          /**
           * @fileoverview added by tsickle
           * @suppress {checkTypes} added by tsickle
           * @suppress {extraRequire} added by tsickle
           * @suppress {missingRequire} added by tsickle
           * @suppress {uselessCode} added by tsickle
           * @suppress {suspiciousCode} added by tsickle
           * @suppress {missingReturn} added by tsickle
           * @suppress {unusedLocalVariables} added by tsickle
           * @suppress {missingOverride} added by tsickle
           * @suppress {const} added by tsickle
           */
          goog.module('event_component');

          const goog_proto_platypi_duck_parts_Event_1 = goog.require('proto.platypi.duck.parts.Event');
          const tsickle_Event_41 = goog.requireType("proto.platypi.duck.parts.Event");
          const i0 = goog.require('google3.third_party.javascript.angular2.rc.packages.core.src.render3.definition');

          const event_proto_1 = {};
          /** @const */ event_proto_1.Event = goog_proto_platypi_duck_parts_Event_1;

          class EventComponent {
            constructor() {
              this.event = function() {
                return new event_proto_1.Event(/** @type {?} */ (1));
              };
              this.Event = event_proto_1.Event;
            }
          }

          /** @nocollapse */ EventComponent.ɵcmp =
              /** @pureOrBreakMyCode */ i0.ɵɵdefineComponent({
                type: EventComponent,
                template: function EventComponent_Template(ctx) {
                  if (ctx.event().getType() === ctx.Event.Type.TYPE_ONE) {
                    return 'one';
                  } else {
                    return 'unknown';
                  }
                }
              });

          /* istanbul ignore if */
          if (false) {
            /**
             * @const {function(): !event_proto_1.Event}
             * @protected
             */
            EventComponent.prototype.event;
            /**
             * @const {function(new:tsickle_Event_41, (undefined|null|!Array<?>)=)}
             * @protected
             */
            EventComponent.prototype.Event;
          }

          // Manual code for retention.
          alert(EventComponent.ɵcmp.template(new EventComponent()));
          """
        },
        new String[] {
          "",
          "",
          "",
          "",
          """
          // TODO: b/454591584 - Event.Type should not have been collapsed / pruned.
          var $jspb$platypi$duck$parts$MutableEvent$$ = class {
            constructor($type$$) {
              this.$type_$ = $type$$;
            }
          };
          class $module$contents$event_component_EventComponent$$ {
            constructor() {
              this.$event$ = function() {
                return new $jspb$platypi$duck$parts$MutableEvent$$(1);
              };
              this.$Event$ = $jspb$platypi$duck$parts$MutableEvent$$;
            }
          }
          $module$contents$event_component_EventComponent$$.$a$ = {
            $type$: $module$contents$event_component_EventComponent$$,
            $template$: function($ctx$$) {
              return $ctx$$.$event$().$type_$ === $ctx$$.$Event$.$Type$.$TYPE_ONE$ ?
                  'one' :
                  'unknown';
            }
          };
          alert($module$contents$event_component_EventComponent$$.$a$.$template$(
              new $module$contents$event_component_EventComponent$$()));
          """
        });
  }

  @Test
  public void test_b454591584_simplified() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);
    options.setGeneratePseudoNames(true);

    test(
        options,
        """
        /** @enum {number} */
        var Event$Type = {
          UNKNOWN: 0,
          TYPE_ONE: 1
        };

        class Event {}

        /** @const */
        Event.Type = Event$Type;

        class EventComponent {
          constructor() {
            /** @const */
            this.Event = Event;
          }
        }

        /** @param {?} x */
        function foo(x) {
          return x.Event.Type.TYPE_ONE;
        }

        const obj = new EventComponent();
        alert(foo(obj));
        """,
        """
        // TODO: b/454591584 - Event.Type should not have been collapsed / pruned.
        class $Event$$ {}

        class $EventComponent$$ {
          constructor() {
            this.$a$ = $Event$$;
          }
        }
        alert((new $EventComponent$$()).$a$.$Type$.$TYPE_ONE$);
        """);
  }

  @Test
  public void testDeclareLegacyNamespaceSubModuleCrash() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2016);

    options.setRemoveUnusedVariables(Reach.NONE);
    options.setRemoveUnusedClassProperties(false);
    options.setDeadAssignmentElimination(false);

    // Ensures that references to aliases that occur within member function defs do not crash the
    // compiler (see b/293184904). In this example, the name 'method' in 'static method() {...}'
    // is a reference to a.b.Foo.method, but since the reference is not fully qualified, the
    // compiler should not try to touch it.
    //
    // This test case contains a use of a computed property to ensure that the fix for the above
    // problem does not affect computed properties.
    test(
        options,
        new String[] {
          """
          goog.module('a.b');
          goog.module.declareLegacyNamespace();
          var b = {};
          exports = b;
          """,
          """
          goog.provide('a.b.Foo');
          a.b.Foo = class {
            static method() {}
            static getVarName() { return 'someVar'; }
          };
          var x = {[a.b.Foo.getVarName()]: '4'};
          alert(x['someVar']);
          """
        },
        new String[] {
          "", "alert(\"4\");",
        });
  }

  @Test
  public void testComputedClassPropertyWithAwait() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    options.setBrowserFeaturesetYear(2023);
    test(
        options,
        """
        /** @define {number} */
        goog.FEATURESET_YEAR = 2023;

        async function getVal() {
          return 1;
        }

        async function asyncFunc() {
          /** @unrestricted */
          class C {
            [await getVal()] = 1;
          }
          return new C()[1];
        }

        asyncFunc().then(alert);
        """,
        """
        goog.a = 2023;
        async function a() {
          return 1;
        }
        (async function() {
          var b = await a();
          class c {
            [b] = 1;
          }
          return (new c())[1];
        })().then(alert);
        """);
  }
}
