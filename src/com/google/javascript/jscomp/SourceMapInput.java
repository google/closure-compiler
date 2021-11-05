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

import com.google.debugging.sourcemap.SourceMapConsumerV3;
import com.google.debugging.sourcemap.SourceMapParseException;
import java.io.IOException;
import java.io.Serializable;
import javax.annotation.Nullable;

/**
 * A lazy-loaded SourceMapConsumerV3 instance.
 */
public final class SourceMapInput implements Serializable {
  private final SourceFile sourceFile;
  // No need to serialize the consumer, it will be recreated because the serialized version will
  // have cached = false.
  private transient volatile SourceMapConsumerV3 parsedSourceMap = null;
  private transient volatile boolean cached = false;

  static final DiagnosticType SOURCEMAP_RESOLVE_FAILED =
      DiagnosticType.warning("SOURCEMAP_RESOLVE_FAILED", "Failed to resolve sourcemap at {0}: {1}");

  static final DiagnosticType SOURCEMAP_PARSE_FAILED =
      DiagnosticType.warning(
          "SOURCEMAP_PARSE_FAILED", "Failed to parse malformed sourcemap in {0}: {1}");

  public SourceMapInput(SourceFile sourceFile) {
    this.sourceFile = sourceFile;
  }

  /**
   * Gets the source map, reading from disk and parsing if necessary. Returns null if the sourcemap
   * cannot be resolved or is malformed.
   */
  public synchronized @Nullable SourceMapConsumerV3 getSourceMap(ErrorManager errorManager) {
    if (!cached) {
      // Avoid re-reading or reparsing files.
      cached = true;
      String sourceMapPath = sourceFile.getName();
      try {
        String sourceMapContents = sourceFile.getCode();
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        consumer.parse(sourceMapContents);
        parsedSourceMap = consumer;
      } catch (IOException e) {
        JSError error =
            JSError.make(SourceMapInput.SOURCEMAP_RESOLVE_FAILED, sourceMapPath, e.getMessage());
        errorManager.report(error.getDefaultLevel(), error);
      } catch (SourceMapParseException e) {
        JSError error =
            JSError.make(SourceMapInput.SOURCEMAP_PARSE_FAILED, sourceMapPath, e.getMessage());
        errorManager.report(error.getDefaultLevel(), error);
      }
    }
    return parsedSourceMap;
  }

  /**
   * Gets the original location of this sourcemap file on disk.
   */
  public String getOriginalPath() {
    return sourceFile.getName();
  }
}
