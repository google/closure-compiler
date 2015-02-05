/*
 * Copyright 2014 The Closure Compiler Authors.
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A lazy-loaded SourceMapConsumerV3 instance.
 */
public class SourceMapInput {

  private static final Logger logger =
      Logger.getLogger(SourceMapInput.class.getName());

  private SourceFile sourceFile;
  private volatile SourceMapConsumerV3 parsedSourceMap = null;

  public SourceMapInput(SourceFile sourceFile) {
    this.sourceFile = sourceFile;
  }

  /**
   * Gets the source map, reading from disk and parsing if necessary.
   */
  public SourceMapConsumerV3 getSourceMap() {
    if (parsedSourceMap == null) {
      synchronized (this) {
        if (parsedSourceMap == null) {
          parsedSourceMap = new SourceMapConsumerV3();
          try {
            parsedSourceMap.parse(sourceFile.getCode());
          } catch (IOException | SourceMapParseException parseFailure) {
            logger.log(
                Level.WARNING, "Failed to parse sourcemap", parseFailure);
          }
        }
      }
    }

    return parsedSourceMap;
  }

  /**
   * Gets the original location of this sourcemap file on disk.
   */
  public String getOriginalPath() {
    return sourceFile.getOriginalPath();
  }
}
