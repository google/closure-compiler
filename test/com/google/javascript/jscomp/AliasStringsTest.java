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
import java.util.Set;

/**
 * Tests for {@link AliasStrings}.
 *
 */
public final class AliasStringsTest extends CompilerTestCase {

  private static final String EXTERNS = "alert";
  private static final Set<String> ALL_STRINGS = null;

  private Set<String> strings = ALL_STRINGS;
  private boolean hashReduction = false;

  public AliasStringsTest() {
    super(EXTERNS);
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    AliasStrings pass =
        new AliasStrings(compiler, compiler.getModuleGraph(), strings, "(?i)secret", false);
    if (hashReduction) {
      pass.unitTestHashReductionMask = 0;
    }
    return pass;
  }

  public void testAssignment() {
    strings = ImmutableSet.of("none", "width", "overimaginative");

    // Strings not in alias list
    testSame("var foo='foo'");
    testSame("if(true) {myStr='width'}");
    testSame("a='titanium',b='titanium',c='titanium',d='titanium'");

    // Not worth aliasing:
    testSame("myStr='width'");
    testSame("Bar.prototype.start='none'");
    // Worth aliasing:
    test("a='overimaginative';b='overimaginative'",
         "var $$S_overimaginative='overimaginative';" +
         "a=$$S_overimaginative;b=$$S_overimaginative");
    test("if(true) {a='overimaginative';b='overimaginative'}",
        "var $$S_overimaginative='overimaginative';" +
        "if(true) {a=$$S_overimaginative;b=$$S_overimaginative }");

    testSame("var width=1234");
    testSame("width=1234;width=10000;width=9900;width=17;");
  }

  public void testSeveral() {
    strings = ImmutableSet.of("", "px", "none", "width");

    // 'display' is not in the allowed string set and only 'none' and 'width' are large and common
    // enough to be worth interning.
    test(
        "function f() {"
            + "  var styles1 = ["
            + "      'width', 100, 'px', 'display', 'none'"
            + "  ].join('');"
            + "  var styles2 = ["
            + "      'width', 100, 'px', 'display', 'none'"
            + "  ].join('');"
            + "  var styles3 = ["
            + "      'width', 100, 'px', 'display', 'none'"
            + "  ].join('');"
            + "  var styles4 = ["
            + "      'width', 100, 'px', 'display', 'none'"
            + "  ].join('');"
            + "  var styles5 = ["
            + "      'width', 100, 'px', 'display', 'none'"
            + "  ].join('');"
            + "  var styles6 = ["
            + "      'width', 100, 'px', 'display', 'none'"
            + "  ].join('');"
            + "}",
        "var $$S_none = 'none';"
            + "var $$S_width = 'width';"
            + "function f() {"
            + "  var styles1 = ["
            + "      $$S_width, 100, 'px', 'display', $$S_none"
            + "  ].join('');"
            + "  var styles2 = ["
            + "      $$S_width, 100, 'px', 'display', $$S_none"
            + "  ].join('');"
            + "  var styles3 = ["
            + "      $$S_width, 100, 'px', 'display', $$S_none"
            + "  ].join('');"
            + "  var styles4 = ["
            + "      $$S_width, 100, 'px', 'display', $$S_none"
            + "  ].join('');"
            + "  var styles5 = ["
            + "      $$S_width, 100, 'px', 'display', $$S_none"
            + "  ].join('');"
            + "  var styles6 = ["
            + "      $$S_width, 100, 'px', 'display', $$S_none"
            + "  ].join('')"
            + "}");
  }

