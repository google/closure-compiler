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

package com.google.javascript.jscomp.parsing.parser;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.parsing.parser.util.ErrorReporter;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;
import java.util.ArrayList;
import javax.annotation.Nullable;

/**
 * Scans javascript source code into tokens. All entrypoints assume the
 * caller is not expecting a regular expression literal except for
 * nextRegularExpressionLiteralToken.
 *
 * <p>7 Lexical Conventions
 */
public class Scanner {
  private final boolean parseTypeSyntax;
  private final ErrorReporter errorReporter;
  private final SourceFile source;
  private final String contents;
  private final int contentsLength;
  private final ArrayList<Token> currentTokens = new ArrayList<>();
  private int index;
  private final CommentRecorder commentRecorder;
  private int typeParameterLevel;

  public Scanner(
      boolean parseTypeSyntax,
      ErrorReporter errorReporter,
      CommentRecorder commentRecorder,
      SourceFile source) {
    this(parseTypeSyntax, errorReporter, commentRecorder, source, 0);
  }

  public Scanner(
      boolean parseTypeSyntax,
      ErrorReporter errorReporter,
      CommentRecorder commentRecorder,
      SourceFile file,
      int offset) {
    this.parseTypeSyntax = parseTypeSyntax;
    this.errorReporter = errorReporter;
    this.commentRecorder = commentRecorder;
    this.source = file;
    // To help reason about the expected JVM performance unwrap "file" values.
    // The scanner is key to the parsing speed.
    this.contents = file.contents;
    this.contentsLength = file.contents.length();
    this.index = offset;
    this.typeParameterLevel = 0;
  }

  public interface CommentRecorder {
    void recordComment(Comment.Type type, SourceRange range, String value);
  }

  private LineNumberTable getLineNumberTable() {
    return this.getFile().lineNumberTable;
  }

  public SourceFile getFile() {
    return source;
  }

  public int getOffset() {
    return currentTokens.isEmpty()
        ? index
        : peekToken().location.start.offset;
  }

  public void setOffset(int index) {
    currentTokens.clear();
    this.index = index;
  }

  public SourcePosition getPosition() {
    return getPosition(getOffset());
  }

  private SourcePosition getPosition(int offset) {
    return getLineNumberTable().getSourcePosition(offset);
  }

  private SourceRange getTokenRange(int startOffset) {
    return getLineNumberTable().getSourceRange(startOffset, index);
  }

  public Token nextToken() {
    peekToken();
    return currentTokens.remove(0);
  }

  private void clearTokenLookahead() {
    index = getOffset();
    currentTokens.clear();
  }

  public LiteralToken nextRegularExpressionLiteralToken() {
    clearTokenLookahead();

    int beginToken = index;

    // leading '/'
    nextChar();

    // body
    if (!skipRegularExpressionBody()) {
      return new LiteralToken(
          TokenType.REGULAR_EXPRESSION,
          getTokenString(beginToken),
          getTokenRange(beginToken));
    }

    // separating '/'
    if (peekChar() != '/') {
      reportError("Expected '/' in regular expression literal");
      return new LiteralToken(
          TokenType.REGULAR_EXPRESSION,
          getTokenString(beginToken),
          getTokenRange(beginToken));
    }
    nextChar();

    // flags
    while (isIdentifierPart(peekChar())) {
      nextChar();
    }

    return new LiteralToken(
        TokenType.REGULAR_EXPRESSION,
        getTokenString(beginToken),
        getTokenRange(beginToken));
  }

  public TemplateLiteralToken nextTemplateLiteralToken() {
    Token token = nextToken();
    if (isAtEnd() || token.type != TokenType.CLOSE_CURLY) {
      reportError(getPosition(index), "Expected '}' after expression in template literal");
    }

    return nextTemplateLiteralTokenShared(TokenType.TEMPLATE_TAIL, TokenType.TEMPLATE_MIDDLE);
  }

  private boolean skipRegularExpressionBody() {
    if (!isRegularExpressionFirstChar(peekChar())) {
      reportError("Expected regular expression first char");
      return false;
    }
    if (!skipRegularExpressionChar()) {
      return false;
    }
    while (!isAtEnd() && isRegularExpressionChar(peekChar())) {
      if (!skipRegularExpressionChar()) {
        return false;
      }
    }
    return true;
  }

  private boolean skipRegularExpressionChar() {
    switch (peekChar()) {
      case '\\':
        return skipRegularExpressionBackslashSequence();
      case '[':
        return skipRegularExpressionClass();
      default:
        nextChar();
        return true;
    }
  }

