/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;

import java.util.*;

/**
 * Tests for {@link AliasStrings}.
 *
 */
public class AliasStringsTest extends CompilerTestCase {

  private static final String EXTERNS = "alert";
  private static final Set<String> ALL_STRINGS = null;

  private Set<String> strings = ALL_STRINGS;
  private JSModuleGraph moduleGraph = null;
  private boolean hashReduction = false;

  public AliasStringsTest() {
    super(EXTERNS);
  }

  @Override
  public void setUp() {
    super.enableLineNumberCheck(false);
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    AliasStrings pass =
        new AliasStrings(compiler, moduleGraph, strings, "(?i)secret", false);
    if (hashReduction)
      pass.unitTestHashReductionMask = 0;
    return pass;
  }

  public void testAssignment() {
    strings = ImmutableSet.of("none", "width", "overimaginative");

    // Strings not in alias list
    testSame("var foo='foo'");
    testSame("a='titanium',b='titanium',c='titanium',d='titanium'");

    // Not worth aliasing:
    testSame("myStr='width'");
    testSame("Bar.prototype.start='none'");
    // Worth aliasing:
    test("a='overimaginative';b='overimaginative'",
         "var $$S_overimaginative='overimaginative';" +
         "a=$$S_overimaginative;b=$$S_overimaginative");

    testSame("var width=1234");
    testSame("width=1234;width=10000;width=9900;width=17;");
  }

  public void testSeveral() {
    strings = ImmutableSet.of("", "px", "none", "width");

    test("function f() {" +
         "var styles=['width',100,'px','display','none'].join('')}",
         "var $$S_='';" +
         "var $$S_none='none';" +
         "var $$S_px='px';" +
         "var $$S_width='width';" +
         "function f() {var styles=[$$S_width,100,$$S_px,'display'," +
         "$$S_none].join($$S_)}");
  }

  public void testSortedOutput() {
    strings = ImmutableSet.of("aba", "aaa", "aca", "bca", "bba");
    test("function f() {return ['aba', 'aaa', 'aca', 'bca', 'bba']}",
         "var $$S_aaa='aaa';" +
         "var $$S_aba='aba';" +
         "var $$S_aca='aca';" +
         "var $$S_bba='bba';" +
         "var $$S_bca='bca';" +
         "function f() {" +
         "  return [$$S_aba, $$S_aaa, $$S_aca, $$S_bca, $$S_bba]}");
  }

  public void testObjectLiterals() {
    strings = ImmutableSet.of("px", "!@#$%^&*()");

    test("var foo={px:435}", "var foo={px:435}");

    // string as key
    test("var foo={'px':435}", "var foo={'px':435}");
    test("bar=function f(){return {'px':435}}",
         "bar=function f(){return {'px':435}}");

    test("function f() {var foo={bar:'!@#$%^&*()'}}",
         "var $$S_$21$40$23$24$25$5e$26$2a$28$29='!@#$%^&*()';" +
         "function f() {var foo={bar:$$S_$21$40$23$24$25$5e$26$2a$28$29}}");

    test("function f() {var foo={px:435,foo:'px',bar:'baz'}}",
         "var $$S_px='px';" +
         "function f() {var foo={px:435,foo:$$S_px,bar:'baz'}}");
  }

  public void testGetProp() {
    strings = ImmutableSet.of("px", "width");

    testSame("function f(){element.style.px=1234}");

    test("function f(){shape.width.units='px'}",
        "var $$S_px='px';function f(){shape.width.units=$$S_px}");

    test("function f(){shape['width'].units='pt'}",
         "var $$S_width='width';" +
         "function f(){shape[$$S_width].units='pt'}");
  }

  public void testFunctionCalls() {
    strings = ImmutableSet.of("", ",", "overimaginative");

    // Not worth aliasing
    testSame("alert('')");
    testSame("var a=[1,2,3];a.join(',')");
    // worth aliasing
    test("f('overimaginative', 'overimaginative')",
         "var $$S_overimaginative='overimaginative';" +
         "f($$S_overimaginative,$$S_overimaginative)");
 }

  public void testRegularExpressions() {
    strings = ImmutableSet.of("px");

    testSame("/px/.match('10px')");
  }

  public void testBlackList() {
    test("(function (){var f=\'sec ret\';g=\"TOPseCreT\"})",
         "var $$S_sec$20ret='sec ret';" +
         "(function (){var f=$$S_sec$20ret;g=\"TOPseCreT\"})");
  }

  public void testLongStableAlias() {
    strings = ALL_STRINGS;

    // Check long strings get a hash code

    test("a='Antidisestablishmentarianism';" +
         "b='Antidisestablishmentarianism';",
         "var $$S_Antidisestablishment_506eaf9c=" +
         "  'Antidisestablishmentarianism';"      +
         "a=$$S_Antidisestablishment_506eaf9c;"   +
         "b=$$S_Antidisestablishment_506eaf9c");

    // Check that small changes give different hash codes

    test("a='AntidisestablishmentarianIsm';" +
         "b='AntidisestablishmentarianIsm';",
         "var $$S_Antidisestablishment_6823e97c=" +
         "  'AntidisestablishmentarianIsm';"      +
         "a=$$S_Antidisestablishment_6823e97c;"   +
         "b=$$S_Antidisestablishment_6823e97c");

    // TODO(user): check that hash code collisions are handled.
  }

