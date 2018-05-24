/*
 * Copyright 2016 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.transpile;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.escape.Escaper;
import com.google.common.net.PercentEscaper;
import java.net.URI;
import java.util.Objects;

/**
 * The result of transpiling a single file.  Includes transpiled
 * and original source, and an optional source map.
 */
public final class TranspileResult {

  private final URI path;
  private final String original;
  private final String transpiled;
  private final String sourceMap;

  public TranspileResult(URI path, String original, String transpiled, String sourceMap) {
    this.path = checkNotNull(path);
    this.original = checkNotNull(original);
    this.transpiled = checkNotNull(transpiled);
    this.sourceMap = checkNotNull(sourceMap);
  }

  public URI path() {
    return path;
  }

  // TODO(sdh): how to specify naming scheme for sourcemap and original?
  //  - if we're not embedding then we need to know how to find them.
  //  - probabaly we'll keep 'path' pointing to the original, since that's
  //    what's more important and harder to find - we can add some suffix
  //    on for the transpiled version as necessary.
  public String original() {
    return original;
  }

  public String sourceMap() {
    return sourceMap;
  }

  // TODO(sdh): might be a bit nicer for this to be a suffix rather than a whole path?
  public TranspileResult embedSourcemapUrl(String url) {
    // If there's no sourcemap, don't reference it.
    if (sourceMap.isEmpty()) {
      return this;
    }
    String embedded = transpiled + "\n//# sourceMappingURL=" + url + "\n";
    return new TranspileResult(path, original, embedded, sourceMap);
  }

  // sourcemaps must escape :, and cannot escape space as +, so we need a custom escaper.
  private static final Escaper ESCAPER = new PercentEscaper("-_.*", false /* plusForSpace */);

  public TranspileResult embedSourcemap() {
    if (sourceMap.isEmpty()) {
      return this;
    }
    String embedded =
        transpiled + "\n//# sourceMappingURL=data:," + ESCAPER.escape(sourceMap) + "\n";
    return new TranspileResult(path, original, embedded, "");
  }

  public String transpiled() {
    return transpiled;
  }

  public boolean wasTranspiled() {
    return !transpiled.equals(original);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TranspileResult
        && ((TranspileResult) other).path.equals(path)
        && ((TranspileResult) other).original.equals(original)
        && ((TranspileResult) other).transpiled.equals(transpiled)
        && ((TranspileResult) other).sourceMap.equals(sourceMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, original, transpiled, sourceMap);
  }

  @Override
  public String toString() {
    return String.format(
        "TranspileResut{path=%s, original=%s, transpiled=%s, sourceMapURL=%s}",
        path, original, transpiled, sourceMap);
  }
}
