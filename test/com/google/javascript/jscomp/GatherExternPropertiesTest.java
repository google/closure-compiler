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

import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc;

/**
 * Test case for {@link GatherExternProperties}.
 */
public final class GatherExternPropertiesTest extends TypeICompilerTestCase {

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

  @Override void checkMinimalExterns(Iterable<SourceFile> externs) {}

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    this.mode = TypeInferenceMode.BOTH;
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
    ignoreWarnings(JSTypeCreatorFromJSDoc.CIRCULAR_TYPEDEF_ENUM);
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

  public void testExternClassNoTypeCheck() {
    this.mode = TypeInferenceMode.NEITHER;
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

  public void testExternClassWithTypeCheck() {
    allowExternsChanges();
    enableTranspile();
    assertExternProperties(
        lines(
            "class Foo {",
            "  bar() {",
            "    return this;",
            "  }",
            "}",
            "var baz = new Foo();",
            "var bar = baz.bar;"),
        "prototype", "bar");
  }

  public void testExternWithMethod() {
    this.mode = TypeInferenceMode.NEITHER;
    assertExternProperties(
        lines(
            "foo = {",
            "  method() {}",
            "}"),
        "method");
  }

  public void testExternAsyncFunction() {
    this.mode = TypeInferenceMode.NEITHER;
    assertExternProperties(
        lines(
            "function *gen() {",
            " var x = 0;",
            " yield x;",
            "}",
            "var foo = gen();",
            "gen.next().value;"),
        "next", "value");
  }

  private static Postcondition expectExterns(final String... properties) {
    return new Postcondition() {
      @Override void verify(Compiler compiler) {
        assertThat(compiler.getExternProperties()).containsExactly((Object[]) properties);
      }
    };
  }

  private void assertExternProperties(String externs, String... properties) {
    testSame(externs(EXTERNS + externs), srcs(""), expectExterns(properties));
  }
}
