/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A utility class for generating and parsing id mappings held by {@link ReplaceIdGenerators}.
 */
public final class IdMappingUtil {

  @VisibleForTesting
  static final char NEW_LINE = '\n';

  private static final Splitter LINE_SPLITTER = Splitter.on(NEW_LINE).omitEmptyStrings();

  // Prevent instantiation.
  private IdMappingUtil() {}

  /**
   * @return The serialize map of generators and their ids and their
   *     replacements.
   */
  static String generateSerializedIdMappings(Map<String, Map<String, String>> idGeneratorMaps) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Map<String, String>> replacements : idGeneratorMaps.entrySet()) {
      if (!replacements.getValue().isEmpty()) {
        sb.append('[')
            .append(replacements.getKey())
            .append(']')
            .append(NEW_LINE)
            .append(NEW_LINE);

        for (Map.Entry<String, String> replacement :
            replacements.getValue().entrySet()) {
          sb.append(replacement.getKey())
              .append(':')
              .append(replacement.getValue())
              .append(NEW_LINE);
        }
        sb.append(NEW_LINE);
      }
    }
    return sb.toString();
  }

  /**
   * The expected format looks like this:
   *
   * <p>[generatorName1]
   * someId1:someFile:theLine:theColumn
   * ...
   *
   * <p>[[generatorName2]
   * someId2:someFile:theLine:theColumn]
   * ...
   *
   * <p>The returned data is grouped by generator name (the map key). The inner map provides
   * mappings from id to content (file, line and column info). In a glimpse, the structure is
   * {@code Map<generator name, BiMap<id, value>>}.
   *
   * <p>@throws IllegalArgumentException malformed input where there it 1) has duplicate generator
   *     name, or 2) the line has no ':' for id and its content.
   */
  public static Map<String, BiMap<String, String>> parseSerializedIdMappings(String idMappings) {
    if (Strings.isNullOrEmpty(idMappings)) {
      return Collections.emptyMap();
    }

    Map<String, BiMap<String, String>> resultMap = new HashMap<>();
    BiMap<String, String> currentSectionMap = null;

    int lineIndex = 0;
    for (String line : LINE_SPLITTER.split(idMappings)) {
      lineIndex++;
      if (line.isEmpty()) {
        continue;
      }
      if (line.charAt(0) == '[') {
        String currentSection = line.substring(1, line.length() - 1);
        currentSectionMap = resultMap.get(currentSection);
        if (currentSectionMap == null) {
          currentSectionMap = HashBiMap.create();
          resultMap.put(currentSection, currentSectionMap);
        } else {
          throw new IllegalArgumentException(
              SimpleFormat.format("Cannot parse id map: %s\n Line: $s, lineIndex: %s",
                  idMappings, line, lineIndex));
        }
      } else {
        int split = line.indexOf(':');
        if (split != -1) {
          String name = line.substring(0, split);
          String location = line.substring(split + 1, line.length());
          currentSectionMap.put(name, location);
        } else {
          throw new IllegalArgumentException(
              SimpleFormat.format("Cannot parse id map: %s\n Line: $s, lineIndex: %s",
                  idMappings, line, lineIndex));
        }
      }
    }
    return resultMap;
  }
}
