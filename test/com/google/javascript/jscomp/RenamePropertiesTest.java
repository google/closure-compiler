/*
 * Copyright 2005 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.jspecify.nullness.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link RenameProperties} tests. */
@RunWith(JUnit4.class)
public final class RenamePropertiesTest extends CompilerTestCase {

  private static final String EXTERNS =
      "var window;" + "prop.toString;" + "var google = { gears: { factory: {}, workerPool: {} } };";

  private RenameProperties renameProperties;
  private boolean generatePseudoNames;
  private @Nullable VariableMap prevUsedPropertyMap;

  public RenamePropertiesTest() {
    super(EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    generatePseudoNames = false;
    prevUsedPropertyMap = null;
    enableNormalize();
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    enableGatherExternProperties();
  }

  @Override
  protected int getNumRepetitions() {
    // The RenameProperties pass should only be run once over a parse tree.
    return 1;
  }

  @Test
  public void testPrototypeProperties() {
    test(
        "Bar.prototype.getA = function(){}; bar.getA();"
            + "Bar.prototype.getB = function(){};"
            + "Bar.prototype.getC = function(){};",
        "Bar.prototype.a = function(){}; bar.a();"
            + "Bar.prototype.b = function(){};"
            + "Bar.prototype.c = function(){}");
  }

  @Test
  public void testPrototypeProperties_optChain() {
    test(
        lines(
            "Bar.prototype.getA = function(){}; bar?.getA();",
            "Bar.prototype.getB = function(){};",
            "Bar.prototype.getC = function(){};"),
        lines(
            "Bar.prototype.a = function(){}; bar?.a();",
            "Bar.prototype.b = function(){};",
            "Bar.prototype.c = function(){}"));
  }

  @Test
  public void testPrototypePropertiesAsObjLitKeys1() {
    test(
        "Bar.prototype = {2: function(){}, getA: function(){}}; bar[2]();",
        "Bar.prototype = {2: function(){}, a: function(){}}; bar[2]();");
  }

  @Test
  public void testPrototypePropertiesAsObjLitKeys2() {
    testSame("Bar.prototype = {get 2(){}}; bar[2];");

    testSame("Bar.prototype = {get 'a'(){}}; bar['a'];");

    test("Bar.prototype = {get getA(){}}; bar.getA;", "Bar.prototype = {get a(){}}; bar.a;");
  }

  @Test
  public void testPrototypePropertiesAsObjLitKeys3() {
    testSame("Bar.prototype = {set 2(x){}}; bar[2];");

    testSame("Bar.prototype = {set 'a'(x){}}; bar['a'];");

    test("Bar.prototype = {set getA(x){}}; bar.getA;", "Bar.prototype = {set a(x){}}; bar.a;");

    test("Bar.prototype = {set getA(x){}}; bar?.getA;", "Bar.prototype = {set a(x){}}; bar?.a;");
  }

  @Test
  public void testMixedQuotedAndUnquotedObjLitKeys1() {
    test(
        "Bar = {getA: function(){}, 'getB': function(){}}; bar.getA();",
        "Bar = {a: function(){}, 'getB': function(){}}; bar.a();");

    test(
        "Bar = {getA: function(){}, 'getB': function(){}}; bar?.getA();",
        "Bar = {a: function(){}, 'getB': function(){}}; bar?.a();");
  }

  @Test
  public void testMixedQuotedAndUnquotedObjLitKeys2() {
    test(
        "Bar = {getA: function(){}, 'getB': function(){}}; bar.getA();",
        "Bar = {a: function(){}, 'getB': function(){}}; bar.a();");
  }

  @Test
  public void testQuotedPrototypeProperty() {
    testSame("Bar.prototype['getA'] = function(){}; bar['getA']();");
    testSame("Bar.prototype['getA'] = function(){}; bar?.['getA']();");
  }

  @Test
  public void testOverlappingOriginalAndGeneratedNames() {
    test(
        "Bar.prototype = {b: function(){}, a: function(){}}; bar.b();",
        "Bar.prototype = {a: function(){}, b: function(){}}; bar.a();");
  }

  @Test
  public void testRenamePropertiesWithLeadingUnderscores() {
    test(
        "Bar.prototype = {_getA: function(){}, _b: 0}; bar._getA();",
        "Bar.prototype = {a: function(){}, b: 0}; bar.a();");

    test(
        "Bar.prototype = {_getA: function(){}, _b: 0}; bar?._getA();",
        "Bar.prototype = {a: function(){}, b: 0}; bar?.a();");
  }

  @Test
  public void testPropertyAddedToObject() {
    test("var foo = {}; foo.prop = '';", "var foo = {}; foo.a = '';");
  }

  @Test
  public void testPropertyAddedToFunction() {
    test("var foo = function(){}; foo.prop = '';", "var foo = function(){}; foo.a = '';");
  }

  @Test
  public void testPropertyOfObjectOfUnknownType() {
    test("var foo = x(); foo.prop = '';", "var foo = x(); foo.a = '';");
  }

  @Test
  public void testSetPropertyOfThis() {
    test("this.prop = 'bar'", "this.a = 'bar'");
  }

  @Test
  public void testReadPropertyOfThis() {
    test("f(this.prop);", "f(this.a);");

    test("f(this?.prop);", "f(this?.a);");
  }

  @Test
  public void testObjectLiteralInLocalScope() {
    test(
        "function x() { var foo = {prop1: 'bar', prop2: 'baz'}; }",
        "function x() { var foo = {a: 'bar', b: 'baz'}; }");
  }

  @Test
  public void testIncorrectAttemptToAccessQuotedProperty() {
    // The correct way to call the quoted 'getFoo' method is: bar['getFoo']().
    test(
        "Bar.prototype = {'B': 0, 'getFoo': function(){}}; bar.getFoo();",
        "Bar.prototype = {'B': 0, 'getFoo': function(){}}; bar.a();");
  }

  @Test
  public void testSetQuotedPropertyOfThis() {
    testSame("this['prop'] = 'bar';");
  }

  @Test
  public void testExternedPropertyName() {
    test(
        "Bar.prototype = {toString: function(){}, foo: 0}; bar.toString();",
        "Bar.prototype = {toString: function(){}, a: 0}; bar.toString();");
  }

  @Test
  public void testExternedPropertyNameDefinedByObjectLiteral() {
    testSame("function x() { var foo = google.gears.factory; }");
  }

  @Test
  public void testAvoidingConflictsBetweenQuotedAndUnquotedPropertyNames() {
    test(
        "Bar.prototype.foo = function(){}; Bar.prototype['a'] = 0; bar.foo();",
        "Bar.prototype.b = function(){}; Bar.prototype['a'] = 0; bar.b();");
  }

  @Test
  public void testSamePropertyNameQuotedAndUnquoted() {
    test(
        "Bar.prototype.prop = function(){}; y = {'prop': 0};",
        "Bar.prototype.a = function(){}; y = {'prop': 0};");
  }

  @Test
  public void testStaticAndInstanceMethodWithSameName() {
    test(
        "Bar = function(){}; Bar.getA = function(){}; "
            + "Bar.prototype.getA = function(){}; Bar.getA(); bar.getA();",
        "Bar = function(){}; Bar.a = function(){}; "
            + "Bar.prototype.a = function(){}; Bar.a(); bar.a();");
  }

  @Test
  public void testRenamePropertiesFunctionCall1() {
    test(
        "var foo = {myProp: 0}; f(foo[JSCompiler_renameProperty('myProp')]);",
        "var foo = {a: 0}; f(foo['a']);");
    // JSCompiler does not handle optional calls to `JSCompiler_renameProperty`. The
    // OPTCHAIN_GETELEM is handled consistently with regular GETELEM.
    test(
        "var foo = {myProp: 0}; f(foo?.[JSCompiler_renameProperty('myProp')]);",
        "var foo = {a: 0}; f(foo?.['a']);");
  }

  @Test
  public void testRenamePropertiesFunctionCall2() {
    test(
        "var foo = {myProp: 0}; "
            + "f(JSCompiler_renameProperty('otherProp.myProp.someProp')); "
            + "foo.myProp = 1; foo.theirProp = 2; foo.yourProp = 3;",
        "var foo = {a: 0}; f('b.a.c'); " + "foo.a = 1; foo.d = 2; foo.e = 3;");
  }

  @Test
  public void testRemoveRenameFunctionStubs1() {
    test("function JSCompiler_renameProperty(x) { return x; }", "");
  }

  @Test
  public void testRemoveRenameFunctionStubs2() {
    test(
        "function JSCompiler_renameProperty(x) { return x; }"
            + "var foo = {myProp: 0}; f(foo[JSCompiler_renameProperty('myProp')]);",
        "var foo = {a: 0}; f(foo['a']);");
  }

  @Test
  public void testGeneratePseudoNames() {
    generatePseudoNames = true;
    test("var foo={}; foo.bar=1; foo['abc']=2", "var foo={}; foo.$bar$=1; foo['abc']=2");
  }

  @Test
  public void testModules() {
    String module1Js =
        "function Bar(){} Bar.prototype.getA=function(x){};"
            + "var foo;foo.getA(foo);foo.doo=foo;foo.bloo=foo;";

    String module2Js =
        "function Far(){} Far.prototype.getB=function(y){};"
            + "var too;too.getB(too);too.woo=too;too.bloo=too;";

    String module3Js =
        "function Car(){} Car.prototype.getC=function(z){};"
            + "var noo;noo.getC(noo);noo.zoo=noo;noo.cloo=noo;";

    JSChunk module1 = new JSChunk("m1");
    module1.add(SourceFile.fromCode("input1", module1Js));

    JSChunk module2 = new JSChunk("m2");
    module2.add(SourceFile.fromCode("input2", module2Js));

    JSChunk module3 = new JSChunk("m3");
    module3.add(SourceFile.fromCode("input3", module3Js));

    JSChunk[] modules = new JSChunk[] {module1, module2, module3};
    Compiler compiler = compileModules("", modules);

    Result result = compiler.getResult();
    assertThat(result.success).isTrue();

    assertThat(compiler.toSource(module1))
        .isEqualTo(
            "function Bar(){}Bar.prototype.b=function(x){};"
                + "var foo;foo.b(foo);foo.f=foo;foo.a=foo;");

    assertThat(compiler.toSource(module2))
        .isEqualTo(
            "function Far(){}Far.prototype.c=function(y){};"
                + "var too;too.c(too);too.g=too;too.a=too;");

    // Note that properties that occur most often globally get the earliest
    // names. The "getC" property, which doesn't occur until module 3, is
    // renamed to an earlier name in the alphabet than "woo", which appears
    // in module 2, because "getC" occurs more total times across all modules.
    // Might be better to give early modules the shortest names, but this is
    // how the pass currently works.
    assertThat(compiler.toSource(module3))
        .isEqualTo(
            "function Car(){}Car.prototype.d=function(z){};"
                + "var noo;noo.d(noo);noo.h=noo;noo.e=noo;");
  }

  @Test
  public void testPropertyAffinityOff() {
    test(
        "var foo={};foo.x=1;foo.y=2;foo.z=3;"
            + "function f1() { foo.z; foo.z; foo.z; foo.y}"
            + "function f2() {                      foo.x}",
        "var foo={};foo.b=1;foo.c=2;foo.a=3;"
            + "function f1() { foo.a; foo.a; foo.a; foo.c}"
            + "function f2() {                      foo.b}");

    test(
        "var foo={};foo.x=1;foo.y=2;foo.z=3;"
            + "function f1() { foo.z; foo.z; foo.z; foo.y}"
            + "function f2() { foo.z; foo.z; foo.z; foo.x}",
        "var foo={};foo.b=1;foo.c=2;foo.a=3;"
            + "function f1() { foo.a; foo.a; foo.a; foo.c}"
            + "function f2() { foo.a; foo.a; foo.a; foo.b}");
  }

  @Test
  public void testPrototypePropertiesStable() {
    testStableRenaming(
        "Bar.prototype.getA = function(){}; bar.getA();" + "Bar.prototype.getB = function(){};",
        "Bar.prototype.a = function(){}; bar.a();" + "Bar.prototype.b = function(){}",
        "Bar.prototype.get = function(){}; bar.get();"
            + "Bar.prototype.getA = function(){}; bar.getA();"
            + "Bar.prototype.getB = function(){};",
        "Bar.prototype.c = function(){}; bar.c();"
            + "Bar.prototype.a = function(){}; bar.a();"
            + "Bar.prototype.b = function(){}");
  }

  @Test
  public void testPrototypePropertiesAsObjLitKeysStable() {
    testStableRenaming(
        "Bar.prototype = {2: function(){}, getA: function(){}}; bar[2]();",
        "Bar.prototype = {2: function(){}, a: function(){}}; bar[2]();",
        "Bar.prototype = {getB: function(){},getA: function(){}}; bar.getB();",
        "Bar.prototype = {b: function(){},a: function(){}}; bar.b();");
  }

  @Test
  public void testMixedQuotedAndUnquotedObjLitKeysStable() {
    testStableRenaming(
        "Bar = {getA: function(){}, 'getB': function(){}}; bar.getA();",
        "Bar = {a: function(){}, 'getB': function(){}}; bar.a();",
        "Bar = {get: function(){}, getA: function(){}, 'getB': function(){}};"
            + "bar.getA();bar.get();",
        "Bar = {b: function(){}, a: function(){}, 'getB': function(){}};" + "bar.a();bar.b();");
  }

  @Test
  public void testOverlappingOriginalAndGeneratedNamesStable() {
    testStableRenaming(
        "Bar.prototype = {b: function(){}, a: function(){}}; bar.b();",
        "Bar.prototype = {a: function(){}, b: function(){}}; bar.a();",
        "Bar.prototype = {c: function(){}, b: function(){}, a: function(){}};" + "bar.b();",
        "Bar.prototype = {c: function(){}, a: function(){}, b: function(){}};" + "bar.a();");
  }

  @Test
  public void testStableWithTrickyExternsChanges() {
    test(
        "Bar.prototype = {b: function(){}, a: function(){}}; bar.b();",
        "Bar.prototype = {a: function(){}, b: function(){}}; bar.a();");
    prevUsedPropertyMap = renameProperties.getPropertyMap();
    String externs = EXTERNS + "prop.b;";
    test(
        externs(externs),
        srcs(
            "Bar.prototype = {new_f: function(){}, b: function(){}, "
                + "a: function(){}};bar.b();"),
        expected("Bar.prototype = {c:function(){}, b:function(){}, a:function(){}};" + "bar.b();"));
  }

  @Test
  public void testRenamePropertiesWithLeadingUnderscoresStable() {
    testStableRenaming(
        "Bar.prototype = {_getA: function(){}, _b: 0}; bar._getA();",
        "Bar.prototype = {a: function(){}, b: 0}; bar.a();",
        "Bar.prototype = {_getA: function(){}, _c: 1, _b: 0}; bar._getA();",
        "Bar.prototype = {a: function(){}, c: 1, b: 0}; bar.a();");
  }

  @Test
  public void testPropertyAddedToObjectStable() {
    testStableRenaming(
        "var foo = {}; foo.prop = '';",
        "var foo = {}; foo.a = '';",
        "var foo = {}; foo.prop = ''; foo.a='';",
        "var foo = {}; foo.a = ''; foo.b='';");
  }

  @Test
  public void testAvoidingConflictsBetQuotedAndUnquotedPropertyNamesStable() {
    testStableRenaming(
        "Bar.prototype.foo = function(){}; Bar.prototype['b'] = 0; bar.foo();",
        "Bar.prototype.a = function(){}; Bar.prototype['b'] = 0; bar.a();",
        "Bar.prototype.foo = function(){}; Bar.prototype['a'] = 0; bar.foo();",
        "Bar.prototype.b = function(){}; Bar.prototype['a'] = 0; bar.b();");
  }

  @Test
  public void testRenamePropertiesFunctionCallStable() {
    testStableRenaming(
        "var foo = {myProp: 0}; "
            + "f(JSCompiler_renameProperty('otherProp.myProp.someProp')); "
            + "foo.myProp = 1; foo.theirProp = 2; foo.yourProp = 3;",
        "var foo = {a: 0}; f('b.a.c'); " + "foo.a = 1; foo.d = 2; foo.e = 3;",
        "var bar = {newProp: 0}; var foo = {myProp: 0}; "
            + "f(JSCompiler_renameProperty('otherProp.myProp.someProp')); "
            + "foo.myProp = 1; foo.theirProp = 2; foo.yourProp = 3;",
        "var bar = {f: 0}; var foo = {a: 0}; f('b.a.c'); " + "foo.a = 1; foo.d = 2; foo.e = 3;");
  }

  private void testStableRenaming(
      String input1, String expected1, String input2, String expected2) {
    test(input1, expected1);
    prevUsedPropertyMap = renameProperties.getPropertyMap();
    test(input2, expected2);
  }

  // Test cases added for ES6 Features
  @Test
  public void testPrototypePropertyForArrowFunction() {
    test(
        "Bar.prototype = {2: () => {}, getA: () => {}}; bar[2]();",
        "Bar.prototype = {2: () => {}, a:    () => {}}; bar[2]();");
  }

  @Test
  public void testArrayDestructuring() {
    testSame("var [first, second] = someArray");
  }

  @Test
  public void testDestructuredProperties() {
    // using destructuring shorthand
    test("var {   foo,   bar } = { foo: 1, bar: 2 }", "var { b:foo, a:bar } = {    b:1,    a:2 }");

    // without destructuring shorthand
    test(
        "var { foo:foo, bar:bar } = { foo:1, bar:2 }",
        "var {   b:foo,   a:bar } = {   b:1,   a:2 }");

    test(
        "var foo = { bar: 1, baz: 2 }; var foo1 = foo.bar; var foo2 = foo.baz; ",
        "var foo = {   a: 1,   b: 2 }; var foo1 = foo.a;   var foo2 = foo.b;");
  }

  @Test
  public void testNestedDestructuringProperties() {
    test(
        "var {outer: {inner}} = {outer: {inner: 'value'}};",
        "var {b: {a: inner}} = {b: {a: 'value'}};");
  }

  @Test
  public void testComputedPropertyNamesInObjectLit() {
    // TODO (simranarora) A restriction of this pass is that quoted and unquoted property
    // references cannot be mixed.
    test(
        lines("var a = {", "  ['val' + ++i]: i,", "  ['val' + ++i]: i", "};", "a.val1;"),
        lines(
            "var a = {",
            "  ['val' + ++i]: i,", // don't rename here
            "  ['val' + ++i]: i",
            "};",
            "a.a;")); // rename here
  }

  @Test
  public void testComputedMethodPropertyNamesInClass() {
    // TODO (simranarora) A restriction of this pass is that quoted and unquoted property
    // references cannot be mixed.

    // Concatination for computed property
    test(
        lines(
            "class Bar {",
            "  constructor(){}",
            "  ['f'+'oo']() {",
            "    return 1",
            "  }",
            "}",
            "var bar = new Bar()",
            "bar.foo();"),
        lines(
            "class Bar {",
            "  constructor(){}",
            "  ['f'+'oo']() {", // don't rename here
            "    return 1",
            "  }",
            "}",
            "var bar = new Bar()",
            "bar.a();")); // rename here

    // Without property concatination
    test(
        lines(
            "class Bar {",
            "  constructor(){}",
            "  ['foo']() {",
            "    return 1",
            "  }",
            "}",
            "var bar = new Bar()",
            "bar.foo();"),
        lines(
            "class Bar {",
            "  constructor(){}",
            "  ['foo']() {", // don't rename here
            "    return 1",
            "  }",
            "}",
            "var bar = new Bar()",
            "bar.a();")); // rename here
  }

  @Test
  public void testClasses() {
    // Call class method inside class scope - due to the scoping rules of javascript, the "getA()"
    // call inside of getB() refers to a method getA() in the outer scope and not the getA() method
    // inside the Bar class
    test(
        lines(
            "function getA() {};",
            "class Bar {",
            "  constructor(){}",
            "  getA() {",
            "    return 1",
            "  }",
            "  getB(x) {",
            "    getA();",
            "  }",
            "}"),
        lines(
            "function getA() {};",
            "class Bar {",
            "  constructor(){}",
            "  a() {",
            "    return 1",
            "  }",
            "  b(x) {",
            "    getA();",
            "  }",
            "}"));

    // Call class method inside class scope - due to the scoping rules of javascript,
    // the "this.getA()" call inside of getB() refers to a method getA() in the Bar class and
    // not the getA() method in the outer scope
    test(
        lines(
            "function getA() {};",
            "class Bar {",
            "  constructor(){}",
            "  getA() {",
            "    return 1",
            "  }",
            "  getB(x) {",
            "    this.getA();",
            "  }",
            "}"),
        lines(
            "function getA() {};",
            "class Bar {",
            "  constructor(){}",
            "  a() {",
            "    return 1",
            "  }",
            "  b(x) {",
            "    this.a();",
            "  }",
            "}"));

    // Call class method outside class scope
    test(
        lines(
            "class Bar {",
            "  constructor(){}",
            "  getB(x) {}",
            "}",
            "var too;",
            "var too = new Bar();",
            "too.getB(too);"),
        lines(
            "class Bar {",
            "  constructor(){}",
            "  a(x) {}",
            "}",
            "var too;",
            "var too = new Bar();",
            "too.a(too);"));
  }

  @Test
  public void testGetSetInClass() {
    test(
        lines(
            "class Bar {",
            "  constructor(foo){",
            "    this.foo = foo;",
            "  }",
            "  get foo() {",
            "    return this.foo;",
            "  }",
            "  set foo(x) {",
            "    this.foo = x;",
            "  }",
            "}",
            "var barObj = new Bar();",
            "barObj.foo();",
            "barObj.foo(1);"),
        lines(
            "class Bar {",
            "  constructor(foo){",
            "    this.a = foo;",
            "  }",
            "  get a() {",
            "    return this.a;",
            "  }",
            "  set a(x) {",
            "    this.a = x;",
            "  }",
            "}",
            "var barObj = new Bar();",
            "barObj.a();",
            "barObj.a(1);"));
  }

  @Test
  public void testStaticMethodInClass() {

    test(
        lines(
            "class Bar {", "  static double(n) {", "    return n*2", "  }", "}", "Bar.double(1);"),
        lines("class Bar {", "  static a(n) {", "    return n*2", "  }", "}", "Bar.a(1);"));
  }

  @Test
  public void testClassFields() {
    test(
        lines(
            "class Bar {", //
            "  field = 7;",
            "}",
            "var bar = new Bar();",
            "bar.field;"),
        lines(
            "class Bar {", //
            "  a = 7;",
            "}",
            "var bar = new Bar();",
            "bar.a;"));
  }

  @Test
  public void testClassFieldWithFunctionRHS() {
    test(
        lines(
            "class Bar {", //
            "  superClass_ = function f(){};",
            "}",
            "var bar = new Bar();",
            "bar.superClass_;"),
        lines(
            "class Bar {", //
            "  a = function f(){};",
            "}",
            "var bar = new Bar();",
            "bar.a;"));
  }

  @Test
  public void testStaticClassFields() {
    test(
        lines("class Bar {", "  static field = 1;", "}", "Bar.field;"),
        lines("class Bar {", "  static a = 1;", "}", "Bar.a;"));
  }

  @Test
  public void testClassComputedFields() {
    test(
        lines(
            "class Bar {", //
            "  ['field'] = 1;",
            "}",
            "var bar = new Bar()",
            "bar.field;"),
        lines(
            "class Bar {", //
            "  ['field'] = 1;",
            "}",
            "var bar = new Bar()",
            "bar.a;"));
  }

  @Test
  public void testStaticClassComputedFields() {
    test(
        lines(
            "class Bar {", //
            "  static ['field'] = 1;",
            "}",
            "Bar.field;"),
        lines("class Bar {", "  static ['field'] = 1;", "}", "Bar.a;"));
  }

  @Test
  public void testClassMixedFields() {
    test(
        lines(
            "class Bar {", //
            "  field = 1;",
            "  ['field'] = 2;",
            "  static 1 = 5;",
            "}",
            "var bar = new Bar()",
            "bar.field;"),
        lines(
            "class Bar {",
            "  a = 1;",
            "  ['field'] = 2;",
            "  static 1 = 5;",
            "}",
            "var bar = new Bar()",
            "bar.a;"));
  }

  @Test
  public void testObjectMethodProperty() {
    // ES5 version
    test(
        lines(
            "var foo = { ",
            "  bar: 1, ",
            "  myFunc: function myFunc() {",
            "    return this.bar",
            "  }",
            "};",
            "foo.myFunc();"),
        lines(
            "var foo = { ",
            "  a: 1, ",
            "  b: function myFunc() {",
            "    return this.a",
            "  }",
            "};",
            "foo.b();"));

    // ES6 version
    test(
        lines(
            "var foo = { ",
            "  bar: 1, ",
            "  myFunc() {",
            "    return this.bar",
            "  }",
            "};",
            "foo.myFunc();"),
        lines("var foo = { ", "  a: 1, ", "  b() {", "    return this.a", "  }", "};", "foo.b();"));
  }

  private Compiler compileModules(String externs, JSChunk[] modules) {
    SourceFile externsInput = SourceFile.fromCode("externs", externs);

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);

    Compiler compiler = new Compiler();
    compiler.compileModules(ImmutableList.of(externsInput), ImmutableList.copyOf(modules), options);
    return compiler;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return renameProperties =
        new RenameProperties(
            compiler,
            generatePseudoNames,
            prevUsedPropertyMap,
            null,
            null,
            new DefaultNameGenerator());
  }
}
