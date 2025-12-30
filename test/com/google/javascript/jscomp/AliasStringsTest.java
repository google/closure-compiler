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

import com.google.javascript.jscomp.CompilerOptions.AliasStringsMode;
import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link AliasStrings}. */
@RunWith(JUnit4.class)
public final class AliasStringsTest extends CompilerTestCase {

  private static final String EXTERNS = "alert";

  private boolean hashReduction = false;

  private AliasStringsMode aliasStringsMode = AliasStringsMode.ALL;

  public AliasStringsTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    AliasStrings pass =
        new AliasStrings(compiler, compiler.getChunkGraph(), false, aliasStringsMode);
    if (hashReduction) {
      pass.unitTestHashReductionMask = 0;
    }
    return pass;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    aliasStringsMode = AliasStringsMode.ALL;
  }

  @Test
  public void testTemplateLiteral() {
    // TODO(bradfordcsmith): Maybe implement using aliases in template literals?
    test(
        """
        const A = 'aliasable string';
        const B = 'aliasable string';
        const AB = `${A}aliasable string${B}`;
        """,
        """
        var $$S_aliasable$20string = 'aliasable string';
        const A = $$S_aliasable$20string;
        const B = $$S_aliasable$20string;
        const AB = `${A}aliasable string${B}`
        """);
  }

  @Test
  public void testAliasAggressively() {
    testSame("function f() { return 'aliasable string'; }");

    aliasStringsMode = AliasStringsMode.ALL_AGGRESSIVE;
    test(
        "function f() { return 'aliasable string'; }",
        """
        var $$S_aliasable$20string = 'aliasable string';
        function f() { return $$S_aliasable$20string; }
        """);
  }

  @Test
  public void testProtectedMessage() {
    test(
        """
        const A = 'aliasable string';
        const B = 'aliasable string';
        var MSG_A =
            DEFINE_MSG_CALLEE(
                {
                  "key":    "MSG_A",
                  "msg_text":"aliasable string",
                });

        """
            .replace("DEFINE_MSG_CALLEE", ReplaceMessagesConstants.DEFINE_MSG_CALLEE),
        // msg_text's string is left unchanged instead of using an alias,
        // because `ReplaceMessages` needs the literal string here.
        """
        var $$S_aliasable$20string = 'aliasable string';
        const A = $$S_aliasable$20string;
        const B = $$S_aliasable$20string;
        var MSG_A =
            DEFINE_MSG_CALLEE(
                {
                  "key":    "MSG_A",
                  "msg_text":"aliasable string",
                });

        """
            .replace("DEFINE_MSG_CALLEE", ReplaceMessagesConstants.DEFINE_MSG_CALLEE));
  }

  @Test
  public void testLongStableAlias() {
    // Check long strings get a hash code

    test(
        """
        a='Antidisestablishmentarianism';
        b='Antidisestablishmentarianism';
        """,
        """
        var $$S_Antidisestablishment_e428eaa9=
          'Antidisestablishmentarianism';
        a=$$S_Antidisestablishment_e428eaa9;
        b=$$S_Antidisestablishment_e428eaa9
        """);

    // Check that small changes give different hash codes

    test(
        """
        a='AntidisestablishmentarianIsm';
        b='AntidisestablishmentarianIsm';
        """,
        """
        var $$S_Antidisestablishment_e4287289=
          'AntidisestablishmentarianIsm';
        a=$$S_Antidisestablishment_e4287289;
        b=$$S_Antidisestablishment_e4287289
        """);

    // TODO(user): check that hash code collisions are handled.
  }

  @Test
  public void testLongStableAliasHashCollision() {
    hashReduction = true;

    // Check that hash code collisions generate different alias
    // variable names

    test(
        """
        f('Antidisestablishmentarianism');
        f('Antidisestablishmentarianism');
        f('Antidisestablishmentarianismo');
        f('Antidisestablishmentarianismo');
        """,
        """
        var $$S_Antidisestablishment_0=
          'Antidisestablishmentarianism';
        var $$S_Antidisestablishment_0_1=
          'Antidisestablishmentarianismo';
        f($$S_Antidisestablishment_0);
        f($$S_Antidisestablishment_0);
        f($$S_Antidisestablishment_0_1);
        f($$S_Antidisestablishment_0_1);
        """);
  }

  @Test
  public void testStringsThatAreGlobalVarValues() {

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

  @Test
  public void testStringsInChunks() {

    // Aliases must be placed in the correct chunk. The alias for
    // '------adios------' must be lifted from m2 and m3 and go in the
    // common parent chunks m1

    JSChunk[] chunks =
        JSChunkGraphBuilder.forBush()
            .addChunk(
                """
                function f(a) { alert('ffffffffffffffffffff' + 'ffffffffffffffffffff' + a); }
                function g() { alert('ciaociaociaociaociao'); }
                """)
            .addChunk(
                """
                f('---------hi---------');f('bye');function h(a) { alert('hhhhhhhhhhhhhhhhhhhh'
                 + 'hhhhhhhhhhhhhhhhhhhh' + a); }
                """)
            .addChunk(
                """
                f('---------hi---------');h('ciaociaociaociaociao' +
                 '--------adios-------');(function() { alert('zzzzzzzzzzzzzzzzzzzz' +
                 'zzzzzzzzzzzzzzzzzzzz'); })();
                """)
            .addChunk(
                """
                f('---------hi---------'); alert('--------adios-------');
                h('-------peaches------'); h('-------peaches------');
                """)
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            """
            var $$S_ciaociaociaociaociao = 'ciaociaociaociaociao';
            var $$S_ffffffffffffffffffff = 'ffffffffffffffffffff';
            function f(a) {
              alert($$S_ffffffffffffffffffff + $$S_ffffffffffffffffffff + a);
            }
            function g() { alert($$S_ciaociaociaociaociao); }
            """,
            // m2
            """
            var $$S_$2d$2d$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d$2d$2d
             = '---------hi---------';
            var $$S_$2d$2d$2d$2d$2d$2d$2d$2d_adios$2d$2d$2d$2d$2d$2d$2d
             = '--------adios-------';
            var $$S_hhhhhhhhhhhhhhhhhhhh = 'hhhhhhhhhhhhhhhhhhhh';
            f($$S_$2d$2d$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d$2d$2d);
            f('bye');
            function h(a) {
              alert($$S_hhhhhhhhhhhhhhhhhhhh + $$S_hhhhhhhhhhhhhhhhhhhh + a);
            }
            """,
            // m3
            """
            var $$S_zzzzzzzzzzzzzzzzzzzz = 'zzzzzzzzzzzzzzzzzzzz';
            f($$S_$2d$2d$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d$2d$2d);
            h($$S_ciaociaociaociaociao +\s
            $$S_$2d$2d$2d$2d$2d$2d$2d$2d_adios$2d$2d$2d$2d$2d$2d$2d);
            (function() { alert($$S_zzzzzzzzzzzzzzzzzzzz + $$S_zzzzzzzzzzzzzzzzzzzz)
             })();
            """,
            // m4
            """
            var $$S_$2d$2d$2d$2d$2d$2d$2dpeaches$2d$2d$2d$2d$2d$2d
             = '-------peaches------';
            f($$S_$2d$2d$2d$2d$2d$2d$2d$2d$2dhi$2d$2d$2d$2d$2d$2d$2d$2d$2d);
            alert($$S_$2d$2d$2d$2d$2d$2d$2d$2d_adios$2d$2d$2d$2d$2d$2d$2d);
            h($$S_$2d$2d$2d$2d$2d$2d$2dpeaches$2d$2d$2d$2d$2d$2d);
            h($$S_$2d$2d$2d$2d$2d$2d$2dpeaches$2d$2d$2d$2d$2d$2d);
            """));
  }

  @Test
  public void testStringsInChunks2() {

    // Aliases must be placed in the correct chunk. The alias for
    // '------adios------' must be lifted from m2 and m3 and go in the
    // common parent chunk m1

    JSChunk[] chunks =
        JSChunkGraphBuilder.forBush()
            .addChunk("function g() { alert('ciaociaociaociaociao'); }")
            .addChunk(
                """
                function h(a) {
                  alert('hhhhhhhhhhhhhhhhhhh:' + a);
                  alert('hhhhhhhhhhhhhhhhhhh:' + a);
                }
                """)
            .addChunk("h('ciaociaociaociaociao' + 'adios');")
            .addChunk("g();")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            """
            var $$S_ciaociaociaociaociao = 'ciaociaociaociaociao';
            function g() { alert($$S_ciaociaociaociaociao); }
            """,
            // m2
            """
            var $$S_hhhhhhhhhhhhhhhhhhh$3a = 'hhhhhhhhhhhhhhhhhhh:';
            function h(a) {
              alert($$S_hhhhhhhhhhhhhhhhhhh$3a + a);
              alert($$S_hhhhhhhhhhhhhhhhhhh$3a + a);
            }
            """,
            // m3
            "h($$S_ciaociaociaociaociao + 'adios');",
            // m4
            "g();"));
  }

  @Test
  public void testAliasInCommonChunkInclusive() {

    JSChunk[] chunks =
        JSChunkGraphBuilder.forBush()
            .addChunk("")
            .addChunk("function g() { alert('ciaociaociaociaociao'); }")
            .addChunk("h('ciaociaociaociaociao' + 'adios');")
            .addChunk("g();")
            .build();

    // The "ciao" string is used in m1 and m2.
    // Since m2 depends on m1, we should create the alias there and not force it into m0.
    test(
        srcs(chunks),
        expected(
            // m0
            "",
            // m1
            """
            var $$S_ciaociaociaociaociao = 'ciaociaociaociaociao';
            function g() { alert($$S_ciaociaociaociaociao); }
            """,
            // m2
            "h($$S_ciaociaociaociaociao + 'adios');",
            // m3
            "g();"));
  }

  @Test
  public void testEmptyChunks() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            .addChunk("")
            .addChunk("function foo() { f('goodgoodgoodgoodgood') }")
            .addChunk("function foo() { f('goodgoodgoodgoodgood') }")
            .build();

    test(
        srcs(chunks),
        expected(
            // m0
            "var $$S_goodgoodgoodgoodgood='goodgoodgoodgoodgood'",
            // m1
            "function foo() {f($$S_goodgoodgoodgoodgood)}",
            // m2
            "function foo() {f($$S_goodgoodgoodgoodgood)}"));
  }

  @Test
  public void testOnlyAliasLargeStrings() {
    aliasStringsMode = AliasStringsMode.LARGE;

    test(
"""
const A = 'non aliasable string with length <= 100 characters';
const B = 'non aliasable string with length <= 100 characters';
const C = 'aliasable large string largestringlargestringlargestringlargestringlargestringlargestringlargestring!';
const D = 'aliasable large string largestringlargestringlargestringlargestringlargestringlargestringlargestring!';
""",
"""
var $$S_aliasable$20large$20stri_6c7cf169 = 'aliasable large string largestringlargestringlargestringlargestringlargestringlargestringlargestring!';
const A = 'non aliasable string with length <= 100 characters';
const B = 'non aliasable string with length <= 100 characters';
const C = $$S_aliasable$20large$20stri_6c7cf169;
const D = $$S_aliasable$20large$20stri_6c7cf169;
""");
  }
}
