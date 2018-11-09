/*
 * Copyright 2004 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.debugging.sourcemap.FilePosition;
import com.google.javascript.jscomp.CodePrinter.Builder.CodeGeneratorFactory;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * CodePrinter prints out JS code in either pretty format or compact format.
 *
 * @see CodeGenerator
 */
public final class CodePrinter {
  // There are two separate CodeConsumers, one for pretty-printing and
  // another for compact printing.

  // There are two implementations because the CompactCodePrinter
  // potentially has a very different implementation to the pretty
  // version.

  private abstract static class MappedCodePrinter extends CodeConsumer {
    private final Deque<Mapping> mappings;
    private final List<Mapping> allMappings;
    private final boolean createSrcMap;
    private final SourceMap.DetailLevel sourceMapDetailLevel;
    protected final StringBuilder code = new StringBuilder(1024);
    protected final int lineLengthThreshold;
    protected int lineLength = 0;
    protected int lineIndex = 0;

    MappedCodePrinter(
        int lineLengthThreshold,
        boolean createSrcMap,
        SourceMap.DetailLevel sourceMapDetailLevel) {
      checkState(sourceMapDetailLevel != null);
      this.lineLengthThreshold = lineLengthThreshold <= 0 ? Integer.MAX_VALUE :
        lineLengthThreshold;
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
      FilePosition start;
      FilePosition end;

      @Override
      public String toString() {
        // This toString() representation is used for debugging purposes only.
        return "Mapping: start " + start + ", end " + end + ", node " + node;
      }
    }

    /**
     * Starts the source mapping for the given
     * node at the current position.
     */
    @Override
    void startSourceMapping(Node node) {
      checkState(sourceMapDetailLevel != null);
      checkState(node != null);
      if (createSrcMap
          && node.getSourceFileName() != null
          && node.getLineno() > 0
          && sourceMapDetailLevel.apply(node)) {
        int line = getCurrentLineIndex();
        int index = getCurrentCharIndex();
        checkState(line >= 0);
        Mapping mapping = new Mapping();
        mapping.node = node;
        mapping.start = new FilePosition(line, index);
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
        checkState(line >= 0);
        mapping.end = new FilePosition(line, index);
      }
    }

    /**
     * Generates the source map from the given code consumer,
     * appending the information it saved to the SourceMap
     * object given.
     */
    void generateSourceMap(String code, SourceMap map) {
      if (createSrcMap) {
        List<Integer> lineLengths = computeLineLengths(code);
        for (Mapping mapping : allMappings) {
          map.addMapping(
              mapping.node, mapping.start, adjustEndPosition(lineLengths, mapping.end));
        }
      }
    }

    /**
     * Reports to the code consumer that the given line has been cut at the given position, i.e. a
     * \n has been inserted there. Or that a cut has been undone, i.e. a previously inserted \n has
     * been removed. All mappings in the source maps after that position will be renormalized as
     * needed.
     *
     * @param lineIndex The index of the line at which the newline was inserted/removed.
     * @param charIndex The position on the line at which the newline was inserted./removed
     * @param insertion True if a newline was inserted, false if a newline was removed.
     * @param lastLineContainsLineBreaks True if the last line contains line breaks. This is useful
     *     when the last line is a template literal that spans multiple lines.
     */
    void reportLineCut(
        int lineIndex, int charIndex, boolean insertion, boolean lastLineContainsLineBreaks) {
      if (createSrcMap) {
        for (Mapping mapping : allMappings) {
          mapping.start =
              convertPosition(
                  mapping.start, lineIndex, charIndex, insertion, lastLineContainsLineBreaks);

          if (mapping.end != null) {
            mapping.end =
                convertPosition(
                    mapping.end, lineIndex, charIndex, insertion, lastLineContainsLineBreaks);
          }
        }
      }
    }

