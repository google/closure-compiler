/*
 * Copyright 2017 The Closure Compiler Authors.
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.BaseEncoding;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SourceMapResolverTest {

  @Test
  public void testResolveBase64Inline() throws Exception {
    String sourceMap = "{map: 'asdfasdf'}";
    String encoded = BaseEncoding.base64().encode(sourceMap.getBytes(UTF_8));
    String url = "data:application/json;base64," + encoded;
    String code = "console.log('asdf')\n//# sourceMappingURL=" + url;
    SourceFile s =
        SourceMapResolver.extractSourceMap(
            SourceFile.fromCode("somePath/hello.js", code), url, true);
    assertThat(s.getCode()).isEqualTo(sourceMap);
    assertThat(s.getName()).isEqualTo("somePath/hello.js.inline.map");

    // --parse_inline_source_maps=false
    SourceFile noInline =
        SourceMapResolver.extractSourceMap(
            SourceFile.fromCode("somePath/hello.js", code), url, false);
    assertThat(noInline).isNull();
  }

  @Test
  public void testResolveBase64WithCharsetInline() throws Exception {
    String sourceMap = "{map: 'asdfasdf'}";
    String encoded = BaseEncoding.base64().encode(sourceMap.getBytes(UTF_8));
    String url = "data:application/json;charset=utf-8;base64," + encoded;
    String code = "console.log('asdf')\n//# sourceMappingURL=" + url;
    SourceFile s =
        SourceMapResolver.extractSourceMap(
            SourceFile.fromCode("somePath/hello.js", code), url, true);
    assertThat(s.getCode()).isEqualTo(sourceMap);
    assertThat(s.getName()).isEqualTo("somePath/hello.js.inline.map");

    // Try non supported charset.
    String dataURLWithBadCharset = "data:application/json;charset=asdf;base64," + encoded;
    String charsetCode = "console.log('asdf')\n//# sourceMappingURL=" + dataURLWithBadCharset;
    SourceFile result =
        SourceMapResolver.extractSourceMap(
            SourceFile.fromCode("somePath/hello.js", charsetCode), dataURLWithBadCharset, true);
    assertThat(result).isNull();
  }

  @Test
  public void testAbsolute() {
    SourceFile jsFile = SourceFile.fromCode("somePath/hello.js", "console.log(1)");
    // We cannot reslove absolute urls.
    assertThat(SourceMapResolver.extractSourceMap(jsFile, "/asdf/asdf.js", true)).isNull();
    assertThat(SourceMapResolver.extractSourceMap(jsFile, "/asdf/.././asdf.js", true)).isNull();
    assertThat(SourceMapResolver.extractSourceMap(jsFile, "http://google.com/asdf/asdf.js", true))
        .isNull();
    assertThat(SourceMapResolver.extractSourceMap(jsFile, "https://google.com/asdf/asdf.js", true))
        .isNull();

    // We can resolve relative urls
    assertThat(SourceMapResolver.extractSourceMap(jsFile, "asdf.js", true)).isNotNull();
    assertThat(SourceMapResolver.extractSourceMap(jsFile, "asdf/asdf.js", true)).isNotNull();
    assertThat(SourceMapResolver.extractSourceMap(jsFile, "asdf/.././asdf.js", true)).isNotNull();
    assertThat(SourceMapResolver.extractSourceMap(jsFile, "not/.././a/js/file.txt", true))
        .isNotNull();
  }

  @Test
  public void testRelativePaths() {
    assertThat(
            SourceMapResolver.getRelativePath("basefile.js", "basefile.js.map").getOriginalPath())
        .isEqualTo("basefile.js.map");
    assertThat(
            SourceMapResolver.getRelativePath("path/basefile.js", "relative/path/basefile.js.map")
                .getOriginalPath())
        .isEqualTo("path/relative/path/basefile.js.map");
    assertThat(
            SourceMapResolver.getRelativePath("some/longer/path/basefile.js", "../sourcemap.js.map")
                .toString())
        .isEqualTo("some/longer/sourcemap.js.map");
    assertThat(
            SourceMapResolver.getRelativePath(
                    "some/longer/path/basefile.js", ".././../sourcemap.js.map")
                .getOriginalPath())
        .isEqualTo("some/sourcemap.js.map");
    assertThat(
            SourceMapResolver.getRelativePath("basefile.js", "../basefile.js.map")
                .getOriginalPath())
        .isEqualTo("../basefile.js.map");
    assertThat(
            SourceMapResolver.getRelativePath("baz/bam/qux.js", "../foo/bar.js").getOriginalPath())
        .isEqualTo("baz/foo/bar.js");
  }

  @Test
  public void testIntegration() {
    String url = "relative/path/to/sourcemap/hello.js.map";
    SourceFile s =
        SourceMapResolver.extractSourceMap(
            SourceFile.fromCode("somePath/hello.js", ""), url, false);
    assertThat(s.getName()).isEqualTo("somePath/relative/path/to/sourcemap/hello.js.map");
  }
}
