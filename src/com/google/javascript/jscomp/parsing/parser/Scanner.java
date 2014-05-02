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

import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.parsing.parser.util.ErrorReporter;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

import java.util.LinkedList;

/**
 * Scans javascript source code into tokens. All entrypoints assume the
 * caller is not expecting a regular expression literal except for
 * nextRegularExpressionLiteralToken.
 *
 * <p>7 Lexical Conventions
 */
public class Scanner {
  private final ErrorReporter errorReporter;
  private final SourceFile source;
  private final LinkedList<Token> currentTokens = new LinkedList<>();
  private int index;
  private final CommentRecorder commentRecorder;

  public Scanner(ErrorReporter errorReporter, CommentRecorder commentRecorder,
      SourceFile source) {
    this(errorReporter, commentRecorder, source, 0);
  }

  public Scanner(ErrorReporter errorReporter, CommentRecorder commentRecorder,
      SourceFile file, int offset) {
    this.errorReporter = errorReporter;
    this.commentRecorder = commentRecorder;
    this.source = file;
    this.index = offset;
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
    return currentTokens.remove();
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

  private boolean isRegularExpressionFirstChar(char ch) {
    return isRegularExpressionChar(ch) && ch != '*';
  }

  private boolean isRegularExpressionChar(char ch) {
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
    return index >= 0 && index < source.contents.length();
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
      }
    }
    return false;
  }

  private void reportHtmlCommentWarning() {
    reportWarning("In some cases, '<!--' and '-->' are treated as a '//' " +
                  "for legacy reasons. Removing this from your code is " +
                  "safe for all browsers currently in use.");
  }

  private void skipSingleLineComment() {
    int startOffset = index;
    while (!isAtEnd() && !isLineTerminator(peekChar())) {
      nextChar();
    }
    SourceRange range = getLineNumberTable().getSourceRange(startOffset, index);
    String value = this.source.contents.substring(startOffset, index);
    recordComment(Comment.Type.LINE, range, value);
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
      Comment.Type type = (index - startOffset > 4
          && this.source.contents.charAt(startOffset + 2) == '*')
          ? Comment.Type.JSDOC
          : Comment.Type.BLOCK;
      SourceRange range = getLineNumberTable().getSourceRange(
          startOffset, index);
      String value = this.source.contents.substring(
          startOffset, index);
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
          return  createToken(TokenType.LEFT_SHIFT, beginToken);
        case '=':
          nextChar();
          return createToken(TokenType.LESS_EQUAL, beginToken);
        default:
          return createToken(TokenType.OPEN_ANGLE, beginToken);
        }
      case '>':
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
            return  createToken(TokenType.RIGHT_SHIFT, beginToken);
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
      return new LiteralToken(
          TokenType.NUMBER, getTokenString(beginToken), getTokenRange(beginToken));
    case 'x':
    case 'X':
      nextChar();
      if (!isHexDigit(peekChar())) {
        reportError("Hex Integer Literal must contain at least one digit");
      }
      skipHexDigits();
      return new LiteralToken(
          TokenType.NUMBER, getTokenString(beginToken), getTokenRange(beginToken));
    case '.':
      return scanFractionalNumericLiteral(beginToken);
    case '0': case '1': case '2': case '3': case '4':
    case '5': case '6': case '7': case '8': case '9':
      return scanPostDigit(beginToken);
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

    ch = peekChar();
    while (isIdentifierPart(ch) || ch == '\\') {
      if (ch == '\\') {
        containsUnicodeEscape = true;
      }
      valueBuilder.append(nextChar());
      ch = peekChar();
    }

    String value = valueBuilder.toString();

    // Process unicode escapes.
    while (containsUnicodeEscape && value.contains("\\")) {
      int escapeStart = value.indexOf('\\');
      try {
        if (value.charAt(escapeStart + 1) != 'u') {
          reportError(
              getPosition(index),
              "Invalid escape sequence: '\\%c'", value.charAt(escapeStart + 1));
          return createToken(TokenType.ERROR, beginToken);
        }

        String hexDigits = value.substring(escapeStart + 2, escapeStart + 6);
        ch = (char) Integer.parseInt(hexDigits, 0x10);
        if (!isIdentifierPart(ch)) {
          reportError(
              getPosition(index),
              "Character '%c' (U+%04X) is not a valid identifier char",
              ch, (int) ch);
          return createToken(TokenType.ERROR, beginToken);
        }
        value = value.substring(0, escapeStart) + ch +
            value.substring(escapeStart + 6);
      } catch (NumberFormatException|StringIndexOutOfBoundsException e) {
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

    if (Keywords.isKeyword(value)) {
      return new Token(Keywords.getTokenType(value), getTokenRange(beginToken));
    }

    return new IdentifierToken(getTokenRange(beginToken), value);
  }

  private boolean isIdentifierStart(char ch) {
    switch (ch) {
    case '$':
    case '_':
      return true;
    default:
      // TODO: UnicodeLetter also includes Letter Number (NI)
      return Character.isLetter(ch);
    }
  }

  private boolean isIdentifierPart(char ch) {
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
      reportError(getPosition(beginIndex), "Unterminated String Literal");
    } else {
      nextChar();
    }
    return new LiteralToken(
        TokenType.STRING, getTokenString(beginIndex), getTokenRange(beginIndex));
  }

  private String getTokenString(int beginIndex) {
    return this.source.contents.substring(beginIndex, this.index);
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

    switch (nextChar()) {
    case '\'':
    case '"':
    case '\\':
    case 'b':
    case 'f':
    case 'n':
    case 'r':
    case 't':
    case 'v':
    case '0':
      return true;
    case 'x':
      return skipHexDigit() && skipHexDigit();
    case 'u':
      return skipHexDigit() && skipHexDigit() && skipHexDigit() && skipHexDigit();
    default:
      return true;
    }
  }

  private boolean skipHexDigit() {
    if (!isHexDigit(peekChar())) {
      reportError("Hex digit expected");
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

  private void skipHexDigits() {
    while (isHexDigit(peekChar())) {
      nextChar();
    }
  }

  private static boolean isHexDigit(char ch) {
    return valueOfHexDigit(ch) >= 0;
  }

  private static int valueOfHexDigit(char ch) {
    switch (ch) {
    case '0': case '1': case '2': case '3': case '4':
    case '5': case '6': case '7': case '8': case '9':
      return ch - '0';
    case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
      return ch - 'a' + 10;
    case 'A': case 'B': case 'C': case 'D': case 'E': case 'F':
      return ch - 'A' + 10;
    default:
      return -1;
    }
  }

  private void skipOctalDigits() {
    while (isOctalDigit(peekChar())) {
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
    return source.contents.charAt(index++);
  }

  private boolean peek(char ch) {
    return peekChar() == ch;
  }

  private char peekChar() {
    return peekChar(0);
  }

  private char peekChar(int offset) {
    return !isValidIndex(index + offset) ? '\0' : source.contents.charAt(index + offset);
  }

  private void reportError(String format, Object... arguments) {
    reportError(getPosition(), format, arguments);
  }

  private void reportError(SourcePosition position, String format, Object... arguments) {
    errorReporter.reportError(position, format, arguments);
  }

  private void reportWarning(String format, Object... arguments) {
    errorReporter.reportWarning(getPosition(), format, arguments);
  }
}
