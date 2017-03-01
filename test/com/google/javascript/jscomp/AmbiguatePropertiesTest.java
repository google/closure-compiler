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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit test for AmbiguateProperties Compiler pass.
 *
 */

public final class AmbiguatePropertiesTest extends CompilerTestCase {
  private AmbiguateProperties lastPass;

  private static final String EXTERNS = LINE_JOINER.join(
      "Function.prototype.call=function(){};",
      "Function.prototype.inherits=function(){};",
      "/** @const */ var Object = {};",
      "Object.defineProperties = function(typeRef, definitions) {};",
      "prop.toString;",
      "var google = { gears: { factory: {}, workerPool: {} } };");

  public AmbiguatePropertiesTest() {
    super(EXTERNS);
    enableNormalize();
    enableTypeCheck();
    enableClosurePass();
    enableGatherExternProperties();
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
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

  @Override
  protected CompilerOptions getOptions() {
    // no missing properties check
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    return options;
  }

  public void testOneVar1() {
    test("/** @constructor */ var Foo = function(){};Foo.prototype.b = 0;",
         "/** @constructor */ var Foo = function(){};Foo.prototype.a = 0;");
  }

  public void testOneVar2() {
    test(LINE_JOINER.join(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {b: 0};"),
        LINE_JOINER.join(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {a: 0};"));
  }

  public void testOneVar3() {
    test(
        LINE_JOINER.join(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {get b() {return 0}};"),
        LINE_JOINER.join(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {get a() {return 0}};"));
  }

  public void testOneVar4() {
    test(
        LINE_JOINER.join(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {set b(a) {}};"),
        LINE_JOINER.join(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {set a(a) {}};"));
  }

  public void testTwoVar1() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.z=0;",
        "Foo.prototype.z=0;",
        "Foo.prototype.x=0;");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.a=0;",
        "Foo.prototype.a=0;",
        "Foo.prototype.b=0;");
    test(js, output);
  }

  public void testTwoVar2() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {z:0, z:1, x:0};");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {a:0, a:1, b:0};");
    test(js, output);
  }

  public void testTwoIndependentVar() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.b = 0;",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype.c = 0;");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.a=0;",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype.a=0;");
    test(js, output);
  }

  public void testTwoTypesTwoVar() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.r = 0;",
        "Foo.prototype.g = 0;",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype.c = 0;",
        "Bar.prototype.r = 0;");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.a=0;",
        "Foo.prototype.b=0;",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype.b=0;",
        "Bar.prototype.a=0;");
    test(js, output);
  }

  public void testUnion() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "Foo.prototype.foodoo=0;",
        "Bar.prototype.bardoo=0;",
        "/** @type {Foo|Bar} */",
        "var U;",
        "U.joint;",
        "U.joint");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "Foo.prototype.b=0;",
        "Bar.prototype.b=0;",
        "/** @type {Foo|Bar} */",
        "var U;",
        "U.a;",
        "U.a");
    test(js, output);
  }

  public void testUnions() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "/** @constructor */ var Baz = function(){};",
        "/** @constructor */ var Bat = function(){};",
        "Foo.prototype.lone1=0;",
        "Bar.prototype.lone2=0;",
        "Baz.prototype.lone3=0;",
        "Bat.prototype.lone4=0;",
        "/** @type {Foo|Bar} */",
        "var U1;",
        "U1.j1;",
        "U1.j2;",
        "/** @type {Baz|Bar} */",
        "var U2;",
        "U2.j3;",
        "U2.j4;",
        "/** @type {Baz|Bat} */",
        "var U3;",
        "U3.j5;",
        "U3.j6");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "/** @constructor */ var Baz = function(){};",
        "/** @constructor */ var Bat = function(){};",
        "Foo.prototype.c=0;",
        "Bar.prototype.e=0;",
        "Baz.prototype.e=0;",
        "Bat.prototype.c=0;",
        "/** @type {Foo|Bar} */",
        "var U1;",
        "U1.a;",
        "U1.b;",
        "/** @type {Baz|Bar} */",
        "var U2;",
        "U2.c;",
        "U2.d;",
        "/** @type {Baz|Bat} */",
        "var U3;",
        "U3.a;",
        "U3.b");
    test(js, output);
  }

  public void testExtends() {
    String js = LINE_JOINER.join(
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
    String output = LINE_JOINER.join(
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

  public void testFunctionType() {
    String js = LINE_JOINER.join(
        "/** @constructor */ function Foo(){};",
        "/** @return {Bar} */",
        "Foo.prototype.fun = function() { return new Bar(); };",
        "/** @constructor */ function Bar(){};",
        "Bar.prototype.bazz;",
        "(new Foo).fun().bazz();");
    String output = LINE_JOINER.join(
        "/** @constructor */ function Foo(){};",
        "/** @return {Bar} */",
        "Foo.prototype.a = function() { return new Bar(); };",
        "/** @constructor */ function Bar(){};",
        "Bar.prototype.a;",
        "(new Foo).a().a();");
    test(js, output);
  }

  public void testPrototypePropertiesAsObjLitKeys1() {
    test("/** @constructor */ function Bar() {};" +
             "Bar.prototype = {2: function(){}, getA: function(){}};",
             "/** @constructor */ function Bar() {};" +
             "Bar.prototype = {2: function(){}, a: function(){}};");
  }

  public void testPrototypePropertiesAsObjLitKeys2() {
    testSame("/** @constructor */ function Bar() {};" +
             "Bar.prototype = {2: function(){}, 'getA': function(){}};");
  }

  public void testQuotedPrototypeProperty() {
    testSame("/** @constructor */ function Bar() {};" +
             "Bar.prototype['getA'] = function(){};" +
             "var bar = new Bar();" +
             "bar['getA']();");
  }

  public void testObjectDefineProperties() {
    String js =
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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

  public void testObjectDefinePropertiesQuoted() {
    String js =
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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

  public void testOverlappingOriginalAndGeneratedNames() {
    test(
        LINE_JOINER.join(
            "/** @constructor */ function Bar(){};",
            "Bar.prototype.b = function(){};",
            "Bar.prototype.a = function(){};",
            "var bar = new Bar();",
            "bar.b();"),
        LINE_JOINER.join(
            "/** @constructor */ function Bar(){};",
            "Bar.prototype.a = function(){};",
            "Bar.prototype.b = function(){};",
            "var bar = new Bar();",
            "bar.a();"));
  }

  public void testPropertyAddedToObject() {
    testSame("var foo = {}; foo.prop = '';");
  }

  public void testPropertyAddedToFunction() {
    test("var foo = function(){}; foo.prop = '';",
         "var foo = function(){}; foo.a = '';");
  }

  public void testPropertyOfObjectOfUnknownType() {
    testSame("var foo = x(); foo.prop = '';");
  }

  public void testPropertyOnParamOfUnknownType() {
    testSame(
        LINE_JOINER.join(
            "/** @constructor */ function Foo(){};",
             "Foo.prototype.prop = 0;",
             "function go(aFoo){",
             "  aFoo.prop = 1;",
             "}"));
  }

  public void testSetPropertyOfGlobalThis() {
    testSame("this.prop = 'bar'");
  }

  public void testReadPropertyOfGlobalThis() {
    testSame("Object.prototype.prop;", "f(this.prop);", null);
  }

  public void testSetQuotedPropertyOfThis() {
    testSame("this['prop'] = 'bar';");
  }

  public void testExternedPropertyName() {
    test(
        LINE_JOINER.join(
            "/** @constructor */ var Bar = function(){};",
            "/** @override */ Bar.prototype.toString = function(){};",
            "Bar.prototype.func = function(){};",
            "var bar = new Bar();",
            "bar.toString();"),
        LINE_JOINER.join(
            "/** @constructor */ var Bar = function(){};",
            "/** @override */ Bar.prototype.toString = function(){};",
            "Bar.prototype.a = function(){};",
            "var bar = new Bar();",
            "bar.toString();"));
  }

  public void testExternedPropertyNameDefinedByObjectLiteral() {
    testSame("/**@constructor*/function Bar(){};Bar.prototype.factory");
  }

  public void testStaticAndInstanceMethodWithSameName() {
    test("/** @constructor */function Bar(){}; Bar.getA = function(){}; " +
         "Bar.prototype.getA = function(){}; Bar.getA();" +
         "var bar = new Bar(); bar.getA();",
         "/** @constructor */function Bar(){}; Bar.a = function(){};" +
         "Bar.prototype.a = function(){}; Bar.a();" +
         "var bar = new Bar(); bar.a();");
  }

  public void testStaticAndInstanceProperties() {
    test("/** @constructor */function Bar(){};" +
         "Bar.getA = function(){}; " +
         "Bar.prototype.getB = function(){};",
         "/** @constructor */function Bar(){}; Bar.a = function(){};" +
         "Bar.prototype.a = function(){};");
  }

  public void testStaticAndSubInstanceProperties() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.x=0;",
        "/** @constructor \n @extends Foo */ var Bar = function(){};",
        "goog.inherits(Bar, Foo);",
        "Bar.y=0;",
        "Bar.prototype.z=0;\n");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.a=0;",
        "/** @constructor \n @extends Foo */ var Bar = function(){};",
        "goog.inherits(Bar, Foo);",
        "Bar.a=0;",
        "Bar.prototype.a=0;\n");
    test(js, output);
  }

  public void testStaticWithFunctions() {
    String js = LINE_JOINER.join(
      "/** @constructor */ var Foo = function() {};",
      "Foo.x = 0;",
      "/** @param {!Function} x */ function f(x) { x.y = 1 }",
      "f(Foo)");
    String output = LINE_JOINER.join(
      "/** @constructor */ var Foo = function() {};",
      "Foo.a = 0;",
      "/** @param {!Function} x */ function f(x) { x.y = 1 }",
      "f(Foo)");
    test(js, output);

    js = LINE_JOINER.join(
      "/** @constructor */ var Foo = function() {};",
      "Foo.x = 0;",
      "/** @param {!Function} x */ function f(x) { x.y = 1; x.x = 2;}",
      "f(Foo)");
    testSame(js);

    js = LINE_JOINER.join(
      "/** @constructor */ var Foo = function() {};",
      "Foo.x = 0;",
      "/** @constructor */ var Bar = function() {};",
      "Bar.y = 0;");

    output = LINE_JOINER.join(
      "/** @constructor */ var Foo = function() {};",
      "Foo.a = 0;",
      "/** @constructor */ var Bar = function() {};",
      "Bar.a = 0;");
    test(js, output);

  }

  public void testTypeMismatch() {
    testSame(EXTERNS,
        LINE_JOINER.join(
            "/** @constructor */var Foo = function(){};",
            "/** @constructor */var Bar = function(){};",
            "Foo.prototype.b = 0;",
            "/** @type {Foo} */",
            "var F = new Bar();"),
        TypeValidator.TYPE_MISMATCH_WARNING,
        LINE_JOINER.join(
             "initializing variable",
             "found   : Bar",
             "required: (Foo|null)"));
  }

  public void testRenamingMap() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype.z=0;",
        "Foo.prototype.z=0;",
        "Foo.prototype.x=0;",
        "Foo.prototype.y=0;");
    String output = LINE_JOINER.join(
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
    assertEquals(answerMap, lastPass.getRenamingMap());
  }

  public void testInline() {
    String js = LINE_JOINER.join(
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
    String output = LINE_JOINER.join(
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

  public void testImplementsAndExtends() {
    String js = LINE_JOINER.join(
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
    String output = LINE_JOINER.join(
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

  public void testImplementsAndExtends2() {
    String js = LINE_JOINER.join(
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
    String output = LINE_JOINER.join(
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

  public void testExtendsInterface() {
    String js = LINE_JOINER.join(
        "/** @interface */ function A() {}",
        "/** @interface \n @extends {A} */ function B() {}",
        "/** @param {A} x */ function f(x) { x.y = 3; }",
        "/** @param {B} x */ function g(x) { x.z = 3; }\n");
    String output = LINE_JOINER.join(
        "/** @interface */ function A(){}",
        "/** @interface \n @extends {A} */ function B(){}",
        "/** @param {A} x */ function f(x) { x.a = 3; }",
        "/** @param {B} x */ function g(x) { x.b = 3; }\n");
    test(js, output);
  }

  public void testFunctionSubType() {
    String js = LINE_JOINER.join(
        "Function.prototype.a = 1;",
        "function f() {}",
        "f.y = 2;\n");
    String output = LINE_JOINER.join(
        "Function.prototype.a = 1;",
        "function f() {}",
        "f.b = 2;\n");
    test(js, output);
  }

  public void testFunctionSubType2() {
    String js = LINE_JOINER.join(
        "Function.prototype.a = 1;",
        "/** @constructor */ function F() {}",
        "F.y = 2;\n");
    String output = LINE_JOINER.join(
        "Function.prototype.a = 1;",
        "/** @constructor */ function F() {}",
        "F.b = 2;\n");
    test(js, output);
  }

  public void testPredeclaredType() {
    String js = LINE_JOINER.join(
        "goog.addDependency('zzz.js', ['goog.Foo'], []);",
        "/** @constructor */ ",
        "function A() {",
        "  this.x = 3;",
        "}",
        "/** @param {goog.Foo} x */",
        "function f(x) { x.y = 4; }");
    String result = LINE_JOINER.join(
        "0;",
        "/** @constructor */ ",
        "function A() {",
        "  this.a = 3;",
        "}",
        "/** @param {goog.Foo} x */",
        "function f(x) { x.y = 4; }");
    test(js, result);
  }

  public void testBug14291280() {
    String js = LINE_JOINER.join(
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
    String result = LINE_JOINER.join(
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

  public void testAmbiguateWithAnAlias() {
    String js = LINE_JOINER.join(
        "/** @constructor */ function Foo() { this.abc = 5; }\n",
        "/** @const */ var alias = Foo;\n",
        "/** @constructor @extends alias */\n",
        "function Bar() {\n",
        "  this.xyz = 7;\n",
        "}");
    String result = LINE_JOINER.join(
        "/** @constructor */ function Foo() { this.a = 5; }\n",
        "/** @const */ var alias = Foo;\n",
        "/** @constructor @extends alias */\n",
        "function Bar() {\n",
        "  this.b = 7;\n",
        "}");
    test(js, result);
  }

  public void testAmbiguateWithAliases() {
    String js = LINE_JOINER.join(
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
    String result = LINE_JOINER.join(
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
  public void testAmbiguateWithStructuralInterfaces() {
    String js = LINE_JOINER.join(
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
  public void testUnrelatedObjectLiterals() {
    testSame(LINE_JOINER.join(
        "/** @constructor */ function Foo() {}",
        "/** @constructor */ function Bar() {}",
        "var lit1 = {a: new Foo()};",
        "var lit2 = {b: new Bar()};"));
  }

  public void testObjectLitTwoVar() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {z:0, z:1, x:0};");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {a:0, a:1, b:0};");
    test(js, output);
  }

  public void testObjectLitTwoIndependentVar() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {b: 0};",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype = {c: 0};");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {a: 0};",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype = {a: 0};");
    test(js, output);
  }

  public void testObjectLitTwoTypesTwoVar() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {r: 0, g: 0};",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype = {c: 0, r: 0};");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {a: 0, b: 0};",
        "/** @constructor */ var Bar = function(){};",
        "Bar.prototype = {b: 0, a: 0};");
    test(js, output);
  }

  public void testObjectLitUnion() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "Foo.prototype = {foodoo: 0};",
        "Bar.prototype = {bardoo: 0};",
        "/** @type {Foo|Bar} */",
        "var U;",
        "U.joint;");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "Foo.prototype = {a: 0};",
        "Bar.prototype = {a: 0};",
        "/** @type {Foo|Bar} */",
        "var U;",
        "U.b;");
    test(js, output);
  }

  public void testObjectLitUnions() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "/** @constructor */ var Baz = function(){};",
        "/** @constructor */ var Bat = function(){};",
        "Foo.prototype = {lone1: 0};",
        "Bar.prototype = {lone2: 0};",
        "Baz.prototype = {lone3: 0};",
        "Bat.prototype = {lone4: 0};",
        "/** @type {Foo|Bar} */",
        "var U1;",
        "U1.j1;",
        "U1.j2;",
        "/** @type {Baz|Bar} */",
        "var U2;",
        "U2.j3;",
        "U2.j4;",
        "/** @type {Baz|Bat} */",
        "var U3;",
        "U3.j5;",
        "U3.j6");
    String output = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "/** @constructor */ var Bar = function(){};",
        "/** @constructor */ var Baz = function(){};",
        "/** @constructor */ var Bat = function(){};",
        "Foo.prototype = {c: 0};",
        "Bar.prototype = {e: 0};",
        "Baz.prototype = {e: 0};",
        "Bat.prototype = {c: 0};",
        "/** @type {Foo|Bar} */",
        "var U1;",
        "U1.a;",
        "U1.b;",
        "/** @type {Baz|Bar} */",
        "var U2;",
        "U2.c;",
        "U2.d;",
        "/** @type {Baz|Bat} */",
        "var U3;",
        "U3.a;",
        "U3.b");
    test(js, output);
  }

  public void testObjectLitExtends() {
    String js = LINE_JOINER.join(
        "/** @constructor */ var Foo = function(){};",
        "Foo.prototype = {x: 0};",
        "/** @constructor \n @extends Foo */ var Bar = function(){};",
        "goog.inherits(Bar, Foo);",
        "Bar.prototype = {y: 0, z: 0};",
        "/** @constructor */ var Baz = function(){};",
        "Baz.prototype = {l: 0, m: 0, n: 0};",
        "(new Baz).m\n");
    String output = LINE_JOINER.join(
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

  public void testObjectLitFunctionType() {
    String js = LINE_JOINER.join(
        "/** @constructor */ function Foo(){};",
        "Foo.prototype = { /** @return {Bar} */ fun: function() { return new Bar(); } };",
        "/** @constructor */ function Bar(){};",
        "Bar.prototype.bazz;",
        "(new Foo).fun().bazz();");
    String output = LINE_JOINER.join(
        "/** @constructor */ function Foo(){};",
        "Foo.prototype = { /** @return {Bar} */ a: function() { return new Bar(); } };",
        "/** @constructor */ function Bar(){};",
        "Bar.prototype.a;",
        "(new Foo).a().a();");
    test(js, output);
  }

  public void testObjectLitOverlappingOriginalAndGeneratedNames() {
    test(
        LINE_JOINER.join(
            "/** @constructor */ function Bar(){};",
            "Bar.prototype = {b: function(){}, a: function(){}};",
            "var bar = new Bar();",
            "bar.b();"),
        LINE_JOINER.join(
            "/** @constructor */ function Bar(){};",
            "Bar.prototype = {a: function(){}, b: function(){}};",
            "var bar = new Bar();",
            "bar.a();"));
  }
}
