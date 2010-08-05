/*
 * Copyright 2004 Google Inc.
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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * CodePrinter prints out js code in either pretty format or compact format.
 *
 * @see CodeGenerator
*
*
 */
class CodePrinter {
  // The number of characters after which we insert a line break in the code
  static final int DEFAULT_LINE_LENGTH_THRESHOLD = 500;


  // There are two separate CodeConsumers, one for pretty-printing and
  // another for compact printing.

  // There are two implementations because the CompactCodePrinter
  // potentially has a very different implementation to the pretty
  // version.

  private abstract static class MappedCodePrinter extends CodeConsumer {
    final private Deque<Mapping> mappings;
    final private List<Mapping> allMappings;
    final private boolean createSrcMap;
    final private SourceMap.DetailLevel sourceMapDetailLevel;
    protected final StringBuilder code = new StringBuilder(1024);
    protected final int lineLengthThreshold;
    protected int lineLength = 0;
    protected int lineIndex = 0;

    MappedCodePrinter(
        int lineLengthThreshold,
        boolean createSrcMap,
        SourceMap.DetailLevel sourceMapDetailLevel) {
      Preconditions.checkState(sourceMapDetailLevel != null);
      this.lineLengthThreshold = lineLengthThreshold;
      this.createSrcMap = createSrcMap;
      this.sourceMapDetailLevel = sourceMapDetailLevel;
      this.mappings = createSrcMap ? new ArrayDeque<Mapping>() : null;
      this.allMappings = createSrcMap ? new ArrayList<Mapping>() : null;
    }

    /**
     * Maintains a mapping from a given node to the position
     * in the source code at which its generated form was
     * placed. This position is relative only to the current
     * run of the CodeConsumer and will be normalized
     * later on by the SourceMap.
     *
     * @see SourceMap
     */
    private static class Mapping {
      Node node;
      Position start;
      Position end;
    }

    /**
     * Starts the source mapping for the given
     * node at the current position.
     */
    @Override
    void startSourceMapping(Node node) {
      Preconditions.checkState(sourceMapDetailLevel != null);
      Preconditions.checkState(node != null);
      if (createSrcMap
          && node.getProp(Node.SOURCEFILE_PROP) != null
          && node.getLineno() > 0
          && sourceMapDetailLevel.apply(node)) {
        int line = getCurrentLineIndex();
        int index = getCurrentCharIndex();
        Preconditions.checkState(line >= 0);
        Mapping mapping = new Mapping();
        mapping.node = node;
        mapping.start = new Position(line, index);
        mappings.push(mapping);
        allMappings.add(mapping);
      }
    }

    /**
     * Finishes the source mapping for the given
     * node at the current position.
     */
    @Override
    void endSourceMapping(Node node) {
      if (createSrcMap && !mappings.isEmpty() && mappings.peek().node == node) {
        Mapping mapping = mappings.pop();
        int line = getCurrentLineIndex();
        int index = getCurrentCharIndex();
        Preconditions.checkState(line >= 0);
        mapping.end = new Position(line, index);
      }
    }

    /**
     * Generates the source map from the given code consumer,
     * appending the information it saved to the SourceMap
     * object given.
     */
    void generateSourceMap(SourceMap map){
      if (createSrcMap) {
        for (Mapping mapping : allMappings) {
          map.addMapping(mapping.node, mapping.start, mapping.end);
        }
      }
    }

    /**
     * Reports to the code consumer that the given line has been cut at the
     * given position (i.e. a \n has been inserted there). All mappings in
     * the source maps after that position will be renormalized as needed.
     */
    void reportLineCut(int lineIndex, int charIndex) {
      if (createSrcMap) {
        for (Mapping mapping : allMappings) {
          mapping.start = convertPosition(mapping.start, lineIndex, charIndex);

          if (mapping.end != null) {
            mapping.end = convertPosition(mapping.end, lineIndex, charIndex);
          }
        }
      }
    }

