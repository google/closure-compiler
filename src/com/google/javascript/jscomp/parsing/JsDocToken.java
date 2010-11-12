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

/**
 * JSDoc-specific tokens.
 *
 * This class is based on Rhino's Token.
 *
 */
enum JsDocToken {
  // Tokens recycled from Rhino
  EOF,  // end of file token - (not EOF_CHAR)
  EOL,  // end of line
  LT,
  GT,
  STRING,
  LB,  // left and right brackets
  RB,
  LC,  // left and right curlies (braces)
  RC,
  LP,  // left and right parentheses
  RP,
  COMMA,  // comma operator
  COLON,
  // JsDoc-only tokens
  ANNOTATION,
  PIPE,
  STAR,
  EOC,
  QMARK,
  ELLIPSIS,
  BANG,
  EQUALS;
}
