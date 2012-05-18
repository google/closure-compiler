/*
 * Copyright 2009 The Closure Compiler Authors.
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

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Collects information mapping the generated (compiled) source back to
 * its original source for debugging purposes.
 *
 */
public class SourceMapGeneratorV1 implements SourceMapGenerator {

  private final static int UNMAPPED = -1;


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
     * The input source file.
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

  private class MappingWriter {
    /**
     * Cache of escaped source file name.
     */
    private String lastSourceFile = null;
    private String lastSourceFileEscaped = null;
    private int lastLine = 0;
    private String lastLineString = String.valueOf(0);

    /**
     * Appends the mapping to the given buffer.
     */
    private void appendMappingTo(
        Mapping m, Appendable out) throws IOException {
      out.append("[");

      String sourceFile = m.sourceFile;
      // The source file rarely changes, so cache the escaped string.
      String escapedSourceFile;
      if (lastSourceFile != sourceFile) { // yes, s1 != s2, not !s1.equals(s2)
        lastSourceFile = sourceFile;
        lastSourceFileEscaped = escapeString(sourceFile);
      }
      escapedSourceFile = lastSourceFileEscaped;

      out.append(escapedSourceFile);
      out.append(",");

      int line = m.originalPosition.getLine();
      if (line != lastLine) {
        lastLineString = String.valueOf(line);
      }
      String lineValue = lastLineString;

      out.append(lineValue);

      out.append(",");
      out.append(String.valueOf(
          m.originalPosition.getColumn()));

      if (m.originalName != null) {
        out.append(",");
        out.append(escapeString(m.originalName));
      }

      out.append("]\n");
    }

    /**
     * Add used mappings to the supplied Appendable.
     */
    void appendMappings(Appendable out) throws IOException {
      for (Mapping m : mappings) {
        if (m.used) {
          appendMappingTo(m, out);
        }
      }
    }
  }

  /**
   * A pre-order traversal ordered list of mappings stored in this map.
   */
  private List<Mapping> mappings = Lists.newArrayList();

  /**
   * For validation store the start of the last mapping added.
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
   * Escapes the given string for JSON.
   */
  private static String escapeString(String value) {
    return Util.escapeString(value);
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

    // Create the new mapping.
    Mapping mapping = new Mapping();
    mapping.sourceFile = sourceName;
    mapping.originalPosition = sourceStartPosition;
    mapping.originalName = symbolName; // may be null

    // NOTE: When multiple outputs are concatenated together, the positions in
    // the mapping are relative to offsetPosition.
    if (offsetPosition.getLine() == 0
        && offsetPosition.getColumn() == 0) {
      mapping.startPosition = startPosition;
      mapping.endPosition = endPosition;
    } else {
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

      mapping.startPosition =
          new FilePosition(startPosition.getLine() + offsetLine,
                       startPosition.getColumn() + startOffsetPosition);

      mapping.endPosition =
          new FilePosition(endPosition.getLine() + offsetLine,
                       endPosition.getColumn() + endOffsetPosition);
    }

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

  /**
   * Sets the prefix used for wrapping the generated source file before
   * it is output. This ensures that the source map is adjusted as
   * needed.
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
   * Sets the source code that exists in the buffer to which the
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
   * Resets the source map for reuse for the generation of a new source file.
   */
  @Override
  public void reset() {
    mappings = Lists.newArrayList();
    lastMapping = null;
    offsetPosition = new FilePosition(0, 0);
    prefixPosition = new FilePosition(0, 0);
  }

  /**
   * Appends the source map in LavaBug format to the given buffer.
   *
   * @param out The stream to which the map will be appended.
   * @param name The name of the generated source file that this source map
   *   represents.
   */
  @Override
  public void appendTo(Appendable out, String name) throws IOException {
    // Write the mappings out to the file. The format of the generated
    // source map is three sections, each delimited by a magic comment.
    //
    // The first section contains an array for each line of the generated
    // code, where each element in the array is the ID of the mapping which
    // best represents the index-th character found on that line of the
    // generated source code.
    //
    // The second section contains an array per generated line. Unused.
    //
    // The third and final section contains an array per line, each of which
    // represents a mapping with a unique ID. The mappings are added in order.
    // The array itself contains a tuple representing
    // ['source file', line, col (, 'original name')]
    //
    // Example for 2 lines of generated code (with line numbers added for
    // readability):
    //
    // 1)  /** Begin line maps. **/{ "count": 2 }
    // 2)  [0,0,0,0,0,0,1,1,1,1,2]
    // 3)  [2,2,2,2,2,2,3,4,4,4,4,4]
    // 4)  /** Begin file information. **/
    // 5)  []
    // 6)  []
    // 7)  /** Begin mapping definitions. **/
    // 8)  ["a.js", 1, 34]
    // 9)  ["a.js", 5, 2]
    // 10) ["b.js", 1, 3, "event"]
    // 11) ["c.js", 1, 4]
    // 12) ["d.js", 3, 78, "foo"]

    int maxLine = prepMappings();

    // Add the line character maps.
    out.append("/** Begin line maps. **/{ \"file\" : ");
    out.append(escapeString(name));
    out.append(", \"count\": ");
    out.append(String.valueOf(maxLine + 1));
    out.append(" }\n");
    (new LineMapper(out)).appendLineMappings();

    // Add the source file maps.
    out.append("/** Begin file information. **/\n");

    // This section is unused but we need one entry per line to
    // prevent changing the format.
    for (int i = 0; i <= maxLine; ++i) {
      out.append("[]\n");
    }

    // Add the mappings themselves.
    out.append("/** Begin mapping definitions. **/\n");

    (new MappingWriter()).appendMappings(out);
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

  private class LineMapper implements MappingVisitor {
    // The destination.
    private final Appendable out;

    // Whether the current line has had a value written yet.
    private boolean firstChar = true;

    private final static String UNMAPPED_STRING = "-1";

    private int lastId = UNMAPPED;
    private String lastIdString = UNMAPPED_STRING;

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
      if (lastId != id) {
        // Prevent the creation of unnecessary temporary stings for often
        // repeated values.
        lastIdString = (id == UNMAPPED) ? UNMAPPED_STRING : String.valueOf(id);
        lastId = id;
      }
      String idString = lastIdString;

      for (int i = line; i <= nextLine; i++) {
        if (i == nextLine) {
          for (int j = col; j < nextCol; j++) {
            addCharEntry(idString);
          }
          break;
        }

        closeLine();
        openLine();

        // Set the starting location for the next line.
        col = 0;
      }
    }

    // Append the line mapping entries.
    void appendLineMappings() throws IOException {
      // Start the first line.
      openLine();

      (new MappingTraversal()).traverse(this);

      // And close the final line.
      closeLine();
    }

    /**
     * Begin the entry for a new line.
     */
    private void openLine() throws IOException {
      if (out != null) {
        out.append("[");
        this.firstChar = true;
      }
    }

    /**
     * End the entry for a line.
     */
    private void closeLine() throws IOException {
      if (out != null) {
        out.append("]\n");
      }
    }

    /**
     * Add a new char position entry.
     * @param id The mapping id to record.
     */
    private void addCharEntry(String id) throws IOException {
      if (out != null) {
        if (firstChar) {
          firstChar = false;
        } else {
          out.append(",");
        }
        out.append(id);
      }
    }
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

  @Override
  public void validate(boolean validate) {
    // No additional validation to do.
  }

  @Override
  public void appendIndexMapTo(
      Appendable out, String name, List<SourceMapSection> appSections) {
    throw new UnsupportedOperationException();
  }
}
