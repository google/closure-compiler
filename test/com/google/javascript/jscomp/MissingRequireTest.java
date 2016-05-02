/*
 * Copyright 2008 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.CheckRequiresForConstructors.MISSING_REQUIRE_CALL_WARNING;
import static com.google.javascript.jscomp.CheckRequiresForConstructors.MISSING_REQUIRE_WARNING;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

import java.util.List;

/**
 * Tests for the "missing requires" check in {@link CheckRequiresForConstructors}.
 *
 */

public final class MissingRequireTest extends Es6CompilerTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return super.getOptions(options);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckRequiresForConstructors(compiler,
        CheckRequiresForConstructors.Mode.FULL_COMPILE);
  }

  private void testMissingRequireCall(String js, String warningText) {
    test(js, js, null, MISSING_REQUIRE_CALL_WARNING, warningText);
  }

  private void testMissingRequire(String js, String warningText) {
    test(js, js, null, MISSING_REQUIRE_WARNING, warningText);
  }

  private void testMissingRequire(String[] js, String warningText) {
    test(js, js, null, MISSING_REQUIRE_WARNING, warningText);
  }

  public void testPassWithNoNewNodes() {
    String js = "var str = 'g4'; /* does not use new */";
    testSame(js);
  }

  public void testPassWithOneNew() {
    String js =
        LINE_JOINER.join(
            "goog.require('foo.bar.goo');",
            "var bar = new foo.bar.goo();");
    testSame(js);
  }

  public void testPassWithNewDeclaredClass() {
    testSameEs6("class C {}; var c = new C();");
  }

  public void testClassRecognizedAsConstructor() {
    testSameEs6("/** @constructor */ module$test.A = function() {};"
                + "class C extends module$test.A {}");
    testSameEs6("module$test.A = class {}; class C extends module$test.A {}");
  }

  public void testPassWithOneNewOuterClass() {
    String js =
        LINE_JOINER.join(
            "goog.require('goog.foo.Bar');",
            "var bar = new goog.foo.Bar.Baz();");
    testSame(js);
  }

  public void testPassWithOneNewOuterClassWithUpperPrefix() {
    String js =
        LINE_JOINER.join(
            "goog.require('goog.foo.IDBar');",
            "var bar = new goog.foo.IDBar.Baz();");
    testSame(js);
  }

  public void testSuppression() {
    testSame("/** @suppress {missingRequire} */ var x = new foo.Bar();");
    testSame("/** @suppress {missingRequire} */ function f() { var x = new foo.Bar(); }");
  }

  public void testFailWithOneNew() {
    String js = "goog.provide('foo'); var bar = new foo.abc.bar();";
    String warning = "missing require: 'foo.abc.bar'";
    testMissingRequire(js, warning);
  }

  public void testPassWithTwoNewNodes() {
    String js =
        LINE_JOINER.join(
            "goog.require('goog.foo.Bar');",
            "goog.require('goog.foo.Baz');",
            "var str = new goog.foo.Bar('g4'),",
            "    num = new goog.foo.Baz(5);");
    testSame(js);
  }

  public void testPassWithNestedNewNodes() {
    String js =
        LINE_JOINER.join(
            "goog.require('goog.foo.Bar');",
            "var str = new goog.foo.Bar(new goog.foo.Bar('5'));");
    testSame(js);
  }

  public void testPassWithInnerClassInExtends() {
    String js =
        LINE_JOINER.join(
            "goog.require('goog.foo.Bar');",
            "",
            "/** @constructor @extends {goog.foo.Bar.Inner} */",
            "function SubClass() {}");
    testSame(js);
  }

  public void testPassEs6ClassExtends() {
    String js =
        LINE_JOINER.join(
            "goog.require('goog.foo.Bar');",
            "",
            "class SubClass extends goog.foo.Bar.Inner {}");
    testSameEs6(js);
  }

  public void testPassPolymer() {
    testSame(
        LINE_JOINER.join(
            "var Example = Polymer({});",
            "new Example();"));
    testSame(
        LINE_JOINER.join(
            "foo.bar.Example = Polymer({});",
            "new foo.bar.Example();"));
  }

  public void testPassGoogDefineClass() {
    testSameEs6(
        LINE_JOINER.join(
            "var Example = goog.defineClass(null, {constructor() {}});",
            "new Example();"));
    testSameEs6(
        LINE_JOINER.join(
            "foo.bar.Example = goog.defineClass(null, {constructor() {}});",
            "new foo.bar.Example();"));
  }

  public void testPassGoogDefineClass_noRewriting() {
    testSameEs6(
        LINE_JOINER.join(
            "var Example = goog.defineClass(null, {constructor() {}});",
            "new Example();"));
    testSameEs6(
        LINE_JOINER.join(
            "foo.bar.Example = goog.defineClass(null, {constructor() {}});",
            "new foo.bar.Example();"));
  }

  public void testWarnGoogModule_noRewriting() {
    testMissingRequireCall(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "/**",
            " * @param {Array<string>} ids",
            " * @return {Array<HTMLElement>}",
            " */",
            "function getElems(ids) {",
            "  return ids.map(function(id) { return goog.dom.getElement(id); });",
            "}",
            "",
            "exports = getElems;"),
        "missing require: 'goog.dom'");
  }

  public void testPassGoogModule_noRewriting() {
    testSameEs6(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var dom = goog.require('goog.dom');",
            "",
            "/**",
            " * @param {Array<string>} ids",
            " * @return {Array<HTMLElement>}",
            " */",
            "function getElems(ids) {",
            "  return ids.map(id => dom.getElement(id));",
            "}",
            "",
            "exports = getElems;"));

    testSameEs6(
        LINE_JOINER.join(
            "goog.module('example');",
            "goog.module.declareLegacyNamespace();",
            "",
            "var dom = goog.require('goog.dom');",
            "",
            "/**",
            " * @param {Array<string>} ids",
            " * @return {Array<HTMLElement>}",
            " */",
            "function getElems(ids) {",
            "  return ids.map(id => dom.getElement(id));",
            "}",
            "",
            "exports = getElems;"));

    testSameEs6(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var {getElement} = goog.require('goog.dom');",
            "",
            "/**",
            " * @param {Array<string>} ids",
            " * @return {Array<HTMLElement>}",
            " */",
            "function getElems(ids) {",
            "  return ids.map(id => getElement(id));",
            "}",
            "",
            "exports = getElems;"));

    testSameEs6(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var {getElement: getEl} = goog.require('goog.dom');",
            "",
            "/**",
            " * @param {Array<string>} ids",
            " * @return {Array<HTMLElement>}",
            " */",
            "function getElems(ids) {",
            "  return ids.map(id => getEl(id));",
            "}",
            "",
            "exports = getElems;"));

    testSameEs6(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "goog.require('goog.dom');",
            "",
            "/**",
            " * @param {Array<string>} ids",
            " * @return {Array<HTMLElement>}",
            " */",
            "function getElems(ids) {",
            "  return ids.map(id => goog.dom.getElement(id));",
            "}",
            "",
            "exports = getElems;"));
  }

  public void testGoogModuleGet() {
    testSame(
        LINE_JOINER.join(
            "goog.provide('x.y');",
            "",
            "x.y = function() { var bar = goog.module.get('foo.bar'); }"));
  }

  public void testDirectCall() {
    String js = "foo.bar.baz();";
    testMissingRequireCall(js, "missing require: 'foo.bar'");

    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "var foo;"));
    test(externs, js, js, null, null, null);

    testSame("goog.require('foo.bar.baz'); " + js);
    testSame("goog.require('foo.bar'); " + js);
  }

  public void testDotCall() {
    String js = "foo.bar.baz.call();";
    testMissingRequireCall(js, "missing require: 'foo.bar'");

    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "var foo;"));
    test(externs, js, js, null, null, null);

    testSame("goog.require('foo.bar.baz'); " + js);
    testSame("goog.require('foo.bar'); " + js);
  }

  public void testDotApply() {
    String js = "foo.bar.baz.call();";
    testMissingRequireCall(js, "missing require: 'foo.bar'");

    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "var foo;"));
    test(externs, js, js, null, null, null);

    testSame("goog.require('foo.bar.baz'); " + js);
    testSame("goog.require('foo.bar'); " + js);
  }

  public void testCallLocal() {
    testSame("function f(foo) { foo.bar.baz(); }");
  }

  public void testCallWithParentNamespaceProvided() {
    testSame("goog.require('foo.bar'); foo.bar.baz();");
  }

  public void testCallOnInnerClass_namespaceRequire() {
    testSame("goog.require('foo.bar'); foo.bar.Outer.Inner.hello();");
  }

  public void testCallOnInnerClass_outerRequired() {
    testSame("goog.require('foo.bar.Outer'); foo.bar.Outer.Inner.hello();");
  }

  public void testCallOnInnerClass_innerRequired() {
    testSame("goog.require('foo.bar.Outer.Inner'); foo.bar.Outer.Inner.hello();");
  }

  /**
   * When the inner class is a substring of the outer class.
   */
  public void testCallOnInnerClass_substring() {
    testSame("goog.require('foo.bar'); foo.bar.JavaScript.Java.hello();");
    testSame("goog.require('foo.bar.JavaScript'); foo.bar.JavaScript.Java.hello();");
    testSame("goog.require('foo.bar.JavaScript.Java'); foo.bar.JavaScript.Java.hello();");
  }

  public void testGoogLocale() {
    testSame("var locale = goog.LOCALE.replace('_', '-');");
  }

  public void testGoogArray() {
    testMissingRequireCall(
        "goog.array.forEach(arr, fn);",
        "missing require: 'goog.array'");
  }

  public void testGoogDom() {
    testMissingRequireCall(
        "goog.dom.getElement('x');",
        "missing require: 'goog.dom'");
  }

  public void testLongNameNoClasses() {
    testMissingRequireCall(
        "example.of.a.long.qualified.name(arr, fn);",
        "missing require: 'example.of.a.long.qualified'");
  }

  // Occasionally people use namespaces that start with a capital letter, so this
  // check thinks it's a class name. Predictably, we don't handle this well.
  public void testClassNameAtStart() {
    testMissingRequireCall(
        "Example.of.a.namespace.that.looks.like.a.class.name(arr, fn);",
        "missing require: 'Example'");
  }

  public void testGoogTimerCallOnce() {
    testMissingRequireCall(
        "goog.Timer.callOnce(goog.nullFunction, 0);",
        "missing require: 'goog.Timer'");
  }

  public void testGoogTimer() {
    testMissingRequire(
        "var t = new goog.Timer();",
        "missing require: 'goog.Timer'");
  }

  public void testFailEs6ClassExtends() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    String js = "var goog = {}; class SubClass extends goog.foo.Bar.Inner {}";
    String warning = "missing require: 'goog.foo.Bar.Inner'";
    testMissingRequire(js, warning);
  }

  public void testFailEs6ClassExtendsSomethingWithoutNS() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    String js = "var goog = {}; class SubClass extends SomethingWithoutNS {}";
    String warning = "missing require: 'SomethingWithoutNS'";
    testMissingRequire(js, warning);
  }

  public void testEs6ClassExtendsSomethingInExterns() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    String js = "var goog = {}; class SubClass extends SomethingInExterns {}";
    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "/** @constructor */ var SomethingInExterns;"));
    test(externs, js, js, null, null, null);
  }

  public void testEs6ClassExtendsSomethingInExternsWithNS() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    String js = "var goog = {}; class SubClass extends MyExterns.SomethingInExterns {}";
    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "var MyExterns;\n"
        + "/** @constructor */ MyExterns.SomethingInExterns;"));
    test(externs, js, js, null, null, null);
  }

  public void testFailWithNestedNewNodes() {
    String js = "goog.require('goog.foo.Bar'); var str = new goog.foo.Bar(new goog.foo.Baz('5'));";
    String warning = "missing require: 'goog.foo.Baz'";
    testMissingRequire(js, warning);
  }

  public void testFailWithImplements() {
    String[] js = new String[] {
      "var goog = {};"
      + "goog.provide('example.Foo'); /** @interface */ example.Foo = function() {};",

      "/** @constructor @implements {example.Foo} */ var Ctor = function() {};"
    };
    String warning = "missing require: 'example.Foo'";
    testMissingRequire(js, warning);
  }

  public void testInterfaceExtends() {
    String js =
        LINE_JOINER.join(
            "/**",
            " * @interface",
            " * @extends {some.other.Interface}",
            " */",
            "function AnInterface() {}");

    String warning = "missing require: 'some.other.Interface'";
    testMissingRequire(js, warning);
  }

  public void testPassWithImplements() {
    String js = "goog.require('example.Foo');"
      + "/** @constructor @implements {example.Foo} */"
      + "var Ctor = function() {};";
    testSame(js);
  }

  public void testFailWithExtends1() {
    String[] js = new String[] {
      "var goog = {};\n"
      + "goog.provide('example.Foo');\n"
      + "/** @constructor */ example.Foo = function() {};",

      "/** @constructor @extends {example.Foo} */ var Ctor = function() {};"
    };
    String warning = "missing require: 'example.Foo'";
    testMissingRequire(js, warning);
  }

  public void testFailWithExtends2() {
    String[] js = new String[] {
      "var goog = {};\n"
      + "goog.provide('Foo');\n"
      + "/** @constructor */ var Foo = function() {};",

      "/** @constructor @extends {Foo} */ var Ctor = function() {};"
    };
    String warning = "missing require: 'Foo'";
    testMissingRequire(js, warning);
  }

  public void testPassWithExtends() {
    String js = "goog.require('example.Foo');"
      + "/** @constructor @extends {example.Foo} */"
      + "var Ctor = function() {};";
    testSame(js);

    // When @extends is on a non-function (typically an alias) don't warn.
    js = "goog.require('some.other.Class');"
      + "/** @constructor @extends {example.Foo} */"
      + "var LocalAlias = some.other.Class;";
    testSame(js);
  }

  public void testPassWithLocalFunctions() {
    String js =
        "/** @constructor */ function tempCtor() {}; var foo = new tempCtor();";
    testSame(js);
  }

  public void testPassWithLocalVariables() {
    String js =
        "/** @constructor */ var nodeCreator = function() {};"
            + "var newNode = new nodeCreator();";
    testSame(js);
  }

  public void testFailWithLocalVariableInMoreThanOneFile() {
    // there should be a warning for the 2nd script because it is only declared
    // in the 1st script
    String localVar =
        LINE_JOINER.join(
            "/** @constructor */ function tempCtor() {}",
            "function baz() {",
            "  /** @constructor */ function tempCtor() {}",
            "  var foo = new tempCtor();",
            "}");
    String[] js = new String[] {localVar, " var foo = new tempCtor();"};
    String warning = "missing require: 'tempCtor'";
    testMissingRequire(js, warning);
  }

  public void testNewNodesMetaTraditionalFunctionForm() {
    // the class in this script creates an instance of itself
    // there should be no warning because the class should not have to
    // goog.require itself .
    String js =
        "/** @constructor */ function Bar(){}; "
            + "Bar.prototype.bar = function(){ return new Bar();};";
    testSame(js);
  }

  public void testNewNodesMeta() {
    String js =
        LINE_JOINER.join(
            "/** @constructor */",
            "goog.ui.Option = function() {};",
            "goog.ui.Option.optionDecorator = function() {",
            "  return new goog.ui.Option();",
            "};");
    testSame(js);
  }

  public void testShouldWarnWhenInstantiatingObjectsDefinedInGlobalScope() {
    // there should be a warning for the 2nd script because
    // Bar was declared in the 1st file, not the 2nd
    String good =
        "/** @constructor */ function Bar(){}; "
            + "Bar.prototype.bar = function(){return new Bar();};";
    String bad = "/** @constructor */ function Foo(){ var bar = new Bar();}";
    String[] js = new String[] {good, bad};
    String warning = "missing require: 'Bar'";
    testMissingRequire(js, warning);
  }

  public void testShouldWarnWhenInstantiatingGlobalClassesFromGlobalScope() {
    // there should be a warning for the 2nd script because Baz
    // was declared in the first file, not the 2nd
    String good =
      "/** @constructor */ function Baz(){}; "
          + "Baz.prototype.bar = function(){return new Baz();};";
    String bad = "var baz = new Baz()";
    String[] js = new String[] {good, bad};
    String warning = "missing require: 'Baz'";
    testMissingRequire(js, warning);
  }

  public void testIgnoresNativeObject() {
    String externs = "/** @constructor */ function String(val) {}";
    String js = "var str = new String('4');";
    test(externs, js, js, null, null);
  }

  public void testPassExterns() {
    String externs = "/** @const */ var google = {};";
    String js = "var ll = new google.maps.LatLng();";
    test(externs, js, js, null, null);
  }

  public void testNewNodesWithMoreThanOneFile() {
    // Bar is created, and goog.require()ed, but in different files.
    String[] js =
        new String[] {
          LINE_JOINER.join(
              "/** @constructor */",
              "function Bar() {}",
              "/** @suppress {extraRequire} */",
              "goog.require('Bar');"),

          "var bar = new Bar();"
        };
    String warning = "missing require: 'Bar'";
    testMissingRequire(js, warning);
  }

  public void testPassWithoutWarningsAndMultipleFiles() {
    String[] js =
        new String[] {
          LINE_JOINER.join("goog.require('Foo');", "var foo = new Foo();"),

          "goog.require('Bar'); var bar = new Bar();"
        };
    testSame(js);
  }

  public void testFailWithWarningsAndMultipleFiles() {
    /* goog.require is in the code base, but not in the correct file */
    String[] js =
        new String[] {
          LINE_JOINER.join(

              "/** @constructor */",
              "function Bar() {}",
              "/** @suppress {extraRequire} */",
              "goog.require('Bar');"),

          "var bar = new Bar();"
        };
    String warning = "missing require: 'Bar'";
    testMissingRequire(js, warning);
  }

  public void testCanStillCallNumberWithoutNewOperator() {
    String externs = "/** @constructor */ function Number(opt_value) {}";
    String js = "var n = Number('42');";
    test(externs, js, js, null, null);
    js = "var n = Number();";
    test(externs, js, js, null, null);
  }

  public void testRequiresAreCaughtBeforeProcessed() {
    String js = "goog.provide('foo'); var bar = new foo.bar.goo();";
    SourceFile input = SourceFile.fromCode("foo.js", js);
    Compiler compiler = new Compiler();
    CompilerOptions opts = new CompilerOptions();
    opts.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE, CheckLevel.WARNING);
    opts.setClosurePass(true);

    Result result = compiler.compile(ImmutableList.<SourceFile>of(), ImmutableList.of(input), opts);
    JSError[] warnings = result.warnings;
    assertNotNull(warnings);
    assertThat(warnings).isNotEmpty();

    String expectation = "missing require: 'foo.bar.goo'";

    for (JSError warning : warnings) {
      if (expectation.equals(warning.description)) {
        return;
      }
    }

    fail("Could not find the following warning:" + expectation);
  }

  public void testNoWarningsForThisConstructor() {
    String js =
        LINE_JOINER.join(
            "/** @constructor */goog.Foo = function() {};",
            "goog.Foo.bar = function() {",
            "  return new this.constructor;",
            "};");
    testSame(js);
  }

  public void testBug2062487() {
    testSame(
        LINE_JOINER.join(
            "/** @constructor */",
            "goog.Foo = function() {",
            "  /** @constructor */",
            "  this.x_ = function() {};",
            "  this.y_ = new this.x_();",
            "};"));
  }

  public void testIgnoreDuplicateWarningsForSingleClasses(){
    // no use telling them the same thing twice
    String[] js =
        new String[] {
          LINE_JOINER.join(
              "/** @constructor */",
              "example.Foo = function() {};",
              "example.Foo.bar = function(){",
              "  var first = new example.Forgot();",
              "  var second = new example.Forgot();",
              "};")
        };
    String warning = "missing require: 'example.Forgot'";
    testMissingRequire(js, warning);
  }

  public void testVarConstructorName() {
    String js = "/** @type {function(new:Date)} */var bar = Date; new bar();";
    testSame(js);
  }

  public void testVarConstructorFunction() {
    String js = "/** @type {function(new:Date)} */var bar = function() {}; new bar();";
    testSame(js);
  }

  public void testLetConstConstructorName() {
    testSameEs6("/** @type {function(new:Date)} */let bar = Date; new bar();");
    testSameEs6("/** @type {function(new:Date)} */const bar = Date; new bar();");
  }

  public void testLetConstConstructorFunction() {
    testSameEs6(
        "/** @type {function(new:Date)} */let bar = function() {}; new bar();");
    testSameEs6(
        "/** @type {function(new:Date)} */const bar = function() {}; new bar();");
  }

  public void testAssignConstructorName() {
    String js =
        LINE_JOINER.join(
            "var foo = {};",
            "/** @type {function(new:Date)} */",
            "foo.bar = Date;",
            "new foo.bar();");
    testSame(js);
  }

  public void testAssignConstructorFunction() {
    String js =
        LINE_JOINER.join(
            "var foo = {};",
            "/** @type {function(new:Date)} */",
            "foo.bar = function() {};",
            "new foo.bar();");
    testSame(js);
  }

  public void testConstructorFunctionReference() {
    String js = "/** @type {function(new:Date)} */function bar() {}; new bar();";
    testSame(js);
  }

  public void testMissingGoogRequireNoRootScope() {
    String good = ""
        + "goog.provide('foo.Bar');\n"
        + "/** @constructor */\n"
        + "foo.Bar = function() {};\n";
    String bad = ""
        + "function someFn() {\n"
        + "  var bar = new foo.Bar();\n"
        + "}\n";
    String[] js = new String[] {good, bad};
    String warning = "missing require: 'foo.Bar'";
    testMissingRequire(js, warning);
  }

  public void testMissingGoogRequireFromGoogDefineClass() {
    String good = ""
        + "goog.provide('foo.Bar');\n"
        + "foo.Bar = goog.defineClass(null, {\n"
        + "  constructor: function() {}\n"
        + "});\n";
    String bad = ""
        + "function someFn() {\n"
        + "  var bar = new foo.Bar();\n"
        + "}\n";
    String[] js = new String[] {good, bad};
    String warning = "missing require: 'foo.Bar'";
    testMissingRequire(js, warning);
  }

  public void testNoMissingGoogRequireFromGoogDefineClass() {
    String good = ""
        + "goog.provide('foo.Bar');\n"
        + "foo.Bar = goog.defineClass(null, {\n"
        + "  constructor: function() {}\n"
        + "});\n";
    String bad = ""
        + "goog.require('foo.Bar');\n"
        + "function someFn() {\n"
        + "  var bar = new foo.Bar();\n"
        + "}\n";
    String[] js = new String[] {good, bad};
    testSame(js);
  }

  public void testNoMissingGoogRequireFromGoogDefineClassSameFile() {
    String js = ""
        + "goog.provide('foo.Bar');\n"
        + "foo.Bar = goog.defineClass(null, {\n"
        + "  constructor: function() {}\n"
        + "});\n"
        + "function someFn() {\n"
        + "  var bar = new foo.Bar();\n"
        + "}\n";
    testSame(js);
  }

  public void testAliasConstructorToPrivateVariable() {
    String js = ""
        + "var foo = {};\n"
        + "/** @constructor */\n"
        + "foo.Bar = function() {}\n"
        + "/** @private */\n"
        + "foo.Bar.baz_ = Date;\n"
        + "function someFn() {\n"
        + "  var qux = new foo.Bar.baz_();\n"
        + "}";
    testSame(js);
  }

  public void testMissingGoogRequireFromGoogScope() {
    String good = ""
        + "goog.provide('foo.bar.Baz');\n"
        + "/** @constructor */\n"
        + "foo.bar.Baz = function() {}\n";
    String bad = ""
        + "goog.scope(function() {\n"
        + "  var Baz = foo.bar.Baz;\n"
        + "  function someFn() {\n"
        + "    var qux = new Baz();\n"
        + "  }\n"
        + "});\n";
    String[] js = new String[] {good, bad};
    String warning = "missing require: 'foo.bar.Baz'";
    testMissingRequire(js, warning);
  }

  public void testNoMissingGoogRequireFromGoogScope() {
    String good = ""
        + "goog.provide('foo.bar.Baz');\n"
        + "/** @constructor */\n"
        + "foo.bar.Baz = function() {}\n";
    String alsoGood = ""
        + "goog.require('foo.bar.Baz');\n"
        + "goog.scope(function() {\n"
        + "  var Baz = foo.bar.Baz;\n"
        + "  function someFn() {\n"
        + "    var qux = new Baz();\n"
        + "  }\n"
        + "});\n";
    String[] js = new String[] {good, alsoGood};
    testSame(js);
  }

  public void testNoMissingGoogRequireFromGoogScopeSameFile() {
    String js = ""
        + "goog.provide('foo.bar.Baz');\n"
        + "/** @constructor */\n"
        + "foo.bar.Baz = function() {}\n"
        + "goog.scope(function() {\n"
        + "  var Baz = foo.bar.Baz;\n"
        + "  function someFn() {\n"
        + "    var qux = new Baz();\n"
        + "  }\n"
        + "});\n";
    testSame(js);
  }

  public void testTypedefInGoogScope() {
    String js = LINE_JOINER.join(
        "goog.scope(function() {",
        "  /** @typedef {string} */",
        "  var Baz_;",
        "});");
    testSame(js);
  }

  public void testMissingGoogRequireFromGoogModule() {
    String good = ""
        + "goog.module('foo');\n"
        + "\n"
        + "var Atom = goog.defineClass(null, {\n"
        + "  constructor: function() {}\n"
        + "});\n";
    String bad = ""
        + "goog.module('fooTest');"
        + "function someFn() {\n"
        + "  var bar = new foo.Atom();\n"
        + "}\n";
    String[] js = new String[] {good, bad};
    String warning = "missing require: 'foo.Atom'";
    testMissingRequire(js, warning);
  }

  public void testNoMissingGoogRequireFromGoogModule() {
    String good = ""
        + "goog.module('foo.bar');\n"
        + "\n"
        + "var Atom = goog.defineClass(null, {\n"
        + "  constructor: function() {}\n"
        + "});\n";
    String alsoGood = ""
        + "goog.module('foo.barTest');\n"
        + "var bar = goog.require('foo.bar');"
        + "function someFn() {\n"
        + "  var baz = new bar.Atom();\n"
        + "}\n";
    String[] js = new String[] {good, alsoGood};
    testSame(js);
  }

  public void testNoMissingGoogRequireFromGoogModuleSameFile() {
    String js = ""
        + "goog.module('foo.bar');\n"
        + "\n"
        + "var Atom = goog.defineClass(null, {\n"
        + "  constructor: function() {}\n"
        + "});\n"
        + "function someFn() {\n"
        + "  var baz = new Atom();\n"
        + "}\n";
    testSame(js);
  }

  public void testNoMissingGoogRequireFromSameFile() {
    String js = LINE_JOINER.join(
        "var Atom = constructorFactory();",
        "function someFn() {",
        "  var baz = new Atom();",
        "}");
    testSame(js);
  }

  public void testReferenceToLocalNamespace() {
    testSame(
        LINE_JOINER.join(
            "/** @constructor */ function FooBar() {};",
            "FooBar.Subclass = constructorFactory();",
            "new FooBar.Subclass();"));
  }

  public void testReferenceInDestructuringParam() {
    testSameEs6(LINE_JOINER.join(
        "goog.require('Bar');",
        "function func( {a} ){}",
        "func( {a: new Bar()} );"));
    testWarningEs6(LINE_JOINER.join(
        "function func( {a} ){}",
        "func( {a: new Bar()} );"), MISSING_REQUIRE_WARNING);
    testSameEs6(LINE_JOINER.join(
        "/** @constructor */ var Bar = function(){};",
        "function func( {a} ){}",
        "func( {a: new Bar()} );"));
  }

  public void testReferenceInDefaultParam() {
    testWarningEs6(LINE_JOINER.join(
        "function func( a = new Bar() ){}",
        "func();"), MISSING_REQUIRE_WARNING);
    testSameEs6(LINE_JOINER.join(
        "/** @constructor */ var Bar = function(){};",
        "function func( a = new Bar() ){}",
        "func();"));
  }

  public void testPassModule() {
    testSameEs6(
        LINE_JOINER.join(
            "import {Foo} from 'bar';",
            "new Foo();"));
  }

  // Check to make sure that we still get warnings when processing a non-module after processing
  // a module.
  public void testFailAfterModule() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);

    String module = "import {Foo} from 'bar';";
    String script = "var x = new example.X()";
    String[] js = new String[] {module, script};
    String warning = "missing require: 'example.X'";
    testMissingRequire(js, warning);
  }
}
