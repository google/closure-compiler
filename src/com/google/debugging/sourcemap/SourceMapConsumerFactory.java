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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

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
      throw new SourceMapParseException(
          "This appears to be a V1 SourceMap, which is not supported.");
    } else if (contents.startsWith("{")){
      try {
        // Revision 2 and 3, are JSON Objects
        JsonObject sourceMapRoot = new Gson().fromJson(contents, JsonObject.class);
        // Check basic assertions about the format.
        int version = sourceMapRoot.get("version").getAsInt();
        switch (version) {
          case 3: {
            SourceMapConsumerV3 consumer =  new SourceMapConsumerV3();
            consumer.parse(sourceMapRoot, supplier);
            return consumer;
          }
          default:
            throw new SourceMapParseException(
                "Unknown source map version:" + version);
        }
      } catch (JsonParseException ex) {
        throw new SourceMapParseException("JSON parse exception: " + ex);
      }
    }

    throw new SourceMapParseException("unable to detect source map format");
  }
}
