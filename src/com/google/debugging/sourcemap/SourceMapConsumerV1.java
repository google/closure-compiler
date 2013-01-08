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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Lists;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Shorts;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for parsing and representing a SourceMap, as produced by the
 * Closure Compiler, Caja-Compiler, etc.
 */
public class SourceMapConsumerV1 implements SourceMapConsumer {
  private static final String LINEMAP_HEADER = "/** Begin line maps. **/";
  private static final String FILEINFO_HEADER =
      "/** Begin file information. **/";

  private static final String DEFINITION_HEADER =
      "/** Begin mapping definitions. **/";

  /**
   * Internal class for parsing the SourceMap. Used to maintain parser
   * state in an easy to use instance.
   */
  private static class ParseState {
    final String contents;
    int currentPosition = 0;

    ParseState(String contents) {
      this.contents = contents;
    }

    /** Reads a line, returning null at EOF. */
    String readLineOrNull() {
      if (currentPosition >= contents.length()) {
        return null;
      }
      int index = contents.indexOf('\n', currentPosition);
      if (index < 0) {
        index = contents.length();
      }
      String line = contents.substring(currentPosition, index);
      currentPosition = index + 1;
      return line;
    }

    /** Reads a line, throwing a parse exception at EOF. */
    String readLine() throws SourceMapParseException {
      String line = readLineOrNull();
      if (line == null) {
        fail("EOF");
      }
      return line;
    }

    /**
     * Reads a line and throws an parse exception if the line does not
     * equal the argument.
     */
    void expectLine(String expect) throws SourceMapParseException {
      String line = readLine();
      if (!expect.equals(line)) {
        fail("Expected " + expect + " got " + line);
      }
    }

    /**
     * Indicates that parsing has failed by throwing a parse exception.
     */
    void fail(String message) throws SourceMapParseException {
      throw new SourceMapParseException(message);
    }
  }

  /**
   * Mapping from a line number (0-indexed), to a list of mapping IDs, one for
   * each character on the line. For example, if the array for line 2 is
   * {@code [4,,,,5,6,,,7]}, then there will be the entry:
   *
   * <pre>
   * 1 => {4, 4, 4, 4, 5, 6, 6, 6, 7}
   * </pre>
   */
  private ImmutableList<ImmutableList<LineFragment>> characterMap;

  /**
   * Map of Mapping IDs to the actual mapping object.
   */
  private ImmutableList<SourceFile> mappings;

  /**
   * Parses the given contents containing a source map.
   */
  @Override
  public void parse(String contents) throws SourceMapParseException {
    ParseState parser = new ParseState(contents);
    try {
      parseInternal(parser);
    } catch (JSONException ex) {
      parser.fail("JSON parse exception: " + ex);
    }
  }

  /**
   * Parses the first section of the source map file that has character
   * mappings.
   * @param parser The parser to use
   * @param lineCount The number of lines in the generated JS
   * @return The max id found in the file
   */
  private int parseCharacterMap(
      ParseState parser, int lineCount,
      ImmutableList.Builder<ImmutableList<LineFragment>> characterMapBuilder)
      throws SourceMapParseException, JSONException {
    int maxID = -1;
    // [0,,,,,,1,2]
    for (int i = 0; i < lineCount; ++i) {
      String currentLine = parser.readLine();

      // Blank lines are allowed in the spec to indicate no mapping
      // information for the line.
      if (currentLine.isEmpty()) {
        continue;
      }

      ImmutableList.Builder<LineFragment> fragmentList =
          ImmutableList.builder();
      // We need the start index to initialize this, needs to be done in the
      // loop.
      LineFragment myLineFragment = null;

      JSONArray charArray = new JSONArray(currentLine);
      int lastID = -1;
      int startID = Integer.MIN_VALUE;
      List<Byte> currentOffsets = Lists.newArrayList();
      for (int j = 0; j < charArray.length(); ++j) {
        // Keep track of the current mappingID, if the next element in the
        // array is empty we reuse the existing mappingID for the column.
        int mappingID = lastID;
        if (!charArray.isNull(j)) {
          mappingID = charArray.optInt(j);
          if (mappingID > maxID) {
            maxID = mappingID;
          }
        }

        if (startID == Integer.MIN_VALUE) {
          startID = mappingID;
        } else {
          // If the difference is bigger than a byte we need to keep track of
          // a new line fragment with a new start value.
          if (mappingID - lastID > Byte.MAX_VALUE
              || mappingID - lastID < Byte.MIN_VALUE) {
            myLineFragment = new LineFragment(
                startID, Bytes.toArray(currentOffsets));
            currentOffsets.clear();
            // Start a new section.
            fragmentList.add(myLineFragment);
            startID = mappingID;
          } else {
            currentOffsets.add((byte) (mappingID - lastID));
          }
        }

        lastID = mappingID;
      }
      if (startID != Integer.MIN_VALUE) {
        myLineFragment = new LineFragment(
            startID, Bytes.toArray(currentOffsets));
        fragmentList.add(myLineFragment);
      }
      characterMapBuilder.add(fragmentList.build());
    }
    return maxID;
  }

