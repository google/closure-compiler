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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.debugging.sourcemap.SourceMapConsumer;
import com.google.debugging.sourcemap.SourceMapConsumerV3;
import com.google.debugging.sourcemap.SourceMapTestCase;
import com.google.javascript.jscomp.SourceMap.Format;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author johnlenz@google.com (John Lenz)
 */
@RunWith(JUnit4.class)
public final class SourceMapTest extends SourceMapTestCase {

  public SourceMapTest() {}

  private ImmutableList<SourceMap.LocationMapping> mappings;
  private ImmutableMap.Builder<String, SourceMapInput> inputMaps;

  @Test
  public void testPrefixReplacement1() throws IOException {
    // mapping can be used to remove a prefix
    mappings = ImmutableList.of(new SourceMap.PrefixLocationMapping("pre/", ""));
    checkSourceMap2(
        "alert(1);",
        Compiler.joinPathParts("pre", "file1"),
        "alert(2);",
        Compiler.joinPathParts("pre", "file2"),
        """
        {
        "version":3,
        "file":"testcode",
        "lineCount":1,
        "mappings":"A,aAAAA,KAAA,CAAM,CAAN,C,CCAAA,KAAA,CAAM,CAAN;",
        "sources":["file1","file2"],
        "names":["alert"]
        }
        """);
  }

  @Test
  public void testPrefixReplacement2() throws IOException {
    // mapping can be used to replace a prefix
    mappings = ImmutableList.of(new SourceMap.PrefixLocationMapping("pre/file", "src"));
    checkSourceMap2(
        "alert(1);",
        Compiler.joinPathParts("pre", "file1"),
        "alert(2);",
        "pre/file2",
        """
        {
        "version":3,
        "file":"testcode",
        "lineCount":1,
        "mappings":"A,aAAAA,KAAA,CAAM,CAAN,C,CCAAA,KAAA,CAAM,CAAN;",
        "sources":["src1","src2"],
        "names":["alert"]
        }
        """);
  }

  @Test
  public void testPrefixReplacement3() throws IOException {
    // multiple mappings can be applied
    mappings =
        ImmutableList.of(
            new SourceMap.PrefixLocationMapping("file1", "x"),
            new SourceMap.PrefixLocationMapping("file2", "y"));
    checkSourceMap2(
        "alert(1);",
        "file1",
        "alert(2);",
        "file2",
        """
        {
        "version":3,
        "file":"testcode",
        "lineCount":1,
        "mappings":"A,aAAAA,KAAA,CAAM,CAAN,C,CCAAA,KAAA,CAAM,CAAN;",
        "sources":["x","y"],
        "names":["alert"]
        }
        """);
  }

  @Test
  public void testPrefixReplacement4() throws IOException {
    // first match wins
    mappings =
        ImmutableList.of(
            new SourceMap.PrefixLocationMapping("file1", "x"),
            new SourceMap.PrefixLocationMapping("file", "y"));
    checkSourceMap2(
        "alert(1);",
        "file1",
        "alert(2);",
        "file2",
        """
        {
        "version":3,
        "file":"testcode",
        "lineCount":1,
        "mappings":"A,aAAAA,KAAA,CAAM,CAAN,C,CCAAA,KAAA,CAAM,CAAN;",
        "sources":["x","y2"],
        "names":["alert"]
        }
        """);
  }

  @Test
  public void testLambdaReplacement() throws IOException {
    mappings = ImmutableList.of((location) -> "mapped/" + location);
    checkSourceMap2(
        "alert(1);",
        "file1",
        "alert(2);",
        "file2",
        """
        {
        "version":3,
        "file":"mapped/testcode",
        "lineCount":1,
        "mappings":"A,aAAAA,KAAA,CAAM,CAAN,C,CCAAA,KAAA,CAAM,CAAN;",
        "sources":["mapped/file1","mapped/file2"],
        "names":["alert"]
        }
        """);
  }

