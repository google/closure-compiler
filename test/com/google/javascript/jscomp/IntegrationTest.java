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
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static com.google.javascript.jscomp.TypeValidator.TYPE_MISMATCH_WARNING;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Chars;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.CompilerOptions.Reach;
import com.google.javascript.jscomp.CompilerTestCase.NoninjectingCompiler;
import com.google.javascript.jscomp.testing.NodeSubject;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Integration tests for the compiler.
 *
 * @author nicksantos@google.com (Nick Santos)
 */

public final class IntegrationTest extends IntegrationTestCase {
  private static final String CLOSURE_BOILERPLATE =
      "/** @define {boolean} */ var COMPILED = false; var goog = {};"
      + "goog.exportSymbol = function() {};";

  private static final String CLOSURE_COMPILED =
      "var COMPILED = true; var goog$exportSymbol = function() {};";

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

  public void testIssue2365() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.addWarningsGuard(new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.CHECK_TYPES, CheckLevel.OFF));

    // With type checking disabled we should assume that extern functions (even ones known not to
    // have any side effects) return a non-local value, so it isn't safe to remove assignments to
    // properties on them.
    // noSideEffects() and the property 'value' are declared in the externs defined in
    // IntegrationTestCase.
    testSame(options, "noSideEffects().value = 'something';");
  }

  public void testBug65688660() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setCoalesceVariableNames(true);
    test(
        options,
        LINE_JOINER.join(
            "function f(param) {",
            "  if (true) {",
            "    const b1 = [];",
            "    for (const [key, value] of []) {}",
            "  }",
            "  if (true) {",
            "    const b2 = [];",
            "    for (const kv of []) {",
            "      const key2 = kv.key;",
            "    }",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f(param) {",
            "  if (true) {",
            "    param = [];",
            "    for (const [key, value] of []) {}",
            "  }",
            "  if (true) {",
            "    param = [];",
            "    for (const kv of []) {",
            "      param = kv.key;",
            "    }",
            "  }",
            "}"));
  }

  public void testBug65688660_pseudoNames() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setGeneratePseudoNames(true);
    options.setCoalesceVariableNames(true);
    test(
        options,
        LINE_JOINER.join(
            "function f(param) {",
            "  if (true) {",
            "    const b1 = [];",
            "    for (const [key, value] of []) {}",
            "  }",
            "  if (true) {",
            "    const b2 = [];",
            "    for (const kv of []) {",
            "      const key2 = kv.key;",
            "    }",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f(b1_b2_key2_param) {",
            "  if (true) {",
            "    b1_b2_key2_param = [];",
            "    for (const [key, value] of []) {}",
            "  }",
            "  if (true) {",
            "    b1_b2_key2_param = [];",
            "    for (const kv of []) {",
            "      b1_b2_key2_param = kv.key;",
            "    }",
            "  }",
            "}"));
  }

  public void testObjDestructuringConst() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setCoalesceVariableNames(true);
    test(
        options,
        LINE_JOINER.join(
            "function f(obj) {",
            "  {",
            "    const {foo} = obj;",
            "    alert(foo);",
            "  }",
            "  {",
            "    const {bar} = obj;",
            "    alert(bar);",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f(obj) {",
            "  {",
            "    const {foo} = obj;",
            "    alert(foo);",
            "  }",
            "  {",
            "    var {bar: obj} = obj;",
            "    alert(obj);",
            "  }",
            "}"));
  }

  public void testConstructorCycle() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    test(
        options,
        "/** @return {function()} */ var AsyncTestCase = function() {};\n"
        + "/**\n"
        + " * @constructor\n"
        + " */ Foo = /** @type {function(new:Foo)} */ (AyncTestCase());",
        RhinoErrorReporter.PARSE_ERROR);
  }

  // b/27531865
  public void testLetInSwitch() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.ERROR);
    String before = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "    let x = 1;",
        "  case 1:",
        "    x = 2;",
        "}");
    String after = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "    var x = 1;",
        "  case 1:",
        "    x = 2;",
        "}");
    test(options, before, after);

    before = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "  default:",
        "    let x = 1;",
        "}");
    after = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "  default:",
        "    var x = 1;",
        "}");
    test(options, before, after);
  }

  public void testExplicitBlocksInSwitch() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.ERROR);
    String before = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "    { const x = 3; break; }",
        "  case 1:",
        "    { const x = 5; break; }",
        "}");
    String after = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "    { var x = 3; break; }",
        "  case 1:",
        "    { var x$0 = 5; break; }",
        "}");
    test(options, before, after);
  }

  public void testMultipleAliasesInlined_bug31437418() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    test(
        options,
        LINE_JOINER.join(
            "class A { static z() {} }",
            "const B = {};",
            " B.A = A;",
            " const C = {};",
            " C.A = B.A; ",
            "const D = {};",
            " D.A = C.A;",
            " D.A.z();"),
        LINE_JOINER.join(
            "var A = function(){};",
            "var A$z = function(){};",
            "var B$A = null;",
            "var C$A = null;",
            "var D$A = null;",
            "A$z();"));
  }

  @GwtIncompatible // b/63595345
  public void testBug1949424() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setClosurePass(true);
    test(options, CLOSURE_BOILERPLATE + "goog.provide('FOO'); FOO.bar = 3;",
        CLOSURE_COMPILED + "var FOO$bar = 3;");
  }

  @GwtIncompatible // b/63595345
  public void testBug1949424_v2() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setClosurePass(true);
    test(
        options,
        LINE_JOINER.join(
            CLOSURE_BOILERPLATE,
            "goog.provide('FOO.BAR');",
            "FOO.BAR = 3;"),
        LINE_JOINER.join(
            CLOSURE_COMPILED,
            "var FOO$BAR = 3;"));
  }

  @GwtIncompatible // b/63595345
  public void testUnresolvedDefine() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setCheckTypes(true);
    DiagnosticType[] warnings = {
        ProcessDefines.INVALID_DEFINE_TYPE_ERROR, RhinoErrorReporter.TYPE_PARSE_ERROR};
    String[] input = { "var goog = {};" +
                       "goog.provide('foo.bar');" +
                       "/** @define{foo.bar} */ foo.bar = {};" };
    String[] output = { "var goog = {};" +
                        "var foo = {};" +
                        "/** @define{foo.bar} */ foo.bar = {};"};
    test(options, input, output, warnings);
  }

  public void testBug1956277() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setInlineVariables(true);
    test(
        options,
        "var CONST = {}; CONST.bar = null;"
        + "function f(url) { CONST.bar = url; }",
        "var CONST$bar = null; function f(url) { CONST$bar = url; }");
  }

  public void testBug1962380() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setInlineVariables(true);
    options.setGenerateExports(true);
    test(
        options,
        CLOSURE_BOILERPLATE + "/** @export */ goog.CONSTANT = 1;"
        + "var x = goog.CONSTANT;",
        "(function() {})('goog.CONSTANT', 1);");
  }

  /**
   * Tests that calls to goog.string.Const.from() with non-constant arguments
   * are detected with and without collapsed properties.
   */
  public void testBug22684459() {
    String source =
        ""
            + "var goog = {};"
            + "goog.string = {};"
            + "goog.string.Const = {};"
            + "goog.string.Const.from = function(x) {};"
            + "var x = window.document.location;"
            + "goog.string.Const.from(x);";

    // Without collapsed properties.
    CompilerOptions options = createCompilerOptions();
    test(options, source, ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);

    // With collapsed properties.
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(options, source, ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  /**
   * Tests that calls to goog.string.Const.from() with non-constant arguments
   * are detected with and without collapsed properties, even when
   * goog.string.Const.from has been aliased.
   */
  public void testBug22684459_aliased() {
    String source =
        ""
            + "var goog = {};"
            + "goog.string = {};"
            + "goog.string.Const = {};"
            + "goog.string.Const.from = function(x) {};"
            + "var mkConst = goog.string.Const.from;"
            + "var x = window.document.location;"
            + "mkConst(x);";

    // Without collapsed properties.
    CompilerOptions options = createCompilerOptions();
    test(options, source, ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);

    // With collapsed properties.
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(options, source, ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testBug31301233() {
    String source = LINE_JOINER.join(
        "function Foo() {",
        "  var x = window.document.location;",
        "  goog.string.Const.from(x);",
        "};");

    CompilerOptions options = createCompilerOptions();
    options.setSmartNameRemoval(true);
    options.setExtraSmartNameRemoval(true);
    test(options, source, ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

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

  public void testBug2410122() {
    CompilerOptions options = createCompilerOptions();
    options.setGenerateExports(true);
    options.setClosurePass(true);
    test(
        options,
        "var goog = {};"
        + "function F() {}"
        + "/** @export */ function G() { goog.base(this); } "
        + "goog.inherits(G, F);",
        "var goog = {};"
        + "function F() {}"
        + "function G() { F.call(this); } "
        + "goog.inherits(G, F); goog.exportSymbol('G', G);");
  }

  public void testBug18078936() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);

    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

    test(options,
        "var goog = {};" +
        "goog.inherits = function(a,b) {};" +
        "goog.defineClass = function(a,b) {};" +

         "/** @template T */\n" +
         "var ClassA = goog.defineClass(null, {\n" +
         "  constructor: function() {},\n" +
         "" +
         "  /** @param {T} x */\n" +
         "  fn: function(x) {}\n" +
         "});\n" +
         "" +
         "/** @extends {ClassA.<string>} */\n" +
         "var ClassB = goog.defineClass(ClassA, {\n" +
         "  constructor: function() {},\n" +
         "" +
         "  /** @override */\n" +
         "  fn: function(x) {}\n" +
         "});\n" +
         "" +
         "(new ClassB).fn(3);\n" +
         "",
         TypeValidator.TYPE_MISMATCH_WARNING);
  }

  // http://b/31448683
  public void testBug31448683() {
    CompilerOptions options = createCompilerOptions();
    WarningLevel.QUIET.setOptionsForWarningLevel(options);
    options.setInlineFunctions(Reach.ALL);
    test(
        options,
        LINE_JOINER.join(
            "function f() {",
            "  x = x || 1",
            "  var x;",
            "  console.log(x);",
            "}",
            "for (var _ in [1]) {",
            "  f();",
            "}"),
        LINE_JOINER.join(
            "for(var _ in[1]) {",
            "  {",
            "     var x$jscomp$inline_0 = void 0;",
            "     x$jscomp$inline_0 = x$jscomp$inline_0 || 1;",
            "     console.log(x$jscomp$inline_0);",
            "  }",
            "}"));
  }

  public void testBug32660578() {
    testSame(createCompilerOptions(), "function f() { alert(x); for (var x in []) {} }");
  }

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
            "for (var x of []);",  // Force injection of es6_runtime.js
            "var /** number */ y = window;"),
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  private void addPolymerExterns() {
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "polymer_externs.js",
            lines(
                "/** @return {function(new: PolymerElement)} */",
                "var Polymer = function(descriptor) {};",
                "",
                "/** @constructor @extends {HTMLElement} */",
                "var PolymerElement = function() {};",  // Polymer 1
                "",
                "/** @constructor @extends {HTMLElement} */",
                "Polymer.Element = function() {};",  // Polymer 2
                "")));
    externs = externsList.build();
  }

  public void testPolymer1() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    test(
        options,
        "var XFoo = Polymer({ is: 'x-foo' });",
        "var XFoo=function(){};XFoo=Polymer({is:'x-foo'})");
  }

  public void testConstPolymerElementNotAllowed() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    test(
        options,
        "const Foo = Polymer({ is: 'x-foo' });",
        PolymerPassErrors.POLYMER_INVALID_DECLARATION);
  }

  public void testPolymer2_nti() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setNewTypeInference(true);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.declaredGlobalExternsOnWindow = true;
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    Compiler compiler = compile(
        options,
        lines(
            "class XFoo extends PolymerElement {",
            "  get is() { return 'x-foo'; }",
            "  static get properties() { return { bar: Boolean, }; }",
            "}"));
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  public void testPolymer2_oti() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setNewTypeInference(false);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.declaredGlobalExternsOnWindow = true;
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    // TODO(b/74549003): Fix this so that OTI doesn't report an error.
    test(
        options,
        lines(
            "class XFoo extends Polymer.Element {",
            "  get is() { return 'x-foo'; }",
            "  static get properties() { return {}; }",
            "}"),
        // Bad type annotation. Unknown type PolymerElementProperties
        RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);
  }

  public void testPreservedForwardDeclare() {
    CompilerOptions options = createCompilerOptions();
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setClosurePass(true);
    options.setPreserveGoogProvidesAndRequires(true);

    compile(
        options,
        new String[] {
          LINE_JOINER.join(
              "var goog = {};",
              "goog.forwardDeclare = function(/** string */ t) {};",
              "goog.module = function(/** string */ t) {};"),
          "goog.module('fwd.declared.Type'); exports = class {}",
          LINE_JOINER.join(
              "goog.module('a.b.c');",
              "const Type = goog.forwardDeclare('fwd.declared.Type');",
              "",
              "/** @type {!fwd.declared.Type} */",
              "var y;"),
        });
    assertThat(lastCompiler.getResult().errors).isEmpty();
    assertThat(lastCompiler.getResult().warnings).isEmpty();
  }

  public void testForwardDeclaredTypeInTemplate() {
    CompilerOptions options = createCompilerOptions();
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setClosurePass(true);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);

    test(
        options,
        LINE_JOINER.join(
            "var goog = {};",
            "goog.forwardDeclare = function(/** string */ typeName) {};",
            "goog.forwardDeclare('fwd.declared.Type');",
            "",
            "/** @type {!fwd.declared.Type<string>} */",
            "var x;",
            "",
            "/** @type {!fwd.declared.Type<string, number>} */",
            "var y;"),
        "var goog={};goog.forwardDeclare=function(typeName){};var x;var y");
  }

  // http://blickly.github.io/closure-compiler-issues/#90
  public void testIssue90() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.setInlineVariables(true);
    options.setRemoveDeadCode(true);
    test(options, "var x; x && alert(1);", "");
  }

  @GwtIncompatible // b/63595345
  public void testClosurePassOff() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(false);
    testSame(options, "var goog = {}; goog.require = function(x) {}; goog.require('foo');");
    testSame(
        options,
        "var goog = {}; goog.getCssName = function(x) {};" +
        "goog.getCssName('foo');");
  }

  @GwtIncompatible // b/63595345
  public void testClosurePassOn() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    test(options, "var goog = {}; goog.require = function(x) {}; goog.require('foo');",
        ProcessClosurePrimitives.MISSING_PROVIDE_ERROR);
    test(
        options,
        "/** @define {boolean} */ var COMPILED = false;" +
        "var goog = {}; goog.getCssName = function(x) {};" +
        "goog.getCssName('foo');",
        "var COMPILED = true;" +
        "var goog = {}; goog.getCssName = function(x) {};" +
        "'foo';");
  }

  public void testChromePass_noTranspile() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setChromePass(true);
    options.setLanguage(LanguageMode.ECMASCRIPT_2017);
    test(
        options,
        "var cr = {}; cr.define('my.namespace', function() { class X {} return {X: X}; });",
        LINE_JOINER.join(
            "var cr = {},",
            "    my = my || {};",
            "my.namespace = my.namespace || {};",
            "cr.define('my.namespace', function() {",
            "  my.namespace.X = class {};",
            "  return { X: my.namespace.X };",
            "});"));
  }

  public void testChromePass_transpile() {
    CompilerOptions options = createCompilerOptions();
    options.setChromePass(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        "cr.define('my.namespace', function() { class X {} return {X: X}; });",
        LINE_JOINER.join(
            "var my = my || {};",
            "my.namespace = my.namespace || {};",
            "cr.define('my.namespace', function() {",
            "  /** @constructor */",
            "  my.namespace.X = function() {};",
            "  return { X: my.namespace.X };",
            "});"));
  }

  @GwtIncompatible("CheckMissingGetCssName is incompatible")
  public void testCssNameCheck() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckMissingGetCssNameLevel(CheckLevel.ERROR);
    options.setCheckMissingGetCssNameBlacklist("foo");
    test(options, "var x = 'foo';", CheckMissingGetCssName.MISSING_GETCSSNAME);
  }

  public void testBug2592659() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setCheckMissingGetCssNameLevel(CheckLevel.WARNING);
    options.setCheckMissingGetCssNameBlacklist("foo");
    test(
        options,
        "var goog = {};\n"
        + "/**\n"
        + " * @param {string} className\n"
        + " * @param {string=} opt_modifier\n"
        + " * @return {string}\n"
        + "*/\n"
        + "goog.getCssName = function(className, opt_modifier) {}\n"
        + "var x = goog.getCssName(123, 'a');",
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  @GwtIncompatible // b/63595345
  public void testTypedefBeforeOwner1() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    test(
        options,
        LINE_JOINER.join(
            "goog.provide('foo.Bar.Type');",
            "goog.provide('foo.Bar');",
            "/** @typedef {number} */ foo.Bar.Type;",
            "foo.Bar = function() {};"),
        LINE_JOINER.join(
            "var foo = {};",
            "foo.Bar.Type;",
            "foo.Bar = function() {};"));
  }

  @GwtIncompatible // b/63595345
  public void testTypedefBeforeOwner2() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(
        options,
        LINE_JOINER.join(
            "goog.provide('foo.Bar.Type');",
            "goog.provide('foo.Bar');",
            "/** @typedef {number} */ foo.Bar.Type;",
            "foo.Bar = function() {};"),
        LINE_JOINER.join(
            "var foo$Bar$Type;",
            "var foo$Bar = function() {};"));
  }

  @GwtIncompatible // b/63595345
  public void testTypedefProvides() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        lines(
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

  public void testExportedNames() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(
        options,
        "/** @define {boolean} */ var COMPILED = false;"
        + "var goog = {}; goog.exportSymbol('b', goog);",
        "var a = true; var c = {}; c.exportSymbol('b', c);");
    test(options,
         "/** @define {boolean} */ var COMPILED = false;" +
         "var goog = {}; goog.exportSymbol('a', goog);",
         "var b = true; var c = {}; c.exportSymbol('a', c);");
  }

  public void testCheckGlobalThisOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(true);
    options.setCheckGlobalThisLevel(CheckLevel.ERROR);
    test(options, "function f() { this.y = 3; }", CheckGlobalThis.GLOBAL_THIS);
  }

  public void testSusiciousCodeOff() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(false);
    options.setCheckGlobalThisLevel(CheckLevel.ERROR);
    test(options, "function f() { this.y = 3; }", CheckGlobalThis.GLOBAL_THIS);
  }

  public void testCheckGlobalThisOff() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(true);
    options.setCheckGlobalThisLevel(CheckLevel.OFF);
    testSame(options, "function f() { this.y = 3; }");
  }

  public void testCheckRequiresAndCheckProvidesOff() {
    testSame(createCompilerOptions(), new String[] {
      "/** @constructor */ function Foo() {}",
      "new Foo();"
    });
  }

  public void testCheckProvidesOn() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.ERROR);
    test(
        options,
        new String[] {"goog.require('x'); /** @constructor */ function Foo() {}", "new Foo();"},
        CheckProvides.MISSING_PROVIDE_WARNING);
  }

  public void testGenerateExportsOff() {
    testSame(createCompilerOptions(), "/** @export */ function f() {}");
  }

  public void testExportTestFunctionsOn1() {
    CompilerOptions options = createCompilerOptions();
    options.exportTestFunctions = true;
    test(options, "function testFoo() {}",
        "/** @export */ function testFoo() {}"
        + "goog.exportSymbol('testFoo', testFoo);");
  }

  @GwtIncompatible // b/63595345
  public void testExportTestFunctionsOn2() {
    CompilerOptions options = createCompilerOptions();
    options.setExportTestFunctions(true);
    options.setClosurePass(true);
    options.setRenamingPolicy(
        VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setGeneratePseudoNames(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(options,
        new String[] {
            LINE_JOINER.join(
                "var goog = {};",
                "goog.provide('goog.testing.testSuite');",
                "goog.testing.testSuite = function(a) {};"),
            LINE_JOINER.join(
                "goog.module('testing');",
                "var testSuite = goog.require('goog.testing.testSuite');",
                "testSuite({testMethod:function(){}});")
        },
        new String[] {
            "var $goog$testing$testSuite$$ = function($a$$) {};",
            "$goog$testing$testSuite$$({'testMethod':function(){}})"
        });
  }

  public void testAngularPassOff() {
    testSame(createCompilerOptions(),
        "/** @ngInject */ function f() {} " +
        "/** @ngInject */ function g(a){} " +
        "/** @ngInject */ var b = function f(a) {} ");
  }

  public void testAngularPassOn() {
    CompilerOptions options = createCompilerOptions();
    options.angularPass = true;
    test(options,
        "/** @ngInject */ function f() {} " +
        "/** @ngInject */ function g(a){} " +
        "/** @ngInject */ var b = function f(a, b, c) {} ",

        "function f() {} " +
        "function g(a) {} g['$inject']=['a'];" +
        "var b = function f(a, b, c) {}; b['$inject']=['a', 'b', 'c']");
  }

  public void testAngularPassOn_transpile() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.angularPass = true;
    test(options,
        "class C { /** @ngInject */ constructor(x) {} }",
        "var C = function(x){}; C['$inject'] = ['x'];");
  }

  public void testAngularPassOn_Es6Out() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.angularPass = true;
    test(options,
        "class C { /** @ngInject */ constructor(x) {} }",
        "class C { constructor(x){} } C['$inject'] = ['x'];");
  }

  public void testExportTestFunctionsOff() {
    testSame(createCompilerOptions(), "function testFoo() {}");
  }

  public void testExportTestFunctionsOn() {
    CompilerOptions options = createCompilerOptions();
    options.exportTestFunctions = true;
    test(options, "function testFoo() {}",
         "/** @export */ function testFoo() {}" +
         "goog.exportSymbol('testFoo', testFoo);");
  }

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

  public void testCheckSymbolsOff() {
    CompilerOptions options = createCompilerOptions();
    testSame(options, "x = 3;");
  }

  public void testCheckSymbolsOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);
    test(options, "x = 3;", VarCheck.UNDEFINED_VAR_ERROR);
  }

  public void testNoTypeWarningForDupExternNamespace() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    externs = ImmutableList.of(SourceFile.fromCode(
        "externs",
        LINE_JOINER.join(
            "/** @const */",
            "var ns = {};",
            "/** @type {number} */",
            "ns.prop1;",
            "/** @const */",
            "var ns = {};",
            "/** @type {number} */",
            "ns.prop2;")));
    testSame(options, "");
  }

  public void testCheckReferencesOff() {
    CompilerOptions options = createCompilerOptions();
    testSame(options, "x = 3; var x = 5;");
  }

  public void testCheckReferencesOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);
    test(options, "x = 3; var x = 5;", VariableReferenceCheck.EARLY_REFERENCE);
  }

  @GwtIncompatible // b/63595345
  public void testInferTypes() {
    CompilerOptions options = createCompilerOptions();
    options.inferTypes = true;
    options.setCheckTypes(false);
    options.setClosurePass(true);

    test(options,
        CLOSURE_BOILERPLATE + "goog.provide('Foo'); /** @enum */ Foo = {a: 3};",
        "var COMPILED=true;var goog={};goog.exportSymbol=function(){};var Foo={a:3}");
    assertThat(lastCompiler.getErrorManager().getTypedPercent()).isEqualTo(0.0);

    // This does not generate a warning.
    test(options, "/** @type {number} */ var n = window.name;",
        "var n = window.name;");
    assertThat(lastCompiler.getErrorManager().getTypedPercent()).isEqualTo(0.0);
  }

  public void testTypeCheckAndInference() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    test(
        options, "/** @type {number} */ var n = window.name;", TypeValidator.TYPE_MISMATCH_WARNING);
    assertThat(lastCompiler.getErrorManager().getTypedPercent()).isGreaterThan(0.0);
  }

  public void testTypeNameParser() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    test(options, "/** @type {n} */ var n = window.name;",
        RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);
  }

  // This tests that the TypedScopeCreator is memoized so that it only creates a
  // Scope object once for each scope. If, when type inference requests a scope,
  // it creates a new one, then multiple JSType objects end up getting created
  // for the same local type, and ambiguate will rename the last statement to
  // o.a(o.a, o.a), which is bad.
  public void testMemoizedTypedScopeCreator() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(
        options,
        "function someTest() {\n"
        + "  /** @constructor */\n"
        + "  function Foo() { this.instProp = 3; }\n"
        + "  Foo.prototype.protoProp = function(a, b) {};\n"
        + "  /** @constructor\n @extends Foo */\n"
        + "  function Bar() {}\n"
        + "  goog.inherits(Bar, Foo);\n"
        + "  var o = new Bar();\n"
        + "  o.protoProp(o.protoProp, o.instProp);\n"
        + "}",
        "function someTest() {\n"
        + "  function Foo() { this.b = 3; }\n"
        + "  function Bar() {}\n"
        + "  Foo.prototype.a = function(a, b) {};\n"
        + "  goog.c(Bar, Foo);\n"
        + "  var o = new Bar();\n"
        + "  o.a(o.a, o.b);\n"
        + "}");
  }

  public void testCheckTypes() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    test(options, "var x = x || {}; x.f = function() {}; x.f(3);", TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testRenamingOfTypedefPropertiesWithNTI() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setNewTypeInference(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);

    test(options,
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {",
            "  this.myprop = 123;",
            "}",
            "/** @typedef {{myprop: number}} */",
            "var MyType;"),
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {",
            "  this.a = 123;",
            "}",
            "/** @typedef {{myprop: number}} */",
            "var MyType;"));

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode("extraExterns", "/** @typedef {{myprop: number}} */ var MyType;"));
    externs = externsList.build();
    testSame(options,
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {",
            "  this.myprop = 123;",
            "}"));
  }

  public void testBothTypeCheckersRunNoDupWarning() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setNewTypeInference(true);

    test(
        options,
        LINE_JOINER.join(
            "/** @return {number} */",
            "function f() {",
            "  return 'asdf';",
            "}"),
        NewTypeInference.RETURN_NONDECLARED_TYPE);

    test(
        options,
        "/** @type {ASDF} */ var x;",
        GlobalTypeInfoCollector.UNRECOGNIZED_TYPE_NAME);

    options.setWarningLevel(
        DiagnosticGroups.REPORT_UNKNOWN_TYPES, CheckLevel.WARNING);
    test(
        options,
        "function f(/** ? */ x) { var y = x; }",
        NewTypeInference.UNKNOWN_EXPR_TYPE);
    options.setWarningLevel(
        DiagnosticGroups.REPORT_UNKNOWN_TYPES, CheckLevel.OFF);

    testSame(
        options,
        LINE_JOINER.join(
            "(function() {",
            "  /** @constructor */",
            "  var Boolean = function() {};",
            "})();"));
  }

  public void testSilenceUnknownTypeWarningFromOTI() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setCheckTypes(true);
    options.setNewTypeInference(true);

    test(options,
        LINE_JOINER.join(
            "/**",
            " * @param {T} x",
            " * @template T",
            " */",
            "function f(x) {",
            "  function g(y) {",
            "    var w = /** @type {T} */ (y);",
            "    var /** T */ z = x;",
            "  };",
            "}"),
        LINE_JOINER.join(
            "/**",
            " * @param {T} x",
            " * @template T",
            " */",
            "function f(x) {",
            "  function g(y) {",
            "    var w = y;",
            "    var /** T */ z = x;",
            "  };",
            "}"));
  }

  public void testNTIConstWarningsOverrideAccessControls() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    String js =
        LINE_JOINER.join(
            "/** @constructor */ function Foo(name) {}",
            "/** @const */ Foo.prop = 1;",
            "Foo.prop = 2;");
    test(options, js, CheckAccessControls.CONST_PROPERTY_REASSIGNED_VALUE);
    options.setNewTypeInference(true);
    test(options, js, NewTypeInference.CONST_PROPERTY_REASSIGNED);
  }

  public void testNTInoMaskTypeParseError() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setNewTypeInference(true);
    test(options, LINE_JOINER.join(
        "/** @param {(boolean|number} x */",
        "function f(x) {}"),
        RhinoErrorReporter.TYPE_PARSE_ERROR);
  }

  public void testLegacyCompileOverridesStrict() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setCheckTypes(true);
    options.addWarningsGuard(new StrictWarningsGuard());
    options.setLegacyCodeCompile(true);
    Compiler compiler = compile(options, "123();");
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).hasLength(1);
  }

  public void testLegacyCompileOverridesExplicitPromotionToError() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setCheckTypes(true);
    options.addWarningsGuard(new DiagnosticGroupWarningsGuard(
        DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR));
    options.setLegacyCodeCompile(true);
    Compiler compiler = compile(options, "123();");
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).hasLength(1);
  }

  public void testLegacyCompileTurnsOffDisambiguateProperties() {
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setLegacyCodeCompile(true);
    testSame(options,
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {",
            "  this.p = 123;",
            "}",
            "/** @constructor */",
            "function Bar() {",
            "  this.p = 234;",
            "}"));
  }

  public void testReplaceCssNames() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setGatherCssNames(true);
    test(
        options,
        "/** @define {boolean} */\n"
        + "var COMPILED = false;\n"
        + "goog.setCssNameMapping({'foo':'bar'});\n"
        + "function getCss() {\n"
        + "  return goog.getCssName('foo');\n"
        + "}",
        "var COMPILED = true;\n"
        + "function getCss() {\n"
        + "  return 'bar';"
        + "}");
    assertEquals(
        ImmutableMap.of("foo", 1), lastCompiler.getCssNames());
  }

  public void testReplaceIdGeneratorsTest() {
    CompilerOptions options = createCompilerOptions();
    options.replaceIdGenerators = true;

    options.setIdGenerators(ImmutableMap.<String, RenamingMap>of(
        "xid", new RenamingMap() {
      @Override
      public String get(String value) {
        return ":" + value + ":";
      }
    }));

    test(options, "/** @idGenerator {mapped} */"
         + "var xid = function() {};\n"
         + "function f() {\n"
         + "  return xid('foo');\n"
         + "}",
         "var xid = function() {};\n"
         + "function f() {\n"
         + "  return ':foo:';\n"
         + "}");
  }

  public void testRemoveClosureAsserts() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    testSame(
        options,
        "var goog = {};"
        + "goog.asserts.assert(goog);");
    options.removeClosureAsserts = true;
    test(options,
        "var goog = {};"
        + "goog.asserts.assert(goog);",
        "var goog = {};");
  }

  public void testRemoveClosureAsserts2() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setNewTypeInference(true);
    options.setRemoveClosureAsserts(true);
    options.setDisambiguateProperties(true);

    String js = LINE_JOINER.join(
        "/** @const */ var goog = {};",
        "goog.asserts;",
        "goog.asserts.assertInstanceof;",
        "/** @constructor */",
        "function Bar() {",
        "  this.a = 1;",
        "}",
        "/** @constructor */",
        "function Baz() {",
        "  this.a = 1;",
        "}",
        "function f(x) {",
        "  return (goog.asserts.assertInstanceof(x, Bar)).a;",
        "}");

    String output = LINE_JOINER.join(
        "/** @const */ var goog = {};",
        "goog.asserts;",
        "goog.asserts.assertInstanceof;",
        "/** @constructor */",
        "function Bar() {",
        "  this.Bar$a = 1;",
        "}",
        "/** @constructor */",
        "function Baz() {",
        "  this.Baz$a = 1;",
        "}",
        "function f(x) {",
        "  return x.Bar$a;",
        "}");

    test(options, js, output);
  }

  public void testDeprecation() {
    String code = "/** @deprecated */ function f() { } function g() { f(); }";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.DEPRECATED, CheckLevel.ERROR);
    testSame(options, code);

    options.setCheckTypes(true);
    test(options, code, CheckAccessControls.DEPRECATED_NAME);
  }

  public void testVisibility() {
    String[] code = {
        "/** @private */ function f() { }",
        "function g() { f(); }"
    };

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.VISIBILITY, CheckLevel.ERROR);
    testSame(options, code);

    options.setCheckTypes(true);
    test(options, code, CheckAccessControls.BAD_PRIVATE_GLOBAL_ACCESS);
  }

  public void testUnreachableCode() {
    String code = "function f() { return \n x(); }";

    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.ERROR);
    test(options, code, CheckUnreachableCode.UNREACHABLE_CODE);
  }

  public void testMissingReturn() {
    String code =
        "/** @return {number} */ function f() { if (f) { return 3; } }";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.MISSING_RETURN, CheckLevel.ERROR);
    testSame(options, code);

    options.setCheckTypes(true);
    test(options, code, CheckMissingReturn.MISSING_RETURN_STATEMENT);
  }

  public void testIdGenerators() {
    String code =  "function f() {} f('id');";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setIdGenerators(ImmutableSet.of("f"));
    test(options, code, "function f() {} 'a';");
  }

  public void testOptimizeArgumentsArray() {
    String code =  "function f() { return arguments[0]; }";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setOptimizeArgumentsArray(true);
    String argName = "JSCompiler_OptimizeArgumentsArray_p0";
    test(options, code,
         "function f(" + argName + ") { return " + argName + "; }");
  }

  public void testOptimizeParameters() {
    String code = "function f(a) {} f(true);";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setOptimizeCalls(true);
    test(options, code, "function f() { var a = true;} f();");
  }

  public void testOptimizeReturns() {
    String code = "var x; function f() { return x; } f();";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setOptimizeCalls(true);
    test(options, code, "var x; function f() {x; return;} f();");
  }

  public void testRemoveAbstractMethods() {
    String code = CLOSURE_BOILERPLATE +
        "var x = {}; x.foo = goog.abstractMethod; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setClosurePass(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setRemoveAbstractMethods(true);
    test(options, code, CLOSURE_COMPILED + " var x$bar = 3;");
  }

  public void testGoogDefine1() {
    String code = CLOSURE_BOILERPLATE +
        "/** @define {boolean} */ goog.define('FLAG', true);";

    CompilerOptions options = createCompilerOptions();

    options.setClosurePass(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setDefineToBooleanLiteral("FLAG", false);

    test(options, code, CLOSURE_COMPILED + " var FLAG = false;");
  }

  @GwtIncompatible // b/63595345
  public void testGoogDefine2() {
    String code = CLOSURE_BOILERPLATE +
        "goog.provide('ns');" +
        "/** @define {boolean} */ goog.define('ns.FLAG', true);";

    CompilerOptions options = createCompilerOptions();

    options.setClosurePass(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setDefineToBooleanLiteral("ns.FLAG", false);
    test(options, code, CLOSURE_COMPILED + "var ns$FLAG = false;");
  }

  public void testCollapseProperties1() {
    String code =
        "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(options, code, "var x$FOO = 5; var x$bar = 3;");
  }

  public void testCollapseProperties2() {
    String code =
        "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.collapseObjectLiterals = true;
    test(options, code, "var x$FOO = 5; var x$bar = 3;");
  }

  public void testCollapseObjectLiteral1() {
    // Verify collapseObjectLiterals does nothing in global scope
    String code = "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.collapseObjectLiterals = true;
    testSame(options, code);
  }

  public void testCollapseObjectLiteral2() {
    String code = "function f() {var x = {}; x.FOO = 5; x.bar = 3;}";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.collapseObjectLiterals = true;
    test(
        options,
        code,
        LINE_JOINER.join(
            "function f() {",
            "  var JSCompiler_object_inline_FOO_0 = 5;",
            "  var JSCompiler_object_inline_bar_1 = 3;",
            "}"));
  }

  public void testDisambiguateProperties() {
    String code =
        "/** @constructor */ function Foo(){} Foo.prototype.bar = 3;" +
        "/** @constructor */ function Baz(){} Baz.prototype.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setDisambiguateProperties(true);
    options.setCheckTypes(true);
    test(options, code,
        "function Foo(){} Foo.prototype.Foo_prototype$bar = 3;"
        + "function Baz(){} Baz.prototype.Baz_prototype$bar = 3;");
  }

  // When closure-code-removal runs before disambiguate-properties, make sure
  // that removing abstract methods doesn't mess up disambiguation.
  public void testDisambiguateProperties2() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setRemoveDeadCode(true);
    options.setRemoveAbstractMethods(true);
    test(options,
        LINE_JOINER.join(
            "/** @const */ var goog = {};",
            "goog.abstractMethod = function() {};",
            "/** @interface */ function I() {}",
            "I.prototype.a = function(x) {};",
            "/** @constructor @implements {I} */ function Foo() {}",
            "/** @override */ Foo.prototype.a = goog.abstractMethod;",
            "/** @constructor @extends Foo */ function Bar() {}",
            "/** @override */ Bar.prototype.a = function(x) {};"),
        LINE_JOINER.join(
            "var goog={};",
            "goog.abstractMethod = function() {};",
            "function I(){}",
            "I.prototype.a=function(x){};",
            "function Foo(){}",
            "function Bar(){}",
            "Bar.prototype.a=function(x){};"));
  }

  public void testDisambiguatePropertiesWithNtiNoCrash() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setNewTypeInference(true);
    options.setDisambiguateProperties(true);
    this.externs = ImmutableList.of(SourceFile.fromCode(
        "externs",
        LINE_JOINER.join(
            "/** @constructor */",
            "function Object() {}",
            "/** @constructor */",
            "function Function() {}",
            "/** @const */",
            "var ns = {};",
            "ns.subns = {};",
            "ns.subns.prototype.f = function() {};")));

    testSame(options, "");
  }

  public void testDisambiguatePropertiesWithNtiNoCrash2() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setNewTypeInference(true);
    options.setDisambiguateProperties(true);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);

    testSame(options,
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {",
            "  /** @export */",
            "  this.prop = 123;",
            "}"));
  }

  // Test that NTI doesn't invalidate types involved in type mismatches with type variables.
  // This behavior is generally unsafe (as in the code tested here), but also allows more
  // disambiguation.
  public void testNtiTypeVariableErrorsDontBlockDisambiguation() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setNewTypeInference(true);
    options.setDisambiguateProperties(true);

    String js = LINE_JOINER.join(
        "/** @constructor */",
        "function Foo() {",
        "  this.a = 1;",
        "}",
        "/** @constructor */",
        "function Bar() {",
        "  this.a = 2;",
        "}",
        "/**",
        " * @template T",
        " * @return {T}",
        " */",
        "function f() {",
        "  return new Foo;",
        "}",
        "var /** !Bar */ x = f();",
        "var y = x.a;");

    String output = LINE_JOINER.join(
        "/** @constructor */",
        "function Foo() {",
        "  this.Foo$a = 1;",
        "}",
        "/** @constructor */",
        "function Bar() {",
        "  this.Bar$a = 2;",
        "}",
        "/**",
        " * @template T",
        " * @return {T}",
        " */",
        "function f() {",
        "  return new Foo;",
        "}",
        "var /** !Bar */ x = f();",
        "var y = x.Bar$a;");

    test(options, js, output, NewTypeInference.RETURN_NONDECLARED_TYPE);
  }

  public void testNtiTypeVariableErrorsDontBlockDisambiguation2() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setNewTypeInference(true);
    options.setDisambiguateProperties(true);

    String js = LINE_JOINER.join(
        "/**",
        " * @constructor",
        " * @template T",
        " */",
        "function Foo() {",
        "  /** @type {T} */",
        "  this.p;",
        "}",
        "/** @return {!Bar} */",
        "Foo.prototype.f = function() {",
        "  return /** @type {!Bar} */ (this.p);",
        "};",
        "/** @constructor */",
        "function Bar() {",
        "  this.a = 1;",
        "}",
        "/** @constructor */",
        "function Baz() {",
        "  this.a = 1;",
        "}");

    String output = LINE_JOINER.join(
        "/**",
        " * @constructor",
        " * @template T",
        " */",
        "function Foo() {",
        "  /** @type {T} */",
        "  this.p;",
        "}",
        "/** @return {!Bar} */",
        "Foo.prototype.f = function() {",
        "  return this.p;",
        "};",
        "/** @constructor */",
        "function Bar() {",
        "  this.Bar$a = 1;",
        "}",
        "/** @constructor */",
        "function Baz() {",
        "  this.Baz$a = 1;",
        "}");

    test(options, js, output);
  }

  public void testTurnOffConstChecksOfCheckAccessControlsWithNtiOn() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setNewTypeInference(true);

    testSame(
        options,
        LINE_JOINER.join(
            "/** @constructor  */",
            "var Foo = function() {};",
            "/** @const {number|undefined} */",
            "Foo.prototype.p;",
            "/**",
            " * @constructor",
            " * @extends {Foo}",
            " */",
            "var Bar = function() {};",
            "/** @const */",
            "Bar.prototype.p = 123;",
            "/**",
            " * @constructor",
            " * @extends {Foo}",
            " */",
            "var Baz = function() {};",
            "Baz.prototype.p = 345;"));
  }

  public void testMarkPureCalls() {
    String testCode = "function foo() {} foo();";
    CompilerOptions options = createCompilerOptions();
    options.setRemoveDeadCode(true);

    testSame(options, testCode);

    options.setComputeFunctionSideEffects(true);
    test(options, testCode, "function foo() {}");
  }

  public void testMarkNoSideEffects() {
    String testCode = "noSideEffects();";
    CompilerOptions options = createCompilerOptions();
    options.setRemoveDeadCode(true);

    testSame(options, testCode);

    options.markNoSideEffectCalls = true;
    test(options, testCode, "");
  }

  public void testExtraAnnotationNames() {
    CompilerOptions options = createCompilerOptions();
    options.setExtraAnnotationNames(ImmutableSet.of("TagA", "TagB"));
    test(
        options,
        "/** @TagA */ var f = new Foo(); /** @TagB */ f.bar();",
        "var f = new Foo(); f.bar();");
  }

  public void testDevirtualizePrototypeMethods() {
    CompilerOptions options = createCompilerOptions();
    options.setDevirtualizePrototypeMethods(true);
    test(
        options,
        "/** @constructor */ var Foo = function() {}; "
        + "Foo.prototype.bar = function() {};"
        + "(new Foo()).bar();",
        "var Foo = function() {};"
        + "var JSCompiler_StaticMethods_bar = "
        + "    function(JSCompiler_StaticMethods_bar$self) {};"
        + "JSCompiler_StaticMethods_bar(new Foo());");
  }

  public void testCheckConsts() {
    CompilerOptions options = createCompilerOptions();
    options.setInlineConstantVars(true);
    test(options, "var FOO = true; FOO = false", ConstCheck.CONST_REASSIGNED_VALUE_ERROR);
  }

  public void testAllChecksOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.ERROR);
    options.setGenerateExports(true);
    options.exportTestFunctions = true;
    options.setClosurePass(true);
    options.setCheckMissingGetCssNameLevel(CheckLevel.ERROR);
    options.setCheckMissingGetCssNameBlacklist("goog");
    options.syntheticBlockStartMarker = "synStart";
    options.syntheticBlockEndMarker = "synEnd";
    options.setCheckSymbols(true);
    options.processObjectPropertyString = true;
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(options, CLOSURE_BOILERPLATE, CLOSURE_COMPILED);
  }

  public void testTypeCheckingWithSyntheticBlocks() {
    CompilerOptions options = createCompilerOptions();
    options.syntheticBlockStartMarker = "synStart";
    options.syntheticBlockEndMarker = "synEnd";
    options.setCheckTypes(true);

    // We used to have a bug where the CFG drew an
    // edge straight from synStart to f(progress).
    // If that happens, then progress will get type {number|undefined}.
    testSame(
        options,
        "/** @param {number} x */ function f(x) {}" +
        "function g() {" +
        " synStart('foo');" +
        " var progress = 1;" +
        " f(progress);" +
        " synEnd('foo');" +
        "}");
  }

  public void testCompilerDoesNotBlowUpIfUndefinedSymbols() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);

    // Disable the undefined variable check.
    options.setWarningLevel(
        DiagnosticGroup.forType(VarCheck.UNDEFINED_VAR_ERROR),
        CheckLevel.OFF);

    // The compiler used to throw an IllegalStateException on this.
    testSame(options, "var x = {foo: y};");
  }

  // Make sure that if we change variables which are constant to have
  // $$constant appended to their names, we remove that tag before
  // we finish.
  public void testConstantTagsMustAlwaysBeRemoved() {
    CompilerOptions options = createCompilerOptions();

    options.setVariableRenaming(VariableRenamingPolicy.LOCAL);
    String originalText =
        "var G_GEO_UNKNOWN_ADDRESS=1;\n"
        + "function foo() {"
        + "  var localVar = 2;\n"
        + "  if (G_GEO_UNKNOWN_ADDRESS == localVar) {\n"
        + "    alert('A'); }}";
    String expectedText = "var G_GEO_UNKNOWN_ADDRESS=1;" +
        "function foo(){var a=2;if(G_GEO_UNKNOWN_ADDRESS==a){alert('A')}}";

    test(options, originalText, expectedText);
  }

  @GwtIncompatible // b/63595345
  public void testClosurePassPreservesJsDoc() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setClosurePass(true);

    test(
        options,
        LINE_JOINER.join(
            CLOSURE_BOILERPLATE,
            "goog.provide('Foo');",
            "/** @constructor */ Foo = function() {};",
            "var x = new Foo();"),
        LINE_JOINER.join(
            "var COMPILED=true;",
            "var goog = {};",
            "goog.exportSymbol=function() {};",
            "var Foo = function() {};",
            "var x = new Foo;"));
    test(options,
        CLOSURE_BOILERPLATE + "goog.provide('Foo'); /** @enum */ Foo = {a: 3};",
        "var COMPILED=true;var goog={};goog.exportSymbol=function(){};var Foo={a:3}");
  }

  @GwtIncompatible // b/63595345
  public void testProvidedNamespaceIsConst() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setInlineConstantVars(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(
        options,
        LINE_JOINER.join(
            "var goog = {};",
            "goog.provide('foo');",
            "function f() { foo = {};}"),
        LINE_JOINER.join(
            "var foo = {};",
            "function f() { foo = {}; }"),
        ConstCheck.CONST_REASSIGNED_VALUE_ERROR);
  }

  @GwtIncompatible // b/63595345
  public void testProvidedNamespaceIsConst3() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setInlineConstantVars(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(
        options,
        "var goog = {}; "
        + "goog.provide('foo.bar'); goog.provide('foo.bar.baz'); "
        + "/** @constructor */ foo.bar = function() {};"
        + "/** @constructor */ foo.bar.baz = function() {};",
        "var foo$bar = function(){};"
        + "var foo$bar$baz = function(){};");
  }

  @GwtIncompatible // b/63595345
  public void testProvidedNamespaceIsConst4() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setInlineConstantVars(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(
        options,
        "var goog = {}; goog.provide('foo.Bar'); "
        + "var foo = {}; foo.Bar = {};",
        "var foo = {}; foo = {}; foo.Bar = {};");
  }

  @GwtIncompatible // b/63595345
  public void testProvidedNamespaceIsConst5() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setInlineConstantVars(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(
        options,
        "var goog = {}; goog.provide('foo.Bar'); "
        + "foo = {}; foo.Bar = {};",
        "var foo = {}; foo = {}; foo.Bar = {};");
  }

  public void testAtDefineReassigned() {
    test(createCompilerOptions(),
         "/** @define {boolean} */ var HI = true; HI = false;",
         ConstCheck.CONST_REASSIGNED_VALUE_ERROR);
  }

  public void testProcessDefinesAdditionalReplacements() {
    CompilerOptions options = createCompilerOptions();
    options.setDefineToBooleanLiteral("HI", false);
    test(options,
         "/** @define {boolean} */ var HI = true;",
         "var HI = false;");
  }

  @GwtIncompatible("com.google.javascript.jscomp.ReplaceMessages is incompatible")
  public void testReplaceMessages() {
    CompilerOptions options = createCompilerOptions();
    String prefix = "var goog = {}; goog.getMsg = function() {};";
    testSame(options, prefix + "var MSG_HI = goog.getMsg('hi');");

    options.setMessageBundle(new EmptyMessageBundle());
    test(options, prefix + "/** @desc xyz */ var MSG_HI = goog.getMsg('hi');",
        prefix + "var MSG_HI = 'hi';");
  }

  public void testCheckGlobalNames() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckGlobalNamesLevel(CheckLevel.ERROR);
    test(options, "var x = {}; var y = x.z;", CheckGlobalNames.UNDEFINED_NAME_WARNING);
  }

  public void testInlineGetters() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "function Foo() {} Foo.prototype.bar = function() { return 3; };" +
        "var x = new Foo(); x.bar();";

    testSame(options, code);
    options.inlineGetters = true;

    test(options, code,
        "function Foo() {} Foo.prototype.bar = function() { return 3 };"
        + "var x = new Foo(); 3;");
  }

  public void testInlineGettersWithAmbiguate() {
    CompilerOptions options = createCompilerOptions();

    String code =
        "/** @constructor */" +
        "function Foo() {}" +
        "/** @type {number} */ Foo.prototype.field;" +
        "Foo.prototype.getField = function() { return this.field; };" +
        "/** @constructor */" +
        "function Bar() {}" +
        "/** @type {string} */ Bar.prototype.field;" +
        "Bar.prototype.getField = function() { return this.field; };" +
        "new Foo().getField();" +
        "new Bar().getField();";

    testSame(options, code);

    options.inlineGetters = true;

    test(options, code,
        "function Foo() {}"
        + "Foo.prototype.field;"
        + "Foo.prototype.getField = function() { return this.field; };"
        + "function Bar() {}"
        + "Bar.prototype.field;"
        + "Bar.prototype.getField = function() { return this.field; };"
        + "new Foo().field;"
        + "new Bar().field;");

    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);

    // Propagating the wrong type information may cause ambiguate properties
    // to generate bad code.
    testSame(options, code);
  }

  public void testInlineVariables() {
    CompilerOptions options = createCompilerOptions();
    String code = "function foo() {} var x = 3; foo(x);";
    testSame(options, code);

    options.setInlineVariables(true);
    test(options, code, "(function foo() {})(3);");
  }

  public void testInlineConstants() {
    CompilerOptions options = createCompilerOptions();
    String code = "function foo() {} var x = 3; foo(x); var YYY = 4; foo(YYY);";
    testSame(options, code);

    options.setInlineConstantVars(true);
    test(options, code, "function foo() {} foo(3); foo(4);");
  }

  public void testMinimizeExits() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "function f() {" +
        "  if (window.foo) return; window.h(); " +
        "}";
    testSame(options, code);

    options.setFoldConstants(true);
    test(options, code,
        "function f() {"
        + "  window.foo || window.h(); "
        + "}");
  }

  public void testFoldConstants() {
    CompilerOptions options = createCompilerOptions();
    String code = "if (true) { window.foo(); }";
    testSame(options, code);

    options.setFoldConstants(true);
    test(options, code, "window.foo();");
  }

  public void testRemoveUnreachableCode() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    String code = "function f() { return; f(); }";
    testSame(options, code);

    options.setRemoveDeadCode(true);
    test(options, code, "function f() {}");
  }

  public void testRemoveUnusedPrototypeProperties1() {
    CompilerOptions options = createCompilerOptions();
    String code = "function Foo() {} " +
        "Foo.prototype.bar = function() { return new Foo(); };";
    testSame(options, code);

    options.setRemoveUnusedPrototypeProperties(true);
    test(options, code, "function Foo() {}");
  }

  public void testRemoveUnusedPrototypeProperties2() {
    CompilerOptions options = createCompilerOptions();
    String code = "function Foo() {} " +
        "Foo.prototype.bar = function() { return new Foo(); };" +
        "function f(x) { x.bar(); }";
    testSame(options, code);

    options.setRemoveUnusedPrototypeProperties(true);
    testSame(options, code);

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "");
  }

  public void testRemoveUnusedClass() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    String code =
        lines(
            "/** @constructor */ function Foo() { this.bar(); }",
            "Foo.prototype.bar = function() { return new Foo(); };");
    test(options, code, "");
  }

  public void testClassWithGettersIsRemoved() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "class Foo { get xx() {}; set yy(v) {}; static get init() {}; static set prop(v) {} }";

    // TODO(radokirov): The compiler should be removing statics too, but in this case they are
    // kept. A similar unittest in RemoveUnusedCodeClassPropertiesTest removes everything.
    // Investigate why are they kept when ran together with other passes.
    String expected =
        LINE_JOINER.join(
        "('undefined'!=typeof window&&window===this?this:'undefined'!=typeof ",
        "global&&null!=global?global:this).",
        "c.defineProperties(function() {},",
        "{e:{a:!0,b:!0,d:function(){}},",  // renamed from init
        "f:{a:!0,b:!0,g:function(){}}})");  // renamed from prop

    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setExtraSmartNameRemoval(false);
    test(options, code, expected);
  }

  public void testExportOnClassGetter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.declaredGlobalExternsOnWindow = true;
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        "class C { /** @export @return {string} */ get exportedName() {} }; (new C).exportedName;",
        EMPTY_JOINER.join(
            "function a(){}('undefined'!=typeof window&&",
            "window===this?this:'undefined'!=typeof global&&",
            "null!=global?global:this).Object.defineProperties(",
            "a.prototype,{exportedName:{a:!0,b:!0,get:function(){}}});(new a).exportedName"));
  }

  public void testExportOnClassSetter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.declaredGlobalExternsOnWindow = true;
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        "class C { /** @export @return {string} */ set exportedName(x) {} }; (new C).exportedName;",
        EMPTY_JOINER.join(
            "function a(){}('undefined'!=typeof window&&",
            "window===this?this:'undefined'!=typeof global&&",
            "null!=global?global:this).Object.defineProperties(",
            "a.prototype,{exportedName:{a:!0,b:!0,set:function(){}}});(new a).exportedName"));
  }

  public void testExportOnStaticGetter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.declaredGlobalExternsOnWindow = true;
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
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
        EMPTY_JOINER.join(
            // TODO(tbreisacher): Find out why C is renamed to a despite the @export annotation.
            "function a(){}('undefined'!=typeof window&&window===this?",
            "this:'undefined'!=typeof global&&null!=global?",
            "global:this).Object.defineProperties(a,",
            "{exportedName:{a:!0,b:!0,get:function(){}}});alert(a.exportedName)"));
  }

  public void testExportOnStaticSetter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.declaredGlobalExternsOnWindow = true;
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        LINE_JOINER.join(
            "var goog = {};",
            "goog.exportSymbol = function(path, symbol) {};",
            "",
            "/** @export */",
            "class C {",
            "  /** @export @param {number} x */ static set exportedName(x) {}",
            "};",
            "C.exportedName = 0;"),
        EMPTY_JOINER.join(
            // TODO(tbreisacher): Find out why C is removed despite the @export annotation.
            "('undefined'!=typeof window&&window===this?",
            "this:'undefined'!=typeof global&&null!=global?global:this)",
            ".Object.defineProperties(function(){},{exportedName:{a:!0,b:!0,set:function(){}}})"));
  }

  public void testSmartNamePassBug11163486() {
    CompilerOptions options = createCompilerOptions();

    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setRemoveDeadCode(true);
    options.setRemoveUnusedVariables(Reach.ALL);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setSmartNameRemoval(true);
    options.extraSmartNameRemoval = true;
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);

    String code = "/** @constructor */ function A() {} " +
        "A.prototype.foo = function() { " +
        "  window.console.log('A'); " +
        "}; " +
        "/** @constructor */ function B() {} " +
        "B.prototype.foo = function() { " +
        "  window.console.log('B'); " +
        "};" +
        "window['main'] = function() { " +
        "  var a = window['a'] = new A; " +
        "  a.foo(); " +
        "  window['b'] = new B; " +
        "}; " +
        "function notCalled() { " +
        "  var something = {}; " +
        "  something.foo(); " +
        "}";

    String expected = "function A() {} " +
        "A.prototype.A_prototype$foo = function() { " +
        "  window.console.log('A'); " +
        "}; " +
        "function B() {} " +
        "window['main'] = function() { " +
        "  var a = window['a'] = new A; " +
        "  a.A_prototype$foo(); " +
        "  window['b'] = new B; " +
        "}";

    test(options, code, expected);
  }

  public void testDeadCodeHasNoDisambiguationSideEffects() {
    // This test case asserts that unreachable code does not
    // confuse the disambigation process and type inferencing.
    CompilerOptions options = createCompilerOptions();

    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setRemoveDeadCode(true);
    options.setRemoveUnusedVariables(Reach.ALL);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setSmartNameRemoval(true);
    options.extraSmartNameRemoval = true;
    options.setFoldConstants(true);
    options.setInlineVariables(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);

    String code =
        "/** @constructor */ function A() {} "
        + "A.prototype.always = function() { "
        + "  window.console.log('AA'); "
        + "}; "
        + "A.prototype.sometimes = function() { "
        + "  window.console.log('SA'); "
        + "}; "
        + "/** @constructor */ function B() {} "
        + "B.prototype.always = function() { "
        + "  window.console.log('AB'); "
        + "};"
        + "B.prototype.sometimes = function() { "
        + "  window.console.log('SB'); "
        + "};"
        + "/** @constructor @struct @template T */ function C() {} "
        + "/** @param {!T} x */ C.prototype.touch = function(x) { "
        + "  return x.sometimes(); "
        + "}; "
        + "window['main'] = function() { "
        + "  var a = window['a'] = new A; "
        + "  a.always(); "
        + "  a.sometimes(); "
        + "  var b = window['b'] = new B; "
        + "  b.always(); "
        + "};"
        + "function notCalled() { "
        + "  var something = {}; "
        + "  something.always(); "
        + "  var c = new C; "
        + "  c.touch(something);"
        + "}";

    // B.prototype.sometimes should be stripped out, as it is not used, and the
    // type ambiguity in function notCalled is unreachable.
    String expected = "function A() {} " +
        "A.prototype.A_prototype$always = function() { " +
        "  window.console.log('AA'); " +
        "}; " +
        "A.prototype.A_prototype$sometimes = function(){ " +
        "  window.console.log('SA'); " +
        "}; " +
        "function B() {} " +
        "B.prototype.B_prototype$always=function(){ " +
        "  window.console.log('AB'); " +
        "};" +
        "window['main'] = function() { " +
        "  var a = window['a'] = new A; " +
        "  a.A_prototype$always(); " +
        "  a.A_prototype$sometimes(); " +
        "  (window['b'] = new B).B_prototype$always(); " +
        "}";


    test(options, code, expected);

  }

  public void testQMarkTIsNullable() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);

    String code = LINE_JOINER.join(
        "/** @constructor @template T */",
        "function F() {}",
        "",
        "/** @return {?T} */",
        "F.prototype.foo = function() {",
        "  return null;",
        "}",
        "",
        "/** @type {F<string>} */",
        "var f = new F;",
        "/** @type {string} */",
        "var s = f.foo(); // Type error: f.foo() has type {?string}.");

    test(options, code, TYPE_MISMATCH_WARNING);
  }

  public void testTIsNotNullable() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);

    String code = LINE_JOINER.join("/** @constructor @template T */",
        "function F() {}",
        "",
        "/** @param {T} t */",
        "F.prototype.foo = function(t) {",
        "}",
        "",
        "/** @type {F<string>} */",
        "var f = new F;",
        "/** @type {?string} */",
        "var s = null;",
        "f.foo(s); // Type error: f.foo() takes a {string}, not a {?string}");

    test(options, code, TYPE_MISMATCH_WARNING);
  }

  public void testDeadAssignmentsElimination() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() { var x = 3; 4; x = 5; return x; } f(); ";
    testSame(options, code);

    options.setDeadAssignmentElimination(true);
    testSame(options, code);

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "function f() { 3; 4; var x = 5; return x; } f();");
  }

  public void testPreservesCastInformation() {
    // Only set the suffix instead of both prefix and suffix, because J2CL pass
    // looks for that exact suffix, and IntegrationTestCase adds an input
    // id number between prefix and suffix.
    inputFileNameSuffix = "vmbootstrap/Arrays.impl.java.js";
    String code = LINE_JOINER.join(
        "/** @constructor */",
        "var Arrays = function() {};",
        "Arrays.$create = function() { return {}; }",
        "/** @constructor */",
        "function Foo() { this.myprop = 1; }",
        "/** @constructor */",
        "function Bar() { this.myprop = 2; }",
        "var x = /** @type {!Foo} */ (Arrays.$create()).myprop;");

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);

    test(options, code,
        LINE_JOINER.join(
            "/** @constructor */",
            "var Arrays = function() {};",
            "Arrays.$create = function() { return {}; }",
            "/** @constructor */",
            "function Foo() { this.Foo$myprop = 1; }",
            "/** @constructor */",
            "function Bar() { this.Bar$myprop = 2; }",
            "var x = {}.Foo$myprop;"));
  }

  public void testInliningLocalVarsPreservesCasts() {
    String code = LINE_JOINER.join(
        "/** @constructor */",
        "function Foo() { this.myprop = 1; }",
        "/** @constructor */",
        "function Bar() { this.myprop = 2; }",
        "/** @return {Object} */",
        "function getSomething() {",
        "  var x = new Bar();",
        "  return new Foo();",
        "}",
        "(function someMethod() {",
        "  var x = getSomething();",
        "  var y = /** @type {Foo} */ (x).myprop;",
        "  return 1 != y;",
        "})()");

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setCheckTypes(true);
    options.setSmartNameRemoval(true);
    options.setFoldConstants(true);
    options.setExtraSmartNameRemoval(true);
    options.setInlineVariables(true);
    options.setDisambiguateProperties(true);

    // Verify that property disambiguation continues to work after the local
    // var and cast have been removed by inlining.
    test(options, code,
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() { this.Foo$myprop = 1; }",
            "/** @constructor */",
            "function Bar() { this.Bar$myprop = 2; }",
            "/** @return {Object} */",
            "function getSomething() {",
            "  var x = new Bar();",
            "  return new Foo();",
            "}",
            "(function someMethod() {",
            "  return 1 != getSomething().Foo$myprop;",
            "})()"));
  }

  /**
   * Tests that inlining of local variables doesn't destroy type information
   * when the cast is from a non-nullable type to a nullable type.
   */
  public void testInliningLocalVarsPreservesCastsNullable() {
    String code = LINE_JOINER.join(
        "/** @constructor */",
        "function Foo() { this.myprop = 1; }",
        "/** @constructor */",
        "function Bar() { this.myprop = 2; }",
        // Note that this method return a non-nullable type.
        "/** @return {!Object} */",
        "function getSomething() {",
        "  var x = new Bar();",
        "  return new Foo();",
        "}",
        "(function someMethod() {",
        "  var x = getSomething();",
        // Note that this casts from !Object to ?Foo.
        "  var y = /** @type {Foo} */ (x).myprop;",
        "  return 1 != y;",
        "})()");

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setCheckTypes(true);
    options.setSmartNameRemoval(true);
    options.setFoldConstants(true);
    options.setExtraSmartNameRemoval(true);
    options.setInlineVariables(true);
    options.setDisambiguateProperties(true);

    // Verify that property disambiguation continues to work after the local
    // var and cast have been removed by inlining.
    test(options, code,
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() { this.Foo$myprop = 1; }",
            "/** @constructor */",
            "function Bar() { this.Bar$myprop = 2; }",
            "/** @return {Object} */",
            "function getSomething() {",
            "  var x = new Bar();",
            "  return new Foo();",
            "}",
            "(function someMethod() {",
            "  return 1 != getSomething().Foo$myprop;",
            "})()"));
  }

  public void testInlineFunctions() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() { return 3; } f(); ";
    testSame(options, code);

    options.setInlineFunctions(Reach.ALL);
    test(options, code, "3;");
  }

  public void testRemoveUnusedVars1() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f(x) {} f();";
    testSame(options, code);

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "function f() {} f();");
  }

  public void testRemoveUnusedVars2() {
    CompilerOptions options = createCompilerOptions();
    String code = "(function f(x) {})();var g = function() {}; g();";
    testSame(options, code);

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "(function() {})();var g = function() {}; g();");

    options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.UNMAPPED);
    test(options, code, "(function f() {})();var g = function $g$() {}; g();");
  }

  public void testCrossModuleCodeMotion() {
    CompilerOptions options = createCompilerOptions();
    String[] code = new String[] {
      "var x = 1;",
      "x;",
    };
    testSame(options, code);

    options.setCrossModuleCodeMotion(true);
    test(options, code,
        new String[] {
            "", "var x = 1; x;",
        });
  }

  public void testCrossModuleMethodMotion() {
    CompilerOptions options = createCompilerOptions();
    String[] code = new String[] {
      "var Foo = function() {}; Foo.prototype.bar = function() {};" +
      "var x = new Foo();",
      "x.bar();",
    };
    testSame(options, code);

    options.setCrossModuleMethodMotion(true);
    test(options, code,
        new String[] {
            CrossModuleMethodMotion.STUB_DECLARATIONS + "var Foo = function() {};"
            + "Foo.prototype.bar=JSCompiler_stubMethod(0); var x=new Foo;",
            "Foo.prototype.bar=JSCompiler_unstubMethod(0,function(){}); x.bar()",
        });
  }

  public void testCrossModuleDepCheck() {
    CompilerOptions options = createCompilerOptions();
    String[] code = new String[] {
      "var goog = {}; new goog.Foo();",
      "/** @constructor */ goog.Foo = function() {};",
    };
    testSame(options, code);

    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    test(options, code, code, CheckGlobalNames.STRICT_MODULE_DEP_QNAME);
  }

  public void testFlowSensitiveInlineVariables1() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() { var x = 3; x = 5; return x; }";
    testSame(options, code);

    options.setInlineVariables(true);
    test(options, code, "function f() { var x = 3; return 5; }");

    String unusedVar = "function f() { var x; x = 5; return x; } f()";
    test(options, unusedVar, "(function f() { var x; return 5; })()");

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, unusedVar, "(function () { return 5; })()");
  }

  public void testFlowSensitiveInlineVariables2() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    test(options,
        "function f () {\n" +
        "    var ab = 0;\n" +
        "    ab += '-';\n" +
        "    alert(ab);\n" +
        "}",
        "function f () {\n" +
        "    alert('0-');\n" +
        "}");
  }

  // Github issue #1540: https://github.com/google/closure-compiler/issues/1540
  public void testFlowSensitiveInlineVariablesUnderAdvanced() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    options.setCheckSymbols(false);
    test(options,
        LINE_JOINER.join(
            "function f(x) {",
            "  var a = x + 1;",
            "  var b = x + 1;",
            "  window.c = x > 5 ? a : b;",
            "}",
            "f(g);"),
            "window.a = g + 1;");
  }

  public void testCollapseAnonymousFunctions() {
    CompilerOptions options = createCompilerOptions();
    String code = "var f = function() {};";
    testSame(options, code);

    options.setCollapseAnonymousFunctions(true);
    test(options, code, "function f() {}");
  }

  public void testMoveFunctionDeclarations() {
    CompilerOptions options = createCompilerOptions();
    String code = "var x = f(); function f() { return 3; }";
    testSame(options, code);

    options.moveFunctionDeclarations = true;
    test(options, code, "var f = function() { return 3; }; var x = f();");
  }

  public void testNameAnonymousFunctions() {
    CompilerOptions options = createCompilerOptions();
    String code = "var f = function() {};";
    testSame(options, code);

    options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.MAPPED);
    test(options, code, "var f = function $() {}");
    assertNotNull(lastCompiler.getResult().namedAnonFunctionMap);

    options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.UNMAPPED);
    test(options, code, "var f = function $f$() {}");
    assertNull(lastCompiler.getResult().namedAnonFunctionMap);
  }

  public void testNameAnonymousFunctionsWithVarRemoval() {
    CompilerOptions options = createCompilerOptions();
    options.setRemoveUnusedVariables(CompilerOptions.Reach.LOCAL_ONLY);
    options.setInlineVariables(true);
    String code = "var f = function longName() {}; var g = function() {};" +
        "function longerName() {} var i = longerName;";
    test(options, code,
         "var f = function() {}; var g = function() {}; " +
         "var i = function() {};");

    options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.MAPPED);
    test(options, code,
        "var f = function longName() {}; var g = function $() {};"
        + "var i = function longerName(){};");
    assertNotNull(lastCompiler.getResult().namedAnonFunctionMap);

    options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.UNMAPPED);
    test(options, code,
        "var f = function longName() {}; var g = function $g$() {};"
        + "var i = function longerName(){};");
    assertNull(lastCompiler.getResult().namedAnonFunctionMap);
  }

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

  public void testDevirtualizationAndExtractPrototypeMemberDeclarations() {
    CompilerOptions options = createCompilerOptions();
    options.setDevirtualizePrototypeMethods(true);
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

  public void testPropertyRenaming() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "function f() { return this.foo + this['bar'] + this.Baz; }" +
        "f.prototype.bar = 3; f.prototype.Baz = 3;";
    String all =
        "function f() { return this.c + this['bar'] + this.a; }"
            + "f.prototype.b = 3; f.prototype.a = 3;";
    testSame(options, code);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(options, code, all);
  }

  public void testConvertToDottedProperties() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "function f() { return this['bar']; } f.prototype.bar = 3;";
    String expected =
        "function f() { return this.bar; } f.prototype.a = 3;";
    testSame(options, code);

    options.convertToDottedProperties = true;
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(options, code, expected);
  }

  public void testRewriteFunctionExpressions() {
    CompilerOptions options = createCompilerOptions();
    String code = "var a = function() {};";
    String expected = "function JSCompiler_emptyFn(){return function(){}} " +
        "var a = JSCompiler_emptyFn();";
    for (int i = 0; i < 10; i++) {
      code += "a = function() {};";
      expected += "a = JSCompiler_emptyFn();";
    }
    testSame(options, code);

    options.setRewriteFunctionExpressions(true);
    test(options, code, expected);
  }

  public void testAliasAllStrings() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "function f() {" + "  return 'aaaaaaaaaaaaaaaaaaaa' + 'aaaaaaaaaaaaaaaaaaaa';" + "}";
    String expected =
        "var $$S_aaaaaaaaaaaaaaaaaaaa = 'aaaaaaaaaaaaaaaaaaaa';"
            + "function f() {"
            + "  return $$S_aaaaaaaaaaaaaaaaaaaa + $$S_aaaaaaaaaaaaaaaaaaaa;"
            + "}";
    testSame(options, code);

    options.setAliasAllStrings(true);
    test(options, code, expected);
  }

  public void testRenameVars1() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "var abc = 3; function f() { var xyz = 5; return abc + xyz; }";
    String local = "var abc = 3; function f() { var a = 5; return abc + a; }";
    String all = "var a = 3; function c() { var b = 5; return a + b; }";
    testSame(options, code);

    options.setVariableRenaming(VariableRenamingPolicy.LOCAL);
    test(options, code, local);

    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(options, code, all);

    options.reserveRawExports = true;
  }

  public void testRenameVars2() {
    CompilerOptions options = createCompilerOptions();
    options.setVariableRenaming(VariableRenamingPolicy.ALL);

    String code = "var abc = 3; function f() { window['a'] = 5; }";
    String noexport = "var a = 3;   function b() { window['a'] = 5; }";
    String export =   "var b = 3;   function c() { window['a'] = 5; }";

    options.reserveRawExports = false;
    test(options, code, noexport);

    options.reserveRawExports = true;
    test(options, code, export);
  }

  public void testShadowVaribles() {
    CompilerOptions options = createCompilerOptions();
    options.setVariableRenaming(VariableRenamingPolicy.LOCAL);
    options.shadowVariables = true;
    String code =     "var f = function(x) { return function(y) {}}";
    String expected = "var f = function(a) { return function(a) {}}";
    test(options, code, expected);
  }

  public void testRenameLabels() {
    CompilerOptions options = createCompilerOptions();
    String code = "longLabel: for(;true;) { break longLabel; }";
    String expected = "a: for(;true;) { break a; }";
    testSame(options, code);

    options.labelRenaming = true;
    test(options, code, expected);
  }

  public void testBadBreakStatementInIdeMode() {
    // Ensure that type-checking doesn't crash, even if the CFG is malformed.
    // This can happen in IDE mode.
    CompilerOptions options = createCompilerOptions();
    options.setDevMode(DevMode.OFF);
    options.setIdeMode(true);
    options.setCheckTypes(true);
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    test(options,
         "function f() { try { } catch(e) { break; } }",
         RhinoErrorReporter.PARSE_ERROR);
  }

  // https://github.com/google/closure-compiler/issues/2388
  public void testNoCrash_varInCatch() {
    CompilerOptions options = createCompilerOptions();
    options.setInlineFunctions(Reach.ALL);

    test(
        options,
        LINE_JOINER.join(
            "(function() {",
            "  try {",
            "    x = 2;",
            "  } catch (e) {",
            "    var x = 1;",
            "  }",
            "})();"),
      LINE_JOINER.join(
            "{ try {",
            "    x$jscomp$inline_0=2",
            "  } catch(e) {",
            "    var x$jscomp$inline_0=1",
            "  }",
            "}"));
  }

  // https://github.com/google/closure-compiler/issues/2364
  public void testNoCrash_varInCatch2() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    testSame(
        options,
        LINE_JOINER.join(
            "function foo() {",
            "  var msg;",
            "}",
            "",
            "function bar() {",
            "  msg;",
            "  try {}",
            "  catch(err) {",
            "    var msg;",
            "  }",
            "}"));
  }

  // http://blickly.github.io/closure-compiler-issues/#63
  public void testIssue63SourceMap() {
    CompilerOptions options = createCompilerOptions();
    String code = "var a;";

    options.skipNonTranspilationPasses = true;
    options.sourceMapOutputPath = "./src.map";

    Compiler compiler = compile(options, code);
    compiler.toSource();
  }

  public void testRegExp1() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);

    String code = "/(a)/.test('a');";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    String expected = "";

    test(options, code, expected);
  }

  public void testRegExp2() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code = "/(a)/.test('a');var a = RegExp.$1";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    test(options, code, CheckRegExp.REGEXP_REFERENCE);

    options.setWarningLevel(DiagnosticGroups.CHECK_REGEXP, CheckLevel.OFF);

    testSame(options, code);
  }

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

  public void testFoldLocals2() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);
    options.setCheckTypes(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);

    // An external function that returns a local object that the
    // method "go" that only modifies the object.
    String code = "widgetToken().go();";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    test(options, code, "widgetToken()");
  }


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
    //test(options, code, definition);
    testSame(options, code);
  }

  public void testFoldLocals4() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        "/** @constructor */\n"
        + "function InternalWidget(){this.x = 1;}"
        + "InternalWidget.prototype.internalGo = function (){this.x = 2};"
        + "new InternalWidget().internalGo();";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    String optimized =
        ""
        + "function InternalWidget(){this.x = 1;}"
        + "InternalWidget.prototype.internalGo = function (){this.x = 2};";

    test(options, code, optimized);
  }

  public void testFoldLocals5() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        ""
        + "function fn(){var a={};a.x={};return a}"
        + "fn().x.y = 1;";

    // "fn" returns a unescaped local object, we should be able to fold it,
    // but we don't currently.
    String result = ""
        + "function fn(){var a={x:{}};return a}"
        + "fn().x.y = 1;";

    test(options, code, result);

    options.setComputeFunctionSideEffects(true);

    test(options, code, result);
  }

  public void testFoldLocals6() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        ""
        + "function fn(){return {}}"
        + "fn().x.y = 1;";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    testSame(options, code);
  }

  public void testFoldLocals7() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        ""
        + "function InternalWidget(){return [];}"
        + "Array.prototype.internalGo = function (){this.x = 2};"
        + "InternalWidget().internalGo();";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    String optimized =
        ""
        + "function InternalWidget(){return [];}"
        + "Array.prototype.internalGo = function (){this.x = 2};";

    test(options, code, optimized);
  }

  public void testVarDeclarationsIntoFor() {
    CompilerOptions options = createCompilerOptions();

    options.setCollapseVariableDeclarations(false);

    String code = "var a = 1; for (var b = 2; ;) {}";

    testSame(options, code);

    options.setCollapseVariableDeclarations(true);

    test(options, code, "for (var a = 1, b = 2; ;) {}");
  }

  public void testExploitAssigns() {
    CompilerOptions options = createCompilerOptions();

    options.setCollapseVariableDeclarations(false);

    String code = "a = 1; b = a; c = b";

    testSame(options, code);

    options.setCollapseVariableDeclarations(true);

    test(options, code, "c=b=a=1");
  }

  public void testRecoverOnBadExterns() throws Exception {
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

    test(options,
         "var extern; " +
         "function f() { return extern + extern + extern + extern; }",
         "var extern; " +
         "function f() { return extern + extern + extern + extern; }",
         VarCheck.UNDEFINED_EXTERN_VAR_ERROR);
  }

  public void testDuplicateVariablesInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);
    externs = ImmutableList.of(SourceFile.fromCode(
        "externs", "var externs = {}; /** @suppress {duplicate} */ var externs = {};"));
    testSame(options, "");
  }

  public void testEs6ClassInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode("extraExterns", "class ExternalClass { externalMethod() {} }"));
    externs = externsList.build();

    testSame(options, "(new ExternalClass).externalMethod();");
    test(options, "(new ExternalClass).nonexistentMethod();", TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testGeneratorFunctionInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
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
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testAsyncFunctionInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
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
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testLanguageMode() {
    CompilerOptions options = createCompilerOptions();

    String code = "var a = {get f(){}}";

    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    Compiler compiler = compile(options, code);
    checkUnexpectedErrorsOrWarnings(compiler, 1);
    assertEquals(
        "JSC_PARSE_ERROR. Parse error."
            + " getters are not supported in older versions of JavaScript."
            + " If you are targeting newer versions of JavaScript,"
            + " set the appropriate language_in option."
            + " at i0.js line 1 : 0",
        compiler.getErrors()[0].toString());

    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    testSame(options, code);

    options.setLanguageIn(LanguageMode.ECMASCRIPT5_STRICT);
    testSame(options, code);
  }

  public void testLanguageMode2() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.OFF);

    String code = "var a  = 2; delete a;";

    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    testSame(options, code);

    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    testSame(options, code);

    options.setLanguageIn(LanguageMode.ECMASCRIPT5_STRICT);
    test(options,
        code,
        code,
        StrictModeCheck.DELETE_VARIABLE);
  }

  // http://blickly.github.io/closure-compiler-issues/#598
  public void testIssue598() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5_STRICT);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    String code =
        "'use strict';\n" +
        "function App() {}\n" +
        "App.prototype = {\n" +
        "  get appData() { return this.appData_; },\n" +
        "  set appData(data) { this.appData_ = data; }\n" +
        "};";

    testSame(options, code);
  }

  public void testCheckStrictMode() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

    externs = ImmutableList.of(
        SourceFile.fromCode(
            "externs", "var use; var arguments; arguments.callee;"));

    String code =
        "function App() {}\n" +
        "App.prototype.method = function(){\n" +
        "  use(arguments.callee)\n" +
        "};";

    test(options, code, "", StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN);
  }

  public void testCheckStrictModeGeneratorFunction() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.WARNING);

    externs = ImmutableList.of(
        SourceFile.fromCode("externs", "var arguments; arguments.callee;"));

    String code = "function *gen() { arguments.callee; }";

    DiagnosticType[] expectedErrors = new DiagnosticType[] {
      StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN,
      Es6ExternsCheck.MISSING_ES6_EXTERNS,
    };

    test(options, new String[] { code }, null, expectedErrors);
  }

  // http://blickly.github.io/closure-compiler-issues/#701
  public void testIssue701() {
    // Check ASCII art in license comments.
    String ascii = "/**\n" +
        " * @preserve\n" +
        "   This\n" +
        "     is\n" +
        "       ASCII    ART\n" +
        "*/";
    String result = "/*\n\n" +
        "   This\n" +
        "     is\n" +
        "       ASCII    ART\n" +
        "*/\n";
    testSame(createCompilerOptions(), ascii);
    assertEquals(result, lastCompiler.toSource());
  }

  // http://blickly.github.io/closure-compiler-issues/#724
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
        "isFunction = function(a){ var b={}; return a && '[object Function]' === b.a.apply(a); }";

    test(options, code, result);
  }

  // http://blickly.github.io/closure-compiler-issues/#730
  public void testIssue730() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);

    String code =
        "/** @constructor */function A() {this.foo = 0; Object.seal(this);}\n" +
        "/** @constructor */function B() {this.a = new A();}\n" +
        "B.prototype.dostuff = function() {this.a.foo++;alert('hi');}\n" +
        "new B().dostuff();\n";

    test(options,
        code,
        "function a(){this.b=0;Object.seal(this)}" +
        "(new function(){this.a=new a}).a.b++;" +
        "alert('hi')");

    options.setRemoveUnusedClassProperties(true);

    // This is still not a problem when removeUnusedClassProperties is enabled.
    test(options,
        code,
        "function a(){this.b=0;Object.seal(this)}" +
        "(new function(){this.a=new a}).a.b++;" +
        "alert('hi')");
  }

  public void testAddFunctionProperties1() throws Exception {
    String source =
        "var Foo = {};" +
        "var addFuncProp = function(o) {" +
        "  o.f = function() {}" +
        "};" +
        "addFuncProp(Foo);" +
        "alert(Foo.f());";
    String expected =
        "alert(void 0);";
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    options.setRenamingPolicy(
        VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    test(options, source, expected);
  }

  public void testAddFunctionProperties2a() throws Exception {
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

  public void testAddFunctionProperties2b() throws Exception {
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
            + "x.bar=function(){alert(3)};x.bar()";

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRenamingPolicy(
        VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    test(options, source, expected);
  }

  public void testAddFunctionProperties3() throws Exception {
    String source =
        "/** @constructor */ function F() {}" +
        "var x = new F();" +
        "/** @this {F} */" +
        "function g(y) { y.bar = function() { alert(3); }; }" +
        "g(x);" +
        "x.bar();";
    String expected =
        "var x = new function() {};" +
        "/** @this {F} */" +
        "(function (y) { y.bar = function() { alert(3); }; })(x);" +
        "x.bar();";

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    options.setRenamingPolicy(
        VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    options.setCheckTypes(false);
    test(options, source, expected);
  }

  public void testAddFunctionProperties4() throws Exception {
    String source =
        "/** @constructor */" +
        "var Foo = function() {};" +
        "var goog = {};" +
        "goog.addSingletonGetter = function(o) {" +
        "  o.f = function() {" +
        "    o.i = new o;" +
        "  };" +
        "};" +
        "goog.addSingletonGetter(Foo);" +
        "alert(Foo.f());";
    String expected = "function Foo(){} Foo.f=function(){Foo.i=new Foo}; alert(Foo.f());";

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    test(options, source, expected);
  }

  public void testCoalesceVariableNames() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() {var x = 3; var y = x; var z = y; return z;}";
    testSame(options, code);

    options.setCoalesceVariableNames(true);
    test(options, code, "function f() {var x = 3; x = x; x = x; return x;}");
  }

  public void testCoalesceVariables() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);
    options.setFoldConstants(false);
    options.setCoalesceVariableNames(true);

    String code =
        "function f(a) {"
        + "  if (a) {"
        + "    return a;"
        + "  } else {"
        + "    var b = a;"
        + "    return b;"
        + "  }"
        + "  return a;"
        + "}";
    String expected =
        "function f(a) {" +
        "  if (a) {" +
        "    return a;" +
        "  } else {" +
        "    a = a;" +
        "    return a;" +
        "  }" +
        "  return a;" +
        "}";

    test(options, code, expected);

    options.setFoldConstants(true);
    options.setCoalesceVariableNames(false);

    code =
        "function f(a) {"
        + "  if (a) {"
        + "    return a;"
        + "  } else {"
        + "    var b = a;"
        + "    return b;"
        + "  }"
        + "  return a;"
        + "}";
    expected =
        "function f(a) {" +
        "  if (!a) {" +
        "    var b = a;" +
        "    return b;" +
        "  }" +
        "  return a;" +
        "}";

    test(options, code, expected);

    options.setFoldConstants(true);
    options.setCoalesceVariableNames(true);

    expected =
        "function f(a) {"
        + "  return a;"
        + "}";

    test(options, code, expected);
  }

  public void testLateStatementFusion() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    test(options, "while(a){a();if(b){b();b()}}", "for(;a;)a(),b&&(b(),b())");
  }

  public void testLateConstantReordering() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    test(options, "if (((x < 1 || x > 1) || 1 < x) || 1 > x) { alert(x) }",
        "   (((1 > x || 1 < x) || 1 < x) || 1 > x) && alert(x) ");
  }

  public void testsyntheticBlockOnDeadAssignments() {
    CompilerOptions options = createCompilerOptions();
    options.setDeadAssignmentElimination(true);
    options.setRemoveUnusedVariables(Reach.ALL);
    options.syntheticBlockStartMarker = "START";
    options.syntheticBlockEndMarker = "END";
    test(options, "var x; x = 1; START(); x = 1;END();x()",
                  "var x; x = 1;{START();{x = 1}END()}x()");
  }

  public void testBug4152835() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.syntheticBlockStartMarker = "START";
    options.syntheticBlockEndMarker = "END";
    test(options, "START();END()", "{START();{}END()}");
  }

  public void testNoFuseIntoSyntheticBlock() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.syntheticBlockStartMarker = "START";
    options.syntheticBlockEndMarker = "END";
    options.aggressiveFusion = false;
    testSame(options, "for(;;) { x = 1; {START(); {z = 3} END()} }");
    testSame(options, "x = 1; y = 2; {START(); {z = 3} END()} f()");
    options.aggressiveFusion = true;
    testSame(options, "x = 1; {START(); {z = 3} END()} f()");
    test(options, "x = 1; y = 3; {START(); {z = 3} END()} f()",
                  "x = 1, y = 3; {START(); {z = 3} END()} f()");
  }

  /**
   * Make sure this doesn't compile to code containing <pre>a: break a;</pre> which doesn't work
   * properly on some versions of Edge. See b/72667630.
   */
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

  public void testBug5786871() {
    CompilerOptions options = createCompilerOptions();
    options.setIdeMode(true);
    testParseError(options, "function () {}");
  }

  // http://blickly.github.io/closure-compiler-issues/#378
  public void testIssue378() {
    CompilerOptions options = createCompilerOptions();
    options.setInlineVariables(true);
    testSame(
        options,
        "function f(c) {var f = c; arguments[0] = this;"
        + "    f.apply(this, arguments); return this;}");
  }

  // http://blickly.github.io/closure-compiler-issues/#550
  public void testIssue550() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    options.setFoldConstants(true);
    options.setInlineVariables(true);
    test(
        options,
        "function f(h) {\n"
        + "  var a = h;\n"
        + "  a = a + 'x';\n"
        + "  a = a + 'y';\n"
        + "  return a;\n"
        + "}",
        "function f(a) { return a += 'xy'; }");
  }

  // http://blickly.github.io/closure-compiler-issues/#1168
  public void testIssue1168() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);
    test(
        options,
        "while (function () {\n" + " function f(){};\n" + " L: while (void(f += 3)) {}\n" + "}) {}",
        "for( ; ; );");
  }

  // http://blickly.github.io/closure-compiler-issues/#1198
  public void testIssue1198() throws Exception {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    test(options,
         "function f(x) { return 1; do { x(); } while (true); }",
         "function f(a) { return 1; }");
  }

  // http://blickly.github.io/closure-compiler-issues/#1131
  public void testIssue1131() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    // TODO(tbreisacher): Re-enable dev mode and fix test failure.
    options.setDevMode(DevMode.OFF);

    test(options,
         "function f(k) { return k(k); } alert(f(f));",
         "function a(b) { return b(b); } alert(a(a));");
  }

  // http://blickly.github.io/closure-compiler-issues/#284
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

  // http://blickly.github.io/closure-compiler-issues/#772
  public void testIssue772() throws Exception {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    test(
        options,
        "/** @const */ var a = {};"
        + "/** @const */ a.b = {};"
        + "/** @const */ a.b.c = {};"
        + "goog.scope(function() {"
        + "  var b = a.b;"
        + "  var c = b.c;"
        + "  /** @typedef {string} */"
        + "  c.MyType;"
        + "  /** @param {c.MyType} x The variable. */"
        + "  c.myFunc = function(x) {};"
        + "});",
        "/** @const */ var a = {};"
        + "/** @const */ a.b = {};"
        + "/** @const */ a.b.c = {};"
        + "a.b.c.MyType;"
        + "a.b.c.myFunc = function(x) {};");
  }

  public void testSuppressBadGoogRequire() throws Exception {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    test(
        options,
        "/** @suppress {closureDepMethodUsageChecks} */\n"
        + "function f() { goog.require('foo'); }\n"
        + "f();",
        "function f() { goog.require('foo'); }\n"
        + "f();");
  }

  // http://blickly.github.io/closure-compiler-issues/#1204
  public void testIssue1204() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    WarningLevel.VERBOSE
        .setOptionsForWarningLevel(options);
    test(options,
         "goog.scope(function () {" +
         "  /** @constructor */ function F(x) { this.x = x; }" +
         "  alert(new F(1));" +
         "});",
         "alert(new function(){}(1));");
  }

  public void testCodingConvention() {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    assertEquals(
      compiler.getCodingConvention().getClass().toString(),
      ClosureCodingConvention.class.toString());
  }

  public void testJQueryStringSplitLoops() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    test(options, "var x=['1','2','3','4','5','6','7']", "var x='1234567'.split('')");

    options = createCompilerOptions();
    options.setFoldConstants(true);
    options.setComputeFunctionSideEffects(false);
    options.setRemoveUnusedVariables(Reach.ALL);

    // If we do splits too early, it would add a side-effect to x.
    test(options,
      "var x=['1','2','3','4','5','6','7']",
      "");

  }

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
            assertEquals(Token.VAR, var.getToken());
            var.detach();
          }
        });


    try {
      test(options,
           "var x = 3; function f() { return x + z; }",
           "function f() { return x + z; }");
      fail("Expected run-time exception");
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().contains("Unexpected variable x");
    }
  }

  public void testSuppressEs5StrictWarning() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.WARNING);
    test(options,
        "/** @suppress{es5Strict} */ function f() { var arguments; }",
        "");
  }

  public void testCheckProvidesWarning() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.OFF);
    test(options,
        "goog.require('x'); /** @constructor */ function f() { var arguments; }",
        CheckProvides.MISSING_PROVIDE_WARNING);
  }

  public void testSuppressCheckProvidesWarning() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE,
        CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.WARNING);
    testSame(options,
        "/** @constructor\n" +
        " *  @suppress{missingProvide} */\n" +
        "function f() {}");
  }

  public void testSuppressCastWarning() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);

    normalizeResults = true;

    test(options,
        "function f() { var xyz = /** @type {string} */ (0); }",
        DiagnosticType.warning(
            "JSC_INVALID_CAST", "invalid cast"));

    testSame(options,
        "/** @suppress {invalidCasts} */\n" +
        "function f() { var xyz = /** @type {string} */ (0); }");

    testSame(options,
        "/** @const */ var g = {};" +
        "/** @suppress {invalidCasts} */" +
        "g.a = g.b = function() { var xyz = /** @type {string} */ (0); }");
  }

  public void testLhsCast() {
    CompilerOptions options = createCompilerOptions();
    test(
        options,
        "/** @const */ var g = {};" +
        "/** @type {number} */ g.foo = 3;",
        "/** @const */ var g = {};" +
        "g.foo = 3;");
  }

  public void testRenamePrefix() {
    String code = "var x = {}; function f(y) {}";
    CompilerOptions options = createCompilerOptions();
    options.setRenamePrefix("G_");
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(options, code, "var G_={}; function G_a(a) {}");
  }

  public void testRenamePrefixNamespace() {
    String code =
        "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setRenamePrefixNamespace("_");
    test(options, code, "_.x$FOO = 5; _.x$bar = 3;");
  }

  public void testRenamePrefixNamespaceProtectSideEffects() {
    String code = "var x = null; try { +x.FOO; } catch (e) {}";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(
        options);
    options.setRenamePrefixNamespace("_");
    test(options, code, "_.x = null; try { +_.x.FOO; } catch (a) {}");
  }

  // https://github.com/google/closure-compiler/issues/1875
  public void testNoProtectSideEffectsInChecksOnly() {
    String code = "x;";

    CompilerOptions options = createCompilerOptions();
    options.setChecksOnly(true);
    options.setProtectHiddenSideEffects(true);
    testSame(options, code);
  }

  public void testRenameCollision() {
    String code = "" +
          "/**\n" +
          " * @fileoverview\n" +
          " * @suppress {uselessCode}\n" +
          " */" +
          "var x = {};\ntry {\n(0,use)(x.FOO);\n} catch (e) {}";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(
        options);
    options.setRenamePrefixNamespace("a");
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    options.setRenamePrefixNamespaceAssumeCrossModuleNames(false);
    WarningLevel.DEFAULT.setOptionsForWarningLevel(options);

    test(options, code,
        "var b = {}; try { (0,window.use)(b.FOO); } catch (c) {}");
  }

  public void testRenamePrefixNamespaceActivatesMoveFunctionDeclarations() {
    CompilerOptions options = createCompilerOptions();
    String code = "var x = f; function f() { return 3; }";
    testSame(options, code);
    assertFalse(options.moveFunctionDeclarations);
    options.setRenamePrefixNamespace("_");
    test(options, code, "_.f = function() { return 3; }; _.x = _.f;");
  }

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

  public void testNamelessParameter() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setCheckTypes(false);
    String code =
        "var impl_0;" +
        "$load($init());" +
        "function $load(){" +
        "  window['f'] = impl_0;" +
        "}" +
        "function $init() {" +
        "  impl_0 = {};" +
        "}";
    String result = "window.f = {};";
    test(options, code, result);
  }

  public void testHiddenSideEffect() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    String code =
        "window.offsetWidth;";
    String result =
        "window.offsetWidth;";
    test(options, code, result);
  }

  public void testNegativeZero() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setCheckSymbols(false);
    test(options,
        "function bar(x) { return x; }\n" +
        "function foo(x) { print(x / bar(0));\n" +
        "                 print(x / bar(-0)); }\n" +
        "foo(3);",
        "print(3/0);print(3/-0);");
  }

  public void testSingletonGetter1() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    options.setCodingConvention(new ClosureCodingConvention());
    test(options,
        "/** @const */\n" +
        "var goog = goog || {};\n" +
        "goog.addSingletonGetter = function(ctor) {\n" +
        "  ctor.getInstance = function() {\n" +
        "    return ctor.instance_ || (ctor.instance_ = new ctor());\n" +
        "  };\n" +
        "};" +
        "function Foo() {}\n" +
        "goog.addSingletonGetter(Foo);" +
        "Foo.prototype.bar = 1;" +
        "function Bar() {}\n" +
        "goog.addSingletonGetter(Bar);" +
        "Bar.prototype.bar = 1;",
        "");
  }

  public void testIncompleteFunction1() {
    CompilerOptions options = createCompilerOptions();
    options.setIdeMode(true);
    DiagnosticType[] warnings =
        new DiagnosticType[] {RhinoErrorReporter.PARSE_ERROR, RhinoErrorReporter.PARSE_ERROR};
    test(options,
        new String[] { "var foo = {bar: function(e) }" },
        new String[] { "var foo = {bar: function(e){}};" },
        warnings
    );
  }

  public void testIncompleteFunction2() {
    CompilerOptions options = createCompilerOptions();
    options.setIdeMode(true);
    testParseError(options, "function hi", "function hi() {}");
  }

  public void testSortingOff() {
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    test(options,
         new String[] {
           "goog.require('goog.beer');",
           "goog.provide('goog.beer');"
         },
         ProcessClosurePrimitives.LATE_PROVIDE_ERROR);
  }

  public void testGoogModuleDuplicateExport() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setStrictModeInput(true);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.ERROR);

    test(
        options,
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "class Foo {}",
            "exports = {",
            "  Foo,",
            "  Foo,",
            "};"),
        StrictModeCheck.DUPLICATE_OBJECT_KEY);
  }

  public void testGoogModuleOuterLegacyInner() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());

    test(
        options,
        new String[] {
          // googModuleOuter
          LINE_JOINER.join(
              "goog.module('foo.Outer');",
              "/** @constructor */ function Outer() {}",
              "exports = Outer;"),
          // legacyInner
          LINE_JOINER.join(
              "goog.module('foo.Outer.Inner');",
              "goog.module.declareLegacyNamespace();",
              "/** @constructor */ function Inner() {}",
              "exports = Inner;"),
          // legacyUse
          LINE_JOINER.join(
              "goog.provide('legacy.Use');",
              "goog.require('foo.Outer');",
              "goog.require('foo.Outer.Inner');",
              "goog.scope(function() {",
              "var Outer = goog.module.get('foo.Outer');",
              "new Outer;",
              "new foo.Outer.Inner;",
              "});")
        },
        new String[] {
          "/** @constructor */ function module$exports$foo$Outer() {}",
          LINE_JOINER.join(
              "/** @const */ var foo={};",
              "/** @const */ foo.Outer={};",
              "/** @constructor */ function module$contents$foo$Outer$Inner_Inner(){}",
              "/** @const */ foo.Outer.Inner=module$contents$foo$Outer$Inner_Inner;"),
          LINE_JOINER.join(
              "/** @const */ var legacy={};",
              "/** @const */ legacy.Use={};",
              "new module$exports$foo$Outer;",
              "new module$contents$foo$Outer$Inner_Inner")
        });
  }

  public void testLegacyGoogModuleExport() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    options.setGenerateExports(true);

    test(
        options,
        new String[] {
          LINE_JOINER.join(
              "var goog = {};",
              "goog.exportSymbol = function(path, symbol) {};"),
          LINE_JOINER.join(
              "goog.module('foo.example.ClassName');",
              "goog.module.declareLegacyNamespace();",
              "",
              "/** @constructor */ function ClassName() {}",
              "",
              "/** @export */",
              "exports = ClassName;"),
        },
        new String[] {
          LINE_JOINER.join(
              "var goog = {};",
              "goog.exportSymbol = function(path, symbol) {};"),
          LINE_JOINER.join(
              "var foo = {};",
              "foo.example = {};",
              "function module$contents$foo$example$ClassName_ClassName() {}",
              "foo.example.ClassName = module$contents$foo$example$ClassName_ClassName;",
              "goog.exportSymbol('foo.example.ClassName', foo.example.ClassName);"),
        });

    test(
        options,
        new String[] {
          LINE_JOINER.join(
              "var goog = {};",
              "goog.exportSymbol = function(path, symbol) {};"),
          LINE_JOINER.join(
              "goog.module('foo.ns');",
              "goog.module.declareLegacyNamespace();",
              "",
              "/** @constructor */ function ClassName() {}",
              "",
              "/** @export */",
              "exports.ExportedName = ClassName;"),
        },
        new String[] {
          LINE_JOINER.join(
              "var goog = {};",
              "goog.exportSymbol = function(path, symbol) {};"),
          LINE_JOINER.join(
              "var foo = {};",
              "foo.ns = {};",
              "function module$contents$foo$ns_ClassName() {}",
              "foo.ns.ExportedName = module$contents$foo$ns_ClassName;",
              "goog.exportSymbol('foo.ns.ExportedName', foo.ns.ExportedName);"),
        });
  }

  public void testUnboundedArrayLiteralInfiniteLoop() {
    CompilerOptions options = createCompilerOptions();
    options.setIdeMode(true);
    testParseError(options, "var x = [1, 2", "var x = [1, 2]");
  }

  @GwtIncompatible // b/63595345
  public void testProvideRequireSameFile() throws Exception {
    CompilerOptions options = createCompilerOptions();
    options.setDependencyOptions(
        new DependencyOptions()
        .setDependencySorting(true));
    options.setClosurePass(true);
    test(
        options,
        "goog.provide('x'); goog.require('x');",
        "var x = {};");
  }

  public void testDependencySorting() throws Exception {
    CompilerOptions options = createCompilerOptions();
    options.setDependencyOptions(
        new DependencyOptions()
        .setDependencySorting(true));
    test(
        options,
        new String[] {
          "goog.require('x');",
          "goog.provide('x');",
        },
        new String[] {
          "goog.provide('x');",
          "goog.require('x');",

          // For complicated reasons involving modules,
          // the compiler creates a synthetic source file.
          "",
        });
  }

  public void testStrictWarningsGuard() throws Exception {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.addWarningsGuard(new StrictWarningsGuard());

    Compiler compiler = compile(options,
        "/** @return {number} */ function f() { return true; }");
    assertThat(compiler.getErrors()).hasLength(1);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  public void testStrictWarningsGuardEmergencyMode() throws Exception {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.addWarningsGuard(new StrictWarningsGuard());
    options.useEmergencyFailSafe();

    Compiler compiler = compile(options,
        "/** @return {number} */ function f() { return true; }");
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).hasLength(1);
  }

  public void testInlineProperties() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    level.setTypeBasedOptimizationOptions(options);

    String code = "" +
        "var ns = {};\n" +
        "/** @constructor */\n" +
        "ns.C = function () {this.someProperty = 1}\n" +
        "alert(new ns.C().someProperty + new ns.C().someProperty);\n";
    assertTrue(options.shouldInlineProperties());
    assertTrue(options.shouldCollapseProperties());
    // CollapseProperties used to prevent inlining this property.
    test(options, code, "alert(2);");
  }

  public void testGoogDefineClass1() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    options.setCheckTypes(true);
    level.setOptionsForCompilationLevel(options);
    level.setTypeBasedOptimizationOptions(options);

    String code = "" +
        "var ns = {};\n" +
        "ns.C = goog.defineClass(null, {\n" +
        "  /** @constructor */\n" +
        "  constructor: function () {this.someProperty = 1}\n" +
        "});\n" +
        "alert(new ns.C().someProperty + new ns.C().someProperty);\n";
    assertTrue(options.shouldInlineProperties());
    assertTrue(options.shouldCollapseProperties());
    // CollapseProperties used to prevent inlining this property.
    test(options, code, "alert(2);");
  }

  public void testGoogDefineClass2() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    options.setCheckTypes(true);
    level.setOptionsForCompilationLevel(options);
    level.setTypeBasedOptimizationOptions(options);

    String code = "" +
        "var C = goog.defineClass(null, {\n" +
        "  /** @constructor */\n" +
        "  constructor: function () {this.someProperty = 1}\n" +
        "});\n" +
        "alert(new C().someProperty + new C().someProperty);\n";
    assertTrue(options.shouldInlineProperties());
    assertTrue(options.shouldCollapseProperties());
    // CollapseProperties used to prevent inlining this property.
    test(options, code, "alert(2);");
  }

  public void testGoogDefineClass3() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    level.setTypeBasedOptimizationOptions(options);
    WarningLevel warnings = WarningLevel.VERBOSE;
    warnings.setOptionsForWarningLevel(options);

    String code = "" +
        "var C = goog.defineClass(null, {\n" +
        "  /** @constructor */\n" +
        "  constructor: function () {\n" +
        "    /** @type {number} */\n" +
        "    this.someProperty = 1},\n" +
        "  /** @param {string} a */\n" +
        "  someMethod: function (a) {}\n" +
        "});" +
        "var x = new C();\n" +
        "x.someMethod(x.someProperty);\n";
    assertTrue(options.shouldInlineProperties());
    assertTrue(options.shouldCollapseProperties());
    // CollapseProperties used to prevent inlining this property.
    test(options, code, TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testGoogDefineClass4() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.VERBOSE;
    warnings.setOptionsForWarningLevel(options);
    options.setWarningLevel(
        DiagnosticGroups.GLOBAL_THIS, CheckLevel.WARNING);

    String code = "" +
        "var C = goog.defineClass(null, {\n" +
        "  /** @param {string} a */\n" +
        "  constructor: function (a) {this.someProperty = 1}\n" +
        "});\n";
    test(options, code, "");
  }

  public void testGoogDefineClassWithNti() {
    CompilerOptions options = createCompilerOptions();
    options.setNewTypeInference(true);
    options.setClosurePass(true);

    String code = lines(
        "var Foo = goog.defineClass(null,",
        "  {",
        "    statics: {",
        "      a: /** @type {number} */ ('asdf')",
        "    },",
        "    constructor: function() {}",
        "  });");

    test(options, code, NewTypeInference.INVALID_CAST);
  }

  public void testCheckConstants1() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.QUIET;
    warnings.setOptionsForWarningLevel(options);

    String code = "" +
        "var foo; foo();\n" +
        "/** @const */\n" +
        "var x = 1; foo(); x = 2;\n";
    test(options, code, code);
  }

  public void testCheckConstants2() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code = "" +
        "var foo;\n" +
        "/** @const */\n" +
        "var x = 1; foo(); x = 2;\n";
    test(options, code, ConstCheck.CONST_REASSIGNED_VALUE_ERROR);
  }

  // http://blickly.github.io/closure-compiler-issues/#937
  public void testIssue937() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code = "" +
        "console.log(" +
            "/** @type {function():!string} */ ((new x())['abc'])());";
    String result = "" +
        "console.log((new x()).abc());";
    test(options, code, result);
  }

  public void testES5toES6() throws Exception {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5_STRICT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    CompilationLevel.SIMPLE_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    String code = "f = function(c) { for (var i = 0; i < c.length; i++) {} };";
    compile(options, code);
  }

  // Due to JsFileParse not being supported in the JS version, the dependency parsing delegates to
  // the {@link CompilerInput$DepsFinder} class which is incompatible with the
  // DefaultCodingConvention due to it throwing on methods such as extractIsModuleFile which is
  // needed in {@link CompilerInput$DepsFinder#visitSubtree}. Disable this test in the JsVersion.
  // TODO(tdeegan): DepsFinder should error out early if run with DefaultCodingConvention.
  @GwtIncompatible
  public void testES6UnusedClassesAreRemovedDefaultCodingConvention() {
    testES6UnusedClassesAreRemoved(CodingConventions.getDefault());
  }

  // Tests that unused classes are removed, even if they are passed to $jscomp.inherits.
  private void
      testES6UnusedClassesAreRemoved(CodingConvention convention) {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setCodingConvention(convention);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    Compiler compiler = compile(options, LINE_JOINER.join(
        "class Base {}",
        "class Sub extends Base {}",
        "alert(1);"));
    String result = compiler.toSource(compiler.getJsRoot());
    assertThat(result).isEqualTo("alert(1)");
  }

  public void testES6UnusedClassesAreRemoved() {
    testES6UnusedClassesAreRemoved(new ClosureCodingConvention());
    testES6UnusedClassesAreRemoved(new GoogleCodingConvention());
  }

  /**
   * @param js A snippet of JavaScript in which alert('No one ever calls me'); is called
   *     in a method which is never called. Verifies that the method is stripped out by
   *     asserting that the result does not contain the string 'No one ever calls me'.
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

  public void testES6StaticsAreRemoved1() {
    testES6StaticsAreRemoved(LINE_JOINER.join(
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

  public void testES6StaticsAreRemoved2() {
    testES6StaticsAreRemoved(LINE_JOINER.join(
        "class Base {",
        "  static calledInSubclassOnly() { alert('No one ever calls me'); }",
        "}",
        "class Sub extends Base {",
        "  static calledInSubclassOnly() { alert('I am called'); }",
        "}",
        "Sub.calledInSubclassOnly();"));
  }

  // http://blickly.github.io/closure-compiler-issues/#787
  public void testIssue787() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code = LINE_JOINER.join(
        "function some_function() {",
        "  var fn1;",
        "  var fn2;",
        "",
        "  if (any_expression) {",
        "    fn2 = external_ref;",
        "    fn1 = function (content) {",
        "      return fn2();",
        "    }",
        "  }",
        "",
        "  return {",
        "    method1: function () {",
        "      if (fn1) fn1();",
        "      return true;",
        "    },",
        "    method2: function () {",
        "      return false;",
        "    }",
        "  }",
        "}");

    String result =
        LINE_JOINER.join(
            "function some_function() {",
            "  if (any_expression) {",
            "    var b = external_ref;",
            "    var a = function(a) {",
            "      return b()",
            "    };",
            "  }",
            "  return {",
            "    method1:function() {",
            "      a && a();",
            "      return !0",
            "    },",
            "    method2: function() {",
            "      return !1",
            "    }",
            "  }",
            "}");

    test(options, code, result);
  }

  public void testClosureDefines() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code = "" +
        "var CLOSURE_DEFINES = {\n" +
        "  'FOO': 1,\n" +
        "  'BAR': true\n" +
        "};\n" +
        "\n" +
        "/** @define {number} */ var FOO = 0;\n" +
        "/** @define {boolean} */ var BAR = false;\n" +
        "";

    String result = "" +
        "var CLOSURE_DEFINES = {\n" +
        "  FOO: 1,\n" +
        "  BAR: !0\n" +
        "}," +
        "FOO = 1," +
        "BAR = !0" +
        "";

    test(options, code, result);
  }

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

  public void testGatherExternPropsWithNTI() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setNewTypeInference(true);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);

    testSame(options,
        LINE_JOINER.join(
            "(function exportedTokensFromTemplate() {",
            "  function unusedFn(a) {}",
            "  var UNUSED = 1;",
            "  unusedFn({ /** @export */ foo: UNUSED });",
            "})();",
            "/** @constructor */",
            "function Bar() {",
            "  // Should not be renamed",
            "  this.foo = 1;",
            "}"));
  }

  public void testRmUnusedProtoPropsInExternsUsage() {
    CompilerOptions options = new CompilerOptions();
    options.setRemoveUnusedPrototypePropertiesInExterns(true);
    options.setRemoveUnusedPrototypeProperties(false);
    try {
      test(options, "", "");
      fail("Expected CompilerOptionsPreprocessor.InvalidOptionsException");
    } catch (RuntimeException e) {
      if (!(e instanceof CompilerOptionsPreprocessor.InvalidOptionsException)) {
        fail("Expected CompilerOptionsPreprocessor.InvalidOptionsException");
      }
    }
  }

  public void testMaxFunSizeAfterInliningUsage() {
    CompilerOptions options = new CompilerOptions();
    options.setInlineFunctions(Reach.NONE);
    options.setMaxFunctionSizeAfterInlining(1);
    try {
      test(options, "", "");
      fail("Expected CompilerOptionsPreprocessor.InvalidOptionsException");
    } catch (RuntimeException e) {
      if (!(e instanceof CompilerOptionsPreprocessor.InvalidOptionsException)) {
        fail("Expected CompilerOptionsPreprocessor.InvalidOptionsException");
      }
    }
  }

  // isEquivalentTo returns false for alpha-equivalent nodes
  public void testIsEquivalentTo() {
    String[] input1 = {"function f(z) { return z; }"};
    String[] input2 = {"function f(y) { return y; }"};
    CompilerOptions options = new CompilerOptions();
    Node out1 = parse(input1, options, false);
    Node out2 = parse(input2, options, false);
    assertFalse(out1.isEquivalentTo(out2));
  }

  public void testEs6OutDoesntCrash() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    test(options, "function f(x) { if (x) var x=5; }", "function f(x) { if (x) x=5; }");
  }

  public void testExternsProvideIsAllowed() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    externs = ImmutableList.of(SourceFile.fromCode("<externs>",
        "goog.provide('foo.bar'); /** @type {!Array<number>} */ foo.bar;"));

    test(options, "", "");
  }

  public void testIjsProvideIsAllowed1() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    test(
        options,
        new String[] {
          "/** @typeSummary */ goog.provide('foo.bar'); /** @type {!Array<number>} */ foo.bar;",
          LINE_JOINER.join(
              "goog.provide('foo.baz');",
              "",
              "/** @return {number} */",
              "foo.baz = function() { return foo.bar[0]; }"),
        },
        new String[] {
            "/** @return {number} */ foo.baz = function() { return foo.bar[0]; }",
        });
  }

  public void testIjsWithGoogScopeWorks() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    test(
        options,
        new String[] {
            "/** @typeSummary */ /** @const */ var goog = {};",
            LINE_JOINER.join(
                "goog.provide('foo.baz');",
                "",
                "goog.scope(function() {",
                "",
                "var RESULT = 5;",
                "/** @return {number} */",
                "foo.baz = function() { return RESULT; }",
                "",
                "}); // goog.scope"),
        },
        new String[] {
            LINE_JOINER.join(
                "var foo = {};",
                "/** @const */ $jscomp.scope.RESULT = 5;",
                "/** @return {number} */ foo.baz = function() { return $jscomp.scope.RESULT; }"),
        });
  }

  // GitHub issue #250: https://github.com/google/closure-compiler/issues/250
  public void testInlineStringConcat() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(options, LINE_JOINER.join(
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

  // GitHub issue #2079: https://github.com/google/closure-compiler/issues/2079
  public void testStrictEqualityComparisonAgainstUndefined() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.setCheckTypes(true);
    options.setUseTypesForLocalOptimization(true);
    test(
        options,
        LINE_JOINER.join(
            "if (/** @type {Array|undefined} */ (window['c']) === null) {",
            "  window['d'] = 12;",
            "}"),
            "null===window['c']&&(window['d']=12)");
  }

  // GitHub issue #2122: https://github.com/google/closure-compiler/issues/2122
  public void testNoRemoveSuperCallWithWrongArgumentCount() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(options,
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
            "  goog.base(this, 'resize', width);",
            "};",
            "(new Bar).resize(100, 200);"),
        LINE_JOINER.join(
            "function a(){}a.prototype.a=function(b,e){window.c=b*e};",
            "function d(){}d.b=a.prototype;d.prototype.a=function(b){d.b.a.call(this,b)};",
            "(new d).a(100, 200);"));
  }

  // GitHub issue #2203: https://github.com/google/closure-compiler/issues/2203
  public void testPureFunctionIdentifierWorksWithMultipleNames() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(options,
        LINE_JOINER.join(
            "var SetCustomData1 = function SetCustomData2(element, dataName, dataValue) {",
            "    var x = element['_customData'];",
            "    x[dataName] = dataValue;",
            "}",
            "SetCustomData1(window, 'foo', 'bar');"),
        "window._customData.foo='bar';");
  }

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

  public void testUnnecessaryBackslashInStringLiteral() {
    CompilerOptions options = createCompilerOptions();
    test(options,
        "var str = '\\q';",
        "var str = 'q';");
  }

  public void testWarnUnnecessaryBackslashInStringLiteral() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.UNNECESSARY_ESCAPE, CheckLevel.WARNING);
    test(options,
        "var str = '\\q';",
        "var str = 'q';",
        RhinoErrorReporter.UNNECESSARY_ESCAPE);
  }

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
    assertEquals(
        "Expected no warnings or errors\n"
            + "Errors: \n"
            + Joiner.on("\n").join(compiler.getErrors())
            + "\n"
            + "Warnings: \n"
            + Joiner.on("\n").join(compiler.getWarnings()),
        0,
        compiler.getErrors().length + compiler.getWarnings().length);

    Node root = compiler.getRoot().getLastChild();
    assertNotNull(root);
    Node script = root.getFirstChild();
    assertNotNull(script);
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

  public void testCheckVarsOnInAdvancedMode() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(options, "var x = foobar;", VarCheck.UNDEFINED_VAR_ERROR);
  }

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

  // TODO(b/69850796): Re-enable when InlineFunctions' FeatureSet is set back to ES6+.
  public void disabled_testInlineRestParamNonTranspiling() {
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

  // NOTE(dimvar): the jsdocs are ignored in the comparison of the before/after ASTs. It'd be nice
  // to test the jsdocs as well, but AFAICT we can only do that in CompilerTestCase, not here.
  public void testRestParametersWithGenerics() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setCheckTypes(true);
    test(
        options,
        lines(
            "/**",
            " * @constructor",
            " * @template T",
            " */",
            "function Foo() {}",
            "/**",
            " * @template T",
            " * @param {...function(!Foo<T>)} x",
            " */",
            "function f(...x) {",
            "  return 123;",
            "}"),
        lines(
            "/**",
            " * @constructor",
            " * @template T",
            " */",
            "function Foo() {}",
            "/**",
            " * @param {...function(!Foo<T>)} x",
            " * @template T",
            " */",
            "function f(x) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 0;",
            "       $jscomp$restIndex < arguments.length;",
            "       ++$jscomp$restIndex) {",
            "         $jscomp$restParams[$jscomp$restIndex - 0] =",
            "        arguments[$jscomp$restIndex];",
            "       }",
            "  {",
            "    var /** @type {!Array<function(!Foo<?>)>} */ x$0 = $jscomp$restParams;",
            "    return 123;",
            "  }",
            "}"));
  }

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

  // TODO(b/69850796): Re-enable when InlineFunctions' FeatureSet is set back to ES6+.
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

  // TODO(b/69850796): Re-enable when InlineFunctions' FeatureSet is set back to ES6+.
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

  public void testTranspilingEs2016ToEs2015() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);

    test(options, "alert(2 ** 5);", "alert(Math.pow(2, 5));");
    test(options, "const n = 2 ** 5;", "const n = Math.pow(2, 5);");
  }

  public void testMethodDestructuringInTranspiledAsyncFunction() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setOptimizeCalls(false);
    options.setAllowMethodCallDecomposing(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);

    // Create a noninjecting compiler avoid comparing all the polyfill code.
    useNoninjectingCompiler = true;

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(SourceFile.fromCode("extraExterns", "var $jscomp = {}; Symbol.iterator;"));
    externs = externsList.build();

    test(
        options,
        LINE_JOINER.join(
            "class A {",
            "  static doSomething(i) { alert(i); }",
            "}",
            "async function foo() {",
            "  A.doSomething(await 3);",
            "}",
            "foo();"),
        LINE_JOINER.join(
            "var A = function() {};",
            "var A$doSomething = function(i) {",
            "  alert(i);",
            "};",
            "function foo() {",
            "  return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "    function $jscomp$generator$function() {",
            "      var JSCompiler_temp_const;",
            "      var JSCompiler_temp_const$jscomp$1;",
            "      return $jscomp.generator.createGenerator(",
            "          $jscomp$generator$function,",
            "          function ($jscomp$generator$context) {",
            "            do switch ($jscomp$generator$context.nextAddress) {",
            "              case 1:",
            "                JSCompiler_temp_const = A;",
            "                JSCompiler_temp_const$jscomp$1 = A$doSomething;",
            "                return $jscomp$generator$context.yield(3, 2);",
            "              case 2:",
            "                JSCompiler_temp_const$jscomp$1.call(",
            "                    JSCompiler_temp_const,",
            "                    $jscomp$generator$context.yieldResult);",
            "                $jscomp$generator$context.jumpToEnd();",
            "            } while (0);",
            "          });",
            "    })",
            "}",
            "foo();"));
  }

  /** Creates a CompilerOptions object with google coding conventions. */
  @Override
  protected CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setRenamePrefixNamespaceAssumeCrossModuleNames(true);
    options.declaredGlobalExternsOnWindow = false;
    return options;
  }
}
