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


// 7.5 Tokens
public enum TokenType {
  END_OF_FILE("End of File"),
  ERROR("error"),

  // 7.6 Identifier Names and Identifiers
  IDENTIFIER("identifier"),

  // 7.6.1.1 keywords
  BREAK,
  CASE,
  CATCH,
  CONTINUE,
  DEBUGGER,
  DEFAULT,
  DELETE,
  DO,
  ELSE,
  FINALLY,
  FOR,
  FUNCTION,
  IF,
  IN,
  INSTANCEOF,
  NEW,
  RETURN,
  SWITCH,
  THIS,
  THROW,
  TRY,
  TYPEOF,
  VAR,
  VOID,
  WHILE,
  WITH,

  // 7.6.1.2 Future reserved words
  CLASS,
  CONST,
  ENUM,
  EXPORT,
  EXTENDS,
  IMPORT,
  SUPER,

  // Future reserved words in strict mode
  IMPLEMENTS,
  INTERFACE,
  LET,
  PACKAGE,
  PRIVATE,
  PROTECTED,
  PUBLIC,
  STATIC,
  YIELD,

  // 7.7 Punctuators
  OPEN_CURLY("{"),
  CLOSE_CURLY("}"),
  OPEN_PAREN("("),
  CLOSE_PAREN(")"),
  OPEN_SQUARE("["),
  CLOSE_SQUARE("]"),
  PERIOD("."),
  SEMI_COLON(";"),
  COMMA(","),
  OPEN_ANGLE("<"),
  CLOSE_ANGLE(">"),
  LESS_EQUAL("<="),
  GREATER_EQUAL(">="),
  ARROW("=>"),
  EQUAL_EQUAL("=="),
  NOT_EQUAL("!="),
  EQUAL_EQUAL_EQUAL("==="),
  NOT_EQUAL_EQUAL("!=="),
  PLUS("+"),
  MINUS("-"),
  STAR("*"),
  PERCENT("%"),
  PLUS_PLUS("++"),
  MINUS_MINUS("--"),
  LEFT_SHIFT("<<"),
  RIGHT_SHIFT(">>"),
  UNSIGNED_RIGHT_SHIFT(">>>"),
  AMPERSAND("&"),
  BAR("|"),
  CARET("^"),
  BANG("!"),
  TILDE("~"),
  AND("&&"),
  OR("||"),
  QUESTION("?"),
  COLON(":"),
  EQUAL("="),
  PLUS_EQUAL("+="),
  MINUS_EQUAL("-="),
  STAR_EQUAL("*="),
  PERCENT_EQUAL("%="),
  LEFT_SHIFT_EQUAL("<<="),
  RIGHT_SHIFT_EQUAL(">>="),
  UNSIGNED_RIGHT_SHIFT_EQUAL(">>>="),
  AMPERSAND_EQUAL("&="),
  BAR_EQUAL("|="),
  CARET_EQUAL("^="),
  SLASH("/"),
  SLASH_EQUAL("/="),
  POUND("#"),

  // 7.8 Literals
  NULL,
  TRUE,
  FALSE,
  NUMBER("number literal"),
  STRING("string literal"),
  REGULAR_EXPRESSION("regular expression literal"),

  // Harmony extensions
  SPREAD("..."),
  // 12.2.9 Template Literals
  // Template literal tokens corresponding to different parts of the literal
  // Eg: `hello` is scanned as a single NO_SUBSTITUTION_TEMPLATE: hello
  // `hello${world}!` is scanned as TEMPLATE_HEAD: hello, TEMPLATE_MIDDLE: world,
  // and TEMPLATE_TAIL: !
  TEMPLATE_HEAD("template head"),
  TEMPLATE_MIDDLE("template middle"),
  TEMPLATE_TAIL("template tail"),
  NO_SUBSTITUTION_TEMPLATE("no substitution template"),
  ;

  public final String value;

  TokenType() {
    this(null);
  }

  TokenType(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    // TODO: straighten this out
    return value == null ? Keywords.get(this).toString() : value;
  }
}
