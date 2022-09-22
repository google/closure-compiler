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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.Config.RunMode;
import com.google.javascript.jscomp.parsing.Config.StrictMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.Parser;
import com.google.javascript.jscomp.parsing.parser.Parser.Config.Mode;
import com.google.javascript.jscomp.parsing.parser.SourceFile;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.parsing.parser.trees.ProgramTree;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/** parser runner */
public final class ParserRunner {

  private static @Nullable ImmutableSet<String> annotationNames = null;
  private static @Nullable ImmutableSet<String> suppressionNames = null;
  private static @Nullable ImmutableSet<String> reservedVars = null;
  private static @Nullable ImmutableSet<String> closurePrimitiveNames = null;

  // Should never need to instantiate class of static methods.
  private ParserRunner() {}

  public static Config createConfig(
      LanguageMode languageMode, Set<String> extraAnnotationNames, StrictMode strictMode) {
    return createConfig(
        languageMode,
        JsDocParsing.TYPES_ONLY,
        RunMode.STOP_AFTER_ERROR,
        extraAnnotationNames,
        true,
        strictMode);
  }

  public static Config createConfig(
      LanguageMode languageMode,
      JsDocParsing jsdocParsingMode,
      RunMode runMode,
      Set<String> extraAnnotationNames,
      boolean parseInlineSourceMaps,
      StrictMode strictMode) {

    initResourceConfig();
    Set<String> effectiveAnnotationNames;
    if (extraAnnotationNames == null) {
      effectiveAnnotationNames = annotationNames;
    } else {
      effectiveAnnotationNames = new HashSet<>(annotationNames);
      effectiveAnnotationNames.addAll(extraAnnotationNames);
    }
    return Config.builder()
        .setExtraAnnotationNames(effectiveAnnotationNames)
        .setJsDocParsingMode(jsdocParsingMode)
        .setRunMode(runMode)
        .setSuppressionNames(suppressionNames)
        .setClosurePrimitiveNames(closurePrimitiveNames)
        .setLanguageMode(languageMode)
        .setParseInlineSourceMaps(parseInlineSourceMaps)
        .setStrictMode(strictMode)
        .build();
  }

  public static ImmutableSet<String> getReservedVars() {
    initResourceConfig();
    return reservedVars;
  }

  public static ImmutableSet<String> getSuppressionNames() {
    initResourceConfig();
    return suppressionNames;
  }

  private static synchronized void initResourceConfig() {
    if (annotationNames != null) {
      return;
    }

    annotationNames = extractList(ParserConfiguration.getString("jsdoc.annotations"));
    suppressionNames = extractList(ParserConfiguration.getString("jsdoc.suppressions"));
    closurePrimitiveNames = extractList(ParserConfiguration.getString("jsdoc.primitives"));
    reservedVars = extractList(ParserConfiguration.getString("compiler.reserved.vars"));
  }

  private static ImmutableSet<String> extractList(String configProp) {
    return ImmutableSet.copyOf(Splitter.on(',').trimResults().split(configProp));
  }

  public static ParseResult parse(
      StaticSourceFile sourceFile,
      String sourceString,
      Config config,
      ErrorReporter errorReporter) {
    // TODO(johnlenz): unify "SourceFile", "Es6ErrorReporter" and "Config"

    String sourceName = sourceFile.getName();
    try {
      SourceFile file = new SourceFile(sourceName, sourceString);
      boolean keepGoing = config.runMode() == RunMode.KEEP_GOING;
      Es6ErrorReporter es6ErrorReporter = new Es6ErrorReporter(errorReporter, keepGoing);
      com.google.javascript.jscomp.parsing.parser.Parser.Config es6config = newParserConfig(config);
      Parser p = new Parser(es6config, es6ErrorReporter, file);
      ProgramTree tree = p.parseProgram();
      Node root = null;
      List<Comment> comments = ImmutableList.of();
      FeatureSet features = p.getFeatures();
      if (tree != null && (!es6ErrorReporter.hadError() || keepGoing)) {
        IRFactory factory = IRFactory.transformTree(tree, sourceFile, config, errorReporter, file);
        root = factory.getResultNode();
        features = features.union(factory.getFeatures());
        root.putProp(Node.FEATURE_SET, features);

        if (config.jsDocParsingMode().shouldParseDescriptions()) {
          comments = p.getComments();
        }
      }
      return new ParseResult(root, comments, features, p.getSourceMapURL());
    } catch (Throwable t) {
      throw new RuntimeException("Exception parsing \"" + sourceName + "\"", t);
    }
  }

  private static com.google.javascript.jscomp.parsing.parser.Parser.Config newParserConfig(
      Config config) {
    LanguageMode languageMode = config.languageMode();
    boolean isStrictMode = config.strictMode().isStrict();
    Mode parserConfigLanguageMode = null;
    switch (languageMode) {
      case ECMASCRIPT3:
        parserConfigLanguageMode = Mode.ES3;
        break;

      case ECMASCRIPT5:
        parserConfigLanguageMode = Mode.ES5;
        break;

      case ECMASCRIPT_2015:
      case ECMASCRIPT_2016:
        parserConfigLanguageMode = Mode.ES6_OR_ES7;
        break;
      case ECMASCRIPT_2017:
      case ECMASCRIPT_2018:
      case ECMASCRIPT_2019:
      case ECMASCRIPT_2020:
      case ECMASCRIPT_2021:
      case ES_NEXT:
      case UNSTABLE:
      case UNSUPPORTED:
        parserConfigLanguageMode = Mode.ES8_OR_GREATER;
        break;
    }
    return new com.google.javascript.jscomp.parsing.parser.Parser.Config(
        checkNotNull(parserConfigLanguageMode), isStrictMode);
  }

  private static class Es6ErrorReporter
      extends com.google.javascript.jscomp.parsing.parser.util.ErrorReporter {
    private final ErrorReporter reporter;
    private boolean errorSeen = false;
    private final boolean reportAllErrors;

    Es6ErrorReporter(
        ErrorReporter reporter,
        boolean reportAllErrors) {
      this.reporter = reporter;
      this.reportAllErrors = reportAllErrors;
    }

    @Override
    protected void reportError(SourcePosition location, String message) {
      // In normal usage, only the first parse error should be reported, but
      // sometimes it is useful to keep going.
      if (reportAllErrors || !errorSeen) {
        errorSeen = true;
        this.reporter.error(
            message, location.source.name,
            location.line + 1, location.column);
      }
    }

    @Override
    protected void reportWarning(SourcePosition location, String message) {
      this.reporter.warning(
          message, location.source.name,
          location.line + 1, location.column);
    }
  }

  /**
   * Holds results of parsing.
   */
  public static class ParseResult {
    public final Node ast;
    public final List<Comment> comments;
    public final FeatureSet features;
    public final @Nullable String sourceMapURL;

    public ParseResult(Node ast, List<Comment> comments, FeatureSet features, String sourceMapURL) {
      this.ast = ast;
      this.comments = comments;
      this.features = features;
      this.sourceMapURL = sourceMapURL;
    }
  }
}