  private boolean skipRegularExpressionBackslashSequence() {
    // TODO(tbreisacher): Warn if this is an unnecessary escape, like we do for string literals.
    nextChar();
    if (isLineTerminator(peekChar())) {
      reportError("New line not allowed in regular expression literal");
      return false;
    }
    nextChar();
    return true;
  }

  private boolean skipRegularExpressionClass() {
    nextChar();
    while (!isAtEnd() && peekRegularExpressionClassChar()) {
      if (!skipRegularExpressionClassChar()) {
        return false;
      }
    }
    if (peekChar() != ']') {
      reportError("']' expected");
      return false;
    }
    nextChar();
    return true;
  }

  private boolean peekRegularExpressionClassChar() {
    return peekChar() != ']' && !isLineTerminator(peekChar());
  }

  private boolean skipRegularExpressionClassChar() {
    if (peek('\\')) {
      return skipRegularExpressionBackslashSequence();
    }
    nextChar();
    return true;
  }

  private static boolean isRegularExpressionFirstChar(char ch) {
    return isRegularExpressionChar(ch) && ch != '*';
  }

  private static boolean isRegularExpressionChar(char ch) {
    switch (ch) {
      case '/':
        return false;
      case '\\':
      case '[':
        return true;
      default:
        return !isLineTerminator(ch);
    }
  }

  public Token peekToken() {
    return peekToken(0);
  }

  public Token peekToken(int index) {
    while (currentTokens.size() <= index) {
      currentTokens.add(scanToken());
    }
    return currentTokens.get(index);
  }

  private boolean isAtEnd() {
    return !isValidIndex(index);
  }

  private boolean isValidIndex(int index) {
    return index >= 0 & index < contentsLength;
  }

  // 7.2 White Space
  /**
   * Returns true if the whitespace that was skipped included any
   * line terminators.
   */
  private boolean skipWhitespace() {
    boolean foundLineTerminator = false;
    while (!isAtEnd() && peekWhitespace()) {
      if (isLineTerminator(nextChar())) {
        foundLineTerminator = true;
      }
    }
    return foundLineTerminator;
  }

  private boolean peekWhitespace() {
    return isWhitespace(peekChar());
  }

  private static boolean isWhitespace(char ch) {
    switch (ch) {
      case '\u0009':  // Tab
      case '\u000B':  // Vertical Tab
      case '\u000C':  // Form Feed
      case '\u0020':  // Space
      case '\u00A0':  // No-break space
      case '\uFEFF':  // Byte Order Mark
      case '\n':      // Line Feed
      case '\r':      // Carriage Return
      case '\u2028':  // Line Separator
      case '\u2029':  // Paragraph Separator
        // TODO: there are other Unicode Category 'Zs' chars that should go here.
        return true;
      default:
        return false;
    }
  }

  // 7.3 Line Terminators
  private static boolean isLineTerminator(char ch) {
    switch (ch) {
      case '\n': // Line Feed
      case '\r':  // Carriage Return
      case '\u2028':  // Line Separator
      case '\u2029':  // Paragraph Separator
        return true;
      default:
        return false;
    }
  }

  // 7.4 Comments
  private void skipComments() {
    while (skipComment())
    {}
  }

  private boolean skipComment() {
    boolean isStartOfLine = skipWhitespace();
    if (!isAtEnd()) {
      switch (peekChar(0)) {
        case '/':
          switch (peekChar(1)) {
            case '/':
              skipSingleLineComment();
              return true;
            case '*':
              skipMultiLineComment();
              return true;
            default: // fall out
          }
          break;
        case '<':
          // Check if this is the start of an HTML comment ("<!--").
          // http://www.w3.org/TR/REC-html40/interact/scripts.html#h-18.3.2
          if (peekChar(1) == '!' && peekChar(2) == '-' && peekChar(3) == '-') {
            reportHtmlCommentWarning();
            skipSingleLineComment();
            return true;
          }
          break;
        case '-':
          // Check if this is the start of an HTML comment ("-->").
          // Note that the spec does not require us to check for this case,
          // but there is some legacy code that depends on this behavior.
          if (isStartOfLine && peekChar(1) == '-' && peekChar(2) == '>') {
            reportHtmlCommentWarning();
            skipSingleLineComment();
            return true;
          }
          break;
        case '#':
          if (index == 0 && peekChar(1) == '!') {
            skipSingleLineComment(Comment.Type.SHEBANG);
            return true;
          }
          break;
        default: // fall out
      }
    }
    return false;
  }

