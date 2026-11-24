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

package com.google.javascript.jscomp.regex;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.min;

import com.google.common.collect.ImmutableList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** An AST for JavaScript regular expressions. */
public sealed interface RegExpTree {

  /** The children of this node. */
  default ImmutableList<? extends RegExpTree> children() {
    return ImmutableList.of();
  }

  /**
   * Parses a regular expression to an AST.
   *
   * @param pattern The {@code foo} From {@code /foo/i}.
   * @param flags The {@code i} From {@code /foo/i}.
   */
  static RegExpTree parseRegExp(final String pattern, final String flags) {
    enum ParentheticalType {
      CAPTURING,
      NONCAPTURING,
      POSITIVE_LOOKAHEAD,
      NEGATIVE_LOOKAHEAD,
      POSITIVE_LOOKBEHIND,
      NEGATIVE_LOOKBEHIND,
      NAMED_GROUPS,
    }

    // A recursive descent parser that closes over pattern and flags above.
    class Parser {
      // The number of characters in pattern consumed.
      int pos;

      // The number of capturing groups seen so far.
      int numCapturingGroups;

      // The names of capturing groups in the regex expression.
      final Set<String> capturingGroupNames = new LinkedHashSet<>();

      // The length of pattern.
      final int limit = pattern.length();

      // Boolean indicating whether we should look for named capture group backreferences.
      boolean lookForNamedCaptureBackreferences;

      RegExpTree parseTopLevel() {
        // First assume there are no named capture backreferences,
        // because the spec says they should only be recognized
        // if the pattern contains at least one named capture group.
        this.pos = 0;
        this.numCapturingGroups = 0;
        this.lookForNamedCaptureBackreferences = false;
        RegExpTree out = parse();

        // If there were named capture groups, we must parse the pattern string again
        // so we can check for any backreferences to them.
        if (!capturingGroupNames.isEmpty()) {
          this.pos = 0;
          this.numCapturingGroups = 0;
          this.lookForNamedCaptureBackreferences = true;
          out = parse();
        }

        if (pos < limit) { // Unmatched closed paren maybe.
          throw new IllegalArgumentException(pattern.substring(pos));
        }
        return out;
      }

      RegExpTree parse() {
        // Collects ["foo", "bar", "baz"] for /foo|bar|baz/.
        ImmutableList.Builder<RegExpTree> alternatives = null;
        // The last item parsed within an alternation.
        RegExpTree preceder = null;

        topLoop:
        while (pos < limit) {
          char ch = pattern.charAt(pos);
          RegExpTree atom;
          switch (ch) {
            case '[' -> atom = parseCharset();
            case '(' -> atom = parseParenthetical();
            case ')' -> {
              break topLoop;
            }
            case '\\' -> atom = parseEscape();
            case '^', '$' -> {
              atom = new Anchor(ch);
              ++pos;
            }
            case '.' -> {
              atom = Charset.DOT;
              ++pos;
            }
            case '|' ->
                // An alternative may be empty as in /foo||bar/.
                // The '|' is consumed below.
                atom = Empty.INSTANCE;
            default -> {
              // Find a run of concatenated characters to avoid building a
              // tree node per literal character.
              int start = pos;
              int end = pos + 1;
              charsLoop:
              while (end < limit) {
                switch (pattern.charAt(end)) {
                  case '[':
                  case '(':
                  case ')':
                  case '\\':
                  case '^':
                  case '$':
                  case '|':
                  case '.':
                  case '*':
                  case '+':
                  case '?':
                  case '{':
                    break charsLoop;
                  default:
                    // Repetition binds more tightly than concatenation.
                    // Only consume up to "foo" in /foob*/ so that the suffix
                    // operator parser below has the right precedence.
                    if (end + 1 >= limit || !isRepetitionStart(pattern.charAt(end + 1))) {
                      ++end;
                    } else {
                      break charsLoop;
                    }
                }
              }
              atom = new Text(pattern.substring(start, end));
              pos = end;
            }
          }
          if (pos < limit && isRepetitionStart(pattern.charAt(pos))) {
            atom = parseRepetition(atom);
          }
          if (preceder == null) {
            preceder = atom;
          } else {
            preceder = new Concatenation(preceder, atom);
          }
          // If this is an alternative in an alternation, then add it to the
          // list of complete alternatives, and reset the parser state for the
          // next alternative.
          if (pos < limit && pattern.charAt(pos) == '|') {
            if (alternatives == null) {
              alternatives = ImmutableList.builder();
            }
            alternatives.add(preceder);
            preceder = null;
            ++pos;
          }
        }
        // An alternative may have no parsed content blank as in /foo|/.
        if (preceder == null) {
          preceder = Empty.INSTANCE;
        }
        if (alternatives != null) {
          alternatives.add(preceder);
          return new Alternation(alternatives.build());
        } else {
          return preceder;
        }
      }

      // Handles capturing groups (...), non-capturing groups (?:...), lookahead assertions
      // (?=...) / (?!...), lookbehind assertions (?<=...) / (?<!...), and named groups (?<name>).
      private RegExpTree parseParenthetical() {
        checkState(pattern.charAt(pos) == '(');
        int start = pos;
        ++pos;
        ParentheticalType type;
        String captureName = null;
        if (pos < limit && pattern.charAt(pos) == '?') {
          if (pos + 1 < limit) {
            char ch = pattern.charAt(pos + 1);
            switch (ch) {
              case ':' -> {
                // (?:...) Non-capturing groups.
                pos += 2;
                type = ParentheticalType.NONCAPTURING;
              }
              // (?=...) and (?!...) Lookahead Assertions
              case '=' -> {
                pos += 2;
                type = ParentheticalType.POSITIVE_LOOKAHEAD;
              }
              case '!' -> {
                pos += 2;
                type = ParentheticalType.NEGATIVE_LOOKAHEAD;
              }
              // (?<=...) and (?<!...) Lookbehind Assertions, (?<name>) named groups
              case '<' -> {
                if (pos + 2 < limit && pattern.charAt(pos + 2) == '=') {
                  pos += 3;
                  type = ParentheticalType.POSITIVE_LOOKBEHIND;
                } else if (pos + 2 < limit && pattern.charAt(pos + 2) == '!') {
                  pos += 3;
                  type = ParentheticalType.NEGATIVE_LOOKBEHIND;
                } else {
                  pos += 2;
                  captureName = scanNamedGroupName();
                  capturingGroupNames.add(captureName);
                  type = ParentheticalType.NAMED_GROUPS;
                }
              }
              default ->
                  throw new IllegalArgumentException(
                      "Malformed parenthetical: " + pattern.substring(start));
            }
          } else {
            throw new IllegalArgumentException(
                "Malformed parenthetical: " + pattern.substring(start));
          }
        } else {
          type = ParentheticalType.CAPTURING;
        }
        RegExpTree body = parse();
        if (pos < limit && pattern.charAt(pos) == ')') {
          ++pos;
        } else {
          throw new IllegalArgumentException(
              "Unclosed parenthetical group: " + pattern.substring(start));
        }
        switch (type) {
          case CAPTURING -> {
            ++numCapturingGroups;
            return new CapturingGroup(body);
          }
          case NONCAPTURING -> {
            return body;
          }
          case POSITIVE_LOOKAHEAD -> {
            return new LookaheadAssertion(body, true);
          }
          case NEGATIVE_LOOKAHEAD -> {
            return new LookaheadAssertion(body, false);
          }
          case POSITIVE_LOOKBEHIND -> {
            return new LookbehindAssertion(body, true);
          }
          case NEGATIVE_LOOKBEHIND -> {
            return new LookbehindAssertion(body, false);
          }
          case NAMED_GROUPS -> {
            if (captureName != null) {
              ++numCapturingGroups;
              return new NamedCaptureGroup(body, captureName);
            } else {
              throw new IllegalArgumentException(
                  "Malformed named capture group: " + pattern.substring(start));
            }
          }
        }
        throw new AssertionError("Unrecognized ParentheticalType " + type);
      }

      // Scans the pattern for a named group name. Assumes pos points to the character after '<'.
      private String scanNamedGroupName() {
        int start = pos;
        int end = pos;
        if (!isIdentifierStart(pattern.charAt(start))) {
          throw new IllegalArgumentException(
              "Invalid capture group name: <" + pattern.substring(start));
        }
        ++end;
        while (end < limit) {
          if (pattern.charAt(end) == '>') {
            pos = end + 1;
            return pattern.substring(start, end);
          } else if (isIdentifierPart(pattern.charAt(end))) {
            ++end;
          } else {
            throw new IllegalArgumentException(
                "Invalid capture group name: <" + pattern.substring(start));
          }
        }
        throw new IllegalArgumentException(
            "Malformed named capture group: <" + pattern.substring(start));
      }

      // Parses a square bracketed character set. Standalone character groups /\d/ are handled by
      // parseEscape.
      private RegExpTree parseCharset() {
        checkState(pattern.charAt(pos) == '[');
        if (flags.contains("v")) {
          return parseVModeCharset();
        }
        int startPos = pos;
        ++pos;

        if (pos < limit && pattern.charAt(pos) == '^') {
          ++pos;
        }
        while (pos < limit && pattern.charAt(pos) != ']') {
          char ch = pattern.charAt(pos);
          int start;
          if (ch == '\\') {
            ++pos;
            char possibleGroupName = pattern.charAt(pos);
            if (isNamedCharGroup(possibleGroupName)) {
              ++pos;
              continue;
            }
            start = parseEscapeChar();
          } else {
            start = ch;
            ++pos;
          }
          if (pos + 1 < limit && pattern.charAt(pos) == '-' && pattern.charAt(pos + 1) != ']') {
            ++pos;
            ch = pattern.charAt(pos);
            int end;
            if (ch == '\\') {
              ++pos;
              end = parseEscapeChar();
            } else {
              end = ch;
              ++pos;
            }
            if (start > end) {
              throw new IndexOutOfBoundsException(start + " > " + end);
            }
          }
        }
        ++pos; // Consume ']'
        return new Charset(pattern.substring(startPos, min(pos, limit)));
      }

      // Scans a square-bracketed ClassSetExpression in ES2024 v (unicodeSets) mode by tracking
      // nested [...] bracket depth. Because unescaped '[' and ']' are syntax characters everywhere
      // inside a valid v-mode character class (including inside \q{...} and \p{...}), balanced
      // bracket scanning consumes the entire class expression without needing to model set
      // operators or string disjunctions.
      private RegExpTree parseVModeCharset() {
        int start = pos;
        int depth = 0;
        while (pos < limit) {
          char ch = pattern.charAt(pos++);
          if (ch == '\\') {
            if (pos >= limit) {
              throw new IllegalArgumentException("Trailing '\\' in character class");
            }
            ++pos;
          } else if (ch == '[') {
            ++depth;
          } else if (ch == ']' && --depth == 0) {
            return new Charset(pattern.substring(start, pos));
          }
        }
        throw new IllegalArgumentException("Unclosed character class");
      }

      // Parses an escape to a code point. Some of the characters parsed here have special meanings
      // in various contexts, so contexts must filter those instead. E.g. '\b' means a different
      // thing inside a charset than without.
      private int parseEscapeChar() {
        char ch = pattern.charAt(pos++);
        return switch (ch) {
          case 'b' -> '\b';
          case 'f' -> '\f';
          case 'n' -> '\n';
          case 'r' -> '\r';
          case 't' -> '\t';
          case 'u' ->
              ((flags.contains("u") || flags.contains("v"))
                      && pos < limit
                      && pattern.charAt(pos) == '{')
                  ? parseBracedUnicodeEscape()
                  : parseHex(4);
          case 'v' -> '\u000b';
          case 'x' -> parseHex(2);
          default -> {
            if ('0' <= ch && ch <= '7') {
              char codeUnit = (char) (ch - '0');
              // Allow octal literals in the range \0-\377.
              // \41 might be a group, but \041 is not a group.
              int octLimit = min(limit, pos + (ch <= '3' ? 2 : 1) + (ch == '0' ? 1 : 0));
              while (pos < octLimit) {
                ch = pattern.charAt(pos);
                if ('0' <= ch && ch <= '7') {
                  codeUnit = (char) ((codeUnit << 3) + (ch - '0'));
                  ++pos;
                } else {
                  break;
                }
              }
              yield codeUnit;
            }
            yield ch;
          }
        };
      }

      // Parses an escape that appears outside a charset.
      private RegExpTree parseEscape() {
        checkState(pattern.charAt(pos) == '\\');
        int start = pos;
        ++pos;
        char ch = pattern.charAt(pos);
        if (ch == 'b' || ch == 'B') {
          ++pos;
          return new WordBoundary(ch);
        } else if ((ch == 'p' || ch == 'P') && (flags.contains("u") || flags.contains("v"))) {
          // handle ES2018 unicode property tests, e.g.
          // /\p{ASCII_Hex_Digit=true}/ only hex digits
          // /\P{Script=Greek}/  no greek letters
          boolean negated = ch == 'P';
          ++pos;
          if (pos < limit && pattern.charAt(pos) == '{') {
            StringBuilder lhs = new StringBuilder();
            while (++pos < limit
                && ((ch = pattern.charAt(pos)) == '_'
                    || ('a' <= ch && ch <= 'z')
                    || ('A' <= ch && ch <= 'Z')
                    || ('0' <= ch && ch <= '9'))) {
              lhs.append(ch);
            }
            if (pos < limit && ch == '}') {
              // Case of shorthand like /\p{ASCII_Hex_Digit}/u
              ++pos;
              return new UnicodePropertyEscape(null, lhs.toString(), negated);
            } else if (pos < limit && ch == '=') {
              // Case of having '=' like /\p{Script=Greek}/u
              StringBuilder rhs = new StringBuilder();
              while (++pos < limit
                  && ((ch = pattern.charAt(pos)) == '_'
                      || ('a' <= ch && ch <= 'z')
                      || ('A' <= ch && ch <= 'Z')
                      || ('0' <= ch && ch <= '9'))) {
                rhs.append(ch);
              }
              if (pos < limit && ch == '}') {
                ++pos;
                return new UnicodePropertyEscape(lhs.toString(), rhs.toString(), negated);
              } else {
                throw new IllegalArgumentException(
                    "Malformed Unicode Property Escape: expected '}' after "
                        + pattern.substring(start, pos));
              }
            } else {
              throw new IllegalArgumentException(
                  "Malformed Unicode Property Escape: expected '=' or '}' after "
                      + pattern.substring(start, pos));
            }
          } else {
            throw new IllegalArgumentException(
                "Malformed Unicode Property Escape: expected '{' after "
                    + pattern.substring(start, pos));
          }
        } else if ('1' <= ch && ch <= '9') {
          ++pos;
          int possibleGroupIndex = ch - '0';
          if (numCapturingGroups >= possibleGroupIndex) {
            if (pos < limit) {
              char next = pattern.charAt(pos);
              if ('0' <= next && next <= '9') {
                int twoDigitGroupIndex = possibleGroupIndex * 10 + (next - '0');
                if (numCapturingGroups >= twoDigitGroupIndex) {
                  ++pos;
                  possibleGroupIndex = twoDigitGroupIndex;
                }
              }
            }
            return new BackReference(possibleGroupIndex);
          } else {
            // \1 - \7 are octal escapes if there is no such group.
            // \8 and \9 are the literal characters '8' and '9' if there
            // is no such group.
            return new Text(
                Character.toString(possibleGroupIndex <= 7 ? (char) possibleGroupIndex : ch));
          }
        } else if (lookForNamedCaptureBackreferences
            && ch == 'k'
            && pos + 1 < limit
            && pattern.charAt(pos + 1) == '<'
            // According to the spec
            // https://github.com/tc39/proposal-regexp-named-groups#backwards-compatibility-of-new-syntax
            // we want to treat \k as a normal string if there are no named capturing groups present
            && !capturingGroupNames.isEmpty()) {
          pos += 2;
          String potentialName = scanNamedGroupName();
          if (!capturingGroupNames.contains(potentialName)) {
            throw new IllegalArgumentException(
                "Invalid named capture referenced: " + pattern.substring(start));
          }
          return new NamedBackReference(potentialName);
        } else {
          if (isNamedCharGroup(ch)) { // Handle \d, etc.
            ++pos;
            return new Charset("\\" + ch);
          }
          return new Text(new String(Character.toChars(parseEscapeChar())));
        }
      }

      // Parses n hex digits to a code-unit.
      private int parseHex(int n) {
        if (pos + n > limit) {
          throw new IllegalArgumentException("Abbreviated hex escape " + pattern.substring(pos));
        }
        if (n > 7) {
          // We need to guard the MSB to prevent overflow.
          throw new IllegalArgumentException(
              "Cannot parse hexadecimal encoding wider than 28 bits: "
                  + pattern.substring(pos, pos + n));
        }

        int result = 0;
        while (--n >= 0) {
          char ch = pattern.charAt(pos);
          int digit;
          if ('0' <= ch && ch <= '9') {
            digit = ch - '0';
          } else if ('a' <= ch && ch <= 'f') {
            digit = ch + (10 - 'a');
          } else if ('A' <= ch && ch <= 'F') {
            digit = ch + (10 - 'A');
          } else {
            throw new IllegalArgumentException(pattern.substring(pos));
          }
          ++pos;
          result = (result << 4) | digit;
        }
        return result;
      }

      private int parseBracedUnicodeEscape() {
        int openBrace = pos;
        checkState(pattern.charAt(pos++) == '{');

        int closeBrace = pos;
        while (closeBrace < limit && pattern.charAt(closeBrace) != '}') {
          closeBrace++;
        }
        if (closeBrace == limit) {
          throw new IllegalArgumentException(
              "Malformed unicode escape: expected '}' after " + pattern.substring(openBrace));
        } else if (closeBrace == pos) {
          throw new IllegalArgumentException("Empty unicode escape");
        }

        int result = parseHex(closeBrace - pos);
        if (result > 0x10FFFF) {
          throw new IllegalArgumentException(
              "Unicode must be at most 0x10FFFF: " + pattern.substring(openBrace + 1, pos));
        }
        pos++; // Consume the close brace.
        return result;
      }

      private boolean isRepetitionStart(char ch) {
        return switch (ch) {
          case '?', '*', '+', '{' -> true;
          default -> false;
        };
      }

      // Parses a repetition. x? is treated as a repetition -- an optional production can be
      // matched 0 or 1 time.
      private RegExpTree parseRepetition(RegExpTree body) {
        if (pos == limit) {
          return body;
        }
        int min;
        int max;
        switch (pattern.charAt(pos)) {
          case '+' -> {
            ++pos;
            min = 1;
            max = Integer.MAX_VALUE;
          }
          case '*' -> {
            ++pos;
            min = 0;
            max = Integer.MAX_VALUE;
          }
          case '?' -> {
            ++pos;
            min = 0;
            max = 1;
          }
          case '{' -> {
            ++pos;
            int start = pos;
            int end = pattern.indexOf('}', start);
            if (end < 0) {
              pos = start - 1;
              return body;
            }
            String counts = pattern.substring(start, end);
            pos = end + 1;
            int comma = counts.indexOf(',');
            try {
              min = Integer.parseInt(comma >= 0 ? counts.substring(0, comma) : counts);
              max =
                  comma >= 0
                      ? comma + 1 != counts.length()
                          ? Integer.parseInt(counts.substring(comma + 1))
                          : Integer.MAX_VALUE
                      : min;
            } catch (NumberFormatException ex) {
              min = max = -1;
            }
            if (min < 0 || min > max) {
              // Treat the open curly bracket literally.
              pos = start - 1;
              return body;
            }
          }
          default -> {
            return body;
          }
        }
        boolean greedy = true;
        if (pos < limit && pattern.charAt(pos) == '?') {
          greedy = false;
          ++pos;
        }
        return new Repetition(body, min, max, greedy);
      }
    }

    return new Parser().parseTopLevel();
  }

