/*
 * Copyright 2009 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Collects information mapping the generated (compiled) source back to
 * its original source for debugging purposes.
 *
 * @see CodeConsumer
 * @see CodeGenerator
 * @see CodePrinter
 *
*
 * @author johnlenz@google.com (John Lenz)
 */
public class SourceMap {

  /**
   * A mapping from a given position in an input source file to a given position
   * in the generated code.
   */
  static class Mapping {
    /**
     * A unique ID for this mapping for record keeping purposes.
     */
    int id;

    /**
     * The JSON escaped input source file.
     */
    String sourceFile;

    /**
     * The position of the code in the input source file. Both
     * the line number and the character index are indexed by
     * 1 for legacy reasons via the Rhino Node class.
     */
    Position originalPosition;

    /**
     * The starting position of the code in the generated source
     * file which this mapping represents. Indexed by 0.
     */
    Position startPosition;

    /**
     * The ending position of the code in the generated source
     * file which this mapping represents. Indexed by 0.
     */
    Position endPosition;

    /**
     * The JSON escaped original name of the token found at the position
     * represented by this mapping (if any).
     */
    String originalName;

    /**
     * Appends the mapping to the given buffer.
     */
    void appendTo(Appendable out) throws IOException {
      out.append("[");

      out.append(sourceFile);

      out.append(",");
      out.append(String.valueOf(originalPosition.getLineNumber()));

      out.append(",");
      out.append(String.valueOf(originalPosition.getCharacterIndex()));

      if (originalName != null) {
        out.append(",");
        out.append(originalName);
      }

      out.append("]");
    }
  }

  /**
   * A pre-order traversal ordered list of mappings stored in this map.
   */
  private List<Mapping> mappings = Lists.newArrayList();

  /**
   * The position that the current source map is offset in the
   * buffer being used to generated the compiled source file.
   */
  private Position offsetPosition = new Position(0, 0);

  /**
   * The position that the current source map is offset in the
   * generated the compiled source file by the addition of a
   * an output wrapper prefix.
   */
  private Position prefixPosition = new Position(0, 0);

  /**
   * Escapes the given string for JSON.
   */
  private static String escapeString(String value) {
    return CodeGenerator.escapeToDoubleQuotedJsString(value);
  }

  /**
   * Cache of escaped source file name.
   */
  private String lastSourceFile = null;
  private String lastSourceFileEscaped = null;

  /**
   * Adds a mapping for the given node.  Mappings must be added in order.
   *
   * @param node The node that the new mapping represents.
   * @param startPosition The position on the starting line
   * @param endPosition The position on the ending line.
   */
  void addMapping(Node node, Position startPosition, Position endPosition) {
    String sourceFile = (String)node.getProp(Node.SOURCEFILE_PROP);

    // If the node does not have an associated source file or
    // its line number is -1, then the node does not have sufficient
    // information for a mapping to be useful.
    if (sourceFile == null || node.getLineno() < 0) {
      return;
    }

    // The source file rarely changes, so cache the escaped string.
    String escapedSourceFile;
    if (lastSourceFile != sourceFile) {  // yes, "s1 != s2" not "!s1.equals(s2)"
      lastSourceFile = sourceFile;
      lastSourceFileEscaped = escapeString(sourceFile);
    }
    escapedSourceFile = lastSourceFileEscaped;

    // Create the new mapping.
    Mapping mapping = new Mapping();
    mapping.id = mappings.size();
    mapping.sourceFile = escapedSourceFile;
    mapping.originalPosition = new Position(node.getLineno(), node.getCharno());

    String originalName = (String)node.getProp(Node.ORIGINALNAME_PROP);
    if (originalName != null) {
      mapping.originalName = escapeString(originalName);
    }

    // If the mapping is found on the first line, we need to offset
    // its character position by the number of characters found on
    // the *last* line of the source file to which the code is
    // being generated.
    int offsetLine = offsetPosition.getLineNumber();
    int startOffsetPosition = offsetPosition.getCharacterIndex();
    int endOffsetPosition = offsetPosition.getCharacterIndex();

    if (startPosition.getLineNumber() > 0) {
      startOffsetPosition = 0;
    }

    if (endPosition.getLineNumber() > 0) {
      endOffsetPosition = 0;
    }

    mapping.startPosition =
        new Position(startPosition.getLineNumber() + offsetLine,
                     startPosition.getCharacterIndex() + startOffsetPosition);

    mapping.endPosition =
        new Position(endPosition.getLineNumber() + offsetLine,
                     endPosition.getCharacterIndex() + endOffsetPosition);

    mappings.add(mapping);
  }

