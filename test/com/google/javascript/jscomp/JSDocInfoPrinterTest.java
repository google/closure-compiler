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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author moz@google.com (Michael Zhou)
 */
@RunWith(JUnit4.class)
public final class JSDocInfoPrinterTest {
  private JSDocInfo.Builder builder;
  private JSDocInfoPrinter jsDocInfoPrinter;

  @Before
  public void setUp() {
    builder = JSDocInfo.builder().parseDocumentation();
    jsDocInfoPrinter = new JSDocInfoPrinter(/* useOriginalName= */ false, /* printDesc= */ true);
  }

  @Test
  public void testBasic() {
    builder.recordConstancy();
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @const */ ");
    builder.recordConstructor();
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @constructor */ ");
    builder.recordSuppressions(ImmutableSet.of("globalThis", "uselessCode"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo("/**\n * @suppress {globalThis,uselessCode}\n */\n");
  }

  @Test
  public void testSuppressions() {
    builder.recordSuppressions(ImmutableSet.of("globalThis", "uselessCode"), "Common description.");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @suppress {globalThis,uselessCode} Common description.
             */
            """);
  }

  @Test
  public void testSuppressions_multipleLineDescription() {
    builder.recordSuppressions(
        ImmutableSet.of("globalThis", "uselessCode"),
        "Common description.\n More on another line.");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @suppress {globalThis,uselessCode} Common description.
             * More on another line.
             */
            """);
  }

  @Test
  public void testSuppressions_multiple() {
    builder.recordSuppressions(ImmutableSet.of("globalThis", "uselessCode"), "Common description.");
    builder.recordSuppressions(ImmutableSet.of("const")); // has no description

    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @suppress {globalThis,uselessCode} Common description.
             * @suppress {const}
             */
            """);
  }

  @Test
  public void testSuppressions_multiple_printOrder() {
    builder.recordSuppressions(ImmutableSet.of("const")); // has no description
    builder.recordSuppressions(ImmutableSet.of("uselessCode", "globalThis"), "Common description.");

    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            // @suppress printed in order in which it is recorded(parsed)
            // warnings inside a suppress printed in natural order for consistency
            """
            /**
             * @suppress {const}
             * @suppress {globalThis,uselessCode} Common description.
             */
            """);
  }

  @Test
  public void testFinal() {
    builder.recordFinality();
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @final */ ");
  }

  @Test
  public void testDescTag() {
    builder.recordDescription("foo");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @desc foo
             */
            """);
  }

