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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.Parser.Config.Mode;
import com.google.javascript.jscomp.parsing.parser.trees.ProgramTree;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.head.CompilerEnvirons;
import com.google.javascript.rhino.head.Context;
import com.google.javascript.rhino.head.ErrorReporter;
import com.google.javascript.rhino.head.EvaluatorException;
import com.google.javascript.rhino.head.Parser;
import com.google.javascript.rhino.head.Token;
import com.google.javascript.rhino.head.ast.AstNode;
import com.google.javascript.rhino.head.ast.AstRoot;
import com.google.javascript.rhino.head.ast.NodeVisitor;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Logger;

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
                                    boolean acceptConstKeyword) {
    return createConfig(isIdeMode, languageMode, acceptConstKeyword, null);
  }

  public static Config createConfig(boolean isIdeMode,
      LanguageMode languageMode,
      boolean acceptConstKeyword,
      Set<String> extraAnnotationNames) {
    return createConfig(isIdeMode, languageMode, acceptConstKeyword,
        extraAnnotationNames, false);
  }

  public static Config createConfig(boolean isIdeMode,
                                    LanguageMode languageMode,
                                    boolean acceptConstKeyword,
                                    Set<String> extraAnnotationNames,
                                    boolean useNewParser) {
    initResourceConfig();
    Set<String> effectiveAnnotationNames;
    if (extraAnnotationNames == null) {
      effectiveAnnotationNames = annotationNames;
    } else {
      effectiveAnnotationNames = new HashSet<>(annotationNames);
      effectiveAnnotationNames.addAll(extraAnnotationNames);
    }
    return new Config(effectiveAnnotationNames, suppressionNames,
        isIdeMode, languageMode, acceptConstKeyword, useNewParser);
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
    String[] names = configProp.split(",");
    Set<String> trimmedNames = Sets.newHashSet();
    for (String name : names) {
      trimmedNames.add(name.trim());
    }
    return ImmutableSet.copyOf(trimmedNames);
  }

  /**
   * Parses the JavaScript text given by a reader.
   *
   * @param sourceString Source code from the file.
   * @param errorReporter An error.
   * @param logger A logger.
   * @return A ParseResult of the given text. The comments field of the
   *     ParseResult will be an empty list if IDE mode is not enabled.
   * @throws IOException
   */
  public static ParseResult parse(StaticSourceFile sourceFile,
                                  String sourceString,
                                  Config config,
                                  ErrorReporter errorReporter,
                                  Logger logger) throws IOException {
    if (!useExperimentalParser(config)) {
      return parseEs5(sourceFile, sourceString, config, errorReporter, logger);
    } else {
      return parseEs6(sourceFile, sourceString, config, errorReporter, logger);
    }
  }

  private static boolean useExperimentalParser(Config config) {
    return config.useExperimentalParser
        || config.languageMode == LanguageMode.ECMASCRIPT6
        || config.languageMode == LanguageMode.ECMASCRIPT6_STRICT;
  }

  /**
   * Parses the JavaScript text given by a reader using Rhino.
   *
   * @param sourceString Source code from the file.
   * @param errorReporter An error.
   * @param logger A logger.
   * @return The AST of the given text.
   * @throws IOException
   */
  public static ParseResult parseEs5(StaticSourceFile sourceFile,
                                  String sourceString,
                                  Config config,
                                  ErrorReporter errorReporter,
                                  Logger logger) throws IOException {
    Context cx = Context.enter();
    cx.setErrorReporter(errorReporter);
    cx.setLanguageVersion(Context.VERSION_1_5);
    CompilerEnvirons compilerEnv = new CompilerEnvirons();
    compilerEnv.initFromContext(cx);
    compilerEnv.setRecordingComments(true);
    compilerEnv.setRecordingLocalJsDocComments(true);

    // ES5 specifically allows trailing commas
    compilerEnv.setWarnTrailingComma(
        config.languageMode == LanguageMode.ECMASCRIPT3);

    compilerEnv.setReservedKeywordAsIdentifier(true);

    compilerEnv.setAllowMemberExprAsFunctionName(false);
    compilerEnv.setIdeMode(config.isIdeMode);
    compilerEnv.setRecoverFromErrors(config.isIdeMode);

    Parser p = new Parser(compilerEnv, errorReporter);
    AstRoot astRoot = null;
    try {
      astRoot = p.parse(sourceString, sourceFile.getName(), 1);
    } catch (EvaluatorException e) {
      logger.info(
          "Error parsing " + sourceFile.getName() + ": " + e.getMessage());
    } finally {
      Context.exit();
    }
    Node root = null;
    final List<Comment> comments = Lists.newArrayList();
    if (astRoot != null) {
      root = IRFactory.transformTree(
          astRoot, sourceFile, sourceString, config, errorReporter);
      root.setIsSyntheticBlock(true);

      if (config.isIdeMode) {
        astRoot.visitAll(new NodeVisitor() {
            @Override
            public boolean visit(AstNode node) {
              if (node.getType() == Token.COMMENT) {
                comments.add(new CommentWrapper(
                    (com.google.javascript.rhino.head.ast.Comment) node));
              }
              return true;
            }
        });
      }
    }
    return new ParseResult(root, comments);
  }

  private static class Es6ErrorReporter
      extends com.google.javascript.jscomp.parsing.parser.util.ErrorReporter {
    private ErrorReporter reporter;
    private boolean errorSeen = false;
    private boolean isIdeMode;
    private com.google.javascript.jscomp.parsing.parser.SourceFile source;

    Es6ErrorReporter(
        ErrorReporter reporter,
        com.google.javascript.jscomp.parsing.parser.SourceFile source,
        Config config) {
      this.reporter = reporter;
      this.isIdeMode = config.isIdeMode;
      this.source = source;
    }

    @Override
    protected void reportMessage(
        SourcePosition location, String kind, String format,
        Object... arguments) {
      String message = SimpleFormat.format("%s",
          SimpleFormat.format(format, arguments));
      String sourceLine = source.getSnippet(location);
      switch (kind) {
        case "Error":
          if (isIdeMode || !errorSeen) {
            errorSeen = true;
            this.reporter.error(
                message, location.source.name,
                location.line + 1, sourceLine, location.column);
          }
          break;
        case "Warning":
          this.reporter.warning(
              message, location.source.name,
              location.line + 1, sourceLine, location.column);
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

  /**
   * Parses the JavaScript text using the ES6 parser.
   *
   * @param sourceString Source code from the file.
   * @param errorReporter An error.
   * @param logger A logger.
   * @return The AST of the given text.
   * @throws IOException
   */
  public static ParseResult parseEs6(StaticSourceFile sourceFile,
                                  String sourceString,
                                  Config config,
                                  ErrorReporter errorReporter,
                                  Logger logger) throws IOException {
    com.google.javascript.jscomp.parsing.parser.SourceFile file =
        new com.google.javascript.jscomp.parsing.parser.SourceFile(
            sourceFile.getName(), sourceString);
    Es6ErrorReporter es6ErrorReporter =
        new Es6ErrorReporter(errorReporter, file, config);
    com.google.javascript.jscomp.parsing.parser.Parser.Config es6config =
        new com.google.javascript.jscomp.parsing.parser.Parser.Config(mode(
            config.languageMode));
    com.google.javascript.jscomp.parsing.parser.Parser p =
        new com.google.javascript.jscomp.parsing.parser.Parser(
            es6config, es6ErrorReporter, file);
    ProgramTree tree = null;
    try {
      tree = p.parseProgram();
    } catch (EvaluatorException e) {
      logger.info(
          "Error parsing " + sourceFile.getName() + ": " + e.getMessage());
    }
    Node root = null;
    List<Comment> comments = ImmutableList.of();
    if (tree != null && (!es6ErrorReporter.hadError() || config.isIdeMode)) {
      root = NewIRFactory.transformTree(
          tree, sourceFile, sourceString, config, errorReporter);
      root.setIsSyntheticBlock(true);

      if (config.isIdeMode) {
        List<com.google.javascript.jscomp.parsing.parser.trees.Comment> parserComments =
            p.getComments();
        comments = Lists.newArrayListWithCapacity(parserComments.size());
        for (com.google.javascript.jscomp.parsing.parser.trees.Comment c : parserComments) {
          comments.add(new CommentWrapper(c));
        }
      }
    }
    return new ParseResult(root, comments);
  }

  private static Mode mode(
      LanguageMode mode) {
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
        return Mode.ES5_STRICT;
      default:
        throw new IllegalStateException("unexpected");
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
