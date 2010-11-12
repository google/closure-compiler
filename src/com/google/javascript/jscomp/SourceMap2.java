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

package com.google.javascript.jscomp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 * Collects information mapping the generated (compiled) source back to
 * its original source for debugging purposes.
 *
 * @see CodeConsumer
 * @see CodeGenerator
 * @see CodePrinter
 *
 */
public class SourceMap2 implements SourceMap {

  private boolean validate = false;

  private final static int UNMAPPED = -1;

  /**
   *  A map used to convert integer values in the range 0-63 to their base64
   *  values.
   */
  private static final String BASE64_MAP =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
      "abcdefghijklmnopqrstuvwzyz" +
      "0123456789+/";

  /**
   * A pre-order traversal ordered list of mappings stored in this map.
   */
  private List<Mapping> mappings = Lists.newArrayList();

  /**
   * A map of source names to source name index
   */
  private LinkedHashMap<String, Integer> source_file_map =
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
  private Position offsetPosition = new Position(0, 0);

  /**
   * The position that the current source map is offset in the
   * generated the compiled source file by the addition of a
   * an output wrapper prefix.
   */
  private Position prefixPosition = new Position(0, 0);

  /**
   * {@inheritDoc}
   */
  public void reset() {
    mappings.clear();
    lastMapping = null;
    source_file_map.clear();
    lastSourceFile = null;
    lastSourceFileIndex = -1;
    offsetPosition = new Position(0, 0);
    prefixPosition = new Position(0, 0);
  }

  /**
   * @param validate Whether to perform (potentially costly) validation on the
   * generated source map.
   */
  @VisibleForTesting
  void validate(boolean validate) {
    this.validate = validate;
  }

  /**
   * Sets the prefix used for wrapping the generated source file before
   * it is written. This ensures that the source map is adjusted for the
   * change in character offsets.
   *
   * @param prefix The prefix that is added before the generated source code.
   */
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

    prefixPosition = new Position(prefixLine, prefixIndex);
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
  public void setStartingPosition(int offsetLine, int offsetIndex) {
    Preconditions.checkState(offsetLine >= 0);
    Preconditions.checkState(offsetIndex >= 0);
    offsetPosition = new Position(offsetLine, offsetIndex);
  }

  /**
   * Adds a mapping for the given node.  Mappings must be added in order.
   *
   * @param node The node that the new mapping represents.
   * @param startPosition The position on the starting line
   * @param endPosition The position on the ending line.
   */
  public void addMapping(Node node, Position startPosition, Position endPosition) {
    String sourceFile = (String)node.getProp(Node.SOURCENAME_PROP);

    // If the node does not have an associated source file or
    // its line number is -1, then the node does not have sufficient
    // information for a mapping to be useful.
    if (sourceFile == null || node.getLineno() < 0) {
      return;
    }

    if (sourceFile != lastSourceFile) {
      lastSourceFile = sourceFile;
      Integer index = source_file_map.get(sourceFile);
      if (index != null) {
        lastSourceFileIndex = index;
      } else {
        lastSourceFileIndex = source_file_map.size();
        source_file_map.put(sourceFile, lastSourceFileIndex);
      }
    }

    // Create the new mapping.
    Mapping mapping = new Mapping();
    mapping.sourceFile = lastSourceFileIndex;
    mapping.originalPosition = new Position(node.getLineno(), node.getCharno());

    String originalName = (String)node.getProp(Node.ORIGINALNAME_PROP);
    if (originalName != null) {
      mapping.originalName = originalName;
    }

    if (offsetPosition.getLineNumber() == 0
        && offsetPosition.getCharacterIndex() == 0) {
      mapping.startPosition = startPosition;
      mapping.endPosition = endPosition;
    } else {
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
    }

    // Validate the mappings are in a proper order.
    if (lastMapping != null) {
      int lastLine = lastMapping.startPosition.getLineNumber();
      int lastColumn = lastMapping.startPosition.getCharacterIndex();
      int nextLine = mapping.startPosition.getLineNumber();
      int nextColumn = mapping.startPosition.getCharacterIndex();
      Preconditions.checkState(nextLine > lastLine
          || (nextLine == lastLine && nextColumn >= lastColumn),
          "Incorrect source mappings order, previous : (%s,%s)\n"
          + "new : (%s,%s)\nnode : %s",
          lastLine, lastColumn, nextLine, nextColumn, node);
    }

    lastMapping = mapping;
    mappings.add(mapping);
  }

