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

public final class DisambiguatePropertiesTest extends TypeICompilerTestCase {
  private DisambiguateProperties lastPass;
  private static String renameFunctionDefinition =
      "/** @const */ var goog = {};\n"
          + "/** @const */ goog.reflect = {};\n"
          + "/** @return {string} */\n"
          + "goog.reflect.objectProperty = function(prop, obj) { return ''; };\n";

  public DisambiguatePropertiesTest() {
    super(DEFAULT_EXTERNS);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    enableParseTypeInfo();
    ignoreWarnings(DiagnosticGroups.NEW_CHECK_TYPES_EXTRA_CHECKS);
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

  public void testOneType1() {
    String js = ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.a = 0;\n"
        + "/** @type {Foo} */\n"
        + "var F = new Foo;\n"
        + "F.a = 0;";
    testSets(js, js, "{a=[[Foo.prototype]]}");

    js =
        renameFunctionDefinition
            + "/** @constructor */ function Foo() {}\n"
            + "Foo.prototype.a = 0;\n"
            + "/** @type {Foo} */\n"
            + "var F = new Foo;\n"
            + "F[goog.reflect.objectProperty('a', F)] = 0;";
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

    js =
        renameFunctionDefinition
            + "/** @constructor */ function Foo() {}\n"
            + "Foo.prototype = {a: 0};\n"
            + "/** @type {Foo} */\n"
            + "var F = new Foo;\n"
            + "F[goog.reflect.objectProperty('a', F)] = 0;";
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

    js =
        renameFunctionDefinition
            + "/** @constructor */ function Foo() {}\n"
            + "Foo.prototype = { get a() {return  0},"
            + "                  set a(b) {} };\n"
            + "/** @type {Foo} */\n"
            + "var F = new Foo;\n"
            + "F[goog.reflect.objectProperty('a', F)] = 0;";
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

    js =
        ""
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
    testSets(js, output,
        "{a=[[Bar.prototype], [Foo.prototype]], b=[[Bar.prototype], [Foo.prototype]]}");
  }

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

    this.mode = TypeInferenceMode.OTI_ONLY;
    testSets(js, expected, "{}");

    this.mode = TypeInferenceMode.NTI_ONLY;
    testSets("", js, expected, "{}", NewTypeInference.INEXISTENT_PROPERTY,
        "Property blah never defined on Object{}");
  }

