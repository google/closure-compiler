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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

/**
 * @author moz@google.com (Michael Zhou)
 */
public final class JSDocInfoPrinterTest extends TestCase {
  private static final Joiner LINE_JOINER = Joiner.on('\n');
  private JSDocInfoBuilder builder;
  private JSDocInfoPrinter jsDocInfoPrinter;

  @Override
  protected void setUp() {
    builder = new JSDocInfoBuilder(true);
    jsDocInfoPrinter = new JSDocInfoPrinter(false);
  }

  public void testBasic() {
    builder.recordConstancy();
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @const */ ", jsDocInfoPrinter.print(info));
    builder.recordConstructor();
    info = builder.buildAndReset();
    assertEquals("/** @constructor */ ", jsDocInfoPrinter.print(info));
    builder.recordSuppressions(ImmutableSet.of("globalThis", "uselessCode"));
    info = builder.buildAndReset();
    assertEquals("/**\n @suppress {globalThis,uselessCode}\n */\n",
        jsDocInfoPrinter.print(info));
  }

  public void testDontCrashWhenNoThrowType() {
    // Happens for code like: @throws TypeNameWithoutBraces
    builder.recordThrowType(null);
    builder.recordThrowDescription(null, "TypeNameWithoutBraces");
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** */ ", jsDocInfoPrinter.print(info));
  }

  public void testFinal() {
    builder.recordFinality();
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @final */ ", jsDocInfoPrinter.print(info));
  }

  public void testDescTag() {
    builder.recordDescription("foo");
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @desc foo\n */ ", jsDocInfoPrinter.print(info));
  }

  public void testRecordTag() {
    builder.recordImplicitMatch();
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @record */ ", jsDocInfoPrinter.print(info));
  }

  public void testTemplate() {
    builder.recordTemplateTypeName("T");
    builder.recordTemplateTypeName("U");
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**\n @template T,U\n */\n", jsDocInfoPrinter.print(info));
  }

