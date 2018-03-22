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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import javax.annotation.Nullable;

/** Utility class for resolving source maps and files referenced in source maps. */
@GwtIncompatible("Accesses the file system")
public class SourceMapResolver {
  private static final String BASE64_URL_PREFIX = "data:";
  private static final String BASE64_START = "base64,";

  // For now only accept UTF-8. We could use the actual charset information in the future.
  private static final ImmutableSet<String> ACCEPTED_MEDIA_TYPES = ImmutableSet.of(
      "application/json;charset=utf-8;",
      // default is UTF-8 if unspecified.
      "application/json;");

  /**
   * For a given //# sourceMappingUrl, this locates the appropriate sourcemap on disk. This is use
   * for sourcemap merging (--apply_input_source_maps) and for error resolution.
   *
   * @param parseInlineSourceMaps Whether to parse Base64 encoded source maps.
   */
  static SourceFile extractSourceMap(
      SourceFile jsFile, String sourceMapURL, boolean parseInlineSourceMaps) {
    if (parseInlineSourceMaps && sourceMapURL.startsWith(BASE64_URL_PREFIX)) {
      String extractedString = extractBase64String(sourceMapURL);
      if (extractedString != null) {
        return SourceFile.fromCode(jsFile.getName() + ".inline.map", extractedString);
      }
      return null;
    }
    // TODO(tdeegan): Handle absolute urls here.  The compiler needs to come up with a scheme for
    // properly resolving absolute urls from http:// or the root /some/abs/path/... See b/62544959.
    if (isAbsolute(sourceMapURL)) {
      return null;
    }
    // If not absolute, its relative.
    // TODO(tdeegan): Handle urls relative to //# sourceURL. See the sourcemap spec.
    return getRelativePath(jsFile.getName(), sourceMapURL);
  }

  /**
   * Based on https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/Data_URIs
   * @param url
   * @return String or null.
   */
  @Nullable
  private static String extractBase64String(String url) {
    if (url.startsWith(BASE64_URL_PREFIX) && url.contains(BASE64_START)) {
      int base64StartIndex = url.indexOf(BASE64_START);
      String mediaType = url.substring(BASE64_URL_PREFIX.length(), base64StartIndex);
      if (ACCEPTED_MEDIA_TYPES.contains(mediaType)) {
        byte[] data = BaseEncoding.base64().decode(
                url.substring(base64StartIndex + BASE64_START.length()));
        return new String(data, UTF_8);
      }
    }
    return null;
  }

  @VisibleForTesting
  private static boolean isAbsolute(String url) {
    try {
      return new URI(url).isAbsolute() || url.startsWith("/");
    } catch (URISyntaxException e) {
      throw new RuntimeException("Sourcemap url was invalid: " + url, e);
    }
  }

  /**
   * Returns the relative path, resolved relative to the base path, where the base path is
   * interpreted as a filename rather than a directory. E.g.: getRelativeTo("../foo/bar.js",
   * "baz/bam/qux.js") --> "baz/foo/bar.js"
   */
  @Nullable
  static SourceFile getRelativePath(String baseFilePath, String relativePath) {
    return SourceFile.fromPath(
        FileSystems.getDefault().getPath(baseFilePath).resolveSibling(relativePath).normalize(),
        UTF_8);
  }
}
