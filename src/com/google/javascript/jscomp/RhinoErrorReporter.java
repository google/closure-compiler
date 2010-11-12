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
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.EvaluatorException;
import com.google.javascript.rhino.ScriptRuntime;
import com.google.javascript.jscomp.CheckLevel;

import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * An error reporter for serizalizing Rhino errors into our error format.
 * @author nicksantos@google.com (Nick Santos)
 */
class RhinoErrorReporter {

  static final DiagnosticType PARSE_ERROR =
      DiagnosticType.error("JSC_PARSE_ERROR", "Parse error. {0}");

  // A special-cased error, so that it can be configured via the
  // warnings API.
  static final DiagnosticType EXTRA_FILEOVERVIEW =
      DiagnosticType.warning("JSC_EXTRA_FILEOVERVIEW", "Parse error. {0}");

  static final DiagnosticType TRAILING_COMMA =
      DiagnosticType.error("JSC_TRAILING_COMMA",
          "Parse error. Internet Explorer has a non-standard " +
          "intepretation of trailing commas. Arrays will have the wrong " +
          "length and objects will not parse at all.");

  static final DiagnosticType DUPLICATE_PARAM =
      DiagnosticType.error("JSC_DUPLICATE_PARAM", "Parse error. {0}");

  static final DiagnosticType BAD_JSDOC_ANNOTATION =
    DiagnosticType.warning("JSC_BAD_JSDOC_ANNOTATION", "Parse error. {0}");

  // A map of Rhino messages to their DiagnosticType.
  private final Map<String, DiagnosticType> typeMap;

  private final AbstractCompiler compiler;

  /**
   * For each message such as "Not a good use of {0}", replace the place
   * holder {0} with a wild card that matches all possible strings.
   * Also put the any non-place-holder in quotes for regex matching later.
   */
  private String replacePlaceHolders(String s) {
    s = Pattern.quote(s);
    return s.replaceAll("\\{\\d+\\}", "\\\\E.*\\\\Q");
  }

  private RhinoErrorReporter(AbstractCompiler compiler) {
    this.compiler = compiler;
    typeMap = ImmutableMap.of(

        // Extra @fileoverview
        replacePlaceHolders(
            ScriptRuntime.getMessage0("msg.jsdoc.fileoverview.extra")),
        EXTRA_FILEOVERVIEW,

        // Trailing comma
        replacePlaceHolders(
            com.google.javascript.jscomp.mozilla.rhino.ScriptRuntime
              .getMessage0("msg.extra.trailing.comma")),
        TRAILING_COMMA,

        // Duplicate parameter
        replacePlaceHolders(
            com.google.javascript.jscomp.mozilla.rhino.ScriptRuntime
              .getMessage0("msg.dup.parms")),
        DUPLICATE_PARAM,

        // Unknown @annotations.
        replacePlaceHolders(ScriptRuntime.getMessage0("msg.bad.jsdoc.tag")),
        BAD_JSDOC_ANNOTATION);
  }

  public static com.google.javascript.jscomp.mozilla.rhino.ErrorReporter
      forNewRhino(AbstractCompiler compiler) {
    return new NewRhinoErrorReporter(compiler);
  }

  public static ErrorReporter forOldRhino(AbstractCompiler compiler) {
    return new OldRhinoErrorReporter(compiler);
  }

  public void warning(String message, String sourceName, int line,
      String lineSource, int lineOffset) {
    compiler.report(
        makeError(message, sourceName, line, lineOffset, CheckLevel.WARNING));
  }

  public void error(String message, String sourceName, int line,
      String lineSource, int lineOffset) {
    compiler.report(
        makeError(message, sourceName, line, lineOffset, CheckLevel.ERROR));
  }

  private JSError makeError(String message, String sourceName, int line,
      int lineOffset, CheckLevel defaultLevel) {

    // Try to see if the message is one of the rhino errors we want to
    // expose as DiagnosticType by matching it with the regex key.
    for (Entry<String, DiagnosticType> entry : typeMap.entrySet()) {
      if (message.matches(entry.getKey())) {
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

    public EvaluatorException runtimeError(String message, String sourceName,
        int line, String lineSource, int lineOffset) {
      return new EvaluatorException(message, sourceName, line, lineSource,
          lineOffset);
    }
  }

  private static class NewRhinoErrorReporter extends RhinoErrorReporter
      implements com.google.javascript.jscomp.mozilla.rhino.ErrorReporter {

    private NewRhinoErrorReporter(AbstractCompiler compiler) {
      super(compiler);
    }

    public com.google.javascript.jscomp.mozilla.rhino.EvaluatorException
        runtimeError(String message, String sourceName, int line,
            String lineSource, int lineOffset) {
      return new com.google.javascript.jscomp.mozilla.rhino.EvaluatorException(
          message, sourceName, line, lineSource, lineOffset);
    }
  }
}
