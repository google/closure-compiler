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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.debugging.sourcemap.Base64VLQ.CharIterator;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping.Builder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Class for parsing version 3 of the SourceMap format, as produced by the
 * Closure Compiler, etc.
 * http://code.google.com/p/closure-compiler/wiki/SourceMaps
 * @author johnlenz@google.com (John Lenz)
 */
public class SourceMapConsumerV3 implements SourceMapConsumer,
    SourceMappingReversable {
  static final int UNMAPPED = -1;

  private String[] sources;
  private String[] names;
  private int lineCount;
  // Slots in the lines list will be null if the line does not have any entries.
  private ArrayList<ArrayList<Entry>> lines = null;
  /** originalFile path ==> original line ==> target mappings */
  private Map<String, Map<Integer, Collection<OriginalMapping>>>
      reverseSourceMapping;

  public SourceMapConsumerV3() {

  }

  static class DefaultSourceMapSupplier implements SourceMapSupplier {
    @Override
    public String getSourceMap(String url) {
      return null;
    }
  }

  /**
   * Parses the given contents containing a source map.
   */
  @Override
  public void parse(String contents) throws SourceMapParseException {
    parse(contents, null);
  }

  /**
   * Parses the given contents containing a source map.
   */
  public void parse(String contents, SourceMapSupplier sectionSupplier)
      throws SourceMapParseException {
    try {
      JSONObject sourceMapRoot = new JSONObject(contents);
      parse(sourceMapRoot, sectionSupplier);
    } catch (JSONException ex) {
      throw new SourceMapParseException("JSON parse exception: " + ex);
    }
  }

  /**
   * Parses the given contents containing a source map.
   */
  public void parse(JSONObject sourceMapRoot) throws SourceMapParseException {
    parse(sourceMapRoot, null);
  }

  /**
   * Parses the given contents containing a source map.
   */
  public void parse(JSONObject sourceMapRoot, SourceMapSupplier sectionSupplier)
      throws SourceMapParseException {
    try {
      // Check basic assertions about the format.
      int version = sourceMapRoot.getInt("version");
      if (version != 3) {
        throw new SourceMapParseException("Unknown version: " + version);
      }

      String file = sourceMapRoot.getString("file");
      if (file.isEmpty()) {
        throw new SourceMapParseException("File entry is missing or empty");
      }

      if (sourceMapRoot.has("sections")) {
        // Looks like a index map, try to parse it that way.
        parseMetaMap(sourceMapRoot, sectionSupplier);
        return;
      }

      lineCount = sourceMapRoot.getInt("lineCount");
      String lineMap = sourceMapRoot.getString("mappings");

      sources = getJavaStringArray(sourceMapRoot.getJSONArray("sources"));
      names = getJavaStringArray(sourceMapRoot.getJSONArray("names"));

      lines = Lists.newArrayListWithCapacity(lineCount);

      new MappingBuilder(lineMap).build();
    } catch (JSONException ex) {
      throw new SourceMapParseException("JSON parse exception: " + ex);
    }
  }

  /**
   * @param sourceMapRoot
   * @throws SourceMapParseException
   */
  private void parseMetaMap(
      JSONObject sourceMapRoot, SourceMapSupplier sectionSupplier)
      throws SourceMapParseException {
    if (sectionSupplier == null) {
      sectionSupplier = new DefaultSourceMapSupplier();
    }

    try {
      // Check basic assertions about the format.
      int version = sourceMapRoot.getInt("version");
      if (version != 3) {
        throw new SourceMapParseException("Unknown version: " + version);
      }

      String file = sourceMapRoot.getString("file");
      if (file.isEmpty()) {
        throw new SourceMapParseException("File entry is missing or empty");
      }

      if (sourceMapRoot.has("lineCount")
          || sourceMapRoot.has("mappings")
          || sourceMapRoot.has("sources")
          || sourceMapRoot.has("names")) {
        throw new SourceMapParseException("Invalid map format");
      }

      SourceMapGeneratorV3 generator = new SourceMapGeneratorV3();
      JSONArray sections = sourceMapRoot.getJSONArray("sections");
      for (int i = 0, count = sections.length(); i < count; i++) {
        JSONObject section = sections.getJSONObject(i);
        if (section.has("map") && section.has("url")) {
          throw new SourceMapParseException(
              "Invalid map format: section may not have both 'map' and 'url'");
        }
        JSONObject offset = section.getJSONObject("offset");
        int line = offset.getInt("line");
        int column = offset.getInt("column");
        String mapSectionContents;
        if (section.has("url")) {
          String url = section.getString("url");
          mapSectionContents = sectionSupplier.getSourceMap(url);
          if (mapSectionContents == null) {
            throw new SourceMapParseException("Unable to retrieve: " + url);
          }
        } else if (section.has("map")) {
          mapSectionContents = section.getString("map");
        } else {
          throw new SourceMapParseException(
              "Invalid map format: section must have either 'map' or 'url'");
        }
        generator.mergeMapSection(line, column, mapSectionContents);
      }

      StringBuilder sb = new StringBuilder();
      try {
        generator.appendTo(sb, file);
      } catch (IOException e) {
        // Can't happen.
        throw new RuntimeException(e);
      }

      parse(sb.toString());
    } catch (IOException ex) {
      throw new SourceMapParseException("IO exception: " + ex);
    } catch (JSONException ex) {
      throw new SourceMapParseException("JSON parse exception: " + ex);
    }
  }

  @Override
  public OriginalMapping getMappingForLine(int lineNumber, int column) {
    // Normalize the line and column numbers to 0.
    lineNumber--;
    column--;

    if (lineNumber < 0 || lineNumber >= lines.size()) {
      return null;
    }

    Preconditions.checkState(lineNumber >= 0);
    Preconditions.checkState(column >= 0);


    // If the line is empty return the previous mapping.
    if (lines.get(lineNumber) == null) {
      return getPreviousMapping(lineNumber);
    }

    ArrayList<Entry> entries = lines.get(lineNumber);
    // No empty lists.
    Preconditions.checkState(entries.size() > 0);
    if (entries.get(0).getGeneratedColumn() > column) {
      return getPreviousMapping(lineNumber);
    }

    int index = search(entries, column, 0, entries.size() - 1);
    Preconditions.checkState(index >= 0, "unexpected:%s", index);
    return getOriginalMappingForEntry(entries.get(index));
  }

  @Override
  public Collection<String> getOriginalSources() {
    return Arrays.asList(sources);
  }

  @Override
  public Collection<OriginalMapping> getReverseMapping(String originalFile,
      int line, int column) {
    // TODO(user): This implementation currently does not make use of the column
    // parameter.

    // Synchronization needs to be handled by callers.
    if (reverseSourceMapping == null) {
      createReverseMapping();
    }

    Map<Integer, Collection<OriginalMapping>> sourceLineToCollectionMap =
        reverseSourceMapping.get(originalFile);

    if (sourceLineToCollectionMap == null) {
      return Collections.emptyList();
    } else {
      Collection<OriginalMapping> mappings =
          sourceLineToCollectionMap.get(line);

      if (mappings == null) {
        return Collections.emptyList();
      } else {
        return mappings;
      }
    }
  }

  private String[] getJavaStringArray(JSONArray array) throws JSONException {
    int len = array.length();
    String[] result = new String[len];
    for(int i = 0; i < len; i++) {
      result[i] = array.getString(i);
    }
    return result;
  }

  private class MappingBuilder {
    private static final int MAX_ENTRY_VALUES = 5;
    private final StringCharIterator content;
    private int line = 0;
    private int previousCol = 0;
    private int previousSrcId = 0;
    private int previousSrcLine = 0;
    private int previousSrcColumn = 0;
    private int previousNameId = 0;

    MappingBuilder(String lineMap) {
      this.content = new StringCharIterator(lineMap);
    }

    void build() {
      int [] temp = new int[MAX_ENTRY_VALUES];
      ArrayList<Entry> entries = new ArrayList<Entry>();
      while (content.hasNext()) {
        // ';' denotes a new line.
        if (tryConsumeToken(';')) {
          // The line is complete, store the result for the line,
          // null if the line is empty.
          ArrayList<Entry> result;
          if (entries.size() > 0) {
            result = entries;
            // A new array list for the next line.
            entries = new ArrayList<Entry>();
          } else {
            result = null;
          }
          lines.add(result);
          entries.clear();
          line++;
          previousCol = 0;
        } else {
          // grab the next entry for the current line.
          int entryValues = 0;
          while (!entryComplete()) {
            temp[entryValues] = nextValue();
            entryValues++;
          }
          Entry entry = decodeEntry(temp, entryValues);

          validateEntry(entry);
          entries.add(entry);

          // Consume the separating token, if there is one.
          tryConsumeToken(',');
        }
      }
    }

    /**
     * Sanity check the entry.
     */
    private void validateEntry(Entry entry) {
      Preconditions.checkState(line < lineCount);
      Preconditions.checkState(entry.getSourceFileId() == UNMAPPED
          || entry.getSourceFileId() < sources.length);
      Preconditions.checkState(entry.getNameId() == UNMAPPED
          || entry.getNameId() < names.length);
    }

    /**
     * Decodes the next entry, using the previous encountered values to
     * decode the relative values.
     *
     * @param vals An array of integers that represent values in the entry.
     * @param entryValues The number of entries in the array.
     * @return The entry object.
     */
    private Entry decodeEntry(int[] vals, int entryValues) {
      Entry entry;
      switch (entryValues) {
        // The first values, if present are in the following order:
        //   0: the starting column in the current line of the generated file
        //   1: the id of the original source file
        //   2: the starting line in the original source
        //   3: the starting column in the original source
        //   4: the id of the original symbol name
        // The values are relative to the last encountered value for that field.
        // Note: the previously column value for the generated file is reset
        // to '0' when a new line is encountered.  This is done in the 'build'
        // method.

        case 1:
          // An unmapped section of the generated file.
          entry = new UnmappedEntry(
              vals[0] + previousCol);
          // Set the values see for the next entry.
          previousCol = entry.getGeneratedColumn();
          return entry;

        case 4:
          // A mapped section of the generated file.
          entry = new UnnamedEntry(
              vals[0] + previousCol,
              vals[1] + previousSrcId,
              vals[2] + previousSrcLine,
              vals[3] + previousSrcColumn);
          // Set the values see for the next entry.
          previousCol = entry.getGeneratedColumn();
          previousSrcId = entry.getSourceFileId();
          previousSrcLine = entry.getSourceLine();
          previousSrcColumn = entry.getSourceColumn();
          return entry;

        case 5:
          // A mapped section of the generated file, that has an associated
          // name.
          entry = new NamedEntry(
              vals[0] + previousCol,
              vals[1] + previousSrcId,
              vals[2] + previousSrcLine,
              vals[3] + previousSrcColumn,
              vals[4] + previousNameId);
          // Set the values see for the next entry.
          previousCol = entry.getGeneratedColumn();
          previousSrcId = entry.getSourceFileId();
          previousSrcLine = entry.getSourceLine();
          previousSrcColumn = entry.getSourceColumn();
          previousNameId = entry.getNameId();
          return entry;

        default:
          throw new IllegalStateException(
              "Unexpected number of values for entry:" + entryValues);
      }
    }

    private boolean tryConsumeToken(char token) {
      if (content.hasNext() && content.peek() == token) {
        // consume the comma
        content.next();
        return true;
      }
      return false;
    }

    private boolean entryComplete() {
      if (!content.hasNext()) {
        return true;
      }

      char c = content.peek();
      return (c == ';' || c == ',');
    }

    private int nextValue() {
      return Base64VLQ.decode(content);
    }
  }

  /**
   * Perform a binary search on the array to find a section that covers
   * the target column.
   */
  private int search(ArrayList<Entry> entries, int target, int start, int end) {
    while (true) {
      int mid = ((end - start) / 2) + start;
      int compare = compareEntry(entries, mid, target);
      if (compare == 0) {
        return mid;
      } else if (compare < 0) {
        // it is in the upper half
        start = mid + 1;
        if (start > end) {
          return end;
        }
      } else {
        // it is in the lower half
        end = mid - 1;
        if (end < start) {
          return end;
        }
      }
    }
  }

  /**
   * Compare an array entry's column value to the taget column value.
   */
  private int compareEntry(ArrayList<Entry> entries, int entry, int target) {
    return entries.get(entry).getGeneratedColumn() - target;
  }

  /**
   * Returns the mapping entry that proceeds the supplied line or null if no
   * such entry exists.
   */
  private OriginalMapping getPreviousMapping(int lineNumber) {
    do {
      if (lineNumber == 0) {
        return null;
      }
      lineNumber--;
    } while (lines.get(lineNumber) == null);
    ArrayList<Entry> entries = lines.get(lineNumber);
    return getOriginalMappingForEntry(entries.get(entries.size() - 1));
  }

  /**
   * Creates an "OriginalMapping" object for the given entry object.
   */
  private OriginalMapping getOriginalMappingForEntry(Entry entry) {
    if (entry.getSourceFileId() == UNMAPPED) {
      return null;
    } else {
      // Adjust the line/column here to be start at 1.
      Builder x = OriginalMapping.newBuilder()
        .setOriginalFile(sources[entry.getSourceFileId()])
        .setLineNumber(entry.getSourceLine() + 1)
        .setColumnPosition(entry.getSourceColumn() + 1);
      if (entry.getNameId() != UNMAPPED) {
        x.setIdentifier(names[entry.getNameId()]);
      }
      return x.build();
    }
  }

  /**
   * Reverse the source map; the created mapping will allow us to quickly go
   * from a source file and line number to a collection of target
   * OriginalMappings.
   */
  private void createReverseMapping() {
    reverseSourceMapping =
        new HashMap<String, Map<Integer, Collection<OriginalMapping>>>();

    for (int targetLine = 0; targetLine < lines.size(); targetLine++) {
      ArrayList<Entry> entries = lines.get(targetLine);

      if (entries != null) {
        for (Entry entry : entries) {
          if (entry.getSourceFileId() != UNMAPPED
              && entry.getSourceLine() != UNMAPPED) {
            String originalFile = sources[entry.getSourceFileId()];

            if (!reverseSourceMapping.containsKey(originalFile)) {
              reverseSourceMapping.put(originalFile,
                  new HashMap<Integer, Collection<OriginalMapping>>());
            }

            Map<Integer, Collection<OriginalMapping>> lineToCollectionMap =
                reverseSourceMapping.get(originalFile);

            int sourceLine = entry.getSourceLine();

            if (!lineToCollectionMap.containsKey(sourceLine)) {
              lineToCollectionMap.put(sourceLine,
                  new ArrayList<OriginalMapping>(1));
            }

            Collection<OriginalMapping> mappings =
                lineToCollectionMap.get(sourceLine);

            Builder builder = OriginalMapping.newBuilder().setLineNumber(
                targetLine).setColumnPosition(entry.getGeneratedColumn());

            mappings.add(builder.build());
          }
        }
      }
    }
  }

  /**
   * A implementation of the Base64VLQ CharIterator used for decoding the
   * mappings encoded in the JSON string.
   */
  private static class StringCharIterator implements CharIterator {
    final String content;
    final int length;
    int current = 0;

    StringCharIterator(String content) {
      this.content = content;
      this.length = content.length();
    }

    @Override
    public char next() {
      return content.charAt(current++);
    }

    char peek() {
      return content.charAt(current);
    }

    @Override
    public boolean hasNext() {
      return  current < length;
    }
  }

  /**
   * Represents a mapping entry in the source map.
   */
  private interface Entry {
    int getGeneratedColumn();
    int getSourceFileId();
    int getSourceLine();
    int getSourceColumn();
    int getNameId();
  }

  /**
   * This class represents a portion of the generated file, that is not mapped
   * to a section in the original source.
   */
  private static class UnmappedEntry implements Entry {
    private final int column;

    UnmappedEntry(int column) {
      this.column = column;
    }

    @Override
    public int getGeneratedColumn() {
      return column;
    }

    @Override
    public int getSourceFileId() {
      return UNMAPPED;
    }

    @Override
    public int getSourceLine() {
      return UNMAPPED;
    }

    @Override
    public int getSourceColumn() {
      return UNMAPPED;
    }

    @Override
    public int getNameId() {
      return UNMAPPED;
    }
  }

  /**
   * This class represents a portion of the generated file, that is mapped
   * to a section in the original source.
   */
  private static class UnnamedEntry extends UnmappedEntry {
    private final int srcFile;
    private final int srcLine;
    private final int srcColumn;

    UnnamedEntry(int column, int srcFile, int srcLine, int srcColumn) {
      super(column);
      this.srcFile = srcFile;
      this.srcLine = srcLine;
      this.srcColumn = srcColumn;
    }

    @Override
    public int getSourceFileId() {
      return srcFile;
    }

    @Override
    public int getSourceLine() {
      return srcLine;
    }

    @Override
    public int getSourceColumn() {
      return srcColumn;
    }

    @Override
    public int getNameId() {
      return UNMAPPED;
    }
  }

  /**
   * This class represents a portion of the generated file, that is mapped
   * to a section in the original source, and is associated with a name.
   */
  private static class NamedEntry extends UnnamedEntry {
    private final int name;

    NamedEntry(int column, int srcFile, int srcLine, int srcColumn, int name) {
      super(column, srcFile, srcLine, srcColumn);
      this.name = name;
    }

    @Override
    public int getNameId() {
      return name;
    }
  }

  static interface EntryVisitor {
    void visit(String sourceName,
               String symbolName,
               FilePosition sourceStartPosition,
               FilePosition startPosition,
               FilePosition endPosition);
  }

  public void visitMappings(EntryVisitor visitor) {
    boolean pending = false;
    String sourceName = null;
    String symbolName = null;
    FilePosition sourceStartPosition = null;
    FilePosition startPosition = null;

    final int lineCount = lines.size();
    for (int i = 0; i < lineCount; i++) {
      ArrayList<Entry> line = lines.get(i);
      if (line != null) {
        final int entryCount = line.size();
        for (int j = 0; j < entryCount; j++) {
          Entry entry = line.get(j);
          if (pending) {
            FilePosition endPosition = new FilePosition(
                i, entry.getGeneratedColumn());
            visitor.visit(
                sourceName,
                symbolName,
                sourceStartPosition,
                startPosition,
                endPosition);
            pending = false;
          }

          if (entry.getSourceFileId() != UNMAPPED) {
            pending = true;
            sourceName = sources[entry.getSourceFileId()];
            symbolName = (entry.getNameId() != UNMAPPED)
                ? names[entry.getNameId()] : null;
            sourceStartPosition = new FilePosition(
                entry.getSourceLine(), entry.getSourceColumn());
            startPosition = new FilePosition(
                i, entry.getGeneratedColumn());
          }
        }
      }
    }
  }
}
