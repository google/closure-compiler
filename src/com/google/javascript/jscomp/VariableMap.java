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

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

import java.io.*;
import java.text.*;
import java.util.*;

/**
 * Stores the mapping from original variable name to new variable names.
 * @see RenameVars
 */
public class VariableMap {

  /** Maps original source name to new name */
  private final Map<String, String> map;

  /** Maps new name to source name, lazily initialized */
  private Map<String, String> reverseMap = null;

  private static final char SEPARATOR = ':';

  VariableMap(Map<String, String> map) {
    this.map = Collections.unmodifiableMap(map);
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
    if (reverseMap == null) {
      initReverseMap();
    }
    return reverseMap.get(newName);
  }

  /**
   * Initializes the reverse map.
   */
  private synchronized void initReverseMap() {
    if (reverseMap == null) {
      Map<String, String> rm = new HashMap<String, String>();
      for (Map.Entry<String, String> entry : map.entrySet()) {
        rm.put(entry.getValue(), entry.getKey());
      }
      reverseMap = Collections.unmodifiableMap(rm);
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
    if (reverseMap == null) {
      initReverseMap();
    }
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
    Writer writer = new OutputStreamWriter(baos, Charsets.UTF_8);
    try {
      for (Map.Entry<String, String> entry : map.entrySet()) {
        writer.write(entry.getKey());
        writer.write(SEPARATOR);
        writer.write(entry.getValue());
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
  public static VariableMap fromBytes(byte[] bytes) throws ParseException {
    Iterable<String> lines;
    try {
      lines = CharStreams.readLines(CharStreams.newReaderSupplier(
          ByteStreams.newInputStreamSupplier(bytes), Charsets.UTF_8));
    } catch (IOException e) {
      // Note: An IOException is never thrown while reading from a byte array.
      // This try/catch is just here to appease the Java compiler.
      throw new RuntimeException(e);
    }

    Map<String, String> map = new HashMap<String, String>();

    for (String line : lines) {
      int pos = line.lastIndexOf(SEPARATOR);
      if (pos <= 0 || pos == line.length() - 1) {
        throw new ParseException("Bad line: " + line, 0);
      }
      map.put(line.substring(0, pos), line.substring(pos + 1));
    }
    return new VariableMap(map);
  }

  /**
   * Initializes the variable map from an existing map.
   * @param map The map to use from original names to generated names. It is
   *   copied and changes to the specified map will not affect the returned
   *   object.
   */
  public static VariableMap fromMap(Map<String, String> map) {
    return new VariableMap(Maps.newHashMap(map));
  }
}
