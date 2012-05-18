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
 * Tests for {@link CrossModuleMethodMotion}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class CrossModuleMethodMotionTest extends CompilerTestCase {
  private static final String EXTERNS =
      "IFoo.prototype.bar; var mExtern; mExtern.bExtern; mExtern['cExtern'];";

  private boolean canMoveExterns = false;

  private final String STUB_DECLARATIONS =
      CrossModuleMethodMotion.STUB_DECLARATIONS;

  public CrossModuleMethodMotionTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CrossModuleMethodMotion(
        compiler, new CrossModuleMethodMotion.IdGenerator(), canMoveExterns);
  }

  @Override
  public void setUp() {
    super.enableLineNumberCheck(true);
    canMoveExterns = false;
  }

  public void testMovePrototypeMethod1() {
    testSame(createModuleChain(
                 "function Foo() {}" +
                 "Foo.prototype.bar = function() {};",
                 // Module 2
                 "(new Foo).bar()"));

    canMoveExterns = true;
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.bar = function() {};",
             // Module 2
             "(new Foo).bar()"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.bar = JSCompiler_stubMethod(0);",
             // Module 2
             "Foo.prototype.bar = JSCompiler_unstubMethod(0, function() {});" +
             "(new Foo).bar()"
         });
  }

  public void testMovePrototypeMethod2() {
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype = { method: function() {} };",
             // Module 2
             "(new Foo).method()"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype = { method: JSCompiler_stubMethod(0) };",
             // Module 2
             "Foo.prototype.method = " +
             "    JSCompiler_unstubMethod(0, function() {});" +
             "(new Foo).method()"
         });
  }

  public void testMovePrototypeMethod3() {
    testSame(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype = { get method() {} };",
             // Module 2
             "(new Foo).method()"));
  }

  public void testMovePrototypeRecursiveMethod() {
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.baz = function() { this.baz(); };",
             // Module 2
             "(new Foo).baz()"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(0);",
             // Module 2
             "Foo.prototype.baz = JSCompiler_unstubMethod(0, " +
             "    function() { this.baz(); });" +
             "(new Foo).baz()"
         });
  }

  public void testCantMovePrototypeProp() {
    testSame(createModuleChain(
                 "function Foo() {}" +
                 "Foo.prototype.baz = goog.nullFunction;",
                 // Module 2
                 "(new Foo).baz()"));
  }

  public void testMoveMethodsInRightOrder() {
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.baz = function() { return 1; };" +
             "Foo.prototype.baz = function() { return 2; };",
             // Module 2
             "(new Foo).baz()"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(1);" +
             "Foo.prototype.baz = JSCompiler_stubMethod(0);",
             // Module 2
             "Foo.prototype.baz = " +
             "JSCompiler_unstubMethod(1, function() { return 1; });" +
             "Foo.prototype.baz = " +
             "JSCompiler_unstubMethod(0, function() { return 2; });" +
             "(new Foo).baz()"
         });
  }

  public void testMoveMethodsInRightOrder2() {
    JSModule[] m = createModules(
        "function Foo() {}" +
        "Foo.prototype.baz = function() { return 1; };" +
        "function Goo() {}" +
        "Goo.prototype.baz = function() { return 2; };",
        // Module 2, depends on 1
        "",
        // Module 3, depends on 2
        "(new Foo).baz()",
        // Module 4, depends on 3
        "",
        // Module 5, depends on 3
        "(new Goo).baz()");

    m[1].addDependency(m[0]);
    m[2].addDependency(m[1]);
    m[3].addDependency(m[2]);
    m[4].addDependency(m[2]);

    test(m,
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(1);" +
             "function Goo() {}" +
             "Goo.prototype.baz = JSCompiler_stubMethod(0);",
             // Module 2
             "",
             // Module 3
             "Foo.prototype.baz = " +
             "JSCompiler_unstubMethod(1, function() { return 1; });" +
             "Goo.prototype.baz = " +
             "JSCompiler_unstubMethod(0, function() { return 2; });" +
             "(new Foo).baz()",
             // Module 4
             "",
             // Module 5
             "(new Goo).baz()"
         });
  }

  public void testMoveMethodsUsedInTwoModules() {
    testSame(createModuleStar(
                 "function Foo() {}" +
                 "Foo.prototype.baz = function() {};",
                 // Module 2
                 "(new Foo).baz()",
                 // Module 3
                 "(new Foo).baz()"));
  }

  public void testMoveMethodsUsedInTwoModules2() {
    JSModule[] modules = createModules(
        "function Foo() {}" +
        "Foo.prototype.baz = function() {};",
        // Module 2
        "", // a blank module in the middle
        // Module 3
        "(new Foo).baz() + 1",
        // Module 4
        "(new Foo).baz() + 2");

    modules[1].addDependency(modules[0]);
    modules[2].addDependency(modules[1]);
    modules[3].addDependency(modules[1]);
    test(modules,
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(0);",
             // Module 2
             "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});",
             // Module 3
             "(new Foo).baz() + 1",
             // Module 4
             "(new Foo).baz() + 2"
         });
  }

  public void testTwoMethods() {
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.baz = function() {};",
             // Module 2
             "Foo.prototype.callBaz = function() { this.baz(); }",
             // Module 3
             "(new Foo).callBaz()"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(1);",
             // Module 2
             "Foo.prototype.callBaz = JSCompiler_stubMethod(0);",
             // Module 3
             "Foo.prototype.baz = JSCompiler_unstubMethod(1, function() {});" +
             "Foo.prototype.callBaz = " +
             "  JSCompiler_unstubMethod(0, function() { this.baz(); });" +
             "(new Foo).callBaz()"
         });
  }

  public void testTwoMethods2() {
    // if the programmer screws up the module order, we don't try to correct
    // the mistake.
    test(createModuleChain(
             "function Foo() {}" +
             "Foo.prototype.baz = function() {};",
             // Module 2
             "(new Foo).callBaz()",
             // Module 3
             "Foo.prototype.callBaz = function() { this.baz(); }"),
         new String[] {
             STUB_DECLARATIONS +
             "function Foo() {}" +
             "Foo.prototype.baz = JSCompiler_stubMethod(0);",
             // Module 2
             "(new Foo).callBaz()",
             // Module 3
             "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});" +
             "Foo.prototype.callBaz = function() { this.baz(); };"
         });
  }

  public void testGlobalFunctionsInGraph() {
    test(createModuleChain(
            "function Foo() {}" +
            "Foo.prototype.baz = function() {};" +
            "function x() { return (new Foo).baz(); }",
            // Module 2
            "x();"),
        new String[] {
          STUB_DECLARATIONS +
          "function Foo() {}" +
          "Foo.prototype.baz = JSCompiler_stubMethod(0);" +
          "function x() { return (new Foo).baz(); }",
          // Module 2
          "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});" +
          "x();"
        });
  }

  // Read of closure variable disables method motions.
  public void testClosureVariableReads1() {
    testSame(createModuleChain(
            "function Foo() {}" +
            "(function() {" +
            "var x = 'x';" +
            "Foo.prototype.baz = function() {x};" +
            "})();",
            // Module 2
            "var y = new Foo(); y.baz();"));
  }

  // Read of global variable is fine.
  public void testClosureVariableReads2() {
    test(createModuleChain(
            "function Foo() {}" +
            "Foo.prototype.b1 = function() {" +
            "  var x = 1;" +
            "  Foo.prototype.b2 = function() {" +
            "    Foo.prototype.b3 = function() {" +
            "      x;" +
            "    }" +
            "  }" +
            "};",
            // Module 2
            "var y = new Foo(); y.b1();",
            // Module 3
            "y = new Foo(); z.b2();",
            // Module 4
            "y = new Foo(); z.b3();"
            ),
         new String[] {
           STUB_DECLARATIONS +
           "function Foo() {}" +
           "Foo.prototype.b1 = JSCompiler_stubMethod(0);",
           // Module 2
           "Foo.prototype.b1 = JSCompiler_unstubMethod(0, function() {" +
           "  var x = 1;" +
           "  Foo.prototype.b2 = function() {" +
           "    Foo.prototype.b3 = function() {" +
           "      x;" +
           "    }" +
           "  }" +
           "});" +
           "var y = new Foo(); y.b1();",
           // Module 3
           "y = new Foo(); z.b2();",
           // Module 4
           "y = new Foo(); z.b3();"
        });
  }

  public void testClosureVariableReads3() {
    test(createModuleChain(
            "function Foo() {}" +
            "Foo.prototype.b1 = function() {" +
            "  Foo.prototype.b2 = function() {" +
            "    var x = 1;" +
            "    Foo.prototype.b3 = function() {" +
            "      x;" +
            "    }" +
            "  }" +
            "};",
            // Module 2
            "var y = new Foo(); y.b1();",
            // Module 3
            "y = new Foo(); z.b2();",
            // Module 4
            "y = new Foo(); z.b3();"
            ),
         new String[] {
           STUB_DECLARATIONS +
           "function Foo() {}" +
           "Foo.prototype.b1 = JSCompiler_stubMethod(0);",
           // Module 2
           "Foo.prototype.b1 = JSCompiler_unstubMethod(0, function() {" +
           "  Foo.prototype.b2 = JSCompiler_stubMethod(1);" +
           "});" +
           "var y = new Foo(); y.b1();",
           // Module 3
           "Foo.prototype.b2 = JSCompiler_unstubMethod(1, function() {" +
           "  var x = 1;" +
           "  Foo.prototype.b3 = function() {" +
           "    x;" +
           "  }" +
           "});" +
           "y = new Foo(); z.b2();",
           // Module 4
           "y = new Foo(); z.b3();"
        });
  }

  // Read of global variable is fine.
  public void testNoClosureVariableReads1() {
    test(createModuleChain(
            "function Foo() {}" +
            "var x = 'x';" +
            "Foo.prototype.baz = function(){x};",
            // Module 2
            "var y = new Foo(); y.baz();"),
         new String[] {
           STUB_DECLARATIONS +
           "function Foo() {}" +
           "var x = 'x';" +
           "Foo.prototype.baz = JSCompiler_stubMethod(0);",
           // Module 2
           "Foo.prototype.baz = JSCompiler_unstubMethod(0, function(){x});" +
           "var y = new Foo(); y.baz();"
        });
  }

  // Read of a local is fine.
  public void testNoClosureVariableReads2() {
    test(createModuleChain(
            "function Foo() {}" +
            "Foo.prototype.baz = function(){var x = 1;x};",
            // Module 2
            "var y = new Foo(); y.baz();"),
         new String[] {
           STUB_DECLARATIONS +
           "function Foo() {}" +
           "Foo.prototype.baz = JSCompiler_stubMethod(0);",
           // Module 2
           "Foo.prototype.baz = JSCompiler_unstubMethod(" +
           "    0, function(){var x = 1; x});" +
           "var y = new Foo(); y.baz();"
        });
  }

  // An anonymous inner function reading a closure variable is fine.
  public void testInnerFunctionClosureVariableReads() {
    test(createModuleChain(
            "function Foo() {}" +
            "Foo.prototype.baz = function(){var x = 1;" +
            "  return function(){x}};",
            // Module 2
            "var y = new Foo(); y.baz();"),
         new String[] {
           STUB_DECLARATIONS +
           "function Foo() {}" +
           "Foo.prototype.baz = JSCompiler_stubMethod(0);",
           // Module 2
           "Foo.prototype.baz = JSCompiler_unstubMethod(" +
           "    0, function(){var x = 1; return function(){x}});" +
           "var y = new Foo(); y.baz();"
        });
  }

  public void testIssue600() {
    testSame(
        createModuleChain(
            "var jQuery1 = (function() {\n" +
            "  var jQuery2 = function() {};\n" +
            "  var theLoneliestNumber = 1;\n" +
            "  jQuery2.prototype = {\n" +
            "    size: function() {\n" +
            "      return theLoneliestNumber;\n" +
            "    }\n" +
            "  };\n" +
            "  return jQuery2;\n" +
            "})();\n",

            "(function() {" +
            "  var div = jQuery1('div');" +
            "  div.size();" +
            "})();"));
  }

  public void testIssue600b() {
    testSame(
        createModuleChain(
            "var jQuery1 = (function() {\n" +
            "  var jQuery2 = function() {};\n" +
            "  jQuery2.prototype = {\n" +
            "    size: function() {\n" +
            "      return 1;\n" +
            "    }\n" +
            "  };\n" +
            "  return jQuery2;\n" +
            "})();\n",

            "(function() {" +
            "  var div = jQuery1('div');" +
            "  div.size();" +
            "})();"));
  }

  public void testIssue600c() {
    test(
        createModuleChain(
            "var jQuery2 = function() {};\n" +
            "jQuery2.prototype = {\n" +
            "  size: function() {\n" +
            "    return 1;\n" +
            "  }\n" +
            "};\n",

            "(function() {" +
            "  var div = jQuery2('div');" +
            "  div.size();" +
            "})();"),
        new String[] {
            STUB_DECLARATIONS +
            "var jQuery2 = function() {};\n" +
            "jQuery2.prototype = {\n" +
            "  size: JSCompiler_stubMethod(0)\n" +
            "};\n",
            "jQuery2.prototype.size=" +
            "    JSCompiler_unstubMethod(0,function(){return 1});" +
            "(function() {" +
            "  var div = jQuery2('div');" +
            "  div.size();" +
            "})();"
        });
  }

  public void testIssue600d() {
    test(
        createModuleChain(
            "var jQuery2 = function() {};\n" +
            "(function() {" +
            "  jQuery2.prototype = {\n" +
            "    size: function() {\n" +
            "      return 1;\n" +
            "    }\n" +
            "  };\n" +
            "})();",

            "(function() {" +
            "  var div = jQuery2('div');" +
            "  div.size();" +
            "})();"),
        new String[] {
            STUB_DECLARATIONS +
            "var jQuery2 = function() {};\n" +
            "(function() {" +
            "  jQuery2.prototype = {\n" +
            "    size: JSCompiler_stubMethod(0)\n" +
            "  };\n" +
            "})();",
            "jQuery2.prototype.size=" +
            "    JSCompiler_unstubMethod(0,function(){return 1});" +
            "(function() {" +
            "  var div = jQuery2('div');" +
            "  div.size();" +
            "})();"
        });
  }

  public void testIssue600e() {
    testSame(
        createModuleChain(
            "var jQuery2 = function() {};\n" +
            "(function() {" +
            "  var theLoneliestNumber = 1;\n" +
            "  jQuery2.prototype = {\n" +
            "    size: function() {\n" +
            "      return theLoneliestNumber;\n" +
            "    }\n" +
            "  };\n" +
            "})();",

            "(function() {" +
            "  var div = jQuery2('div');" +
            "  div.size();" +
            "})();"));
  }

  public void testPrototypeOfThisAssign() {
    testSame(
        createModuleChain(
            "/** @constructor */" +
            "function F() {}" +
            "this.prototype.foo = function() {};",
            "(new F()).foo();"));
  }
}
