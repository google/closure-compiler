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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit test for AmbiguateProperties Compiler pass.
 *
 */

@RunWith(JUnit4.class)
public final class AmbiguatePropertiesTest extends CompilerTestCase {
  private AmbiguateProperties lastPass;

  private static final String EXTERNS = lines(
      MINIMAL_EXTERNS,
      "Function.prototype.call=function(){};",
      "Function.prototype.inherits=function(){};",
      "Object.defineProperties = function(typeRef, definitions) {};",
      "Object.prototype.toString = function() {};",
      "var google = { gears: { factory: {}, workerPool: {} } };",
      "/** @return {?} */ function any() {};",
      "/** @constructor */ function Window() {}",
      "/** @const */ var window = {};");

  public AmbiguatePropertiesTest() {
    super(EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableNormalize();
    enableClosurePass();
    enableGatherExternProperties();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        lastPass =
            AmbiguateProperties.makePassForTesting(compiler, new char[] {'$'}, new char[] {'$'});
        lastPass.process(externs, root);
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testOneVar1() {
    test("/** @constructor */ var Foo = function(){};Foo.prototype.b = 0;",
         "/** @constructor */ var Foo = function(){};Foo.prototype.a = 0;");
  }

  @Test
  public void testOneVar2() {
    test(lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {b: 0};"),
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {a: 0};"));
  }

  @Test
  public void testOneVar3() {
    test(
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {get b() {return 0}};"),
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {get a() {return 0}};"));
  }

  @Test
  public void testOneVar4() {
    test(
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {set b(a) {}};"),
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {set a(a) {}};"));
  }

  @Test
  public void testTwoVar1() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.z=0;",
        "Foo.prototype.z=0;",
        "Foo.prototype.x=0;");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.a=0;",
        "Foo.prototype.a=0;",
        "Foo.prototype.b=0;");
    test(js, output);
  }

  @Test
  public void testTwoVar2() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {z:0, z:1, x:0};");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {a:0, a:1, b:0};");
    test(js, output);
  }

