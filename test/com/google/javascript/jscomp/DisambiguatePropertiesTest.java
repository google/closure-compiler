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
import static com.google.javascript.jscomp.DisambiguateProperties.Warnings.INVALIDATION;
import static com.google.javascript.jscomp.DisambiguateProperties.Warnings.INVALIDATION_ON_TYPE;

import com.google.common.collect.Multimap;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit test for the Compiler DisambiguateProperties pass.
 *
 */

@RunWith(JUnit4.class)
public final class DisambiguatePropertiesTest extends CompilerTestCase {
  private DisambiguateProperties lastPass;
  private static final String RENAME_FUNCTION_DEFINITION =
      "/** @const */ var goog = {};\n"
          + "/** @const */ goog.reflect = {};\n"
          + "/** @return {string} */\n"
          + "goog.reflect.objectProperty = function(prop, obj) { return ''; };\n";

  public DisambiguatePropertiesTest() {
    super(DEFAULT_EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableNormalize();
    enableParseTypeInfo();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {

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

  @Test
  public void testOneType1() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;";
    testSets(js, js, "{a=[[Foo.prototype]]}");

    js =
        RENAME_FUNCTION_DEFINITION
            + "/** @constructor */ function Foo() {}\n"
            + "Foo.prototype.a = 0;\n"
            + "/** @type {Foo} */\n"
            + "var F = new Foo;\n"
            + "F[goog.reflect.objectProperty('a', F)] = 0;";
    testSets(js, js, "{a=[[Foo.prototype]]}");
  }

  @Test
  public void testOneType2() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype = {a: 0};\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;";
    String expected = "{a=[[Foo.prototype]]}";
    testSets(js, js, expected);

    js =
        RENAME_FUNCTION_DEFINITION
            + "/** @constructor */ function Foo() {}\n"
            + "Foo.prototype = {a: 0};\n"
            + "/** @type {Foo} */\n"
            + "var F = new Foo;\n"
            + "F[goog.reflect.objectProperty('a', F)] = 0;";
    testSets(js, js, expected);
  }

  @Test
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

    js =
        RENAME_FUNCTION_DEFINITION
            + "/** @constructor */ function Foo() {}\n"
            + "Foo.prototype = { get a() {return  0},"
            + "                  set a(b) {} };\n"
            + "/** @type {Foo} */\n"
            + "var F = new Foo;\n"
            + "F[goog.reflect.objectProperty('a', F)] = 0;";
    testSets(js, js, expected);
  }

  @Test
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

  @Test
  public void testPrototypeAndInstance1() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;";
    testSets(js, js, "{a=[[Foo.prototype]]}");