  public void testSortedOutput() {
    strings =
        ImmutableSet.of(
            "abababababababababab",
            "aaaaaaaaaaaaaaaaaaaa",
            "acacacacacacacacacac",
            "bcabcabcabcabcabcabc",
            "bbabbabbabbabbabbabb");
    test(
        "function f() {return ['abababababababababab', 'abababababababababab', "
            + "                       'aaaaaaaaaaaaaaaaaaaa', 'aaaaaaaaaaaaaaaaaaaa', "
            + "                       'acacacacacacacacacac', 'acacacacacacacacacac', "
            + "                       'bcabcabcabcabcabcabc', 'bcabcabcabcabcabcabc', "
            + "                       'bbabbabbabbabbabbabb', 'bbabbabbabbabbabbabb']}",
        "var $$S_aaaaaaaaaaaaaaaaaaaa='aaaaaaaaaaaaaaaaaaaa';"
            + "var $$S_abababababababababab='abababababababababab';"
            + "var $$S_acacacacacacacacacac='acacacacacacacacacac';"
            + "var $$S_bbabbabbabbabbabbabb='bbabbabbabbabbabbabb';"
            + "var $$S_bcabcabcabcabcabcabc='bcabcabcabcabcabcabc';"
            + "function f() {"
            + "  return [$$S_abababababababababab, $$S_abababababababababab, "
            + "          $$S_aaaaaaaaaaaaaaaaaaaa, $$S_aaaaaaaaaaaaaaaaaaaa, "
            + "          $$S_acacacacacacacacacac, $$S_acacacacacacacacacac, "
            + "          $$S_bcabcabcabcabcabcabc, $$S_bcabcabcabcabcabcabc, "
            + "          $$S_bbabbabbabbabbabbabb, $$S_bbabbabbabbabbabbabb]}");
  }

  public void testObjectLiterals() {
    strings = ImmutableSet.of("pxpxpxpxpxpxpxpxpxpx", "abcdefghijabcdefghij");

    testSame("var foo={px:435}");

    // string as key
    testSame("var foo={'pxpxpxpxpxpxpxpxpxpx':435}");
    testSame("bar=function f(){return {'pxpxpxpxpxpxpxpxpxpx':435}}");

    test(
        "function f() {var foo={bar:'abcdefghijabcdefghij'+'abcdefghijabcdefghij'}}",
        "var $$S_abcdefghijabcdefghij='abcdefghijabcdefghij';"
            + "function f() {var foo={bar:$$S_abcdefghijabcdefghij+$$S_abcdefghijabcdefghij}}");

    test(
        "function f() {"
            + "  var foo = {"
            + "      px: 435,"
            + "      foo1: 'pxpxpxpxpxpxpxpxpxpx',"
            + "      foo2: 'pxpxpxpxpxpxpxpxpxpx',"
            + "      bar: 'baz'"
            + "  }"
            + "}",
        "var $$S_pxpxpxpxpxpxpxpxpxpx = 'pxpxpxpxpxpxpxpxpxpx';"
            + "function f() {"
            + "  var foo = {"
            + "      px: 435,"
            + "      foo1: $$S_pxpxpxpxpxpxpxpxpxpx,"
            + "      foo2: $$S_pxpxpxpxpxpxpxpxpxpx,"
            + "      bar: 'baz'"
            + "  }"
            + "}");
  }

  public void testGetProp() {
    strings = ImmutableSet.of("pxpxpxpxpxpxpxpxpxpx", "widthwidthwidthwidth");

    testSame("function f(){element.style.px=1234}");

    test(
        "function f() {"
            + "  shape.width.units='pxpxpxpxpxpxpxpxpxpx';"
            + "  shape.width.units='pxpxpxpxpxpxpxpxpxpx';"
            + "}",
        "var $$S_pxpxpxpxpxpxpxpxpxpx='pxpxpxpxpxpxpxpxpxpx';"
            + "function f() {"
            + "  shape.width.units=$$S_pxpxpxpxpxpxpxpxpxpx;"
            + "  shape.width.units=$$S_pxpxpxpxpxpxpxpxpxpx;"
            + "}");

    test(
        "function f() {"
            + "  shape['widthwidthwidthwidth'].units='pt';"
            + "  shape['widthwidthwidthwidth'].units='pt';"
            + "}",
        "var $$S_widthwidthwidthwidth='widthwidthwidthwidth';"
            + "function f() {"
            + "  shape[$$S_widthwidthwidthwidth].units='pt';"
            + "  shape[$$S_widthwidthwidthwidth].units='pt';"
            + "}");
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
    // The 'TOPseCreT' string is configured to be ignored even though it fits the aliasing
    // conditions.
    test(
        "(function() {"
            + "  var f = 'sec ret sec ret sec ' + 'sec ret sec ret sec ';"
            + "  g = 'TOPseCreT TOPseCreT ' + 'TOPseCreT TOPseCreT '"
            + "})"
            + "",
        "var $$S_sec$20ret$20sec$20ret$20sec$20 = 'sec ret sec ret sec ';"
            + "(function() {"
            + "  var f = $$S_sec$20ret$20sec$20ret$20sec$20 + $$S_sec$20ret$20sec$20ret$20sec$20;"
            + "  g = 'TOPseCreT TOPseCreT ' + 'TOPseCreT TOPseCreT '"
            + "})");
  }

