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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link RemoveUnusedCode} that cover removal of prototype properties and class
 * properties.
 */
@RunWith(JUnit4.class)
public final class RemoveUnusedCodePrototypePropertiesTest extends CompilerTestCase {
  private static final String EXTERNS =
      lines(
          MINIMAL_EXTERNS,
          "var window;",
          "var Math = {};",
          "Math.random = function() {};",
          "function alert(x) {}",
          "function externFunction() {}",
          "externFunction.prototype.externPropName;",
          "var mExtern;",
          "mExtern.bExtern;",
          "mExtern['cExtern'];");

  private boolean keepLocals = true;
  private boolean keepGlobals = false;
  private boolean allowRemovalOfExternProperties = false;

  public RemoveUnusedCodePrototypePropertiesTest() {
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
        new RemoveUnusedCode.Builder(compiler)
            .removeLocalVars(!keepLocals)
            .removeGlobals(!keepGlobals)
            .removeUnusedPrototypeProperties(true)
            .allowRemovalOfExternProperties(allowRemovalOfExternProperties)
            .build()
            .process(externs, root);
      }
    };
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    enableNormalize();
    enableGatherExternProperties();
    keepLocals = true;
    keepGlobals = false;
    allowRemovalOfExternProperties = false;
  }

  @Test
  public void testClassPropertiesNotRemoved() {
    keepGlobals = true;
    // This whole test class runs with removeUnusedClassProperties disabled.
    testSame("/** @constructor */ function C() {} C.unused = 3;");
    testSame(
        "/** @constructor */ function C() {} Object.defineProperties(C, {unused: {value: 3}});");
  }

  @Test
  public void testUnusedPrototypeFieldReference() {
    test(
        "function C() {} C.prototype.x; new C();", // x is not actually read
        "function C() {}                new C();");
  }

  @Test
  public void testUnusedReferenceToFieldWithGetter() {
    // Reference to a field with a getter should not be removed unless we know it has no side
    // effects.
    // TODO(bradfordcsmith): Implement removal for the no-side-effect cases.
    testSame("function C() {} C.prototype = { get x() {} }; new C().x");
    testSame("function C() {} C.prototype = { get x() { alert('x'); } }; new C().x");
    testSame("class C { get x() {} } new C().x;");
    testSame("class C { get x() { alert('x'); } } new C().x");
    testSame("let c = { get x() {} }; c.x;");
    testSame("let c = { get x() { alert('x'); } }; c.x;");
  }

  @Test
  public void testAnonymousPrototypePropertyRemoved() {
    test("({}.prototype.x = 5, externFunction())", "externFunction();");
    test("({}).prototype.x = 5;", "");
    test("({}).prototype.x = externFunction();", "externFunction();");
    test("externFunction({}.prototype.x = 5);", "externFunction(5);");
    test("externFunction().prototype.x = 5;", "externFunction();");
    test("externFunction().prototype.x = externFunction();", "externFunction(), externFunction();");

    // make sure an expression with '.prototype' is traversed when it should be and not when it
    // shouldn't.
    test(
        "function C() {} externFunction(C).prototype.x = 5;", // preserve format
        "function C() {} externFunction(C);");
    test("function C() {} ({ C: C }).prototype.x = 5;", "");
  }

  @Test
  public void testAnonymousPrototypePropertyNoRemoveSideEffect1() {
    test(
        lines(
            "function A() {", // preserve format
            "  externFunction('me');",
            "  return function(){}",
            "}",
            "A().prototype.foo = function() {};"),
        lines(
            "function A() {", // preserve format
            "  externFunction('me');",
            "  return function(){}",
            "}",
            "A();"));
  }

  @Test
  public void testAnonymousPrototypePropertyNoRemoveSideEffect2() {
    test(
        "function A() { externFunction('me'); return function(){}; } A().prototype.foo++;",
        "function A() { externFunction('me'); return function(){}; } A();");
  }

  @Test
  public void testIncPrototype() {
    test("function A() {} A.prototype.x = 1; A.prototype.x++;", "");
    test(
        "function A() {} A.prototype.x = 1; A.prototype.x++; new A();",
        "function A() {}                                     new A();");
    test("externFunction().prototype.x++", "externFunction()");
  }

  @Test
  public void testRenamePropertyFunctionTest() {
    test(
        lines(
            "function C() {}", // preserve formatting
            "C.prototype.unreferenced = function() {};",
            "C.prototype.renamed = function() {};",
            "JSCompiler_renameProperty('renamed');",
            "new C();"),
        lines(
            "function C() {}", // preserve formatting
            "C.prototype.renamed = function() {};",
            "JSCompiler_renameProperty('renamed');",
            "new C();"));
  }

  @Test
  public void testNonPrototypePropertiesAreKept() {
    // foo cannot be removed because it is called
    // x cannot be removed because a property is set on it and we don't know where it comes from
    // x.a cannot be removed because we don't know where x comes from.
    // x.prototype.b *can* be removed because we consider it safe to remove prototype properties
    // that have no references.
    test(
        "function foo(x) { x.a = 1; x.prototype.b = 2; }; foo({});",
        "function foo(x) { x.a = 1; }; foo({});");
  }

  @Test
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

  @Test
  public void testObjectLiteralPrototype() {
    test(
        "function e(){} e.prototype = {a: function(){}, b: function(){}}; var x = new e; x.a()",
        "function e(){} e.prototype = {a: function(){}                 }; var x = new e; x.a()");
  }

  @Test
  public void testObjectLiteralPrototypeUnusedPropDefinitionWithSideEffects() {
    test(
        "function e(){} e.prototype = {a: alert('a'), b: function(){}}; new e;",
        "function e(){} e.prototype = {a: alert('a')                 }; new e;");
  }

  @Test
  public void testPropertiesDefinedInExterns() {
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
            "  bExtern() {}",  // property name defined in externs.
            "}",
            "new C();"));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testExportedMethodsByNamingConvention() {
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

  @Test
  public void testMethodsFromExternsFileNotExported() {
    allowRemovalOfExternProperties = true;

    test(
        lines(
            "function Foo() {}",
            "Foo.prototype.bar_ = function() {};",
            "Foo.prototype.unused = function() {};",
            "var instance = new Foo;",
            "Foo.prototype.externPropName = Foo.prototype.bar_"),
        "function Foo(){} new Foo;");
  }

  @Test
  public void testExportedMethodsByNamingConventionAlwaysExported() {
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

  @Test
  public void testExternMethodsFromExternsFile() {
    String classAndItsMethodAliasedAsExtern =
        "function Foo() {}" +
        "Foo.prototype.bar_ = function() {};" +  // not removed
        "Foo.prototype.unused = function() {};" +  // removed
        "var instance = new Foo;" +
        "Foo.prototype.externPropName = Foo.prototype.bar_";  // aliased here

    String compiled =
        lines(
            "function Foo(){}",
            "Foo.prototype.bar_ = function(){};",
            "new Foo;",
            "Foo.prototype.externPropName = Foo.prototype.bar_");

    test(classAndItsMethodAliasedAsExtern, compiled);
  }

  @Test
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

  @Test
  public void testPropertiesDefinedWithGetElem() {
    testSame("function Foo() {} Foo.prototype['elem'] = function() {}; new Foo;");
    testSame("function Foo() {} Foo.prototype[1 + 1] = function() {}; new Foo;");
  }

  @Test
  public void testQuotedProperties() {
    // Basic removal for prototype replacement
    testSame("function e(){} e.prototype = {'a': function(){}, 'b': function(){}}; new e;");
  }

  @Test
  public void testNeverRemoveImplicitlyUsedProperties() {
    testSame(
        lines(
            "function Foo() {}",
            "Foo.prototype.length = 3;",
            "Foo.prototype.toString = function() { return 'Foo'; };",
            "Foo.prototype.valueOf = function() { return 'Foo'; };",
            "new Foo;"));
  }

  @Test
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

  @Test
  public void testUsingAnonymousObjectsToDefeatRemoval() {
    test("function Foo() {} Foo.prototype.baz = 3; new Foo;", "function Foo() {} new Foo;");
    testSame("function Foo() {} Foo.prototype.baz = 3; new Foo; var x = {}; x.baz;");
    testSame("function Foo() {} Foo.prototype.baz = 3; new Foo; var x = {baz: 5}; x;");
    // quoted properties still prevent removal
    testSame("function Foo() {} Foo.prototype.baz = 3; new Foo; var x = {'baz': 5}; x;");
  }

  @Test
  public void testGlobalFunctionsInGraph() {
    test(
        "var x = function() { (new Foo).baz(); };" +
        "var y = function() { x(); };" +
        "function Foo() {}" +
        "Foo.prototype.baz = function() { y(); };",
        "");
  }

  @Test
  public void testGlobalFunctionsInGraph2() {
    test(
        lines(
            "var x = function() { (new Foo).baz(); };",
            "var y = function() { x(); };",
            "function Foo() { this.baz(); }",
            "Foo.prototype.baz = function() { y(); };"),
        "");
  }

  @Test
  public void testGlobalFunctionsInGraph3() {
    test(
        lines(
            "var x = function() { (new Foo).baz(); };",
            "var y = function() { x(); };",
            "function Foo() { this.baz(); }",
            "Foo.prototype.baz = function() { x(); };"),
        "");
  }

  @Test
  public void testGlobalFunctionsInGraph4() {
    test(
        "var x = function() { (new Foo).baz(); };" +
        "var y = function() { x(); };" +
        "function Foo() { Foo.prototype.baz = function() { y(); }; }",
        "");
  }

  @Test
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

  @Test
  public void testGlobalFunctionsInGraph6() {
    testSame(
        "function Foo() {}" +
        "Foo.prototype.methodA = function() {};" +
        "function x() { (new Foo).methodA(); }" +
        "Foo.prototype.methodB = function() { x(); };" +
        "(new Foo).methodB();");
  }

  @Test
  public void testGlobalFunctionsInGraph7() {
    keepGlobals = true;
    testSame("function Foo() {} Foo.prototype.methodA = function() {}; this.methodA();");
  }

  @Test
  public void testGlobalFunctionsInGraph8() {
    test(
        lines(
            "let x = function() { (new Foo).baz(); };",
            "const y = function() { x(); };",
            "function Foo() { Foo.prototype.baz = function() { y(); }; }"),
    "");
  }

  @Test
  public void testGetterBaseline() {
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

  @Test
  public void testGetter1() {
    test(
        lines(
            "function Foo() {}",
            "Foo.prototype = { ",
            "  get methodA() {},",
            "  get methodB() { x(); }",
            "};",
            "function x() { (new Foo).methodA; }",
            "new Foo();"),
        lines(
            "function Foo() {}",
            // x() and all methods of Foo removed.
            "Foo.prototype = {};",
            "new Foo();"));

    keepGlobals = true;
    test(
        lines(
            "function Foo() {}",
            "Foo.prototype = { ",
            "  get methodA() {},",
            "  get methodB() { x(); }",
            "};",
            "function x() { (new Foo).methodA; }"),
        lines(
            "function Foo() {}",
            "Foo.prototype = { ",
            "  get methodA() {}",
            "};",
            // x() keeps methodA alive
            "function x() { (new Foo).methodA; }"));
  }

  @Test
  public void testGetter2() {
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

  @Test
  public void testHook1() {
    test(
        lines(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.method1 =",
            "    Math.random()",
            "        ? function() { this.method2(); }",
            "        : function() { this.method3(); };",
            "Foo.prototype.method2 = function() {};",
            "Foo.prototype.method3 = function() {};"),
        "");
  }

  @Test
  public void testHook2() {
    testSame(
        lines(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.method1 =",
            "    Math.random()",
            "        ? function() { this.method2(); }",
            "        : function() { this.method3(); };",
            "Foo.prototype.method2 = function() {};",
            "Foo.prototype.method3 = function() {};",
            "(new Foo()).method1();"));
  }

  @Test
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
            "function Foo() {}", // preserve newlines
            "Foo.prototype.a = function() {};",
            "let x;",
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

  @Test
  public void testEs6Class() {
    testSame(
        lines(
            "class C {",
            "  constructor() {",  // constructor is not removable
            "    this.x = 1;",
            "  }",
            "}",
            "new C();"));

    test(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  foo() {}",
            "}",
            "var c = new C "),
        lines(
            "class C {",
            "  constructor() {",  // constructor is not removable
            "    this.x = 1;",
            "  }",
            "}",
            "new C();"));

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

    test(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  static foo() {}",
            "}",
            "new C;"),
        lines(
            "class C {",
            "  constructor() {",  // constructor is not removable
            "    this.x = 1;",
            "  }",
            "  static foo() {}",  // static method removal is disabled
            "}",
            "new C();"));

    test(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  get foo() {}",
            "  set foo(val) {}",
            "}",
            "var c = new C "),
        "class C { constructor() { this.x = 1; } } new C");

    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  get foo() {}",
            "  set foo(val) {}",
            "}",
            "var c = new C;",
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
            "var c = new C;",
            "c.foo;"));
  }

  @Test
  public void testEs6Extends() {
    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "}",
            "class D extends C {",
            "  constructor() {}",
            "}",
            "new D();"));

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
            "var d = new D;"),
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "}",
            "class D extends C {",
            "  constructor() {}",
            "}",
            "new D;"));
  }

  @Test
  public void testAnonClasses() {
    // Make sure class expression names are removed.
    keepLocals = false;
    test(
        lines(
            "var C = class InnerC {",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "  foo() {}",
            "};",
            "new C;"),
        "var C = class { constructor() { this.x = 1; } }; new C;");

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
            "}",
            "new C.D();"),
        lines(
            "var C = class {}",
            "C.D = class{",
            "  constructor() {",
            "    this.x = 1;",
            "  }",
            "}",
            "new C.D();"));

    test(
        "externFunction(class C { constructor() { } externPropName() { } })",
        "externFunction(class   { constructor() { } externPropName() { } })");
  }

  @Test
  public void testBaseClassExpressionHasSideEffects() {
    // Make sure names are removed from class expressions.
    keepLocals = false;
    testSame(
        lines(
            "function getBaseClass() { return class {}; }",
            "class C extends getBaseClass() {}"));
    test(
        lines(
            "function getBaseClass() { return class {}; }",
            "const C = class InnerC extends getBaseClass() {};"),
        lines(
            "function getBaseClass() { return class {}; }",
            "(class extends getBaseClass() {})"));
    test(
        lines(
            "function getBaseClass() { return class {}; }",
            "let C;",
            "C = class InnerC extends getBaseClass() {}"),
        lines(
            "function getBaseClass() { return class {}; }",
            "(class extends getBaseClass() {})"));
    test(
        lines(
            "function getBaseClass() { return class {}; }",
            "externFunction(class InnerC extends getBaseClass() {})"),
        lines(
            "function getBaseClass() { return class {}; }",
            "externFunction(class extends getBaseClass() {})"));
  }

  @Test
  public void testModules() {
    testSame("export default function(){}");
    testSame("export class C {};");
    testSame("class Bar {} export {Bar}");

    testSame("import { square, diag } from 'lib';");
    testSame("import * as lib from 'lib';");
  }
}
