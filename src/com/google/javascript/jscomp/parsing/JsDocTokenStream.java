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

package com.google.javascript.jscomp.parsing;

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.head.ScriptRuntime;

/**
 * This class implements the scanner for JsDoc strings.
 *
 * It is heavily based on Rhino's TokenStream.
 *
 */
class JsDocTokenStream {
  /*
   * For chars - because we need something out-of-range
   * to check.  (And checking EOF by exception is annoying.)
   * Note distinction from EOF token type!
   */
  private final static int
      EOF_CHAR = -1;

  JsDocTokenStream(String sourceString) {
    this(sourceString, 0);
  }

  JsDocTokenStream(String sourceString, int lineno) {
    this(sourceString, lineno, 0);
  }

  JsDocTokenStream(String sourceString, int lineno, int initCharno) {
    Preconditions.checkNotNull(sourceString);
    this.lineno = lineno;
    this.sourceString = sourceString;
    this.sourceEnd = sourceString.length();
    this.sourceCursor = this.cursor = 0;
    this.initLineno = lineno;
    this.initCharno = initCharno;
  }

  /**
   * Tokenizes JSDoc comments.
   */
  @SuppressWarnings("fallthrough")
  final JsDocToken getJsDocToken() {
    int c;
    stringBufferTop = 0;
    for (;;) {
      // eat white spaces
      for (;;) {
        charno = -1;
        c = getChar();
        if (c == EOF_CHAR) {
          return JsDocToken.EOF;
        } else if (c == '\n') {
          return JsDocToken.EOL;
        } else if (!isJSSpace(c)) {
          break;
        }
      }

      switch (c) {
        // annotation, e.g. @type or @constructor
        case '@':
          do {
            c = getChar();
            if (isAlpha(c)) {
              addToString(c);
            } else {
              ungetChar(c);
              this.string = getStringFromBuffer();
              stringBufferTop = 0;
              return JsDocToken.ANNOTATION;
            }
          } while (true);

        case '*':
          if (matchChar('/')) {
            return JsDocToken.EOC;
          } else {
            return JsDocToken.STAR;
          }

        case ',':
          return JsDocToken.COMMA;

        case '>':
          return JsDocToken.GT;

        case '(':
          return JsDocToken.LP;

        case ')':
          return JsDocToken.RP;

        case '{':
          return JsDocToken.LC;

        case '}':
          return JsDocToken.RC;

        case '[':
          return JsDocToken.LB;

        case ']':
          return JsDocToken.RB;

        case '?':
          return JsDocToken.QMARK;

        case '!':
          return JsDocToken.BANG;

        case ':':
          return JsDocToken.COLON;

        case '=':
          return JsDocToken.EQUALS;

        case '|':
          matchChar('|');
          return JsDocToken.PIPE;

        case '.':
          c = getChar();
          if (c == '<') {
            return JsDocToken.LT;
          } else {
            if (c == '.') {
              c = getChar();
              if (c == '.') {
                return JsDocToken.ELLIPSIS;
              } else {
                addToString('.');
              }
            }
            // we may backtrack across line boundary
            ungetBuffer[ungetCursor++] = c;
            c = '.';
          }
          // fall through

        default: {
          // recognize a JsDoc string but discard last . if it is followed by
          // a non-JsDoc comment char, e.g. Array.<
          int c1 = c;
          addToString(c);
          int c2 = getChar();
          if (!isJSDocString(c2)) {
            ungetChar(c2);
            this.string = getStringFromBuffer();
            stringBufferTop = 0;
            return JsDocToken.STRING;
          } else {
            do {
              c1 = c2;
              c2 = getChar();
              if (c1 == '.' && c2 == '<') {
                ungetChar(c2);
                ungetChar(c1);
                this.string = getStringFromBuffer();
                stringBufferTop = 0;
                return JsDocToken.STRING;
              } else {
                if (isJSDocString(c2)) {
                  addToString(c1);
                } else {
                  ungetChar(c2);
                  addToString(c1);
                  this.string = getStringFromBuffer();
                  stringBufferTop = 0;
                  return JsDocToken.STRING;
                }
              }
            } while (true);
          }
        }
      }
    }
  }

  /**
   * Gets the remaining JSDoc line without the {@link JsDocToken#EOL},
   * {@link JsDocToken#EOF} or {@link JsDocToken#EOC}.
   */
  @SuppressWarnings("fallthrough")
  String getRemainingJSDocLine() {
    int c;
    for (;;) {
      c = getChar();
      switch (c) {
        case '*':
          if (peekChar() != '/') {
            addToString(c);
            break;
          }
          // fall through
        case EOF_CHAR:
        case '\n':
          ungetChar(c);
          this.string = getStringFromBuffer();
          stringBufferTop = 0;
          return this.string;

        default:
          addToString(c);
          break;
      }
    }
  }

  final int getLineno() { return lineno; }

  final int getCharno() {
    return lineno == initLineno? initCharno + charno : charno;
  }

  final String getString() { return string; }

  final boolean eof() { return hitEOF; }

  private String getStringFromBuffer() {
    tokenEnd = cursor;
    return new String(stringBuffer, 0, stringBufferTop);
  }

  private void addToString(int c) {
    int N = stringBufferTop;
    if (N == stringBuffer.length) {
        char[] tmp = new char[stringBuffer.length * 2];
        System.arraycopy(stringBuffer, 0, tmp, 0, N);
        stringBuffer = tmp;
    }
    stringBuffer[N] = (char)c;
    stringBufferTop = N + 1;
  }

