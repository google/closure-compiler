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
import com.google.debugging.sourcemap.Base64VLQ.CharIterator;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping.Precision;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Class for parsing version 3 of the SourceMap format, as produced by the Closure Compiler, etc.
 * https://github.com/google/closure-compiler/wiki/Source-Maps
 */
public final class SourceMapConsumerV3 implements SourceMapConsumer, SourceMappingReversable {
  static final int UNMAPPED = -1;

  private String[] sources;
  private String[] sourcesContent;
  private String[] names;
  private @Nullable String file;
  private int lineCount;

  private Mappings mappings = null;

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
  public void parse(SourceMapObject sourceMapObject, @Nullable SourceMapSupplier sectionSupplier)
      throws SourceMapParseException {
    if (sourceMapObject.getVersion() != 3) {
      throw new SourceMapParseException("Unknown version: " + sourceMapObject.getVersion());
    }

    file = sourceMapObject.getFile();
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

    boolean useCompactMappings =
        (sources.length < 65535) && (names == null || names.length < 65535);

    mappings = new Mappings(lineCount, useCompactMappings);

    // The value type of each extension is the native JSON type (e.g. JsonObject, or JSONObject
    // when compiled with GWT).
    extensions.putAll(sourceMapObject.getExtensions());
    new MappingBuilder(sourceMapObject.getMappings()).build();
  }

  /** */
  private void parseMetaMap(SourceMapObject sourceMapObject, SourceMapSupplier sectionSupplier)
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