    /**
     * Converts the given position by normalizing it against the insertion or removal of a newline
     * at the given line and character position.
     *
     * @param position The existing position before the newline was inserted/removed.
     * @param lineIndex The index of the line at which the newline was inserted/removed.
     * @param characterPosition The position on the line at which the newline was inserted.
     * @param insertion True if a newline was inserted, false if a newline was removed.
     * @param lastLineContainsLineBreaks True if the last line contains line breaks. This is useful
     *     when the last line is a template literal that spans multiple lines.
     * @return The normalized position.
     * @throws IllegalStateException if an attempt to reverse a line cut is made on a previous line
     *     rather than the current line.
     */
    private static FilePosition convertPosition(
        FilePosition position,
        int lineIndex,
        int characterPosition,
        boolean insertion,
        boolean lastLineContainsLineBreaks) {
      int originalLine = position.getLine();
      int originalChar = position.getColumn();
      if (insertion) {
        if (originalLine == lineIndex && originalChar >= characterPosition) {
          // If the position falls on the line itself, then normalize it
          // if it falls at or after the place the newline was inserted.
          return new FilePosition(
              originalLine + 1, originalChar - characterPosition);
        } else {
          return position;
        }
      } else {
        if (originalLine == lineIndex) {
          return new FilePosition(
              originalLine - 1, originalChar + characterPosition);
        } else if (originalLine > lineIndex) {
          if (lastLineContainsLineBreaks) {
            return new FilePosition(originalLine - 1, originalChar);
          } else {
            // Not supported, can only undo a cut on the most recent line. To
            // do this on a previous lines would require reevaluating the cut
            // positions on all subsequent lines.
            throw new IllegalStateException("Cannot undo line cut on a previous line.");
          }
        } else {
          return position;
        }
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

    /** Calculates length of each line in compiled code. */
    private static ImmutableList<Integer> computeLineLengths(String code) {
      ImmutableList.Builder<Integer> builder = ImmutableList.<Integer>builder();
      int lineStartPos = 0;
      int lineEndPos = code.indexOf('\n');
      while (lineEndPos > -1) {
        builder.add(lineEndPos - lineStartPos);
        // Next line starts where current line ends + 1 to skip "\n" character.
        lineStartPos = lineEndPos + 1;
        lineEndPos = code.indexOf('\n', lineStartPos);
      }
      return builder.build();
    }

    /**
     * Adjusts end position of a mapping. End position points to a column *after* the last character
     * that is covered by a mapping. And if it's end of the line there are 2 possibilities: either
     * point to the non-existent character after the last char on a line or point to the first
     * character on the next line. In some cases we end up with 2 mappings which should have the
     * same end position, but they use different styles as described above it leads to invalid
     * source maps.
     *
     * This method adjusts all such end positions, so if it points to the non-existing character
     * at the end of line - it is changed to point to the first character on the next line.
     *
     * @param lineLengths List of all line lengths in compiled code.
     * @param endPosition End position of a mapping.
     */
    private static FilePosition adjustEndPosition(
        List<Integer> lineLengths, FilePosition endPosition) {
      int line = endPosition.getLine();
      // if position points to non-existing line, return it unmodified
      if (line >= lineLengths.size()) {
        return endPosition;
      }

      Preconditions.checkState(
          endPosition.getColumn() <= lineLengths.get(line),
          "End position %s points to a column larger than line length %s",
          endPosition,
          lineLengths.get(line));

      // if end position points to the column just after the last character on the line -
      // change it to point the first character on the next line
      if (endPosition.getColumn() == lineLengths.get(line)) {
        return new FilePosition(line + 1, 0);
      }
      return endPosition;
    }
  }

  static class PrettyCodePrinter extends MappedCodePrinter {
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
      // Correct lineIndex and lineLength if there were newlines in the string.
      int newlines = CharMatcher.is('\n').countIn(str);
      if (newlines > 0) {
        lineIndex += newlines;
        lineLength = str.length() - str.lastIndexOf('\n');
      }
    }

    /**
     * Attempt to read the number format out of the original source location, falling back to the
     * default behavior if we cannot locate it.
     */
    @Override
    void addNumber(double x, Node n) {
      if (isNegativeZero(x)) {
        super.addNumber(x, n);
        return;
      }
      String numberFromSource = getNumberFromSource(n);
      if (numberFromSource == null) {
        super.addNumber(x, n);
        return;
      }

      if (x < 0) {
        numberFromSource = "-" + numberFromSource;
      }

      // The string we extract from the source code is not always a number.
      // Conservatively, we only use it if we can verify that it is as a number
      // with the right value. This excludes some valid constants (hex, etc.)
      // for simplicity.
      double d;
      try {
        d = Double.parseDouble(numberFromSource);
      } catch (NumberFormatException e) {
        super.addNumber(x, n);
        return;
      }

      if (x != d) {
        super.addNumber(x, n);
        return;
      }

      addConstant(numberFromSource);
    }

    /**
     * Adds a newline to the code, resetting the line length and handling indenting for pretty
     * printing.
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
      maybeInsertSpace();
      append("{");
      indent++;
    }

    @Override
    void appendBlockEnd() {
      maybeEndStatement();
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
    }

    @Override
    void appendOp(String op, boolean binOp) {
      if (getLastChar() != ' ' && binOp && op.charAt(0) != ',') {
        append(" ");
      }
      append(op);
      if (binOp) {
        append(" ");
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

    @Override
    void maybeInsertSpace() {
      if (getLastChar() != ' ' && getLastChar() != '\n') {
        add(" ");
      }
    }

    /**
     * @return The TRY node for the specified CATCH node.
     */
    private static Node getTryForCatch(Node n) {
      return n.getGrandparent();
    }

    /**
     * @return Whether the a line break should be added after the specified
     * BLOCK.
     */
    @Override
    boolean breakAfterBlockFor(Node n,  boolean isStatementContext) {
      checkState(n.isBlock(), n);
      Node parent = n.getParent();
      Token type = parent.getToken();
      switch (type) {
        case DO:
          // Don't break before 'while' in DO-WHILE statements.
          return false;
        case FUNCTION:
          // FUNCTIONs are handled separately, don't break here.
          return false;
        case TRY:
          // Don't break before catch
          return n != parent.getFirstChild();
        case CATCH:
          // Don't break before finally
          return !NodeUtil.hasFinally(getTryForCatch(parent));
        case IF:
          // Don't break before else
          return n == parent.getLastChild();
        default:
          break;
      }
      return true;
    }

    @Override
    void endStatement(boolean needsSemicolon) {
      append(";");
      endLine();
      statementNeedsEnded = false;
    }

    @Override
    void endFile() {
      maybeEndStatement();
    }

    private static String getNumberFromSource(Node n) {
      if (!n.isNumber()) {
        return null;
      }

      StaticSourceFile staticSrc = NodeUtil.getSourceFile(n);
      if (!(staticSrc instanceof SourceFile)) {
        return null;
      }
      SourceFile src = (SourceFile) staticSrc;

      String srcCode;
      try {
        srcCode = src.getCode();
      } catch (IOException e) {
        return null;
      }

      int offset;
      try {
        offset = n.getSourceOffset();
      } catch (IllegalArgumentException e) {
        return null;
      }
      int endOffset = offset + n.getLength();
      if (offset < 0 || endOffset > srcCode.length()) {
        return null;
      }

      return srcCode.substring(offset, endOffset);
    }
  }

