/*
 * Copyright 2010 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.jsonml;

import com.google.javascript.jscomp.jsonml.SecureCompiler;
import com.google.javascript.jscomp.jsonml.JsonML;
import com.google.javascript.jscomp.jsonml.JsonMLUtil;
import com.google.javascript.jscomp.jsonml.SecureCompiler.Report;

import junit.framework.TestCase;

/**
 * Test class for secure compilation.
 *
 * @author dhans@google.com (Daniel Hans)
 *
 */
public class SecureCompilerTest extends TestCase {

  // simple correct source
  // var x = 1; var t = x;
  private static final String SIMPLE_SOURCE =
    "['Program',{}," +
        "['VarDecl',{}," +
            "['InitPatt',{}," +
                "['IdPatt',{'name':'x'}]," +
                "['LiteralExpr',{'type':'number','value':1}]]]," +
        "['VarDecl',{}," +
            "['InitPatt',{}," +
            "['IdPatt',{'name':'t'}]," +
            "['IdExpr',{'name':'x'}]]]]";

  // syntax error source
  // missing InitPatt element
  private static final String SYNTAX_ERROR =
    "['Program',{}," +
    "['VarDecl',{}," +
        "['InitPatt',{}," +
            "['IdPatt',{'name':'x'}]," +
            "['LiteralExpr',{'type':'number','value':1}]]]," +
    "['VarDecl',{}," +
        "['IdPatt',{'name':'t'}]," +
        "['IdExpr',{'name':'x'}]]]]";

  private void testSuccess(JsonML source) throws Exception {
    SecureCompiler compiler = new SecureCompiler();
    compiler.compile(source);
    Report report = compiler.getReport();
    assertTrue(report.isSuccessful());
    assertEquals(0, report.getErrors().length);
    assertEquals(0, report.getWarnings().length);
  }

  private void testError(JsonML source) throws Exception {
    SecureCompiler compiler = new SecureCompiler();
    compiler.compile(source);
    Report report = compiler.getReport();
    assertFalse(report.isSuccessful());
  }

  private void testString(String jsonml) throws Exception {
    JsonML source = JsonMLUtil.parseString(jsonml);
    testSuccess(source);
  }

  private void testInvalidString(String jsonml) throws Exception {
    JsonML source = JsonMLUtil.parseString(jsonml);
    testError(source);
  }

  public void testCompilerInterface() throws Exception {
    testString(SIMPLE_SOURCE);
    testInvalidString(SYNTAX_ERROR);
  }


}
