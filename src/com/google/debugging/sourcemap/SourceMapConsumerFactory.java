/*
 * Copyright 2011 The Closure Compiler Authors.
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
 * Detect and parse the provided source map.
 * @author johnlenz@google.com (John Lenz)
 */
public class SourceMapConsumerFactory {

  /** not constructible */
  private SourceMapConsumerFactory() {}

  /**
   * @param contents The string representing the source map file contents.
   * @return The parsed source map.
   * @throws SourceMapParseException
   */
  public static SourceMapping parse(String contents)
      throws SourceMapParseException {
    SourceMapConsumer consumer = null;
    SourceMapGenerator.Format format = detectVersion(contents);
    consumer = createForVerion(detectVersion(contents));
    consumer.parse(contents);
    return consumer;
  }

  /**
   * @param contents
   * @return The best guess of the source map version.
   * @throws SourceMapParseException
   */
  private static SourceMapGenerator.Format detectVersion(String contents)
      throws SourceMapParseException {
    if (contents.startsWith("/** Begin line maps. **/")) {
      return SourceMapGenerator.Format.LEGACY;
    } else if (contents.startsWith("{")){
      return SourceMapGenerator.Format.EXPERIMENTIAL;
    } else {
      throw new SourceMapParseException("unable to detect source map format");
    }
  }

  /**
   * @return The appropriate source map object for the given source map format.
   * @throws SourceMapParseException
   */
  private static SourceMapConsumer createForVerion(
      SourceMapGenerator.Format format)
      throws SourceMapParseException {
    switch (format) {
      case LEGACY:
        return new SourceMapConsumerV1();
      case EXPERIMENTIAL:
        return new SourceMapConsumerV2();
      default:
        throw new SourceMapParseException("unsupported source map format");
    }
  }
}
