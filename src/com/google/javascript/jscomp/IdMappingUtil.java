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
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** A utility class for generating and parsing id mappings held by {@link ReplaceIdGenerators}. */
public final class IdMappingUtil {

  @VisibleForTesting static final char NEW_LINE = '\n';

  // Prevent instantiation.
  private IdMappingUtil() {}

  /**
   * @return The serialize map of generators and their ids and their replacements.
   */
  static String generateSerializedIdMappings(Map<String, Map<String, String>> idGeneratorMaps) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Map<String, String>> replacements : idGeneratorMaps.entrySet()) {
      if (!replacements.getValue().isEmpty()) {
        sb.append('[').append(replacements.getKey()).append(']').append(NEW_LINE).append(NEW_LINE);

        for (Map.Entry<String, String> replacement : replacements.getValue().entrySet()) {
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

  /** A stateful pull parser for reading id mapping sections and entries line by line. */
  public static class MappingReader {
    private final BufferedReader reader;
    private String currentSection = null;
    private String currentKey = null;
    private String currentValue = null;
    private int lineIndex = 0;

    public MappingReader(BufferedReader reader) {
      this.reader = reader;
    }

    public boolean next() throws IOException {
      String line;
      while ((line = reader.readLine()) != null) {
        lineIndex++;
        if (line.isEmpty()) {
          continue;
        }
        if (line.charAt(0) == '[') {
          currentSection = line.substring(1, line.length() - 1);
          currentKey = null;
          currentValue = null;
          return true;
        } else {
          int split = line.indexOf(':');
          if (split != -1) {
            currentKey = line.substring(0, split);
            currentValue = line.substring(split + 1);
            return true;
          } else {
            throw new IllegalArgumentException(
                String.format("Cannot parse id map.\n Line: %s, lineIndex: %d", line, lineIndex));
          }
        }
      }
      return false;
    }

    public boolean isSection() {
      return currentKey == null;
    }

    public String getSection() {
      return currentSection;
    }

    public String getKey() {
      return currentKey;
    }

    public String getValue() {
      return currentValue;
    }

    public int getLineIndex() {
      return lineIndex;
    }
  }

  public static ImmutableMap<String, String> parseSectionAsStream(
      InputStream stream, String sectionFilter) throws IOException {
    ImmutableMap.Builder<String, String> mapBuilder = ImmutableMap.builder();

    BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    MappingReader mr = new MappingReader(br);
    boolean inSection = false;

    while (mr.next()) {
      if (mr.isSection()) {
        if (inSection) {
          break; // Next section found, stop
        }
        if (mr.getSection().equals(sectionFilter)) {
          inSection = true;
        }
      } else if (inSection) {
        mapBuilder.put(mr.getKey(), mr.getValue());
      }
    }
    return mapBuilder.buildKeepingLast();
  }

  public static Map<String, BiMap<String, String>> parseSerializedIdMappings(BufferedReader br)
      throws IOException {
    Map<String, BiMap<String, String>> resultMap = new LinkedHashMap<>();
    MappingReader mr = new MappingReader(br);
    BiMap<String, String> currentSectionMap = null;
    Set<String> sectionNames = new LinkedHashSet<>();

    while (mr.next()) {
      if (mr.isSection()) {
        String sectionName = mr.getSection();
        if (!sectionNames.add(sectionName)) {
          throw new IllegalArgumentException(
              String.format(
                  "Cannot parse id map: Duplicate section %s\n lineIndex: %d",
                  sectionName, mr.getLineIndex()));
        }
        currentSectionMap = HashBiMap.create();
        resultMap.put(sectionName, currentSectionMap);
      } else {
        if (currentSectionMap == null) {
          throw new IllegalArgumentException("Mapping entry outside of section");
        }
        currentSectionMap.put(mr.getKey(), mr.getValue()); // throws if duplicate values
      }
    }
    return resultMap;
  }

  public static Map<String, BiMap<String, String>> parseSerializedIdMappings(String idMappings) {
    if (Strings.isNullOrEmpty(idMappings)) {
      return ImmutableMap.of();
    }
    try (BufferedReader br = new BufferedReader(new StringReader(idMappings))) {
      return parseSerializedIdMappings(br);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to parse id mappings", e);
    }
  }
}