  public void testTypeTransformationLanguageTemplate() {
    builder.recordTypeTransformation("T", IR.string("Promise"));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**\n @template T := \"Promise\" =:\n */\n", jsDocInfoPrinter.print(info));
  }

  public void testParam() {
    builder.recordParameter("foo",
        new JSTypeExpression(JsDocInfoParser.parseTypeString("number"), "<testParam>"));
    builder.recordParameter("bar",
        new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), "<testParam>"));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**\n @param {number} foo\n @param {string} bar\n */\n",
        jsDocInfoPrinter.print(info));

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.EQUALS, IR.string("number")), "<testParam>"));
    info = builder.buildAndReset();
    assertEquals("/**\n @param {number=} foo\n */\n", jsDocInfoPrinter.print(info));

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.string("number")), "<testParam>"));
    info = builder.buildAndReset();
    assertEquals("/**\n @param {...number} foo\n */\n", jsDocInfoPrinter.print(info));

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.empty()), "<testParam>"));
    info = builder.buildAndReset();
    assertEquals("/**\n @param {...} foo\n */\n", jsDocInfoPrinter.print(info));

    builder.recordParameter("foo", null);
    info = builder.buildAndReset();
    assertEquals("/**\n @param foo\n */\n", jsDocInfoPrinter.print(info));
  }

  public void testRecordTypes() {
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo: number}"), "<testRecordTypes>"));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @type {{foo:number}} */ ", jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo}"), "<testRecordTypes>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {{foo}} */ ", jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo, bar}"), "<testRecordTypes>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {{foo,bar}} */ ", jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo: number, bar}"), "<testRecordTypes>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {{foo:number,bar}} */ ", jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo, bar: number}"), "<testRecordTypes>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {{foo,bar:number}} */ ", jsDocInfoPrinter.print(info));
  }

  public void testTypes() {
    builder.recordReturnType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("number|string"), "<testTypes>"));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**\n @return {(number|string)}\n */\n", jsDocInfoPrinter.print(info));

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.string("number")), "<testTypes>"));
    info = builder.buildAndReset();
    assertEquals("/**\n @param {...number} foo\n */\n", jsDocInfoPrinter.print(info));
    builder.recordThrowType(new JSTypeExpression(new Node(Token.STAR), "<testTypes>"));
    info = builder.buildAndReset();
    assertEquals("/** @throws {*} */ ", jsDocInfoPrinter.print(info));
    builder.recordTypedef(new JSTypeExpression(new Node(Token.QMARK), "<testTypes>"));
    info = builder.buildAndReset();
    assertEquals("/** @typedef {?} */ ", jsDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(new Node(Token.VOID), "<testTypes>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {void} */ ", jsDocInfoPrinter.print(info));

    // Object types
    builder.recordEnumParameterType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo:number,bar:string}"), "<testTypes>"));
    info = builder.buildAndReset();
    assertEquals(
        "/** @enum {{foo:number,bar:string}} */ ", jsDocInfoPrinter.print(info));

    builder.recordEnumParameterType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo:(number|string)}"), "<testTypes>"));
    info = builder.buildAndReset();
    assertEquals(
        "/** @enum {{foo:(number|string)}} */ ", jsDocInfoPrinter.print(info));

    // Nullable/non-nullable types.
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("?Object"), "<testTypes>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {?Object} */ ", jsDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Object"), "<testTypes>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {!Object} */ ", jsDocInfoPrinter.print(info));

    // Array types
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Array<(number|string)>"), "<testTypes>"));
    info = builder.buildAndReset();
    assertEquals(
        "/** @type {!Array<(number|string)>} */ ", jsDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("Array"), "<testTypes>"));
    builder.recordInlineType();
    info = builder.buildAndReset();
    assertEquals("/** Array */ ", jsDocInfoPrinter.print(info));

    // Other template types
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Set<number|string>"), "<testTypes>"));
    info = builder.buildAndReset();
    assertEquals(
        "/** @type {!Set<(number|string)>} */ ", jsDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Map<!Foo, !Bar<!Baz|string>>"), "<testTypes>"));
    info = builder.buildAndReset();
    assertEquals(
        "/** @type {!Map<!Foo,!Bar<(!Baz|string)>>} */ ", jsDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("Map"), "<testTypes>"));
    builder.recordInlineType();
    info = builder.buildAndReset();
    assertEquals("/** Map */ ", jsDocInfoPrinter.print(info));
  }

  public void testInheritance() {
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), "<testInheritance>"));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**\n @implements {Foo}\n */\n", jsDocInfoPrinter.print(info));

    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Foo"), "<testInheritance>"));
    info = builder.buildAndReset();
    assertEquals("/**\n @implements {Foo}\n */\n", jsDocInfoPrinter.print(info));

    builder.recordBaseType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), "<testInheritance>"));
    info = builder.buildAndReset();
    assertEquals("/**\n @extends {Foo}\n */\n", jsDocInfoPrinter.print(info));

    builder.recordBaseType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Foo"), "<testInheritance>"));
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar"), "<testInheritance>"));
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar.Baz"), "<testInheritance>"));
    info = builder.buildAndReset();
    assertEquals(
        "/**\n @extends {Foo}\n @implements {Bar}\n @implements {Bar.Baz}\n */\n",
        jsDocInfoPrinter.print(info));
  }

  public void testInterfaceInheritance() {
    builder.recordInterface();
    builder.recordExtendedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), "<testInterfaceInheritance>"));
     builder.recordExtendedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar"), "<testInterfaceInheritance>"));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**\n @interface\n @extends {Foo}\n @extends {Bar}\n */\n",
        jsDocInfoPrinter.print(info));
  }

  public void testFunctions() {
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function()"), "<testFunctions>"));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @type {function()} */ ", jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(foo,bar)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(foo,bar)} */ ", jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(foo):number"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(foo):number} */ ", jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(new:goog,number)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(new:goog,number)} */ ",
        jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(this:number,...)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(this:number,...)} */ ",
        jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(...number)"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(...number)} */ ",
        jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function():void"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {function():void} */ ", jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function():number"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {function():number} */ ", jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(string):number"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(string):number} */ ", jsDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(this:foo):?"), "<testFunctions>"));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(this:foo):?} */ ", jsDocInfoPrinter.print(info));
  }

  public void testDefines() {
    builder.recordDefineType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("string"), "<testDefines>"));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @define {string} */ ", jsDocInfoPrinter.print(info));
  }

  public void testDeprecated() {
    builder.recordDeprecated();
    builder.recordDeprecationReason("See {@link otherClass} for more info.");
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("string"), "<testDeprecated>"));
    JSDocInfo info = builder.buildAndReset();
    assertEquals(LINE_JOINER.join(
        "/**",
        " @type {string}",
        " @deprecated See {@link otherClass} for more info.",
        " */",
        ""),
        jsDocInfoPrinter.print(info));
  }

  public void testExport() {
    testSame("/** @export */ ");
  }

  public void testAbstract() {
    testSame("/** @abstract */ ");
  }

  public void testImplicitCast() {
    testSame("/** @implicitCast */ ");
  }

  public void testNoCollapse() {
    testSame("/** @nocollapse */ ");
  }

  private void testSame(String jsdoc) {
    test(jsdoc, jsdoc);
  }

  private void test(String input, String output) {
    assertThat(input).startsWith("/**");
    String contents = input.substring("/**".length());
    JSDocInfo info = JsDocInfoParser.parseJsdoc(contents);
    assertNotNull("Parse error on parsing JSDoc: " + input, info);
    assertThat(jsDocInfoPrinter.print(info)).isEqualTo(output);
  }
}
