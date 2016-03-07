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

package com.google.javascript.jscomp.parsing.parser.util.format;

import java.util.Arrays;
import java.util.Date;

/**
 * This is a hacked apart version of the Apache Harmony String.format class
 * with all parts outside of the GWT subset removed.  It will work for
 * simple format commands but does not handle I18N and doesn't handle complex
 * formatting options such as calendar entries and hashcodes.
 *
 * Formats arguments according to a format string (like {@code printf} in C).
 * <p>
 */
public final class SimpleFormat {
  final StringBuilder out;

  // Implementation details.
  private Object arg;
  private FormatToken formatToken;

  /**
   * Writes a formatted string to the output destination of the {@code Formatter}.
   *
   * @param format
   *            a format string.
   * @param args
   *            the arguments list used in the {@code format()} method. If there are
   *            more arguments than those specified by the format string, then
   *            the additional arguments are ignored.
   * @return this {@code Formatter}.
   * @throws IllegalFormatFlagsException
   *             if the format string is illegal or incompatible with the
   *             arguments, or if fewer arguments are sent than those required by
   *             the format string, or any other illegal situation.
   */
  public static String format(String format, Object... args) {
    SimpleFormat f = new SimpleFormat();
    f.doFormat(format, args);
    return f.out.toString();
  }

  private SimpleFormat() {
    out = new StringBuilder();
  }

  private void doFormat(String format, Object... args) {
    FormatSpecifierParser fsp = new FormatSpecifierParser(format);
    int currentObjectIndex = 0;
    Object lastArgument = null;
    boolean hasLastArgumentSet = false;

    int length = format.length();
    int i = 0;
    while (i < length) {
      // Find the maximal plain-text sequence...
      int plainTextStart = i;
      int nextPercent = format.indexOf('%', i);
      int plainTextEnd = (nextPercent == -1) ? length : nextPercent;
      // ...and output it.
      if (plainTextEnd > plainTextStart) {
        outputCharSequence(format, plainTextStart, plainTextEnd);
      }
      i = plainTextEnd;
      // Do we have a format specifier?
      if (i < length) {
        FormatToken token = fsp.parseFormatToken(i + 1);

        Object argument = null;
        if (token.requireArgument()) {
          int index =
              token.getArgIndex() == FormatToken.UNSET ? currentObjectIndex++ : token.getArgIndex();
          argument = getArgument(args, index, fsp, lastArgument, hasLastArgumentSet);
          lastArgument = argument;
          hasLastArgumentSet = true;
        }

        CharSequence substitution = transform(token, argument);
        // The substitution is null if we called Formattable.formatTo.
        if (substitution != null) {
          outputCharSequence(substitution, 0, substitution.length());
        }
        i = fsp.i;
      }
    }
  }

  // Fixes http://code.google.com/p/android/issues/detail?id=1767.
  private void outputCharSequence(CharSequence cs, int start, int end) {
    out.append(cs, start, end);
  }

  private static Object getArgument(
      Object[] args,
      int index,
      FormatSpecifierParser fsp,
      Object lastArgument,
      boolean hasLastArgumentSet) {
    if (index == FormatToken.LAST_ARGUMENT_INDEX && !hasLastArgumentSet) {
      throw new MissingFormatArgumentException("<");
    }

    if (args == null) {
      return null;
    }

    if (index >= args.length) {
      throw new MissingFormatArgumentException(fsp.getFormatSpecifierText());
    }

    if (index == FormatToken.LAST_ARGUMENT_INDEX) {
      return lastArgument;
    }

    return args[index];
  }

  /*
   * Complete details of a single format specifier parsed from a format string.
   */
  private static class FormatToken {
    static final int LAST_ARGUMENT_INDEX = -2;

    static final int UNSET = -1;

    static final int DEFAULT_PRECISION = 6;

    private int argIndex = UNSET;

    // These have package access for performance. They used to be represented by an int bitmask
    // and accessed via methods, but Android's JIT doesn't yet do a good job of such code.
    // Direct field access, on the other hand, is fast.
    boolean flagAdd;
    boolean flagComma;
    boolean flagMinus;
    boolean flagParenthesis;
    boolean flagSharp;
    boolean flagSpace;
    boolean flagZero;

    private char conversionType = (char) UNSET;
    private char dateSuffix;

    private int precision = UNSET;
    private int width = UNSET;

    private StringBuilder strFlags;

