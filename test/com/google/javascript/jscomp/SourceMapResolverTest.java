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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import com.google.common.io.BaseEncoding;
import junit.framework.TestCase;

public final class SourceMapResolverTest extends TestCase {

  public void testResolveBase64Inline() throws Exception {
    String sourceMap = "{map: 'asdfasdf'}";
    String encoded = BaseEncoding.base64().encode(sourceMap.getBytes("UTF-8"));
    String url = "data:application/json;base64," + encoded;
    String code = "console.log('asdf')\n//# sourceMappingURL=" + url;
    SourceFile s =
        SourceMapResolver.extractSourceMap(
            SourceFile.fromCode("somePath/hello.js", code), url, true);
    assertEquals(sourceMap, s.getCode());
    assertEquals("somePath/hello.js.inline.map", s.getName());

    // --parse_inline_source_maps=false
    SourceFile noInline =
        SourceMapResolver.extractSourceMap(
            SourceFile.fromCode("somePath/hello.js", code), url, false);
    assertNull(noInline);
  }

  public void testResolveBase64WithCharsetInline() throws Exception {
    String sourceMap = "{map: 'asdfasdf'}";
    String encoded = BaseEncoding.base64().encode(sourceMap.getBytes("UTF-8"));
    String url = "data:application/json;charset=utf-8;base64," + encoded;
    String code = "console.log('asdf')\n//# sourceMappingURL=" + url;
    SourceFile s =
        SourceMapResolver.extractSourceMap(
            SourceFile.fromCode("somePath/hello.js", code), url, true);
    assertEquals(sourceMap, s.getCode());
    assertEquals("somePath/hello.js.inline.map", s.getName());

    // Try non supported charset.
    String dataURLWithBadCharset = "data:application/json;charset=asdf;base64," + encoded;
    String charsetCode = "console.log('asdf')\n//# sourceMappingURL=" + dataURLWithBadCharset;
    SourceFile result =
        SourceMapResolver.extractSourceMap(
            SourceFile.fromCode("somePath/hello.js", charsetCode), dataURLWithBadCharset, true);
    assertNull(result);
  }

  public void testAbsolute() {
    SourceFile jsFile = SourceFile.fromCode("somePath/hello.js", "console.log(1)");
    // We cannot reslove absolute urls.
    assertNull(SourceMapResolver.extractSourceMap(jsFile, "/asdf/asdf.js", true));
    assertNull(SourceMapResolver.extractSourceMap(jsFile, "/asdf/.././asdf.js", true));
    assertNull(SourceMapResolver.extractSourceMap(jsFile, "http://google.com/asdf/asdf.js", true));
    assertNull(SourceMapResolver.extractSourceMap(jsFile, "https://google.com/asdf/asdf.js", true));

    // We can resolve relative urls
    assertNotNull(SourceMapResolver.extractSourceMap(jsFile, "asdf.js", true));
    assertNotNull(SourceMapResolver.extractSourceMap(jsFile, "asdf/asdf.js", true));
    assertNotNull(SourceMapResolver.extractSourceMap(jsFile, "asdf/.././asdf.js", true));
    assertNotNull(SourceMapResolver.extractSourceMap(jsFile, "not/.././a/js/file.txt", true));
  }

  public void testRelativePaths() {
    assertEquals(
        "basefile.js.map",
        SourceMapResolver.getRelativePath("basefile.js", "basefile.js.map").getOriginalPath());
    assertEquals(
        "path/relative/path/basefile.js.map",
        SourceMapResolver.getRelativePath("path/basefile.js", "relative/path/basefile.js.map")
            .getOriginalPath());
    assertEquals(
        "some/longer/sourcemap.js.map",
        SourceMapResolver.getRelativePath("some/longer/path/basefile.js", "../sourcemap.js.map")
            .toString());
    assertEquals(
        "some/sourcemap.js.map",
        SourceMapResolver.getRelativePath(
                "some/longer/path/basefile.js", ".././../sourcemap.js.map")
            .getOriginalPath());
    assertEquals(
        "../basefile.js.map",
        SourceMapResolver.getRelativePath("basefile.js", "../basefile.js.map").getOriginalPath());
    assertEquals(
        "baz/foo/bar.js",
        SourceMapResolver.getRelativePath("baz/bam/qux.js", "../foo/bar.js").getOriginalPath());
  }

  public void testIntegration() {
    String url = "relative/path/to/sourcemap/hello.js.map";
    SourceFile s =
        SourceMapResolver.extractSourceMap(
            SourceFile.fromCode("somePath/hello.js", ""), url, false);
    assertEquals("somePath/relative/path/to/sourcemap/hello.js.map", s.getName());
  }
}
