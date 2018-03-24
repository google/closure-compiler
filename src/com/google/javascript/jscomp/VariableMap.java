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
import static java.util.Map.Entry.comparingByKey;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
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
public final class VariableMap {

  private static final char SEPARATOR = ':';

  /** Maps between original source name to new name */
  private final ImmutableBiMap<String, String> map;

  public VariableMap(Map<String, String> map) {
    this.map = ImmutableBiMap.copyOf(map);
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
    return map.inverse().get(newName);
  }

  /** Returns an immutable mapping from original names to new names. */
  public ImmutableMap<String, String> getOriginalNameToNewNameMap() {
    return ImmutableSortedMap.copyOf(map);
  }

  /** Returns an immutable mapping from new names to original names. */
  public ImmutableMap<String, String> getNewNameToOriginalNameMap() {
    return map.inverse();
  }

  /**
   * Saves the variable map to a file.
   */
  @GwtIncompatible("com.google.io.Files")
  public void save(String filename) throws IOException {
    Files.write(toBytes(), new File(filename));
  }

  /**
   * Reads the variable map from a file written via {@link #save(String)}.
   */
  @GwtIncompatible("java.io.File")
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
  @GwtIncompatible("java.io.ByteArrayOutputStream")
  public byte[] toBytes() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Writer writer = new OutputStreamWriter(baos, UTF_8);
    try {
      // The output order should be stable.
      for (Map.Entry<String, String> entry :
          ImmutableSortedSet.copyOf(comparingByKey(), map.entrySet())) {
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

  /**
   * Deserializes the variable map from a byte array returned by
   * {@link #toBytes()}.
   */
  @GwtIncompatible("com.google.common.base.Splitter.onPattern()")
  public static VariableMap fromBytes(byte[] bytes) throws ParseException {
    String string = new String(bytes, UTF_8);
    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
    int startOfLine = 0;
    while (startOfLine < string.length()) {
      int newLine = string.indexOf('\n', startOfLine);
      if (newLine == -1) {
        newLine = string.length();
      }
      int endOfLine = newLine;
      if (string.charAt(newLine - 1) == '\r') {
        newLine--;
      }
      String line = string.substring(startOfLine, newLine);
      startOfLine = endOfLine + 1; // update index for next iteration
      if (line.isEmpty()) {
        continue;
      }
      int pos = findIndexOfUnescapedChar(line, SEPARATOR);
      if (pos <= 0) {
        throw new ParseException("Bad line: " + line, 0);
      }
      map.put(
          unescape(line.substring(0, pos)),
          pos == line.length() - 1 ? "" : unescape(line.substring(pos + 1)));
    }
    return new VariableMap(map.build());
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\")
        .replace(":", "\\:")
        .replace("\n", "\\n");
  }

  private static int findIndexOfUnescapedChar(String value, char stopChar) {
    int len = value.length();
    for (int i = 0; i < len; ) {
      int stopCharIndex = value.indexOf(stopChar, i);
      if (stopCharIndex == -1) {
        return -1;
      }
      if (value.charAt(stopCharIndex - 1) != '\\') {
        // it isn't escaped, return
        return stopCharIndex;
      }
      i = stopCharIndex + 1;
    }
    return -1;
  }

  private static String unescape(String value) {
    int slashIndex = value.indexOf('\\');
    if (slashIndex == -1) {
      return value;
    }
    StringBuilder sb = new StringBuilder(value.length() - 1);
    sb.append(value, 0, slashIndex);
    int len = value.length();
    for (int i = slashIndex; i < len; i++) {
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
  ImmutableMap<String, String> toMap() {
    return map;
  }
}
