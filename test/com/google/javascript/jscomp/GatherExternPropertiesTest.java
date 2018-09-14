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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link GatherExternProperties}. */
@RunWith(JUnit4.class)
public final class GatherExternPropertiesTest extends CompilerTestCase {

  private static final String EXTERNS = lines(
      "/**",
      " * @constructor",
      " * @param {*=} opt_value",
      " * @return {!Object}",
      " */",
      "function Object(opt_value) {}",
      "/**",
      " * @constructor",
      " * @param {...*} var_args",
      " */",
      "function Function(var_args) {}",
      "/**",
      " * @constructor",
      " * @param {*=} arg",
      " * @return {string}",
      " */",
      "function String(arg) {}",
      "/**",
      " * @template T",
      " * @constructor ",
      " * @param {...*} var_args",
      " * @return {!Array<?>}",
      " */",
      "function Array(var_args) {}");

  public GatherExternPropertiesTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new GatherExternProperties(compiler);
  }

  @Test
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

  @Test
  public void testGatherExternTypedefProperties() {
    String typedefExtern =
        lines(
            "/**",
            " * @typedef {{",
            " *    typedefPropA: { 'subTypedefProp': string },",  // quotes should be stripped
            " *  }}",
            " */",
            "var TypedefExtern;",
            "/**",
            " * @param {!{ paramProp1, paramProp2: number }} p",
            " */",
            "function externFunction(p) {}");
    assertExternProperties(
        typedefExtern, "typedefPropA", "subTypedefProp", "paramProp1", "paramProp2");
  }

  @Test
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
    assertExternProperties(lines(
        "/** @type {function(string, {bar: string}): {baz: string}} */",
        "var foo;"),
        "bar", "baz");

    // Record types as template arguments.
    assertExternProperties(
        "/** @type {Array<{bar: string, baz: string}>} */ var foo;",
        "bar", "baz");

    // Record types in implemented interfaces.
    assertExternProperties(lines(
        "/**",
        " * @interface",
        " * @template T",
        " */",
        "var Foo = function() {};",
        "/**",
        " * @constructor",
        " * @implements {Foo<{bar: string, baz: string}>}",
        " */",
        "var Bar;"),
        "bar", "baz");

    // Record types in extended class.
    assertExternProperties(lines(
        "/**",
        " * @constructor",
        " * @template T",
        " */",
        "var Foo = function() {};",
        "/**",
        " * @constructor",
        " * @extends {Foo<{bar: string, baz: string}>}",
        " */",
        "var Bar = function() {};"),
        "bar", "baz");

    // Record types in enum.
    // Note that "baz" exists only in the type of the enum,
    // but it is still picked up.
    assertExternProperties(lines(
        "/** @enum {{bar: string, baz: (string|undefined)}} */",
        "var FooEnum = {VALUE: {bar: ''}};"),
        "VALUE", "bar", "baz");

    // Nested record types.
    assertExternProperties(
        "/** @type {{bar: string, baz: {foobar: string}}} */ var foo;",
        "bar", "baz", "foobar");

    // Recursive @record types.
    assertExternProperties(lines(
        "/** @record */",
        "function D1() { /** @type {D2} */ this.a; }",
        "",
        "/** @record */",
        "function D2() { /** @type {D1} */ this.b; }"),
        "a", "b");
    assertExternProperties(lines(
        "/** @record */",
        "function D1() { /** @type {function(D2)} */ this.a; }",
        "",
        "/** @record */",
        "function D2() { /** @type {D1} */ this.b; }"),
        "a", "b");

    // Recursive types
    assertExternProperties(lines(
        "/** @typedef {{a: D2}} */",
        "var D1;",
        "",
        "/** @typedef {{b: D1}} */",
        "var D2;"),
        "a", "b");
    assertExternProperties(lines(
        "/** @typedef {{a: function(D2)}} */",
        "var D1;",
        "",
        "/** @typedef {{b: D1}} */",
        "var D2;"),
        "a", "b");

    // Record types defined in normal code and referenced in externs should
    // not bleed-through.
    testSame(
        externs(EXTERNS + "/** @type {NonExternType} */ var foo;"),
        srcs("/** @typedef {{bar: string, baz: string}} */ var NonExternType;"),
        // Check that no properties were found.
        expectExterns());
  }

  @Test
  public void testExternClass() {
    assertExternProperties(
        lines(
            "class Foo {",
            "  bar() {",
            "    return this;",
            "  }",
            "}",
            "var baz = new Foo();",
            "var bar = baz.bar;"),
        "bar");
  }

  @Test
  public void testExternWithMethod() {
    assertExternProperties(
        lines(
            "foo = {",
            "  method() {}",
            "}"),
        "method");
  }

  private static Postcondition expectExterns(final String... properties) {
    return compiler ->
        assertThat(compiler.getExternProperties()).containsExactly((Object[]) properties);
  }

  private void assertExternProperties(String externs, String... properties) {
    testSame(externs(EXTERNS + externs), srcs(""), expectExterns(properties));
  }
}
