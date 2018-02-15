/*
 * Copyright 2007 The Closure Compiler Authors.
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

public final class InlineSimpleMethodsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new InlineSimpleMethods(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableNormalize();
  }

  /**
   * Helper for tests that expects definitions to remain unchanged, such
   * that {@code definitions+js} is converted to {@code definitions+expected}.
   */
  private void testWithPrefix(String definitions, String js, String expected) {
    test(definitions + js, definitions + expected);
  }

  public void testSimpleInline1() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){return this.baz};",
        "var x=(new Foo).bar();var y=(new Foo).bar();",
        "var x=(new Foo).baz;var y=(new Foo).baz");
  }

  public void testSimpleInline2() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype={bar:function(){return this.baz}};",
        "var x=(new Foo).bar();var y=(new Foo).bar();",
        "var x=(new Foo).baz;var y=(new Foo).baz");
  }

  public void testSimpleGetterInline1() {
    // TODO(johnlenz): Support this case.
    testSame("function Foo(){}" +
      "Foo.prototype={get bar(){return this.baz}};" +
      "var x=(new Foo).bar;var y=(new Foo).bar");
    // Verify we are not confusing calling the result of an ES5 getter
    // with call the getter.
    testSame("function Foo(){}" +
      "Foo.prototype={get bar(){return this.baz}};" +
      "var x=(new Foo).bar();var y=(new Foo).bar()");
  }

  public void testSimpleSetterInline1() {
    // Verify 'get' and 'set' are not confused.
    testSame("function Foo(){}" +
      "Foo.prototype={set bar(a){return this.baz}};" +
      "var x=(new Foo).bar;var y=(new Foo).bar");
    testSame("function Foo(){}" +
      "Foo.prototype={set bar(a){return this.baz}};" +
      "var x=(new Foo).bar();var y=(new Foo).bar()");
  }

  public void testSelfInline() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){return this.baz};",
        "Foo.prototype.meth=function(){this.bar();}",
        "Foo.prototype.meth=function(){this.baz}");
  }

  public void testCallWithArgs() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){return this.baz};",
        "var x=(new Foo).bar(3,new Foo)",
        "var x=(new Foo).bar(3,new Foo)");
  }

  public void testCallWithConstArgs() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(a){return this.baz};",
        "var x=(new Foo).bar(3, 4)",
        "var x=(new Foo).baz");
  }

  public void testNestedProperties() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){return this.baz.ooka};",
        "(new Foo).bar()",
        "(new Foo).baz.ooka");
  }

  public void testSkipComplexMethods() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){return this.baz};" +
        "Foo.prototype.condy=function(){return this.baz?this.baz:1};",
        "var x=(new Foo).argy()",
        "var x=(new Foo).argy()");
  }

  public void testSkipConflictingMethods() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){return this.baz};" +
        "Foo.prototype.bar=function(){return this.bazz};",
        "var x=(new Foo).bar()",
        "var x=(new Foo).bar()");
  }

  public void testSameNamesDifferentDefinitions() {
    testWithPrefix("function A(){}" +
        "A.prototype.g=function(){return this.a};" +
        "function B(){}" +
        "B.prototype.g=function(){return this.b};",
        "var x=(new A).g();" +
        "var y=(new B).g();" +
        "var a=new A;" +
        "var ag=a.g();",
        "var x=(new A).g();" +
        "var y=(new B).g();" +
        "var a=new A;" +
        "var ag=a.g()");
  }

  public void testSameNamesSameDefinitions() {
    testWithPrefix("function A(){}" +
        "A.prototype.g=function(){return this.a};" +
        "function B(){}" +
        "B.prototype.g=function(){return this.a};",
        "var x=(new A).g();" +
        "var y=(new B).g();" +
        "var a=new A;" +
        "var ag=a.g();",
        "var x=(new A).a;" +
        "var y=(new B).a;" +
        "var a=new A;" +
        "var ag=a.a");
  }

  public void testConfusingNames() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){return this.baz};",
        "function bar(){var bar=function(){};bar()}",
        "function bar(){var bar=function(){};bar()}");
  }

  public void testConstantInline() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){return 3};",
        "var f=new Foo;var x=f.bar()",
        "var f=new Foo;var x=3");
  }

  public void testConstantArrayInline() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){return[3,4]};",
        "var f=new Foo;var x=f.bar()",
        "var f=new Foo;var x=[3,4]");
  }

  public void testConstantInlineWithSideEffects() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){return 3};",
        "var x=(new Foo).bar()",
        "var x=(new Foo).bar()");
  }

  public void testEmptyMethodInline() {
    testWithPrefix(
        "function Foo(){} Foo.prototype.bar=function(a){};",
        "var x = new Foo(); x.bar();",
        "var x = new Foo();;");
  }

  public void testEmptyMethodInlineWithSideEffects() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){};",
        "(new Foo).bar();var y=new Foo;y.bar(new Foo)",
        "(new Foo).bar();var y=new Foo;y.bar(new Foo)");
  }

  public void testEmptyMethodInlineInAssign1() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){};",
        "var x=new Foo;var y=x.bar()",
        "var x=new Foo;var y=void 0");
  }

  public void testEmptyMethodInlineInAssign2() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){};",
        "var x=new Foo;var y=x.bar().toString()",
        "var x=new Foo;var y=(void 0).toString()");
  }

  public void testNormalMethod() {
    testWithPrefix("function Foo(){}" +
        "Foo.prototype.bar=function(){var x=1};",
        "var x=new Foo;x.bar()",
        "var x=new Foo;x.bar()");
  }

  public void testNoInlineOfExternMethods1() {
    testSame(externs("var external={};external.charAt;"), srcs("external.charAt()"));
  }

  public void testNoInlineOfExternMethods2() {
    testSame(externs("var external={};external.charAt=function(){};"), srcs("external.charAt()"));
  }

  public void testNoInlineOfExternMethods3() {
    testSame(
        externs("var external={};external.bar=function(){};"),
        srcs("function Foo(){}Foo.prototype.bar=function(){};(new Foo).bar()"));
  }

  public void testNoInlineOfDangerousProperty() {
    testSame("function Foo(){this.bar=3}" +
        "Foo.prototype.bar=function(){};" +
        "var x=new Foo;var y=x.bar()");
  }

  // Don't warn about argument naming conventions (this is done in another pass)
  //   opt_ parameters must not be followed by non-optional parameters.
  //   var_args must be last
  public void testNoWarn() {
    testSame("function Foo(){}" +
        "Foo.prototype.bar=function(opt_a,b){var x=1};" +
        "var x=new Foo;x.bar()");

    testSame("function Foo(){}" +
        "Foo.prototype.bar=function(var_args,b){var x=1};" +
        "var x=new Foo;x.bar()");
  }

  public void testObjectLit() {
    testSame("Foo.prototype.bar=function(){return this.baz_};" +
             "var blah={bar:function(){}};" +
             "(new Foo).bar()");
  }

  public void testObjectLit2() {
    testSame("var blah={bar:function(){}};" +
             "(new Foo).bar()");
  }

  public void testObjectLit3() {
    testSame("var blah={bar(){}};"
        + "(new Foo).bar()");
  }

  public void testObjectLit4() {
    testSame("var key='bar';"
        + "var blah={[key]:a};"
        + "(new Foo).bar");
  }

  public void testObjectLitExtern1() {
    String externs = "window.bridge={_sip:function(){}};";
    testSame(externs(externs), srcs("window.bridge._sip()"));
  }

  public void testObjectLitExtern2() {
    String externs = "window.bridge={_sip(){}};";
    testSame(externs(externs), srcs("window.bridge._sip()"));
  }

  public void testClassExtern() {
    String externs = "window.bridge= class { _sip() {} };";
    testSame(externs(externs), srcs("window.bridge._sip()"));
  }

  public void testExternFunction() {
    String externs = "function emptyFunction() {}";
    testSame(
        externs(externs), srcs("function Foo(){this.empty=emptyFunction}" + "(new Foo).empty()"));
  }

  public void testIssue2508576_1() {
    // Method defined by an extern should be left alone.
    String externs = "function alert(a) {}";
    testSame(externs(externs), srcs("({a:alert,b:alert}).a('a')"));
  }

  public void testIssue2508576_2() {
    // Anonymous object definition with a side-effect should be left alone.
    testSame("({a:function(){},b:x()}).a('a')");
  }

  public void testIssue2508576_3() {
    // Anonymous object definition without side-effect should be removed.
    test("({a:function(){},b:alert}).a('a')", ";");
  }

  // When there are two methods with the same name, one on an ES3-style class, and one on an
  // ES6-style class, make sure the right one gets inlined into the right place, if inlining happens
  // at all.
  public void testEs6Issue1() {
    testSame(
        lines(
            "/** @constructor */",
            "function OldClass() {}",
            "",
            "OldClass.prototype.foo = function() { return this.oldbar; };",
            "",
            "class NewClass {",
            "  foo() { return this.newbar; }",
            "}",
            "",
            "var x = new OldClass;",
            "x.foo();",
            "x = new NewClass;",
            "x.foo();"));
  }

  public void testEs6Issue2() {
    testSame(
        lines(
            "/** @constructor */",
            "function OldClass() {}",
            "",
            "OldClass.prototype.foo = function() { return this.oldbar; };",
            "",
            "class NewClass {",
            "  foo() { return this.newbar; }",
            "}",
            "",
            "var x = new OldClass;",
            "x.foo();",
            "var y = new NewClass;",
            "y.foo();"));
  }

  public void testAnonymousGet() {
    // Anonymous object definition without side-effect should be removed.
    testSame("({get a(){return function(){}},b:alert}).a('a')");
    testSame("({get a(){},b:alert}).a('a')");
    testSame("({get a(){},b:alert}).a");
  }

  public void testAnonymousSet() {
    // Anonymous object definition without side-effect should be removed.
    testSame("({set a(b){return function(){}},b:alert}).a('a')");
    testSame("({set a(b){},b:alert}).a('a')");
    testSame("({set a(b){},b:alert}).a");
  }

  public void testInlinesEvenIfClassEscapes() {
    // The purpose of this test is to record an unsafe assumption made by the
    // pass. In practice, it's usually safe to inline even if the class
    // escapes, because method definitions aren't commonly mutated.
    test(
        externs("var esc;"),
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {",
                "  this.prop = 123;",
                "}",
                "Foo.prototype.m = function() {",
                "  return this.prop;",
                "}",
                "(new Foo).m();",
                "esc(Foo);")),
        expected(
            lines(
                "/** @constructor */",
                "function Foo(){this.prop=123}",
                "Foo.prototype.m=function(){return this.prop}",
                "(new Foo).m();",
                "esc(Foo)")));
  }
}
