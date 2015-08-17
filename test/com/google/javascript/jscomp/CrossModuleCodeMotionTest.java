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

/**
 * Tests for {@link CrossModuleCodeMotion}.
 *
 */
public final class CrossModuleCodeMotionTest extends CompilerTestCase {

  private static final String EXTERNS = "alert";
  private boolean parentModuleCanSeeSymbolsDeclaredInChildren = false;

  public CrossModuleCodeMotionTest() {
    super(EXTERNS);
  }

  @Override
  public void setUp() {
    parentModuleCanSeeSymbolsDeclaredInChildren = false;
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CrossModuleCodeMotion(
        compiler,
        compiler.getModuleGraph(),
        parentModuleCanSeeSymbolsDeclaredInChildren);
  }

  public void testFunctionMovement1() {
    // This tests lots of things:
    // 1) f1 is declared in m1, and used in m2. Move it to m2
    // 2) f2 is declared in m1, and used in m3 twice. Move it to m3
    // 3) f3 is declared in m1, and used in m2+m3. It stays put
    // 4) g declared in m1 and never used. It stays put
    // 5) h declared in m2 and never used. It stays put
    // 6) f4 declared in m1 and used in m2 as var. It moves to m2

    JSModule[] modules = createModuleStar(
      // m1
      "function f1(a) { alert(a); }" +
      "function f2(a) { alert(a); }" +
      "function f3(a) { alert(a); }" +
      "function f4() { alert(1); }" +
      "function g() { alert('ciao'); }",
      // m2
      "f1('hi'); f3('bye'); var a = f4;" +
      "function h(a) { alert('h:' + a); }",
      // m3
      "f2('hi'); f2('hi'); f3('bye');");

    test(modules, new String[] {
      // m1
      "function f3(a) { alert(a); }" +
      "function g() { alert('ciao'); }",
      // m2
      "function f4() { alert(1); }" +
      "function f1(a) { alert(a); }" +
      "f1('hi'); f3('bye'); var a = f4;" +
      "function h(a) { alert('h:' + a); }",
      // m3
      "function f2(a) { alert(a); }" +
      "f2('hi'); f2('hi'); f3('bye');",
    });
  }

  public void testFunctionMovement2() {
    // having f declared as a local variable should block the migration to m2
    JSModule[] modules = createModuleStar(
      // m1
      "function f(a) { alert(a); }" +
      "function g() {var f = 1; f++}",
      // m2
      "f(1);");

    test(modules, new String[] {
      // m1
      "function g() {var f = 1; f++}",
      // m2
      "function f(a) { alert(a); }" +
      "f(1);",
    });
  }

  public void testFunctionMovement3() {
    // having f declared as a arg should block the migration to m2
    JSModule[] modules = createModuleStar(
      // m1
      "function f(a) { alert(a); }" +
      "function g(f) {f++}",
      // m2
      "f(1);");

    test(modules, new String[] {
      // m1
      "function g(f) {f++}",
      // m2
      "function f(a) { alert(a); }" +
      "f(1);",
    });
  }

  public void testFunctionMovement4() {
    // Try out moving a function which returns a closure
    JSModule[] modules = createModuleStar(
      // m1
      "function f(){return function(a){}}",
      // m2
      "var a = f();"
    );

    test(modules, new String[] {
      // m1
      "",
      // m2
      "function f(){return function(a){}}" +
      "var a = f();",
    });
  }

  public void testFunctionMovement5() {
    // Try moving a recursive function [using factorials for kicks]
    JSModule[] modules = createModuleStar(
      // m1
      "function f(n){return (n<1)?1:f(n-1)}",
      // m2
      "var a = f(4);"
    );

    test(modules, new String[] {
      // m1
      "",
      // m2
      "function f(n){return (n<1)?1:f(n-1)}" +
      "var a = f(4);",
    });
  }

  public void testFunctionMovement5b() {
    // Try moving a recursive function declared differently.
    JSModule[] modules = createModuleStar(
      // m1
      "var f = function(n){return (n<1)?1:f(n-1)};",
      // m2
      "var a = f(4);"
    );

    test(modules, new String[] {
      // m1
      "",
      // m2
      "var f = function(n){return (n<1)?1:f(n-1)};" +
      "var a = f(4);",
    });
  }

  public void testFunctionMovement5c() {
    // Try moving a recursive function declared differently, in a nested block scope.
    JSModule[] modules = createModuleStar(
      // m1
      "var f = function(n){if(true){if(true){return (n<1)?1:f(n-1)}}};",
      // m2
      "var a = f(4);"
    );

    test(modules, new String[] {
      // m1
      "",
      // m2
      LINE_JOINER.join(
          "var f = function(n){if(true){if(true){return (n<1)?1:f(n-1)}}};",
          "var a = f(4);")
    });
  }