  private void reportHtmlCommentWarning() {
    reportWarning(
        "In some cases, '<!--' and '-->' are treated as a '//' "
            + "for legacy reasons. Removing this from your code is "
            + "safe for all browsers currently in use.");
  }

  private void skipSingleLineComment() {
    skipSingleLineComment(Comment.Type.LINE);
  }

  private void skipSingleLineComment(Comment.Type type) {
    int startOffset = index;
    while (!isAtEnd() && !isLineTerminator(peekChar())) {
      nextChar();
    }
    SourceRange range = getLineNumberTable().getSourceRange(startOffset, index);
    String value = this.contents.substring(startOffset, index);
    recordComment(type, range, value);
  }

  private void recordComment(
      Comment.Type type, SourceRange range, String value) {
    commentRecorder.recordComment(type, range, value);
  }

  private void skipMultiLineComment() {
    int startOffset = index;
    nextChar(); // '/'
    nextChar(); // '*'
    while (!isAtEnd() && (peekChar() != '*' || peekChar(1) != '/')) {
      nextChar();
    }
    if (!isAtEnd()) {
      nextChar();
      nextChar();
      Comment.Type type = Comment.Type.BLOCK;
      if (index - startOffset > 4) {
        if (this.contents.charAt(startOffset + 2) == '*') {
          type = Comment.Type.JSDOC;
        } else if (this.contents.charAt(startOffset + 2) == '!') {
          type = Comment.Type.IMPORTANT;
        }
      }
      SourceRange range = getLineNumberTable().getSourceRange(startOffset, index);
      String value = this.contents.substring(startOffset, index);
      recordComment(type, range, value);
    } else {
      reportError("unterminated comment");
    }
  }