  void ungetChar(int c) {
    // can not unread past across line boundary
    assert(!(ungetCursor != 0 && ungetBuffer[ungetCursor - 1] == '\n'));
    ungetBuffer[ungetCursor++] = c;
    cursor--;
  }

  private boolean matchChar(int test) {
    int c = getCharIgnoreLineEnd();
    if (c == test) {
      tokenEnd = cursor;
      return true;
    } else {
      ungetCharIgnoreLineEnd(c);
      return false;
    }
  }

  private static boolean isAlpha(int c) {
    // Use 'Z' < 'a'
    if (c <= 'Z') {
      return 'A' <= c;
    } else {
      return 'a' <= c && c <= 'z';
    }
  }

  private boolean isJSDocString(int c) {
    switch (c) {
      case '@':
      case '*':
      case ',':
      case '>':
      case ':':
      case '(':
      case ')':
      case '{':
      case '}':
      case '[':
      case ']':
      case '?':
      case '!':
      case '|':
      case '=':
      case EOF_CHAR:
      case '\n':
        return false;

      default:
        return !isJSSpace(c);
    }
  }

  /* As defined in ECMA.  jsscan.c uses C isspace() (which allows
   * \v, I think.)  note that code in getChar() implicitly accepts
   * '\r' == \u000D as well.
   */
  static boolean isJSSpace(int c) {
    if (c <= 127) {
      return c == 0x20 || c == 0x9 || c == 0xC || c == 0xB;
    } else {
      return c == 0xA0
          || Character.getType((char)c) == Character.SPACE_SEPARATOR;
    }
  }

  private static boolean isJSFormatChar(int c) {
    return c > 127 && Character.getType((char)c) == Character.FORMAT;
  }

  /**
   * Allows the JSDocParser to update the character offset
   * so that getCharno() returns a valid character position.
   */
  void update() {
    charno = getOffset();
  }

  private int peekChar() {
    int c = getChar();
    ungetChar(c);
    return c;
  }

  protected int getChar() {
    if (ungetCursor != 0) {
      cursor++;
      --ungetCursor;
      if (charno == -1) {
        charno = getOffset();
      }
      return ungetBuffer[ungetCursor];
    }

    for(;;) {
      int c;
      if (sourceCursor == sourceEnd) {
        hitEOF = true;
        if (charno == -1) {
          charno = getOffset();
        }
        return EOF_CHAR;
      }
      cursor++;
      c = sourceString.charAt(sourceCursor++);


      if (lineEndChar >= 0) {
        if (lineEndChar == '\r' && c == '\n') {
          lineEndChar = '\n';
          continue;
        }
        lineEndChar = -1;
        lineStart = sourceCursor - 1;
        lineno++;
      }

      if (c <= 127) {
        if (c == '\n' || c == '\r') {
          lineEndChar = c;
          c = '\n';
        }
      } else {
        if (isJSFormatChar(c)) {
          continue;
        }
        if (ScriptRuntime.isJSLineTerminator(c)) {
          lineEndChar = c;
          c = '\n';
        }
      }

      if (charno == -1) {
        charno = getOffset();
      }

      return c;
    }
  }

  private int getCharIgnoreLineEnd() {
    if (ungetCursor != 0) {
      cursor++;
      --ungetCursor;
      if (charno == -1) {
        charno = getOffset();
      }
      return ungetBuffer[ungetCursor];
    }

    for(;;) {
      int c;
      if (sourceCursor == sourceEnd) {
        hitEOF = true;
        if (charno == -1) {
          charno = getOffset();
        }
        return EOF_CHAR;
      }
      cursor++;
      c = sourceString.charAt(sourceCursor++);


      if (c <= 127) {
        if (c == '\n' || c == '\r') {
          lineEndChar = c;
          c = '\n';
        }
      } else {
        if (isJSFormatChar(c)) {
          continue;
        }
        if (ScriptRuntime.isJSLineTerminator(c)) {
          lineEndChar = c;
          c = '\n';
        }
      }

      if (charno == -1) {
        charno = getOffset();
      }

      return c;
    }
  }

  private void ungetCharIgnoreLineEnd(int c) {
    ungetBuffer[ungetCursor++] = c;
    cursor--;
  }

  /**
   * Returns the offset into the current line.
   */
  final int getOffset() {
    return sourceCursor - lineStart - ungetCursor - 1;
  }

  // Set this to an initial non-null value so that the Parser has
  // something to retrieve even if an error has occurred and no
  // string is found.  Fosters one class of error, but saves lots of
  // code.
  private String string = "";

  private char[] stringBuffer = new char[128];
  private int stringBufferTop;

  // Room to backtrace from to < on failed match of the last - in <!--
  private final int[] ungetBuffer = new int[3];
  private int ungetCursor;

  private boolean hitEOF = false;

  private int lineStart = 0;
  private int lineEndChar = -1;
  int lineno;
  private int charno = -1;
  private int initCharno;
  private int initLineno;

  private String sourceString;
  private int sourceEnd;

  // sourceCursor is an index into a small buffer that keeps a
  // sliding window of the source stream.
  int sourceCursor;

  // cursor is a monotonically increasing index into the original
  // source stream, tracking exactly how far scanning has progressed.
  // Its value is the index of the next character to be scanned.
  int cursor;

  // Record start and end positions of last scanned token.
  int tokenBeg;
  int tokenEnd;
}
