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
public final class ReplaceIdGeneratorsTest extends Es6CompilerTestCase {

  private boolean generatePseudoNames = false;
  private ReplaceIdGenerators lastPass = null;
  private String previousMappings = null;

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    RenamingMap idTestMap = new RenamingMap() {
      private final ImmutableMap<String, String> map = ImmutableMap.of(
          "foo", ":foo:",
          "bar", ":bar:");
      @Override
      public String get(String value) {
        String replacement = map.get(value);
        return replacement != null ? replacement : "unknown:" + value;
      }
    };
    RenamingMap gen = new UniqueRenamingToken();
    lastPass = new ReplaceIdGenerators(
        compiler,
        new ImmutableMap.Builder<String, RenamingMap>()
            .put("goog.events.getUniqueId", gen)
            .put("goog.place.getUniqueId", gen)
            .put("id", idTestMap)
            .put("get.id", idTestMap)
            .build(),
        generatePseudoNames,
        previousMappings,
        null /* xidHashFunction */);
    return lastPass;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    generatePseudoNames = false;
    previousMappings = null;
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
    testMap(
        LINE_JOINER.join(
        "var x = goog.events.getUniqueId('xxx');",
        "var y = goog.events.getUniqueId('yyy');"),

        LINE_JOINER.join(
        "var x = 'a';",
        "var y = 'b';"),

        LINE_JOINER.join(
        "[goog.events.getUniqueId]",
        "",
        "a:testcode:1:32",
        "b:testcode:2:32",
        "", ""));
  }

  public void testSerialization2() {
    testMap(
        LINE_JOINER.join(
        "/** @consistentIdGenerator */",
        "id = function() {};",
        "f1 = id('f1');",
        "f1 = id('f1')"),

        LINE_JOINER.join(
        "/** @consistentIdGenerator */",
        "id = function() {};",
        "f1 = 'a';",
        "f1 = 'a'"),

        LINE_JOINER.join(
        "[id]",
        "",
        "a:f1",
        "", ""));
  }

  public void testReusePreviousSerialization1() {
    previousMappings =
        LINE_JOINER.join(
        "[goog.events.getUniqueId]",
        "",
        "previous1:testcode:1:32",
        "previous2:testcode:2:32",
        "",
        "[goog.place.getUniqueId]",
        "", "");
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
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ id = function() {};",
        "f1 = id('f1');",
        "f1 = id('f1')"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ id = function() {};",
        "f1 = 'a';",
        "f1 = 'a'"),

        "[id]\n" +
        "\n" +
        "a:f1\n" +
        "\n");
  }

  public void testSimple() {
    test(
    	LINE_JOINER.join(
    	"/** @idGenerator */ foo.getUniqueId = function() {};",
        "foo.bar = foo.getUniqueId('foo_bar')"),
        LINE_JOINER.join(
        "/** @idGenerator */ foo.getUniqueId = function() {};",
        "foo.bar = 'a'"),
        LINE_JOINER.join(
        "/** @idGenerator */ foo.getUniqueId = function() {};",
        "foo.bar = 'foo_bar$0'"));

    test(
    	LINE_JOINER.join(
    	"/** @idGenerator */ goog.events.getUniqueId = function() {};",
        "foo1 = goog.events.getUniqueId('foo1');",
        "foo1 = goog.events.getUniqueId('foo1');"),
        LINE_JOINER.join(
        "/** @idGenerator */ goog.events.getUniqueId = function() {};",
        "foo1 = 'a';",
        "foo1 = 'b';"),
        LINE_JOINER.join(
        "/** @idGenerator */ goog.events.getUniqueId = function() {};",
        "foo1 = 'foo1$0';",
        "foo1 = 'foo1$1';"));
  }

  public void testObjectLit() {
    test(LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = goog.id({foo1: 'test', 'foo bar': 'test'})"),
        LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = {'a': 'test', 'b': 'test'}"),
        LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = {'foo1$0': 'test', 'foo bar$1': 'test'}"));
  }

  public void testObjectLit_mapped() {
    testNonPseudoSupportingGenerator(
        LINE_JOINER.join(
        "/** @idGenerator {mapped} */ id = function() {};",
        "things = id({foo: 'test', 'bar': 'test'})"),
         LINE_JOINER.join(
        "/** @idGenerator {mapped} */ id = function() {};",
        "things = {':foo:': 'test', ':bar:': 'test'}"));
  }

  public void testObjectLit_xid() {
    testNonPseudoSupportingGenerator(
        LINE_JOINER.join(
        "/** @idGenerator {xid} */ xid.object = function() {};",
        "things = xid.object({foo: 'test', 'value': 'test'})"),
        LINE_JOINER.join(
        "/** @idGenerator {xid} */ xid.object = function() {};",
        "things = {'QB6rXc': 'test', 'b6Lt6c': 'test'}"));
  }

  public void testObjectLit_empty() {
    test(
        LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = goog.id({})"),
        LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = {}"),
        LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = {}"));
  }

  public void testObjectLit_function() {
    test(
        LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = goog.id({foo: function() {}})"),
        LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = {'a': function() {}}"),
        LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = {'foo$0': function() {}}"));

    testEs6(
        LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = goog.id({foo: function*() {}})"),
        LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = {'a': function*() {}}"),
        LINE_JOINER.join(
        "/** @idGenerator */ goog.id = function() {};",
        "things = {'foo$0': function*() {}}"));
  }

  public void testObjectLit_ES6() {
    testErrorEs6(LINE_JOINER.join(
        "/** @idGenerator */",
        "goog.id = function() {};",
        "things = goog.id({fooX() {}})"),
        ReplaceIdGenerators.SHORTHAND_FUNCTION_NOT_SUPPORTED_IN_ID_GEN);

    testErrorEs6(LINE_JOINER.join(
        "/** @idGenerator */ ",
        "goog.id = function() {};",
        "things = goog.id({shorthand})"),
        ReplaceIdGenerators.SHORTHAND_ASSIGNMENT_NOT_SUPPORTED_IN_ID_GEN);

    testErrorEs6(LINE_JOINER.join(
        "/** @idGenerator */",
        "goog.id = function() {};",
        "things = goog.id({['fooX']: 'test'})"),
        ReplaceIdGenerators.COMPUTED_PROP_NOT_SUPPORTED_IN_ID_GEN);
  }

  public void testClass() {
    testSameEs6("", LINE_JOINER.join(
        "/** @idGenerator */",
        "goog.id = function() {};",
        "things = goog.id(class fooBar{})"),
        ReplaceIdGenerators.INVALID_GENERATOR_PARAMETER);
  }

  public void testSimpleConsistent() {
    test(
    	LINE_JOINER.join(
    	"/** @consistentIdGenerator */ id = function() {};",
        "foo.bar = id('foo_bar')"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ id = function() {};",
        "foo.bar = 'a'"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ id = function() {};",
        "foo.bar = 'foo_bar$0'"));

    test(
    	LINE_JOINER.join(
    	"/** @consistentIdGenerator */ id = function() {};",
        "f1 = id('f1');",
        "f1 = id('f1')"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ id = function() {};",
        "f1 = 'a';",
        "f1 = 'a'"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ id = function() {};",
        "f1 = 'f1$0';",
        "f1 = 'f1$0'"));

    test(
    	LINE_JOINER.join(
    	"/** @consistentIdGenerator */ id = function() {};",
        "f1 = id('f1');",
        "f1 = id('f1');",
        "f1 = id('f1')"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ id = function() {};",
        "f1 = 'a';",
        "f1 = 'a';",
        "f1 = 'a'"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ id = function() {};",
        "f1 = 'f1$0';",
        "f1 = 'f1$0';",
        "f1 = 'f1$0'"));
  }

  public void testSimpleStable() {
    testNonPseudoSupportingGenerator(
        "/** @stableIdGenerator */ id = function() {};" +
        "foo.bar = id('foo_bar')",

        "/** @stableIdGenerator */ id = function() {};" +
        "foo.bar = '125lGg'");

    testNonPseudoSupportingGenerator(
        "/** @stableIdGenerator */ id = function() {};" +
        "f1 = id('f1');" +
        "f1 = id('f1')",

        "/** @stableIdGenerator */ id = function() {};" +
        "f1 = 'AAAMiw';" +
        "f1 = 'AAAMiw'");
  }

  public void testSimpleXid() {
    testNonPseudoSupportingGenerator(
        LINE_JOINER.join(
        "/** @idGenerator {xid} */ id = function() {};",
        "foo.bar = id('foo')"),
        LINE_JOINER.join(
        "/** @idGenerator {xid} */ id = function() {};",
        "foo.bar = 'QB6rXc'"));

    testNonPseudoSupportingGenerator(
        LINE_JOINER.join(
        "/** @idGenerator {xid} */ id = function() {};",
        "f1 = id('foo');",
        "f1 = id('foo')"),
        LINE_JOINER.join(
        "/** @idGenerator {xid} */ id = function() {};",
        "f1 = 'QB6rXc';",
        "f1 = 'QB6rXc'"));
  }

  public void testVar() {
    test(
    	LINE_JOINER.join(
    	"/** @consistentIdGenerator */ var id = function() {};",
        "foo.bar = id('foo_bar')"),
        LINE_JOINER.join(
        "/** @consistentIdGenerator */ var id = function() {};",
        "foo.bar = 'a'"),
        LINE_JOINER.join(
        "/** @consistentIdGenerator */ var id = function() {};",
        "foo.bar = 'foo_bar$0'"));

    testNonPseudoSupportingGenerator(
    	LINE_JOINER.join(
        "/** @stableIdGenerator */ var id = function() {};",
        "foo.bar = id('foo_bar')"),
        LINE_JOINER.join(
        "/** @stableIdGenerator */ var id = function() {};",
        "foo.bar = '125lGg'"));
  }

  public void testLet() {
    testEs6(
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ let id = function() {};",
        "foo.bar = id('foo_bar')"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ let id = function() {};",
        "foo.bar = 'a'"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ let id = function() {};",
        "foo.bar = 'foo_bar$0'"));

    testNonPseudoSupportingGeneratorEs6(
        "/** @stableIdGenerator */ let id = function() {};" +
        "foo.bar = id('foo_bar')",

        "/** @stableIdGenerator */ let id = function() {};" +
        "foo.bar = '125lGg'");
  }

  public void testConst() {
    testEs6(
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ const id = function() {};",
        "foo.bar = id('foo_bar')"),
        LINE_JOINER.join(
        "/** @consistentIdGenerator */ const id = function() {};",
        "foo.bar = 'a'"),
        LINE_JOINER.join(
        "/** @consistentIdGenerator */ const id = function() {};",
        "foo.bar = 'foo_bar$0'"));

    testNonPseudoSupportingGeneratorEs6(
    	LINE_JOINER.join(
        "/** @stableIdGenerator */ const id = function() {};",
        "foo.bar = id('foo_bar')"),
    	LINE_JOINER.join(
        "/** @stableIdGenerator */ const id = function() {};",
        "foo.bar = '125lGg'"));
  }

  public void testInObjLit() {
    test(
    	LINE_JOINER.join(
    	"/** @consistentIdGenerator */ get.id = function() {};",
        "foo.bar = {a: get.id('foo_bar')}"),
    	LINE_JOINER.join(
    	"/** @consistentIdGenerator */ get.id = function() {};",
        "foo.bar = {a: 'a'}"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ get.id = function() {};",
        "foo.bar = {a: 'foo_bar$0'}"));

    testNonPseudoSupportingGenerator(
    	LINE_JOINER.join(
        "/** @stableIdGenerator */ get.id = function() {};",
        "foo.bar = {a: get.id('foo_bar')}"),
        LINE_JOINER.join(
        "/** @stableIdGenerator */ get.id = function() {};",
        "foo.bar = {a: '125lGg'}"));

    testNonPseudoSupportingGenerator(
        LINE_JOINER.join(
        "/** @idGenerator {xid} */ get.id = function() {};",
        "foo.bar = {a: get.id('foo')}"),
        LINE_JOINER.join(
        "/** @idGenerator {xid} */ get.id = function() {};",
        "foo.bar = {a: 'QB6rXc'}"));
  }

  public void testInObjLit_mapped() {
    test(
        LINE_JOINER.join(
        "/** @idGenerator {mapped}*/ id = function() {};",
        "foo.bar = {a: id('foo')}"),
        LINE_JOINER.join(
        "/** @idGenerator {mapped}*/ id = function() {};",
        "foo.bar = {a: ':foo:'}"),
        LINE_JOINER.join(
        "/** @idGenerator {mapped}*/ id = function() {};",
        "foo.bar = {a: ':foo:'}"));
  }

  public void testMapped() {
    test(
        LINE_JOINER.join(
        "/** @idGenerator {mapped}*/ id = function() {};",
        "foo.bar = id('foo');"),
        LINE_JOINER.join(
        "/** @idGenerator {mapped}*/ id = function() {};",
        "foo.bar = ':foo:';"),
        LINE_JOINER.join(
        "/** @idGenerator {mapped}*/ id = function() {};",
        "foo.bar = ':foo:';"));
  }

  public void testMappedMap() {
    testMap(
        LINE_JOINER.join(
        "/** @idGenerator {mapped}*/ id = function() {};",
        "foo.bar = id('foo');",
        "foo.bar = id('foo');"),
        LINE_JOINER.join(
        "/** @idGenerator {mapped}*/id = function() {};",
        "foo.bar = ':foo:';",
        "foo.bar = ':foo:';"),
        LINE_JOINER.join(
        "[id]",
        "",
        ":foo::foo",
        "",
        ""));
  }

  public void testMapped2() {
    test(
        LINE_JOINER.join(
        "/** @idGenerator {mapped}*/ id = function() {};",
        "foo.bar = function() { return id('foo'); };"),
        LINE_JOINER.join(
        "/** @idGenerator {mapped}*/ id = function() {};",
        "foo.bar = function() { return ':foo:'; };"),
        LINE_JOINER.join(
        "/** @idGenerator {mapped}*/ id = function() {};",
        "foo.bar = function() { return ':foo:'; };"));
  }

  public void testTwoGenerators() {
    test(
    	LINE_JOINER.join(
    	"/** @idGenerator */ var id1 = function() {};",
        "/** @idGenerator */ var id2 = function() {};",
        "f1 = id1('1');",
        "f2 = id1('1');",
        "f3 = id2('1');",
        "f4 = id2('1');"),
    	LINE_JOINER.join(
        "/** @idGenerator */ var id1 = function() {};",
        "/** @idGenerator */ var id2 = function() {};",
        "f1 = 'a';",
        "f2 = 'b';",
        "f3 = 'a';",
        "f4 = 'b';"),
    	LINE_JOINER.join(
        "/** @idGenerator */ var id1 = function() {};",
        "/** @idGenerator */ var id2 = function() {};",
        "f1 = '1$0';",
        "f2 = '1$1';",
        "f3 = '1$0';",
        "f4 = '1$1';"));
  }

  public void testMixedGenerators() {
    test(
    	LINE_JOINER.join(
    	"/** @idGenerator */ var id1 = function() {};",
        "/** @consistentIdGenerator */ var id2 = function() {};",
        "/** @stableIdGenerator */ var id3 = function() {};",
        "f1 = id1('1');",
        "f2 = id1('1');",
        "f3 = id2('1');",
        "f4 = id2('1');",
        "f5 = id3('1');",
        "f6 = id3('1');"),
        LINE_JOINER.join(
        "/** @idGenerator */ var id1 = function() {};",
        "/** @consistentIdGenerator */ var id2 = function() {};",
        "/** @stableIdGenerator */ var id3 = function() {};",
        "f1 = 'a';",
        "f2 = 'b';",
        "f3 = 'a';",
        "f4 = 'a';",
        "f5 = 'AAAAMQ';",
        "f6 = 'AAAAMQ';"),
        LINE_JOINER.join(
        "/** @idGenerator */ var id1 = function() {};",
        "/** @consistentIdGenerator */ var id2 = function() {};",
        "/** @stableIdGenerator */ var id3 = function() {};",
        "f1 = '1$0';",
        "f2 = '1$1';",
        "f3 = '1$0';",
        "f4 = '1$0';",
        "f5 = 'AAAAMQ';",
        "f6 = 'AAAAMQ';"));
  }

  public void testNonLiteralParam1() {
    testSame("/** @idGenerator */ var id = function() {}; "
            + "var x = 'foo';"
            + "id(x);",
        ReplaceIdGenerators.INVALID_GENERATOR_PARAMETER);
  }

  public void testNonLiteralParam2() {
    testSame("/** @idGenerator */ var id = function() {}; "
            + "id('foo' + 'bar');",
        ReplaceIdGenerators.INVALID_GENERATOR_PARAMETER);
  }

  public void testLocalCall() {
    testError("/** @idGenerator */ var id = function() {}; "
            + "function Foo() { id('foo'); }",
        ReplaceIdGenerators.NON_GLOBAL_ID_GENERATOR_CALL);
  }

  public void testConditionalCall() {
    testError(
    	LINE_JOINER.join(
        "/** @idGenerator */",
        "var id = function() {}; ",
        "while(0){ id('foo');}"),
    ReplaceIdGenerators.CONDITIONAL_ID_GENERATOR_CALL);

    testError(
    	LINE_JOINER.join(
        "/** @idGenerator */",
        "var id = function() {}; ",
        "for(;;){ id('foo');}"),
    ReplaceIdGenerators.CONDITIONAL_ID_GENERATOR_CALL);

    testError("/** @idGenerator */ var id = function() {}; "
            + "if(x) id('foo');",
        ReplaceIdGenerators.CONDITIONAL_ID_GENERATOR_CALL);

    test(
    	LINE_JOINER.join(
    	"/** @consistentIdGenerator */ var id = function() {};",
        "function fb() {foo.bar = id('foo_bar')}"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ var id = function() {};",
        "function fb() {foo.bar = 'a'}"),
    	LINE_JOINER.join(
        "/** @consistentIdGenerator */ var id = function() {};",
        "function fb() {foo.bar = 'foo_bar$0'}"));

    testNonPseudoSupportingGenerator(
    	LINE_JOINER.join(
        "/** @stableIdGenerator */ var id = function() {};",
        "function fb() {foo.bar = id('foo_bar')}"),
    	LINE_JOINER.join(
        "/** @stableIdGenerator */ var id = function() {};",
        "function fb() {foo.bar = '125lGg'}"));

    testErrorEs6(
        LINE_JOINER.join(
            "/** @idGenerator */",
            "var id = function() {}; ",
            "for(x of [1, 2, 3]){ id('foo');}"),
        ReplaceIdGenerators.CONDITIONAL_ID_GENERATOR_CALL);
  }

  public void testConflictingIdGenerator() {
    testError("/** @idGenerator \n @consistentIdGenerator \n*/"
            + "var id = function() {}; ",
        ReplaceIdGenerators.CONFLICTING_GENERATOR_TYPE);

    testError("/** @stableIdGenerator \n @idGenerator \n*/"
            + "var id = function() {}; ",
        ReplaceIdGenerators.CONFLICTING_GENERATOR_TYPE);

    testError("/** @stableIdGenerator \n "
            + "@consistentIdGenerator \n*/"
            + "var id = function() {}; ",
        ReplaceIdGenerators.CONFLICTING_GENERATOR_TYPE);

    test(
    	LINE_JOINER.join(
    		"/** @consistentIdGenerator */ var id = function() {};",
    		"if (x) {foo.bar = id('foo_bar')}"),
    	LINE_JOINER.join(
    		"/** @consistentIdGenerator */ var id = function() {};",
    		"if (x) {foo.bar = 'a'}"),
    	LINE_JOINER.join(
    		"/** @consistentIdGenerator */ var id = function() {};",
    		"if (x) {foo.bar = 'foo_bar$0'}"));
  }

  public void testUnknownMapping() {
    testSame(LINE_JOINER.join(
        "/** @idGenerator {mapped} */",
        "var unknownId = function() {};",
        "function Foo() { unknownId('foo'); }"),
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

  private void testEs6(String code, String expected, String expectedPseudo) {
    generatePseudoNames = false;
    testEs6(code, expected);
    generatePseudoNames = true;
    testEs6(code, expectedPseudo);
  }

  private void testNonPseudoSupportingGenerator(String code, String expected) {
    generatePseudoNames = false;
    test(code, expected);
    generatePseudoNames = true;
    test(code, expected);
  }

  private void testNonPseudoSupportingGeneratorEs6(String code, String expected) {
    generatePseudoNames = false;
    testEs6(code, expected);
    generatePseudoNames = true;
    testEs6(code, expected);
  }
}