    // Tests whether there were no flags, no width, and no precision specified.
    private boolean isDefault() {
      return !flagAdd
          && !flagComma
          && !flagMinus
          && !flagParenthesis
          && !flagSharp
          && !flagSpace
          && !flagZero
          && width == UNSET
          && precision == UNSET;
    }

    private boolean isPrecisionSet() {
      return precision != UNSET;
    }

    private int getArgIndex() {
      return argIndex;
    }

    private void setArgIndex(int index) {
      argIndex = index;
    }

    private int getWidth() {
      return width;
    }

    private void setWidth(int width) {
      this.width = width;
    }

    private int getPrecision() {
      return precision;
    }

    private void setPrecision(int precise) {
      this.precision = precise;
    }

    private String getStrFlags() {
      return (strFlags != null) ? strFlags.toString() : "";
    }

    /*
     * Sets qualified char as one of the flags. If the char is qualified,
     * sets it as a flag and returns true. Or else returns false.
     */
    private boolean setFlag(int ch) {
      boolean dupe = false;
      switch (ch) {
        case '+':
          dupe = flagAdd;
          flagAdd = true;
          break;
        case ',':
          dupe = flagComma;
          flagComma = true;
          break;
        case '-':
          dupe = flagMinus;
          flagMinus = true;
          break;
        case '(':
          dupe = flagParenthesis;
          flagParenthesis = true;
          break;
        case '#':
          dupe = flagSharp;
          flagSharp = true;
          break;
        case ' ':
          dupe = flagSpace;
          flagSpace = true;
          break;
        case '0':
          dupe = flagZero;
          flagZero = true;
          break;
        default:
          return false;
      }
      if (dupe) {
        throw new RuntimeException(String.valueOf(ch));
      }
      if (strFlags == null) {
        strFlags = new StringBuilder(7); // There are seven possible flags.
      }
      strFlags.append((char) ch);
      return true;
    }

    private char getConversionType() {
      return conversionType;
    }

    private void setConversionType(char c) {
      conversionType = c;
    }

    private void setDateSuffix(char c) {
      dateSuffix = c;
    }

    private boolean requireArgument() {
      return conversionType != '%' && conversionType != 'n';
    }

    private void checkFlags(Object arg) {
      // Work out which flags are allowed.
      boolean allowAdd = false;
      boolean allowComma = false;
      boolean allowMinus = true;
      boolean allowParenthesis = false;
      boolean allowSharp = false;
      boolean allowSpace = false;
      boolean allowZero = false;
      // Precision and width?
      boolean allowPrecision = true;
      boolean allowWidth = true;
      // Argument?
      boolean allowArgument = true;
      switch (conversionType) {
          // Character and date/time.
        case 'c':
        case 'C':
        case 't':
        case 'T':
          // Only '-' is allowed.
          allowPrecision = false;
          break;

          // Floating point.
        case 'g':
        case 'G':
          allowAdd = allowComma = allowParenthesis = allowSpace = allowZero = true;
          break;
        case 'f':
          allowAdd = allowComma = allowParenthesis = allowSharp = allowSpace = true;
          allowZero = true;
          break;
        case 'e':
        case 'E':
          allowAdd = allowParenthesis = allowSharp = allowSpace = allowZero = true;
          break;
        case 'a':
        case 'A':
          allowAdd = allowSharp = allowSpace = allowZero = true;
          break;

          // Integral.
        case 'd':
          allowAdd = allowComma = allowParenthesis = allowSpace = allowZero = true;
          allowPrecision = false;
          break;
        case 'o':
        case 'x':
        case 'X':
          allowSharp = allowZero = true;
          if (arg == null) {
            allowAdd = allowParenthesis = allowSpace = true;
          }
          allowPrecision = false;
          break;

          // Special.
        case 'n':
          // Nothing is allowed.
          allowMinus = false;
          allowArgument = allowPrecision = allowWidth = false;
          break;
        case '%':
          // The only flag allowed is '-', and no argument or precision is allowed.
          allowArgument = false;
          allowPrecision = false;
          break;

          // Strings, booleans and hash codes.
        case 's':
        case 'S':
        case 'b':
        case 'B':
        case 'h':
        case 'H':
          break;

        default:
          throw new RuntimeException("unknownFormatConversionException");
      }

      // Check for disallowed flags.
      String mismatch = null;
      if (!allowAdd && flagAdd) {
        mismatch = "+";
      } else if (!allowComma && flagComma) {
        mismatch = ",";
      } else if (!allowMinus && flagMinus) {
        mismatch = "-";
      } else if (!allowParenthesis && flagParenthesis) {
        mismatch = "(";
      } else if (!allowSharp && flagSharp) {
        mismatch = "#";
      } else if (!allowSpace && flagSpace) {
        mismatch = " ";
      } else if (!allowZero && flagZero) {
        mismatch = "0";
      }
      if (mismatch != null) {
        if (conversionType == 'n') {
          // For no good reason, %n is a special case...
          throw new RuntimeException("IllegalFormatFlagsException:" + mismatch);
        } else {
          throw new RuntimeException(
              "FormatFlagsConversionMismatchException:" + mismatch + "," + conversionType);
        }
      }

      // Check for a missing width with flags that require a width.
      if ((flagMinus || flagZero) && width == UNSET) {
        throw new MissingFormatWidthException("-" + conversionType);
      }

      // Check that no-argument conversion types don't have an argument.
      // Note: the RI doesn't enforce this.
      if (!allowArgument && argIndex != UNSET) {
        throw new IllegalFormatFlagsException(getStrFlags());
      }

      // Check that we don't have a precision or width where they're not allowed.
      if (!allowPrecision && precision != UNSET) {
        throw new IllegalFormatPrecisionException(precision);
      }
      if (!allowWidth && width != UNSET) {
        throw new IllegalFormatWidthException(width);
      }

      // Some combinations make no sense...
      if (flagAdd && flagSpace) {
        throw new IllegalFormatFlagsException("the '+' and ' ' flags are incompatible");
      }
      if (flagMinus && flagZero) {
        throw new IllegalFormatFlagsException("the '-' and '0' flags are incompatible");
      }
    }