  private Token scanToken() {
    skipComments();
    int beginToken = index;
    if (isAtEnd()) {
      return createToken(TokenType.END_OF_FILE, beginToken);
    }
    char ch = nextChar();
    switch (ch) {
      case '{': return createToken(TokenType.OPEN_CURLY, beginToken);
      case '}': return createToken(TokenType.CLOSE_CURLY, beginToken);
      case '(': return createToken(TokenType.OPEN_PAREN, beginToken);
      case ')': return createToken(TokenType.CLOSE_PAREN, beginToken);
      case '[': return createToken(TokenType.OPEN_SQUARE, beginToken);
      case ']': return createToken(TokenType.CLOSE_SQUARE, beginToken);
      case '.':
        if (isDecimalDigit(peekChar())) {
          return scanNumberPostPeriod(beginToken);
        }

        // Harmony spread operator
        if (peek('.') && peekChar(1) == '.') {
          nextChar();
          nextChar();
          return createToken(TokenType.SPREAD, beginToken);
        }

        return createToken(TokenType.PERIOD, beginToken);
      case ';': return createToken(TokenType.SEMI_COLON, beginToken);
      case ',': return createToken(TokenType.COMMA, beginToken);
      case '~': return createToken(TokenType.TILDE, beginToken);
      case '?': return createToken(TokenType.QUESTION, beginToken);
      case ':': return createToken(TokenType.COLON, beginToken);
      case '<':
        switch (peekChar()) {
          case '<':
            nextChar();
            if (peek('=')) {
              nextChar();
              return createToken(TokenType.LEFT_SHIFT_EQUAL, beginToken);
            }
            return createToken(TokenType.LEFT_SHIFT, beginToken);
          case '=':
            nextChar();
            return createToken(TokenType.LESS_EQUAL, beginToken);
          default:
            return createToken(TokenType.OPEN_ANGLE, beginToken);
        }
      case '>':
        if (typeParameterLevel > 0) {
          return createToken(TokenType.CLOSE_ANGLE, beginToken);
        }
        switch (peekChar()) {
          case '>':
            nextChar();
            switch (peekChar()) {
              case '=':
                nextChar();
                return createToken(TokenType.RIGHT_SHIFT_EQUAL, beginToken);
              case '>':
                nextChar();
                if (peek('=')) {
                  nextChar();
                  return createToken(TokenType.UNSIGNED_RIGHT_SHIFT_EQUAL, beginToken);
                }
                return createToken(TokenType.UNSIGNED_RIGHT_SHIFT, beginToken);
              default:
                return createToken(TokenType.RIGHT_SHIFT, beginToken);
            }
          case '=':
            nextChar();
            return createToken(TokenType.GREATER_EQUAL, beginToken);
          default:
            return createToken(TokenType.CLOSE_ANGLE, beginToken);
        }
      case '=':
        switch (peekChar()) {
          case '=':
            nextChar();
            if (peek('=')) {
              nextChar();
              return createToken(TokenType.EQUAL_EQUAL_EQUAL, beginToken);
            }
            return createToken(TokenType.EQUAL_EQUAL, beginToken);
          case '>':
            nextChar();
            return createToken(TokenType.ARROW, beginToken);
          default:
            return createToken(TokenType.EQUAL, beginToken);
        }
      case '!':
        if (peek('=')) {
          nextChar();
          if (peek('=')) {
            nextChar();
            return createToken(TokenType.NOT_EQUAL_EQUAL, beginToken);
          }
          return createToken(TokenType.NOT_EQUAL, beginToken);
        }
        return createToken(TokenType.BANG, beginToken);
      case '*':
        if (peek('=')) {
          nextChar();
          return createToken(TokenType.STAR_EQUAL, beginToken);
        } else if (peek('*')) {
          nextChar();
          // '**' seen so far
          if (peek('=')) {
            nextChar();
            return createToken(TokenType.STAR_STAR_EQUAL, beginToken);
          } else {
            return createToken(TokenType.STAR_STAR, beginToken);
          }
        }
        return createToken(TokenType.STAR, beginToken);
      case '%':
        if (peek('=')) {
          nextChar();
          return createToken(TokenType.PERCENT_EQUAL, beginToken);
        }
        return createToken(TokenType.PERCENT, beginToken);
      case '^':
        if (peek('=')) {
          nextChar();
          return createToken(TokenType.CARET_EQUAL, beginToken);
        }
        return createToken(TokenType.CARET, beginToken);
      case '/':
        if (peek('=')) {
          nextChar();
          return createToken(TokenType.SLASH_EQUAL, beginToken);
        }
        return createToken(TokenType.SLASH, beginToken);
      case '+':
        switch (peekChar()) {
          case '+':
            nextChar();
            return createToken(TokenType.PLUS_PLUS, beginToken);
          case '=':
            nextChar();
            return createToken(TokenType.PLUS_EQUAL, beginToken);
          default:
            return createToken(TokenType.PLUS, beginToken);
        }
      case '-':
        switch (peekChar()) {
          case '-':
            nextChar();
            return createToken(TokenType.MINUS_MINUS, beginToken);
          case '=':
            nextChar();
            return createToken(TokenType.MINUS_EQUAL, beginToken);
          default:
            return createToken(TokenType.MINUS, beginToken);
        }
      case '&':
        switch (peekChar()) {
          case '&':
            nextChar();
            return createToken(TokenType.AND, beginToken);
          case '=':
            nextChar();
            return createToken(TokenType.AMPERSAND_EQUAL, beginToken);
          default:
            return createToken(TokenType.AMPERSAND, beginToken);
        }
      case '|':
        switch (peekChar()) {
          case '|':
            nextChar();
            return createToken(TokenType.OR, beginToken);
          case '=':
            nextChar();
            return createToken(TokenType.BAR_EQUAL, beginToken);
          default:
            return createToken(TokenType.BAR, beginToken);
        }
      case '#':
        return createToken(TokenType.POUND, beginToken);
      // TODO: add NumberToken
      // TODO: character following NumericLiteral must not be an IdentifierStart or DecimalDigit
      case '0':
        return scanPostZero(beginToken);
      case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
        return scanPostDigit(beginToken);
      case '"':
      case '\'':
        return scanStringLiteral(beginToken, ch);
      case '`':
        return scanTemplateLiteral(beginToken);
      default:
        return scanIdentifierOrKeyword(beginToken, ch);
    }
  }

  private Token scanNumberPostPeriod(int beginToken) {
    skipDecimalDigits();
    return scanExponentOfNumericLiteral(beginToken);
  }

  private Token scanPostDigit(int beginToken) {
    skipDecimalDigits();
    return scanFractionalNumericLiteral(beginToken);
  }