  @Test
  public void testMultilineDescTag() {
    builder.recordDescription("foo\nbar");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @desc foo
             * bar
             */
            """);
  }

  @Test
  public void testRecordTag() {
    builder.recordImplicitMatch();
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @record */ ");
  }

  @Test
  public void testTemplate() {
    builder.recordTemplateTypeName("T");
    builder.recordTemplateTypeName("U");

    JSDocInfo info = builder.buildAndReset();

    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @template T
             * @template U
             */
            """);
  }

  @Test
  public void testTemplateBound_single() {
    builder.recordTemplateTypeName(
        "T", new JSTypeExpression(JsDocInfoParser.parseTypeString("!Array<number>"), ""));

    JSDocInfo info = builder.buildAndReset();

    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @template {!Array<number>} T
             */
            """);
  }

  @Test
  public void testTemplateBound_nullabilityIsPreserved() {
    builder.recordTemplateTypeName(
        "T", new JSTypeExpression(JsDocInfoParser.parseTypeString("?Array<number>"), ""));

    JSDocInfo info = builder.buildAndReset();

    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @template {?Array<number>} T
             */
            """);
  }

  @Test
  public void testTemplateBound_explicitlyOnUnknown_isOmitted() {
    builder.recordTemplateTypeName(
        "T", new JSTypeExpression(JsDocInfoParser.parseTypeString("?"), ""));

    JSDocInfo info = builder.buildAndReset();

    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @template T
             */
            """);
  }

  @Test
  public void testTemplatesBound_multiple() {
    builder.recordTemplateTypeName(
        "T", new JSTypeExpression(JsDocInfoParser.parseTypeString("!Array<number>"), ""));
    builder.recordTemplateTypeName(
        "U", new JSTypeExpression(JsDocInfoParser.parseTypeString("boolean"), ""));

    JSDocInfo info = builder.buildAndReset();

    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @template {!Array<number>} T
             * @template {boolean} U
             */
            """);
  }

  @Test
  public void testTemplatesBound_mixedWithUnbounded() {
    builder.recordTemplateTypeName(
        "T", new JSTypeExpression(JsDocInfoParser.parseTypeString("!Object"), ""));
    builder.recordTemplateTypeName("S");
    builder.recordTemplateTypeName("R");
    builder.recordTemplateTypeName(
        "U", new JSTypeExpression(JsDocInfoParser.parseTypeString("*"), ""));
    builder.recordTemplateTypeName("Q");

    JSDocInfo info = builder.buildAndReset();

    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @template {!Object} T
             * @template S
             * @template R
             * @template {*} U
             * @template Q
             */
            """);
  }

  @Test
  public void testTypeTransformationLanguageTemplate() {
    builder.recordTypeTransformation("T", IR.string("Promise"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo("/**\n * @template T := \"Promise\" =:\n */\n");
  }

  @Test
  public void testParam() {
    builder.recordParameter(
        "foo", new JSTypeExpression(JsDocInfoParser.parseTypeString("number"), "<testParam>"));
    builder.recordParameter(
        "bar", new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), "<testParam>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo("/**\n * @param {number} foo\n * @param {string} bar\n */\n");

    builder.recordParameter(
        "foo", new JSTypeExpression(new Node(Token.EQUALS, IR.string("number")), "<testParam>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n * @param {number=} foo\n */\n");

    builder.recordParameter(
        "foo", new JSTypeExpression(new Node(Token.ITER_REST, IR.string("number")), "<testParam>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n * @param {...number} foo\n */\n");

    builder.recordParameter(
        "foo", new JSTypeExpression(new Node(Token.ITER_REST, IR.empty()), "<testParam>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n * @param {...} foo\n */\n");

    builder.recordParameter("foo", null);
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n * @param foo\n */\n");
  }

  @Test
  public void testRecordTypes() {
    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("{foo: number}"), "<testRecordTypes>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {{foo:number}} */ ");

    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("{foo}"), "<testRecordTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {{foo}} */ ");

    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("{foo, bar}"), "<testRecordTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {{foo,bar}} */ ");

    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("{foo: number, bar}"), "<testRecordTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {{foo:number,bar}} */ ");

    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("{foo, bar: number}"), "<testRecordTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {{foo,bar:number}} */ ");
  }

  @Test
  public void testTypeof() {
    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("typeof foo"), "<testTypeof>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {typeof foo} */ ");
  }

  @Test
  public void testTypes() {
    builder.recordReturnType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("number|string"), "<testTypes>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n * @return {(number|string)}\n */\n");

    builder.recordParameter(
        "foo", new JSTypeExpression(new Node(Token.ITER_REST, IR.string("number")), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n * @param {...number} foo\n */\n");
    builder.recordTypedef(new JSTypeExpression(new Node(Token.QMARK), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @typedef {?} */ ");
    builder.recordType(new JSTypeExpression(new Node(Token.VOID), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {void} */ ");

    // Object types
    builder.recordEnumParameterType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("{foo:number,bar:string}"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @enum {{foo:number,bar:string}} */ ");

    builder.recordEnumParameterType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("{foo:(number|string)}"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @enum {{foo:(number|string)}} */ ");

    // Nullable/non-nullable types.
    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("?Object"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {?Object} */ ");
    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Object"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {!Object} */ ");

    // Array types
    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("!Array<(number|string)>"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {!Array<(number|string)>} */ ");
    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Array"), "<testTypes>"));
    builder.recordInlineType();
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** Array */ ");

    // Other template types
    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("!Set<number|string>"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {!Set<(number|string)>} */ ");
    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("!Map<!Foo, !Bar<!Baz|string>>"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo("/** @type {!Map<!Foo,!Bar<(!Baz|string)>>} */ ");
    builder.recordType(new JSTypeExpression(JsDocInfoParser.parseTypeString("Map"), "<testTypes>"));
    builder.recordInlineType();
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** Map */ ");
  }

  @Test
  public void testInheritance() {
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), "<testInheritance>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n * @implements {Foo}\n */\n");

    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Foo"), "<testInheritance>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n * @implements {Foo}\n */\n");

    builder.recordBaseType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), "<testInheritance>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n * @extends {Foo}\n */\n");

    builder.recordBaseType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Foo"), "<testInheritance>"));
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar"), "<testInheritance>"));
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar.Baz"), "<testInheritance>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo("/**\n * @extends {Foo}\n * @implements {Bar}\n * @implements {Bar.Baz}\n */\n");
  }

  @Test
  public void testInterfaceInheritance() {
    builder.recordInterface();
    builder.recordExtendedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), "<testInterfaceInheritance>"));
    builder.recordExtendedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar"), "<testInterfaceInheritance>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo("/**\n * @interface\n * @extends {Foo}\n * @extends {Bar}\n */\n");
  }

  @Test
  public void testFunctions() {
    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("function()"), "<testFunctions>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function()} */ ");

    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("function(foo,bar)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(foo,bar)} */ ");

    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("function(foo):number"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(foo):number} */ ");

    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("function(new:goog,number)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(new:goog,number)} */ ");

    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("function(this:number,...)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(this:number,...)} */ ");

    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("function(...number)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(...number)} */ ");

    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("function():void"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function():void} */ ");

    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("function():number"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function():number} */ ");

    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("function(string):number"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(string):number} */ ");

    builder.recordType(
        new JSTypeExpression(
            JsDocInfoParser.parseTypeString("function(this:foo):?"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(this:foo):?} */ ");
  }

  @Test
  public void testDefines() {
    builder.recordDefineType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), "<testDefines>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @define {string} */ ");
  }

  @Test
  public void testConstDefines() {
    builder.recordDefineType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), "<testDefines>"));
    builder.recordConstancy();
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @define {string} */ ");
  }

  @Test
  public void testBlockDescription() {
    builder.recordBlockDescription("Description of the thing");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n * Description of the thing\n */\n");
  }

  @Test
  public void testParamDescriptions() {
    builder.recordParameter(
        "foo", new JSTypeExpression(JsDocInfoParser.parseTypeString("number"), "<testParam>"));
    builder.recordParameter(
        "bar", new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), "<testParam>"));
    // The parser will retain leading whitespace for descriptions.
    builder.recordParameterDescription("foo", " A number for foo");
    builder.recordParameterDescription("bar", " A multline\n     description for bar");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @param {number} foo A number for foo
             * @param {string} bar A multline
             *     description for bar
             */
            """);
  }

  @Test
  public void testReturnDescription() {
    builder.recordReturnType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("boolean"), "<testReturn>"));
    builder.recordReturnDescription("The return value");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @return {boolean} The return value
             */
            """);
  }

  @Test
  public void testAllDescriptions() {
    builder.recordBlockDescription("Description of the thing");
    builder.recordParameter(
        "foo", new JSTypeExpression(JsDocInfoParser.parseTypeString("number"), "<testParam>"));
    builder.recordParameter(
        "bar", new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), "<testParam>"));
    builder.recordParameterDescription("foo", " A number for foo");
    builder.recordParameterDescription("bar", " A multline\n     description for bar");
    builder.recordReturnType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("boolean"), "<testReturn>"));
    builder.recordReturnDescription("The return value");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * Description of the thing
             *
             * @param {number} foo A number for foo
             * @param {string} bar A multline
             *     description for bar
             * @return {boolean} The return value
             */
            """);
  }

  @Test
  public void testDeprecated() {
    builder.recordDeprecated();
    builder.recordDeprecationReason("See {@link otherClass} for more info.");
    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), "<testDeprecated>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @type {string}
             * @deprecated See {@link otherClass} for more info.
             */
            """);
  }

  @Test
  public void testDeprecated_noReason() {
    builder.recordDeprecated();
    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), "<testDeprecated>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @type {string}
             * @deprecated
             */
            """);
  }

  // Tests that a {@code @see} is sufficient to populate a JSDocInfo.
  @Test
  public void testJSDocIsPopulated_withSeeReferenceAlone() {
    builder.recordReference("SomeClassName for more details");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @see SomeClassName for more details
             */
            """);
  }

  // Tests that an {@code @Author} is sufficient to populate a JSDocInfo.
  @Test
  public void testJSDocIsPopulated_withAuthorAlone() {
    builder.recordAuthor("John Doe.");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @author John Doe.
             */
            """);
  }

  @Test
  public void testSeeReferencePrintedWithOtherAnnotations() {
    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), "<testSee>"));
    builder.recordReference("SomeClassName for more details");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @see SomeClassName for more details
             * @type {string}
             */
            """);
  }

  @Test
  public void testAuthorReferencePrintedWithOtherAnnotations() {
    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), "<testAuthor>"));
    builder.recordAuthor("John Doe.");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            """
            /**
             * @author John Doe.
             * @type {string}
             */
            """);
  }

  @Test
  public void testExterns() {
    builder.recordExterns();
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @externs */ ");
  }

  @Test
  public void testTypeSummary() {
    builder.recordTypeSummary();
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @typeSummary */ ");
  }

  @Test
  public void testExport() {
    builder.recordExport();
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @export */ ");
  }

  @Test
  public void testAbstract() {
    builder.recordAbstract();
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @abstract */ ");
  }

  @Test
  public void testImplicitCast() {
    builder.recordImplicitCast();
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @implicitCast */ ");
  }

  @Test
  public void testClosurePrimitive() {
    builder.recordClosurePrimitiveId("testPrimitive");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @closurePrimitive {testPrimitive} */ ");
  }

  @Test
  public void testNoCollapse() {
    builder.recordNoCollapse();
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @nocollapse */ ");
  }

  @Test
  public void testNgInject() {
    builder.recordNgInject(true);
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @ngInject */ ");
  }

  @Test
  public void testTsType() {
    builder.recordTsType("():string");
    JSDocInfo info = builder.buildAndReset();

    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @tsType ():string */ ");
  }

  @Test
  public void testTsType_multipleTsTypes() {
    builder.recordTsType("():string");
    builder.recordTsType("(x:string):number");
    JSDocInfo info = builder.buildAndReset();

    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo("/** @tsType ():string @tsType (x:string):number */ ");
  }
}