    private UnknownFormatConversionException unknownFormatConversionException() {
      if (conversionType == 't' || conversionType == 'T') {
        throw new UnknownFormatConversionException(conversionType + "" + dateSuffix);
      }
      throw new UnknownFormatConversionException(String.valueOf(conversionType));
    }
  }

  /*
   * Gets the formatted string according to the format token and the
   * argument.
   */
  private CharSequence transform(FormatToken token, Object argument) {
    this.formatToken = token;
    this.arg = argument;

    // There are only two format specifiers that matter: "%d" and "%s".
    // Nothing else is common in the wild. We fast-path these two to
    // avoid the heavyweight machinery needed to cope with flags, width,
    // and precision.
    if (token.isDefault()) {
      switch (token.getConversionType()) {
        case 's':
          if (arg == null) {
            return "null";
          }
        case 'd':
          if (arg instanceof Integer
              || arg instanceof Long
              || arg instanceof Short
              || arg instanceof Byte) {
            String result = arg.toString();
            return result;
          }
      }
    }

    formatToken.checkFlags(arg);
    CharSequence result;
    switch (token.getConversionType()) {
      case 'B':
      case 'b':
        result = transformFromBoolean();
        break;
      case 'H':
      case 'h':
        result = transformFromHashCode();
        break;
      case 'S':
      case 's':
        result = transformFromString();
        break;
      case 'C':
      case 'c':
        result = transformFromCharacter();
        break;
      case 'd':
      case 'o':
      case 'x':
      case 'X':
        result = transformFromInteger();
        break;
      case 'A':
      case 'a':
      case 'E':
      case 'e':
      case 'f':
      case 'G':
      case 'g':
        result = transformFromFloat();
        break;
      case '%':
        result = transformFromPercent();
        break;
      case 'n':
        result = transformFromLineSeparator();
        break;
      case 't':
      case 'T':
        result = transformFromDateTime();
        break;
      default:
        throw token.unknownFormatConversionException();
    }

    if (Character.isUpperCase(token.getConversionType()) && result != null) {
      result = result.toString().toUpperCase();
    }
    return result;
  }

  private IllegalFormatConversionException badArgumentType() {
    throw new IllegalFormatConversionException(formatToken.getConversionType(), arg.getClass());
  }

  private CharSequence transformFromBoolean() {
    CharSequence result;
    if (arg instanceof Boolean) {
      result = arg.toString();
    } else if (arg == null) {
      result = "false";
    } else {
      result = "true";
    }
    return padding(result, 0);
  }

  private CharSequence transformFromHashCode() {
    CharSequence result;
    if (arg == null) {
      result = "null";
    } else {
      throw new RuntimeException("Integer.toHexString is outside the GWT subset");
    }
    return padding(result, 0);
  }

  private CharSequence transformFromString() {
    CharSequence result = String.valueOf(arg);
    return padding(result, 0);
  }