  static class CompactCodePrinter extends MappedCodePrinter {
    // The CompactCodePrinter tries to emit just enough newlines to stop there
    // being lines longer than the threshold.  Since the output is going to be
    // gzipped, it makes sense to try to make the newlines appear in similar
    // contexts so that gzip can encode them for 'free'.
    //
    // This version tries to break the lines at 'preferred' places, which are
    // between the top-level forms.  This works because top-level forms tend to
    // be more uniform than arbitrary legal contexts.  Better compression would
    // probably require explicit modeling of the gzip algorithm.

    private final boolean lineBreak;
    private final boolean preferLineBreakAtEndOfFile;
    private int lineStartPosition = 0;
    private int preferredBreakPosition = 0;
    private int prevCutPosition = 0;
    private int prevLineStartPosition = 0;

  /**
   * @param lineBreak break the lines a bit more aggressively
   * @param lineLengthThreshold The length of a line after which we force
   *                            a newline when possible.
   * @param createSrcMap Whether to gather source position
   *                            mapping information when printing.
   * @param sourceMapDetailLevel A filter to control which nodes get mapped into
   *     the source map.
   */
    private CompactCodePrinter(boolean lineBreak,
        boolean preferLineBreakAtEndOfFile, int lineLengthThreshold,
        boolean createSrcMap, SourceMap.DetailLevel sourceMapDetailLevel) {
      super(lineLengthThreshold, createSrcMap, sourceMapDetailLevel);
      this.lineBreak = lineBreak;
      this.preferLineBreakAtEndOfFile = preferLineBreakAtEndOfFile;
    }

