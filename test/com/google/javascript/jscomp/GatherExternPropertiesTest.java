/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.collect.Sets;

/**
 * Test case for {@link GatherExternProperties}.
 */
public class GatherExternPropertiesTest extends CompilerTestCase {
  public GatherExternPropertiesTest() {
    super();
    enableTypeCheck(CheckLevel.WARNING);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new GatherExternProperties(compiler);
  }

  public void testGatherExternProperties() {
    // Properties.
    assertExternProperties(
        "foo.bar;",
        "bar");

    // Object literals.
    assertExternProperties(
        "foo = {bar: null, 'baz': {foobar: null}};",
        "bar", "baz", "foobar");

    // Object literal with numeric propertic.
    assertExternProperties(
        "foo = {0: null};",
         "0");

    // Top-level variables do not count.
    assertExternProperties(
        "var foo;");

    // String-key access does not count.
    assertExternProperties(
        "foo['bar'] = {};");
  }

  public void testGatherExternPropertiesIncludingRecordTypes() {
    // Properties.
    assertExternProperties(
        "foo.bar;",
        "bar");

    // Object literals.
    assertExternProperties(
        "foo = {bar: null, 'baz': {foobar: null}};",
        "bar", "baz", "foobar");

    // Object literal with numeric propertic.
    assertExternProperties(
        "foo = {0: null};",
         "0");

    // Top-level variables do not count.
    assertExternProperties(
        "var foo;");

    // String-key access does not count.
    assertExternProperties(
        "foo['bar'] = {};");

    // Record types on properties.
    assertExternProperties(
        "/** @type {{bar: string, baz: string}} */ var foo;",
        "bar", "baz");

    // Record types in typedef.
    assertExternProperties(
        "/** @typedef {{bar: string, baz: string}} */ var FooType;",
        "bar", "baz");

    // Record types in type unions.
    assertExternProperties(
        "/** @type {string|{bar: string}|{baz: string}} */ var foo;",
        "bar", "baz");

    // Record types in function parameters and return types.
    assertExternProperties(
        "/** @type {function(string, {bar: string}): {baz: string}} */\n" +
        "var foo;",
        "bar", "baz");

    // Record types as template arguments.
    assertExternProperties(
        "/** @type {Array.<{bar: string, baz: string}>} */ var foo;",
        "bar", "baz");

    // Record types in implemented interfaces.
    assertExternProperties(
        "/**\n" +
        " * @interface\n" +
        " * @template T\n" +
        " */\n" +
        "var Foo;\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @implements {Foo.<{bar: string, baz: string}>}\n" +
        " */\n" +
        "var Bar;",
        "bar", "baz");

    // Record types in extended class.
    assertExternProperties(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "var Foo = function() {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo.<{bar: string, baz: string}>}\n" +
        " */\n" +
        "var Bar = function() {};",
        "bar", "baz");

    // Record types in enum.
    // Note that "baz" exists only in the type of the enum,
    // but it is still picked up.
    assertExternProperties(
        "/** @enum {{bar: string, baz: (string|undefined)}} */\n" +
        "var FooEnum = {VALUE: {bar: ''}};",
        "VALUE", "bar", "baz");

    // Nested record types.
    assertExternProperties(
        "/** @type {{bar: string, baz: {foobar: string}}} */ var foo;",
        "bar", "baz", "foobar");

    // Recursive types.
    assertExternProperties(
        "/** @typedef {{a: D2}} */\n" +
        "var D1;\n" +
        "\n" +
        "/** @typedef {{b: D1}} */\n" +
        "var D2;",
        "a", "b");
    assertExternProperties(
        "/** @typedef {{a: function(D2)}} */\n" +
        "var D1;\n" +
        "\n" +
        "/** @typedef {{b: D1}} */\n" +
        "var D2;",
        "a", "b");

    // Record types defined in normal code and referenced in externs should
    // not bleed-through.
    testSame(
        // Externs.
        "/** @type {NonExternType} */ var foo;",
        // Normal code.
        "/** @typedef {{bar: string, baz: string}} */ var NonExternType;",
        null);
    // Check that no properties were found.
    assertTrue(getLastCompiler().getExternProperties().isEmpty());
  }

  private void assertExternProperties(String externs, String... properties) {
    testSame(externs, "", null);
    assertEquals(Sets.newHashSet(properties),
        getLastCompiler().getExternProperties());
  }
}