  private CharSequence transformFromCharacter() {
    if (arg == null) {
      return padding("null", 0);
    }
    if (arg instanceof Character) {
      return padding(String.valueOf(arg), 0);
    } else if (arg instanceof Byte || arg instanceof Short || arg instanceof Integer) {
      int codePoint = ((Number) arg).intValue();
      if (!Character.isValidCodePoint(codePoint)) {
        throw new IllegalFormatCodePointException(codePoint);
      }
      CharSequence result =
          (codePoint < Character.MIN_SUPPLEMENTARY_CODE_POINT)
              ? String.valueOf((char) codePoint)
              : String.valueOf(Character.toChars(codePoint));
      return padding(result, 0);
    } else {
      throw badArgumentType();
    }
  }

  private CharSequence transformFromPercent() {
    return padding("%", 0);
  }

  private static CharSequence transformFromLineSeparator() {
    return "\n";
  }

  private CharSequence padding(CharSequence source, int startIndex) {
    int start = startIndex;
    int width = formatToken.getWidth();
    int precision = formatToken.getPrecision();

    int length = source.length();
    if (precision >= 0) {
      length = Math.min(length, precision);
      if (source instanceof StringBuilder) {
        ((StringBuilder) source).setLength(length);
      } else {
        source = source.subSequence(0, length);
      }
    }
    if (width > 0) {
      width = Math.max(source.length(), width);
    }
    if (length >= width) {
      return source;
    }

    char paddingChar = '\u0020'; // space as padding char.
    if (formatToken.flagZero) {
      if (formatToken.getConversionType() == 'd') {
        paddingChar = '0';
      } else {
        paddingChar = '0'; // No localized digits for bases other than decimal.
      }
    } else {
      // if padding char is space, always pad from the start.
      start = 0;
    }
    char[] paddingChars = new char[width - length];
    Arrays.fill(paddingChars, paddingChar);

    boolean paddingRight = formatToken.flagMinus;
    StringBuilder result = toStringBuilder(source);
    if (paddingRight) {
      result.append(paddingChars);
    } else {
      result.insert(start, paddingChars);
    }
    return result;
  }

  private static StringBuilder toStringBuilder(CharSequence cs) {
    return cs instanceof StringBuilder ? (StringBuilder) cs : new StringBuilder(cs);
  }

  private StringBuilder wrapParentheses(StringBuilder result) {
    result.setCharAt(0, '('); // Replace the '-'.
    if (formatToken.flagZero) {
      formatToken.setWidth(formatToken.getWidth() - 1);
      result = (StringBuilder) padding(result, 1);
      result.append(')');
    } else {
      result.append(')');
      result = (StringBuilder) padding(result, 0);
    }
    return result;
  }

  private CharSequence transformFromInteger() {
    int startIndex = 0;
    StringBuilder result = new StringBuilder();
    char currentConversionType = formatToken.getConversionType();

    long value;
    if (arg instanceof Long) {
      value = ((Long) arg).longValue();
    } else if (arg instanceof Integer) {
      value = ((Integer) arg).longValue();
    } else if (arg instanceof Short) {
      value = ((Short) arg).longValue();
    } else if (arg instanceof Byte) {
      value = ((Byte) arg).longValue();
    } else {
      throw badArgumentType();
    }

    if (formatToken.flagSharp) {
      if (currentConversionType == 'o') {
        result.append("0");
        startIndex += 1;
      } else {
        result.append("0x");
        startIndex += 2;
      }
    }

    if ('d' == currentConversionType) {
      if (formatToken.flagComma) {
        // Too bad... we don't care about the comma...
        result.append(arg);
      } else {
        result.append(value);
      }
      if (value < 0) {
        if (formatToken.flagParenthesis) {
          return wrapParentheses(result);
        } else if (formatToken.flagZero) {
          startIndex++;
        }
      } else {
        if (formatToken.flagAdd) {
          result.insert(0, '+');
          startIndex += 1;
        } else if (formatToken.flagSpace) {
          result.insert(0, ' ');
          startIndex += 1;
        }
      }
    } else {
      // Undo sign-extension, since we'll be using Long.to(Octal|Hex)String.
      if (arg instanceof Byte) {
        value &= 0xffL;
      } else if (arg instanceof Short) {
        value &= 0xffffL;
      } else if (arg instanceof Integer) {
        value &= 0xffffffffL;
      }
      if ('o' == currentConversionType) {
        result.append(Long.toOctalString(value));
      } else {
        result.append(Long.toHexString(value));
      }
    }

    return padding(result, startIndex);
  }