  public void testLongStableAlias() {
    strings = ALL_STRINGS;

    // Check long strings get a hash code

    test("a='Antidisestablishmentarianism';" +
         "b='Antidisestablishmentarianism';",
         "var $$S_Antidisestablishment_e428eaa9=" +
         "  'Antidisestablishmentarianism';"      +
         "a=$$S_Antidisestablishment_e428eaa9;"   +
         "b=$$S_Antidisestablishment_e428eaa9");

    // Check that small changes give different hash codes

    test("a='AntidisestablishmentarianIsm';" +
         "b='AntidisestablishmentarianIsm';",
         "var $$S_Antidisestablishment_e4287289=" +
         "  'AntidisestablishmentarianIsm';"      +
         "a=$$S_Antidisestablishment_e4287289;"   +
         "b=$$S_Antidisestablishment_e4287289");

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
    testSame("var foo=['foo', 'bar'];function bar() {return 'foo';}");

    // Regular object literal
    testSame("var foo={'foo': 'bar'};");

    // Nested object literal
    testSame("var foo={'foo': {'bar': 'baz'}};");

    // Same string is in a global object literal (as key) and local in a
    // function
    testSame("var foo={'foo': 'bar'};function bar() {return 'foo';}");

    // Same string is in a global object literal (as value) and local in a
    // function
    testSame("var foo={'foo': 'foo'};function bar() {return 'foo';}");
  }

  public void testStringsInModules() {
    strings = ALL_STRINGS;

    // Aliases must be placed in the correct module. The alias for
    // '------adios------' must be lifted from m2 and m3 and go in the
    // common parent module m1

    JSModule[] modules =
        createModuleBush(
            // m0
            "function f(a) { alert('ffffffffffffffffffff' + 'ffffffffffffffffffff' + a); }"
                + "function g() { alert('ciaociaociaociaociao'); }",
            // m1
            "f('---------hi---------');"
                + "f('bye');"
                + "function h(a) { alert('hhhhhhhhhhhhhhhhhhhh' + 'hhhhhhhhhhhhhhhhhhhh' + a); }",
            // m2
            "f('---------hi---------');"
                + "h('ciaociaociaociaociao' + '--------adios-------');"
                + "(function() { alert('zzzzzzzzzzzzzzzzzzzz' + 'zzzzzzzzzzzzzzzzzzzz'); })();",
            // m3
            "f('---------hi---------'); alert('--------adios-------');"
                + "h('-------peaches------'); h('-------peaches------');");

    test(
        modules,
        new String[] {
          // m1
          "var $$S_ciaociaociaociaociao = 'ciaociaociaociaociao';"
              + "var $$S_ffffffffffffffffffff = 'ffffffffffffffffffff';"
              + "function f(a) { alert($$S_ffffffffffffffffffff + $$S_ffffffffffffffffffff + a); }"
              + "function g() { alert($$S_ciaociaociaociaociao); }",
          // m2
          "var $$S_$2d$2d$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d$2d$2d"
              + " = '---------hi---------';"
              + "var $$S_$2d$2d$2d$2d$2d$2d$2d$2d_adios$2d$2d$2d$2d$2d$2d$2d"
              + " = '--------adios-------'; "
              + "var $$S_hhhhhhhhhhhhhhhhhhhh = 'hhhhhhhhhhhhhhhhhhhh';"
              + "f($$S_$2d$2d$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d$2d$2d);"
              + "f('bye');"
              + "function h(a) { alert($$S_hhhhhhhhhhhhhhhhhhhh + $$S_hhhhhhhhhhhhhhhhhhhh + a); }",
          // m3
          "var $$S_zzzzzzzzzzzzzzzzzzzz = 'zzzzzzzzzzzzzzzzzzzz';"
              + "f($$S_$2d$2d$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d$2d$2d);"
              + "h($$S_ciaociaociaociaociao + "
              + "$$S_$2d$2d$2d$2d$2d$2d$2d$2d_adios$2d$2d$2d$2d$2d$2d$2d);"
              + "(function() { alert($$S_zzzzzzzzzzzzzzzzzzzz + $$S_zzzzzzzzzzzzzzzzzzzz) })();",
          // m4
          "var $$S_$2d$2d$2d$2d$2d$2d$2dpeaches$2d$2d$2d$2d$2d$2d"
              + " = '-------peaches------';"
              + "f($$S_$2d$2d$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d$2d$2d);"
              + "alert($$S_$2d$2d$2d$2d$2d$2d$2d$2d_adios$2d$2d$2d$2d$2d$2d$2d);"
              + "h($$S_$2d$2d$2d$2d$2d$2d$2dpeaches$2d$2d$2d$2d$2d$2d);"
              + "h($$S_$2d$2d$2d$2d$2d$2d$2dpeaches$2d$2d$2d$2d$2d$2d);",
        });
  }

