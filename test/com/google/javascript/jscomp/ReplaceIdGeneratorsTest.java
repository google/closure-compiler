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

import static com.google.javascript.jscomp.ReplaceIdGenerators.INVALID_GENERATOR_PARAMETER;

import com.google.common.collect.ImmutableMap;

/**
 * Tests for {@link ReplaceIdGenerators}.
 *
 */
public class ReplaceIdGeneratorsTest extends CompilerTestCase {

  private boolean generatePseudoNames = false;
  private ReplaceIdGenerators lastPass = null;
  private String previousMappings = null;

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    RenamingMap xidTestMap = new RenamingMap() {
      private final ImmutableMap<String, String> map = ImmutableMap.of(
          "foo", ":foo:",
          "bar", ":bar:");
      @Override
      public String get(String value) {
        String replacement = map.get(value);
        return replacement != null ? replacement : "unknown:" + value;
      }
    };

    lastPass = new ReplaceIdGenerators(
        compiler,
        new ImmutableMap.Builder<String, RenamingMap>()
            .put("goog.events.getUniqueId", ReplaceIdGenerators.UNIQUE)
            .put("goog.place.getUniqueId", ReplaceIdGenerators.UNIQUE)
            .put("xid", xidTestMap)
            .build(),
        generatePseudoNames,
        previousMappings);
    return lastPass;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    generatePseudoNames = false;
    previousMappings = null;
    compareJsDoc = false;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testBackwardCompat() {
    test("foo.bar = goog.events.getUniqueId('foo_bar')",
         "foo.bar = 'a'",
         "foo.bar = 'foo_bar$0'");
  }

  public void testSerialization1() {
    testMap("var x = goog.events.getUniqueId('xxx');\n" +
            "var y = goog.events.getUniqueId('yyy');\n",

            "var x = 'a';\n" +
            "var y = 'b';\n",

            "[goog.events.getUniqueId]\n" +
            "\n" +
            "a:testcode:1:32\n" +
            "b:testcode:2:32\n" +
            "\n");
  }

  public void testSerialization2() {
    testMap("/** @consistentIdGenerator */ id = function() {};" +
         "f1 = id('f1');" +
         "f1 = id('f1')",

         "id = function() {};" +
         "f1 = 'a';" +
         "f1 = 'a'",

         "[id]\n" +
         "\n" +
         "a:f1\n" +
         "\n");
  }

  public void testReusePreviousSerialization1() {
    previousMappings =
        "[goog.events.getUniqueId]\n" +
        "\n" +
        "previous1:testcode:1:32\n" +
        "previous2:testcode:2:32\n" +
        "\n" +
        "[goog.place.getUniqueId]\n" +
        "\n" +
        "\n";
    testMap("var x = goog.events.getUniqueId('xxx');\n" +
            "var y = goog.events.getUniqueId('yyy');\n",

            "var x = 'previous1';\n" +
            "var y = 'previous2';\n",

            "[goog.events.getUniqueId]\n" +
            "\n" +
            "previous1:testcode:1:32\n" +
            "previous2:testcode:2:32\n" +
            "\n");
  }

  public void testReusePreviousSerialization2() {
    previousMappings =
        "[goog.events.getUniqueId]\n" +
        "\n" +
        "a:testcode:1:32\n" +
        "b:testcode:2:32\n" +
        "\n" +
        "[goog.place.getUniqueId]\n" +
        "\n" +
        "\n";
    testMap(
        "var x = goog.events.getUniqueId('xxx');\n" +
        "\n" + // new line to change location
        "var y = goog.events.getUniqueId('yyy');\n",

        "var x = 'a';\n" +
        "var y = 'c';\n",

        "[goog.events.getUniqueId]\n" +
        "\n" +
        "a:testcode:1:32\n" +
        "c:testcode:3:32\n" +
        "\n");
  }

  public void testReusePreviousSerializationConsistent1() {
    previousMappings =
        "[id]\n" +
        "\n" +
        "a:f1\n" +
        "\n";
    testMap(
        "/** @consistentIdGenerator */ id = function() {};" +
        "f1 = id('f1');" +
        "f1 = id('f1')",

        "id = function() {};" +
        "f1 = 'a';" +
        "f1 = 'a'",

        "[id]\n" +
        "\n" +
        "a:f1\n" +
        "\n");
  }

  public void testSimple() {
    test("/** @idGenerator */ foo.getUniqueId = function() {};" +
         "foo.bar = foo.getUniqueId('foo_bar')",

         "foo.getUniqueId = function() {};" +
         "foo.bar = 'a'",

         "foo.getUniqueId = function() {};" +
         "foo.bar = 'foo_bar$0'");

    test("/** @idGenerator */ goog.events.getUniqueId = function() {};" +
        "foo1 = goog.events.getUniqueId('foo1');" +
        "foo1 = goog.events.getUniqueId('foo1');",

        "goog.events.getUniqueId = function() {};" +
        "foo1 = 'a';" +
        "foo1 = 'b';",

        "goog.events.getUniqueId = function() {};" +
        "foo1 = 'foo1$0';" +
        "foo1 = 'foo1$1';");
  }

