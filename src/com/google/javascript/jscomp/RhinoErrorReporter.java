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

package com.google.javascript.jscomp;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.ScriptRuntime;

import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * An error reporter for serializing Rhino errors into our error format.
 * @author nicksantos@google.com (Nick Santos)
 */
class RhinoErrorReporter {

  static final DiagnosticType PARSE_ERROR =
      DiagnosticType.error("JSC_PARSE_ERROR", "Parse error. {0}");

  static final DiagnosticType TYPE_PARSE_ERROR =
      DiagnosticType.warning("JSC_TYPE_PARSE_ERROR", "{0}");

  // Special-cased errors, so that they can be configured via the
  // warnings API.
  static final DiagnosticType TRAILING_COMMA =
      DiagnosticType.error("JSC_TRAILING_COMMA",
          "Parse error. IE8 (and below) will parse trailing commas in " +
          "array and object literals incorrectly. " +
          "If you are targeting newer versions of JS, " +
          "set the appropriate language_in option.");

  static final DiagnosticType DUPLICATE_PARAM =
      DiagnosticType.error("JSC_DUPLICATE_PARAM", "Parse error. {0}");

  static final DiagnosticType BAD_JSDOC_ANNOTATION =
      DiagnosticType.warning("JSC_BAD_JSDOC_ANNOTATION", "Parse error. {0}");

  static final DiagnosticType MISPLACED_TYPE_ANNOTATION =
      DiagnosticType.warning("JSC_MISPLACED_TYPE_ANNOTATION",
          "Type annotations are not allowed here. " +
          "Are you missing parentheses?");

  // A map of Rhino messages to their DiagnosticType.
  private final Map<Pattern, DiagnosticType> typeMap;

  final AbstractCompiler compiler;

  /**
   * For each message such as "Not a good use of {0}", replace the place
   * holder {0} with a wild card that matches all possible strings.
   * Also put the any non-place-holder in quotes for regex matching later.
   */
  private Pattern replacePlaceHolders(String s) {
    s = Pattern.quote(s);
    return Pattern.compile(s.replaceAll("\\{\\d+\\}", "\\\\E.*\\\\Q"));
  }

  private RhinoErrorReporter(AbstractCompiler compiler) {
    this.compiler = compiler;
    typeMap = ImmutableMap.of(
        // Trailing comma
        replacePlaceHolders(
            com.google.javascript.rhino.head.ScriptRuntime
              .getMessage0("msg.extra.trailing.comma")),
        TRAILING_COMMA,

        // Duplicate parameter
        replacePlaceHolders(
            com.google.javascript.rhino.head.ScriptRuntime
              .getMessage0("msg.dup.parms")),
        DUPLICATE_PARAM,

        // Unknown @annotations.
        replacePlaceHolders(ScriptRuntime.getMessage0("msg.bad.jsdoc.tag")),
        BAD_JSDOC_ANNOTATION,

        Pattern.compile("^Type annotations are not allowed here.*"),
        MISPLACED_TYPE_ANNOTATION,

        // Type annotation errors.
        Pattern.compile("^Bad type annotation.*"),
        TYPE_PARSE_ERROR
        );
  }

  public static com.google.javascript.rhino.head.ErrorReporter
      forNewRhino(AbstractCompiler compiler) {
    return new NewRhinoErrorReporter(compiler);
  }

  public static ErrorReporter forOldRhino(AbstractCompiler compiler) {
    return new OldRhinoErrorReporter(compiler);
  }

  void warningAtLine(String message, String sourceName, int line,
      int lineOffset) {
    compiler.report(
        makeError(message, sourceName, line, lineOffset, CheckLevel.WARNING));
  }

  void errorAtLine(String message, String sourceName, int line,
      int lineOffset) {
    compiler.report(
        makeError(message, sourceName, line, lineOffset, CheckLevel.ERROR));
  }

  private JSError makeError(String message, String sourceName, int line,
      int lineOffset, CheckLevel defaultLevel) {

    // Try to see if the message is one of the rhino errors we want to
    // expose as DiagnosticType by matching it with the regex key.
    for (Entry<Pattern, DiagnosticType> entry : typeMap.entrySet()) {
      if (entry.getKey().matcher(message).matches()) {
        return JSError.make(
            sourceName, line, lineOffset, entry.getValue(), message);
      }
    }

    return JSError.make(sourceName, line, lineOffset, defaultLevel,
        PARSE_ERROR, message);
  }

  private static class OldRhinoErrorReporter extends RhinoErrorReporter
      implements ErrorReporter {

    private OldRhinoErrorReporter(AbstractCompiler compiler) {
      super(compiler);
    }

    @Override
    public void error(String message, String sourceName, int line,
        int lineOffset) {
      super.errorAtLine(message, sourceName, line, lineOffset);
    }

    @Override
    public void warning(String message, String sourceName, int line,
        int lineOffset) {
      super.warningAtLine(message, sourceName, line, lineOffset);
    }
  }

  private static class NewRhinoErrorReporter extends RhinoErrorReporter
      implements com.google.javascript.rhino.head.ast.IdeErrorReporter {

    private NewRhinoErrorReporter(AbstractCompiler compiler) {
      super(compiler);
    }

    @Override
    public com.google.javascript.rhino.head.EvaluatorException
        runtimeError(String message, String sourceName, int line,
            String lineSource, int lineOffset) {
      return new com.google.javascript.rhino.head.EvaluatorException(
          message, sourceName, line, lineSource, lineOffset);
    }

    @Override
    public void error(String message, String sourceName, int line,
        String sourceLine, int lineOffset) {
      super.errorAtLine(message, sourceName, line, lineOffset);
    }

    @Override
    public void error(String message, String sourceName,
        int offset, int length) {
      int line = 1;
      int column = 0;
      SourceFile file = this.compiler.getSourceFileByName(sourceName);
      if (file != null) {
        line = file.getLineOfOffset(offset);
        column = file.getColumnOfOffset(offset);
      }
      super.errorAtLine(message, sourceName, line, column);
    }

    @Override
    public void warning(String message, String sourceName, int line,
        String sourceLine, int lineOffset) {
      super.warningAtLine(message, sourceName, line, lineOffset);
    }

    @Override
    public void warning(String message, String sourceName,
        int offset, int length) {
      int line = 1;
      int column = 0;
      SourceFile file = this.compiler.getSourceFileByName(sourceName);
      if (file != null) {
        line = file.getLineOfOffset(offset);
        column = file.getColumnOfOffset(offset);
      }
      super.errorAtLine(message, sourceName, line, column);
    }
  }
}
