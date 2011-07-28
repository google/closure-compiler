/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * Class for parsing version 2 of the SourceMap format, as produced by the
 * Closure Compiler, etc.
 * @author johnlenz@google.com (John Lenz)
 * @author jschorr@google.com (Joseph Schorr)
 */
public class SourceMapConsumerV2 implements SourceMapConsumer {
  /**
   * The character map for each line. If a line does not have an entry,
   * then it has not yet been decoded.
   */
  private Map<Integer, List<Integer>> characterMap = null;

  /**
   * The undecoded line maps. Will be accessed to decode lines as needed.
   */
  private JSONArray lineMaps = null;

  /**
   * Map of Mapping IDs to the actual mapping object.
   */
  private List<OriginalMapping> mappings;

  public SourceMapConsumerV2() {}

  /**
   * Parses the given contents containing a source map.
   */
  @Override
  public void parse(String contents) throws SourceMapParseException {
    try {
      JSONObject sourceMapRoot = new JSONObject(contents);
      parse(sourceMapRoot);
    } catch (JSONException ex) {
      throw new SourceMapParseException("JSON parse exception: " + ex);
    }
  }

  /**
   * Parses the given contents containing a source map.
   */
  public void parse(JSONObject sourceMapRoot) throws SourceMapParseException {
    try {
      parseInternal(sourceMapRoot);
    } catch (JSONException ex) {
      throw new SourceMapParseException("JSON parse exception: " + ex);
    }
  }

  /**
   * Parses the given contents as version 2 of a SourceMap.
   */
  private void parseInternal(JSONObject sourceMapRoot)
      throws JSONException, SourceMapParseException {

    // Check basic assertions about the format.
    int version = sourceMapRoot.getInt("version");
    if (version != 2) {
      throw new SourceMapParseException("Unknown version: " + version);
    }

    String file = sourceMapRoot.getString("file");
    if (file.isEmpty()) {
      throw new SourceMapParseException("File entry is missing or empty");
    }

    int lineCount = sourceMapRoot.getInt("lineCount");
    lineMaps = sourceMapRoot.getJSONArray("lineMaps");
    if (lineCount != lineMaps.length()) {
      throw new SourceMapParseException(
          "lineMaps length does not match lineCount");
    }

    // Build an empty character map. The character map will be filled in as
    // lines are requested.
    characterMap = Maps.newHashMap();

    JSONArray sources = sourceMapRoot.getJSONArray("sources");
    JSONArray names = sourceMapRoot.has("names")
        ? sourceMapRoot.getJSONArray("names") : null;

    // Create each of the OriginalMappings.
    JSONArray jsonMappings = sourceMapRoot.getJSONArray("mappings");
    mappings = Lists.newArrayListWithCapacity(lineCount);

    for (int i = 0; i < jsonMappings.length(); i++) {
      JSONArray entry = jsonMappings.getJSONArray(i);

      // The name can be accessed in two ways: Directly (i.e. a string) or
      // indirectly (i.e. an index into the name map).
      String name = entry.optString(3, "");
      if (names != null) {
        try {
          int nameIndex = entry.getInt(3);
          name = names.getString(nameIndex);
        } catch (JSONException e) {
        }
      }

      // Build the new OriginalMapping entry.
      String sourceFile = sources.getString(entry.getInt(0));
      int lineNumber = entry.getInt(1);
      int column = entry.getInt(2);

      OriginalMapping.Builder builder = OriginalMapping.newBuilder()
          .setOriginalFile(sourceFile)
          .setLineNumber(lineNumber)
          .setColumnPosition(column)
          .setIdentifier(name);
      mappings.add(builder.build());
    }
  }

  @Override
  public OriginalMapping getMappingForLine(int lineNumber, int columnIndex) {
    // Normalize the line and column numbers to 0.
    lineNumber--;
    columnIndex--;

    if (lineNumber >= lineMaps.length()) {
      return null;
    }

    Preconditions.checkState(lineNumber >= 0, "Line number must be >= 0");
    Preconditions.checkState(columnIndex >= 0, "Column index must be >= 0");

    if (!characterMap.containsKey(lineNumber)) {
      // Parse the line map entry and place it into the character map.
      try {
        characterMap.put(lineNumber,
            SourceMapLineDecoder.decodeLine(lineMaps.getString(lineNumber)));
      } catch (JSONException jse) {
        throw new IllegalStateException(
            "JSON exception when retrieving line map", jse);
      }
    }

    List<Integer> map = characterMap.get(lineNumber);
    if (map == null || map.size() <= columnIndex) {
      return null;
    }

    int index = map.get(columnIndex);
    if (index == -1) {
      return null;
    }
    Preconditions.checkState(index < mappings.size(),
        "Invalid mapping reference");
    return mappings.get(index);
  }
}