    /**
     * Appends a string to the code, keeping track of the current line length.
     */
    @Override
    void append(String str) {
      code.append(str);
      lineLength += str.length();
      // Correct lineIndex and lineLength if there were newlines in the string.
      int newlines = CharMatcher.is('\n').countIn(str);
      if (newlines > 0) {
        lineIndex += newlines;
        lineLength = str.length() - str.lastIndexOf('\n');
      }
    }

    /**
     * Adds a newline to the code, resetting the line length.
     */
    @Override
    void startNewLine() {
      if (lineLength > 0) {
        prevCutPosition = code.length();
        prevLineStartPosition = lineStartPosition;
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
          prevCutPosition = position;
          reportLineCut(lineIndex, position - lineStartPosition, true, false);
          lineIndex++;
          lineLength -= (position - lineStartPosition);
          prevLineStartPosition = lineStartPosition;
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

    @Override
    void endFile() {
      super.endFile();
      if (!preferLineBreakAtEndOfFile) {
        return;
      }
      if (lineLength > lineLengthThreshold / 2) {
        // Add an extra break at end of file.
        append(";");
        startNewLine();
      } else if (prevCutPosition > 0) {

        // Shift the previous break to end of file by replacing it with a
        // <space> and adding a new break at end of file. Adding the space
        // handles cases like instanceof\nfoo. (it would be nice to avoid this)
        code.setCharAt(prevCutPosition, ' ');
        lineStartPosition = prevLineStartPosition;
        int cutLineIndex = lineIndex;
        // We need +1 to account for the space added few lines above.
        int prevLineEndPosition = prevCutPosition - prevLineStartPosition + 1;
        if (code.indexOf("\n", prevCutPosition) != -1) {
          // having "\n" in the code after prevCutPosition means that the original code had
          // irremovable \n such as template literals with line breaks.
          lineLength = code.length() - code.lastIndexOf("\n");
          cutLineIndex = lineIndex - CharMatcher.is('\n').countIn(code.substring(prevCutPosition));
          reportLineCut(cutLineIndex, prevLineEndPosition, false, true);
        } else {
          lineLength = code.length() - lineStartPosition;
          reportLineCut(cutLineIndex, prevLineEndPosition, false, false);
        }
        lineIndex--;
        prevCutPosition = 0;
        prevLineStartPosition = 0;
        append(";");
        startNewLine();
      } else {
        // A small file with no line breaks. We do nothing in this case to
        // avoid excessive line breaks. It's not ideal if a lot of these pile
        // up, but that is reasonably unlikely.
      }
    }

  }

  public static final class Builder {
    private final Node root;
    private CompilerOptions options = new CompilerOptions();
    private boolean lineBreak;
    private boolean prettyPrint;
    private boolean outputTypes = false;
    private SourceMap sourceMap = null;
    private boolean tagAsTypeSummary;
    private boolean tagAsStrict;
    private JSTypeRegistry registry;
    private CodeGeneratorFactory codeGeneratorFactory = new CodeGeneratorFactory() {
      @Override
      public CodeGenerator getCodeGenerator(Format outputFormat, CodeConsumer cc) {
        return outputFormat == Format.TYPED
            ? new TypedCodeGenerator(cc, options, registry)
            : new CodeGenerator(cc, options);
      }
    };

    /**
     * Sets the root node from which to generate the source code.
     * @param node The root node.
     */
    public Builder(Node node) {
      root = node;
    }

    /**
     * Sets the output options from compiler options.
     */
    public Builder setCompilerOptions(CompilerOptions options) {
      this.options = options;
      this.prettyPrint = options.isPrettyPrint();
      this.lineBreak = options.lineBreak;
      return this;
    }

    public Builder setTypeRegistry(JSTypeRegistry registry) {
      this.registry = registry;
      return this;
    }

    /**
     * Sets whether pretty printing should be used.
     * @param prettyPrint If true, pretty printing will be used.
     */
    public Builder setPrettyPrint(boolean prettyPrint) {
      this.prettyPrint = prettyPrint;
      return this;
    }

    /**
     * Sets whether line breaking should be done automatically.
     * @param lineBreak If true, line breaking is done automatically.
     */
    public Builder setLineBreak(boolean lineBreak) {
      this.lineBreak = lineBreak;
      return this;
    }

    /**
     * Sets whether to output closure-style type annotations.
     * @param outputTypes If true, outputs closure-style type annotations.
     */
    public Builder setOutputTypes(boolean outputTypes) {
      this.outputTypes = outputTypes;
      return this;
    }

    /**
     * Sets the source map to which to write the metadata about
     * the generated source code.
     *
     * @param sourceMap The source map.
     */
    public Builder setSourceMap(SourceMap sourceMap) {
      this.sourceMap = sourceMap;
      return this;
    }

    /** Set whether the output should be tagged as an .i.js file. */
    public Builder setTagAsTypeSummary(boolean tagAsTypeSummary) {
      this.tagAsTypeSummary = tagAsTypeSummary;
      return this;
    }

    /**
     * Set whether the output should be tags as ECMASCRIPT 5 Strict.
     */
    public Builder setTagAsStrict(boolean tagAsStrict) {
      this.tagAsStrict = tagAsStrict;
      return this;
    }

    /**
     * Set a custom code generator factory to enable custom code generation.
     */
    public Builder setCodeGeneratorFactory(CodeGeneratorFactory factory) {
      this.codeGeneratorFactory = factory;
      return this;
    }

    public interface CodeGeneratorFactory {
      CodeGenerator getCodeGenerator(Format outputFormat, CodeConsumer cc);
    }

    /**
     * Generates the source code and returns it.
     */
    public String build() {
      if (root == null) {
        throw new IllegalStateException(
            "Cannot build without root node being specified");
      }

      return toSource(
          root,
          Format.fromOptions(options, outputTypes, prettyPrint),
          options,
          sourceMap,
          tagAsTypeSummary,
          tagAsStrict,
          lineBreak,
          codeGeneratorFactory);
    }
  }

  /**
   * Specifies a format for code generation.
   */
  public enum Format {
    COMPACT,
    PRETTY,
    TYPED;

    static Format fromOptions(CompilerOptions options, boolean outputTypes, boolean prettyPrint) {
      if (outputTypes) {
        return Format.TYPED;
      }
      if (prettyPrint || options.getOutputFeatureSet().contains(FeatureSet.TYPESCRIPT)) {
        return Format.PRETTY;
      }
      return Format.COMPACT;
    }
  }

  /** Converts a tree to JS code */
  private static String toSource(
      Node root,
      Format outputFormat,
      CompilerOptions options,
      SourceMap sourceMap,
      boolean tagAsTypeSummary,
      boolean tagAsStrict,
      boolean lineBreak,
      CodeGeneratorFactory codeGeneratorFactory) {
    checkState(options.sourceMapDetailLevel != null);

    boolean createSourceMap = (sourceMap != null);
    MappedCodePrinter mcp =
        outputFormat == Format.COMPACT
        ? new CompactCodePrinter(
            lineBreak,
            options.preferLineBreakAtEndOfFile,
            options.lineLengthThreshold,
            createSourceMap,
            options.sourceMapDetailLevel)
        : new PrettyCodePrinter(
            options.lineLengthThreshold,
            createSourceMap,
            options.sourceMapDetailLevel);
    CodeGenerator cg = codeGeneratorFactory.getCodeGenerator(outputFormat, mcp);

    if (tagAsTypeSummary) {
      cg.tagAsTypeSummary();
    }
    if (tagAsStrict) {
      cg.tagAsStrict();
    }

    cg.add(root);
    mcp.endFile();

    String code = mcp.getCode();

    if (createSourceMap) {
      mcp.generateSourceMap(code, sourceMap);
    }

    return code;
  }
}
