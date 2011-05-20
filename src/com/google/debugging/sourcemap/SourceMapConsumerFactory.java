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

import org.json.JSONException;
import org.json.JSONObject;

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
     return parse(contents, null);
  }

  /**
   * @param contents The string representing the source map file contents.
   * @param supplier A supplier for any referenced maps.
   * @return The parsed source map.
   * @throws SourceMapParseException
   */
  public static SourceMapping parse(String contents, SourceMapSupplier supplier)
      throws SourceMapParseException {
    // Version 1, starts with a magic string
    if (contents.startsWith("/** Begin line maps. **/")) {
      SourceMapConsumerV1 consumer =  new SourceMapConsumerV1();
      consumer.parse(contents);
      return consumer;
    } else if (contents.startsWith("{")){
      try {
        // Revision 2 and 3, are JSON Objects
        JSONObject sourceMapRoot = new JSONObject(contents);
        // Check basic assertions about the format.
        int version = sourceMapRoot.getInt("version");
        switch (version) {
          case 2: {
            SourceMapConsumerV2 consumer =  new SourceMapConsumerV2();
            consumer.parse(sourceMapRoot);
            return consumer;
          }
          case 3: {
            SourceMapConsumerV3 consumer =  new SourceMapConsumerV3();
            consumer.parse(sourceMapRoot, supplier);
            return consumer;
          }
          default:
            throw new SourceMapParseException(
                "Unknown source map version:" + version);
        }
      } catch (JSONException ex) {
        throw new SourceMapParseException("JSON parse exception: " + ex);
      }
    }

    throw new SourceMapParseException("unable to detect source map format");
  }
}
