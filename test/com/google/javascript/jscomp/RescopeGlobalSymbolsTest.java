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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link RescopeGlobalSymbols}
 *
 */
@RunWith(JUnit4.class)
public final class RescopeGlobalSymbolsTest extends CompilerTestCase {

  private static final String NAMESPACE = "_";

  private boolean assumeCrossModuleNames = true;

  @Override protected CompilerPass getProcessor(Compiler compiler) {
    return new RescopeGlobalSymbols(
        compiler,
        NAMESPACE,
        false,
        assumeCrossModuleNames);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    assumeCrossModuleNames = true;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testVarDeclarations() {
    test("var a = 1;", "_.a = 1;");
    test("var a = 1, b = 2, c = 3;", "_.a = 1; _.b = 2; _.c = 3;");
    test(
        "var a = 'str', b = 1, c = { foo: 'bar' }, d = function() {};",
        "_.a = 'str'; _.b = 1; _.c = { foo: 'bar' }; _.d = function() {};");
    test("if(1){var x = 1;}", "if(1){_.x = 1;}");
    test("var x;", "");
    test("var x; alert(x)", "window.alert(_.x);");
    test("var a, b = 1;", "_.b = 1");
    test("var a, b = 1; alert(a);", "_.b = 1; window.alert(_.a);");
  }

  @Test
  public void testVarDeclarations_allSameModule() {
    assumeCrossModuleNames = false;
    testSame("var a = 1;");
    testSame("var a = 1, b = 2, c = 3;");
    testSame("var a = 'str', b = 1, c = { foo: 'bar' }, d = function() {};");
    testSame("if(1){var x = 1;}");
    testSame("var x;");
    testSame("var a, b = 1;");
  }

  @Test
  public void testVarDeclarations_export() {
    assumeCrossModuleNames = false;
    test("var _dumpException = 1;", "_._dumpException = 1");
  }

  @Test
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
    test(createModules(
        "var a, b = 1, c = 2;", "b"),
        new String[] {"var a, c;_.b = 1;c = 2", "_.b"});
    test(createModules(
        "var a, b = 1, c = 2;", "a"),
        new String[] {"var b, c;b = 1;c = 2", "_.a"});
    test(createModules(
        "var a=1; var b=2,c=3;", "a;c;"),
        new String[] {"var b;_.a=1;b=2;_.c=3", "_.a;_.c"});
    test(createModules(
        "1;var a, b = 1, c = 2;", "b"),
        new String[] {"var a, c;1;_.b = 1;c = 2", "_.b"});
  }

  @Test
  public void testLetDeclarations() {
    test("let a = 1;", "_.a = 1;");
    test("let a = 1, b = 2, c = 3;", "_.a = 1; _.b = 2; _.c = 3;");
    test(
        "let a = 'str', b = 1, c = { foo: 'bar' }, d = function() {};",
        "_.a = 'str'; _.b = 1; _.c = { foo: 'bar' }; _.d = function() {};");
    testSame("if(1){let x = 1;}");
    test("let x;", "");
    test("let x; alert(x);", "window.alert(_.x);");
    test("let a, b = 1;", "_.b = 1");
    test("let a, b = 1; alert(a);", "_.b = 1; window.alert(_.a);");
  }

  @Test
  public void testLetDeclarations_allSameModule() {
    assumeCrossModuleNames = false;
    testSame("let a = 1;");
    testSame("let a = 1, b = 2, c = 3;");
    testSame("let a = 'str', b = 1, c = { foo: 'bar' }, d = function() {};");
    testSame("if(1){let x = 1;}");
    testSame("let x;");
    testSame("let a, b = 1;");
  }

  @Test
  public void testLetDeclarations_export() {
    assumeCrossModuleNames = false;
    test("let _dumpException = 1;", "_._dumpException = 1");
  }