  private class FileName {
    private final String dir;
    private final String name;

    FileName(String directory, String name) {
      this.dir = directory;
      this.name = name;
    }
  }

  /**
   * Split the file into a filename/directory pair.
   *
   * @param interner The interner to use for interning the strings.
   * @param input The input to split.
   * @return The pair of directory, filename.
   */
  private FileName splitFileName(
      Interner<String> interner, String input) {
    int hashIndex = input.lastIndexOf('/');
    String dir = interner.intern(input.substring(0, hashIndex + 1));
    String fileName = interner.intern(input.substring(hashIndex + 1));
    return new FileName(dir, fileName);
  }

  /**
   * Parse the file mappings section of the source map file.  This maps the
   * ids to the filename, line number and column number in the original
   * files.
   * @param parser The parser to get the data from.
   * @param maxID The maximum id found in the character mapping section.
   */
  private void parseFileMappings(ParseState parser, int maxID)
      throws SourceMapParseException, JSONException {
    // ['d.js', 3, 78, 'foo']
    // Intern the strings to save memory.
    Interner<String> interner = Interners.newStrongInterner();
    ImmutableList.Builder<SourceFile> mappingsBuilder = ImmutableList.builder();

    // Setup all the arrays to keep track of the various details about the
    // source file.
    ArrayList<Byte> lineOffsets = Lists.newArrayList();
    ArrayList<Short> columns = Lists.newArrayList();
    ArrayList<String> identifiers = Lists.newArrayList();

    // The indexes and details about the current position in the file to do
    // diffs against.
    String currentFile = null;
    int lastLine = -1;
    int startLine = -1;
    int startMapId = -1;
    for (int mappingId = 0; mappingId <= maxID; ++mappingId) {
      String currentLine = parser.readLine();
      JSONArray mapArray = new JSONArray(currentLine);
      if (mapArray.length() < 3) {
        parser.fail("Invalid mapping array");
      }

      // Split up the file and directory names to reduce memory usage.
      String myFile = mapArray.getString(0);
      int line = mapArray.getInt(1);
      if (!myFile.equals(currentFile) || (line - lastLine) > Byte.MAX_VALUE
          || (line - lastLine) < Byte.MIN_VALUE) {
        if (currentFile != null) {
          FileName dirFile = splitFileName(interner, currentFile);
          SourceFile.Builder builder = SourceFile.newBuilder()
              .setDir(dirFile.dir)
              .setFileName(dirFile.name)
              .setStartLine(startLine)
              .setStartMapId(startMapId)
              .setLineOffsets(lineOffsets)
              .setColumns(columns)
              .setIdentifiers(identifiers);
          mappingsBuilder.add(builder.build());
        }
        // Reset all the positions back to the start and clear out the arrays
        // to start afresh.
        currentFile = myFile;
        startLine = line;
        lastLine = line;
        startMapId = mappingId;
        columns.clear();
        lineOffsets.clear();
        identifiers.clear();
      }
      // We need to add on the columns and identifiers for all the lines, even
      // for the first line.
      lineOffsets.add((byte) (line - lastLine));
      columns.add((short) mapArray.getInt(2));
      identifiers.add(interner.intern(mapArray.optString(3, "")));
      lastLine = line;
    }
    if (currentFile != null) {
      FileName dirFile = splitFileName(interner, currentFile);
      SourceFile.Builder builder = SourceFile.newBuilder()
          .setDir(dirFile.dir)
          .setFileName(dirFile.name)
          .setStartLine(startLine)
          .setStartMapId(startMapId)
          .setLineOffsets(lineOffsets)
          .setColumns(columns)
          .setIdentifiers(identifiers);
      mappingsBuilder.add(builder.build());
    }
    mappings = mappingsBuilder.build();
  }

