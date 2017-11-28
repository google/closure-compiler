/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;

/**
 * Tests for {@link RemoveUnusedVars} that cover functionality originally in
 * {@link RemoveUnusedPrototypeProperties}.
 */
public final class RemoveUnusedVarsPrototypePropertiesTest extends CompilerTestCase {
  private static final String EXTERNS =
      MINIMAL_EXTERNS + "IFoo.prototype.bar; var mExtern; mExtern.bExtern; mExtern['cExtern'];";

  private boolean keepGlobals = false;

  public RemoveUnusedVarsPrototypePropertiesTest() {
    super(EXTERNS);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;  // should reach fixed point in a single run
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        new RemoveUnusedVars(
                compiler,
                !keepGlobals,
                /* preserveFunctionExpressionNames */ false,
                /* removeUnusedProperties */ true)
            .process(externs, root);
      }
    };
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    enableNormalize();
    enableGatherExternProperties();
    keepGlobals = false;
  }

  public void testAnalyzePrototypeProperties() {
    // Basic removal for prototype properties
    test("function e(){}" +
           "e.prototype.a = function(){};" +
           "e.prototype.b = function(){};" +
           "var x = new e; x.a()",
         "function e(){}" +
           "e.prototype.a = function(){};" +
           "var x = new e; x.a()");
  }

  public void disabledTestObjectLiteralPrototype() {
    // TODO(bradfordcsmith): handle properties in object literal assigned to prototype
    test("function e(){}" +
            "e.prototype = {a: function(){}, b: function(){}};" +
            "var x=new e; x.a()",
        "function e(){}" +
            "e.prototype = {a: function(){}};" +
            "var x = new e; x.a()");
  }

  public void disabledTestPropertiesDefinedInExterns() {
    // TODO(bradfordcsmith): handle properties defined in externs
    // Unused properties that were referenced in the externs file should not be
    // removed
    test("function e(){}" +
            "e.prototype.a = function(){};" +
            "e.prototype.bExtern = function(){};" +
            "var x = new e;x.a()",
        "function e(){}" +
            "e.prototype.a = function(){};" +
            "e.prototype.bExtern = function(){};" +
            "var x = new e; x.a()");
    testSame("function e(){}"
        + "e.prototype = {a: function(){}, bExtern: function(){}};"
        + "var x = new e; x.a()");

    testSame(
        lines(
            "class C {",
            "  constructor() {}",
            "  bExtern() {}",
            "}"));
  }

  public void testAliasing1() {
    // Aliasing a property is not enough for it to count as used
    test("function e(){}" +
           "e.prototype.method1 = function(){};" +
           "e.prototype.method2 = function(){};" +
           // aliases
           "e.prototype.alias1 = e.prototype.method1;" +
           "e.prototype.alias2 = e.prototype.method2;" +
           "var x = new e; x.method1()",
         "function e(){}" +
           "e.prototype.method1 = function(){};" +
           "var x = new e; x.method1()");

    // Using an alias should keep it
    test("function e(){}" +
           "e.prototype.method1 = function(){};" +
           "e.prototype.method2 = function(){};" +
           // aliases
           "e.prototype.alias1 = e.prototype.method1;" +
           "e.prototype.alias2 = e.prototype.method2;" +
           "var x=new e; x.alias1()",
         "function e(){}" +
           "e.prototype.method1 = function(){};" +
           "e.prototype.alias1 = e.prototype.method1;" +
           "var x = new e; x.alias1()");
  }

  public void testAliasing2() {
    // Aliasing a property is not enough for it to count as used
    test("function e(){}" +
           "e.prototype.method1 = function(){};" +
           // aliases
           "e.prototype.alias1 = e.prototype.method1;" +
           "(new e).method1()",
         "function e(){}" +
           "e.prototype.method1 = function(){};" +
           "(new e).method1()");

    // Using an alias should keep it
    testSame(
        "function e(){}"
            + "e.prototype.method1 = function(){};"
            // aliases
            + "e.prototype.alias1 = e.prototype.method1;"
            + "(new e).alias1()");
  }

  public void testAliasing3() {
    // Aliasing a property is not enough for it to count as used
    testSame(
        lines(
            "function e(){}",
            "e.prototype.method1 = function(){};",
            "e.prototype.method2 = function(){};",
            // aliases
            "e.prototype['alias1'] = e.prototype.method1;",
            "e.prototype['alias2'] = e.prototype.method2;",
            "new e;"));
  }

  public void testAliasing4() {
    // Aliasing a property is not enough for it to count as used
    test(
        lines(
            "function e(){}",
            "e.prototype['alias1'] = e.prototype.method1 = function(){};",
            "e.prototype['alias2'] = e.prototype.method2 = function(){};",
            "new e;"),
        lines(
            "function e(){}",
            "e.prototype['alias1'] = function(){};",
            "e.prototype['alias2'] = function(){};",
            "new e;"));
  }

  public void testAliasing5() {
    // An exported alias must preserved any referenced values in the
    // referenced function.
    testSame(
        lines(
            "function e(){}",
            "e.prototype.method1 = function(){this.method2()};",
            "e.prototype.method2 = function(){};",
            // aliases
            "e.prototype['alias1'] = e.prototype.method1;",
            "new e;"));
  }

  public void testAliasing6() {
    // An exported alias must preserved any referenced values in the
    // referenced function.
    test("function e(){}" +
           "e.prototype.method1 = function(){this.method2()};" +
           "e.prototype.method2 = function(){};" +
           // aliases
           "window['alias1'] = e.prototype.method1;",
         "function e(){}" +
           "e.prototype.method1=function(){this.method2()};" +
           "e.prototype.method2=function(){};" +
           "window['alias1']=e.prototype.method1;");
  }

  public void testAliasing7() {
    // An exported alias must preserved any referenced values in the
    // referenced function.
    test(
        lines(
            "function e(){}",
            "e.prototype['alias1'] = e.prototype.method1 = function(){this.method2()};",
            "e.prototype.method2 = function(){};",
            "new e;"),
        lines(
            "function e(){}",
            "e.prototype['alias1'] = function(){this.method2()};",
            "e.prototype.method2 = function(){};",
            "new e;"));
  }

  public void disabledTestExportedMethodsByNamingConvention() {
    // TODO(bradfordcsmith): Implement this
    String classAndItsMethodAliasedAsExtern =
        "function Foo() {}" +
        "Foo.prototype.method = function() {};" +  // not removed
        "Foo.prototype.unused = function() {};" +  // removed
        "var _externInstance = new Foo();" +
        "Foo.prototype._externMethod = Foo.prototype.method";  // aliased here

    String compiled =
        "function Foo(){}" +
        "Foo.prototype.method = function(){};" +
        "var _externInstance = new Foo;" +
        "Foo.prototype._externMethod = Foo.prototype.method";

    test(classAndItsMethodAliasedAsExtern, compiled);
  }

  public void disbledTestMethodsFromExternsFileNotExported() {
    // TODO(bradfordcsmith): implement this
    String classAndItsMethodAliasedAsExtern =
        "function Foo() {}" +
        "Foo.prototype.bar_ = function() {};" +
        "Foo.prototype.unused = function() {};" +
        "var instance = new Foo;" +
        "Foo.prototype.bar = Foo.prototype.bar_";

    String compiled =
        "function Foo(){}" +
        "var instance = new Foo;";

    test(classAndItsMethodAliasedAsExtern, compiled);
  }

  public void disabledTestExportedMethodsByNamingConventionAlwaysExported() {
    // TODO(bradfordcsmith): implement this
    String classAndItsMethodAliasedAsExtern =
        "function Foo() {}" +
        "Foo.prototype.method = function() {};" +  // not removed
        "Foo.prototype.unused = function() {};" +  // removed
        "var _externInstance = new Foo();" +
        "Foo.prototype._externMethod = Foo.prototype.method";  // aliased here

    String compiled =
        "function Foo(){}" +
        "Foo.prototype.method = function(){};" +
        "var _externInstance = new Foo;" +
        "Foo.prototype._externMethod = Foo.prototype.method";

    test(classAndItsMethodAliasedAsExtern, compiled);
  }

  public void disabledTestExternMethodsFromExternsFile() {
    // TODO(bradfordcsmith): implement this
    String classAndItsMethodAliasedAsExtern =
        "function Foo() {}" +
        "Foo.prototype.bar_ = function() {};" +  // not removed
        "Foo.prototype.unused = function() {};" +  // removed
        "var instance = new Foo;" +
        "Foo.prototype.bar = Foo.prototype.bar_";  // aliased here

    String compiled =
        "function Foo(){}" +
        "Foo.prototype.bar_ = function(){};" +
        "var instance = new Foo;" +
        "Foo.prototype.bar = Foo.prototype.bar_";

    test(classAndItsMethodAliasedAsExtern, compiled);
  }

  public void testPropertyReferenceGraph() {
    // test a prototype property graph that looks like so:
    // b -> a, c -> b, c -> a, d -> c, e -> a, e -> f
    String constructor = "function Foo() {}";
    String defA =
        "Foo.prototype.a = function() { Foo.superClass_.a.call(this); };";
    String defB = "Foo.prototype.b = function() { this.a(); };";
    String defC = "Foo.prototype.c = function() { " +
        "Foo.superClass_.c.call(this); this.b(); this.a(); };";
    String defD = "Foo.prototype.d = function() { this.c(); };";
    String defE = "Foo.prototype.e = function() { this.a(); this.f(); };";
    String defF = "Foo.prototype.f = function() { };";
    String fullClassDef = constructor + defA + defB + defC + defD + defE + defF;

    // ensure that all prototypes are compiled out if none are used
    test(fullClassDef, "");

    // make sure that the right prototypes are called for each use
    String callA = "(new Foo()).a();";
    String callB = "(new Foo()).b();";
    String callC = "(new Foo()).c();";
    String callD = "(new Foo()).d();";
    String callE = "(new Foo()).e();";
    String callF = "(new Foo()).f();";
    test(fullClassDef + callA, constructor + defA + callA);
    test(fullClassDef + callB, constructor + defA + defB + callB);
    test(fullClassDef + callC, constructor + defA + defB + defC + callC);
    test(fullClassDef + callD, constructor + defA + defB + defC + defD + callD);
    test(fullClassDef + callE, constructor + defA + defE + defF + callE);
    test(fullClassDef + callF, constructor + defF + callF);

    test(fullClassDef + callA + callC,
         constructor + defA + defB + defC + callA + callC);
    test(fullClassDef + callB + callC,
         constructor + defA + defB + defC + callB + callC);
    test(fullClassDef + callA + callB + callC,
         constructor + defA + defB + defC + callA + callB + callC);
  }

  public void testPropertiesDefinedWithGetElem() {
    testSame("function Foo() {} Foo.prototype['elem'] = function() {}; new Foo;");
    testSame("function Foo() {} Foo.prototype[1 + 1] = function() {}; new Foo;");
  }

  public void testQuotedProperties() {
    // Basic removal for prototype replacement
    testSame("function e(){} e.prototype = {'a': function(){}, 'b': function(){}}; new e;");
  }

  public void testNeverRemoveImplicitlyUsedProperties() {
    testSame(
        lines(
            "function Foo() {}",
            "Foo.prototype.length = 3;",
            "Foo.prototype.toString = function() { return 'Foo'; };",
            "Foo.prototype.valueOf = function() { return 'Foo'; };",
            "new Foo;"));
  }

  public void testPropertyDefinedInBranch() {
    test("function Foo() {} if (true) Foo.prototype.baz = function() {};",
         "if (true);");
    test("function Foo() {} while (true) Foo.prototype.baz = function() {};",
         "while (true);");
    test("function Foo() {} for (;;) Foo.prototype.baz = function() {};",
         "for (;;);");
    test("function Foo() {} do Foo.prototype.baz = function() {}; while(true);",
         "do; while(true);");
  }

  public void testUsingAnonymousObjectsToDefeatRemoval() {
    test("function Foo() {} Foo.prototype.baz = 3; new Foo;", "function Foo() {} new Foo;");
    testSame("function Foo() {} Foo.prototype.baz = 3; new Foo; var x = {}; x.baz;");
    testSame("function Foo() {} Foo.prototype.baz = 3; new Foo; var x = {baz: 5}; x;");
    test(
        "function Foo() {} Foo.prototype.baz = 3; new Foo; var x = {'baz': 5}; x;",
        "function Foo() {} new Foo; var x = {'baz': 5}; x;");
  }

  public void testGlobalFunctionsInGraph() {
    test(
        "var x = function() { (new Foo).baz(); };" +
        "var y = function() { x(); };" +
        "function Foo() {}" +
        "Foo.prototype.baz = function() { y(); };",
        "");
  }

  public void testGlobalFunctionsInGraph2() {
    test(
        lines(
            "var x = function() { (new Foo).baz(); };",
            "var y = function() { x(); };",
            "function Foo() { this.baz(); }",
            "Foo.prototype.baz = function() { y(); };"),
        "");
  }

  public void testGlobalFunctionsInGraph3() {
    test(
        lines(
            "var x = function() { (new Foo).baz(); };",
            "var y = function() { x(); };",
            "function Foo() { this.baz(); }",
            "Foo.prototype.baz = function() { x(); };"),
        "");
  }

  public void testGlobalFunctionsInGraph4() {
    test(
        "var x = function() { (new Foo).baz(); };" +
        "var y = function() { x(); };" +
        "function Foo() { Foo.prototype.baz = function() { y(); }; }",
        "");
  }

  public void testGlobalFunctionsInGraph5() {
    test(
        "function Foo() {}" +
        "Foo.prototype.methodA = function() {};" +
        "function x() { (new Foo).methodA(); }" +
        "Foo.prototype.methodB = function() { x(); };",
        "");

    keepGlobals = true;
    test(
        "function Foo() {}" +
        "Foo.prototype.methodA = function() {};" +
        "function x() { (new Foo).methodA(); }" +
        "Foo.prototype.methodB = function() { x(); };",

        "function Foo() {}" +
        "Foo.prototype.methodA = function() {};" +
        "function x() { (new Foo).methodA(); }");
  }

  public void testGlobalFunctionsInGraph6() {
    testSame(
        "function Foo() {}" +
        "Foo.prototype.methodA = function() {};" +
        "function x() { (new Foo).methodA(); }" +
        "Foo.prototype.methodB = function() { x(); };" +
        "(new Foo).methodB();");
  }

  public void testGlobalFunctionsInGraph7() {
    keepGlobals = true;
    testSame("function Foo() {} Foo.prototype.methodA = function() {}; this.methodA();");
  }

  public void testGlobalFunctionsInGraph8() {
    test(
        lines(
            "let x = function() { (new Foo).baz(); };",
            "const y = function() { x(); };",
            "function Foo() { Foo.prototype.baz = function() { y(); }; }"),
    "");
  }

  public void disabledTestGetterBaseline() {
    // TODO(bradfordcsmith): remove unused getters
    keepGlobals = true;
    test(
        "function Foo() {}" +
        "Foo.prototype = { " +
        "  methodA: function() {}," +
        "  methodB: function() { x(); }" +
        "};" +
        "function x() { (new Foo).methodA(); }",

        "function Foo() {}" +
        "Foo.prototype = { " +
        "  methodA: function() {}" +
        "};" +
        "function x() { (new Foo).methodA(); }");
  }

  public void disabledTestGetter1() {
    // TODO(bradfordcsmith): implement getters and setters
    test(
      "function Foo() {}" +
      "Foo.prototype = { " +
      "  get methodA() {}," +
      "  get methodB() { x(); }" +
      "};" +
      "function x() { (new Foo).methodA; }",

      "function Foo() {}" +
      "Foo.prototype = {};");

    keepGlobals = true;
    test(
        "function Foo() {}" +
        "Foo.prototype = { " +
        "  get methodA() {}," +
        "  get methodB() { x(); }" +
        "};" +
        "function x() { (new Foo).methodA; }",

        "function Foo() {}" +
        "Foo.prototype = { " +
        "  get methodA() {}" +
        "};" +
        "function x() { (new Foo).methodA; }");
  }

  public void disabledTestGetter2() {
    // TODO(bradfordcsmith): remove unused getters
    keepGlobals = true;
    test(
        "function Foo() {}" +
        "Foo.prototype = { " +
        "  get methodA() {}," +
        "  set methodA(a) {}," +
        "  get methodB() { x(); }," +
        "  set methodB(a) { x(); }" +
        "};" +
        "function x() { (new Foo).methodA; }",

        "function Foo() {}" +
        "Foo.prototype = { " +
        "  get methodA() {}," +
        "  set methodA(a) {}" +
        "};" +
        "function x() { (new Foo).methodA; }");
  }

  public void testHook1() throws Exception {
    test(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.method1 = Math.random() ?" +
        "   function() { this.method2(); } : function() { this.method3(); };" +
        "Foo.prototype.method2 = function() {};" +
        "Foo.prototype.method3 = function() {};",
        "");
  }

  public void testHook2() throws Exception {
    testSame(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.method1 = Math.random() ?" +
        "   function() { this.method2(); } : function() { this.method3(); };" +
        "Foo.prototype.method2 = function() {};" +
        "Foo.prototype.method3 = function() {};" +
        "(new Foo()).method1();");
  }

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

  public void testDestructuringProperty() {
    // Makes the cases below shorter because we don't have to add references
    // to globals to keep them around and just test prototype property removal.
    keepGlobals = true;
    test(
        "function Foo() {} Foo.prototype.a = function() {}; var {} = new Foo();",
        "function Foo() {} var {} = new Foo();");

    test(
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "Foo.prototype.b = function() {}",
            "var {a} = new Foo();"),
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "var {a} = new Foo();"));

    test(
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "Foo.prototype.b = function() {}",
            "var {a:x} = new Foo();"),
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "var {a:x} = new Foo();"));

    testSame(
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "Foo.prototype.b = function() {}",
            "var {a, b} = new Foo();"));

    testSame(
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "Foo.prototype.b = function() {}",
            "var {a:x, b:y} = new Foo();"));

    testSame(
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "({a:x} = new Foo());"));

    testSame(
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "function f({a:x}) { x; }; f(new Foo());"));

    testSame(
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "var {a : x = 3} = new Foo();"));

    test(
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "Foo.prototype.b = function() {}",
            "var {a : a = 3} = new Foo();"),
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "var {a : a = 3} = new Foo();"));

    testSame(
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "let { a : [b, c, d] } = new Foo();"));

    testSame(
        lines(
            "function Foo() {}",
            "Foo.prototype.a = function() {};",
            "const { a : { b : { c : d = '' }}} = new Foo();"));

  }

  public void disabledTestEs6Class() {
    // TODO(bradfordcsmith): Implement removal of ES6 class methods
    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "}"));

    test(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  foo() {}",
            "}",
            "var c = new C "
        ),
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "}",
            "var c = new C"));

    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  foo() {}",
            "}",
            "var c = new C ",
            "c.foo()"
        ));

    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  static foo() {}",
            "}"
        ));

    test(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  get foo() {}",
            "  set foo(val) {}",
            "}",
            "var c = new C "
        ),
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "}",
            "var c = new C"));

    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  get foo() {}",
            "  set foo(val) {}",
            "}",
            "var c = new C ",
            "c.foo = 3;"));

    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  get foo() {}",
            "  set foo(val) {}",
            "}",
            "var c = new C ",
            "c.foo;"));
  }

  public void disabledTestEs6Extends() {
    // TODO(bradfordcsmith): Implement removal of ES6 class methods.
    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "}",
            "class D extends C {",
            "  constructor() {}",
            "}"));

    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  foo() {}",
            "}",
            "class D extends C {",
            "  constructor() {}",
            "  foo() {",
            "     return super.foo()",
            "  }",
            "}",
            "var d = new D",
            "d.foo()"));

    test(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  foo() {}",
            "}",
            "class D extends C {",
            "  constructor() {}",
            "  foo() {",
            "     return super.foo()",
            "  }",
            "}",
            "var d = new D"),
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "}",
            "class D extends C {",
            "  constructor() {}",
            "}",
            "var d = new D"));
  }

  public void disabledTestAnonClasses() {
    // TODO(bradfordcsmith): Handle ES6 class methods
    test(
        lines(
            "var C = class {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  foo() {}",
            "}",
            "new C;"),
        lines(
            "var C = class {", "  constructor() {", "    this.x = 1;", "  }", "}", "new C;"));

    testSame(
        lines(
            "var C = class {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  foo() {}",
            "}",
            "var c = new C()",
            "c.foo()"));

    test(
        lines(
            "var C = class {}",
            "C.D = class {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  foo() {}",
            "}"),
        lines(
            "var C = class {}",
            "C.D = class{",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "}"));

    testSame(
        lines(
            "foo(class {",
            "  constructor() { }",
            "  bar() { }",
            "})"));
  }

  public void testModules() {
    testSame("export default function(){}");
    testSame("export class C {};");
    testSame("export {Bar}");

    testSame("import { square, diag } from 'lib';");
    testSame("import * as lib from 'lib';");
  }
}