  public void testObjectLit() {
    test("/** @idGenerator */ goog.xid = function() {};" +
        "things = goog.xid({foo1: 'test', 'foo bar': 'test'})",

        "goog.xid = function() {};" +
        "things = {'a': 'test', 'b': 'test'}",

        "goog.xid = function() {};" +
        "things = {'foo1$0': 'test', 'foo bar$1': 'test'}");
  }

  public void testObjectLit_empty() {
    test("/** @idGenerator */ goog.xid = function() {};" +
        "things = goog.xid({})",

        "goog.xid = function() {};" +
        "things = {}",

        "goog.xid = function() {};" +
        "things = {}");
  }

  public void testSimpleConsistent() {
    test("/** @consistentIdGenerator */ id = function() {};" +
         "foo.bar = id('foo_bar')",

         "id = function() {};" +
         "foo.bar = 'a'",

         "id = function() {};" +
         "foo.bar = 'foo_bar$0'");

    test("/** @consistentIdGenerator */ id = function() {};" +
         "f1 = id('f1');" +
         "f1 = id('f1')",

         "id = function() {};" +
         "f1 = 'a';" +
         "f1 = 'a'",

         "id = function() {};" +
         "f1 = 'f1$0';" +
         "f1 = 'f1$0'");

    test("/** @consistentIdGenerator */ id = function() {};" +
        "f1 = id('f1');" +
        "f1 = id('f1');" +
        "f1 = id('f1')",

        "id = function() {};" +
        "f1 = 'a';" +
        "f1 = 'a';" +
        "f1 = 'a'",

        "id = function() {};" +
        "f1 = 'f1$0';" +
        "f1 = 'f1$0';" +
        "f1 = 'f1$0'");
  }

  public void testSimpleStable() {
    testNonPseudoSupportingGenerator(
        "/** @stableIdGenerator */ id = function() {};" +
        "foo.bar = id('foo_bar')",

        "id = function() {};" +
        "foo.bar = '125lGg'");

    testNonPseudoSupportingGenerator(
        "/** @stableIdGenerator */ id = function() {};" +
        "f1 = id('f1');" +
        "f1 = id('f1')",

        "id = function() {};" +
        "f1 = 'AAAMiw';" +
        "f1 = 'AAAMiw'");
  }

  public void testVar() {
    test("/** @consistentIdGenerator */ var id = function() {};" +
         "foo.bar = id('foo_bar')",

         "var id = function() {};" +
         "foo.bar = 'a'",

         "var id = function() {};" +
         "foo.bar = 'foo_bar$0'");

    testNonPseudoSupportingGenerator(
        "/** @stableIdGenerator */ var id = function() {};" +
        "foo.bar = id('foo_bar')",

        "var id = function() {};" +
        "foo.bar = '125lGg'");
  }

  public void testInObjLit() {
    test("/** @consistentIdGenerator */ get.id = function() {};" +
         "foo.bar = {a: get.id('foo_bar')}",

         "get.id = function() {};" +
         "foo.bar = {a: 'a'}",

         "get.id = function() {};" +
         "foo.bar = {a: 'foo_bar$0'}");

    testNonPseudoSupportingGenerator(
        "/** @stableIdGenerator */ get.id = function() {};" +
        "foo.bar = {a: get.id('foo_bar')}",

        "get.id = function() {};" +
        "foo.bar = {a: '125lGg'}");
  }

  public void testInObjLit2() {
    test("/** @idGenerator {mapped}*/ xid = function() {};" +
         "foo.bar = {a: xid('foo')}",

         "xid = function() {};" +
         "foo.bar = {a: ':foo:'}",

         "xid = function() {};" +
         "foo.bar = {a: ':foo:'}");
  }

  public void testMapped() {
    test("/** @idGenerator {mapped}*/ xid = function() {};" +
        "foo.bar = xid('foo');",

        "xid = function() {};" +
        "foo.bar = ':foo:';",

        "xid = function() {};" +
        "foo.bar = ':foo:';");
  }

  public void testMappedMap() {
    testMap("/** @idGenerator {mapped}*/ xid = function() {};" +
        "foo.bar = xid('foo');" +
        "foo.bar = xid('foo');",

        "xid = function() {};" +
        "foo.bar = ':foo:';" +
        "foo.bar = ':foo:';",

        "[xid]\n" +
        "\n" +
        ":foo::foo\n" +
        "\n");
  }

  public void testMapped2() {
    test("/** @idGenerator {mapped}*/ xid = function() {};" +
        "foo.bar = function() { return xid('foo'); };",

        "xid = function() {};" +
        "foo.bar = function() { return ':foo:'; };",

        "xid = function() {};" +
        "foo.bar = function() { return ':foo:'; };");
  }

