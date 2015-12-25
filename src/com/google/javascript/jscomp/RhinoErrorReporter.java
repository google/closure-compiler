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
import com.google.javascript.rhino.SimpleErrorReporter;

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

  // This is separate from TYPE_PARSE_ERROR because there are many instances of this warning
  // and it is unfeasible to fix them all right away.
  static final DiagnosticType JSDOC_MISSING_BRACES_WARNING =
      DiagnosticType.disabled("JSC_JSDOC_MISSING_BRACES_WARNING", "{0}");

  // This is separate from TYPE_PARSE_ERROR because there are many instances of this warning
  // and it is unfeasible to fix them all right away.
  static final DiagnosticType JSDOC_MISSING_TYPE_WARNING =
      DiagnosticType.disabled("JSC_JSDOC_MISSING_TYPE_WARNING", "{0}");

  static final DiagnosticType TOO_MANY_TEMPLATE_PARAMS =
      DiagnosticType.disabled("JSC_TOO_MANY_TEMPLATE_PARAMS", "{0}");

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

  static final DiagnosticType INVALID_PARAM =
      DiagnosticType.warning("JSC_INVALID_PARAM", "Parse error. {0}");

  static final DiagnosticType BAD_JSDOC_ANNOTATION =
      DiagnosticType.warning("JSC_BAD_JSDOC_ANNOTATION", "Parse error. {0}");

  static final DiagnosticType JSDOC_IN_BLOCK_COMMENT =
      DiagnosticType.warning("JSC_JSDOC_IN_BLOCK_COMMENT", "Parse error. {0}");

  static final DiagnosticType INVALID_ES3_PROP_NAME = DiagnosticType.warning(
      "JSC_INVALID_ES3_PROP_NAME",
      "Keywords and reserved words are not allowed as unquoted property " +
      "names in older versions of JavaScript. " +
      "If you are targeting newer versions of JavaScript, " +
      "set the appropriate language_in option.");

  static final DiagnosticType PARSE_TREE_TOO_DEEP =
      DiagnosticType.error("PARSE_TREE_TOO_DEEP",
          "Parse tree too deep.");

  static final DiagnosticType INVALID_OCTAL_LITERAL =
      DiagnosticType.warning("INVALID_OCTAL_LITERAL",
          "This style of octal literal is not supported in strict mode.");

  static final DiagnosticType ES6_FEATURE =
      DiagnosticType.error("ES6_FEATURE",
          "{0}. Use --language_in=ECMASCRIPT6 or ECMASCRIPT6_STRICT " +
          "to enable ES6 features.");

  static final DiagnosticType ES6_TYPED =
      DiagnosticType.error("ES6_TYPED",
          "{0}. Use --language_in=ECMASCRIPT6_TYPED " +
          "to enable ES6 typed features.");

  static final DiagnosticType MISPLACED_TYPE_SYNTAX =
      DiagnosticType.error("MISPLACED_TYPE_SYNTAX",
          "Can only have JSDoc or inline type annotations, not both");

  // A map of Rhino messages to their DiagnosticType.
  private final Map<Pattern, DiagnosticType> typeMap;

  final AbstractCompiler compiler;

  /**
   * For each message such as "Not a good use of {0}", replace the place
   * holder {0} with a wild card that matches all possible strings.
   * Also put the any non-place-holder in quotes for regex matching later.
   */
  private static Pattern replacePlaceHolders(String s) {
    s = Pattern.quote(s);
    return Pattern.compile(s.replaceAll("\\{\\d+\\}", "\\\\E.*\\\\Q"));
  }

  private RhinoErrorReporter(AbstractCompiler compiler) {
    this.compiler = compiler;
    typeMap =
        ImmutableMap.<Pattern, DiagnosticType>builder()
            // Trailing comma
            .put(
                replacePlaceHolders(
                    "Trailing comma is not legal in an ECMA-262 object initializer"),
                TRAILING_COMMA)

            // Duplicate parameter
            .put(replacePlaceHolders("Duplicate parameter name \"{0}\""), DUPLICATE_PARAM)

            .put(Pattern.compile("^invalid param name.*"), INVALID_PARAM)

            // Unknown @annotations.
            .put(
                replacePlaceHolders(SimpleErrorReporter.getMessage0("msg.bad.jsdoc.tag")),
                BAD_JSDOC_ANNOTATION)

            .put(
                Pattern.compile("^" + Pattern.quote(
                    "Non-JSDoc comment has annotations. "
                        + "Did you mean to start it with '/**'?")),
                JSDOC_IN_BLOCK_COMMENT)

            .put(
                Pattern.compile(
                    "^Keywords and reserved words" + " are not allowed as unquoted property.*"),
                INVALID_ES3_PROP_NAME)

            .put(Pattern.compile("^Too many template parameters"), TOO_MANY_TEMPLATE_PARAMS)

            // Type annotation warnings.
            .put(
                replacePlaceHolders(
                    "Bad type annotation. Type annotations should have curly braces."),
                JSDOC_MISSING_BRACES_WARNING)

            .put(replacePlaceHolders("Missing type declaration."), JSDOC_MISSING_TYPE_WARNING)

            // Type annotation errors.
            .put(
                Pattern.compile(
                    "^Bad type annotation.*(?!Type annotations should have curly braces\\.)"),
                TYPE_PARSE_ERROR)

            // Parse tree too deep.
            .put(replacePlaceHolders("Too deep recursion while parsing"), PARSE_TREE_TOO_DEEP)

            // Octal literals
            .put(Pattern.compile("^Octal .*literal.*"), INVALID_OCTAL_LITERAL)

            .put(
                Pattern.compile("^this language feature is only supported in es6 mode.*"),
                ES6_FEATURE)

            .put(Pattern.compile("^type syntax is only supported in ES6 typed mode.*"), ES6_TYPED)

            .put(Pattern.compile("^Can only have JSDoc or inline type.*"), MISPLACED_TYPE_SYNTAX)

            .build();
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

  protected DiagnosticType mapError(String message) {
    for (Entry<Pattern, DiagnosticType> entry : typeMap.entrySet()) {
      if (entry.getKey().matcher(message).matches()) {
        return entry.getValue();
      }
    }
    return null;
  }

  private JSError makeError(String message, String sourceName, int line,
      int lineOffset, CheckLevel defaultLevel) {

    // Try to see if the message is one of the rhino errors we want to
    // expose as DiagnosticType by matching it with the regex key.
    DiagnosticType type = mapError(message);
    if (type != null) {
      return JSError.make(
          sourceName, line, lineOffset, type, message);
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
}
