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

import com.google.common.collect.Multimap;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.TestErrorReporter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Unit test for the Compiler DisambiguateProperties pass.
 *
 */

public final class DisambiguatePropertiesTest extends CompilerTestCase {
  private DisambiguateProperties lastPass;

  public DisambiguatePropertiesTest() {
    parseTypeInfo = true;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    super.enableNormalize();
    super.enableTypeCheck();
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {

    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        Map<String, CheckLevel> propertiesToErrorFor = new HashMap<>();
        propertiesToErrorFor.put("foobar", CheckLevel.ERROR);

        // This must be created after type checking is run as it depends on
        // any mismatches found during checking.
        lastPass = new DisambiguateProperties(compiler, propertiesToErrorFor);

        lastPass.process(externs, root);
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testOneType1() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;";
    testSets(js, js, "{a=[[Foo.prototype]]}");
  }

  public void testOneType2() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype = {a: 0};\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;";
    String expected = "{a=[[Foo.prototype]]}";
    testSets(js, js, expected);
  }

  public void testOneType3() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype = { get a() {return  0},"
        + "                  set a(b) {} };\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;";
    String expected = "{a=[[Foo.prototype]]}";
    testSets(js, js, expected);
  }

  public void testOneType4() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype = {'a': 0};\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F['a'] = 0;";
    String expected = "{}";
    testSets(js, js, expected);
  }

  public void testPrototypeAndInstance1() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;";
    testSets(js, js, "{a=[[Foo.prototype]]}");
  }

  public void testPrototypeAndInstance2() {
    String js = ""
        + "/** @constructor @template T */ "
        + "function Foo() {"
        + "  this.a = 0;"
        + "}\n"
        + "/** @type {Foo.<string>} */\n"
        + "var f1 = new Foo;\n"
        + "f1.a = 0;"
        + "/** @type {Foo.<string>} */\n"
        + "var f2 = new Foo;\n"
        + "f2.a = 0;";
    testSets(js, js, "{a=[[Foo]]}");
  }

  public void testPrototypeAndInstance3() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "new Foo().a = 0;";
    testSets(js, js, "{a=[[Foo.prototype]]}");
  }

  public void testPrototypeAndInstance4() {
    String js = ""
        + "/** @constructor @template T */ "
        + "function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "/** @type {Foo.<string>} */\n"
        + "var f = new Foo;\n"
        + "f.a = 0;";
    testSets(js, js, "{a=[[Foo.prototype]]}");
  }

  public void testTwoTypes1() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;"
        + "/** @type {Bar} */\n"
        + "var B = new Bar;\n"
        + "B.a = 0;";
    String output = ""
        + "/** @constructor */function Foo(){}"
        + "Foo.prototype.Foo_prototype$a=0;"
        + "/** @type {Foo} */"
        + "var F=new Foo;"
        + "F.Foo_prototype$a=0;"
        + "/** @constructor */ function Bar(){}"
        + "Bar.prototype.Bar_prototype$a=0;"
        + "/** @type {Bar} */"
        + "var B=new Bar;"
        + "B.Bar_prototype$a=0";
    testSets(js, output, "{a=[[Bar.prototype], [Foo.prototype]]}");
  }

  public void testTwoTypes2() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype = {a: 0};"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype = {a: 0};"
        + "/** @type {Bar} */\n"
        + "var B = new Bar;\n"
        + "B.a = 0;";

    String output = ""
        + "/** @constructor */ function Foo(){}"
        + "Foo.prototype = {Foo_prototype$a: 0};"
        + "/** @type {Foo} */"
        + "var F=new Foo;"
        + "F.Foo_prototype$a=0;"
        + "/** @constructor */ function Bar(){}"
        + "Bar.prototype = {Bar_prototype$a: 0};"
        + "/** @type {Bar} */"
        + "var B=new Bar;"
        + "B.Bar_prototype$a=0";

    testSets(js, output, "{a=[[Bar.prototype], [Foo.prototype]]}");
  }

  public void testTwoTypes3() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype = { get a() {return  0},"
        + "                  set a(b) {} };\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype = { get a() {return  0},"
        + "                  set a(b) {} };\n"
        + "/** @type {Bar} */\n"
        + "var B = new Bar;\n"
        + "B.a = 0;";

    String output = ""
        + "/** @constructor */ function Foo(){}"
        + "Foo.prototype = { get Foo_prototype$a() {return  0},"
        + "                  set Foo_prototype$a(b) {} };\n"
        + "/** @type {Foo} */\n"
        + "var F=new Foo;"
        + "F.Foo_prototype$a=0;"
        + "/** @constructor */ function Bar(){}"
        + "Bar.prototype = { get Bar_prototype$a() {return  0},"
        + "                  set Bar_prototype$a(b) {} };\n"
        + "/** @type {Bar} */\n"
        + "var B=new Bar;"
        + "B.Bar_prototype$a=0";

    testSets(js, output, "{a=[[Bar.prototype], [Foo.prototype]]}");
  }

  public void testTwoTypes4() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype = {a: 0};"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype = {'a': 0};"
        + "/** @type {Bar} */\n"
        + "var B = new Bar;\n"
        + "B['a'] = 0;";

    String output = ""
        + "/** @constructor */ function Foo(){}"
        + "Foo.prototype = {a: 0};"
        + "/** @type {Foo} */ var F=new Foo;"
        + "F.a=0;"
        + "/** @constructor */ function Bar(){}"
        + "Bar.prototype = {'a': 0};"
        + "/** @type {Bar} */ var B=new Bar;"
        + "B['a']=0";

    testSets(js, output, "{a=[[Foo.prototype]]}");
  }

  public void testTwoTypes5() {
    String js = ""
        + "/** @constructor @template T */ function Foo() { this.a = 0; }\n"
        + "/** @type {Foo<string>} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;"
        + "/** @constructor @template T */ function Bar() { this.a = 0; }\n"
        + "/** @type {Bar<string>} */\n"
        + "var B = new Bar;\n"
        + "B.a = 0;";
    String output = ""
        + "/** @constructor @template T */ function Foo(){ this.Foo$a = 0; }"
        + "/** @type {Foo<string>} */"
        + "var F=new Foo;"
        + "F.Foo$a=0;"
        + "/** @constructor @template T */ function Bar(){ this.Bar$a = 0; }"
        + "/** @type {Bar<string>} */ var B=new Bar;"
        + "B.Bar$a=0";
    testSets(js, output, "{a=[[Bar], [Foo]]}");
  }

  public void testTwoFields() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;"
        + "Foo.prototype.b = 0;"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;"
        + "F.b = 0;";
    String output = ""
        + "/** @constructor */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.a=0;\n"
        + "Foo.prototype.b=0;"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;\n"
        + "F.b = 0";
    testSets(js, output, "{a=[[Foo.prototype]], b=[[Foo.prototype]]}");
  }

  public void testTwoSeparateFieldsTwoTypes() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;"
        + "Foo.prototype.b = 0;"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;"
        + "F.b = 0;"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;"
        + "Bar.prototype.b = 0;"
        + "/** @type {Bar} */\n"
        + "var B = new Bar;\n"
        + "B.a = 0;"
        + "B.b = 0;";
    String output = ""
        + "/** @constructor */ function Foo(){}"
        + "Foo.prototype.Foo_prototype$a=0;"
        + "Foo.prototype.Foo_prototype$b=0;"
        + "/** @type {Foo} */ var F=new Foo;"
        + "F.Foo_prototype$a=0;"
        + "F.Foo_prototype$b=0;"
        + "/** @constructor */ function Bar(){}"
        + "Bar.prototype.Bar_prototype$a=0;"
        + "Bar.prototype.Bar_prototype$b=0;"
        + "/** @type {Bar} */ var B=new Bar;"
        + "B.Bar_prototype$a=0;"
        + "B.Bar_prototype$b=0";
    testSets(js, output, "{a=[[Bar.prototype], [Foo.prototype]],"
                                + " b=[[Bar.prototype], [Foo.prototype]]}");
  }

  public void testUnionType() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;"
        + "/** @type {Bar|Foo} */\n"
        + "var B = new Bar;\n"
        + "B.a = 0;\n"
        + "B = new Foo;\n"
        + "B.a = 0;\n"
        + "/** @constructor */ function Baz() {}\n"
        + "Baz.prototype.a = 0;\n";
    testSets(js, "{a=[[Bar.prototype, Foo.prototype], [Baz.prototype]]}");
  }

  public void testIgnoreUnknownType() {
    String js = ""
        + "/** @constructor */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.blah = 3;\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.blah = 0;\n"
        + "var U = function() { return {} };\n"
        + "U().blah();";
    String expected = ""
        + "/** @constructor */ function Foo(){}"
        + "Foo.prototype.blah=3;"
        + "/** @type {Foo} */"
        + "var F = new Foo;F.blah=0;"
        + "var U=function(){return{}};U().blah()";
    testSets(js, expected, "{}");
  }

  public void testIgnoreUnknownType1() {
    String js = ""
        + "/** @constructor */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.blah = 3;\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.blah = 0;\n"
        + "/** @return {Object} */\n"
        + "var U = function() { return {} };\n"
        + "U().blah();";
    String expected = ""
        + "/** @constructor */ function Foo(){}"
        + "Foo.prototype.blah=3;"
        + "/** @type {Foo} */ var F = new Foo;"
        + "F.blah=0;"
        + "/** @return {Object} */"
        + "var U=function(){return{}};"
        + "U().blah()";
    testSets(js, expected, "{blah=[[Foo.prototype]]}");
  }

  public void testIgnoreUnknownType2() {
    String js = ""
        + "/** @constructor */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.blah = 3;\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.blah = 0;\n"
        + "/** @constructor */\n"
        + "function Bar() {}\n"
        + "Bar.prototype.blah = 3;\n"
        + "/** @return {Object} */\n"
        + "var U = function() { return {} };\n"
        + "U().blah();";
    String expected = ""
        + "/** @constructor */ function Foo(){}"
        + "Foo.prototype.blah=3;"
        + "/** @type {Foo} */"
        + "var F = new Foo;"
        + "F.blah=0;"
        + "/** @constructor */ function Bar(){}"
        + "Bar.prototype.blah=3;"
        + "/** @return {Object} */"
        + "var U=function(){return{}};U().blah()";
    testSets(js, expected, "{}");
  }

  public void testUnionTypeTwoFields() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "Foo.prototype.b = 0;\n"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;\n"
        + "Bar.prototype.b = 0;\n"
        + "/** @type {Foo|Bar} */\n"
        + "var B = new Bar;\n"
        + "B.a = 0;\n"
        + "B.b = 0;\n"
        + "B = new Foo;\n"
        + "/** @constructor */ function Baz() {}\n"
        + "Baz.prototype.a = 0;\n"
        + "Baz.prototype.b = 0;\n";
    testSets(js, "{a=[[Bar.prototype, Foo.prototype], [Baz.prototype]],"
                 + " b=[[Bar.prototype, Foo.prototype], [Baz.prototype]]}");
  }

  public void testCast() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;"
        + "/** @type {Foo|Bar} */\n"
        + "var F = new Foo;\n"
        + "(/** @type {Bar} */(F)).a = 0;";
    String output = ""
        + "/** @constructor */ function Foo(){}\n"
        + "Foo.prototype.Foo_prototype$a=0;\n"
        + "/** @constructor */ function Bar(){}\n"
        + "Bar.prototype.Bar_prototype$a=0;\n"
        + "/** @type {Foo|Bar} */\n"
        + "var F=new Foo;\n"
        + "/** @type {Bar} */ (F).Bar_prototype$a=0;";
    testSets(js, output, "{a=[[Bar.prototype], [Foo.prototype]]}");
  }

  public void testConstructorFields() {
    String js = ""
      + "/** @constructor */\n"
      + "var Foo = function() { this.a = 0; };\n"
      + "/** @constructor */ function Bar() {}\n"
      + "Bar.prototype.a = 0;"
      + "new Foo";
    String output = ""
        + "/** @constructor */ var Foo=function(){this.Foo$a=0};"
        + "/** @constructor */ function Bar(){}"
        + "Bar.prototype.Bar_prototype$a=0;"
        + "new Foo";
    testSets(js, output, "{a=[[Bar.prototype], [Foo]]}");
  }

  public void testStaticProperty() {
    String js = ""
      + "/** @constructor */ function Foo() {} \n"
      + "/** @constructor */ function Bar() {}\n"
      + "Foo.a = 0;"
      + "Bar.a = 0;";
    String output = ""
        + "/** @constructor */ function Foo(){}"
        + "/** @constructor */ function Bar(){}"
        + "Foo.function__new_Foo___undefined$a = 0;"
        + "Bar.function__new_Bar___undefined$a = 0;";

    testSets(js, output, "{a=[[function (new:Bar): undefined]," +
    " [function (new:Foo): undefined]]}");
  }

  public void testSupertypeWithSameField() {
    String js = ""
      + "/** @constructor */ function Foo() {}\n"
      + "Foo.prototype.a = 0;\n"
      + "/** @constructor\n* @extends {Foo} */ function Bar() {}\n"
      + "/** @override */\n"
      + "Bar.prototype.a = 0;\n"
      + "/** @type {Bar} */\n"
      + "var B = new Bar;\n"
      + "B.a = 0;"
      + "/** @constructor */ function Baz() {}\n"
      + "Baz.prototype.a = function(){};\n";

    String output = ""
        + "/** @constructor */ function Foo(){}"
        + "Foo.prototype.Foo_prototype$a=0;"
        + "/** @constructor @extends {Foo} */ function Bar(){}"
        + "/** @override */"
        + "Bar.prototype.Foo_prototype$a=0;"
        + "/** @type {Bar} */"
        + "var B = new Bar;"
        + "B.Foo_prototype$a=0;"
        + "/** @constructor */ function Baz(){}Baz.prototype.Baz_prototype$a=function(){};";
    testSets(js, output, "{a=[[Baz.prototype], [Foo.prototype]]}");
  }

  public void testScopedType() {
    String js = ""
        + "var g = {};\n"
        + "/** @constructor */ g.Foo = function() {}\n"
        + "g.Foo.prototype.a = 0;"
        + "/** @constructor */ g.Bar = function() {}\n"
        + "g.Bar.prototype.a = 0;";
    String output = ""
        + "var g={};"
        + "/** @constructor */ g.Foo=function(){};"
        + "g.Foo.prototype.g_Foo_prototype$a=0;"
        + "/** @constructor */ g.Bar=function(){};"
        + "g.Bar.prototype.g_Bar_prototype$a=0;";
    testSets(js, output, "{a=[[g.Bar.prototype], [g.Foo.prototype]]}");
  }

  public void testUnresolvedType() {
    // NOTE(nicksantos): This behavior seems very wrong to me.
    String js = ""
        + "var g = {};"
        + "/** @constructor \n @extends {?} */ "
        + "var Foo = function() {};\n"
        + "Foo.prototype.a = 0;"
        + "/** @constructor */ var Bar = function() {};\n"
        + "Bar.prototype.a = 0;";
    String output = ""
        + "var g={};"
        + "/** @constructor @extends {?} */ var Foo=function(){};"
        + "Foo.prototype.Foo_prototype$a=0;"
        + "/** @constructor */ var Bar=function(){};"
        + "Bar.prototype.Bar_prototype$a=0;";

    setExpectParseWarningsThisTest();
    testSets(BaseJSTypeTestCase.ALL_NATIVE_EXTERN_TYPES, js,
        output, "{a=[[Bar.prototype], [Foo.prototype]]}");
  }

  public void testNamedType() {
    String js = ""
        + "var g = {};"
        + "/** @constructor \n @extends {g.Late} */ var Foo = function() {}\n"
        + "Foo.prototype.a = 0;"
        + "/** @constructor */ var Bar = function() {}\n"
        + "Bar.prototype.a = 0;"
        + "/** @constructor */ g.Late = function() {}";
    String output = ""
        + "var g={};"
        + "/** @constructor @extends {g.Late} */ var Foo=function(){};"
        + "Foo.prototype.Foo_prototype$a=0;"
        + "/** @constructor */ var Bar=function(){};"
        + "Bar.prototype.Bar_prototype$a=0;"
        + "/** @constructor */ g.Late = function(){}";
    testSets(js, output, "{a=[[Bar.prototype], [Foo.prototype]]}");
  }

  public void testUnknownType() {
    String js = ""
        + "/** @constructor */ var Foo = function() {};\n"
        + "/** @constructor */ var Bar = function() {};\n"
        + "/** @return {?} */ function fun() {}\n"
        + "Foo.prototype.a = fun();\n"
        + "fun().a;\n"
        + "Bar.prototype.a = 0;";
    testSets(js, js, "{}");
  }

  // When objects flow to untyped code, it is the programmer's responsibility to
  // use them in a type-safe way, otherwise disambiguation will be wrong.
  public void testUntypedCodeWrongDisambiguation1() {
    String js = ""
        + "/** @constructor */\n"
        + "function Foo() { this.p1 = 0; }\n"
        + "/** @constructor */\n"
        + "function Bar() { this.p1 = 1; }\n"
        + "var arr = [new Foo, new Bar];\n"
        + "var /** !Foo */ z = arr[1];\n"
        + "z.p1;\n";
    String output = ""
        + "/** @constructor */ function Foo() { this.Foo$p1 = 0; }\n"
        + "/** @constructor */ function Bar() { this.Bar$p1 = 1; }\n"
        + "var arr = [new Foo, new Bar];\n"
        + "var /** !Foo */z = arr[1];\n"
        + "z.Foo$p1;\n";
    testSets(js, output, "{p1=[[Bar], [Foo]]}");
  }

  // When objects flow to untyped code, it is the programmer's responsibility to
  // use them in a type-safe way, otherwise disambiguation will be wrong.
  public void testUntypedCodeWrongDisambiguation2() {
    String js = ""
        + "/** @constructor */\n"
        + "function Foo() { this.p1 = 0; }\n"
        + "/** @constructor */\n"
        + "function Bar() { this.p1 = 1; }\n"
        + "function select(cond, x, y) { return cond ? x : y; }\n"
        + "/**\n"
        + " * @param {!Foo} x\n"
        + " * @param {!Bar} y\n"
        + " * @return {!Foo}\n"
        + " */\n"
        + "function f(x, y) {\n"
        + "  var /** !Foo */ z = select(false, x, y);\n"
        + "  return z;\n"
        + "}\n"
        + "f(new Foo, new Bar).p1;\n";
    String output = ""
        + "/** @constructor */ function Foo() { this.Foo$p1 = 0; }\n"
        + "/** @constructor */ function Bar() { this.Bar$p1 = 1; }\n"
        + "function select(cond, x, y) { return cond ? x : y; }\n"
        + "/**\n"
        + " * @param {!Foo} x\n"
        + " * @param {!Bar} y\n"
        + " * @return {!Foo}\n"
        + " */\n"
        + "function f(x, y) {\n"
        + "  var /** !Foo */ z = select(false, x, y);\n"
        + "  return z;\n"
        + "}\n"
        + "f(new Foo, new Bar).Foo$p1;\n";
    testSets(js, output, "{p1=[[Bar], [Foo]]}");
  }

  public void testEnum() {
    String js = ""
        + "/** @enum {string} */ var En = {\n"
        + "  A: 'first',\n"
        + "  B: 'second'\n"
        + "};\n"
        + "var EA = En.A;\n"
        + "var EB = En.B;\n"
        + "/** @constructor */ function Foo(){};\n"
        + "Foo.prototype.A = 0;\n"
        + "Foo.prototype.B = 0;\n";
    String output = ""
        + "/** @enum {string} */ var En={A:'first',B:'second'};"
        + "var EA=En.A;"
        + "var EB=En.B;"
        + "/** @constructor */ function Foo(){};"
        + "Foo.prototype.Foo_prototype$A=0;"
        + "Foo.prototype.Foo_prototype$B=0";
    testSets(js, output, "{A=[[Foo.prototype]], B=[[Foo.prototype]]}");
  }

  public void testEnumOfObjects() {
    String js = ""
        + "/** @constructor */ function Formatter() {}"
        + "Formatter.prototype.format = function() {};"
        + "/** @constructor */ function Unrelated() {}"
        + "Unrelated.prototype.format = function() {};"
        + "/** @enum {!Formatter} */ var Enum = {\n"
        + "  A: new Formatter()\n"
        + "};\n"
        + "Enum.A.format();\n";
    String output = ""
        + "/** @constructor */ function Formatter() {}"
        + "Formatter.prototype.Formatter_prototype$format = function() {};"
        + "/** @constructor */ function Unrelated() {}"
        + "Unrelated.prototype.Unrelated_prototype$format = function() {};"
        + "/** @enum {!Formatter} */ var Enum = {\n"
        + "  A: new Formatter()\n"
        + "};\n"
        + "Enum.A.Formatter_prototype$format();\n";
    testSets(js, output, "{format=[[Formatter.prototype], [Unrelated.prototype]]}");
  }

  public void testEnumOfObjects2() {
    String js = ""
        + "/** @constructor */ function Formatter() {}"
        + "Formatter.prototype.format = function() {};"
        + "/** @constructor */ function Unrelated() {}"
        + "Unrelated.prototype.format = function() {};"
        + "/** @enum {?Formatter} */ var Enum = {\n"
        + "  A: new Formatter(),\n"
        + "  B: new Formatter()\n"
        + "};\n"
        + "function f() {\n"
        + "  var formatter = window.toString() ? Enum.A : Enum.B;\n"
        + "  formatter.format();\n"
        + "}";
    String output = ""
        + "/** @constructor */ function Formatter() {}"
        + "Formatter.prototype.format = function() {};"
        + "/** @constructor */ function Unrelated() {}"
        + "Unrelated.prototype.format = function() {};"
        + "/** @enum {?Formatter} */ var Enum = {\n"
        + "  A: new Formatter(),\n"
        + "  B: new Formatter()\n"
        + "};\n"
        + "function f() {\n"
        + "  var formatter = window.toString() ? Enum.A : Enum.B;\n"
        + "  formatter.format();\n"
        + "}";
    testSets(js, output, "{}");
  }

  public void testEnumOfObjects3() {
    String js = ""
        + "/** @constructor */ function Formatter() {}"
        + "Formatter.prototype.format = function() {};"
        + "/** @constructor */ function Unrelated() {}"
        + "Unrelated.prototype.format = function() {};"
        + "/** @enum {!Formatter} */ var Enum = {\n"
        + "  A: new Formatter(),\n"
        + "  B: new Formatter()\n"
        + "};\n"
        + "/** @enum {!Enum} */ var SubEnum = {\n"
        + "  C: Enum.A\n"
        + "};\n"
        + "function f() {\n"
        + "  var formatter = SubEnum.C\n"
        + "  formatter.format();\n"
        + "}";
    String output = ""
        + "/** @constructor */ function Formatter() {}"
        + "Formatter.prototype.Formatter_prototype$format = function() {};"
        + "/** @constructor */ function Unrelated() {}"
        + "Unrelated.prototype.Unrelated_prototype$format = function() {};"
        + "/** @enum {!Formatter} */ var Enum = {\n"
        + "  A: new Formatter(),\n"
        + "  B: new Formatter()\n"
        + "};\n"
        + "/** @enum {!Enum} */ var SubEnum = {\n"
        + "  C: Enum.A\n"
        + "};\n"
        + "function f() {\n"
        + "  var formatter = SubEnum.C\n"
        + "  formatter.Formatter_prototype$format();\n"
        + "}";
    testSets(js, output, "{format=[[Formatter.prototype], [Unrelated.prototype]]}");
  }

  public void testUntypedExterns() {
    String externs =
        BaseJSTypeTestCase.ALL_NATIVE_EXTERN_TYPES
        + "var window;"
        + "window.alert = function() {x};";
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "Foo.prototype.alert = 0;\n"
        + "Foo.prototype.window = 0;\n"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;\n"
        + "Bar.prototype.alert = 0;\n"
        + "Bar.prototype.window = 0;\n"
        + "window.alert();";
    String output = ""
        + "/** @constructor */ function Foo(){}"
        + "Foo.prototype.Foo_prototype$a=0;"
        + "Foo.prototype.alert=0;"
        + "Foo.prototype.Foo_prototype$window=0;"
        + "/** @constructor */ function Bar(){}"
        + "Bar.prototype.Bar_prototype$a=0;"
        + "Bar.prototype.alert=0;"
        + "Bar.prototype.Bar_prototype$window=0;"
        + "window.alert();";

    testSets(externs, js, output, "{a=[[Bar.prototype], [Foo.prototype]]"
             + ", window=[[Bar.prototype], [Foo.prototype]]}");
  }

  public void testUnionTypeInvalidation() {
    String externs = ""
        + "/** @constructor */ function Baz() {}"
        + "Baz.prototype.a";
    String js = ""
        + "/** @constructor */ function Ind() {this.a=0}\n"
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;\n"
        + "/** @type {Foo|Bar} */\n"
        + "var F = new Foo;\n"
        + "F.a = 1;\n"
        + "F = new Bar;\n"
        + "/** @type {Baz} */\n"
        + "var Z = new Baz;\n"
        + "Z.a = 1;\n"
        + "/** @type {Bar|Baz} */\n"
        + "var B = new Baz;\n"
        + "B.a = 1;\n"
        + "B = new Bar;\n";
    // Only the constructor field a of Ind is renamed, as Foo is related to Baz
    // through Bar in the unions Bar|Baz and Foo|Bar.
    String output = ""
        + "/** @constructor */ function Ind() { this.Ind$a = 0; }\n"
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;\n"
        + "/** @type {Foo|Bar} */\n"
        + "var F = new Foo;\n"
        + "F.a = 1;\n"
        + "F = new Bar;\n"
        + "/** @type {Baz} */\n"
        + "var Z = new Baz;\n"
        + "Z.a = 1;\n"
        + "/** @type {Bar|Baz} */"
        + "var B = new Baz;"
        + "B.a = 1;"
        + "B = new Bar;";
    testSets(externs, js, output, "{a=[[Ind]]}");
  }

  public void testUnionAndExternTypes() {
    String externs = ""
      + "/** @constructor */ function Foo() { }"
      + "Foo.prototype.a = 4;\n";
    String js = ""
      + "/** @constructor */ function Bar() { this.a = 2; }\n"
      + "/** @constructor */ function Baz() { this.a = 3; }\n"
      + "/** @constructor */ function Buz() { this.a = 4; }\n"
      + "/** @constructor */ function T1() { this.a = 3; }\n"
      + "/** @constructor */ function T2() { this.a = 3; }\n"
      + "/** @type {Bar|Baz} */ var b;\n"
      + "/** @type {Baz|Buz} */ var c;\n"
      + "/** @type {Buz|Foo} */ var d;\n"
      + "b.a = 5; c.a = 6; d.a = 7;";
    String output = ""
      + "/** @constructor */ function Bar() { this.a = 2; }\n"
      + "/** @constructor */ function Baz() { this.a = 3; }\n"
      + "/** @constructor */ function Buz() { this.a = 4; }\n"
      + "/** @constructor */ function T1() { this.T1$a = 3; }\n"
      + "/** @constructor */ function T2() { this.T2$a = 3; }\n"
      + "/** @type {Bar|Baz} */ var b;\n"
      + "/** @type {Baz|Buz} */ var c;\n"
      + "/** @type {Buz|Foo} */ var d;\n"
      + "b.a = 5; c.a = 6; d.a = 7;";

    // We are testing the skipping of multiple types caused by unionizing with
    // extern types.
    testSets(externs, js, output, "{a=[[T1], [T2]]}");
  }

  public void testTypedExterns() {
    String externs = ""
        + "/** @constructor */ function Window() {};\n"
        + "Window.prototype.alert;"
        + "/** @type {Window} */"
        + "var window;";
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.alert = 0;\n"
        + "window.alert('blarg');";
    String output = ""
        + "/** @constructor */ function Foo(){}"
        + "Foo.prototype.Foo_prototype$alert=0;"
        + "window.alert('blarg');";
    testSets(externs, js, output, "{alert=[[Foo.prototype]]}");
  }

  public void testSubtypesWithSameField() {
    String js = ""
        + "/** @constructor */ function Top() {}\n"
        + "/** @constructor \n@extends Top*/ function Foo() {}\n"
        + "Foo.prototype.a;\n"
        + "/** @constructor \n@extends Top*/ function Bar() {}\n"
        + "Bar.prototype.a;\n"
        + "/** @param {Top} top */"
        + "function foo(top) {\n"
        + "  var x = top.a;\n"
        + "}\n"
        + "foo(new Foo);\n"
        + "foo(new Bar);\n";
    testSets(js, "{}");
  }

  public void testSupertypeReferenceOfSubtypeProperty() {
    String externs = ""
        + "/** @constructor */ function Ext() {}"
        + "Ext.prototype.a;";
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}\n"
        + "Bar.prototype.a;\n"
        + "/** @param {Foo} foo */"
        + "function foo(foo) {\n"
        + "  var x = foo.a;\n"
        + "}\n";
    String result = ""
        + "/** @constructor */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}\n"
        + "Bar.prototype.Bar_prototype$a;\n"
        + "/** @param {Foo} foo */\n"
        + "function foo(foo$$1) {\n"
        + "  var x = foo$$1.Bar_prototype$a;\n"
        + "}\n";
    testSets(externs, js, result, "{a=[[Bar.prototype]]}");
  }

  public void testObjectLiteralNotRenamed() {
    String js = ""
        + "var F = {a:'a', b:'b'};"
        + "F.a = 'z';";
    testSets(js, js, "{}");
  }

  public void testObjectLiteralReflected() {
    String js = ""
        + "var goog = {};"
        + "goog.reflect = {};"
        + "goog.reflect.object = function(x, y) { return y; };"
        + "/** @constructor */ function F() {}"
        + "/** @type {number} */ F.prototype.foo = 3;"
        + "/** @constructor */ function G() {}"
        + "/** @type {number} */ G.prototype.foo = 3;"
        + "goog.reflect.object(F, {foo: 5});";
    String result = ""
        + "var goog = {};"
        + "goog.reflect = {};"
        + "goog.reflect.object = function(x, y) { return y; };"
        + "/** @constructor */ function F() {}"
        + "/** @type {number} */ F.prototype.F_prototype$foo = 3;"
        + "/** @constructor */ function G() {}"
        + "/** @type {number} */ G.prototype.G_prototype$foo = 3;"
        + "goog.reflect.object(F, {F_prototype$foo: 5});";
    testSets(js, result, "{foo=[[F.prototype], [G.prototype]]}");
  }

  public void testObjectLiteralLends() {
    String js = ""
        + "var mixin = function(x) { return x; };"
        + "/** @constructor */ function F() {}"
        + "/** @type {number} */ F.prototype.foo = 3;"
        + "/** @constructor */ function G() {}"
        + "/** @type {number} */ G.prototype.foo = 3;"
        + "mixin(/** @lends {F.prototype} */ ({foo: 5}));";
    String result = ""
        + "var mixin = function(x) { return x; };"
        + "/** @constructor */ function F() {}"
        + "/** @type {number} */ F.prototype.F_prototype$foo = 3;"
        + "/** @constructor */ function G() {}"
        + "/** @type {number} */ G.prototype.G_prototype$foo = 3;"
        + "mixin(/** @lends {F.prototype} */ ({F_prototype$foo: 5}));";
    testSets(js, result, "{foo=[[F.prototype], [G.prototype]]}");
  }

  public void testClosureInherits() {
    String js = ""
        + "var goog = {};"
        + "/** @param {Function} childCtor Child class.\n"
        + " * @param {Function} parentCtor Parent class. */\n"
        + "goog.inherits = function(childCtor, parentCtor) {\n"
        + "  /** @constructor */\n"
        + "  function tempCtor() {};\n"
        + "  tempCtor.prototype = parentCtor.prototype;\n"
        + "  childCtor.superClass_ = parentCtor.prototype;\n"
        + "  childCtor.prototype = new tempCtor();\n"
        + "  childCtor.prototype.constructor = childCtor;\n"
        + "};"
        + "/** @constructor */ function Top() {}\n"
        + "Top.prototype.f = function() {};"
        + "/** @constructor \n@extends Top*/ function Foo() {}\n"
        + "goog.inherits(Foo, Top);\n"
        + "/** @override */\n"
        + "Foo.prototype.f = function() {"
        + "  Foo.superClass_.f();"
        + "};\n"
        + "/** @constructor \n* @extends Foo */ function Bar() {}\n"
        + "goog.inherits(Bar, Foo);\n"
        + "/** @override */\n"
        + "Bar.prototype.f = function() {"
        + "  Bar.superClass_.f();"
        + "};\n"
        + "(new Bar).f();\n";
    testSets(js, "{f=[[Top.prototype]]}");
  }

  public void testSkipNativeFunctionMethod() {
    String externs = ""
        + "/** @constructor \n @param {*} var_args */"
        + "function Function(var_args) {}"
        + "Function.prototype.call = function() {};";
    String js = ""
        + "/** @constructor */ function Foo(){};"
        + "/** @constructor\n @extends Foo */"
        + "function Bar() { Foo.call(this); };"; // call should not be renamed
    testSame(externs, js, null);
  }

  public void testSkipNativeObjectMethod() {
    String externs = ""
        + "/**"
        + " * @constructor\n"
        + " * @param {*} opt_v\n"
        + " * @return {!Object}\n"
        + " */\n"
        + "function Object(opt_v) {}"
        + "Object.prototype.hasOwnProperty;";
    String js = ""
        + "/** @constructor */ function Foo(){};"
        + "(new Foo).hasOwnProperty('x');";
    testSets(externs, js, js, "{}");
  }

  public void testExtendNativeType() {
    String externs = ""
        + "/** @constructor \n @return {string} */"
        + "function Date(opt_1, opt_2, opt_3, opt_4, opt_5, opt_6, opt_7) {}"
        + "/** @override */ Date.prototype.toString = function() {}";
    String js = ""
        + "/** @constructor\n @extends {Date} */ function SuperDate() {};\n"
        + "(new SuperDate).toString();";
    testSets(externs, js, js, "{}");
  }

  public void testStringFunction() {
    // Extern functions are not renamed, but user functions on a native
    // prototype object are.
    String externs = "/**@constructor\n@param {*} opt_str \n @return {string}*/"
         + "function String(opt_str) {};\n"
         + "/** @override \n @return {string} */\n"
         + "String.prototype.toString = function() { };\n";
    String js = ""
         + "/** @constructor */ function Foo() {};\n"
         + "Foo.prototype.foo = function() {};\n"
         + "String.prototype.foo = function() {};\n"
         + "var a = 'str'.toString().foo();\n";
    String output = ""
         + "/** @constructor */ function Foo() {};\n"
         + "Foo.prototype.Foo_prototype$foo = function() {};\n"
         + "String.prototype.String_prototype$foo = function() {};\n"
         + "var a = 'str'.toString().String_prototype$foo();\n";

    testSets(externs, js, output, "{foo=[[Foo.prototype], [String.prototype]]}");
  }

  public void testUnusedTypeInExterns() {
    String externs = ""
        + "/** @constructor */ function Foo() {};\n"
        + "Foo.prototype.a";
    String js = ""
        + "/** @constructor */ function Bar() {};\n"
        + "Bar.prototype.a;"
        + "/** @constructor */ function Baz() {};\n"
        + "Baz.prototype.a;";
    String output = ""
        + "/** @constructor */ function Bar() {};\n"
        + "Bar.prototype.Bar_prototype$a;"
        + "/** @constructor */ function Baz() {};\n"
        + "Baz.prototype.Baz_prototype$a";
    testSets(externs, js, output, "{a=[[Bar.prototype], [Baz.prototype]]}");
  }

  public void testInterface() {
    String js = ""
        + "/** @interface */ function I() {};\n"
        + "I.prototype.a;\n"
        + "/** @constructor \n @implements {I} */ function Foo() {};\n"
        + "Foo.prototype.a;\n"
        + "/** @type {I} */\n"
        + "var F = new Foo;"
        + "var x = F.a;";
    testSets(js, "{a=[[Foo.prototype, I.prototype]]}");
  }

  public void testInterfaceOfSuperclass() {
    String js = ""
        + "/** @interface */ function I() {};\n"
        + "I.prototype.a;\n"
        + "/** @constructor \n @implements {I} */ function Foo() {};\n"
        + "Foo.prototype.a;\n"
        + "/** @constructor \n @extends Foo */ function Bar() {};\n"
        + "Bar.prototype.a;\n"
        + "/** @type {Bar} */\n"
        + "var B = new Bar;"
        + "B.a = 0";
    testSets(js, "{a=[[Foo.prototype, I.prototype]]}");
  }

  public void testInterfaceOfSuperclass2() {
    String js = LINE_JOINER.join(
        "/** @const */ var goog = {};",
        "goog.abstractMethod = function(var_args) {};",
        "/** @interface */ function I() {}",
        "I.prototype.a = function(x) {};",
        "/** @constructor @implements {I} */ function Foo() {}",
        "/** @override */ Foo.prototype.a = goog.abstractMethod;",
        "/** @constructor @extends Foo */ function Bar() {}",
        "/** @override */ Bar.prototype.a = function(x) {};");
    testSets(js, "{a=[[Foo.prototype, I.prototype]]}");
  }

  public void testTwoInterfacesWithSomeInheritance() {
    String js = ""
        + "/** @interface */ function I() {};\n"
        + "I.prototype.a;\n"
        + "/** @interface */ function I2() {};\n"
        + "I2.prototype.a;\n"
        + "/** @constructor \n @implements {I} */ function Foo() {};\n"
        + "Foo.prototype.a;\n"
        + "/** @constructor \n @extends {Foo} \n @implements {I2}*/\n"
        + "function Bar() {};\n"
        + "Bar.prototype.a;\n"
        + "/** @type {Bar} */\n"
        + "var B = new Bar;"
        + "B.a = 0";
    testSets(js, "{a=[[Foo.prototype, I.prototype, I2.prototype]]}");
  }

  public void testInvalidatingInterface() {
    String js = ""
        + "/** @interface */ function I2() {};\n"
        + "I2.prototype.a;\n"
        + "/** @constructor */ function Bar() {}\n"
        + "/** @type {I} */\n"
        + "var i = new Bar;\n" // Make I invalidating
        + "/** @constructor \n @implements {I} \n @implements {I2} */"
        + "function Foo() {};\n"
        + "/** @override */\n"
        + "Foo.prototype.a = 0;\n"
        + "(new Foo).a = 0;"
        + "/** @interface */ function I() {};\n"
        + "I.prototype.a;\n";
    testSets(js, "{}", TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testMultipleInterfaces() {
    String js = ""
        + "/** @interface */ function I() {};\n"
        + "/** @interface */ function I2() {};\n"
        + "I2.prototype.a;\n"
        + "/** @constructor \n @implements {I} \n @implements {I2} */"
        + "function Foo() {};\n"
        + "/** @override */"
        + "Foo.prototype.a = 0;\n"
        + "(new Foo).a = 0";
    testSets(js, "{a=[[Foo.prototype, I2.prototype]]}");
  }

  public void testInterfaceWithSupertypeImplementor() {
    String js = ""
        + "/** @interface */ function C() {}\n"
        + "C.prototype.foo = function() {};\n"
        + "/** @constructor */ function A (){}\n"
        + "A.prototype.foo = function() {};\n"
        + "/** @constructor \n @implements {C} \n @extends {A} */\n"
        + "function B() {}\n"
        + "/** @type {C} */ var b = new B();\n"
        + "b.foo();\n";
    testSets(js, "{foo=[[A.prototype, C.prototype]]}");
  }

  public void testSuperInterface() {
    String js = ""
        + "/** @interface */ function I() {};\n"
        + "I.prototype.a;\n"
        + "/** @interface \n @extends I */ function I2() {};\n"
        + "/** @constructor \n @implements {I2} */"
        + "function Foo() {};\n"
        + "/** @override */\n"
        + "Foo.prototype.a = 0;\n"
        + "(new Foo).a = 0";
    testSets(js, "{a=[[Foo.prototype, I.prototype]]}");
  }

  public void testInterfaceUnionWithCtor() {
    String js = ""
        + "/** @interface */ function I() {};\n"
        + "/** @type {!Function} */ I.prototype.addEventListener;\n"
        + "/** @constructor \n * @implements {I} */ function Impl() {};\n"
        + "/** @type {!Function} */ Impl.prototype.addEventListener;"
        + "/** @constructor */ function C() {};\n"
        + "/** @type {!Function} */ C.prototype.addEventListener;"
        + "/** @param {C|I} x */"
        + "function f(x) { x.addEventListener(); };\n"
        + "f(new C()); f(new Impl());";

    testSets(js, js, "{addEventListener=[[C.prototype, I.prototype, Impl.prototype]]}");
  }

  public void testExternInterfaceUnionWithCtor() {
    String externs = ""
        + "/** @interface */ function I() {};\n"
        + "/** @type {!Function} */ I.prototype.addEventListener;\n"
        + "/** @constructor \n * @implements {I} */ function Impl() {};\n"
        + "/** @type {!Function} */ Impl.prototype.addEventListener;";

    String js = ""
        + "/** @constructor */ function C() {};\n"
        + "/** @type {!Function} */ C.prototype.addEventListener;"
        + "/** @param {C|I} x */"
        + "function f(x) { x.addEventListener(); };\n"
        + "f(new C()); f(new Impl());";

    testSets(externs, js, js, "{}");
  }

  public void testAliasedTypeIsNotDisambiguated() {
    String js = LINE_JOINER.join(
        "/** @return {SecondAlias} */",
        "function f() {}",
        "function g() { f().blah; }",
        "",
        "/** @constructor */",
        "function Second() {",
        " /** @type {number} */",
        " this.blah = 5;",
        "};",
        "var /** @const */ SecondAlias = Second;");

        testSets(js, js, "{blah=[[Second]]}");
  }

  public void testConstructorsWithTypeErrorsAreNotDisambiguated() {
    String js = LINE_JOINER.join(
        "/** @constructor */",
        "function Foo(){}",
        "Foo.prototype.alias = function() {};",
        "",
        "/** @constructor */",
        "function Bar(){};",
        "/** @return {void} */",
        "Bar.prototype.alias;",
        "",
        "Bar = Foo;",
        "",
        "(new Bar()).alias();");

    testSets("", js, js, "{}", TypeValidator.TYPE_MISMATCH_WARNING, "assignment\n"
            + "found   : function (new:Foo): undefined\n"
            + "required: function (new:Bar): undefined");
  }

  public void testStructuralTypingWithDisambiguatePropertyRenaming1() {
    String js = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.x;",
        "",
        "/** @constructor @implements {I} */",
        "function Foo(){}",
        "/** @type {number} */",
        "Foo.prototype.x;",
        "",
        "/** @constructor */",
        "function Bar(){}",
        "/** @type {number} */",
        "Bar.prototype.x;",
        "",
        "function f(/** I */ i) { return i.x; }");

    // In this case, I.prototype.x and Bar.prototype.x could be the
    // same property since Bar <: I (under structural interface matching).
    // If there is no code that uses a Bar as an I, however, then we
    // will consider the two types distinct and disambiguate the properties
    // with different names.

    String output = LINE_JOINER.join(
        "/** @record */",
        "function I(){}/** @type {number} */I.prototype.Foo_prototype$x;",
        "/** @constructor @implements {I} */",
        "function Foo(){}/** @type {number} */Foo.prototype.Foo_prototype$x;",
        "/** @constructor */",
        "function Bar(){}/** @type {number} */Bar.prototype.Bar_prototype$x;",
        "function f(/** I */ i){return i.Foo_prototype$x}");

    testSets(js, output, "{x=[[Bar.prototype], [Foo.prototype, I.prototype]]}");
  }

  public void testStructuralTypingWithDisambiguatePropertyRenaming1_1() {
    String js = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.x;",
        "",
        "/** @constructor @implements {I} */",
        "function Foo(){}",
        "/** @type {number} */",
        "Foo.prototype.x;",
        "",
        "/** @constructor */",
        "function Bar(){}",
        "/** @type {number} */",
        "Bar.prototype.x;",
        "",
        "function f(/** I */ i) { return i.x; }",
        "f(new Bar());");

    testSets(js, js, "{}");
  }

  public void testStructuralTypingWithDisambiguatePropertyRenaming1_2() {
    String js = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.x;",
        "",
        "/** @constructor @implements {I} */",
        "function Foo(){}",
        "/** @type {number} */",
        "Foo.prototype.x;",
        "",
        "function f(/** I */ i) { return i.x; }",
        "f({x:5});");

    testSets(js, js, "{}");
  }

  public void testStructuralTypingWithDisambiguatePropertyRenaming1_3() {
    String js = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.x;",
        "",
        "/** @constructor @implements {I} */",
        "function Foo(){}",
        "/** @type {number} */",
        "Foo.prototype.x;",
        "",
        "/** @constructor */",
        "function Bar(){}",
        "/** @type {number} */",
        "Bar.prototype.x;",
        "",
        "function f(/** I */ i) { return i.x; }",
        "function g(/** {x:number} */ i) { return f(i); }",
        "g(new Bar());");

    testSets(js, js, "{}");
  }

  public void testStructuralTypingWithDisambiguatePropertyRenaming1_4() {
    String js = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.x;",
        "",
        "/** @constructor @implements {I} */",
        "function Foo(){}",
        "/** @type {number} */",
        "Foo.prototype.x;",
        "",
        "/** @constructor */",
        "function Bar(){}",
        "/** @type {number} */",
        "Bar.prototype.x;",
        "",
        "function f(/** I */ i) { return i.x; }",
        "function g(/** {x:number} */ i) { return f(i); }",
        "g(new Bar());");
    testSets(js, js, "{}");
  }

  public void testStructuralTypingWithDisambiguatePropertyRenaming1_5() {
    String js = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.x;",
        "",
        "/** @constructor @implements {I} */",
        "function Foo(){}",
        "/** @type {number} */",
        "Foo.prototype.x;",
        "",
        "/** @constructor */",
        "function Bar(){}",
        "/** @type {number} */",
        "Bar.prototype.x;",
        "",
        "function g(/** I */ i) { return f.x; }",
        "var /** I */ i = new Bar();",
        "g(i);");
    testSets(js, js, "{}");
  }

  /**
   * a test case where registerMismatch registers a strict mismatch
   * but not a regular mismatch.
   */
  public void testStructuralTypingWithDisambiguatePropertyRenaming1_6() throws Exception {
    String js = LINE_JOINER.join(
        "/** @record */ function I() {}",
        "/** @type {!Function} */ I.prototype.addEventListener;",
        "/** @constructor */ function C() {}",
        "/** @type {!Function} */ C.prototype.addEventListener;",
        "/** @param {I} x */",
        "function f(x) { x.addEventListener(); }",
        "f(new C());");

    testSets(js, js, "{}");

  }

  /**
   * a test case where registerMismatch registers a strict mismatch
   * but not a regular mismatch.
   */
  public void testStructuralTypingWithDisambiguatePropertyRenaming1_7() throws Exception {
    String js = LINE_JOINER.join(
        "/** @record */ function I() {}",
        "/** @type {!Function} */ I.prototype.addEventListener;",
        "/** @constructor */ function C() {}",
        "/** @type {!Function} */ C.prototype.addEventListener;",
        "/** @type {I} */ var x",
        "x = new C()");

    testSets(js, js, "{}");
  }

  public void testStructuralTypingWithDisambiguatePropertyRenaming2() {
    String js = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.x;",
        "",
        "/** @constructor @implements {I} */",
        "function Foo(){}",
        "/** @type {number} */",
        "Foo.prototype.x;",
        "",
        "/** @constructor */",
        "function Bar(){}",
        "/** @type {number} */",
        "Bar.prototype.x;",
        "",
        "/** @param {Foo|Bar} i */",
        "function f(i) { return i.x; }");

    testSets(js, js, "{x=[[Bar.prototype, Foo.prototype, I.prototype]]}");
  }

  public void testStructuralTypingWithDisambiguatePropertyRenaming3() {
    String js = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.x;",
        "",
        "/** @constructor */",
        "function Bar(){}",
        "/** @type {number} */",
        "Bar.prototype.x;",
        "",
        "/** @param {I} i */",
        "function f(i) { return i.x; }",
        "f(new Bar());");

    testSets(js, js, "{}");
  }

  public void testStructuralTypingWithDisambiguatePropertyRenaming3_1() {
    String js = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.x;",
        "",
        "/** @constructor @implements {I} */\n" +
        "function Foo(){}\n" +
        "/** @type {number} */\n" +
        "Foo.prototype.x;",
        "",
        "/** @constructor */",
        "function Bar(){}",
        "/** @type {number} */",
        "Bar.prototype.x;",
        "",
        "/** @param {(I|Bar)} i */",
        "function f(i) { return i.x; }",
        "f(new Bar());");

    testSets(js, js, "{x=[[Bar.prototype, Foo.prototype, I.prototype]]}");
  }

  public void testStructuralTypingWithDisambiguatePropertyRenaming4() {
    String js = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.x;",
        "",
        "/** @constructor @implements {I} */",
        "function Foo(){}",
        "/** @type {number} */",
        "Foo.prototype.x;",
        "",
        "/** @constructor */",
        "function Bar(){}",
        "/** @type {number} */",
        "Bar.prototype.x;",
        "",
        "/** @param {Foo|I} i */",
        "function f(i) { return i.x; }");

    String output = LINE_JOINER.join(
        "/** @record */",
        "function I(){}/** @type {number} */I.prototype.Foo_prototype$x;",
        "/** @constructor @implements {I} */",
        "function Foo(){}/** @type {number} */Foo.prototype.Foo_prototype$x;",
        "/** @constructor */",
        "function Bar(){}/** @type {number} */Bar.prototype.Bar_prototype$x;",
        "/** @param {Foo|I} i */",
        "function f(i){return i.Foo_prototype$x}");

    testSets(js, output, "{x=[[Bar.prototype], [Foo.prototype, I.prototype]]}");
  }

  public void testStructuralTypingWithDisambiguatePropertyRenaming5() {
    String js = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.x;",
        "",
        "/** @constructor @implements {I} */",
        "function Foo(){}",
        "/** @type {number} */",
        "Foo.prototype.x;",
        "",
        "/** @constructor */",
        "function Bar(){}",
        "/** @type {number} */",
        "Bar.prototype.x;",
        "",
        "function f(/** Bar */ i) { return i.x; }");

    String output = LINE_JOINER.join(
        "/** @record */",
        "function I(){}",
        "/** @type {number} */",
        "I.prototype.Foo_prototype$x;",
        "/** @constructor @implements {I} */",
        "function Foo(){}",
        "/** @type {number} */",
        "Foo.prototype.Foo_prototype$x;",
        "/** @constructor */",
        "function Bar(){}",
        "/** @type {number} */",
        "Bar.prototype.Bar_prototype$x;",
        "function f(/** Bar */ i){return i.Bar_prototype$x}");

    testSets(js, output, "{x=[[Bar.prototype], [Foo.prototype, I.prototype]]}");
  }

  /**
   * Tests that the type based version skips renaming on types that have a
   * mismatch, and the type tightened version continues to work as normal.
   */
  public void testMismatchInvalidation() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;\n"
        + "/** @type {Foo} */\n"
        + "var F = new Bar;\n"
        + "F.a = 0;";

    testSets("", js, js, "{}", TypeValidator.TYPE_MISMATCH_WARNING,
        "initializing variable\n"
     + "found   : Bar\n"
     + "required: (Foo|null)");
  }

  public void testBadCast() {
    String js = "/** @constructor */ function Foo() {};\n"
        + "Foo.prototype.a = 0;\n"
        + "/** @constructor */ function Bar() {};\n"
        + "Bar.prototype.a = 0;\n"
        + "var a = /** @type {!Foo} */ (new Bar);\n"
        + "a.a = 4;";
    testSets("", js, js, "{}", TypeValidator.INVALID_CAST,
             "invalid cast - must be a subtype or supertype\n"
             + "from: Bar\n"
             + "to  : Foo");
  }

  public void testDeterministicNaming() {
    String js =
        "/** @constructor */function A() {}\n"
        + "/** @return {string} */A.prototype.f = function() {return 'a';};\n"
        + "/** @constructor */function B() {}\n"
        + "/** @return {string} */B.prototype.f = function() {return 'b';};\n"
        + "/** @constructor */function C() {}\n"
        + "/** @return {string} */C.prototype.f = function() {return 'c';};\n"
        + "/** @type {A|B} */var ab = 1 ? new B : new A;\n"
        + "/** @type {string} */var n = ab.f();\n";

    String output =
        "/** @constructor */ function A() {}\n"
        + "/** @return {string} */ A.prototype.A_prototype$f = function() { return'a'; };\n"
        + "/** @constructor */ function B() {}\n"
        + "/** @return {string} */ B.prototype.A_prototype$f = function() { return'b'; };\n"
        + "/** @constructor */ function C() {}\n"
        + "/** @return {string} */ C.prototype.C_prototype$f = function() { return'c'; };\n"
        + "/** @type {A|B} */ var ab = 1 ? new B : new A;\n"
        + "/** @type {string} */ var n = ab.A_prototype$f();\n";

    for (int i = 0; i < 5; i++) {
      testSets(js, output, "{f=[[A.prototype, B.prototype], [C.prototype]]}");
    }
  }

  public void testObjectLiteral() {
    String js = "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a;\n"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a;\n"
        + "var F = /** @type {Foo} */({ a: 'a' });\n";

    String output = "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.Foo_prototype$a;\n"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.Bar_prototype$a;\n"
        + "var F = /** @type {Foo} */ ({ Foo_prototype$a: 'a' });";

    testSets(js, output, "{a=[[Bar.prototype], [Foo.prototype]]}");
  }

  public void testCustomInherits() {
    String js = "Object.prototype.inheritsFrom = function(shuper) {\n" +
        "  /** @constructor */\n" +
        "  function Inheriter() { }\n" +
        "  Inheriter.prototype = shuper.prototype;\n" +
        "  this.prototype = new Inheriter();\n" +
        "  this.superConstructor = shuper;\n" +
        "};\n" +
        "function Foo(var1, var2, strength) {\n" +
        "  Foo.superConstructor.call(this, strength);\n" +
        "}" +
        "Foo.inheritsFrom(Object);";

    String externs =
        "function Function(var_args) {}"
        + "/** @return {*} */Function.prototype.call = function(var_args) {};";

    testSets(externs, js, js, "{}");
  }

  public void testSkipNativeFunctionStaticProperty() {
    String js = ""
      + "/** @param {!Function} ctor */\n"
      + "function addSingletonGetter(ctor) { ctor.a; }\n"
      + "/** @constructor */ function Foo() {}\n"
      + "Foo.a = 0;"
      + "/** @constructor */ function Bar() {}\n"
      + "Bar.a = 0;";

    String output = ""
        + "/** @param {!Function} ctor */"
        + "function addSingletonGetter(ctor){ctor.a}"
        + "/** @constructor */ function Foo(){}"
        + "Foo.a=0;"
        + "/** @constructor */ function Bar(){}"
        + "Bar.a=0";

    testSets(js, output, "{}");
  }

  public void testStructuralInterfacesInExterns() {
    String externs =
        LINE_JOINER.join(
            "/** @record */",
            "var I = function() {};",
            "/** @return {string} */",
            "I.prototype.baz = function() {};");

    String js =
        LINE_JOINER.join(
            "/** @constructor */",
            "function Bar() {}",
            "Bar.prototype.baz = function() { return ''; };",
            "",
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prototype.baz = function() { return ''; };");

    testSets(externs, js, js, "{}");
  }

  public void testErrorOnProtectedProperty() {
    testError("function addSingletonGetter(foo) { foo.foobar = 'a'; };",
         DisambiguateProperties.Warnings.INVALIDATION);
    assertThat(getLastCompiler().getErrors()[0].toString()).contains("foobar");
  }

  public void testMismatchForbiddenInvalidation() {
    testError(
        "/** @constructor */ function F() {}"
        + "/** @type {number} */ F.prototype.foobar = 3;"
        + "/** @return {number} */ function g() { return new F(); }",
        DisambiguateProperties.Warnings.INVALIDATION);
    assertThat(getLastCompiler().getErrors()[0].toString()).contains("Consider fixing errors");
  }

  public void testUnionTypeInvalidationError() {
    String externs = ""
        + "/** @constructor */ function Baz() {}"
        + "Baz.prototype.foobar";
    String js = ""
        + "/** @constructor */ function Ind() {this.foobar=0}\n"
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.foobar = 0;\n"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.foobar = 0;\n"
        + "/** @type {Foo|Bar} */\n"
        + "var F = new Foo;\n"
        + "F.foobar = 1\n;"
        + "F = new Bar;\n"
        + "/** @type {Baz} */\n"
        + "var Z = new Baz;\n"
        + "Z.foobar = 1\n;";

    test(
        externs, js, (String) null,
        DisambiguateProperties.Warnings.INVALIDATION_ON_TYPE, null);
    assertThat(getLastCompiler().getErrors()[0].toString()).contains("foobar");
  }

  public void runFindHighestTypeInChain() {
    // Check that this doesn't go into an infinite loop.
    new DisambiguateProperties(new Compiler(), new HashMap<String, CheckLevel>())
        .getTypeWithProperty("no",
            new JSTypeRegistry(new TestErrorReporter(null, null))
            .getNativeType(JSTypeNative.OBJECT_PROTOTYPE));
  }

  private void testSets(String js, String expected, String fieldTypes) {
    test(js, expected);
    assertEquals(
        fieldTypes, mapToString(lastPass.getRenamedTypesForTesting()));
  }

  private void testSets(String externs, String js, String expected,
       String fieldTypes) {
    testSets(externs, js, expected, fieldTypes, null, null);
  }

  private void testSets(String externs, String js, String expected,
       String fieldTypes, DiagnosticType warning, String description) {
    test(externs, js, expected, null, warning, description);
    assertEquals(
        fieldTypes, mapToString(lastPass.getRenamedTypesForTesting()));
  }

  /**
   * Compiles the code and checks that the set of types for each field matches
   * the expected value.
   *
   * <p>The format for the set of types for fields is:
   * {field=[[Type1, Type2]]}
   */
  private void testSets(String js, String fieldTypes) {
    test(js, null, null, null);
    assertEquals(fieldTypes, mapToString(lastPass.getRenamedTypesForTesting()));
  }

  /**
   * Compiles the code and checks that the set of types for each field matches
   * the expected value.
   *
   * <p>The format for the set of types for fields is:
   * {field=[[Type1, Type2]]}
   */
  private void testSets(String js, String fieldTypes, DiagnosticType warning) {
    testWarning(js, warning);
    assertEquals(fieldTypes, mapToString(lastPass.getRenamedTypesForTesting()));
  }

  /** Sorts the map and converts to a string for comparison purposes. */
  private <T> String mapToString(Multimap<String, Collection<T>> map) {
    TreeMap<String, String> retMap = new TreeMap<>();
    for (String key : map.keySet()) {
      TreeSet<String> treeSet = new TreeSet<>();
      for (Collection<T> collection : map.get(key)) {
        Set<String> subSet = new TreeSet<>();
        for (T type : collection) {
          subSet.add(type.toString());
        }
        treeSet.add(subSet.toString());
      }
      retMap.put(key, treeSet.toString());
    }
    return retMap.toString();
  }
}