  public void testTwoGenerators() {
    test("/** @idGenerator */ var id1 = function() {};" +
         "/** @idGenerator */ var id2 = function() {};" +
         "f1 = id1('1');" +
         "f2 = id1('1');" +
         "f3 = id2('1');" +
         "f4 = id2('1');",

         "var id1 = function() {};" +
         "var id2 = function() {};" +
         "f1 = 'a';" +
         "f2 = 'b';" +
         "f3 = 'a';" +
         "f4 = 'b';",

         "var id1 = function() {};" +
         "var id2 = function() {};" +
         "f1 = '1$0';" +
         "f2 = '1$1';" +
         "f3 = '1$0';" +
         "f4 = '1$1';");
  }

  public void testMixedGenerators() {
    test("/** @idGenerator */ var id1 = function() {};" +
         "/** @consistentIdGenerator */ var id2 = function() {};" +
         "/** @stableIdGenerator */ var id3 = function() {};" +
         "f1 = id1('1');" +
         "f2 = id1('1');" +
         "f3 = id2('1');" +
         "f4 = id2('1');" +
         "f5 = id3('1');" +
         "f6 = id3('1');",

         "var id1 = function() {};" +
         "var id2 = function() {};" +
         "var id3 = function() {};" +
         "f1 = 'a';" +
         "f2 = 'b';" +
         "f3 = 'a';" +
         "f4 = 'a';" +
         "f5 = 'AAAAMQ';" +
         "f6 = 'AAAAMQ';",

         "var id1 = function() {};" +
         "var id2 = function() {};" +
         "var id3 = function() {};" +
         "f1 = '1$0';" +
         "f2 = '1$1';" +
         "f3 = '1$0';" +
         "f4 = '1$0';" +
         "f5 = 'AAAAMQ';" +
         "f6 = 'AAAAMQ';");
  }

  public void testNonLiteralParam1() {
    testSame(new String[] {"/** @idGenerator */ var id = function() {}; " +
                           "var x = 'foo';" +
                           "id(x);"},
        null,
        ReplaceIdGenerators.INVALID_GENERATOR_PARAMETER);
  }

  public void testNonLiteralParam2() {
    testSame(new String[] {"/** @idGenerator */ var id = function() {}; " +
                           "id('foo' + 'bar');"},
        null,
        ReplaceIdGenerators.INVALID_GENERATOR_PARAMETER);
  }

  public void testLocalCall() {
    testSame(new String[] {"/** @idGenerator */ var id = function() {}; " +
                           "function Foo() { id('foo'); }"},
        ReplaceIdGenerators.NON_GLOBAL_ID_GENERATOR_CALL);
  }

  public void testConditionalCall() {
    testSame(new String[] {"/** @idGenerator */ var id = function() {}; " +
                           "if(x) id('foo');"},
        ReplaceIdGenerators.CONDITIONAL_ID_GENERATOR_CALL);

    test("/** @consistentIdGenerator */ var id = function() {};" +
        "function fb() {foo.bar = id('foo_bar')}",

        "var id = function() {};" +
        "function fb() {foo.bar = 'a'}",

        "var id = function() {};" +
        "function fb() {foo.bar = 'foo_bar$0'}");

    testNonPseudoSupportingGenerator(
        "/** @stableIdGenerator */ var id = function() {};" +
        "function fb() {foo.bar = id('foo_bar')}",

        "var id = function() {};" +
        "function fb() {foo.bar = '125lGg'}");
  }

  public void testConflictingIdGenerator() {
    testSame(new String[] {"/** @idGenerator \n @consistentIdGenerator \n*/" +
                           "var id = function() {}; "},
        ReplaceIdGenerators.CONFLICTING_GENERATOR_TYPE);

    testSame(new String[] {"/** @stableIdGenerator \n @idGenerator \n*/" +
                           "var id = function() {}; "},
        ReplaceIdGenerators.CONFLICTING_GENERATOR_TYPE);

    testSame(new String[] {"/** @stableIdGenerator \n " +
                           "@consistentIdGenerator \n*/" +
                           "var id = function() {}; "},
        ReplaceIdGenerators.CONFLICTING_GENERATOR_TYPE);

    test("/** @consistentIdGenerator */ var id = function() {};" +
        "if (x) {foo.bar = id('foo_bar')}",

        "var id = function() {};" +
        "if (x) {foo.bar = 'a'}",

        "var id = function() {};" +
        "if (x) {foo.bar = 'foo_bar$0'}");
  }

  public void testUnknownMapping() {
    testSame("" +
        "/** @idGenerator {mapped} */\n" +
        "var id = function() {};\n" +
        "function Foo() { id('foo'); }\n",
        ReplaceIdGenerators.MISSING_NAME_MAP_FOR_GENERATOR);
  }

  public void testBadGenerator1() {
    testSame("/** @idGenerator */ id = function() {};" +
         "foo.bar = id()",
         INVALID_GENERATOR_PARAMETER);
  }

  public void testBadGenerator2() {
    testSame("/** @consistentIdGenerator */ id = function() {};" +
         "foo.bar = id()",
         INVALID_GENERATOR_PARAMETER);
  }

  private void testMap(String code, String expected, String expectedMap) {
    test(code, expected);
    assertEquals(expectedMap, lastPass.getSerializedIdMappings());
  }

  private void test(String code, String expected, String expectedPseudo) {
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