  public void testLongStableAliasHashCollision() {
    strings = ALL_STRINGS;
    hashReduction = true;

    // Check that hash code collisions generate different alias
    // variable names

    test("f('Antidisestablishmentarianism');"  +
         "f('Antidisestablishmentarianism');"  +
         "f('Antidisestablishmentarianismo');" +
         "f('Antidisestablishmentarianismo');",

         "var $$S_Antidisestablishment_0="     +
         "  'Antidisestablishmentarianism';"   +
         "var $$S_Antidisestablishment_0_1="   +
         "  'Antidisestablishmentarianismo';"  +

         "f($$S_Antidisestablishment_0);"      +
         "f($$S_Antidisestablishment_0);"      +
         "f($$S_Antidisestablishment_0_1);"    +
         "f($$S_Antidisestablishment_0_1);");
  }


  public void testStringsThatAreGlobalVarValues() {
    strings = ALL_STRINGS;

    testSame("var foo='foo'; var bar='';");

    // Regular array
    testSame("var foo=['foo','bar'];");

    // Nested array
    testSame("var foo=['foo',['bar']];");

    // Same string is in a global array and a local in a function
    test("var foo=['foo', 'bar'];function bar() {return 'foo';}",
        "var $$S_foo='foo';" +
        "var foo=[$$S_foo, 'bar'];function bar() {return $$S_foo;}");

    // Regular object literal
    testSame("var foo={'foo': 'bar'};");

    // Nested object literal
    testSame("var foo={'foo': {'bar': 'baz'}};");

    // Same string is in a global object literal (as key) and local in a
    // function
    test("var foo={'foo': 'bar'};function bar() {return 'foo';}",
        "var $$S_foo='foo';var foo={'foo': 'bar'};" +
        "function bar() {return $$S_foo;}");

    // Same string is in a global object literal (as value) and local in a
    // function
    test("var foo={'foo': 'foo'};function bar() {return 'foo';}",
         "var $$S_foo='foo';" +
         "var foo={'foo': $$S_foo};function bar() {return $$S_foo;}");

  }

  public void testStringsInModules() {
    strings = ALL_STRINGS;

    // Aliases must be placed in the correct module. The alias for
    // '------adios------' must be lifted from m2 and m3 and go in the
    // common parent module m1

    JSModule[] modules =
        createModuleBush(
            // m0
            "function f(a) { alert('f:' + a); }" +
            "function g() { alert('ciao'); }",
            // m1
            "f('-------hi-------');" +
            "f('bye');" +
            "function h(a) { alert('h:' + a); }",
            // m2
            "f('-------hi-------');" +
            "h('ciao' + '------adios------');" +
            "(function() { alert('zzz'); })();",
            // m3
            "f('-------hi-------'); alert('------adios------');" +
            "h('-----peaches-----'); h('-----peaches-----');");

    moduleGraph = new JSModuleGraph(modules);

    test(modules,
         new String[] {
             // m1
             "var $$S_ciao = 'ciao';" +
             "var $$S_f$3a = 'f:';" +
             "function f(a) { alert($$S_f$3a + a); }" +
             "function g() { alert($$S_ciao); }",
             // m2
             "var $$S_$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d" +
             " = '-------hi-------';" +
             "var $$S_$2d$2d$2d$2d$2d$2d_adios$2d$2d$2d$2d$2d$2d" +
             " = '------adios------'; " +
             "var $$S_h$3a = 'h:';" +
             "f($$S_$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d);" +
             "f('bye');" +
             "function h(a) { alert($$S_h$3a + a); }",
             // m3
             "var $$S_zzz = 'zzz';" +
             "f($$S_$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d);" +
             "h($$S_ciao + $$S_$2d$2d$2d$2d$2d$2d_adios$2d$2d$2d$2d$2d$2d);" +
             "(function() { alert($$S_zzz) })();",
             // m4
             "var $$S_$2d$2d$2d$2d$2dpeaches$2d$2d$2d$2d$2d" +
             " = '-----peaches-----';" +
             "f($$S_$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d);" +
             "alert($$S_$2d$2d$2d$2d$2d$2d_adios$2d$2d$2d$2d$2d$2d);" +
             "h($$S_$2d$2d$2d$2d$2dpeaches$2d$2d$2d$2d$2d);" +
             "h($$S_$2d$2d$2d$2d$2dpeaches$2d$2d$2d$2d$2d);",
         });
    moduleGraph = null;
  }

  public void testStringsInModules2() {
    strings = ALL_STRINGS;

    // Aliases must be placed in the correct module. The alias for
    // '------adios------' must be lifted from m2 and m3 and go in the
    // common parent module m1

    JSModule[] modules =
        createModuleBush(
            // m0
            "function g() { alert('ciao'); }",
            // m1
            "function h(a) { alert('h:' + a); }",
            // m2
            "h('ciao' + 'adios');",
            // m3
            "g();");

    moduleGraph = new JSModuleGraph(modules);

    test(modules,
         new String[] {
             // m1
             "var $$S_ciao = 'ciao';" +
             "function g() { alert($$S_ciao); }",
             // m2
             "var $$S_h$3a = 'h:';" +
             "function h(a) { alert($$S_h$3a + a); }",
             // m3
             "h($$S_ciao + 'adios');",
             // m4
             "g();",
         });
    moduleGraph = null;
  }


  public void testEmptyModules() {
    JSModule[] modules =
      createModuleStar(
          // m0
          "",
          // m1
          "function foo() { f('good') }",
          // m2
          "function foo() { f('good') }");

    moduleGraph = new JSModuleGraph(modules);
    test(modules,
        new String[] {
        // m0
        "var $$S_good='good'",
        // m1
        "function foo() {f($$S_good)}",
        // m2
        "function foo() {f($$S_good)}",});

    moduleGraph = null;
  }
}