  private CharSequence transformFromSpecialNumber() {
    if (!(arg instanceof Number)) {
      return null;
    }

    Number number = (Number) arg;
    double d = number.doubleValue();
    String source = null;
    if (Double.isNaN(d)) {
      source = "NaN";
    } else if (d == Double.POSITIVE_INFINITY) {
      if (formatToken.flagAdd) {
        source = "+Infinity";
      } else if (formatToken.flagSpace) {
        source = " Infinity";
      } else {
        source = "Infinity";
      }
    } else if (d == Double.NEGATIVE_INFINITY) {
      if (formatToken.flagParenthesis) {
        source = "(Infinity)";
      } else {
        source = "-Infinity";
      }
    } else {
      return null;
    }

    formatToken.setPrecision(FormatToken.UNSET);
    formatToken.flagZero = false;
    return padding(source, 0);
  }

  private CharSequence transformFromNull() {
    formatToken.flagZero = false;
    return padding("null", 0);
  }

  private CharSequence transformFromDateTime() {
    if (arg == null) {
      return transformFromNull();
    }
    // Ignore calendar...
    // this is a total hack... as we don't care...
    Date date = null;
    {
      if (arg instanceof Long) {
        date = new Date(((Long) arg).longValue());
      } else if (arg instanceof Date) {
        date = (Date) arg;
      } else {
        throw badArgumentType();
      }
    }

    StringBuilder result = new StringBuilder();
    result.append(date);
    return padding(result, 0);
  }

  private CharSequence transformFromFloat() {
    if (arg == null) {
      return transformFromNull();
    }

    if (!(arg instanceof Float || arg instanceof Double)) {
      throw badArgumentType();
    }

    CharSequence specialNumberResult = transformFromSpecialNumber();
    if (specialNumberResult != null) {
      return specialNumberResult;
    }

    char conversionType = formatToken.getConversionType();
    if (conversionType != 'a' && conversionType != 'A' && !formatToken.isPrecisionSet()) {
      formatToken.setPrecision(FormatToken.DEFAULT_PRECISION);
    }

    StringBuilder result = new StringBuilder();
    switch (conversionType) {
      case 'a':
      case 'A':
        transformA();
        break;
      case 'e':
      case 'E':
        transformE(result);
        break;
      case 'f':
        transformF(result);
        break;
      case 'g':
      case 'G':
        transformG(result);
        break;
      default:
        throw formatToken.unknownFormatConversionException();
    }

    formatToken.setPrecision(FormatToken.UNSET);

    int startIndex = 0;
    if ('-' == result.charAt(0)) {
      if (formatToken.flagParenthesis) {
        return wrapParentheses(result);
      }
    } else {
      if (formatToken.flagSpace) {
        result.insert(0, ' ');
        startIndex++;
      }
      if (formatToken.flagAdd) {
        result.insert(0, '+');
        startIndex++;
      }
    }

    char firstChar = result.charAt(0);
    if (formatToken.flagZero && (firstChar == '+' || firstChar == '-')) {
      startIndex = 1;
    }

    if (conversionType == 'a' || conversionType == 'A') {
      startIndex += 2;
    }
    return padding(result, startIndex);
  }

  private void transformE(StringBuilder result) {
    StringBuilder pattern = new StringBuilder();
    pattern.append('0');
    if (formatToken.getPrecision() > 0) {
      pattern.append('.');
      char[] zeros = new char[formatToken.getPrecision()];
      Arrays.fill(zeros, '0');
      pattern.append(zeros);
    }
    pattern.append("E+00");

    String formattedString = arg.toString();
    result.append(formattedString.replace('E', 'e'));

    // if the flag is sharp and decimal separator is always given out.
    if (formatToken.flagSharp && formatToken.getPrecision() == 0) {
      int indexOfE = result.indexOf("e");
      result.insert(indexOfE, ".");
    }
  }

  private void transformG(StringBuilder result) {
    int precision = formatToken.getPrecision();
    precision = (0 == precision ? 1 : precision);
    formatToken.setPrecision(precision);

    double d = ((Number) arg).doubleValue();
    if (d == 0.0) {
      precision--;
      formatToken.setPrecision(precision);
      transformF(result);
      return;
    }

    d = Math.abs(d);
    if (Double.isInfinite(d)) {
      precision = formatToken.getPrecision();
      precision--;
      formatToken.setPrecision(precision);
      transformE(result);
      return;
    }
    // Skipped a bunch of scientific notation stuff.
    transformF(result);
  }

