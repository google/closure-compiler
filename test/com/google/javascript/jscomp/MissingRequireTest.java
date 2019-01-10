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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CheckMissingAndExtraRequires.MISSING_REQUIRE_FOR_GOOG_SCOPE;
import static com.google.javascript.jscomp.CheckMissingAndExtraRequires.MISSING_REQUIRE_STRICT_WARNING;
import static com.google.javascript.jscomp.CheckMissingAndExtraRequires.MISSING_REQUIRE_WARNING;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the "missing requires" check in {@link CheckMissingAndExtraRequires}.
 *
 */

@RunWith(JUnit4.class)
public final class MissingRequireTest extends CompilerTestCase {
  private CheckMissingAndExtraRequires.Mode mode;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    mode = CheckMissingAndExtraRequires.Mode.FULL_COMPILE;
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_REQUIRE, CheckLevel.WARNING);
    return super.getOptions(options);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckMissingAndExtraRequires(compiler, mode);
  }

  private void testMissingRequireStrict(String js, String warningText) {
    testSame(srcs(js), warning(MISSING_REQUIRE_STRICT_WARNING).withMessage(warningText));
  }

  private void testMissingRequire(String js, String warningText) {
    testSame(srcs(js), warning(MISSING_REQUIRE_WARNING).withMessage(warningText));
  }

  private void testMissingRequire(String[] js, String warningText) {
    testSame(srcs(js), warning(MISSING_REQUIRE_WARNING).withMessage(warningText));
  }

  private void testMissingRequireForScope(String[] js, String warningText) {
    testSame(srcs(js), warning(MISSING_REQUIRE_FOR_GOOG_SCOPE).withMessage(warningText));
  }

  @Test
  public void testPassWithNoNewNodes() {
    String js = "var str = 'g4'; /* does not use new */";
    testSame(js);
  }

  @Test
  public void testPassWithNoNewNodes_withES6Modules() {
    String js = "export var str = 'g4'; /* does not use new */";
    testSame(js);
  }

  @Test
  public void testPassWithOneNew() {
    String js =
        lines(
            "goog.require('foo.bar.goo');",
            "var bar = new foo.bar.goo();");
    testSame(js);
  }

  @Test
  public void testPassWithNewDeclaredClass() {
    testSame("class C {}; var c = new C();");
  }

  @Test
  public void testPassWithNewDeclaredClass_withES6Modules() {
    testSame("export class C {}; var c = new C();");
  }

  @Test
  public void testClassRecognizedAsConstructor() {
    testSame(
        lines(
            "/** @constructor */ module$test.A = function() {};",
            "class C extends module$test.A {}"));
    testSame("module$test.A = class {}; class C extends module$test.A {}");
  }

  @Test
  public void testPassWithOneNewOuterClass() {
    String js =
        lines(
            "goog.require('goog.foo.Bar');",
            "var bar = new goog.foo.Bar.Baz();");
    testSame(js);
  }

  @Test
  public void testPassWithOneNewOuterClassWithUpperPrefix() {
    String js =
        lines(
            "goog.require('goog.foo.IDBar');",
            "var bar = new goog.foo.IDBar.Baz();");
    testSame(js);
  }

  @Test
  public void testSuppression() {
    testSame("/** @suppress {missingRequire} */ var x = new foo.Bar();");
    testSame("/** @suppress {missingRequire} */ function f() { var x = new foo.Bar(); }");
  }

  @Test
  public void testFailWithOneNew() {
    String js = "goog.provide('foo'); var bar = new foo.abc.bar();";
    String warning = "missing require: 'foo.abc.bar'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testPassWithTwoNewNodes() {
    String js =
        lines(
            "goog.require('goog.foo.Bar');",
            "goog.require('goog.foo.Baz');",
            "var str = new goog.foo.Bar('g4'),",
            "    num = new goog.foo.Baz(5);");
    testSame(js);
  }

  @Test
  public void testPassWithNestedNewNodes() {
    String js =
        lines(
            "goog.require('goog.foo.Bar');",
            "var str = new goog.foo.Bar(new goog.foo.Bar('5'));");
    testSame(js);
  }

  @Test
  public void testPassWithInnerClassInExtends() {
    String js =
        lines(
            "goog.require('goog.foo.Bar');",
            "",
            "/** @constructor @extends {goog.foo.Bar.Inner} */",
            "function SubClass() {}");
    testSame(js);
  }

  @Test
  public void testPassEs6ClassExtends() {
    testSame(
        lines(
            "goog.require('goog.foo.Bar');",
            "",
            "class SubClass extends goog.foo.Bar.Inner {}"));
    testSame(
        lines(
            "goog.require('goog.foo.Bar');",
            "",
            "class SubClass extends goog.foo.Bar {}"));
  }

  @Test
  public void testPassPolymer() {
    testSame(
        lines(
            "var Example = Polymer({});",
            "new Example();"));
    testSame(
        lines(
            "foo.bar.Example = Polymer({});",
            "new foo.bar.Example();"));
  }

  @Test
  public void testPassGoogDefineClass() {
    testSame(
        lines(
            "var Example = goog.defineClass(null, {constructor() {}});",
            "new Example();"));
    testSame(
        lines(
            "foo.bar.Example = goog.defineClass(null, {constructor() {}});",
            "new foo.bar.Example();"));
  }

  @Test
  public void testPassGoogDefineClass_noRewriting() {
    testSame(
        lines(
            "var Example = goog.defineClass(null, {constructor() {}});",
            "new Example();"));
    testSame(
        lines(
            "foo.bar.Example = goog.defineClass(null, {constructor() {}});",
            "new foo.bar.Example();"));
  }

  @Test
  public void testWarnGoogModule_noRewriting() {
    testMissingRequireStrict(
        lines(
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

  @Test
  public void testWarnES6Module_noRewriting() {
    testMissingRequireStrict(
        lines(
            "import 'example';",
            "",
            "/**",
            " * @param {Array<string>} ids",
            " * @return {Array<HTMLElement>}",
            " */",
            "function getElems(ids) {",
            "  return ids.map(function(id) { return goog.dom.getElement(id); });",
            "}",
            "",
            "export default getElems();"),
        "missing require: 'goog.dom'");
  }

  @Test
  public void testPassForwardDeclare() {
    testSame(
        lines(
            "goog.module('example');",
            "",
            "var Event = goog.forwardDeclare('goog.events.Event');",
            "",
            "/**",
            " * @param {!Event} event",
            " */",
            "function listener(event) {",
            "  alert(event);",
            "}",
            "",
            "exports = listener;"));
  }

  @Test
  public void testFailForwardDeclare() {
    // TODO(tbreisacher): This should be a missing-require error.
    testSame(
        lines(
            "goog.module('example');",
            "",
            "var Event = goog.forwardDeclare('goog.events.Event');",
            "",
            "var e = new Event();",
            "",
            "exports = listener;"));
  }

  @Test
  public void testPassGoogModule_noRewriting() {
    testSame(
        lines(
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

    testSame(
        lines(
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

    testSame(
        lines(
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

    testSame(
        lines(
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

    testSame(
        lines(
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

  @Test
  public void testPassES6Module_noRewriting() {
    testSame(
        lines(
            "import 'example';",
            "",
            "import dom from 'goog.dom';",
            "",
            "/**",
            " * @param {Array<string>} ids",
            " * @return {Array<HTMLElement>}",
            " */",
            "function getElems(ids) {",
            "  return ids.map(id => dom.getElement(id));",
            "}",
            "",
            "export default getElems();"));
  }

  @Test
  public void testGoogModuleGet() {
    testSame(
        lines(
            "goog.provide('x.y');",
            "",
            "x.y = function() { var bar = goog.module.get('foo.bar'); }"));
  }

  @Test
  public void testDirectCall() {
    String js = "foo.bar.baz();";
    testMissingRequireStrict(js, "missing require: 'foo.bar'");

    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "var foo;"));
    testSame(externs(externs), srcs(js));

    testSame("goog.require('foo.bar.baz'); " + js);
    testSame("goog.require('foo.bar'); " + js);
  }

  @Test
  public void testDotCall() {
    String js = "foo.bar.baz.call();";
    testMissingRequireStrict(js, "missing require: 'foo.bar.baz'");

    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "var foo;"));
    testSame(externs(externs), srcs(js));

    testSame("goog.require('foo.bar.baz.call'); " + js);
    testSame("goog.require('foo.bar.baz'); " + js);
    testSame("goog.require('foo.bar'); " + js);
  }

  @Test
  public void testDotApply() {
    String js = "foo.bar.baz.apply();";
    testMissingRequireStrict(js, "missing require: 'foo.bar.baz'");

    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "var foo;"));
    testSame(externs(externs), srcs(js));

    testSame("goog.require('foo.bar.baz.apply'); " + js);
    testSame("goog.require('foo.bar.baz'); " + js);
    testSame("goog.require('foo.bar'); " + js);
  }

  @Test
  public void testDotBind() {
    String js = "foo.bar.baz.bind(this);";
    testMissingRequireStrict(js, "missing require: 'foo.bar.baz'");

    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "var foo;"));
    testSame(externs(externs), srcs(js));

    testSame("goog.require('foo.bar.baz.bind'); " + js);
    testSame("goog.require('foo.bar.baz'); " + js);
    testSame("goog.require('foo.bar'); " + js);
  }

  @Test
  public void testCallLocal() {
    testSame("function f(foo) { foo.bar.baz(); }");
  }

  @Test
  public void testCallWithParentNamespaceProvided() {
    testSame("goog.require('foo.bar'); foo.bar.baz();");
  }

  @Test
  public void testCallOnInnerClass_namespaceRequire() {
    testSame("goog.require('foo.bar'); foo.bar.Outer.Inner.hello();");
  }

  @Test
  public void testCallOnInnerClass_outerRequired() {
    testSame("goog.require('foo.bar.Outer'); foo.bar.Outer.Inner.hello();");
  }

  @Test
  public void testCallOnInnerClass_innerRequired() {
    testSame("goog.require('foo.bar.Outer.Inner'); foo.bar.Outer.Inner.hello();");
  }

  /** When the inner class is a substring of the outer class. */
  @Test
  public void testCallOnInnerClass_substring() {
    testSame("goog.require('foo.bar'); foo.bar.JavaScript.Java.hello();");
    testSame("goog.require('foo.bar.JavaScript'); foo.bar.JavaScript.Java.hello();");
    testSame("goog.require('foo.bar.JavaScript.Java'); foo.bar.JavaScript.Java.hello();");
  }

  @Test
  public void testGoogLocale() {
    testSame("var locale = goog.LOCALE.replace('_', '-');");
  }

  @Test
  public void testGoogArray() {
    testMissingRequireStrict(
        "goog.array.forEach(arr, fn);",
        "missing require: 'goog.array'");
  }

  @Test
  public void testGoogDom() {
    testMissingRequireStrict(
        "goog.dom.getElement('x');",
        "missing require: 'goog.dom'");
  }

  @Test
  public void testLongNameNoClasses() {
    testMissingRequireStrict(
        "example.of.a.long.qualified.name(arr, fn);",
        "missing require: 'example.of.a.long.qualified'");
  }

  // Occasionally people use namespaces that start with a capital letter, so this
  // check thinks it's a class name. Predictably, we don't handle this well.
  @Test
  public void testClassNameAtStart() {
    testMissingRequireStrict(
        "Example.of.a.namespace.that.looks.like.a.class.name(arr, fn);",
        "missing require: 'Example'");
  }

  @Test
  public void testGoogTimerCallOnce() {
    testMissingRequireStrict(
        "goog.Timer.callOnce(goog.nullFunction, 0);",
        "missing require: 'goog.Timer'");
  }

  @Test
  public void testGoogTimer() {
    testMissingRequire(
        "var t = new goog.Timer();",
        "missing require: 'goog.Timer'");
  }

  @Test
  public void testFailEs6ClassExtends() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    String js = "var goog = {}; class SubClass extends goog.foo.Bar.Inner {}";
    String warning = "missing require: 'goog.foo.Bar'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testFailEs6ClassExtendsSomethingWithoutNS() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    String js = "var goog = {}; class SubClass extends SomethingWithoutNS {}";
    String warning = "missing require: 'SomethingWithoutNS'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testEs6ClassExtendsSomethingInExterns() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    String js = "var goog = {}; class SubClass extends SomethingInExterns {}";
    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "/** @constructor */ var SomethingInExterns;"));
    testSame(externs(externs), srcs(js));
  }

  @Test
  public void testEs6ClassExtendsSomethingInExternsWithNS() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    String js = "var goog = {}; class SubClass extends MyExterns.SomethingInExterns {}";
    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "var MyExterns;\n"
        + "/** @constructor */ MyExterns.SomethingInExterns;"));
    testSame(externs(externs), srcs(js));
  }

  @Test
  public void testFailConstant() {
    mode = CheckMissingAndExtraRequires.Mode.SINGLE_FILE;
    testMissingRequireStrict(
        "goog.require('example.Class'); alert(example.Constants.FOO);",
        "missing require: 'example.Constants'");
    testMissingRequireStrict(
        "goog.require('example.Class'); alert(example.Outer.Inner.FOO);",
        "missing require: 'example.Outer'");
  }

  @Test
  public void testFailGoogArray() {
    mode = CheckMissingAndExtraRequires.Mode.SINGLE_FILE;
    testMissingRequireStrict(
        "console.log(goog.array.contains([1, 2, 3], 4));",
        "missing require: 'goog.array'");
  }

  @Test
  public void testPassConstant() {
    testSame("goog.require('example.Constants'); alert(example.Constants.FOO);");
    testSame("goog.require('example.Outer'); alert(example.Outer.Inner.FOO);");
  }

  @Test
  public void testPassLHSFromProvide() {
    testSame("goog.provide('example.foo.Outer.Inner'); example.foo.Outer.Inner = {};");
  }

  @Test
  public void testPassTypedef() {
    testSame("/** @typedef {string|number} */\nexample.TypeDef;");
  }

  @Test
  public void testPassConstantFromExterns() {
    testNoWarning("var example;", "alert(example.Constants.FOO);");
  }

  @Test
  public void testFailWithNestedNewNodes() {
    String js = "goog.require('goog.foo.Bar'); var str = new goog.foo.Bar(new goog.foo.Baz('5'));";
    String warning = "missing require: 'goog.foo.Baz'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testFailWithImplements() {
    String[] js = new String[] {
      "var goog = {};"
      + "goog.provide('example.Foo'); /** @interface */ example.Foo = function() {};",

      "/** @constructor @implements {example.Foo} */ var Ctor = function() {};"
    };
    String warning = "missing require: 'example.Foo'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testFailWithImplements_class() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);

    String[] js = new String[] {
      "var goog = {};"
      + "goog.provide('example.Foo'); /** @interface */ example.Foo = function() {};",

      "/** @implements {example.Foo} */ var SomeClass = class {};"
    };
    String warning = "missing require: 'example.Foo'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testFailWithImplements_class2() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);

    String[] js = new String[] {
      "var goog = {};"
      + "goog.provide('example.Foo'); /** @interface */ example.Foo = function() {};",

      "goog.provide('example.Bar'); /** @implements {example.Foo} */ example.Bar = class {};"
    };
    String warning = "missing require: 'example.Foo'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testFailWithImplements_googModule() {
    String[] js = new String[] {
      "goog.provide('example.Interface'); /** @interface */ example.Interface = function() {};",

      "goog.module('foo.Bar');"
          + "/** @constructor @implements {example.Interface} */ function Bar() {}; exports = Bar;"
    };
    String warning = "missing require: 'example.Interface'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testFailWithImplements_class_googModule() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);

    String[] js = new String[] {
      "goog.provide('example.Interface'); /** @interface */ example.Interface = function() {};",

      "goog.module('foo.Bar');"
          + "/** @implements {example.Interface} */ class Bar {}; exports = Bar;"
    };
    String warning = "missing require: 'example.Interface'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testInterfaceExtends() {
    String js =
        lines(
            "/**",
            " * @interface",
            " * @extends {some.other.Interface}",
            " */",
            "function AnInterface() {}");

    String warning = "missing require: 'some.other.Interface'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testPassWithImplements() {
    String js = "goog.require('example.Foo');"
      + "/** @constructor @implements {example.Foo} */"
      + "var Ctor = function() {};";
    testSame(js);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testPassWithLocalFunctions() {
    String js =
        "/** @constructor */ function tempCtor() {}; var foo = new tempCtor();";
    testSame(js);
  }

  @Test
  public void testPassWithLocalVariables() {
    String js =
        "/** @constructor */ var nodeCreator = function() {};"
            + "var newNode = new nodeCreator();";
    testSame(js);
  }

  @Test
  public void testFailWithLocalVariableInMoreThanOneFile() {
    // there should be a warning for the 2nd script because it is only declared
    // in the 1st script
    String localVar =
        lines(
            "/** @constructor */ function tempCtor() {}",
            "function baz() {",
            "  /** @constructor */ function tempCtor() {}",
            "  var foo = new tempCtor();",
            "}");
    String[] js = new String[] {localVar, " var foo = new tempCtor();"};
    String warning = "missing require: 'tempCtor'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testNewNodesMetaTraditionalFunctionForm() {
    // the class in this script creates an instance of itself
    // there should be no warning because the class should not have to
    // goog.require itself .
    String js =
        "/** @constructor */ function Bar(){}; "
            + "Bar.prototype.bar = function(){ return new Bar();};";
    testSame(js);
  }

  @Test
  public void testNewNodesMeta() {
    String js =
        lines(
            "/** @constructor */",
            "goog.ui.Option = function() {};",
            "goog.ui.Option.optionDecorator = function() {",
            "  return new goog.ui.Option();",
            "};");
    testSame(js);
  }

  @Test
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

  @Test
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

  @Test
  public void testIgnoresNativeObject() {
    String externs = "/** @constructor */ function String(val) {}";
    String js = "var str = new String('4');";
    testSame(externs(externs), srcs(js));
  }

  @Test
  public void testPassExterns() {
    String externs = "/** @const */ var google = {};";
    String js = "var ll = new google.maps.LatLng();";
    testSame(externs(externs), srcs(js));
  }

  @Test
  public void testNewNodesWithMoreThanOneFile() {
    // Bar is created, and goog.require()ed, but in different files.
    String[] js =
        new String[] {
          lines(
              "/** @constructor */",
              "function Bar() {}",
              "/** @suppress {extraRequire} */",
              "goog.require('Bar');"),

          "var bar = new Bar();"
        };
    String warning = "missing require: 'Bar'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testPassWithoutWarningsAndMultipleFiles() {
    String[] js =
        new String[] {
          lines(
              "goog.require('Foo');",
              "var foo = new Foo();"),

          "goog.require('Bar'); var bar = new Bar();"
        };
    testSame(js);
  }

  @Test
  public void testFailWithWarningsAndMultipleFiles() {
    /* goog.require is in the code base, but not in the correct file */
    String[] js =
        new String[] {
          lines(
              "/** @constructor */",
              "function Bar() {}",
              "/** @suppress {extraRequire} */",
              "goog.require('Bar');"),

          "var bar = new Bar();"
        };
    String warning = "missing require: 'Bar'";
    testMissingRequire(js, warning);
  }

  @Test
  public void testCanStillCallNumberWithoutNewOperator() {
    String externs = "/** @constructor */ function Number(opt_value) {}";
    String js = "var n = Number('42');";
    testSame(externs(externs), srcs(js));
    js = "var n = Number();";
    testSame(externs(externs), srcs(js));
  }

  @Test
  public void testRequiresAreCaughtBeforeProcessed() {
    String js = "goog.provide('foo'); var bar = new foo.bar.goo();";
    SourceFile input = SourceFile.fromCode("foo.js", js);
    Compiler compiler = new Compiler();
    CompilerOptions opts = new CompilerOptions();
    opts.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE, CheckLevel.WARNING);
    opts.setClosurePass(true);

    Result result = compiler.compile(ImmutableList.<SourceFile>of(), ImmutableList.of(input), opts);
    JSError[] warnings = result.warnings;
    assertThat(warnings).isNotNull();
    assertThat(warnings).isNotEmpty();

    String expectation = "missing require: 'foo.bar.goo'";

    for (JSError warning : warnings) {
      if (expectation.equals(warning.description)) {
        return;
      }
    }

    assertWithMessage("Could not find the following warning:" + expectation).fail();
  }

  @Test
  public void testNoWarningsForThisConstructor() {
    String js =
        lines(
            "/** @constructor */goog.Foo = function() {};",
            "goog.Foo.bar = function() {",
            "  return new this.constructor;",
            "};");
    testSame(js);
  }

  @Test
  public void testBug2062487() {
    testSame(
        lines(
            "/** @constructor */",
            "goog.Foo = function() {",
            "  /** @constructor */",
            "  this.x_ = function() {};",
            "  this.y_ = new this.x_();",
            "};"));
  }

  @Test
  public void testIgnoreDuplicateWarningsForSingleClasses() {
    // no use telling them the same thing twice
    String[] js =
        new String[] {
          lines(
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

  @Test
  public void testVarConstructorName() {
    String js = "/** @type {function(new:Date)} */var bar = Date; new bar();";
    testSame(js);
  }

  @Test
  public void testVarConstructorFunction() {
    String js = "/** @type {function(new:Date)} */var bar = function() {}; new bar();";
    testSame(js);
  }

  @Test
  public void testLetConstConstructorName() {
    testSame("/** @type {function(new:Date)} */let bar = Date; new bar();");
    testSame("/** @type {function(new:Date)} */const bar = Date; new bar();");
  }

  @Test
  public void testLetConstConstructorFunction() {
    testSame(
        "/** @type {function(new:Date)} */let bar = function() {}; new bar();");
    testSame(
        "/** @type {function(new:Date)} */const bar = function() {}; new bar();");
  }

  @Test
  public void testAssignConstructorName() {
    String js =
        lines(
            "var foo = {};",
            "/** @type {function(new:Date)} */",
            "foo.bar = Date;",
            "new foo.bar();");
    testSame(js);
  }

  @Test
  public void testAssignConstructorFunction() {
    String js =
        lines(
            "var foo = {};",
            "/** @type {function(new:Date)} */",
            "foo.bar = function() {};",
            "new foo.bar();");
    testSame(js);
  }

  @Test
  public void testConstructorFunctionReference() {
    String js = "/** @type {function(new:Date)} */function bar() {}; new bar();";
    testSame(js);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testMissingGoogRequireFromGoogScope1() {
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
    testMissingRequireForScope(js, warning);
  }

  @Test
  public void testMissingGoogRequireFromGoogScope2() {
    String good = ""
        + "goog.provide('foo.bar.Baz');\n"
        + "/** @constructor */\n"
        + "foo.bar.Baz = function() {}\n";
    String bad = ""
        + "goog.require('foo.bar.Baz');\n"
        + "goog.scope(function() {\n"
        + "  var bar = foo.bar;\n"
        + "  use(new bar.Baz);\n"
        + "});";
    String[] js = new String[] {good, bad};
    String warning = "missing require: 'foo.bar'";
    testMissingRequireForScope(js, warning);
  }

  @Test
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

  @Test
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

  @Test
  public void testNoMissingGoogRequireFromGoogScopeExterns() {
    String externs = "var location;";
    String js = ""
        + "goog.scope(function() {\n"
        + "  var BASE_URL = location.href;\n"
        + "});";
    testSame(externs(externs), srcs(js));
  }

  @Test
  public void testTypedefInGoogScope() {
    String js = lines(
        "goog.scope(function() {",
        "  /** @typedef {string} */",
        "  var Baz_;",
        "});");
    testSame(js);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testNoMissingGoogRequireFromSameFile() {
    String js = lines(
        "var Atom = constructorFactory();",
        "function someFn() {",
        "  var baz = new Atom();",
        "}");
    testSame(js);
  }

  @Test
  public void testReferenceToLocalNamespace() {
    testSame(
        lines(
            "/** @constructor */ function FooBar() {};",
            "FooBar.Subclass = constructorFactory();",
            "new FooBar.Subclass();"));
  }

  @Test
  public void testReferenceInDestructuringParam() {
    testSame(lines(
        "goog.require('Bar');",
        "function func( {a} ){}",
        "func( {a: new Bar()} );"));
    testWarning(lines(
        "function func( {a} ){}",
        "func( {a: new Bar()} );"), MISSING_REQUIRE_WARNING);
    testSame(lines(
        "/** @constructor */ var Bar = function(){};",
        "function func( {a} ){}",
        "func( {a: new Bar()} );"));
  }

  @Test
  public void testDestructuredAfterRequire() {
    testNoWarning(lines(
        "goog.module('x');",
        "",
        "const ns = goog.require('some.namespace');",
        "",
        "const {AnInterface} = ns;",
        "",
        "/** @implements {AnInterface} */",
        "class C {}",
        ""));

    testNoWarning(lines(
        "goog.module('x');",
        "",
        "const ns = goog.require('some.namespace');",
        "",
        "const {AnotherClass} = ns;",
        "",
        "/** @constructor @extends {AnotherClass} */",
        "function C() {}",
        ""));
  }

  @Test
  public void testReferenceInDefaultParam() {
    testWarning(lines(
        "function func( a = new Bar() ){}",
        "func();"), MISSING_REQUIRE_WARNING);
    testSame(lines(
        "/** @constructor */ var Bar = function(){};",
        "function func( a = new Bar() ){}",
        "func();"));
  }

  @Test
  public void testPassModule() {
    testSame(
        lines(
            "import {Foo} from 'bar';",
            "new Foo();"));
  }

  // Check to make sure that we still get warnings when processing a non-module after processing
  // a module.
  @Test
  public void testFailAfterModule() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);

    String module = "import {Foo} from 'bar';";
    String script = "var x = new example.X()";
    String[] js = new String[] {module, script};
    String warning = "missing require: 'example.X'";
    testMissingRequire(js, warning);
  }
}
