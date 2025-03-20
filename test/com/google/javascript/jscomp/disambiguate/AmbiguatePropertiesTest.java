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
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {b: 0};
        """,
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {a: 0};
        """);
  }

  @Test
  public void testOneVar3() {
    test(
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {get b() {return 0}};
        """,
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {get a() {return 0}};
        """);
  }

  @Test
  public void testOneVar4() {
    test(
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {set b(a) {}};
        """,
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {set a(a) {}};
        """);
  }

  @Test
  public void testTwoVar1() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype.z=0;
        Foo.prototype.z=0;
        Foo.prototype.x=0;
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype.a=0;
        Foo.prototype.a=0;
        Foo.prototype.b=0;
        """;
    test(js, output);
  }

  @Test
  public void testTwoVar2() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {z:0, z:1, x:0};
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {a:0, a:1, b:0};
        """;
    test(js, output);
  }

  @Test
  public void testTwoIndependentVar() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype.b = 0;
        /** @constructor */ var Bar = function(){};
        Bar.prototype.c = 0;
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype.a=0;
        /** @constructor */ var Bar = function(){};
        Bar.prototype.a=0;
        """;
    test(js, output);
  }

  @Test
  public void testTwoTypesTwoVar() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype.r = 0;
        Foo.prototype.g = 0;
        /** @constructor */ var Bar = function(){};
        Bar.prototype.c = 0;
        Bar.prototype.r = 0;
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype.a=0;
        Foo.prototype.b=0;
        /** @constructor */ var Bar = function(){};
        Bar.prototype.b=0;
        Bar.prototype.a=0;
        """;
    test(js, output);
  }

  @Test
  public void testUnion_withUnrelatedPropertyAccess() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        /** @constructor */ var Bar = function(){};
        Foo.prototype.foodoo=0;
        Bar.prototype.bardoo=0;
        // variable exists that could be a Foo or a Bar
        /** @type {Foo|Bar} */
        var U = any();
        // We don't actually access either foodoo or bardoo on it,
        // though, so it's OK if they end up having the same name
        U.joint
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        /** @constructor */ var Bar = function(){};
        Foo.prototype.a=0;
        Bar.prototype.a=0;
        /** @type {Foo|Bar} */
        var U = any();
        U.b
        """;
    test(js, output);
  }

  @Test
  public void testUnion_withRelatedPropertyAccess() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        /** @constructor */ var Bar = function(){};
        Foo.prototype.foodoo=0;
        Bar.prototype.bardoo=0;
        // variable exists that could be a Foo or a Bar
        /** @type {Foo|Bar} */
        var U = any();
        // both foodoo and bardoo are accessed through that variable,
        // so they must have different names.
        U.foodoo;
        U.bardoo
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        /** @constructor */ var Bar = function(){};
        Foo.prototype.b=0;
        Bar.prototype.a=0;
        /** @type {Foo|Bar} */
        var U = any();
        U.b;
        U.a
        """;
    test(js, output);
  }

  @Test
  public void testUnions() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        /** @constructor */ var Bar = function(){};
        /** @constructor */ var Baz = function(){};
        /** @constructor */ var Bat = function(){};
        Foo.prototype.lone1=0;
        Bar.prototype.lone2=0;
        Baz.prototype.lone3=0;
        Bat.prototype.lone4=0;
        /** @type {Foo|Bar} */
        var U1 = any();
        U1.j1;
        U1.j2;
        /** @type {Baz|Bar} */
        var U2 = any();
        U2.j3;
        U2.j4;
        /** @type {Baz|Bat} */
        var U3 = any();
        U3.j5;
        U3.j6
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        /** @constructor */ var Bar = function(){};
        /** @constructor */ var Baz = function(){};
        /** @constructor */ var Bat = function(){};
        Foo.prototype.c=0;
        Bar.prototype.e=0;
        Baz.prototype.e=0;
        Bat.prototype.c=0;
        /** @type {Foo|Bar} */
        var U1 = any();
        U1.a;
        U1.b;
        /** @type {Baz|Bar} */
        var U2 = any();
        U2.c;
        U2.d;
        /** @type {Baz|Bat} */
        var U3 = any();
        U3.a;
        U3.b
        """;
    test(js, output);
  }

  @Test
  public void testExtends() {
    this.enableClosurePass();

    String js =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype.x=0;
        /** @constructor
         @extends Foo */ var Bar = function(){};
        goog.inherits(Bar, Foo);
        Bar.prototype.y=0;
        Bar.prototype.z=0;
        /** @constructor */ var Baz = function(){};
        Baz.prototype.l=0;
        Baz.prototype.m=0;
        Baz.prototype.n=0;
        (new Baz).m
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype.a=0;
        /** @constructor
         @extends Foo */ var Bar = function(){};
        goog.inherits(Bar, Foo);
        Bar.prototype.b=0;
        Bar.prototype.c=0;
        /** @constructor */ var Baz = function(){};
        Baz.prototype.b=0;
        Baz.prototype.a=0;
        Baz.prototype.c=0;
        (new Baz).a
        """;
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
        """
        /** @constructor */ function Foo(){};
        /** @return {Bar} */
        Foo.prototype.fun = function() { return new Bar(); };
        /** @constructor */ function Bar(){};
        Bar.prototype.bazz;
        (new Foo).fun().bazz();
        """;
    String output =
        """
        /** @constructor */ function Foo(){};
        /** @return {Bar} */
        Foo.prototype.a = function() { return new Bar(); };
        /** @constructor */ function Bar(){};
        Bar.prototype.a;
        (new Foo).a().a();
        """;
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
        """
        /** @constructor */ var Bar = function(){};
        Bar.prototype.bar = 0;
        /** @struct @constructor */ var Foo = function() {
          this.bar_ = 'bar';
        };
        /** @type {?} */ Foo.prototype.bar;
        Object.defineProperties(Foo.prototype, {
          bar: {
            configurable: true,
            enumerable: true,
            /** @this {Foo} */ get: function() { return this.bar_;},
            /** @this {Foo} */ set: function(value) { this.bar_ = value; }
          }
        });
        """;

    String result =
        """
        /** @constructor */ var Bar = function(){};
        Bar.prototype.a = 0;
        /** @struct @constructor */ var Foo = function() {
          this.b = 'bar';
        };
        /** @type {?} */ Foo.prototype.a;
        Object.defineProperties(Foo.prototype, {
          a: {
            configurable: true,
            enumerable: true,
            /** @this {Foo} */ get: function() { return this.b;},
            /** @this {Foo} */ set: function(value) { this.b = value; }
          }
        });
        """;

    test(js, result);
  }

  @Test
  public void testObjectDefinePropertiesQuoted() {
    String js =
        """
        /** @constructor */ var Bar = function(){};
        Bar.prototype.bar = 0;
        /** @struct @constructor */ var Foo = function() {
          this.bar_ = 'bar';
        };
        /** @type {?} */ Foo.prototype['bar'];
        Object.defineProperties(Foo.prototype, {
          'a': {
            configurable: true,
            enumerable: true,
            /** @this {Foo} */ get: function() { return this.bar_;},
            /** @this {Foo} */ set: function(value) { this.bar_ = value; }
          }
        });
        """;

    String result =
        """
        /** @constructor */ var Bar = function(){};
        Bar.prototype.b = 0;
        /** @struct @constructor */ var Foo = function() {
          this.b = 'bar';
        };
        /** @type {?} */ Foo.prototype['bar'];
        Object.defineProperties(Foo.prototype, {
          'a': {
            configurable: true,
            enumerable: true,
            /** @this {Foo} */ get: function() { return this.b;},
            /** @this {Foo} */ set: function(value) { this.b = value; }
          }
        });
        """;

    test(js, result);
  }

  @Test
  public void testObjectDefinePropertiesComputed() {
    String js =
        """
        /** @constructor */ var Bar = function(){};
        Bar.prototype.bar = 0;
        /** @struct @constructor */ var Foo = function() {
          this.bar_ = 'bar';
        };
        /** @type {?} */ Foo.prototype['bar'];
        Object.defineProperties(Foo.prototype, {
          ['a']: {
            configurable: true,
            enumerable: true,
            /** @this {Foo} */ get: function() { return this.bar_;},
            /** @this {Foo} */ set: function(value) { this.bar_ = value; }
          }
        });
        """;

    String result =
        """
        /** @constructor */ var Bar = function(){};
        Bar.prototype.b = 0;
        /** @struct @constructor */ var Foo = function() {
          this.b = 'bar';
        };
        /** @type {?} */ Foo.prototype['bar'];
        Object.defineProperties(Foo.prototype, {
          ['a']: {
            configurable: true,
            enumerable: true,
            /** @this {Foo} */ get: function() { return this.b;},
            /** @this {Foo} */ set: function(value) { this.b = value; }
          }
        });
        """;

    test(js, result);
  }

  @Test
  public void testObjectDefinePropertiesMemberFn() {
    // NOTE: this is very odd code, and people should not be writing it anyway. We really just
    // don't want a crash.
    String js =
        """
        /** @constructor */ var Bar = function(){};
        Bar.prototype.bar = 0;
        /** @struct @constructor */ var Foo = function() {
          this.bar_ = 'bar';
        };
        /** @type {?} */ Foo.prototype['bar'];
        Object.defineProperties(Foo.prototype, {
          a() {}
        });
        """;

    String result =
        """
        /** @constructor */ var Bar = function(){};
        Bar.prototype.a = 0;
        /** @struct @constructor */ var Foo = function() {
          this.b = 'bar';
        };
        /** @type {?} */ Foo.prototype['bar'];
        Object.defineProperties(Foo.prototype, {
          a() {}
        });
        """;

    test(js, result);
  }

  @Test
  public void testOverlappingOriginalAndGeneratedNames() {
    test(
        """
        /** @constructor */ function Bar(){};
        Bar.prototype.b = function(){};
        Bar.prototype.a = function(){};
        var bar = new Bar();
        bar.b();
        """,
        """
        /** @constructor */ function Bar(){};
        Bar.prototype.a = function(){};
        Bar.prototype.b = function(){};
        var bar = new Bar();
        bar.a();
        """);
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
        """
        var foo = function(){}; foo.prop = ''; foo.baz = '';
        function f(/** function(): void */ fun) { fun.bar = ''; fun.baz = ''; }
        """);
  }

  @Test
  public void testConstructorTreatedAsSubtypeOfFunction() {
    String js =
        """
        Function.prototype.a = 1;
        /** @constructor */
        function F() {}
        F.y = 2;
        """;

    String output =
        """
        Function.prototype.a = 1;
        // F is a subtype of Function, so we can't ambiguate "F.b" to "F.a".
        /** @constructor */
        function F() {}
        F.b = 2;
        """;
    test(js, output);
  }

  @Test
  public void testPropertyOfObjectOfUnknownType() {
    testSame("var foo = x(); foo.prop = '';");
  }

  @Test
  public void testPropertyOnParamOfUnknownType() {
    testSame(
        """
        /** @constructor */ function Foo(){};
        Foo.prototype.prop = 0;
        function go(aFoo){
          aFoo.prop = 1;
        }
        """);
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
        """
        /** @constructor */ var Bar = function(){};
        /** @override */ Bar.prototype.toString = function(){};
        Bar.prototype.func = function(){};
        var bar = new Bar();
        bar.toString();
        """,
        """
        /** @constructor */ var Bar = function(){};
        /** @override */ Bar.prototype.toString = function(){};
        Bar.prototype.a = function(){};
        var bar = new Bar();
        bar.toString();
        """);
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
        """
        /** @constructor */ var Foo = function(){};
        Foo.x=0;
        /** @constructor
         @extends Foo */ var Bar = function(){};
        goog.inherits(Bar, Foo);
        Bar.y=0;
        Bar.prototype.z=0;
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        Foo.a=0;
        /** @constructor
         @extends Foo */ var Bar = function(){};
        goog.inherits(Bar, Foo);
        Bar.a=0;
        Bar.prototype.a=0;
        """;
    test(js, output);
  }

  @Test
  public void testStatic() {
    String js =
        """
        /** @constructor */ var Foo = function() {};
        Foo.x = 0;
        /** @constructor */ var Bar = function() {};
        Bar.y = 0;
        """;
    String output =
        """
        /** @constructor */ var Foo = function() {};
        Foo.a = 0;
        /** @constructor */ var Bar = function() {};
        Bar.a = 0;
        """;
    test(js, output);
  }

  @Test
  public void testClassWithStaticsPassedToUnrelatedFunction() {
    String js =
        """
        /** @constructor */ var Foo = function() {};
        Foo.x = 0;
        /** @param {!Function} x */ function f(x) { x.y = 1; x.z = 2; }
        f(Foo)
        """;
    String output =
        """
        /** @constructor */ var Foo = function() {};
        Foo.a = 0;
        /** @param {!Function} x */ function f(x) { x.y = 1; x.z = 2; }
        f(Foo)
        """;
    test(js, output);
  }

  @Test
  public void testClassWithStaticsPassedToRelatedFunction() {
    String js =
        """
        /** @constructor */ var Foo = function() {};
        Foo.x = 0;
        /** @param {!Function} x */ function f(x) { x.y = 1; x.x = 2;}
        f(Foo)
        """;
    testSame(js);
  }

  @Test
  public void testFunctionInstanceProtoype_isInvalidating() {
    String js =
        """
        class Foo {
          bar() { }
        }

        function addPrototypeProp(/** !Function */ ctor) {
          ctor.prototype.kip = 0;
        }
        """;
    String output =
        """
        class Foo {
          a() { }
        }

        function addPrototypeProp(/** !Function */ ctor) {
          ctor.prototype.kip = 0;
        }
        """;
    test(js, output);
  }

  @Test
  public void testTypeMismatch() {
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);
    testSame(
        """
        /** @constructor */var Foo = function(){};
        /** @constructor */var Bar = function(){};
        Foo.prototype.b = 0;
        /** @type {Foo} */
        var F = new Bar();
        """);
  }

  @Test
  public void testRenamingMap() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype.z=0;
        Foo.prototype.z=0;
        Foo.prototype.x=0;
        Foo.prototype.y=0;
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype.a=0;
        Foo.prototype.a=0;
        Foo.prototype.b=0;
        Foo.prototype.c=0;
        """;
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
        """
        /** @interface */ function Foo(){}
        Foo.prototype.x = function(){};
        /**
         * @constructor
         * @implements {Foo}
         */
        function Bar(){}
        Bar.prototype.y;
        /** @override */
        Bar.prototype.x = function() { return this.y; };
        Bar.prototype.z = function() {};
        /** @type {Foo} */ (new Bar).y;
        """;
    String output =
        """
        /** @interface */ function Foo(){}
        Foo.prototype.b = function(){};
        /**
         * @constructor
         * @implements {Foo}
         */
        function Bar(){}
        Bar.prototype.a;
        /** @override */
        Bar.prototype.b = function() { return this.a; };
        Bar.prototype.c = function() {};
        (new Bar).a;
        """;
    test(js, output);
  }

  @Test
  public void testImplementsAndExtends() {
    String js =
        """
        /** @interface */ function Foo() {}
        /** @constructor */ function Bar(){}
        Bar.prototype.y = function() { return 3; };
        /**
         * @constructor
         * @extends {Bar}
         * @implements {Foo}
         */
        function SubBar(){ }
        /** @param {Foo} x1 */ function f(x1) { x1.z = 3; }
        /** @param {SubBar} x2 */ function g(x2) { x2.z = 3; }
        """;
    String output =
        """
        /** @interface */ function Foo(){}
        /** @constructor */ function Bar(){}
        Bar.prototype.b = function() { return 3; };
        /**
         * @constructor
         * @extends {Bar}
         * @implements {Foo}
         */
        function SubBar(){}
        /** @param {Foo} x1 */ function f(x1) { x1.a = 3; }
        /** @param {SubBar} x2 */ function g(x2) { x2.a = 3; }
        """;
    test(js, output);
  }

  @Test
  public void testImplementsAndExtends_respectsUndeclaredProperties() {
    String js =
        """
        /** @interface */ function A() {}
        /**
         * @constructor
         */
        function C1(){}
        /**
         * @constructor
         * @extends {C1}
         * @implements {A}
         */
        function C2(){}
        /** @param {C1} x1 */ function f(x1) { x1.y = 3; }
        /** @param {A} x2 */ function g(x2) { x2.z = 3; }
        """;
    String output =
        """
        /** @interface */ function A(){}
        /**
         * @constructor
         */
        function C1(){}
        /**
         * @constructor
         * @extends {C1}
         * @implements {A}
         */
        function C2(){}
        /** @param {C1} x1 */ function f(x1) { x1.a = 3; }
        /** @param {A} x2 */ function g(x2) { x2.b = 3; }
        """;
    test(js, output);
  }

  @Test
  public void testImplementsAndExtendsEs6Class_respectsUndeclaredProperties() {
    String js =
        """
        /** @interface */
        class A {
          constructor() {
        // Optional property; C2 does not need to declare it implemented.
            /** @type {number|undefined} */
            this.y;
          }
        }
        class C1 {
          constructor() {
            /** @type {number} */
            this.z;
          }
        }
        /** @implements {A} */
        class C2 extends C1 {}
        /** @param {A} x1 */ function f(x1) { x1.y = 3; }
        /** @param {C1} x2 */ function g(x2) { x2.z = 3; }
        """;
    String output =
        """
        /** @interface */
        class A {
          constructor() {
            /** @type {number|undefined} */
            this.a;
          }
        }
        class C1 {
          constructor() {
            /** @type {number} */
            this.b;
          }
        }
        /** @implements {A} */
        class C2 extends C1 {}
        /** @param {A} x1 */ function f(x1) { x1.a = 3; }
        /** @param {C1} x2 */ function g(x2) { x2.b = 3; }
        """;
    test(js, output);
  }

  @Test
  public void testExtendsInterface() {
    String js =
        """
        /** @interface */ function A() {}
        /** @interface
         @extends {A} */ function B() {}
        /** @param {A} x1 */ function f(x1) { x1.y = 3; }
        /** @param {B} x2 */ function g(x2) { x2.z = 3; }
        """;
    String output =
        """
        /** @interface */ function A(){}
        /** @interface
         @extends {A} */ function B(){}
        /** @param {A} x1 */ function f(x1) { x1.a = 3; }
        /** @param {B} x2 */ function g(x2) { x2.b = 3; }
        """;
    test(js, output);
  }

  @Test
  public void testInterfaceWithSubInterfaceAndDirectImplementors() {
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);
    test(
        """
        /** @interface */ function Foo(){};
        Foo.prototype.foo = function(){};
        /** @interface @extends {Foo} */ function Bar(){};
        /** @constructor @implements {Foo} */ function Baz(){};
        Baz.prototype.baz = function(){};
        """,
        """
        /** @interface */ function Foo(){};
        Foo.prototype.b = function(){};
        /** @interface @extends {Foo} */ function Bar(){};
        /** @constructor @implements {Foo} */ function Baz(){};
        Baz.prototype.a = function(){};
        """);
  }

  @Test
  public void testPredeclaredType() {
    this.enableClosurePass();
    this.enableRewriteClosureProvides();

    String js =
        """
        goog.forwardDeclare('goog.Foo');
        /** @constructor */
        function A() {
          this.x = 3;
        }
        /** @param {goog.Foo} x */
        function f(x) { x.y = 4; }
        """;
    String result =
        """
        /** @constructor */
        function A() {
          this.a = 3;
        }
        /** @param {goog.Foo} x */
        function f(x) { x.y = 4; }
        """;
    test(
        externs(EXTERNS + new TestExternsBuilder().addClosureExterns().build()),
        srcs(js),
        expected(result));
  }

  @Test
  public void testBug14291280() {
    String js =
        """
        /** @constructor
         @template T */

        function C() {

          this.aa = 1;

        }

        /** @return {C.<T>} */

        C.prototype.method = function() {

          return this;

        }

        /** @type {C.<string>} */

        var x = new C().method();

        x.bb = 2;

        x.cc = 3;
        """;
    String result =
        """
        /** @constructor
         @template T */

        function C() {
          this.b = 1;
        }
        /** @return {C.<T>} */

        C.prototype.a = function() {
          return this;
        };
        /** @type {C.<string>} */

        var x = (new C).a();
        x.c = 2;
        x.d = 3;
        """;
    test(js, result);
  }

  @Test
  public void testAmbiguateWithAnAlias() {
    String js =
        """
        /** @constructor */ function Foo() { this.abc = 5; }

        /** @const */ var alias = Foo;

        /** @constructor @extends alias */

        function Bar() {

          this.xyz = 7;

        }
        """;
    String result =
        """
        /** @constructor */ function Foo() { this.a = 5; }

        /** @const */ var alias = Foo;

        /** @constructor @extends alias */

        function Bar() {

          this.b = 7;

        }
        """;
    test(js, result);
  }

  @Test
  public void testAmbiguateWithAliases() {
    String js =
        """
        /** @constructor */ function Foo() { this.abc = 5; }

        /** @const */ var alias = Foo;

        /** @constructor @extends alias */

        function Bar() {

          this.def = 7;

        }

        /** @constructor @extends alias */

        function Baz() {

          this.xyz = 8;

        }
        """;
    String result =
        """
        /** @constructor */ function Foo() { this.a = 5; }

        /** @const */ var alias = Foo;

        /** @constructor @extends alias */

        function Bar() {

          this.b = 7;

        }

        /** @constructor @extends alias */

        function Baz() {

          this.b = 8;

        }
        """;
    test(js, result);
  }

  // See https://github.com/google/closure-compiler/issues/1358
  @Test
  public void testAmbiguateWithStructuralInterfaces() {
    String js =
        """
        /** @record */
        function Record() {}
        /** @type {number|undefined} */
        Record.prototype.recordProp;

        function f(/** !Record */ a) { use(a.recordProp); }

        /** @constructor */
        function Type() {
          /** @const */
          this.classProp = 'a';
        }
        f(new Type)
        """;

    String expected =
        """
        /** @record */
        function Record() {}
        /** @type {number|undefined} */
        Record.prototype.recordProp;

        function f(/** !Record */ a) { use(a.recordProp); }

        /** @constructor */
        function Type() {
          /** @const */
          this.a = 'a';
        }
        f(new Type)
        """;

    test(js, expected);
  }

  @Test
  public void structuralTypesNotAmbiguated_forwardRefsNotInUnion() {
    // edge case where the early referenced to "Params" caused data.name => data.a
    test(
        """
        class Foo {
          method() {}
        }
        /** @param {!Params=} data */
        function f(data) {
          return data.name;
        }
        /** @typedef {{name: string}} */
        let Params;
        """,
        """
        class Foo {
          a() {}
        }
        /** @param {!Params=} data */
        function f(data) {
          return data.name;
        }
        /** @typedef {{name: string}} */
        let Params;
        """);
  }

  @Test
  public void testObjectSpreadDoesNotCrash() {
    testSame("var lit1 = {...a};");
  }

  @Test
  public void testObjectRestDoesNotCrash() {
    // only normalization changes, nothing else
    test("var {...a} = b;", "var a; ({...a} = b);");
  }

  // See https://github.com/google/closure-compiler/issues/2119
  @Test
  public void testUnrelatedObjectLiterals() {
    testSame(
        """
        /** @constructor */ function Foo() {}
        /** @constructor */ function Bar() {}
        var lit1 = {a: new Foo()};
        var lit2 = {b: new Bar()};
        """);
  }

  @Test
  public void testObjectLitTwoVar() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {z:0, z:1, x:0};
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {a:0, a:1, b:0};
        """;
    test(js, output);
  }

  @Test
  public void testObjectLitTwoIndependentVar() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {b: 0};
        /** @constructor */ var Bar = function(){};
        Bar.prototype = {c: 0};
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {a: 0};
        /** @constructor */ var Bar = function(){};
        Bar.prototype = {a: 0};
        """;
    test(js, output);
  }

  @Test
  public void testObjectLitTwoTypesTwoVar() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {r: 0, g: 0};
        /** @constructor */ var Bar = function(){};
        Bar.prototype = {c: 0, r: 0};
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {a: 0, b: 0};
        /** @constructor */ var Bar = function(){};
        Bar.prototype = {b: 0, a: 0};
        """;
    test(js, output);
  }

  @Test
  public void testObjectLitUnion() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        /** @constructor */ var Bar = function(){};
        Foo.prototype = {foodoo: 0};
        Bar.prototype = {bardoo: 0};
        /** @type {Foo|Bar} */
        var U = any();
        U.joint;
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        /** @constructor */ var Bar = function(){};
        Foo.prototype = {a: 0};
        Bar.prototype = {a: 0};
        /** @type {Foo|Bar} */
        var U = any();
        U.b;
        """;
    test(js, output);
  }

  @Test
  public void testObjectLitUnions() {
    String js =
        """
        /** @constructor */ var Foo = function(){};
        /** @constructor */ var Bar = function(){};
        /** @constructor */ var Baz = function(){};
        /** @constructor */ var Bat = function(){};
        Foo.prototype = {lone1: 0};
        Bar.prototype = {lone2: 0};
        Baz.prototype = {lone3: 0};
        Bat.prototype = {lone4: 0};
        /** @type {Foo|Bar} */
        var U1 = any();
        U1.j1;
        U1.j2;
        /** @type {Baz|Bar} */
        var U2 = any();
        U2.j3;
        U2.j4;
        /** @type {Baz|Bat} */
        var U3 = any();
        U3.j5;
        U3.j6
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        /** @constructor */ var Bar = function(){};
        /** @constructor */ var Baz = function(){};
        /** @constructor */ var Bat = function(){};
        Foo.prototype = {c: 0};
        Bar.prototype = {e: 0};
        Baz.prototype = {e: 0};
        Bat.prototype = {c: 0};
        /** @type {Foo|Bar} */
        var U1 = any();
        U1.a;
        U1.b;
        /** @type {Baz|Bar} */
        var U2 = any();
        U2.c;
        U2.d;
        /** @type {Baz|Bat} */
        var U3 = any();
        U3.a;
        U3.b
        """;
    test(js, output);
  }

  @Test
  public void testObjectLitExtends() {
    this.enableClosurePass();

    String js =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {x: 0};
        /** @constructor
         @extends Foo */ var Bar = function(){};
        goog.inherits(Bar, Foo);
        Bar.prototype = {y: 0, z: 0};
        /** @constructor */ var Baz = function(){};
        Baz.prototype = {l: 0, m: 0, n: 0};
        (new Baz).m
        """;
    String output =
        """
        /** @constructor */ var Foo = function(){};
        Foo.prototype = {a: 0};
        /** @constructor
         @extends Foo */ var Bar = function(){};
        goog.inherits(Bar, Foo);
        Bar.prototype = {b: 0, c: 0};
        /** @constructor */ var Baz = function(){};
        Baz.prototype = {b: 0, a: 0, c: 0};
        (new Baz).a
        """;
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
        """
        /** @constructor */ function Foo(){};
        Foo.prototype = { /** @return {Bar} */ fun: function() { return new Bar(); } };
        /** @constructor */ function Bar(){};
        Bar.prototype.bazz;
        (new Foo).fun().bazz();
        """;
    String output =
        """
        /** @constructor */ function Foo(){};
        Foo.prototype = { /** @return {Bar} */ a: function() { return new Bar(); } };
        /** @constructor */ function Bar(){};
        Bar.prototype.a;
        (new Foo).a().a();
        """;
    test(js, output);
  }

  @Test
  public void testObjectLitOverlappingOriginalAndGeneratedNames() {
    test(
        """
        /** @constructor */ function Bar(){};
        Bar.prototype = {b: function(){}, a: function(){}};
        var bar = new Bar();
        bar.b();
        """,
        """
        /** @constructor */ function Bar(){};
        Bar.prototype = {a: function(){}, b: function(){}};
        var bar = new Bar();
        bar.a();
        """);
  }

  @Test
  public void testEnum() {
    testSame(
        """
        /** @enum {string} */ var Foo = {X: 'y'};
        var x = Foo.X
        """);
  }

  @Test
  public void testUnannotatedConstructorsDontCrash() {
    testSame(
        """
        function Foo() {}
        Foo.prototype.a;
        function Bar() {}
        Bar.prototype.a;
        """);
  }

  @Test
  public void testGenericPrototypeObject() {
    String js =
        """
        /**
         * @constructor
         * @template T
         */
        function Foo() {
          this.a = 1;
        }
        /** @constructor @extends {Foo<number>} */
        function Bar() {}
        /** @constructor */
        function Baz() {
          this.b = 2;
        }
        """;

    String output =
        """
        /**
         * @constructor
         * @template T
         */
        function Foo() {
          this.a = 1;
        }
        /** @constructor @extends {Foo<number>} */
        function Bar() {}
        /** @constructor */
        function Baz() {
          this.a = 2;
        }
        """;

    test(js, output);
  }

  @Test
  public void testRelatedInstanceAndPrototypePropNotAmbiguated() {
    test(
        """
        class Foo {
          constructor() {
            this.y = 0;
          }
          x() {}
        }
        """,
        """
        class Foo {
          constructor() {
            this.b = 0;
          }
          a() {}
        }
        """);
  }

  @Test
  public void testRelatedInstanceAndSuperclassPrototypePropNotAmbiguated() {
    test(
        """
        class Foo {
          x() {}
        }
        class Bar extends Foo {
          constructor() {
            this.y = 0;
          }
        }
        """,
        """
        class Foo {
          a() {}
        }
        class Bar extends Foo {
          constructor() {
            this.b = 0;
          }
        }
        """);
  }

  @Test
  public void testPropertiesWithTypesThatHaveBeenNarrowed() {
    String js =
        """
        /** @constructor */
        function Foo() {
          /** @type {?Object} */
          this.prop1 = {};
          /** @type {?Object} */
          this.prop2;
          this.headerObserver_;
        }
        Foo.prototype.m = function() {
          this.prop2 = {};
          return this.headerObserver_;
        };
        """;

    String output =
        """
        /** @constructor */
        function Foo() {
          /** @type {?Object} */
          this.d = {};
          /** @type {?Object} */
          this.b;
          this.a;
        }
        Foo.prototype.c = function() {
          this.b = {};
          return this.a;
        };
        """;

    test(js, output);
  }

  @Test
  public void testDontRenamePrototypeWithoutExterns() {
    String js =
        """
        /** @interface */
        function Foo() {}
        /** @return {!Foo} */
        Foo.prototype.foo = function() {};
        """;

    String output =
        """
        /** @interface */
        function Foo() {}
        /** @return {!Foo} */
        Foo.prototype.a = function() {};
        """;

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
        """
        /** @constructor */
        function Foo() {}
        Foo.prototype.bar = 3;
        const barName = JSCompiler_renameProperty('bar', Foo);
        """);
  }

  @Test
  public void testJSCompiler_renameProperty_oneArg_blocksAmbiguation() {
    testSame(
        """
        /** @constructor */
        function Foo() {}
        Foo.prototype.bar = 3;
        const barName = JSCompiler_renameProperty('bar');
        """);
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
        """
        class Foo { static fieldFoo = 2; }
        class Bar { static fieldBar = 2; }
        """,
        """
        class Foo { static a = 2; }
        class Bar { static a = 2; }
        """);
  }

  @Test
  public void testEs6SuperclassStaticField_notAmbiguated() {
    test(
        """
        class Foo { static fieldFoo; }
        class Bar extends Foo { static fieldBar; }
        """,
        // Since someone could access Foo.methodFoo() through Bar, make sure the fields get
        // distinct names.
        """
        class Foo { static b; }
        class Bar extends Foo { static a; }
        """);
  }

  @Test
  public void testSingleClass_withMultipleMemberFields_notAmbiguated() {
    test(
        """
        /** @unrestricted */
        class Foo {
          field = 1;
          ['hi'] = 4;
          static 1 = 2;
          hello = 5;
        }
        """,
        """
        /** @unrestricted */
        class Foo {
          a = 1;
          ['hi'] = 4;
          static 1 = 2;
          b = 5;
        }
        """);
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
        """
        class Foo { static methodFoo() {} }
        class Bar { static methodBar() {} }
        """,
        """
        class Foo { static a() {} }
        class Bar { static a() {} }
        """);
  }

  @Test
  public void testEs6SuperclassStaticMethod_notAmbiguated() {
    test(
        """
        class Foo { static methodFoo() {} }
        class Bar extends Foo { static methodBar() {} }
        """,
        // Since someone could access Foo.methodFoo() through Bar, make sure the methods get
        // distinct names.
        """
        class Foo { static b() {} }
        class Bar extends Foo { static a() {} }
        """);
  }

  @Test
  public void testEs6SubclassChain_withStaticMethods_notAmbiguated() {
    test(
        """
        class Foo { static methodFoo() { alert('foo'); } }
        class Bar extends Foo { static methodBar() {alert('bar'); } }
        class Baz extends Bar { static methodBaz() {alert('baz'); } }
        class Moo extends Baz { static methodMoo() { alert('moo'); } }
        Moo.methodFoo();
        """,
        // All four static methods must get distinct names, so that Moo.a resolves correctly to
        // Foo.a
        """
        class Foo { static a() {alert('foo'); } }
        class Bar extends Foo { static b() {alert('bar'); } }
        class Baz extends Bar { static c() {alert('baz'); } }
        class Moo extends Baz { static d() { alert('moo'); } }
        Moo.a();
        """);
  }

  @Test
  public void testEs5ClassWithExtendsChainStaticMethods_notAmbiguated() {
    test(
        """
        /** @constructor */ function Foo () {}
        Foo.methodFoo = function() { alert('foo'); };
        class Bar extends Foo { static methodBar() {alert('bar'); } }
        class Baz extends Bar { static methodBaz() {alert('baz'); } }
        class Moo extends Baz { static methodMoo() { alert('moo'); } }
        Moo.methodFoo();
        """,
        """
        /** @constructor */ function Foo () {}
        Foo.a = function() { alert('foo'); };
        class Bar extends Foo { static b() {alert('bar'); } }
        class Baz extends Bar { static c() {alert('baz'); } }
        class Moo extends Baz { static d() { alert('moo'); } }
        Moo.a();
        """);
  }

  @Test
  public void testEs5ClassWithEs5SubclassWtaticMethods_ambiguated() {
    test(
        """
        /** @constructor */ function Foo () {}
        Foo.methodFoo = function() { alert('foo'); };
        /** @constructor @extends {Foo} */ function Bar() {}
        Bar.methodBar = function() { alert('bar'); };
        """,
        """
        /** @constructor */ function Foo () {}
        Foo.a = function() { alert('foo'); };
        /** @constructor @extends {Foo} */ function Bar() {}
        Bar.a = function() { alert('bar'); };
        """);
  }

  @Test
  public void testClassWithSuperclassStaticMethodsCalledWithSuper_ambiguated() {

    test(
        """
        class Foo { static methodFoo() {} }
        class Bar extends Foo { static methodBar() { super.methodFoo(); } }
        """,
        // Since someone could access Foo.methodFoo() through Bar, make sure the methods get
        // distinct names.
        """
        class Foo { static a() {} }
        class Bar extends Foo { static b() { super.a(); } }
        """);
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
        """
        const Foo = /** @type {?} */ (class {
          method() {}
        });
        class Bar {
          method() {}
        }
        """);
  }

  @Test
  public void testObjectSetPrototypeOfIsIgnored() {
    test(
        externs("Object.setPrototypeOf = function(obj, proto) {}"),
        srcs(
            """
            /** @constructor */
            function Foo() {}
            Foo.fooMethod = () => 3;
            /** @constructor */
            function Bar() {}
            Bar.barMethod = () => 4;
            Object.setPrototypeOf(Foo, Bar);
            """),
        expected(
            """
            /** @constructor */
            function Foo() {}
            Foo.a = () => { return 3; };
            /** @constructor */
            function Bar() {}
            Bar.a = () => { return 4; };
            Object.setPrototypeOf(Foo, Bar);
            """));
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
        """
        class Foo {
          method() {}
        }
        const {method} = new Foo();
        """,
        """
        class Foo {
          a() {}
        }
        const {a: method} = new Foo();
        """);
  }

  @Test
  public void testObjectPatternDeclarationWithStringKeWithDefault_ambiguated() {
    test(
        """
        class Foo {
          method() {}
        }
        const {method = () => 3} = new Foo();
        """,
        """
        class Foo {
          a() {}
        }
        const {
          a: method = () => { return 3; }
        } = new Foo();
        """);
  }

  @Test
  public void testNestedObjectPatternWithStringKey_ambiguated() {
    test(
        """
        class Foo {
          method() {}
        }
        const {foo: {method}} = {foo: new Foo()};
        """,
        """
        class Foo {
          a() {}
        }
        // note: we rename the 'method' access but not 'foo', because we don't try ambiguating
        // properties on object literal types.
        const {foo: {a: method}} = {foo: new Foo()};
        """);
  }

  @Test
  public void testObjectPatternParameterWithStringKey_ambiguated() {
    test(
        """
        class Foo {
          method() {}
        }
        /** @param {!Foo} foo */
        function f({method}) {}
        """,
        """
        class Foo {
          a() {}
        }
        /** @param {!Foo} foo */
        function f({a: method}) {}
        """);
  }

  @Test
  public void testObjectPatternQuotedStringKey_notAmbiguated() {
    // this emits a warning for a computed property access on a struct, since property ambiguation
    // will break this code.
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);
    test(
        """
        class Foo {
          method() {}
        }
        const {'method': method} = new Foo();
        """,
        """
        class Foo {
          a() {}
        }
        const {'method': method} = new Foo();
        """);
  }

  @Test
  public void testObjectPatternComputedProperty_notAmbiguated() {
    // this emits a warning for a computed property access on a struct, since property ambiguation
    // will break this code.
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);
    test(
        """
        class Foo {
          method() {}
        }
        const {['method']: method} = new Foo();
        """,
        """
        class Foo {
          a() {}
        }
        const {['method']: method} = new Foo();
        """);
  }

  @Test
  public void testMixinPropertiesNotAmbiguated() {
    test(
        """
        class A {
          constructor() {
            this.aProp = 'aProp';
          }
        }

        /**
         * @template T
         * @param {function(new: T)} baseType
         * @return {?}
         */
        function mixinX(baseType) {
          return class extends baseType {
            constructor() {
              super();
              this.x = 'x';
            }
          };
        }
        /** @constructor @extends {A} */
        const BSuper = mixinX(A);

        class B extends BSuper {
          constructor() {
            super();
            this.bProp = 'bProp';
          }
        }
        """,
        """
        class A {
          constructor() {
            this.a = 'aProp';
          }
        }

        /**
         * @template T
         * @param {function(new: T)} baseType
         * @return {?}
         */
        function mixinX(baseType) {
          return class extends baseType {
            constructor() {
              super();
              this.x = 'x';
            }
          };
        }
        /** @constructor @extends {A} */
        const BSuper = mixinX(A);

        class B extends BSuper {
          constructor() {
            super();
        // Note that ambiguating 'bProp' => 'a' would be incorrect because BSuper extends A via
        // the mixin, so 'bProp' would conflict with 'aProp'. JSCompiler doesn't actually track
        // that B extends A. Instead it conservatively invalidates all properties on B because
        // BSuper is from a mixin, not a class literal.
            this.bProp = 'bProp';
          }
        }
        """);
  }

  @Test
  public void testMixinPrototypePropertiesNotAmbiguated() {
    test(
        """
        class A {
          aMethod() {}
        }

        function mixinX(baseType) {
          return class extends baseType {
            x() {}
          };
        }
        /** @constructor @extends {A} */
        const BSuper = mixinX(A);

        class B extends BSuper {
          bMethod() {}
        }
        """,
        """
        class A {
          a() {}
        }

        function mixinX(baseType) {
          return class extends baseType {
            x() {}
          };
        }
        /** @constructor @extends {A} */
        const BSuper = mixinX(A);

        class B extends BSuper {
        // Note that ambiguating 'bMethod' => 'a' would be incorrect because BSuper extends A
        // via the mixin, so 'bMethod' would conflict with 'aMethod'.
          bMethod() {}
        }
        """);
  }

  @Test
  public void testMixinConstructorPropertiesNotAmbiguated() {
    test(
        """
        class A {
          static aMethod() {}
        }

        /**
         * @template T
         * @param {function(new: T)} baseType
         * @return {?}
         */
        function mixinX(baseType) {
          return class extends baseType {
            static x() {}
          };
        }
        /** @constructor @extends {A} */
        const BSuper = mixinX(A);

        class B extends BSuper {
          static bMethod() {}
        }
        """,
        """
        class A {
          static a() {}
        }

        /**
         * @template T
         * @param {function(new: T)} baseType
         * @return {?}
         */
        function mixinX(baseType) {
          return class extends baseType {
            static x() {}
          };
        }
        /** @constructor @extends {A} */
        const BSuper = mixinX(A);

        class B extends BSuper {
          static bMethod() {}
        }
        """);
  }

  @Test
  public void testObjectUsedAsMapIsInvalidating() {
    ignoreWarnings(DiagnosticGroups.MISSING_PROPERTIES);
    test(
        """
        /** @param {!Object<string>} obj */
        function f(obj) {
          return obj.x;
        }
        class OtherClass {
          constructor() {
            this.y = 0;
          }
        }
        """,
        """
        /** @param {!Object<string>} obj */
        function f(obj) {
        // Can't ambiguate this 'x' to 'a' since we don't track different Object types uniquely
        // and OtherClass is also assignable to !Object<string>
          return obj.x;
        }
        class OtherClass {
          constructor() {
            this.a = 0;
          }
        }
        """);
  }

  @Test
  public void testAmbiguateMethodAddedToStringPrototype_accessedOffScalar() {
    test(
        """
        /** @return {string} */
        String.prototype.customMethod = function() { return ''; };
        'FOOBAR'.customMethod();
        class Bar {
          bar() {}
        }
        new Bar().bar();
        """,
        """
        /** @return {string} */
        String.prototype.a = function() { return ''; };
        'FOOBAR'.a();
        class Bar {
          a() {}
        }
        new Bar().a();
        """);
  }

  @Test
  public void testAmbiguateMethodAddedToStringPrototype() {
    test(
        """
        /** @return {string} */
        String.prototype.customMethod = function() { return ''; };
        new String('FOOBAR').customMethod();
        class Bar {
          bar() {}
        }
        new Bar().bar();
        """,
        """
        /** @return {string} */
        String.prototype.a = function() { return ''; };
        new String('FOOBAR').a();
        class Bar {
          a() {}
        }
        new Bar().a();
        """);
  }
}
