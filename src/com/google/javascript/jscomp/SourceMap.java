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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.rhino.Node;

import java.io.IOException;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Collects information mapping the generated (compiled) source back to
 * its original source for debugging purposes.
 *
 * @see CodeConsumer
 * @see CodeGenerator
 * @see CodePrinter
 *
*
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
     * The input source file.
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
     * The original name of the token found at the position
     * represented by this mapping (if any).
     */
    String originalName;

    /**
     * Appends the mapping to the given buffer.
     */
    void appendTo(Appendable out) throws IOException {
      out.append("[");

      out.append(escapeString(sourceFile));

      out.append(",");
      out.append(originalPosition.getLineNumber() + "");

      out.append(",");
      out.append(originalPosition.getCharacterIndex() + "");

      if (originalName != null) {
        out.append(",");
        out.append(escapeString(originalName));
      }

      out.append("]");
    }
  }


  /**
   * Information about a particular line in the compiled (generated) source.
   */
  private static class LineMapping {
    /**
     * The list of input files which created the code on this line.
     */
    List<String> files = Lists.newArrayList();

    /**
     * The line number of this line (indexed by 0).
     */
    int lineNumber;

    /**
     * The number of characters on this line.
     * Note: This is not guarenteed to be accurate, but
     * rather merely reflects the number of characters as
     * known by the source map, which is good enough for
     * our purposes (because we do not have any information
     * for the missing characters anyway).
     */
    int length;

    /**
     * The list of all character mappings. Equivalent
     * to the values found in the charToMap.
     */
    List<LineCharMapping> characterMappings = Lists.newArrayList();

    /**
     * A mapping of each character index on the line to
     * its equivalent LineCharMapping describing that character's
     * original source file and location.
     */
    Map<Integer, LineCharMapping> charToMap = Maps.newHashMap();

    /**
     * Appends the line mapping's character map to the given
     * buffer.
     */
    void appendCharMapTo(Appendable out) throws IOException {
      out.append("[");

      for (int j = 0; j <= length; ++j) {
        if (j > 0) {
          out.append(",");
        }

        LineCharMapping current = charToMap.get(j);

        if (current == null) {
          out.append("-1");
        } else {
          out.append(current.basisMapping.id + "");
        }
      }

      out.append("]");
    }

    /**
     * Appends the line mapping's file map to the given
     * buffer.
     */
    void appendFileMapTo(Appendable out) throws IOException {
      // Sort the files list for deterministic consistency.
      Collections.sort(files);

      out.append("[");

      for (int j = 0; j < files.size(); ++j) {
        if (j > 0) {
          out.append(",");
        }

        out.append(escapeString(files.get(j)));
      }

      out.append("]");
    }
  }

  /**
   * Maps a range of characters in the compiled source file
   * back to a given Mapping.
   */
  private static class LineCharMapping {
    /**
     * The starting character in the compiled code.
     */
    int startCharacter;

    /**
     * The ending character in the compiled code.
     */
    int endCharacter;

    /**
     * The mapping associated with this character range
     * on the line(s) in the compiled code.
     */
    Mapping basisMapping;
  }

  /**
   * The list of mappings stored in this map.
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
   * Adds a mapping for the given node.
   *
   * @param node The node that the new mapping represents.
   * @param startPosition The position on the starting line
   * @param endPosition The position on the ending line.
   */
  void addMapping(Node node, Position startPosition, Position endPosition) {
    Object sourceFile = node.getProp(Node.SOURCEFILE_PROP);

    // If the node does not have an associated source file or
    // its line number is -1, then the node does not have sufficient
    // information for a mapping to be useful.
    if (sourceFile == null || node.getLineno() < 0) {
      return;
    }

    // Create the new mapping.
    Mapping mapping = new Mapping();
    mapping.id = mappings.size();
    mapping.sourceFile = sourceFile.toString();
    mapping.originalPosition = new Position(node.getLineno(), node.getCharno());

    Object originalName = node.getProp(Node.ORIGINALNAME_PROP);
    if (originalName != null) {
      mapping.originalName = originalName.toString();
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
   * Return structure from a determineLineMappings() call.
   */
  private static class LineMappingInformation {
    Map<Integer, LineMapping> mappings;
    int maxLine;

    public LineMappingInformation(Map<Integer, LineMapping> mappings,
                                  int maxLine) {
      this.maxLine = maxLine;
      this.mappings = mappings;
    }
  }

  /**
   * Build the list of mappings per line and per each character on
   * that line. This will allow consumers of this source map to ask:
   * "Which mapping best describes this character on this line in the
   * generated source code?".
   */
  private LineMappingInformation determineLineMappings() {
    int maxLine = 0;
    Map<Integer, LineMapping> lineMappings = Maps.newHashMap();

    for (Mapping mapping : mappings) {
      int prefixLine = prefixPosition.getLineNumber();

      int startPositionLine =
          prefixLine + mapping.startPosition.getLineNumber();

      int endPositionLine = prefixLine + mapping.endPosition.getLineNumber();

      // Determine the size of the line.
      maxLine = Math.max(maxLine, endPositionLine);

      // Iterate over each (possibly partial) line in the generated file that
      // this mapping represents.
      for (int i = startPositionLine; i <= endPositionLine; ++i) {
        LineMapping lineMapping = lineMappings.get(i);

        // If there is no line mapping for the current line, create it.
        if (lineMapping == null) {
          lineMapping = new LineMapping();
          lineMapping.lineNumber = i;
          lineMappings.put(i, lineMapping);
        }

        int startCharacter = mapping.startPosition.getCharacterIndex();

        // If this is the first line of the generated source file
        // (before we consider the prefix added), then add the
        // offset on the line caused by the inclusion of the prefix.
        if (mapping.startPosition.getLineNumber() == 0) {
          startCharacter += prefixPosition.getCharacterIndex();
        }

        int endCharacter = mapping.endPosition.getCharacterIndex();

        if (mapping.endPosition.getLineNumber() == 0) {
          endCharacter += prefixPosition.getCharacterIndex();
        }

        // Set the length of the current line's mapping.
        lineMapping.length = Math.max(lineMapping.length, endCharacter);

        // If we are not on the starting line, then it means the current
        // mapping is multiline and must start on the 0th character.
        if (i > startPositionLine) {
          startCharacter = 0;
        }

        // If we are not on the ending line, then it means the current
        // mapping is multiline and must start on the last character.
        if (i < endPositionLine) {
          endCharacter = Integer.MAX_VALUE;
        }

        // Create the line character mapping for this character.
        LineCharMapping lcm = new LineCharMapping();
        lcm.startCharacter = startCharacter;
        lcm.endCharacter = endCharacter;
        lcm.basisMapping = mapping;

        if (!lineMapping.files.contains(mapping.sourceFile)) {
          lineMapping.files.add(mapping.sourceFile);
        }

        lineMapping.characterMappings.add(lcm);
      }
    }

    return new LineMappingInformation(lineMappings, maxLine);
  }


  /**
   * For each character on each of the lines, find the LineCharMapping which
   * best represents the generation of that character. This is done by finding
   * ALL the LineCharMappings which span that character and then taking the one
   * with the *smallest* span. For example, this means that if you have an LCM
   * representing a block and an LCM inside of it representing a string literal,
   * the string literal will (correctly) be chosen, because its span is smaller
   * than that of the block for the characters representing the string literal.
   */
  private void buildCharacterMappings(Collection<LineMapping> lineMappings) {
    for (LineMapping lineMapping : lineMappings) {
      for (int i = 0; i <= lineMapping.length; ++i) {
        int minLength = Integer.MAX_VALUE;
        LineCharMapping current = null;

        Collections.sort(lineMapping.characterMappings,
            new Comparator<LineCharMapping>() {
            @Override
            public int compare(LineCharMapping first, LineCharMapping second) {
              Mapping firstBasis = first.basisMapping;
              Mapping secondBasis = second.basisMapping;

              String firstName = firstBasis.originalName;
              String secondName = secondBasis.originalName;

              firstName = firstName == null ? "" : firstName;
              secondName = secondName == null ? "" : secondName;

              return firstName.compareTo(secondName);
            }
          });

        for (LineCharMapping lcm : lineMapping.characterMappings) {
          // Ignore LCMs that do not include the current character.
          if (i < lcm.startCharacter || i > lcm.endCharacter) {
            continue;
          }

          int lcmLength = lcm.endCharacter - lcm.startCharacter;

          // Give precedence to items with names.
          if (lcmLength == minLength && lcm.basisMapping.originalName != null) {
            current = lcm;
            continue;
          }

          if (lcmLength < minLength) {
            minLength = lcmLength;
            current = lcm;
          }
        }

        lineMapping.charToMap.put(i, current);
      }
    }
  }

  /**
   * Retrieves the mapping for the given position in the generated source file.
   */
  Mapping getMappingFor(Position position) {
    // Build the map for each line.
    LineMappingInformation info = determineLineMappings();
    Map<Integer, LineMapping> lineMappings = info.mappings;

    // Build the character maps for each line.
    buildCharacterMappings(lineMappings.values());

    LineMapping lineMapping = lineMappings.get(position.getLineNumber());

    if (lineMapping == null) {
      return null;
    }

    LineCharMapping lcm =
        lineMapping.charToMap.get(position.getCharacterIndex());


    if (lcm == null) {
      return null;
    }

    return lcm.basisMapping;
  }

  /**
   * Appends the source map in LavaBug format to the given buffer.
   *
   * @param out The stream to which the map will be appended.
   * @param name The name of the generated source file that this source map
   *   represents.
   */
  public void appendTo(Appendable out, String name) throws IOException {
    // Build the map for each line.
    LineMappingInformation info = determineLineMappings();

    Map<Integer, LineMapping> lineMappings = info.mappings;
    int maxLine = info.maxLine;

    // Build the character maps for each line.
    buildCharacterMappings(lineMappings.values());

    // Write the mappings out to the file. The format of the generated
    // source map is three sections, each deliminated by a magic comment.
    //
    // The first section contains an array for each line of the generated
    // code, where each element in the array is the ID of the mapping which
    // best represents the index-th character found on that line of the
    // generated source code.
    //
    // The second section contains an array per generated line that contains
    // all the paths of the input source files that caused the code on the given
    // generated line to be generated. This is a simple array of Javascript
    // strings.
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
    // 5)  ["a.js", "b.js"]
    // 6)  ["b.js", "c.js", "d.js"]
    // 7)  /** Begin mapping definitions. **/
    // 8)  ["a.js", 1, 34]
    // 9)  ["a.js", 5, 2]
    // 10) ["b.js", 1, 3, "event"]
    // 11) ["c.js", 1, 4]
    // 12) ["d.js", 3, 78, "foo"]

    // Add the line character maps.
    out.append("/** Begin line maps. **/{ \"file\" : ");
    out.append(escapeString(name));
    out.append(", \"count\": ");
    out.append((maxLine + 1) + "");
    out.append(" }\n");

    for (int i = 0; i <= maxLine; ++i) {
      LineMapping lineMapping = lineMappings.get(i);

      if (lineMapping == null) {
        out.append("[]");
      } else {
        lineMapping.appendCharMapTo(out);
      }

      out.append("\n");
    }

    // Add the source file maps.
    out.append("/** Begin file information. **/\n");

    for (int i = 0; i <= maxLine; ++i) {
      LineMapping lineMapping = lineMappings.get(i);

      if (lineMapping == null) {
        out.append("[]");
      } else {
        lineMapping.appendFileMapTo(out);
      }

      out.append("\n");
    }

    // Add the mappings themselves.
    out.append("/** Begin mapping definitions. **/\n");

    for (int i = 0; i < mappings.size(); ++i) {
      Mapping mapping = mappings.get(i);
      mapping.appendTo(out);
      out.append("\n");
    }
  }
}