  public void testFunctionMovement6() {
    // Try out moving to the common ancestor
    JSModule[] modules = createModuleChain(
      // m1
      "function f(){return 1}",
      // m2
      "var a = f();",
      // m3
      "var b = f();"
    );

    test(modules, new String[] {
      // m1
      "",
      // m2
      "function f(){return 1}" +
      "var a = f();",
      // m3
      "var b = f();",
    });
  }

  public void testFunctionMovement7() {
    // Try out moving to the common ancestor with deeper ancestry chain
    JSModule[] modules = createModules(
      // m1
      "function f(){return 1}",
      // m2
      "",
      // m3
      "var a = f();",
      // m4
      "var b = f();",
      // m5
      "var c = f();"
    );


    modules[1].addDependency(modules[0]);
    modules[2].addDependency(modules[1]);
    modules[3].addDependency(modules[1]);
    modules[4].addDependency(modules[1]);

    test(modules, new String[] {
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

  public void testFunctionMovement8() {
    // Check what happens with named functions
    JSModule[] modules = createModuleChain(
      // m1
      "var v = function f(){return 1}",
      // m2
      "v();"
    );

    test(modules, new String[] {
      // m1
      "",
      // m2
      "var v = function f(){return 1};" +
      "v();",
    });
  }

  public void testFunctionNonMovement1() {
    // This tests lots of things:
    // 1) we can't move it if it is a class with non-const attributes accessed
    // 2) if it's in an if statement, we can't move it
    // 3) if it's in an while statement, we can't move it [with some extra
    // block elements]
    testSame(createModuleStar(
      // m1
      "function f(){};f.prototype.bar=new f;" +
      "if(a)function f2(){}" +
      "{{while(a)function f3(){}}}",
      // m2
      "var a = new f();f2();f3();"));
  }

  public void testFunctionNonMovement2() {
    // A generic case where 2 modules depend on the first one. But it's the
    // common ancestor, so we can't move.
    testSame(createModuleStar(
      // m1
      "function f(){return 1}",
      // m2
      "var a = f();",
      // m3
      "var b = f();"));
  }

  public void testClassMovement1() {
    test(createModuleStar(
             // m1
             "function f(){} f.prototype.bar=function (){};",
             // m2
             "var a = new f();"),
         new String[] {
           "",
           "function f(){} f.prototype.bar=function (){};" +
           "var a = new f();"
         });
  }

  public void testClassMovement_instanceof() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(createModuleStar(
             // m1
             "function f(){} f.prototype.bar=function (){};" +
             "1 instanceof f;",
             // m2
             "var a = new f();"),
         new String[] {
           "'undefined' != typeof f && 1 instanceof f;",
           "function f(){} f.prototype.bar=function (){};" +
           "var a = new f();"
         });
  }

  public void testClassMovement_instanceofTurnedOff() {
    parentModuleCanSeeSymbolsDeclaredInChildren = false;
    testSame(createModuleStar(
             // m1
             "function f(){} f.prototype.bar=function (){};" +
             "1 instanceof f;",
             // m2
             "var a = new f();"));
  }

  public void testClassMovement_instanceof2() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(createModuleStar(
             // m1
             "function f(){} f.prototype.bar=function (){};" +
             "(true && 1 instanceof f);",
             // m2
             "var a = new f();"),
         new String[] {
           "(true && ('undefined' != typeof f && 1 instanceof f));",
           "function f(){} f.prototype.bar=function (){};" +
           "var a = new f();"
         });
  }

  public void testClassMovement_instanceof3() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    testSame(createModuleStar(
             // m1
             "function f(){} f.prototype.bar=function (){};" +
             "f instanceof 1",
             // m2
             "var a = new f();"));
  }