  private void parseInternal(ParseState parser)
      throws SourceMapParseException, JSONException {

    // /** Begin line maps. **/{ count: 2 }
    String headerCount = parser.readLine();
    Preconditions.checkArgument(headerCount.startsWith(LINEMAP_HEADER),
        "Expected %s", LINEMAP_HEADER);
    JSONObject countObject = new JSONObject(
        headerCount.substring(LINEMAP_HEADER.length()));
    if (!countObject.has("count")) {
      parser.fail("Missing 'count'");
    }

    int lineCount = countObject.getInt("count");
    if (lineCount <= 0) {
      parser.fail("Count must be >= 1");
    }
    ImmutableList.Builder<ImmutableList<LineFragment>> characterMapBuilder =
        ImmutableList.builder();
    int maxId = parseCharacterMap(parser, lineCount, characterMapBuilder);
    characterMap = characterMapBuilder.build();

    // /** Begin file information. **/
    parser.expectLine(FILEINFO_HEADER);

    // File information. Not used, so we just consume it.
    for (int i = 0; i < lineCount; i++) {
      parser.readLine();
    }

    // /** Begin mapping definitions. **/
    parser.expectLine(DEFINITION_HEADER);

    parseFileMappings(parser, maxId);
  }

  @Override
  public OriginalMapping getMappingForLine(int lineNumber, int columnIndex) {
    Preconditions.checkNotNull(characterMap, "parse() must be called first");

    if (lineNumber < 1 || lineNumber > characterMap.size() || columnIndex < 1) {
      return null;
    }

    List<LineFragment> lineFragments = characterMap.get(lineNumber - 1);
    if (lineFragments == null || lineFragments.isEmpty()) {
      return null;
    }

    int columnOffset = 0;
    // The code assumes everything past the end is the same as the last item
    // so we default to the last item in the line.
    LineFragment lastFragment = lineFragments.get(lineFragments.size() - 1);
    int mapId = lastFragment.valueAtColumn(lastFragment.length());
    for (LineFragment lineFragment : lineFragments) {
      int columnPosition = columnIndex - columnOffset;
      if (columnPosition <= lineFragment.length()) {
        mapId = lineFragment.valueAtColumn(columnPosition);
        break;
      }
      columnOffset += lineFragment.length();
    }

    if (mapId < 0) {
      return null;
    }

    return getMappingFromId(mapId);
  }

  /**
   * Do a binary search for the correct mapping array to use.
   *
   * @param mapId The mapping array to find
   * @return The source file mapping to use.
   */
  private SourceFile binarySearch(int mapId) {
    int lower = 0;
    int upper = mappings.size() - 1;

    while (lower <= upper) {
      int middle = lower + (upper - lower) / 2;
      SourceFile middleCompare = mappings.get(middle);
      if (mapId < middleCompare.getStartMapId()) {
        upper = middle - 1;
      } else if (mapId < (middleCompare.getStartMapId()
            + middleCompare.getLength())) {
        return middleCompare;
      } else {
        lower = middle + 1;
      }
    }

    return null;
  }

  /**
   * Find the original mapping for the specified mapping id.
   *
   * @param mapID The mapID to lookup.
   * @return The originalMapping protocol buffer for the id.
   */
  private OriginalMapping getMappingFromId(int mapID) {
    SourceFile match = binarySearch(mapID);
    if (match == null) {
      return null;
    }
    int pos = mapID - match.getStartMapId();
    return match.getOriginalMapping(pos);
  }

  /**
   * Keeps track of the information about the line in a more compact way.  It
   * represents a fragment of the line starting at a specific index and then
   * looks at offsets from that index stored as a byte, this dramatically
   * reduces the memory usage of this array.
   */
  private static final class LineFragment {
    private final int startIndex;
    private final byte[] offsets;

