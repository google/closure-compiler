/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.SourceMap.DetailLevel;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.Map;

/**
 * Tests for {@link SourceMap}.
 *
 */
public class SourceMapTest extends TestCase {
  private static final JSSourceFile[] EXTERNS = {
      JSSourceFile.fromCode("externs", "")
  };

  private DetailLevel detailLevel = SourceMap.DetailLevel.ALL;

  @Override
  public void setUp() {
    detailLevel = SourceMap.DetailLevel.ALL;
  }

  public void testBasicMapping() throws Exception {
    compileAndCheck("function __BASIC__() { }");
  }

  public void testLiteralMappings() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) { " +
                    "var __VAR__ = '__STR__'; }");
  }

  public void testMultilineMapping() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) {\n" +
                    "var __VAR__ = '__STR__';\n" +
                    "var __ANO__ = \"__STR2__\";\n" +
                    "}");
  }

  public void testMultiFunctionMapping() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) {\n" +
                    "var __VAR__ = '__STR__';\n" +
                    "var __ANO__ = \"__STR2__\";\n" +
                    "}\n\n" +

                    "function __BASIC2__(__PARAM3__, __PARAM4__) {\n" +
                    "var __VAR2__ = '__STR2__';\n" +
                    "var __ANO2__ = \"__STR3__\";\n" +
                    "}\n\n");
  }

  public void testGoldenOutput0() throws Exception {
    // Empty source map test
    checkSourceMap("",

                   "/** Begin line maps. **/{ \"file\" : \"testcode\"," +
                   " \"count\": 1 }\n" +

                   "[]\n" +

                   "/** Begin file information. **/\n" +
                   "[]\n" +

                   "/** Begin mapping definitions. **/\n");
  }

  public void testFunctionNameOutput1() throws Exception {
    checkSourceMap("function f() {}",
                   "/** Begin line maps. **/{ \"file\" : \"testcode\", " +
                   "\"count\": 1 }\n" +

                   "[0,0,0,0,0,0,0,0,1,1,2,2,3,3]\n" +

                   "/** Begin file information. **/\n" +
                   "[]\n" +

                   "/** Begin mapping definitions. **/\n" +
                   "[\"testcode\",1,9,\"f\"]\n" +
                   "[\"testcode\",1,9,\"f\"]\n" +
                   "[\"testcode\",1,10]\n" +
                   "[\"testcode\",1,13]\n");
  }

  public void testFunctionNameOutput2() throws Exception {
    checkSourceMap("a.b.c = function () {};",

                   "/** Begin line maps. **/{ \"file\" : \"testcode\", " +
                   "\"count\": 1 }\n" +

                   "[3,2,2,1,1,0,4,4,4,4,4,4,4,4,5,5,6,6]\n" +

                   "/** Begin file information. **/\n" +
                   "[]\n" +

                   "/** Begin mapping definitions. **/\n" +
                   "[\"testcode\",1,0]\n" +
                   "[\"testcode\",1,0,\"c\"]\n" +
                   "[\"testcode\",1,0,\"b\"]\n" +
                   "[\"testcode\",1,0,\"a\"]\n" +
                   "[\"testcode\",1,17,\"a.b.c\"]\n" +
                   "[\"testcode\",1,17]\n" +
                   "[\"testcode\",1,20]\n");
  }

  public void testFunctionNameOutput3() throws Exception {
    checkSourceMap("var q = function () {};",

                   "/** Begin line maps. **/{ \"file\" : \"testcode\", " +
                   "\"count\": 1 }\n" +

                   "[0,0,0,0,1,1,2,2,2,2,2,2,2,2,3,3,4,4]\n" +

                   "/** Begin file information. **/\n" +
                   "[]\n" +

                   "/** Begin mapping definitions. **/\n" +
                   "[\"testcode\",1,0]\n" +
                   "[\"testcode\",1,4,\"q\"]\n" +
                   "[\"testcode\",1,17,\"q\"]\n" +
                   "[\"testcode\",1,17]\n" +
                   "[\"testcode\",1,20]\n");
  }

  public void testFunctionNameOutput4() throws Exception {
    checkSourceMap("({ 'q' : function () {} })",

                   "/** Begin line maps. **/{ \"file\" : \"testcode\", " +
                   "\"count\": 1 }\n" +

                   "[0,0,0,0,1,1,1,1,1,1,1,1,2,2,3,3,0,0]\n" +

                   "/** Begin file information. **/\n" +
                   "[]\n" +

                   "/** Begin mapping definitions. **/\n" +
                   "[\"testcode\",1,1]\n" +
                   "[\"testcode\",1,18,\"q\"]\n" +
                   "[\"testcode\",1,18]\n" +
                   "[\"testcode\",1,21]\n");
  }

  public void testGoldenOutput1() throws Exception {
    detailLevel = SourceMap.DetailLevel.ALL;

    checkSourceMap("function f(foo, bar) { foo = foo + bar + 2; return foo; }",

                   "/** Begin line maps. **/{ \"file\" : \"testcode\", " +
                   "\"count\": 1 }\n" +

                   "[0,0,0,0,0,0,0,0,1,1,2,3,3,3,2,4,4,4,2,5,7,7,7,6,8,8,8,6," +
                   "9,9,9,6,10,11,11,11,11,11,11,11,12,12,12,12,5]\n" +

                   "/** Begin file information. **/\n" +
                   "[]\n" +

                   "/** Begin mapping definitions. **/\n" +
                   "[\"testcode\",1,9,\"f\"]\n" +
                   "[\"testcode\",1,9,\"f\"]\n" +
                   "[\"testcode\",1,10]\n" +
                   "[\"testcode\",1,11,\"foo\"]\n" +
                   "[\"testcode\",1,16,\"bar\"]\n" +
                   "[\"testcode\",1,21]\n" +
                   "[\"testcode\",1,23]\n" +
                   "[\"testcode\",1,23,\"foo\"]\n" +
                   "[\"testcode\",1,29,\"foo\"]\n" +
                   "[\"testcode\",1,35,\"bar\"]\n" +
                   "[\"testcode\",1,41]\n" +
                   "[\"testcode\",1,44]\n" +
                   "[\"testcode\",1,51,\"foo\"]\n");

    detailLevel = SourceMap.DetailLevel.SYMBOLS;

    checkSourceMap("function f(foo, bar) { foo = foo + bar + 2; return foo; }",

                   "/** Begin line maps. **/{ \"file\" : \"testcode\", " +
                   "\"count\": 1 }\n" +

                   "[0,0,0,0,0,0,0,0,1,1,0,2,2,2,0,3,3,3,0,0,4,4,4,0,5,5,5,0," +
                   "6,6,6,0,0,0,0,0,0,0,0,0,7,7,7,7,0]\n" +

                   "/** Begin file information. **/\n" +
                   "[]\n" +

                   "/** Begin mapping definitions. **/\n" +
                   "[\"testcode\",1,9,\"f\"]\n" +
                   "[\"testcode\",1,9,\"f\"]\n" +
                   "[\"testcode\",1,11,\"foo\"]\n" +
                   "[\"testcode\",1,16,\"bar\"]\n" +
                   "[\"testcode\",1,23,\"foo\"]\n" +
                   "[\"testcode\",1,29,\"foo\"]\n" +
                   "[\"testcode\",1,35,\"bar\"]\n" +
                   "[\"testcode\",1,51,\"foo\"]\n");
  }

  public void testGoldenOutput2() throws Exception {
    checkSourceMap("function f(foo, bar) {\r\n\n\n\nfoo = foo + bar + foo;" +
                   "\nreturn foo;\n}",

                   "/** Begin line maps. **/{ \"file\" : \"testcode\", " +
                   "\"count\": 1 }\n" +

                   "[0,0,0,0,0,0,0,0,1,1,2,3,3,3,2,4,4,4,2,5,7,7,7,6,8,8,8," +
                   "6,9,9,9,6,10,10,10,11,11,11,11,11,11,11,12,12,12," +
                   "12,5]\n" +

                   "/** Begin file information. **/\n" +
                   "[]\n" +
                   "/** Begin mapping definitions. **/\n" +
                   "[\"testcode\",1,9,\"f\"]\n" +
                   "[\"testcode\",1,9,\"f\"]\n" +
                   "[\"testcode\",1,10]\n" +
                   "[\"testcode\",1,11,\"foo\"]\n" +
                   "[\"testcode\",1,16,\"bar\"]\n" +
                   "[\"testcode\",1,21]\n" +
                   "[\"testcode\",5,0]\n" +
                   "[\"testcode\",5,0,\"foo\"]\n" +
                   "[\"testcode\",5,6,\"foo\"]\n" +
                   "[\"testcode\",5,12,\"bar\"]\n" +
                   "[\"testcode\",5,18,\"foo\"]\n" +
                   "[\"testcode\",6,0]\n" +
                   "[\"testcode\",6,7,\"foo\"]\n");
  }

  public void testGoldenOutput3() throws Exception {
    checkSourceMap("c:\\myfile.js",
                   "foo;",

                   "/** Begin line maps. **/{ \"file\" : \"testcode\", " +
                   "\"count\": 1 }\n" +

                   "[0,0,0]\n" +

                   "/** Begin file information. **/\n" +
                   "[]\n" +
                   "/** Begin mapping definitions. **/\n" +
                   "[\"c:\\\\myfile.js\",1,0,\"foo\"]\n");
  }

  public void testGoldenOutput4() throws Exception {
    checkSourceMap("c:\\myfile.js",
                   "foo;   boo;   goo;",

                   "/** Begin line maps. **/" +
                   "{ \"file\" : \"testcode\", \"count\": 1 }\n" +
                   "[0,0,0,1,1,1,1,2,2,2,2]\n" +

                   "/** Begin file information. **/\n" +
                   "[]\n" +

                   "/** Begin mapping definitions. **/\n" +
                   "[\"c:\\\\myfile.js\",1,0,\"foo\"]\n" +
                   "[\"c:\\\\myfile.js\",1,7,\"boo\"]\n" +
                   "[\"c:\\\\myfile.js\",1,14,\"goo\"]\n");
  }

  public void testGoldenOutput5() throws Exception {
    detailLevel = SourceMap.DetailLevel.ALL;

    checkSourceMap("c:\\myfile.js",
                   "/** @preserve\n" +
                   " * this is a test.\n" +
                   " */\n" +
                   "var foo=a + 'this is a really long line that will force the"
                   + " mapping to span multiple lines 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + " 123456789 123456789 123456789 123456789 123456789"
                   + "' + c + d + e;",

                   "/** Begin line maps. **/" +
                   "{ \"file\" : \"testcode\", \"count\": 6 }\n" +
                   "[]\n" +
                   "[]\n" +
                   "[]\n" +
                   "[]\n" +
                   "[0,0,0,0,1,1,1,1,2,1,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3," +
                   "3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3]\n" +
                   "[4,1,5,1,6]\n" +
                   "/** Begin file information. **/\n" +
                   "[]\n" +
                   "[]\n" +
                   "[]\n" +
                   "[]\n" +
                   "[]\n" +
                   "[]\n" +
                   "/** Begin mapping definitions. **/\n" +
                   "[\"c:\\\\myfile.js\",4,0]\n" +
                   "[\"c:\\\\myfile.js\",4,4,\"foo\"]\n" +
                   "[\"c:\\\\myfile.js\",4,8,\"a\"]\n" +
                   "[\"c:\\\\myfile.js\",4,12]\n" +
                   "[\"c:\\\\myfile.js\",4,1314,\"c\"]\n" +
                   "[\"c:\\\\myfile.js\",4,1318,\"d\"]\n" +
                   "[\"c:\\\\myfile.js\",4,1322,\"e\"]\n");

    detailLevel = SourceMap.DetailLevel.SYMBOLS;

    checkSourceMap("c:\\myfile.js",
        "/** @preserve\n" +
        " * this is a test.\n" +
        " */\n" +
        "var foo=a + 'this is a really long line that will force the"
        + " mapping to span multiple lines 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + " 123456789 123456789 123456789 123456789 123456789"
        + "' + c + d + e;",

        "/** Begin line maps. **/" +
        "{ \"file\" : \"testcode\", \"count\": 6 }\n" +
        "[]\n" +
        "[]\n" +
        "[]\n" +
        "[]\n" +
        "[-1,-1,-1,-1,0,0,0,0,1]\n" +
        "[2,0,3,0,4]\n" +
        "/** Begin file information. **/\n" +
        "[]\n" +
        "[]\n" +
        "[]\n" +
        "[]\n" +
        "[]\n" +
        "[]\n" +
        "/** Begin mapping definitions. **/\n" +
        "[\"c:\\\\myfile.js\",4,4,\"foo\"]\n" +
        "[\"c:\\\\myfile.js\",4,8,\"a\"]\n" +
        "[\"c:\\\\myfile.js\",4,1314,\"c\"]\n" +
        "[\"c:\\\\myfile.js\",4,1318,\"d\"]\n" +
        "[\"c:\\\\myfile.js\",4,1322,\"e\"]\n");
  }

  public void testBasicDeterminism() throws Exception {
    RunResult result1 = compile("file1", "foo;", "file2", "bar;");
    RunResult result2 = compile("file2", "foo;", "file1", "bar;");

    String map1 = getSourceMap(result1);
    String map2 = getSourceMap(result2);

    // Assert that the files section of the maps are the same. The actual
    // entries will differ, so we cannot do a simple full comparison.

    // Line 5 has the file information.
    String files1 = map1.split("\n")[4];
    String files2 = map2.split("\n")[4];

    assertEquals(files1, files2);
  }

  /**
   * Creates a source map for the given JS code and asserts it is
   * equal to the expected golden map.
   */
  private void checkSourceMap(String js, String expectedMap)
      throws IOException {
    checkSourceMap("testcode", js, expectedMap);
  }

  private String getSourceMap(RunResult result) throws IOException {
    StringBuilder sb = new StringBuilder();
    result.sourceMap.appendTo(sb, "testcode");
    return sb.toString();
  }

  private void checkSourceMap(String fileName, String js, String expectedMap)
      throws IOException {
    RunResult result = compile(js, fileName);
    assertEquals(expectedMap, result.sourceMapFileContent);
    assertEquals(result.sourceMapFileContent, getSourceMap(result));
  }

  private static class RunResult {
    String generatedSource;
    SourceMap sourceMap;
    public String sourceMapFileContent;
  }

  private static class Token {
    String tokenName;
    Position position;
  }

  /**
   * Finds the all the __XX__ tokens in the given Javascript
   * string.
   */
  private Map<String, Token> findTokens(String js) {
    Map<String, Token> tokens = Maps.newLinkedHashMap();

    int currentLine = 0;
    int positionOffset = 0;

    for (int i = 0; i < js.length(); ++i) {
      char current = js.charAt(i);

      if (current == '\n') {
        positionOffset = i + 1;
        currentLine++;
        continue;
      }

      if (current == '_' && (i < js.length() - 5)) {
        // Check for the _ token.
        if (js.charAt(i + 1) != '_') {
          continue;
        }

        // Loop until we have another _ token.
        String tokenName = "";

        int j = i + 2;
        for (; j < js.length(); ++j) {
          if (js.charAt(j) == '_') {
            break;
          }

          tokenName += js.charAt(j);
        }

        if (tokenName.length() > 0) {
          Token token = new Token();
          token.tokenName = tokenName;
          int currentPosition = i - positionOffset;
          token.position = new Position(currentLine, currentPosition);
          tokens.put(tokenName, token);
        }

        i = j;
      }
    }

    return tokens;
  }

  private void compileAndCheck(String js) {
    RunResult result = compile(js);

    // Find all instances of the __XXX__ pattern in the original
    // source code.
    Map<String, Token> originalTokens = findTokens(js);

    // Find all instances of the __XXX__ pattern in the generated
    // source code.
    Map<String, Token> resultTokens = findTokens(result.generatedSource);

    // Ensure that the generated instances match via the source map
    // to the original source code.

    // Ensure the token counts match.
    assertEquals(originalTokens.size(), resultTokens.size());

    SourceMapReader reader = new SourceMapReader();
    try {
      reader.parse(result.sourceMapFileContent);
    } catch (SourceMapParseException e) {
      throw new RuntimeException("unexpected exception", e);
    }

    // Map the tokens from the generated source back to the
    // input source and ensure that the map is correct.
    for (Token token : resultTokens.values()) {
      OriginalMapping mapping = reader.getMappingForLine(
          token.position.getLineNumber() + 1,
          token.position.getCharacterIndex() + 1);

      assertNotNull(mapping);

      // Find the associated token in the input source.
      Token inputToken = originalTokens.get(token.tokenName);
      assertNotNull(inputToken);

      // Ensure that the map correctly points to the token (we add 1
      // to normalize versus the Rhino line number indexing scheme).
      assertEquals(mapping.position.getLineNumber(),
                   inputToken.position.getLineNumber() + 1);

      // Ensure that if the token name does not being with an 'STR' (meaning a
      // string) it has an original name.
      if (!inputToken.tokenName.startsWith("STR")) {
        assertTrue(!mapping.originalName.isEmpty());
      }

      // Ensure that if the mapping has a name, it matches the token.
      if (!mapping.originalName.isEmpty()) {
        assertEquals(mapping.originalName, "__" + inputToken.tokenName + "__");
      }
    }
  }

  private RunResult compile(String js) {
    return compile(js, "testcode");
  }

  private RunResult compile(String js, String fileName) {
    return compile(js, fileName, null, null);
  }

  private RunResult compile(String js1, String fileName1, String js2,
      String fileName2) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.sourceMapOutputPath = "testcode_source_map.out";
    options.sourceMapDetailLevel = detailLevel;

    // Turn on IDE mode to get rid of optimizations.
    options.ideMode = true;

    JSSourceFile[] inputs = { JSSourceFile.fromCode(fileName1, js1) };

    if (js2 != null && fileName2 != null) {
      JSSourceFile[] multiple =  { JSSourceFile.fromCode(fileName1, js1),
                                   JSSourceFile.fromCode(fileName2, js2) };
      inputs = multiple;
    }

    Result result = compiler.compile(EXTERNS, inputs, options);

    assertTrue(result.success);
    String source = compiler.toSource();

    StringBuilder sb = new StringBuilder();
    try {
      result.sourceMap.appendTo(sb, "testcode");
    } catch (IOException e) {
      throw new RuntimeException("unexpected exception", e);
    }

    RunResult rr = new RunResult();
    rr.generatedSource = source;
    rr.sourceMap = result.sourceMap;
    rr.sourceMapFileContent = sb.toString();
    return rr;
  }

  public static class SourceMapParseException extends IOException {
    private static final long serialVersionUID = 1L;

    public SourceMapParseException(String message) {
      super(message);
    }
  }

  public static class OriginalMapping {
    public final String srcfile;
    public final Position position;
    public final String originalName;

    OriginalMapping(String srcfile, int line, int column, String name) {
      this.srcfile = srcfile;
      this.position = new Position(line, column);
      this.originalName = name;
    }
  }

  /**
   * Class for parsing and representing a SourceMap
   * TODO(johnlenz): This would be best as a separate open-source component.
   *     Remove this when it is.
   */
  public class SourceMapReader {
    private static final String LINEMAP_HEADER = "/** Begin line maps. **/";
    private static final String FILEINFO_HEADER =
        "/** Begin file information. **/";

    private static final String DEFINITION_HEADER =
        "/** Begin mapping definitions. **/";

    /**
     * Internal class for parsing the SourceMap. Used to maintain parser
     * state in an easy to use instance.
     */
    private class ParseState {
      private Reader reader = null;
      private int currentPosition = 0;

      public ParseState(String contents) {
        this.reader = new StringReader(contents);
      }

      /**
       * Consumes a single character. If we have already reached the end
       * of the string, returns  -1.
       */
      private int consumeCharacter() {
        try {
          currentPosition++;
          return reader.read();
        } catch (IOException iox) {
          // Should never happen. Local reader.
          throw new IllegalStateException("IOException raised by reader");
        }
      }

      /**
       * Consumes the specified value found in the contents string. If the value
       * is not found, throws a parse exception.
       */
      public void consume(String value) throws SourceMapParseException {
         for (int i = 0; i < value.length(); ++i) {
          int ch = consumeCharacter();

          if (ch == -1 || ch != value.charAt(i)) {
            fail("At character " + currentPosition + " expected: " + value);
          }
        }
      }

      /**
       * Consumes characters until the newline character is found or the string
       * has been entirely consumed. Returns the string consumed (without the
       * newline).
       */
      public String consumeUntilEOL() {
        StringBuilder sb = new StringBuilder();

        int ch = -1;

        while ((ch = consumeCharacter()) != '\n') {
          if (ch == -1) {
            return sb.toString();
          }

          sb.append((char) ch);
        }

        return sb.toString();
      }

      /**
       * Indicates that parsing has failed by throwing a parse exception.
       */
      public void fail(String message) throws SourceMapParseException {
        throw new SourceMapParseException(message);
      }
    }

    /**
     * Mapping from a line number (0-indexed), to a list of mapping IDs, one for
     * each character on the line. For example, if the array for line 2 is
     * [4,,,,5,6,,,7], then there will be the entry:
     *
     * 1 => {4, 4, 4, 4, 5, 6, 6, 6, 7}
     *
     */
    private Multimap<Integer, Integer> characterMap = null;

    /**
     * Map of Mapping IDs to the actual mapping object.
     */
    private Map<Integer, OriginalMapping> mappings = null;

    public SourceMapReader() {
    }

    /**
     * Parses the given contents containing a source map.
     */
    public void parse(String contents) throws SourceMapParseException {
      ParseState parser = new ParseState(contents);

      characterMap = LinkedListMultimap.create();
      mappings = Maps.newHashMap();

      try {
        // /** Begin line maps. **/{ count: 2 }
        parser.consume(LINEMAP_HEADER);
        String countJSON = parser.consumeUntilEOL();

        JSONObject countObject = new JSONObject(countJSON);

        if (!countObject.has("count")) {
          parser.fail("Missing 'count'");
        }

        int lineCount = countObject.getInt("count");

        if (lineCount <= 0) {
          parser.fail("Count must be >= 1");
        }

        // [0,,,,,,1,2]
        for (int i = 0; i < lineCount; ++i) {
          String currentLine = parser.consumeUntilEOL();

          // Blank lines are allowed in the spec to indicate no mapping
          // information for the line.
          if (currentLine.isEmpty()) {
            continue;
          }

          JSONArray charArray = new JSONArray(currentLine);

          int lastID = -1;

          for (int j = 0; j < charArray.length(); ++j) {
            int mappingID = lastID;

            if (!charArray.isNull(j)) {
              mappingID = charArray.optInt(j);
            }

            // Save the current character's mapping.
            characterMap.put(i, mappingID);

            lastID = mappingID;
          }
        }

        // /** Begin file information. **/
        parser.consume(FILEINFO_HEADER);

        if (parser.consumeUntilEOL().length() > 0) {
          parser.fail("Unexpected token after file information header");
        }

        // File information. Not used, so we just consume it.
        for (int i = 0; i < lineCount; ++i) {
          parser.consumeUntilEOL();
        }

        // /** Begin mapping definitions. **/
        parser.consume(DEFINITION_HEADER);

        if (parser.consumeUntilEOL().length() > 0) {
          parser.fail("Unexpected token after definition header");
        }

        String currentLine = null;

        // ['d.js', 3, 78, 'foo']
        for (int mappingID = 0;
             (currentLine = parser.consumeUntilEOL()).length() > 0;
             ++mappingID) {

          JSONArray mapArray = new JSONArray(currentLine);

          if (mapArray.length() < 3) {
            parser.fail("Invalid mapping array");
          }

          OriginalMapping mapping = new OriginalMapping(
              mapArray.getString(0), // srcfile
              mapArray.getInt(1),    // line
              mapArray.getInt(2),    // column
              mapArray.optString(3, "")); // identifier

          mappings.put(mappingID, mapping);
        }
      } catch (JSONException ex) {
        parser.fail("JSON parse exception: " + ex);
      }
    }

    public OriginalMapping getMappingForLine(int lineNumber, int columnIndex) {
      Preconditions.checkNotNull(characterMap, "parse() must be called first");

      if (!characterMap.containsKey(lineNumber - 1)) {
        return null;
      }

      Collection<Integer> mapIds = characterMap.get(lineNumber - 1);

      int columnPosition = columnIndex - 1;
      if (columnPosition >= mapIds.size() || columnPosition < 0) {
        return null;
      }

      // TODO(user): Find a way to make this faster.
      Integer[] mapIdsAsArray = new Integer[mapIds.size()];
      mapIds.<Integer>toArray(mapIdsAsArray);

      int mapId = mapIdsAsArray[columnPosition];

      if (mapId < 0) {
        return null;
      }

      return mappings.get(mapId);
    }
  }
}
