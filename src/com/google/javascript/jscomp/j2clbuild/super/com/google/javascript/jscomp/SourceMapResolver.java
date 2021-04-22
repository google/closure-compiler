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

import com.google.common.io.BaseEncoding;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

/**
 * GWT/J2CL replacement of SourceMapResolver. Utility class for resolving source maps and files
 * referenced in source maps.
 */
public class SourceMapResolver {
  private static final String BASE64_URL_PREFIX = "data:application/json;base64,";

  /**
   * For a given //# sourceMappingUrl, this locates the appropriate sourcemap on disk. This is use
   * for sourcemap merging (--apply_input_source_maps) and for error resolution.
   */
  static SourceFile extractSourceMap(
      SourceFile jsFile, String sourceMapURL, boolean parseInlineSourceMaps) {
    // Javascript version of the compiler can only deal with inline sources.
    if (sourceMapURL.startsWith(BASE64_URL_PREFIX)) {
      byte[] data =
          BaseEncoding.base64().decode(sourceMapURL.substring(BASE64_URL_PREFIX.length()));
      String source = new String(data, StandardCharsets.UTF_8);
      return SourceFile.fromCode(jsFile.getName() + ".inline.map", source, SourceKind.NON_CODE);
    }
    return null;
  }

  /**
   * While we could probably emulate this method closely to the Java version, there isn't much point
   * as this path cannot be used from JS to actually get a file from disk. Instead, we return null
   * meaning that we could not resolve the relative path.
   */
  @Nullable
  static SourceFile getRelativePath(String baseFilePath, String relativePath) {
    return null;
  }
}