      // Build up a new source map in a new generator using the mappings of this metamap. The new
      // map will be rendered to JSON and then parsed using this consumer.
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
      generator.appendTo(sb, sourceMapObject.getFile());
      parse(sb.toString());

    } catch (IOException ex) {
      throw new SourceMapParseException("IO exception: " + ex);
    }
  }

  @Override
  public @Nullable OriginalMapping getMappingForLine(int lineNumber, int column) {
    // Normalize the line and column numbers to 0.
    lineNumber--;
    column--;

    if (lineNumber < 0 || lineNumber >= mappings.getParsedLineCount()) {
      return null;
    }

    int start = mappings.getLineStart(lineNumber);
    int end = mappings.getLineStart(lineNumber + 1);

    // If the line is empty return the previous mapping.
    if (start == end) {
      return getPreviousMapping(lineNumber);
    }

    if (mappings.atIndex(start).getGeneratedColumn() > column) {
      return getPreviousMapping(lineNumber);
    }

    int index = search(column, start, end - mappings.getEntrySize());
    Preconditions.checkState(index >= 0, "unexpected:%s", index);
    return getOriginalMappingForEntry(index, Precision.EXACT);
  }

  @Override
  public Collection<String> getOriginalSources() {
    return Arrays.asList(sources);
  }

  public @Nullable Collection<String> getOriginalSourcesContent() {
    return sourcesContent == null ? null : Arrays.asList(sourcesContent);
  }

  public List<String> getOriginalNames() {
    return Arrays.asList(names);
  }

  public @Nullable String getFile() {
    return file;
  }

  public int getLineCount() {
    return lineCount;
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
      int[] temp = new int[MAX_ENTRY_VALUES];

      int entriesCount = 0;
      int linesCount = 0;

      mappings.setLineStart(0, 0);

      while (content.hasNext()) {
        // ';' denotes a new line.
        if (tryConsumeToken(';')) {
          // The line is complete, store the result
          linesCount++;
          mappings.setLineStart(linesCount, entriesCount);
          line++;
          previousCol = 0;
        } else {
          // grab the next entry for the current line.
          int entryValues = 0;
          while (!entryComplete()) {
            temp[entryValues] = nextValue();
            entryValues++;
          }

          mappings.setEntry(
              entriesCount,
              temp,
              entryValues,
              previousCol,
              previousSrcId,
              previousSrcLine,
              previousSrcColumn,
              previousNameId);
          mappings.validateEntry(entriesCount, line);

          mappings.atIndex(entriesCount);
          previousCol = mappings.getGeneratedColumn();
          if (entryValues >= 4) {
            previousSrcId = mappings.getSourceFileId();
            previousSrcLine = mappings.getSourceLine();
            previousSrcColumn = mappings.getSourceColumn();
          }
          if (entryValues == 5) {
            previousNameId = mappings.getNameId();
          }

          entriesCount += mappings.getEntrySize();

          // Consume the separating token, if there is one.
          tryConsumeToken(',');
        }
      }

      // Some source map generator (e.g.UglifyJS) generates lines without
      // a trailing line separator. So add the rest of the content.
      if (entriesCount > mappings.getLineStart(linesCount)) {
        linesCount++;
        mappings.setLineStart(linesCount, entriesCount);
      }

      mappings.trim(entriesCount, linesCount);
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

  /** Perform a binary search on the array to find a section that covers the target column. */
  private int search(int target, int start, int end) {
    int entrySize = mappings.getEntrySize();
    while (true) {
      int mid = ((end - start) / (entrySize * 2)) * entrySize + start;
      int compare = mappings.atIndex(mid).getGeneratedColumn() - target;
      if (compare == 0) {
        return mid;
      } else if (compare < 0) {
        // it is in the upper half
        start = mid + entrySize;
        if (start > end) {
          return end;
        }
      } else {
        // it is in the lower half
        end = mid - entrySize;
        if (end < start) {
          return end;
        }
      }
    }
  }

  private @Nullable OriginalMapping getPreviousMapping(int lineNumber) {
    do {
      if (lineNumber == 0) {
        return null;
      }
      lineNumber--;
    } while (mappings.getLineStart(lineNumber) == mappings.getLineStart(lineNumber + 1));
    int index = mappings.getLineStart(lineNumber + 1) - mappings.getEntrySize();
    return getOriginalMappingForEntry(index, Precision.APPROXIMATE_LINE);
  }

  /** Creates an "OriginalMapping" object for the given entry object. */
  private @Nullable OriginalMapping getOriginalMappingForEntry(int index, Precision precision) {
    mappings.atIndex(index);
    int sourceFileId = mappings.getSourceFileId();
    if (sourceFileId == UNMAPPED) {
      return null;
    } else {
      // Adjust the line/column here to be start at 1.
      OriginalMapping.Builder x =
          OriginalMapping.newBuilder()
              .setOriginalFile(sources[sourceFileId])
              .setLineNumber(mappings.getSourceLine() + 1)
              .setColumnPosition(mappings.getSourceColumn() + 1)
              .setPrecision(precision);
      int nameId = mappings.getNameId();
      if (nameId != UNMAPPED) {
        x.setIdentifier(names[nameId]);
      }
      return x.build();
    }
  }

  /**
   * Reverse the source map; the created mapping will allow us to quickly go from a source file and
   * line number to a collection of target OriginalMappings.
   */
  private void createReverseMapping() {
    reverseSourceMapping = new LinkedHashMap<>();

    for (int targetLine = 0; targetLine < mappings.getParsedLineCount(); targetLine++) {
      int start = mappings.getLineStart(targetLine);
      int end = mappings.getLineStart(targetLine + 1);

      for (int i = start; i < end; i += mappings.getEntrySize()) {
        mappings.atIndex(i);
        int sourceFileId = mappings.getSourceFileId();
        int sourceLine = mappings.getSourceLine();
        if (sourceFileId != UNMAPPED && sourceLine != UNMAPPED) {
          String originalFile = sources[sourceFileId];

          Map<Integer, Collection<OriginalMapping>> lineToCollectionMap =
              reverseSourceMapping.computeIfAbsent(
                  originalFile,
                  (String k) -> new LinkedHashMap<Integer, Collection<OriginalMapping>>());

          if (!lineToCollectionMap.containsKey(sourceLine)) {
            lineToCollectionMap.put(sourceLine, new ArrayList<OriginalMapping>(1));
          }

          Collection<OriginalMapping> mappingsLine = lineToCollectionMap.get(sourceLine);

          OriginalMapping.Builder builder =
              OriginalMapping.newBuilder()
                  .setLineNumber(targetLine)
                  .setColumnPosition(mappings.getGeneratedColumn());

          mappingsLine.add(builder.build());
        }
      }
    }
  }

  /**
   * A implementation of the Base64VLQ CharIterator used for decoding the mappings encoded in the
   * JSON string.
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

  /** A container for mapping entries. */
  private final class Mappings {
    // flatEntries stores all entries sequentially.
    // Each entry is packed into 5 consecutive integers:
    // [generatedCol, sourceLine, sourceCol, sourceFileId, nameId].
    // For unmapped entries, sourceFileId (and other source-related fields) will be set to UNMAPPED
    // (-1).
    private int[] flatEntries;
    // lineStart[i] is the index of the first entry for line i in flatEntries.
    // The length of lineStart is lineCount + 1, where lineStart[lineCount] is the total number of
    // entries.
    private int[] lineStart;
    private int lineCount;
    private int index;

    private final int entrySize;

    Mappings(int lineCount, boolean useCompactMappings) {
      this.lineCount = lineCount;
      this.entrySize = useCompactMappings ? 4 : 5;
      int estimatedEntries = (lineCount >= 0) ? lineCount * 2 : 1000;
      this.flatEntries = new int[estimatedEntries * entrySize];
      int estimatedLines = (lineCount >= 0) ? lineCount : 1000;
      this.lineStart = new int[estimatedLines + 1];
    }

    int getEntrySize() {
      return entrySize;
    }

    @CanIgnoreReturnValue
    Mappings atIndex(int index) {
      this.index = index;
      return this;
    }

    int getGeneratedColumn() {
      return flatEntries[index];
    }

    int getSourceFileId() {
      if (entrySize == 4) {
        int val = flatEntries[index + 3] >> 16;
        return (val == -1 || (val & 0xFFFF) == 0xFFFF) ? UNMAPPED : (val & 0xFFFF);
      }
      return flatEntries[index + 3];
    }

    int getSourceLine() {
      return flatEntries[index + 1];
    }

    int getSourceColumn() {
      return flatEntries[index + 2];
    }

    int getNameId() {
      if (entrySize == 4) {
        int val = flatEntries[index + 3] & 0xFFFF;
        return (val == 0xFFFF) ? UNMAPPED : val;
      }
      return flatEntries[index + 4];
    }

    int getParsedLineCount() {
      return lineCount;
    }

    int getLineStart(int line) {
      return lineStart[line];
    }

    void setLineStart(int line, int entriesCount) {
      if (line >= lineStart.length) {
        lineStart = Arrays.copyOf(lineStart, lineStart.length * 2);
      }
      lineStart[line] = entriesCount;
    }

    void setEntry(
        int entriesCount,
        int[] vals,
        int entryValues,
        int previousCol,
        int previousSrcId,
        int previousSrcLine,
        int previousSrcColumn,
        int previousNameId)
        throws SourceMapParseException {
      if (entriesCount + entrySize > flatEntries.length) {
        flatEntries = Arrays.copyOf(flatEntries, flatEntries.length * 2);
      }
      // `entrySize == 4` indicates `useCompactMappings` is true, meaning the number of
      // unique source files and names are both small enough (under 65535) that their IDs
      // can be bit-packed into a single integer. Otherwise, they each get their own integer.
      if (entrySize == 4) {
        setEntryCompact(
            entriesCount,
            vals,
            entryValues,
            previousCol,
            previousSrcId,
            previousSrcLine,
            previousSrcColumn,
            previousNameId);
      } else {
        setEntryNormal(
            entriesCount,
            vals,
            entryValues,
            previousCol,
            previousSrcId,
            previousSrcLine,
            previousSrcColumn,
            previousNameId);
      }
    }

    private void setEntryCompact(
        int entriesCount,
        int[] vals,
        int entryValues,
        int previousCol,
        int previousSrcId,
        int previousSrcLine,
        int previousSrcColumn,
        int previousNameId)
        throws SourceMapParseException {
      // The `vals` array holds the decoded VLQ values in the exact order established
      // by the SourceMap V3 specification:
      // [generatedCol, sourceFileId, sourceLine, sourceCol, nameId]
      // We reorder these values when storing them in `flatEntries` to minimize branching
      // overhead during lookups, putting `sourceLine` and `sourceColumn` before the IDs.
      switch (entryValues) {
        case 1 -> {
          flatEntries[entriesCount] = vals[0] + previousCol;
          flatEntries[entriesCount + 1] = UNMAPPED;
          flatEntries[entriesCount + 2] = UNMAPPED;
          flatEntries[entriesCount + 3] = UNMAPPED; // Both IDs are -1
        }
        case 4 -> {
          flatEntries[entriesCount] = vals[0] + previousCol;
          flatEntries[entriesCount + 1] = vals[2] + previousSrcLine;
          flatEntries[entriesCount + 2] = vals[3] + previousSrcColumn;
          int srcId = vals[1] + previousSrcId;
          flatEntries[entriesCount + 3] = (srcId << 16) | 0xFFFF;
        }
        case 5 -> {
          flatEntries[entriesCount] = vals[0] + previousCol;
          flatEntries[entriesCount + 1] = vals[2] + previousSrcLine;
          flatEntries[entriesCount + 2] = vals[3] + previousSrcColumn;
          int srcId = vals[1] + previousSrcId;
          int nameId = vals[4] + previousNameId;
          flatEntries[entriesCount + 3] = (srcId << 16) | (nameId & 0xFFFF);
        }
        default ->
            throw new SourceMapParseException(
                "Unexpected number of values for entry:" + entryValues);
      }
    }

    private void setEntryNormal(
        int entriesCount,
        int[] vals,
        int entryValues,
        int previousCol,
        int previousSrcId,
        int previousSrcLine,
        int previousSrcColumn,
        int previousNameId)
        throws SourceMapParseException {
      // The `vals` array holds the decoded VLQ values in the exact order established
      // by the SourceMap V3 specification:
      // [generatedCol, sourceFileId, sourceLine, sourceCol, nameId]
      // We reorder these values when storing them in `flatEntries` to minimize branching
      // overhead during lookups, putting `sourceLine` and `sourceColumn` before the IDs.
      switch (entryValues) {
        case 1 -> {
          flatEntries[entriesCount] = vals[0] + previousCol;
          flatEntries[entriesCount + 1] = UNMAPPED;
          flatEntries[entriesCount + 2] = UNMAPPED;
          flatEntries[entriesCount + 3] = UNMAPPED;
          flatEntries[entriesCount + 4] = UNMAPPED;
        }
        case 4 -> {
          flatEntries[entriesCount] = vals[0] + previousCol;
          flatEntries[entriesCount + 1] = vals[2] + previousSrcLine;
          flatEntries[entriesCount + 2] = vals[3] + previousSrcColumn;
          flatEntries[entriesCount + 3] = vals[1] + previousSrcId;
          flatEntries[entriesCount + 4] = UNMAPPED;
        }
        case 5 -> {
          flatEntries[entriesCount] = vals[0] + previousCol;
          flatEntries[entriesCount + 1] = vals[2] + previousSrcLine;
          flatEntries[entriesCount + 2] = vals[3] + previousSrcColumn;
          flatEntries[entriesCount + 3] = vals[1] + previousSrcId;
          flatEntries[entriesCount + 4] = vals[4] + previousNameId;
        }
        default ->
            throw new SourceMapParseException(
                "Unexpected number of values for entry:" + entryValues);
      }
    }

    void validateEntry(int entriesCount, int line) {
      Preconditions.checkState(
          lineCount < 0 || line < lineCount, "line=%s, lineCount=%s", line, lineCount);
      atIndex(entriesCount);
      int sourceFileId = getSourceFileId();
      int nameId = getNameId();
      checkState(sourceFileId == UNMAPPED || sourceFileId < sources.length);
      checkState(nameId == UNMAPPED || nameId < names.length);
    }

    void trim(int entriesCount, int linesCount) {
      this.flatEntries = Arrays.copyOf(flatEntries, entriesCount);
      this.lineStart = Arrays.copyOf(lineStart, linesCount + 1);
      this.lineCount = linesCount;
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

    for (int i = 0; i < mappings.getParsedLineCount(); i++) {
      int start = mappings.getLineStart(i);
      int end = mappings.getLineStart(i + 1);
      if (start != end) {
        for (int j = start; j < end; j += mappings.getEntrySize()) {
          mappings.atIndex(j);
          if (pending) {
            FilePosition endPosition = new FilePosition(i, mappings.getGeneratedColumn());
            visitor.visit(
                sourceName,
                symbolName,
                sourceStartPosition,
                startPosition,
                endPosition);
            pending = false;
          }

          int sourceFileId = mappings.getSourceFileId();
          if (sourceFileId != UNMAPPED) {
            pending = true;
            sourceName = sources[sourceFileId];
            int nameId = mappings.getNameId();
            symbolName = (nameId != UNMAPPED) ? names[nameId] : null;
            sourceStartPosition =
                new FilePosition(mappings.getSourceLine(), mappings.getSourceColumn());
            startPosition = new FilePosition(i, mappings.getGeneratedColumn());
          }
        }
      }
    }
    // Complete pending entry if any.
    if (pending) {
      // Given that this is the last entry and we don't know how much of the generated file left
      // after that entry - make it of length 1.
      FilePosition endPosition =
          new FilePosition(startPosition.getLine(), startPosition.getColumn() + 1);
      visitor.visit(sourceName, symbolName, sourceStartPosition, startPosition, endPosition);
    }
  }
}