  @Test
  public void testLetDeclarations_acrossModules() {
    assumeCrossModuleNames = false;
    // test references across modules.
    test(createModules("let a = 1;", "a"), new String[] {"_.a = 1", "_.a"});
    test(
        createModules("let a = 1, b = 2, c = 3;", "a;c;"),
        new String[] {"var b;_.a = 1; b = 2; _.c = 3;", "_.a;_.c"});
    test(
        createModules("let a = 1, b = 2, c = 3;", "b;c;"),
        new String[] {"var a;a = 1; _.b = 2; _.c = 3;", "_.b;_.c"});
    test(
        createModules("let a = 1, b = 2, c = 3;b;c;", "a;c;"),
        new String[] {"var b;_.a = 1; b = 2; _.c = 3;b;_.c", "_.a;_.c"});
    test(createModules("let a, b = 1;", "b"), new String[] {"var a;_.b = 1;", "_.b"});
    test(
        createModules("let a, b = 1, c = 2;", "b"), new String[] {"var a, c;_.b = 1;c = 2", "_.b"});
    test(createModules("let a, b = 1, c = 2;", "a"), new String[] {"var b, c;b = 1;c = 2", "_.a"});
    test(
        createModules("let a=1; let b=2,c=3;", "a;c;"),
        new String[] {"var b;_.a=1;b=2;_.c=3", "_.a;_.c"});
    test(
        createModules("1;let a, b = 1, c = 2;", "b"),
        new String[] {"var a, c;1;_.b = 1;c = 2", "_.b"});
    // test non-globals with same name as cross-module globals.
    testSame(createModules("1;let a, b = 1, c = 2;", "if (true) { let b = 3; b; }"));
    test(
        createModules("1;let a, b = 1, c = 2;", "b; if (true) { let b = 3; b; }"),
        new String[] {"var a, c; 1;_.b = 1;c = 2", "_.b; if (true) { let b = 3; b; }"});
  }

  @Test
  public void testConstDeclarations() {
    test("const a = 1;", "_.a = 1;");
    test("const a = 1, b = 2, c = 3;", "_.a = 1; _.b = 2; _.c = 3;");
    test(
        "const a = 'str', b = 1, c = { foo: 'bar' }, d = function() {};",
        "_.a = 'str'; _.b = 1; _.c = { foo: 'bar' }; _.d = function() {};");
    testSame("if(1){const x = 1;}");
  }

  @Test
  public void testConstDeclarations_allSameModule() {
    assumeCrossModuleNames = false;
    testSame("const a = 1;");
    testSame("const a = 1, b = 2, c = 3;");
    testSame("const a = 'str', b = 1, c = { foo: 'bar' }, d = function() {};");
    testSame("if(1){const x = 1;}");
  }

  @Test
  public void testConstDeclarations_export() {
    assumeCrossModuleNames = false;
    test("const _dumpException = 1;", "_._dumpException = 1");
  }

  @Test
  public void testConstDeclarations_acrossModules() {
    assumeCrossModuleNames = false;
    // test references across modules.
    test(createModules("const a = 1;", "a"), new String[] {"_.a = 1", "_.a"});
    test(
        createModules("const a = 1, b = 2, c = 3;", "a;c;"),
        new String[] {"var b;_.a = 1; b = 2; _.c = 3;", "_.a;_.c"});
    test(
        createModules("const a = 1, b = 2, c = 3;", "b;c;"),
        new String[] {"var a;a = 1; _.b = 2; _.c = 3;", "_.b;_.c"});
    test(
        createModules("const a = 1, b = 2, c = 3;b;c;", "a;c;"),
        new String[] {"var b;_.a = 1; b = 2; _.c = 3;b;_.c", "_.a;_.c"});
    test(
        createModules("const a=1; const b=2,c=3;", "a;c;"),
        new String[] {"var b;_.a=1;b=2;_.c=3", "_.a;_.c"});
    // test non-globals with same name as cross-module globals.
    testSame(createModules("1;const a = 1, b = 1, c = 2;", "if (true) { const b = 3; b; }"));
    test(
        createModules("1;const a = 1, b = 1, c = 2;", "b; if (true) { const b = 3; b; }"),
        new String[] {"var a, c; 1;a = 1; _.b = 1;c = 2", "_.b; if (true) { const b = 3; b; }"});
  }

  @Test
  public void testObjectDestructuringDeclarations() {
    test("var {a} = {}; a;", "({a: _.a} = {}); _.a;");
    test("var {a: a} = {}; a;", "({a: _.a} = {}); _.a;");
    test("var {key: a} = {}; a;", "({key: _.a} = {}); _.a;");
    test("var {a: {b}} = {}; b;", "({a: {b: _.b}} = {}); _.b;");
    test("var {a: {key: b}} = {}; b;", "({a: {key: _.b}} = {}); _.b;");
    test("var {['computed']: a} = {}; a;", "({['computed']: _.a} = {}); _.a;");

    test("var {a = 3} = {}; a;", "({a: _.a = 3} = {}); _.a");
    test("var {key: a = 3} = {}; a;", "({key: _.a = 3} = {}); _.a");
    test("var {a} = {}, [b] = [], c = 3", "({a: _.a} = {}); [_.b] = []; _.c = 3;");
  }

