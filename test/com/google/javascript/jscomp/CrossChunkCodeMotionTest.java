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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CrossChunkCodeMotion}.
 *
 */
@RunWith(JUnit4.class)
public final class CrossChunkCodeMotionTest extends CompilerTestCase {

  private static final String EXTERNS = "alert";
  private boolean parentModuleCanSeeSymbolsDeclaredInChildren = false;

  public CrossChunkCodeMotionTest() {
    super(EXTERNS);
  }

  @Override
  protected int getNumRepetitions() {
    // A single run should be sufficient to move all definitions to their final destinations.
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    disableScriptFeatureValidation();
    parentModuleCanSeeSymbolsDeclaredInChildren = false;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CrossChunkCodeMotion(
        compiler, compiler.getModuleGraph(), parentModuleCanSeeSymbolsDeclaredInChildren);
  }

  @Test
  public void testFunctionMovement1() {
    // This tests lots of things:
    // 1) f1 is declared in m1, and used in m2. Move it to m2
    // 2) f2 is declared in m1, and used in m3 twice. Move it to m3
    // 3) f3 is declared in m1, and used in m2+m3. It stays put
    // 4) g declared in m1 and never used. It stays put
    // 5) h declared in m2 and never used. It stays put
    // 6) f4 declared in m1 and used in m2 as var. It moves to m2

    JSModule[] modules =
        createModuleStar(
            // m1
            lines(
                "function f1(a) { alert(a); }",
                "function f2(a) { alert(a); }",
                "function f3(a) { alert(a); }",
                "function f4() { alert(1); }",
                "function g() { alert('ciao'); }"),
            // m2
            "f1('hi'); f3('bye'); var a = f4; function h(a) { alert('h:' + a); }",
            // m3
            "f2('hi'); f2('hi'); f3('bye');");

    test(
        modules,
        new String[] {
          // m1
          "function f3(a) { alert(a); } function g() { alert('ciao'); }",
          // m2
          lines(
              "function f1(a) { alert(a); }",
              "function f4() { alert(1); }",
              "f1('hi'); f3('bye'); var a = f4;",
              "function h(a) { alert('h:' + a); }",
              ""),
          // m3
          "function f2(a) { alert(a); } f2('hi'); f2('hi'); f3('bye');",
        });
  }

  @Test
  public void testFunctionMovement2() {
    // having f declared as a local variable should block the migration to m2
    JSModule[] modules =
        createModuleStar(
            // m1
            "function f(a) { alert(a); } function g() {var f = 1; f++}",
            // m2
            "f(1);");

    test(
        modules,
        new String[] {
          // m1
          "function g() {var f = 1; f++}",
          // m2
          "function f(a) { alert(a); } f(1);",
        });
  }

  @Test
  public void testFunctionMovement3() {
    // having f declared as a arg should block the migration to m2
    JSModule[] modules =
        createModuleStar(
            // m1
            "function f(a) { alert(a); } function g(f) {f++}",
            // m2
            "f(1);");

    test(
        modules,
        new String[] {
          // m1
          "function g(f) {f++}",
          // m2
          "function f(a) { alert(a); } f(1);",
        });
  }