  private Token scanPostZero(int beginToken) {
    switch (peekChar()) {
      case 'b':
      case 'B':
        // binary
        nextChar();
        if (!isBinaryDigit(peekChar())) {
          reportError("Binary Integer Literal must contain at least one digit");
        }
        skipBinaryDigits();
        return new LiteralToken(
            TokenType.NUMBER, getTokenString(beginToken), getTokenRange(beginToken));

      case 'o':
      case 'O':
        // octal
        nextChar();
        if (!isOctalDigit(peekChar())) {
          reportError("Octal Integer Literal must contain at least one digit");
        }
        skipOctalDigits();
        if (peek('8') || peek('9')) {
          reportError("Invalid octal digit in octal literal.");
        }
        return new LiteralToken(
            TokenType.NUMBER, getTokenString(beginToken), getTokenRange(beginToken));
      case 'x':
      case 'X':
        nextChar();
        if (!peekHexDigit()) {
          reportError("Hex Integer Literal must contain at least one digit");
        }
        skipHexDigits();
        return new LiteralToken(
            TokenType.NUMBER, getTokenString(beginToken), getTokenRange(beginToken));
      case 'e':
      case 'E':
        return scanExponentOfNumericLiteral(beginToken);
      case '.':
        return scanFractionalNumericLiteral(beginToken);
      case '0': case '1': case '2': case '3': case '4':
      case '5': case '6': case '7': case '8': case '9':
        skipDecimalDigits();
        if (peek('.')) {
          nextChar();
          skipDecimalDigits();
        }
        return new LiteralToken(
            TokenType.NUMBER, getTokenString(beginToken), getTokenRange(beginToken));
      default:
        return new LiteralToken(
            TokenType.NUMBER, getTokenString(beginToken), getTokenRange(beginToken));
    }
  }

  private Token createToken(TokenType type, int beginToken) {
    return new Token(type, getTokenRange(beginToken));
  }

  private Token scanIdentifierOrKeyword(int beginToken, char ch) {
    StringBuilder valueBuilder = new StringBuilder();
    valueBuilder.append(ch);

    boolean containsUnicodeEscape = ch == '\\';
    boolean bracedUnicodeEscape = false;
    int unicodeEscapeLen = containsUnicodeEscape ? 1 : 0;

    ch = peekChar();
    while (isIdentifierPart(ch)
        || ch == '\\'
        || (ch == '{' && unicodeEscapeLen == 2)
        || (ch == '}' && bracedUnicodeEscape)) {
      if (ch == '\\') {
        containsUnicodeEscape = true;
      }
      // Update length of current Unicode escape.
      if (ch == '\\' || unicodeEscapeLen > 0) {
        unicodeEscapeLen++;
      }
      // Enter Unicode point escape.
      if (ch == '{') {
        bracedUnicodeEscape = true;
      }
      // Exit Unicode escape
      if (ch == '}' || (unicodeEscapeLen >= 6 && !bracedUnicodeEscape)) {
        bracedUnicodeEscape = false;
        unicodeEscapeLen = 0;
      }

      // Add character to token
      valueBuilder.append(nextChar());
      ch = peekChar();
    }

    String value = valueBuilder.toString();

    // Process unicode escapes.
    if (containsUnicodeEscape) {
      value = processUnicodeEscapes(value);
      if (value == null) {
        reportError(
            getPosition(index),
            "Invalid escape sequence");
        return createToken(TokenType.ERROR, beginToken);
      }
    }

    // Check to make sure the first character (or the unicode escape at the
    // beginning of the identifier) is a valid identifier start character.
    char start = value.charAt(0);
    if (!isIdentifierStart(start)) {
      reportError(
          getPosition(beginToken),
          "Character '%c' (U+%04X) is not a valid identifier start char",
          start, (int) start);
      return createToken(TokenType.ERROR, beginToken);
    }

    Keywords k = Keywords.get(value, parseTypeSyntax);
    if (k != null) {
      return new Token(k.type, getTokenRange(beginToken));
    }

    return new IdentifierToken(getTokenRange(beginToken), value);
  }

