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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author moz@google.com (Michael Zhou) */
@RunWith(JUnit4.class)
public final class JSDocInfoPrinterTest {
  private static final Joiner LINE_JOINER = Joiner.on('\n');
  private JSDocInfoBuilder builder;
  private JSDocInfoPrinter jsDocInfoPrinter;

  @Before
  public void setUp() {
    builder = new JSDocInfoBuilder(true);
    jsDocInfoPrinter = new JSDocInfoPrinter(false);
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
        .isEqualTo("/**\n @suppress {globalThis,uselessCode}\n */\n");
  }

  @Test
  public void testDontCrashWhenNoThrowType() {
    // Happens for code like: @throws TypeNameWithoutBraces
    builder.recordThrowType(null);
    builder.recordThrowDescription(null, "TypeNameWithoutBraces");
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** */ ");
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
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @desc foo\n */ ");
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
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n @template T,U\n */\n");
  }

  @Test
  public void testTypeTransformationLanguageTemplate() {
    builder.recordTypeTransformation("T", IR.string("Promise"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo("/**\n @template T := \"Promise\" =:\n */\n");
  }

  @Test
  public void testParam() {
    builder.recordParameter("foo",
        new JSTypeExpression(JsDocInfoParser.parseTypeString("number"), "<testParam>"));
    builder.recordParameter("bar",
        new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), "<testParam>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo("/**\n @param {number} foo\n @param {string} bar\n */\n");

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.EQUALS, IR.string("number")), "<testParam>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n @param {number=} foo\n */\n");

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.string("number")), "<testParam>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n @param {...number} foo\n */\n");

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.empty()), "<testParam>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n @param {...} foo\n */\n");

    builder.recordParameter("foo", null);
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n @param foo\n */\n");
  }

  @Test
  public void testRecordTypes() {
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo: number}"), "<testRecordTypes>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {{foo:number}} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo}"), "<testRecordTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {{foo}} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo, bar}"), "<testRecordTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {{foo,bar}} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo: number, bar}"), "<testRecordTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {{foo:number,bar}} */ ");

    builder.recordType(new JSTypeExpression(
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
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n @return {(number|string)}\n */\n");

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.string("number")), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n @param {...number} foo\n */\n");
    builder.recordThrowType(new JSTypeExpression(new Node(Token.STAR), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @throws {*} */ ");
    builder.recordTypedef(new JSTypeExpression(new Node(Token.QMARK), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @typedef {?} */ ");
    builder.recordType(new JSTypeExpression(new Node(Token.VOID), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {void} */ ");

    // Object types
    builder.recordEnumParameterType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo:number,bar:string}"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @enum {{foo:number,bar:string}} */ ");

    builder.recordEnumParameterType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo:(number|string)}"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @enum {{foo:(number|string)}} */ ");

    // Nullable/non-nullable types.
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("?Object"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {?Object} */ ");
    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Object"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {!Object} */ ");

    // Array types
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Array<(number|string)>"), "<testTypes>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {!Array<(number|string)>} */ ");
    builder.recordType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Array"), "<testTypes>"));
    builder.recordInlineType();
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** Array */ ");

    // Other template types
    builder.recordType(new JSTypeExpression(
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
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n @implements {Foo}\n */\n");

    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Foo"), "<testInheritance>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n @implements {Foo}\n */\n");

    builder.recordBaseType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), "<testInheritance>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/**\n @extends {Foo}\n */\n");

    builder.recordBaseType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Foo"), "<testInheritance>"));
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar"), "<testInheritance>"));
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar.Baz"), "<testInheritance>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo("/**\n @extends {Foo}\n @implements {Bar}\n @implements {Bar.Baz}\n */\n");
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
        .isEqualTo("/**\n @interface\n @extends {Foo}\n @extends {Bar}\n */\n");
  }

  @Test
  public void testFunctions() {
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function()"), "<testFunctions>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function()} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(foo,bar)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(foo,bar)} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(foo):number"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(foo):number} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(new:goog,number)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(new:goog,number)} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(this:number,...)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(this:number,...)} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(...number)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(...number)} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function():void"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function():void} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function():number"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function():number} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(string):number"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(string):number} */ ");

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(this:foo):?"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @type {function(this:foo):?} */ ");
  }

  @Test
  public void testDefines() {
    builder.recordDefineType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("string"), "<testDefines>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @define {string} */ ");
  }

  @Test
  public void testConstDefines() {
    builder.recordDefineType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("string"), "<testDefines>"));
    builder.recordConstancy();
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo("/** @define {string} */ ");
  }

  @Test
  public void testDeprecated() {
    builder.recordDeprecated();
    builder.recordDeprecationReason("See {@link otherClass} for more info.");
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("string"), "<testDeprecated>"));
    JSDocInfo info = builder.buildAndReset();
    assertThat(jsDocInfoPrinter.print(info))
        .isEqualTo(
            LINE_JOINER.join(
                "/**",
                " @type {string}",
                " @deprecated See {@link otherClass} for more info.",
                " */",
                ""));
  }

  @Test
  public void testExterns() {
    testSameFileoverview("/** @externs */ ");
  }

  @Test
  public void testTypeSummary() {
    testSameFileoverview("/** @typeSummary */ ");
  }

  @Test
  public void testExport() {
    testSame("/** @export */ ");
  }

  @Test
  public void testAbstract() {
    testSame("/** @abstract */ ");
  }

  @Test
  public void testImplicitCast() {
    testSame("/** @implicitCast */ ");
  }

  @Test
  public void testNoCollapse() {
    testSame("/** @nocollapse */ ");
  }

  private void testSame(String jsdoc) {
    test(jsdoc, jsdoc);
  }

  private void testSameFileoverview(String jsdoc) {
    test(jsdoc, jsdoc, JsDocInfoParser::parseFileOverviewJsdoc);
  }

  private void test(String input, String output) {
    test(input, output, JsDocInfoParser::parseJsdoc);
  }

  private void test(String input, String output, Function<String, JSDocInfo> parser) {
    assertThat(input).startsWith("/**");
    String contents = input.substring("/**".length());
    JSDocInfo info = parser.apply(contents);
    assertWithMessage("Parse error on parsing JSDoc: " + input).that(info).isNotNull();
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo(output);
  }
}