  @Test
  public void testFunctionMovement4() {
    // Try out moving a function which returns a closure
    JSModule[] modules =
        createModuleStar(
            // m1
            "function f(){return function(a){}}",
            // m2
            "var a = f();");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "function f(){return function(a){}} var a = f();",
        });
  }

  @Test
  public void testFunctionMovement5() {
    // Try moving a recursive function [using factorials for kicks]
    JSModule[] modules =
        createModuleStar(
            // m1
            "function f(n){return (n<1)?1:f(n-1)}",
            // m2
            "var a = f(4);");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "function f(n){return (n<1)?1:f(n-1)} var a = f(4);",
        });
  }

  @Test
  public void testFunctionMovement5b() {
    // Try moving a recursive function declared differently.
    JSModule[] modules =
        createModuleStar(
            // m1
            "var f = function(n){return (n<1)?1:f(n-1)};",
            // m2
            "var a = f(4);");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "var f = function(n){return (n<1)?1:f(n-1)}; var a = f(4);",
        });
  }

  @Test
  public void testFunctionMovement5c() {
    // Try moving a recursive function declared differently, in a nested block scope.
    JSModule[] modules =
        createModuleStar(
            // m1
            "var f = function(n){if(true){if(true){return (n<1)?1:f(n-1)}}};",
            // m2
            "var a = f(4);");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          lines(
              "var f = function(n){if(true){if(true){return (n<1)?1:f(n-1)}}};", "var a = f(4);")
        });
  }

  @Test
  public void testFunctionMovement6() {
    // Try out moving to the common ancestor
    JSModule[] modules =
        createModuleChain(
            // m1
            "function f(){return 1}",
            // m2
            "var a = f();",
            // m3
            "var b = f();");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "function f(){return 1} var a = f();",
          // m3
          "var b = f();",
        });
  }

  @Test
  public void testFunctionMovement7() {
    // Try out moving to the common ancestor with deeper ancestry chain
    JSModule[] modules =
        createModules(
            // m1
            "function f(){return 1}",
            // m2
            "",
            // m3
            "var a = f();",
            // m4
            "var b = f();",
            // m5
            "var c = f();");

    modules[1].addDependency(modules[0]);
    modules[2].addDependency(modules[1]);
    modules[3].addDependency(modules[1]);
    modules[4].addDependency(modules[1]);

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "function f(){return 1}",
          // m3
          "var a = f();",
          // m4
          "var b = f();",
          // m5
          "var c = f();",
        });
  }

  @Test
  public void testFunctionMovement8() {
    // Check what happens with named functions
    JSModule[] modules =
        createModuleChain(
            // m1
            "var v = function f(){return 1}",
            // m2
            "v();");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "var v = function f(){return 1}; v();",
        });
  }

  @Test
  public void testFunctionNonMovement1() {
    // This tests lots of things:
    // 1) we can't move it if it is a class with non-const attributes accessed
    // 2) if it's in an if statement, we can't move it
    // 3) if it's in an while statement, we can't move it [with some extra
    // block elements]
    setAcceptedLanguage(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    testSame(
        createModuleStar(
            // m1
            lines(
                "function f(){};f.prototype.bar=new f;",
                "if(a)function f2(){}",
                "{{while(a)function f3(){}}}"),
            // m2
            "var a = new f();f2();f3();"));
  }

  @Test
  public void testFunctionNonMovement2() {
    // A generic case where 2 modules depend on the first one. But it's the
    // common ancestor, so we can't move.
    testSame(
        createModuleStar(
            // m1
            "function f(){return 1}",
            // m2
            "var a = f();",
            // m3
            "var b = f();"));
  }

  @Test
  public void testEs6ClassMovement1() {
    test(
        createModuleStar(
            // m1
            "class f { bar() {} }",
            // m2
            "var a = new f();"),
        new String[] {"", "class f { bar() {} } var a = new f();"});
    test(
        createModuleStar(
            // m1
            "var f = class { bar() {} };",
            // m2
            "var a = new f();"),
        new String[] {"", "var f = class { bar() {} }; var a = new f();"});
  }

  @Test
  public void testClassMovement1() {
    test(
        createModuleStar(
            // m1
            "function f(){} f.prototype.bar=function (){};",
            // m2
            "var a = new f();"),
        new String[] {"", "function f(){} f.prototype.bar=function (){}; var a = new f();"});
  }

  @Test
  public void testEs6ClassMovement_instanceof() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        createModuleStar(
            // m1
            "class f { bar(){} } 1 instanceof f;",
            // m2
            "var a = new f();"),
        new String[] {
            "'undefined' != typeof f && 1 instanceof f;",
            "class f { bar(){} } var a = new f();"
        });
  }

  @Test
  public void testClassMovement_instanceof() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        createModuleStar(
            // m1
            "function f(){} f.prototype.bar=function (){}; 1 instanceof f;",
            // m2
            "var a = new f();"),
        new String[] {
          "'undefined' != typeof f && 1 instanceof f;",
          "function f(){} f.prototype.bar=function (){}; var a = new f();"
        });
  }

  @Test
  public void testEs6ClassMovement_instanceofTurnedOff() {
    parentModuleCanSeeSymbolsDeclaredInChildren = false;
    testSame(
        createModuleStar(
            // m1
            "class f { bar(){} } 1 instanceof f;",
            // m2
            "var a = new f();"));
  }

  @Test
  public void testClassMovement_instanceofTurnedOff() {
    parentModuleCanSeeSymbolsDeclaredInChildren = false;
    testSame(
        createModuleStar(
            // m1
            "function f(){} f.prototype.bar=function (){}; 1 instanceof f;",
            // m2
            "var a = new f();"));
  }

  @Test
  public void testEs6ClassMovement_instanceof2() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        createModuleStar(
            // m1
            "class f { bar(){} } (true && 1 instanceof f);",
            // m2
            "var a = new f();"),
        new String[] {
          "(true && ('undefined' != typeof f && 1 instanceof f));",
          "class f { bar(){} } var a = new f();"
        });
  }

  @Test
  public void testClassMovement_instanceof2() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        createModuleStar(
            // m1
            "function f(){} f.prototype.bar=function (){}; (true && 1 instanceof f);",
            // m2
            "var a = new f();"),
        new String[] {
          "(true && ('undefined' != typeof f && 1 instanceof f));",
          "function f(){} f.prototype.bar=function (){}; var a = new f();"
        });
  }

  @Test
  public void testClassMovement_alreadyGuardedInstanceof() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        createModuleStar(
            // m1
            lines(
                "function f(){} f.prototype.bar=function (){};",
                "(true && ('undefined' != typeof f && 1 instanceof f));"),
            // m2
            "var a = new f();"),
        new String[] {
          "(true && ('undefined' != typeof f && 1 instanceof f));",
          "function f(){} f.prototype.bar=function (){}; var a = new f();"
        });
  }

  @Test
  public void testClassMovement_instanceof3() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    testSame(
        createModuleStar(
            // m1
            "function f(){} f.prototype.bar=function (){}; f instanceof 1",
            // m2
            "var a = new f();"));
  }

  @Test
  public void testClassMovement_instanceof_noRewriteRequired() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    testSame(
        createModuleStar(
            // m1
            "function f(){} f.prototype.bar=function (){}; 1 instanceof f; new f;",
            // m2
            "var a = new f();"));
  }

  @Test
  public void testClassMovement_instanceof_noRewriteRequired2() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    testSame(
        createModuleChain(
            // m1
            "function f(){} f.prototype.bar=function (){}; new f;",
            // m2
            "1 instanceof f;",
            // m3
            "var a = new f();"));
  }

  @Test
  public void testEs6ClassMovement2() {
    test(
        createModuleChain(
            // m1
            "class f {} f.prototype.bar=3; f.prototype.baz=5;",
            // m2
            "f.prototype.baq = 7;",
            // m3
            "f.prototype.baz = 9;",
            // m4
            "var a = new f();"),
        new String[] {
            // m1
            "",
            // m2
            "",
            // m3
            "",
            // m4
            lines(
                "class f {}",
                "f.prototype.bar = 3;",
                "f.prototype.baz = 5;",
                "f.prototype.baq = 7;",
                "f.prototype.baz = 9;",
                "var a = new f();")
        });
  }

  @Test
  public void testClassMovement2() {
    test(
        createModuleChain(
            // m1
            "function f(){} f.prototype.bar=3; f.prototype.baz=5;",
            // m2
            "f.prototype.baq = 7;",
            // m3
            "f.prototype.baz = 9;",
            // m4
            "var a = new f();"),
        new String[] {
          // m1
          "",
          // m2
          "",
          // m3
          "",
          // m4
          lines(
              "function f(){}",
              "f.prototype.bar=3;",
              "f.prototype.baz=5;",
              "f.prototype.baq = 7;",
              "f.prototype.baz = 9;",
              "var a = new f();")
        });
  }

  @Test
  public void testClassMovement3() {
    test(
        createModuleChain(
            // m1
            "var f = function() {}; f.prototype.bar=3; f.prototype.baz=5;",
            // m2
            "f = 7;",
            // m3
            "f = 9;",
            // m4
            "f = 11;"),
        new String[] {
          // m1
          "",
          // m2
          "",
          // m3
          "",
          // m4
          lines(
              "var f = function() {};",
              "f.prototype.bar=3;",
              "f.prototype.baz=5;",
              "f = 7;",
              "f = 9;",
              "f = 11;")
        });
  }

  @Test
  public void testClassMovement4() {
    testSame(
        createModuleStar(
            // m1
            "function f(){} f.prototype.bar=3; f.prototype.baz=5;",
            // m2
            "f.prototype.baq = 7;",
            // m3
            "var a = new f();"));
  }

  @Test
  public void testClassMovement5() {
    JSModule[] modules =
        createModules(
            // m1
            "function f(){} f.prototype.bar=3; f.prototype.baz=5;",
            // m2
            "",
            // m3
            "f.prototype.baq = 7;",
            // m4
            "var a = new f();");

    modules[1].addDependency(modules[0]);
    modules[2].addDependency(modules[1]);
    modules[3].addDependency(modules[1]);

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "function f(){} f.prototype.bar=3; f.prototype.baz=5;",
          // m3
          "f.prototype.baq = 7;",
          // m4 +
          "var a = new f();"
        });
  }

  @Test
  public void testClassMovement6() {
    test(
        createModuleChain(
            // m1
            "function Foo(){} function Bar(){} goog.inherits(Bar, Foo); new Foo();",
            // m2
            "new Bar();"),
        new String[] {
          // m1
          "function Foo(){} new Foo();",
          // m2
          "function Bar(){} goog.inherits(Bar, Foo); new Bar();"
        });
  }

  @Test
  public void testEs6ClassMovement6() {
    test(
        createModuleChain(
            // m1
            "class Foo{} class Bar extends Foo {} new Foo();",
            // m2
            "new Bar();"),
        new String[] {
          // m1
          "class Foo {} new Foo();",
          // m2
          "class Bar extends Foo {} new Bar();"
        });
  }

  @Test
  public void testClassMovement7() {
    testSame(
        createModuleChain(
            // m1
            "function Foo(){} function Bar(){} goog.inherits(Bar, Foo); new Bar();",
            // m2
            "new Foo();"));
  }

  @Test
  public void testEs6ClassMovement7() {
    testSame(
        createModuleChain(
            // m1
            "class Foo {} class Bar extends Foo {} new Bar();",
            // m2
            "new Foo();"));
  }

  @Test
  public void testClassMovement8() {
    test(
        createModuleChain(
            // m1
            lines(
                "function Foo(){}",
                "Object.defineProperties(Foo.prototype, {a: {get:function(){return 0;}}});"),
            // m2
            "new Foo();"),
        new String[] {
          "", // m1
          lines(
              "function Foo(){}",
              "Object.defineProperties(Foo.prototype, {a: {get:function(){return 0;}}});",
              "new Foo();") // m2
        });
  }

  @Test
  public void testEs6ClassMovement8() {
    test(
        createModuleChain(
            // m1
            "class Foo { get test() { return 0; }}",
            // m2
            "new Foo();"),
        new String[] {
          "", // m1
          "class Foo { get test() { return 0; }} new Foo();" // m2
        });
  }

  @Test
  public void testStubMethodMovement() {
    // The method stub can move, but the unstub definition cannot, because
    // CrossChunkCodeMotion doesn't know where individual methods are used.
    // CrossChunkMethodMotion is responsible for putting the unstub definitions
    // in the right places.
    test(
        createModuleChain(
            // m0
            "function Foo(){} Foo.prototype.bar = JSCompiler_stubMethod(x);",
            // m1
            "Foo.prototype.bar = JSCompiler_unstubMethod(x);",
            // m2
            "new Foo();"),
        new String[] {
          // m0
          "",
          // m1
          lines(
              "function Foo(){} Foo.prototype.bar = JSCompiler_stubMethod(x);",
              "Foo.prototype.bar = JSCompiler_unstubMethod(x);"),
          // m2
          "new Foo();"
        });
  }

  @Test
  public void testNoMoveSideEffectProperty() {
    testSame(
        createModuleChain(
            // m1
            "function Foo(){}  Foo.prototype.bar = createSomething();",
            // m2
            "new Foo();"));
  }

  @Test
  public void testNoMoveSideEffectDefineProperties() {
    testSame(
        createModuleChain(
            // m1
            lines(
                "function Foo(){}",
                "Object.defineProperties(Foo.prototype, {a: {get: createSomething()}})"),
            // m2
            "new Foo();"));
  }

  @Test
  public void testNoMoveSideEffectDefinePropertiesComputed() {
    testSame(
        createModuleChain(
            // m1
            lines(
                "function Foo(){}",
                "Object.defineProperties(Foo.prototype,{[test()]:{get: function() {return 10;}}})"),
            // m2
            "new Foo();"));
  }

  @Test
  public void testAssignMovement() {
    test(
        createModuleChain(
            // m1
            "var f = 3; f = 5;",
            // m2
            "var h = f;"),
        new String[] {
          // m1
          "",
          // m2
          "var f = 3; f = 5; var h = f;"
        });

    // don't move nested assigns
    testSame(
        createModuleChain(
            // m1
            "var f = 3; var g = f = 5;",
            // m2
            "var h = f;"));
  }

  @Test
  public void testNoClassMovement2() {
    test(
        createModuleChain(
            // m1
            "var f = {}; f.h = 5;",
            // m2
            "var h = f;"),
        new String[] {
          // m1
          "",
          // m2
          "var f = {}; f.h = 5; var h = f;"
        });

    // don't move nested getprop assigns
    testSame(
        createModuleChain(
            // m1
            "var f = {}; var g = f.h = 5;",
            // m2
            "var h = f;"));
  }

  @Test
  public void testLiteralMovement1() {
    test(
        createModuleChain(
            // m1
            "var f = {'hi': 'mom', 'bye': function() {}};",
            // m2
            "var h = f;"),
        new String[] {
          // m1
          "",
          // m2
          "var f = {'hi': 'mom', 'bye': function() {}}; var h = f;"
        });
  }

  @Test
  public void testLiteralMovement2() {
    testSame(
        createModuleChain(
            // m1
            "var f = {'hi': 'mom', 'bye': goog.nullFunction};",
            // m2
            "var h = f;"));
  }

  @Test
  public void testLiteralMovement3() {
    test(
        createModuleChain(
            // m1
            "var f = ['hi', function() {}];",
            // m2
            "var h = f;"),
        new String[] {
          // m1
          "",
          // m2
          "var f = ['hi', function() {}]; var h = f;"
        });
  }

  @Test
  public void testLiteralMovement4() {
    testSame(
        createModuleChain(
            // m1
            "var f = ['hi', goog.nullFunction];",
            // m2
            "var h = f;"));
  }

  @Test
  public void testStringTemplateLiteralMovement1() {
    test(
        createModuleChain(
            // m1
            "var s = 'world'; var f = `hi ${s}`;",
            // m2
            "var h = f;"),
        new String[] {
          // m1
          "",
          // m2
          "var s = 'world'; var f = `hi ${s}`; var h = f;"
        });
  }

  @Test
  public void testStringTemplateLiteralMovement2() {
    testSame(
        createModuleChain(
            // m1
            "var f = `hi ${goog.nullFunction()}`;",
            // m2
            "var h = f;"));
  }

  @Test
  public void testVarMovement1() {
    // test moving a variable
    JSModule[] modules =
        createModuleStar(
            // m1
            "var a = 0;",
            // m2
            "var x = a;");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "var a = 0; var x = a;",
        });
  }

  @Test
  public void testLetConstMovement() {
    // test moving a variable
    JSModule[] modules =
        createModuleStar(
            // m1
            "const a = 0;",
            // m2
            "let x = a;");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "const a = 0; let x = a;",
        });
  }

  @Test
  public void testVarMovement2() {
    // Test moving 1 variable out of the block
    JSModule[] modules =
        createModuleStar(
            // m1
            "var a = 0; var b = 1; var c = 2;",
            // m2
            "var x = b;");

    test(
        modules,
        new String[] {
          // m1
          "var a = 0; var c = 2;",
          // m2
          "var b = 1; var x = b;"
        });
  }

  @Test
  public void testLetConstMovement2() {
    // Test moving 1 variable out of the block
    JSModule[] modules =
        createModuleStar(
            // m1
            "const a = 0; const b = 1; const c = 2;",
            // m2
            "let x = b;");

    test(
        modules,
        new String[] {
          // m1
          "const a = 0; const c = 2;",
          // m2
          "const b = 1; let x = b;"
        });
  }

  @Test
  public void testVarMovement3() {
    // Test moving all variables out of the block
    JSModule[] modules =
        createModuleStar(
            // m1
            "var a = 0; var b = 1;",
            // m2
            "var x = a + b;");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "var a = 0; var b = 1; var x = a + b;"
        });
  }

  @Test
  public void testLetConstMovement3() {
    // Test moving all variables out of the block
    JSModule[] modules =
        createModuleStar(
            // m1
            "const a = 0; const b = 1;",
            // m2
            "let x = a + b;");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "const a = 0; const b = 1; let x = a + b;"
        });
  }

  @Test
  public void testVarMovement4() {
    // Test moving a function
    JSModule[] modules =
        createModuleStar(
            // m1
            "var a = function(){alert(1)};",
            // m2
            "var x = a;");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "var a = function(){alert(1)}; var x = a;"
        });
  }

  @Test
  public void testLetConstMovement4() {
    // Test moving a function
    JSModule[] modules =
        createModuleStar(
            // m1
            "const a = function(){alert(1)};",
            // m2
            "let x = a;");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "const a = function(){alert(1)}; let x = a;"
        });
  }

  @Test
  public void testVarMovement5() {
    // Don't move a function outside of scope
    testSame(
        createModuleStar(
            // m1
            "var a = alert;",
            // m2
            "var x = a;"));
  }

  @Test
  public void testLetConstMovement5() {
    // Don't move a function outside of scope
    testSame(
        createModuleStar(
            // m1
            "const a = alert;",
            // m2
            "let x = a;"));
  }

  @Test
  public void testVarMovement6() {
    // Test moving a var with no assigned value
    JSModule[] modules =
        createModuleStar(
            // m1
            "var a;",
            // m2
            "var x = a;");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "var a; var x = a;"
        });
  }

  @Test
  public void testLetMovement6() {
    // Test moving a let with no assigned value
    JSModule[] modules =
        createModuleStar(
            // m1
            "let a;",
            // m2
            "let x = a;");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "let a; let x = a;"
        });
  }

  @Test
  public void testVarMovement7() {
    // Don't move a variable higher in the dependency tree
    testSame(
        createModuleStar(
            // m1
            "function f() {g();} f();",
            // m2
            "function g(){};"));
  }

  @Test
  public void testVarMovement8() {
    JSModule[] modules =
        createModuleBush(
            // m1
            "var a = 0;",
            // m2 -> m1
            "",
            // m3 -> m2
            "var x = a;",
            // m4 -> m2
            "var y = a;");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "var a = 0;",
          // m3
          "var x = a;",
          // m4
          "var y = a;"
        });
  }

  @Test
  public void testLetConstMovement8() {
    JSModule[] modules =
        createModuleBush(
            // m1
            "const a = 0;",
            // m2 -> m1
            "",
            // m3 -> m2
            "let x = a;",
            // m4 -> m2
            "let y = a;");

    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "const a = 0;",
          // m3
          "let x = a;",
          // m4
          "let y = a;"
        });
  }

  @Test
  public void testVarMovement9() {
    JSModule[] modules =
        createModuleTree(
            // m1
            "var a = 0; var b = 1; var c = 3;",
            // m2 -> m1
            "",
            // m3 -> m1
            "",
            // m4 -> m2
            "a;",
            // m5 -> m2
            "a;c;",
            // m6 -> m3
            "b;",
            // m7 -> m4
            "b;c;");

    test(
        modules,
        new String[] {
          // m1
          "var c = 3;",
          // m2
          "var a = 0;",
          // m3
          "var b = 1;",
          // m4
          "a;",
          // m5
          "a;c;",
          // m6
          "b;",
          // m7
          "b;c;"
        });
  }

  @Test
  public void testConstMovement9() {
    JSModule[] modules =
        createModuleTree(
            // m1
            "const a = 0; const b = 1; const c = 3;",
            // m2 -> m1
            "",
            // m3 -> m1
            "",
            // m4 -> m2
            "a;",
            // m5 -> m2
            "a;c;",
            // m6 -> m3
            "b;",
            // m7 -> m4
            "b;c;");

    test(
        modules,
        new String[] {
          // m1
          "const c = 3;",
          // m2
          "const a = 0;",
          // m3
          "const b = 1;",
          // m4
          "a;",
          // m5
          "a;c;",
          // m6
          "b;",
          // m7
          "b;c;"
        });
  }

  @Test
  public void testClinit1() {
    JSModule[] modules =
        createModuleChain(
            // m1
            "function Foo$clinit() { Foo$clinit = function() {}; }",
            // m2
            "Foo$clinit();");
    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "function Foo$clinit() { Foo$clinit = function() {}; } Foo$clinit();"
        });
  }

  @Test
  public void testClinit2() {
    JSModule[] modules =
        createModuleChain(
            // m1
            "var Foo$clinit = function() { Foo$clinit = function() {}; };",
            // m2
            "Foo$clinit();");
    test(
        modules,
        new String[] {
          // m1
          "",
          // m2
          "var Foo$clinit = function() { Foo$clinit = function() {}; }; Foo$clinit();"
        });
  }

  @Test
  public void testClone1() {
    test(
        createModuleChain(
            // m1
            "function f(){} f.prototype.clone = function() { return new f };",
            // m2
            "var a = (new f).clone();"),
        new String[] {
          // m1
          "",
          // m2
          lines(
              "function f(){}",
              "f.prototype.clone = function() { return new f() };",
              "var a = (new f).clone();",
              "")
        });
  }

  @Test
  public void testClone2() {
    test(
        createModuleChain(
            // m1
            lines(
                "function f(){}",
                "f.prototype.cloneFun = function() {",
                "  return function() {new f}",
                "};"),
            // m2
            "var a = (new f).cloneFun();"),
        new String[] {
          // m1
          "",
          lines(
              "function f(){}",
              "f.prototype.cloneFun = function() {",
              "  return function() {new f}",
              "};",
              "var a = (new f).cloneFun();")
        });
  }

  @Test
  public void testBug4118005() {
    testSame(
        createModuleChain(
            // m1
            "var m = 1;\n"
                + "(function () {\n"
                + " var x = 1;\n"
                + " m = function() { return x };\n"
                + "})();\n",
            // m2
            "m();"));
  }

  @Test
  public void testEmptyModule() {
    // When the dest module is empty, it might try to move the code to the
    // one of the modules that the empty module depends on. In some cases
    // this might ended up to be the same module as the definition of the code.
    // When that happens, CrossChunkCodeMotion might report a code change
    // while nothing is moved. This should not be a problem if we know all
    // modules are non-empty.
    JSModule m1 = new JSModule("m1");
    m1.add(SourceFile.fromCode("m1", "function x() {}"));

    JSModule empty = new JSModule("empty");
    empty.addDependency(m1);

    JSModule m2 = new JSModule("m2");
    m2.add(SourceFile.fromCode("m2", "x()"));
    m2.addDependency(empty);

    JSModule m3 = new JSModule("m3");
    m3.add(SourceFile.fromCode("m3", "x()"));
    m3.addDependency(empty);

    test(new JSModule[] {m1, empty, m2, m3}, new String[] {"", "function x() {}", "x()", "x()"});
  }

  @Test
  public void testAbstractMethod() {
    test(
        createModuleStar(
            // m1
            "var abstractMethod = function () {};"
                + "function F(){} F.prototype.bar=abstractMethod;"
                + "function G(){} G.prototype.bar=abstractMethod;",
            // m2
            "var f = new F();",
            // m3
            "var g = new G();"),
        new String[] {
          "var abstractMethod = function () {};",
          "function F(){} F.prototype.bar=abstractMethod; var f = new F();",
          "function G(){} G.prototype.bar=abstractMethod; var g = new G();"
        });
  }

  @Test
  public void testMovableUseBeforeDeclaration() {
    test(
        createModuleChain(
            // m0
            "function f() { g(); } function g() {}",
            // m1
            "f();"),
        new String[] {
          // m0
          "",
          // m1
          "function g() {} function f() { g(); } f();"
        });
  }

  @Test
  public void testImmovableUseBeforeDeclaration() {
    testSame(
        createModuleChain(
            // m0
            lines(
                "g();", // must recognize this as a reference to the following declaration
                "function g() {}"),
            // m1
            "g();"));

    testSame(
        createModuleChain(
            // m0
            lines(
                "function f() { g(); }",
                "function g() {}",
                "f();"), // f() cannot move, so neither can g()
            // m1
            "g();"));
  }

  @Test
  public void testSplitDeclaration() {
    test(
        createModuleChain(
            // m0
            lines(
                "function a() { b(); }",
                "function b() {}",
                "function c() {}",
                "a.prototype.x = function() { c(); };"),
            // m1
            "a();"),
        new String[] {
          // m0
          "",
          // m1
          lines(
              "function b() {}",
              "function c() {}",
              "function a() { b(); }",
              "a.prototype.x = function() { c(); };",
              "a();"),
        });
  }

  @Test
  public void testOutOfOrderAfterSplitDeclaration() {
    test(
        createModuleChain(
            // m0
            lines(
                "function a() { c(); }",
                "function b() {}",
                "a.prototype.x = function() { c(); };",
                "function c() {}",
                ""),
            // m1
            "a();"),
        new String[] {
          // m0
          "function b() {}",
          // m1
          lines(
              "function c() {}",
              "function a() { c(); }",
              "a.prototype.x = function() { c(); };",
              "a();",
              ""),
        });
  }

  @Test
  public void testOutOfOrderWithInterveningReferrer() {
    test(
        createModuleChain(
            // m0
            "function a() { c(); } function b() { a(); } function c() {}",
            // m1
            "b();"),
        new String[] {
          // m0
          "",
          // m1
          "function c() {} function a() { c(); } function b() { a(); } b();",
        });
  }

  @Test
  public void testOutOfOrderWithDifferentReferrers() {
    test(
        createModuleChain(
            // m0
            "function a() { b(); } function b() {}",
            // m1
            "b();",
            // m2
            "a();"),
        new String[] {
          // m0
          "",
          // m1
          "function b() { } b();",
          "function a() { b(); } a();",
        });
  }

  @Test
  public void testCircularWithDifferentReferrers() {
    test(
        createModuleChain(
            // m0
            "function a() { b(); } function b() { a(); }",
            // m1
            "b();",
            // m2
            "a();"),
        new String[] {
          // m0
          "",
          // m1
          "function a() { b(); } function b() { a(); } b();",
          "a();",
        });
  }

  @Test
  public void testSmallestCoveringDependencyDoesNotDependOnDeclarationModule() {
    //       m0
    //      /  \
    //    m1   m2  // declaration in m1
    //    |    /|  // smallest common dep is m2
    //    m3_ | |  // best place for declaration is m3
    //   / | X  |
    //  /  |/ \ /
    // m4  m5  m6  // references in m5 and m6
    JSModule[] m =
        createModules(
            // m0
            "",
            // m1
            "function f() {}",
            // m2
            "",
            // m3
            "",
            // m4
            "",
            // m5
            "f();",
            // m6
            "f();");

    m[1].addDependency(m[0]);
    m[2].addDependency(m[0]);
    m[3].addDependency(m[1]);
    m[4].addDependency(m[3]);
    m[5].addDependency(m[2]);
    m[5].addDependency(m[3]);
    m[6].addDependency(m[2]);
    m[6].addDependency(m[3]);

    test(
        m,
        new String[] {
          // m0
          "",
          // m1
          "",
          // m2
          "",
          // m3
          "function f() {}",
          // m4
          "",
          // m5
          "f();",
          // m6
          "f();",
        });
  }

  @Test
  public void testEarlyReferencesPinLateDeclarations() {
    testSame(
        createModuleChain(
            // m0
            "function C() {} C.prototype.x = 1; var globalC = new C();",
            // m1
            "C.prototype.x = 2; globalC.x;", // globalC.x == 2 - not safe to move declaration
            // m2
            "new C().x;"));
  }

  @Test
  public void testMovedInstanceofIsHandledCorrectly() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        createModuleChain(
            // m0
            lines(
                "function C() {}",
                "function X() {}",
                "X.prototype.a = function(x) { return x instanceof C; }",
                ""),
            // m1
            "new C();",
            // m2
            "new X();"),
        new String[] {
          // m0
          "",
          // m1
          "function C() {} new C();",
          // m2
          lines(
              "function X() {}",
              // no need to guard instanceof
              "X.prototype.a = function(x) { return x instanceof C; }",
              "new X();",
              ""),
        });
    test(
        createModuleChain(
            // m0
            lines(
                "function C() {}",
                "function X() {}",
                "X.prototype.a = function(x) { return x instanceof C; }",
                ""),
            // m1
            "new X();",
            // m2
            "new C();"),
        new String[] {
          // m0
          "",
          // m1
          lines(
              "function X() {}",
              "X.prototype.a = function(x) { return 'undefined' != typeof C && x instanceof C; }",
              "new X();",
              ""),
          // m2
          "function C() {} new C();",
        });
  }

  @Test
  public void testValueNotWellDefined() {
    // TODO(bradfordcsmith): This prevents us from guaranteeing that all moves are made in a single
    //     pass. Code movement in the first pass may cause some variables to become "well defined"
    //     that weren't before, unblocking movement of some statements.
    // B is blocked from moving because A is used before it is defined.
    // See ReferenceCollection#isWellDefined and CrossChunkReferenceCollector#canMoveValue
    test(
        createModuleChain(
            // m0
            "function f() { return A; } var A = 1; var B = A;",
            // m1
            "f(); function f2() { return B; }"),
        new String[] {
          // m0
          "var A = 1; var B = A;",
          // m1
          "function f() { return A; } f(); function f2() { return B; }"
        });
  }

  @Test
  public void testDestructuringDeclarationsNotMovable() {
    testSame(createModuleChain("const [a] = [];", "a;"));
    testSame(createModuleChain("const {a} = { a: 1 };", "a;"));
  }

  @Test
  public void testDestructuringAssignmentsAreReferences() {
    testSame(createModuleChain("let a = 1; [a] = [5];", "a;"));
    testSame(createModuleChain("let a = 1; ({x: a} = {x: 5});", "a;"));
    test(
        createModuleChain("let a = 1;", "[a] = [5];", "a;"),
        new String[] {"", "let a = 1; [a] = [5];", "a;"});
    test(
        createModuleChain("let a = 1;", "({x: a} = {x: 5});", "a;"),
        new String[] {"", "let a = 1; ({x: a} = {x: 5});", "a;"});
  }

  @Test
  public void testDefaultParamValuesAreReferences() {
    testSame(createModuleChain("let a = 1; function f(x = a) {} f();", "a;"));
    test(
        createModuleChain("let a = 1; function f(x = a) {}", "f();"),
        new String[] {"", "let a = 1; function f(x = a) {} f();"});
  }

  @Test
  public void testSpreadCountsAsAReference() {
    test(
        createModuleChain("let a = [];", "function f(...args) {} f(...a);", "a;"),
        new String[] {"", "let a = []; function f(...args) {} f(...a);", "a;"});
  }

  @Test
  public void testObjectLiteralMethods() {
    // Object literal methods, getters, and setters are movable and references within them are
    // handled correctly.
    test(
        createModuleChain(
            "const a = 1;",
            "const o = { foo() {return a;}, get x() {}, set x(v) {a = v;} };",
            "o;",
            "a;"),
        new String[] {
          "",
          "",
          "const a = 1; const o = { foo() {return a;}, get x() {}, set x(v) {a = v;} }; o;",
          "a;"
        });
  }

  @Test
  public void testComputedProperties() {
    // Computed properties are movable if the key expression is a literal.
    test(
        createModuleChain("let a = { ['something']: 1};", "a;"),
        new String[] {"", "let a = { ['something']: 1}; a;"});

    // Computed properties are movable if the key is a well defined variable
    test(createModuleChain(
        "const x = 1; let a = { [x]: 1};", "a;"),
        new String[] {"", "const x = 1; let a = { [x]: 1}; a;"});
    test(createModuleChain(
        "const x = 1; let a = class { [x]() {} };", "a;"),
        new String[] {"", "const x = 1; let a = class { [x]() {} }; a;"});

    // Computed properties are not movable if the key is an unknown variable
    testSame(createModuleChain("let a = { [x]: 1};", "a;"));
    testSame(createModuleChain("let a = class { [x]() {} };", "a;"));

    // Computed properties are not movable if the key is a
    testSame(createModuleChain("let a = { [x]: 1};", "a;"));

    // references in computed properties are honored
    test(
        createModuleChain("let a = 1;", "let b = { [a + 1]: 2 };", "a;"),
        new String[] {"", "let a = 1; let b = { [a + 1]: 2 };", "a;"});
 }
}