  @Test
  public void testObjectDestructuringDeclarations_allSameModule() {
    assumeCrossModuleNames = false;
    testSame("var {a} = {}; a;");
    testSame("var {a: a} = {}; a;");
    testSame("var {key: a} = {}; a;");
    testSame("var {a: {b: b}} = {}; b;");
    testSame("var {['computed']: a} = {}; a;");
    testSame("var {a: a = 3} = {}; a;");
    testSame("var {key: a = 3} = {}; a;");
    testSame("var {['computed']: a = 3} = {}; a;");
    testSame("var {a} = {}, b = 3;");
    testSame("var [a] = [], b = 3;");
    testSame("var a = 1, [b] = [], {c} = {};");
  }

  @Test
  public void testObjectDestructuringDeclarations_acrossModules() {
    assumeCrossModuleNames = false;
    test(createModules("var {a: a} = {};", "a"), new String[] {"({a: _.a} = {});", "_.a"});
    test(
        createModules("var {a: a, b: b, c: c} = {};", "a; c;"),
        new String[] {"var b;({a: _.a, b: b, c: _.c} = {});", "_.a; _.c"});
    test(
        createModules("var {a: a, b: b, c: c} = {};", "b; c;"),
        new String[] {"var a;({a: a, b: _.b, c: _.c} = {});", "_.b; _.c"});
    test(
        createModules("var {a: a, b: b, c: c} = {}; b; c;", "a; c;"),
        new String[] {"var b;({a: _.a, b: b, c: _.c} = {});b;_.c;", "_.a; _.c"});

    // Test var declarations containing a mix of destructuring and regular names
    test(createModules("var {a} = {}, b;", "a"), new String[] {"var b; ({a: _.a} = {});", "_.a"});

    test(
        createModules("var {a} = {}, b = 3;", "b"),
        new String[] {"var a; ({a} = {}); _.b = 3;", "_.b"});
  }

  @Test
  public void testObjectDestructuringAssignments() {
    test("var a, b; ({key1: a, key2: b} = {}); a; b;", "({key1: _.a, key2: _.b} = {}); _.a; _.b;");
    // Test a destructuring assignment with mixed global and local variables.
    test(
        "var a; if (true) { let b; ({key1: a, key2: b} = {}); b; } a;",
        "if (true) { let b; ({key1: _.a, key2: b} = {}); b; } _.a;");
    test("var obj = {}; ({a: obj.a} = {}); obj.a;", "_.obj = {}; ({a: _.obj.a} = {}); _.obj.a;");
    test(
        "var obj = {}; ({a:   obj['foo bar']} = {});   obj['foo bar'];",
        "  _.obj = {}; ({a: _.obj['foo bar']} = {}); _.obj['foo bar'];");
  }

  @Test
  public void testArrayDestructuringDeclarations() {
    test("var [a] = [1]; a", "[_.a] = [1]; _.a;");
    test("var [a, b, c] = [1, 2, 3]; a; b; c;", "[_.a, _.b, _.c] = [1, 2, 3]; _.a; _.b; _.c");
    test("var [[a, b], c] = []; a; b; c", "[[_.a, _.b], _.c] = []; _.a; _.b; _.c;");
    test("var [a = 5] = [1]; a", "[_.a = 5] = [1]; _.a;");
    test("var [a, b = 5] = [1]; a; b;", "[_.a, _.b = 5] = [1]; _.a; _.b;");
    test("var [...a] = [1, 2, 3]; a;", "[..._.a] = [1, 2, 3]; _.a;");
    test("var [a, ...b] = [1, 2, 3]; a; b;", "[_.a, ..._.b] = [1, 2, 3]; _.a; _.b;");
    test("var [a] = 1, b = 2; a; b;", "[_.a] = 1; _.b = 2; _.a; _.b;");
  }