    /**
     * Converts the given position by normalizing it against the insertion
     * of a newline at the given line and character position.
     *
     * @param position The existing position before the newline was inserted.
     * @param lineIndex The index of the line at which the newline was inserted.
     * @param characterPosition The position on the line at which the newline
     *     was inserted.
     *
     * @return The normalized position.
     */
    private Position convertPosition(Position position, int lineIndex,
                                     int characterPosition) {
      int originalLine = position.getLineNumber();
      int originalChar = position.getCharacterIndex();
      if (originalLine == lineIndex && originalChar >= characterPosition) {
        // If the position falls on the line itself, then normalize it
        // if it falls at or after the place the newline was inserted.
        return new Position(originalLine + 1, originalChar - characterPosition);
      } else {
        return position;
      }
    }

    public String getCode() {
      return code.toString();
    }

    @Override
    char getLastChar() {
      return (code.length() > 0) ? code.charAt(code.length() - 1) : '\0';
    }

    protected final int getCurrentCharIndex() {
      return lineLength;
    }

    protected final int getCurrentLineIndex() {
      return lineIndex;
    }
  }

  static class PrettyCodePrinter
      extends MappedCodePrinter {
    // The number of characters after which we insert a line break in the code
    static final String INDENT = "  ";

    private int indent = 0;

    /**
     * @param lineLengthThreshold The length of a line after which we force
     *                            a newline when possible.
     * @param createSourceMap Whether to generate source map data.
     * @param sourceMapDetailLevel A filter to control which nodes get mapped
     *     into the source map.
     */
    private PrettyCodePrinter(
        int lineLengthThreshold,
        boolean createSourceMap,
        SourceMap.DetailLevel sourceMapDetailLevel) {
      super(lineLengthThreshold, createSourceMap, sourceMapDetailLevel);
    }

    /**
     * Appends a string to the code, keeping track of the current line length.
     */
    @Override
    void append(String str) {
      // For pretty printing: indent at the beginning of the line
      if (lineLength == 0) {
        for (int i = 0; i < indent; i++) {
          code.append(INDENT);
          lineLength += INDENT.length();
        }
      }
      code.append(str);
      lineLength += str.length();
    }

    /**
     * Adds a newline to the code, resetting the line length and handling
     * indenting for pretty printing.
     */
    @Override
    void startNewLine() {
      if (lineLength > 0) {
        code.append('\n');
        lineIndex++;
        lineLength = 0;
      }
    }

    @Override
    void maybeLineBreak() {
      maybeCutLine();
    }

    /**
     * This may start a new line if the current line is longer than the line
     * length threshold.
     */
    @Override
    void maybeCutLine() {
      if (lineLength > lineLengthThreshold) {
        startNewLine();
      }
    }

    @Override
    void endLine() {
      startNewLine();
    }

    @Override
    void appendBlockStart() {
      append(" {");
      indent++;
    }

    @Override
    void appendBlockEnd() {
      endLine();
      indent--;
      append("}");
    }

    @Override
    void listSeparator() {
      add(", ");
      maybeLineBreak();
    }

    @Override
    void endFunction(boolean statementContext) {
      super.endFunction(statementContext);
      if (statementContext) {
        startNewLine();
      }
    }

    @Override
    void beginCaseBody() {
      super.beginCaseBody();
      indent++;
      endLine();
    }

    @Override
    void endCaseBody() {
      super.endCaseBody();
      indent--;
      endStatement();
    }

    @Override
    void appendOp(String op, boolean binOp) {
      if (binOp) {
        if (getLastChar() != ' ') {
          append(" ");
        }
        append(op);
        append(" ");
      } else {
        append(op);
      }
    }

    /**
     * If the body of a for loop or the then clause of an if statement has
     * a single statement, should it be wrapped in a block?
     * {@inheritDoc}
     */
    @Override
    boolean shouldPreserveExtraBlocks() {
      // When pretty-printing, always place the statement in its own block
      // so it is printed on a separate line.  This allows breakpoints to be
      // placed on the statement.
      return true;
    }

    /**
     * @return The TRY node for the specified CATCH node.
     */
    private Node getTryForCatch(Node n) {
      return n.getParent().getParent();
    }

    /**
     * @return Whether the a line break should be added after the specified
     * BLOCK.
     */
    @Override
    boolean breakAfterBlockFor(Node n,  boolean isStatementContext) {
      Preconditions.checkState(n.getType() == Token.BLOCK);
      Node parent = n.getParent();
      if (parent != null) {
        int type = parent.getType();
        switch (type) {
          case Token.DO:
            // Don't break before 'while' in DO-WHILE statements.
            return false;
          case Token.FUNCTION:
            // FUNCTIONs are handled separately, don't break here.
            return false;
          case Token.TRY:
            // Don't break before catch
            return n != parent.getFirstChild();
          case Token.CATCH:
            // Don't break before finally
            return !NodeUtil.hasFinally(getTryForCatch(parent));
          case Token.IF:
            // Don't break before else
            return n == parent.getLastChild();
        }
      }
      return true;
    }
  }


