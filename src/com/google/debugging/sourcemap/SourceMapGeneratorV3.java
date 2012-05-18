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
import com.google.common.collect.Maps;
import com.google.debugging.sourcemap.SourceMapConsumerV3.EntryVisitor;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

/**
 * Collects information mapping the generated (compiled) source back to
 * its original source for debugging purposes.
 *
 * @author johnlenz@google.com (John Lenz)
 */
public class SourceMapGeneratorV3 implements SourceMapGenerator {

  private static final int UNMAPPED = -1;


  /**
   * A pre-order traversal ordered list of mappings stored in this map.
   */
  private List<Mapping> mappings = Lists.newArrayList();

  /**
   * A map of source names to source name index
   */
  private LinkedHashMap<String, Integer> sourceFileMap =
      Maps.newLinkedHashMap();

  /**
   * A map of source names to source name index
   */
  private LinkedHashMap<String, Integer> originalNameMap =
      Maps.newLinkedHashMap();

  /**
   * Cache of the last mappings source name.
   */
  private String lastSourceFile = null;

  /**
   * Cache of the last mappings source name index.
   */
  private int lastSourceFileIndex = -1;

  /**
   * For validation store the last mapping added.
   */
  private Mapping lastMapping;

  /**
   * The position that the current source map is offset in the
   * buffer being used to generated the compiled source file.
   */
  private FilePosition offsetPosition = new FilePosition(0, 0);

