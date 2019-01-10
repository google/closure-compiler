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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.debugging.sourcemap.Base64VLQ.CharIterator;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping.Builder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class for parsing version 3 of the SourceMap format, as produced by the
 * Closure Compiler, etc.
 * http://code.google.com/p/closure-compiler/wiki/SourceMaps
 * @author johnlenz@google.com (John Lenz)
 */
public final class SourceMapConsumerV3 implements SourceMapConsumer,
    SourceMappingReversable {
  static final int UNMAPPED = -1;

  private String[] sources;
  private String[] sourcesContent;
  private String[] names;
  private int lineCount;
  // Slots in the lines list will be null if the line does not have any entries.
  private ArrayList<ArrayList<Entry>> lines = null;
  /** originalFile path ==> original line ==> target mappings */
  private Map<String, Map<Integer, Collection<OriginalMapping>>>
      reverseSourceMapping;
  private String sourceRoot;
  private final Map<String, Object> extensions = new LinkedHashMap<>();

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
    SourceMapObject sourceMapObject = SourceMapObjectParser.parse(contents);
    parse(sourceMapObject, null);
  }

  /** Parses the given contents containing a source map. */
  public void parse(SourceMapObject sourceMapObject, SourceMapSupplier sectionSupplier)
      throws SourceMapParseException {
    if (sourceMapObject.getVersion() != 3) {
      throw new SourceMapParseException("Unknown version: " + sourceMapObject.getVersion());
    }

    String file = sourceMapObject.getFile();
    if (file != null && file.isEmpty()) {
      throw new SourceMapParseException("File entry is empty");
    }

    if (sourceMapObject.getSections() != null) {
      // Looks like a index map, try to parse it that way.
      parseMetaMap(sourceMapObject, sectionSupplier);
      return;
    }

    lineCount = sourceMapObject.getLineCount();
    sourceRoot = sourceMapObject.getSourceRoot();
    sources = sourceMapObject.getSources();
    sourcesContent = sourceMapObject.getSourcesContent();
    names = sourceMapObject.getNames();

    if (lineCount >= 0) {
      lines = new ArrayList<>(lineCount);
    } else {
      lines = new ArrayList<>();
    }

    // The value type of each extension is the native JSON type (e.g. JsonObject, or JSONObject
    // when compiled with GWT).
    extensions.putAll(sourceMapObject.getExtensions());
    new MappingBuilder(sourceMapObject.getMappings()).build();
  }

  /**
   * @param sourceMapObject
   * @throws SourceMapParseException
   */
  private void parseMetaMap(
      SourceMapObject sourceMapObject, SourceMapSupplier sectionSupplier)
      throws SourceMapParseException {
    if (sectionSupplier == null) {
      sectionSupplier = new DefaultSourceMapSupplier();
    }

    try {
      if (sourceMapObject.getLineCount() >= 0
          || sourceMapObject.getMappings() != null
          || sourceMapObject.getSources() != null
          || sourceMapObject.getNames() != null) {
        throw new SourceMapParseException("Invalid map format");
      }

      SourceMapGeneratorV3 generator = new SourceMapGeneratorV3();
      for (SourceMapSection section : sourceMapObject.getSections()) {
        String mapSectionContents = section.getSectionValue();
        if (section.getSectionType() == SourceMapSection.SectionType.URL) {
          mapSectionContents = sectionSupplier.getSourceMap(section.getSectionValue());
        }
        if (mapSectionContents == null) {
          throw new SourceMapParseException("Unable to retrieve: " + section.getSectionValue());
        }
        generator.mergeMapSection(section.getLine(), section.getColumn(), mapSectionContents);
      }

      StringBuilder sb = new StringBuilder();
      try {
        generator.appendTo(sb, sourceMapObject.getFile());
      } catch (IOException e) {
        // Can't happen.
        throw new RuntimeException(e);
      }

      parse(sb.toString());
    } catch (IOException ex) {
      throw new SourceMapParseException("IO exception: " + ex);
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

    checkState(lineNumber >= 0);
    checkState(column >= 0);

    // If the line is empty return the previous mapping.
    if (lines.get(lineNumber) == null) {
      return getPreviousMapping(lineNumber);
    }

    ArrayList<Entry> entries = lines.get(lineNumber);
    // No empty lists.
    checkState(!entries.isEmpty());
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

  public Collection<String> getOriginalSourcesContent() {
    return sourcesContent == null ? null : Arrays.asList(sourcesContent);
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

  public String getSourceRoot(){
    return this.sourceRoot;
  }

  /**
   * Returns all extensions and their values (which can be any json value)
   * in a Map object.
   *
   * @return The extension list
   */
  public Map<String, Object> getExtensions(){
    return this.extensions;
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

    void build() throws SourceMapParseException {
      int [] temp = new int[MAX_ENTRY_VALUES];
      ArrayList<Entry> entries = new ArrayList<>();
      while (content.hasNext()) {
        // ';' denotes a new line.
        if (tryConsumeToken(';')) {
          // The line is complete, store the result
          completeLine(entries);
          if (!entries.isEmpty()) {
            // A new array list for the next line.
            entries = new ArrayList<>();
          }
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

      // Some source map generator (e.g.UglifyJS) generates lines without
      // a trailing line separator. So add the rest of the content.
      if (!entries.isEmpty()) {
        completeLine(entries);
      }
    }

    private void completeLine(ArrayList<Entry> entries) {
      // The line is complete, store the result for the line,
      // null if the line is empty.
      if (!entries.isEmpty()) {
        lines.add(entries);
      } else {
        lines.add(null);
      }
      line++;
      previousCol = 0;
    }

    private void validateEntry(Entry entry) {
      Preconditions.checkState((lineCount < 0) || (line < lineCount),
          "line=%s, lineCount=%s", line, lineCount);
      checkState(entry.getSourceFileId() == UNMAPPED || entry.getSourceFileId() < sources.length);
      checkState(entry.getNameId() == UNMAPPED || entry.getNameId() < names.length);
    }

    /**
     * Decodes the next entry, using the previous encountered values to
     * decode the relative values.
     *
     * @param vals An array of integers that represent values in the entry.
     * @param entryValues The number of entries in the array.
     * @return The entry object.
     */
    private Entry decodeEntry(int[] vals, int entryValues) throws SourceMapParseException {
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
          throw new SourceMapParseException(
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
  private static int search(ArrayList<Entry> entries, int target, int start, int end) {
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
   * Compare an array entry's column value to the target column value.
   */
  private static int compareEntry(ArrayList<Entry> entries, int entry, int target) {
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
    return getOriginalMappingForEntry(Iterables.getLast(entries));
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
    reverseSourceMapping = new HashMap<>();

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
      return current < length;
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

  public static interface EntryVisitor {
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
