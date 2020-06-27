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
import static com.google.javascript.jscomp.TypeValidator.TYPE_MISMATCH_WARNING;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleType;
import com.google.javascript.jscomp.testing.IntegrationTestCase;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.Token;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for Closure primitives on the goog.* namespace that have special handling in
 * the compiler.
 */
@RunWith(JUnit4.class)
public final class ClosureIntegrationTest extends IntegrationTestCase {
  private static final String CLOSURE_BOILERPLATE =
      "/** @define {boolean} */ var COMPILED = false; var goog = {};"
      + "goog.exportSymbol = function() {};";

  private static final String CLOSURE_COMPILED =
      "var COMPILED = true; var goog$exportSymbol = function() {};";

  @Test
  public void testProcessDefinesInModule() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setDefineToBooleanLiteral("USE", false);
    test(
        options,
        lines(
            "goog.module('X');",
            "/** @define {boolean} */",
            "const USE = goog.define('USE', false);",
            "/** @const {boolean} */",
            "exports.USE = USE;"),
        "var module$exports$X={};module$exports$X.USE=false");
  }

  @Test
  public void testProcessDefinesInModuleWithDifferentDefineName() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setDefineToBooleanLiteral("MY_USE", false);
    test(
        options,
        lines(
            "goog.module('X');",
            "/** @define {boolean} */",
            "const USE = goog.define('MY_USE', false);",
            "/** @const {boolean} */",
            "exports.USE = USE;"),
        "var module$exports$X={};module$exports$X.USE=false");
  }

  @Test
  public void testStaticMemberClass() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setChecksOnly(true);

    testNoWarnings(
        options,
        new String[] {
          lines(
              "/** @fileoverview @typeSummary */",
              "goog.loadModule(function(exports) {",
              "  \"use strict\";",
              "  goog.module(\"foo.Foo\");",
              "  goog.module.declareLegacyNamespace();",
              "  class Foo {",
              "    /**",
              "     * @param {!Foo.Something} something",
              "     */",
              "     constructor(something) {",
              "     }",
              "  }",
              "  /** @private @const @type {!Foo.Something} */ Foo.prototype.something_;",
              "",
              // We're testing to be sure that the reference to Foo.Something
              // in JSDoc below resolves correctly even after module rewriting.
              "  Foo.Something = class {",
              "  };",
              "  exports = Foo;",
              "  return exports;",
              "});",
              ""),
          lines(
              "goog.module('b4d');",
              "",
              "const Foo = goog.require('foo.Foo');",
              "",
              "/**",
              " * @param {!Foo.Something} something",
              " */",
              "function foo(something) {",
              "  console.log(something);",
              "}",
              ""),
        });
  }

  @Test
  public void testBug1949424() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setClosurePass(true);
    test(options, CLOSURE_BOILERPLATE + "goog.provide('FOO'); FOO.bar = 3;",
        CLOSURE_COMPILED + "var FOO$bar = 3;");
  }

  @Test
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

  /**
   * Tests that calls to goog.string.Const.from() with non-constant arguments are detected with and
   * without collapsed properties.
   */
  @Test
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

  @Test
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

  @Test
  public void testBug2410122() {
    CompilerOptions options = createCompilerOptions();
    options.setGenerateExports(true);
    options.setClosurePass(true);
    test(
        options,
        "var goog = {};"
            + "function F() {}"
            + "/** @export */ function G() { G.base(this, 'constructor'); } "
            + "goog.inherits(G, F);",
        "var goog = {};"
            + "function F() {}"
            + "function G() { F.call(this); } "
            + "goog.inherits(G, F); goog.exportSymbol('G', G);");
  }

  @Test
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

  @Test
  public void testUnresolvedDefine() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setCheckTypes(true);
    DiagnosticType[] warnings = {
      ProcessDefines.INVALID_DEFINE_TYPE,
      ProcessDefines.INVALID_DEFINE_VALUE,
      RhinoErrorReporter.TYPE_PARSE_ERROR
    };
    String[] input = {
      "var goog = {};" + "goog.provide('foo.bar');" + "/** @define{foo.bar} */ foo.bar = {};"
    };
    String[] output = {
      "var goog = {};" + "var foo = {};" + "/** @define{foo.bar} */ foo.bar = {};"
    };
    test(options, input, output, warnings);
  }

  /**
   * Tests that calls to goog.string.Const.from() with non-constant arguments are detected with and
   * without collapsed properties, even when goog.string.Const.from has been aliased.
   */
  @Test
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

  @Test
  public void testDisableModuleRewriting() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    options.setEnableModuleRewriting(false);
    options.setCheckTypes(true);

    test(
        options,
        lines(
            "goog.module('foo.Outer');",
            "/** @constructor */ function Outer() {}",
            "exports = Outer;"),
        lines(
            "goog.module('foo.Outer');", //
            "function Outer(){}",
            "exports = Outer;"));
  }

  @Test
  public void testDisableModuleRewriting_doesntCrashWhenFirstInputIsModule_andGoogScopeUsed() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    options.setEnableModuleRewriting(false);
    options.setCheckTypes(true);
    options.setChecksOnly(true);

    testNoWarnings(
        options,
        new String[] {
          "goog.module('foo.bar');",
          lines(
              "goog.scope(function() {;", //
              "  /** @constructor */ function Outer() {}",
              "});")
        });
  }

  @Test
  public void testDisableModuleRewriting_doesntCrashWhenFirstInputIsModule_andPolyfillIsInjected() {
    CompilerOptions options = createCompilerOptions();
    options.setEnableModuleRewriting(false);
    options.setClosurePass(true);
    options.setChecksOnly(true);
    options.setForceLibraryInjection(ImmutableList.of("es6/string/startswith"));

    testNoWarnings(options, "goog.module('foo.bar');");
  }

  @Test
  public void testTypecheckNativeModulesDoesntCrash_givenTemplatizedTypedefOfUnionType() {
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);
    options.setCheckTypes(true);
    test(
        options,
        new String[] {
          lines(
              "goog.module('a');",
              "/** @interface */ class Foo {}",
              "/** @interface */ class Bar {}",
              "/** @typedef {(!Foo<?>|!Bar<?>)} */",
              "exports.TypeDef;",
              ""),
          lines(
              "goog.module('b');",
              "const a = goog.requireType('a');",
              "const /** null */ n = /** @type {!a.TypeDef<?>} */ (0);",
              "")
        },
        // Just making sure that typechecking ran and didn't crash.  It would be reasonable
        // for there also to be other type errors in this code before the final null assignment.
        TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testWeakSymbols_arentInlinedIntoStrongCode() {
    SourceFile extern =
        SourceFile.fromCode(
            "extern.js",
            lines(
                "/** @fileoverview @externs */", //
                "",
                "function alert(x) {}"));

    SourceFile aa =
        SourceFile.fromCode(
            "A.js",
            lines(
                "goog.module('a.A');",
                "goog.module.declareLegacyNamespace();",
                "",
                "class A { };",
                "",
                "exports = A;"),
            SourceKind.WEAK);

    SourceFile ab =
        SourceFile.fromCode(
            "B.js",
            lines(
                "goog.module('a.B');",
                "goog.module.declareLegacyNamespace();",
                "",
                "class B { };",
                "",
                "exports = B;"),
            SourceKind.STRONG);

    SourceFile entryPoint =
        SourceFile.fromCode(
            "C.js",
            lines(
                "goog.module('a.C');", //
                "",
                "const A = goog.requireType('a.A');",
                "const B = goog.require('a.B');",
                "",
                "alert(new B());",
                // Note how `a` is declared by any strong legacy module rooted on "a" (`a.B`).
                "alert(new a.A());"),
            SourceKind.STRONG);

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forClosure("a.C"))));

    Compiler compiler = new Compiler();
    compiler.init(ImmutableList.of(extern), ImmutableList.of(entryPoint, aa, ab), options);
    compiler.parse();
    compiler.check();
    compiler.performOptimizations();

    assertThat(compiler.toSource())
        .isEqualTo(
            "var a={};"
                + "class module$contents$a$B_B{}"
                + "a.B=module$contents$a$B_B;"
                + ""
                + "var module$exports$a$C={};"
                + "alert(new module$contents$a$B_B);"
                + "alert(new a.A);");
  }

  @Test
  public void testPreservedForwardDeclare() {
    CompilerOptions options = createCompilerOptions();
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setClosurePass(true);
    options.setPreserveClosurePrimitives(true);

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
  }

  @Test
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

  @GwtIncompatible // b/63595345
  @Test
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
  @Test
  public void testClosurePassOn() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    test(
        options,
        "var goog = {}; goog.require = function(x) {}; goog.require('foo');",
        ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE);
    test(
        options,
        "/** @define {boolean} */ var COMPILED = false;" +
        "var goog = {}; goog.getCssName = function(x) {};" +
        "goog.getCssName('foo');",
        "var COMPILED = true;" +
        "var goog = {}; goog.getCssName = function(x) {};" +
        "'foo';");
  }

  @Test
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

  @Test
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
  public void testCheckProvidesOn() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.ERROR);
    test(
        options,
        new String[] {"goog.require('x'); /** @constructor */ function Foo() {}", "new Foo();"},
        CheckProvides.MISSING_PROVIDE_WARNING);
  }

  @Test
  public void testGoogDefine1() {
    String code =
        CLOSURE_BOILERPLATE + "/** @define {boolean} */ var FLAG = goog.define('FLAG_XYZ', true);";

    CompilerOptions options = createCompilerOptions();

    options.setClosurePass(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setDefineToBooleanLiteral("FLAG_XYZ", false);

    test(options, code, CLOSURE_COMPILED + " var FLAG = false;");
  }

  @Test
  public void testGoogDefine2() {
    String code =
        CLOSURE_BOILERPLATE
            + "goog.provide('ns');"
            + "/** @define {boolean} */ ns.FLAG = goog.define('ns.FLAG_XYZ', true);";

    CompilerOptions options = createCompilerOptions();

    options.setClosurePass(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setDefineToBooleanLiteral("ns.FLAG_XYZ", false);
    test(options, code, CLOSURE_COMPILED + "var ns$FLAG = false;");
  }


  @Test
  public void testI18nMessageValidation_throughModuleExport() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);

    test(
        options,
        new String[] {
          lines(
              "goog.module('a.b')", //
              "",
              "exports = {",
              "  MSG_HELLO: goog.getMsg('Hello!'),",
              "}"),
          lines(
              "goog.module('a.c')", //
              "",
              "const b = goog.require('a.b');",
              "",
              "const {MSG_HELLO} = b;")
        },
        new String[] {
          lines(
              "var module$exports$a$b = {", //
              "  MSG_HELLO: goog.getMsg('Hello!'),",
              "}"),
          lines(
              "var module$exports$a$c = {};", //
              "",
              "const {MSG_HELLO: module$contents$a$c_MSG_HELLO} = module$exports$a$b;")
        });
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testProvidingTopLevelVar() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    testNoWarnings(
        options,
        lines(
            "goog.provide('Placer');",
            "goog.provide('Placer.Alignment');",
            "/** @param {*} image */ var Placer = function(image) {};",
            "Placer.Alignment = {LEFT: 'left'};"));
  }

  @Test
  public void testCheckProvidesWarning() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.OFF);
    test(options,
        "goog.require('x'); /** @constructor */ function f() { var arguments; }",
        CheckProvides.MISSING_PROVIDE_WARNING);
  }

  @Test
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

  @Test
  public void testSortingOff() {
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    test(
        options,
        new String[] {"goog.require('goog.beer');", "goog.provide('goog.beer');"},
        CheckClosureImports.LATE_PROVIDE_ERROR);
  }

  @Test
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

  @Test
  public void testGoogModuleOuterLegacyInner() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());

    test(
        options,
        new String[] {
          // googModuleOuter
          lines(
              "goog.module('foo.Outer');",
              "/** @constructor */ function Outer() {}",
              "exports = Outer;"),
          // legacyInner
          lines(
              "goog.module('foo.Outer.Inner');",
              "goog.module.declareLegacyNamespace();",
              "/** @constructor */ function Inner() {}",
              "exports = Inner;"),
          // legacyUse
          lines(
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
          lines(
              "/** @constructor */ function module$contents$foo$Outer_Outer() {}",
              "var module$exports$foo$Outer = module$contents$foo$Outer_Outer;"),
          lines(
              "/** @const */ var foo={};",
              "/** @const */ foo.Outer={};",
              "/** @constructor */ function module$contents$foo$Outer$Inner_Inner(){}",
              "/** @const */ foo.Outer.Inner=module$contents$foo$Outer$Inner_Inner;"),
          lines(
              "/** @const */ var legacy={};",
              "/** @const */ legacy.Use={};",
              "new module$contents$foo$Outer_Outer;",
              "new module$contents$foo$Outer$Inner_Inner")
        });
  }

  @Test
  public void testLegacyGoogModuleExport1() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    options.setGenerateExports(true);

    test(
        options,
        new String[] {
          lines(
              "var goog = {};", //
              "goog.exportSymbol = function(path, symbol) {};"),
          lines(
              "goog.module('foo.example.ClassName');",
              "goog.module.declareLegacyNamespace();",
              "",
              "/** @constructor */ function ClassName() {}",
              "",
              "/** @export */",
              "exports = ClassName;"),
        },
        new String[] {
          lines(
              "var goog = {};", //
              "goog.exportSymbol = function(path, symbol) {};"),
          lines(
              "var foo = {};",
              "foo.example = {};",
              "function module$contents$foo$example$ClassName_ClassName() {}",
              "foo.example.ClassName = module$contents$foo$example$ClassName_ClassName;",
              "goog.exportSymbol('foo.example.ClassName',"
                  + " module$contents$foo$example$ClassName_ClassName);"),
        });
  }

  @Test
  public void testLegacyGoogModuleExport2() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    options.setGenerateExports(true);

    test(
        options,
        new String[] {
          lines(
              "var goog = {};", //
              "goog.exportSymbol = function(path, symbol) {};"),
          lines(
              "goog.module('foo.ns');",
              "goog.module.declareLegacyNamespace();",
              "",
              "/** @constructor */ function ClassName() {}",
              "",
              "/** @export */",
              "exports.ExportedName = ClassName;"),
        },
        new String[] {
          lines(
              "var goog = {};", //
              "goog.exportSymbol = function(path, symbol) {};"),
          lines(
              "var foo = {};",
              "foo.ns = {};",
              "function module$contents$foo$ns_ClassName() {}",
              "foo.ns.ExportedName = module$contents$foo$ns_ClassName;",
              "goog.exportSymbol('foo.ns.ExportedName', module$contents$foo$ns_ClassName);"),
        });
  }

  @Test
  public void testGoogModuleGet_notAllowedInGlobalScope() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);

    // Test in a regular script
    test(
        options,
        new String[] {"goog.module('a.b');", "goog.module.get('a.b');"},
        ClosurePrimitiveErrors.INVALID_GET_CALL_SCOPE);

    // Test in a file with a goog.provide
    test(
        options,
        new String[] {"goog.module('a.b');", "goog.provide('c'); goog.module.get('a.b');"},
        ClosurePrimitiveErrors.INVALID_GET_CALL_SCOPE);
  }

  @GwtIncompatible // b/63595345
  @Test
  public void testProvideRequireSameFile() {
    CompilerOptions options = createCompilerOptions();
    options.setDependencyOptions(DependencyOptions.sortOnly());
    options.setClosurePass(true);
    test(
        options,
        "goog.provide('x'); goog.require('x');",
        "var x = {};");
  }

  @Test
  public void testDependencySorting() {
    CompilerOptions options = createCompilerOptions();
    options.setDependencyOptions(DependencyOptions.sortOnly());
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

  @Test
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

  @Test
  public void testExternsProvideIsAllowed() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "<externs>",
                lines(
                    "/** @fileoverview @suppress {externsValidation} */",
                    "goog.provide('foo.bar');",
                    "/** @type {!Array<number>} */ foo.bar;")));

    test(options, "var goog;", "var goog;");
  }

  @Test
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

  @Test
  public void testTypeSummaryReferencesToGoogModuleTypesAreRewritten() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    test(
        options,
        new String[] {
            LINE_JOINER.join(
                "/** @typeSummary */",
                "/** @constructor @template T */ function Bar() {}",
                "/** @const {!Bar<!ns.Foo>} */ var B;",
                ""),
            LINE_JOINER.join(
                "goog.module('ns');",
                "",
                "exports.Foo = class {}",
                ""),
        },
        (String[]) null);
  }

  @Test
  public void testGoogModuleReferencesToExternsTypesAreRewritten() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    // This is a very weird pattern, but we need to remove the usages before we remove support
    test(
        options,
        new String[] {
          lines(
              "/** @externs */",
              "goog.provide('ext.Bar');",
              "/** @const */",
              "var goog = {};",
              "/** @constructor */ ext.Bar = function() {};",
              ""),
          lines(
              "goog.module('ns');",
              "const Bar = goog.require('ext.Bar');",
              "",
              "exports.Foo = class extends Bar {}",
              ""),
        },
        (String[]) null);
  }

  /** Creates a CompilerOptions object with google coding conventions. */
  @Override
  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setRenamePrefixNamespaceAssumeCrossChunkNames(true);
    options.setAssumeGettersArePure(false);
    return options;
  }

  @Test
  public void testGetOriginalQualifiedNameAfterEs6RewriteClasses() {
    // A bug in Es6RewriteClasses meant we were putting the wrong `originalName` on some nodes.
    CompilerOptions options = createCompilerOptions();
    // force SourceInformationAnnotator to run
    options.setExternExports(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setClosurePass(true);

    test(
        options,
        lines(
            "goog.module('a');",
            "class Foo { static method() {} }",
            "class Bar { foo() { Foo.method(); } }"),
        lines(
            "var module$exports$a = {};",
            "/** @constructor @struct */",
            "var module$contents$a_Foo = function() {};",
            "module$contents$a_Foo.method = function() {};",
            "",
            "/** @constructor @struct */",
            "var module$contents$a_Bar = function () {}",
            "module$contents$a_Bar.prototype.foo = ",
            "    function() { module$contents$a_Foo.method(); }"));

    Node script = lastCompiler.getJsRoot().getFirstChild();

    Node exprResult = script.getLastChild();
    Node anonFunction = exprResult.getFirstChild().getLastChild();
    assertNode(anonFunction).hasToken(Token.FUNCTION);
    Node body = anonFunction.getLastChild();

    Node callNode = body.getOnlyChild().getOnlyChild();
    assertNode(callNode).hasToken(Token.CALL);

    Node modulecontentsFooMethod = callNode.getFirstChild();
    // Verify this is actually "Foo.method" - it used to be "Foo.foo".
    assertThat(modulecontentsFooMethod.getOriginalQualifiedName()).isEqualTo("Foo.method");
    assertThat(modulecontentsFooMethod.getSecondChild().getOriginalName()).isNull();
  }

  @Test
  public void testExternsWithGoogProvide() {
    CompilerOptions options = createCompilerOptions();
    options.setDependencyOptions(DependencyOptions.sortOnly());
    options.setClosurePass(true);
    options.setChecksOnly(true);
    options.setCheckGlobalNamesLevel(CheckLevel.ERROR);

    test(
        options,
        lines(
            "var ns = {};",
            // generally it's not allowed to access undefined namespaces
            "ns.subns.foo = function() {};"),
        CheckGlobalNames.UNDEFINED_NAME_WARNING);

    testSame(
        options,
        lines(
            "/** @externs */",
            "var ns = {};",
            // but @externs annotation hoists code to externs, where it is allowed
            "ns.subns.foo = function() {};"));

    testSame(
        options,
        lines(
            "/** @externs */",
            "var ns = {};",
            "ns.subns.foo = function() {};",
            "var goog;",
            // even when there is a goog.provide statement
            "goog.provide('provided');"));
  }

  @Test
  public void testExternsWithGoogProvide_required() {
    CompilerOptions options = createCompilerOptions();
    options.setDependencyOptions(DependencyOptions.sortOnly());
    options.setClosurePass(true);
    options.setChecksOnly(true);
    options.setCheckGlobalNamesLevel(CheckLevel.ERROR);
    String externs =
        lines(
            "/** @externs */",
            "var goog;",
            "/** @const */",
            "var mangled$name$from$externs = {};",
            "/** @constructor */",
            "mangled$name$from$externs.Clazz = function() {};",
            "goog.provide('ns.from.externs');",
            "/** @const */ var ns = {};",
            "/** @const */ ns.from = {};",
            "ns.from.externs = mangled$name$from$externs;");

    test(
        options,
        new String[] {
          externs,
          lines(
              "goog.module('ns.from.other');",
              "exports = {val: 1};",
              "/** @type {ns.from.externs.Clazz} */",
              "var usingExterns = null;"),
        },
        new String[] {
          "",
          lines(
              "var module$exports$ns$from$other = {val: 1};",
              "/** @type {ns.from.externs.Clazz} */",
              "var module$contents$ns$from$other_usingExterns = null;"),
        });
  }

  @Test
  public void testGoogReflectObjectPropertyNoArgs() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    // options.setCheckTypes(true);
    // options.setChecksOnly(true);

    test(options, new String[] {"goog.reflect.objectProperty();"}, (String[]) null);
  }

  @Test
  public void testGoogForwardDeclareInExterns_doesNotBlockGoogRenaming() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(SourceFile.fromCode("abc.js", "goog.forwardDeclare('a.b.c');"))
            .build();

    test(
        options,
        lines(
            "/** @type {!a.b.c} */ var x;", //
            "/** @const */",
            "var goog = {};"),
        "var a; var b = {};");
  }

  @Test
  public void testCanSpreadOnGoogModuleImport() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setClosurePass(true);
    useNoninjectingCompiler = true;

    String originalModule =
        lines(
            "goog.module('utils');", //
            "exports.Klazz = class {};",
            "exports.fn = function() {};");

    String originalModuleCompiled =
        lines(
            "var module$exports$utils$Klazz = function() {};",
            "var module$exports$utils$fn = function() {};");

    // Test destructuring import
    test(
        options,
        new String[] {
          originalModule,
          lines(
              "goog.module('client');", //
              "const {fn} = goog.require('utils');",
              "fn(...[]);")
        },
        new String[] {originalModuleCompiled, "module$exports$utils$fn.apply(null, []);"});

    // Test non-destructuring import
    test(
        options,
        new String[] {
          originalModule,
          lines(
              "goog.module('client');", //
              "const utils = goog.require('utils');",
              "utils.fn(...[]);")
        },
        new String[] {originalModuleCompiled, "module$exports$utils$fn.apply(null,[])"});
  }

  @Test
  public void testMalformedGoogModulesGracefullyError() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "closure_externs.js", new TestExternsBuilder().addClosureExterns().build()))
            .build();

    test(
        options,
        lines(
            "goog.module('m');", //
            "var x;",
            "goog.module.declareLegacyNamespace();"),
        ClosureCheckModule.LEGACY_NAMESPACE_NOT_AFTER_GOOG_MODULE);

    test(options, "var x; goog.module('m');", ClosureCheckModule.GOOG_MODULE_MISPLACED);
  }

  @Test
  public void testDuplicateClosureNamespacesError() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "closure_externs.js", new TestExternsBuilder().addClosureExterns().build()))
            .build();

    ImmutableMap<String, ModuleType> namespaces =
        ImmutableMap.of(
            "goog.provide('a.b.c');", ModuleType.GOOG_PROVIDE,
            "goog.module('a.b.c');", ModuleType.GOOG_MODULE,
            "goog.declareModuleId('a.b.c'); export {};", ModuleType.ES6_MODULE,
            "goog.module('a.b.c'); goog.module.declareLegacyNamespace();",
                ModuleType.LEGACY_GOOG_MODULE);

    for (Map.Entry<String, ModuleType> firstNs : namespaces.entrySet()) {
      for (Map.Entry<String, ModuleType> secondNs : namespaces.entrySet()) {
        // Error is based on whether the namespace originally came from a module or a provide.
        DiagnosticType expectedError =
            firstNs.getValue().equals(ModuleType.GOOG_PROVIDE)
                ? ClosurePrimitiveErrors.DUPLICATE_NAMESPACE
                : ClosurePrimitiveErrors.DUPLICATE_MODULE;

        test(options, new String[] {firstNs.getKey(), secondNs.getKey()}, expectedError);
      }
    }
  }

  @Test
  public void testExportMissingClosureBase() {
    CompilerOptions options = createCompilerOptions();
    options.setGenerateExports(true);
    options.setContinueAfterErrors(true); // test that the AST is not left in an invalid state.

    test(
        options,
        "/** @export */ function Foo() { alert('hi'); }",
        GenerateExports.MISSING_GOOG_FOR_EXPORT);
  }
}
