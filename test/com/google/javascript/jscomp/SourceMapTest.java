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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.debugging.sourcemap.SourceMapConsumer;
import com.google.debugging.sourcemap.SourceMapConsumerV3;
import com.google.debugging.sourcemap.SourceMapTestCase;
import com.google.javascript.jscomp.SourceMap.Format;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author johnlenz@google.com (John Lenz) */

@RunWith(JUnit4.class)
public final class SourceMapTest extends SourceMapTestCase {

  public SourceMapTest() {}

  private List<SourceMap.LocationMapping> mappings;

  @Test
  public void testPrefixReplacement1() throws IOException {
    // mapping can be used to remove a prefix
    mappings = ImmutableList.of(new SourceMap.PrefixLocationMapping("pre/", ""));
    checkSourceMap2(
        "alert(1);",
        Compiler.joinPathParts("pre", "file1"),
        "alert(2);",
        Compiler.joinPathParts("pre", "file2"),
        Joiner.on('\n')
            .join(
                "{",
                "\"version\":3,",
                "\"file\":\"testcode\",",
                "\"lineCount\":1,",
                "\"mappings\":\"AAAAA,KAAA,CAAM,CAAN,C,CCAAA,KAAA,CAAM,CAAN;\",",
                "\"sources\":[\"file1\",\"file2\"],",
                "\"names\":[\"alert\"]",
                "}\n"));
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
        Joiner.on('\n')
            .join(
                "{",
                "\"version\":3,",
                "\"file\":\"testcode\",",
                "\"lineCount\":1,",
                "\"mappings\":\"AAAAA,KAAA,CAAM,CAAN,C,CCAAA,KAAA,CAAM,CAAN;\",",
                "\"sources\":[\"src1\",\"src2\"],",
                "\"names\":[\"alert\"]",
                "}\n"));
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
        Joiner.on('\n')
            .join(
                "{",
                "\"version\":3,",
                "\"file\":\"testcode\",",
                "\"lineCount\":1,",
                "\"mappings\":\"AAAAA,KAAA,CAAM,CAAN,C,CCAAA,KAAA,CAAM,CAAN;\",",
                "\"sources\":[\"x\",\"y\"],",
                "\"names\":[\"alert\"]",
                "}\n"));
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
        Joiner.on('\n')
            .join(
                "{",
                "\"version\":3,",
                "\"file\":\"testcode\",",
                "\"lineCount\":1,",
                "\"mappings\":\"AAAAA,KAAA,CAAM,CAAN,C,CCAAA,KAAA,CAAM,CAAN;\",",
                "\"sources\":[\"x\",\"y2\"],",
                "\"names\":[\"alert\"]",
                "}\n"));
  }

  @Test
  public void testLambdaReplacement() throws IOException {
    mappings = ImmutableList.of((location) -> "mapped/" + location);
    checkSourceMap2(
        "alert(1);",
        "file1",
        "alert(2);",
        "file2",
        Joiner.on('\n')
            .join(
                "{",
                "\"version\":3,",
                "\"file\":\"mapped/testcode\",",
                "\"lineCount\":1,",
                "\"mappings\":\"AAAAA,KAAA,CAAM,CAAN,C,CCAAA,KAAA,CAAM,CAAN;\",",
                "\"sources\":[\"mapped/file1\",\"mapped/file2\"],",
                "\"names\":[\"alert\"]",
                "}\n"));
  }

  @Override
  protected CompilerOptions getCompilerOptions() {
    CompilerOptions options = super.getCompilerOptions();
    if (mappings != null) {
      options.sourceMapLocationMappings = mappings;
    }
    return options;
  }

  @Override
  @Before
  public void setUp() {
    super.setUp();
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
