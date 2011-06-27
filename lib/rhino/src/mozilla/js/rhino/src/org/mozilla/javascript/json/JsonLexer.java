/* -*- Mode: java; tab-width: 4; indent-tabs-mode: 1; c-basic-offset: 4 -*-
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Norris Boyd
 *   Raphael Speyer
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.mozilla.javascript.json;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.mozilla.javascript.Kit;

/**
 * This class converts a string into a stream of JSON tokens.
 *
 * See ECMA 15.12.
 */
public class JsonLexer {
  private Token currentToken;
  private int offset;
  private int beginLexeme, endLexeme;

  private char[] input;
  private CharSequence seq;

  public static enum Token {
    NULL("null"),
    BOOLEAN("true|false") {
      @SuppressWarnings("unchecked")
      @Override
      public Boolean evaluate(String lexeme) {
        return Boolean.valueOf(lexeme);
      }
    },
    NUMBER("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][-+]?\\d+)?") {
      @SuppressWarnings("unchecked")
      @Override
      public Number evaluate(String lexeme) {
        return toNumber(lexeme);
      }
    },
    STRING("\"([^\\\\\"\\x00-\\x1F]|\\\\([\"/\\\\bfnrt]|u[a-fA-F0-9]{4}))*\"") {
      @SuppressWarnings("unchecked")
      @Override
      public String evaluate(String lexeme) {
        return unescape(lexeme);
      }
    },
    OPEN_BRACKET("\\["),
    CLOSE_BRACKET("]"),
    OPEN_BRACE("\\{"),
    CLOSE_BRACE("}"),
    COMMA(","),
    COLON(":");

    private Pattern pattern;

    private Token(String patternString) {
      this.pattern = Pattern.compile(patternString);
    }

    public final int eat(CharSequence rest) {
      Matcher matcher = pattern.matcher(rest);
      return matcher.lookingAt() ? matcher.end() : 0;
    }

    /**
     * Override to return the Java value for lexemes of this token
     */
    public <T> T evaluate(String lexeme) {
      throw Kit.codeBug(toString()+" tokens to not represent a complete JSON value");
    }

  }

  public static final Token[] VALUE_START_TOKENS = new Token[] { Token.NULL,
    Token.BOOLEAN, Token.NUMBER, Token.STRING, Token.OPEN_BRACKET,
    Token.OPEN_BRACE
  };

  public JsonLexer(String input) {
    reset(input);
  }

  public void reset(String input) {
    this.seq = input;
    this.input = input.toCharArray();
    this.beginLexeme = 0;
    this.endLexeme = 0;
    this.offset = 0;
    this.currentToken = null;
  }

  public boolean moveNext() {
    eatWhitespace();

    CharSequence rest = seq.subSequence(offset, seq.length());

    return
      eatToken(Token.OPEN_BRACE, rest) ||
      eatToken(Token.CLOSE_BRACE, rest) ||
      eatToken(Token.OPEN_BRACKET, rest) ||
      eatToken(Token.CLOSE_BRACKET, rest) ||
      eatToken(Token.COMMA, rest) ||
      eatToken(Token.COLON, rest) ||
      eatToken(Token.NULL, rest) ||
      eatToken(Token.BOOLEAN, rest) ||
      eatToken(Token.NUMBER, rest) ||
      eatToken(Token.STRING, rest);
  }

  public String getLexeme() {
    return new String(input, beginLexeme, endLexeme - beginLexeme).trim();
  }

  public Token getToken() {
    return currentToken;
  }

  public long getOffset() {
    return offset;
  }

  private static Number toNumber(String lexeme) {
    try {
      return Integer.parseInt(lexeme);
    } catch (NumberFormatException exi) {
      try {
        return Long.parseLong(lexeme);
      } catch (NumberFormatException exl) {
        BigDecimal decimal = new BigDecimal(lexeme);
        double doubleValue = decimal.doubleValue();
        if (doubleValue == Double.POSITIVE_INFINITY
            || doubleValue == Double.NEGATIVE_INFINITY
            || new BigDecimal(doubleValue).compareTo(decimal) != 0) {
          return decimal;
        } else {
          return doubleValue;
        }
      }
    }
  }

  private static String unescape(String lexeme) {
    char[] escaped = lexeme.toCharArray();

    int start = 0;
    int end = escaped.length - 1;
    // Skip leading and trailing whitespace
    while (escaped[start] != '"') {
      ++start;
    }
    while (escaped[end] != '"') {
      --end;
    }
    // Skip the leading quote
    ++start;

    StringBuffer buffer = new StringBuffer(end - start);
    boolean escaping = false;

    for (int i = start; i < end; i += 1) {
      char c = escaped[i];

      if (escaping) {
        switch (c) {
        case '"':
          buffer.append('"');
          break;
        case '\\':
          buffer.append('\\');
          break;
        case '/':
          buffer.append('/');
          break;
        case 'b':
          buffer.append('\b');
          break;
        case 'f':
          buffer.append('\f');
          break;
        case 'n':
          buffer.append('\n');
          break;
        case 'r':
          buffer.append('\r');
          break;
        case 't':
          buffer.append('\t');
          break;
        case 'u':
          // interpret the following 4 characters as the hex of the unicode code point
          int codePoint = Integer.parseInt(new String(escaped, i + 1, 4), 16);
          buffer.appendCodePoint(codePoint);
          i += 4;
          break;
        default:
          throw new IllegalArgumentException("Illegal escape sequence: '\\" + c + "'");
        }
        escaping = false;
      } else {
        if (c == '\\') {
          escaping = true;
        } else {
          buffer.append(c);
        }
      }
    }

    return buffer.toString();
  }

  private boolean eatToken(Token token, CharSequence rest) {
    beginLexeme();

    int eaten = token.eat(rest);

    if (eaten == 0) return false;

    offset += eaten;
    endLexeme(token);
    return true;
  }

  private void eatWhitespace() {
    while (!finished()) {
      char c = input[offset];
      if (c == ' ' || c == '\t' || c == '\r' || c == '\n')
        offset += 1;
      else
          break;
    }
  }

  private void beginLexeme() {
    this.beginLexeme = this.offset;
    this.endLexeme = this.offset;
  }

  private void endLexeme(Token token) {
    this.endLexeme = offset;
    this.currentToken = token;
  }

  public boolean finished() {
    return offset >= input.length;
  }
}