  /**
   * The position that the current source map is offset in the
   * generated the compiled source file by the addition of a
   * an output wrapper prefix.
   */
  private FilePosition prefixPosition = new FilePosition(0, 0);


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    mappings.clear();
    lastMapping = null;
    sourceFileMap.clear();
    originalNameMap.clear();
    lastSourceFile = null;
    lastSourceFileIndex = -1;
    offsetPosition = new FilePosition(0, 0);
    prefixPosition = new FilePosition(0, 0);
  }

  /**
   * @param validate Whether to perform (potentially costly) validation on the
   * generated source map.
   */
  @Override
  public void validate(boolean validate) {
    // Nothing currently.
  }

  /**
   * Sets the prefix used for wrapping the generated source file before
   * it is written. This ensures that the source map is adjusted for the
   * change in character offsets.
   *
   * @param prefix The prefix that is added before the generated source code.
   */
  @Override
  public void setWrapperPrefix(String prefix) {
    // Determine the current line and character position.
    int prefixLine = 0;
    int prefixIndex = 0;

    for (int i = 0; i < prefix.length(); ++i) {
      if (prefix.charAt(i) == '\n') {
        prefixLine++;
        prefixIndex = 0;
      } else {
        prefixIndex++;
      }
    }

    prefixPosition = new FilePosition(prefixLine, prefixIndex);
  }

  /**
   * Sets the source code that exists in the buffer for which the
   * generated code is being generated. This ensures that the source map
   * accurately reflects the fact that the source is being appended to
   * an existing buffer and as such, does not start at line 0, position 0
   * but rather some other line and position.
   *
   * @param offsetLine The index of the current line being printed.
   * @param offsetIndex The column index of the current character being printed.
   */
  @Override
  public void setStartingPosition(int offsetLine, int offsetIndex) {
    Preconditions.checkState(offsetLine >= 0);
    Preconditions.checkState(offsetIndex >= 0);
    offsetPosition = new FilePosition(offsetLine, offsetIndex);
  }

  /**
   * Adds a mapping for the given node.  Mappings must be added in order.
   * @param startPosition The position on the starting line
   * @param endPosition The position on the ending line.
   */
  @Override
  public void addMapping(
      String sourceName, @Nullable String symbolName,
      FilePosition sourceStartPosition,
      FilePosition startPosition, FilePosition endPosition) {

    // Don't bother if there is not sufficient information to be useful.
    if (sourceName == null || sourceStartPosition.getLine() < 0) {
      return;
    }

    FilePosition adjustedStart = startPosition;
    FilePosition adjustedEnd = endPosition;

    if (offsetPosition.getLine() != 0
        || offsetPosition.getColumn() != 0) {
      // If the mapping is found on the first line, we need to offset
      // its character position by the number of characters found on
      // the *last* line of the source file to which the code is
      // being generated.
      int offsetLine = offsetPosition.getLine();
      int startOffsetPosition = offsetPosition.getColumn();
      int endOffsetPosition = offsetPosition.getColumn();

      if (startPosition.getLine() > 0) {
        startOffsetPosition = 0;
      }

      if (endPosition.getLine() > 0) {
        endOffsetPosition = 0;
      }

      adjustedStart = new FilePosition(
          startPosition.getLine() + offsetLine,
          startPosition.getColumn() + startOffsetPosition);

      adjustedEnd = new FilePosition(
          endPosition.getLine() + offsetLine,
          endPosition.getColumn() + endOffsetPosition);
    }

    // Create the new mapping.
    Mapping mapping = new Mapping();
    mapping.sourceFile = sourceName;
    mapping.originalPosition = sourceStartPosition;
    mapping.originalName = symbolName;
    mapping.startPosition = adjustedStart;
    mapping.endPosition = adjustedEnd;

    // Validate the mappings are in a proper order.
    if (lastMapping != null) {
      int lastLine = lastMapping.startPosition.getLine();
      int lastColumn = lastMapping.startPosition.getColumn();
      int nextLine = mapping.startPosition.getLine();
      int nextColumn = mapping.startPosition.getColumn();
      Preconditions.checkState(nextLine > lastLine
          || (nextLine == lastLine && nextColumn >= lastColumn),
          "Incorrect source mappings order, previous : (%s,%s)\n"
          + "new : (%s,%s)\nnode : %s",
          lastLine, lastColumn, nextLine, nextColumn);
    }

    lastMapping = mapping;
    mappings.add(mapping);
  }

  class ConsumerEntryVisitor implements EntryVisitor {

    @Override
    public void visit(
        String sourceName, String symbolName,
        FilePosition sourceStartPosition,
        FilePosition startPosition, FilePosition endPosition) {
      addMapping(sourceName, symbolName,
          sourceStartPosition, startPosition, endPosition);
    }
  }

  public void mergeMapSection(int line, int column, String mapSectionContents)
      throws SourceMapParseException {
     setStartingPosition(line, column);
     SourceMapConsumerV3 section = new SourceMapConsumerV3();
     section.parse(mapSectionContents);
     section.visitMappings(new ConsumerEntryVisitor());
  }

  /**
   * Writes out the source map in the following format (line numbers are for
   * reference only and are not part of the format):
   *
   * 1.  {
   * 2.    version: 3,
   * 3.    file: "out.js",
   * 4.    lineCount: 2,
   * 5.    sourceRoot: "",
   * 6.    sources: ["foo.js", "bar.js"],
   * 7.    names: ["src", "maps", "are", "fun"],
   * 8.    mappings: "a;;abcde,abcd,a;"
   * 9.  }
   *
   * Line 1: The entire file is a single JSON object
   * Line 2: File revision (always the first entry in the object)
   * Line 3: The name of the file that this source map is associated with.
   * Line 4: The number of lines represented in the source map.
   * Line 5: An optional source root, useful for relocating source files on a
   *     server or removing repeated prefix values in the "sources" entry.
   * Line 6: A list of sources used by the "mappings" entry relative to the
   *     sourceRoot.
   * Line 7: A list of symbol names used by the "mapping" entry.  This list
   *     may be incomplete.
   * Line 8: The mappings field.
   */
  @Override
  public void appendTo(Appendable out, String name) throws IOException {
    int maxLine = prepMappings();

    // Add the header fields.
    out.append("{\n");
    appendFirstField(out, "version", "3");
    appendField(out, "file", escapeString(name));
    appendField(out, "lineCount", String.valueOf(maxLine + 1));

    // Add the mappings themselves.
    appendFieldStart(out, "mappings");
    // out.append("[");
    (new LineMapper(out)).appendLineMappings();
    // out.append("]");
    appendFieldEnd(out);

    // Files names
    appendFieldStart(out, "sources");
    out.append("[");
    addSourceNameMap(out);
    out.append("]");
    appendFieldEnd(out);

    // Files names
    appendFieldStart(out, "names");
    out.append("[");
    addSymbolNameMap(out);
    out.append("]");
    appendFieldEnd(out);

    out.append("\n}\n");
  }

  /**
   * Writes the source name map to 'out'.
   */
  private void addSourceNameMap(Appendable out) throws IOException {
    addNameMap(out, sourceFileMap);
  }

  /**
   * Writes the source name map to 'out'.
   */
  private void addSymbolNameMap(Appendable out) throws IOException {
    addNameMap(out, originalNameMap);
  }

  private void addNameMap(Appendable out, Map<String, Integer> map)
      throws IOException {
    int i = 0;
    for (Entry<String, Integer> entry : map.entrySet()) {
      String key = entry.getKey();
      if (i != 0) {
        out.append(",");
      }
      out.append(escapeString(key));
      i++;
    }
  }

  /**
   * Escapes the given string for JSON.
   */
  private static String escapeString(String value) {
    return Util.escapeString(value);
  }

  // Source map field helpers.

  private static void appendFirstField(
      Appendable out, String name, CharSequence value)
      throws IOException {
    out.append("\"");
    out.append(name);
    out.append("\"");
    out.append(":");
    out.append(value);
  }

  private static void appendField(
      Appendable out, String name, CharSequence value)
      throws IOException {
    out.append(",\n");
    out.append("\"");
    out.append(name);
    out.append("\"");
    out.append(":");
    out.append(value);
  }

  private static void appendFieldStart(Appendable out, String name)
      throws IOException {
    appendField(out, name, "");
  }

  @SuppressWarnings("unused")
  private static void appendFieldEnd(Appendable out)
     throws IOException {
  }

  /**
   * Assigns sequential ids to used mappings, and returns the last line mapped.
   */
  private int prepMappings() throws IOException {
    // Mark any unused mappings.
    (new MappingTraversal()).traverse(new UsedMappingCheck());

    // Renumber used mappings and keep track of the last line.
    int id = 0;
    int maxLine = 0;
    int sourceId = 0;
    int nameId = 0;
    for (Mapping m : mappings) {
      if (m.used) {
        m.id = id++;
        int endPositionLine = m.endPosition.getLine();
        maxLine = Math.max(maxLine, endPositionLine);
      }
    }

    // Adjust for the prefix.
    return maxLine + prefixPosition.getLine();
  }

  /**
   * A mapping from a given position in an input source file to a given position
   * in the generated code.
   */
  static class Mapping {
    /**
     * A unique ID for this mapping for record keeping purposes.
     */
    int id = UNMAPPED;

    /**
     * The source file index.
     */
    String sourceFile;

    /**
     * The position of the code in the input source file. Both
     * the line number and the character index are indexed by
     * 1 for legacy reasons via the Rhino Node class.
     */
    FilePosition originalPosition;

    /**
     * The starting position of the code in the generated source
     * file which this mapping represents. Indexed by 0.
     */
    FilePosition startPosition;

    /**
     * The ending position of the code in the generated source
     * file which this mapping represents. Indexed by 0.
     */
    FilePosition endPosition;

    /**
     * The original name of the token found at the position
     * represented by this mapping (if any).
     */
    String originalName;

    /**
     * Whether the mapping is actually used by the source map.
     */
    boolean used = false;
  }

  /**
   * Mark any visited mapping as "used".
   */
  private class UsedMappingCheck implements MappingVisitor {
    /**
     * @throws IOException
     */
    @Override
    public void visit(Mapping m, int line, int col, int nextLine, int nextCol)
        throws IOException {
      if (m != null) {
        m.used = true;
      }
    }
  }

  private interface MappingVisitor {
    /**
     * @param m The mapping for the current code segment. null if the segment
     *     is unmapped.
     * @param line The starting line for this code segment.
     * @param col The starting column for this code segment.
     * @param endLine The ending line
     * @param endCol The ending column
     * @throws IOException
     */
    void visit(Mapping m, int line, int col, int endLine, int endCol)
        throws IOException;
  }

  /**
   * Walk the mappings and visit each segment of the mappings, unmapped
   * segments are visited with a null mapping, unused mapping are not visited.
   */
  private class MappingTraversal {
    // The last line and column written
    private int line;
    private int col;

    MappingTraversal() {
    }

    // Append the line mapping entries.
    void traverse(MappingVisitor v) throws IOException {
      // The mapping list is ordered as a pre-order traversal.  The mapping
      // positions give us enough information to rebuild the stack and this
      // allows the building of the source map in O(n) time.
      Deque<Mapping> stack = new ArrayDeque<Mapping>();
      for (Mapping m : mappings) {
        // Find the closest ancestor of the current mapping:
        // An overlapping mapping is an ancestor of the current mapping, any
        // non-overlapping mappings are siblings (or cousins) and must be
        // closed in the reverse order of when they encountered.
        while (!stack.isEmpty() && !isOverlapped(stack.peek(), m)) {
          Mapping previous = stack.pop();
          maybeVisit(v, previous);
        }

        // Any gaps between the current line position and the start of the
        // current mapping belong to the parent.
        Mapping parent = stack.peek();
        maybeVisitParent(v, parent, m);

        stack.push(m);
      }

      // There are no more children to be had, simply close the remaining
      // mappings in the reverse order of when they encountered.
      while (!stack.isEmpty()) {
        Mapping m = stack.pop();
        maybeVisit(v, m);
      }
    }

    /**
     * @return The line adjusted for the prefix position.
     */
    private int getAdjustedLine(FilePosition p) {
      return p.getLine() + prefixPosition.getLine();
    }

    /**
     * @return The column adjusted for the prefix position.
     */
    private int getAdjustedCol(FilePosition p) {
      int rawLine = p.getLine();
      int rawCol = p.getColumn();
      // Only the first line needs the character position adjusted.
      return (rawLine != 0)
          ? rawCol : rawCol + prefixPosition.getColumn();
    }

    /**
     * @return Whether m1 ends before m2 starts.
     */
    private boolean isOverlapped(Mapping m1, Mapping m2) {
      // No need to use adjusted values here, relative positions are sufficient.
      int l1 = m1.endPosition.getLine();
      int l2 = m2.startPosition.getLine();
      int c1 = m1.endPosition.getColumn();
      int c2 = m2.startPosition.getColumn();

      return (l1 == l2 && c1 >= c2) || l1 > l2;
    }

    /**
     * Write any needed entries from the current position to the end of the
     * provided mapping.
     */
    private void maybeVisit(MappingVisitor v, Mapping m) throws IOException {
      int nextLine = getAdjustedLine(m.endPosition);
      int nextCol = getAdjustedCol(m.endPosition);
      // If this anything remaining in this mapping beyond the
      // current line and column position, write it out now.
      if (line < nextLine || (line == nextLine && col < nextCol)) {
        visit(v, m, nextLine, nextCol);
      }
    }

    /**
     * Write any needed entries to complete the provided mapping.
     */
    private void maybeVisitParent(MappingVisitor v, Mapping parent, Mapping m)
        throws IOException {
      int nextLine = getAdjustedLine(m.startPosition);
      int nextCol = getAdjustedCol(m.startPosition);
      // If the previous value is null, no mapping exists.
      Preconditions.checkState(line < nextLine || col <= nextCol);
      if (line < nextLine || (line == nextLine && col < nextCol)) {
        visit(v, parent, nextLine, nextCol);
      }
    }

    /**
     * Write any entries needed between the current position the next position
     * and update the current position.
     */
    private void visit(MappingVisitor v, Mapping m,
        int nextLine, int nextCol)
        throws IOException {
      Preconditions.checkState(line <= nextLine);
      Preconditions.checkState(line < nextLine || col < nextCol);

      if (line == nextLine && col == nextCol) {
        // Nothing to do.
        Preconditions.checkState(false);
        return;
      }

      v.visit(m, line, col, nextLine, nextCol);

      line = nextLine;
      col = nextCol;
    }
  }

  /**
   * Appends the index source map to the given buffer.
   *
   * @param out The stream to which the map will be appended.
   * @param name The name of the generated source file that this source map
   *   represents.
   * @param sections An ordered list of map sections to include in the index.
   * @throws IOException
   */
  @Override
  public void appendIndexMapTo(
      Appendable out, String name, List<SourceMapSection> sections)
      throws IOException {
    // Add the header fields.
    out.append("{\n");
    appendFirstField(out, "version", "3");
    appendField(out, "file", escapeString(name));

    // Add the line character maps.
    appendFieldStart(out, "sections");
    out.append("[\n");
    boolean first = true;
    int line = 0, column = 0;
    for (SourceMapSection section : sections) {
      if (first) {
        first = false;
      } else {
        out.append(",\n");
      }
      out.append("{\n");
      appendFirstField(out, "offset",
          offsetValue(section.getLine(), section.getColumn()));
      if (section.getSectionType() == SourceMapSection.SectionType.URL) {
        appendField(out, "url", escapeString(section.getSectionValue()));
      } else if (section.getSectionType() == SourceMapSection.SectionType.MAP) {
        appendField(out, "map", section.getSectionValue());
      } else {
        throw new IOException("Unexpected section type");
      }
      out.append("\n}");
    }

    out.append("\n]");
    appendFieldEnd(out);

    out.append("\n}\n");
  }

  private CharSequence offsetValue(int line, int column) throws IOException {
    StringBuilder out = new StringBuilder();
    out.append("{\n");
    appendFirstField(out, "line", String.valueOf(line));
    appendField(out, "column", String.valueOf(column));
    out.append("\n}");
    return out;
  }

  private int getSourceId(String sourceName) {
    if (sourceName != lastSourceFile) {
      lastSourceFile = sourceName;
      Integer index = sourceFileMap.get(sourceName);
      if (index != null) {
        lastSourceFileIndex = index;
      } else {
        lastSourceFileIndex = sourceFileMap.size();
        sourceFileMap.put(sourceName, lastSourceFileIndex);
      }
    }
    return lastSourceFileIndex;
  }

  private int getNameId(String symbolName) {
    int originalNameIndex;
    Integer index = originalNameMap.get(symbolName);
    if (index != null) {
      originalNameIndex = index;
    } else {
      originalNameIndex = originalNameMap.size();
      originalNameMap.put(symbolName, originalNameIndex);
    }
    return originalNameIndex;
  }

  private class LineMapper implements MappingVisitor {
    // The destination.
    private final Appendable out;

    private int previousLine = -1;
    private int previousColumn = 0;

    // Previous values used for storing relative ids.
    private int previousSourceFileId;
    private int previousSourceLine;
    private int previousSourceColumn;
    private int previousNameId;

    LineMapper(Appendable out) {
      this.out = out;
    }

    /**
     * As each segment is visited write out the appropriate line mapping.
     */
    @Override
    public void visit(Mapping m, int line, int col, int nextLine, int nextCol)
      throws IOException {

      int id = (m != null) ? m.id : UNMAPPED;

      if (previousLine != line) {
        previousColumn = 0;
      }

      if (line != nextLine || col != nextCol) {
        if (previousLine == line) { // not the first entry for the line
          out.append(',');
        }
        writeEntry(m, col);
        previousLine = line;
        previousColumn = col;
      }

      for (int i = line; i <= nextLine; i++) {
        if (i == nextLine) {
          break;
        }

        closeLine(false);
        openLine(false);
      }
    }

    /**
     * Writes an entry for the given column (of the generated text) and
     * associated mapping.
     * The values are stored as relative to the last seen values for each
     * field and encoded as Base64VLQs.
     */
    void writeEntry(Mapping m, int column) throws IOException {
      // The relative generated column number
      Base64VLQ.encode(out, column - previousColumn);
      previousColumn = column;
      if (m != null) {
        // The relative source file id
        int sourceId = getSourceId(m.sourceFile);
        Base64VLQ.encode(out, sourceId - previousSourceFileId);
        previousSourceFileId = sourceId;

        // The relative source file line and column
        int srcline = m.originalPosition.getLine();
        int srcColumn = m.originalPosition.getColumn();
        Base64VLQ.encode(out, srcline - previousSourceLine);
        previousSourceLine = srcline;

        Base64VLQ.encode(out, srcColumn - previousSourceColumn);
        previousSourceColumn = srcColumn;

        if (m.originalName != null) {
          // The relative id for the associated symbol name
          int nameId = getNameId(m.originalName);
          Base64VLQ.encode(out, (nameId - previousNameId));
          previousNameId = nameId;
        }
      }
    }

    // Append the line mapping entries.
    void appendLineMappings() throws IOException {
      // Start the first line.
      openLine(true);

      (new MappingTraversal()).traverse(this);

      // And close the final line.
      closeLine(true);
    }

    /**
     * Begin the entry for a new line.
     */
    private void openLine(boolean firstEntry) throws IOException {
      if (firstEntry) {
        out.append('\"');
      }
    }

    /**
     * End the entry for a line.
     */
    private void closeLine(boolean finalEntry) throws IOException {
      out.append(';');
      if (finalEntry) {
        out.append('\"');
      }
    }
  }

}
