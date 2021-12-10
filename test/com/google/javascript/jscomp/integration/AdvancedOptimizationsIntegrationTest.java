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
import static com.google.javascript.jscomp.base.JSCompStrings.lines;

import com.google.common.annotations.GwtIncompatible;
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
import com.google.javascript.jscomp.testing.NoninjectingCompiler;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.NodeSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for the compiler running with {@link CompilationLevel#ADVANCED_OPTIMIZATIONS}.
 */
@RunWith(JUnit4.class)
public final class AdvancedOptimizationsIntegrationTest extends IntegrationTestCase {

  @Test
  public void testBug123583793() {
    // Avoid including the transpilation library
    useNoninjectingCompiler = true;
    CompilerOptions options = createCompilerOptions();
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
        lines(
            "function foo() {",
            "  const {a, ...rest} = {a: 1, b: 2, c: 3};",
            "  return {a, rest};",
            "};",
            "console.log(foo());",
            ""),
        lines(
            "var a = console,",
            "    b = a.log,",
            "    c = {a:1, b:2, c:3},",
            "    d = Object.assign({}, c),",
            "    e = c.a,",
            "    f = (delete d.a, d);",
            "b.call(a, {a:e, d:f});",
            ""));
    assertThat(((NoninjectingCompiler) lastCompiler).getInjected()).contains("es6/object/assign");
  }

  @Test
  public void testBug173319540() {
    // Avoid including the transpilation library
    useNoninjectingCompiler = true;
    CompilerOptions options = createCompilerOptions();
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
        lines(
            "", //
            "async function foo() {",
            "  for await (const [key, value] of asyncIterator) {",
            "    console.log(key,value);",
            "  }",
            "}",
            "foo();"),
        lines(
            "", //
            "(async function() {",
            "  for (const $$jscomp$forAwait$tempIterator0$$ =",
            "           $jscomp.makeAsyncIterator(asyncIterator);;) {",
            "    const $$jscomp$forAwait$tempResult0$$ =",
            "        await $$jscomp$forAwait$tempIterator0$$.next();",
            "    if ($$jscomp$forAwait$tempResult0$$.done) {",
            "      break;",
            "    }",
            "    const [$key$$, $value$jscomp$2$$] = $$jscomp$forAwait$tempResult0$$.value;",
            "    console.log($key$$, $value$jscomp$2$$);",
            "  }",
            "})();",
            ""));
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
          lines(
              "goog.module('base');", //
              "class Base {", //
              "  constructor({paramProblemFunc = problemFunc} = {}) {",
              "    /** @public */",
              "    this.prop = paramProblemFunc();",
              "  }",
              "}",
              "",
              "const problemFunc = () => 1;",
              "Base.problemFunc = problemFunc;",
              "",
              "exports = {Base};",
              ""),
          lines(
              "goog.module('child');", //
              "",
              "const {Base} = goog.require('base');",
              "",
              "class Child extends Base {",
              "  constructor({paramProblemFunc = Base.problemFunc} = {}) {",
              "    super({paramProblemFunc});",
              "  }",
              "}",
              "",
              "exports = {Child};",
              ""),
          lines(
              "goog.module('grandchild');",
              "",
              "const {Child} = goog.require('child');",
              "",
              "class GrandChild extends Child {",
              "  constructor() {",
              "    super({paramProblemFunc: () => GrandChild.problemFunc() + 1});",
              "  }",
              "}",
              "",
              "console.log(new GrandChild().prop);",
              ""),
        },
        new String[] {
          "",
          "",
          lines(
              "const $module$contents$base_problemFunc$$ = () => 1;",
              "var $module$exports$base$Base$$ = class {",
              "  constructor(",
              "      {",
              "        $paramProblemFunc$:$paramProblemFunc$$ =",
              "            $module$contents$base_problemFunc$$",
              "      } = {}) {",
              "    this.$a$ = $paramProblemFunc$$();",
              "  }",
              "}, $module$exports$child$Child$$ = class extends $module$exports$base$Base$$ {",
              "  constructor(",
              "      {",
              "        $paramProblemFunc$:$paramProblemFunc$jscomp$1$$ =",
              "            $module$contents$base_problemFunc$$",
              "      } = {}) {",
              "    super({$paramProblemFunc$:$paramProblemFunc$jscomp$1$$});",
              "  }",
              "};",
              "class $module$contents$grandchild_GrandChild$$",
              "    extends $module$exports$child$Child$$ {",
              "  constructor() {",
              // TODO(b/196083761): Fix this!
              // NOTE the call to `null()` here!
              "    super({$paramProblemFunc$:() => null() + 1});",
              "  }",
              "}",
              "console.log((new $module$contents$grandchild_GrandChild$$).$a$);",
              "")
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
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);
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
        lines(
            "/**",
            " * @param {bigint} value",
            " * @return {bigint}",
            " */",
            "function bigintTimes3(value){",
            "  return value * 3n;",
            "}",
            "console.log(bigintTimes3(5n));"),
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
            // NOTE: this alert is here to prevent the entire constructor from being removed. Any
            // statement after the super call will do.
            "    alert('hi');",
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
            "  constructor(        ) {",
            "    this.elements =       [];",
            "    for (const element of this.elements) {",
            "      element.someProp = 1;",
            "    }",
            "  }",
            "}",
            "new C(  );"));
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
  public void testAdvancedModeRemovesLocalTypeAndAlias() {
    // An extra pass of RemoveUnusedCode used to be conditional.  This specific code pattern
    // wasn't removed without it.  Verify it is removed.
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        lines(
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
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        new String[] {lines("var x = 1;"), lines("import * as y from './i0.js';", "const z = y.x")},
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
  public void testArrayValuesIsPolyfilledForEs2015Out() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setRewritePolyfills(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);

    useNoninjectingCompiler = true;
    compile(options, new String[] {"for (const x of [1, 2, 3].values()) { alert(x); }"});

    assertThat(lastCompiler.getResult().errors).isEmpty();
    assertThat(lastCompiler.getResult().warnings).isEmpty();
    assertThat(((NoninjectingCompiler) lastCompiler).getInjected())
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
        lines(
            "for (var x of []);", // Force injection of es6_runtime.js
            "var /** number */ y = window;"),
        DiagnosticGroups.CHECK_TYPES);
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
        lines(
            "",
            "/** @export */",
            "class C {",
            "  /** @export @return {string} */ static get exportedName() {}",
            "};",
            "alert(C.exportedName);"),
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

    useNoninjectingCompiler = true;
    test(
        options,
        lines(
            // hack replacement for compiler injected code
            "var $jscomp = { global: window };",
            "",
            "class C {",
            "  /** @export @param {number} x */ static set exportedName(x) {}",
            "};",
            "C.exportedName = 0;"),
        lines(
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
        lines(
            "function f(x) {",
            "  var a = x + 1;",
            "  var b = x + 1;",
            "  window.c = x > 5 ? a : b;",
            "}",
            "f(g);"),
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
        lines(
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
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addAlert()
                .buildExternsFile("externs.js")); // add Closure base.js as srcs
    String source =
        lines(
            TestExternsBuilder.getClosureExternsAsSource(),
            "/** @constructor */",
            "var Foo = function() {};",
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
            "goog.provide('i.am.on.a.Horse');",
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
    externs = ImmutableList.of();
    test(
        options,
        TestExternsBuilder.getClosureExternsAsSource()
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
    test(options, code, DiagnosticGroups.CHECK_TYPES);
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
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        lines(
            "", //
            // Previously ConcretizeStaticInheritanceForInlining failed to take scope into account,
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
        lines(
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
        lines(
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

    String code =
        ""
            + "/** @constructor */ var X = function() {"
            + "/** @export */ this.abc = 1;};\n"
            + "/** @constructor */ var Y = function() {"
            + "/** @export */ this.abc = 1;};\n"
            + "alert(new X().abc + new Y().abc);";

    options.setExportLocalPropertyDefinitions(false);

    // exports enabled, but not local exports
    compile(
        options, "/** @constructor */ var X = function() {" + "/** @export */ this.abc = 1;};\n");
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
        lines(
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
        lines(
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
        lines(
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
        lines(
            TestExternsBuilder.getClosureExternsAsSource(),
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
        lines(
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
        lines(
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
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    // Create a noninjecting compiler avoid comparing all the polyfill code.
    useNoninjectingCompiler = true;

    // include externs definitions for the stuff that would have been injected
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.add(
        SourceFile.fromCode(
            "extraExterns",
            new TestExternsBuilder()
                .addExtra("/** @type {!Global} */ var globalThis;")
                .addJSCompLibraries()
                .build()));
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

    useNoninjectingCompiler = true;
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
        lines(
            "function foo() {",
            "  var f = (...args) => args[0];",
            "  return f(8);",
            "}",
            "alert(foo());"),
        lines(
            "alert(function() {",
            "  return $jscomp.getRestArguments.apply(0, arguments)[0];",
            "}(8))"));
  }

  @Test
  public void testInlineRestParamNonTranspiling() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        lines(
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
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        options,
        lines(
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
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);

    test(
        options,
        lines(
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
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    useNoninjectingCompiler = true;
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
        lines(
            "function countArgs(x, ...{length}) {",
            "  return length;",
            "}",
            "alert(countArgs(1, 1, 1, 1, 1));"),
        lines(
            "alert(function (a) {",
            "  return $jscomp.getRestArguments.apply(1, arguments).length;",
            "}(1,1,1,1,1))"));
  }

  // TODO(tbreisacher): Re-enable if/when InlineFunctions supports rest parameters that are
  // object patterns.
  public void disabled_testRestObjectPatternParametersNonTranspiling() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    externs = DEFAULT_EXTERNS;

    test(
        options,
        lines(
            "function countArgs(x, ...{length}) {",
            "  return length;",
            "}",
            "alert(countArgs(1, 1, 1, 1, 1));"),
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
        lines(
            "const X = { a: 1, b: 2 };", //
            "const { a, b } = X;",
            "console.log(a, b);",
            ""),
        "console.log(1,2);");

    test(
        options,
        lines(
            "const X = { a: 1, b: 2 };", //
            "let { a, b } = X;",
            "const Y = { a: 4, b: 5 };",
            "({ a, b } = Y);",
            "console.log(a, b);",
            ""),
        lines(
            "", //
            "let {a, b} = {a:1, b:2};",
            "({a, b} = {a:4, b:5});",
            "console.log(a, b);"));

    // Demonstrates https://github.com/google/closure-compiler/issues/3671
    test(
        options,
        lines(
            "const X = { a: 1, b: 2 };", //
            "const Y = { a: 1, b: 2 };",
            // Conditional destructuring assignment makes it unsafe to collapse
            // the properties on X and Y.
            "const { a, b } = Math.random() ? X : Y;",
            "console.log(a, b);",
            ""),
        lines(
            "const a = { a: 1, b: 2},", //
            "      b = { a: 1, b: 2},",
            "      {a:c, b:d} = Math.random() ? a : b;",
            "console.log(c, d);",
            ""));
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
    useNoninjectingCompiler = true;
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

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

    test(options, "import.meta", DiagnosticGroups.CANNOT_TRANSPILE_FEATURE);
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
        lines(
            "goog.module('mod');",
            "function alwaysNull() { return null; }",
            // transpiled form of `const y = alwaysNull() ? 42;`
            "var _a;",
            "const y = (_a = alwaysNull()) !== null && _a !== void 0 ? _a : 42;",
            "alert(y);"),
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
        lines(
            "goog.module('mod');",
            "function alwaysNull() { return null; }",
            "function getDefaultValue(maybeNull) {",
            // transpiled form of `return maybeNull ?? 42;`
            "  var _a;",
            "  return (_a = maybeNull) !== null && _a !== void 0 ? _a : 42;",
            "}",
            "alert(getDefaultValue(alwaysNull()));"),
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
        lines(
            "function installBaa({ obj }) {",
            " obj.baa = 'foo';",
            "}",
            "",
            "const obj = {};",
            "installBaa({ obj });",
            "alert(obj.baa);"),
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
        lines(
            "function installBaa({ obj }) {",
            " obj.baa = 'foo';",
            "}",
            "",
            "const obj = {};",
            "installBaa({ obj });",
            "alert(obj.baa);"),
        lines(
            "const a = {};", //
            "(function({b}){",
            "  b.a = 'foo';",
            "})({b: a});",
            "alert(a.a);"));
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
        lines(
            "class Foo {", //
            "  constructor(foo) {",
            "    this.foo = foo;",
            "  }",
            "}",
            "class Bar {",
            "  constructor(bar) {",
            "    this.bar = bar;",
            "  }",
            "}",
            "window['Foo'] = Foo;",
            "window['Bar'] = Bar;",
            "/**",
            " * @param {!Foo} foo",
            " * @param {!Bar} bar",
            " */",
            "window['foobar'] = function(foo, bar) {",
            "  return foo.foo + bar.bar;",
            "}"),
        lines(
            "class b {", //
            "  constructor(a) {",
            "    this.a = a;",
            "  }",
            "}",
            "class c {",
            "  constructor(a) {",
            "    this.a = a;",
            "  }",
            "}",
            "window.Foo = b;",
            "window.Bar = c",
            "window.foobar = function(a, d) {",
            // Property ambiguation renames both ".foo" and ".bar" to ".a".
            "  return a.a + d.a;",
            "}"));
  }

  @Test
  public void testDisambiguationWithInvalidCastDownAndCommonInterface() {
    // test for a legacy-compat behavior of the disambiguator
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDisambiguateProperties(true);

    test(
        options,
        lines(
            "/** @interface */",
            "class Speaker {",
            "  speak() {}",
            "}",
            "/** @implements {Speaker} */",
            "class SpeakerImpl {",
            "  speak() { alert('Speaker'); }",
            "}",
            "/** @interface @extends {Speaker} */",
            "class SpeakerChild {}",
            "/** @param {!Speaker} speaker */",
            "function speak(speaker) {",
            // cast is invalid: a SpeakerImpl is passed. Note that this function is needed because
            // the compiler is smart enough to detect invalid casts in
            // /** @type {!SpeakerImpl} (/** @type {!Speaker} */ (new SpeakerImpl()));
            "  /** @type {!SpeakerChild} */ (speaker).speak();",
            "}",
            "speak(new SpeakerImpl());",
            "class Other {",
            "  speak() { alert('other'); }",
            "}",
            "new Other().speak();"),
        lines(
            // Compiler calls SpeakerImpl.prototype.speak even though it's called off SpeakerChild.
            "alert('Speaker'); alert('other');"));
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

    // with just variable renaming on, the code is unchanged becasue of the 'foobar' externs ref.
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(options, "foobar = {x: 1}; alert(foobar.x);", "foobar = {x:1}; alert(foobar.x);");

    // with inlining + other advanced optimizations, foobar.x is renamed
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(options, "foobar = {x: 1}; alert(foobar.x);", "foobar = {a: 1}; alert(foobar.a);");
  }
}
