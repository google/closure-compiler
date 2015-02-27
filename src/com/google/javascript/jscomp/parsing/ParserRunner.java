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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.Parser;
import com.google.javascript.jscomp.parsing.parser.Parser.Config.Mode;
import com.google.javascript.jscomp.parsing.parser.SourceFile;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.parsing.parser.trees.ProgramTree;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;

import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

/** parser runner */
public class ParserRunner {

  private static final String CONFIG_RESOURCE =
      "com.google.javascript.jscomp.parsing.ParserConfig";

  private static Set<String> annotationNames = null;

  private static Set<String> suppressionNames = null;
  private static Set<String> reservedVars = null;

  // Should never need to instantiate class of static methods.
  private ParserRunner() {}

  public static Config createConfig(boolean isIdeMode,
                                    LanguageMode languageMode,
                                    boolean acceptConstKeyword,
                                    Set<String> extraAnnotationNames) {
    initResourceConfig();
    Set<String> effectiveAnnotationNames;
    if (extraAnnotationNames == null) {
      effectiveAnnotationNames = annotationNames;
    } else {
      effectiveAnnotationNames = new HashSet<>(annotationNames);
      effectiveAnnotationNames.addAll(extraAnnotationNames);
    }
    return new Config(effectiveAnnotationNames, suppressionNames,
        isIdeMode, languageMode, acceptConstKeyword);
  }

  public static Set<String> getReservedVars() {
    initResourceConfig();
    return reservedVars;
  }

  private static synchronized void initResourceConfig() {
    if (annotationNames != null) {
      return;
    }

    ResourceBundle config = ResourceBundle.getBundle(CONFIG_RESOURCE);
    annotationNames = extractList(config.getString("jsdoc.annotations"));
    suppressionNames = extractList(config.getString("jsdoc.suppressions"));
    reservedVars = extractList(config.getString("compiler.reserved.vars"));
  }

  private static Set<String> extractList(String configProp) {
    return ImmutableSet.copyOf(Splitter.on(',').trimResults().split(configProp));
  }

  public static ParseResult parse(
      StaticSourceFile sourceFile,
      String sourceString,
      Config config,
      ErrorReporter errorReporter) {
    // TODO(johnlenz): unify "SourceFile", "Es6ErrorReporter" and "Config"
    SourceFile file = new SourceFile(sourceFile.getName(), sourceString);
    Es6ErrorReporter es6ErrorReporter =
        new Es6ErrorReporter(errorReporter, file, config);
    com.google.javascript.jscomp.parsing.parser.Parser.Config es6config =
        new com.google.javascript.jscomp.parsing.parser.Parser.Config(mode(
            config.languageMode));
    Parser p = new Parser(es6config, es6ErrorReporter, file);
    ProgramTree tree = p.parseProgram();
    Node root = null;
    List<Comment> comments = ImmutableList.of();
    if (tree != null && (!es6ErrorReporter.hadError() || config.isIdeMode)) {
      root = IRFactory.transformTree(
          tree, sourceFile, sourceString, config, errorReporter);
      root.setIsSyntheticBlock(true);

      if (config.isIdeMode) {
        comments = p.getComments();
      }
    }
    return new ParseResult(root, comments);
  }

  private static class Es6ErrorReporter
      extends com.google.javascript.jscomp.parsing.parser.util.ErrorReporter {
    private ErrorReporter reporter;
    private boolean errorSeen = false;
    private boolean isIdeMode;

    Es6ErrorReporter(
        ErrorReporter reporter,
        SourceFile source,
        Config config) {
      this.reporter = reporter;
      this.isIdeMode = config.isIdeMode;
    }

    @Override
    protected void reportMessage(
        SourcePosition location, String kind, String format,
        Object... arguments) {
      String message = SimpleFormat.format("%s",
          SimpleFormat.format(format, arguments));
      switch (kind) {
        case "Error":
          if (isIdeMode || !errorSeen) {
            errorSeen = true;
            this.reporter.error(
                message, location.source.name,
                location.line + 1, location.column);
          }
          break;
        case "Warning":
          this.reporter.warning(
              message, location.source.name,
              location.line + 1, location.column);
          break;
        default:
          throw new IllegalStateException("Unexpected:" + kind);
      }
    }

    @Override
    protected void reportMessage(SourcePosition location, String message) {
      throw new IllegalStateException("Not called directly");
    }
  }

  private static Mode mode(LanguageMode mode) {
    switch (mode) {
      case ECMASCRIPT3:
        return Mode.ES3;
      case ECMASCRIPT5:
        return Mode.ES5;
      case ECMASCRIPT5_STRICT:
        return Mode.ES5_STRICT;
      case ECMASCRIPT6:
        return Mode.ES6;
      case ECMASCRIPT6_STRICT:
        return Mode.ES6_STRICT;
      case ECMASCRIPT6_TYPED:
        return Mode.ES6_TYPED;
      default:
        throw new IllegalStateException("unexpected language mode: " + mode);
    }
  }

  /**
   * Holds results of parsing.
   */
  public static class ParseResult {
    public final Node ast;
    public final List<Comment> comments;

    public ParseResult(Node ast, List<Comment> comments) {
      this.ast = ast;
      this.comments = comments;
    }
  }
}
