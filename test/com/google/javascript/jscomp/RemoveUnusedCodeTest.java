/*
 * Copyright 2004 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RemoveUnusedCodeTest extends CompilerTestCase {

  private boolean removeGlobal;
  private boolean preserveFunctionExpressionNames;

  public RemoveUnusedCodeTest() {
    // Set up externs to be used in the test cases.
    super(
        lines(
            "var undefined;",
            "var goog = {};",
            "goog.reflect = {};",
            "goog.reflect.object = function(obj, propertiesObj) {};",
            "function goog$inherits(subClass, superClass) {}",
            "function goog$mixin(dstPrototype, srcPrototype){}",
            "function alert() {}",
            "function use() {}",
            "function externFunction() {}",
            "var externVar;",
            "var window;"));
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    enableGatherExternProperties();
    removeGlobal = true;
    preserveFunctionExpressionNames = false;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        new RemoveUnusedCode.Builder(compiler)
            .removeLocalVars(true)
            .removeGlobals(removeGlobal)
            .preserveFunctionExpressionNames(preserveFunctionExpressionNames)
            .build()
            .process(externs, root);
      }
    };
  }

  @Test
  public void testUnusedPrototypeFieldReference() {
    // Simply mentioning a prototype property without using it doesn't count as a reference.
    test("function C() {} C.prototype.x;", "");
  }

  @Test
  public void testLeaveZeroBehind() {
    // We don't need the assignment or the assigned value, but we need to keep the AST valid.
    test(
        "var x; (x = 15, externFunction())", // preserve formatting
        "                externFunction()");
  }

  @Test
  public void testRemoveInBlock() {
    test(lines(
        "if (true) {",
        "  if (true) {",
        "    var foo = function() {};",
        "  }",
        "}"),
        lines(
            "if (true) {",
            "  if (true) {",
            "  }",
            "}"));

    test("if (true) { let foo = function() {} }", "if (true);");
  }

  @Test
  public void testDeclarationInSwitch() {
    test(
        lines(
            "const x = 1;",
            "const y = 2;",
            "switch (x) {",
            "  case 1:",
            "    let y = 3;",
            "    break;",
            "  default:",
            "    let z = 5;",
            "    alert(z);",
            "    break;",
            "}",
            "alert(y);"),
        lines(
            "const x = 1;",
            "const y = 2;",
            "switch (x) {",
            "  case 1:",
            "    break;",
            "  default:",
            "    let z = 5;",
            "    alert(z);",
            "    break;",
            "}",
            "alert(y);"));
  }

  @Test
  public void testPrototypeIsAliased() {
    // without alias C is removed
    test("function C() {} C.prototype = {};", "");
    // with alias C must stay
    testSame("function C() {} C.prototype = {}; var x = C.prototype; x;");
    testSame("var x; function C() {} C.prototype = {}; x = C.prototype; x;");
    testSame("var x; function C() {} x = C.prototype = {}; x;");
  }

  @Test
  public void testWholePrototypeAssignment() {
    test("function C() {} C.prototype = { constructor: C };", "");
  }

  @Test
  public void testUsageBeforeDefinition() {
    test("function f(a) { x[a] = 1; } var x; x = {}; f();", "function f() {} f();");
  }

  @Test
  public void testReferencedPropertiesOnUnreferencedVar() {
    test(
        "var x = {}; x.a = 1; var y = {a: 2}; y.a;", // preserve format
        "                     var y = {a: 2}; y.a;");
  }

  @Test
  public void testPropertyValuesAddedAfterReferenceAreRemoved() {
    // Make sure property assignments added after the first reference to the var are kept and their
    // values traversed.
    testSame("var x = 1; var y = {}; y; y.foo = x;");
  }

  @Test
  public void testReferenceInObjectLiteral() {
    testSame(lines(
        "function f(a) {",
        "  return {a: a};",
        "}",
        "f(1);"));
  }

  @Test
  public void testSelfOverwrite() {
    // Test for possible ConcurrentModificationException
    // Reference to `a` triggers traversal of its function value, which recursively adds another
    // definition of `a` to consider.
    testSame("var a = function() { a = function() {}; }; a();");
  }

  @Test
  public void testPropertyReferenceAddsPropertyReference() {
    // Test for possible ConcurrentModificationException
    // Reference to `a.foo()` triggers traversal of its function value, which recursively adds
    // the `foo` property to another variable.
    testSame("var a = {}; a.foo = function() { b.foo = 1; }; var b = {}; a.foo(); b.foo;");
  }

  @Test
  public void testExternVarDestructuredAssign() {
    testSame("({a:externVar} = {a:1});");
    testSame("({a:externVar = 1} = {});");
    testSame("({['a']:externVar} = {a:1});");
    testSame("({['a']:externVar = 1} = {});");
    testSame("[externVar] = [1];");
    testSame("[externVar = 1] = [];");
    testSame("[, ...externVar] = [1];");
    testSame("[, ...[externVar = 1]] = [1];");
  }

  @Test
  public void testRemoveVarDeclaration1() {
    test("var a = 0, b = a = 1", "");
  }

  @Test
  public void testRemoveVarDeclaration2() {
    test("var a;var b = 0, c = a = b = 1", "");
  }

  @Test
  public void testRemoveUnusedVarsFn0() {
    // Test with function expressions in another function call
    test(
        "function A(){} if(0){function B(){}} window.setTimeout(function(){A()})",
        "function A(){} if(0);                window.setTimeout(function(){A()})");
  }

  @Test
  public void testRemoveUnusedVarsFn1s() {
    // Test with function expressions in another function call
    test(
        lines("function A(){}", "if(0){var B = function(){}}", "A();"),
        lines("function A(){}", "if(0){}", "A();"));
  }

  @Test
  public void testRemoveUnusedVars1() {
    setAcceptedLanguage(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    test(
        lines(
            "var a;",
            "var b=3;",
            "var c=function(){};",
            "var x=A();",
            "var y;",
            "var z;",
            "function A(){B()}",
            "function B(){C(b)}",
            "function C(){} ",
            "function X(){Y()}",
            "function Y(z){Z(x)}",
            "function Z(){y} ",
            "externVar = function(){A()}; ",
            "try{0}catch(e){a}"),
        lines(
            "var a;",
            "var b=3;",
            "A();",
            "function A(){B()}",
            "function B(){C(b)}",
            "function C(){}",
            "externVar = function(){A()};",
            "try{0}catch(e){a}"));

    // Test removal from if {} blocks
    test(
        "var i=0; var j=0; if(i>0){var k=1;}", // preserve alignment
        "var i=0;          if(i>0);");

    // Test with for loop
    test(
        lines(
            "var x = '';",
            "for (var i in externVar) {",
            "  if (i > 0) x += ', ';",
            "  var arg = 'foo';",
            "  if (arg.length > 40) {",
            "    var unused = 'bar';",
            "    arg = arg.substr(0, 40) + '...';",
            "  }",
            "  x += arg;",
            "}",
            "alert(x);"),
        lines(
            "var x = '';",
            "for(var i in externVar) {",
            "  if (i > 0) x += ', ';",
            "  var arg = 'foo';",
            "  if (arg.length > 40) arg = arg.substr(0,40) + '...';",
            "  x += arg",
            "}",
            "alert(x);"));

    // Test with recursive functions
    test(
        "function A(){A()}function B(){B()}B()", // preserve alignment
        "                 function B(){B()}B()");

    // Test with multiple var declarations.
    test(
        "var x,y=2,z=3; alert(x); alert(z); var a,b,c=4;", // preserve alignment
        "var x,z=3;     alert(x); alert(z);");

    // Test with for loop declarations
    test(
        lines(
            "for(var i=0,j=0;i<10;){}",
            "for(var x=0,y=0;;y++){}",
            "for(var a,b;;){a}",
            "for(var c,d;;);",
            "for(var item in externVar){}"),
        lines(
            "for(var i=0;i<10;);",
            "for(var y=0;;y++);",
            "for(var a;;)a;",
            "for(;;);",
            "for(var item in externVar);"));

    // Test multiple passes required
    test(
        "var a,b,c,d; var e=[b,c]; var x=e[3]; var f=[d]; alert(f[0])",
        "var       d;                          var f=[d]; alert(f[0])");

    // Test proper scoping (static vs dynamic)
    test(
        "var x; function A() {var x; B()} function B(){ alert(x); } A();",
        "var x; function A() {       B()} function B(){ alert(x); } A();");

    // Test closures in a return statement
    testSame("function A(){var x;return function(){alert(x)}}A()");

    // Test other closures, multiple passes
    test(
        lines(
            "function A(){}",
            "function B() {",
            "  var c,d,e,f,g,h;",
            "  function C(){ alert(c) }",
            "  var handler = function(){ alert(d) };",
            "  var handler2 = function(){ handler() };",
            "  e = function(){ alert(e) };",
            "  if (1) { function G(){ alert(g) } }",
            "  externVar = [function(){ alert(h) }];",
            "  return function(){ alert(f) };",
            "}",
            "B()"),
        lines(
            "function B() {",
            "  var f,h;",
            "  if(1);",
            "  externVar = [function(){ alert(h) }];",
            "  return function(){ alert(f) }",
            "}",
            "B()"));

    // Test exported names
    test(
        "var a,b=1; function _A1() {this.foo(a)}", // preserve alignment
        "var a;     function _A1() {this.foo(a)}");

    // Test undefined (i.e. externally defined) names
    testSame("externVar = 1");

    // Test unused vars with side effects
    test(
        "var a, b = alert(), c = externVar++,     d; var e = alert(); var f; alert(d);",
        "           alert();     externVar++; var d;         alert();        alert(d);");

    test("var a, b = alert()", "alert()");
    test("var b = alert(), a", "alert()");
    test("var a, b = alert(a)", "var a; alert(a);");
  }

  @Test
  public void testFunctionArgRemoval() {
    // remove all function arguments
    test(
        "var b=function(c,d){return};b(1,2)", // preserve alignment
        "var b=function(   ){return};b(1,2)");

    // remove no function arguments
    testSame("var b=function(c,d){return c+d};b(1,2)");
    testSame("var b=function(e,f,c,d){return c+d};b(1,2)");

    // remove some function arguments
    test(
        "var b=function(c,d,e,f){return c+d};b(1,2)", // preserve alignment
        "var b=function(c,d    ){return c+d};b(1,2)");
    test("var b=function(e,c,f,d,g){return c+d};b(1,2)",
        "var b=function(e,c,f,d){return c+d};b(1,2)");
  }

  @Test
  public void testDollarSuperParameterNotRemoved() {
    // supports special parameter expected by the prototype open-source library
    testSame("function f($super) {} f();");
  }

  @Test
  public void testFunctionArgRemovalWithLeadingUnderscore() {
    // Coding convention usually prevents removal of variables beginning with a leading underscore,
    // but that makes no sense for parameter names.
    test(
        "function f(__$jscomp$1) {__$jscomp$1 = 1;} f();", // preserve alignment
        "function f(           ) {                } f();");
  }

  @Test
  public void testComputedPropertyDestructuring() {
    // Don't remove variables accessed by computed properties
    testSame("var {['a']:a, ['b']:b} = {a:1, b:2}; a; b;");

    test(
        "var {['a']:a, ['b']:b} = {a:1, b:2};", // preserve alignment
        "var {                } = {a:1, b:2};");

    testSame("var {[alert()]:a, [alert()]:b} = {a:1, b:2};");

    testSame("var a = {['foo' + 1]:1, ['foo' + 2]:2}; alert(a.foo1);");
  }

  @Test
  public void testFunctionArgRemoval_defaultValue1() {
    test(
        "function f(unusedParam = undefined) {}; f();", // preserve alignment
        "function f(                       ) {}; f();");
  }

  @Test
  public void testFunctionArgRemoval_defaultValue2() {
    test(
        "function f(unusedParam = 0) {}; f();", // preserve alignment
        "function f(               ) {}; f();");
  }

  @Test
  public void testFunctionArgRemoval_defaultValue3() {
    // Parameters already encountered can be used by later parameters
    test(
        "function f(x, y = x + 0) {}; f();", // preserve alignment
        "function f(            ) {}; f();");
  }

  @Test
  public void testFunctionArgRemoval_defaultValue4() {
    // Parameters already encountered can be used by later parameters
    testSame("function f(x, y = x + 0) { y; }; f();");
  }

  @Test
  public void testFunctionArgRemoval_defaultValue5() {
    // Default value inside arrow function param list
    test(
        "var f = (unusedParam = 0) => {}; f();", // preserve alignment
        "var f = (               ) => {}; f();");

    testSame("var f = (usedParam = 0) => { usedParam; }; f();");

    test("var f = (usedParam = 0) => {usedParam;};", "");
  }

  @Test
  public void testFunctionArgRemoval_defaultValue6() {
    // Parameters already encountered can be used by later parameters
    testSame("var x = 2; function f(y = x) { use(y); }; f();");
  }

  @Test
  public void testFunctionArgRemoval_defaultValue7() {
    // Parameters already encountered can be used by later parameters
    test(
        "var x = 2; function f(y = x) {}; f();", // preserve alignment
        "           function f(     ) {}; f();");
  }

  @Test
  public void testDestructuringParams() {
    // Default value not used
    test(
        "function f({a:{b:b}} = {a:{}}) { /** b is unused */ }; f();",
        "function f({a:{   }} = {a:{}}) { /** b is unused */ }; f();");

    // Default value with nested value used in default value assignment
    test(
        "function f({a:{b:b}} = {a:{b:1}}) { /** b is unused */ }; f();",
        "function f({a:{   }} = {a:{b:1}}) { /** b is unused */ }; f();");

    // Default value with nested value used in function body
    testSame("function f({a:{b:b}} = {a:{b:1}}) { b; }; f();");

    test(
        "function f({a:{b:b}} = {a:{b:1}}) {}; f();", // preserve alignment
        "function f({a:{   }} = {a:{b:1}}) {}; f();");

    test(
        "function f({a:{b:b}} = {a:{}}) {}; f();", // preserve alignment
        "function f({a:{   }} = {a:{}}) {}; f();");

    // Destructuring pattern not default and parameter not used
    test(
        "function f({a:{b:b}}) { /** b is unused */ }; f({c:{d:1}});",
        "function f({a:{   }}) { /** b is unused */ }; f({c:{d:1}});");

    // Destructuring pattern not default and parameter used
    testSame("function f({a:{b:b}}) { b; }; f({c:{d:1}});");
  }

  @Test
  public void testMixedParamTypes() {
    // Traditional and destructuring pattern
    test(
        "function f({a:{b:b}}, c, d) { c; }; f();", // preserve alignment
        "function f({a:{   }}, c   ) { c; }; f();");

    test(
        "function f({a:{b:b = 5}}, c, d) { c; }; f();", // preserve alignment
        "function f({a:{       }}, c   ) { c; }; f()");

    testSame("function f({}, c, {d:{e:e}}) { c; e; }; f();");

    // Parent is the parameter list
    test(
        "function f({a}, b, {c}) {use(a)}; f({}, {});", // preserve alignment
        "function f({a}        ) {use(a)}; f({}, {});");

    // Default and traditional
    test(
        "function f(unusedParam = undefined, z) { z; }; f();",
        "function f(unusedParam, z            ) { z; }; f();");
  }

  @Test
  public void testDefaultParams() {
    test(
        "function f(x = undefined) {}; f();", // preserve alignment
        "function f(             ) {}; f();");
    test(
        "function f(x = 0) {}; f();", // preserve alignment
        "function f(     ) {}; f();");
    testSame("function f(x = 0, y = x) { alert(y); }; f();");
  }

  @Test
  public void testDefaultParamsInClass() {
    test(
        "class Foo { constructor(value = undefined) {} }; new Foo;",
        "class Foo { constructor(                 ) {} }; new Foo;");

    testSame("class Foo { constructor(value = undefined) { value; } }; new Foo;");
    testSame(
        lines(
            "class Bar {}",
            "class Foo extends Bar {",
            "  constructor(value = undefined) { super(); value; }",
            "}",
            "new Foo;"));
  }

  @Test
  public void testDefaultParamsInClassThatReferencesArguments() {
    testSame(
        lines(
            "class Bar {}",
            "class Foo extends Bar {",
            "  constructor(value = undefined) {",
            "    super();",
            "    if (arguments.length)",
            "      value;",
            "  }",
            "};",
            "new Foo;"));
  }

  @Test
  public void testDefaultParamsWithoutSideEffects0() {
    test(
        "function f({} = {}){}; f()", // preserve alignment
        "function f(       ){}; f()");
  }

  @Test
  public void testDefaultParamsWithoutSideEffects1() {
    test(
        "function f(a = 1) {}; f();", // preserve alignment
        "function f(     ) {}; f();");

    test(
        "function f({a:b = 1} = 1){}; f();", // preserve alignment
        "function f(             ){}; f();");

    test(
        "function f({a:b} = {}){}; f();", // preserve alignment
        "function f(          ){}; f();");
  }

  @Test
  public void testDefaultParamsWithSideEffects1() {
    testSame("function f(a = alert('foo')) {}; f();");

    testSame("function f({} = alert('foo')){}; f()");

    test(
        "function f(){var x; var {a:b} = x()}; f();", // preserve alignment
        "function f(){var x; var {   } = x()}; f();");

    test(
        "function f(){var {a:b} = alert('foo')}; f();",
        "function f(){var {} = alert('foo')}; f();");
    test(
        "function f({a:b} = alert('foo')){}; f();", // preserve alignment
        "function f({   } = alert('foo')){}; f();");

    testSame("function f({a:b = alert('bar')} = alert('foo')){}; f();");
  }

  @Test
  public void testDefaultParamsWithSideEffects2() {
    test(
        "function f({a:b} = alert('foo')){}; f();", // preserve alignment
        "function f({   } = alert('foo')){}; f();");
  }

  @Test
  public void testArrayDestructuringParams() {
    // Default values in array pattern unused
    test(
        "function f([x,y] = [1,2]) {}; f();", // preserve alignment
        "function f(             ) {}; f();");

    test(
        "function f([x,y] = [1,2], z) { z; }; f();", // preserve alignment
        "function f([   ] = [1,2], z) { z; }; f();");

    // Default values in array pattern used
    test(
        "function f([x,y,z] = [1,2,3]) { y; }; f();", // preserve alignment
        "function f([ ,y  ] = [1,2,3]) { y; }; f();");

    // Side effects
    test(
        "function f([x] = [alert()]) {}; f();", // preserve alignment
        "function f([ ] = [alert()]) {}; f();");
  }

  @Test
  public void testRestPattern() {
    test(
        "var x; [...x] = externVar;", // preserve alignment
        "       [    ] = externVar;");
    test(
        "var [...x] = externVar;", // preserve alignment
        "var [    ] = externVar;");
    testSame("var x; [...x] = externVar; use(x);");
    testSame("var [...x] = externVar; use(x);");
    testSame("var x; [...x.y] = externVar;");
    testSame("var x; [...x['y']] = externVar;");
    testSame("var x; [...x().y] = externVar;");
    testSame("var x; [...x()['y']] = externVar;");
  }

  @Test
  public void testRestParams() {
    test(
        "function foo(...args) {/**rest param unused*/}; foo();",
        "function foo(       ) {/**rest param unused*/}; foo();");

    testSame("function foo(a, ...args) { args[0]; }; foo();");

    // Rest param in pattern
    testSame(
        lines(
            "function countArgs(x, ...{length}) {",
            "  return length;",
            "}",
          "alert(countArgs(1, 1, 1, 1, 1));"));

    test(
        " function foo([...rest]) {/**rest unused*/}; foo();",
        " function foo(         ) {/**rest unused*/}; foo();");

    test(
        "function foo([x, ...rest]) { x; }; foo();", // preserve alignment
        "function foo([x         ]) { x; }; foo();");
  }

  @Test
  public void testFunctionsDeadButEscaped() {
    testSame("function b(a) { a = 1; alert(arguments[0]) }; b(6)");
    testSame("function b(a) { var c = 2; a = c; alert(arguments[0]) }; b(6)");
  }

  @Test
  public void testVarInControlStructure() {
    test("if (true) var b = 3;", "if(true);");
    test("if (true) var b = 3; else var c = 5;", "if(true);else;");
    test("while (true) var b = 3;", "while(true);");
    test("for (;;) var b = 3;", "for(;;);");
    test("do var b = 3; while(true)", "do;while(true)");
    test("with (true) var b = 3;", "with(true);");
    test("f: var b = 3;", "f:{}");
  }

  @Test
  public void testRValueHoisting() {
    test("var x = alert();", "alert()");
    test("var x = {a: alert()};", "({a:alert()})");

    test("var x=function y(){}", "");
  }

  @Test
  public void testModule() {
    test(createModules(
        "var unreferenced=1; function x() { foo(); }" +
            "function uncalled() { var x; return 2; }",
        "var a,b; function foo() { this.foo(a); } x()"),
        new String[]{
            "function x(){foo()}",
            "var a;function foo(){this.foo(a)}x()"
        });
  }

  @Test
  public void testRecursiveFunction1() {
    testSame("(function x(){return x()})()");
  }

  @Test
  public void testRecursiveFunction2() {
    test(
        "var x = 3; (function          x() { return          x(); })();",
        "           (function x$jscomp$1() { return x$jscomp$1(); })()");
  }

  @Test
  public void testFunctionWithName1() {
    test(
        "var x=function f(){};x()", // preserve alignment
        "var x=function  (){};x()");

    preserveFunctionExpressionNames = true;
    testSame("var x=function f(){};x()");
  }

  @Test
  public void testFunctionWithName2() {
    test("alert(function bar(){})", "alert(function(){})");

    preserveFunctionExpressionNames = true;
    testSame("alert(function bar(){})");
  }

  @Test
  public void testRemoveGlobal1() {
    removeGlobal = false;
    testSame("var x=1");
    test("var y=function(x){var z;}", "var y=function(x){}");
  }

  @Test
  public void testRemoveGlobal2() {
    removeGlobal = false;
    testSame("var x=1");
    test("function y(x){var z;}", "function y(x){}");
  }

  @Test
  public void testRemoveGlobal3() {
    removeGlobal = false;
    testSame("var x=1");
    test(
        "function x(){function y(x){var z;}y()}", // preserve alignment
        "function x(){function y(x){      }y()}");
  }

  @Test
  public void testRemoveGlobal4() {
    removeGlobal = false;
    testSame("var x=1");
    test(
        "function x(){function y(x){var z;}}", // preserve alignment
        "function x(){                     }");
  }

  @Test
  public void testIssue168a() {
    test("function _a(){" +
            "  (function(x){ _b(); })(1);" +
            "}" +
            "function _b(){" +
            "  _a();" +
            "}",
        "function _a(){(function(){_b()})(1)}" +
            "function _b(){_a()}");
  }

  @Test
  public void testIssue168b() {
    removeGlobal = false;
    test("function a(){" +
            "  (function(x){ b(); })(1);" +
            "}" +
            "function b(){" +
            "  a();" +
            "}",
        "function a(){(function(x){b()})(1)}" +
            "function b(){a()}");
  }

  @Test
  public void testUnusedAssign1() {
    test("var x = 3; x = 5;", "");
  }

  @Test
  public void testUnusedAssign2() {
    test("function f(a) { a = 3; } this.x = f;",
        "function f(){} this.x=f");
  }

  @Test
  public void testUnusedAssign3() {
    // e can't be removed, so we don't try to remove the dead assign.
    // We might be able to improve on this case.
    test("try { throw ''; } catch (e) { e = 3; }",
        "try{throw\"\";}catch(e){e=3}");
  }

  @Test
  public void testUnusedAssign4() {
    test("function f(a, b) { this.foo(b); a = 3; } this.x = f;",
        "function f(a,b){this.foo(b);}this.x=f");
  }

  @Test
  public void testUnusedAssign5() {
    test("var z = function f() { f = 3; }; z();",
        "var z=function(){};z()");
  }

  @Test
  public void testUnusedAssign5b() {
    test("var z = function f() { f = alert(); }; z();",
        "var z=function(){alert()};z()");
  }

  @Test
  public void testUnusedAssign6() {
    test("var z; z = 3;", "");
  }

  @Test
  public void testUnusedAssign6b() {
    test("var z; z = alert();", "alert()");
  }

  @Test
  public void testUnusedAssign7() {
    // This loop is normalized to "var i;for(i in..."
    test("var a = 3; for (var i in {}) { i = a; }",
        // TODO(johnlenz): "i = a" should be removed here.
        "var a = 3; var i; for (i in {}) {i = a;}");
  }

  @Test
  public void testUnusedAssign8() {
    // This loop is normalized to "var i;for(i in..."
    test("var a = 3; for (var i in {}) { i = a; } alert(a);",
        // TODO(johnlenz): "i = a" should be removed here.
        "var a = 3; var i; for (i in {}) {i = a} alert(a);");
  }

  @Test
  public void testUnusedAssign9a() {
    testSame("function b(a) { a = 1; arguments; }; b(6)");
  }

  @Test
  public void testUnusedAssign9() {
    testSame("function b(a) { a = 1; arguments=1; }; b(6)");
  }

  @Test
  public void testES6ModuleExports() {
    test("const X = 1; function f() {}", "");
    test("const X = 1; export function f() {}", "function f() {} export {f as f}");
    test("const X = 1; export class C {}", "class C {} export { C as C }");
    test("const X = 1; export default function f() {};", "export default function f() {}");
    test("const X = 1; export default class C {}", "export default class C {}");
  }

  @Test
  public void testUnusedPropAssign1() {
    test("var x = {}; x.foo = 3;", "");
  }

  @Test
  public void testUnusedPropAssign1b() {
    test("var x = {}; x.foo = alert();", "alert()");
  }

  @Test
  public void testUnusedPropAssign2() {
    test("var x = {}; x['foo'] = 3;", "");
  }

  @Test
  public void testUnusedPropAssign2b() {
    test("var x = {}; x[alert()] = alert();", "alert(),alert()");
  }

  @Test
  public void testUnusedPropAssign3() {
    test("var x = {}; x['foo'] = {}; x['bar'] = 3", "");
  }

  @Test
  public void testUnusedPropAssign3b() {
    test("var x = {}; x[alert()] = alert(); x[alert() + alert()] = alert()",
        "alert(),alert();(alert() + alert()),alert()");
  }

  @Test
  public void testUnusedPropAssign4() {
    test("var x = {foo: 3}; x['foo'] = 5;", "");
  }

  @Test
  public void testUnusedPropAssign5() {
    test(
        "var x = {foo: alert()}; x['foo'] = 5;", // preserve newline
        "       ({foo: alert()})             ;");
  }

  @Test
  public void testUnusedPropAssign6() {
    test("var x = function() {}; x.prototype.bar = function() {};", "");
  }

  @Test
  public void testUnusedPropAssign6b() {
    test("function x() {} x.prototype.bar = function() {};", "");
  }

  @Test
  public void testUnusedPropAssign6c() {
    test("function x() {} x.prototype['bar'] = function() {};", "");
  }

  @Test
  public void testUnusedPropAssign7() {
    test("var x = {}; x[x.foo] = x.bar;", "");
  }

  @Test
  public void testUnusedPropAssign7b() {
    testSame("var x = {}; x[x.foo] = alert(x.bar);");
  }

  @Test
  public void testUnusedPropAssign7c() {
    test("var x = {}; x[alert(x.foo)] = x.bar;",
        "var x={};x[alert(x.foo)]=x.bar");
  }

  @Test
  public void testUsedPropAssign1() {
    testSame("function f(x) { x.bar = 3; } f({});");
  }

  @Test
  public void testUsedPropAssign2() {
    testSame("try { throw {}; } catch (e) { e.bar = 3; }");
  }

  @Test
  public void testUsedPropAssign3() {
    // This pass does not do flow analysis.
    testSame("var x = {}; x.foo = 3; x = alert();");
  }

  @Test
  public void testUsedPropAssign4() {
    testSame("var y = alert(); var x = {}; x.foo = 3; y[x.foo] = 5;");
  }

  @Test
  public void testUsedPropAssign5() {
    testSame("var y = alert(); var x = 3; y[x] = 5;");
  }

  @Test
  public void testUsedPropAssign6() {
    testSame("var x = alert(externVar); x.innerHTML = 'new text';");
  }

  @Test
  public void testUsedPropAssign7() {
    testSame("var x = {}; for (x in alert()) { x.foo = 3; }");
  }

  @Test
  public void testUsedPropAssign8() {
    testSame("for (var x in alert()) { x.foo = 3; }");
  }

  @Test
  public void testUsedPropAssign9() {
    testSame("var x = {}; x.foo = alert(externVar); x.foo.innerHTML = 'new test';");
  }

  @Test
  public void testUsedPropNotAssign() {
    testSame("function f(x) { x.a; }; f(externVar);");
  }

  @Test
  public void testDependencies1() {
    test("var a = 3; var b = function() { alert(a); };", "");
  }

  @Test
  public void testDependencies1b() {
    test("var a = 3; var b = alert(function() { alert(a); });",
        "var a=3;alert(function(){alert(a)})");
  }

  @Test
  public void testDependencies1c() {
    test("var a = 3; var _b = function() { alert(a); };",
        "var a=3;var _b=function(){alert(a)}");
  }

  @Test
  public void testDependencies2() {
    test("var a = 3; var b = 3; b = function() { alert(a); };", "");
  }

  @Test
  public void testDependencies2b() {
    test("var a = 3; var b = 3; b = alert(function() { alert(a); });",
        "var a=3;alert(function(){alert(a)})");
  }

  @Test
  public void testDependencies2c() {
    testSame("var a=3;var _b=3;_b=function(){alert(a)}");
  }

  @Test
  public void testGlobalVarReferencesLocalVar() {
    testSame("var a=3;function f(){var b=4;a=b}alert(a + f())");
  }

  @Test
  public void testLocalVarReferencesGlobalVar1() {
    testSame("var a=3;function f(b, c){b=a; alert(b + c);} f();");
  }

  @Test
  public void testLocalVarReferencesGlobalVar2() {
    test("var a=3;function f(b, c){b=a; alert(c);} f();",
        "function f(b, c) { alert(c); } f();");
  }

  @Test
  public void testNestedAssign1() {
    test("var b = null; var a = (b = 3); alert(a);",
        "var a = 3; alert(a);");
  }

  @Test
  public void testNestedAssign2() {
    test("var a = 1; var b = 2; var c = (b = a); alert(c);",
        "var a = 1; var c = a; alert(c);");
  }

  @Test
  public void testNestedAssign3() {
    test("var b = 0; var z; z = z = b = 1; alert(b);",
        "var b = 0; b = 1; alert(b);");
  }

  @Test
  public void testCallSiteInteraction() {
    testSame("var b=function(){return};b()");
    testSame("var b=function(c){return c};b(1)");
    test(
        "var b=function(c){return};b(1)",
        "var b=function(){return};b(1)");
    test(
        "var b=function(c){};b.call(null, externVar)",
        "var b=function( ){};b.call(null, externVar)");
    test(
        "var b=function(c){};b.apply(null, externVar)",
        "var b=function( ){};b.apply(null, externVar)");

    // Recursive calls
    testSame(
        "var b=function(c,d){b(1, 2);return d};b(3,4);b(5,6)");

    testSame("var b=function(c){return arguments};b(1,2);b(3,4)");
  }

  @Test
  public void testCallSiteInteraction_constructors() {
    // The third level tests that the functions which have already been looked
    // at get re-visited if they are changed by a call site removal.
    test(
        lines(
            "var Ctor1=function(a, b){return a};",
            "var Ctor2=function(x, y){Ctor1.call(this, x, y)};",
            "goog$inherits(Ctor2, Ctor1);",
            "new Ctor2(1, 2)"),
        lines(
            "var Ctor1=function(a){return a};",
            "var Ctor2=function(x, y){Ctor1.call(this, x, y)};",
            "goog$inherits(Ctor2, Ctor1);",
            "new Ctor2(1, 2)"));
  }

  @Test
  public void testRemoveUnusedVarsPossibleNpeCase() {
    test(
        lines(
            "var a = [];",
            "var register = function(callback) {a[0] = callback};",
            "register(function(transformer) {});",
            "register(function(transformer) {});"),
        lines(
            "var register = function() {};",
            "register(function() {});",
            "register(function() {});"));
  }

  @Test
  public void testDoNotOptimizeJSCompiler_renameProperty() {
    // Only the function definition can be modified, none of the call sites.
    test("function JSCompiler_renameProperty(a) {};" +
            "JSCompiler_renameProperty('a');",
        "function JSCompiler_renameProperty() {};" +
            "JSCompiler_renameProperty('a');");
  }

  @Test
  public void testDoNotOptimizeJSCompiler_ObjectPropertyString() {
    test("function JSCompiler_ObjectPropertyString(a, b) {};" +
            "JSCompiler_ObjectPropertyString(window,'b');",
        "function JSCompiler_ObjectPropertyString() {};" +
            "JSCompiler_ObjectPropertyString(window,'b');");
  }

  @Test
  public void testDoNotOptimizeSetters() {
    testSame("({set s(a) {}})");
  }

  @Test
  public void testRemoveSingletonClass1() {
    test("function goog$addSingletonGetter(a){}" +
            "/**@constructor*/function a(){}" +
            "goog$addSingletonGetter(a);",
        "");
  }

  @Test
  public void testRemoveInheritedClass1() {
    test(
        lines(
            "/**@constructor*/ function a() {}",
            "/**@constructor*/ function b() {}",
            "goog$inherits(b, a);",
            "new a;"),
        lines("/**@constructor*/ function a() {}", "new a;"));
  }

  @Test
  public void testRemoveInheritedClass2() {
    test(
        lines(
            "/**@constructor*/function a(){}",
            "/**@constructor*/function b(){}",
            "/**@constructor*/function c(){}",
            "goog$inherits(b,a);"),
        "");
  }

  @Test
  public void testRemoveInheritedClass3() {
    testSame("/**@constructor*/function a(){}" +
        "/**@constructor*/function b(){}" +
        "goog$inherits(b,a); new b");
  }

  @Test
  public void testRemoveInheritedClass4() {
    testSame("function goog$inherits(){}" +
        "/**@constructor*/function a(){}" +
        "/**@constructor*/function b(){}" +
        "goog$inherits(b,a);" +
        "/**@constructor*/function c(){}" +
        "goog$inherits(c,b); new c");
  }

  @Test
  public void testRemoveInheritedClass5() {
    test(
        lines(
            "function goog$inherits() {}",
            "/**@constructor*/ function a() {}",
            "/**@constructor*/ function b() {}",
            "goog$inherits(b,a);",
            "/**@constructor*/ function c() {}",
            "goog$inherits(c,b); new b"),
        lines(
            "function goog$inherits(){}",
            "/**@constructor*/ function a(){}",
            "/**@constructor*/ function b(){}",
            "goog$inherits(b,a); new b"));
  }

  @Test
  public void testRemoveInheritedClass6() {
    test(
        lines(
            "/** @constructor*/ function a(){}",
            "/** @constructor*/ function b(){}",
            "/** @constructor*/ function c(){}",
            "/** @constructor*/ function d(){}",
            "goog$mixin(b.prototype,a.prototype);",
            "goog$mixin(c.prototype,a.prototype); new c;",
            "goog$mixin(d.prototype,a.prototype)"),
        lines(
            "/** @constructor*/ function a(){}",
            "/** @constructor*/ function c(){}",
            "goog$mixin(c.prototype,a.prototype); new c"));
  }

  @Test
  public void testRemoveInheritedClass7() {
    test(
        lines(
            "/**@constructor*/function a(){alert(goog$mixin(a, a))}",
            "/**@constructor*/function b(){}",
            "goog$mixin(b.prototype,a.prototype); new a"),
        lines(
            "/**@constructor*/function a(){alert(goog$mixin(a, a))} new a"));
  }

  @Test
  public void testRemoveInheritedClass8() {
    testSame("/**@constructor*/function a(){}" +
        "/**@constructor*/function b(){}" +
        "/**@constructor*/function c(){}" +
        "b.inherits(a);c.mixin(b.prototype);new c");
  }

  @Test
  public void testRemoveInheritedClass9() {
    test(
        lines(
            "function goog$inherits() {}",
            "/**@constructor*/ function a() {}",
            "/**@constructor*/ function b() {}",
            "goog$inherits(b,a); new a;",
            "var c = a; var d = a.g; new b"),
        lines(
            "function goog$inherits(){}",
            "/**@constructor*/ function a(){}",
            "/**@constructor*/ function b(){}",
            "goog$inherits(b,a); ",
            "new a; new b"));
  }

  @Test
  public void testRemoveInheritedClass10() {
    testSame(
        lines(
            "/**@constructor*/function a(){}",
            "/**@constructor*/function b(){}",
            "goog$mixin(b.prototype,a.prototype);",
            "new b"));
  }

  @Test
  public void testRemoveInheritedClass11() {
    testSame(
        lines(
            "/**@constructor*/function a(){}",
            "var b = {};",
            // goog$inherits not treated specially when derived class is a property
            "goog$inherits(b.foo, a)"));
  }

  @Test
  public void testRemoveInheritedClass12() {
    // An inherits call must be a statement unto itself or the left side of a comma to be considered
    // specially. These calls have no return values, so this restriction avoids false positives.
    // It also simplifies removal logic.
    testSame(
        lines(
            "function a(){}",
            "function b(){}",
            "goog$inherits(b, a) + 1;"));

    // Although a human is unlikely to write code like this, some optimizations may end up
    // converting an inherits call statement into the left side of a comma. We should still
    // remove this case.
    test(
        lines(
            "function a(){}",
            "function b(){}",
            "(goog$inherits(b, a), 1);"),
        "1");
  }

  @Test
  public void testReflectedMethods() {
    testSame(
        "/** @constructor */" +
            "function Foo() {}" +
            "Foo.prototype.handle = function(x, y) { alert(y); };" +
            "var x = goog.reflect.object(Foo, {handle: 1});" +
            "for (var i in x) { x[i].call(x); }" +
            "window['Foo'] = Foo;");
  }

  @Test
  public void testIssue618_1() {
    this.removeGlobal = false;
    testSame(
        "function f() {\n" +
            "  var a = [], b;\n" +
            "  a.push(b = []);\n" +
            "  b[0] = 1;\n" +
            "  return a;\n" +
            "}");
  }

  @Test
  public void testIssue618_2() {
    this.removeGlobal = false;
    testSame("var b; externVar.push(b = []); b[0] = 1;");
  }

  @Test
  public void testBug38457201() {
    this.removeGlobal = true;
    test(
        lines(
            "var DOCUMENT_MODE = 1;",
            "var temp;",
            "false || (temp = (Number(DOCUMENT_MODE) >= 9));"),
        "false || 0");
  }

  @Test
  public void testBlockScoping() {
    test("{let x;}", "{}");

    test("{const x = 1;}", "{}");

    testSame("{const x =1; alert(x)}");

    testSame("{let x; alert(x)}");

    test("let x = 1;", "");

    test("let x; x = 3;", "");

    test(
        lines(
            "let x;",
            "{ let x; }", // unused in block
            "alert(x);" // keeps outer x alive
            ),
        lines(
            "let x;", // outer x kept
            "{}", // inner x removed
            "alert(x);"));

    testSame(
        lines(
            "var x;",
            "{",
            "  var y = 1;",
            "  alert(y);", // keeps y alive
            "}",
            "alert(x);" // keeps x alive
            ));

    testSame(
        lines(
            "let x;",
            "{",
            "  let x = 1;",
            "  alert(x);", // keeps inner x alive
            "}",
            "alert(x);" // keeps outer x alive
            ));

    test(
        lines(
            "let x;",
            "{ let x; }", // inner x unused
            "let y;", // outer y unused
            "alert(x);"),
        lines(
            "let x;", // only outer x was used
            "{}",
            "alert(x);"));

    test(
        lines(
            "let x;",
            "{",
            "  let g;",
            "  {",
            "    const y = 1;",
            "    alert(y);", // keeps y alive
            "  }",
            "}",
            "let z;",
            "alert(x);" // keeps x alive
            ),
        lines(
            "let x;", // z removed
            "{",
            "  {", // g removed
            "    const y = 1;",
            "    alert(y);",
            "  }",
            "}",
            "alert(x);"));

    test(
        lines(
            "let x;",
            "{",
            "  let x = 1; ",
            "  {",
            "    let y;",
            "  }",
            "  alert(x);", // keeps inner x alive
            "}",
            "alert(x);" // keeps outer x alive
            ),
        lines(
            "let x;", // outer x kept
            "{",
            "  let x = 1; ", // inner x kept
            "  {}", // y removed
            "  alert(x);",
            "}",
            "alert(x);"));

    test(
        lines(
            "let x;",
            "{",
            "  let x;",
            "  alert(x);", // keeps inner x alive
            "}"),
        lines(
            "{",
            "  let x$jscomp$1;", // inner x was renamed
            "  alert(x$jscomp$1);",
            "}"));
  }

  @Test
  public void testArrowFunctions() {
    test(
        lines(
            "class C {",
            "  g() {",
            "    var x;",
            "  }",
            "}",
            "new C"
        )
        ,
        lines(
            "class C {",
            "  g() {",
            "  }",
            "}",
            "new C"
        )
    );

    test("() => {var x}", "() => {};");

    test("(x) => {}", "() => {};");

    testSame("(x) => {alert(x)}");

    testSame("(x) => {x + 1}");
  }

  @Test
  public void testClasses() {
    test("var C = class {};", "");

    test("class C {}", "");

    test("class C {constructor(){} g() {}}", "");

    testSame("class C {}; new C;");

    test("var C = class X {}; new C;", "var C = class {}; new C;");

    testSame(
        lines(
            "class C{",
            "  g() {",
            "    var x;",
            "    alert(x);", // use in alert prevents removal of x
            "  }",
            "}",
            "new C"));

    testSame(
        lines(
            "class C{",
            "  g() {",
            "    let x;", // same as above but with 'let'
            "    alert(x);",
            "  }",
            "}",
            "new C"));

    test(
        lines(
            "class C {",
            "  g() {",
            "    let x;", // unused x
            "  }",
            "}",
            "new C"),
        lines(
            "class C {",
            "  g() {", // x is removed
            "  }",
            "}",
            "new C"));
  }

  @Test
  public void testReferencesInClasses() {
    testSame(
        lines(
            "const A = 15;",
            "const C = class {",
            "  constructor() {",
            "    this.a = A;",
            "  }",
            "}",
            "new C;"));
  }

  @Test
  public void testRemoveGlobalClasses() {
    removeGlobal = false;
    testSame("class C{}");
  }

  @Test
  public void testSubClasses() {
    test("class D {} class C extends D {}", "");

    test("class D {} class C extends D {} new D;", "class D {} new D;");

    testSame("class D {} class C extends D {} new C;");
  }

  @Test
  public void testGenerators() {
    test(
        lines(
            "function* f() {",
            "  var x;",
            "  yield x;",
            "}"
        ),
        ""
    );
    test(
        lines(
            "function* f() {",
            "  var x;",
            "  var y;",
            "  yield x;",
            "}",
            "f();"
        ),
        lines(
            "function* f() {",
            "  var x;",
            "  yield x;",
            "}",
            "f()"
        )
    );
  }

  @Test
  public void testForLet() {
    // Could be optimized so that unused lets in the for loop are removed
    test("for(let x; ;){}", "for(;;){}");

    test("for(let x, y; ;) {x}", "for(let x; ;) {x}");
    test("for(let x, y; ;) {y}", "for(let y; ;) {y}");

    test("for(let x=0,y=0;;y++){}", "for(let y=0;;y++){}");
  }

  @Test
  public void testForInLet() {
    testSame("let item; for(item in externVar){}");
    testSame("for(let item in externVar){}");
  }

  @Test
  public void testForOf() {
    testSame("let item; for(item of externVar){}");

    testSame("for(var item of externVar){}");

    testSame("for(let item of externVar){}");

    testSame(
        lines(
            "var x;",
            "for (var n of externVar) {",
            "  if (x > n) {};", // keeps x alive
            "}"));

    test(
        lines(
            "var x;", // no references to this
            "for (var n of externVar) {",
            "  if (n) {};",
            "}"),
        lines(
            "for (var n of externVar) {", // x removed
            "  if (n) {};",
            "}"));
  }

  @Test
  public void testEnhancedForWithExternVar() {
    testSame("for(externVar in externVar){}");
    testSame("for(externVar of externVar){}");
  }

  @Test
  public void testExternVarNotRemovable() {
    testSame("externVar = 5;");
  }

  @Test
  public void testTemplateStrings() {
    testSame(
        lines(
            "var name = 'foo';",
            "`Hello ${name}`"
        )
    );
  }

  @Test
  public void testDestructuringArrayPattern0() {
    test("function f(a) {} f();", "function f() {} f();");
    test("function f([a]) {} f();", "function f() {} f();");
    test("function f(...a) {} f();", "function f() {} f();");
    test("function f(...[a]) {} f();", "function f() {} f();");
    test("function f(...[...a]) {} f();", "function f() {} f();");
    test("function f(...{length:a}) {} f();", "function f() {} f();");

    test("function f(a) {} f();", "function f() {} f();");
    test("function f([a] = 1) {} f();", "function f() {} f();");
    test("function f([a] = alert()) {} f();", "function f([] = alert()) {} f();");

    test("function f(a = 1) {} f();", "function f() {} f();");
    test("function f([a = 1]) {} f();", "function f() {} f();");
    test("function f(...[a = 1]) {} f();", "function f() {} f();");
    test("function f(...{length:a = 1}) {} f();", "function f() {} f();");

    testSame("function f(a = alert()) {} f();");
    testSame("function f([a = alert()]) {} f();");
    testSame("function f(...[a = alert()]) {} f();");
    testSame("function f(...{length:a = alert()}) {} f();");

    test("function f([a] = []) {} f();", "function f() {} f();");
    test("function f([a]) {} f();", "function f() {} f();");
    test("function f([a], b) {} f();", "function f() {} f();");
    test("function f([a], ...b) {} f();", "function f() {} f();");
    test("function f([...a]) {} f();", "function f() {} f();");
    test("function f([[]]) {} f();", "function f([[]]) {} f();");
    test("function f([[],...a]) {} f();", "function f([[]]) {} f();");

    test("var [a] = [];", "var [] = [];");
    test("var [...a] = [];", "var [] = [];");
    test("var [...[...a]] = [];", "var [...[]] = [];");

    test("var [a, b] = [];", "var [] = [];");
    test("var [a, ...b] = [];", "var [] = [];");
    test("var [a, ...[...b]] = [];", "var [,...[]] = [];");

    test("var [a, b, c] = []; use(a, b);", "var [a,b  ] = []; use(a, b);");
    test("var [a, b, c] = []; use(a, c);", "var [a, ,c] = []; use(a, c);");
    test("var [a, b, c] = []; use(b, c);", "var [ ,b,c] = []; use(b, c);");

    test("var [a, b, c] = []; use(a);", "var [a] = []; use(a);");
    test("var [a, b, c] = []; use(b);", "var [ ,b, ] = []; use(b);");
    test("var [a, b, c] = []; use(c);", "var [ , ,c] = []; use(c);");

    test("var a, b, c; [a, b, c] = []; use(a);", "var a; [a] = []; use(a);");
    test("var a, b, c; [a, b, c] = []; use(b);", "var b; [ ,b, ] = []; use(b);");
    test("var a, b, c; [a, b, c] = []; use(c);", "var c; [ , ,c] = []; use(c);");

    test("var a, b, c; [[a, b, c]] = []; use(a);", "var a; [[a]] = []; use(a);");
    test("var a, b, c; [{a, b, c}] = []; use(a);", "var a; [{a}] = []; use(a);");
    test("var a, b, c; ({x:[a, b, c]} = []); use(a);", "var a; ({x:[a]} = []); use(a);");

    test("var a, b, c; [[a, b, c]] = []; use(b);", "var b; [[ ,b]] = []; use(b);");
    test("var a, b, c; [[a, b, c]] = []; use(c);", "var c; [[ , ,c]] = []; use(c);");

    test("var a, b, c; [a, b, ...c] = []; use(a);", "var a; [a] = []; use(a);");
    test("var a, b, c; [a, b, ...c] = []; use(b);", "var b; [ ,b, ] = []; use(b);");
    test("var a, b, c; [a, b, ...c] = []; use(c);", "var c; [ , ,...c] = []; use(c);");

    test("var a, b, c; [a=1, b=2, c=3] = []; use(a);", "var a; [a=1] = []; use(a);");
    test("var a, b, c; [a=1, b=2, c=3] = []; use(b);", "var b; [   ,b=2, ] = []; use(b);");
    test("var a, b, c; [a=1, b=2, c=3] = []; use(c);", "var c; [   ,   ,c=3] = []; use(c);");

    testSame("var a, b, c; [a.x, b.y, c.z] = []; use(a);"); // unnecessary retention of b,c
    testSame("var a, b, c; [a.x, b.y, c.z] = []; use(b);"); // unnecessary retention of a,c
    testSame("var a, b, c; [a.x, b.y, c.z] = []; use(c);"); // unnecessary retention of a,b

    testSame("var a, b, c; [a().x, b().y, c().z] = []; use(a);");
    testSame("var a, b, c; [a().x, b().y, c().z] = []; use(b);");
    testSame("var a, b, c; [a().x, b().y, c().z] = []; use(c);");
  }

  @Test
  public void testDestructuringArrayPattern1() {
    test(
        lines(
            "var a; var b",
            "[a, b] = [1, 2]"),
        lines(
            "[] = [1, 2]"));

    test(
        lines(
            "var b; var a",
            "[a, b] = [1, 2]"),
        lines(
            "[] = [1, 2]"));

    test(
        lines(
            "var a; var b;",
            "[a] = [1]"),
        lines(
            "[] = [1]"));

    testSame("var [a, b] = [1, 2]; alert(a); alert(b);");
  }

  @Test
  public void testDestructuringObjectPattern0() {
    test("function f({a}) {} f();", "function f() {} f();");
    test("function f({a:b}) {} f();", "function f() {} f();");
    test("function f({[a]:b}) {} f();", "function f() {} f();");
    test("function f({['a']:b}) {} f();", "function f() {} f();");
    testSame("function f({[alert()]:b}) {} f();");
    test("function f({a} = {}) {} f();", "function f() {} f();");
    test("function f({a:b} = {}) {} f();", "function f() {} f();");
    testSame("function f({[alert()]:b} = {}) {} f();");
    test("function f({a} = alert()) {} f();", "function f({} = alert()) {} f();");
    test("function f({a:b} = alert()) {} f();", "function f({} = alert()) {} f();");
    testSame("function f({[alert()]:b} = alert()) {} f();");
    test("function f({a = 1}) {} f();", "function f() {} f();");
    test("function f({a:b = 1}) {} f();", "function f() {} f();");
    test("function f({[a]:b = 1}) {} f();", "function f() {} f();");
    test("function f({['a']:b = 1}) {} f();", "function f() {} f();");
    testSame("function f({[alert()]:b = 1}) {} f();"); // fix me, remove "= 1"
    test("function f({a = 1}) {} f();", "function f() {} f();");
    testSame("function f({a:b = alert()}) {} f();");
    testSame("function f({[externVar]:b = alert()}) {} f();");
    testSame("function f({['a']:b = alert()}) {} f();");
    testSame("function f({[alert()]:b = alert()}) {} f();");

    test("function f({a}, c) {use(c)} f();", "function f({}, c) {use(c)} f();");
    test("function f({a:b}, c) {use(c)} f();", "function f({}, c) {use(c)} f();");
    test("function f({[externVar]:b}, c) {use(c)} f();", "function f({}, c) {use(c)} f();");
    test("function f({['a']:b}, c) {use(c)} f();", "function f({}, c) {use(c)} f();");
    testSame("function f({[use()]:b}, c) {use(c)} f();");
    test("function f({a} = {}, c) {use(c)} f();", "function f({} = {}, c) {use(c)} f();");
    test("function f({a:b} = {}, c) {use(c)} f();", "function f({} = {}, c) {use(c)} f();");
    testSame("function f({[use()]:b} = {}, c) {use(c)} f();");
    test(
        "function f({a} = use(), c) {use(c)} f();", // preserve alignment
        "function f({ } = use(), c) {use(c)} f();");
    test(
        "function f({a:b} = use(), c) {use(c)} f();", // preserve alignment
        "function f({   } = use(), c) {use(c)} f();");
    testSame("function f({[use()]:b} = use(), c) {use(c)} f();");
    test("function f({a = 1}, c) {use(c)} f();", "function f({}, c) {use(c)} f();");
    test("function f({a:b = 1}, c) {use(c)} f();", "function f({}, c) {use(c)} f();");
    test("function f({[a]:b = 1}, c) {use(c)} f();", "function f({}, c) {use(c)} f();");
    test("function f({['a']:b = 1}, c) {use(c)} f();", "function f({}, c) {use(c)} f();");
    testSame("function f({[use()]:b = 1}, c) {use(c)} f();"); // fix me, remove "= 1"

    test("var {a} = {a:1};", "var {} = {a:1};");
    test("var {a:a} = {a:1};", "var {} = {a:1};");
    test("var {['a']:a} = {a:1};", "var {} = {a:1};");
    test("var {['a']:a = 1} = {a:1};", "var {} = {a:1};");
    testSame("var {[alert()]:a = 1} = {a:1};");
    test("var {a:a = 1} = {a:1};", "var {} = {a:1};");
    testSame("var {a:a = alert()} = {a:1};");

    test("var a; ({a} = {a:1});", "({} = {a:1});");
    test("var a; ({a:a} = {a:1});", "({} = {a:1});");
    test("var a; ({['a']:a} = {a:1});", "({} = {a:1});");
    test("var a; ({['a']:a = 1} = {a:1});", "({} = {a:1});");
    testSame("var a; ({[alert()]:a = 1} = {a:1});");
    test("var a; ({a:a = 1} = {a:1});", "({} = {a:1});");
    testSame("var a; ({a:a = alert()} = {a:1});");

    testSame("var a = {}; ({a:a.foo} = {a:1});");
    testSame("var a = {}; ({['a']:a.foo} = {a:1});");
    testSame("var a = {}; ({['a']:a.foo = 1} = {a:1});");
    testSame("var a = {}; ({[alert()]:a.foo = 1} = {a:1});");
    testSame("var a = {}; ({a:a.foo = 1} = {a:1});");
    testSame("var a = {}; ({a:a.foo = alert()} = {a:1});");

    testSame("function a() {} ({a:a().foo} = {a:1});");
    testSame("function a() {} ({['a']:a().foo} = {a:1});");
    testSame("function a() {} ({['a']:a().foo = 1} = {a:1});");
    testSame("function a() {} ({[alert()]:a().foo = 1} = {a:1});");
    testSame("function a() {} ({a:a().foo = 1} = {a:1});");
    testSame("function a() {} ({a:a().foo = alert()} = {a:1});");
  }

  @Test
  public void testDestructuringObjectPattern1() {
    test("var a; var b; ({a, b} = {a:1, b:2})", "({} = {a:1, b:2});");
    test("var a; var b; ({a:a, b:b} = {a:1, b:2})", "({} = {a:1, b:2});");
    testSame("var a; var b; ({[alert()]:a, [alert()]:b} = {x:1, y:2})");

    test("var a; var b; ({a} = {a:1})", "({} = {a:1})");
    test("var a; var b; ({a:a.foo} = {a:1})", "var a; ({a:a.foo} = {a:1})");

    testSame("var {a, b} = {a:1, b:2}; alert(a); alert(b);");

    testSame("var {a} = {p:{}, q:{}}; a.q = 4;");

    // Nested Destructuring
    test(
        "const someObject = {a:{ b:1 }}; var {a: {b}} = someObject; someObject.a.b;",
        "const someObject = {a:{ b:1 }}; var {a: {}} = someObject; someObject.a.b;");

    testSame("const someObject = {a:{ b:1 }}; var {a: {b}} = someObject; b;");
  }

  @Test
  public void testRemoveUnusedVarsDeclaredInDestructuring0() {
    // Array destructuring
    test("var [a, b] = [1, 2]; alert(a);", "var [a] = [1, 2]; alert(a);");

    test("var [a, b] = [1, 2];", "var [] = [1, 2];");

    // Object pattern destructuring
    test(
        "var {a, b} = {a:1, b:2}; alert(a);", // comment to keep before/after alignment
        "var {a,  } = {a:1, b:2}; alert(a);");

    test(
        "var {a, b} = {a:1, b:2};", // comment to keep before/after alignment
        "var {    } = {a:1, b:2};");
  }

  @Test
  public void testRemoveUnusedVarsDeclaredInDestructuring1() {
    // Nested pattern
    test(
        "var {a, b:{c}} = {a:1, b:{c:5}}; alert(a);", "var {a, b:{ }} = {a:1, b:{c:5}}; alert(a);");

    testSame("var {a, b:{c}} = {a:1, b:{c:5}}; alert(a, c);");

    test("var {a, b:{c}} = externVar;", "var {b:{}} = externVar;");

    // Value may have side effects
    test(
        "var {a, b} = {a:alert(), b:alert()};", // preserve before / after alignment
        "var {    } = {a:alert(), b:alert()};");

    test(
        "var {a, b} = alert();", // preserve before / after alignment
        "var {    } = alert();");

    // Same as above case without the destructuring declaration
    test("var a, b = alert();", "alert();");
  }
}