  static class CompactCodePrinter
      extends MappedCodePrinter {

    // The CompactCodePrinter tries to emit just enough newlines to stop there
    // being lines longer than the threshold.  Since the output is going to be
    // gzipped, it makes sense to try to make the newlines appear in similar
    // contexts so that GZIP can encode them for 'free'.
    //
    // This version tries to break the lines at 'preferred' places, which are
    // between the top-level forms.  This works because top level forms tend to
    // be more uniform than arbitary legal contexts.  Better compression would
    // probably require explicit modelling of the gzip algorithm.

    private final boolean lineBreak;
    private int lineStartPosition = 0;
    private int preferredBreakPosition = 0;

  /**
   * @param lineBreak break the lines a bit more aggressively
   * @param lineLengthThreshold The length of a line after which we force
   *                            a newline when possible.
   * @param createSrcMap Whether to gather source position
   *                            mapping information when printing.
   * @param sourceMapDetailLevel A filter to control which nodes get mapped into
   *     the source map.
   */
    private CompactCodePrinter(boolean lineBreak, int lineLengthThreshold,
        boolean createSrcMap, SourceMap.DetailLevel sourceMapDetailLevel) {
      super(lineLengthThreshold, createSrcMap, sourceMapDetailLevel);
      this.lineBreak = lineBreak;
    }

    /**
     * Appends a string to the code, keeping track of the current line length.
     */
    @Override
    void append(String str) {
      code.append(str);
      lineLength += str.length();
    }

    /**
     * Adds a newline to the code, resetting the line length.
     */
    @Override
    void startNewLine() {
      if (lineLength > 0) {
        code.append('\n');
        lineLength = 0;
        lineIndex++;
        lineStartPosition = code.length();
      }
    }

    @Override
    void maybeLineBreak() {
      if (lineBreak) {
        if (sawFunction) {
          startNewLine();
          sawFunction = false;
        }
      }

      // Since we are at a legal line break, can we upgrade the
      // preferred break position?  We prefer to break after a
      // semicolon rather than before it.
      int len = code.length();
      if (preferredBreakPosition == len - 1) {
        char ch = code.charAt(len - 1);
        if (ch == ';') {
          preferredBreakPosition = len;
        }
      }
      maybeCutLine();
    }

    /**
     * This may start a new line if the current line is longer than the line
     * length threshold.
     */
    @Override
    void maybeCutLine() {
      if (lineLength > lineLengthThreshold) {
        // Use the preferred position provided it will break the line.
        if (preferredBreakPosition > lineStartPosition &&
            preferredBreakPosition < lineStartPosition + lineLength) {
          int position = preferredBreakPosition;
          code.insert(position, '\n');
          reportLineCut(lineIndex, position - lineStartPosition);
          lineIndex++;
          lineLength -= (position - lineStartPosition);
          lineStartPosition = position + 1;
        } else {
          startNewLine();
        }
      }
    }

    @Override
    void notePreferredLineBreak() {
      preferredBreakPosition = code.length();
    }
  }

