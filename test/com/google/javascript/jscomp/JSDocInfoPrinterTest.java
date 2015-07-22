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
public final class JSDocInfoPrinterTest extends TestCase {

  private JSDocInfoBuilder builder;

  @Override
  protected void setUp() {
    builder = new JSDocInfoBuilder(true);
  }

  public void testBasic() {
    builder.recordConstancy();
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**@const */", JSDocInfoPrinter.print(info));
    builder.recordConstructor();
    info = builder.buildAndReset();
    assertEquals("/**@constructor */", JSDocInfoPrinter.print(info));
    builder.recordSuppressions(ImmutableSet.of("globalThis", "uselessCode"));
    info = builder.buildAndReset();
    assertEquals("/**@suppress {globalThis,uselessCode} */",
        JSDocInfoPrinter.print(info));
  }

  /**
   * test case for the @record tag
   */
  public void testRecordTag() {
    builder.recordImplicitMatch();
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**@record */", JSDocInfoPrinter.print(info));
  }

  public void testTemplate() {
    builder.recordTemplateTypeName("T");
    builder.recordTemplateTypeName("U");
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**@template T,U\n*/", JSDocInfoPrinter.print(info));
  }

  public void testParam() {
    builder.recordParameter("foo",
        new JSTypeExpression(JsDocInfoParser.parseTypeString("number"), ""));
    builder.recordParameter("bar",
        new JSTypeExpression(JsDocInfoParser.parseTypeString("string"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**@param {number} foo @param {string} bar */",
        JSDocInfoPrinter.print(info));

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.EQUALS, IR.string("number")), ""));
    info = builder.buildAndReset();
    assertEquals("/**@param {number=} foo */", JSDocInfoPrinter.print(info));

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.string("number")), ""));
    info = builder.buildAndReset();
    assertEquals("/**@param {...number} foo */", JSDocInfoPrinter.print(info));

    builder.recordParameter("foo", null);
    info = builder.buildAndReset();
    assertEquals("/**@param foo */", JSDocInfoPrinter.print(info));
  }

  public void testRecordTypes() {
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo: number}"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**@type {{foo:number}} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo}"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {{foo}} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo, bar}"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {{foo,bar}} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo: number, bar}"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {{foo:number,bar}} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo, bar: number}"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {{foo,bar:number}} */", JSDocInfoPrinter.print(info));
  }

  public void testTypes() {
    builder.recordReturnType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("number|string"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**@return {(number|string)} */", JSDocInfoPrinter.print(info));

    builder.recordParameter("foo",
        new JSTypeExpression(new Node(Token.ELLIPSIS, IR.string("number")), ""));
    info = builder.buildAndReset();
    assertEquals("/**@param {...number} foo */", JSDocInfoPrinter.print(info));
    builder.recordThrowType(new JSTypeExpression(new Node(Token.STAR), ""));
    info = builder.buildAndReset();
    assertEquals("/**@throws {*} */", JSDocInfoPrinter.print(info));
    builder.recordTypedef(new JSTypeExpression(new Node(Token.QMARK), ""));
    info = builder.buildAndReset();
    assertEquals("/**@typedef {?} */", JSDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(new Node(Token.VOID), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {void} */", JSDocInfoPrinter.print(info));

    // Object types
    builder.recordEnumParameterType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo:number,bar:string}"), ""));
    info = builder.buildAndReset();
    assertEquals(
        "/**@enum {{foo:number,bar:string}} */", JSDocInfoPrinter.print(info));

    builder.recordEnumParameterType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("{foo:(number|string)}"), ""));
    info = builder.buildAndReset();
    assertEquals(
        "/**@enum {{foo:(number|string)}} */", JSDocInfoPrinter.print(info));

    // Array types
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Array<(number|string)>"), ""));
    info = builder.buildAndReset();
    assertEquals(
        "/**@type {!Array<(number|string)>} */", JSDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("Array"), ""));
    builder.recordInlineType();
    info = builder.buildAndReset();
    assertEquals("/** Array */", JSDocInfoPrinter.print(info));

    // Other template types
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Set<number|string>"), ""));
    info = builder.buildAndReset();
    assertEquals(
        "/**@type {!Set<(number|string)>} */", JSDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!Map<!Foo, !Bar<!Baz|string>>"), ""));
    info = builder.buildAndReset();
    assertEquals(
        "/**@type {!Map<!Foo,!Bar<(!Baz|string)>>} */", JSDocInfoPrinter.print(info));
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("Map"), ""));
    builder.recordInlineType();
    info = builder.buildAndReset();
    assertEquals("/** Map */", JSDocInfoPrinter.print(info));
  }

  public void testInheritance() {
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**@implements {Foo} */", JSDocInfoPrinter.print(info));

    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Foo"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@implements {Foo} */", JSDocInfoPrinter.print(info));

    builder.recordBaseType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Foo"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@extends {Foo} */", JSDocInfoPrinter.print(info));

    builder.recordBaseType(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("!Foo"), ""));
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar"), ""));
    builder.recordImplementedInterface(
        new JSTypeExpression(JsDocInfoParser.parseTypeString("Bar.Baz"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@extends {Foo} @implements {Bar} @implements {Bar.Baz} */",
        JSDocInfoPrinter.print(info));
  }

  public void testFunctions() {
    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function()"), ""));
    JSDocInfo info = builder.buildAndReset();
    assertEquals("/**@type {function()} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(foo,bar)"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {function(foo,bar)} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(foo):number"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {function(foo):number} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(new:goog,number)"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {function(new:goog,number)} */",
        JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(this:number,...)"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {function(this:number,...)} */",
        JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(...number)"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {function(...number)} */",
        JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function():void"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {function():void} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function():number"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {function():number} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(string):number"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {function(string):number} */", JSDocInfoPrinter.print(info));

    builder.recordType(new JSTypeExpression(
        JsDocInfoParser.parseTypeString("function(this:foo):?"), ""));
    info = builder.buildAndReset();
    assertEquals("/**@type {function(this:foo):?} */", JSDocInfoPrinter.print(info));
  }
}
