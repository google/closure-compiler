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

package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.javascript.jscomp.AbstractCommandLineRunner.FlagEntry;
import com.google.javascript.jscomp.AbstractCommandLineRunner.FlagUsageException;
import com.google.javascript.jscomp.AbstractCommandLineRunner.JsSourceType;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceMap.LocationMapping;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tests for {@link CommandLineRunner}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class CommandLineRunnerTest extends TestCase {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  private Compiler lastCompiler = null;
  private CommandLineRunner lastCommandLineRunner = null;
  private List<Integer> exitCodes = null;
  private ByteArrayOutputStream outReader = null;
  private ByteArrayOutputStream errReader = null;
  private Map<Integer, String> filenames;

  // If set, this will be appended to the end of the args list.
  // For testing args parsing.
  private String lastArg = null;

  // If set to true, uses comparison by string instead of by AST.
  private boolean useStringComparison = false;

  private ModulePattern useModules = ModulePattern.NONE;

  private enum ModulePattern {
    NONE,
    CHAIN,
    STAR
  }

  private List<String> args = new ArrayList<>();

  /** Externs for the test */
  private static final List<SourceFile> DEFAULT_EXTERNS = ImmutableList.of(
    SourceFile.fromCode("externs", Joiner.on('\n').join(
        "var arguments;",
        "/**",
        " * @constructor",
        " * @param {...*} var_args",
        " * @nosideeffects",
        " * @throws {Error}",
        " */",
        "function Function(var_args) {}",
        "/**",
        " * @param {...*} var_args",
        " * @return {*}",
        " */",
        "Function.prototype.call = function(var_args) {};",
        "/**",
        " * @constructor",
        " * @param {...*} var_args",
        " * @return {!Array}",
        " */",
        "function Array(var_args) {}",
        "/**",
        " * @param {*=} opt_begin",
        " * @param {*=} opt_end",
        " * @return {!Array}",
        " * @this {Object}",
        " */",
        "Array.prototype.slice = function(opt_begin, opt_end) {};",
        "/** @constructor */ function Window() {}",
        "/** @type {string} */ Window.prototype.name;",
        "/** @type {Window} */ var window;",
        "/** @constructor */ function Element() {}",
        "Element.prototype.offsetWidth;",
        "/** @nosideeffects */ function noSideEffects() {}",
        "/** @param {...*} x */ function alert(x) {}",
        "function Symbol() {}")));

  private List<SourceFile> externs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    externs = DEFAULT_EXTERNS;
    filenames = new HashMap<>();
    lastCompiler = null;
    lastArg = null;
    outReader = new ByteArrayOutputStream();
    errReader = new ByteArrayOutputStream();
    useStringComparison = false;
    useModules = ModulePattern.NONE;
    args.clear();
    exitCodes = new ArrayList<>();
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testUnknownAnnotation() {
    args.add("--warning_level=VERBOSE");
    test("/** @unknownTag */ function f() {}",
         RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    args.add("--extra_annotation_name=unknownTag");
    testSame("/** @unknownTag */ function f() {}");
  }

  // See b/26884264
  public void testForOfTypecheck() throws IOException {
    args.add("--jscomp_error=checkTypes");
    args.add("--language_in=ES6_STRICT");
    args.add("--language_out=ES3");
    externs = AbstractCommandLineRunner.getBuiltinExterns(CompilerOptions.Environment.BROWSER);
    test(
        Joiner.on('\n').join(
            "class Cat {meow() {}}",
            "class Dog {}",
            "",
            "/** @type {!Array<!Dog>} */",
            "var dogs = [];",
            "",
            "for (var dog of dogs) {",
            "  dog.meow();",  // type error
            "}"),
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testWarningGuardOrdering1() {
    args.add("--jscomp_error=globalThis");
    args.add("--jscomp_off=globalThis");
    testSame("function f() { this.a = 3; }");
  }

  public void testWarningGuardOrdering2() {
    args.add("--jscomp_off=globalThis");
    args.add("--jscomp_error=globalThis");
    test("function f() { this.a = 3; }", CheckGlobalThis.GLOBAL_THIS);
  }

  public void testWarningGuardOrdering3() {
    args.add("--jscomp_warning=globalThis");
    args.add("--jscomp_off=globalThis");
    testSame("function f() { this.a = 3; }");
  }

  public void testWarningGuardOrdering4() {
    args.add("--jscomp_off=globalThis");
    args.add("--jscomp_warning=globalThis");
    test("function f() { this.a = 3; }", CheckGlobalThis.GLOBAL_THIS);
  }

  public void testWarningGuardWildcard1() {
    args.add("--jscomp_warning=*");
    test("/** @public */function f() { this.a = 3; }", CheckGlobalThis.GLOBAL_THIS);
  }

  public void testWarningGuardWildcardOrdering() {
    args.add("--jscomp_warning=*");
    args.add("--jscomp_off=globalThis");
    testSame("/** @public */function f() { this.a = 3; }");
  }

  public void testWarningGuardHideWarningsFor1() {
    args.add("--jscomp_warning=globalThis");
    args.add("--hide_warnings_for=foo/bar");
    setFilename(0, "foo/bar.baz");
    testSame("function f() { this.a = 3; }");
  }

  public void testWarningGuardHideWarningsFor2() {
    args.add("--jscomp_warning=globalThis");
    args.add("--hide_warnings_for=bar/baz");
    setFilename(0, "foo/bar.baz");
    test("function f() { this.a = 3; }", CheckGlobalThis.GLOBAL_THIS);
  }

  public void testSimpleModeLeavesUnusedParams() {
    args.add("--compilation_level=SIMPLE_OPTIMIZATIONS");
    testSame("window.f = function(a) {};");
  }

  public void testAdvancedModeRemovesUnusedParams() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test("window.f = function(a) {};", "window.a = function() {};");
  }

  public void testCheckGlobalThisOffByDefault() {
    testSame("function f() { this.a = 3; }");
  }

  public void testCheckGlobalThisOnWithAdvancedMode() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test("function f() { this.a = 3; }", CheckGlobalThis.GLOBAL_THIS);
  }

  public void testCheckGlobalThisOnWithAdvanced() {
    args.add("-O=ADVANCED");
    test("function f() { this.a = 3; }", CheckGlobalThis.GLOBAL_THIS);
  }

  public void testCheckGlobalThisOnWithErrorFlag() {
    args.add("--jscomp_error=globalThis");
    test("function f() { this.a = 3; }", CheckGlobalThis.GLOBAL_THIS);
  }

  public void testCheckGlobalThisOff() {
    args.add("--warning_level=VERBOSE");
    args.add("--jscomp_off=globalThis");
    testSame("function f() { this.a = 3; }");
  }

  public void testTypeCheckingOffByDefault() {
    test("function f(x) { return x; } f();",
         "function f(a) { return a; } f();");
  }

  public void testReflectedMethods() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test(
        "/** @constructor */" +
        "function Foo() {}" +
        "Foo.prototype.handle = function(x, y) { alert(y); };" +
        "var x = goog.reflect.object(Foo, {handle: 1});" +
        "for (var i in x) { x[i].call(x); }" +
        "window['Foo'] = Foo;",
        "function a() {}" +
        "a.prototype.a = function(e, d) { alert(d); };" +
        "var b = goog.c.b(a, {a: 1}),c;" +
        "for (c in b) { b[c].call(b); }" +
        "window.Foo = a;");
  }

  public void testInlineVariables() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    // Verify local var "val" in method "bar" is not inlined over the "inc"
    // method call (which has side-effects) but "c" is inlined (which can't be
    // modified by the call).
    test(
        "/** @constructor */ function F() { this.a = 0; }" +
        "F.prototype.inc = function() { this.a++; return 10; };" +
        "F.prototype.bar = function() { " +
        "  var c = 3; var val = this.inc(); this.a += val + c;" +
        "};" +
        "window['f'] = new F();" +
        "window['f']['inc'] = window['f'].inc;" +
        "window['f']['bar'] = window['f'].bar;" +
        "use(window['f'].a)",
        "function a(){ this.a = 0; }" +
        "a.prototype.b = function(){ this.a++; return 10; };" +
        "a.prototype.c = function(){ var b=this.b(); this.a += b + 3; };" +
        "window.f = new a;" +
        "window.f.inc = window.f.b;" +
        "window.f.bar = window.f.c;" +
        "use(window.f.a);");
  }

  public void testTypedAdvanced() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test(
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "Foo.prototype.handle1 = function(x, y) { alert(y); };\n" +
        "/** @constructor */\n" +
        "function Bar() {}\n" +
        "Bar.prototype.handle1 = function(x, y) {};\n" +
        "new Foo().handle1(1, 2);\n" +
        "new Bar().handle1(1, 2);\n",
        "alert(2)");
  }

  public void testTypedDisabledAdvanced() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    args.add("--use_types_for_optimization=false");
    test(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        +"Foo.prototype.handle1 = function(x, y) { alert(y); };\n"
        + "/** @constructor */\n"
        + "function Bar() {}\n"
        + "Bar.prototype.handle1 = function(x, y) {};\n"
        + "new Foo().handle1(1, 2);\n"
        + "new Bar().handle1(1, 2);\n",
        "function a() {}\n"
        + "a.prototype.a = function(d, c) { alert(c); };\n"
        + "function b() {}\n"
        + "b.prototype.a = function() {};\n"
        + "(new a).a(1, 2);\n"
        + "(new b).a(1, 2);");
  }

  public void testTypeCheckingOnWithVerbose() {
    args.add("--warning_level=VERBOSE");
    test("function f(x) { return x; } f();", TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testTypeCheckingOnWithWVerbose() {
    args.add("-W=VERBOSE");
    test("function f(x) { return x; } f();", TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testTypeParsingOffByDefault() {
    testSame("/** @return {number */ function f(a) { return a; }");
  }

  public void testTypeParsingOnWithVerbose() {
    args.add("--warning_level=VERBOSE");
    test("/** @return {number */ function f(a) { return a; }",
         RhinoErrorReporter.TYPE_PARSE_ERROR);
    test("/** @return {n} */ function f(a) { return a; }",
         RhinoErrorReporter.TYPE_PARSE_ERROR);
  }

  public void testTypeCheckOverride1() {
    args.add("--warning_level=VERBOSE");
    args.add("--jscomp_off=checkTypes");
    testSame("var x = x || {}; x.f = function() {}; x.f(3);");
  }

  public void testTypeCheckOverride2() {
    args.add("--warning_level=DEFAULT");
    testSame("var x = x || {}; x.f = function() {}; x.f(3);");

    args.add("--jscomp_warning=checkTypes");
    test("var x = x || {}; x.f = function() {}; x.f(3);",
         TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testCheckSymbolsOffForDefault() {
    args.add("--warning_level=DEFAULT");
    test("x = 3; var y; var y;", "x=3; var y;");
  }

  public void testCheckSymbolsOnForVerbose() {
    args.add("--jscomp_error=checkVars");
    args.add("--warning_level=VERBOSE");
    test("x = 3;", VarCheck.UNDEFINED_VAR_ERROR);
    test("var y; var y;", VarCheck.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testCheckSymbolsOverrideForVerbose() {
    args.add("--warning_level=VERBOSE");
    args.add("--jscomp_off=undefinedVars");
    testSame("x = 3;");
  }

  public void testCheckSymbolsOverrideForQuiet() {
    args.add("--warning_level=QUIET");
    args.add("--jscomp_error=undefinedVars");
    test("x = 3;", VarCheck.UNDEFINED_VAR_ERROR);
  }

  public void testCheckUndefinedProperties1() {
    args.add("--warning_level=VERBOSE");
    args.add("--jscomp_error=missingProperties");
    test("var x = {}; var y = x.bar;", TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testCheckUndefinedProperties2() {
    args.add("--warning_level=VERBOSE");
    args.add("--jscomp_off=missingProperties");
    test("var x = {}; var y = x.bar;", CheckGlobalNames.UNDEFINED_NAME_WARNING);
  }

  public void testCheckUndefinedProperties3() {
    args.add("--warning_level=VERBOSE");
    test("function f() {var x = {}; var y = x.bar;}",
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testDuplicateParams() {
    test("function f(a, a) {}", RhinoErrorReporter.DUPLICATE_PARAM);
    assertThat(lastCompiler.hasHaltingErrors()).isTrue();
  }

  public void testDefineFlag() {
    args.add("--define=FOO");
    args.add("--define=\"BAR=5\"");
    args.add("--D"); args.add("CCC");
    args.add("-D"); args.add("DDD");
    test("/** @define {boolean} */ var FOO = false;" +
         "/** @define {number} */ var BAR = 3;" +
         "/** @define {boolean} */ var CCC = false;" +
         "/** @define {boolean} */ var DDD = false;",
         "var FOO = !0, BAR = 5, CCC = !0, DDD = !0;");
  }

  public void testDefineFlag2() {
    args.add("--define=FOO='x\"'");
    test("/** @define {string} */ var FOO = \"a\";",
         "var FOO = \"x\\\"\";");
  }

  public void testDefineFlag3() {
    args.add("--define=FOO=\"x'\"");
    test("/** @define {string} */ var FOO = \"a\";",
         "var FOO = \"x'\";");
  }

  public void testScriptStrictModeNoWarning() {
    test("'use strict';", "");
    test("'no use strict';", CheckSideEffects.USELESS_CODE_ERROR);
  }

  public void testFunctionStrictModeNoWarning() {
    test("function f() {'use strict';}", "function f() {}");
    test("function f() {'no use strict';}",
         CheckSideEffects.USELESS_CODE_ERROR);
  }

  public void testQuietMode() {
    args.add("--warning_level=DEFAULT");
    test("/** @const \n * @const */ var x;",
         RhinoErrorReporter.PARSE_ERROR);
    args.add("--warning_level=QUIET");
    testSame("/** @const \n * @const */ var x;");
  }

  public void testProcessClosurePrimitives() {
    test("var goog = {}; goog.provide('goog.dom');",
         "var goog = {dom:{}};");
    args.add("--process_closure_primitives=false");
    testSame("var goog = {}; goog.provide('goog.dom');");
  }

  public void testGetMsgWiring() throws Exception {
    test("var goog = {}; goog.getMsg = function(x) { return x; };" +
         "/** @desc A real foo. */ var MSG_FOO = goog.getMsg('foo');",
         "var goog={getMsg:function(a){return a}}, " +
         "MSG_FOO=goog.getMsg('foo');");
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test("var goog = {}; goog.getMsg = function(x) { return x; };" +
         "/** @desc A real foo. */ var MSG_FOO = goog.getMsg('foo');" +
         "window['foo'] = MSG_FOO;",
         "window.foo = 'foo';");
  }

  public void testGetMsgWiringNoWarnings() throws Exception {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test("/** @desc A bad foo. */ var MSG_FOO = 1;", "");
  }

  public void testCssNameWiring() throws Exception {
    test("var goog = {}; goog.getCssName = function() {};" +
         "goog.setCssNameMapping = function() {};" +
         "goog.setCssNameMapping({'goog': 'a', 'button': 'b'});" +
         "var a = goog.getCssName('goog-button');" +
         "var b = goog.getCssName('css-button');" +
         "var c = goog.getCssName('goog-menu');" +
         "var d = goog.getCssName('css-menu');",
         "var goog = { getCssName: function() {}," +
         "             setCssNameMapping: function() {} }," +
         "    a = 'a-b'," +
         "    b = 'css-b'," +
         "    c = 'a-menu'," +
         "    d = 'css-menu';");
  }


  public void testIssue70a() {
    args.add("--language_in=ECMASCRIPT5");
    test("function foo({}) {}", RhinoErrorReporter.ES6_FEATURE);
  }

  public void testIssue70b() {
    args.add("--language_in=ECMASCRIPT5");
    test("function foo([]) {}", RhinoErrorReporter.ES6_FEATURE);
  }

  public void testIssue81() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    useStringComparison = true;
    test("eval('1'); var x = eval; x('2');",
         "eval(\"1\");(0,eval)(\"2\");");
  }

  public void testIssue115() {
    args.add("--compilation_level=SIMPLE_OPTIMIZATIONS");
    args.add("--jscomp_off=es5Strict");
    args.add("--warning_level=VERBOSE");
    test("function f() { " +
         "  var arguments = Array.prototype.slice.call(arguments, 0);" +
         "  return arguments[0]; " +
         "}",
         "function f() { " +
         "  arguments = Array.prototype.slice.call(arguments, 0);" +
         "  return arguments[0]; " +
         "}");
  }

  public void testIssue297() {
    args.add("--compilation_level=SIMPLE_OPTIMIZATIONS");
    test("function f(p) {" +
         " var x;" +
         " return ((x=p.id) && (x=parseInt(x.substr(1)))) && x>0;" +
         "}",
         "function f(b) {" +
         " var a;" +
         " return ((a=b.id) && (a=parseInt(a.substr(1)))) && 0<a;" +
         "}");
  }

  public void testHiddenSideEffect() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test("element.offsetWidth;",
         "element.offsetWidth", CheckSideEffects.USELESS_CODE_ERROR);
  }

  public void testIssue504() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test("void function() { alert('hi'); }();",
         "alert('hi');void 0", CheckSideEffects.USELESS_CODE_ERROR);
  }

  public void testIssue601() {
    args.add("--compilation_level=WHITESPACE_ONLY");
    test("function f() { return '\\v' == 'v'; } window['f'] = f;",
         "function f(){return'\\v'=='v'}window['f']=f");
  }

  public void testIssue601b() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test("function f() { return '\\v' == 'v'; } window['f'] = f;",
         "window.f=function(){return'\\v'=='v'}");
  }

  public void testIssue601c() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test("function f() { return '\\u000B' == 'v'; } window['f'] = f;",
         "window.f=function(){return'\\u000B'=='v'}");
  }

  public void testIssue846() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    testSame(
        "try { new Function('this is an error'); } catch(a) { alert('x'); }");
  }

  public void testSideEffectIntegration() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test("/** @constructor */" +
         "var Foo = function() {};" +

         "Foo.prototype.blah = function() {" +
         "  Foo.bar_(this)" +
         "};" +

         "Foo.bar_ = function(f) {" +
         "  f.x = 5;" +
         "};" +

         "var y = new Foo();" +

         "Foo.bar_({});" +

         // We used to strip this too
         // due to bad side-effect propagation.
         "y.blah();" +

         "alert(y);",
         "var a = new function(){}; a.a = 5; alert(a);");
  }

  public void testDebugFlag1() {
    args.add("--compilation_level=SIMPLE_OPTIMIZATIONS");
    args.add("--debug=false");
    test("function foo(a) {}",
         "function foo(a) {}");
  }

  public void testDebugFlag2() {
    args.add("--compilation_level=SIMPLE_OPTIMIZATIONS");
    args.add("--debug=true");
    test("function foo(a) {alert(a)}",
         "function foo($a$$) {alert($a$$)}");
  }

  public void testDebugFlag3() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    args.add("--warning_level=QUIET");
    args.add("--debug=false");
    test("function Foo() {}" +
         "Foo.x = 1;" +
         "function f() {throw new Foo().x;} f();",
         "throw (new function() {}).a;");
  }

  public void testDebugFlag4() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    args.add("--warning_level=QUIET");
    args.add("--debug=true");
    test("function Foo() {}" +
        "Foo.x = 1;" +
        "function f() {throw new Foo().x;} f();",
        "throw (new function Foo() {}).$x$;");
  }

  public void testBooleanFlag1() {
    args.add("--compilation_level=SIMPLE_OPTIMIZATIONS");
    args.add("--debug");
    test("function foo(a) {alert(a)}",
         "function foo($a$$) {alert($a$$)}");
  }

  public void testBooleanFlag2() {
    args.add("--debug");
    args.add("--compilation_level=SIMPLE_OPTIMIZATIONS");
    test("function foo(a) {alert(a)}",
         "function foo($a$$) {alert($a$$)}");
  }

  public void testHelpFlag() {
    args.add("--help");
    CommandLineRunner runner =
        createCommandLineRunner(new String[] {"function f() {}"});
    assertThat(runner.shouldRunCompiler()).isFalse();
    assertThat(runner.hasErrors()).isFalse();
    String output = new String(outReader.toByteArray(), UTF_8);
    assertThat(output).contains(" --help ");
    assertThat(output).contains(" --version ");
  }

  public void testHoistedFunction1() {
    args.add("--jscomp_off=es5Strict");
    args.add("-W=VERBOSE");
    test("if (true) { f(); function f() {} }",
         VariableReferenceCheck.EARLY_REFERENCE);
  }

  public void testHoistedFunction2() {
    test("if (window) { f(); function f() {} }",
         "if (window) { var f = function() {}; f(); }");
  }

  public void testExternsLifting1() throws Exception{
    String code = "/** @externs */ function f() {}";
    test(new String[] {code},
         new String[] {});

    assertThat(lastCompiler.getExternsForTesting()).hasSize(2);

    CompilerInput extern = lastCompiler.getExternsForTesting().get(1);
    assertThat(extern.getModule()).isNull();
    assertThat(extern.isExtern()).isTrue();
    assertThat(extern.getCode()).isEqualTo(code);

    assertThat(lastCompiler.getInputsForTesting()).hasSize(1);

    CompilerInput input = lastCompiler.getInputsForTesting().get(0);
    assertThat(input.getModule()).isNotNull();
    assertThat(input.isExtern()).isFalse();
    assertThat(input.getCode()).isEmpty();
  }

  public void testExternsLifting2() {
    args.add("--warning_level=VERBOSE");
    test(new String[] {"/** @externs */ function f() {}", "f(3);"},
         new String[] {"f(3);"},
         TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testSourceSortingOff() {
    args.add("--compilation_level=WHITESPACE_ONLY");
    testSame(
        new String[] {
          "goog.require('beer');",
          "goog.provide('beer');"
        });
  }

  public void testSourceSortingOn() {
    test(new String[] {
          "goog.require('beer');",
          "goog.provide('beer');"
         },
         new String[] {
           "var beer = {};",
           ""
         });
  }

  public void testSourceSortingOn2() {
    test(new String[] {
          "goog.provide('a');",
          "goog.require('a');\n" +
          "var COMPILED = false;",
         },
         new String[] {
           "var a={};",
           "var COMPILED=!1"
         });
  }

  public void testSourceSortingOn3() {
    args.add("--dependency_mode=LOOSE");
    args.add("--language_in=ECMASCRIPT5");
    test(new String[] {
          "goog.addDependency('sym', [], []);\nvar x = 3;",
          "var COMPILED = false;",
         },
         new String[] {
          "var COMPILED = !1;",
          "var x = 3;"
         });
  }

  public void testSourceSortingCircularDeps1() {
    args.add("--dependency_mode=LOOSE");
    args.add("--language_in=ECMASCRIPT5");
    test(new String[] {
          "goog.provide('gin'); goog.require('tonic'); var gin = {};",
          "goog.provide('tonic'); goog.require('gin'); var tonic = {};",
          "goog.require('gin'); goog.require('tonic');"
         },
         ProcessClosurePrimitives.LATE_PROVIDE_ERROR);
  }

  public void testSourceSortingCircularDeps2() {
    args.add("--dependency_mode=LOOSE");
    args.add("--language_in=ECMASCRIPT5");
    test(new String[] {
          "goog.provide('roses.lime.juice');",
          "goog.provide('gin'); goog.require('tonic'); var gin = {};",
          "goog.provide('tonic'); goog.require('gin'); var tonic = {};",
          "goog.require('gin'); goog.require('tonic');",
          "goog.provide('gimlet'); goog.require('gin'); goog.require('roses.lime.juice');"
         },
         ProcessClosurePrimitives.LATE_PROVIDE_ERROR);
  }

  public void testSourcePruningOn1() {
    args.add("--dependency_mode=LOOSE");
    args.add("--language_in=ECMASCRIPT5");
    test(new String[] {
          "goog.require('beer');",
          "goog.provide('beer');",
          "goog.provide('scotch'); var x = 3;"
         },
         new String[] {
           "var beer = {};",
           ""
         });
  }

  public void testSourcePruningOn2() {
    args.add("--entry_point=goog:guinness");
    test(new String[] {
          "goog.provide('guinness');\ngoog.require('beer');",
          "goog.provide('beer');",
          "goog.provide('scotch'); var x = 3;"
         },
         new String[] {
           "var beer = {};",
           "var guinness = {};"
         });
  }

  public void testSourcePruningOn3() {
    args.add("--entry_point=goog:scotch");
    test(new String[] {
          "goog.provide('guinness');\ngoog.require('beer');",
          "goog.provide('beer');",
          "goog.provide('scotch'); var x = 3;"
         },
         new String[] {
           "var scotch = {}, x = 3;",
         });
  }

  public void testSourcePruningOn4() {
    args.add("--entry_point=goog:scotch");
    args.add("--entry_point=goog:beer");
    test(new String[] {
          "goog.provide('guinness');\ngoog.require('beer');",
          "goog.provide('beer');",
          "goog.provide('scotch'); var x = 3;"
         },
         new String[] {
           "var beer = {};",
           "var scotch = {}, x = 3;",
         });
  }

  public void testSourcePruningOn5() {
    args.add("--entry_point=goog:shiraz");
    test(new String[] {
          "goog.provide('guinness');\ngoog.require('beer');",
          "goog.provide('beer');",
          "goog.provide('scotch'); var x = 3;"
         },
         Compiler.MISSING_ENTRY_ERROR);
  }

  public void testSourcePruningOn6() {
    args.add("--entry_point=goog:scotch");
    test(new String[] {
          "goog.require('beer');",
          "goog.provide('beer');",
          "goog.provide('scotch'); var x = 3;"
         },
         new String[] {
           "var beer = {};",
           "",
           "var scotch = {}, x = 3;",
         });
  }

  public void testSourcePruningOn7() {
    args.add("--dependency_mode=LOOSE");
    test(new String[] {
          "var COMPILED = false;",
         },
         new String[] {
          "var COMPILED = !1;",
         });
  }

  public void testSourcePruningOn8() {
    args.add("--dependency_mode=STRICT");
    args.add("--entry_point=goog:scotch");
    args.add("--warning_level=VERBOSE");
    test(new String[] {
          "/** @externs */\n" +
          "var externVar;",
          "goog.provide('scotch'); var x = externVar;"
         },
         new String[] {
           "var scotch = {}, x = externVar;",
         });
  }

  public void testModuleEntryPoint() throws Exception {
    useModules = ModulePattern.STAR;
    args.add("--dependency_mode=STRICT");
    args.add("--entry_point=goog:m1:a");
    test(
        new String[] {
          "goog.provide('a');",
          "goog.provide('b');"
        },
        // Check that 'b' was stripped out, and 'a' was moved to the second
        // module (m1).
        new String[] {
          "",
          "var a = {};"
        });
  }

  public void testNoCompile() {
    args.add("--warning_level=VERBOSE");
    test(new String[] {
          "/** @nocompile */\n" +
          "goog.provide('x');\n" +
          "var dupeVar;",
          "var dupeVar;"
         },
         new String[] {
           "var dupeVar;"
         });
  }

  public void testDependencySortingWhitespaceMode() {
    args.add("--dependency_mode=LOOSE");
    args.add("--compilation_level=WHITESPACE_ONLY");
    test(new String[] {
          "goog.require('beer');",
          "goog.provide('beer');\ngoog.require('hops');",
          "goog.provide('hops');",
         },
         new String[] {
          "goog.provide('hops');",
          "goog.provide('beer');\ngoog.require('hops');",
          "goog.require('beer');"
         });
  }

  public void testForwardDeclareDroppedTypes() {
    args.add("--dependency_mode=LOOSE");

    args.add("--warning_level=VERBOSE");
    test(
        new String[] {
          "goog.require('beer');",
          "goog.provide('beer'); /** @param {Scotch} x */ function f(x) {}",
          "goog.provide('Scotch'); var x = 3;"
        },
        new String[] {"var beer = {}; function f(a) {}", ""});

    test(new String[] {
          "goog.require('beer');",
          "goog.provide('beer'); /** @param {Scotch} x */ function f(x) {}"
         },
         new String[] {
           "var beer = {}; function f(a) {}",
           ""
         },
         RhinoErrorReporter.TYPE_PARSE_ERROR);
  }

  public void testOnlyClosureDependenciesEmptyEntryPoints() throws Exception {
    // Prevents this from trying to load externs.zip
    args.add("--env=CUSTOM");

    args.add("--dependency_mode=STRICT");
    try {
      CommandLineRunner runner = createCommandLineRunner(new String[0]);
      runner.doRun();
      fail("Expected FlagUsageException");
    } catch (FlagUsageException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("dependency_mode=STRICT"));
    }
  }

  public void testOnlyClosureDependenciesOneEntryPoint() throws Exception {
    args.add("--dependency_mode=STRICT");
    args.add("--entry_point=goog:beer");
    test(new String[] {
          "goog.require('beer'); var beerRequired = 1;",
          "goog.provide('beer');\ngoog.require('hops');\nvar beerProvided = 1;",
          "goog.provide('hops'); var hopsProvided = 1;",
          "goog.provide('scotch'); var scotchProvided = 1;",
          "goog.require('scotch');\nvar includeFileWithoutProvides = 1;",
          "/** This is base.js */\nvar COMPILED = false;",
         },
         new String[] {
           "var COMPILED = !1;",
           "var hops = {}, hopsProvided = 1;",
           "var beer = {}, beerProvided = 1;"
         });
  }

  public void testSourceMapExpansion1() {
    args.add("--js_output_file");
    args.add("/path/to/out.js");
    args.add("--create_source_map=%outname%.map");
    testSame("var x = 3;");
    assertThat(lastCommandLineRunner.expandSourceMapPath(lastCompiler.getOptions(), null))
        .isEqualTo("/path/to/out.js.map");
  }

  public void testSourceMapExpansion2() {
    useModules = ModulePattern.CHAIN;
    args.add("--create_source_map=%outname%.map");
    args.add("--module_output_path_prefix=foo");
    testSame(new String[] {"var x = 3;", "var y = 5;"});
    assertThat(lastCommandLineRunner.expandSourceMapPath(lastCompiler.getOptions(), null))
        .isEqualTo("foo.map");
  }

  public void testSourceMapExpansion3() {
    useModules = ModulePattern.CHAIN;
    args.add("--create_source_map=%outname%.map");
    args.add("--module_output_path_prefix=foo_");
    testSame(new String[] {"var x = 3;", "var y = 5;"});
    assertThat(
        lastCommandLineRunner.expandSourceMapPath(
            lastCompiler.getOptions(), lastCompiler.getModuleGraph().getRootModule()))
        .isEqualTo("foo_m0.js.map");
  }

  public void testInvalidSourceMapPattern() {
    useModules = ModulePattern.CHAIN;
    args.add("--create_source_map=out.map");
    args.add("--module_output_path_prefix=foo_");
    test(
        new String[] {"var x = 3;", "var y = 5;"},
        AbstractCommandLineRunner.INVALID_MODULE_SOURCEMAP_PATTERN);
  }

  public void testSourceMapFormat1() {
    args.add("--js_output_file");
    args.add("/path/to/out.js");
    testSame("var x = 3;");
    assertThat(lastCompiler.getOptions().sourceMapFormat).isEqualTo(SourceMap.Format.DEFAULT);
  }

  public void testSourceMapFormat2() {
    args.add("--js_output_file");
    args.add("/path/to/out.js");
    args.add("--source_map_format=V3");
    testSame("var x = 3;");
    assertThat(lastCompiler.getOptions().sourceMapFormat).isEqualTo(SourceMap.Format.V3);
  }

  public void testSourceMapLocationsTranslations1() {
    args.add("--js_output_file");
    args.add("/path/to/out.js");
    args.add("--create_source_map=%outname%.map");
    args.add("--source_map_location_mapping=foo/|http://bar");
    testSame("var x = 3;");

    List<LocationMapping> mappings = lastCompiler.getOptions()
        .sourceMapLocationMappings;
    assertThat(ImmutableSet.copyOf(mappings).toString())
        .isEqualTo(ImmutableSet.of(new LocationMapping("foo/", "http://bar")).toString());
  }

  public void testSourceMapLocationsTranslations2() {
    args.add("--js_output_file");
    args.add("/path/to/out.js");
    args.add("--create_source_map=%outname%.map");
    args.add("--source_map_location_mapping=foo/|http://bar");
    args.add("--source_map_location_mapping=xxx/|http://yyy");
    testSame("var x = 3;");

    List<LocationMapping> mappings = lastCompiler.getOptions()
        .sourceMapLocationMappings;
    assertThat(ImmutableSet.copyOf(mappings).toString())
        .isEqualTo(
            ImmutableSet.of(
                    new LocationMapping("foo/", "http://bar"),
                    new LocationMapping("xxx/", "http://yyy"))
                .toString());
  }

  public void testSourceMapLocationsTranslations3() {
    // Prevents this from trying to load externs.zip
    args.add("--env=CUSTOM");

    args.add("--js_output_file");
    args.add("/path/to/out.js");
    args.add("--create_source_map=%outname%.map");
    args.add("--source_map_location_mapping=foo/");

    CommandLineRunner runner = createCommandLineRunner(new String[0]);
    assertThat(runner.shouldRunCompiler()).isFalse();
    assertThat(new String(errReader.toByteArray(), UTF_8))
        .contains("Bad value for --source_map_location_mapping");
  }

  public void testInputOneZip() throws IOException {
    LinkedHashMap<String, String> zip1Contents = new LinkedHashMap<>();
    zip1Contents.put("run.js", "console.log(\"Hello World\");");
    FlagEntry<JsSourceType> zipFile1 = createZipFile(zip1Contents);

    compileFiles("console.log(\"Hello World\");", zipFile1);
  }

  public void testInputMultipleZips() throws IOException {
    LinkedHashMap<String, String> zip1Contents = new LinkedHashMap<>();
    zip1Contents.put("run.js", "console.log(\"Hello World\");");
    FlagEntry<JsSourceType> zipFile1 = createZipFile(zip1Contents);

    LinkedHashMap<String, String> zip2Contents = new LinkedHashMap<>();
    zip2Contents.put("run1.js", "window.alert(\"Hi Browser\");");
    FlagEntry<JsSourceType> zipFile2 = createZipFile(zip2Contents);

    compileFiles(
        "console.log(\"Hello World\");window.alert(\"Hi Browser\");", zipFile1, zipFile2);
  }

  public void testInputMultipleDuplicateZips() throws IOException {
    args.add("--jscomp_error=duplicateZipContents");
    FlagEntry<JsSourceType> zipFile1 =
        createZipFile(ImmutableMap.of("run.js", "console.log(\"Hello World\");"));

    FlagEntry<JsSourceType> zipFile2 =
        createZipFile(ImmutableMap.of("run.js", "console.log(\"Hello World\");"));

    compileFilesError(
        SourceFile.DUPLICATE_ZIP_CONTENTS, zipFile1, zipFile2);
  }

  public void testInputMultipleConflictingZips() throws IOException {
    FlagEntry<JsSourceType> zipFile1 =
        createZipFile(ImmutableMap.of("run.js", "console.log(\"Hello World\");"));

    FlagEntry<JsSourceType> zipFile2 =
        createZipFile(ImmutableMap.of("run.js", "window.alert(\"Hi Browser\");"));

    compileFilesError(
        AbstractCommandLineRunner.CONFLICTING_DUPLICATE_ZIP_CONTENTS, zipFile1, zipFile2);
  }

  public void testInputMultipleContents() throws IOException {
    LinkedHashMap<String, String> zip1Contents = new LinkedHashMap<>();
    zip1Contents.put("a.js", "console.log(\"File A\");");
    zip1Contents.put("b.js", "console.log(\"File B\");");
    zip1Contents.put("c.js", "console.log(\"File C\");");
    FlagEntry<JsSourceType> zipFile1 = createZipFile(zip1Contents);

    compileFiles(
        "console.log(\"File A\");console.log(\"File B\");console.log(\"File C\");", zipFile1);
  }

  public void testInputMultipleFiles() throws IOException {
    LinkedHashMap<String, String> zip1Contents = new LinkedHashMap<>();
    zip1Contents.put("run.js", "console.log(\"Hello World\");");
    FlagEntry<JsSourceType> zipFile1 = createZipFile(zip1Contents);

    FlagEntry<JsSourceType> jsFile1 = createJsFile("testjsfile", "var a;");

    LinkedHashMap<String, String> zip2Contents = new LinkedHashMap<>();
    zip2Contents.put("run1.js", "window.alert(\"Hi Browser\");");
    FlagEntry<JsSourceType> zipFile2 = createZipFile(zip2Contents);

    compileFiles(
        "console.log(\"Hello World\");var a;window.alert(\"Hi Browser\");",
        zipFile1, jsFile1, zipFile2);
  }

  public void testInputMultipleJsFilesWithOneJsFlag() throws IOException {
    // Test that file order is preserved with --js test3.js test2.js test1.js
    FlagEntry<JsSourceType> jsFile1 = createJsFile("test1", "var a;");
    FlagEntry<JsSourceType> jsFile2 = createJsFile("test2", "var b;");
    FlagEntry<JsSourceType> jsFile3 = createJsFile("test3", "var c;");
    compileJsFiles("var c;var b;var a;", jsFile3, jsFile2, jsFile1);
  }

  public void testGlobJs1() throws IOException {
    FlagEntry<JsSourceType> jsFile1 = createJsFile("test1", "var a;");
    FlagEntry<JsSourceType> jsFile2 = createJsFile("test2", "var b;");
    // Move test2 to the same directory as test1, also make the filename of test2
    // lexicographically larger than test1
    assertTrue(new File(jsFile2.getValue()).renameTo(new File(
        new File(jsFile1.getValue()).getParentFile() + File.separator + "utest2.js")));
    String glob = new File(jsFile1.getValue()).getParent() + File.separator + "**.js";
    compileFiles(
        "var a;var b;", new FlagEntry<>(JsSourceType.JS, glob));
  }

  public void testGlobJs2() throws IOException {
    FlagEntry<JsSourceType> jsFile1 = createJsFile("test1", "var a;");
    FlagEntry<JsSourceType> jsFile2 = createJsFile("test2", "var b;");
    assertTrue(new File(jsFile2.getValue()).renameTo(new File(
        new File(jsFile1.getValue()).getParentFile() + File.separator + "utest2.js")));
    String glob = new File(jsFile1.getValue()).getParent() + File.separator + "*test*.js";
    compileFiles(
        "var a;var b;", new FlagEntry<>(JsSourceType.JS, glob));
  }

  public void testGlobJs3() throws IOException {
    FlagEntry<JsSourceType> jsFile1 = createJsFile("test1", "var a;");
    FlagEntry<JsSourceType> jsFile2 = createJsFile("test2", "var b;");
    assertTrue(new File(jsFile2.getValue()).renameTo(new File(
        new File(jsFile1.getValue()).getParentFile() + File.separator + "test2.js")));
    // Make sure test2.js is excluded from the inputs when the exclusion
    // comes after the inclusion
    String glob1 = new File(jsFile1.getValue()).getParent() + File.separator + "**.js";
    String glob2 = "!" + new File(jsFile1.getValue()).getParent() + File.separator + "**test2.js";
    compileFiles(
        "var a;", new FlagEntry<>(JsSourceType.JS, glob1),
        new FlagEntry<>(JsSourceType.JS, glob2));
  }

  public void testGlobJs4() throws IOException {
    FlagEntry<JsSourceType> jsFile1 = createJsFile("test1", "var a;");
    FlagEntry<JsSourceType> jsFile2 = createJsFile("test2", "var b;");
    assertTrue(new File(jsFile2.getValue()).renameTo(new File(
        new File(jsFile1.getValue()).getParentFile() + File.separator + "test2.js")));
    // Make sure test2.js is excluded from the inputs when the exclusion
    // comes before the inclusion
    String glob1 = "!" + new File(jsFile1.getValue()).getParent() + File.separator + "**test2.js";
    String glob2 = new File(jsFile1.getValue()).getParent() + File.separator + "**.js";
    compileFiles(
        "var a;", new FlagEntry<>(JsSourceType.JS, glob1),
        new FlagEntry<>(JsSourceType.JS, glob2));
  }

  public void testGlobJs5() throws IOException {
    FlagEntry<JsSourceType> jsFile1 = createJsFile("test1", "var a;");
    FlagEntry<JsSourceType> jsFile2 = createJsFile("test2", "var b;");
    File temp1 = Files.createTempDir();
    File temp2 = Files.createTempDir();
    File jscompTempDir = new File(jsFile1.getValue()).getParentFile();
    File newTemp1 = new File(jscompTempDir + File.separator + "temp1");
    File newTemp2 = new File(jscompTempDir + File.separator + "temp2");
    assertTrue(temp1.renameTo(newTemp1));
    assertTrue(temp2.renameTo(newTemp2));
    new File(jsFile1.getValue()).renameTo(new File(newTemp1 + File.separator + "test1.js"));
    new File(jsFile2.getValue()).renameTo(new File(newTemp2 + File.separator + "test2.js"));
    // Test multiple segments with glob patterns, like /foo/bar/**/*.js
    String glob = jscompTempDir + File.separator + "**" + File.separator + "*.js";
    compileFiles(
        "var a;var b;", new FlagEntry<>(JsSourceType.JS, glob));
  }

  // TODO(tbreisacher): Re-enable this test when we drop Ant.
  public void disabled_testGlobJs6() throws IOException {
    FlagEntry<JsSourceType> jsFile1 = createJsFile("test1", "var a;");
    FlagEntry<JsSourceType> jsFile2 = createJsFile("test2", "var b;");
    File ignoredJs = new File("." + File.separator + "ignored.js");
    if (ignoredJs.isDirectory()) {
      for (File f : ignoredJs.listFiles()) {
        f.delete();
      }
    }
    ignoredJs.delete();
    assertTrue(new File(jsFile2.getValue()).renameTo(ignoredJs));
    // Make sure patterns like "!**\./ignored**.js" work
    String glob1 = "!**\\." + File.separator + "ignored**.js";
    String glob2 = new File(jsFile1.getValue()).getParent() + File.separator + "**.js";
    compileFiles(
        "var a;", new FlagEntry<>(JsSourceType.JS, glob1),
        new FlagEntry<>(JsSourceType.JS, glob2));
    ignoredJs.delete();
  }

  // TODO(tbreisacher): Re-enable this test when we drop Ant.
  public void disabled_testGlobJs7() throws IOException {
    FlagEntry<JsSourceType> jsFile1 = createJsFile("test1", "var a;");
    FlagEntry<JsSourceType> jsFile2 = createJsFile("test2", "var b;");
    File takenJs = new File("." + File.separator + "globTestTaken.js");
    File ignoredJs = new File("." + File.separator + "globTestIgnored.js");
    if (takenJs.isDirectory()) {
      for (File f : takenJs.listFiles()) {
        f.delete();
      }
    }
    takenJs.delete();
    if (ignoredJs.isDirectory()) {
      for (File f : ignoredJs.listFiles()) {
        f.delete();
      }
    }
    ignoredJs.delete();
    assertTrue(new File(jsFile1.getValue()).renameTo(takenJs));
    assertTrue(new File(jsFile2.getValue()).renameTo(ignoredJs));
    // Make sure that relative paths like "!**ignored.js" work with absolute paths.
    String glob1 = takenJs.getParentFile().getAbsolutePath() + File.separator + "**Taken.js";
    String glob2 = "!**Ignored.js";
    compileFiles(
        "var a;", new FlagEntry<>(JsSourceType.JS, glob1),
        new FlagEntry<>(JsSourceType.JS, glob2));
    takenJs.delete();
    ignoredJs.delete();
  }

  public void testSourceMapInputs() throws Exception {
    args.add("--js_output_file");
    args.add("/path/to/out.js");
    args.add("--source_map_input=input1|input1.sourcemap");
    args.add("--source_map_input=input2|input2.sourcemap");
    testSame("var x = 3;");

    Map<String, SourceMapInput> inputMaps = lastCompiler.getOptions()
        .inputSourceMaps;
    assertThat(inputMaps).hasSize(2);
    assertThat(inputMaps.get("input1").getOriginalPath())
        .isEqualTo("input1.sourcemap");
    assertThat(inputMaps.get("input2").getOriginalPath())
        .isEqualTo("input2.sourcemap");
  }

  public void testModuleWrapperBaseNameExpansion() throws Exception {
    useModules = ModulePattern.CHAIN;
    args.add("--module_wrapper=m0:%s // %basename%");
    testSame(new String[] {
      "var x = 3;",
      "var y = 4;"
    });

    StringBuilder builder = new StringBuilder();
    lastCommandLineRunner.writeModuleOutput(
        builder,
        lastCompiler.getModuleGraph().getRootModule());
    assertThat(builder.toString()).isEqualTo("var x=3; // m0.js\n");
  }

  public void testCharSetExpansion() {
    testSame("");
    assertThat(lastCompiler.getOptions().outputCharset).isEqualTo(US_ASCII);
    args.add("--charset=UTF-8");
    testSame("");
    assertThat(lastCompiler.getOptions().outputCharset).isEqualTo(UTF_8);
  }

  public void testChainModuleManifest() throws Exception {
    useModules = ModulePattern.CHAIN;
    testSame(new String[] {"var x = 3;", "var y = 5;", "var z = 7;", "var a = 9;"});

    StringBuilder builder = new StringBuilder();
    lastCommandLineRunner.printModuleGraphManifestOrBundleTo(
        lastCompiler.getModuleGraph(), builder, true);
    assertThat(builder.toString())
        .isEqualTo(Joiner.on('\n').join(
            "{m0}",
            "i0",
            "",
            "{m1:m0}",
            "i1",
            "",
            "{m2:m1}",
            "i2",
            "",
            "{m3:m2}",
            "i3",
            ""));
  }

  public void testStarModuleManifest() throws Exception {
    useModules = ModulePattern.STAR;
    testSame(new String[] {
          "var x = 3;", "var y = 5;", "var z = 7;", "var a = 9;"});

    StringBuilder builder = new StringBuilder();
    lastCommandLineRunner.printModuleGraphManifestOrBundleTo(
        lastCompiler.getModuleGraph(), builder, true);
    assertThat(builder.toString())
        .isEqualTo(Joiner.on('\n').join(
            "{m0}",
            "i0",
            "",
            "{m1:m0}",
            "i1",
            "",
            "{m2:m0}",
            "i2",
            "",
            "{m3:m0}",
            "i3",
            ""));
  }

  public void testOutputModuleGraphJson() throws Exception {
    useModules = ModulePattern.STAR;
    testSame(new String[] {"var x = 3;", "var y = 5;", "var z = 7;", "var a = 9;"});

    StringBuilder builder = new StringBuilder();
    lastCommandLineRunner.printModuleGraphJsonTo(builder);
    assertThat(builder.toString()).contains("transitive-dependencies");
  }

  public void testVersionFlag() {
    args.add("--version");
    CommandLineRunner runner =
        createCommandLineRunner(new String[] {"function f() {}"});
    assertThat(runner.shouldRunCompiler()).isFalse();
    assertThat(runner.hasErrors()).isFalse();
    assertThat(
        new String(outReader.toByteArray(), UTF_8)
            .indexOf("Closure Compiler (http://github.com/google/closure-compiler)\n"
                + "Version: ")).isEqualTo(0);
  }

  public void testVersionFlag2() {
    lastArg = "--version";
    CommandLineRunner runner =
        createCommandLineRunner(new String[] {"function f() {}"});
    assertThat(runner.shouldRunCompiler()).isFalse();
    assertThat(runner.hasErrors()).isFalse();
    assertThat(new String(outReader.toByteArray(), UTF_8))
        .startsWith("Closure Compiler (http://github.com/google/closure-compiler)\nVersion: ");
  }

  public void testPrintAstFlag() {
    args.add("--print_ast=true");
    testSame("");
    assertThat(new String(outReader.toByteArray(), UTF_8))
        .isEqualTo("digraph AST {\n"
            + "  node [color=lightblue2, style=filled];\n"
            + "  node0 [label=\"BLOCK\"];\n"
            + "  node1 [label=\"SCRIPT\"];\n"
            + "  node0 -> node1 [weight=1];\n"
            + "  node1 -> RETURN [label=\"UNCOND\", "
            + "fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
            + "  node0 -> RETURN [label=\"SYN_BLOCK\", "
            + "fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
            + "  node0 -> node1 [label=\"UNCOND\", "
            + "fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
            + "}\n\n");
  }

  public void testSyntheticExterns() {
    externs = ImmutableList.of(
        SourceFile.fromCode("externs", "function Symbol() {}; myVar.property;"));
    test("var theirVar = {}; var myVar = {}; var yourVar = {};",
         VarCheck.UNDEFINED_EXTERN_VAR_ERROR);

    args.add("--jscomp_off=externsValidation");
    args.add("--warning_level=VERBOSE");
    test("var theirVar = {}; var myVar = {}; var yourVar = {};",
         "var theirVar={},myVar={},yourVar={};");

    args.add("--jscomp_off=externsValidation");
    args.add("--jscomp_error=checkVars");
    args.add("--warning_level=VERBOSE");
    test(
        "var theirVar = {}; var myVar = {}; var myVar = {};", VarCheck.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testGoogAssertStripping() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    test("goog.asserts.assert(false)", "");
    args.add("--debug");
    test("goog.asserts.assert(false)", "goog.$asserts$.$assert$(!1)");
  }

  public void testMissingReturnCheckOnWithVerbose() {
    args.add("--warning_level=VERBOSE");
    test("/** @return {number} */ function f() {f()} f();",
        CheckMissingReturn.MISSING_RETURN_STATEMENT);
  }

  public void testChecksOnlySkipsOptimizations() {
    args.add("--checks_only");
    test("var foo = 1 + 1;",
      "var foo = 1 + 1;");
  }

  public void testChecksOnlyWithParseError() {
    args.add("--compilation_level=WHITESPACE_ONLY");
    args.add("--checks_only");
    test("val foo = 1;",
      RhinoErrorReporter.PARSE_ERROR);
  }

  public void testChecksOnlyWithWarning() {
    args.add("--checks_only");
    args.add("--warning_level=VERBOSE");
    test("/** @deprecated */function foo() {}; foo();",
      CheckAccessControls.DEPRECATED_NAME);
  }

  public void testGenerateExports() {
    args.add("--generate_exports=true");
    test("var goog; /** @export */ foo.prototype.x = function() {};",
        "var goog; foo.prototype.x=function(){};" +
        "goog.exportProperty(foo.prototype,\"x\",foo.prototype.x);");
  }

  public void testDepreciationWithVerbose() {
    args.add("--warning_level=VERBOSE");
    test("/** @deprecated */ function f() {}; f()",
       CheckAccessControls.DEPRECATED_NAME);
  }

  public void testTwoParseErrors() {
    // If parse errors are reported in different files, make
    // sure all of them are reported.
    Compiler compiler = compile(new String[] {
      "var a b;",
      "var b c;"
    });
    assertThat(compiler.getErrors()).hasLength(2);
  }

  public void testES3() {
    args.add("--language_in=ECMASCRIPT3");
    useStringComparison = true;
    test(
        "var x = f.function",
        "var x=f[\"function\"];",
        RhinoErrorReporter.INVALID_ES3_PROP_NAME);
  }

  public void testES6TranspiledByDefault() {
    test("var x = class X {};", "var x = function() {};");
  }

  public void testES5ChecksByDefault() {
    testSame("var x = 3; delete x;");
  }

  public void testES5ChecksInVerbose() {
    args.add("--warning_level=VERBOSE");
    test("function f(x) { delete x; }", StrictModeCheck.DELETE_VARIABLE);
  }

  public void testES5() {
    args.add("--language_in=ECMASCRIPT5");
    test("var x = f.function", "var x = f.function");
    test("var let", "var let");
  }

  public void testES5Strict() {
    args.add("--language_in=ECMASCRIPT5_STRICT");
    args.add("--language_out=ECMASCRIPT5_STRICT");
    test("var x = f.function", "'use strict';var x = f.function");
    test("var let", RhinoErrorReporter.PARSE_ERROR);
    test("function f(x) { delete x; }", StrictModeCheck.DELETE_VARIABLE);
  }

  public void testES5StrictUseStrict() {
    args.add("--language_in=ECMASCRIPT5_STRICT");
    args.add("--language_out=ECMASCRIPT5_STRICT");
    Compiler compiler = compile(new String[] {"var x = f.function"});
    String outputSource = compiler.toSource();
    assertThat(outputSource).startsWith("'use strict'");
  }

  public void testES5StrictUseStrictMultipleInputs() {
    args.add("--language_in=ECMASCRIPT5_STRICT");
    args.add("--language_out=ECMASCRIPT5_STRICT");
    Compiler compiler = compile(new String[] {"var x = f.function",
        "var y = f.function", "var z = f.function"});
    String outputSource = compiler.toSource();
    assertThat(outputSource).startsWith("'use strict'");
    assertThat(outputSource.substring(13)).doesNotContain("'use strict'");
  }

  public void testWithKeywordWithEs5ChecksOff() {
    args.add("--jscomp_off=es5Strict");
    testSame("var x = {}; with (x) {}");
  }

  public void testNoSrCFilesWithManifest() throws IOException {
    args.add("--env=CUSTOM");
    args.add("--output_manifest=test.MF");
    CommandLineRunner runner = createCommandLineRunner(new String[0]);
    String expectedMessage = "";
    try {
      runner.doRun();
    } catch (FlagUsageException e) {
      expectedMessage = e.getMessage();
    }
    assertThat("Bad --js flag. "
        + "Manifest files cannot be generated when the input is from stdin.")
        .isEqualTo(expectedMessage);
  }

  public void testTransformAMD() {
    args.add("--transform_amd_modules");
    test("define({test: 1})", "module.exports = {test: 1}");
  }

  public void testProcessCJS() {
    useStringComparison = true;
    args.add("--process_common_js_modules");
    args.add("--entry_point=foo/bar");
    setFilename(0, "foo/bar.js");
    String expected = "var module$foo$bar={test:1};";
    test("exports.test = 1", expected);
    assertThat(outReader.toString()).isEqualTo(expected + "\n");
  }

  public void testProcessCJSWithModuleOutput() {
    args.add("--process_common_js_modules");
    args.add("--entry_point=foo/bar");
    args.add("--module=auto");
    setFilename(0, "foo/bar.js");
    test("exports.test = 1",
        "var module$foo$bar={test:1};");
    // With modules=auto no direct output is created.
    assertThat(outReader.toString()).isEmpty();
  }

  /**
   * closure requires mixed with cjs, raised in
   * https://github.com/google/closure-compiler/pull/630
   * https://gist.github.com/sayrer/c4c4ce0c1748573f863e
   */
  public void testProcessCJSWithClosureRequires() {
    args.add("--process_common_js_modules");
    args.add("--entry_point=app");
    args.add("--dependency_mode=STRICT");
    setFilename(0, "base.js");
    setFilename(1, "array.js");
    setFilename(2, "Baz.js");
    setFilename(3, "app.js");
    test(
        new String[] {
          LINE_JOINER.join(
              "/** @provideGoog */",
              "/** @const */ var goog = goog || {};",
              "var COMPILED = false;",
              "goog.provide = function (arg) {};",
              "goog.require = function (arg) {};"),
          "goog.provide('goog.array');",
          LINE_JOINER.join(
              "goog.require('goog.array');",
              "function Baz() {}",
              "Baz.prototype = {",
              "  baz: function() {",
              "    return goog.array.last(['asdf','asd','baz']);",
              "  },",
              "  bar: function () {",
              "    return 4 + 4;",
              "  }",
              "};",
              "module.exports = Baz;"),
          LINE_JOINER.join(
              "var Baz = require('./Baz');",
              "var baz = new Baz();",
              "console.log(baz.baz());",
              "console.log(baz.bar());")
        },
        new String[] {
          LINE_JOINER.join(
              "var goog=goog||{},COMPILED=!1;",
              "goog.provide=function(a){};goog.require=function(a){};"),
          "goog.array={};",
          LINE_JOINER.join(
              "function Baz$$module$Baz(){}",
              "Baz$$module$Baz.prototype={",
              "  baz:function(){return goog.array.last(['asdf','asd','baz'])},",
              "  bar:function(){return 8}",
              "};",
              "var module$Baz=Baz$$module$Baz;"),
          LINE_JOINER.join(
              "var Baz = Baz$$module$Baz,",
              "    baz = new Baz();",
              "console.log(baz.baz());",
              "console.log(baz.bar());")
        });
  }

  /**
   * closure requires mixed with cjs, raised in
   * https://github.com/google/closure-compiler/pull/630
   * https://gist.github.com/sayrer/c4c4ce0c1748573f863e
   */
  public void testProcessCJSWithClosureRequires2() {
    args.add("--process_common_js_modules");
    args.add("--dependency_mode=LOOSE");
    args.add("--entry_point=app");
    setFilename(0, "base.js");
    setFilename(1, "array.js");
    setFilename(2, "Baz.js");
    setFilename(3, "app.js");

    test(
        new String[] {
          LINE_JOINER.join(
              "/** @provideGoog */",
              "/** @const */ var goog = goog || {};",
              "var COMPILED = false;",
              "goog.provide = function (arg) {};",
              "goog.require = function (arg) {};"),
          "goog.provide('goog.array');",
          LINE_JOINER.join(
              "goog.require('goog.array');",
              "function Baz() {}",
              "Baz.prototype = {",
              "  baz: function() {",
              "    return goog.array.last(['asdf','asd','baz']);",
              "  },",
              "  bar: function () {",
              "    return 4 + 4;",
              "  }",
              "};",
              "module.exports = Baz;"),
          LINE_JOINER.join(
              "var Baz = require('./Baz');",
              "var baz = new Baz();",
              "console.log(baz.baz());",
              "console.log(baz.bar());")
        },
        new String[] {
          LINE_JOINER.join(
          "var goog=goog||{},COMPILED=!1;",
              "goog.provide=function(a){};goog.require=function(a){};"),
          "goog.array={};",
          LINE_JOINER.join(
              "function Baz$$module$Baz(){}",
              "Baz$$module$Baz.prototype={",
              "baz:function(){return goog.array.last([\"asdf\",\"asd\",\"baz\"])},",
              "bar:function(){return 8}",
              "};",
              "var module$Baz=Baz$$module$Baz;"),
          LINE_JOINER.join(
              "var Baz = Baz$$module$Baz,",
              "    baz = new Baz();",
              "console.log(baz.baz());",
              "console.log(baz.bar());")
        });
  }

  public void testProcessCJSWithES6Export() {
    args.add("--process_common_js_modules");
    args.add("--entry_point=app");
    args.add("--dependency_mode=STRICT");
    args.add("--language_in=ECMASCRIPT6");
    setFilename(0, "foo.js");
    setFilename(1, "app.js");
    test(
        new String[] {
          LINE_JOINER.join(
              "export default class Foo {",
              "  bar() { console.log('bar'); }",
              "}"),
          LINE_JOINER.join(
             "var FooBar = require('./foo');",
             "var baz = new FooBar.default();",
             "console.log(baz.bar());")
        },
        new String[] {
          LINE_JOINER.join(
              "var module$foo={},",
              "Foo$$module$foo=function(){};",
              "Foo$$module$foo.prototype.bar=function(){console.log(\"bar\")};",
              "module$foo.default=Foo$$module$foo;"),
          LINE_JOINER.join(
              "var FooBar = module$foo,",
              "    baz = new FooBar.default();",
              "console.log(baz.bar());")
        });
  }

  public void testES6ImportOfCJS() {
    args.add("--process_common_js_modules");
    args.add("--entry_point=app");
    args.add("--dependency_mode=STRICT");
    args.add("--language_in=ECMASCRIPT6");
    setFilename(0, "foo.js");
    setFilename(1, "app.js");
    test(
        new String[] {
          LINE_JOINER.join(
              "/** @constructor */ function Foo () {}",
              "Foo.prototype.bar = function() { console.log('bar'); };",
              "module.exports = Foo;"),
          LINE_JOINER.join(
              "import * as FooBar from './foo';",
              "var baz = new FooBar();",
              "console.log(baz.bar());")
        },
        new String[] {
          LINE_JOINER.join(
              "function Foo$$module$foo(){}",
              "Foo$$module$foo.prototype.bar=function(){console.log(\"bar\")};",
              "var module$foo=Foo$$module$foo;"),
          LINE_JOINER.join(
              "var baz$$module$app = new Foo$$module$foo();",
              "console.log(baz$$module$app.bar());")
        });
  }

  public void testES6ImportOfFileWithoutImportsOrExports() {
    args.add("--dependency_mode=NONE");
    args.add("--language_in=ECMASCRIPT6");
    setFilename(0, "foo.js");
    setFilename(1, "app.js");
    test(
        new String[] {
          CompilerTestCase.LINE_JOINER.join("function foo() { alert('foo'); }", "foo();"),
          "import './foo';"
        },
        new String[] {
          CompilerTestCase.LINE_JOINER.join(
              "/** @const */ var module$foo={};",
              "function foo$$module$foo(){ alert('foo'); }",
              "foo$$module$foo();"),
          "'use strict';"
        });
  }

  public void testCommonJSRequireOfFileWithoutExports() {
    args.add("--process_common_js_modules");
    args.add("--dependency_mode=NONE");
    args.add("--language_in=ECMASCRIPT6");
    setFilename(0, "foo.js");
    setFilename(1, "app.js");
    test(
        new String[] {
          CompilerTestCase.LINE_JOINER.join("function foo() { alert('foo'); }", "foo();"),
          "require('./foo');"
        },
        new String[] {
          CompilerTestCase.LINE_JOINER.join(
              "/** @const */ var module$foo={};",
              "function foo$$module$foo(){ alert('foo'); }",
              "foo$$module$foo();"),
          CompilerTestCase.LINE_JOINER.join("'use strict';", "")
        });
  }

  public void testFormattingSingleQuote() {
    testSame("var x = '';");
    assertThat(lastCompiler.toSource()).isEqualTo("var x=\"\";");

    args.add("--formatting=SINGLE_QUOTES");
    testSame("var x = '';");
    assertThat(lastCompiler.toSource()).isEqualTo("var x='';");
  }

  public void testTransformAMDAndProcessCJS() {
    args.add("--transform_amd_modules");
    args.add("--process_common_js_modules");
    args.add("--entry_point=foo/bar");
    setFilename(0, "foo/bar.js");
    test("define({foo: 1})",
        "var module$foo$bar={foo:1};");
  }

  public void testModuleJSON() {
    args.add("--transform_amd_modules");
    args.add("--process_common_js_modules");
    args.add("--entry_point=foo/bar");
    args.add("--output_module_dependencies=test.json");
    setFilename(0, "foo/bar.js");
    test("define({foo: 1})",
        "var module$foo$bar={foo:1};");
  }

  public void testOutputSameAsInput() {
    args.add("--js_output_file=" + getFilename(0));
    test("", AbstractCommandLineRunner.OUTPUT_SAME_AS_INPUT_ERROR);
  }

  public void testOutputWrapperFlag() {
    // if the output wrapper flag is specified without a valid output marker,
    // ensure that the compiler displays an error and exits.
    // See github issue 123
    args.add("--output_wrapper=output");
    CommandLineRunner runner =
        createCommandLineRunner(new String[] {"function f() {}"});
    assertThat(runner.shouldRunCompiler()).isFalse();
    assertThat(runner.hasErrors()).isTrue();
  }

  public void testJsonStreamInputFlag() {
    String inputString = "[{\"src\": \"alert('foo');\", \"path\":\"foo.js\"}]";
    args.add("--json_streams=IN");

    CommandLineRunner runner = new CommandLineRunner(
        args.toArray(new String[]{}),
        new ByteArrayInputStream(inputString.getBytes()),
        new PrintStream(outReader),
        new PrintStream(errReader));

    lastCompiler = runner.getCompiler();
    try {
      runner.doRun();
    } catch (IOException e) {
      e.printStackTrace();
      fail("Unexpected exception " + e);
    }
    String output = runner.getCompiler().toSource();
    assertThat(output).isEqualTo("alert(\"foo\");");
  }

  public void testJsonStreamOutputFlag() {
    String inputString = "alert('foo');";
    args.add("--json_streams=OUT");

    CommandLineRunner runner = new CommandLineRunner(
        args.toArray(new String[]{}),
        new ByteArrayInputStream(inputString.getBytes()),
        new PrintStream(outReader),
        new PrintStream(errReader));

    lastCompiler = runner.getCompiler();
    try {
      runner.doRun();
    } catch (IOException e) {
      e.printStackTrace();
      fail("Unexpected exception " + e);
    }
    String output = new String(outReader.toByteArray(), UTF_8);
    assertThat(output).isEqualTo("[{\"src\":\"alert(\\\"foo\\\");\\n\","
        + "\"path\":\"compiled.js\",\"source_map\":\"{\\n\\\"version\\\":3,"
        + "\\n\\\"file\\\":\\\"compiled.js\\\",\\n\\\"lineCount\\\":1,"
        + "\\n\\\"mappings\\\":\\\"AAAAA,KAAA,CAAM,KAAN;\\\","
        + "\\n\\\"sources\\\":[\\\"stdin\\\"],"
        + "\\n\\\"names\\\":[\\\"alert\\\"]\\n}\\n\"}]");
  }

  public void testJsonStreamBothFlag() {
    String inputString = "[{\"src\": \"alert('foo');\", \"path\":\"foo.js\"}]";
    args.add("--json_streams=BOTH");
    args.add("--js_output_file=bar.js");

    CommandLineRunner runner = new CommandLineRunner(
        args.toArray(new String[]{}),
        new ByteArrayInputStream(inputString.getBytes()),
        new PrintStream(outReader),
        new PrintStream(errReader));

    lastCompiler = runner.getCompiler();
    try {
      runner.doRun();
    } catch (IOException e) {
      e.printStackTrace();
      fail("Unexpected exception " + e);
    }
    String output = new String(outReader.toByteArray(), UTF_8);
    assertThat(output).isEqualTo("[{\"src\":\"alert(\\\"foo\\\");\\n\","
        + "\"path\":\"bar.js\",\"source_map\":\"{\\n\\\"version\\\":3,"
        + "\\n\\\"file\\\":\\\"bar.js\\\",\\n\\\"lineCount\\\":1,"
        + "\\n\\\"mappings\\\":\\\"AAAAA,KAAA,CAAM,KAAN;\\\","
        + "\\n\\\"sources\\\":[\\\"foo.js\\\"],"
        + "\\n\\\"names\\\":[\\\"alert\\\"]\\n}\\n\"}]");
  }

  public void testOutputModuleNaming() {
    String inputString = "[{\"src\": \"alert('foo');\", \"path\":\"foo.js\"}]";
    args.add("--json_streams=BOTH");
    args.add("--module=foo--bar.baz:1");

    CommandLineRunner runner = new CommandLineRunner(
        args.toArray(new String[]{}),
        new ByteArrayInputStream(inputString.getBytes()),
        new PrintStream(outReader),
        new PrintStream(errReader));

    lastCompiler = runner.getCompiler();
    try {
      runner.doRun();
    } catch (IOException e) {
      e.printStackTrace();
      fail("Unexpected exception " + e);
    }
    String output = new String(outReader.toByteArray(), UTF_8);
    assertThat(output).isEqualTo("[{\"src\":\"alert(\\\"foo\\\");\\n\","
        + "\"path\":\"./foo--bar.baz.js\",\"source_map\":\"{\\n\\\"version\\\":3,"
        + "\\n\\\"file\\\":\\\"./foo--bar.baz.js\\\",\\n\\\"lineCount\\\":1,"
        + "\\n\\\"mappings\\\":\\\"AAAAA,KAAA,CAAM,KAAN;\\\","
        + "\\n\\\"sources\\\":[\\\"foo.js\\\"],"
        + "\\n\\\"names\\\":[\\\"alert\\\"]\\n}\\n\"}]");
  }

  public void testAssumeFunctionWrapper() {
    args.add("--compilation_level=SIMPLE_OPTIMIZATIONS");
    args.add("--assume_function_wrapper");
    // remove used vars enabled
    test(
        "var someName = function(a) {};",
        "");
    // function inlining enabled.
    test(
        "var someName = function() {return 'hi'};alert(someName())",
        "alert('hi')");
    // renaming enabled, and collapse anonymous functions enabled
    test(
        "var someName = function() {return 'hi'};alert(someName);alert(someName)",
        "function a() {return 'hi'}alert(a);alert(a)");
  }

  /* Helper functions */

  private void testSame(String original) {
    testSame(new String[] { original });
  }

  private void testSame(String[] original) {
    test(original, original);
  }

  private void test(String original, String compiled) {
    test(new String[] { original }, new String[] { compiled });
  }

  /**
   * Asserts that when compiling with the given compiler options,
   * {@code original} is transformed into {@code compiled}.
   */
  private void test(String[] original, String[] compiled) {
    test(original, compiled, null);
  }

  /**
   * Asserts that when compiling with the given compiler options,
   * {@code original} is transformed into {@code compiled}.
   * If {@code warning} is non-null, we will also check if the given
   * warning type was emitted.
   */
  private void test(String[] original, String[] compiled, DiagnosticType warning) {
    exitCodes.clear();
    Compiler compiler = compile(original);

    if (warning == null) {
      assertEquals("Expected no warnings or errors\n" +
          "Errors: \n" + Joiner.on("\n").join(compiler.getErrors()) +
          "Warnings: \n" + Joiner.on("\n").join(compiler.getWarnings()),
          0, compiler.getErrors().length + compiler.getWarnings().length);
    } else {
      assertThat(compiler.getWarnings()).hasLength(1);
      assertThat(compiler.getWarnings()[0].getType()).isEqualTo(warning);
    }

    Node root = compiler.getRoot().getLastChild();
    if (useStringComparison) {
      assertThat(compiler.toSource()).isEqualTo(Joiner.on("").join(compiled));
    } else {
      Node expectedRoot = parse(compiled);
      String explanation = expectedRoot.checkTreeEquals(root);
      assertNull("\nExpected: " + compiler.toSource(expectedRoot) +
          "\nResult: " + compiler.toSource(root) +
          "\n" + explanation, explanation);
    }

    assertThat(exitCodes).containsExactly(0);
  }

  /**
   * Asserts that when compiling, there is an error or warning.
   */
  private void test(String original, DiagnosticType warning) {
    test(new String[] { original }, warning);
  }

  private void test(String original, String expected, DiagnosticType warning) {
    test(new String[] { original }, new String[] { expected }, warning);
  }

  /**
   * Asserts that when compiling, there is an error or warning.
   */
  private void test(String[] original, DiagnosticType warning) {
    Compiler compiler = compile(original);
    assertEquals("Expected exactly one warning or error " +
        "\nErrors: \n" + Joiner.on("\n").join(compiler.getErrors()) +
        "\nWarnings: \n" + Joiner.on("\n").join(compiler.getWarnings()),
        1, compiler.getErrors().length + compiler.getWarnings().length);

    assertThat(exitCodes).isNotEmpty();
    int lastExitCode = Iterables.getLast(exitCodes);

    if (compiler.getErrors().length > 0) {
      assertThat(compiler.getErrors()).hasLength(1);
      assertThat(compiler.getErrors()[0].getType()).isEqualTo(warning);
      assertThat(lastExitCode).isEqualTo(1);
    } else {
      assertThat(compiler.getWarnings()).hasLength(1);
      assertThat(compiler.getWarnings()[0].getType()).isEqualTo(warning);
      assertThat(lastExitCode).isEqualTo(0);
    }
  }

  private CommandLineRunner createCommandLineRunner(String[] original) {
    for (int i = 0; i < original.length; i++) {
      args.add("--js");
      args.add("/path/to/input" + i + ".js");
      if (useModules == ModulePattern.CHAIN) {
        args.add("--module");
        args.add("m" + i + ":1" + (i > 0 ? (":m" + (i - 1)) : ""));
      } else if (useModules == ModulePattern.STAR) {
        args.add("--module");
        args.add("m" + i + ":1" + (i > 0 ? ":m0" : ""));
      }
    }

    if (lastArg != null) {
      args.add(lastArg);
    }

    String[] argStrings = args.toArray(new String[] {});
    return new CommandLineRunner(
        argStrings,
        new PrintStream(outReader),
        new PrintStream(errReader));
  }

  private static FlagEntry<JsSourceType> createZipFile(Map<String, String> entryContentsByName)
      throws IOException {
    File tempZipFile = File.createTempFile("testdata", ".js.zip",
        java.nio.file.Files.createTempDirectory("jscomp").toFile());

    try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(tempZipFile))) {
      for (Entry<String, String> entry : entryContentsByName.entrySet()) {
        zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
        zipOutputStream.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
    }

    return new FlagEntry<>(JsSourceType.JS_ZIP, tempZipFile.getAbsolutePath());
  }

  private FlagEntry<JsSourceType> createJsFile(String filename, String fileContent)
      throws IOException {
    File tempJsFile = File.createTempFile(filename, ".js",
        java.nio.file.Files.createTempDirectory("jscomp").toFile());
    try (FileOutputStream fileOutputStream = new FileOutputStream(tempJsFile)) {
      fileOutputStream.write(fileContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    return new FlagEntry<>(JsSourceType.JS, tempJsFile.getAbsolutePath());
  }

  /**
   * Helper for compiling from zip and js files and checking output string.
   * @param expectedOutput string representation of expected output.
   * @param entries entries of flags for zip and js files containing source to compile.
   */
  @SafeVarargs
  private final void compileFiles(String expectedOutput, FlagEntry<JsSourceType>... entries) {
    setupFlags(entries);
    compileArgs(expectedOutput, null);
  }

  @SafeVarargs
  private final void compileFilesError(
      DiagnosticType expectedError, FlagEntry<JsSourceType>... entries) {
    setupFlags(entries);
    compileArgs("", expectedError);
  }

  @SafeVarargs
  private final void setupFlags(FlagEntry<JsSourceType>... entries) {
    for (FlagEntry<JsSourceType> entry : entries) {
      args.add("--" + entry.getFlag().flagName + "=" + entry.getValue());
    }
  }

  /**
   * Helper for compiling js files and checking output string, using a single --js flag.
   * @param expectedOutput string representation of expected output.
   * @param entries entries of flags for js files containing source to compile.
   */
  @SafeVarargs
  private final void compileJsFiles(String expectedOutput, FlagEntry<JsSourceType>... entries)
      throws FlagUsageException {
    args.add("--js");
    for (FlagEntry<JsSourceType> entry : entries) {
      args.add(entry.getValue());
    }
    compileArgs(expectedOutput, null);
  }

  private void compileArgs(String expectedOutput, DiagnosticType expectedError)
      throws FlagUsageException {
    String[] argStrings = args.toArray(new String[] {});

    CommandLineRunner runner =
        new CommandLineRunner(argStrings, new PrintStream(outReader), new PrintStream(errReader));
    lastCompiler = runner.getCompiler();
    try {
      runner.doRun();
    } catch (IOException e) {
      e.printStackTrace();
      fail("Unexpected exception " + e);
    }
    Compiler compiler = runner.getCompiler();
    String output = compiler.toSource();
    if (expectedError == null) {
      assertThat(compiler.getErrors()).isEmpty();
      assertThat(compiler.getWarnings()).isEmpty();
      assertThat(output).isEqualTo(expectedOutput);
    } else {
      assertThat(compiler.getErrors()).hasLength(1);
      assertError(compiler.getErrors()[0]).hasType(expectedError);
    }
  }

  private Compiler compile(String[] original) {
    CommandLineRunner runner = createCommandLineRunner(original);
    if (!runner.shouldRunCompiler()) {
      assertThat(runner.hasErrors()).isTrue();
      fail(new String(errReader.toByteArray(), UTF_8));
    }
    Supplier<List<SourceFile>> inputsSupplier = null;
    Supplier<List<JSModule>> modulesSupplier = null;

    if (useModules == ModulePattern.NONE) {
      List<SourceFile> inputs = new ArrayList<>();
      for (int i = 0; i < original.length; i++) {
        inputs.add(SourceFile.fromCode(getFilename(i), original[i]));
      }
      inputsSupplier = Suppliers.ofInstance(inputs);
    } else if (useModules == ModulePattern.STAR) {
      modulesSupplier = Suppliers.<List<JSModule>>ofInstance(
          ImmutableList.copyOf(
              CompilerTestCase.createModuleStar(original)));
    } else if (useModules == ModulePattern.CHAIN) {
      modulesSupplier = Suppliers.<List<JSModule>>ofInstance(
          ImmutableList.copyOf(
              CompilerTestCase.createModuleChain(original)));
    } else {
      throw new IllegalArgumentException("Unknown module type: " + useModules);
    }

    runner.enableTestMode(
        Suppliers.ofInstance(externs),
        inputsSupplier,
        modulesSupplier,
        new Function<Integer, Boolean>() {
          @Override
          public Boolean apply(Integer code) {
            return exitCodes.add(code);
          }
        });
    runner.run();
    lastCompiler = runner.getCompiler();
    lastCommandLineRunner = runner;
    return lastCompiler;
  }

  private Node parse(String[] original) {
    String[] argStrings = args.toArray(new String[] {});
    CommandLineRunner runner = new CommandLineRunner(argStrings);
    Compiler compiler = runner.createCompiler();
    List<SourceFile> inputs = new ArrayList<>();
    for (int i = 0; i < original.length; i++) {
      inputs.add(SourceFile.fromCode(getFilename(i), original[i]));
    }
    CompilerOptions options = new CompilerOptions();
    // ECMASCRIPT5 is the most forgiving.
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    compiler.init(externs, inputs, options);
    Node all = compiler.parseInputs();
    Preconditions.checkState(compiler.getErrorCount() == 0);
    Preconditions.checkNotNull(all);
    Node n = all.getLastChild();
    return n;
  }

  private void setFilename(int i, String filename) {
    this.filenames.put(i, filename);
  }

  private String getFilename(int i) {
    if (filenames.isEmpty()) {
      return "input" + i;
    }
    String name = filenames.get(i);
    Preconditions.checkState(name != null && !name.isEmpty());
    return name;
  }
}
