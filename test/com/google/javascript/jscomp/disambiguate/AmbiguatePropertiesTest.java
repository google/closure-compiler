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

package com.google.javascript.jscomp.disambiguate;

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit test for {@link AmbiguateProperties} Compiler pass. */
@RunWith(JUnit4.class)
public final class AmbiguatePropertiesTest extends CompilerTestCase {
  private AmbiguateProperties lastPass;

  private static final String EXTERNS =
      lines(
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

  @Before
  public void customSetUp() throws Exception {
    enableTypeCheck();
    replaceTypesWithColors();
    enableNormalize();
    enableGatherExternProperties();
    disableCompareJsDoc(); // removeTypes also deletes JSDocInfo
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        lastPass =
            AmbiguateProperties.makePassForTesting(
                compiler, new char[] {'$'}, new char[] {'$'}, getGatheredExternProperties());
        lastPass.process(externs, root);
      }
    };
  }

  @Test
  public void testOneVar1() {
    test(
        "/** @constructor */ var Foo = function(){};Foo.prototype.b = 0;",
        "/** @constructor */ var Foo = function(){};Foo.prototype.a = 0;");
  }

  @Test
  public void testOneVar2() {
    test(
        lines("/** @constructor */ var Foo = function(){};", "Foo.prototype = {b: 0};"),
        lines("/** @constructor */ var Foo = function(){};", "Foo.prototype = {a: 0};"));
  }

  @Test
  public void testOneVar3() {
    test(
        lines(
            "/** @constructor */ var Foo = function(){};", "Foo.prototype = {get b() {return 0}};"),
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {get a() {return 0}};"));
  }

  @Test
  public void testOneVar4() {
    test(
        lines("/** @constructor */ var Foo = function(){};", "Foo.prototype = {set b(a) {}};"),
        lines("/** @constructor */ var Foo = function(){};", "Foo.prototype = {set a(a) {}};"));
  }

  @Test
  public void testTwoVar1() {
    String js =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype.z=0;",
            "Foo.prototype.z=0;",
            "Foo.prototype.x=0;");
    String output =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype.a=0;",
            "Foo.prototype.a=0;",
            "Foo.prototype.b=0;");
    test(js, output);
  }

  @Test
  public void testTwoVar2() {
    String js =
        lines("/** @constructor */ var Foo = function(){};", "Foo.prototype = {z:0, z:1, x:0};");
    String output =
        lines("/** @constructor */ var Foo = function(){};", "Foo.prototype = {a:0, a:1, b:0};");
    test(js, output);
  }

  @Test
  public void testTwoIndependentVar() {
    String js =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype.b = 0;",
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype.c = 0;");
    String output =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype.a=0;",
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype.a=0;");
    test(js, output);
  }

  @Test
  public void testTwoTypesTwoVar() {
    String js =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype.r = 0;",
            "Foo.prototype.g = 0;",
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype.c = 0;",
            "Bar.prototype.r = 0;");
    String output =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype.a=0;",
            "Foo.prototype.b=0;",
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype.b=0;",
            "Bar.prototype.a=0;");
    test(js, output);
  }

  @Test
  public void testUnion_withUnrelatedPropertyAccess() {
    String js =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "/** @constructor */ var Bar = function(){};",
            "Foo.prototype.foodoo=0;",
            "Bar.prototype.bardoo=0;",
            // variable exists that could be a Foo or a Bar
            "/** @type {Foo|Bar} */",
            "var U = any();",
            // We don't actually access either foodoo or bardoo on it,
            // though, so it's OK if they end up having the same name
            "U.joint");
    String output =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "/** @constructor */ var Bar = function(){};",
            "Foo.prototype.a=0;",
            "Bar.prototype.a=0;",
            "/** @type {Foo|Bar} */",
            "var U = any();",
            "U.b");
    test(js, output);
  }

  @Test
  public void testUnion_withRelatedPropertyAccess() {
    String js =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "/** @constructor */ var Bar = function(){};",
            "Foo.prototype.foodoo=0;",
            "Bar.prototype.bardoo=0;",
            // variable exists that could be a Foo or a Bar
            "/** @type {Foo|Bar} */",
            "var U = any();",
            // both foodoo and bardoo are accessed through that variable,
            // so they must have different names.
            "U.foodoo;",
            "U.bardoo");
    String output =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "/** @constructor */ var Bar = function(){};",
            "Foo.prototype.b=0;",
            "Bar.prototype.a=0;",
            "/** @type {Foo|Bar} */",
            "var U = any();",
            "U.b;",
            "U.a");
    test(js, output);
  }

  @Test
  public void testUnions() {
    String js =
        lines(
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
    String output =
        lines(
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
    this.enableClosurePass();

    String js =
        lines(
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
    String output =
        lines(
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
    String js =
        lines(
            "/** @constructor */ function Foo(){};",
            "/** @return {Bar} */",
            "Foo.prototype.fun = function() { return new Bar(); };",
            "/** @constructor */ function Bar(){};",
            "Bar.prototype.bazz;",
            "(new Foo).fun().bazz();");
    String output =
        lines(
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
    test(
        "/** @constructor */ function Bar() {};"
            + "Bar.prototype = {2: function(){}, getA: function(){}};",
        "/** @constructor */ function Bar() {};"
            + "Bar.prototype = {2: function(){}, a: function(){}};");
  }

  @Test
  public void testPrototypePropertiesAsObjLitKeys2() {
    testSame(
        "/** @constructor */ function Bar() {};"
            + "Bar.prototype = {2: function(){}, 'getA': function(){}};");
  }

  @Test
  public void testQuotedPrototypeProperty() {
    testSame(
        "/** @constructor */ function Bar() {};"
            + "Bar.prototype['getA'] = function(){};"
            + "var bar = new Bar();"
            + "bar['getA']();");
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
  public void testObjectDefinePropertiesComputed() {
    String js =
        lines(
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype.bar = 0;",
            "/** @struct @constructor */ var Foo = function() {",
            "  this.bar_ = 'bar';",
            "};",
            "/** @type {?} */ Foo.prototype['bar'];",
            "Object.defineProperties(Foo.prototype, {",
            "  ['a']: {",
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
            "  ['a']: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {Foo} */ get: function() { return this.b;},",
            "    /** @this {Foo} */ set: function(value) { this.b = value; }",
            "  }",
            "});");

    test(js, result);
  }

  @Test
  public void testObjectDefinePropertiesMemberFn() {
    // NOTE: this is very odd code, and people should not be writing it anyway. We really just
    // don't want a crash.
    String js =
        lines(
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype.bar = 0;",
            "/** @struct @constructor */ var Foo = function() {",
            "  this.bar_ = 'bar';",
            "};",
            "/** @type {?} */ Foo.prototype['bar'];",
            "Object.defineProperties(Foo.prototype, {",
            "  a() {}",
            "});");

    String result =
        lines(
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype.a = 0;",
            "/** @struct @constructor */ var Foo = function() {",
            "  this.b = 'bar';",
            "};",
            "/** @type {?} */ Foo.prototype['bar'];",
            "Object.defineProperties(Foo.prototype, {",
            "  a() {}",
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
    testSame("var foo = function(){}; foo.prop = '';");
  }

  @Test
  public void testPropertyAddedToFunctionIndirectly() {
    testSame(
        lines(
            "var foo = function(){}; foo.prop = ''; foo.baz = '';",
            "function f(/** function(): void */ fun) { fun.bar = ''; fun.baz = ''; }"));
  }

  @Test
  public void testConstructorTreatedAsSubtypeOfFunction() {
    String js =
        lines(
            "Function.prototype.a = 1;", //
            "/** @constructor */",
            "function F() {}",
            "F.y = 2;");

    String output =
        lines(
            "Function.prototype.a = 1;", //
            // F is a subtype of Function, so we can't ambiguate "F.b" to "F.a".
            "/** @constructor */",
            "function F() {}",
            "F.b = 2;");
    test(js, output);
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
    test(
        "/** @constructor */function Bar(){}; Bar.getA = function(){}; "
            + "Bar.prototype.getA = function(){}; Bar.getA();"
            + "var bar = new Bar(); bar.getA();",
        "/** @constructor */function Bar(){}; Bar.a = function(){};"
            + "Bar.prototype.a = function(){}; Bar.a();"
            + "var bar = new Bar(); bar.a();");
  }

  @Test
  public void testStaticAndInstanceProperties() {
    test(
        "/** @constructor */function Bar(){};"
            + "Bar.getA = function(){}; "
            + "Bar.prototype.getB = function(){};",
        "/** @constructor */function Bar(){}; Bar.a = function(){};"
            + "Bar.prototype.a = function(){};");
  }

  @Test
  public void testStaticAndSubInstanceProperties() {
    this.enableClosurePass();

    String js =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.x=0;",
            "/** @constructor \n @extends Foo */ var Bar = function(){};",
            "goog.inherits(Bar, Foo);",
            "Bar.y=0;",
            "Bar.prototype.z=0;\n");
    String output =
        lines(
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
    String js =
        lines(
            "/** @constructor */ var Foo = function() {};",
            "Foo.x = 0;",
            "/** @constructor */ var Bar = function() {};",
            "Bar.y = 0;");

    String output =
        lines(
            "/** @constructor */ var Foo = function() {};",
            "Foo.a = 0;",
            "/** @constructor */ var Bar = function() {};",
            "Bar.a = 0;");
    test(js, output);
  }

  @Test
  public void testClassWithStaticsPassedToUnrelatedFunction() {
    String js =
        lines(
            "/** @constructor */ var Foo = function() {};",
            "Foo.x = 0;",
            "/** @param {!Function} x */ function f(x) { x.y = 1; x.z = 2; }",
            "f(Foo)");
    String output =
        lines(
            "/** @constructor */ var Foo = function() {};",
            "Foo.a = 0;",
            "/** @param {!Function} x */ function f(x) { x.y = 1; x.z = 2; }",
            "f(Foo)");
    test(js, output);
  }

  @Test
  public void testClassWithStaticsPassedToRelatedFunction() {
    String js =
        lines(
            "/** @constructor */ var Foo = function() {};",
            "Foo.x = 0;",
            "/** @param {!Function} x */ function f(x) { x.y = 1; x.x = 2;}",
            "f(Foo)");
    testSame(js);
  }

  @Test
  public void testFunctionInstanceProtoype_isInvalidating() {
    String js =
        lines(
            "class Foo {",
            "  bar() { }",
            "}",
            "",
            "function addPrototypeProp(/** !Function */ ctor) {",
            "  ctor.prototype.kip = 0;",
            "}");
    String output =
        lines(
            "class Foo {",
            "  a() { }",
            "}",
            "",
            "function addPrototypeProp(/** !Function */ ctor) {",
            "  ctor.prototype.kip = 0;",
            "}");
    test(js, output);
  }

  @Test
  public void testTypeMismatch() {
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);
    testSame(
        lines(
            "/** @constructor */var Foo = function(){};",
            "/** @constructor */var Bar = function(){};",
            "Foo.prototype.b = 0;",
            "/** @type {Foo} */",
            "var F = new Bar();"));
  }

  @Test
  public void testRenamingMap() {
    String js =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype.z=0;",
            "Foo.prototype.z=0;",
            "Foo.prototype.x=0;",
            "Foo.prototype.y=0;");
    String output =
        lines(
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
    String js =
        lines(
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
    String output =
        lines(
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
    String js =
        lines(
            "/** @interface */ function Foo() {}",
            "/** @constructor */ function Bar(){}",
            "Bar.prototype.y = function() { return 3; };",
            "/**",
            " * @constructor",
            " * @extends {Bar}",
            " * @implements {Foo}",
            " */",
            "function SubBar(){ }",
            "/** @param {Foo} x1 */ function f(x1) { x1.z = 3; }",
            "/** @param {SubBar} x2 */ function g(x2) { x2.z = 3; }");
    String output =
        lines(
            "/** @interface */ function Foo(){}",
            "/** @constructor */ function Bar(){}",
            "Bar.prototype.b = function() { return 3; };",
            "/**",
            " * @constructor",
            " * @extends {Bar}",
            " * @implements {Foo}",
            " */",
            "function SubBar(){}",
            "/** @param {Foo} x1 */ function f(x1) { x1.a = 3; }",
            "/** @param {SubBar} x2 */ function g(x2) { x2.a = 3; }");
    test(js, output);
  }

  @Test
  public void testImplementsAndExtends_respectsUndeclaredProperties() {
    String js =
        lines(
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
            "/** @param {C1} x1 */ function f(x1) { x1.y = 3; }",
            "/** @param {A} x2 */ function g(x2) { x2.z = 3; }");
    String output =
        lines(
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
            "/** @param {C1} x1 */ function f(x1) { x1.a = 3; }",
            "/** @param {A} x2 */ function g(x2) { x2.b = 3; }");
    test(js, output);
  }

  @Test
  public void testImplementsAndExtendsEs6Class_respectsUndeclaredProperties() {
    String js =
        lines(
            "/** @interface */",
            "class A {",
            "  constructor() {",
            // Optional property; C2 does not need to declare it implemented.
            "    /** @type {number|undefined} */",
            "    this.y;",
            "  }",
            "}",
            "class C1 {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.z;",
            "  }",
            "}",
            "/** @implements {A} */",
            "class C2 extends C1 {}",
            "/** @param {A} x1 */ function f(x1) { x1.y = 3; }",
            "/** @param {C1} x2 */ function g(x2) { x2.z = 3; }");
    String output =
        lines(
            "/** @interface */",
            "class A {",
            "  constructor() {",
            "    /** @type {number|undefined} */",
            "    this.a;",
            "  }",
            "}",
            "class C1 {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.b;",
            "  }",
            "}",
            "/** @implements {A} */",
            "class C2 extends C1 {}",
            "/** @param {A} x1 */ function f(x1) { x1.a = 3; }",
            "/** @param {C1} x2 */ function g(x2) { x2.b = 3; }");
    test(js, output);
  }

  @Test
  public void testExtendsInterface() {
    String js =
        lines(
            "/** @interface */ function A() {}",
            "/** @interface \n @extends {A} */ function B() {}",
            "/** @param {A} x1 */ function f(x1) { x1.y = 3; }",
            "/** @param {B} x2 */ function g(x2) { x2.z = 3; }\n");
    String output =
        lines(
            "/** @interface */ function A(){}",
            "/** @interface \n @extends {A} */ function B(){}",
            "/** @param {A} x1 */ function f(x1) { x1.a = 3; }",
            "/** @param {B} x2 */ function g(x2) { x2.b = 3; }\n");
    test(js, output);
  }

  @Test
  public void testInterfaceWithSubInterfaceAndDirectImplementors() {
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);
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
  public void testPredeclaredType() {
    this.enableClosurePass();
    this.enableRewriteClosureProvides();

    String js =
        lines(
            "goog.forwardDeclare('goog.Foo');",
            "/** @constructor */ ",
            "function A() {",
            "  this.x = 3;",
            "}",
            "/** @param {goog.Foo} x */",
            "function f(x) { x.y = 4; }");
    String result =
        lines(
            "/** @constructor */ ",
            "function A() {",
            "  this.a = 3;",
            "}",
            "/** @param {goog.Foo} x */",
            "function f(x) { x.y = 4; }");
    test(
        externs(EXTERNS + new TestExternsBuilder().addClosureExterns().build()),
        srcs(js),
        expected(result));
  }

  @Test
  public void testBug14291280() {
    String js =
        lines(
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
    String result =
        lines(
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
    String js =
        lines(
            "/** @constructor */ function Foo() { this.abc = 5; }\n",
            "/** @const */ var alias = Foo;\n",
            "/** @constructor @extends alias */\n",
            "function Bar() {\n",
            "  this.xyz = 7;\n",
            "}");
    String result =
        lines(
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
    String js =
        lines(
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
    String result =
        lines(
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
    String js =
        lines(
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

    String expected =
        lines(
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
            "  this.a = 'a';",
            "}",
            "f(new Type)");

    test(js, expected);
  }

  @Test
  public void structuralTypesNotAmbiguated_forwardRefsNotInUnion() {
    // edge case where the early referenced to "Params" caused data.name => data.a
    test(
        lines(
            "class Foo {",
            "  method() {}",
            "}",
            "/** @param {!Params=} data */",
            "function f(data) {",
            "  return data.name;",
            "}",
            "/** @typedef {{name: string}} */",
            "let Params;"),
        lines(
            "class Foo {",
            "  a() {}",
            "}",
            "/** @param {!Params=} data */",
            "function f(data) {",
            "  return data.name;",
            "}",
            "/** @typedef {{name: string}} */",
            "let Params;"));
  }

  @Test
  public void testObjectSpreadDoesNotCrash() {
    testSame("var lit1 = {...a};");
  }

  @Test
  public void testObjectRestDoesNotCrash() {
    testSame("var {...a} = b;");
  }

  // See https://github.com/google/closure-compiler/issues/2119
  @Test
  public void testUnrelatedObjectLiterals() {
    testSame(
        lines(
            "/** @constructor */ function Foo() {}",
            "/** @constructor */ function Bar() {}",
            "var lit1 = {a: new Foo()};",
            "var lit2 = {b: new Bar()};"));
  }

  @Test
  public void testObjectLitTwoVar() {
    String js =
        lines("/** @constructor */ var Foo = function(){};", "Foo.prototype = {z:0, z:1, x:0};");
    String output =
        lines("/** @constructor */ var Foo = function(){};", "Foo.prototype = {a:0, a:1, b:0};");
    test(js, output);
  }

  @Test
  public void testObjectLitTwoIndependentVar() {
    String js =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {b: 0};",
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype = {c: 0};");
    String output =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {a: 0};",
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype = {a: 0};");
    test(js, output);
  }

  @Test
  public void testObjectLitTwoTypesTwoVar() {
    String js =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {r: 0, g: 0};",
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype = {c: 0, r: 0};");
    String output =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {a: 0, b: 0};",
            "/** @constructor */ var Bar = function(){};",
            "Bar.prototype = {b: 0, a: 0};");
    test(js, output);
  }

  @Test
  public void testObjectLitUnion() {
    String js =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "/** @constructor */ var Bar = function(){};",
            "Foo.prototype = {foodoo: 0};",
            "Bar.prototype = {bardoo: 0};",
            "/** @type {Foo|Bar} */",
            "var U = any();",
            "U.joint;");
    String output =
        lines(
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
    String js =
        lines(
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
    String output =
        lines(
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
    this.enableClosurePass();

    String js =
        lines(
            "/** @constructor */ var Foo = function(){};",
            "Foo.prototype = {x: 0};",
            "/** @constructor \n @extends Foo */ var Bar = function(){};",
            "goog.inherits(Bar, Foo);",
            "Bar.prototype = {y: 0, z: 0};",
            "/** @constructor */ var Baz = function(){};",
            "Baz.prototype = {l: 0, m: 0, n: 0};",
            "(new Baz).m\n");
    String output =
        lines(
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
    String js =
        lines(
            "/** @constructor */ function Foo(){};",
            "Foo.prototype = { /** @return {Bar} */ fun: function() { return new Bar(); } };",
            "/** @constructor */ function Bar(){};",
            "Bar.prototype.bazz;",
            "(new Foo).fun().bazz();");
    String output =
        lines(
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
    testSame(lines("/** @enum {string} */ var Foo = {X: 'y'};", "var x = Foo.X"));
  }

  @Test
  public void testUnannotatedConstructorsDontCrash() {
    testSame(
        lines("function Foo() {}", "Foo.prototype.a;", "function Bar() {}", "Bar.prototype.a;"));
  }

  @Test
  public void testGenericPrototypeObject() {
    String js =
        lines(
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

    String output =
        lines(
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
  public void testRelatedInstanceAndPrototypePropNotAmbiguated() {
    test(
        lines(
            "class Foo {", //
            "  constructor() {",
            "    this.y = 0;",
            "  }",
            "  x() {}",
            "}"),
        lines(
            "class Foo {", //
            "  constructor() {",
            "    this.b = 0;",
            "  }",
            "  a() {}",
            "}"));
  }

  @Test
  public void testRelatedInstanceAndSuperclassPrototypePropNotAmbiguated() {
    test(
        lines(
            "class Foo {", //
            "  x() {}",
            "}",
            "class Bar extends Foo {",
            "  constructor() {",
            "    this.y = 0;",
            "  }",
            "}"),
        lines(
            "class Foo {", //
            "  a() {}",
            "}",
            "class Bar extends Foo {",
            "  constructor() {",
            "    this.b = 0;",
            "  }",
            "}"));
  }

  @Test
  public void testPropertiesWithTypesThatHaveBeenNarrowed() {
    String js =
        lines(
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

    String output =
        lines(
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
    String js =
        lines(
            "/** @interface */",
            "function Foo() {}",
            "/** @return {!Foo} */",
            "Foo.prototype.foo = function() {};");

    String output =
        lines(
            "/** @interface */",
            "function Foo() {}",
            "/** @return {!Foo} */",
            "Foo.prototype.a = function() {};");

    test(externs(""), srcs(js), expected(output));
  }

  @Test
  public void testInvalidRenameFunction_doesNotCrash() {
    testSame("const p = JSCompiler_renameProperty('foo', 0, 1)");
    testSame("const p = JSCompiler_renameProperty(0)");
    testSame("const p = JSCompiler_renameProperty('a.b')");
  }

  @Test
  public void testJSCompiler_renameProperty_twoArgs_blocksAmbiguation() {
    testSame(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prototype.bar = 3;",
            "const barName = JSCompiler_renameProperty('bar', Foo);"));
  }

  @Test
  public void testJSCompiler_renameProperty_oneArg_blocksAmbiguation() {
    testSame(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prototype.bar = 3;",
            "const barName = JSCompiler_renameProperty('bar');"));
  }

  @Test
  public void testSingleClass_withSingleMemberFn_ambiguated() {
    test("class Foo { methodFoo() {} }", "class Foo { a() {} }");
  }

  @Test
  public void testSingleClass_withSingleMemberField_ambiguated() {
    test("class Foo { field = 2; } ", "class Foo { a = 2; }");
  }

  @Test
  public void testSingleClass_withSingleQuotedMemberField_notAmbiguated() {
    testSame("/** @dict */ class Foo { 'field' = 2; }");
  }

  @Test
  public void testSingleClass_withSingleStaticMemberField_ambiguated() {
    test("class Foo { static field = 2; }", "class Foo { static a = 2; }");
  }

  @Test
  public void testSingleClass_withSingleComputedMemberField_notAmbiguated() {
    testSame("/** @dict */ class Foo { ['field'] = 2; }");
  }

  @Test
  public void testSingleClass_withSingleStaticComputedMemberField_notAmbiguated() {
    testSame("/** @dict */ class Foo { static ['field'] = 2; }");
  }

  @Test
  public void testSingleClass_withSingleNumberMemberField_notAmbiguated() {
    testSame("/** @dict */ class Foo { 1 = 2; }");
  }

  @Test
  public void testSingleClass_withSingleStaticNumberMemberField_notAmbiguated() {
    testSame("/** @dict */ class Foo { static 1 = 2; }");
  }

  @Test
  public void testQuotedMemberFieldInClass_notAmbiguated() {
    testSame("/** @dict */ class Foo { 'fieldFoo' }");
  }

  @Test
  public void testQuotedMemberFieldInClassReservesPropertyName() {
    test(
        "/** @unrestricted */ class Foo { 'a'; foo; }",
        "/** @unrestricted */ class Foo { 'a'; b; }");
  }

  @Test
  public void testSingleClass_withTwoMemberFields_notAmbiguated() {
    test("class Foo { field1; field2 = 2;}", "class Foo { a; b = 2;}");
  }

  @Test
  public void testSingleClass_withStaticAndPrototypeMemberFields_ambiguated() {
    test("class Foo { static staticField; memberField; }", "class Foo { static a; a; }");
  }

  @Test
  public void testTwoUnrelatedClasses_withMemberFields_ambiguated() {
    test("class Foo { fieldFoo; } class Bar { fieldBar; }", "class Foo { a; } class Bar { a; }");
  }

  @Test
  public void testTwoUnrelatedClasses_withStaticMemberFields_ambiguated() {
    test(
        lines(
            "class Foo { static fieldFoo = 2; }", //
            "class Bar { static fieldBar = 2; }"),
        lines(
            "class Foo { static a = 2; }", //
            "class Bar { static a = 2; }"));
  }

  @Test
  public void testEs6SuperclassStaticField_notAmbiguated() {
    test(
        lines("class Foo { static fieldFoo; }", "class Bar extends Foo { static fieldBar; }"),
        // Since someone could access Foo.methodFoo() through Bar, make sure the fields get
        // distinct names.
        lines("class Foo { static b; }", "class Bar extends Foo { static a; }"));
  }

  @Test
  public void testSingleClass_withMultipleMemberFields_notAmbiguated() {
    test(
        lines(
            "/** @unrestricted */",
            "class Foo {",
            "  field = 1;",
            "  ['hi'] = 4;",
            "  static 1 = 2;",
            "  hello = 5;",
            "}"),
        lines(
            "/** @unrestricted */",
            "class Foo {",
            "  a = 1;",
            "  ['hi'] = 4;",
            "  static 1 = 2;",
            "  b = 5;",
            "}"));
  }

  @Test
  public void testQuotedMemberFnInClass_notAmbiguated() {
    testSame("/** @dict */ class Foo { 'methodFoo'() {} }");
  }

  @Test
  public void testQuotedMemberFnInClassReservesPropertyName() {
    test(
        "/** @unrestricted */ class Foo { 'a'() {} foo() {} }",
        "/** @unrestricted */ class Foo { 'a'() {} b() {} }");
  }

  @Test
  public void testSingleClass_withTwoMemberFns_notAmbiguated() {
    test("class Foo { method1() {} method2() {} }", "class Foo { a() {} b() {} }");
  }

  @Test
  public void testSingleClass_withStaticAndPrototypeMemberFns_ambiguated() {
    test("class Foo { static method1() {} method2() {} }", "class Foo { static a() {} a() {} }");
  }

  @Test
  public void testTwoUnrelatedClasses_withMemberFns_ambiguated() {
    test(
        "class Foo { methodFoo() {} } class Bar { methodBar() {} }",
        "class Foo { a() {} } class Bar { a() {} }");
  }

  @Test
  public void testTwoUnrelatedClasses_withStaticMemberFns_ambiguated() {
    test(
        lines(
            "class Foo { static methodFoo() {} }", //
            "class Bar { static methodBar() {} }"),
        lines(
            "class Foo { static a() {} }", //
            "class Bar { static a() {} }"));
  }

  @Test
  public void testEs6SuperclassStaticMethod_notAmbiguated() {
    test(
        lines(
            "class Foo { static methodFoo() {} }",
            "class Bar extends Foo { static methodBar() {} }"),
        // Since someone could access Foo.methodFoo() through Bar, make sure the methods get
        // distinct names.
        lines(
            "class Foo { static b() {} }", //
            "class Bar extends Foo { static a() {} }"));
  }

  @Test
  public void testEs6SubclassChain_withStaticMethods_notAmbiguated() {
    test(
        lines(
            "class Foo { static methodFoo() { alert('foo'); } }",
            "class Bar extends Foo { static methodBar() {alert('bar'); } }",
            "class Baz extends Bar { static methodBaz() {alert('baz'); } }",
            "class Moo extends Baz { static methodMoo() { alert('moo'); } }",
            "Moo.methodFoo();"),
        // All four static methods must get distinct names, so that Moo.a resolves correctly to
        // Foo.a
        lines(
            "class Foo { static a() {alert('foo'); } }", //
            "class Bar extends Foo { static b() {alert('bar'); } }",
            "class Baz extends Bar { static c() {alert('baz'); } }",
            "class Moo extends Baz { static d() { alert('moo'); } }",
            "Moo.a();"));
  }

  @Test
  public void testEs5ClassWithExtendsChainStaticMethods_notAmbiguated() {
    test(
        lines(
            "/** @constructor */ function Foo () {}",
            "Foo.methodFoo = function() { alert('foo'); };",
            "class Bar extends Foo { static methodBar() {alert('bar'); } }",
            "class Baz extends Bar { static methodBaz() {alert('baz'); } }",
            "class Moo extends Baz { static methodMoo() { alert('moo'); } }",
            "Moo.methodFoo();"),
        lines(
            "/** @constructor */ function Foo () {}",
            "Foo.a = function() { alert('foo'); };",
            "class Bar extends Foo { static b() {alert('bar'); } }",
            "class Baz extends Bar { static c() {alert('baz'); } }",
            "class Moo extends Baz { static d() { alert('moo'); } }",
            "Moo.a();"));
  }

  @Test
  public void testEs5ClassWithEs5SubclassWtaticMethods_ambiguated() {
    test(
        lines(
            "/** @constructor */ function Foo () {}",
            "Foo.methodFoo = function() { alert('foo'); };",
            "/** @constructor @extends {Foo} */ function Bar() {}",
            "Bar.methodBar = function() { alert('bar'); };"),
        lines(
            "/** @constructor */ function Foo () {}",
            "Foo.a = function() { alert('foo'); };",
            "/** @constructor @extends {Foo} */ function Bar() {}",
            "Bar.a = function() { alert('bar'); };"));
  }

  @Test
  public void testClassWithSuperclassStaticMethodsCalledWithSuper_ambiguated() {

    test(
        lines(
            "class Foo { static methodFoo() {} }",
            "class Bar extends Foo { static methodBar() { super.methodFoo(); } }"),
        // Since someone could access Foo.methodFoo() through Bar, make sure the methods get
        // distinct names.
        lines(
            "class Foo { static a() {} }", //
            "class Bar extends Foo { static b() { super.a(); } }"));
  }

  @Test
  public void testGetterInClass_ambiguated() {
    test("class Foo { get prop() {} }", "class Foo { get a() {} }");
  }

  @Test
  public void testQuotedGetterInClass_isNotAmbiguated() {
    testSame("/** @dict */ class Foo { get 'prop'() {} }");
  }

  @Test
  public void testQuotedGetterInClassReservesPropertyName() {
    test(
        "/** @unrestricted */ class Foo { get 'a'() {} foo() {} }",
        "/** @unrestricted */ class Foo { get 'a'() {} b() {} }");
  }

  @Test
  public void testSetterInClass_isAmbiguated() {
    test("class Foo { set prop(x) {} }", "class Foo { set a(x) {} }");
  }

  @Test
  public void testQuotedSetterInClass_notAmbiguated() {
    testSame("/** @dict */ class Foo { set 'prop'(x) {} }");
  }

  @Test
  public void testSameGetterAndSetterInClass_ambiguated() {
    test("class Foo { get prop() {} set prop(x) {} }", "class Foo { get a() {} set a(x) {} }");
  }

  @Test
  public void testDistinctGetterAndSetterInClass_notAmbiguated() {
    test("class Foo { set propA(x) {} get propB() {} }", "class Foo { set a(x) {} get b() {} }");
  }

  @Test
  public void testComputedMemberFunctionInClass_notAmbiguated() {
    testSame("/** @dict */ class Foo { ['method']() {}}");
  }

  @Test
  public void testSimpleComputedMemberFnInClassReservesPropertyName() {
    test(
        "/** @unrestricted */ class Foo { ['a']() {} foo() {} }",
        "/** @unrestricted */ class Foo { ['a']() {} b() {} }");
  }

  @Test
  public void testComplexComputedMemberFnInClassDoesntReservePropertyName() {
    // we don't try to evaluate 'a' + '' to see that it's identical to 'a', and so end up renaming
    // 'foo' -> 'a'
    // The property name invalidation is already just a heuristic, so just handle the very simple
    // case of ['a']() {}
    test(
        "/** @unrestricted */ class Foo { ['a' + '']() {} foo() {} }",
        "/** @unrestricted */ class Foo { ['a' + '']() {} a() {} }");
  }

  @Test
  public void testEs6ClassConstructorMethod_notAmbiguated() {
    testSame("class Foo { constructor() {} }");
  }

  @Test
  public void testOptchainGet_ambiguated() {
    test("class Foo { foo() { this?.foo(); } };", "class Foo { a() { this?.a(); } };");
  }

  @Test
  public void testAmbiguateEs6ClassMethodsDoesntCrashOnClassInACast() {
    // the cast causes the actual CLASS node to have the unknown type, so verify that the pass
    // can handle it not being a function type.
    testSame(
        lines(
            "const Foo = /** @type {?} */ (class {", //
            "  method() {}",
            "});",
            "class Bar {",
            "  method() {}",
            "}"));
  }

  @Test
  public void testObjectSetPrototypeOfIsIgnored() {
    test(
        externs("Object.setPrototypeOf = function(obj, proto) {}"),
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "Foo.fooMethod = () => 3;",
                "/** @constructor */",
                "function Bar() {}",
                "Bar.barMethod = () => 4;",
                "Object.setPrototypeOf(Foo, Bar);")),
        expected(
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "Foo.a = () => { return 3; };",
                "/** @constructor */",
                "function Bar() {}",
                "Bar.a = () => { return 4; };",
                "Object.setPrototypeOf(Foo, Bar);")));
    // now trying to reference Foo.barMethod will not work, and will call barMethod instead.
    // AmbiguateProperties currently ignores this case
  }

  @Test
  public void testComputedPropertyInObjectLiteral_notAmbiguated() {
    testSame("const obj = {['a']: 3, b: 4}");
  }

  @Test
  public void testQuotedPropertyInObjectLiteralReservesPropertyName() {
    test(
        "const obj = {'a': 3}; class C { method() {}}",
        // `method` is renamed to `b`, not `a`, to avoid colliding with the computed prop.
        // This is just a heuristic; JSCompiler cannot statically determine all string
        // property names.
        "const obj = {'a': 3}; class C { b() {}}");
  }

  @Test
  public void testQuotedMemberFnInObjectLiteralReservesPropertyName() {
    test(
        "const obj = {'a'() {}}; class C { method() {}}",
        "const obj = {'a'() {}}; class C { b() {}}");
  }

  @Test
  public void testComputedPropertyInObjectLiteralReservesPropertyName() {
    test(
        "const obj = {['a']: 3}; class C { method() {}}",
        "const obj = {['a']: 3}; class C { b() {}}");
  }

  @Test
  public void testMemberFnInObjectLiteralPreventsPropertyRenaming() {
    // Don't rename the class member 'm' because the object literal type is invalidating, and
    // also has a property 'm'
    testSame("const obj = {m() {}}; class C {m() {}}");
  }

  @Test
  public void testSimplePropInObjectLiteralPreventsPropertyRenaminge() {
    // Don't rename the class member 'm' because the object literal type is invalidating, and
    // also has a property 'm'
    testSame("const obj = {m: 0}; class C {m() {}}");
  }

  @Test
  public void testObjectPatternDeclarationWithStringKey_ambiguated() {
    test(
        lines(
            "class Foo {", //
            "  method() {}",
            "}",
            "const {method} = new Foo();"),
        lines(
            "class Foo {", //
            "  a() {}",
            "}",
            "const {a: method} = new Foo();"));
  }

  @Test
  public void testObjectPatternDeclarationWithStringKeWithDefault_ambiguated() {
    test(
        lines(
            "class Foo {", //
            "  method() {}",
            "}",
            "const {method = () => 3} = new Foo();"),
        lines(
            "class Foo {", //
            "  a() {}",
            "}",
            "const {",
            "  a: method = () => { return 3; }",
            "} = new Foo();"));
  }

  @Test
  public void testNestedObjectPatternWithStringKey_ambiguated() {
    test(
        lines(
            "class Foo {", //
            "  method() {}",
            "}",
            "const {foo: {method}} = {foo: new Foo()};"),
        lines(
            "class Foo {", //
            "  a() {}",
            "}",
            // note: we rename the 'method' access but not 'foo', because we don't try ambiguating
            // properties on object literal types.
            "const {foo: {a: method}} = {foo: new Foo()};"));
  }

  @Test
  public void testObjectPatternParameterWithStringKey_ambiguated() {
    test(
        lines(
            "class Foo {", //
            "  method() {}",
            "}",
            "/** @param {!Foo} foo */",
            "function f({method}) {}"),
        lines(
            "class Foo {", //
            "  a() {}",
            "}",
            "/** @param {!Foo} foo */",
            "function f({a: method}) {}"));
  }

  @Test
  public void testObjectPatternQuotedStringKey_notAmbiguated() {
    // this emits a warning for a computed property access on a struct, since property ambiguation
    // will break this code.
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);
    test(
        lines(
            "class Foo {", //
            "  method() {}",
            "}",
            "const {'method': method} = new Foo();"),
        lines(
            "class Foo {", //
            "  a() {}",
            "}",
            "const {'method': method} = new Foo();"));
  }

  @Test
  public void testObjectPatternComputedProperty_notAmbiguated() {
    // this emits a warning for a computed property access on a struct, since property ambiguation
    // will break this code.
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);
    test(
        lines(
            "class Foo {", //
            "  method() {}",
            "}",
            "const {['method']: method} = new Foo();"),
        lines(
            "class Foo {", //
            "  a() {}",
            "}",
            "const {['method']: method} = new Foo();"));
  }

  @Test
  public void testMixinPropertiesNotAmbiguated() {
    test(
        lines(
            "", //
            "class A {",
            "  constructor() {",
            "    this.aProp = 'aProp';",
            "  }",
            "}",
            "",
            "/**",
            " * @template T",
            " * @param {function(new: T)} baseType",
            " * @return {?}",
            " */",
            "function mixinX(baseType) {",
            "  return class extends baseType {",
            "    constructor() {",
            "      super();",
            "      this.x = 'x';",
            "    }",
            "  };",
            "}",
            "/** @constructor @extends {A} */",
            "const BSuper = mixinX(A);",
            "",
            "class B extends BSuper {",
            "  constructor() {",
            "    super();",
            "    this.bProp = 'bProp';",
            "  }",
            "}",
            "",
            ""),
        lines(
            "", //
            "class A {",
            "  constructor() {",
            "    this.a = 'aProp';",
            "  }",
            "}",
            "",
            "/**",
            " * @template T",
            " * @param {function(new: T)} baseType",
            " * @return {?}",
            " */",
            "function mixinX(baseType) {",
            "  return class extends baseType {",
            "    constructor() {",
            "      super();",
            "      this.x = 'x';",
            "    }",
            "  };",
            "}",
            "/** @constructor @extends {A} */",
            "const BSuper = mixinX(A);",
            "",
            "class B extends BSuper {",
            "  constructor() {",
            "    super();",
            // Note that ambiguating 'bProp' => 'a' would be incorrect because BSuper extends A via
            // the mixin, so 'bProp' would conflict with 'aProp'. JSCompiler doesn't actually track
            // that B extends A. Instead it conservatively invalidates all properties on B because
            // BSuper is from a mixin, not a class literal.
            "    this.bProp = 'bProp';",
            "  }",
            "}",
            "",
            ""));
  }

  @Test
  public void testMixinPrototypePropertiesNotAmbiguated() {
    test(
        lines(
            "", //
            "class A {",
            "  aMethod() {}",
            "}",
            "",
            "function mixinX(baseType) {",
            "  return class extends baseType {",
            "    x() {}",
            "  };",
            "}",
            "/** @constructor @extends {A} */",
            "const BSuper = mixinX(A);",
            "",
            "class B extends BSuper {",
            "  bMethod() {}",
            "}",
            "",
            ""),
        lines(
            "", //
            "class A {",
            "  a() {}",
            "}",
            "",
            "function mixinX(baseType) {",
            "  return class extends baseType {",
            "    x() {}",
            "  };",
            "}",
            "/** @constructor @extends {A} */",
            "const BSuper = mixinX(A);",
            "",
            "class B extends BSuper {",
            // Note that ambiguating 'bMethod' => 'a' would be incorrect because BSuper extends A
            // via the mixin, so 'bMethod' would conflict with 'aMethod'.
            "  bMethod() {}",
            "}",
            "",
            ""));
  }

  @Test
  public void testMixinConstructorPropertiesNotAmbiguated() {
    test(
        lines(
            "", //
            "class A {",
            "  static aMethod() {}",
            "}",
            "",
            "/**",
            " * @template T",
            " * @param {function(new: T)} baseType",
            " * @return {?}",
            " */",
            "function mixinX(baseType) {",
            "  return class extends baseType {",
            "    static x() {}",
            "  };",
            "}",
            "/** @constructor @extends {A} */",
            "const BSuper = mixinX(A);",
            "",
            "class B extends BSuper {",
            "  static bMethod() {}",
            "}",
            "",
            ""),
        lines(
            "", //
            "class A {",
            "  static a() {}",
            "}",
            "",
            "/**",
            " * @template T",
            " * @param {function(new: T)} baseType",
            " * @return {?}",
            " */",
            "function mixinX(baseType) {",
            "  return class extends baseType {",
            "    static x() {}",
            "  };",
            "}",
            "/** @constructor @extends {A} */",
            "const BSuper = mixinX(A);",
            "",
            "class B extends BSuper {",
            "  static bMethod() {}",
            "}",
            "",
            ""));
  }

  @Test
  public void testObjectUsedAsMapIsInvalidating() {
    ignoreWarnings(DiagnosticGroups.MISSING_PROPERTIES);
    test(
        lines(
            "/** @param {!Object<string>} obj */",
            "function f(obj) {",
            "  return obj.x;",
            "}",
            "class OtherClass {",
            "  constructor() {",
            "    this.y = 0;",
            "  }",
            "}"),
        lines(
            "/** @param {!Object<string>} obj */",
            "function f(obj) {",
            // Can't ambiguate this 'x' to 'a' since we don't track different Object types uniquely
            // and OtherClass is also assignable to !Object<string>
            "  return obj.x;",
            "}",
            "class OtherClass {",
            "  constructor() {",
            "    this.a = 0;",
            "  }",
            "}"));
  }

  @Test
  public void testAmbiguateMethodAddedToStringPrototype_accessedOffScalar() {
    test(
        lines(
            "/** @return {string} */",
            "String.prototype.customMethod = function() { return ''; };",
            "'FOOBAR'.customMethod();",
            "class Bar {",
            "  bar() {}",
            "}",
            "new Bar().bar();"),
        lines(
            "/** @return {string} */",
            "String.prototype.a = function() { return ''; };",
            "'FOOBAR'.a();",
            "class Bar {",
            "  a() {}",
            "}",
            "new Bar().a();"));
  }

  @Test
  public void testAmbiguateMethodAddedToStringPrototype() {
    test(
        lines(
            "/** @return {string} */",
            "String.prototype.customMethod = function() { return ''; };",
            "new String('FOOBAR').customMethod();",
            "class Bar {",
            "  bar() {}",
            "}",
            "new Bar().bar();"),
        lines(
            "/** @return {string} */",
            "String.prototype.a = function() { return ''; };",
            "new String('FOOBAR').a();",
            "class Bar {",
            "  a() {}",
            "}",
            "new Bar().a();"));
  }
}
