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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;

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
    builder.recordParameter("foo", new JSTypeExpression(IR.string("number"), ""));
    builder.recordParameter("bar", new JSTypeExpression(IR.string("string"), ""));
    JSDocInfo info = builder.build(null);
    assertEquals("/**@param {number} foo @param {string} bar */",
        JSDocInfoPrinter.print(info));
  }

  public void testTypes() {
    builder.recordReturnType(new JSTypeExpression(IR.string("number|string"), ""));
    JSDocInfo info = builder.build(null);
    assertEquals("/**@return {number|string} */", JSDocInfoPrinter.print(info));
    builder.recordThisType(new JSTypeExpression(IR.string("...number"), ""));
    info = builder.build(null);
    assertEquals("/**@this {...number} */", JSDocInfoPrinter.print(info));
    builder.recordThrowType(new JSTypeExpression(IR.string("*"), ""));
    info = builder.build(null);
    assertEquals("/**@throws {*} */", JSDocInfoPrinter.print(info));
    builder.recordTypedef(new JSTypeExpression(IR.string("?"), ""));
    info = builder.build(null);
    assertEquals("/**@typedef {?} */", JSDocInfoPrinter.print(info));
    builder.recordEnumParameterType(
        new JSTypeExpression(IR.string("{foo: number, bar: string}"), ""));
    info = builder.build(null);
    assertEquals(
        "/**@enum {{foo: number, bar: string}} */", JSDocInfoPrinter.print(info));
    builder.recordType(
        new JSTypeExpression(IR.string("!Array.<number|string>"), ""));
    info = builder.build(null);
    assertEquals(
        "/**@type {!Array.<number|string>} */", JSDocInfoPrinter.print(info));
  }
}