  public void testIgnoreUnknownType1() {
    String js = LINE_JOINER.join(
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

  public void testIgnoreUnknownType3() {
    String js = LINE_JOINER.join(
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
    String output;

    this.mode = TypeInferenceMode.OTI_ONLY;
    output = ""
        + "/** @constructor */ function Foo(){}"
        + "/** @constructor */ function Bar(){}"
        + "Foo.function__new_Foo___undefined$a = 0;"
        + "Bar.function__new_Bar___undefined$a = 0;";

    testSets(js, output, "{a=[[function (new:Bar): undefined]," +
    " [function (new:Foo): undefined]]}");

    this.mode = TypeInferenceMode.NTI_ONLY;
    output = ""
        + "/** @constructor */ function Foo(){}"
        + "/** @constructor */ function Bar(){}"
        + "Foo.Foo__function_new_Foo__undefined__$a = 0;"
        + "Bar.Bar__function_new_Bar__undefined__$a = 0;";

    testSets(js, output,
        "{a=[[Bar<|function(new:Bar):undefined|>], [Foo<|function(new:Foo):undefined|>]]}");
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
    this.mode = TypeInferenceMode.OTI_ONLY;
    testSets(js, output, "{p1=[[Bar], [Foo]]}");
    this.mode = TypeInferenceMode.NTI_ONLY;
    testSets("", js, output, "{p1=[[Bar], [Foo]]}",
        NewTypeInference.MISTYPED_ASSIGN_RHS,
        LINE_JOINER.join(
        "The right side in the assignment is not a subtype of the left side.",
        "Expected : Foo",
        "Found    : Bar|Foo",
        "More details:",
        "The found type is a union that includes an unexpected type: Bar"));
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
    String js = LINE_JOINER.join(
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

  public void testObjectLiteralNotRenamed() {
    String js = ""
        + "var F = {a:'a', b:'b'};"
        + "F.a = 'z';";
    testSets(js, js, "{}");
  }

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

  public void testObjectLiteralBlocksPropertiesOnOtherTypes() {
    String js = LINE_JOINER.join(
        "/** @constructor */",
        "function Foo() {",
        "  this.myprop = 123;",
        "}",
        "var x = (new Foo).myprop;",
        "var y = { myprop: 'asdf' };");
    testSets(js, js, "{}");
  }

  public void testObjectLiteralDefineProperties() {
    String externs =
        LINE_JOINER.join(
            "Object.defineProperties = function(typeRef, definitions) {}",
            "/** @constructor */ function FooBar() {}",
            "/** @type {string} */ FooBar.prototype.bar_;",
            "/** @type {string} */ FooBar.prototype.bar;");

    String js =
        LINE_JOINER.join(
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

  public void testObjectLiteralDefinePropertiesQuoted() {
    String externs =
        LINE_JOINER.join(
            "Object.defineProperties = function(typeRef, definitions) {}",
            "/** @constructor */ function FooBar() {}",
            "/** @type {string} */ FooBar.prototype.bar_;",
            "/** @type {string} */ FooBar.prototype.bar;");

    String js =
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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
    String js = ""
        + "/** @constructor */ function Foo(){};"
        + "/** @constructor\n @extends Foo */"
        + "function Bar() { Foo.call(this); };"; // call should not be renamed
    testSame(js);
  }

  public void testSkipNativeObjectMethod() {
    String js = ""
        + "/** @constructor */ function Foo(){};"
        + "(new Foo).hasOwnProperty('x');";
    testSets(js, js, "{}");
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

  public void testInterface_subInterfaceAndDirectImplementors() {
    String js = LINE_JOINER.join(
        "/** @interface */ function I() {};",
        "I.prototype.a;",
        "/** @constructor @implements {I} */ function Foo() {};",
        "Foo.prototype.a;",
        "/** @interface @extends {I} */ function Bar() {};",
        "Bar.prototype.a;");
    testSets(js, "{a=[[Bar.prototype, Foo.prototype, I.prototype]]}");
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
    this.mode = TypeInferenceMode.OTI_ONLY;
    testSets(js, "{}", TypeValidator.TYPE_MISMATCH_WARNING);
    this.mode = TypeInferenceMode.NTI_ONLY;
    testSets(js, "{}", NewTypeInference.MISTYPED_ASSIGN_RHS);
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

  public void testSuperInterface2() {
    String js = LINE_JOINER.join(
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

  public void testSuperInterface3() {
    testSets(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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

    this.mode = TypeInferenceMode.OTI_ONLY;
    testSets("", js, js, "{}", TypeValidator.TYPE_MISMATCH_WARNING, "assignment\n"
            + "found   : function (new:Foo): undefined\n"
            + "required: function (new:Bar): undefined");
    this.mode = TypeInferenceMode.NTI_ONLY;
    testSets("", js, js, "{}",
        NewTypeInference.MISTYPED_ASSIGN_RHS,
        LINE_JOINER.join(
            "The right side in the assignment is not a subtype of the left side.",
            "Expected : Bar<|function(new:Bar):?|>",
            "Found    : Foo<|function(new:Foo):undefined|>",
            "More details:",
            "Incompatible types for property prototype.",
            "Expected : Bar.prototype",
            "Found    : Foo.prototype"));
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
        "function f(/** !I */ i) { return i.x; }",
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
        "/** @type {I} */ var x;",
        "x = new C()");

    testSets(js, js, "{}");
  }

  public void testDisambiguatePropertiesClassCastedToUnrelatedInterface() {
    String js = LINE_JOINER.join(
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

  public void testDontInvalidateForGenericsMismatch() {
    String js = LINE_JOINER.join(
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

    String output = LINE_JOINER.join(
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

    this.mode = TypeInferenceMode.OTI_ONLY;
    testSets(js, output, "{prop=[[Bar], [Foo]]}");
    this.mode = TypeInferenceMode.NTI_ONLY;
    testSets("", js, output, "{prop=[[Bar], [Foo]]}",
        NewTypeInference.INVALID_CAST,
        LINE_JOINER.join(
            "invalid cast - the types do not have a common subtype",
            "from: Foo<number>",
            "to  : Foo<string>"));
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

    this.mode = TypeInferenceMode.OTI_ONLY;
    testSets("", js, js, "{}", TypeValidator.TYPE_MISMATCH_WARNING,
        LINE_JOINER.join(
            "initializing variable",
            "found   : Bar",
            "required: (Foo|null)"));

    this.mode = TypeInferenceMode.NTI_ONLY;
    testSets("", js, js, "{}", NewTypeInference.MISTYPED_ASSIGN_RHS,
        LINE_JOINER.join(
            "The right side in the assignment is not a subtype of the left side.",
            "Expected : Foo|null",
            "Found    : Bar\n"));
  }

  public void testBadCast() {
    String js = "/** @constructor */ function Foo() {};\n"
        + "Foo.prototype.a = 0;\n"
        + "/** @constructor */ function Bar() {};\n"
        + "Bar.prototype.a = 0;\n"
        + "var a = /** @type {!Foo} */ (new Bar);\n"
        + "a.a = 4;";
    this.mode = TypeInferenceMode.OTI_ONLY;
    testSets("", js, js, "{}", TypeValidator.INVALID_CAST,
             "invalid cast - must be a subtype or supertype\n"
             + "from: Bar\n"
             + "to  : Foo");
    this.mode = TypeInferenceMode.NTI_ONLY;
    testSets("", js, js, "{}", NewTypeInference.INVALID_CAST,
             "invalid cast - the types do not have a common subtype\n"
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

    this.mode = TypeInferenceMode.OTI_ONLY;
    testSets(js, output, "{a=[[Bar.prototype], [Foo.prototype]]}");

    this.mode = TypeInferenceMode.NTI_ONLY;
    testSets(js, "{}", NewTypeInference.INVALID_CAST);
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

    testSets(js, js, "{}");
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

  public void testPropInParentInterface1() {
    String js = LINE_JOINER.join(
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

  public void testPropInParentInterface2() {
    String js = LINE_JOINER.join(
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
        " * @implements {MyCollection<?>}",
        " */",
        "function MyAbstractCollection() {}",
        "/** @override */",
        "MyAbstractCollection.prototype.iterator = function() {};");

    testSets(js, "{iterator=[[MyAbstractCollection.prototype, MyIterable.prototype]]}");
  }

  public void testPropInParentInterface3() {
    String js = LINE_JOINER.join(
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

    String output = LINE_JOINER.join(
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
  // Because it's not, we get wrong disambiguation.
  // On top of that, this can happen in OTI when types are joined during generics instantiation.
  // Just documenting the behavior here.
  public void testUnsafeTypingOfThis() {
    String js = LINE_JOINER.join(
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

    String output = LINE_JOINER.join(
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

    this.mode = TypeInferenceMode.OTI_ONLY;
    testSets(js, output, "{method=[[Foo.prototype]], myprop=[[Bar], [Foo]]}");
    this.mode = TypeInferenceMode.NTI_ONLY;
    testSets("", js, output,
        "{method=[[Foo.prototype]], myprop=[[Bar], [Foo]]}",
        NewTypeInference.INVALID_ARGUMENT_TYPE,
        LINE_JOINER.join(
            "Invalid type for parameter 1 of function myArrayPrototypeMap.",
            "Expected : function(this:Bar|Foo):?",
            "Found    : function(this:Foo):?\n"));

    js = LINE_JOINER.join(
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

    output = LINE_JOINER.join(
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

    testSets("", js, output,
        "{method=[[Foo.prototype]], myprop=[[Bar], [Foo]]}",
        NewTypeInference.INVALID_ARGUMENT_TYPE,
        LINE_JOINER.join(
            "Invalid type for parameter 1 of function f.",
            "Expected : function(this:Bar|Foo):?",
            "Found    : function(this:Foo):?\n"));
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

    testError(
        DEFAULT_EXTERNS + externs, js,
        DisambiguateProperties.Warnings.INVALIDATION_ON_TYPE);
    assertThat(getLastCompiler().getErrors()[0].toString()).contains("foobar");
  }

  public void testDontCrashOnNonConstructorsWithPrototype() {
    String externs = LINE_JOINER.join(
        "function f(x) { return x; }",
        "f.prototype.method = function() {};");

    test(DEFAULT_EXTERNS + externs, "" , "");
  }

  private void testSets(String js, String expected, String fieldTypes) {
    test(js, expected);
    assertEquals(fieldTypes, mapToString(lastPass.getRenamedTypesForTesting()));
  }

  private void testSets(String externs, String js, String expected, String fieldTypes) {
    test(DEFAULT_EXTERNS + externs, js, expected);
    assertEquals(fieldTypes, mapToString(lastPass.getRenamedTypesForTesting()));
  }

  private void testSets(String externs, String js, String expected,
       String fieldTypes, DiagnosticType warning, String description) {
    test(DEFAULT_EXTERNS + externs, js, expected, warning(warning, description));
    assertEquals(fieldTypes, mapToString(lastPass.getRenamedTypesForTesting()));
  }

  /**
   * Compiles the code and checks that the set of types for each field matches
   * the expected value.
   *
   * <p>The format for the set of types for fields is:
   * {field=[[Type1, Type2]]}
   */
  private void testSets(String js, String fieldTypes) {
    testNoWarning(js);
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