  @Test
  public void testTwoIndependentVar() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.b = 0;",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype.c = 0;");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.a=0;",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype.a=0;");
    test(js, output);
  }

  @Test
  public void testTwoTypesTwoVar() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.r = 0;",
        "Foo.prototype.g = 0;",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype.c = 0;",
        "Bar.prototype.r = 0;");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.a=0;",
        "Foo.prototype.b=0;",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype.b=0;",
        "Bar.prototype.a=0;");
    test(js, output);
  }

  @Test
  public void testUnion() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "Foo.prototype.foodoo=0;",
        "Bar.prototype.bardoo=0;",
        "/** @type {Foo|Bar} */",
        "var U = any();",
        "U.joint;",
        "U.joint");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "Foo.prototype.b=0;",
        "Bar.prototype.b=0;",
        "/** @type {Foo|Bar} */",
        "var U = any();",
        "U.a;",
        "U.a");
    test(js, output);
  }

  @Test
  public void testUnions() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "/** @constructor */ var Baz = function(){};",
        "/** @constructor */ var Bat = function(){};",
        "Foo.prototype.lone1=0;",
        "Bar.prototype.lone2=0;",
        "Baz.prototype.lone3=0;",
        "Bat.prototype.lone4=0;",
        "/** @type {Foo|Bar} */",
        "var U1 = any();",
        "U1.j1;",
        "U1.j2;",
        "/** @type {Baz|Bar} */",
        "var U2 = any();",
        "U2.j3;",
        "U2.j4;",
        "/** @type {Baz|Bat} */",
        "var U3 = any();",
        "U3.j5;",
        "U3.j6");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "/** @constructor */ var Baz = function(){};",
        "/** @constructor */ var Bat = function(){};",
        "Foo.prototype.c=0;",
        "Bar.prototype.e=0;",
        "Baz.prototype.e=0;",
        "Bat.prototype.c=0;",
        "/** @type {Foo|Bar} */",
        "var U1 = any();",
        "U1.a;",
        "U1.b;",
        "/** @type {Baz|Bar} */",
        "var U2 = any();",
        "U2.c;",
        "U2.d;",
        "/** @type {Baz|Bat} */",
        "var U3 = any();",
        "U3.a;",
        "U3.b");
    test(js, output);
  }

  @Test
  public void testExtends() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.x=0;",
        "/** @constructor \n @extends Foo */ var Bar = function(){};",
        "goog.inherits(Bar, Foo);",
        "Bar.prototype.y=0;",
        "Bar.prototype.z=0;",
        "/** @constructor */ var Baz = function(){};",
        "Baz.prototype.l=0;",
        "Baz.prototype.m=0;",
        "Baz.prototype.n=0;",
        "(new Baz).m\n");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.a=0;",
        "/** @constructor \n @extends Foo */ var Bar = function(){};",
        "goog.inherits(Bar, Foo);",
        "Bar.prototype.b=0;",
        "Bar.prototype.c=0;",
        "/** @constructor */ var Baz = function(){};",
        "Baz.prototype.b=0;",
        "Baz.prototype.a=0;",
        "Baz.prototype.c=0;",
        "(new Baz).a\n");
    test(js, output);
  }

  @Test
  public void testLotsOfVars() {
    StringBuilder js = new StringBuilder();
    StringBuilder output = new StringBuilder();
    js.append("/** @constructor */ var Foo = function(){};\n");
    js.append("/** @constructor */ var Bar = function(){};\n");
    output.append(js);

    int vars = 10;
    for (int i = 0; i < vars; i++) {
      js.append("Foo.prototype.var").append(i).append(" = 0;");
      js.append("Bar.prototype.var").append(i + 10000).append(" = 0;");
      output.append("Foo.prototype.").append((char) ('a' + i)).append("=0;");
      output.append("Bar.prototype.").append((char) ('a' + i)).append("=0;");
    }
    test(js.toString(), output.toString());
  }

  @Test
  public void testLotsOfClasses() {
    StringBuilder b = new StringBuilder();
    int classes = 10;
    for (int i = 0; i < classes; i++) {
      String c = "Foo" + i;
      b.append("/** @constructor */ var ").append(c).append(" = function(){};\n");
      b.append(c).append(".prototype.varness").append(i).append(" = 0;");
    }
    String js = b.toString();
    test(js, js.replaceAll("varness\\d+", "a"));
  }

  @Test
  public void testFunctionType() {
    String js = lines(
        "/** @constructor */ function Foo(){};",
        "/** @return {Bar} */",
        "Foo.prototype.fun = function() { return new Bar(); };",
        "/** @constructor */ function Bar(){};",
        "Bar.prototype.bazz;",
        "(new Foo).fun().bazz();");
    String output = lines(
        "/** @constructor */ function Foo(){};",
        "/** @return {Bar} */",
        "Foo.prototype.a = function() { return new Bar(); };",
        "/** @constructor */ function Bar(){};",
        "Bar.prototype.a;",
        "(new Foo).a().a();");
    test(js, output);
  }

  @Test
  public void testPrototypePropertiesAsObjLitKeys1() {
    test("/** @constructor */ function Bar() {};" +
             "Bar.prototype = {2: function(){}, getA: function(){}};",
             "/** @constructor */ function Bar() {};" +
             "Bar.prototype = {2: function(){}, a: function(){}};");
  }

  @Test
  public void testPrototypePropertiesAsObjLitKeys2() {
    testSame("/** @constructor */ function Bar() {};" +
             "Bar.prototype = {2: function(){}, 'getA': function(){}};");
  }

  @Test
  public void testQuotedPrototypeProperty() {
    testSame("/** @constructor */ function Bar() {};" +
             "Bar.prototype['getA'] = function(){};" +
             "var bar = new Bar();" +
             "bar['getA']();");
  }

  @Test
  public void testObjectDefineProperties() {
    String js =
        lines(
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype.bar = 0;",
            "/** @struct @constructor */ var Foo = function() {",
            "  this.bar_ = 'bar';",
            "};",
            "/** @type {?} */ Foo.prototype.bar;",
            "Object.defineProperties(Foo.prototype, {",
            "  bar: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {Foo} */ get: function() { return this.bar_;},",
            "    /** @this {Foo} */ set: function(value) { this.bar_ = value; }",
            "  }",
            "});");

    String result =
        lines(
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype.a = 0;",
            "/** @struct @constructor */ var Foo = function() {",
            "  this.b = 'bar';",
            "};",
            "/** @type {?} */ Foo.prototype.a;",
            "Object.defineProperties(Foo.prototype, {",
            "  a: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {Foo} */ get: function() { return this.b;},",
            "    /** @this {Foo} */ set: function(value) { this.b = value; }",
            "  }",
            "});");

    test(js, result);
  }

  @Test
  public void testObjectDefinePropertiesQuoted() {
    String js =
        lines(
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype.bar = 0;",
            "/** @struct @constructor */ var Foo = function() {",
            "  this.bar_ = 'bar';",
            "};",
            "/** @type {?} */ Foo.prototype['bar'];",
            "Object.defineProperties(Foo.prototype, {",
            "  'a': {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {Foo} */ get: function() { return this.bar_;},",
            "    /** @this {Foo} */ set: function(value) { this.bar_ = value; }",
            "  }",
            "});");

    String result =
        lines(
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype.b = 0;",
            "/** @struct @constructor */ var Foo = function() {",
            "  this.b = 'bar';",
            "};",
            "/** @type {?} */ Foo.prototype['bar'];",
            "Object.defineProperties(Foo.prototype, {",
            "  'a': {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {Foo} */ get: function() { return this.b;},",
            "    /** @this {Foo} */ set: function(value) { this.b = value; }",
            "  }",
            "});");

    test(js, result);
  }

  @Test
  public void testOverlappingOriginalAndGeneratedNames() {
    test(
        lines(
            "/** @constructor */ function Bar(){};",
            "Bar.prototype.b = function(){};",
            "Bar.prototype.a = function(){};",
            "var bar = new Bar();",
            "bar.b();"),
        lines(
            "/** @constructor */ function Bar(){};",
            "Bar.prototype.a = function(){};",
            "Bar.prototype.b = function(){};",
            "var bar = new Bar();",
            "bar.a();"));
  }

  @Test
  public void testPropertyAddedToObject() {
    testSame("var foo = {}; foo.prop = '';");
  }

  @Test
  public void testPropertyAddedToFunction() {
    test("var foo = function(){}; foo.prop = '';",
         "var foo = function(){}; foo.a = '';");
  }

  @Test
  public void testPropertyAddedToFunctionIndirectly() {
    test(
        lines(
            "var foo = function(){}; foo.prop = ''; foo.baz = '';",
            "function f(/** function(): void */ fun) { fun.bar = ''; fun.baz = ''; }"),
        lines(
            "var foo = function(){}; foo.a = ''; foo.baz = '';",
            "function f(/** function(): void */ fun) { fun.bar = ''; fun.baz = ''; }"));
  }

  @Test
  public void testPropertyOfObjectOfUnknownType() {
    testSame("var foo = x(); foo.prop = '';");
  }

  @Test
  public void testPropertyOnParamOfUnknownType() {
    testSame(
        lines(
            "/** @constructor */ function Foo(){};",
             "Foo.prototype.prop = 0;",
             "function go(aFoo){",
             "  aFoo.prop = 1;",
             "}"));
  }

  @Test
  public void testSetPropertyOfGlobalThis() {
    test("this.prop = 'bar'", "this.a = 'bar'");
  }

  @Test
  public void testReadPropertyOfGlobalThis() {
    testSame(externs(EXTERNS + "Object.prototype.prop;"), srcs("f(this.prop);"));
  }

  @Test
  public void testSetQuotedPropertyOfThis() {
    testSame("this['prop'] = 'bar';");
  }

  @Test
  public void testExternedPropertyName() {
    test(
        lines(
            "/** @constructor */ var Bar = function(){};",
            "/** @override */ Bar.prototype.toString = function(){};",
            "Bar.prototype.func = function(){};",
            "var bar = new Bar();",
            "bar.toString();"),
        lines(
            "/** @constructor */ var Bar = function(){};",
            "/** @override */ Bar.prototype.toString = function(){};",
            "Bar.prototype.a = function(){};",
            "var bar = new Bar();",
            "bar.toString();"));
  }

  @Test
  public void testExternedPropertyNameDefinedByObjectLiteral() {
    testSame("/**@constructor*/function Bar(){};Bar.prototype.factory");
  }

  @Test
  public void testStaticAndInstanceMethodWithSameName() {
    test("/** @constructor */function Bar(){}; Bar.getA = function(){}; " +
         "Bar.prototype.getA = function(){}; Bar.getA();" +
         "var bar = new Bar(); bar.getA();",
         "/** @constructor */function Bar(){}; Bar.a = function(){};" +
         "Bar.prototype.a = function(){}; Bar.a();" +
         "var bar = new Bar(); bar.a();");
  }

  @Test
  public void testStaticAndInstanceProperties() {
    test("/** @constructor */function Bar(){};" +
         "Bar.getA = function(){}; " +
         "Bar.prototype.getB = function(){};",
         "/** @constructor */function Bar(){}; Bar.a = function(){};" +
         "Bar.prototype.a = function(){};");
  }

  @Test
  public void testStaticAndSubInstanceProperties() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.x=0;",
        "/** @constructor \n @extends Foo */ var Bar = function(){};",
        "goog.inherits(Bar, Foo);",
        "Bar.y=0;",
        "Bar.prototype.z=0;\n");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.a=0;",
        "/** @constructor \n @extends Foo */ var Bar = function(){};",
        "goog.inherits(Bar, Foo);",
        "Bar.a=0;",
        "Bar.prototype.a=0;\n");
    test(js, output);
  }

  @Test
  public void testStatic() {
    String js = lines(
      "/** @constructor */ var Foo = function() {};",
      "Foo.x = 0;",
      "/** @constructor */ var Bar = function() {};",
      "Bar.y = 0;");

    String output = lines(
      "/** @constructor */ var Foo = function() {};",
      "Foo.a = 0;",
      "/** @constructor */ var Bar = function() {};",
      "Bar.a = 0;");
    test(js, output);
  }

  @Test
  public void testClassWithStaticsPassedToUnrelatedFunction() {
    String js = lines(
      "/** @constructor */ var Foo = function() {};",
      "Foo.x = 0;",
      "/** @param {!Function} x */ function f(x) { x.y = 1; x.z = 2; }",
      "f(Foo)");
    String output = lines(
      "/** @constructor */ var Foo = function() {};",
      "Foo.a = 0;",
      "/** @param {!Function} x */ function f(x) { x.y = 1; x.z = 2; }",
      "f(Foo)");
    test(js, output);
  }

  @Test
  public void testClassWithStaticsPassedToRelatedFunction() {
    String js = lines(
      "/** @constructor */ var Foo = function() {};",
      "Foo.x = 0;",
      "/** @param {!Function} x */ function f(x) { x.y = 1; x.x = 2;}",
      "f(Foo)");
    testSame(js);
  }

  @Test
  public void testTypeMismatch() {
    ignoreWarnings(
        TypeValidator.TYPE_MISMATCH_WARNING);
    testSame(lines(
        "/** @constructor */var Foo = function(){};",
        "/** @constructor */var Bar = function(){};",
        "Foo.prototype.b = 0;",
        "/** @type {Foo} */",
        "var F = new Bar();"));
  }

  @Test
  public void testRenamingMap() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.z=0;",
        "Foo.prototype.z=0;",
        "Foo.prototype.x=0;",
        "Foo.prototype.y=0;");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.a=0;",
        "Foo.prototype.a=0;",
        "Foo.prototype.b=0;",
        "Foo.prototype.c=0;");
    test(js, output);

    Map<String, String> answerMap = new HashMap<>();
    answerMap.put("x", "b");
    answerMap.put("y", "c");
    answerMap.put("z", "a");
    assertThat(lastPass.getRenamingMap()).isEqualTo(answerMap);
  }

  @Test
  public void testInline() {
    String js = lines(
        "/** @interface */ function Foo(){}",
        "Foo.prototype.x = function(){};",
        "/**",
        " * @constructor",
        " * @implements {Foo}",
        " */",
        "function Bar(){}",
        "Bar.prototype.y;",
        "/** @override */",
        "Bar.prototype.x = function() { return this.y; };",
        "Bar.prototype.z = function() {};\n"
        // Simulates inline getters.
        + "/** @type {Foo} */ (new Bar).y;");
    String output = lines(
        "/** @interface */ function Foo(){}",
        "Foo.prototype.b = function(){};",
        "/**",
        " * @constructor",
        " * @implements {Foo}",
        " */",
        "function Bar(){}",
        "Bar.prototype.a;",
        "/** @override */",
        "Bar.prototype.b = function() { return this.a; };",
        "Bar.prototype.c = function() {};\n"
        // Simulates inline getters.
        + "(new Bar).a;");
    test(js, output);
  }

  @Test
  public void testImplementsAndExtends() {
    String js = lines(
        "/** @interface */ function Foo() {}",
        "/** @constructor */ function Bar(){}",
        "Bar.prototype.y = function() { return 3; };",
        "/**",
        " * @constructor",
        " * @extends {Bar}",
        " * @implements {Foo}",
        " */",
        "function SubBar(){ }",
        "/** @param {Foo} x */ function f(x) { x.z = 3; }",
        "/** @param {SubBar} x */ function g(x) { x.z = 3; }");
    String output = lines(
        "/** @interface */ function Foo(){}",
        "/** @constructor */ function Bar(){}",
        "Bar.prototype.b = function() { return 3; };",
        "/**",
        " * @constructor",
        " * @extends {Bar}",
        " * @implements {Foo}",
        " */",
        "function SubBar(){}",
        "/** @param {Foo} x */ function f(x) { x.a = 3; }",
        "/** @param {SubBar} x */ function g(x) { x.a = 3; }");
    test(js, output);
  }

  @Test
  public void testImplementsAndExtends2() {
    String js = lines(
        "/** @interface */ function A() {}",
        "/**",
        " * @constructor",
        " */",
        "function C1(){}",
        "/**",
        " * @constructor",
        " * @extends {C1}",
        " * @implements {A}",
        " */",
        "function C2(){}",
        "/** @param {C1} x */ function f(x) { x.y = 3; }",
        "/** @param {A} x */ function g(x) { x.z = 3; }\n");
    String output = lines(
        "/** @interface */ function A(){}",
        "/**",
        " * @constructor",
        " */",
        "function C1(){}",
        "/**",
        " * @constructor",
        " * @extends {C1}",
        " * @implements {A}",
        " */",
        "function C2(){}",
        "/** @param {C1} x */ function f(x) { x.a = 3; }",
        "/** @param {A} x */ function g(x) { x.b = 3; }\n");
    test(js, output);
  }

  @Test
  public void testExtendsInterface() {
    String js = lines(
        "/** @interface */ function A() {}",
        "/** @interface \n @extends {A} */ function B() {}",
        "/** @param {A} x */ function f(x) { x.y = 3; }",
        "/** @param {B} x */ function g(x) { x.z = 3; }\n");
    String output = lines(
        "/** @interface */ function A(){}",
        "/** @interface \n @extends {A} */ function B(){}",
        "/** @param {A} x */ function f(x) { x.a = 3; }",
        "/** @param {B} x */ function g(x) { x.b = 3; }\n");
    test(js, output);
  }

  @Test
  public void testInterfaceWithSubInterfaceAndDirectImplementors() {
    ignoreWarnings(
        TypeValidator.INTERFACE_METHOD_NOT_IMPLEMENTED);
    test(
        lines(
            "/** @interface */ function Foo(){};",
            "Foo.prototype.foo = function(){};",
            "/** @interface @extends {Foo} */ function Bar(){};",
            "/** @constructor @implements {Foo} */ function Baz(){};",
            "Baz.prototype.baz = function(){};"),
        lines(
            "/** @interface */ function Foo(){};",
            "Foo.prototype.b = function(){};",
            "/** @interface @extends {Foo} */ function Bar(){};",
            "/** @constructor @implements {Foo} */ function Baz(){};",
            "Baz.prototype.a = function(){};"));
  }

  @Test
  public void testFunctionSubType() {
    String js = lines(
        "Function.prototype.a = 1;",
        "function f() {}",
        "f.y = 2;\n");
    String output = lines(
        "Function.prototype.a = 1;",
        "function f() {}",
        "f.b = 2;\n");
    test(js, output);
  }

  @Test
  public void testFunctionSubType2() {
    String js = lines(
        "Function.prototype.a = 1;",
        "/** @constructor */ function F() {}",
        "F.y = 2;\n");
    String output = lines(
        "Function.prototype.a = 1;",
        "/** @constructor */ function F() {}",
        "F.b = 2;\n");
    test(js, output);
  }

  @Test
  public void testPredeclaredType() {
    String js = lines(
        "goog.addDependency('zzz.js', ['goog.Foo'], []);",
        "/** @constructor */ ",
        "function A() {",
        "  this.x = 3;",
        "}",
        "/** @param {goog.Foo} x */",
        "function f(x) { x.y = 4; }");
    String result = lines(
        "0;",
        "/** @constructor */ ",
        "function A() {",
        "  this.a = 3;",
        "}",
        "/** @param {goog.Foo} x */",
        "function f(x) { x.y = 4; }");
    test(js, result);
  }

  @Test
  public void testBug14291280() {
    String js = lines(
        "/** @constructor \n @template T */\n",
        "function C() {\n",
        "  this.aa = 1;\n",
        "}\n",
        "/** @return {C.<T>} */\n",
        "C.prototype.method = function() {\n",
        "  return this;\n",
        "}\n",
        "/** @type {C.<string>} */\n",
        "var x = new C().method();\n",
        "x.bb = 2;\n",
        "x.cc = 3;");
    String result = lines(
        "/** @constructor \n @template T */\n",
        "function C() {",
        "  this.b = 1;",
        "}",
        "/** @return {C.<T>} */\n",
        "C.prototype.a = function() {",
        "  return this;",
        "};",
        "/** @type {C.<string>} */\n",
        "var x = (new C).a();",
        "x.c = 2;",
        "x.d = 3;");
    test(js, result);
  }

  @Test
  public void testAmbiguateWithAnAlias() {
    String js = lines(
        "/** @constructor */ function Foo() { this.abc = 5; }\n",
        "/** @const */ var alias = Foo;\n",
        "/** @constructor @extends alias */\n",
        "function Bar() {\n",
        "  this.xyz = 7;\n",
        "}");
    String result = lines(
        "/** @constructor */ function Foo() { this.a = 5; }\n",
        "/** @const */ var alias = Foo;\n",
        "/** @constructor @extends alias */\n",
        "function Bar() {\n",
        "  this.b = 7;\n",
        "}");
    test(js, result);
  }

  @Test
  public void testAmbiguateWithAliases() {
    String js = lines(
        "/** @constructor */ function Foo() { this.abc = 5; }\n",
        "/** @const */ var alias = Foo;\n",
        "/** @constructor @extends alias */\n",
        "function Bar() {\n",
        "  this.def = 7;\n",
        "}\n",
        "/** @constructor @extends alias */\n",
        "function Baz() {\n",
        "  this.xyz = 8;\n",
        "}");
    String result = lines(
        "/** @constructor */ function Foo() { this.a = 5; }\n",
        "/** @const */ var alias = Foo;\n",
        "/** @constructor @extends alias */\n",
        "function Bar() {\n",
        "  this.b = 7;\n",
        "}\n",
        "/** @constructor @extends alias */\n",
        "function Baz() {\n",
        "  this.b = 8;\n",
        "}");
    test(js, result);
  }

  // See https://github.com/google/closure-compiler/issues/1358
  @Test
  public void testAmbiguateWithStructuralInterfaces() {
    String js = lines(
        "/** @record */",
        "function Record() {}",
        "/** @type {number|undefined} */",
        "Record.prototype.recordProp;",
        "",
        "function f(/** !Record */ a) { use(a.recordProp); }",
        "",
        "/** @constructor */",
        "function Type() {",
        "  /** @const */",
        "  this.classProp = 'a';",
        "}",
        "f(new Type)");
    testSame(js);
  }

  // See https://github.com/google/closure-compiler/issues/2119
  @Test
  public void testUnrelatedObjectLiterals() {
    testSame(lines(
        "/** @constructor */ function Foo() {}",
        "/** @constructor */ function Bar() {}",
        "var lit1 = {a: new Foo()};",
        "var lit2 = {b: new Bar()};"));
  }

  @Test
  public void testObjectLitTwoVar() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {z:0, z:1, x:0};");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {a:0, a:1, b:0};");
    test(js, output);
  }

  @Test
  public void testObjectLitTwoIndependentVar() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {b: 0};",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype = {c: 0};");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {a: 0};",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype = {a: 0};");
    test(js, output);
  }

  @Test
  public void testObjectLitTwoTypesTwoVar() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {r: 0, g: 0};",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype = {c: 0, r: 0};");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {a: 0, b: 0};",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype = {b: 0, a: 0};");
    test(js, output);
  }

  @Test
  public void testObjectLitUnion() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "Foo.prototype = {foodoo: 0};",
        "Bar.prototype = {bardoo: 0};",
        "/** @type {Foo|Bar} */",
        "var U = any();",
        "U.joint;");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "Foo.prototype = {a: 0};",
        "Bar.prototype = {a: 0};",
        "/** @type {Foo|Bar} */",
        "var U = any();",
        "U.b;");
    test(js, output);
  }

  @Test
  public void testObjectLitUnions() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "/** @constructor */ var Baz = function(){};",
        "/** @constructor */ var Bat = function(){};",
        "Foo.prototype = {lone1: 0};",
        "Bar.prototype = {lone2: 0};",
        "Baz.prototype = {lone3: 0};",
        "Bat.prototype = {lone4: 0};",
        "/** @type {Foo|Bar} */",
        "var U1 = any();",
        "U1.j1;",
        "U1.j2;",
        "/** @type {Baz|Bar} */",
        "var U2 = any();",
        "U2.j3;",
        "U2.j4;",
        "/** @type {Baz|Bat} */",
        "var U3 = any();",
        "U3.j5;",
        "U3.j6");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "/** @constructor */ var Baz = function(){};",
        "/** @constructor */ var Bat = function(){};",
        "Foo.prototype = {c: 0};",
        "Bar.prototype = {e: 0};",
        "Baz.prototype = {e: 0};",
        "Bat.prototype = {c: 0};",
        "/** @type {Foo|Bar} */",
        "var U1 = any();",
        "U1.a;",
        "U1.b;",
        "/** @type {Baz|Bar} */",
        "var U2 = any();",
        "U2.c;",
        "U2.d;",
        "/** @type {Baz|Bat} */",
        "var U3 = any();",
        "U3.a;",
        "U3.b");
    test(js, output);
  }

  @Test
  public void testObjectLitExtends() {
    String js = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {x: 0};",
        "/** @constructor \n @extends Foo */ var Bar = function(){};",
        "goog.inherits(Bar, Foo);",
        "Bar.prototype = {y: 0, z: 0};",
        "/** @constructor */ var Baz = function(){};",
        "Baz.prototype = {l: 0, m: 0, n: 0};",
        "(new Baz).m\n");
    String output = lines(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {a: 0};",
        "/** @constructor \n @extends Foo */ var Bar = function(){};",
        "goog.inherits(Bar, Foo);",
        "Bar.prototype = {b: 0, c: 0};",
        "/** @constructor */ var Baz = function(){};",
        "Baz.prototype = {b: 0, a: 0, c: 0};",
        "(new Baz).a\n");
    test(js, output);
  }

  @Test
  public void testObjectLitLotsOfClasses() {
    StringBuilder b = new StringBuilder();
    int classes = 10;
    for (int i = 0; i < classes; i++) {
      String c = "Foo" + i;
      b.append("/** @constructor */ var ").append(c).append(" = function(){};\n");
      b.append(c).append(".prototype = {varness").append(i).append(": 0};");
    }
    String js = b.toString();
    test(js, js.replaceAll("varness\\d+", "a"));
  }

  @Test
  public void testObjectLitFunctionType() {
    String js = lines(
        "/** @constructor */ function Foo(){};",
        "Foo.prototype = { /** @return {Bar} */ fun: function() { return new Bar(); } };",
        "/** @constructor */ function Bar(){};",
        "Bar.prototype.bazz;",
        "(new Foo).fun().bazz();");
    String output = lines(
        "/** @constructor */ function Foo(){};",
        "Foo.prototype = { /** @return {Bar} */ a: function() { return new Bar(); } };",
        "/** @constructor */ function Bar(){};",
        "Bar.prototype.a;",
        "(new Foo).a().a();");
    test(js, output);
  }

  @Test
  public void testObjectLitOverlappingOriginalAndGeneratedNames() {
    test(
        lines(
            "/** @constructor */ function Bar(){};",
            "Bar.prototype = {b: function(){}, a: function(){}};",
            "var bar = new Bar();",
            "bar.b();"),
        lines(
            "/** @constructor */ function Bar(){};",
            "Bar.prototype = {a: function(){}, b: function(){}};",
            "var bar = new Bar();",
            "bar.a();"));
  }

  @Test
  public void testEnum() {
    // TODO(sdh): Consider removing this test if we decide that enum objects are
    // okay to ambiguate (in which case, this would rename to Foo.a).
    testSame(
        lines(
            "/** @enum {string} */ var Foo = {X: 'y'};",
            "var x = Foo.X"));
  }

  @Test
  public void testUnannotatedConstructorsDontCrash() {
    testSame(lines(
        "function Foo() {}",
        "Foo.prototype.a;",
        "function Bar() {}",
        "Bar.prototype.a;"));
  }

  @Test
  public void testGenericPrototypeObject() {
    String js = lines(
        "/**",
        " * @constructor",
        " * @template T",
        " */",
        "function Foo() {",
        "  this.a = 1;",
        "}",
        "/** @constructor @extends {Foo<number>} */",
        "function Bar() {}",
        "/** @constructor */",
        "function Baz() {",
        "  this.b = 2;",
        "}");

    String output = lines(
        "/**",
        " * @constructor",
        " * @template T",
        " */",
        "function Foo() {",
        "  this.a = 1;",
        "}",
        "/** @constructor @extends {Foo<number>} */",
        "function Bar() {}",
        "/** @constructor */",
        "function Baz() {",
        "  this.a = 2;",
        "}");

    test(js, output);
  }

  @Test
  public void testPropertiesWithTypesThatHaveBeenNarrowed() {
    String js = lines(
        "/** @constructor */",
        "function Foo() {",
        "  /** @type {?Object} */",
        "  this.prop1 = {};",
        "  /** @type {?Object} */",
        "  this.prop2;",
        "  this.headerObserver_;",
        "}",
        "Foo.prototype.m = function() {",
        "  this.prop2 = {};",
        "  return this.headerObserver_;",
        "};");

    String output = lines(
        "/** @constructor */",
        "function Foo() {",
        "  /** @type {?Object} */",
        "  this.d = {};",
        "  /** @type {?Object} */",
        "  this.b;",
        "  this.a;",
        "}",
        "Foo.prototype.c = function() {",
        "  this.b = {};",
        "  return this.a;",
        "};");

    test(js, output);
  }

  @Test
  public void testDontRenamePrototypeWithoutExterns() {
    String js = lines(
      "/** @interface */",
      "function Foo() {}",
      "/** @return {!Foo} */",
      "Foo.prototype.foo = function() {};");

    String output = lines(
      "/** @interface */",
      "function Foo() {}",
      "/** @return {!Foo} */",
      "Foo.prototype.a = function() {};");

    test(externs(""), srcs(js), expected(output));
  }
}
