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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CompilerTestCase.CLOSURE_DEFS;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Chars;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerTestCase.NoninjectingCompiler;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.NodeSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for the compiler running with {@link CompilationLevel.ADVANCED}. */
@RunWith(JUnit4.class)
public final class AdvancedOptimizationsIntegrationTest extends IntegrationTestCase {

  @Test
  public void testDisambiguationOfForwardReferenedAliasedInterface() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_2015);
    options.setDisambiguateProperties(true);
    options.setPrettyPrint(true);
    options.setPreserveTypeAnnotations(true);
    externs =
        ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs.js"));
    test(
        options,
        lines(
            "",
            // base class doesn't say it implements AliasedInterface,
            // but it does have a matching `foo()` method.
            "class MyBaseClass {",
            "    /** @return {void} */",
            "    foo() {",
            "        console.log('I should exist');",
            "    }",
            "}",
            "",
            // subclass claims to implement the interface, relying on the superclass implementation
            // of `foo()`.
            // Note that `AliasedInterface` isn't defined yet.
            "/** @implements {AliasedInterface} */",
            "class MyClass extends MyBaseClass {",
            "}",
            "",
            // AliasedInterface is originally defined using a different name.
            // This can happen due to module rewriting even if the original
            // code doesn't appear to do this.
            "/** @record */",
            "function OriginalInterface() { }",
            "/** @return {void} */",
            "OriginalInterface.prototype.foo = function () { };",
            "",
            // AliasedInterface is defined after it was used above.
            // Because of this the compiler previously failed to connect the `foo()` property
            // on `OriginalInterface` with the one on `MyBaseClass`, leading to a disambiguation
            // error.
            "/** @const */",
            "const AliasedInterface = OriginalInterface;",
            "",
            // Factory function ensures that the call to `foo()` below
            // is seen as `AliasedInterface.prototype.foo` rather than
            // `MyClass.prototype.foo`.
            "/** @return {!AliasedInterface} */",
            "function magicFactory() {",
            "    return new MyClass();",
            "}",
            "",
            "magicFactory().foo();"),
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
    options.setLanguage(LanguageMode.ECMASCRIPT_2015);
    options.setDisambiguateProperties(true);
    options.setPrettyPrint(true);
    options.setPreserveTypeAnnotations(true);
    externs =
        ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs.js"));
    test(
        options,
        lines(
            "",
            // base class doesn't say it implements AliasedInterface,
            // but it does have a matching `foo()` method.
            "class MyBaseClass {",
            "    /** @return {string} */",
            "    foo() {",
            "        console.log('I should exist');",
            "        return 'return value';",
            "    }",
            "}",
            "",
            // subclass claims to implement the interface, relying on the superclass implementation
            // of `foo()`.
            // Note that `AliasedInterface` isn't defined yet.
            "/** @implements {AliasedInterface<string>} */",
            "class MyClass extends MyBaseClass {",
            "}",
            "",
            // AliasedInterface is originally defined using a different name.
            // This can happen due to module rewriting even if the original
            // code doesn't appear to do this.
            "/**",
            " * @record",
            " * @template T",
            " */",
            "function OriginalInterface() { }",
            "/** @return {T} */",
            "OriginalInterface.prototype.foo = function () { };",
            "",
            // AliasedInterface is defined after it was used above.
            // Because of this the compiler previously failed to connect the `foo()` property
            // on `OriginalInterface` with the one on `MyBaseClass`, leading to a disambiguation
            // error.
            "/** @const */",
            "const AliasedInterface = OriginalInterface;",
            "",
            // Factory function ensures that the call to `foo()` below
            // is seen as `AliasedInterface.prototype.foo` rather than
            // `MyClass.prototype.foo`.
            "/** @return {!AliasedInterface<string>} */",
            "function magicFactory() {",
            "    return new MyClass();",
            "}",
            "",
            "magicFactory().foo();"),
        // Compiler correctly recognises the call to `foo()` as referencing
        // the implementation in MyBaseClass, so that implementation ends
        // up inlined as the only output statement.
        "console.log('I should exist');");
  }

  @Test
  public void testBigInt() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    // Confirm that the native BigInt type has been implemented, so the BigInt externs don't cause
    // errors.
    externs = ImmutableList.of(new TestExternsBuilder().addBigInt().buildExternsFile("externs.js"));
    test(options, "BigInt(1)", "BigInt(1)");
  }

  @Test
  public void testUnusedTaggedTemplateLiteralGetsRemoved() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
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
        lines(
            "var i18n_7;",
            "var ngI18nClosureMode = true;",
            "if (ngI18nClosureMode) {",
            "    /**",
            "     * @desc Some message",
            "     */",
            "    const MSG_A = goog.getMsg(\"test\");",
            "    i18n_7 = MSG_A;",
            "}",
            "else {",
            "    i18n_7 = $localize `...`;",
            "}",
            "console.log(i18n_7);",
            ""),
        lines(
            // Tagged template literal was correctly removed.
            "console.log(goog.getMsg('test'));"));
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
        lines(
            "function Super() { }", // A pure function that *might* be used as a constructor.
            "",
            "class Sub extends Super {",
            "  constructor() {",
            // We can't delete this call despite being pointless. It's syntactically required.
            "    super();",
            "  }",
            "}",
            "",
            // We have to use the class somehow or the entire this is removable.
            "alert(new Sub());"));
  }

  @Test
  public void testForOfDoesNotFoolSideEffectDetection() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);

    // we don't want to see injected library code in the output
    useNoninjectingCompiler = true;
    test(
        options,
        lines(
            "class C {",
            "  constructor(elements) {",
            "    this.elements = elements;",
            // This call will be preserved, but inlined, because it has side effects.
            "    this.m1();",
            "  }",
            // m1() must be considered to have side effects because it taints a non-local object
            // through basically this.elements[i].sompProp.
            "  m1() {",
            "    for (const element of this.elements) {",
            "      element.someProp = 1;",
            "    }",
            "  }",
            "}",
            "new C([]);"),
        lines(
            "class C {",
            "  constructor(elements) {",
            "    this.elements = elements;",
            "    for (const element of this.elements) {",
            "      element.someProp = 1;",
            "    }",
            "  }",
            "}",
            "new C([]);"));
  }

  @Test
  public void testIssue2822() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        lines(
            // this method will get inlined into the constructor
            "function classCallCheck(obj, ctor) {",
            "  if (!(obj instanceof ctor)) {",
            "    throw new Error('cannot call a class as a function');",
            "  }",
            "}",
            "/** @constructor */",
            "var C = function InnerC() {",
            // Before inlining happens RemoveUnusedCode sees one use of InnerC,
            // which prevents its removal.
            // After inlining it sees `this instanceof InnerC` as the only use of InnerC.
            // Make sure RemoveUnusedCode recognizes that the value of InnerC escapes.
            "  classCallCheck(this, InnerC);",
            "};",
            // This creates an instance of InnerC, so RemoveUnusedCode should not replace
            // `this instanceof InnerC` with `false`.
            "alert(new C());"),
        lines(
            "alert(",
            "    new function a() {",
            "      if (!(this instanceof a)) {",
            "        throw Error(\"cannot call a class as a function\");",
            "      }",
            "    })",
            ""));
  }

  @Test
  public void testNoInline() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        lines(
            "var namespace = {};", // preserve newlines
            "/** @noinline */ namespace.foo = function() { alert('foo'); };",
            "namespace.foo();"),
        lines(
            "function a() { alert('foo'); }", // preserve newlines
            "a();"));
    test(
        options,
        lines(
            "var namespace = {};", // preserve newlines
            "namespace.foo = function() { alert('foo'); };",
            "namespace.foo();"),
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
  public void testAdvancedModeIncludesExtraSmartNameRemoval() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        LINE_JOINER.join(
            "(function() {",
            "  /** @constructor} */",
            "  function Bar() {}",
            "  var y = Bar;",
            "  new y();",
            "})();"),
        "");
  }

  @Test
  public void testBug139862607() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2019);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        new String[] {lines("var x = 1;"), lines("import * as y from './i0.js';", "const z = y.x")},
        // Property x never defined on module$i0
        TypeCheck.INEXISTENT_PROPERTY);
  }

  @Test
  public void testReferenceToInternalClassName() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_2015);
    options.setEmitUseStrict(false); // 'use strict'; is just noise here
    options.setPrettyPrint(true);
    options.setGeneratePseudoNames(true);

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addExtra("function use(x) {}").buildExternsFile("externs"));
    test(
        options,
        new String[] {
          CLOSURE_DEFS,
          lines(
              "goog.module('a.b.c');",
              "exports = class Foo {",
              // Reference to static property via inner class name must be changed
              // to reference via global name (AggressiveInlineAliases), then collapsed
              // (CollapseProperties) to get full optimization and avoid generating broken code.
              "  method() { return Foo.EventType.E1; }",
              "};",
              "",
              "/** @enum {number} */",
              "exports.EventType = {",
              "  E1: 1,",
              "};",
              ""),
          lines(
              "", //
              "goog.module('a.b.d');",
              "const Foo = goog.require('a.b.c');",
              "use(new Foo().method());",
              "")
        },
        new String[] {
          "",
          "",
          lines(
              // TODO(bradfordcsmith): Why is `new class {};` left behind?
              "new class {};", //
              "use(1)"),
        });
  }

  @Test
  public void testTypedefProvides() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        lines(
            "/** @const */",
            "var goog = {};",
            "goog.provide('ns');",
            "goog.provide('ns.SomeType');",
            "goog.provide('ns.SomeType.EnumValue');",
            "goog.provide('ns.SomeType.defaultName');",
            // subnamespace assignment happens before parent.
            "/** @enum {number} */",
            "ns.SomeType.EnumValue = { A: 1, B: 2 };",
            // parent namespace isn't ever actually assigned.
            // we're relying on goog.provide to provide it.
            "/** @typedef {{name: string, value: ns.SomeType.EnumValue}} */",
            "ns.SomeType;",
            "/** @const {string} */",
            "ns.SomeType.defaultName = 'foobarbaz';"),
        // the provides should be rewritten, then collapsed, then removed by RemoveUnusedCode
        "");
  }

  @Test
  public void testExpose() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    // TODO(tbreisacher): Re-enable dev mode and fix test failure.
    options.setDevMode(DevMode.OFF);

    test(options,
        new String[] {"var x = {eeny: 1, /** @expose */ meeny: 2};" +
            "/** @constructor */ var Foo = function() {};" +
            "/** @expose */  Foo.prototype.miny = 3;" +
            "Foo.prototype.moe = 4;" +
            "/** @expose */  Foo.prototype.tiger;" +
            "function moe(a, b) { return a.meeny + b.miny + a.tiger; }" +
            "window['x'] = x;" +
            "window['Foo'] = Foo;" +
            "window['moe'] = moe;"},
        new String[] {"function a(){}" +
            "a.prototype.miny=3;" +
            "window.x={a:1,meeny:2};" +
            "window.Foo=a;" +
            "window.moe=function(b,c){" +
            "  return b.meeny+c.miny+b.tiger" +
            "}"},
        new DiagnosticType[]{
            CheckJSDoc.ANNOTATION_DEPRECATED,
            CheckJSDoc.ANNOTATION_DEPRECATED,
            CheckJSDoc.ANNOTATION_DEPRECATED});
  }

  @Test
  public void testArrayValuesIsPolyfilledForEs2015Out() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setRewritePolyfills(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);

    useNoninjectingCompiler = true;
    compile(options, new String[] {"for (const x of [1, 2, 3].values()) { alert(x); }"});

    assertThat(lastCompiler.getResult().errors).isEmpty();
    assertThat(lastCompiler.getResult().warnings).isEmpty();
    assertThat(((NoninjectingCompiler) lastCompiler).injected).containsExactly("es6/array/values");
  }

  @Test
  public void testWindowIsTypedEs6() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setCheckTypes(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);
    test(
        options,
        LINE_JOINER.join(
            "for (var x of []);", // Force injection of es6_runtime.js
            "var /** number */ y = window;"),
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testRemoveUnusedClass() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    String code =
        lines(
            "/** @constructor */ function Foo() { this.bar(); }",
            "Foo.prototype.bar = function() { return new Foo(); };");
    test(options, code, "");
  }

  @Test
  public void testClassWithGettersIsRemoved() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "class Foo { get xx() {}; set yy(v) {}; static get init() {}; static set prop(v) {} }";

    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setExtraSmartNameRemoval(false);
    test(options, code, "");
  }

  @Test
  public void testExportOnClassGetter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
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
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
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
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    test(
        options,
        LINE_JOINER.join(
            "var goog = {};",
            "goog.exportSymbol = function(path, symbol) {};",
            "",
            "/** @export */",
            "class C {",
            "  /** @export @return {string} */ static get exportedName() {}",
            "};",
            "alert(C.exportedName);"),
        // TODO(tbreisacher): Find out why C is renamed to a despite the @export annotation.
        "class a {static get exportedName(){}} alert(a.exportedName)");
  }

  @Test
  public void testExportOnStaticSetter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPrettyPrint(true);

    useNoninjectingCompiler = true;
    test(
        options,
        LINE_JOINER.join(
            // hack replacement for compiler injected code
            "var $jscomp = { global: window };",
            // hack replacement for closure library code
            "var goog = {};",
            "goog.exportSymbol = function(path, symbol) {};",
            "",
            "class C {",
            "  /** @export @param {number} x */ static set exportedName(x) {}",
            "};",
            "C.exportedName = 0;"),
        LINE_JOINER.join(
            "var a = window;",
            "function b() {}",
            "b.exportedName;",
            "a.Object.defineProperties(",
            "    b,",
            "    {",
            "      exportedName: { configurable:!0, enumerable:!0, set:function() {} }",
            "    });",
            "b.exportedName = 0;"));
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
        LINE_JOINER.join(
            "function f(x) {",
            "  var a = x + 1;",
            "  var b = x + 1;",
            "  window.c = x > 5 ? a : b;",
            "}",
            "f(g);"),
        "window.a = g + 1;");
  }

  @Test
  public void testCheckStrictMode() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

    externs =
        ImmutableList.of(
            SourceFile.fromCode("externs", "var use; var arguments; arguments.callee;"));

    String code =
        "function App() {}\n"
            + "App.prototype.method = function(){\n"
            + "  use(arguments.callee)\n"
            + "};";

    test(options, code, "", StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN);
  }

  // http://blickly.github.io/closure-compiler-issues/#724
  @Test
  public void testIssue724() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setCheckSymbols(false);
    options.setCheckTypes(false);
    String code =
        LINE_JOINER.join(
            "isFunction = function(functionToCheck) {",
            "  var getType = {};",
            "  return functionToCheck && ",
            "      getType.toString.apply(functionToCheck) === '[object Function]';",
            "};");
    String result =
        lines(
            "",
            "isFunction = function(a){",
            "  var b={};",
            "  return a && '[object Function]' === b.toString.apply(a);",
            "}",
            "");

    test(options, code, result);
  }

  // http://blickly.github.io/closure-compiler-issues/#730
  @Test
  public void testIssue730() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    String code =
        "/** @constructor */function A() {this.foo = 0; Object.seal(this);}\n"
            + "/** @constructor */function B() {this.a = new A();}\n"
            + "B.prototype.dostuff = function() {this.a.foo++;alert('hi');}\n"
            + "new B().dostuff();\n";

    test(
        options,
        code,
        "function a(){this.b=0;Object.seal(this)}"
            + "(new function(){this.a=new a}).a.b++;"
            + "alert('hi')");

    options.setRemoveUnusedClassProperties(true);

    // This is still not a problem when removeUnusedClassProperties is enabled.
    test(
        options,
        code,
        "function a(){this.b=0;Object.seal(this)}"
            + "(new function(){this.a=new a}).a.b++;"
            + "alert('hi')");
  }

  @Test
  public void testAddFunctionProperties1() {
    String source =
        "var Foo = {};"
            + "var addFuncProp = function(o) {"
            + "  o.f = function() {}"
            + "};"
            + "addFuncProp(Foo);"
            + "alert(Foo.f());";
    String expected = "alert(void 0);";
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    test(options, source, expected);
  }

  @Test
  public void testAddFunctionProperties2a() {
    String source =
        ""
            + "/** @constructor */ function F() {}"
            + "var x = new F();"
            + "/** @this {F} */"
            + "function g() { this.bar = function() { alert(3); }; }"
            + "g.call(x);"
            + "x.bar();";
    String expected =
        ""
            + "var x = new function() {};"
            + "/** @this {F} */"
            + "(function () { this.bar = function() { alert(3); }; }).call(x);"
            + "x.bar();";

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
        ""
            + "/** @constructor */ function F() {}"
            + "var x = new F();"
            + "/** @this {F} */"
            + "function g() { this.bar = function() { alert(3); }; }"
            + "g.call(x);"
            + "x.bar();";
    String expected = "" + "var x = new function() {};" + "x.bar=function(){alert(3)};x.bar()";

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    test(options, source, expected);
  }

  @Test
  public void testAddFunctionProperties3() {
    String source =
        "/** @constructor */ function F() {}"
            + "var x = new F();"
            + "/** @this {F} */"
            + "function g(y) { y.bar = function() { alert(3); }; }"
            + "g(x);"
            + "x.bar();";
    String expected =
        "var x = new function() {};"
            + "/** @this {F} */"
            + "(function (y) { y.bar = function() { alert(3); }; })(x);"
            + "x.bar();";

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.OFF);
    test(options, source, expected);
  }

  @Test
  public void testAddFunctionProperties4() {
    String source =
        lines(
            "/** @constructor */",
            "var Foo = function() {};",
            "var goog = {};",
            "goog.addSingletonGetter = function(o) {",
            "  o.f = function() {",
            "    return o.i || (o.i = new o);",
            "  };",
            "};",
            "goog.addSingletonGetter(Foo);",
            "alert(Foo.f());");
    String expected =
        lines(
            "function Foo(){}",
            "Foo.f = function() { return Foo.i || (Foo.i = new Foo()); };",
            "alert(Foo.f());");

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
    options.syntheticBlockStartMarker = "START";
    options.syntheticBlockEndMarker = "END";
    test(
        options,
        lines(
            "const D = false;",
            "/**",
            " * @param {string} m",
            " */",
            "function b(m) {",
            " if (!D) return;",
            "",
            " START('debug');",
            " alert('Shouldnt be here' + m);",
            " END('debug');",
            "}",
            "/**",
            " * @param {string} arg",
            " */",
            "function a(arg) {",
            "  if (arg == 'log') {",
            "    b('logging 1');",
            "    b('logging 2');",
            "  } else {",
            "    alert('Hi!');",
            "  }",
            "}",
            "",
            "a(input);"),
        "'log' != input && alert('Hi!')");
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
        lines(
            "var goog = {};",
            "goog.inherits = function(x, y) {};",
            "var ns = {};",
            "/**",
            " * @constructor",
            " * @extends {ns.PageSelectionModel.FooEvent}",
            " */",
            "ns.PageSelectionModel.ChangeEvent = function() {};",
            "/** @constructor */",
            "ns.PageSelectionModel = function() {};",
            "/** @constructor */",
            "ns.PageSelectionModel.FooEvent = function() {};",
            "/** @constructor */",
            "ns.PageSelectionModel.SelectEvent = function() {};",
            "goog.inherits(ns.PageSelectionModel.ChangeEvent,",
            "    ns.PageSelectionModel.FooEvent);"),
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
        lines(
            "/** @const */",
            "var goog = {};",
            "goog.scope(function () {",
            "  /** @constructor */ function F(x) { this.x = x; }",
            "  alert(new F(1));",
            "});"),
        "alert(new function(){}(1));");
  }

  @Test
  public void testSuppressEs5StrictWarning() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.WARNING);
    test(options, "/** @suppress{es5Strict} */ function f() { var arguments; }", "");
  }

  @GwtIncompatible // b/63595345
  @Test
  public void testBrokenNameSpace() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    String code =
        lines(
            "var goog; goog.provide('i.am.on.a.Horse');",
            "i.am.on.a.Horse = function() {};",
            "i.am.on.a.Horse.prototype.x = function() {};",
            "i.am.on.a.Boat = function() {};",
            "i.am.on.a.Boat.prototype.y = function() {}");
    test(options, code, "");
  }

  @Test
  public void testNamelessParameter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.OFF);
    String code =
        "var impl_0;"
            + "$load($init());"
            + "function $load(){"
            + "  window['f'] = impl_0;"
            + "}"
            + "function $init() {"
            + "  impl_0 = {};"
            + "}";
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
        "function bar(x) { return x; }\n"
            + "function foo(x) { print(x / bar(0));\n"
            + "                 print(x / bar(-0)); }\n"
            + "foo(3);",
        "print(3/0);print(3/-0);");
  }

  @Test
  public void testSingletonGetter1() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setCodingConvention(new ClosureCodingConvention());
    test(
        options,
        "/** @const */\n"
            + "var goog = goog || {};\n"
            + "goog.addSingletonGetter = function(ctor) {\n"
            + "  ctor.getInstance = function() {\n"
            + "    return ctor.instance_ || (ctor.instance_ = new ctor());\n"
            + "  };\n"
            + "};"
            + "function Foo() {}\n"
            + "goog.addSingletonGetter(Foo);"
            + "Foo.prototype.bar = 1;"
            + "function Bar() {}\n"
            + "goog.addSingletonGetter(Bar);"
            + "Bar.prototype.bar = 1;",
        "");
  }

  @Test
  public void testInlineProperties() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    level.setTypeBasedOptimizationOptions(options);

    String code =
        ""
            + "var ns = {};\n"
            + "/** @constructor */\n"
            + "ns.C = function () {this.someProperty = 1}\n"
            + "alert(new ns.C().someProperty + new ns.C().someProperty);\n";
    assertThat(options.shouldInlineProperties()).isTrue();
    assertThat(options.shouldCollapseProperties()).isTrue();
    // CollapseProperties used to prevent inlining this property.
    test(options, code, "alert(2);");
  }

  @Test
  public void testGoogDefineClass1() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    options.setCheckTypes(true);
    level.setOptionsForCompilationLevel(options);
    level.setTypeBasedOptimizationOptions(options);

    String code =
        lines(
            "var goog = {};",
            "var ns = {};",
            "ns.C = goog.defineClass(null, {",
            "  /** @constructor */",
            "  constructor: function () {this.someProperty = 1}",
            "});",
            "alert(new ns.C().someProperty + new ns.C().someProperty);");
    assertThat(options.shouldInlineProperties()).isTrue();
    assertThat(options.shouldCollapseProperties()).isTrue();
    // CollapseProperties used to prevent inlining this property.
    test(options, code, "alert(2);");
  }

  @Test
  public void testGoogDefineClass2() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    options.setCheckTypes(true);
    level.setOptionsForCompilationLevel(options);
    level.setTypeBasedOptimizationOptions(options);

    String code =
        lines(
            "/** @const */",
            "var goog = {};",
            "var C = goog.defineClass(null, {",
            "  /** @constructor */",
            "  constructor: function () {this.someProperty = 1}",
            "});",
            "alert(new C().someProperty + new C().someProperty);");
    assertThat(options.shouldInlineProperties()).isTrue();
    assertThat(options.shouldCollapseProperties()).isTrue();
    // CollapseProperties used to prevent inlining this property.
    test(options, code, "alert(2);");
  }

  @Test
  public void testGoogDefineClass3() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    level.setTypeBasedOptimizationOptions(options);
    WarningLevel warnings = WarningLevel.VERBOSE;
    warnings.setOptionsForWarningLevel(options);

    String code =
        lines(
            "/** @const */",
            "var goog = {};",
            "var C = goog.defineClass(null, {",
            "  /** @constructor */",
            "  constructor: function () {",
            "    /** @type {number} */",
            "    this.someProperty = 1},",
            "  /** @param {string} a */",
            "  someMethod: function (a) {}",
            "});",
            "var x = new C();",
            "x.someMethod(x.someProperty);");
    assertThat(options.shouldInlineProperties()).isTrue();
    assertThat(options.shouldCollapseProperties()).isTrue();
    // CollapseProperties used to prevent inlining this property.
    test(options, code, TypeValidator.TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testGoogDefineClass4() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.VERBOSE;
    warnings.setOptionsForWarningLevel(options);
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.WARNING);

    String code =
        lines(
            "/** @const */",
            "var goog = {};",
            "var C = goog.defineClass(null, {",
            "  /** @param {string} a */",
            "  constructor: function (a) {this.someProperty = 1}",
            "});");
    test(options, code, "");
  }

  // Due to JsFileParse not being supported in the JS version, the dependency parsing delegates to
  // the {@link CompilerInput$DepsFinder} class which is incompatible with the
  // DefaultCodingConvention due to it throwing on methods such as extractIsModuleFile which is
  // needed in {@link CompilerInput$DepsFinder#visitSubtree}. Disable this test in the JsVersion.
  // TODO(tdeegan): DepsFinder should error out early if run with DefaultCodingConvention.
  @GwtIncompatible
  @Test
  public void testES6UnusedClassesAreRemovedDefaultCodingConvention() {
    testES6UnusedClassesAreRemoved(CodingConventions.getDefault());
  }

  @Test
  public void testNoDuplicateClassNameForLocalBaseClasses() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        lines(
            "", //
            // Previously Es6ToEs3ClassSideInheritance failed to take scope into account,
            // so it produced DUPLICATE_CLASS errors for code like this.
            "function f1() {",
            "  class Base {}",
            "  class Sub extends Base {}",
            "}",
            "function f2() {",
            "  class Base {}",
            "  class Sub extends Base {}",
            "}",
            "alert(1);",
            ""),
        "alert(1)");
  }

  // Tests that unused classes are removed, even if they are passed to $jscomp.inherits.
  private void testES6UnusedClassesAreRemoved(CodingConvention convention) {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setCodingConvention(convention);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        lines(
            "", //
            "class Base {}",
            "class Sub extends Base {}",
            "alert(1);",
            ""),
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
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    Compiler compiler = compile(options, js);
    assertThat(compiler.getErrors()).isEmpty();
    String result = compiler.toSource(compiler.getJsRoot());
    assertThat(result).isNotEmpty();
    assertThat(result).doesNotContain("No one ever calls me");
  }

  @Test
  public void testES6StaticsAreRemoved1() {
    testES6StaticsAreRemoved(
        LINE_JOINER.join(
            "class Base {",
            "  static called() { alert('I am called'); }",
            "  static notCalled() { alert('No one ever calls me'); }",
            "}",
            "class Sub extends Base {",
            "  static called() { super.called(); alert('I am called too'); }",
            "  static notCalled() { alert('No one ever calls me'); }",
            "}",
            "Sub.called();"));
  }

  @Test
  public void testES6StaticsAreRemoved2() {
    testES6StaticsAreRemoved(
        LINE_JOINER.join(
            "class Base {",
            "  static calledInSubclassOnly() { alert('No one ever calls me'); }",
            "}",
            "class Sub extends Base {",
            "  static calledInSubclassOnly() { alert('I am called'); }",
            "}",
            "Sub.calledInSubclassOnly();"));
  }

  @Test
  public void testExports() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    options.setRemoveUnusedPrototypePropertiesInExterns(true);

    String code =
        ""
        + "/** @constructor */ var X = function() {"
        + "/** @export */ this.abc = 1;};\n"
        + "/** @constructor */ var Y = function() {"
        + "/** @export */ this.abc = 1;};\n"
        + "alert(new X().abc + new Y().abc);";

    // no export enabled, property name not preserved
    test(options, code,
        "alert((new function(){this.a = 1}).a + " +
            "(new function(){this.a = 1}).a);");

    options.setGenerateExports(true);

    // exports enabled, but not local exports
    test(options,
        "/** @constructor */ var X = function() {" +
        "/** @export */ this.abc = 1;};\n",
        FindExportableNodes.NON_GLOBAL_ERROR);

    options.exportLocalPropertyDefinitions = true;
    options.setRemoveUnusedPrototypePropertiesInExterns(false);

    // property name preserved due to export
    test(options, code,
        "alert((new function(){this.abc = 1}).abc + (new function(){this.abc = 1}).abc);");

    // unreferenced property not removed due to export.
    test(
        options,
        LINE_JOINER.join(
            "/** @constructor */ var X = function() {",
            "  /** @export */ this.abc = 1;",
            "};",
            "",
            "/** @constructor */ var Y = function() {",
            "  /** @export */ this.abc = 1;",
            "};",
            "",
            "alert(new X() + new Y());"),
        "alert((new function(){this.abc = 1}) + (new function(){this.abc = 1}));");

    // disambiguate and ambiguate properties respect the exports.
    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setAmbiguateProperties(true);
    options.propertyInvalidationErrors = ImmutableMap.of("abc", CheckLevel.ERROR);

    test(
        options,
        code,
        "alert((new function(){this.abc = 1}).abc + (new function(){this.abc = 1}).abc);");

    // unreferenced property not removed due to export.
    test(
        options,
        ""
            + "/** @constructor */ var X = function() {"
            + "/** @export */ this.abc = 1;};\n"
            + "/** @constructor */ var Y = function() {"
            + "/** @export */ this.abc = 1;};\n"
            + "alert(new X() + new Y());",
        "alert((new function(){this.abc = 1}) + (new function(){this.abc = 1}));");
  }

  // GitHub issue #250: https://github.com/google/closure-compiler/issues/250
  @Test
  public void testInlineStringConcat() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        LINE_JOINER.join(
            "function f() {",
            "  var x = '';",
            "  x += '1';",
            "  x += '2';",
            "  x += '3';",
            "  x += '4';",
            "  x += '5';",
            "  x += '6';",
            "  x += '7';",
            "  return x;",
            "}",
            "window.f = f;"),
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
        LINE_JOINER.join(
            "var exit;",
            "switch ('a') {",
            "  case 'a':",
            "    break;",
            "  default:",
            "    exit = 21;",
            "    break;",
            "}",
            "switch(exit) {",
            "  case 21: throw 'x';",
            "  default : console.log('good');",
            "}"),
        "console.a('good');");
  }

  // GitHub issue #2122: https://github.com/google/closure-compiler/issues/2122
  @Test
  public void testNoRemoveSuperCallWithWrongArgumentCount() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        LINE_JOINER.join(
            "var goog = {}",
            "goog.inherits = function(childCtor, parentCtor) {",
            "  childCtor.superClass_ = parentCtor.prototype;",
            "};",
            "/** @constructor */",
            "var Foo = function() {}",
            "/**",
            " * @param {number=} width",
            " * @param {number=} height",
            " */",
            "Foo.prototype.resize = function(width, height) {",
            "  window.size = width * height;",
            "}",
            "/**",
            " * @constructor",
            " * @extends {Foo}",
            " */",
            "var Bar = function() {}",
            "goog.inherits(Bar, Foo);",
            "/** @override */",
            "Bar.prototype.resize = function(width, height) {",
            "  Bar.base(this, 'resize', width);",
            "};",
            "(new Bar).resize(100, 200);"),
        LINE_JOINER.join(
            "function a(){}a.prototype.a=function(b,e){window.c=b*e};",
            "function d(){}d.b=a.prototype;d.prototype.a=function(b){d.b.a.call(this,b)};",
            "(new d).a(100, 200);"));
  }

  // GitHub issue #2203: https://github.com/google/closure-compiler/issues/2203
  @Test
  public void testPureFunctionIdentifierWorksWithMultipleNames() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        LINE_JOINER.join(
            "var SetCustomData1 = function SetCustomData2(element, dataName, dataValue) {",
            "    var x = element['_customData'];",
            "    x[dataName] = dataValue;",
            "}",
            "SetCustomData1(window, 'foo', 'bar');"),
        "window._customData.foo='bar';");
  }

  @Test
  public void testSpreadArgumentsPassedToSuperDoesNotPreventRemoval() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    // Create a noninjecting compiler avoid comparing all the polyfill code.
    useNoninjectingCompiler = true;

    // include externs definitions for the stuff that would have been injected
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(SourceFile.fromCode("extraExterns", "var $jscomp = {};"));
    externs = externsList.build();

    test(
        options,
        lines(
            "", // preserve newlines
            "class A {",
            "  constructor(a, b) {",
            "    this.a = a;",
            "    this.b = b;",
            "  }",
            "}",
            "class B extends A {",
            "  constructor() {",
            "    super(...arguments);",
            "  }",
            "}",
            "var b = new B();"),
        "");
  }

  @Test
  public void testAngularPropertyNameRestrictions() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setAngularPass(true);

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 250; i++) {
      sb.append("window.foo").append(i).append("=true;\n");
    }

    Compiler compiler = compile(options, sb.toString());
    assertWithMessage(
            "Expected no warnings or errors\n"
                + "Errors: \n"
                + Joiner.on("\n").join(compiler.getErrors())
                + "\n"
                + "Warnings: \n"
                + Joiner.on("\n").join(compiler.getWarnings()))
        .that(compiler.getErrors().size() + compiler.getWarnings().size())
        .isEqualTo(0);

    Node root = compiler.getRoot().getLastChild();
    assertThat(root).isNotNull();
    Node script = root.getFirstChild();
    assertThat(script).isNotNull();
    ImmutableSet<Character> restrictedChars =
        ImmutableSet.copyOf(Chars.asList(CompilerOptions.ANGULAR_PROPERTY_RESERVED_FIRST_CHARS));
    for (Node expr : script.children()) {
      NodeSubject.assertNode(expr).hasType(Token.EXPR_RESULT);
      NodeSubject.assertNode(expr.getFirstChild()).hasType(Token.ASSIGN);
      NodeSubject.assertNode(expr.getFirstFirstChild()).hasType(Token.GETPROP);
      Node getProp = expr.getFirstFirstChild();
      NodeSubject.assertNode(getProp.getSecondChild()).hasType(Token.STRING);
      String propName = getProp.getSecondChild().getString();
      assertThat(restrictedChars).doesNotContain(propName.charAt(0));
    }
  }

  @Test
  public void testCheckVarsOnInAdvancedMode() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(options, "var x = foobar;", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testInlineRestParam() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        LINE_JOINER.join(
            "function foo() {",
            "  var f = (...args) => args[0];",
            "  return f(8);",
            "}",
            "alert(foo());"),
        "alert(function(c){for(var b=[],a=0;a<arguments.length;++a)"
            + "b[a-0]=arguments[a];return b[0]}(8))");
  }

  @Test
  public void testInlineRestParamNonTranspiling() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        LINE_JOINER.join(
            "function foo() {",
            "  var f = (...args) => args[0];",
            "  return f(8);",
            "}",
            "alert(foo());"),
        "alert(8)");
  }

  @Test
  public void testDefaultParameters() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        options,
        LINE_JOINER.join(
            "function foo(a, b = {foo: 5}) {",
            "  return a + b.foo;",
            "}",
            "alert(foo(3, {foo: 9}));"),
        "var a={a:9}; a=void 0===a?{a:5}:a;alert(3+a.a)");
  }

  // TODO(b/78345133): Re-enable if/when InlineFunctions supports inlining default parameters
  public void disabled_testDefaultParametersNonTranspiling() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);

    test(
        options,
        LINE_JOINER.join(
            "function foo(a, b = {foo: 5}) {",
            "  return a + b.foo;",
            "}",
            "alert(foo(3, {foo: 9}));"),
        "alert(12);");
  }

  @Test
  public void testRestObjectPatternParameters() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    externs = DEFAULT_EXTERNS;

    test(
        options,
        LINE_JOINER.join(
            "function countArgs(x, ...{length}) {",
            "  return length;",
            "}",
            "alert(countArgs(1, 1, 1, 1, 1));"),
        LINE_JOINER.join(
            "alert(function (c,d) {",
            "  for(var b=[], a=1; a < arguments.length; ++a)",
            "    b[a-1] = arguments[a];",
            "    return b.length ",
            "}(1,1,1,1,1))"));
  }

  // TODO(tbreisacher): Re-enable if/when InlineFunctions supports rest parameters that are
  // object patterns.
  public void disabled_testRestObjectPatternParametersNonTranspiling() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    externs = DEFAULT_EXTERNS;

    test(
        options,
        LINE_JOINER.join(
            "function countArgs(x, ...{length}) {",
            "  return length;",
            "}",
            "alert(countArgs(1, 1, 1, 1, 1));"),
        "alert(4);");
  }

  @Test
  public void testQuotedDestructuringNotRenamed() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);

    test(options, "const {'x': x} = window; alert(x);", "const {x:a} = window; alert(a);");
    test(
        options,
        "const {x: {'log': y}} = window; alert(y);",
        "const {a: {log: a}} = window; alert(a);");
  }

  /** Creates a CompilerOptions object with google coding conventions. */
  @Override
  protected CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setRenamePrefixNamespaceAssumeCrossChunkNames(true);
    options.setAssumeGettersArePure(false);
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
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2018);

    // TODO(b/116532470): the compiler should compile this down to nothing.
    test(
        options,
        lines(
            "class C { f() {} }",
            "(new C()).f();",
            "const obj = {f: 3};",
            "const {f, ...rest} = obj;"),
        lines(
            "function a() {}",
            "a.prototype.a = function() {};",
            "(new a).a();",
            "delete Object.assign({}, {a: 3}).a"));
  }

  @Test
  public void testObjectSpreadAndRest_optimizeAndTypecheck() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2018);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    test(
        options,
        new String[] {
          lines(
              "/**",
              " * @param {{a: number, b: string}} x",
              " * @return {*}",
              " */",
              "function fun({a, b, ...c}) {", //
              "  const /** !{a: string, b: number} */ x = {a, b, ...c};",
              "  const y = {...x};",
              "  y['a'] = 5;",
              "",
              "  let {...z} = y;",
              "  return z;",
              "}",
              "",
              "alert(fun({a: 1, b: 'hello', c: null}));")
        },
        new String[] {
          lines(
              "alert(function({b:a, c:b,...c}) {",
              // TODO(b/123102446): We'd really like to collapse these chained assignments.
              "  a = {b:a, c:b, ...c, a:5};",
              "  ({...a} = a);",
              "  return a;",
              "}({b:1, c:'hello', d:null}));")
        },
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testObjectSpreadAndRest_inlineAndCollapseProperties() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2018);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);
    options.setPrettyPrint(true);
    options.setGeneratePseudoNames(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    // This is just a melange of various global namespace operations. The exact sequence of values
    // aren't important so much as trying out lots of combinations.
    test(
        options,
        lines(
            "const a = {",
            "  aa: 2,",
            "  ab: 'hello',",
            "};",
            "",
            "a.ac = {",
            "  aca: true,",
            "  ...a,",
            "  acb: false,",
            "};",
            "",
            "const {ab, ac,...c} = a;",
            "",
            "const d = ac;",
            "",
            "({aa: d.acc} = a);",
            "",
            "alert(d.acc);"),
        lines(
            "const a = {",
            "  $aa$: 2,",
            "  $ab$: 'hello',",
            "};",
            "",
            "a.$ac$ = {",
            "  $aca$: !0,",
            "  ...a,",
            "  $acb$: !1,",
            "};",
            "",
            "const {$ab$: $ab$$, $ac$: $ac$$, ...$c$$} = a;",
            "",
            "({$aa$: $ac$$.$acc$} = a);",
            "",
            "alert($ac$$.$acc$);"));
  }

  @Test
  public void testOptionalCatchBinding_optimizeAndTypecheck() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2019);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    testSame(
        options,
        new String[] {
          lines(
              "try {", //
              "  alert('try');",
              "} catch {",
              "  alert('caught');",
              "}")
        });

    test(
        options,
        new String[] {
          lines(
              "function doNothing() {}",
              "try {",
              " doNothing();",
              "} catch {",
              "  alert('caught');",
              "}")
        },
        new String[] {});

    test(
        options,
        new String[] {
          lines(
              "function doNothing() {}",
              "try {",
              " alert('try');",
              "} catch {",
              "  doNothing();",
              "}")
        },
        new String[] {
          lines(
              "try {", //
              " alert('try');",
              "} catch {",
              "}")
        });
  }

  @Test
  public void testImportMeta() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);

    test(options, "import.meta", Es6ToEs3Util.CANNOT_CONVERT);
  }

  @Test
  public void nullishCoalesceSimple() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    test(options, "var x = 0; var y = {}; alert(x ?? y)", "alert(0)");
  }

  @Test
  public void nullishCoalesceChain() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    test(options, "var x, y; alert(x ?? y ?? 'default string')", "alert('default string')");
  }

  @Test
  public void nullishCoalesceWithAnd() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    test(options, "var x, y, z; alert(x ?? (y && z))", "alert(void 0)");
  }

  @Test
  public void nullishCoalesceWithAssign() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    test(options, "var x, y; var z = 1; x ?? (y = z); alert(y)", "alert(1)");
  }

  @Test
  public void nullishCoalesceTranspiledOutput() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    externs =
        ImmutableList.of(new TestExternsBuilder().addExtra("var x, y").buildExternsFile("externs"));

    test(options, "x ?? y", "let a; null != (a = x) ? a : y");
  }

  @Test
  public void nullishCoalesceChainTranspiledOutput() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addExtra("var x, y, z").buildExternsFile("externs"));

    test(options, "x ?? y ?? z", "let a, b; null != (b = null != (a = x) ? a : y) ? b : z");
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
        new String[] {
          CLOSURE_DEFS,
          lines(
              "goog.module('mod');",
              "function alwaysNull() { return null; }",
              // transpiled form of `const y = alwaysNull() ? 42;`
              "var _a;",
              "const y = (_a = alwaysNull()) !== null && _a !== void 0 ? _a : 42;",
              "alert(y);")
        },
        new String[] {"", "alert(42);"});
  }

  @Test
  public void nullishCoalesceFromTranspiledTs38_inFunction() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);

    // Ensure the compiler can optimize TS 3.8's transpilation of nullish coalescing.
    test(
        options,
        new String[] {
          CLOSURE_DEFS,
          lines(
              "goog.module('mod');",
              "function alwaysNull() { return null; }",
              "function getDefaultValue(maybeNull) {",
              // transpiled form of `return maybeNull ?? 42;`
              "  var _a;",
              "  return (_a = maybeNull) !== null && _a !== void 0 ? _a : 42;",
              "}",
              "alert(getDefaultValue(alwaysNull()));")
        },
        new String[] {"", "alert(42);"});
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
}
