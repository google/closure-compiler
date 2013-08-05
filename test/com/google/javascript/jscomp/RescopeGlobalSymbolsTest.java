/*
 * Copyright 2011 The Closure Compiler Authors.
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

/**
 * Unit tests for {@link RescopeGlobalSymbols}
 *
 */
public class RescopeGlobalSymbolsTest extends CompilerTestCase {

  private static final String NAMESPACE = "_";

  private boolean assumeCrossModuleNames = true;

  public RescopeGlobalSymbolsTest() {
  }

  @Override protected CompilerPass getProcessor(Compiler compiler) {
    return new RescopeGlobalSymbols(
        compiler,
        NAMESPACE,
        false,
        assumeCrossModuleNames);
  }

  @Override public void setUp() throws Exception {
    super.setUp();
    assumeCrossModuleNames = true;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testVarDeclarations() {
    test("var a = 1;", "_.a = 1;");
    test("var a = 1, b = 2, c = 3;", "_.a = 1; _.b = 2; _.c = 3;");
    test(
        "var a = 'str', b = 1, c = { foo: 'bar' }, d = function() {};",
        "_.a = 'str'; _.b = 1; _.c = { foo: 'bar' }; _.d = function() {};");
    test("if(1){var x = 1;}", "if(1){_.x = 1;}");
    test("var x;", "");
    test("var a, b = 1;", "_.b = 1");
  }

  public void testVarDeclarations_allSameModule() {
    assumeCrossModuleNames = false;
    testSame("var a = 1;");
    testSame("var a = 1, b = 2, c = 3;");
    testSame("var a = 'str', b = 1, c = { foo: 'bar' }, d = function() {};");
    testSame("if(1){var x = 1;}");
    testSame("var x;");
    testSame("var a, b = 1;");
  }

  public void testVarDeclarations_acrossModules() {
    assumeCrossModuleNames = false;
    test(createModules(
        "var a = 1;", "a"),
        new String[] {"_.a = 1", "_.a"});
    test(createModules(
        "var a = 1, b = 2, c = 3;", "a;c;"),
        new String[] {"var b;_.a = 1; b = 2; _.c = 3;", "_.a;_.c"});
    test(createModules(
        "var a = 1, b = 2, c = 3;", "b;c;"),
        new String[] {"var a;a = 1; _.b = 2; _.c = 3;", "_.b;_.c"});
    test(createModules(
        "var a = 1, b = 2, c = 3;b;c;", "a;c;"),
        new String[] {"var b;_.a = 1; b = 2; _.c = 3;b;_.c", "_.a;_.c"});
    test(createModules(
        "var a, b = 1;", "b"),
        new String[] {"var a;_.b = 1;", "_.b"});
  }

  public void testForLoops() {
    assumeCrossModuleNames = false;
    test(createModules(
        "for (var i = 0, c = 2; i < 1000; i++);", "c"),
        new String[] {"var i;for (i = 0, _.c = 2; i < 1000; i++);", "_.c"});
    test(createModules(
        "for (var i = 0, c = 2; i < 1000; i++);", "i"),
        new String[] {"var c;for (_.i = 0, c = 2; _.i < 1000; _.i++);",
            "_.i"});
  }

  public void testForLoops_acrossModules() {
    test(
        "for (var i = 0; i < 1000; i++);",
        "for (_.i = 0; _.i < 1000; _.i++);");
    test(
        "for (var i = 0, c = 2; i < 1000; i++);",
        "for (_.i = 0, _.c = 2; _.i < 1000; _.i++);");
    test(
        "for (var i = 0, c = 2, d = 3; i < 1000; i++);",
        "for (_.i = 0, _.c = 2, _.d = 3; _.i < 1000; _.i++);");
    test(
        "for (var i = 0, c = 2, d = 3, e = 4; i < 1000; i++);",
        "for (_.i = 0, _.c = 2, _.d = 3, _.e = 4; _.i < 1000; _.i++);");
    test(
        "for (var i = 0; i < 1000;)i++;",
        "for (_.i = 0; _.i < 1000;)_.i++;");
    test(
        "for (var i = 0,b; i < 1000;)i++;b++",
        "for (_.i = 0,_.b; _.i < 1000;)_.i++;_.b++");
    test(
        "var o={};for (var i in o)i++;",
        "_.o={};for (_.i in _.o)_.i++;");
  }

  public void testFunctionStatements() {
    test(
        "function test(){}",
        "_.test=function (){}");
    test(
        "if(1)function test(){}",
        "if(1)_.test=function (){}");
    new StringCompare().testFreeCallSemantics();
  }

  public void testDeeperScopes() {
    test(
        "var a = function(b){return b}",
        "_.a = function(b){return b}");
    test(
        "var a = function(b){var a; return a+b}",
        "_.a = function(b){var a; return a+b}");
    test(
        "var a = function(a,b){return a+b}",
        "_.a = function(a,b){return a+b}");
    test(
        "var x=1,a = function(b){var a; return a+b+x}",
        "_.x=1;_.a = function(b){var a; return a+b+_.x}");
    test(
        "var x=1,a = function(b){return function(){var a;return a+b+x}}",
        "_.x=1;_.a = function(b){return function(){var a; return a+b+_.x}}");
  }

  public void testTryCatch() {
    test(
        "try{var a = 1}catch(e){throw e}",
        "try{_.a = 1}catch(e){throw e}");
  }

  public void testShadow() {
    test(
        "var _ = 1; (function () { _ = 2 })()",
        "_._ = 1; (function () { _._ = 2 })()");
    test(
        "function foo() { var _ = {}; _.foo = foo; _.bar = 1; }",
        "_.foo = function () { var _$ = {}; _$.foo = _.foo; _$.bar = 1}");
    test(
        "function foo() { var _ = {}; _.foo = foo; _.bar = 1; "
        + "(function() { var _ = 0;})() }",
        "_.foo = function () { var _$ = {}; _$.foo = _.foo; _$.bar = 1; "
        + "(function() { var _$ = 0;})() }");
    test(
        "function foo() { var _ = {}; _.foo = foo; _.bar = 1; "
        + "var _$ = 1; }",
        "_.foo = function () { var _$ = {}; _$.foo = _.foo; _$.bar = 1; "
        + "var _$$ = 1; }");
    test(
        "function foo() { var _ = {}; _.foo = foo; _.bar = 1; "
        + "var _$ = 1; (function() { _ = _$ })() }",
        "_.foo = function () { var _$ = {}; _$.foo = _.foo; _$.bar = 1; "
        + "var _$$ = 1; (function() { _$ = _$$ })() }");
    test(
        "function foo() { var _ = {}; _.foo = foo; _.bar = 1; "
        + "var _$ = 1, _$$ = 2 (function() { _ = _$ = _$$; " +
        "var _$, _$$$ })() }",
        "_.foo = function () { var _$ = {}; _$.foo = _.foo; _$.bar = 1; "
        + "var _$$ = 1, _$$$ = 2 (function() { _$ = _$$ = _$$$; "
        + "var _$$, _$$$$ })() }");
    test(
        "function foo() { var _a = 1;}",
        "_.foo = function () { var _a = 1;}");
    // We accept this unnecessary renaming as acceptable to simplify pattern
    // matching in the traversal.
    test(
        "function foo() { var _$a = 1;}",
        "_.foo = function () { var _$a$ = 1;}");
  }

  public void testExterns() {
    test(
        "var document;",
        "document",
        "window.document", null, null);
    test(
        "var document;",
        "document.getElementsByTagName('test')",
        "window.document.getElementsByTagName('test')", null, null);
    test(
        "var document;",
        "window.document.getElementsByTagName('test')",
        "window.document.getElementsByTagName('test')", null, null);
    test(
        "var document;document.getElementsByTagName",
        "document.getElementsByTagName('test')",
        "window.document.getElementsByTagName('test')", null, null);
    test(
        "var document,navigator",
        "document.navigator;navigator",
        "window.document.navigator;window.navigator", null, null);
    test(
        "var iframes",
        "function test() { iframes.resize(); }",
        "_.test = function() { window.iframes.resize(); }", null, null);
    test(
        "var iframes",
        "var foo = iframes;",
        "_.foo = window.iframes;", null, null);
    // Special names.
    test(
        "var arguments, window, eval;",
        "arguments;window;eval;",
        "arguments;window;eval;", null, null);
    // Actually not an extern.
    test(
        "",
        "document",
        "window.document", null, null);
    // Javascript builtin objects
    test(
        "Object;Function;Array;String;Boolean;Number;Math;"
        + "Date;RegExp;JSON;Error;EvalError;ReferenceError;"
        + "SyntaxError;TypeError;URIError;",
        "Object;Function;Array;String;Boolean;Number;Math;"
        + "Date;RegExp;JSON;Error;EvalError;ReferenceError;"
        + "SyntaxError;TypeError;URIError;");
  }

  private class StringCompare extends CompilerTestCase {

    StringCompare() {
      super("", false);
    }

    @Override protected CompilerPass getProcessor(Compiler compiler) {
      return new RescopeGlobalSymbols(
          compiler,
          NAMESPACE,
          false,
          assumeCrossModuleNames);
    }

    public void testFreeCallSemantics() {
      test(
          "function x(){};var y=function(){var val=x()||{}}",
          "_.x=function(){};_.y=function(){var val=(0,_.x)()||{}}");
      test(
          "function x(){x()}",
          "_.x=function(){(0,_.x)()}");
    }
  }
}