  /**
   * Writes out the source map in the following format (line numbers are for
   * reference only and are not part of the format):
   *
   * 1.  {
   * 2.    version: 2,
   * 3.    file: “out.js”
   * 4.    lineCount: 2
   * 5.    lineMaps: [
   * 6.        "ABAAA",
   * 7.        "ABAA"
   * 8.     ],
   * 9.    sourceRoot: "",
   * 10.   sources: ["foo.js", "bar.js"],
   * 11.   names: ["src", "maps", "are", "fun"],
   * 12.   mappings: [
   * 13.       [1, 1, 2, 4],
   * 14.       [2, 1, 2, "yack"],
   * 15.   ],
   * 16.  }
   *
   * Line 1: The entire file is a single JSON object
   * Line 2: File revision (always the first entry in the object)
   * Line 3: The name of the file that this source map is associated with.
   * Line 4: The number of lines represented in the sourcemap.
   * Line 5: “lineMaps” field is a JSON array, where each entry represents a
   *     line in the generated text.
   * Line 6: A line entry, representing a series of line segments, where each
   *     segment encodes an mappings-id and repetition count.
   * Line 9: An optional source root, useful for relocating source files on a
   *     server or removing repeated prefix values in the “sources” entry.
   * Line 10: A list of sources used by the “mappings” entry relative to the
   *     sourceRoot.
   * Line 11: A list of symbol names used by the “mapping” entry.  This list
   *     may be incomplete.
   * Line 12: The mappings field.
   * Line 13: Each entry represent a block of text in the original source, and
   *     consists four fields:
   *     The source file name
   *     The line in the source file the text begins
   *     The column in the line that the text begins
   *     An optional name (from the original source) that this entry represents.
   *     This can either be an string or index into the “names” field.
   */
  public void appendTo(Appendable out, String name) throws IOException {
    int maxLine = prepMappings();

    // Add the header fields.
    out.append("{\n");
    appendFirstField(out, "version", "2");
    appendField(out, "file", escapeString(name));
    appendField(out, "lineCount", String.valueOf(maxLine + 1));

    // Add the line character maps.
    appendFieldStart(out, "lineMaps");
    out.append("[");
    (new LineMapper(out)).appendLineMappings();
    out.append("]");
    appendFieldEnd(out);

    // Files names
    appendFieldStart(out, "sources");
    out.append("[");
    addSourceNameMap(out);
    out.append("]");
    appendFieldEnd(out);

    // Add the mappings themselves.
    appendFieldStart(out, "mappings");
    out.append("[");
    (new MappingWriter()).appendMappings(out);
    out.append("]");
    appendFieldEnd(out);

    out.append("\n}\n");
  }

  /**
   * Writes the source name map to 'out'.
   */
  private void addSourceNameMap(Appendable out) throws IOException {
    int i = 0;
    for (Entry<String, Integer> entry : source_file_map.entrySet()) {
      String key = entry.getKey();
      if (i != 0) {
        out.append(",");
      }
      out.append("\"");
      out.append(key);
      out.append("\"");
      i++;
    }
  }

  /**
   * Escapes the given string for JSON.
   */
  private static String escapeString(String value) {
    return CodeGenerator.escapeToDoubleQuotedJsString(value);
  }

  // Source map field helpers.

  private void appendFirstField(Appendable out, String name, String value)
      throws IOException {
    out.append("\"");
    out.append(name);
    out.append("\"");
    out.append(":");
    out.append(value);
  }

  private void appendField(Appendable out, String name, String value)
      throws IOException {
    out.append(",\n");
    out.append("\"");
    out.append(name);
    out.append("\"");
    out.append(":");
    out.append(value);
  }

  private void appendFieldStart(Appendable out, String name)
      throws IOException {
    appendField(out, name, "");
  }

  @SuppressWarnings("unused")
  private void appendFieldEnd(Appendable out)
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
    for (Mapping m : mappings) {
      if (m.used) {
        m.id = id++;
        int endPositionLine = m.endPosition.getLineNumber();
        maxLine = Math.max(maxLine, endPositionLine);
      }
    }

    // Adjust for the prefix.
    return maxLine + prefixPosition.getLineNumber();
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
    int sourceFile;

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
    private int lastLine = 0;
    private String lastLineString = String.valueOf(0);