  static class Builder {
    private final Node root;
    private boolean prettyPrint = false;
    private boolean lineBreak = false;
    private boolean outputTypes = false;
    private int lineLengthThreshold = DEFAULT_LINE_LENGTH_THRESHOLD;
    private SourceMap sourceMap = null;
    private SourceMap.DetailLevel sourceMapDetailLevel = SourceMap.DetailLevel.ALL;
    // Specify a charset to use when outputting source code.  If null,
    // then just output ASCII.
    private Charset outputCharset = null;

    /**
     * Sets the root node from which to generate the source code.
     * @param node The root node.
     */
    Builder(Node node) {
      root = node;
    }

    /**
     * Sets whether pretty printing should be used.
     * @param prettyPrint If true, pretty printing will be used.
     */
    Builder setPrettyPrint(boolean prettyPrint) {
      this.prettyPrint = prettyPrint;
      return this;
    }

    /**
     * Sets whether line breaking should be done automatically.
     * @param lineBreak If true, line breaking is done automatically.
     */
    Builder setLineBreak(boolean lineBreak) {
      this.lineBreak = lineBreak;
      return this;
    }

    /**
     * Sets whether to output closure-style type annotations.
     * @param outputTypes If true, outputs closure-style type annotations.
     */
    Builder setOutputTypes(boolean outputTypes) {
      this.outputTypes = outputTypes;
      return this;
    }

    /**
     * Sets the line length threshold that will be used to determine
     * when to break lines, if line breaking is on.
     *
     * @param threshold The line length threshold.
     */
    Builder setLineLengthThreshold(int threshold) {
      this.lineLengthThreshold = threshold;
      return this;
    }

    /**
     * Sets the source map to which to write the metadata about
     * the generated source code.
     *
     * @param sourceMap The source map.
     */
    Builder setSourceMap(SourceMap sourceMap) {
      this.sourceMap = sourceMap;
      return this;
    }

    /**
     * @param level The detail level to use.
     */
    Builder setSourceMapDetailLevel(SourceMap.DetailLevel level) {
      Preconditions.checkState(level != null);
      this.sourceMapDetailLevel = level;
      return this;
    }

    /**
     * Set the charset to use when determining what characters need to be
     * escaped in the output.
     */
    Builder setOutputCharset(Charset outCharset) {
      this.outputCharset = outCharset;
      return this;
    }

    /**
     * Generates the source code and returns it.
     */
    String build() {
      if (root == null) {
        throw new IllegalStateException(
            "Cannot build without root node being specified");
      }

      Format outputFormat = outputTypes
          ? Format.TYPED
          : prettyPrint
              ? Format.PRETTY
              : Format.COMPACT;

      return toSource(root, outputFormat, lineBreak, lineLengthThreshold,
          sourceMap, sourceMapDetailLevel, outputCharset);
    }
  }

  enum Format {
    COMPACT,
    PRETTY,
    TYPED
  }

  /**
   * Converts a tree to js code
   */
  private static String toSource(Node root, Format outputFormat,
                                 boolean lineBreak,  int lineLengthThreshold,
                                 SourceMap sourceMap,
                                 SourceMap.DetailLevel sourceMapDetailLevel,
                                 Charset outputCharset) {
    Preconditions.checkState(sourceMapDetailLevel != null);

    boolean createSourceMap = (sourceMap != null);
    MappedCodePrinter mcp =
        outputFormat == Format.COMPACT
        ? new CompactCodePrinter(
            lineBreak, lineLengthThreshold,
            createSourceMap, sourceMapDetailLevel)
        : new PrettyCodePrinter(
            lineLengthThreshold, createSourceMap, sourceMapDetailLevel);
    CodeGenerator cg =
        outputFormat == Format.TYPED
        ? new TypedCodeGenerator(mcp, outputCharset)
        : new CodeGenerator(mcp, outputCharset);
    cg.add(root);

    String code = mcp.getCode();

    if (createSourceMap) {
      mcp.generateSourceMap(sourceMap);
    }

    return code;
  }
}
