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
public class JSDocInfoPrinterTest extends TestCase {

  private JSDocInfoBuilder builder;

  @Override
  protected void setUp() {
    builder = new JSDocInfoBuilder(true);
  }

  public void testBasic() {
    builder.recordConstancy();
    JSDocInfo info = builder.build(null);
    assertEquals("/**@const */", JSDocInfoPrinter.print(info));
    builder.recordConstructor();
    info = builder.build(null);
    assertEquals("/**@constructor */", JSDocInfoPrinter.print(info));
    builder.recordSuppressions(ImmutableSet.of("globalThis", "uselessCode"));
    info = builder.build(null);
    assertEquals("/**@suppress {globalThis} @suppress {uselessCode} */",
        JSDocInfoPrinter.print(info));
  }

  public void testTemplate() {
    builder.recordTemplateTypeName("T");
    builder.recordTemplateTypeName("U");
    JSDocInfo info = builder.build(null);
    assertEquals("/**@template T,U\n*/", JSDocInfoPrinter.print(info));
  }

  public void testParam() {
    builder.recordParameter("foo",
        new JSTypeExpression(JsDocInfoParser.parseTypeString("number"), ""));
    builder.recordParameter("bar",
        new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), ""));
    JSDocInfo info = builder.build(null);
    assertEquals("/**@param {number} foo @param {string} bar */",
        JSDocInfoPrinter.print(info));
    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.EQUALS, IR.string("number")), ""));
    info = builder.build(null);
    assertEquals("/**@param {number=} foo */", JSDocInfoPrinter.print(info));
    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.string("number")), ""));
    info = builder.build(null);
    assertEquals("/**@param {...number} foo */", JSDocInfoPrinter.print(info));
  }

  public void testTypes() {
    builder.recordReturnType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("number|string"), ""));
    JSDocInfo info = builder.build(null);
    assertEquals("/**@return {number|string} */", JSDocInfoPrinter.print(info));

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.string("number")), ""));
    info = builder.build(null);
    assertEquals("/**@param {...number} foo */", JSDocInfoPrinter.print(info));
    builder.recordThrowType(new JSTypeExpression(new Node(Token.STAR), ""));
    info = builder.build(null);
    assertEquals("/**@throws {*} */", JSDocInfoPrinter.print(info));
    builder.recordTypedef(new JSTypeExpression(new Node(Token.QMARK), ""));
    info = builder.build(null);
    assertEquals("/**@typedef {?} */", JSDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(new Node(Token.VOID), ""));
    info = builder.build(null);
    assertEquals("/**@type {void} */", JSDocInfoPrinter.print(info));

    // Object types
    builder.recordEnumParameterType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo:number,bar:string}"), ""));
    info = builder.build(null);
    assertEquals(
        "/**@enum {{foo:number,bar:string}} */", JSDocInfoPrinter.print(info));

    // Array types
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Array.<number|string>"), ""));
    info = builder.build(null);
    assertEquals(
        "/**@type {!Array.<number|string>} */", JSDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("Array"), ""));
    builder.recordInlineType();
    info = builder.build(null);
    assertEquals("/** Array */", JSDocInfoPrinter.print(info));

    // Function types
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function()"), ""));
    info = builder.build(null);
    assertEquals("/**@type {function()} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(foo,bar)"), ""));
    info = builder.build(null);
    assertEquals("/**@type {function(foo,bar)} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(foo):number"), ""));
    info = builder.build(null);
    assertEquals("/**@type {function(foo):number} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(new:goog,number)"), ""));
    info = builder.build(null);
    assertEquals("/**@type {function(new:goog,number)} */",
        JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(this:number,...)"), ""));
    info = builder.build(null);
    assertEquals("/**@type {function(this:number,...)} */",
        JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(...number)"), ""));
    info = builder.build(null);
    assertEquals("/**@type {function(...number)} */",
        JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function():void"), ""));
    info = builder.build(null);
    assertEquals("/**@type {function():void} */", JSDocInfoPrinter.print(info));
  }
}
