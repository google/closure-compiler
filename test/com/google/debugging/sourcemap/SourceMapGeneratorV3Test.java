/*
 * Copyright 2011 The Closure Compiler Authors.
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

package com.google.debugging.sourcemap;

import com.google.common.collect.Maps;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.SourceMap.Format;

import java.io.IOException;
import java.util.LinkedHashMap;


/**
 * @author johnlenz@google.com (John Lenz)
 */
public class SourceMapGeneratorV3Test extends SourceMapTestCase {

  @Override
  SourceMapConsumer getSourceMapConsumer() {
    return new SourceMapConsumerV3();
  }

  @Override
  Format getSourceMapFormat() {
    return SourceMap.Format.V3;
  }

  public void testBasicMapping1() throws Exception {
    compileAndCheck("function __BASIC__() { }");
  }

  public void testBasicMappingGoldenOutput() throws Exception {
    // Empty source map test
    checkSourceMap("function __BASIC__() { }",

                   "{\n" +
                   "\"version\":3,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"mappings\":\"AACSA,QAAAA,UAAS,EAAG;\",\n" +
                   "\"sources\":[\"testcode\"],\n" +
                   "\"names\":[\"__BASIC__\"]\n" +
                   "}\n");
  }

  public void testBasicMapping2() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__) {}");
  }

  public void testLiteralMappings() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) { " +
                    "var __VAR__ = '__STR__'; }");
  }

  public void testLiteralMappingsGoldenOutput() throws Exception {
    // Empty source map test
    checkSourceMap("function __BASIC__(__PARAM1__, __PARAM2__) { " +
                   "var __VAR__ = '__STR__'; }",

                   "{\n" +
                   "\"version\":3,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"mappings\":\"AACSA,QAAAA,UAAS,CAACC,UAAD,CAAaC,UAAb," +
                       "CAAyB,CAAE,IAAIC,QAAU,SAAhB;\",\n" +
                   "\"sources\":[\"testcode\"],\n" +
                   "\"names\":[\"__BASIC__\",\"__PARAM1__\",\"__PARAM2__\"," +
                       "\"__VAR__\"]\n" +
                   "}\n");
  }

  public void testMultilineMapping() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) {\n" +
                    "var __VAR__ = '__STR__';\n" +
                    "var __ANO__ = \"__STR2__\";\n" +
                    "}");
  }

  public void testMultilineMapping2() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) {\n" +
                    "var __VAR__ = 1;\n" +
                    "var __ANO__ = 2;\n" +
                    "}");
  }

  public void testMultiFunctionMapping() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) {\n" +
                    "var __VAR__ = '__STR__';\n" +
                    "var __ANO__ = \"__STR2__\";\n" +
                    "}\n" +

                    "function __BASIC2__(__PARAM3__, __PARAM4__) {\n" +
                    "var __VAR2__ = '__STR2__';\n" +
                    "var __ANO2__ = \"__STR3__\";\n" +
                    "}\n");
  }

  public void testGoldenOutput0() throws Exception {
    // Empty source map test
    checkSourceMap("",

                   "{\n" +
                   "\"version\":3,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"mappings\":\";\",\n" +
                   "\"sources\":[],\n" +
                   "\"names\":[]\n" +
                   "}\n");
  }

  public void testGoldenOutput1() throws Exception {
    detailLevel = SourceMap.DetailLevel.ALL;

    checkSourceMap("function f(foo, bar) { foo = foo + bar + 2; return foo; }",

                   "{\n" +
                   "\"version\":3,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"mappings\":\"AACSA,QAAAA,EAAC,CAACC,GAAD,CAAMC,GAAN," +
                       "CAAW,CAAED,GAAA,CAAMA,GAAN,CAAYC,GAAZ,CAAkB,CAAG," +
                       "OAAOD,IAA9B;\",\n" +
                   "\"sources\":[\"testcode\"],\n" +
                   "\"names\":[\"f\",\"foo\",\"bar\"]\n" +
                   "}\n");

    detailLevel = SourceMap.DetailLevel.SYMBOLS;

    checkSourceMap("function f(foo, bar) { foo = foo + bar + 2; return foo; }",

                   "{\n" +
                   "\"version\":3,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"mappings\":\"AACSA,QAAAA,EAAAA,CAAEC,GAAFD,CAAOE," +
                       "GAAPF,EAAcC,GAAdD,CAAoBC,GAApBD,CAA0BE,GAA1BF," +
                       "SAA0CC,IAA1CD;\",\n" +
                   "\"sources\":[\"testcode\"],\n" +
                   "\"names\":[\"f\",\"foo\",\"bar\"]\n" +
                   "}\n");
  }

  public void testGoldenOutput2() throws Exception {
    checkSourceMap("function f(foo, bar) {\r\n\n\n\nfoo = foo + bar + foo;" +
                   "\nreturn foo;\n}",

                   "{\n" +
                   "\"version\":3,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"mappings\":\"AACSA,QAAAA,EAAC,CAACC,GAAD,CAAMC,GAAN," +
                       "CAAW,CAIrBD,GAAA,CAAMA,GAAN,CAAYC,GAAZ,CAAkBD," +
                       "GAClB,OAAOA,IALc;\",\n" +
                   "\"sources\":[\"testcode\"],\n" +
                   "\"names\":[\"f\",\"foo\",\"bar\"]\n" +
                   "}\n");
  }

  public void testGoldenOutput3() throws Exception {
    checkSourceMap("c:\\myfile.js",
                   "foo;",

                   "{\n" +
                   "\"version\":3,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"mappings\":\"AACAA;\",\n" +
                   "\"sources\":[\"c:\\\\myfile.js\"],\n" +
                   "\"names\":[\"foo\"]\n" +
                   "}\n");
  }

  public void testGoldenOutput4() throws Exception {
    checkSourceMap("c:\\myfile.js",
                   "foo;   boo;   goo;",

                   "{\n" +
                   "\"version\":3,\n" +
                   "\"file\":\"testcode\",\n" +
                   "\"lineCount\":1,\n" +
                   "\"mappings\":\"AACAA,GAAOC,IAAOC;\",\n" +
                   "\"sources\":[\"c:\\\\myfile.js\"],\n" +
                   "\"names\":[\"foo\",\"boo\",\"goo\"]\n" +
                   "}\n");
  }

  public void testGoldenOutput5() throws Exception {
    detailLevel = SourceMap.DetailLevel.ALL;

    checkSourceMap(
        "c:\\myfile.js",
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

        "{\n" +
        "\"version\":3,\n" +
        "\"file\":\"testcode\",\n" +
        "\"lineCount\":6,\n" +
        "\"mappings\":\"A;;;;AAIA,IAAIA,IAAIC,CAAJD,CAAQ,mxCAARA;AAA8xCE," +
            "CAA9xCF,CAAkyCG,CAAlyCH,CAAsyCI;\",\n" +
        "\"sources\":[\"c:\\\\myfile.js\"],\n" +
        "\"names\":[\"foo\",\"a\",\"c\",\"d\",\"e\"]\n" +
        "}\n");

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

        "{\n" +
        "\"version\":3,\n" +
        "\"file\":\"testcode\",\n" +
        "\"lineCount\":6,\n" +
        "\"mappings\":\"A;;;;IAIIA,IAAIC,CAAJD;AAA8xCE,CAA9xCF,CAAkyCG," +
            "CAAlyCH,CAAsyCI;\",\n" +
        "\"sources\":[\"c:\\\\myfile.js\"],\n" +
        "\"names\":[\"foo\",\"a\",\"c\",\"d\",\"e\"]\n" +
        "}\n");
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

  public void testSourceMapMerging() throws Exception {
    final String INPUT1 = "file1";
    final String INPUT2 = "file2";
    LinkedHashMap<String, String> inputs = Maps.newLinkedHashMap();
    inputs.put(INPUT1, "var __FOO__ = 1;");
    inputs.put(INPUT2, "var __BAR__ = 2;");
    RunResult result1 = compile(inputs.get(INPUT1), INPUT1);
    RunResult result2 = compile(inputs.get(INPUT2), INPUT2);

    StringBuilder output = new StringBuilder();
    FilePosition offset = appendAndCount(output, result1.generatedSource);
    output.append(result2.generatedSource);

    SourceMapGeneratorV3 generator = new SourceMapGeneratorV3();

    generator.mergeMapSection(0, 0, result1.sourceMapFileContent);
    generator.mergeMapSection(offset.getLine(), offset.getColumn(),
        result2.sourceMapFileContent);

    StringBuilder mapContents = new StringBuilder();
    generator.appendTo(mapContents, "out.js");

    check(inputs, output.toString(), mapContents.toString());
  }

  FilePosition appendAndCount(Appendable out, String js) throws IOException {
    int line = 0, column = 0;
    for (int i = 0; i < js.length(); i++) {
      if (js.charAt(i) == '\n') {
        line++;
        column = 0;
      } else {
        column++;
      }
    }
    out.append(js);
    return new FilePosition(line, column);
  }

}