  public void testClassMovement_instanceof_noRewriteRequired() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    testSame(createModuleStar(
             // m1
             "function f(){} f.prototype.bar=function (){};" +
             "1 instanceof f;" +
             "new f;",
             // m2
             "var a = new f();"));
  }

  public void testClassMovement_instanceof_noRewriteRequired2() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    testSame(createModuleChain(
             // m1
             "function f(){} f.prototype.bar=function (){};" +
             "new f;",
             // m2
             "1 instanceof f;",
             // m3
             "var a = new f();"));
  }

  public void testClassMovement2() {
    // NOTE: this is the result of two iterations
    test(createModuleChain(
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
           "function f(){} f.prototype.bar=3; f.prototype.baz=5;" +
           "f.prototype.baq = 7;" +
           "f.prototype.baz = 9;",
           // m4
           "var a = new f();"
         });
  }

  public void testClassMovement3() {
    // NOTE: this is the result of two iterations
    test(createModuleChain(
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
           "var f = function() {}; f.prototype.bar=3; f.prototype.baz=5;" +
           "f = 7;" +
           "f = 9;",
           // m4
           "f = 11;"
         });
  }

  public void testClassMovement4() {
    testSame(createModuleStar(
                 // m1
                 "function f(){} f.prototype.bar=3; f.prototype.baz=5;",
                 // m2
                 "f.prototype.baq = 7;",
                 // m3
                 "var a = new f();"));
  }

  public void testClassMovement5() {
    JSModule[] modules = createModules(
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

    test(modules,
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

  public void testClassMovement6() {
    test(createModuleChain(
             // m1
             "function Foo(){} function Bar(){} goog.inherits(Bar, Foo);" +
             "new Foo();",
             // m2
             "new Bar();"),
         new String[] {
           // m1
           "function Foo(){} new Foo();",
           // m2
           "function Bar(){} goog.inherits(Bar, Foo); new Bar();"
         });
  }

  public void testClassMovement7() {
    testSame(createModuleChain(
                 // m1
                 "function Foo(){} function Bar(){} goog.inherits(Bar, Foo);" +
                 "new Bar();",
                 // m2
                 "new Foo();"));
  }

  public void testStubMethodMovement1() {
    test(createModuleChain(
             // m1
             "function Foo(){} " +
             "Foo.prototype.bar = JSCompiler_stubMethod(x);",
             // m2
             "new Foo();"),
        new String[] {
          // m1
          "",
          "function Foo(){} " +
          "Foo.prototype.bar = JSCompiler_stubMethod(x);" +
          "new Foo();"
        });
  }

  public void testStubMethodMovement2() {
    test(createModuleChain(
             // m1
             "function Foo(){} " +
             "Foo.prototype.bar = JSCompiler_unstubMethod(x);",
             // m2
             "new Foo();"),
        new String[] {
          // m1
          "",
          "function Foo(){} " +
          "Foo.prototype.bar = JSCompiler_unstubMethod(x);" +
          "new Foo();"
        });
  }

  public void testNoMoveSideEffectProperty() {
    testSame(createModuleChain(
                 // m1
                 "function Foo(){} " +
                 "Foo.prototype.bar = createSomething();",
                 // m2
                 "new Foo();"));
  }

  public void testAssignMovement() {
    test(createModuleChain(
             // m1
             "var f = 3;" +
             "f = 5;",
             // m2
             "var h = f;"),
        new String[] {
          // m1
          "",
          // m2
          "var f = 3;" +
          "f = 5;" +
          "var h = f;"
        });

    // don't move nested assigns
    testSame(createModuleChain(
                 // m1
                 "var f = 3;" +
                 "var g = f = 5;",
                 // m2
                 "var h = f;"));
  }

  public void testNoClassMovement2() {
    test(createModuleChain(
             // m1
             "var f = {};" +
             "f.h = 5;",
             // m2
             "var h = f;"),
        new String[] {
          // m1
          "",
          // m2
          "var f = {};" +
          "f.h = 5;" +
          "var h = f;"
        });

    // don't move nested getprop assigns
    testSame(createModuleChain(
                 // m1
                 "var f = {};" +
                 "var g = f.h = 5;",
                 // m2
                 "var h = f;"));
  }

  public void testLiteralMovement1() {
    test(createModuleChain(
             // m1
             "var f = {'hi': 'mom', 'bye': function() {}};",
             // m2
             "var h = f;"),
        new String[] {
          // m1
          "",
          // m2
          "var f = {'hi': 'mom', 'bye': function() {}};" +
          "var h = f;"
        });
  }

  public void testLiteralMovement2() {
    testSame(createModuleChain(
                 // m1
                 "var f = {'hi': 'mom', 'bye': goog.nullFunction};",
                 // m2
                 "var h = f;"));
  }

  public void testLiteralMovement3() {
    test(createModuleChain(
             // m1
             "var f = ['hi', function() {}];",
             // m2
             "var h = f;"),
        new String[] {
          // m1
          "",
          // m2
          "var f = ['hi', function() {}];" +
          "var h = f;"
        });
  }

  public void testLiteralMovement4() {
    testSame(createModuleChain(
                 // m1
                 "var f = ['hi', goog.nullFunction];",
                 // m2
                 "var h = f;"));
  }

  public void testVarMovement1() {
    // test moving a variable
    JSModule[] modules = createModuleStar(
      // m1
      "var a = 0;",
      // m2
      "var x = a;"
    );

    test(modules, new String[] {
      // m1
      "",
      // m2
      "var a = 0;" +
      "var x = a;",
    });
  }

  public void testVarMovement2() {
    // Test moving 1 variable out of the block
    JSModule[] modules = createModuleStar(
      // m1
      "var a = 0; var b = 1; var c = 2;",
      // m2
      "var x = b;"
    );

    test(modules, new String[] {
      // m1
      "var a = 0; var c = 2;",
      // m2
      "var b = 1;" +
      "var x = b;"
    });
  }

  public void testVarMovement3() {
    // Test moving all variables out of the block
    JSModule[] modules = createModuleStar(
      // m1
      "var a = 0; var b = 1;",
      // m2
      "var x = a + b;"
    );

    test(modules, new String[] {
      // m1
      "",
      // m2
      "var b = 1;" +
      "var a = 0;" +
      "var x = a + b;"
    });
  }


  public void testVarMovement4() {
    // Test moving a function
    JSModule[] modules = createModuleStar(
      // m1
      "var a = function(){alert(1)};",
      // m2
      "var x = a;"
    );

    test(modules, new String[] {
      // m1
      "",
      // m2
      "var a = function(){alert(1)};" +
      "var x = a;"
    });
  }


  public void testVarMovement5() {
    // Don't move a function outside of scope
    testSame(createModuleStar(
      // m1
      "var a = alert;",
      // m2
      "var x = a;"));
  }

  public void testVarMovement6() {
    // Test moving a var with no assigned value
    JSModule[] modules = createModuleStar(
      // m1
      "var a;",
      // m2
      "var x = a;"
    );

    test(modules, new String[] {
      // m1
      "",
      // m2
      "var a;" +
      "var x = a;"
    });
  }

  public void testVarMovement7() {
    // Don't move a variable higher in the dependency tree
    testSame(createModuleStar(
      // m1
      "function f() {g();}",
      // m2
      "function g(){};"));
  }

  public void testVarMovement8() {
    JSModule[] modules = createModuleBush(
      // m1
      "var a = 0;",
      // m2 -> m1
      "",
      // m3 -> m2
      "var x = a;",
      // m4 -> m2
      "var y = a;"
    );

    test(modules, new String[] {
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

  public void testVarMovement9() {
    JSModule[] modules = createModuleTree(
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
      "b;c;"
    );

    test(modules, new String[] {
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

  public void testClone1() {
    test(createModuleChain(
             // m1
             "function f(){} f.prototype.clone = function() { return new f };",
             // m2
             "var a = (new f).clone();"),
         new String[] {
           // m1
           "",
           "function f(){} f.prototype.clone = function() { return new f() };" +
           // m2
           "var a = (new f).clone();"
         });
  }

  public void testClone2() {
    test(createModuleChain(
             // m1
             "function f(){}" +
             "f.prototype.cloneFun = function() {" +
             "  return function() {new f}" +
             "};",
             // m2
             "var a = (new f).cloneFun();"),
         new String[] {
           // m1
           "",
           "function f(){}" +
           "f.prototype.cloneFun = function() {" +
           "  return function() {new f}" +
           "};" +
           // m2
           "var a = (new f).cloneFun();"
         });
  }

  public void testBug4118005() {
    testSame(createModuleChain(
             // m1
             "var m = 1;\n" +
             "(function () {\n" +
             " var x = 1;\n" +
             " m = function() { return x };\n" +
             "})();\n",
             // m2
             "m();"));
  }

  public void testEmptyModule() {
    // When the dest module is empty, it might try to move the code to the
    // one of the modules that the empty module depends on. In some cases
    // this might ended up to be the same module as the definition of the code.
    // When that happens, CrossModuleCodeMotion might report a code change
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

    test(new JSModule[] {m1,empty,m2,m3},
        new String[] {
          "",
          "function x() {}",
          "x()",
          "x()"
    });
  }

  public void testAbstractMethod() {
    test(createModuleStar(
             // m1
             "var abstractMethod = function () {};" +
             "function F(){} F.prototype.bar=abstractMethod;" +
             "function G(){} G.prototype.bar=abstractMethod;",
             // m2
             "var f = new F();",
             // m3
             "var g = new G();"),
         new String[] {
           "var abstractMethod = function () {};",
           "function F(){} F.prototype.bar=abstractMethod;" +
           "var f = new F();",
           "function G(){} G.prototype.bar=abstractMethod;" +
           "var g = new G();"
         });
  }
}
