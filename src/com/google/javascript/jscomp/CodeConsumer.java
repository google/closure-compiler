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

import com.google.javascript.rhino.Node;

/**
 * Abstracted consumer of the CodeGenerator output.
 *
 * @see CodeGenerator
 * @see CodePrinter
 * @see InlineCostEstimator
 */
abstract class CodeConsumer {
  boolean statementNeedsEnded = false;
  boolean statementStarted = false;
  boolean sawFunction = false;

  /**
   * Starts the source mapping for the given
   * node at the current position.
   */
  void startSourceMapping(Node node) {
  }

  /**
   * Finishes the source mapping for the given
   * node at the current position.
   */
  void endSourceMapping(Node node) {
  }

  /**
   * Provides a means of interrupting the CodeGenerator. Derived classes should
   * return false to stop further processing.
   */
  boolean continueProcessing() {
    return true;
  }

  /**
   * Retrieve the last character of the last string sent to append.
   */
  abstract char getLastChar();

  void addIdentifier(String identifier) {
    add(identifier);
  }

  /**
   * Appends a string to the code, keeping track of the current line length.
   *
   * NOTE: the string must be a complete token--partial strings or
   * partial regexes will run the risk of being split across lines.
   *
   * Do not directly append newlines with this method. Instead use
   * {@link #startNewLine}.
   */
  abstract void append(String str);

  void appendBlockStart() {
    append("{");
  }

  void appendBlockEnd() {
    append("}");
  }

  void startNewLine() {
  }

  void maybeLineBreak() {
    maybeCutLine();
  }

  void maybeCutLine() {
  }

  void endLine() {
  }

  void notePreferredLineBreak() {
  }

  void beginBlock() {
    if (statementNeedsEnded) {
      append(";");
      maybeLineBreak();
    }
    appendBlockStart();

    endLine();
    statementNeedsEnded = false;
  }

  void endBlock() {
    endBlock(false);
  }

  void endBlock(boolean shouldEndLine) {
    appendBlockEnd();
    if (shouldEndLine) {
      endLine();
    }
    statementNeedsEnded = false;
  }

  void listSeparator() {
    add(",");
    maybeLineBreak();
  }

  /**
   * Indicates the end of a statement and a ';' may need to be added.
   * But we don't add it now, in case we're at the end of a block (in which
   * case we don't have to add the ';').
   * See maybeEndStatement()
   */
  void endStatement() {
    endStatement(false);
  }

  void endStatement(boolean needSemiColon) {
    if (needSemiColon) {
      append(";");
      maybeLineBreak();
      statementNeedsEnded = false;
    } else if (statementStarted) {
      statementNeedsEnded = true;
    }
  }

  /**
   * This is to be called when we're in a statement. If the prev statement
   * needs to be ended, add a ';'.
   */
  void maybeEndStatement() {
    // Add a ';' if we need to.
    if (statementNeedsEnded) {
      append(";");
      maybeLineBreak();
      endLine();
      statementNeedsEnded = false;
    }
    statementStarted = true;
  }

  void endFunction() {
    endFunction(false);
  }

  void endFunction(boolean statementContext) {
    sawFunction = true;
    if (statementContext) {
      endLine();
    }
  }

  void endClass(boolean statementContext) {
    if (statementContext) {
      endLine();
    }
  }

  void beginCaseBody() {
    append(":");
  }

  void endCaseBody() {
  }

  void add(String newcode) {
    maybeEndStatement();

    if (newcode.isEmpty()) {
      return;
    }

    char c = newcode.charAt(0);
    if ((isWordChar(c) || c == '\\') &&
        isWordChar(getLastChar())) {
      // need space to separate. This is not pretty printing.
      // For example: "return foo;"
      append(" ");
    } else if (c == '/' && getLastChar() == '/') {
      // Do not allow a forward slash to appear after a DIV.
      // For example,
      // REGEXP DIV REGEXP
      // is valid and should print like
      // / // / /
      append(" ");
    } else if (c == '"' && isWordChar(getLastChar())) {
      maybeInsertSpace();
    }

    append(newcode);
  }

  void appendOp(String op, boolean binOp) {
    append(op);
  }