  @Test
  public void testArrayDestructuringDeclarations_sameModule() {
    assumeCrossModuleNames = false;
    testSame("var [a] = [1]; a");
    testSame("var [a, b] = [1, 2]; a; b;");
    testSame("var [[a, b], c] = []; a; b; c");
    testSame("var [a = 5] = [1]; a");
    testSame("var [a, b = 5] = [1]; a; b;");
    testSame("var [...a] = [1, 2, 3]; a;");
    testSame("var [a, ...b] = [1, 2, 3]; a; b;");
  }

  @Test
  public void testArrayDestructuringDeclarations_acrossModules() {
    assumeCrossModuleNames = false;
    test(createModules("var [a] = [];", "a"), new String[] {"[_.a] = [];", "_.a"});
    test(
        createModules("var [a, b, c] = [];", "a; c;"),
        new String[] {"var b; [_.a, b, _.c] = [];", "_.a; _.c"});
    test(
        createModules("var [a, b, c] = [];", "b; c;"),
        new String[] {"var a; [a, _.b, _.c] = [];", "_.b; _.c"});
    test(
        createModules("var [a, b, c] = []; b; c;", "a; c;"),
        new String[] {"var b; [_.a, b, _.c] = []; b; _.c;", "_.a; _.c"});
  }

  @Test
  public void testArrayDestructuringAssignments() {
    test("var a, b; [a, b] = []; a; b;", "[_.a, _.b] = []; _.a; _.b;");
    // Test a destructuring assignment with mixed global and local variables.
    test(
        "var a; if (true) { let b; [a, b] = []; b; } a;",
        "if (true) { let b; [_.a, b] = []; b; } _.a;");
    // Test assignments to qualified names and quoted properties.
    test("var obj = {}; [obj.a] = []; obj.a;", "_.obj = {}; [_.obj.a] = []; _.obj.a;");
    test(
        "var obj = {}; [  obj['foo bar']] = [];   obj['foo bar'];",
        "  _.obj = {}; [_.obj['foo bar']] = []; _.obj['foo bar'];");
  }

  @Test
  public void testClasses() {
    test("class A {}", "_.A = class {};");
    test("class A {} class B extends A {}", "_.A = class {}; _.B = class extends _.A {}");
    test("class A {} let a = new A;", "_.A = class {}; _.a = new _.A;");
    test(
        lines(
            "const PI = 3.14;",
            "class A {",
            "  static printPi() {",
            "    console.log(PI);",
            "  }",
            "}",
            "A.printPi();"),
        lines(
            "_.PI = 3.14;",
            "_.A = class {",
            "  static printPi() {",
            "    window.console.log(_.PI);",
            "  }",
            "}",
            "_.A.printPi();"));

    // Test that class expression names are not rewritten.
    test("var A = class Name {};", "_.A = class Name {};");
    test("var A = class A {};", "_.A = class A {};");
  }

  @Test
  public void testClasses_nonGlobal() {
    testSame("if (true) { class A {} }");
    test("function foo() { class A {} }", "_.foo = function() { class A {} };");
    test("const A = 5; { class A {} }", "_.A = 5; { class A {} }");
  }

  @Test
  public void testClasses_allSameModule() {
    assumeCrossModuleNames = false;
    test("class A {}", "var A = class {};");
    test("class A {} class B extends A {}", "var A = class {}; var B = class extends A {}");
    testSame("if (true) { class A {} }");
  }

  @Test
  public void testForLoops() {
    assumeCrossModuleNames = false;
    test(createModules(
        "for (var i = 0, c = 2; i < 1000; i++);", "c"),
        new String[] {"var i;for (i = 0, _.c = 2; i < 1000; i++);", "_.c"});
    test(
        createModules("      for (var i = 0, c = 2;   i < 1000;   i++);", "i"),
        new String[] {"var c;for (  _.i = 0, c = 2; _.i < 1000; _.i++);", "_.i"});
    test(
        createModules("       for (var {i: i, c:   c} = {};  i < 1000; i++);", "c"),
        new String[] {"var i; for (   ({i: i, c: _.c} = {}); i < 1000; i++);", "_.c"});
    test(
        createModules("       for (var [i,   c] = [0, 2]; i < 1000; i++);", "c;"),
        new String[] {"var i; for (    [i, _.c] = [0, 2]; i < 1000; i++);", "_.c;"});
  }

  @Test
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
    test("var o={};for (var i in o)i++;", "_.o={};for (_.i in _.o)_.i++;");

