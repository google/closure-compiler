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

  private static final String EXTERNS =
      "Function.prototype.call=function(){};" +
      "Function.prototype.inherits=function(){};" +
      "prop.toString;" +
      "var google = { gears: { factory: {}, workerPool: {} } };";

  public AmbiguatePropertiesTest() {
    super(EXTERNS);
    enableNormalize();
    enableTypeCheck();
    enableClosurePass();
    enableGatherExternProperties();
    compareJsDoc = false;
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        lastPass = AmbiguateProperties.makePassForTesting(
            compiler, new char[]{'$'});
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
         "var Foo = function(){};Foo.prototype.a = 0;");
  }

  public void testOneVar2() {
    testSame("/** @constructor */ var Foo = function(){};" +
             "Foo.prototype = {b: 0};");
  }

  public void testOneVar3() {
    testSame("/** @constructor */ var Foo = function(){};" +
             "Foo.prototype = {get b() {return 0}};");
  }

  public void testOneVar4() {
    testSame("/** @constructor */ var Foo = function(){};" +
             "Foo.prototype = {set b(a) {}};");
  }

  public void testTwoVar1() {
    String js = ""
        + "/** @constructor */ var Foo = function(){};\n"
        + "Foo.prototype.z=0;\n"
        + "Foo.prototype.z=0;\n"
        + "Foo.prototype.x=0;";
    String output = ""
        + "var Foo = function(){};\n"
        + "Foo.prototype.a=0;\n"
        + "Foo.prototype.a=0;\n"
        + "Foo.prototype.b=0;";
    test(js, output);
  }

  public void testTwoVar2() {
    String js = ""
        + "/** @constructor */ var Foo = function(){};\n"
        + "Foo.prototype={z:0, z:1, x:0};\n";
    // TODO(johnlenz): It would be nice to handle this type of declaration.
    testSame(js);
  }

  public void testTwoIndependentVar() {
    String js = ""
        + "/** @constructor */ var Foo = function(){};\n"
        + "Foo.prototype.b = 0;\n"
        + "/** @constructor */ var Bar = function(){};\n"
        + "Bar.prototype.c = 0;";
    String output = ""
        + "var Foo = function(){};"
        + "Foo.prototype.a=0;"
        + "var Bar = function(){};"
        + "Bar.prototype.a=0;";
    test(js, output);
  }

  public void testTwoTypesTwoVar() {
    String js = ""
        + "/** @constructor */ var Foo = function(){};\n"
        + "Foo.prototype.r = 0;\n"
        + "Foo.prototype.g = 0;\n"
        + "/** @constructor */ var Bar = function(){};\n"
        + "Bar.prototype.c = 0;"
        + "Bar.prototype.r = 0;";
    String output = ""
        + "var Foo = function(){};"
        + "Foo.prototype.a=0;"
        + "Foo.prototype.b=0;"
        + "var Bar = function(){};"
        + "Bar.prototype.b=0;"
        + "Bar.prototype.a=0;";
    test(js, output);
  }

  public void testUnion() {
    String js = ""
        + "/** @constructor */ var Foo = function(){};\n"
        + "/** @constructor */ var Bar = function(){};\n"
        + "Foo.prototype.foodoo=0;\n"
        + "Bar.prototype.bardoo=0;\n"
        + "/** @type {Foo|Bar} */\n"
        + "var U;\n"
        + "U.joint;"
        + "U.joint";
    String output = ""
        + "var Foo = function(){};\n"
        + "var Bar = function(){};\n"
        + "Foo.prototype.b=0;\n"
        + "Bar.prototype.b=0;\n"
        + "var U;\n"
        + "U.a;"
        + "U.a";
    test(js, output);
  }

  public void testUnions() {
    String js = ""
        + "/** @constructor */ var Foo = function(){};\n"
        + "/** @constructor */ var Bar = function(){};\n"
        + "/** @constructor */ var Baz = function(){};\n"
        + "/** @constructor */ var Bat = function(){};\n"
        + "Foo.prototype.lone1=0;\n"
        + "Bar.prototype.lone2=0;\n"
        + "Baz.prototype.lone3=0;\n"
        + "Bat.prototype.lone4=0;\n"
        + "/** @type {Foo|Bar} */\n"
        + "var U1;\n"
        + "U1.j1;"
        + "U1.j2;"
        + "/** @type {Baz|Bar} */\n"
        + "var U2;\n"
        + "U2.j3;"
        + "U2.j4;"
        + "/** @type {Baz|Bat} */\n"
        + "var U3;"
        + "U3.j5;"
        + "U3.j6";
    String output = ""
        + "var Foo = function(){};\n"
        + "var Bar = function(){};\n"
        + "var Baz = function(){};\n"
        + "var Bat = function(){};\n"
        + "Foo.prototype.c=0;\n"
        + "Bar.prototype.e=0;\n"
        + "Baz.prototype.e=0;\n"
        + "Bat.prototype.c=0;\n"
        + "var U1;\n"
        + "U1.a;"
        + "U1.b;"
        + "var U2;\n"
        + "U2.c;"
        + "U2.d;"
        + "var U3;"
        + "U3.a;"
        + "U3.b";
    test(js, output);
  }

  public void testExtends() {
    String js = ""
        + "/** @constructor */ var Foo = function(){};\n"
        + "Foo.prototype.x=0;\n"
        + "/** @constructor \n @extends Foo */ var Bar = function(){};\n"
        + "goog.inherits(Bar, Foo);\n"
        + "Bar.prototype.y=0;\n"
        + "Bar.prototype.z=0;\n"
        + "/** @constructor */ var Baz = function(){};\n"
        + "Baz.prototype.l=0;\n"
        + "Baz.prototype.m=0;\n"
        + "Baz.prototype.n=0;\n"
        + "(new Baz).m\n";
    String output = ""
        + "/** @constructor */ var Foo = function(){};\n"
        + "Foo.prototype.a=0;\n"
        + "/** @constructor \n @extends Foo */ var Bar = function(){};\n"
        + "goog.inherits(Bar, Foo);\n"
        + "Bar.prototype.b=0;\n"
        + "Bar.prototype.c=0;\n"
        + "/** @constructor */ var Baz = function(){};\n"
        + "Baz.prototype.b=0;\n"
        + "Baz.prototype.a=0;\n"
        + "Baz.prototype.c=0;\n"
        + "(new Baz).a\n";
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
    String js = ""
        + "/** @constructor */ function Foo(){};\n"
        + "/** @return {Bar} */\n"
        + "Foo.prototype.fun = function() { return new Bar(); };\n"
        + "/** @constructor */ function Bar(){};\n"
        + "Bar.prototype.bazz;\n"
        + "(new Foo).fun().bazz();";
    String output = ""
        + "function Foo(){};\n"
        + "Foo.prototype.a = function() { return new Bar(); };\n"
        + "function Bar(){};\n"
        + "Bar.prototype.a;\n"
        + "(new Foo).a().a();";
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

  public void testOverlappingOriginalAndGeneratedNames() {
    test("/** @constructor */ function Bar(){};"
         + "Bar.prototype.b = function(){};"
         + "Bar.prototype.a = function(){};"
         + "var bar = new Bar();"
         + "bar.b();",
         "function Bar(){};"
         + "Bar.prototype.a = function(){};"
         + "Bar.prototype.b = function(){};"
         + "var bar = new Bar();"
         + "bar.a();");
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
    testSame("/** @constructor */ function Foo(){};\n"
             + "Foo.prototype.prop = 0;"
             + "function go(aFoo){\n"
             + "  aFoo.prop = 1;"
             + "}");
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
    test("/** @constructor */ var Bar = function(){};"
         + "/** @override */ Bar.prototype.toString = function(){};"
         + "Bar.prototype.func = function(){};"
         + "var bar = new Bar();"
         + "bar.toString();",
         "var Bar = function(){};"
         + "Bar.prototype.toString = function(){};"
         + "Bar.prototype.a = function(){};"
         + "var bar = new Bar();"
         + "bar.toString();");
  }

  public void testExternedPropertyNameDefinedByObjectLiteral() {
    testSame("/**@constructor*/function Bar(){};Bar.prototype.factory");
  }

  public void testStaticAndInstanceMethodWithSameName() {
    test("/** @constructor */function Bar(){}; Bar.getA = function(){}; " +
         "Bar.prototype.getA = function(){}; Bar.getA();" +
         "var bar = new Bar(); bar.getA();",
         "function Bar(){}; Bar.a = function(){};" +
         "Bar.prototype.a = function(){}; Bar.a();" +
         "var bar = new Bar(); bar.a();");
  }

  public void testStaticAndInstanceProperties() {
    test("/** @constructor */function Bar(){};" +
         "Bar.getA = function(){}; " +
         "Bar.prototype.getB = function(){};",
         "function Bar(){}; Bar.a = function(){};" +
         "Bar.prototype.a = function(){};");
  }

  public void testStaticAndSubInstanceProperties() {
    String js = ""
        + "/** @constructor */ var Foo = function(){};\n"
        + "Foo.x=0;\n"
        + "/** @constructor \n @extends Foo */ var Bar = function(){};\n"
        + "goog.inherits(Bar, Foo);\n"
        + "Bar.y=0;\n"
        + "Bar.prototype.z=0;\n";
    String output = ""
        + "/** @constructor */ var Foo = function(){};\n"
        + "Foo.a=0;\n"
        + "/** @constructor \n @extends Foo */ var Bar = function(){};\n"
        + "goog.inherits(Bar, Foo);\n"
        + "Bar.a=0;\n"
        + "Bar.prototype.a=0;\n";
    test(js, output);
  }

  public void testStaticWithFunctions() {
    String js = ""
      + "/** @constructor */ var Foo = function() {};\n"
      + "Foo.x = 0;"
      + "/** @param {!Function} x */ function f(x) { x.y = 1 }"
      + "f(Foo)";
    String output = ""
      + "/** @constructor */ var Foo = function() {};\n"
      + "Foo.a = 0;"
      + "/** @param {!Function} x */ function f(x) { x.y = 1 }"
      + "f(Foo)";
    test(js, output);

    js = ""
      + "/** @constructor */ var Foo = function() {};\n"
      + "Foo.x = 0;"
      + "/** @param {!Function} x */ function f(x) { x.y = 1; x.x = 2;}"
      + "f(Foo)";
    test(js, js);

    js = ""
      + "/** @constructor */ var Foo = function() {};\n"
      + "Foo.x = 0;"
      + "/** @constructor */ var Bar = function() {};\n"
      + "Bar.y = 0;";

    output = ""
      + "/** @constructor */ var Foo = function() {};\n"
      + "Foo.a = 0;"
      + "/** @constructor */ var Bar = function() {};\n"
      + "Bar.a = 0;";
    test(js, output);

  }

  public void testTypeMismatch() {
    testSame(EXTERNS, "/** @constructor */var Foo = function(){};\n"
             + "/** @constructor */var Bar = function(){};\n"
             + "Foo.prototype.b = 0;\n"
             + "/** @type {Foo} */\n"
             + "var F = new Bar();",
             TypeValidator.TYPE_MISMATCH_WARNING,
             "initializing variable\n"
             + "found   : Bar\n"
             + "required: (Foo|null)");
  }

  public void testRenamingMap() {
    String js = ""
        + "/** @constructor */ var Foo = function(){};\n"
        + "Foo.prototype.z=0;\n"
        + "Foo.prototype.z=0;\n"
        + "Foo.prototype.x=0;\n"
        + "Foo.prototype.y=0;";
    String output = ""
        + "var Foo = function(){};\n"
        + "Foo.prototype.a=0;\n"
        + "Foo.prototype.a=0;\n"
        + "Foo.prototype.b=0;\n"
        + "Foo.prototype.c=0;";
    test(js, output);

    Map<String, String> answerMap = new HashMap<>();
    answerMap.put("x", "b");
    answerMap.put("y", "c");
    answerMap.put("z", "a");
    assertEquals(answerMap, lastPass.getRenamingMap());
  }

  public void testInline() {
    String js = ""
        + "/** @interface */ function Foo(){}\n"
        + "Foo.prototype.x = function(){};\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @implements {Foo}\n"
        + " */\n"
        + "function Bar(){}\n"
        + "Bar.prototype.y;\n"
        + "/** @inheritDoc */\n"
        + "Bar.prototype.x = function() { return this.y; };\n"
        + "Bar.prototype.z = function() {};\n"
        // Simulates inline getters.
        + "/** @type {Foo} */ (new Bar).y;";
    String output = ""
        + "function Foo(){}\n"
        + "Foo.prototype.b = function(){};\n"
        + "function Bar(){}\n"
        + "Bar.prototype.a;\n"
        + "Bar.prototype.b = function() { return this.a; };\n"
        + "Bar.prototype.c = function() {};\n"
        // Simulates inline getters.
        + "(new Bar).a;";
    test(js, output);
  }

  public void testImplementsAndExtends() {
    String js = ""
        + "/** @interface */ function Foo() {}\n"
        + "/**\n"
        + " * @constructor\n"
        + " */\n"
        + "function Bar(){}\n"
        + "Bar.prototype.y = function() { return 3; };\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {Bar}\n"
        + " * @implements {Foo}\n"
        + " */\n"
        + "function SubBar(){ }\n"
        + "/** @param {Foo} x */ function f(x) { x.z = 3; }\n"
        + "/** @param {SubBar} x */ function g(x) { x.z = 3; }";
    String output = ""
        + "function Foo(){}\n"
        + "function Bar(){}\n"
        + "Bar.prototype.b = function() { return 3; };\n"
        + "function SubBar(){}\n"
        + "function f(x) { x.a = 3; }\n"
        + "function g(x) { x.a = 3; }";
    test(js, output);
  }

  public void testImplementsAndExtends2() {
    String js = ""
        + "/** @interface */ function A() {}\n"
        + "/**\n"
        + " * @constructor\n"
        + " */\n"
        + "function C1(){}\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {C1}\n"
        + " * @implements {A}\n"
        + " */\n"
        + "function C2(){}\n"
        + "/** @param {C1} x */ function f(x) { x.y = 3; }\n"
        + "/** @param {A} x */ function g(x) { x.z = 3; }\n";
    String output = ""
        + "function A(){}\n"
        + "function C1(){}\n"
        + "function C2(){}\n"
        + "function f(x) { x.a = 3; }\n"
        + "function g(x) { x.b = 3; }\n";
    test(js, output);
  }

  public void testExtendsInterface() {
    String js = ""
        + "/** @interface */ function A() {}\n"
        + "/** @interface \n @extends {A} */ function B() {}\n"
        + "/** @param {A} x */ function f(x) { x.y = 3; }\n"
        + "/** @param {B} x */ function g(x) { x.z = 3; }\n";
    String output = ""
        + "function A(){}\n"
        + "function B(){}\n"
        + "function f(x) { x.a = 3; }\n"
        + "function g(x) { x.b = 3; }\n";
    test(js, output);
  }

  public void testFunctionSubType() {
    String js = ""
        + "Function.prototype.a = 1;\n"
        + "function f() {}\n"
        + "f.y = 2;\n";
    String output = ""
        + "Function.prototype.a = 1;\n"
        + "function f() {}\n"
        + "f.b = 2;\n";
    test(js, output);
  }

  public void testFunctionSubType2() {
    String js = ""
        + "Function.prototype.a = 1;\n"
        + "/** @constructor */ function F() {}\n"
        + "F.y = 2;\n";
    String output = ""
        + "Function.prototype.a = 1;\n"
        + "function F() {}\n"
        + "F.b = 2;\n";
    test(js, output);
  }

  public void testPredeclaredType() {
    String js =
        "goog.addDependency('zzz.js', ['goog.Foo'], []);" +
        "/** @constructor */ " +
        "function A() {" +
        "  this.x = 3;" +
        "}" +
        "/** @param {goog.Foo} x */" +
        "function f(x) { x.y = 4; }";
    String result =
        "0;" +
        "/** @constructor */ " +
        "function A() {" +
        "  this.a = 3;" +
        "}" +
        "/** @param {goog.Foo} x */" +
        "function f(x) { x.y = 4; }";
    test(js, result);
  }

  public void testBug14291280() {
    String js =
        "/** @constructor \n @template T */\n" +
        "function C() {\n" +
        "  this.aa = 1;\n" +
        "}\n" +
        "/** @return {C.<T>} */\n" +
        "C.prototype.method = function() {\n" +
        "  return this;\n" +
        "}\n" +
        "/** @type {C.<string>} */\n" +
        "var x = new C().method();\n" +
        "x.bb = 2;\n" +
        "x.cc = 3;";
    String result =
        "function C() {" +
        "  this.b = 1;" +
        "}" +
        "C.prototype.a = function() {" +
        "  return this;" +
        "};" +
        "var x = (new C).a();" +
        "x.c = 2;" +
        "x.d = 3;";
    test(js, result);
  }

  public void testAmbiguateWithAnAlias() {
    String js =
        "/** @constructor */ function Foo() { this.abc = 5; }\n" +
        "/** @const */ var alias = Foo;\n" +
        "/** @constructor @extends alias */\n" +
        "function Bar() {\n" +
        "  this.xyz = 7;\n" +
        "}";
    String result =
        "/** @constructor */ function Foo() { this.a = 5; }\n" +
        "/** @const */ var alias = Foo;\n" +
        "/** @constructor @extends alias */\n" +
        "function Bar() {\n" +
        "  this.b = 7;\n" +
        "}";
    test(js, result);
  }

  public void testAmbiguateWithAliases() {
    String js =
        "/** @constructor */ function Foo() { this.abc = 5; }\n" +
        "/** @const */ var alias = Foo;\n" +
        "/** @constructor @extends alias */\n" +
        "function Bar() {\n" +
        "  this.def = 7;\n" +
        "}\n" +
        "/** @constructor @extends alias */\n" +
        "function Baz() {\n" +
        "  this.xyz = 8;\n" +
        "}";
    String result =
        "/** @constructor */ function Foo() { this.a = 5; }\n" +
        "/** @const */ var alias = Foo;\n" +
        "/** @constructor @extends alias */\n" +
        "function Bar() {\n" +
        "  this.b = 7;\n" +
        "}\n" +
        "/** @constructor @extends alias */\n" +
        "function Baz() {\n" +
        "  this.b = 8;\n" +
        "}";
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
}