  /**
   * Converts unicode escapes in the given string to the equivalent unicode character. If there are
   * no escapes, returns the input unchanged. If there is an invalid escape sequence, returns null.
   */
  private static String processUnicodeEscapes(String value) {
    while (value.contains("\\")) {
      int escapeStart = value.indexOf('\\');
      try {
        if (value.charAt(escapeStart + 1) != 'u') {
          return null;
        }

        String hexDigits;
        int escapeEnd;
        if (value.charAt(escapeStart + 2) != '{') {
          // Simple escape with exactly four hex digits: \\uXXXX
          escapeEnd = escapeStart + 6;
          hexDigits = value.substring(escapeStart + 2, escapeEnd);
        } else {
          // Escape with braces can have any number of hex digits: \\u{XXXXXXX}
          escapeEnd = escapeStart + 3;
          while (Character.digit(value.charAt(escapeEnd), 0x10) >= 0) {
            escapeEnd++;
          }
          if (value.charAt(escapeEnd) != '}') {
            return null;
          }
          hexDigits = value.substring(escapeStart + 3, escapeEnd);
          escapeEnd++;
        }
        // TODO(mattloring): Allow code points greater than the size of a char
        char ch = (char) Integer.parseInt(hexDigits, 0x10);
        if (!isIdentifierPart(ch)) {
          return null;
        }
        value = value.substring(0, escapeStart) + ch + value.substring(escapeEnd);
      } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
        return null;
      }
    }
    return value;
  }

  @SuppressWarnings("ShortCircuitBoolean") // Intentional to minimize branches in this code
  private static boolean isIdentifierStart(char ch) {
    // Most code is written in pure ASCII create a fast path here.
    if (ch <= 127) {
      // Intentionally avoiding short circuiting behavior of "||" and "&&".
      // This minimizes branches in this code which minimizes branch prediction misses.
      return ((ch >= 'A' & ch <= 'Z') | (ch >= 'a' & ch <= 'z') | (ch == '_' | ch == '$'));
    }

    // Workaround b/36459436
    // When running under GWT, Character.isLetter only handles ASCII
    // Angular relies heavily on U+0275 (Latin Barred O)
    return ch == 0x0275
        // TODO: UnicodeLetter also includes Letter Number (NI)
        || Character.isLetter(ch);
  }

  @SuppressWarnings("ShortCircuitBoolean") // Intentional to minimize branches in this code
  private static boolean isIdentifierPart(char ch) {
    // Most code is written in pure ASCII create a fast path here.
    if (ch <= 127) {
      return ((ch >= 'A' & ch <= 'Z')
          | (ch >= 'a' & ch <= 'z')
          | (ch >= '0' & ch <= '9')
          | (ch == '_' | ch == '$')); // _ or $
    }
    // TODO: identifier part character classes
    // CombiningMark
    //   Non-Spacing mark (Mn)
    //   Combining spacing mark(Mc)
    // Connector punctuation (Pc)
    // Zero Width Non-Joiner
    // Zero Width Joiner
    return isIdentifierStart(ch) || Character.isDigit(ch);
  }

  private Token scanStringLiteral(int beginIndex, char terminator) {
    while (peekStringLiteralChar(terminator)) {
      if (!skipStringLiteralChar()) {
        return new LiteralToken(
            TokenType.STRING, getTokenString(beginIndex), getTokenRange(beginIndex));
      }
    }
    if (peekChar() != terminator) {
      reportError(getPosition(beginIndex), "Unterminated string literal");
    } else {
      nextChar();
    }
    return new LiteralToken(
        TokenType.STRING, getTokenString(beginIndex), getTokenRange(beginIndex));
  }

  private Token scanTemplateLiteral(int beginIndex) {
    if (isAtEnd()) {
      reportError(getPosition(beginIndex), "Unterminated template literal");
    }

    return nextTemplateLiteralTokenShared(
        TokenType.NO_SUBSTITUTION_TEMPLATE, TokenType.TEMPLATE_HEAD);
  }

  private TemplateLiteralToken nextTemplateLiteralTokenShared(
      TokenType endType, TokenType middleType) {
    int beginIndex = index;
    SkipTemplateCharactersResult skipTemplateCharactersResult = skipTemplateCharacters();
    if (isAtEnd()) {
      reportError(getPosition(beginIndex), "Unterminated template literal");
    }

    String value = getTokenString(beginIndex);
    switch (peekChar()) {
      case '`':
        nextChar();
        return new TemplateLiteralToken(
            endType,
            value,
            skipTemplateCharactersResult.getErrorMessage(),
            skipTemplateCharactersResult.getPosition(),
            getTokenRange(beginIndex - 1));
      case '$':
        nextChar(); // $
        nextChar(); // {
        return new TemplateLiteralToken(
            middleType,
            value,
            skipTemplateCharactersResult.getErrorMessage(),
            skipTemplateCharactersResult.getPosition(),
            getTokenRange(beginIndex - 1));
      default: // Should have reported error already
        return new TemplateLiteralToken(
            endType,
            value,
            skipTemplateCharactersResult.getErrorMessage(),
            skipTemplateCharactersResult.getPosition(),
            getTokenRange(beginIndex - 1));
    }
  }

  private String getTokenString(int beginIndex) {
    return this.contents.substring(beginIndex, index);
  }

  private boolean peekStringLiteralChar(char terminator) {
    return !isAtEnd() && peekChar() != terminator && !isLineTerminator(peekChar());
  }

  private boolean skipStringLiteralChar() {
    if (peek('\\')) {
      return skipStringLiteralEscapeSequence();
    }
    nextChar();
    return true;
  }

  private SkipTemplateCharactersResult skipTemplateCharacters() {
    SkipTemplateCharactersResult result = createSkipTemplateCharactersResult(null);
    while (!isAtEnd()) {
      switch (peekChar()) {
        case '`':
          return result;
        case '\\':
          // There might be multiple errors. Take the first one but continue scanning
          SkipTemplateCharactersResult newError = skipTemplateLiteralEscapeSequence();
          if (newError != null && !result.hasError()) {
            result = newError;
          }
          break;
        case '$':
          if (peekChar(1) == '{') {
            return result;
          }
          // Fall through.
        default:
          nextChar();
      }
    }
    return result;
  }

  @SuppressWarnings("IdentityBinaryExpression") // for "skipHexDigit() && skipHexDigit()"
  private SkipTemplateCharactersResult skipTemplateLiteralEscapeSequence() {
    nextChar();
    if (isAtEnd()) {
      reportError("Unterminated template literal escape sequence");
      return null;
    }
    if (isLineTerminator(peekChar())) {
      skipLineTerminator();
      return null;
    }
    char next = nextChar();
    switch (next) {
      case '0':
        if (peekOctalDigit()) {
          return createSkipTemplateCharactersResult("Invalid escape sequence");
        }
        return null;
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
        return createSkipTemplateCharactersResult("Invalid escape sequence");
      case 'x':
        boolean doubleHexDigit = skipHexDigit() && skipHexDigit();
        if (!doubleHexDigit) {
          return createSkipTemplateCharactersResult("Hex digit expected");
        }
        return null;
      case 'u':
        if (peek('{')) {
          nextChar();
          if (peek('}')) {
            return createSkipTemplateCharactersResult("Empty unicode escape");
          }
          boolean allHexDigits = true;
          while (!peek('}') && allHexDigits) {
            allHexDigits = allHexDigits && skipHexDigit();
          }
          if (!allHexDigits) {
            return createSkipTemplateCharactersResult("Hex digit expected");
          }
          nextChar();
          return null;
        } else {
          boolean quadHexDigit =
              skipHexDigit() && skipHexDigit() && skipHexDigit() && skipHexDigit();
          if (!quadHexDigit) {
            return createSkipTemplateCharactersResult("Hex digit expected");
          }
          return null;
        }
      default:
        return null;
    }
  }

  @SuppressWarnings("IdentityBinaryExpression") // for "skipHexDigit() && skipHexDigit()"
  private boolean skipStringLiteralEscapeSequence() {
    nextChar();
    if (isAtEnd()) {
      reportError("Unterminated string literal escape sequence");
      return false;
    }
    if (isLineTerminator(peekChar())) {
      skipLineTerminator();
      return true;
    }

    char next = nextChar();
    switch (next) {
      case '\'':
      case '"':
      case '`':
      case '\\':
      case 'b':
      case 'f':
      case 'n':
      case 'r':
      case 't':
      case 'v':
      case '0':
        return true;
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
        break;
      case 'x':
        boolean doubleHexDigit = skipHexDigit() && skipHexDigit();
        if (!doubleHexDigit) {
          reportError("Hex digit expected");
        }
        return doubleHexDigit;
      case 'u':
        if (peek('{')) {
          nextChar();
          if (peek('}')) {
            reportError("Empty unicode escape");
            return false;
          }
          boolean allHexDigits = true;
          while (!peek('}') && allHexDigits) {
            allHexDigits = allHexDigits && skipHexDigit();
          }
          if (!allHexDigits) {
            reportError("Hex digit expected");
          }
          nextChar();
          return allHexDigits;
        } else {
          boolean quadHexDigit =
              skipHexDigit() && skipHexDigit() && skipHexDigit() && skipHexDigit();
          if (!quadHexDigit) {
            reportError("Hex digit expected");
          }
          return quadHexDigit;
        }
      default:
        break;
    }

    if (next == '/') {
      // Don't warn for '\/' (for now) since it's common in "<\/script>"
    } else {
      reportWarning("Unnecessary escape: '\\%s' is equivalent to just '%s'", next, next);
    }
    return true;
  }

  private boolean skipHexDigit() {
    if (!peekHexDigit()) {
      return false;
    }
    nextChar();
    return true;
  }

  private void skipLineTerminator() {
    char first = nextChar();
    if (first == '\r' && peek('\n')) {
      nextChar();
    }
  }

  private LiteralToken scanFractionalNumericLiteral(int beginToken) {
    if (peek('.')) {
      nextChar();
      skipDecimalDigits();
    }
    return scanExponentOfNumericLiteral(beginToken);
  }

  private LiteralToken scanExponentOfNumericLiteral(int beginToken) {
    switch (peekChar()) {
      case 'e':
      case 'E':
        nextChar();
        switch (peekChar()) {
          case '+':
          case '-':
            nextChar();
            break;
          default: // fall out
        }
        if (!isDecimalDigit(peekChar())) {
          reportError("Exponent part must contain at least one digit");
        }
        skipDecimalDigits();
        break;
      default:
        break;
    }
    return new LiteralToken(
        TokenType.NUMBER, getTokenString(beginToken), getTokenRange(beginToken));
  }

  private void skipDecimalDigits() {
    while (isDecimalDigit(peekChar())) {
      nextChar();
    }
  }

  private static boolean isDecimalDigit(char ch) {
    switch (ch) {
      case '0': case '1': case '2': case '3': case '4':
      case '5': case '6': case '7': case '8': case '9':
        return true;
      default:
        return false;
    }
  }

  private boolean peekHexDigit() {
    return Character.digit(peekChar(), 0x10) >= 0;
  }

  private void skipHexDigits() {
    while (peekHexDigit()) {
      nextChar();
    }
  }

  private boolean peekOctalDigit() {
    return isOctalDigit(peekChar());
  }

  private void skipOctalDigits() {
    while (peekOctalDigit()) {
      nextChar();
    }
  }

  private static boolean isOctalDigit(char ch) {
    return valueOfOctalDigit(ch) >= 0;
  }

  private static int valueOfOctalDigit(char ch) {
    switch (ch) {
      case '0': case '1': case '2': case '3': case '4':
      case '5': case '6': case '7':
        return ch - '0';
      default:
        return -1;
    }
  }

  private void skipBinaryDigits() {
    while (isBinaryDigit(peekChar())) {
      nextChar();
    }
  }

  private static boolean isBinaryDigit(char ch) {
    return valueOfBinaryDigit(ch) >= 0;
  }

  private static int valueOfBinaryDigit(char ch) {
    switch (ch) {
      case '0':
        return 0;
      case '1':
        return 1;
      default:
        return -1;
    }
  }

  private char nextChar() {
    if (isAtEnd()) {
      return '\0';
    }
    return contents.charAt(index++);
  }

  private boolean peek(char ch) {
    return peekChar() == ch;
  }

  private char peekChar() {
    return peekChar(0);
  }

  private char peekChar(int offset) {
    return !isValidIndex(index + offset) ? '\0' : contents.charAt(index + offset);
  }

  @FormatMethod
  private void reportError(@FormatString String format, Object... arguments) {
    reportError(getPosition(), format, arguments);
  }

  @FormatMethod
  private void reportError(
      SourcePosition position, @FormatString String format, Object... arguments) {
    errorReporter.reportError(position, format, arguments);
  }

  @FormatMethod
  private void reportWarning(@FormatString String format, Object... arguments) {
    errorReporter.reportWarning(getPosition(), format, arguments);
  }

  void incTypeParameterLevel() {
    typeParameterLevel++;
  }

  void decTypeParameterLevel() {
    typeParameterLevel--;
  }

  private SkipTemplateCharactersResult createSkipTemplateCharactersResult(String message) {
    return new SkipTemplateCharactersResult(message, getPosition());
  }

  private static class SkipTemplateCharactersResult {
    @Nullable private final String errorMessage;
    private final SourcePosition position;

    SkipTemplateCharactersResult(String message, SourcePosition position) {
      this.errorMessage = message;
      this.position = position;
    }

    String getErrorMessage() {
      return this.errorMessage;
    }

    SourcePosition getPosition() {
      return this.position;
    }

    boolean hasError() {
      return this.errorMessage != null;
    }
  }
}
