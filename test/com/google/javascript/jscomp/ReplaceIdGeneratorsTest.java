/*
 * Copyright 2009 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.ReplaceIdGenerators.INVALID_GENERATOR_PARAMETER;
import static com.google.javascript.jscomp.ReplaceIdGenerators.INVALID_TEMPLATE_LITERAL_PARAMETER;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ReplaceIdGenerators}. */
@RunWith(JUnit4.class)
public final class ReplaceIdGeneratorsTest extends CompilerTestCase {

  private boolean generatePseudoNames = false;
  private @Nullable ReplaceIdGenerators lastPass = null;
  private @Nullable String previousMappings = null;

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    RenamingMap idTestMap =
        new RenamingMap() {
          private final ImmutableMap<String, String> map =
              ImmutableMap.of(
                  "foo", ":foo:",
                  "bar", ":bar:");

          @Override
          public String get(String value) {
            String replacement = map.get(value);
            return replacement != null ? replacement : "unknown:" + value;
          }
        };
    lastPass =
        new ReplaceIdGenerators(
            compiler,
            /* templateLiteralsAreTranspiled= */ compiler
                .getOptions()
                .needsTranspilationOf(Feature.TEMPLATE_LITERALS),
            new ImmutableMap.Builder<String, RenamingMap>()
                .put("goog.events.getUniqueId", RenamingToken.INCONSISTENT)
                .put("goog.place.getUniqueId", RenamingToken.INCONSISTENT)
                .put("id", idTestMap)
                .put("get.id", idTestMap)
                .buildOrThrow(),
            generatePseudoNames,
            previousMappings,
            /* xidHashFunction= */ null);
    return lastPass;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    generatePseudoNames = false;
    previousMappings = null;
  }

  @Test
  public void testBackwardCompat() {
    testWithPseudo(
        "foo.bar = goog.events.getUniqueId('foo_bar')", "foo.bar = 'a'", "foo.bar = 'foo_bar$0'");
  }

  @Test
  public void testSerialization1() {
    testMap(
        """
        var x = goog.events.getUniqueId('xxx');
        var y = goog.events.getUniqueId('yyy');
        """,
        """
        var x = 'a';
        var y = 'b';
        """,
        """
        [goog.events.getUniqueId]

        a:testcode:1:32
        b:testcode:2:32
        """);
  }

  @Test
  public void testSerialization2() {
    testMap(
        """
        /** @idGenerator {consistent} */
        xid = function() {};
        f1 = xid('f1');
        f1 = xid('f1')
        """,
        """
        /** @idGenerator {consistent} */
        xid = function() {};
        f1 = 'a';
        f1 = 'a'
        """,
        """
        [xid]

        a:f1
        """);
  }

  @Test
  public void testReusePreviousSerialization1() {
    previousMappings =
        """
        [goog.events.getUniqueId]

        previous1:testcode:1:32
        previous2:testcode:2:32

        [goog.place.getUniqueId]
        """;
    testMap(
        """
        var x = goog.events.getUniqueId('xxx');
        var y = goog.events.getUniqueId('yyy');
        """,
        """
        var x = 'previous1';
        var y = 'previous2';
        """,
        """
        [goog.events.getUniqueId]

        previous1:testcode:1:32
        previous2:testcode:2:32

        """);
  }

  @Test
  public void testReusePreviousSerialization2() {
    previousMappings =
        """
        [goog.events.getUniqueId]

        a:testcode:1:32
        b:testcode:2:32

        [goog.place.getUniqueId]


        """;
    testMap(
        "var x = goog.events.getUniqueId('xxx');\n"
            + "\n"
            + // new line to change location
            "var y = goog.events.getUniqueId('yyy');\n",
        """
        var x = 'a';
        var y = 'c';
        """,
        """
        [goog.events.getUniqueId]

        a:testcode:1:32
        c:testcode:3:32

        """);
  }

  @Test
  public void testReusePreviousSerializationConsistent1() {
    previousMappings =
        """
        [cid]

        a:f1

        """;
    testMap(
        """
        /** @idGenerator {consistent} */ cid = function() {};
        f1 = cid('f1');
        f1 = cid('f1')
        """,
        """
        /** @idGenerator {consistent} */ cid = function() {};
        f1 = 'a';
        f1 = 'a'
        """,
        """
        [cid]

        a:f1

        """);
  }

  @Test
  public void testNullishCoalesce() {
    testWithPseudo(
        """
        /** @idGenerator */ foo.getUniqueId = function() {x ?? y;};
        foo.bar = foo.getUniqueId('foo_bar')
        """,
        """
        /** @idGenerator */ foo.getUniqueId = function() {x ?? y;};
        foo.bar = 'a'
        """,
        """
        /** @idGenerator */ foo.getUniqueId = function() {x ?? y;};
        foo.bar = 'foo_bar$0'
        """);
  }

  @Test
  public void testSimple() {
    testWithPseudo(
        """
        /** @idGenerator */ foo.getUniqueId = function() {};
        foo.bar = foo.getUniqueId('foo_bar')
        """,
        """
        /** @idGenerator */ foo.getUniqueId = function() {};
        foo.bar = 'a'
        """,
        """
        /** @idGenerator */ foo.getUniqueId = function() {};
        foo.bar = 'foo_bar$0'
        """);

    testWithPseudo(
        """
        /** @idGenerator */ goog.events.getUniqueId = function() {};
        foo1 = goog.events.getUniqueId('foo1');
        foo1 = goog.events.getUniqueId('foo1');
        """,
        """
        /** @idGenerator */ goog.events.getUniqueId = function() {};
        foo1 = 'a';
        foo1 = 'b';
        """,
        """
        /** @idGenerator */ goog.events.getUniqueId = function() {};
        foo1 = 'foo1$0';
        foo1 = 'foo1$1';
        """);
  }

  @Test
  public void testIndirectCall() {
    testWithPseudo(
        """
        /** @idGenerator */ foo.getUniqueId = function() {};
        foo.bar = (0, foo.getUniqueId)('foo_bar')
        """,
        """
        /** @idGenerator */ foo.getUniqueId = function() {};
        foo.bar = 'a'
        """,
        """
        /** @idGenerator */ foo.getUniqueId = function() {};
        foo.bar = 'foo_bar$0'
        """);
    // JSCompiler inserts JSCOMPILER_PRESERVE(...) in the "UselessCode" analysis.
    testWithPseudo(
        """
        /** @idGenerator */ foo.getUniqueId = function() {};
        foo.bar = (JSCOMPILER_PRESERVE(0), foo.getUniqueId)('foo_bar')
        """,
        """
        /** @idGenerator */ foo.getUniqueId = function() {};
        foo.bar = 'a'
        """,
        """
        /** @idGenerator */ foo.getUniqueId = function() {};
        foo.bar = 'foo_bar$0'
        """);
  }

  @Test
  public void testObjectLit() {
    testWithPseudo(
        """
        /** @idGenerator */ goog.id = function() {};
        things = goog.id({foo1: 'test', 'foo bar': 'test'})
        """,
        """
        /** @idGenerator */ goog.id = function() {};
        things = {'a': 'test', 'b': 'test'}
        """,
        """
        /** @idGenerator */ goog.id = function() {};
        things = {'foo1$0': 'test', 'foo bar$1': 'test'}
        """);
  }

  @Test
  public void testObjectLit_mapped() {
    testNonPseudoSupportingGenerator(
        """
        /** @idGenerator {mapped} */ id = function() {};
        things = id({foo: 'test', 'bar': 'test'})
        """,
        """
        /** @idGenerator {mapped} */ id = function() {};
        things = {':foo:': 'test', ':bar:': 'test'}
        """);
  }

  @Test
  public void testObjectLit_xid() {
    testNonPseudoSupportingGenerator(
        """
        /** @idGenerator {xid} */ xid.object = function() {};
        things = xid.object({foo: 'test', 'value': 'test'})
        """,
        """
        /** @idGenerator {xid} */ xid.object = function() {};
        things = {'QB6rXc': 'test', 'b6Lt6c': 'test'}
        """);
  }

  @Test
  public void testObjectLit_empty() {
    testWithPseudo(
        "/** @idGenerator */ goog.id = function() {}; things = goog.id({})",
        "/** @idGenerator */ goog.id = function() {}; things = {}",
        "/** @idGenerator */ goog.id = function() {}; things = {}");
  }

  @Test
  public void testObjectLit_function() {
    testWithPseudo(
        """
        /** @idGenerator */ goog.id = function() {};
        things = goog.id({foo: function() {}})
        """,
        """
        /** @idGenerator */ goog.id = function() {};
        things = {'a': function() {}}
        """,
        """
        /** @idGenerator */ goog.id = function() {};
        things = {'foo$0': function() {}}
        """);

    testWithPseudo(
        """
        /** @idGenerator */ goog.id = function() {};
        things = goog.id({foo: function*() {}})
        """,
        """
        /** @idGenerator */ goog.id = function() {};
        things = {'a': function*() {}}
        """,
        """
        /** @idGenerator */ goog.id = function() {};
        things = {'foo$0': function*() {}}
        """);
  }

  @Test
  public void testObjectLit_ES6() {
    testError(
        """
        /** @idGenerator */
        goog.id = function() {};
        things = goog.id({fooX() {}})
        """,
        ReplaceIdGenerators.SHORTHAND_FUNCTION_NOT_SUPPORTED_IN_ID_GEN);

    testError(
        """
        /** @idGenerator */
        goog.id = function() {};
        things = goog.id({['fooX']: 'test'})
        """,
        ReplaceIdGenerators.COMPUTED_PROP_NOT_SUPPORTED_IN_ID_GEN);
  }

  @Test
  public void testClass() {
    testSame(
        externs(""),
        srcs(
            """
            /** @idGenerator */
            goog.id = function() {};
            things = goog.id(class fooBar{})
            """),
        warning(ReplaceIdGenerators.INVALID_GENERATOR_PARAMETER));
  }

  @Test
  public void testSimpleConsistent() {
    testWithPseudo(
        "/** @idGenerator {consistent} */ cid = function() {}; foo.bar = cid('foo_bar')",
        "/** @idGenerator {consistent} */ cid = function() {}; foo.bar = 'a'",
        "/** @idGenerator {consistent} */ cid = function() {}; foo.bar = 'foo_bar$0'");

    testWithPseudo(
        "/** @idGenerator {consistent} */ cid = function() {}; f1 = cid('f1'); f1 = cid('f1')",
        "/** @idGenerator {consistent} */ cid = function() {}; f1 = 'a'; f1 = 'a'",
        "/** @idGenerator {consistent} */ cid = function() {}; f1 = 'f1$0'; f1 = 'f1$0'");

    testWithPseudo(
        """
        /** @idGenerator {consistent} */ cid = function() {};
        f1 = cid('f1');
        f1 = cid('f1');
        f1 = cid('f1')
        """,
        """
        /** @idGenerator {consistent} */ cid = function() {};
        f1 = 'a';
        f1 = 'a';
        f1 = 'a'
        """,
        """
        /** @idGenerator {consistent} */ cid = function() {};
        f1 = 'f1$0';
        f1 = 'f1$0';
        f1 = 'f1$0'
        """);
  }

  @Test
  public void testSimpleStable() {
    testNonPseudoSupportingGenerator(
        "/** @idGenerator {stable} */ sid = function() {};" + "foo.bar = sid('foo_bar')",
        "/** @idGenerator {stable} */ sid = function() {};" + "foo.bar = '125lGg'");

    testNonPseudoSupportingGenerator(
        "/** @idGenerator {stable} */ sid = function() {};" + "f1 = sid('f1');" + "f1 = sid('f1')",
        "/** @idGenerator {stable} */ sid = function() {};" + "f1 = 'AAAMiw';" + "f1 = 'AAAMiw'");
  }

  @Test
  public void testSimpleXid() {
    testNonPseudoSupportingGenerator(
        """
        /** @idGenerator {xid} */ xid = function() {};
        foo.bar = xid('foo')
        """,
        """
        /** @idGenerator {xid} */ xid = function() {};
        foo.bar = 'QB6rXc'
        """);

    testNonPseudoSupportingGenerator(
        """
        /** @idGenerator {xid} */ xid = function() {};
        f1 = xid('foo');
        f1 = xid('foo')
        """,
        """
        /** @idGenerator {xid} */ xid = function() {};
        f1 = 'QB6rXc';
        f1 = 'QB6rXc'
        """);

    testNonPseudoSupportingGenerator(
        """
        /** @idGenerator {xid} */ xid = function() {};
        f1 = xid(`foo`);
        f1 = xid(`foo`)
        """,
        """
        /** @idGenerator {xid} */ xid = function() {};
        f1 = 'QB6rXc';
        f1 = 'QB6rXc'
        """);
  }

  @Test
  public void testVar() {
    testWithPseudo(
        """
        /** @idGenerator {consistent} */ var cid = function() {};
        foo.bar = cid('foo_bar')
        """,
        """
        /** @idGenerator {consistent} */ var cid = function() {};
        foo.bar = 'a'
        """,
        """
        /** @idGenerator {consistent} */ var cid = function() {};
        foo.bar = 'foo_bar$0'
        """);

    testNonPseudoSupportingGenerator(
        """
        /** @idGenerator {stable} */ var sid = function() {};
        foo.bar = sid('foo_bar')
        """,
        """
        /** @idGenerator {stable} */ var sid = function() {};
        foo.bar = '125lGg'
        """);
  }

  @Test
  public void testLet() {
    testWithPseudo(
        """
        /** @idGenerator {consistent} */ let cid = function() {};
        foo.bar = cid('foo_bar')
        """,
        """
        /** @idGenerator {consistent} */ let cid = function() {};
        foo.bar = 'a'
        """,
        """
        /** @idGenerator {consistent} */ let cid = function() {};
        foo.bar = 'foo_bar$0'
        """);

    testNonPseudoSupportingGenerator(
        "/** @idGenerator {stable} */ let sid = function() {};" + "foo.bar = sid('foo_bar')",
        "/** @idGenerator {stable} */ let sid = function() {};" + "foo.bar = '125lGg'");
  }

  @Test
  public void testConst() {
    testWithPseudo(
        """
        /** @idGenerator {consistent} */ const cid = function() {};
        foo.bar = cid('foo_bar')
        """,
        """
        /** @idGenerator {consistent} */ const cid = function() {};
        foo.bar = 'a'
        """,
        """
        /** @idGenerator {consistent} */ const cid = function() {};
        foo.bar = 'foo_bar$0'
        """);

    testNonPseudoSupportingGenerator(
        """
        /** @idGenerator {stable} */ const cid = function() {};
        foo.bar = cid('foo_bar')
        """,
        """
        /** @idGenerator {stable} */ const cid = function() {};
        foo.bar = '125lGg'
        """);
  }

  @Test
  public void testInObjLit() {
    testWithPseudo(
        """
        /** @idGenerator {consistent} */ cid = function() {};
        foo.bar = {a: cid('foo_bar')}
        """,
        """
        /** @idGenerator {consistent} */ cid = function() {};
        foo.bar = {a: 'a'}
        """,
        """
        /** @idGenerator {consistent} */ cid = function() {};
        foo.bar = {a: 'foo_bar$0'}
        """);

    testNonPseudoSupportingGenerator(
        """
        /** @idGenerator {stable} */ sid = function() {};
        foo.bar = {a: sid('foo_bar')}
        """,
        """
        /** @idGenerator {stable} */ sid = function() {};
        foo.bar = {a: '125lGg'}
        """);

    testNonPseudoSupportingGenerator(
        """
        /** @idGenerator {xid} */ get.xid = function() {};
        foo.bar = {a: get.xid('foo')}
        """,
        """
        /** @idGenerator {xid} */ get.xid = function() {};
        foo.bar = {a: 'QB6rXc'}
        """);
  }

  @Test
  public void testInObjLit_mapped() {
    testWithPseudo("foo.bar = {a: id('foo')}", "foo.bar = {a: ':foo:'}", "foo.bar = {a: ':foo:'}");
  }

  @Test
  public void testMapped() {
    testWithPseudo("foo.bar = id('foo');", "foo.bar = ':foo:';", "foo.bar = ':foo:';");
  }

  @Test
  public void testMappedMap() {
    testMap(
        """
        foo.bar = id('foo');
        foo.bar = id('foo');
        """,
        """
        foo.bar = ':foo:';
        foo.bar = ':foo:';
        """,
        """
        [id]

        :foo::foo
        """);
  }

  @Test
  public void testMapped2() {
    testWithPseudo(
        """
        /** @idGenerator {mapped}*/ id = function() {};
        foo.bar = function() { return id('foo'); };
        """,
        """
        /** @idGenerator {mapped}*/ id = function() {};
        foo.bar = function() { return ':foo:'; };
        """,
        """
        /** @idGenerator {mapped}*/ id = function() {};
        foo.bar = function() { return ':foo:'; };
        """);
  }

  @Test
  public void testTwoGenerators() {
    testWithPseudo(
        """
        /** @idGenerator */ var id1 = function() {};
        /** @idGenerator */ var id2 = function() {};
        f1 = id1('1');
        f2 = id1('1');
        f3 = id2('1');
        f4 = id2('1');
        """,
        """
        /** @idGenerator */ var id1 = function() {};
        /** @idGenerator */ var id2 = function() {};
        f1 = 'a';
        f2 = 'b';
        f3 = 'a';
        f4 = 'b';
        """,
        """
        /** @idGenerator */ var id1 = function() {};
        /** @idGenerator */ var id2 = function() {};
        f1 = '1$0';
        f2 = '1$1';
        f3 = '1$0';
        f4 = '1$1';
        """);
  }

  @Test
  public void testMixedGenerators() {
    testWithPseudo(
        """
        /** @idGenerator */ var id1 = function() {};
        /** @idGenerator {consistent} */ var id2 = function() {};
        /** @idGenerator {stable} */ var id3 = function() {};
        f1 = id1('1');
        f2 = id1('1');
        f3 = id2('1');
        f4 = id2('1');
        f5 = id3('1');
        f6 = id3('1');
        """,
        """
        /** @idGenerator */ var id1 = function() {};
        /** @idGenerator {consistent} */ var id2 = function() {};
        /** @idGenerator {stable} */ var id3 = function() {};
        f1 = 'a';
        f2 = 'b';
        f3 = 'a';
        f4 = 'a';
        f5 = 'AAAAMQ';
        f6 = 'AAAAMQ';
        """,
        """
        /** @idGenerator */ var id1 = function() {};
        /** @idGenerator {consistent} */ var id2 = function() {};
        /** @idGenerator {stable} */ var id3 = function() {};
        f1 = '1$0';
        f2 = '1$1';
        f3 = '1$0';
        f4 = '1$0';
        f5 = 'AAAAMQ';
        f6 = 'AAAAMQ';
        """);
  }

  @Test
  public void testNonLiteralParam1() {
    testSame(
        "/** @idGenerator */ var id = function() {}; " + "var x = 'foo';" + "id(x);",
        ReplaceIdGenerators.INVALID_GENERATOR_PARAMETER);
  }

  @Test
  public void testNonLiteralParam2() {
    testSame(
        "/** @idGenerator */ var id = function() {}; " + "id('foo' + 'bar');",
        ReplaceIdGenerators.INVALID_GENERATOR_PARAMETER);
  }

  @Test
  public void testLocalCall() {
    testError(
        "/** @idGenerator */ var iid = function() {}; " + "function Foo() { iid('foo'); }",
        ReplaceIdGenerators.NON_GLOBAL_ID_GENERATOR_CALL);
  }

  @Test
  public void testConditionalCall() {
    testError(
        """
        /** @idGenerator */
        var xid = function() {};
        while(0){ xid('foo');}
        """,
        ReplaceIdGenerators.CONDITIONAL_ID_GENERATOR_CALL);

    testError(
        """
        /** @idGenerator */
        var xid = function() {};
        for(;;){ xid('foo');}
        """,
        ReplaceIdGenerators.CONDITIONAL_ID_GENERATOR_CALL);

    testError(
        "/** @idGenerator */ var xid = function() {}; " + "if(x) xid('foo');",
        ReplaceIdGenerators.CONDITIONAL_ID_GENERATOR_CALL);

    testWithPseudo(
        """
        /** @idGenerator {consistent} */ var xid = function() {};
        function fb() {foo.bar = xid('foo_bar')}
        """,
        """
        /** @idGenerator {consistent} */ var xid = function() {};
        function fb() {foo.bar = 'a'}
        """,
        """
        /** @idGenerator {consistent} */ var xid = function() {};
        function fb() {foo.bar = 'foo_bar$0'}
        """);

    testNonPseudoSupportingGenerator(
        """
        /** @idGenerator {stable} */ var xid = function() {};
        function fb() {foo.bar = xid('foo_bar')}
        """,
        """
        /** @idGenerator {stable} */ var xid = function() {};
        function fb() {foo.bar = '125lGg'}
        """);

    testError(
        """
        /** @idGenerator */
        var xid = function() {};
        for(x of [1, 2, 3]){ xid('foo');}
        """,
        ReplaceIdGenerators.CONDITIONAL_ID_GENERATOR_CALL);
  }

  @Test
  public void testConflictingIdGenerator() {
    setExpectParseWarningsInThisTest();
    testSame("/** @idGenerator \n @idGenerator {consistent} \n*/ var xid = function() {}; ");

    testSame("/** @idGenerator {stable} \n @idGenerator \n*/ var xid = function() {}; ");

    testSame(
        """
        /** @idGenerator {stable}
         @idGenerator {consistent}
         */
        var xid = function() {};
        """);
  }

  @Test
  public void testConsistentIdGenUnderPseudoRenaming() {
    testWithPseudo(
        """
        /** @idGenerator {consistent} */ var cid = function() {};
        if (x) {foo.bar = cid('foo_bar')}
        """,
        """
        /** @idGenerator {consistent} */ var cid = function() {};
        if (x) {foo.bar = 'a'}
        """,
        """
        /** @idGenerator {consistent} */ var cid = function() {};
        if (x) {foo.bar = 'foo_bar$0'}
        """);
  }

  @Test
  public void testUnknownMapping() {
    testSame(
        """
        /** @idGenerator {mapped} */
        var unknownId = function() {};
        function Foo() { unknownId('foo'); }
        """,
        ReplaceIdGenerators.MISSING_NAME_MAP_FOR_GENERATOR);
  }

  @Test
  public void testBadGenerator1() {
    testSame(
        "/** @idGenerator */ id = function() {};" + "foo.bar = id()", INVALID_GENERATOR_PARAMETER);
  }

  @Test
  public void testBadGenerator2() {
    testSame(
        "/** @idGenerator {consistent} */ id = function() {};" + "foo.bar = id()",
        INVALID_GENERATOR_PARAMETER);
  }

  @Test
  public void testBadGenerator3() {
    testSame(
        "/** @idGenerator {consistent} */ id = function() {};"
            + "foo.bar = id(`hello${ ' '}world`)",
        INVALID_GENERATOR_PARAMETER);
  }

  @Test
  public void testTTLCall() {
    testMap(
        """
        /** @idGenerator {stable} */ function tagId(strings) {return strings[0];};
        function Foo() { tagId`foo`; }
        """,
        """
        /** @idGenerator {stable} */ function tagId(strings) {return strings[0];};
        function Foo() { 'AAGMxg'; }
        """,
        """
        [tagId]

        AAGMxg:foo
        """);
    testMap(
        """
        /** @idGenerator {stable} */ function tagId(strings, ...args) {return strings[0];};
        function Foo() { tagId`foo${1}bar${2}`; }
        """,
        """
        /** @idGenerator {stable} */ function tagId(strings, ...args) {return strings[0];};
        function Foo() { tagId`AAGMxg${1}AAF8Ew${2}AAAAAA`; }
        """,
        """
        [tagId]

        AAGMxg:foo
        AAF8Ew:bar
        AAAAAA:
        """);
  }

  // The application may or may not be transpiling, ensure we support transpiled mode
  @Test
  public void testTTLCall_transpiled() {
    enableTranspile();
    testMap(
        """
        /** @idGenerator {stable} */ function tagId(strings) {return strings[0];};
        function Foo() { tagId`foo`; }
        """,
        """
        /** @const */
        var $jscomp = $jscomp || {};
        /** @const */
        $jscomp.scope = {};
        /**
         * @nosideeffects
         * @noinline
         * @param {!ITemplateArray} arrayStrings
         * @return {!ITemplateArray}
         */
        $jscomp.createTemplateTagFirstArg = function(arrayStrings) {
          return $jscomp.createTemplateTagFirstArgWithRaw(arrayStrings, arrayStrings)
        };
        /**
         * @nosideeffects
         * @noinline
         * @param {!ITemplateArray} arrayStrings
         * @param {!ITemplateArray} rawArrayStrings
         * @return {!ITemplateArray}
         */
        $jscomp.createTemplateTagFirstArgWithRaw = function(arrayStrings, rawArrayStrings) {
          arrayStrings.raw = rawArrayStrings;
          Object.freeze && (Object.freeze(arrayStrings), Object.freeze(rawArrayStrings));
          return (/** @type {!ITemplateArray} */ (arrayStrings));
        };
        /** @idGenerator {stable} */ function tagId(strings) {return strings[0];};
        function Foo() { 'AAGMxg'; }
        """,
        """
        [tagId]

        AAGMxg:foo
        """);
    enableTranspile();
    testMap(
        """
        /** @idGenerator {stable} */ function tagId(strings, ...args) {return strings[0];};
        function Foo() { tagId`foo${1}bar${2}`; }
        """,
        """
        /** @idGenerator {stable} */ function tagId(strings, ...args) {return strings[0];};
        function Foo() { tagId`AAGMxg${1}AAF8Ew${2}AAAAAA`; }
        """,
        """
        [tagId]

        AAGMxg:foo
        AAF8Ew:bar
        AAAAAA:
        """);
  }

  @Test
  public void testTTLCall_invalid() {
    testSame(
        "/** @idGenerator {stable} */ function tagId(strings) {};\n"
            // invalid escape sequence.  We don't support this though we could.
            + "function Foo() { tagId`$\\underline{u}$`;}\n",
        INVALID_TEMPLATE_LITERAL_PARAMETER);
  }

  private void testMap(String code, String expected, String expectedMap) {
    test(code, expected);
    assertThat(lastPass.getSerializedIdMappings().trim()).isEqualTo(expectedMap.trim());
  }

  private void testWithPseudo(String code, String expected, String expectedPseudo) {
    generatePseudoNames = false;
    test(code, expected);
    generatePseudoNames = true;
    test(code, expectedPseudo);
  }

  private void testNonPseudoSupportingGenerator(String code, String expected) {
    generatePseudoNames = false;
    test(code, expected);
    generatePseudoNames = true;
    test(code, expected);
  }
}
