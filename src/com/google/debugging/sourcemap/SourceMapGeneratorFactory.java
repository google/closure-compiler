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
package com.google.debugging.sourcemap;

/**
 * @author johnlenz@google.com (John Lenz)
 */
public class SourceMapGeneratorFactory {

  /**
   * @return The appropriate source map object for the given source map format.
   */
  public static SourceMapGenerator getInstance() {
    return getInstance(SourceMapFormat.DEFAULT);
  }

  /**
   * @return The appropriate source map object for the given source map format.
   */
  public static SourceMapGenerator getInstance(SourceMapFormat format) {
    switch (format) {
      case V1:
        return new SourceMapGeneratorV1();
      case V2:
        return new SourceMapGeneratorV2();
      case DEFAULT:
      case V3:
        return new SourceMapGeneratorV3();
      default:
        throw new IllegalStateException("unsupported source map format");
    }
  }
}