  private void transformF(StringBuilder result) {
    // Removed all the actual logic...
    result.append(arg);
  }

  private void transformA() {
    if (arg instanceof Float) {
      throw new RuntimeException("Float.toHexString is outside the GWT subset");
    } else if (arg instanceof Double) {
      throw new RuntimeException("Double.toHexString is outside the GWT subset");
    } else {
      throw badArgumentType();
    }
  }

  private static class FormatSpecifierParser {
    private final String format;
    private final int length;

    private int startIndex;
    private int i;

    /**
     * Constructs a new parser for the given format string.
     */
    FormatSpecifierParser(String format) {
      this.format = format;
      this.length = format.length();
    }

    /**
     * Returns a FormatToken representing the format specifier starting at 'offset'.
     * @param offset the first character after the '%'
     */
    FormatToken parseFormatToken(int offset) {
      this.startIndex = offset;
      this.i = offset;
      return parseArgumentIndexAndFlags(new FormatToken());
    }

    /**
     * Returns a string corresponding to the last format specifier that was parsed.
     * Used to construct error messages.
     */
    String getFormatSpecifierText() {
      return format.substring(startIndex, i);
    }

    private int peek() {
      return (i < length) ? format.charAt(i) : -1;
    }

    private char advance() {
      if (i >= length) {
        throw unknownFormatConversionException();
      }
      return format.charAt(i++);
    }

    private UnknownFormatConversionException unknownFormatConversionException() {
      throw new UnknownFormatConversionException(getFormatSpecifierText());
    }

    private static boolean isDigit(int ch) {
      return ch >= '0' && ch <= '9';
    }

    private FormatToken parseArgumentIndexAndFlags(FormatToken token) {
      // Parse the argument index, if there is one.
      int position = i;
      int ch = peek();
      if (isDigit(ch)) {
        int number = nextInt();
        if (peek() == '$') {
          // The number was an argument index.
          advance(); // Swallow the '$'.
          if (number == FormatToken.UNSET) {
            throw new MissingFormatArgumentException(getFormatSpecifierText());
          }
          // k$ stands for the argument whose index is k-1 except that
          // 0$ and 1$ both stand for the first element.
          token.setArgIndex(Math.max(0, number - 1));
        } else {
          if (ch == '0') {
            // The digit zero is a format flag, so reparse it as such.
            i = position;
          } else {
            // The number was a width. This means there are no flags to parse.
            return parseWidth(token, number);
          }
        }
      } else if (ch == '<') {
        token.setArgIndex(FormatToken.LAST_ARGUMENT_INDEX);
        advance();
      }

      // Parse the flags.
      while (token.setFlag(peek())) {
        advance();
      }

      // What comes next?
      ch = peek();
      if (isDigit(ch)) {
        return parseWidth(token, nextInt());
      } else if (ch == '.') {
        return parsePrecision(token);
      } else {
        return parseConversionType(token);
      }
    }

    // We pass the width in because in some cases we've already parsed it.
    // (Because of the ambiguity between argument indexes and widths.)
    private FormatToken parseWidth(FormatToken token, int width) {
      token.setWidth(width);
      int ch = peek();
      if (ch == '.') {
        return parsePrecision(token);
      } else {
        return parseConversionType(token);
      }
    }

    private FormatToken parsePrecision(FormatToken token) {
      advance(); // Swallow the '.'.
      int ch = peek();
      if (isDigit(ch)) {
        token.setPrecision(nextInt());
        return parseConversionType(token);
      } else {
        // The precision is required but not given by the format string.
        throw unknownFormatConversionException();
      }
    }

    private FormatToken parseConversionType(FormatToken token) {
      char conversionType = advance(); // A conversion type is mandatory.
      token.setConversionType(conversionType);
      if (conversionType == 't' || conversionType == 'T') {
        char dateSuffix = advance(); // A date suffix is mandatory for 't' or 'T'.
        token.setDateSuffix(dateSuffix);
      }
      return token;
    }

    // Parses an integer (of arbitrary length, but typically just one digit).
    private int nextInt() {
      long value = 0;
      while (i < length && isDigit(format.charAt(i))) {
        value = 10 * value + (format.charAt(i++) - '0');
        if (value > Integer.MAX_VALUE) {
          return failNextInt();
        }
      }
      return (int) value;
    }

    // Swallow remaining digits to resync our attempted parse, but return failure.
    private int failNextInt() {
      while (isDigit(peek())) {
        advance();
      }
      return FormatToken.UNSET;
    }
  }
}
