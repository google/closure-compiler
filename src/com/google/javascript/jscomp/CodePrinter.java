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
import static com.google.javascript.jscomp.base.JSCompDoubles.isPositive;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.debugging.sourcemap.FilePosition;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.CodePrinter.Builder.CodeGeneratorFactory;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jspecify.annotations.Nullable;

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
    private final @Nullable Deque<SourceMap.Mapping> mappings;
    private final @Nullable List<SourceMap.Mapping> allMappings;
    // The ordered list of finalized mappings since the last line break. See #reportLineCut.
    private final @Nullable List<SourceMap.Mapping> completeMappings;
    // The index into allMappings to find the mappings added since the last line
    // break. See #reportLineCut.
    private int firstCandidateMappingForCut = 0;
    private final boolean createSrcMap;
    private final SourceMap.DetailLevel sourceMapDetailLevel;
    private final @Nullable LicenseTracker licenseTracker;
    protected final StringBuilder code = new StringBuilder(1024);
    protected final int lineLengthThreshold;
    protected int lineLength = 0;
    protected int lineIndex = 0;

    MappedCodePrinter(
        int lineLengthThreshold,
        boolean createSrcMap,
        SourceMap.DetailLevel sourceMapDetailLevel,
        @Nullable LicenseTracker licenseTracker) {
      checkState(sourceMapDetailLevel != null);
      this.lineLengthThreshold = lineLengthThreshold <= 0 ? Integer.MAX_VALUE :
        lineLengthThreshold;
      this.createSrcMap = createSrcMap;
      this.sourceMapDetailLevel = sourceMapDetailLevel;
      this.mappings = createSrcMap ? new ArrayDeque<>() : null;
      this.allMappings = createSrcMap ? new ArrayList<>() : null;
      this.completeMappings = createSrcMap ? new ArrayList<>() : null;
      this.licenseTracker = licenseTracker;
    }

    /** Appends a string to the code, keeping track of the current line length. */
    @Override
    void append(String str) {
      code.append(str);
      lineLength += str.length();
    }

    @Override
    void trackLicenses(Node node) {
      if (this.licenseTracker != null) {
        this.licenseTracker.trackLicensesForNode(node);
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
        SourceMap.Mapping mapping = new SourceMap.Mapping();
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
        SourceMap.Mapping mapping = mappings.pop();
        int line = getCurrentLineIndex();
        int index = getCurrentCharIndex();
        checkState(line >= 0);
        mapping.end = new FilePosition(line, index);
        completeMappings.add(mapping);
      }
    }

    /**
     * Returns a list of sourcemap mappings that were generated while printing this code. Only
     * useful if createSrcMap was true for this MappedCodePrinter.
     */
    @Nullable List<SourceMap.Mapping> getSourceMappings(String code) {
      if (!createSrcMap) {
        return null;
      }
      ImmutableList<Integer> lineLengths = computeLineLengths(code);
      List<SourceMap.Mapping> fixedMappings = new ArrayList<>();
      for (SourceMap.Mapping mapping : allMappings) {
        SourceMap.Mapping adjusted = new SourceMap.Mapping();
        adjusted.node = mapping.node;
        adjusted.start = mapping.start;
        adjusted.end = adjustEndPosition(lineLengths, mapping.end);
        fixedMappings.add(adjusted);
      }
      return fixedMappings;
    }

    /**
     * Reports to the code consumer that the given line has been cut at the given position, i.e. a
     * \n has been inserted there. All mappings in the source maps after that position will be
     * renormalized as needed.
     */
    void reportLineCut(int lineIndex, int charIndex) {
      if (createSrcMap) {
        // To avoid iterating over every mapping, every time we cut a line (which can get
        // excessively expensive for large files), we keep track of mappings that must be
        // before the next cut. For the start of mappings, we can use the order in allMappings.
        // However, mapping ends do not have their own entry in the list so we must track those
        // separately.

        int mappingCount = allMappings.size();
        for (int i = firstCandidateMappingForCut; i < mappingCount; i++) {
          SourceMap.Mapping mapping = allMappings.get(i);
          mapping.start = convertPositionAfterLineCut(mapping.start, lineIndex, charIndex);
        }
        firstCandidateMappingForCut = mappingCount;

        for (SourceMap.Mapping mapping : completeMappings) {
          mapping.end = convertPositionAfterLineCut(mapping.end, lineIndex, charIndex);
        }
        // To avoid iterating over every mapping, every time we cut a line, keep track of
        // mappings that must end before the next cut.
        completeMappings.clear();
      }
    }

    /**
     * Converts the given position by normalizing it against the insertion at the given line and
     * character position.
     *
     * @param position The existing position before the newline was inserted.
     * @param lineIndex The index of the line at which the newline was inserted.
     * @param characterPosition The position on the line at which the newline was inserted.
     * @return The normalized position.
     * @throws IllegalStateException if an attempt to reverse a line cut is made on a previous line
     *     rather than the current line.
     */
    private static FilePosition convertPositionAfterLineCut(
        FilePosition position, int lineIndex, int characterPosition) {
      int originalLine = position.getLine();
      int originalChar = position.getColumn();
      if (originalLine == lineIndex && originalChar >= characterPosition) {
        // If the position falls on the line itself, then normalize it
        // if it falls at or after the place the newline was inserted.
        return new FilePosition(originalLine + 1, originalChar - characterPosition);
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

      checkState(
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
     * @param lineLengthThreshold The length of a line after which we force a newline when possible.
     * @param createSourceMap Whether to generate source map data.
     * @param sourceMapDetailLevel A filter to control which nodes get mapped into the source map.
     * @param licenseTracker A license tracking implementation to manage license text emit. The
     *     CodePrinter will never emit license information directly.
     */
    private PrettyCodePrinter(
        int lineLengthThreshold,
        boolean createSourceMap,
        SourceMap.DetailLevel sourceMapDetailLevel,
        @Nullable LicenseTracker licenseTracker) {
      super(lineLengthThreshold, createSourceMap, sourceMapDetailLevel, licenseTracker);
    }

    /**
     * Appends an appropriately indented string to the code, keeping track of the current line count
     * and line length.
     */
    @Override
    void append(String str) {
      // For pretty printing: indent at the beginning of the line, except template literal lines.
      if (lineLength == 0 && !isInTemplateLiteral()) {
        for (int i = 0; i < indent; i++) {
          code.append(INDENT);
          lineLength += INDENT.length();
        }
      }

      super.append(str);
    }

    /**
     * Attempt to read the number format out of the original source location, falling back to the
     * default behavior if we cannot locate it.
     */
    @Override
    void addNumber(double x, Node n) {
      checkState(isPositive(x), x);

      String numberFromSource = getNumberFromSource(n);
      if (numberFromSource == null) {
        super.addNumber(x, n);
        return;
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
      if (lineLength <= 0 && !this.isInTemplateLiteral()) {
        return;
      }

      code.append('\n');
      lineIndex++;
      lineLength = 0;
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
      add("{");
      indent++;
    }

    @Override
    void appendBlockEnd() {
      maybeEndStatement();
      endLine();
      indent--;
      add("}");
    }

    @Override
    void listSeparator() {
      add(", ");
      maybeLineBreak();
    }

    @Override
    void optionalListSeparator() {
      add(",");
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
        add(" ");
      }
      add(op);
      if (binOp) {
        add(" ");
      }
    }

    /**
     * If the body of a for loop or the then clause of an if statement has a single statement,
     * should it be wrapped in a block? And similar. {@inheritDoc}
     */
    @Override
    boolean shouldPreserveExtras(Node n) {
      // When pretty-printing, always place the statement in its own block so it is printed on a
      // separate line. This allows breakpoints to be placed on the statement.
      //
      // The only exception is an added block around an else-if statement. Example code:
      //   if (0) {
      //     0;
      //   } else if (1) {
      //     1;
      //   }
      // Resulting node tree:
      //   IF
      //     NUMBER
      //     BLOCK
      //       EXPR_RESULT
      //         NUMBER
      //     BLOCK [added_block: 1] <-- we don't want to print this block
      //       IF
      //         NUMBER
      //         BLOCK
      //           EXPR_RESULT
      //             NUMBER

      if (!n.isBlock() || !n.isAddedBlock() || !n.hasParent()) {
        return true;
      }

      Node parent = n.getParent();
      boolean isElse = parent.isIf() && parent.hasXChildren(3) && n == parent.getLastChild();
      boolean onlyChildIsIf = n.hasOneChild() && n.getFirstChild().isIf();
      if (isElse && onlyChildIsIf) {
        return false;
      }

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
      return switch (type) {
        case DO -> false; // Don't break before 'while' in DO-WHILE statements.
        case FUNCTION -> false; // FUNCTIONs are handled separately, don't break here.
        case TRY -> n != parent.getFirstChild(); // Don't break before catch
        case CATCH -> !NodeUtil.hasFinally(getTryForCatch(parent)); // Don't break before finally
        case IF -> n == parent.getLastChild(); // Don't break before else
        default -> true;
      };
    }

    @Override
    void endStatement(boolean needsSemicolon, boolean hasTrailingCommentOnSameLine) {
      add(";");
      if (!hasTrailingCommentOnSameLine) {
        endLine();
      }
      statementNeedsEnded = false;
    }

    @Override
    void endFile() {
      maybeEndStatement();
    }

    private static @Nullable String getNumberFromSource(Node n) {
      if (!n.isNumber()) {
        return null;
      }

      StaticSourceFile staticSrc = NodeUtil.getSourceFile(n);
      if (!(staticSrc instanceof SourceFile src)) {
        return null;
      }

      String srcCode;
      try {
        if (src.isStubSourceFileForAlreadyProvidedInput()) {
          // source file is a stub file, so we can not get number from source.
          return null;
        }
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

    private int lineStartPosition = 0;
    private int preferredBreakPosition = 0;

    /**
     * @param lineBreak break the lines a bit more aggressively
     * @param lineLengthThreshold The length of a line after which we force a newline when possible.
     * @param createSrcMap Whether to gather source position mapping information when printing.
     * @param sourceMapDetailLevel A filter to control which nodes get mapped into the source map.
     * @param licenseTracker A license tracking implementation to manage license text emit. The
     *     CodePrinter will never emit license information directly - it only ever passes nodes to
     *     the license tracker to request tracking.
     */
    private CompactCodePrinter(
        boolean lineBreak,
        int lineLengthThreshold,
        boolean createSrcMap,
        SourceMap.DetailLevel sourceMapDetailLevel,
        LicenseTracker licenseTracker) {
      super(lineLengthThreshold, createSrcMap, sourceMapDetailLevel, licenseTracker);
      this.lineBreak = lineBreak;
    }

    /**
     * Adds a newline to the code, resetting the line length.
     */
    @Override
    void startNewLine() {
      if (lineLength <= 0 && !this.isInTemplateLiteral()) {
        return;
      }

      code.append('\n');
      lineLength = 0;
      lineIndex++;
      lineStartPosition = code.length();
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
      if (lineLength <= lineLengthThreshold) {
        return;
      }

      // Use the preferred position provided it will break the line.
      if (preferredBreakPosition > lineStartPosition
          && preferredBreakPosition < lineStartPosition + lineLength) {
        // If the preferred break position is on the current line.
        code.insert(preferredBreakPosition, '\n');
        reportLineCut(lineIndex, preferredBreakPosition - lineStartPosition);
        lineIndex++;
        lineLength -= (preferredBreakPosition - lineStartPosition);
        lineStartPosition = preferredBreakPosition + 1; // Jump over the inserted newline.
      } else {
        startNewLine();
      }
    }

    @Override
    void notePreferredLineBreak() {
      preferredBreakPosition = code.length();
    }
  }

  /**
   * License Trackers are responsible for ensuring that any licensing information attached to nodes
   * is retained in the final output of JSCompiler.
   *
   * <p>The Code Printer will visit every node being printed and call trackLicensesForNode for that
   * node. Later, some other code can call emitLicenses to retrieve all relevant licenses. Deciding
   * what is relevant varies between implementations of License Trackers - read their javadoc to
   * determine how they are intended to be used.
   */
  public interface LicenseTracker {
    void trackLicensesForNode(Node node);

    ImmutableSet<String> emitLicenses();
  }

  public static final class Builder {
    private final Node root;
    private CompilerOptions options = new CompilerOptions();
    private boolean lineBreak;
    private boolean prettyPrint;
    private boolean outputTypes = false;
    private boolean tagAsTypeSummary;
    private boolean tagAsStrict;
    private @Nullable LicenseTracker licenseTracker;
    private @Nullable JSTypeRegistry registry; // may be null unless using Format.TYPED
    private CodeGeneratorFactory codeGeneratorFactory =
        new CodeGeneratorFactory() {
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

    /** Sets the output options from compiler options. */
    @CanIgnoreReturnValue
    public Builder setCompilerOptions(CompilerOptions options) {
      this.options = options;
      this.prettyPrint = options.isPrettyPrint();
      this.lineBreak = options.shouldAddLineBreak();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTypeRegistry(JSTypeRegistry registry) {
      this.registry = registry;
      return this;
    }

    /**
     * Sets whether pretty printing should be used.
     *
     * @param prettyPrint If true, pretty printing will be used.
     */
    @CanIgnoreReturnValue
    public Builder setPrettyPrint(boolean prettyPrint) {
      this.prettyPrint = prettyPrint;
      return this;
    }

    /**
     * Sets whether line breaking should be done automatically.
     *
     * @param lineBreak If true, line breaking is done automatically.
     */
    @CanIgnoreReturnValue
    public Builder setLineBreak(boolean lineBreak) {
      this.lineBreak = lineBreak;
      return this;
    }

    /**
     * Sets whether to output closure-style type annotations.
     *
     * @param outputTypes If true, outputs closure-style type annotations.
     */
    @CanIgnoreReturnValue
    public Builder setOutputTypes(boolean outputTypes) {
      this.outputTypes = outputTypes;
      return this;
    }

    /**
     * Sets the license tracker to use when printing.
     *
     * @param licenseTracker The tracker to use. Can be null to disable license tracking.
     */
    @CanIgnoreReturnValue
    public Builder setLicenseTracker(LicenseTracker licenseTracker) {
      this.licenseTracker = licenseTracker;
      return this;
    }

    /** Set whether the output should be tagged as an .i.js file. */
    @CanIgnoreReturnValue
    public Builder setTagAsTypeSummary(boolean tagAsTypeSummary) {
      this.tagAsTypeSummary = tagAsTypeSummary;
      return this;
    }

    /** Set whether the output should be tags as ECMASCRIPT 5 Strict. */
    @CanIgnoreReturnValue
    public Builder setTagAsStrict(boolean tagAsStrict) {
      this.tagAsStrict = tagAsStrict;
      return this;
    }

    /** Set a custom code generator factory to enable custom code generation. */
    @CanIgnoreReturnValue
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
      return buildWithSourceMappings().source;
    }

    public SourceAndMappings buildWithSourceMappings() {
      if (root == null) {
        throw new IllegalStateException("Cannot build without root node being specified");
      }

      return toSource(
          root,
          Format.fromOptions(options, outputTypes, prettyPrint),
          options,
          licenseTracker,
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
      if (prettyPrint || options.getOutputFeatureSet().contains(Feature.TYPE_ANNOTATION)) {
        return Format.PRETTY;
      }
      return Format.COMPACT;
    }
  }

  /**
   * SourceAndMappings bundles together the source and generated SourceMap Mappings for that source.
   */
  public static class SourceAndMappings {
    String source;
    @Nullable List<SourceMap.Mapping> mappings;
  }

  /** Converts a tree to JS code */
  private static SourceAndMappings toSource(
      Node root,
      Format outputFormat,
      CompilerOptions options,
      @Nullable LicenseTracker licenseTracker,
      boolean tagAsTypeSummary,
      boolean tagAsStrict,
      boolean lineBreak,
      CodeGeneratorFactory codeGeneratorFactory) {
    checkState(options.getSourceMapDetailLevel() != null);

    MappedCodePrinter mcp =
        outputFormat == Format.COMPACT
            ? new CompactCodePrinter(
                lineBreak,
                options.getLineLengthThreshold(),
                options.shouldGatherSourceMapInfo(),
                options.getSourceMapDetailLevel(),
                licenseTracker)
            : new PrettyCodePrinter(
                options.getLineLengthThreshold(),
                options.shouldGatherSourceMapInfo(),
                options.getSourceMapDetailLevel(),
                licenseTracker);
    CodeGenerator cg = codeGeneratorFactory.getCodeGenerator(outputFormat, mcp);

    if (tagAsTypeSummary) {
      cg.tagAsTypeSummary();
    }
    if (tagAsStrict) {
      cg.tagAsStrict();
    }

    cg.add(root);
    mcp.endFile();

    SourceAndMappings result = new SourceAndMappings();
    result.source = mcp.getCode();

    if (options.shouldGatherSourceMapInfo()) {
      result.mappings = mcp.getSourceMappings(result.source);
    }

    return result;
  }

  private CodePrinter() {}
}
