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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An AST for JavaScript regular expressions.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public abstract class RegExpTree {

  /**
   * Returns a simpler regular expression that is semantically the same assuming
   * the given flags.
   * @param flags Regular expression flags, e.g. {@code "igm"}.
   */
  public abstract RegExpTree simplify(String flags);

  /**
   * True if the presence or absence of an {@code "i"} flag would change the
   * meaning of this regular expression.
   */
  public abstract boolean isCaseSensitive();

  /**
   * True if the regular expression contains an anchor : {@code ^} or {@code $}.
   */
  public abstract boolean containsAnchor();

  /**
   * True if the regular expression contains capturing groups.
   */
  public final boolean hasCapturingGroup() {
    return numCapturingGroups() != 0;
  }

  /**
   * The number of capturing groups.
   */
  public abstract int numCapturingGroups();

  /**
   * The children of this node.
   */
  public abstract List<? extends RegExpTree> children();

  /**
   * Appends this regular expression source to the given buffer.
   */
  protected abstract void appendSourceCode(StringBuilder sb);

  protected abstract void appendDebugInfo(StringBuilder sb);

  @Override
  public final String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('/');
    appendSourceCode(sb);
    // Don't emit a regular expression that looks like a line comment start.
    if (sb.length() == 1) {
      sb.append("(?:)");
    }
    sb.append('/');
    return sb.toString();
  }

  public final String toDebugString() {
    StringBuilder sb = new StringBuilder();
    appendDebugString(sb);
    return sb.toString();
  }

  private void appendDebugString(StringBuilder sb) {
    sb.append('(').append(getClass().getSimpleName());
    int len = sb.length();
    sb.append(' ');
    appendDebugInfo(sb);
    if (sb.length() == len + 1) { sb.setLength(len); }
    for (RegExpTree child : children()) {
      sb.append(' ');
      child.appendDebugString(sb);
    }
    sb.append(')');
  }

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();


  /**
   * Parses a regular expression to an AST.
   *
   * @param pattern The {@code foo} From {@code /foo/i}.
   * @param flags The {@code i} From {@code /foo/i}.
   */
  public static RegExpTree parseRegExp(
      final String pattern, final String flags) {

    /** A recursive descent parser that closes over pattern and flags above. */
    class Parser {
      /** The number of characters in pattern consumed. */
      int pos;
      /** The number of capturing groups seen so far. */
      int numCapturingGroups = 0;
      /** The length of pattern. */
      final int limit = pattern.length();

      RegExpTree parseTopLevel() {
        this.pos = 0;
        RegExpTree out = parse();
        if (pos < limit) {  // Unmatched closed paren maybe.
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
            case '[':
              atom = parseCharset();
              break;
            case '(':
              atom = parseParenthetical();
              break;
            case ')':
              break topLoop;
            case '\\':
              atom = parseEscape();
              break;
            case '^':
            case '$':
              atom = new Anchor(ch);
              ++pos;
              break;
            case '.':
              // We represent . as a character set to make it easy to simplify
              // things like /.|[\r\n]/.
              atom = DOT_CHARSET;
              ++pos;
              break;
            case '|':
              // An alternative may be empty as in /foo||bar/.
              // The '|' is consumed below.
              atom = Empty.INSTANCE;
              break;
            default:
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
                    if (end + 1 >= limit
                        || !isRepetitionStart(pattern.charAt(end + 1))) {
                      ++end;
                    } else {
                      break charsLoop;
                    }
                }
              }
              atom = new Text(pattern.substring(start, end));
              pos = end;
              break;
          }
          if (pos < limit && isRepetitionStart(pattern.charAt(pos))) {
            atom = parseRepetition(atom);
          }
          if (preceder == null) {
            preceder = atom;
          } else {
            preceder = new Concatenation(preceder, atom);
          }
          // If this is an alternative in a alternation, then add it to the
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
        if (preceder == null) { preceder = Empty.INSTANCE; }
        if (alternatives != null) {
          alternatives.add(preceder);
          return new Alternation(alternatives.build());
        } else {
          return preceder;
        }
      }

      /**
       * Handles capturing groups {@code (...)},
       * non-capturing groups {@code (?:...)}, and lookahead assertions
       * {@code (?=...)}.
       */
      private RegExpTree parseParenthetical() {
        Preconditions.checkState(pattern.charAt(pos) == '(');
        int start = pos;
        ++pos;
        boolean capturing = true;
        int type = 0;
        if (pos < limit && pattern.charAt(pos) == '?') {
          if (pos + 1 < limit) {
            capturing = false;
            char ch = pattern.charAt(pos + 1);
            switch (ch) {
              case ':':  // A (?:...) style non-capturing group.
                pos += 2;
                break;
              case '!':  // A lookahead assertion
              case '=':
                pos += 2;
                type = ch;
                break;
              default:
                throw new IllegalArgumentException(
                    "Malformed parenthetical: " + pattern.substring(start));
            }
          }
        }
        RegExpTree body = parse();
        if (pos < limit && pattern.charAt(pos) == ')') {
          ++pos;
        } else {
          throw new IllegalArgumentException(
              "Unclosed parenthetical group: " + pattern.substring(start));
        }
        if (capturing) {
          ++numCapturingGroups;
          return new CapturingGroup(body);
        } else if (type != 0) {
          return new LookaheadAssertion(body, type == '=');
        } else {
          return body;
        }
      }

      /**
       * Parses a square bracketed character set.
       * Standalone character groups (@code /\d/} are handled by
       * {@link #parseEscape}.
       */
      private RegExpTree parseCharset() {
        Preconditions.checkState(pattern.charAt(pos) == '[');
        ++pos;

        boolean isCaseInsensitive = flags.indexOf('i') >= 0;
        boolean inverse = pos < limit && pattern.charAt(pos) == '^';
        if (inverse) { ++pos; }
        CharRanges ranges = CharRanges.EMPTY;
        CharRanges ieExplicits = CharRanges.EMPTY;
        while (pos < limit && pattern.charAt(pos) != ']') {
          char ch = pattern.charAt(pos);
          char start;
          if (ch == '\\') {
            ++pos;
            char possibleGroupName = pattern.charAt(pos);
            CharRanges group = NAMED_CHAR_GROUPS.get(possibleGroupName);
            if (group != null) {
              ++pos;
              ranges = ranges.union(group);
              continue;
            }
            start = parseEscapeChar();
          } else {
            start = ch;
            ++pos;
          }
          char end = start;
          if (pos + 1 < limit && pattern.charAt(pos) == '-'
              && pattern.charAt(pos + 1) != ']') {
            ++pos;
            ch = pattern.charAt(pos);
            if (ch == '\\') {
              ++pos;
              end = parseEscapeChar();
            } else {
              end = ch;
              ++pos;
            }
          }
          CharRanges range = CharRanges.inclusive(start, end);
          ranges = ranges.union(range);
          if (IE_SPEC_ERRORS.contains(start) && IE_SPEC_ERRORS.contains(end)) {
            ieExplicits = ieExplicits.union(range.intersection(IE_SPEC_ERRORS));
          }
          if (isCaseInsensitive) {
            // If the flags contain the 'i' flag, then it is not correct to
            // say that [^a-z] contains the letter 'A', or that [a-z] does not
            // contain the letter 'A'.
            // We expand out letter groups here so that parse returns something
            // that is valid independent of flags.
            // Calls to simplify(flags) may later reintroduce flag assumptions.
            // but without this step, later steps might conflate
            //     /[a-z]/i
            // and
            //     /[^\0-`{-\uffff]/i
            // which matches nothing because the information about whether the
            // ^ is present has been lost during optimizations and charset
            // unionizing as in /[...]|[^...]/.
            ranges = CaseCanonicalize.expandToAllMatched(ranges);
          }
        }
        ++pos;  // Consume ']'

        if (inverse) {
          ranges = CharRanges.ALL_CODE_UNITS.difference(ranges);
        }

        return new Charset(ranges, ieExplicits);
      }

      /**
       * Parses an escape to a code point.
       * Some of the characters parsed here have special meanings in various
       * contexts, so contexts must filter those instead.
       * E.g. '\b' means a different thing inside a charset than without.
       */
      private char parseEscapeChar() {
        char ch = pattern.charAt(pos++);
        switch (ch) {
          case 'b': return '\b';
          case 'f': return '\f';
          case 'n': return '\n';
          case 'r': return '\r';
          case 't': return '\t';
          case 'u': return parseHex(4);
          case 'v': return '\u000b';
          case 'x': return parseHex(2);
          default:
            if ('0' <= ch && ch <= '7') {
              char codeUnit = (char) (ch - '0');
              // Allow octal literals in the range \0-\377.
              // \41 might be a group, but \041 is not a group.
              // We read, but do not emit octal literals since they
              // are deprecated in ES5.
              int octLimit = Math.min(
                  limit, pos + (ch <= '3' ? 2 : 1) + (ch == '0' ? 1 : 0));
              while (pos < octLimit) {
                ch = pattern.charAt(pos);
                if ('0' <= ch && ch <= '7') {
                  codeUnit = (char) ((codeUnit << 3) + (ch - '0'));
                  ++pos;
                } else {
                  break;
                }
              }
              return codeUnit;
            }
            return ch;
        }
      }

      /**
       * Parses an escape that appears outside a charset.
       */
      private RegExpTree parseEscape() {
        Preconditions.checkState(pattern.charAt(pos) == '\\');
        ++pos;
        char ch = pattern.charAt(pos);
        if (ch == 'b' || ch == 'B') {
          ++pos;
          return new WordBoundary(ch);
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
            return new Text(Character.toString(
              possibleGroupIndex <= 7 ? (char) possibleGroupIndex : ch));
          }
        } else {
          CharRanges charGroup = NAMED_CHAR_GROUPS.get(ch);
          if (charGroup != null) {  // Handle \d, etc.
            ++pos;
            return new Charset(charGroup, CharRanges.EMPTY);
          }
          return new Text("" + parseEscapeChar());
        }
      }

      /**
       * Parses n hex digits to a code-unit.
       */
      private char parseHex(int n) {
        if (pos + n > limit) {
          throw new IllegalArgumentException(
              "Abbreviated hex escape " + pattern.substring(pos));
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
        return (char) result;
      }

      private boolean isRepetitionStart(char ch) {
        switch (ch) {
          case '?':
          case '*':
          case '+':
          case '{':
            return true;
          default:
            return false;
        }
      }

      /**
       * Parse a repetition.  {@code x?} is treated as a repetition --
       * an optional production can be matched 0 or 1 time.
       */
      private RegExpTree parseRepetition(RegExpTree body) {
        if (pos == limit) { return body; }
        int min, max;
        switch (pattern.charAt(pos)) {
          case '+':
            ++pos;
            min = 1;
            max = Integer.MAX_VALUE;
            break;
          case '*':
            ++pos;
            min = 0;
            max = Integer.MAX_VALUE;
            break;
          case '?':
            ++pos;
            min = 0;
            max = 1;
            break;
          case '{':
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
              min = Integer.parseInt(
                  comma >= 0 ? counts.substring(0, comma) : counts);
              max = comma >= 0
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
            break;
          default:
            return body;
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


  /**
   * True if, but not necessarily always when the, given regular expression
   * must match the whole input or none of it.
   */
  public static boolean matchesWholeInput(RegExpTree t, String flags) {
    if (flags.indexOf('m') >= 0) { return false; }

    if (!(t instanceof Concatenation)) {
      return false;
    }

    Concatenation c = (Concatenation) t;
    if (c.elements.isEmpty()) { return false; }
    RegExpTree first = c.elements.get(0),
        last = c.elements.get(c.elements.size() - 1);
    if (!(first instanceof Anchor && last instanceof Anchor)) { return false; }
    return ((Anchor) first).type == '^' && ((Anchor) last).type == '$';
  }


  static abstract class RegExpTreeAtom extends RegExpTree {
    @Override
    public boolean isCaseSensitive() {
      return false;
    }

    @Override
    public boolean containsAnchor() {
      return false;
    }

    @Override
    public final int numCapturingGroups() {
      return 0;
    }

    @Override
    public final List<? extends RegExpTree> children() {
      return ImmutableList.of();
    }
  }

  static final class Empty extends RegExpTreeAtom {
    static final Empty INSTANCE = new Empty();

    @Override
    public RegExpTree simplify(String flags) {
      return this;
    }

    @Override
    protected void appendSourceCode(StringBuilder sb) {
      // No output
    }

    @Override
    protected void appendDebugInfo(StringBuilder sb) {
      // No output
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Empty;
    }

    @Override
    public int hashCode() {
      return 0x7ee06141;
    }
  }

  static final class Anchor extends RegExpTreeAtom {
    final char type;
    Anchor(char type) { this.type = type; }

    @Override
    public RegExpTree simplify(String flags) {
      return this;
    }

    @Override
    public boolean containsAnchor() {
      return true;
    }

    @Override
    protected void appendSourceCode(StringBuilder sb) {
      sb.append(type);
    }

    @Override
    protected void appendDebugInfo(StringBuilder sb) {
      sb.append(type);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Anchor && type == ((Anchor) o).type;
    }

    @Override
    public int hashCode() {
      return type ^ 0xe85317ff;
    }
  }

  static final class WordBoundary extends RegExpTreeAtom {
    final char type;

    WordBoundary(char type) {
      this.type = type;
    }

    @Override
    public RegExpTree simplify(String flags) {
      return this;
    }

    @Override
    protected void appendSourceCode(StringBuilder sb) {
      sb.append('\\').append(type);
    }

    @Override
    protected void appendDebugInfo(StringBuilder sb) {
      sb.append(type);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof WordBoundary && type == ((WordBoundary) o).type;
    }

    @Override
    public int hashCode() {
      return 0x5673aa29 ^ type;
    }
  }

  static final class BackReference extends RegExpTreeAtom {
    final int groupIndex;

    BackReference(int groupIndex) {
      Preconditions.checkArgument(groupIndex >= 0 && groupIndex <= 99);
      this.groupIndex = groupIndex;
    }

    @Override
    public RegExpTree simplify(String flags) {
      return this;
    }

    @Override
    protected void appendSourceCode(StringBuilder sb) {
      sb.append('\\').append(groupIndex);
    }

    @Override
    protected void appendDebugInfo(StringBuilder sb) {
      sb.append(groupIndex);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof BackReference
          && groupIndex == ((BackReference) o).groupIndex;
    }

    @Override
    public int hashCode() {
      return 0xff072663 ^ groupIndex;
    }
  }

  static final class Text extends RegExpTreeAtom {
    final String text;

    Text(String text) {
      this.text = text;
    }

    /**
     * @param ch The code-unit to escape.
     * @param next The next code-unit or -1 if indeterminable.
     */
    private static void escapeRegularCharOnto(
        char ch, int next, StringBuilder sb) {
      switch (ch) {
        case '$':
        case '^':
        case '*':
        case '(':
        case ')':
        case '+':
        case '[':
        case '|':
        case '.':
        case '/':
        case '?':
          sb.append('\\').append(ch);
          break;
        case '{':
          // If possibly part of a repetition, then escape.
          // Concatenation is handled by the digitsMightBleed check.
          if ('0' <= next && next <= '9') {
            sb.append('\\');
          }
          sb.append(ch);
          break;
        default:
          escapeCharOnto(ch, sb);
      }
    }

    @Override
    public RegExpTree simplify(String flags) {
      int n = text.length();
      if (n == 0) {
        return Empty.INSTANCE;
      }
      if (flags.indexOf('i') >= 0) {
        String canonicalized = CaseCanonicalize.caseCanonicalize(text);
        if (text != canonicalized) {
          return new Text(canonicalized);
        }
      }
      return this;
    }

    @Override
    public boolean isCaseSensitive() {
      for (int i = 0, n = text.length(); i < n; ++i) {
        if (CaseCanonicalize.CASE_SENSITIVE.contains(text.charAt(i))) {
          return true;
        }
      }
      return false;
    }

    @Override
    protected void appendSourceCode(StringBuilder sb) {
      for (int i = 0, n = text.length(); i < n; ++i) {
        escapeRegularCharOnto(text.charAt(i), i + 1 < n ? text.charAt(i + 1) : -1, sb);
      }
    }

    @Override
    protected void appendDebugInfo(StringBuilder sb) {
      sb.append('`').append(text).append('`');
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Text && text.equals(((Text) o).text);
    }

    @Override
    public int hashCode() {
      return text.hashCode() ^ 0x617e310;
    }
  }

  static final class Repetition extends RegExpTree {
    final RegExpTree body;
    final int min, max;
    final boolean greedy;

    Repetition(RegExpTree body, int min, int max, boolean greedy) {
      this.body = body;
      this.min = min;
      this.max = max;
      this.greedy = greedy;
    }

    @Override
    public RegExpTree simplify(String flags) {
      RegExpTree body = this.body.simplify(flags);
      if (max == 0 && !body.hasCapturingGroup()) { return Empty.INSTANCE; }
      if (body instanceof Empty || NEVER_MATCHES.equals(body)) { return body; }
      int min = this.min;
      int max = this.max;
      if (body instanceof Repetition) {
        Repetition rbody = (Repetition) body;
        if (rbody.greedy == greedy) {
          long lmin = ((long) min) * rbody.min;
          long lmax = ((long) max) * rbody.max;
          if (lmin < Integer.MAX_VALUE) {
            body = rbody.body;
            min = (int) lmin;
            max = lmax >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) lmax;
          }
        }
      }
      if (min == 1 && max == 1) { return body; }
      boolean greedy = this.greedy || min == max;
      return body.equals(this.body) && min == this.min && max == this.max
          && greedy == this.greedy
          ? this
          : new Repetition(body, min, max, greedy).simplify(flags);
    }

    @Override
    public boolean isCaseSensitive() {
      return body.isCaseSensitive();
    }

    @Override
    public boolean containsAnchor() {
      return body.containsAnchor();
    }

    @Override
    public int numCapturingGroups() {
      return body.numCapturingGroups();
    }

    @Override
    public List<? extends RegExpTree> children() {
      return ImmutableList.of(body);
    }

    private void appendBodySourceCode(StringBuilder sb) {
      if (body instanceof Alternation || body instanceof Concatenation
          || body instanceof Repetition
          || (body instanceof Text && ((Text) body).text.length() > 1)) {
        sb.append("(?:");
        body.appendSourceCode(sb);
        sb.append(')');
      } else {
        body.appendSourceCode(sb);
      }
    }

    private static int suffixLen(int min, int max) {
      // This mirrors the branches that renders a suffix in appendSourceCode below.
      if (max == Integer.MAX_VALUE) {
        switch (min) {
          case 0: return 1;  // *
          case 1: return 1;  // +
          default: return 3 + numDecimalDigits(min);  // {3,}
        }
      }
      if (min == 0 && max == 1) {
        return 1;  // ?
      }
      if (min == max) {
        if (min == 1) {
          return 0;  // No suffix needed for {1}.
        }
        return 2 + numDecimalDigits(min);  // {4}
      }
      return 3 + numDecimalDigits(min) + numDecimalDigits(max);  // {2,7}
    }

    private static int numDecimalDigits(int n) {
      if (n < 0) {
        // Negative values should not be passed in.
        throw new AssertionError();
        // If changing this code to support negative values,
        // Integer.MIN_VALUE is a corner-case..
      }
      int nDigits = 1;
      while (n >= 10) {
        ++nDigits;
        n /= 10;
      }
      return nDigits;
    }

    @Override
    protected void appendSourceCode(StringBuilder sb) {
      int bodyStart = sb.length();
      appendBodySourceCode(sb);
      int bodyEnd = sb.length();
      int bodyLen = bodyEnd - bodyStart;
      int min = this.min;
      int max = this.max;
      if (min >= 2 && max == Integer.MAX_VALUE || max - min <= 1) {
        int expanded =
           // If min == max then we want to try expanding to the limit and
           // attach the empty suffix, which is equivalent to min = max = 1,
           // i.e. /a/ vs /a{1}/.
           min == max
           // Give aa+ preference over aaa*.
           || max == Integer.MAX_VALUE
           ? min - 1
           : min;
        int expandedMin = min - expanded;
        int expandedMax = max == Integer.MAX_VALUE ? max : max - expanded;
        int suffixLen = suffixLen(min, max);
        int expandedSuffixLen = suffixLen(expandedMin, expandedMax);
        if (bodyLen * expanded + expandedSuffixLen < suffixLen
            && !body.hasCapturingGroup()) {
          // a{2} -> aa
          // a{2,} -> aa+
          // a{2,3} -> aaa?
          while (--expanded >= 0) {
            sb.append(sb, bodyStart, bodyEnd);
          }
          min = expandedMin;
          max = expandedMax;
        }
      }

      if (max == Integer.MAX_VALUE) {
        switch (min) {
          case 0: sb.append('*'); break;
          case 1: sb.append('+'); break;
          default:
            sb.append('{').append(min).append(",}");
        }
      } else if (min == 0 && max == 1) {
        sb.append('?');
      } else if (min == max) {
        if (min != 1) {
          sb.append('{').append(min).append('}');
        }
      } else {
        sb.append('{').append(min).append(',').append(max).append('}');
      }
      if (!greedy) {
        sb.append('?');
      }
    }

    @Override
    protected void appendDebugInfo(StringBuilder sb) {
      sb.append(" min=").append(min).append(", max=").append(max);
      if (!greedy) { sb.append("  not_greedy"); }
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Repetition)) { return false; }
      Repetition that = (Repetition) o;
      return this.body.equals(that.body)
          && this.min == that.min
          && this.max == that.max
          && this.greedy == that.greedy;
    }

    @Override
    public int hashCode() {
      return min + 31 * (max + 31 * ((greedy ? 1 : 0) + 31 * body.hashCode()));
    }
  }

  static final class Alternation extends RegExpTree {
    final ImmutableList<RegExpTree> alternatives;

    Alternation(List<? extends RegExpTree> alternatives) {
      this.alternatives = ImmutableList.copyOf(alternatives);
    }

    @Override
    public RegExpTree simplify(String flags) {
      List<RegExpTree> alternatives = Lists.newArrayList();
      for (RegExpTree alternative : this.alternatives) {
        alternative = alternative.simplify(flags);
        if (alternative instanceof Alternation) {
          alternatives.addAll(((Alternation) alternative).alternatives);
        } else {
          alternatives.add(alternative);
        }
      }
      // Remove duplicates
      RegExpTree last = null;
      for (Iterator<RegExpTree> it = alternatives.iterator(); it.hasNext();) {
        RegExpTree alternative = it.next();
        if (alternative.equals(NEVER_MATCHES)) { continue; }
        if (alternative.equals(last) && !alternative.hasCapturingGroup()) {
          it.remove();
        } else {
          last = alternative;
        }
      }
      // Collapse character alternatives into character sets.
      for (int i = 0, n = alternatives.size(); i < n; ++i) {
        RegExpTree alternative = alternatives.get(i);
        if ((alternative instanceof Text
             && ((Text) alternative).text.length() == 1)
            || alternative instanceof Charset) {
          int end = i;
          int nCharsets = 0;
          while (end < n) {
            RegExpTree follower = alternatives.get(end);
            if (follower instanceof Charset) {
              ++nCharsets;
            } else if (!(follower instanceof Text
                         && ((Text) follower).text.length() == 1)) {
              break;
            }
            ++end;
          }
          if (end - i >= 3 || (nCharsets != 0 && end - i >= 2)) {
            int[] members = new int[end - i - nCharsets];
            int memberIdx = 0;
            CharRanges chars = CharRanges.EMPTY;
            CharRanges ieExplicits = CharRanges.EMPTY;
            List<RegExpTree> charAlternatives = alternatives.subList(i, end);
            for (RegExpTree charAlternative : charAlternatives) {
              if (charAlternative instanceof Text) {
                char ch = ((Text) charAlternative).text.charAt(0);
                members[memberIdx++] = ch;
                if (IE_SPEC_ERRORS.contains(ch)) {
                  ieExplicits = ieExplicits.union(CharRanges.inclusive(ch, ch));
                }
              } else if (charAlternative instanceof Charset) {
                Charset cs = (Charset) charAlternative;
                chars = chars.union(cs.ranges);
                ieExplicits = ieExplicits.union(cs.ieExplicits);
              }
            }
            chars = chars.union(CharRanges.withMembers(members));
            charAlternatives.clear();
            charAlternatives.add(
                new Charset(chars, ieExplicits).simplify(flags));
            n = alternatives.size();
          }
        }
      }
      switch (alternatives.size()) {
        case 0: return Empty.INSTANCE;
        case 1: return alternatives.get(0);
        case 2:
          if (alternatives.get(1) instanceof Empty) {  // (?:a|) -> a?
            return new Repetition(alternatives.get(0), 0, 1, true);
          } else if (alternatives.get(0) instanceof Empty) {
            return new Repetition(alternatives.get(1), 0, 1, false);
          }
          break;
      }
      // TODO: maybe pull out common prefix or suffix
      return alternatives.equals(this.alternatives)
          ? this : new Alternation(alternatives);
    }

    @Override
    public boolean isCaseSensitive() {
      for (RegExpTree alternative : alternatives) {
        if (alternative.isCaseSensitive()) { return true; }
      }
      return false;
    }

    @Override
    public boolean containsAnchor() {
      for (RegExpTree alternative : alternatives) {
        if (alternative.containsAnchor()) { return true; }
      }
      return false;
    }

    @Override
    public int numCapturingGroups() {
      int n = 0;
      for (RegExpTree alternative : alternatives) {
        n += alternative.numCapturingGroups();
      }
      return n;
    }

    @Override
    public List<? extends RegExpTree> children() {
      return alternatives;
    }

    @Override
    protected void appendSourceCode(StringBuilder sb) {
      for (int i = 0, n = alternatives.size(); i < n; ++i) {
        if (i != 0) {
          sb.append('|');
        }
        alternatives.get(i).appendSourceCode(sb);
      }
    }

    @Override
    protected void appendDebugInfo(StringBuilder sb) {
      // Nothing besides children.
    }

    @Override
    public boolean equals(Object o) {
      return this == o || (
          (o instanceof Alternation)
          && alternatives.equals(((Alternation) o).alternatives));
    }

    @Override
    public int hashCode() {
      return 0x51b57cd1 ^ alternatives.hashCode();
    }
  }

  private static final RegExpTree NEVER_MATCHES = new LookaheadAssertion(
      Empty.INSTANCE, false);

  static final class LookaheadAssertion extends RegExpTree {
    final RegExpTree body;
    final boolean positive;

    LookaheadAssertion(RegExpTree body, boolean positive) {
      this.body = body;
      this.positive = positive;
    }

    @Override
    public RegExpTree simplify(String flags) {
      RegExpTree simpleBody = body.simplify(flags);
      if (simpleBody instanceof Empty) {
        if (positive) {  // Always true
          return simpleBody;
        }
      }
      return new LookaheadAssertion(simpleBody, positive);
    }

    @Override
    public boolean isCaseSensitive() {
      return body.isCaseSensitive();
    }

    @Override
    public boolean containsAnchor() {
      return body.containsAnchor();
    }

    @Override
    public int numCapturingGroups() {
      return body.numCapturingGroups();
    }

    @Override
    public List<? extends RegExpTree> children() {
      return ImmutableList.of(body);
    }

    @Override
    protected void appendSourceCode(StringBuilder sb) {
      sb.append(positive ? "(?=" : "(?!");
      body.appendSourceCode(sb);
      sb.append(')');
    }

    @Override
    protected void appendDebugInfo(StringBuilder sb) {
      sb.append(positive ? "positive" : "negative");
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof LookaheadAssertion)) { return false; }
      LookaheadAssertion that = (LookaheadAssertion) o;
      return this.positive == that.positive && this.body.equals(that.body);
    }

    @Override
    public int hashCode() {
      return 0x723aba9 ^ body.hashCode();
    }
  }

  static final class CapturingGroup extends RegExpTree {
    final RegExpTree body;

    CapturingGroup(RegExpTree body) {
      this.body = body;
    }

    @Override
    public RegExpTree simplify(String flags) {
      return new CapturingGroup(body.simplify(flags));
    }

    @Override
    public boolean isCaseSensitive() {
      return body.isCaseSensitive();
    }

    @Override
    public boolean containsAnchor() {
      return body.containsAnchor();
    }

    @Override
    public int numCapturingGroups() {
      return 1;
    }

    @Override
    public List<? extends RegExpTree> children() {
      return ImmutableList.of(body);
    }

    @Override
    protected void appendSourceCode(StringBuilder sb) {
      sb.append('(');
      body.appendSourceCode(sb);
      sb.append(')');
    }

    @Override
    protected void appendDebugInfo(StringBuilder sb) {
      // Nothing besides children.
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof CapturingGroup
          && body.equals(((CapturingGroup) o).body);
    }

    @Override
    public int hashCode() {
      return 0x55781738 ^ body.hashCode();
    }
  }

  private static final CharRanges DIGITS = CharRanges.inclusive('0', '9');

  private static final CharRanges UCASE_LETTERS
      = CharRanges.inclusive('A', 'Z');

  private static final CharRanges LCASE_LETTERS
      = CharRanges.inclusive('a', 'z');

  private static final CharRanges LETTERS = UCASE_LETTERS.union(LCASE_LETTERS);

  private static final CharRanges WORD_CHARS = DIGITS
      .union(LETTERS).union(CharRanges.withMembers('_'));

  private static final CharRanges INVERSE_WORD_CHARS
      = CharRanges.ALL_CODE_UNITS.difference(WORD_CHARS);

  private static final CharRanges SPACE_CHARS = CharRanges.withMembers(
      '\t', '\n', '\u000b', '\u000c', '\r', ' ', '\u00a0',
      // Unicode 3.0 Zs
      '\u1680', '\u180e', '\u2000', '\u2001',
      '\u2002', '\u2003', '\u2004', '\u2005',
      '\u2006', '\u2007', '\u2008', '\u2009',
      '\u200a',
      // Line terminator chars
      '\u2028', '\u2029',
      // Unicode 3.0 Zs
      '\u202f', '\u205f', '\u3000',
      // Byte order marker is a space character in ES5 but not ES3.
      '\ufeff'
      );

  /** IE is broken around \s.  IE (6, 7, 8 at least), only recognize these. */
  private static final CharRanges IE_SPACE_CHARS = CharRanges.withMembers(
    '\t', '\n', '\u000b', '\u000c', '\r', ' '
    );

  /** IE is broken around \s.  IE (6, 7, 8 at least), only recognize these. */
  private static final CharRanges IE_SPEC_ERRORS = SPACE_CHARS.difference(
      IE_SPACE_CHARS);

  private static final ImmutableMap<Character, CharRanges> NAMED_CHAR_GROUPS
       = ImmutableMap.<Character, CharRanges>builder()
          .put('d', DIGITS)
          .put('D', CharRanges.ALL_CODE_UNITS.difference(DIGITS))
          .put('s', SPACE_CHARS)
          .put('S', CharRanges.ALL_CODE_UNITS.difference(SPACE_CHARS))
          .put('w', WORD_CHARS)
          .put('W', INVERSE_WORD_CHARS)
          .build();

  private static final Charset DOT_CHARSET = new Charset(
      CharRanges.ALL_CODE_UNITS.difference(
          CharRanges.withMembers('\n', '\r', '\u2028', '\u2029')),
      CharRanges.EMPTY);

  static final class Charset extends RegExpTreeAtom {
    final CharRanges ranges;
    /**
     * Code units that were mentioned explicitly and that might be matched by
     * a group according to ECMAScript 5 but would not because of specification
     * violations in IE.
     */
    final CharRanges ieExplicits;

    Charset(CharRanges ranges, CharRanges ieExplicits) {
      this.ranges = ranges;
      this.ieExplicits = ieExplicits;
    }

    private static int complexityWordFolded(CharRanges ranges) {
      return Math.min(
          complexityWordFoldedHelper(ranges),
          1 + complexityWordFoldedHelper(
              CharRanges.ALL_CODE_UNITS.difference(ranges)));
    }

    private static int complexityWordFoldedHelper(CharRanges ranges) {
      int complexity = DecomposedCharset.complexity(ranges);
      if (ranges.containsAll(WORD_CHARS)) {
        complexity = Math.min(
            complexity,
            1 + DecomposedCharset.complexity(ranges.difference(WORD_CHARS)));
      }
      if (ranges.containsAll(INVERSE_WORD_CHARS)) {
        complexity = Math.min(
            complexity,
            1 + DecomposedCharset.complexity(
                ranges.difference(INVERSE_WORD_CHARS)));
      }
      return complexity;
    }

    @Override
    public RegExpTree simplify(String flags) {
      if (ranges.isEmpty()) {
        return NEVER_MATCHES;
      }
      CharRanges best = ranges;
      if (flags.indexOf('i') >= 0) {
        Set<CharRanges> options = Sets.newLinkedHashSet();
        options.add(CaseCanonicalize.expandToAllMatched(ranges));
        options.add(CaseCanonicalize.reduceToMinimum(ranges));

        CharRanges lcaseLetters = ranges.intersection(LCASE_LETTERS);
        CharRanges ucaseLetters = ranges.intersection(UCASE_LETTERS);

        CharRanges lcaseLettersToUpper = lcaseLetters.shift(-32);
        CharRanges ucaseLettersToLower = ucaseLetters.shift(32);

        options.add(ranges.union(ucaseLettersToLower));
        options.add(ranges.union(lcaseLettersToUpper));
        options.add(ranges.union(lcaseLettersToUpper)
                    .union(ucaseLettersToLower));

        options.add(ranges.union(ucaseLettersToLower).difference(ucaseLetters));
        options.add(ranges.union(lcaseLettersToUpper).difference(lcaseLetters));

        int bestComplexity = complexityWordFolded(ranges);

        for (CharRanges option : options) {
          int complexity = complexityWordFolded(option);
          if (complexity < bestComplexity) {
            bestComplexity = complexity;
            best = option;
          }
        }
      }

      if (best.getNumRanges() == 1
          && best.end(0) - best.start(0) == 1) {
        return new Text(Character.toString((char) best.start(0)));
      }

      if (!best.equals(ranges)) {
        return new Charset(best, ieExplicits);
      }

      return this;
    }

    @Override
    public boolean isCaseSensitive() {
      // We could test
      //     !ranges.equals(CaseCanonicalize.expandToAllMatched(ranges))
      // but we get better optimizations by leaving the 'i' flag on in most
      // cases.

      // Check whether skipping all the character groups that are known
      // case-insensitive leaves us with something that matches the above
      // definition.
      CharRanges withoutNamedGroups = decompose().ranges;
      return !withoutNamedGroups.equals(
            CaseCanonicalize.expandToAllMatched(withoutNamedGroups));
    }

    private DecomposedCharset decompose(CharRanges ranges, boolean inverted) {
      StringBuilder namedGroups = new StringBuilder();
      CharRanges rangesInterIeExplicits = ranges.intersection(ieExplicits);
      while (true) {
        char groupName = 0;
        CharRanges simplest = null;
        int minComplexity = DecomposedCharset.complexity(ranges);
        for (Map.Entry<Character, CharRanges> namedGroup
             : NAMED_CHAR_GROUPS.entrySet()) {
          CharRanges group = namedGroup.getValue();
          if (ranges.containsAll(group)) {
            CharRanges withoutGroup = ranges.difference(group).union(
                rangesInterIeExplicits);
            int complexity = DecomposedCharset.complexity(withoutGroup);
            if (complexity < minComplexity) {
              simplest = withoutGroup;
              groupName = namedGroup.getKey().charValue();
              minComplexity = complexity;
            }
          }
        }
        if (simplest != null) {
          namedGroups.append('\\').append(groupName);
          ranges = simplest;
        } else {
          break;
        }
      }
      return new DecomposedCharset(inverted, ranges, namedGroups.toString());
    }

    @Override
    protected void appendSourceCode(StringBuilder sb) {
      if (DOT_CHARSET.ranges.equals(ranges)) {
        sb.append('.');
        return;
      }
      decompose().appendSourceCode(sb);
    }

    DecomposedCharset decompose() {
      CharRanges negRanges = CharRanges.ALL_CODE_UNITS.difference(ranges);
      if (!ieExplicits.isEmpty()) {
        if (negRanges.intersection(ieExplicits).isEmpty()) {
          return decompose(ranges, false);
        } else if (ranges.intersection(ieExplicits).isEmpty()) {
          return decompose(negRanges, true);
        }
      }
      DecomposedCharset positive = decompose(ranges, false);
      DecomposedCharset negative = decompose(negRanges, true);
      return positive.complexity() <= negative.complexity()
          ? positive : negative;
    }

    @Override
    protected void appendDebugInfo(StringBuilder sb) {
      sb.append(ranges);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Charset && ranges.equals(((Charset) o).ranges);
    }

    @Override
    public int hashCode() {
      return ranges.hashCode() ^ 0xdede2246;
    }
  }

  static final class DecomposedCharset {
    boolean inverted;
    final CharRanges ranges;
    final String namedGroups;

    DecomposedCharset(
        boolean inverted, CharRanges ranges, String namedGroups) {
      this.inverted = inverted;
      this.ranges = ranges;
      this.namedGroups = namedGroups;
    }

    int complexity() {
      return (inverted ? 1 : 0) + namedGroups.length() + complexity(ranges);
    }

    void appendSourceCode(StringBuilder sb) {
      if (ranges.isEmpty()) {
        if (!inverted && namedGroups.length() == 2) {
          sb.append(namedGroups);
          return;
        } else if (ranges.isEmpty() && namedGroups.length() == 0) {
          sb.append(inverted ? "[\\S\\s]" : "(?!)");
          return;
        }
      }
      sb.append('[');
      if (inverted) { sb.append('^'); }
      sb.append(namedGroups);
      boolean rangesStartCharset = !inverted && namedGroups.length() == 0;
      boolean emitDashAtEnd = false;
      for (int i = 0, n = ranges.getNumRanges(); i < n; ++i) {
        char start = (char) ranges.start(i);
        char end = (char) (ranges.end(i) - 1);
        switch (end - start) {
          case 0:
            if (start == '-') {
              // Put it at the end where it doesn't need escaping.
              emitDashAtEnd = true;
            } else {
              escapeRangeCharOnto(
                  start, rangesStartCharset, i == 0, i + 1 == n, sb);
            }
            break;
          case 1:
            escapeRangeCharOnto(start, rangesStartCharset, i == 0, false, sb);
            escapeRangeCharOnto(
                end, rangesStartCharset, false, i + 1 == n, sb);
            break;
          default:
            escapeRangeCharOnto(start, rangesStartCharset, i == 0, false, sb);
            sb.append('-');
            escapeRangeCharOnto(end, rangesStartCharset, false, true, sb);
            break;
        }
      }
      if (emitDashAtEnd) { sb.append('-'); }
      sb.append(']');
    }

    static void escapeRangeCharOnto(
        char ch, boolean startIsFlush, boolean atStart, boolean atEnd,
        StringBuilder sb) {
      switch (ch) {
        case '\b':
          sb.append("\\b");
          break;
        case '^':
          sb.append(atStart && startIsFlush ? "\\^" : "^");
          break;
        case '-':
          sb.append(atStart || atEnd ? "-" : "\\-");
          break;
        case '\\':
        case ']':
          sb.append('\\').append(ch);
          break;
        default:
          escapeCharOnto(ch, sb);
      }
    }

    static int complexity(CharRanges ranges) {
      int complexity = 0;
      for (int i = 0, n = ranges.getNumRanges(); i < n; ++i) {
        int start = ranges.start(i);
        int end = ranges.end(i) - 1;
        if (start < 0x20 || start >= 0x7f) {
          complexity += start >= 0x100 ? 6 : 4;
        } else {
          ++complexity;
        }
        switch (end - start) {
          case 0: continue;
          case 1: break;
          default: complexity += 1;
        }
        if (end < 0x20 || end >= 0x7f) {
          complexity += end >= 0x100 ? 6 : 4;
        } else {
          ++complexity;
        }
      }
      return complexity;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof DecomposedCharset)) {
        return false;
      }
      DecomposedCharset that = (DecomposedCharset) o;
      return this.inverted = that.inverted
          && this.ranges.equals(that.ranges)
          && this.namedGroups.equals(that.namedGroups);
    }

    @Override
    public int hashCode() {
      return ranges.hashCode()
          + 31 * (namedGroups.hashCode() + (inverted ? 1 : 0));
    }
  }

  static final class Concatenation extends RegExpTree {
    final ImmutableList<RegExpTree> elements;

    Concatenation(RegExpTree a, RegExpTree b) {
      elements = ImmutableList.of(a, b);
    }

    Concatenation(List<? extends RegExpTree> elements) {
      this.elements = ImmutableList.copyOf(elements);
    }

    @Override
    public RegExpTree simplify(final String flags) {
      class Simplifier {
        final List<RegExpTree> simplified = Lists.newArrayList();

        void simplify(RegExpTree t) {
          if (t instanceof Concatenation) {
            for (RegExpTree child : ((Concatenation) t).elements) {
              simplify(child);
            }
          } else if (t instanceof Empty) {
            // Do nothing
          } else {
            int lastIndex = simplified.size() - 1;
            if (lastIndex >= 0) {
              RegExpTree pairwise = simplifyPairwise(
                  simplified.get(lastIndex), t);
              if (pairwise != null) {
                simplified.set(lastIndex, pairwise);
                return;
              }
            }
            simplified.add(t);
          }
        }

        RegExpTree simplifyPairwise(RegExpTree before, RegExpTree after) {
          if (before instanceof Text && after instanceof Text) {
            return new Text(
                ((Text) before).text + ((Text) after).text).simplify(flags);
          }
          // Fold adjacent repetitions.
          int beforeMin = 1, beforeMax = 1;
          RegExpTree beforeBody = before;
          boolean beforeGreedy = false;
          if (before instanceof Repetition) {
            Repetition r = (Repetition) before;
            beforeMin = r.min;
            beforeMax = r.max;
            beforeBody = r.body;
            beforeGreedy = r.greedy;
          }
          int afterMin = 1, afterMax = 1;
          RegExpTree afterBody = after;
          boolean afterGreedy = false;
          if (after instanceof Repetition) {
            Repetition r = (Repetition) after;
            afterMin = r.min;
            afterMax = r.max;
            afterBody = r.body;
            afterGreedy = r.greedy;
          }
          if (beforeBody.equals(afterBody)
              && !beforeBody.hasCapturingGroup()) {
            long lmin = ((long) beforeMin) + afterMin;
            long lmax = ((long) beforeMax) + afterMax;
            if (lmin < Integer.MAX_VALUE) {
              int min = (int) lmin;
              int max = lmax >= Integer.MAX_VALUE
                  ? Integer.MAX_VALUE : (int) lmax;
              return new Repetition(
                  beforeBody, min, max,
                  beforeGreedy || afterGreedy || min == max);
            }
          }
          return null;
        }
      }

      Simplifier s = new Simplifier();
      for (RegExpTree element : elements) {
        s.simplify(element.simplify(flags));
      }

      switch (s.simplified.size()) {
        case 0: return Empty.INSTANCE;
        case 1: return s.simplified.get(0);
        default: return new Concatenation(s.simplified);
      }
    }

    @Override
    public boolean isCaseSensitive() {
      for (RegExpTree element : elements) {
        if (element.isCaseSensitive()) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean containsAnchor() {
      for (RegExpTree element : elements) {
        if (element.containsAnchor()) {
          return true;
        }
      }
      return false;
    }

    @Override
    public int numCapturingGroups() {
      int n = 0;
      for (RegExpTree element : elements) {
        n += element.numCapturingGroups();
      }
      return n;
    }

    @Override
    public List<? extends RegExpTree> children() {
      return elements;
    }

    @Override
    protected void appendSourceCode(StringBuilder sb) {
      // True if the last content written might consume
      // decimal digits written subsequently.
      boolean digitsMightBleed = false;
      for (RegExpTree element : elements) {
        boolean parenthesize = false;
        if (element instanceof Alternation
            || element instanceof Concatenation) {
          parenthesize = true;
        }
        if (parenthesize) {
          sb.append("(?:");
          element.appendSourceCode(sb);
          sb.append(')');
        } else {
          int start = sb.length();
          element.appendSourceCode(sb);
          if (digitsMightBleed && sb.length() > start) {
            char firstChar = sb.charAt(start);
            if ('0' <= firstChar && firstChar <= '9') {
              // Bleeding happened.
              // If the last character would be ambiguous
              // with a repetition, escape it.
              if (sb.charAt(start - 1) == '{') {
                // Concatenation from optimization of
                // /{(?:0,}/ -> /\{0,}/
                sb.insert(start - 1, '\\');
              } else {
                // Or parenthesize otherwise.
                // Concatenation from optimization of
                // /(.)\1(?:0)/ -> /(.)\1(?:0)/.
                sb.insert(start, "(?:").append(')');
              }
            }
          }
        }
        digitsMightBleed = (
            // \1(?:0) bleeds if there are 10 or more
            // capturing groups preceding.
            (element instanceof BackReference
             && ((BackReference) element).groupIndex < 10)
            // foo{(?:10}) bleeds.
            || (element instanceof Text
                && ((Text) element).text.endsWith("{")));
      }
    }

    @Override
    protected void appendDebugInfo(StringBuilder sb) {
      // Nothing besides children.
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Concatenation
          && elements.equals(((Concatenation) o).elements);
    }

    @Override
    public int hashCode() {
      return 0x20997e3e ^ elements.hashCode();
    }
  }

  static void escapeCharOnto(char ch, StringBuilder sb) {
    switch (ch) {
      case '\u0000':
        sb.append("\\0");
        break;
      case '\f':
        sb.append("\\f");
        break;
      case '\t':
        sb.append("\\t");
        break;
      case '\n':
        sb.append("\\n");
        break;
      case '\r':
        sb.append("\\r");
        break;
      case '\\':
        sb.append("\\\\");
        break;
      default:
        if (ch < 0x20 || ch >= 0x7f) {
          if (ch >= 0x100) {
            sb.append("\\u");
            sb.append("0123456789abcdef".charAt((ch >> 12) & 0xf));
            sb.append("0123456789abcdef".charAt((ch >> 8) & 0xf));
            sb.append("0123456789abcdef".charAt((ch >> 4) & 0xf));
            sb.append("0123456789abcdef".charAt((ch) & 0xf));
          } else {
            sb.append("\\x");
            sb.append("0123456789abcdef".charAt((ch >> 4) & 0xf));
            sb.append("0123456789abcdef".charAt((ch) & 0xf));
          }
        } else {
          sb.append(ch);
        }
    }
  }
}
