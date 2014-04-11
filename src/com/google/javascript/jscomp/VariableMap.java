/*
 * Copyright 2005 The Closure Compiler Authors.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.ParseException;
import java.util.Map;


/**
 * Stores the mapping from original variable name to new variable names.
 * @see RenameVars
 */
public class VariableMap {

  /** Maps original source name to new name */
  private final ImmutableMap<String, String> map;

  /** Maps new name to source name, lazily initialized */
  private ImmutableMap<String, String> reverseMap = null;

  private static final char SEPARATOR = ':';

  VariableMap(Map<String, String> map) {
    this.map = ImmutableMap.copyOf(map);
  }

  /**
   * Given an original variable name, look up new name, may return null
   * if it's not found.
   */
  public String lookupNewName(String sourceName) {
    return map.get(sourceName);
  }

  /**
   * Given a new variable name, lookup the source name, may return null
   * if it's not found.
   */
  public String lookupSourceName(String newName) {
    initReverseMap();
    return reverseMap.get(newName);
  }

  /**
   * Initializes the reverse map.
   */
  private synchronized void initReverseMap() {
    if (reverseMap == null) {
      ImmutableMap.Builder<String, String> rm = ImmutableMap.builder();
      for (Map.Entry<String, String> entry : map.entrySet()) {
        rm.put(entry.getValue(), entry.getKey());
      }
      reverseMap = rm.build();
    }
  }

  /**
   * Returns an unmodifiable mapping from original names to new names.
   */
  public Map<String, String> getOriginalNameToNewNameMap() {
    return map;
  }

  /**
   * Returns an unmodifiable mapping from new names to original names.
   */
  public Map<String, String> getNewNameToOriginalNameMap() {
    initReverseMap();
    return reverseMap;
  }

  /**
   * Saves the variable map to a file.
   */
  public void save(String filename) throws IOException {
    Files.write(toBytes(), new File(filename));
  }

  /**
   * Reads the variable map from a file written via {@link #save(String)}.
   */
  public static VariableMap load(String filename) throws IOException {
    try {
      return fromBytes(Files.toByteArray(new File(filename)));
    } catch (ParseException e) {
      // Wrap parse exception for backwards compatibility.
      throw new IOException(e);
    }
  }

  /**
   * Serializes the variable map to a byte array.
   */
  public byte[] toBytes() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Writer writer = new OutputStreamWriter(baos, UTF_8);
    try {
      for (Map.Entry<String, String> entry : map.entrySet()) {
        writer.write(escape(entry.getKey()));
        writer.write(SEPARATOR);
        writer.write(escape(entry.getValue()));
        writer.write('\n');
      }
      writer.close();
    } catch (IOException e) {
      // Note: A ByteArrayOutputStream never throws IOException. This try/catch
      // is just here to appease the Java compiler.
      throw new RuntimeException(e);
    }
    return baos.toByteArray();
  }

  private static final Splitter LINE_SPLITTER
      = Splitter.onPattern("\\r?\\n").omitEmptyStrings();

  /**
   * Deserializes the variable map from a byte array returned by
   * {@link #toBytes()}.
   */
  public static VariableMap fromBytes(byte[] bytes) throws ParseException {
    Iterable<String> lines = LINE_SPLITTER.split(
        new String(bytes, UTF_8));

    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();

    for (String line : lines) {
      int pos = findIndexOfChar(line, SEPARATOR);
      if (pos <= 0 || pos == line.length() - 1) {
        throw new ParseException("Bad line: " + line, 0);
      }
      map.put(
          unescape(line.substring(0, pos)),
          unescape(line.substring(pos + 1)));
    }
    return new VariableMap(map.build());
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\")
        .replace(":", "\\:")
        .replace("\n", "\\n");
  }

  private static int findIndexOfChar(String value, char stopChar) {
    int len = value.length();
    for (int i = 0; i < len; i++) {
      char c = value.charAt(i);
      if (c == '\\' && ++i < len) {
        c = value.charAt(i);
      } else if (c == stopChar){
        return i;
      }
    }
    return -1;
  }

  private static String unescape(CharSequence value) {
    StringBuilder sb = new StringBuilder();
    int len = value.length();
    for (int i = 0; i < len; i++) {
      char c = value.charAt(i);
      if (c == '\\' && ++i < len) {
        c = value.charAt(i);
      }
      sb.append(c);
    }
    return sb.toString();
  }

  /**
   * Initializes the variable map from an existing map.
   * @param map The map to use from original names to generated names. It is
   *   copied and changes to the specified map will not affect the returned
   *   object.
   */
  public static VariableMap fromMap(Map<String, String> map) {
    return new VariableMap(map);
  }

  @VisibleForTesting
  Map<String, String> toMap() {
    return map;
  }
}