  private static boolean isNamedCharGroup(char ch) {
    return switch (ch) {
      case 'd', 'D', 's', 'S', 'w', 'W' -> true;
      default -> false;
    };
  }

  /**
   * @param ch the character
   * @return true if the character is a valid Javascript identifier start character.
   */
  private static boolean isIdentifierStart(char ch) {
    // TODO(yitingwang) This and the one in Scanner.java should share the same implementation
    // Most code is written in pure ASCII create a fast path here.
    if (ch <= 127) {
      return ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch == '_' || ch == '$'));
    }

    return Character.isLetter(ch);
  }

  /**
   * @param ch the character
   * @return true if the character is allowed in Javascript identifiers.
   */
  private static boolean isIdentifierPart(char ch) {
    // TODO(yitingwang) This and the one in Scanner.java should share the same implementation
    // Most code is written in pure ASCII create a fast path here.
    if (ch <= 127) {
      return ((ch >= 'A' && ch <= 'Z')
          || (ch >= 'a' && ch <= 'z')
          || (ch >= '0' && ch <= '9')
          || (ch == '_' || ch == '$')); // _ or $
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

  /** Represents an empty portion of a RegExp such as the middle of "||" */
  record Empty() implements RegExpTree {
    static final Empty INSTANCE = new Empty();
  }

  /** Represents an anchor, namely ^ or $. */
  record Anchor(char type) implements RegExpTree {}

  /** Represents \b or \B */
  record WordBoundary(char type) implements RegExpTree {}

  /** Represents a reference to a previous group such as \1 or \2 */
  record BackReference(int groupIndex) implements RegExpTree {
    public BackReference {
      checkArgument(groupIndex >= 0 && groupIndex <= 99);
    }
  }

  /** Represents a reference to a previous named group */
  record NamedBackReference(String groupName) implements RegExpTree {}

  /** Represents a run of non-special characters such as "foobar" */
  record Text(String text) implements RegExpTree {}

  /** Represents a repeating item such as ...+, ...*, or ...{0,1} */
  record Repetition(RegExpTree body, int min, int max, boolean greedy) implements RegExpTree {
    @Override
    public ImmutableList<? extends RegExpTree> children() {
      return ImmutableList.of(body);
    }
  }

  /** Represents the possibilities ["foo", "bar"] for a RegExp /foo|bar/ */
  record Alternation(ImmutableList<RegExpTree> alternatives) implements RegExpTree {
    @Override
    public ImmutableList<? extends RegExpTree> children() {
      return alternatives;
    }
  }

  /** Represents a lookahead assertion such as (?=...) or (?!...) */
  record LookaheadAssertion(RegExpTree body, boolean positive) implements RegExpTree {
    @Override
    public ImmutableList<? extends RegExpTree> children() {
      return ImmutableList.of(body);
    }
  }

  /** Represents a lookbehind assertion such as {@code (?<=...)} or {@code (?<!...)} */
  record LookbehindAssertion(RegExpTree body, boolean positive) implements RegExpTree {
    @Override
    public ImmutableList<? extends RegExpTree> children() {
      return ImmutableList.of(body);
    }
  }

  /** Represents a capturing group such as (asdf) */
  record CapturingGroup(RegExpTree body) implements RegExpTree {
    @Override
    public ImmutableList<? extends RegExpTree> children() {
      return ImmutableList.of(body);
    }
  }

  /** Represents a named capture group */
  record NamedCaptureGroup(RegExpTree body, String name) implements RegExpTree {
    @Override
    public ImmutableList<? extends RegExpTree> children() {
      return ImmutableList.of(body);
    }
  }

  /** Represents a Unicode Property Escape such as in /\p{Script=Greek}/u */
  record UnicodePropertyEscape(@Nullable String propertyName, String propertyValue, boolean negated)
      implements RegExpTree {
    public UnicodePropertyEscape {
      checkState(propertyValue != null);
      checkArgument(
          propertyName == null || !propertyName.isEmpty(),
          "if '=' is present in a unicode property escape, the name cannot be empty");
      checkArgument(!propertyValue.isEmpty(), "unicode property escape value cannot be empty");
    }
  }

  /** Represents a character set such as [a-zA-Z], [^a-zA-Z], \d, or . */
  record Charset(String source) implements RegExpTree {
    static final Charset DOT = new Charset(".");

    public Charset {
      checkNotNull(source);
    }
  }

  /** Represents a series of nodes chained one after another such as (?:...)[a-z]*(...) */
  record Concatenation(ImmutableList<RegExpTree> elements) implements RegExpTree {
    Concatenation(RegExpTree a, RegExpTree b) {
      this(ImmutableList.of(a, b));
    }

    @Override
    public ImmutableList<? extends RegExpTree> children() {
      return elements;
    }
  }
}