    js =
        ""
            + "/** @constructor */ function Foo() {}\n"
            + "Foo.prototype.a = 0;\n"
            + "/** @type {Foo} */\n"
            + "var F = new Foo;\n"
            + "F.a = 0;";
    testSets(js, js, "{a=[[Foo.prototype]]}");
  }

  @Test
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

  @Test
  public void testPrototypeAndInstance3() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "new Foo().a = 0;";
    testSets(js, js, "{a=[[Foo.prototype]]}");
  }

  @Test
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

  @Test
  public void testPrototypeAndInstance5() {
    String js = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.a = 1;",
        "}",
        "/** @constructor @extends {Foo} */",
        "function Bar() {",
        "  this.a = 2;",
        "}",
        "/** @constructor */",
        "function Baz() {",
        "  this.a = 3;",
        "}",
        "var x = (new Bar).a;");

    String output = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.Foo$a = 1;",
        "}",
        "/** @constructor @extends {Foo} */",
        "function Bar() {",
        "  this.Foo$a = 2;",
        "}",
        "/** @constructor */",
        "function Baz() {",
        "  this.Baz$a = 3;",
        "}",
        "var x = (new Bar).Foo$a;");

    test(js, output);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
    testSets(js, output,
        "{a=[[Bar.prototype], [Foo.prototype]], b=[[Bar.prototype], [Foo.prototype]]}");
  }

  @Test
  public void testUnionType_1() {
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

  @Test
  public void testUnionType_2() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;"
        + "var B = /** @type {Bar|Foo} */ (new Bar);\n"
        + "B.a = 0;\n"
        + "B = new Foo;\n"
        + "B.a = 0;\n"
        + "/** @constructor */ function Baz() {}\n"
        + "Baz.prototype.a = 0;\n";
    testSets(js, "{a=[[Bar.prototype, Foo.prototype], [Baz.prototype]]}");
  }

  @Test
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

  @Test
  public void testIgnoreUnknownType1() {
    String js = lines(
        "/** @constructor */",
        "function Foo() {}",
        "Foo.prototype.blah = 3;",
        "/** @type {Foo} */",
        "var F = new Foo;",
        "F.blah = 0;",
        "/** @return {Object} */",
        "var U = function() { return {} };",
        "U().blah();");
    testSets(js, "{}");
  }

  @Test
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
    testSets(js, "{}");
  }

  @Test
  public void testIgnoreUnknownType3() {
    String js = lines(
        "/** @constructor */",
        "function Foo() {}",
        "Foo.prototype.blah = 3;",
        "/** @type {Foo} */",
        "var F = new Foo;",
        "F.blah = 0;",
        "/** @constructor */",
        "function Bar() {}",
        "Bar.prototype.blah = 3;",
        "/** @return {Object} */",
        "var U = function() { return new Bar; };",
        "U().blah();");
    testSets(js, "{}");
  }

  @Test
  public void testUnionTypeTwoFields() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "Foo.prototype.b = 0;\n"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;\n"
        + "Bar.prototype.b = 0;\n"
        + "var B = /** @type {Foo|Bar} */ (new Bar);\n"
        + "B.a = 0;\n"
        + "B.b = 0;\n"
        + "B = new Foo;\n"
        + "/** @constructor */ function Baz() {}\n"
        + "Baz.prototype.a = 0;\n"
        + "Baz.prototype.b = 0;\n";
    testSets(js, "{a=[[Bar.prototype, Foo.prototype], [Baz.prototype]],"
                 + " b=[[Bar.prototype, Foo.prototype], [Baz.prototype]]}");
  }

  @Test
  public void testCast() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;"
        + "var F = /** @type {Foo|Bar} */ (new Foo);\n"
        + "(/** @type {Bar} */(F)).a = 0;";
    String output = ""
        + "/** @constructor */ function Foo(){}\n"
        + "Foo.prototype.Foo_prototype$a=0;\n"
        + "/** @constructor */ function Bar(){}\n"
        + "Bar.prototype.Bar_prototype$a=0;\n"
        + "var F = /** @type {Foo|Bar} */ (new Foo);\n"
        + "/** @type {Bar} */ (F).Bar_prototype$a=0;";
    testSets(js, output, "{a=[[Bar.prototype], [Foo.prototype]]}");
  }

  @Test
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

  @Test
  public void testStaticProperty() {
    String js = ""
      + "/** @constructor */ function Foo() {} \n"
      + "/** @constructor */ function Bar() {}\n"
      + "Foo.a = 0;"
      + "Bar.a = 0;";
    String output;

    output = ""
        + "/** @constructor */ function Foo(){}"
        + "/** @constructor */ function Bar(){}"
        + "Foo.function_new_Foo___undefined$a = 0;"
        + "Bar.function_new_Bar___undefined$a = 0;";

    testSets(js, output, "{a=[[function(new:Bar): undefined]," +
    " [function(new:Foo): undefined]]}");
  }

  @Test
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

  @Test
  public void testScopedType() {
    String js = ""
        + "/** @const */ var g = {};\n"
        + "/** @constructor */ g.Foo = function() {};\n"
        + "g.Foo.prototype.a = 0;"
        + "/** @constructor */ g.Bar = function() {};\n"
        + "g.Bar.prototype.a = 0;";
    String output = ""
        + "/** @const */ var g={};"
        + "/** @constructor */ g.Foo=function(){};"
        + "g.Foo.prototype.g_Foo_prototype$a=0;"
        + "/** @constructor */ g.Bar=function(){};"
        + "g.Bar.prototype.g_Bar_prototype$a=0;";

    testSets(js, output, "{a=[[g.Bar.prototype], [g.Foo.prototype]]}");
  }

  @Test
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
    testSets(js, output, "{a=[[Bar.prototype], [Foo.prototype]]}");
  }

  @Test
  public void testNamedType() {
    String js = ""
        + "/** @const */ var g = {};"
        + "/** @constructor \n @extends {g.Late} */ var Foo = function() {}\n"
        + "Foo.prototype.a = 0;"
        + "/** @constructor */ var Bar = function() {}\n"
        + "Bar.prototype.a = 0;"
        + "/** @constructor */ g.Late = function() {}";
    String output = ""
        + "/** @const */ var g={};"
        + "/** @constructor @extends {g.Late} */ var Foo=function(){};"
        + "Foo.prototype.Foo_prototype$a=0;"
        + "/** @constructor */ var Bar=function(){};"
        + "Bar.prototype.Bar_prototype$a=0;"
        + "/** @constructor */ g.Late = function(){}";
    testSets(js, output, "{a=[[Bar.prototype], [Foo.prototype]]}");
  }

  @Test
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
  @Test
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
  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testUntypedExterns() {
    String externs = "var untypedvar; untypedvar.alert = function() {x};";
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "Foo.prototype.alert = 0;\n"
        + "Foo.prototype.untypedvar = 0;\n"
        + "/** @constructor */ function Bar() {}\n"
        + "Bar.prototype.a = 0;\n"
        + "Bar.prototype.alert = 0;\n"
        + "Bar.prototype.untypedvar = 0;\n"
        + "untypedvar.alert();";
    String output = ""
        + "/** @constructor */ function Foo(){}"
        + "Foo.prototype.Foo_prototype$a=0;"
        + "Foo.prototype.alert=0;"
        + "Foo.prototype.Foo_prototype$untypedvar=0;"
        + "/** @constructor */ function Bar(){}"
        + "Bar.prototype.Bar_prototype$a=0;"
        + "Bar.prototype.alert=0;"
        + "Bar.prototype.Bar_prototype$untypedvar=0;"
        + "untypedvar.alert();";

    testSets(externs, js, output, "{a=[[Bar.prototype], [Foo.prototype]]"
             + ", untypedvar=[[Bar.prototype], [Foo.prototype]]}");
  }

  @Test
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
       + "var F = /** @type {Foo|Bar} */ (new Foo);\n"
       + "F.a = 1;\n"
       + "F = new Bar;\n"
       + "/** @type {Baz} */\n"
       + "var Z = new Baz;\n"
       + "Z.a = 1;\n"
       + "var B = /** @type {Bar|Baz} */ (new Baz);\n"
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
       + "var F = /** @type {Foo|Bar} */ (new Foo);\n"
       + "F.a = 1;\n"
       + "F = new Bar;\n"
       + "/** @type {Baz} */\n"
       + "var Z = new Baz;\n"
       + "Z.a = 1;\n"
       + "var B = /** @type {Bar|Baz} */ (new Baz);"
       + "B.a = 1;"
       + "B = new Bar;";
   testSets(externs, js, output, "{a=[[Ind]]}");
 }

  @Test
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
      + "/**\n"
      + " * @param {(Bar|Baz)} b\n"
      + " * @param {(Baz|Buz)} c\n"
      + " * @param {(Buz|Foo)} d\n"
      + " */\n"
      + "function f(b, c, d) {\n"
      + "  b.a = 5; c.a = 6; d.a = 7;\n"
      + "}";
    String output = ""
      + "/** @constructor */ function Bar() { this.a = 2; }\n"
      + "/** @constructor */ function Baz() { this.a = 3; }\n"
      + "/** @constructor */ function Buz() { this.a = 4; }\n"
      + "/** @constructor */ function T1() { this.T1$a = 3; }\n"
      + "/** @constructor */ function T2() { this.T2$a = 3; }\n"
      + "/**\n"
      + " * @param {Bar|Baz} b\n"
      + " * @param {Baz|Buz} c\n"
      + " * @param {Buz|Foo} d\n"
      + " */\n"
      + "function f(b, c, d) {\n"
      + "  b.a = 5; c.a = 6; d.a = 7;\n"
      + "}";

    // We are testing the skipping of multiple types caused by unionizing with
    // extern types.
    testSets(externs, js, output, "{a=[[T1], [T2]]}");
  }

  @Test
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

  @Test
  public void testSubtypesWithSameField() {
    String js = lines(
        "/** @constructor */",
        "function Top() {}",
        "/** @constructor @extends Top */",
        "function Foo() {}",
        "Foo.prototype.a;",
        "/** @constructor @extends Top */",
        "function Bar() {}",
        "Bar.prototype.a;",
        "/** @param {Top} top */",
        "function foo(top) {",
        "  var x = top.a;",
        "}",
        "foo(new Foo);",
        "foo(new Bar);");
    testSets(js, "{}");
  }

  @Test
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
        + "function foo(foo$jscomp$1) {\n"
        + "  var x = foo$jscomp$1.Bar_prototype$a;\n"
        + "}\n";
    testSets(externs, js, result, "{a=[[Bar.prototype]]}");
  }

  @Test
  public void testObjectLiteralNotRenamed() {
    String js = ""
        + "var F = {a:'a', b:'b'};"
        + "F.a = 'z';";
    testSets(js, js, "{}");
  }

  @Test
  public void testObjectLiteralReflected() {
    String js = ""
        + "/** @const */ var goog = {};"
        + "goog.reflect = {};"
        + "goog.reflect.object = function(x, y) { return y; };"
        + "/** @constructor */ function F() {}"
        + "/** @type {number} */ F.prototype.foo = 3;"
        + "/** @constructor */ function G() {}"
        + "/** @type {number} */ G.prototype.foo = 3;"
        + "goog.reflect.object(F, {foo: 5});";
    String result = ""
        + "/** @const */ var goog = {};"
        + "goog.reflect = {};"
        + "goog.reflect.object = function(x, y) { return y; };"
        + "/** @constructor */ function F() {}"
        + "/** @type {number} */ F.prototype.F_prototype$foo = 3;"
        + "/** @constructor */ function G() {}"
        + "/** @type {number} */ G.prototype.G_prototype$foo = 3;"
        + "goog.reflect.object(F, {F_prototype$foo: 5});";
    testSets(js, result, "{foo=[[F.prototype], [G.prototype]]}");
  }

  @Test
  public void testObjectLiteralBlocksPropertiesOnOtherTypes() {
    String js = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.myprop = 123;",
        "}",
        "var x = (new Foo).myprop;",
        "var y = { myprop: 'asdf' };");
    testSets(js, js, "{}");
  }

  @Test
  public void testObjectLiteralDefineProperties() {
    String externs =
        lines(
            "Object.defineProperties = function(typeRef, definitions) {}",
            "/** @constructor */ function FooBar() {}",
            "/** @type {string} */ FooBar.prototype.bar_;",
            "/** @type {string} */ FooBar.prototype.bar;");

    String js =
        lines(
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
            "/** @struct @constructor */ var Foo = function() {",
            "  this.Foo$bar_ = 'bar';",
            "};",
            "/** @type {?} */ Foo.prototype.Foo_prototype$bar;",
            "Object.defineProperties(Foo.prototype, {",
            "  Foo_prototype$bar: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {Foo} */ get: function() { return this.Foo$bar_;},",
            "    /** @this {Foo} */ set: function(value) { this.Foo$bar_ = value; }",
            "  }",
            "});");
    testSets(externs, js, result, "{bar=[[Foo.prototype]], bar_=[[Foo]]}");
  }

  @Test
  public void testObjectLiteralDefinePropertiesQuoted() {
    String externs =
        lines(
            "Object.defineProperties = function(typeRef, definitions) {}",
            "/** @constructor */ function FooBar() {}",
            "/** @type {string} */ FooBar.prototype.bar_;",
            "/** @type {string} */ FooBar.prototype.bar;");

    String js =
        lines(
            "/** @struct @constructor */ var Foo = function() {",
            "  this.bar_ = 'bar';",
            "};",
            "/** @type {?} */ Foo.prototype['bar'];",
            "Object.defineProperties(Foo.prototype, {",
            "  'bar': {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {Foo} */ get: function() { return this.bar_;},",
            "    /** @this {Foo} */ set: function(value) { this.bar_ = value; }",
            "  }",
            "});");

    String result =
        lines(
            "/** @struct @constructor */ var Foo = function() {",
            "  this.Foo$bar_ = 'bar';",
            "};",
            "/** @type {?} */ Foo.prototype['bar'];",
            "Object.defineProperties(Foo.prototype, {",
            "  'bar': {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {Foo} */ get: function() { return this.Foo$bar_;},",
            "    /** @this {Foo} */ set: function(value) { this.Foo$bar_ = value; }",
            "  }",
            "});");
    testSets(externs, js, result, "{bar_=[[Foo]]}");
  }

  @Test
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

  @Test
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

  @Test
  public void testSkipNativeFunctionMethod() {
    String js = ""
        + "/** @constructor */ function Foo(){};"
        + "/** @constructor\n @extends Foo */"
        + "function Bar() { Foo.call(this); };"; // call should not be renamed
    testSame(js);
  }

  @Test
  public void testSkipNativeObjectMethod() {
    String js = ""
        + "/** @constructor */ function Foo(){};"
        + "(new Foo).hasOwnProperty('x');";
    testSets(js, js, "{}");
  }

  @Test
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

  @Test
  public void testStringFunction() {
    // Extern functions are not renamed, but user functions on a native
    // prototype object are.
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

    testSets(js, output, "{foo=[[Foo.prototype], [String.prototype]]}");
  }

  @Test
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

  @Test
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

  @Test
  public void testInterface_noDirectImplementors() {
    String js = ""
        + "/** @interface */\n"
        + "function I() {}\n"
        + "I.prototype.a;\n"
        + "I.prototype.b;\n"
        + "/** @interface @extends {I} */\n"
        + "function J() {}\n"
        + "/** @constructor @implements {J} */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.a;\n"
        + "Foo.prototype.b;\n"
        + "function f(/** !I */ x) {\n"
        + "  return x.a;\n"
        + "}\n"
        + "/** @interface */\n"
        + "function Z() {}\n"
        + "Z.prototype.a;\n"
        + "Z.prototype.b;";
    String output = ""
        + "/** @interface */\n"
        + "function I() {}\n"
        + "I.prototype.Foo_prototype$a;\n"
        + "I.prototype.Foo_prototype$b;\n"
        + "/** @interface @extends {I} */\n"
        + "function J() {}\n"
        + "/** @constructor @implements {J} */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.Foo_prototype$a;\n"
        + "Foo.prototype.Foo_prototype$b;\n"
        + "function f(/** !I */ x){\n"
        + "  return x.Foo_prototype$a;\n"
        + "}\n"
        + "/** @interface */\n"
        + "function Z() {}\n"
        + "Z.prototype.Z_prototype$a;\n"
        + "Z.prototype.Z_prototype$b;";
    testSets(
        js,
        output,
        "{a=[[Foo.prototype, I.prototype], [Z.prototype]],"
            + " b=[[Foo.prototype, I.prototype], [Z.prototype]]}");
  }

  @Test
  public void testInterface_subInterfaceAndDirectImplementors() {
    String js = lines(
        "/** @interface */ function I() {};",
        "I.prototype.a;",
        "/** @constructor @implements {I} */ function Foo() {};",
        "Foo.prototype.a;",
        "/** @interface @extends {I} */ function Bar() {};",
        "Bar.prototype.a;");
    testSets(js, "{a=[[Bar.prototype, Foo.prototype, I.prototype]]}");
  }

  @Test
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

  @Test
  public void testInterfaceOfSuperclass2() {
    String js = lines(
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testSuperInterface2() {
    String js = lines(
        "/** @interface */",
        "function High(){}",
        "High.prototype.prop = function() {};",
        "/**",
        " * @interface",
        " * @extends {High}",
        " */",
        "function Low() {}",
        "Low.prototype.prop = function() {};",
        "/**",
        " * @constructor",
        " * @implements {Low}",
        " */",
        "function A() {}",
        "A.prototype.prop = function() {};");

    testSets(js, js, "{prop=[[A.prototype, High.prototype, Low.prototype]]}");
  }

  @Test
  public void testSuperInterface3() {
    testSets(
        lines(
            "/** @interface */",
            "function I0() {}",
            "I0.prototype.prop = function() {};",
            "/** @interface */",
            "function I1() {}",
            "I1.prototype.prop = function() {};",
            "/** @interface */",
            "function I2() {}",
            "I2.prototype.prop = function() {};",
            "/**",
            " * @interface",
            " * @extends {I1}",
            " * @extends {I2}",
            " */",
            "function Mixin() {}",
            "/**",
            " * @constructor",
            " * @implements {Mixin}",
            " */",
            "function C() {}",
            "C.prototype.prop = function() {};",
            "/**",
            " * @constructor",
            " * @implements {I1}",
            " */",
            "function D() {}",
            "D.prototype.prop = function() {};"),
        lines(
            "/** @interface */",
            "function I0() {}",
            "I0.prototype.I0_prototype$prop = function() {};",
            "/** @interface */",
            "function I1() {}",
            "I1.prototype.C_prototype$prop = function() {};",
            "/** @interface */",
            "function I2() {}",
            "I2.prototype.C_prototype$prop = function() {};",
            "/**",
            " * @interface",
            " * @extends {I1}",
            " * @extends {I2}",
            " */",
            "function Mixin() {}",
            "/**",
            " * @constructor",
            " * @implements {Mixin}",
            " */",
            "function C() {}",
            "C.prototype.C_prototype$prop = function() {};",
            "/**",
            " * @constructor",
            " * @implements {I1}",
            " */",
            "function D() {}",
            "D.prototype.C_prototype$prop = function() {};"),
        "{prop=[[C.prototype, D.prototype, I1.prototype, I2.prototype], [I0.prototype]]}");
  }

  @Test
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

  @Test
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

  @Test
  public void testAliasedTypeIsNotDisambiguated() {
    String js = lines(
        "/** @return {SecondAlias} */",
        "function f() { return new Second; }",
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

  @Test
  public void testConstructorsWithTypeErrorsAreNotDisambiguated() {
    String js = lines(
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
            + "found   : function(new:Foo): undefined\n"
            + "required: function(new:Bar): undefined");
  }

  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming1() {
    String js = lines(
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

    String output = lines(
        "/** @record */",
        "function I(){}/** @type {number} */I.prototype.Foo_prototype$x;",
        "/** @constructor @implements {I} */",
        "function Foo(){}/** @type {number} */Foo.prototype.Foo_prototype$x;",
        "/** @constructor */",
        "function Bar(){}/** @type {number} */Bar.prototype.Bar_prototype$x;",
        "function f(/** I */ i){return i.Foo_prototype$x}");

    testSets(js, output, "{x=[[Bar.prototype], [Foo.prototype, I.prototype]]}");
  }

  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming1_1() {
    String js = lines(
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

  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming1_2() {
    String js = lines(
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

  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming1_3() {
    String js = lines(
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

  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming1_4() {
    String js = lines(
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
        "function f(/** !I */ i) { return i.x; }",
        "function g(/** {x:number} */ i) { return f(i); }",
        "g(new Bar());");
    testSets(js, js, "{}");
  }

  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming1_5() {
    String js = lines(
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

  /** a test case where registerMismatch registers a strict mismatch but not a regular mismatch. */
  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming1_6() {
    String js = lines(
        "/** @record */ function I() {}",
        "/** @type {!Function} */ I.prototype.addEventListener;",
        "/** @constructor */ function C() {}",
        "/** @type {!Function} */ C.prototype.addEventListener;",
        "/** @param {I} x */",
        "function f(x) { x.addEventListener(); }",
        "f(new C());");

    testSets(js, js, "{}");

  }

  /** a test case where registerMismatch registers a strict mismatch but not a regular mismatch. */
  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming1_7() {
    String js = lines(
        "/** @record */ function I() {}",
        "/** @type {!Function} */ I.prototype.addEventListener;",
        "/** @constructor */ function C() {}",
        "/** @type {!Function} */ C.prototype.addEventListener;",
        "/** @type {I} */ var x;",
        "x = new C()");

    testSets(js, js, "{}");
  }

  @Test
  public void testReportImplicitUseOfStructuralInterfaceInvalidingProperty() {
    test(
        srcs(lines(
            "/** @record */ function I() {}",
            "/** @type {number} */ I.prototype.foobar;",
            "/** @param {I} arg */ function f(arg) {}",
            "/** @constructor */ function C() { this.foobar = 42; }",
            "f(new C());")),
        error(INVALIDATION).withMessageContaining("foobar"));
  }

  @Test
  public void testDisambiguatePropertiesClassCastedToUnrelatedInterface() {
    String js = lines(
        "/** @interface */",
        "function Foo() {}",
        "Foo.prototype.prop1;",
        "Foo.prototype.prop2;",
        "/** @constructor */",
        "function Bar() {",
        "  this.prop1 = 123;",
        "}",
        "var x = /** @type {!Foo} */ (new Bar);",
        "/** @constructor */",
        "function Baz() {",
        "  this.prop1 = 123;",
        "}");

    testSets(js,  js, "{}");
  }

  @Test
  public void testDontInvalidateForGenericsMismatch() {
    String js = lines(
        "/**",
        " * @constructor",
        " * @template T",
        " */",
        "function Foo() {",
        "  this.prop = 123;",
        "}",
        "/** @param {!Foo<number>} x */",
        "function f(x) {",
        "  return (/** @type {!Foo<string>} */ (x)).prop;",
        "}",
        "/** @constructor */",
        "function Bar() {",
        "  this.prop = 123;",
        "}");

    String output = lines(
        "/**",
        " * @constructor",
        " * @template T",
        " */",
        "function Foo() {",
        "  this.Foo$prop = 123;",
        "}",
        "/** @param {!Foo<number>} x */",
        "function f(x) {",
        "  return (/** @type {!Foo<string>} */ (x)).Foo$prop;",
        "}",
        "/** @constructor */",
        "function Bar() {",
        "  this.Bar$prop = 123;",
        "}");

    testSets(js, output, "{prop=[[Bar], [Foo]]}");
  }

  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming2() {
    String js = lines(
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

  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming3() {
    String js = lines(
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

  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming3_1() {
    String js = lines(
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
        "/** @param {(I|Bar)} i */",
        "function f(i) { return i.x; }",
        "f(new Bar());");

    testSets(js, js, "{x=[[Bar.prototype, Foo.prototype, I.prototype]]}");
  }

  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming4() {
    String js = lines(
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

    String output = lines(
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

  @Test
  public void testStructuralTypingWithDisambiguatePropertyRenaming5() {
    String js = lines(
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

    String output = lines(
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
   * Tests that the type based version skips renaming on types that have a mismatch, and the type
   * tightened version continues to work as normal.
   */
  @Test
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
        lines(
            "initializing variable",
            "found   : Bar",
            "required: (Foo|null)"));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

    testSets(js, js, "{}");
  }

  @Test
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

  @Test
  public void testStructuralInterfacesInExterns() {
    String externs =
        lines(
            "/** @record */",
            "var I = function() {};",
            "/** @return {string} */",
            "I.prototype.baz = function() {};");

    String js =
        lines(
            "/** @constructor */",
            "function Bar() {}",
            "Bar.prototype.baz = function() { return ''; };",
            "",
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prototype.baz = function() { return ''; };");

    testSets(externs, js, js, "{}");
  }

  @Test
  public void testPropInParentInterface1() {
    String js = lines(
        "/** @interface */",
        "function MyIterable() {}",
        "MyIterable.prototype.iterator = function() {};",
        "/**",
        " * @interface",
        " * @extends {MyIterable}",
        " * @template T",
        " */",
        "function MyCollection() {}",
        "/**",
        " * @constructor",
        " * @implements {MyCollection<?>}",
        " */",
        "function MyAbstractCollection() {}",
        "/** @override */",
        "MyAbstractCollection.prototype.iterator = function() {};");

    testSets(js, "{iterator=[[MyAbstractCollection.prototype, MyIterable.prototype]]}");
  }

  @Test
  public void testPropInParentInterface2() {
    String js = lines(
        "/** @interface */",
        "function MyIterable() {}",
        "MyIterable.prototype.iterator = function() {};",
        "/**",
        " * @interface",
        " * @extends {MyIterable}",
        " * @template T",
        " */",
        "function MyCollection() {}",
        "/**",
        " * @constructor",
        " * @implements {MyCollection<?>}",
        " */",
        "function MyAbstractCollection() {}",
        "/** @override */",
        "MyAbstractCollection.prototype.iterator = function() {};");

    testSets(js, "{iterator=[[MyAbstractCollection.prototype, MyIterable.prototype]]}");
  }

  @Test
  public void testPropInParentInterface3() {
    String js = lines(
        "/** @interface */",
        "function MyIterable() {}",
        "MyIterable.prototype.iterator = function() {};",
        "/**",
        " * @interface",
        " * @extends {MyIterable}",
        " */",
        "function MyCollection() {}",
        "/**",
        " * @constructor",
        " * @implements {MyCollection}",
        " */",
        "function MyAbstractCollection() {}",
        "/** @override */",
        "MyAbstractCollection.prototype.iterator = function() {};");

    String output = lines(
        "/** @interface */",
        "function MyIterable() {}",
        "MyIterable.prototype.MyAbstractCollection_prototype$iterator = function() {};",
        "/**",
        " * @interface",
        " * @extends {MyIterable}",
        " */",
        "function MyCollection() {}",
        "/**",
        " * @constructor",
        " * @implements {MyCollection}",
        " */",
        "function MyAbstractCollection() {}",
        "/** @override */",
        "MyAbstractCollection.prototype.MyAbstractCollection_prototype$iterator = function() {};");

    testSets(js, output, "{iterator=[[MyAbstractCollection.prototype, MyIterable.prototype]]}");
  }

  // In function subtyping, the type of THIS should be contravariant, like the argument types.
  // But when overriding a method, it's covariant, and on top of that, we allow methods redefining
  // it with @this.
  // So we check THIS loosely for functions, and as a result, we get wrong disambiguation.
  // On top of that, this can happen when types are joined during generics instantiation.
  // Just documenting the behavior here.
  @Test
  public void testUnsafeTypingOfThis() {
    String js = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.myprop = 123;",
        "}",
        "Foo.prototype.method = function() { this.myprop++; };",
        "/** @constructor */",
        "function Bar() {",
        "  this.myprop = 123;",
        "}",
        "/**",
        " * @param {function(this:T)} callback",
        " * @param {T} thisobj",
        " * @template T",
        " */",
        "function myArrayPrototypeMap(callback, thisobj) {",
        "  callback.call(thisobj);",
        "}",
        "myArrayPrototypeMap(Foo.prototype.method, new Bar);");

    String output = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.Foo$myprop = 123;",
        "}",
        "Foo.prototype.method = function() { this.Foo$myprop++; };",
        "/** @constructor */",
        "function Bar() {",
        "  this.Bar$myprop = 123;",
        "}",
        "/**",
        " * @param {function(this:T)} callback",
        " * @param {T} thisobj",
        " * @template T",
        " */",
        "function myArrayPrototypeMap(callback, thisobj) {",
        "  callback.call(thisobj);",
        "}",
        "myArrayPrototypeMap(Foo.prototype.method, new Bar);");

    testSets(js, output, "{method=[[Foo.prototype]], myprop=[[Bar], [Foo]]}");

    js = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.myprop = 123;",
        "}",
        "Foo.prototype.method = function() { this.myprop++; };",
        "/** @constructor */",
        "function Bar() {",
        "  this.myprop = 123;",
        "}",
        "/** @param {function(this:(!Foo|!Bar))} callback */",
        "function f(callback) {",
        "  callback.call(new Bar);",
        "}",
        "f(Foo.prototype.method);");

    output = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.Foo$myprop = 123;",
        "}",
        "Foo.prototype.method = function() { this.Foo$myprop++; };",
        "/** @constructor */",
        "function Bar() {",
        "  this.Bar$myprop = 123;",
        "}",
        "/** @param {function(this:(!Foo|!Bar))} callback */",
        "function f(callback) {",
        "  callback.call(new Bar);",
        "}",
        "f(Foo.prototype.method);");

    testSets("", js, output, "{method=[[Foo.prototype]], myprop=[[Bar], [Foo]]}");
  }

  @Test
  public void testIgnoreSpecializedProperties() {
    String js = lines(
        "/** @constructor */",
        "function Foo() {",
        "  /** @type {?Array<number>} */",
        "  this.a = null;",
        "  this.b = 1;",
        "}",
        "Foo.prototype.f = function() {",
        "  if (this.a == null) {",
        "    this.a = [];",
        "  }",
        "};",
        "/** @constructor */",
        "function Bar() {",
        "  this.a = 3;",
        "  this.b = 2;",
        "}");

    String output = lines(
        "/** @constructor */",
        "function Foo() {",
        "  /** @type {?Array<number>} */",
        "  this.Foo$a = null;",
        "  this.Foo$b = 1;",
        "}",
        "Foo.prototype.f = function() {",
        "  if (this.Foo$a == null) {",
        "    this.Foo$a = [];",
        "  }",
        "};",
        "/** @constructor */",
        "function Bar() {",
        "  this.Bar$a = 3;",
        "  this.Bar$b = 2;",
        "}");

    test(srcs(js), expected(output));
  }

  @Test
  public void testIgnoreSpecializedProperties2() {
    String js = lines(
        "/** @const */",
        "var ns = function() {};",
        "/** @type {?number} */",
        "ns.num;",
        "function f() {",
        "  if (ns.num !== null) {",
        "    return ns.num + 1;",
        "  }",
        "}",
        "/** @constructor */",
        "function Foo() {",
        "  this.num = 123;",
        "}");

    String otiOutput = lines(
        "/** @const */",
        "var ns = function() {};",
        "/** @type {?number} */",
        "ns.function____undefined$num;",
        "function f() {",
        "  if (ns.function____undefined$num !== null) {",
        "    return ns.function____undefined$num + 1;",
        "  }",
        "}",
        "/** @constructor */",
        "function Foo() {",
        "  this.Foo$num = 123;",
        "}");

    testSets("", js, otiOutput, "{num=[[Foo], [function(): undefined]]}");
  }

  @Test
  public void testIgnoreSpecializedProperties3() {
    String js = lines(
        "/** @constructor */",
        "function Foo() {}",
        "/** @type {?number} */",
        "Foo.prototype.num;",
        "function f() {",
        "  if (Foo.prototype.num != null) {",
        "    return Foo.prototype.num;",
        "  }",
        "}",
        "/** @constructor */",
        "function Bar() {",
        "  this.num = 123;",
        "}");

    String output = lines(
        "/** @constructor */",
        "function Foo() {}",
        "/** @type {?number} */",
        "Foo.prototype.Foo_prototype$num;",
        "function f() {",
        "  if (Foo.prototype.Foo_prototype$num != null) {",
        "    return Foo.prototype.Foo_prototype$num;",
        "  }",
        "}",
        "/** @constructor */",
        "function Bar() {",
        "  this.Bar$num = 123;",
        "}");

    test(srcs(js), expected(output));
  }

  @Test
  public void testErrorOnProtectedProperty() {
    test(
        srcs("function addSingletonGetter(foo) { foo.foobar = 'a'; };"),
        error(INVALIDATION).withMessageContaining("foobar"));
  }

  @Test
  public void testMismatchForbiddenInvalidation() {
    test(
        srcs(lines(
            "/** @constructor */ function F() {}",
            "/** @type {number} */ F.prototype.foobar = 3;",
            "/** @return {number} */ function g() { return new F(); }")),
        error(INVALIDATION).withMessageContaining("Consider fixing errors"));
  }

  @Test
  public void testUnionTypeInvalidationError() {
    String externs = lines(
        "/** @constructor */ function Baz() {}",
        "Baz.prototype.foobar");
    String js = lines(
        "/** @constructor */ function Ind() {this.foobar=0}",
        "/** @constructor */ function Foo() {}",
        "Foo.prototype.foobar = 0;",
        "/** @constructor */ function Bar() {}",
        "Bar.prototype.foobar = 0;",
        "/** @type {Foo|Bar} */",
        "var F = new Foo;",
        "F.foobar = 1;",
        "F = new Bar;",
        "/** @type {Baz} */",
        "var Z = new Baz;",
        "Z.foobar = 1;\n");

    test(
        externs(DEFAULT_EXTERNS + externs),
        srcs(js),
        error(INVALIDATION_ON_TYPE).withMessageContaining("foobar"));
  }

  @Test
  public void testDontCrashOnNonConstructorsWithPrototype() {
    String externs = lines(
        "function f(x) { return x; }",
        "f.prototype.method = function() {};");

    test(
        externs(DEFAULT_EXTERNS + externs),
        srcs(""),
        expected(""));
  }

  @Test
  public void testDontRenameStaticPropertiesOnBuiltins() {
    String externs = "Array.foobar = function() {};";

    String js = lines(
        "/** @constructor */",
        "function Foo() {}",
        "Foo.prototype.foobar = function() {};",
        "var x = Array.foobar;");

    test(
        externs(DEFAULT_EXTERNS + externs),
        srcs(js),
        error(INVALIDATION_ON_TYPE)
            .withMessageContaining("foobar"));
  }

  @Test
  public void testAccessConstructorPropertyDontConfuseWithPrototypeObject() {
    String js = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.p = 1;",
        "}",
        "Foo.m = function(/** !Foo */ x) {",
        "  if (x.constructor === Foo) {",
        "    return x.p;",
        "  }",
        "};",
        "/** @constructor */",
        "function Bar() {",
        "  this.p = 1;",
        "}");

    String output = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.Foo$p = 1;",
        "}",
        "Foo.m = function(/** !Foo */ x) {",
        "  if (x.constructor === Foo) {",
        "    return x.Foo$p;",
        "  }",
        "};",
        "/** @constructor */",
        "function Bar() {",
        "  this.Bar$p = 1;",
        "}");

    test(js, output);
  }

  @Test
  public void testDontCrashWhenConstructingUnknownInstance() {
    String js = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.abc = 123;",
        "}",
        "/** @param {function(new:?)} ctor */",
        "function f(ctor) {",
        "  if (ctor.abc) { return ctor.abc; }",
        "}");

    testSame(js);
  }

  @Test
  public void testDontBackOffForCastsFromObject() {
    String js = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.a = 1;",
        "}",
        "/** @constructor */",
        "function Bar() {",
        "  this.a = 1;",
        "}",
        "function f(/** !Object */ x) {",
        "  return /** @type {!Foo} */ (x);",
        "}");

    String output = lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.Foo$a = 1;",
        "}",
        "/** @constructor */",
        "function Bar() {",
        "  this.Bar$a = 1;",
        "}",
        "function f(/** !Object */ x) {",
        "  return /** @type {!Foo} */ (x);",
        "}");

    test(js, output);
  }

  @Test
  public void testAccessOnSupertypeWithOneSubtype() {
    String externs = lines(
        "/** @constructor */",
        "function Foo() {}",
        "/**",
        " * @constructor",
        " * @extends {Foo}",
        " */",
        "function Bar() {}",
        "Bar.prototype.firstElementChild;");

    String js = lines(
        "function f(/** !Foo */ x) {",
        "  if (x.firstElementChild) {",
        "    return /** @type {!Bar} */ (x).firstElementChild;",
        "  }",
        "}",
        "/** @constructor */",
        "function Baz() {}",
        "Baz.prototype.firstElementChild;");

    String output = lines(
        "function f(/** !Foo */ x) {",
        "  if (x.firstElementChild) {",
        "    return /** @type {!Bar} */ (x).firstElementChild;",
        "  }",
        "}",
        "/** @constructor */",
        "function Baz() {}",
        "Baz.prototype.Baz_prototype$firstElementChild;");

    test(
        externs(DEFAULT_EXTERNS + externs),
        srcs(js),
        expected(output));
  }

  @Test
  public void testAccessOnSupertypeWithManySubtypes() {
    String externs = lines(
        "/** @constructor */",
        "function Foo() {}",
        "/**",
        " * @constructor",
        " * @extends {Foo}",
        " */",
        "function Bar() {}",
        "Bar.prototype.firstElementChild;",
        "/**",
        " * @constructor",
        " * @extends {Foo}",
        " */",
        "function Qux() {}",
        "Qux.prototype.firstElementChild;");

    String js = lines(
        "function f(/** !Foo */ x) {",
        "  if (x.firstElementChild) {",
        "    return /** @type {!Bar} */ (x).firstElementChild;",
        "  }",
        "}",
        "/** @constructor */",
        "function Baz() {}",
        "Baz.prototype.firstElementChild;");

    testSame(externs(DEFAULT_EXTERNS + externs), srcs(js));
  }

  @Test
  public void testAccessOnObjectWithManySubtypes() {
    String externs = lines(
        "/** @constructor */",
        "function Bar() {}",
        "Bar.prototype.firstElementChild;",
        "/** @constructor */",
        "function Qux() {}",
        "Qux.prototype.firstElementChild;");

    String js = lines(
        "function f(/** !Object */ x) {",
        "  if (x.firstElementChild) {",
        "    return /** @type {!Bar} */ (x).firstElementChild;",
        "  }",
        "}",
        "/** @constructor */",
        "function Baz() {}",
        "Baz.prototype.firstElementChild;");

    testSame(externs(DEFAULT_EXTERNS + externs), srcs(js));
  }

  @Test
  public void testInvalidationOnNamespaceType() {
    enableTranspile();

    String js = lines(
        "var goog = {};",
        "goog.array = {};",
        "goog.array.foobar = function(var_args) {}",
        "",
        "var args = [1, 2];",
        "goog.array.foobar(...args);");

    // TODO(b/37673673): This should compile with no errors.
    test(srcs(js), error(INVALIDATION).withMessageContaining("foobar"));
  }

  private void testSets(String js, String expected, final String fieldTypes) {
    test(srcs(js), expected(expected));
    assertThat(mapToString(lastPass.getRenamedTypesForTesting())).isEqualTo(fieldTypes);
  }

  private void testSets(String externs, String js, String expected, final String fieldTypes) {
    test(externs(DEFAULT_EXTERNS + externs), srcs(js), expected(expected));
    assertThat(mapToString(lastPass.getRenamedTypesForTesting())).isEqualTo(fieldTypes);
  }

  private void testSets(String externs, String js, String expected,
       final String fieldTypes, DiagnosticType warning, String description) {
    test(
        externs(DEFAULT_EXTERNS + externs),
        srcs(js),
        expected(expected),
        warning(warning).withMessage(description));
    assertThat(mapToString(lastPass.getRenamedTypesForTesting())).isEqualTo(fieldTypes);
  }

  /**
   * Compiles the code and checks that the set of types for each field matches
   * the expected value.
   *
   * <p>The format for the set of types for fields is:
   * {field=[[Type1, Type2]]}
   */
  private void testSets(String js, final String fieldTypes) {
    test(srcs(js));
    assertThat(mapToString(lastPass.getRenamedTypesForTesting())).isEqualTo(fieldTypes);
  }

  /**
   * Compiles the code and checks that the set of types for each field matches
   * the expected value.
   *
   * <p>The format for the set of types for fields is:
   * {field=[[Type1, Type2]]}
   */
  private void testSets(String js, final String fieldTypes, DiagnosticType warning) {
    test(
        srcs(js),
        warning(warning),
        (Postcondition)
            unused ->
                assertThat(mapToString(lastPass.getRenamedTypesForTesting()))
                    .isEqualTo(fieldTypes));
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