  void addOp(String op, boolean binOp) {
    maybeEndStatement();

    char first = op.charAt(0);
    char prev = getLastChar();

    if ((first == '+' || first == '-') && prev == first) {
      // This is not pretty printing. This is to prevent misparsing of
      // things like "x + ++y" or "x++ + ++y"
      append(" ");
    } else if (Character.isLetter(first) &&
               isWordChar(prev)) {
      // Make sure there is a space after e.g. instanceof , typeof
      append(" ");
    } else if (prev == '-' && first == '>' || prev == '<' && first == '!') {
      // Make sure that we don't emit "<!--" or "-->"
      append(" ");
    }

    // Allow formatting around the operator.
    appendOp(op, binOp);

    // Line breaking after an operator is always safe. Line breaking before an
    // operator on the other hand is not. We only line break after a bin op
    // because it looks strange.
    if (binOp) {
      maybeCutLine();
    }
  }

  void addNumber(double x) {
    // This is not pretty printing. This is to prevent misparsing of x- -4 as
    // x--4 (which is a syntax error).
    char prev = getLastChar();
    boolean negativeZero = isNegativeZero(x);
    if ((x < 0 || negativeZero) && prev == '-') {
      add(" ");
    }

    // Add "-0" if the number is negative zero.
    if (negativeZero) {
      addConstant("-0");
    // If the number is not negative zero, check whether it is a mathematical
    // integer (has no fractional component).
    } else if ((long) x == x) {
      long value = (long) x;
      long mantissa = value;
      int exp = 0;
      if (Math.abs(x) >= 100) {
        while (mantissa / 10 * Math.pow(10, exp + 1) == value) {
          mantissa /= 10;
          exp++;
        }
      }
      if (exp > 2) {
        addConstant(Long.toString(mantissa) + "E" + Integer.toString(exp));
      } else {
        long valueAbs = Math.abs(value);
        if (Long.toHexString(valueAbs).length() + 2 <
            Long.toString(valueAbs).length()) {
          addConstant((value < 0 ? "-" : "") + "0x" +
              Long.toHexString(valueAbs));
        } else {
          addConstant(Long.toString(value));
        }
      }
    // If the number has a fractional component, base the representation on the
    // result of Double.toString.
    } else {
      String xAsString = Double.toString(x);
      // The Double.toString implementation might return a string that looks
      // similar to "6.0E…". This is notation is correct in ECMAScript, but can
      // be written as "6E…" which is shorter and equally correct.
      xAsString = xAsString.replace(".0E", "E");
      // The implementation might also return a string that looks similar to
      // "0.6…". This can be written shorter and equally correct as ".6…" (see
      // ECMA-262 5.1 section 7.8.3).
      int zeroIntegerIndex = xAsString.lastIndexOf("0.", 1);
      // (The zero integer ‒ the "0" before the dot ‒ can be at the start of
      // the string as well as one character after that, but the latter is only
      // correct if that first character is a sign.)
      if (1 == zeroIntegerIndex && '-' != xAsString.charAt(0)) {
        zeroIntegerIndex = -1;
      }
      if (0 == zeroIntegerIndex) {
        // 0.6 → .6
        xAsString = xAsString.substring(1);
      } else if (1 == zeroIntegerIndex) {
        // -0.6 → -.6
        xAsString = xAsString.charAt(0) + xAsString.substring(2);
      }
      addConstant(xAsString);
    }
  }

  void addConstant(String newcode) {
    add(newcode);
  }

  static boolean isNegativeZero(double x) {
    return x == 0.0 && Math.copySign(1, x) == -1.0;
  }

  static boolean isWordChar(char ch) {
    return (ch == '_' ||
            ch == '$' ||
            Character.isLetterOrDigit(ch));
  }

  /**
   * If the body of a for loop or the then clause of an if statement has
   * a single statement, should it be wrapped in a block?  Doing so can
   * help when pretty-printing the code, and permits putting a debugging
   * breakpoint on the statement inside the condition.
   *
   * @return {@boolean true} if such expressions should be wrapped
   */
  boolean shouldPreserveExtraBlocks() {
    return false;
  }

  /**
   * Allows a consumer to insert spaces in locations where it is unnecessary
   * but may improve the readability of the code. This will be called in such
   * places as after a statement and before opening parantheses, or after the
   * end of a if block before the start of an else block.
   */
  void maybeInsertSpace() {}

  /**
   * @return Whether the a line break can be added after the specified BLOCK.
   */
  boolean breakAfterBlockFor(Node n, boolean statementContext) {
    return statementContext;
  }

  /** Called when we're at the end of a file. */
  void endFile() {}
}
