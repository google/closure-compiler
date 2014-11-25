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

import static com.google.javascript.jscomp.CheckRequiresForConstructors.MISSING_REQUIRE_WARNING;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

/**
 * Tests for {@link CheckRequiresForConstructors}.
 *
 */

public class CheckRequiresForConstructorsTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckRequiresForConstructors(compiler, CheckLevel.WARNING);
  }

  public void testPassWithNoNewNodes() {
    String js = "var str = 'g4'; /* does not use new */";
    testSame(js);
  }

  public void testPassWithOneNew() {
    String js =
        "var goog = {};" +
        "goog.require('foo.bar.goo'); var bar = new foo.bar.goo();";
    testSame(js);
  }

  public void testPassWithOneNewOuterClass() {
    String js =
        "var goog = {};" +
        "goog.require('goog.foo.Bar'); var bar = new goog.foo.Bar.Baz();";
    testSame(js);
  }

  public void testPassWithOneNewOuterClassWithUpperPrefix() {
    String js =
        "var goog = {};" +
        "goog.require('goog.foo.IDBar'); var bar = new goog.foo.IDBar.Baz();";
    testSame(js);
  }

  public void testFailWithOneNew() {
    String[] js = new String[] {"var foo = {}; var bar = new foo.bar();"};
    String warning = "'foo.bar' used but not goog.require'd";
    test(js, js, null, MISSING_REQUIRE_WARNING, warning);
  }

  public void testPassWithTwoNewNodes() {
    String js =
        "var goog = {};" +
        "goog.require('goog.foo.Bar');goog.require('goog.foo.Baz');" +
        "var str = new goog.foo.Bar('g4'), num = new goog.foo.Baz(5); ";
    testSame(js);
  }

  public void testPassWithNestedNewNodes() {
    String js =
        "var goog = {}; goog.require('goog.foo.Bar'); " +
        "var str = new goog.foo.Bar(new goog.foo.Bar('5')); ";
    testSame(js);
  }

  public void testFailWithNestedNewNodes() {
    String[] js =
        new String[] {"var goog = {}; goog.require('goog.foo.Bar'); "
            + "var str = new goog.foo.Bar(new goog.foo.Baz('5')); "};
    String warning = "'goog.foo.Baz' used but not goog.require'd";
    test(js, js, null, MISSING_REQUIRE_WARNING, warning);
  }

  public void testFailWithImplements() {
    String[] js = new String[] {
      "var goog = {};"
      + "goog.provide('example.Foo'); /** @interface */ example.Foo = function() {};",

      "/** @constructor @implements {example.Foo} */ var Ctor = function() {};"
    };
    String warning = "'example.Foo' used but not goog.require'd";
    test(js, js, null, MISSING_REQUIRE_WARNING, warning);
  }

  public void testPassWithImplements() {
    String js = "goog.require('example.Foo');"
      + "/** @constructor @implements {example.Foo} */"
      + "var Ctor = function() {};";
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
    String localVar = Joiner.on('\n').join(
        "/** @constructor */ function tempCtor() {}",
        "function baz() {",
        "  /** @constructor */ function tempCtor() {}",
        "  var foo = new tempCtor();",
        "}");
    String[] js = new String[] {localVar, " var foo = new tempCtor();"};
    String warning = "'tempCtor' used but not goog.require'd";
    test(js, js, null, MISSING_REQUIRE_WARNING, warning);
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
        "var goog = {};" +
        "/** @constructor */goog.ui.Option = function(){};"
            + "goog.ui.Option.optionDecorator = function(){"
            + "  return new goog.ui.Option(); };";
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
    String warning = "'Bar' used but not goog.require'd";
    test(js, js, null, MISSING_REQUIRE_WARNING, warning);
  }

  public void testShouldWarnWhenInstantiatingGlobalClassesFromGlobalScope() {
    // there should be a warning for the 2nd script because Baz
    // was declared in the first file, not the 2nd
    String good =
      "/** @constructor */ function Baz(){}; "
          + "Baz.prototype.bar = function(){return new Baz();};";
    String bad = "var baz = new Baz()";
    String[] js = new String[] {good, bad};
    String warning = "'Baz' used but not goog.require'd";
    test(js, js, null, MISSING_REQUIRE_WARNING, warning);
  }

  public void testIgnoresNativeObject() {
    String externs = "/** @constructor */ function String(val) {}";
    String js = "var str = new String('4');";
    test(externs, js, js, null, null);
  }

  public void testNewNodesWithMoreThanOneFile() {
    // Bar is created, and goog.require()ed, but in different files.
    String[] js = new String[] {
        "var goog = {};" +
        "/** @constructor */ function Bar() {}" +
        "goog.require('Bar');",
        "var bar = new Bar();"};
    String warning = "'Bar' used but not goog.require'd";
    test(js, js, null, MISSING_REQUIRE_WARNING, warning);
  }

  public void testPassWithoutWarningsAndMultipleFiles() {
    String[] js = new String[] {
        "var goog = {};" +
        "goog.require('Foo'); var foo = new Foo();",
        "goog.require('Bar'); var bar = new Bar();"};
    testSame(js);
  }

  public void testFailWithWarningsAndMultipleFiles() {
    /* goog.require is in the code base, but not in the correct file */
    String[] js = new String[] {
        "var goog = {};" +
        "/** @constructor */ function Bar() {}" +
        "goog.require('Bar');",
        "var bar = new Bar();"};
    String warning = "'Bar' used but not goog.require'd";
    test(js, js, null, MISSING_REQUIRE_WARNING, warning);
  }

  public void testCanStillCallNumberWithoutNewOperator() {
    String externs = "/** @constructor */ function Number(opt_value) {}";
    String js = "var n = Number('42');";
    test(externs, js, js, null, null);
    js = "var n = Number();";
    test(externs, js, js, null, null);
  }

  public void testRequiresAreCaughtBeforeProcessed() {
    String js = "var foo = {}; var bar = new foo.bar.goo();";
    SourceFile input = SourceFile.fromCode("foo.js", js);
    Compiler compiler = new Compiler();
    CompilerOptions opts = new CompilerOptions();
    opts.checkRequires = CheckLevel.WARNING;
    opts.closurePass = true;

    Result result = compiler.compile(ImmutableList.<SourceFile>of(),
        ImmutableList.of(input), opts);
    JSError[] warnings = result.warnings;
    assertNotNull(warnings);
    assertTrue(warnings.length > 0);

    String expectation = "'foo.bar.goo' used but not goog.require'd";

    for (JSError warning : warnings) {
      if (expectation.equals(warning.description)) {
        return;
      }
    }

    fail("Could not find the following warning:" + expectation);
  }

  public void testNoWarningsForThisConstructor() {
    String js =
      "var goog = {};" +
      "/** @constructor */goog.Foo = function() {};" +
      "goog.Foo.bar = function() {" +
      "  return new this.constructor; " +
      "};";
    testSame(js);
  }

  public void testBug2062487() {
    testSame(
      "var goog = {};" +
      "/** @constructor */goog.Foo = function() {" +
      "  /** @constructor */ this.x_ = function() {};" +
      "  this.y_ = new this.x_();" +
      "};");
  }

  public void testIgnoreDuplicateWarningsForSingleClasses(){
    // no use telling them the same thing twice
    String[] js = new String[]{
      "var goog = {};" +
      "/** @constructor */goog.Foo = function() {};" +
      "goog.Foo.bar = function(){" +
      "  var first = new goog.Forgot();" +
      "  var second = new goog.Forgot();" +
      "};"};
    String warning = "'goog.Forgot' used but not goog.require'd";
    test(js, js, null, MISSING_REQUIRE_WARNING, warning);
  }

  public void testVarConstructorName() {
    String js = "/** @type {function(new:Date)} */var bar = Date;" +
        "new bar();";
    testSame(js);
  }

  public void testVarConstructorFunction() {
    String js = "/** @type {function(new:Date)} */var bar = function() {};" +
        "new bar();";
    testSame(js);
  }

  public void testAssignConstructorName() {
    String js = "var foo = {};" +
        "/** @type {function(new:Date)} */foo.bar = Date;" +
        "new foo.bar();";
    testSame(js);
  }

  public void testAssignConstructorFunction() {
    String js = "var foo = {};" +
        "/** @type {function(new:Date)} */foo.bar = function() {};" +
        "new foo.bar();";
    testSame(js);
  }

  public void testConstructorFunctionReference() {
    String js = "/** @type {function(new:Date)} */function bar() {}" +
        "new bar();";
    testSame(js);
  }
}