  /**
   * Sets the prefix used for wrapping the generated source file before
   * it is output. This ensures that the source map is adjusted as
   * needed.
   *
   * @param prefix The prefix that is added before the generated source code.
   */
  void setWrapperPrefix(String prefix) {
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

    prefixPosition = new Position(prefixLine, prefixIndex);
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
  void setStartingPosition(int offsetLine, int offsetIndex) {
    // TODO(johnlenz): correct this.
    // Preconditions.checkState(mappings.isEmpty(),
    //     "Must be set prior to adding mappings");
    offsetPosition = new Position(offsetLine, offsetIndex);
  }

  /**
   * Resets the source map for reuse for the generation of a new source file.
   */
  void reset() {
    mappings = Lists.newArrayList();
    offsetPosition = new Position(0, 0);
    prefixPosition = new Position(0, 0);
  }

  /**
   * Scan the mappings and return the last line mapped.
   */
  private int findLastLine() {
    int maxLine = 0;
    for (Mapping mapping : mappings) {
      int endPositionLine = mapping.endPosition.getLineNumber();
      maxLine = Math.max(maxLine, endPositionLine);
    }
    // Adjust for the prefix.
    return maxLine + prefixPosition.getLineNumber();
  }

  /**
   * Appends the source map in LavaBug format to the given buffer.
   *
   * @param out The stream to which the map will be appended.
   * @param name The name of the generated source file that this source map
   *   represents.
   */
  public void appendTo(Appendable out, String name) throws IOException {
    // Write the mappings out to the file. The format of the generated
    // source map is three sections, each deliminated by a magic comment.
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

    int maxLine = findLastLine();

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

    for (Mapping mapping : mappings) {
      mapping.appendTo(out);
      out.append("\n");
    }
  }

  /**
   * A class to build the line/character to mappings section
   * of the source map.
   */
  private class LineMapper {
    // The destination.
    private final Appendable out;
    // The last line and column written
    private int line;
    private int col;
    // Whether the current line has had a value written yet.
    private boolean firstChar = true;

    private final static int UNMAPPED = -1;
    private final static String UNMAPPED_STRING = "-1";

    LineMapper(Appendable out) {
      this.out = out;
    }

    // Append the line mapping entries.
    void appendLineMappings() throws IOException {
      Preconditions.checkState(!mappings.isEmpty());

      // Start the first line.
      openLine();

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
          writeClosedMapping(previous);
        }

        // Any gaps between the current line position and the start of the
        // current mapping belong to the parent.
        Mapping parent = stack.peek();
        writeCharsBetween(parent, m);

        stack.push(m);
      }

      // There are no more children to be had, simply close the remaining
      // mappings in the reverse order of when they encountered.
      while (!stack.isEmpty()) {
        Mapping m = stack.pop();
        writeClosedMapping(m);
      }

      // And close the final line.
      closeLine();
    }

    /**
     * Begin the entry for a new line.
     */
    private void openLine() throws IOException {
      out.append("[");
      this.firstChar = true;
    }

    /**
     * End the entry for a line.
     */
    private void closeLine() throws IOException {
      out.append("]\n");
    }

    /**
     * Add a new char position entry.
     * @param id The mapping id to record.
     */
    private void addCharEntry(String id) throws IOException {
      if (firstChar) {
        firstChar = false;
      } else {
        out.append(",");
      }
      out.append(id);
    }

    /**
     * @return The line adjusted for the prefix position.
     */
    private int getAdjustedLine(Position p) {
      return p.getLineNumber() + prefixPosition.getLineNumber();
    }

    /**
     * @return The column adjusted for the prefix position.
     */
    private int getAdjustedCol(Position p) {
      int rawLine = p.getLineNumber();
      int rawCol = p.getCharacterIndex();
      // Only the first line needs the character position adjusted.
      return (rawLine != 0)
          ? rawCol : rawCol + prefixPosition.getCharacterIndex();
    }

    /**
     * @return Whether m1 ends before m2 starts.
     */
    private boolean isOverlapped(Mapping m1, Mapping m2) {
      // No need to use adjusted values here, relative positions are sufficient.
      int l1 = m1.endPosition.getLineNumber();
      int l2 = m2.startPosition.getLineNumber();
      int c1 = m1.endPosition.getCharacterIndex();
      int c2 = m2.startPosition.getCharacterIndex();

      return (l1 == l2 && c1 >= c2) || l1 > l2;
    }

    /**
     * Write any needed entries from the current position to the end of the
     * provided mapping.
     */
    private void writeClosedMapping(Mapping m) throws IOException {
      int nextLine = getAdjustedLine(m.endPosition);
      int nextCol = getAdjustedCol(m.endPosition);
      // If this anything remaining in this mapping beyond the
      // current line and column position, write it out now.
      if (line < nextLine || (line == nextLine && col < nextCol)) {
        writeCharsUpTo(nextLine, nextCol, m.id);
      }
    }

    /**
     * Write any needed entries to complete the provided mapping.
     */
    private void writeCharsBetween(Mapping prev, Mapping next)
        throws IOException {
      int nextLine = getAdjustedLine(next.startPosition);
      int nextCol = getAdjustedCol(next.startPosition);
      // If the previous value is null, no mapping exists use the special
      // "unmapped value"(-1).
      int id = (prev != null) ? prev.id : UNMAPPED;
      writeCharsUpTo(nextLine, nextCol, id);
    }

    /**
     * Write any entries needed between the current position the next position
     * and update the current position.
     */
    private void writeCharsUpTo(
        int nextLine, int nextCol, int id)
        throws IOException {
      Preconditions.checkState(line <= nextLine, "");
      Preconditions.checkState(line < nextLine || col <= nextCol);

      if (line == nextLine && col == nextCol) {
        // Nothing to do.
        return;
      }

      String idString = (id == UNMAPPED) ? UNMAPPED_STRING : String.valueOf(id);
      for (int i = line; i <= nextLine; i++) {
        if (i == nextLine) {
          for (int j = col; j < nextCol; j++) {
            addCharEntry(idString);
          }
          break;
        }

        closeLine();
        openLine();
      }

      line = nextLine;
      col = nextCol;
    }
  }
}
