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

import java.util.HashMap;

/**
 * The javascript keywords.
 */
public enum Keywords {

    // 7.6.1.1 Keywords
    BREAK("break", TokenType.BREAK),
    CASE("case", TokenType.CASE),
    CATCH("catch", TokenType.CATCH),
    CONTINUE("continue", TokenType.CONTINUE),
    DEBUGGER("debugger", TokenType.DEBUGGER),
    DEFAULT("default", TokenType.DEFAULT),
    DELETE("delete", TokenType.DELETE),
    DO("do", TokenType.DO),
    ELSE("else", TokenType.ELSE),
    FINALLY("finally", TokenType.FINALLY),
    FOR("for", TokenType.FOR),
    FUNCTION("function", TokenType.FUNCTION),
    IF("if", TokenType.IF),
    IN("in", TokenType.IN),
    INSTANCEOF("instanceof", TokenType.INSTANCEOF),
    NEW("new", TokenType.NEW),
    RETURN("return", TokenType.RETURN),
    SWITCH("switch", TokenType.SWITCH),
    THIS("this", TokenType.THIS),
    THROW("throw", TokenType.THROW),
    TRY("try", TokenType.TRY),
    TYPEOF("typeof", TokenType.TYPEOF),
    VAR("var", TokenType.VAR),
    VOID("void", TokenType.VOID),
    WHILE("while", TokenType.WHILE),
    WITH("with", TokenType.WITH),

    // 7.6.1.2 Future Reserved Words
    CLASS("class", TokenType.CLASS),
    CONST("const", TokenType.CONST),
    ENUM("enum", TokenType.ENUM),
    EXPORT("export", TokenType.EXPORT),
    EXTENDS("extends", TokenType.EXTENDS),
    IMPORT("import", TokenType.IMPORT),
    SUPER("super", TokenType.SUPER),

    // Future Reserved Words in a strict context
    IMPLEMENTS("implements", TokenType.IMPLEMENTS),
    INTERFACE("interface", TokenType.INTERFACE),
    LET("let", TokenType.LET),
    PACKAGE("package", TokenType.PACKAGE),
    PRIVATE("private", TokenType.PRIVATE),
    PROTECTED("protected", TokenType.PROTECTED),
    PUBLIC("public", TokenType.PUBLIC),
    STATIC("static", TokenType.STATIC),
    YIELD("yield", TokenType.YIELD),

    //7.8 Literals
    NULL("null", TokenType.NULL),
    TRUE("true", TokenType.TRUE),
    FALSE("false", TokenType.FALSE),

    // Parkour Specific
    AWAIT("await", TokenType.AWAIT);

  private static final HashMap<String, Keywords> keywordsByName =
      new HashMap<String, Keywords>();
  private static final HashMap<TokenType, Keywords> keywordsByType =
      new HashMap<TokenType, Keywords>();

  static {
    for (Keywords kw : Keywords.values()) {
      keywordsByName.put(kw.value, kw);
      keywordsByType.put(kw.type, kw);
    }
  }

  public final String value;
  public final TokenType type;

  Keywords(String value, TokenType type) {
    this.value = value;
    this.type = type;
  }

  @Override
  public String toString() {
    return value;
  }

  public static boolean isKeyword(String value) {
    return get(value) != null;
  }

  public static TokenType getTokenType(String value) {
    return keywordsByName.get(value).type;
  }

  public static Keywords get(String value) {
    return keywordsByName.get(value);
  }

  public static Keywords get(TokenType token) {
    return keywordsByType.get(token);
  }
}