    /**
     * Appends the mapping to the given buffer.
     */
    private void appendMappingTo(
        Mapping m, Appendable out) throws IOException {
      out.append("[");

      out.append(String.valueOf(m.sourceFile));
      out.append(",");

      int line = m.originalPosition.getLineNumber();
      if (line != lastLine) {
        lastLineString = String.valueOf(line);
      }
      String lineValue = lastLineString;

      out.append(lineValue);

      out.append(",");
      out.append(String.valueOf(
          m.originalPosition.getCharacterIndex()));

      if (m.originalName != null) {
        out.append(",");
        out.append(escapeString(m.originalName));
      }

      out.append("],\n");
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

  private class LineMapper implements MappingVisitor {
    // The destination.
    private final Appendable out;

    // Whether the current line has had a value written yet.
    private int lastId = UNMAPPED;

    LineMapper(Appendable out) {
      this.out = out;
    }

    /**
     * As each segment is visited write out the appropriate line mapping.
     */
    public void visit(Mapping m, int line, int col, int nextLine, int nextCol)
      throws IOException {

      int id = (m != null) ? m.id : UNMAPPED;

      for (int i = line; i <= nextLine; i++) {
        if (i == nextLine) {
          closeEntry(id, nextCol-col);
          break;
        }

        closeLine(false);
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
      closeLine(true);
    }

    /**
     * Begin the entry for a new line.
     */
    private void openLine() throws IOException {
      out.append("\"");
      // The first id of the line is not relative.
      this.lastId = 0;
    }

    /**
     * End the entry for a line.
     */
    private void closeLine(boolean finalEntry) throws IOException {
      if (finalEntry) {
        out.append("\"");
      } else {
        out.append("\",\n");
      }
    }

    private void closeEntry(int id, int reps) throws IOException {
      if (reps == 0) {
        return;
      }

      StringBuilder sb = new StringBuilder();
      LineMapEncoder.encodeEntry(sb, id, lastId, reps);

      if (validate) {
        LineMapDecoder.LineEntry entry = LineMapDecoder.decodeLineEntry(
            sb.toString(), lastId);
        Preconditions.checkState(entry.id == id && entry.reps == reps,
            "expected (%s,%s) but got (%s,%s)",
            id, reps, entry.id, entry.reps);
      }

      out.append(sb);
      lastId = id;
    }
  }

  @VisibleForTesting
  static class LineMapEncoder {
    /**
     * The source map line map is consists of a series of entries each
     * representing a map entry and a repetition count of that entry.
     *
     * @param out The entry destination.
     * @param id  The id for the entry.
     * @param lastId The previous id written, used to generate a relative
     *     map id.
     * @param reps The number of times the id is repeated in the map.
     * @throws IOException
     */
    static void encodeEntry(Appendable out, int id, int lastId, int reps)
        throws IOException {
      Preconditions.checkState(reps > 0);
      int relativeIdLength = getRelativeMappingIdLength(id, lastId);
      int relativeId = getRelativeMappingId(id, relativeIdLength, lastId);

      String relativeIdString = valueToBase64(relativeId, relativeIdLength);

      // If we can, we use a single base64 digit to encode both the id length
      // and the repetition count.  The current best division of the base64
      // digit (which has 6 bits) is 2 bits for the id length (1-4 digits) and
      // 4 bit for the repetition count (1-16 repetitions).  If either of these
      // two values are exceeded a "!" is written (a non-base64 character) to
      // signal the a full base64 character is used for repetition count and
      // the mapping id length.  As the repetition count can exceed 64, we
      // allow the "escape" ("!") to be repeated to signal additional
      // repetition count length characters.  It is extremely unlikely that
      // mapping id length will exceed 64 base64 characters in length so
      // additional "!" don't signal additional id length characters.
      if (reps > 16 || relativeIdLength > 4) {
        String repsString = valueToBase64(reps -1, 1);
        for (int i = 0; i < repsString.length(); i++) {
          // TODO(johnlenz): update this to whatever is agreed to.
          out.append('!');
        }
        String sizeId = valueToBase64(relativeIdString.length() -1, 1);

        out.append(sizeId);
        out.append(repsString);
      } else {
        int prefix = ((reps -1) << 2) + (relativeIdString.length() -1);
        Preconditions.checkState(prefix < 64 && prefix >= 0,
            "prefix (%s) reps(%s) map id size(%s)",
            prefix, reps, relativeIdString.length());
        out.append(valueToBase64(prefix, 1));
      }
      out.append(relativeIdString);
    }

    /**
     * @param idLength the length relative id, when encoded in as a base64
     *     value. @see #getRelativeMappingIdLength
     * @return A value relative to the the lastId.  Negative value are
     * represented as a two-complement value.
     */
    static int getRelativeMappingId(int id, int idLength, int lastId) {
      int base = 1 << (idLength *6);
      int relativeId = id - lastId;
      return (relativeId < 0) ? relativeId + base : relativeId;
    }

    /**
     * @return The length of the base64 number needed to include the id.
     */
    static int getRelativeMappingIdLength(int rawId, int lastId) {
      Preconditions.checkState(rawId >= 0 || rawId == UNMAPPED);
      int relativeId = rawId - lastId;
      int id = (relativeId < 0 ? Math.abs(relativeId) -1 : relativeId) << 1;
      int digits = 1;
      int base = 64;
      while (id >= base) {
        digits++;
        base *= 64;
      }
      return digits;
    }

    /**
     * @return return the base64 number encoding the provided value,
     *    padded if necessary to create a number with the given minimum length.
     */
    static String valueToBase64(int value, int minimumSize) {
      int size = 0;
      char chars[] = new char[4];
      do {
        int charValue = value & 63; // base64 chars
        value = value >>> 6; // get the next value;
        chars[size++] = BASE64_MAP.charAt(charValue);
      } while (value > 0);

      StringBuilder sb = new StringBuilder(size);

      while (minimumSize > size) {
        sb.append(BASE64_MAP.charAt(0));
        minimumSize--;
      }
      while (size > 0) {
        sb.append(chars[--size]);
      }
      return sb.toString();
    }
  }

  /**
   * A line mapping decoder class used for testing and validation.
   */
  @VisibleForTesting
  static class LineMapDecoder {
    private static LineEntry decodeLineEntry(String in, int lastId) {
      return decodeLineEntry(new StringParser(in), lastId);
    }

    private static LineEntry decodeLineEntry(StringParser reader, int lastId) {
      int repDigits = 0;

      // Determine the number of digits used for the repetition count.
      // Each "!" indicates another base64 digit.
      for (char peek = reader.peek(); peek == '!'; peek = reader.peek()) {
        repDigits++;
        reader.next(); // consume the "!"
      }

      int idDigits = 0;
      int reps = 0;
      if (repDigits == 0) {
        // No repetition digit escapes, so the next character represents the
        // number of digits in the id (bottom 2 bits) and the number of
        // repetitions (top 4 digits).
        char digit = reader.next();
        int value = addBase64Digit(digit, 0);
        reps = (value >> 2);
        idDigits = (value & 3);
      } else {
        char digit = reader.next();
        idDigits = addBase64Digit(digit, 0);

        int value = 0;
        for (int i = 0; i < repDigits; i++) {
          digit = reader.next();
          value = addBase64Digit(digit, value);
        }
        reps = value;
      }

      // Adjust for 1 offset encoding.
      reps += 1;
      idDigits += 1;

      // Decode the id token.
      int value = 0;
      for (int i = 0; i < idDigits; i++) {
        char digit = reader.next();
        value = addBase64Digit(digit, value);
      }
      int mappingId = getIdFromRelativeId(value, idDigits, lastId);
      return new LineEntry(mappingId, reps);
    }

    static List<Integer> decodeLine(String lineSource) {
      return decodeLine(new StringParser(lineSource));
    }

    static private List<Integer> decodeLine(StringParser reader) {
      List<Integer> result = Lists.newArrayListWithCapacity(512);
      int lastId = 0;
      do {
        LineEntry entry = decodeLineEntry(reader, lastId);
        lastId = entry.id;

        for (int i=0; i < entry.reps; i++) {
          result.add(entry.id);
        }
      } while(reader.hasNext());

      return result;
    }

    /**
     * Build base64 number a digit at a time, most significant digit first.
     */
    private static int addBase64Digit(char digit, int previousValue) {
      return (previousValue * 64) + BASE64_MAP.indexOf(digit);
    }

    /**
     * @return the id from the relative id.
     */
    static int getIdFromRelativeId(int rawId, int digits, int lastId) {
      // The value range depends on the number of digits
      int base = 1 << (digits * 6);
      return ((rawId >= base/2) ? rawId - base : rawId) + lastId;
      // return (rawId - (base/2)) + lastId;
    }

    static class LineEntry {
      final int id;
      final int reps;
      public LineEntry(int id, int reps) {
        this.id = id;
        this.reps = reps;
      }
    }

    /**
     * A simple class for maintaining the current location
     * in the input.
     */
    static class StringParser {
      final String content;
      int current = 0;

      StringParser(String content) {
        this.content = content;
      }

      char next() {
        return content.charAt(current++);
      }

      char peek() {
        return content.charAt(current);
      }

      boolean hasNext() {
        return  current < content.length() -1;
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
}
