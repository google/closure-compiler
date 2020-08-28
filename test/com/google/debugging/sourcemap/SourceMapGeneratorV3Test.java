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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.debugging.sourcemap.SourceMapGeneratorV3.ExtensionMergeAction;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.SourceMap.Format;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author johnlenz@google.com (John Lenz) */
@RunWith(JUnit4.class)
public final class SourceMapGeneratorV3Test extends SourceMapTestCase {

  @Override
  protected SourceMapConsumer getSourceMapConsumer() {
    return new SourceMapConsumerV3();
  }

  @Override
  protected Format getSourceMapFormat() {
    return SourceMap.Format.V3;
  }

  private static String getEncodedFileName() {
    if (File.separatorChar == '\\') {
      return "c:/myfile.js";
    } else {
      return "c:\\myfile.js";
    }
  }

  @Test
  public void testBasicMapping1() throws Exception {
    compileAndCheck("function __BASIC__() { }");
  }

  @Test
  public void testBasicMappingGoldenOutput() throws Exception {
    // Empty source map test
    checkSourceMap(
        "function __BASIC__() { }",
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(1)
            .setMappings("A,aAAAA,QAASA,UAAS,EAAG;")
            .setSources("testcode")
            .setNames("__BASIC__")
            .build());
  }

  @Test
  public void testBasicMapping2() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__) {}");
  }

  @Test
  public void testLiteralMappings() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) { " +
                    "var __VAR__ = '__STR__'; }");
  }

  @Test
  public void testLiteralMappingsGoldenOutput() throws Exception {
    // Empty source map test
    checkSourceMap(
        "function __BASIC__(__PARAM1__, __PARAM2__) { " + "var __VAR__ = '__STR__'; }",
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(1)
            .setMappings("A,aAAAA,QAASA,UAAS,CAACC,UAAD,CAAaC,UAAb,CAAyB,CAAE,IAAIC,QAAU,SAAhB;")
            .setSources("testcode")
            .setNames("__BASIC__", "__PARAM1__", "__PARAM2__", "__VAR__")
            .build());
  }

  @Test
  public void testMultilineMapping() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) {\n" +
                    "var __VAR__ = '__STR__';\n" +
                    "var __ANO__ = \"__STR2__\";\n" +
                    "}");
  }

  @Test
  public void testMultilineMapping2() throws Exception {
    compileAndCheck("function __BASIC__(__PARAM1__, __PARAM2__) {\n" +
                    "var __VAR__ = 1;\n" +
                    "var __ANO__ = 2;\n" +
                    "}");
  }

  @Test
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

  @Test
  public void testGoldenOutput0() throws Exception {
    // Empty source map test
    checkSourceMap(
        "",
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(1)
            .setMappings("A;")
            .setSources()
            .setNames()
            .build());
  }

  @Test
  public void testGoldenOutput0a() throws Exception {
    checkSourceMap(
        "a;",
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(1)
            .setMappings("A,aAAAA;")
            .setSources("testcode")
            .setNames("a")
            .build());

    sourceMapIncludeSourcesContent = true;

    checkSourceMap(
        "a;",
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(1)
            .setMappings("A,aAAAA;")
            .setSources("testcode")
            .setSourcesContent("a;")
            .setNames("a")
            .build());
  }

  @Test
  public void testGoldenOutput1() throws Exception {
    detailLevel = SourceMap.DetailLevel.ALL;

    checkSourceMap(
        "function f(foo, bar) { foo = foo + bar + 2; return foo; }",
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(1)
            .setMappings(
                "A,aAAAA,QAASA,EAAC,CAACC,GAAD,CAAMC,GAAN,"
                    + "CAAW,CAAED,GAAA,CAAMA,GAAN,CAAYC,GAAZ,CAAkB,CAAG,"
                    + "OAAOD,IAA9B;")
            .setSources("testcode")
            .setNames("f", "foo", "bar")
            .build());

    detailLevel = SourceMap.DetailLevel.SYMBOLS;

    checkSourceMap(
        "function f(foo, bar) { foo = foo + bar + 2; return foo; }",
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(1)
            .setMappings(
                "A,aAAAA,QAASA,EAATA,CAAWC,GAAXD,CAAgBE,"
                    + "GAAhBF,EAAuBC,GAAvBD,CAA6BC,GAA7BD,CAAmCE,GAAnCF,"
                    + "SAAmDC,IAAnDD;")
            .setSources("testcode")
            .setNames("f", "foo", "bar")
            .build());

    sourceMapIncludeSourcesContent = true;

    checkSourceMap(
        "function f(foo, bar) { foo = foo + bar + 2; return foo; }",
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(1)
            .setMappings(
                "A,aAAAA,QAASA,EAATA,CAAWC,GAAXD,CAAgBE,"
                    + "GAAhBF,EAAuBC,GAAvBD,CAA6BC,GAA7BD,CAAmCE,GAAnCF,"
                    + "SAAmDC,IAAnDD;")
            .setSources("testcode")
            .setSourcesContent("function f(foo, bar) { foo = foo + bar + 2; return foo; }")
            .setNames("f", "foo", "bar")
            .build());
  }

  @Test
  public void testGoldenOutput2() throws Exception {
    checkSourceMap(
        "function f(foo, bar) {\r\n\n\n\nfoo = foo + bar + foo;" + "\nreturn foo;\n}",
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(1)
            .setMappings(
                "A,aAAAA,QAASA,EAAC,CAACC,GAAD,CAAMC,GAAN,"
                    + "CAAW,CAIrBD,GAAA,CAAMA,GAAN,CAAYC,GAAZ,CAAkBD,"
                    + "GAClB,OAAOA,IALc;")
            .setSources("testcode")
            .setNames("f", "foo", "bar")
            .build());
  }

  @Test
  public void testGoldenOutput3() throws Exception {
    checkSourceMap(
        "c:\\myfile.js",
        "foo;",
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(1)
            .setMappings("A,aAAAA;")
            .setSources(getEncodedFileName())
            .setNames("foo")
            .build());
  }

  @Test
  public void testGoldenOutput4() throws Exception {
    checkSourceMap(
        "c:\\myfile.js",
        "foo;   boo;   goo;",
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(1)
            .setMappings("A,aAAAA,GAAOC,IAAOC;")
            .setSources(getEncodedFileName())
            .setNames("foo", "boo", "goo")
            .build());
  }

  @Test
  public void testGoldenOutput5() throws Exception {
    detailLevel = SourceMap.DetailLevel.ALL;

    checkSourceMap(
        "c:\\myfile.js",
        "/** @preserve\n"
            + " * this is a test.\n"
            + " */\n"
            + "var foo=a + 'this is a really long line that will force the"
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
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(6)
            .setMappings(
                "A;;;;aAGA,IAAIA,IAAIC,CAAJD,CAAQ,mxCAARA;AAA8xCE,"
                    + "CAA9xCF,CAAkyCG,CAAlyCH,CAAsyCI;")
            .setSources(getEncodedFileName())
            .setNames("foo", "a", "c", "d", "e")
            .build());

    detailLevel = SourceMap.DetailLevel.SYMBOLS;

    checkSourceMap(
        "c:\\myfile.js",
        "/** @preserve\n"
            + " * this is a test.\n"
            + " */\n"
            + "var foo=a + 'this is a really long line that will force the"
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
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(6)
            .setMappings("A;;;;iBAGIA,IAAIC,CAAJD;AAA8xCE,CAA9xCF,CAAkyCG,CAAlyCH,CAAsyCI;")
            .setSources(getEncodedFileName())
            .setNames("foo", "a", "c", "d", "e")
            .build());
  }

  @Test
  public void testGoldenOutput_semanticLineBreaks() throws Exception {
    // Mapping code segments that contained newlines caused b/121282798. It appears to be related to
    // template literals because they can contain newlines which are meaningful within a single
    // expression, and are thus preserved.
    checkSourceMap(
        "c:\\myfile.js",
        lines(
            "var myMultilineTemplate = `Item ${a}", //
            "Item ${b}",
            "Item ${c}`;"),
        TestJsonBuilder.create()
            .setVersion(3)
            .setFile("testcode")
            .setLineCount(3)
            .setMappings("A,aAAA,IAAIA,oBAAsB,QAAQC,CAAR;OACnBC,CADmB;OAEnBC,CAFmB;")
            .setSources(getEncodedFileName())
            .setNames("myMultilineTemplate", "a", "b", "c")
            .build());
  }

  @Test
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

    assertThat(files2).isEqualTo(files1);
  }

  @Test
  public void testWriteMetaMap() throws IOException {
    StringWriter out = new StringWriter();
    String name = "./app.js";
    List<SourceMapSection> appSections = ImmutableList.of(
        SourceMapSection.forURL("src1", 0, 0),
        SourceMapSection.forURL("src2", 100, 10),
        SourceMapSection.forURL("src3", 150, 5));

    SourceMapGeneratorV3 generator = new SourceMapGeneratorV3();
    generator.appendIndexMapTo(out, name, appSections);

    assertThat(out.toString())
        .isEqualTo("{\n"
            + "\"version\":3,\n"
            + "\"file\":\"./app.js\",\n"
            + "\"sections\":[\n"
            + "{\n"
            + "\"offset\":{\n"
            + "\"line\":0,\n"
            + "\"column\":0\n"
            + "},\n"
            + "\"url\":\"src1\"\n"
            + "},\n"
            + "{\n"
            + "\"offset\":{\n"
            + "\"line\":100,\n"
            + "\"column\":10\n"
            + "},\n"
            + "\"url\":\"src2\"\n"
            + "},\n"
            + "{\n"
            + "\"offset\":{\n"
            + "\"line\":150,\n"
            + "\"column\":5\n"
            + "},\n"
            + "\"url\":\"src3\"\n"
            + "}\n"
            + "]\n"
            + "}\n");
  }

  private static String getEmptyMapFor(String name) throws IOException {
    StringWriter out = new StringWriter();
    SourceMapGeneratorV3 generator = new SourceMapGeneratorV3();
    generator.appendTo(out, name);
    return out.toString();
  }

  @Test
  public void testWriteMetaMap2() throws IOException {
    StringWriter out = new StringWriter();
    String name = "./app.js";
    List<SourceMapSection> appSections = ImmutableList.of(
        // Map and URLs can be mixed.
        SourceMapSection.forMap(getEmptyMapFor("./part.js"), 0, 0),
        SourceMapSection.forURL("src2", 100, 10));

    SourceMapGeneratorV3 generator = new SourceMapGeneratorV3();
    generator.appendIndexMapTo(out, name, appSections);

    assertThat(out.toString())
        .isEqualTo("{\n"
            + "\"version\":3,\n"
            + "\"file\":\"./app.js\",\n"
            + "\"sections\":[\n"
            + "{\n"
            + "\"offset\":{\n"
            + "\"line\":0,\n"
            + "\"column\":0\n"
            + "},\n"
            + "\"map\":{\n"
            + "\"version\":3,\n"
            + "\"file\":\"./part.js\",\n"
            + "\"lineCount\":1,\n"
            + "\"mappings\":\";\",\n"
            + "\"sources\":[],\n"
            + "\"names\":[]\n"
            + "}\n"
            + "\n"
            + "},\n"
            + "{\n"
            + "\"offset\":{\n"
            + "\"line\":100,\n"
            + "\"column\":10\n"
            + "},\n"
            + "\"url\":\"src2\"\n"
            + "}\n"
            + "]\n"
            + "}\n");
  }

  @Test
  public void testParseSourceMetaMap() throws Exception {
    final String INPUT1 = "file1";
    final String INPUT2 = "file2";
    LinkedHashMap<String, String> inputs = new LinkedHashMap<>();
    inputs.put(INPUT1, "var __FOO__ = 1;");
    inputs.put(INPUT2, "var __BAR__ = 2;");
    RunResult result1 = compile(inputs.get(INPUT1), INPUT1);
    RunResult result2 = compile(inputs.get(INPUT2), INPUT2);

    final String MAP1 = "map1";
    final String MAP2 = "map2";
    final LinkedHashMap<String, String> maps = new LinkedHashMap<>();
    maps.put(MAP1, result1.sourceMapFileContent);
    maps.put(MAP2, result2.sourceMapFileContent);

    List<SourceMapSection> sections = new ArrayList<>();

    StringBuilder output = new StringBuilder();
    FilePosition offset = appendAndCount(output, result1.generatedSource);
    sections.add(SourceMapSection.forURL(MAP1, 0, 0));
    output.append(result2.generatedSource);
    sections.add(
        SourceMapSection.forURL(MAP2, offset.getLine(), offset.getColumn()));

    SourceMapGeneratorV3 generator = new SourceMapGeneratorV3();
    StringBuilder mapContents = new StringBuilder();
    generator.appendIndexMapTo(mapContents, "out.js", sections);

    check(inputs, output.toString(), mapContents.toString(),
      new SourceMapSupplier() {
        @Override
        public String getSourceMap(String url){
          return maps.get(url);
      }});
  }

  @Test
  public void testSourceMapMerging() throws Exception {
    final String INPUT1 = "file1";
    final String INPUT2 = "file2";
    LinkedHashMap<String, String> inputs = new LinkedHashMap<>();
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

  @Test
  public void testSourceMerging1() throws SourceMapParseException, IOException {
    // construct first source map
    SourceMapGeneratorV3 sourceMapGeneratorV3 = new SourceMapGeneratorV3();
    sourceMapGeneratorV3.addMapping(
        "sourceName1",
        "symbolName1",
        new FilePosition(1, 1),
        new FilePosition(2, 2),
        new FilePosition(3, 3));
    StringWriter w = new StringWriter();
    sourceMapGeneratorV3.appendTo(w, "foo.js.sourcemap");
    String sourceMap1 = w.toString();

    // construct second source map
    sourceMapGeneratorV3 = new SourceMapGeneratorV3();
    sourceMapGeneratorV3.addMapping(
        "sourceName2",
        "symbolName2",
        new FilePosition(1, 4),
        new FilePosition(2, 5),
        new FilePosition(3, 6));
    w = new StringWriter();
    sourceMapGeneratorV3.appendTo(w, "bar.js.sourcemap");
    String sourceMap2 = w.toString();

    // combine the two
    sourceMapGeneratorV3 = new SourceMapGeneratorV3();
    sourceMapGeneratorV3.mergeMapSection(0, 0, sourceMap1);
    sourceMapGeneratorV3.mergeMapSection(4, 0, sourceMap2);
    sourceMapGeneratorV3.appendTo(w, "foobar.js.sourcemap");
    String combinedMap = w.toString();

    // Construct the same map manually
    sourceMapGeneratorV3 = new SourceMapGeneratorV3();
    sourceMapGeneratorV3.addMapping(
        "sourceName1",
        "symbolName1",
        new FilePosition(1, 1),
        new FilePosition(2, 2),
        new FilePosition(3, 3));
    sourceMapGeneratorV3.addMapping(
        "sourceName2",
        "symbolName2",
        new FilePosition(1, 4),
        new FilePosition(6, 5),
        new FilePosition(7, 6));
    sourceMapGeneratorV3.appendTo(w, "foobar.js.sourcemap");
    String manuallyCombined = w.toString();
    // TODO(b/62591567): these should be equal, but combinedMap is missing the last mapping.
    assertThat(combinedMap).isNotEqualTo(manuallyCombined);
  }

  @Test
  public void testSourceMapExtensions() throws Exception {
    //generating the json
    SourceMapGeneratorV3 mapper = new SourceMapGeneratorV3();
    mapper.addExtension("x_google_foo", new JsonObject());
    mapper.addExtension("x_google_test", parseJsonObject("{\"number\" : 1}"));
    mapper.addExtension("x_google_array", new JsonArray());
    mapper.addExtension("x_google_int", 2);
    mapper.addExtension("x_google_str", "Some text");

    mapper.removeExtension("x_google_foo");
    StringBuilder out = new StringBuilder();
    mapper.appendTo(out, "out.js");

    assertThat(mapper.hasExtension("x_google_test")).isTrue();

    //reading & checking the extension properties
    JsonObject sourceMap = parseJsonObject(out.toString());

    assertThat(sourceMap.has("x_google_foo")).isFalse();
    assertThat(sourceMap.has("google_test")).isFalse();
    assertThat(sourceMap.get("x_google_test").getAsJsonObject().get("number").getAsInt())
        .isEqualTo(1);
    assertThat(sourceMap.get("x_google_array").getAsJsonArray()).isEmpty();
    assertThat(sourceMap.get("x_google_int").getAsInt()).isEqualTo(2);
    assertThat(sourceMap.get("x_google_str").getAsString()).isEqualTo("Some text");
  }

  @Test
  public void testSourceMapMergeExtensions() throws Exception {
    SourceMapGeneratorV3 mapper = new SourceMapGeneratorV3();
    mapper.mergeMapSection(
        0,
        0,
        "{\n"
            + "\"version\":3,\n"
            + "\"file\":\"testcode\",\n"
            + "\"lineCount\":1,\n"
            + "\"mappings\":\"AAAAA,a,QAASA,UAAS,EAAG;\",\n"
            + "\"sources\":[\"testcode\"],\n"
            + "\"names\":[\"__BASIC__\"],\n"
            + "\"x_company_foo\":2\n"
            + "}\n");

    assertThat(mapper.hasExtension("x_company_foo")).isFalse();

    mapper.addExtension("x_company_baz", 2);

    mapper.mergeMapSection(0, 0,
        "{\n" +
            "\"version\":3,\n" +
            "\"file\":\"testcode2\",\n" +
            "\"lineCount\":0,\n" +
            "\"mappings\":\"\",\n" +
            "\"sources\":[\"testcode2\"],\n" +
            "\"names\":[],\n" +
            "\"x_company_baz\":3,\n" +
            "\"x_company_bar\":false\n" +
            "}\n", new ExtensionMergeAction() {
      @Override
      public Object merge(String extensionKey, Object currentValue,
          Object newValue) {
        return (Integer) currentValue
            + ((JsonPrimitive) newValue).getAsInt();
      }
    });

    assertThat(mapper.getExtension("x_company_baz")).isEqualTo(5);
    assertThat(((JsonPrimitive) mapper.getExtension("x_company_bar")).getAsBoolean()).isFalse();
  }

  @Test
  public void testSourceRoot() throws Exception {
    SourceMapGeneratorV3 mapper = new SourceMapGeneratorV3();

    //checking absence of sourceRoot
    StringBuilder out = new StringBuilder();
    mapper.appendTo(out, "out.js");
    JsonObject mapping = parseJsonObject(out.toString());

    assertThat(mapping.get("version").getAsInt()).isEqualTo(3);
    assertThat(mapping.has("sourceRoot")).isFalse();

    out = new StringBuilder();
    mapper.setSourceRoot("");
    mapper.appendTo(out, "out2.js");
    mapping = parseJsonObject(out.toString());

    assertThat(mapping.has("sourceRoot")).isFalse();

    //checking sourceRoot
    out = new StringBuilder();
    mapper.setSourceRoot("http://url/path");
    mapper.appendTo(out, "out3.js");
    mapping = parseJsonObject(out.toString());

    assertThat(mapping.get("sourceRoot").getAsString()).isEqualTo("http://url/path");
  }

  FilePosition count(String js) {
    int line = 0;
    int column = 0;
    for (int i = 0; i < js.length(); i++) {
      if (js.charAt(i) == '\n') {
        line++;
        column = 0;
      } else {
        column++;
      }
    }
    return new FilePosition(line, column);
  }

  FilePosition appendAndCount(Appendable out, String js) throws IOException {
    out.append(js);
    return count(js);
  }

  private static JsonObject parseJsonObject(String json) {
    return new Gson().fromJson(json, JsonObject.class);
  }
}
