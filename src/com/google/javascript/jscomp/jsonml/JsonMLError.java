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

package com.google.javascript.jscomp.jsonml;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;

/**
 * Class used to represent errors which correspond to JsonML elements.
 *
 * @author dhans@google.com (Daniel Hans)
 */
public class JsonMLError {

  /**  */
  private final DiagnosticType type;

  /** Description of the error */
  public final String description;

  /** Name of the source */
  public final String sourceName;

  /** Node where the warning occurred. */
  public final JsonML element;

  /** Line number of the source */
  public final int lineNumber;

  /** Level */
  public final ErrorLevel level;

  private JsonMLError(DiagnosticType type, String sourceName, JsonML element,
      int lineNumber, ErrorLevel level, String... arguments) {
    this.type = type;
    this.description = type.format.format(arguments);
    this.sourceName = sourceName;
    this.element = element;
    this.lineNumber = lineNumber;
    this.level = level;
  }

  private JsonMLError(String description, DiagnosticType type,
      String sourceName, JsonML element, int lineNumber, ErrorLevel level) {
    this.type = type;
    this.description = description;
    this.sourceName = sourceName;
    this.element = element;
    this.lineNumber = lineNumber;
    this.level = level;
  }

  public static JsonMLError make(DiagnosticType type, String sourceName,
      JsonML element, int lineNumber, ErrorLevel level, String... arguments) {
    return new JsonMLError(type, sourceName, element, lineNumber, level,
        arguments);
  }

  public static JsonMLError make(JSError error, JsonMLAst ast) {
    // try to find the corresponding JsonML element
    // it is stored as line number of the JSError
    int n = error.lineNumber;
    JsonML element = ast.getElementPreOrder(n);

    ErrorLevel level = error.level == CheckLevel.ERROR
        ? ErrorLevel.COMPILATION_ERROR
        : ErrorLevel.COMPILATION_WARNING;

    return new JsonMLError(error.getType(), error.sourceName, element, 0,
        level, error.description);
  }
}
