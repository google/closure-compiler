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
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import javax.annotation.Nullable;

/** parser runner */
public final class ParserRunner {

  private static final String CONFIG_RESOURCE =
      "com.google.javascript.jscomp.parsing.ParserConfig";

  private static Set<String> annotationNames = null;

  private static Set<String> suppressionNames = null;
  private static Set<String> reservedVars = null;

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
    return new Config(
        effectiveAnnotationNames,
        jsdocParsingMode,
        runMode,
        suppressionNames,
        languageMode,
        parseInlineSourceMaps,
        strictMode);
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
    boolean keepGoing = config.keepGoing == Config.RunMode.KEEP_GOING;
    Es6ErrorReporter es6ErrorReporter = new Es6ErrorReporter(errorReporter, keepGoing);
    com.google.javascript.jscomp.parsing.parser.Parser.Config es6config = newParserConfig(config);
    Parser p = new Parser(es6config, es6ErrorReporter, file);
    ProgramTree tree = p.parseProgram();
    Node root = null;
    List<Comment> comments = ImmutableList.of();
    FeatureSet features = p.getFeatures();
    if (tree != null && (!es6ErrorReporter.hadError() || keepGoing)) {
      IRFactory factory =
          IRFactory.transformTree(tree, sourceFile, sourceString, config, errorReporter);
      root = factory.getResultNode();
      features = features.require(factory.getFeatures());
      root.setIsSyntheticBlock(true);
      root.putProp(Node.FEATURE_SET, features);

      if (config.parseJsDocDocumentation.shouldParseDescriptions()) {
        comments = p.getComments();
      }
    }
    return new ParseResult(root, comments, features, p.getInlineSourceMap());
  }

  private static com.google.javascript.jscomp.parsing.parser.Parser.Config newParserConfig(
      Config config) {
    LanguageMode languageMode = config.languageMode;
    boolean isStrictMode = config.strictMode == StrictMode.STRICT;
    Mode parserConfigLanguageMode;
    switch (languageMode) {
      case TYPESCRIPT:
        parserConfigLanguageMode = Mode.TYPESCRIPT;
        break;

      case ECMASCRIPT3:
        parserConfigLanguageMode = Mode.ES3;
        break;

      case ECMASCRIPT5:
        parserConfigLanguageMode = Mode.ES5;
        break;

      case ECMASCRIPT6:
      case ECMASCRIPT7:
      case ECMASCRIPT8:
        parserConfigLanguageMode = Mode.ES6_OR_GREATER;
        break;

      default:
        throw new IllegalStateException("unexpected language mode: " + languageMode);
    }
    return new com.google.javascript.jscomp.parsing.parser.Parser.Config(
        parserConfigLanguageMode, isStrictMode);
  }

  // TODO(sdh): this is less useful if we end up needing the node for library version detection
  public static FeatureSet detectFeatures(String sourcePath, String sourceString) {
    SourceFile file = new SourceFile(sourcePath, sourceString);
    ErrorReporter reporter = IRFactory.NULL_REPORTER;
    com.google.javascript.jscomp.parsing.parser.Parser.Config config =
        newParserConfig(IRFactory.NULL_CONFIG);
    Parser p = new Parser(config, new Es6ErrorReporter(reporter, false), file);
    ProgramTree tree = p.parseProgram();
    StaticSourceFile simpleSourceFile = new SimpleSourceFile(sourcePath, false);
    return IRFactory.detectFeatures(tree, simpleSourceFile, sourceString).require(p.getFeatures());
  }

  private static class Es6ErrorReporter
      extends com.google.javascript.jscomp.parsing.parser.util.ErrorReporter {
    private ErrorReporter reporter;
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
    @Nullable
    public final String sourceMap;

    public ParseResult(Node ast, List<Comment> comments, FeatureSet features, String sourceMap) {
      this.ast = ast;
      this.comments = comments;
      this.features = features;
      this.sourceMap = sourceMap;
    }
  }
}