  // This is taken from SourceMapJsLangTest. That test can't run under J2CL because it
  // uses a parameterized runner but we do want to test this basic behavior under J2CL.
  @Test
  public void testRepeatedCompilation() throws Exception {
    // Run compiler twice feeding sourcemaps from the first run as input to the second run.
    // This way we ensure that compiler works fine with its own sourcemaps and doesn't lose
    // important information.
    String fileContent = "function foo() {} alert(foo());";
    String fileName = "foo.js";
    RunResult firstCompilation = compile(fileContent, fileName);
    String newFileName = fileName + ".compiled";
    inputMaps.put(
        newFileName,
        new SourceMapInput(
            SourceFile.fromCode("sourcemap", firstCompilation.sourceMapFileContent)));

    RunResult secondCompilation = compile(firstCompilation.generatedSource, newFileName);
    check(
        fileName,
        fileContent,
        secondCompilation.generatedSource,
        secondCompilation.sourceMapFileContent);
  }

  // Tests that when a generated intermediate file contains a source map, we
  // only map the source positions that the intermediate map specifies. If a
  // mapping is sourceless, it is omitted from the final source map.
  @Test
  public void testIntermediateFilesOmitUnmappedCode() throws IOException {
    // This map has been massaged to have a sourceless mapping covering the `alert` identifier.
    // Pretend it was synthetically generated during transforms.
    String generatedFile = "generated.tsx.js";
    String generatedSource = "'use strict';function foo(){}alert(foo());";
    String generatedSourceMap =
        """
        {
        "version":3,
        "file":"testcode",
        "lineCount":1,
        "mappings":"A,aAAAA,QAASA,IAAG,EAAG,E,KAAG,CAAMA,GAAA,EAAN;",
        "sources":["foo.js"],
        "names":["foo"]
        }
        """;

    inputMaps.put(
        generatedFile, new SourceMapInput(SourceFile.fromCode("sourcemap", generatedSourceMap)));

    RunResult compilation = compile(generatedSource, generatedFile);
    assertThat(compilation.sourceMapFileContent)
        .isEqualTo(
            """
            {
            "version":3,
            "file":"testcode",
            "lineCount":1,
            "mappings":"A,aAAAA,QAASA,IAAG,EAAG,E,MAASA,GAAAA;",
            "sources":["foo.js"],
            "names":["foo"]
            }
            """);
  }

  // Tests that empty "default" source maps, like those provided by Gulp, are
  // ignored and do not cause the compiler to treat the file as an intermediate
  // source file.
  @Test
  public void testEmptySourceMapsAreIgnored() throws IOException {
    // https://github.com/gulp-sourcemaps/gulp-sourcemaps/blob/91b94954/src/init/index.js#L116-L124
    String file = "input.js";
    String code = "inputCode";
    String emptySourceMap =
        """
        {
        "version":3,
        "names":[],
        "mappings":"",
        "sources":["foo/bar/baz.js"],
        "sourcesContent":["inputCode"],
        "file":"foo/bar/baz.js"
        }
        """;

    inputMaps.put(file, new SourceMapInput(SourceFile.fromCode("sourcemap", emptySourceMap)));

    RunResult compilation = compile(code, file);
    assertThat(compilation.sourceMapFileContent)
        .isEqualTo(
            """
            {
            "version":3,
            "file":"testcode",
            "lineCount":1,
            "mappings":"A,aAAAA;",
            "sources":["input.js"],
            "names":["inputCode"]
            }
            """);
  }

  @Override
  protected CompilerOptions getCompilerOptions() {
    CompilerOptions options = super.getCompilerOptions();
    if (mappings != null) {
      options.setSourceMapLocationMappings(mappings);
    }

    if (!this.inputMaps.buildOrThrow().isEmpty()) {
      options.setApplyInputSourceMaps(true);
      options.setInputSourceMaps(this.inputMaps.buildOrThrow());
    }
    return options;
  }

  @Override
  @Before
  public void setUp() {
    super.setUp();
    this.inputMaps = ImmutableMap.builder();
  }

  private void checkSourceMap2(
      String js1, String file1, String js2, String file2, String expectedMap) throws IOException {
    RunResult result = compile(js1, file1, js2, file2);
    assertThat(result.sourceMapFileContent).isEqualTo(expectedMap);
    assertThat(getSourceMap(result)).isEqualTo(result.sourceMapFileContent);
  }

  @Override
  protected Format getSourceMapFormat() {
    return Format.V3;
  }

  @Override
  protected SourceMapConsumer getSourceMapConsumer() {
    return new SourceMapConsumerV3();
  }
}