    // Test destructuring.
    test("for (var [i] = [0]; i < 1000; i++);", "for ([_.i] = [0]; _.i < 1000; _.i++);");
    test("for (var {i: i} = {}; i < 1000; i++);", " for (({i: _.i} = {}); _.i < 1000; _.i++);");
    testSame("for (let [i] = [0]; i  < 1000; i++);");
  }

  @Test
  public void testForInLoops_allSameModule() {
    assumeCrossModuleNames = false;
    testSame("for (var i in {});");
    testSame("for (var [a] in {});");
    testSame("for (var {a: a} in {});");
  }

  @Test
  public void testForInLoops_acrossModules() {
    assumeCrossModuleNames = false;
    test(createModules("for (var i in {});", "i"), new String[] {"for (_.i in {});", "_.i"});
    test(
        createModules("       for (var [  a, b] in {});", "a;"),
        new String[] {"var b; for (    [_.a, b] in {});", "_.a"});
    test(
        createModules("       for (var {i: i, c:   c} in {});", "c"),
        new String[] {"var i; for (    {i: i, c: _.c} in {});", "_.c"});
  }

  @Test
  public void testForOfLoops_allSameModule() {
    assumeCrossModuleNames = false;
    testSame("for (var i of [1, 2, 3]);");
    testSame("for (var [a] of []);");
    testSame("for (var {a: a} of {});");
  }

  @Test
  public void testForOfLoops_acrossModules() {
    assumeCrossModuleNames = false;
    test(createModules("for (var i of []);", "i"), new String[] {"for (_.i of []);", "_.i"});
    test(
        createModules("       for (var [  a, b] of []);", "a;"),
        new String[] {"var b; for (    [_.a, b] of []);", "_.a"});
    test(
        createModules("       for (var {i: i, c:   c} of []);", "c"),
        new String[] {"var i; for (    {i: i, c: _.c} of []);", "_.c"});
  }

  @Test
  public void testFunctionStatements() {
    test(
        "function test(){}",
        "_.test=function (){}");
    test(
        "if(1)function test(){}",
        "if(1)_.test=function (){}");
    test("async function test() {}", "_.test = async function() {}");
    test("function *test() {}", "_.test = function *() {}");
  }

  @Test
  public void testFunctionStatements_allSameModule() {
    assumeCrossModuleNames = false;
    test("function f() {}", "var f = function() {}");
    test("if (true) { function f() {} }", "if (true) { var f = function() {}; }");
  }

  @Test
  public void testFunctionStatements_freeCallSemantics1() {
    disableCompareAsTree();

    // This triggers free call.
    test(
        "function x(){this};var y=function(){var val=x()||{}}",
        "_.x=function(){this};_.y=function(){var val=(0,_.x)()||{}}");
    test(
        "function x(){this;x()}",
        "_.x=function(){this;(0,_.x)()}");
    test(
        "var a=function(){this};a()",
        "_.a=function(){this};(0,_.a)()");
    // Always trigger free calls for variables assigned through destructuring.
    test("var {a: a} = {a: function() {}}; a();", "({a:_.a}={a:function(){}});(0,_.a)()");

    test(
        "var ns = {}; ns.a = function() {}; ns.a()",
        "_.ns={};_.ns.a=function(){};_.ns.a()");
  }

  @Test
  public void testFunctionStatements_freeCallSemantics2() {
    // Cases where free call forcing through (0, foo)() is not necessary.
    test(
        "var a=function(){};a()",
        "_.a=function(){};_.a()");
    test(
        "function a(){};a()",
        "_.a=function(){};;_.a()");
    test(
        "var a;a=function(){};a()",
        "_.a=function(){};_.a()");

    // Test that calls to arrow functions are not forced to be free calls
    test("var a = () => {}; a();", "_.a = () => {}; _.a();");
    test("var a = () => this; a();", "_.a = () => this; _.a();");
  }

  @Test
  public void testFunctionStatements_freeCallSemantics3() {
    disableCompareAsTree();

    // Ambiguous cases.
    test("var a=1;a=function(){};a()", "_.a=1;_.a=function(){};(0,_.a)()");
    test(
        "var b;var a=b;a()",
        "_.a=_.b;(0,_.a)()");
  }

  @Test
  public void testFunctionStatements_defaultParameters() {
    test("var a = 1; function f(param = a) {}", "_.a = 1; _.f = function(param = _.a) {}");
  }

  @Test
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

  @Test
  public void testTryCatch() {
    test(
        "try{var a = 1}catch(e){throw e}",
        "try{_.a = 1}catch(e){throw e}");
  }

  @Test
  public void testShadowInFunctionScope() {
    test(
        "var _ = 1; (function () { _ = 2 })()",
        "_._ = 1; (function () { _._ = 2 })()");
    test(
        "function foo() { var _ = {}; _.foo = foo; _.bar = 1; }",
        "_.foo = function () { var _$ = {}; _$.foo = _.foo; _$.bar = 1}");
    test(
        "function foo() { var {key: _} = {}; _.foo = foo; _.bar = 1; }",
        "_.foo = function () { var {key: _$} = {}; _$.foo = _.foo; _$.bar = 1}");
    test(
        lines(
            "function foo() { var _ = {}; _.foo = foo; _.bar = 1; ",
            "(function() { var _ = 0;})() }"),
        lines(
            "_.foo = function () { var _$ = {}; _$.foo = _.foo; _$.bar = 1; ",
            "(function() { var _$ = 0;})() }"));
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
        "var a = 5; function foo(_) { return _; }", "_.a = 5; _.foo = function(_$) { return _$; }");
    test(
        "var a = 5; function foo({key: _}) { return _; }",
        "_.a = 5; _.foo = function({key: _$}) { return _$; }");
    test(
        "var a = 5; function foo([_]) { return _; }",
        "_.a = 5; _.foo = function([_$]) { return _$; }");
    // We accept this unnecessary renaming as acceptable to simplify pattern
    // matching in the traversal.
    test("function foo() { var _$a = 1;}", "_.foo = function () { var _$a$ = 1;}");
  }

  @Test
  public void testShadowInBlockScope() {
    test(
        "var foo = 1; if (true) { const _ = {}; _.foo = foo; _.bar = 1; }",
        "_.foo = 1; if (true) { const _$ = {}; _$.foo = _.foo; _$.bar = 1}");
    test(
        "var foo = 1; if (true) { const {key: _} = {}; _.foo = foo; _.bar = 1; }",
        "_.foo = 1; if (true) { const {key: _$} = {}; _$.foo = _.foo; _$.bar = 1}");
    test(
        "var foo = 1; if (true) { const _ = {}; _.foo = foo; _.bar = 1; const _$ = 1; }",
        "_.foo = 1; if (true) { const _$ = {}; _$.foo = _.foo; _$.bar = 1;  const _$$ = 1; }");
    test(
        "var foo = 1; if (true) { const [_] = [{}]; _.foo = foo; }",
        "  _.foo = 1; if (true) { const [_$] = [{}]; _$.foo = _.foo; }");
  }

  @Test
  public void testExterns() {
    test(
        externs("var document;"),
        srcs("document"),
        expected("window.document"));
    test(
        externs("var document;"),
        srcs("document.getElementsByTagName('test')"),
        expected("window.document.getElementsByTagName('test')"));
    test(
        externs("var document;"),
        srcs("window.document.getElementsByTagName('test')"),
        expected("window.document.getElementsByTagName('test')"));
    test(
        externs("var document;document.getElementsByTagName"),
        srcs("document.getElementsByTagName('test')"),
        expected("window.document.getElementsByTagName('test')"));
    test(
        externs("var document,navigator"),
        srcs("document.navigator;navigator"),
        expected("window.document.navigator;window.navigator"));
    test(
        externs("var iframes"),
        srcs("function test() { iframes.resize(); }"),
        expected("_.test = function() { window.iframes.resize(); }"));
    test(
        externs("var iframes"),
        srcs("var foo = iframes;"),
        expected("_.foo = window.iframes;"));
    test(
        externs("class A {}"),
        srcs("A"),
        expected("window.A"));
    // Special names.
    test(
        externs("var arguments, window, eval;"),
        srcs("arguments;window;eval;"),
        expected("arguments;window;eval;"));
    // Actually not an extern.
    test(
        externs(""),
        srcs("document"),
        expected("window.document"));
    // Javascript builtin objects
    testSame(
        "Object;Function;Array;String;Boolean;Number;Math;"
        + "Date;RegExp;JSON;Error;EvalError;ReferenceError;"
        + "SyntaxError;TypeError;URIError;");
  }

  @Test
  public void testSameVarDeclaredInExternsAndSource() {
    test(
        externs("/** @const */ var ns = {}; function f() {}"),
        srcs("/** @const */ var ns = ns || {};"),
        expected("/** @const */ window.ns = window.ns || {};"));

    test(
        externs("var x;"),
        srcs("var x = 1; x = 2;"),
        expected("window.x = 1; window.x = 2;"));

    test(
        externs("var x;"),
        srcs("var x; x = 1;"),
        expected("window.x = 1;"));

    test(
        externs("var x;"),
        srcs("function f() { var x; x = 1; }"),
        expected("_.f = function() { var x; x = 1; }"));

    test(
        externs("var x;"),
        srcs("function f() { x = 1; }"),
        expected("_.f = function() { window.x = 1; };"));

    test(
        externs("var x, y;"),
        srcs("var x = 1, y = 2;"),
        expected("window.x = 1; window.y = 2;"));

    test(
        externs("var x, y;"),
        srcs("var x, y = 2;"),
        expected("window.y = 2;"));

    test(
        externs("var x;"),
        srcs("var x = 1, y = 2;"),
        expected("window.x = 1; _.y = 2;"));

    test(
        externs("var x;"),
        srcs("var y = 2, x = 1;"),
        expected("_.y = 2; window.x = 1;"));

    test(
        externs("var x;"),
        srcs("var y, x = 1;"),
        expected("window.x = 1;"));

    test(
        externs("var foo;"),
        srcs("var foo = function(x) { if (x > 0) { var y = foo; } };"),
        expected(
            "window.foo = function(x) { if (x > 0) { var y = window.foo; } };"));

    // The parameter x doesn't conflict with the x in the source
    test(
        externs("function f(x) {}"),
        srcs("var f = 1; var x = 2;"),
        expected("window.f = 1; _.x = 2;"));
  }

  @Test
  public void testSameVarDeclaredInExternsAndSource2() {
    assumeCrossModuleNames = false;

    test(createModules(
        lines(
            "Foo = function() { this.b = ns; };",
            "var f = function(a) {",
            "  if (a instanceof Foo && a.b === ns) {}",
            "},",
            "ns = {},",
            "g = function(a) { var b = new Foo; };"),
        "f; g;"),
        new String[] {
            lines(
                "var ns;",
                "window.Foo = function() { this.b = ns; };",
                "_.f = function(a) {",
                "  if (a instanceof window.Foo && a.b === ns) {}",
                "};",
                "ns = {};",
                "_.g = function(a) { var b = new window.Foo; };"),
            "_.f; _.g;"
    });

    test(
        externs("var y;"),
        srcs("var x = 1, y = 2; function f() { return x + window.y; }"),
        expected(
            "var x; x = 1; window.y = 2; var f = function() { return x + window.y; }"));
  }

  @Test
  public void testArrowFunctions() {
    test("const fn = () => 3;", "_.fn = () => 3;");
    test("const PI = 3.14; const fn = () => PI;", "_.PI = 3.14; _.fn = () => _.PI");
    test("let a = 3; const fn = () => a = 4;", "_.a = 3; _.fn = () => _.a = 4;");
    test("const PI = 3.14; (() => PI)()", "_.PI = 3.14; (() => _.PI)();");
    test("const PI = 3.14; (() => { return PI; })()", "_.PI = 3.14; (() => { return _.PI; })()");
  }

  @Test
  public void testEnhancedObjectLiterals() {
    test("var a = 3; var obj = {[a]: a};", "_.a = 3; _.obj = {[_.a]: _.a};");
    test(
        "var g = 3; var obj = {a() {}, b() { return g; }};",
        "_.g = 3; _.obj = {a() {}, b() { return _.g; }};");
    test("var a = 1; var obj = {a}", "_.a = 1; _.obj = {a: _.a};");
  }

  @Test
  public void testEs6Modules() {
    // Test that this pass does nothing to ES6 modules.
    testSame("var a = 3; a; export default a;");

    assumeCrossModuleNames = false;
    testSame(
        createModules("var a = 3; export {a};", "import {a} from './input0';"));
  }

  @Test
  public void testEmptyDestructuring() {
    testSame("var {} = {};");
  }
}