  public void testStringsInModules2() {
    strings = ALL_STRINGS;

    // Aliases must be placed in the correct module. The alias for
    // '------adios------' must be lifted from m2 and m3 and go in the
    // common parent module m1

    JSModule[] modules =
        createModuleBush(
            // m0
            "function g() { alert('ciaociaociaociaociao'); }",
            // m1
            "function h(a) {"
                + "  alert('hhhhhhhhhhhhhhhhhhh:' + a);"
                + "  alert('hhhhhhhhhhhhhhhhhhh:' + a);"
                + "}",
            // m2
            "h('ciaociaociaociaociao' + 'adios');",
            // m3
            "g();");

    test(
        modules,
        new String[] {
          // m1
          LINE_JOINER.join(
              "var $$S_ciaociaociaociaociao = 'ciaociaociaociaociao';",
              "function g() { alert($$S_ciaociaociaociaociao); }"),
          // m2
          LINE_JOINER.join(
              "var $$S_hhhhhhhhhhhhhhhhhhh$3a = 'hhhhhhhhhhhhhhhhhhh:';",
              "function h(a) {"
                  + "  alert($$S_hhhhhhhhhhhhhhhhhhh$3a + a);"
                  + "  alert($$S_hhhhhhhhhhhhhhhhhhh$3a + a);"
                  + "}"),
          // m3
          "h($$S_ciaociaociaociaociao + 'adios');",
          // m4
          "g();",
        });
  }

  public void testAliasInCommonModuleInclusive() {
    strings = ALL_STRINGS;

    JSModule[] modules =
        createModuleBush(
            // m0
            "",
            // m1
            "function g() { alert('ciaociaociaociaociao'); }",
            // m2
            "h('ciaociaociaociaociao' + 'adios');",
            // m3
            "g();");

    // The "ciao" string is used in m1 and m2.
    // Since m2 depends on m1, we should create the module there and not force it into m0.
    test(
        modules,
        new String[] {
          // m0
          "",
          // m1
          LINE_JOINER.join(
              "var $$S_ciaociaociaociaociao = 'ciaociaociaociaociao';",
              "function g() { alert($$S_ciaociaociaociaociao); }"),
          // m2
          "h($$S_ciaociaociaociaociao + 'adios');",
          // m3
          "g();",
        });
  }


  public void testEmptyModules() {
    JSModule[] modules =
        createModuleStar(
            // m0
            "",
            // m1
            "function foo() { f('goodgoodgoodgoodgood') }",
            // m2
            "function foo() { f('goodgoodgoodgoodgood') }");

    test(
        modules,
        new String[] {
          // m0
          "var $$S_goodgoodgoodgoodgood='goodgoodgoodgoodgood'",
          // m1
          "function foo() {f($$S_goodgoodgoodgoodgood)}",
          // m2
          "function foo() {f($$S_goodgoodgoodgoodgood)}",
        });
  }
}