    /**
     * Create a new line fragment to store information about.
     *
     * @param startIndex The start index for this line.
     * @param offsets The byte array of offsets to store.
     */
    LineFragment(int startIndex, byte[] offsets) {
      this.startIndex = startIndex;
      this.offsets = offsets;
    }

    /**
     * The length of columns stored in the line.  One is added because we
     * store the start index outside of the offsets array.
     */
    int length() {
      return offsets.length + 1;
    }

    /**
     * Find the mapping id at the specified column.
     *
     * @param column The column to lookup
     * @return the value at that point in the column
     */
    int valueAtColumn(int column) {
      Preconditions.checkArgument(column > 0);
      int pos = startIndex;
      for (int i = 0; i < column - 1; i++) {
        pos += offsets[i];
      }
      return pos;
    }
  }

  /**
   * Keeps track of data about the source file itself.  This is contains a list
   * of line offsets and columns to track down where exactly a line falls into
   * the data.
   */
  private static final class SourceFile {
    final String dir;
    final String fileName;
    final int startMapId;
    final int startLine;
    final byte[] lineOffsets;
    final short[] columns;
    final String[] identifiers;

    private SourceFile(
        String dir, String fileName, int startLine, int startMapId,
        byte[] lineOffsets, short[] columns, String[] identifiers) {
      this.fileName = Preconditions.checkNotNull(fileName);
      this.dir = Preconditions.checkNotNull(dir);
      this.startLine = startLine;
      this.startMapId = startMapId;
      this.lineOffsets = Preconditions.checkNotNull(lineOffsets);
      this.columns = Preconditions.checkNotNull(columns);
      this.identifiers = Preconditions.checkNotNull(identifiers);
      Preconditions.checkArgument(lineOffsets.length == columns.length &&
          columns.length == identifiers.length);
    }

    private SourceFile(int startMapId) {
      // Only used for binary searches.
      this.startMapId = startMapId;

      this.fileName = null;
      this.dir = null;
      this.startLine = 0;
      this.lineOffsets = null;
      this.columns = null;
      this.identifiers = null;
    }

    /**
     * Returns the number of elements in this source file.
     */
    int getLength() {
      return lineOffsets.length;
    }

    /**
     * Returns the number of elements in this source file.
     */
    int getStartMapId() {
      return startMapId;
    }

    /**
     * Creates an original mapping from the data.
     *
     * @param offset The offset into the array to find the mapping for.
     * @return A new original mapping object.
     */
    OriginalMapping getOriginalMapping(int offset) {
      int lineNumber = this.startLine;
      // Offset is an index into this array and we need to include it.
      for (int i = 0; i <= offset; i++) {
        lineNumber += lineOffsets[i];
      }
      OriginalMapping.Builder builder = OriginalMapping.newBuilder()
          .setOriginalFile(dir + fileName)
          .setLineNumber(lineNumber)
          .setColumnPosition(columns[offset])
          .setIdentifier(identifiers[offset]);
      return builder.build();
    }

    /**
     * Builder to make a new SourceFile object.
     */
    static final class Builder {
      String dir;
      String fileName;
      int startMapId;
      int startLine;
      byte[] lineOffsets;
      short[] columns;
      String[] identifiers;

      Builder setDir(String dir) {
        this.dir = dir;
        return this;
      }

      Builder setFileName(String fileName) {
        this.fileName = fileName;
        return this;
      }

      Builder setStartMapId(int startMapId) {
        this.startMapId = startMapId;
        return this;
      }

      Builder setStartLine(int startLine) {
        this.startLine = startLine;
        return this;
      }

      Builder setLineOffsets(List<Byte> lineOffsets) {
        this.lineOffsets = Bytes.toArray(lineOffsets);
        return this;
      }

      Builder setColumns(List<Short> columns) {
        this.columns = Shorts.toArray(columns);
        return this;
      }

      Builder setIdentifiers(List<String> identifiers) {
        this.identifiers = identifiers.toArray(new String[0]);
        return this;
      }

      /**
       * Creates a new SourceFile from the parameters.
       */
      SourceFile build() {
        return new SourceFile(dir, fileName, startLine, startMapId,
            lineOffsets, columns, identifiers);
      }
    }

    static Builder newBuilder() {
      return new Builder();
    }
  }
}
