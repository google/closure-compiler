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

  @Override
  protected void setUp() {
    builder = new JSDocInfoBuilder(true);
  }

  public void testBasic() {
    builder.recordConstancy();
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @const */ ", JSDocInfoPrinter.print(info));
    builder.recordConstructor();
    info = builder.buildAndReset();
    assertEquals("/** @constructor */ ", JSDocInfoPrinter.print(info));
    builder.recordSuppressions(ImmutableSet.of("globalThis", "uselessCode"));
    info = builder.buildAndReset();
    assertEquals("/**\n @suppress {globalThis,uselessCode}\n */\n",
        JSDocInfoPrinter.print(info));
  }

  public void testDontCrashWhenNoThrowType() {
    // Happens for code like: @throws TypeNameWithoutBraces
    builder.recordThrowType(null);
    builder.recordThrowDescription(null, "TypeNameWithoutBraces");
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** */ ", JSDocInfoPrinter.print(info));
  }

  /**
   * test case for the @record tag
   */
  public void testRecordTag() {
    builder.recordImplicitMatch();
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @record */ ", JSDocInfoPrinter.print(info));
  }

  public void testTemplate() {
    builder.recordTemplateTypeName("T");
    builder.recordTemplateTypeName("U");
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**\n @template T,U\n */\n", JSDocInfoPrinter.print(info));
  }

  public void testParam() {
    builder.recordParameter("foo",
        new JSTypeExpression(JsDocInfoParser.parseTypeString("number"), ""));
    builder.recordParameter("bar",
        new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**\n @param {number} foo\n @param {string} bar\n */\n",
        JSDocInfoPrinter.print(info));

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.EQUALS, IR.string("number")), ""));
    info = builder.buildAndReset();
    assertEquals("/**\n @param {number=} foo\n */\n", JSDocInfoPrinter.print(info));

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.string("number")), ""));
    info = builder.buildAndReset();
    assertEquals("/**\n @param {...number} foo\n */\n", JSDocInfoPrinter.print(info));

    builder.recordParameter("foo", null);
    info = builder.buildAndReset();
    assertEquals("/**\n @param foo\n */\n", JSDocInfoPrinter.print(info));
  }

  public void testRecordTypes() {
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo: number}"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @type {{foo:number}} */ ", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo}"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {{foo}} */ ", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo, bar}"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {{foo,bar}} */ ", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo: number, bar}"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {{foo:number,bar}} */ ", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo, bar: number}"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {{foo,bar:number}} */ ", JSDocInfoPrinter.print(info));
  }

  public void testTypes() {
    builder.recordReturnType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("number|string"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**\n @return {(number|string)}\n */\n", JSDocInfoPrinter.print(info));

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.string("number")), ""));
    info = builder.buildAndReset();
    assertEquals("/**\n @param {...number} foo\n */\n", JSDocInfoPrinter.print(info));
    builder.recordThrowType(new JSTypeExpression(new Node(Token.STAR), ""));
    info = builder.buildAndReset();
    assertEquals("/** @throws {*} */ ", JSDocInfoPrinter.print(info));
    builder.recordTypedef(new JSTypeExpression(new Node(Token.QMARK), ""));
    info = builder.buildAndReset();
    assertEquals("/** @typedef {?} */ ", JSDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(new Node(Token.VOID), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {void} */ ", JSDocInfoPrinter.print(info));

    // Object types
    builder.recordEnumParameterType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo:number,bar:string}"), ""));
    info = builder.buildAndReset();
    assertEquals(
        "/** @enum {{foo:number,bar:string}} */ ", JSDocInfoPrinter.print(info));

    builder.recordEnumParameterType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo:(number|string)}"), ""));
    info = builder.buildAndReset();
    assertEquals(
        "/** @enum {{foo:(number|string)}} */ ", JSDocInfoPrinter.print(info));

    // Nullable/non-nullable types.
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("?Object"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {?Object} */ ", JSDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Object"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {!Object} */ ", JSDocInfoPrinter.print(info));

    // Array types
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Array<(number|string)>"), ""));
    info = builder.buildAndReset();
    assertEquals(
        "/** @type {!Array<(number|string)>} */ ", JSDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("Array"), ""));
    builder.recordInlineType();
    info = builder.buildAndReset();
    assertEquals("/** Array */ ", JSDocInfoPrinter.print(info));

    // Other template types
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Set<number|string>"), ""));
    info = builder.buildAndReset();
    assertEquals(
        "/** @type {!Set<(number|string)>} */ ", JSDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Map<!Foo, !Bar<!Baz|string>>"), ""));
    info = builder.buildAndReset();
    assertEquals(
        "/** @type {!Map<!Foo,!Bar<(!Baz|string)>>} */ ", JSDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("Map"), ""));
    builder.recordInlineType();
    info = builder.buildAndReset();
    assertEquals("/** Map */ ", JSDocInfoPrinter.print(info));
  }

  public void testInheritance() {
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**\n @implements {Foo}\n */\n", JSDocInfoPrinter.print(info));

    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Foo"), ""));
    info = builder.buildAndReset();
    assertEquals("/**\n @implements {Foo}\n */\n", JSDocInfoPrinter.print(info));

    builder.recordBaseType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), ""));
    info = builder.buildAndReset();
    assertEquals("/**\n @extends {Foo}\n */\n", JSDocInfoPrinter.print(info));

    builder.recordBaseType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Foo"), ""));
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar"), ""));
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar.Baz"), ""));
    info = builder.buildAndReset();
    assertEquals(
        "/**\n @extends {Foo}\n @implements {Bar}\n @implements {Bar.Baz}\n */\n",
        JSDocInfoPrinter.print(info));
  }

  public void testInterfaceInheritance() {
    builder.recordInterface();
    builder.recordExtendedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), ""));
     builder.recordExtendedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**\n @interface\n @extends {Foo}\n @extends {Bar}\n */\n",
        JSDocInfoPrinter.print(info));
  }

  public void testFunctions() {
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function()"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @type {function()} */ ", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(foo,bar)"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(foo,bar)} */ ", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(foo):number"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(foo):number} */ ", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(new:goog,number)"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(new:goog,number)} */ ",
        JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(this:number,...)"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(this:number,...)} */ ",
        JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(...number)"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(...number)} */ ",
        JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function():void"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {function():void} */ ", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function():number"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {function():number} */ ", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(string):number"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(string):number} */ ", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(this:foo):?"), ""));
    info = builder.buildAndReset();
    assertEquals("/** @type {function(this:foo):?} */ ", JSDocInfoPrinter.print(info));
  }

  public void testDefines() {
    builder.recordDefineType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("string"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/** @define {string} */ ", JSDocInfoPrinter.print(info));
  }

  public void testDeprecated() {
    builder.recordDeprecated();
    builder.recordDeprecationReason("See {@link otherClass} for more info.");
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("string"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals(LINE_JOINER.join(
        "/**",
        " @type {string}",
        " @deprecated See {@link otherClass} for more info.",
        " */",
        ""),
        JSDocInfoPrinter.print(info));
  }

  public void testExport() {
    testSame("/** @export */ ");
  }

  private void testSame(String jsdoc) {
    test(jsdoc, jsdoc);
  }

  private void test(String input, String output) {
    assertThat(input).startsWith("/**");
    String contents = input.substring("/**".length());
    JSDocInfo info = JsDocInfoParser.parseJsdoc(contents);
    assertNotNull("Parse error on parsing JSDoc: " + input, info);
    assertThat(JSDocInfoPrinter.print(info)).isEqualTo(output);
  }
}
