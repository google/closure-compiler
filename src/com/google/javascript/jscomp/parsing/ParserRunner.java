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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.head.CompilerEnvirons;
import com.google.javascript.rhino.head.Context;
import com.google.javascript.rhino.head.ErrorReporter;
import com.google.javascript.rhino.head.EvaluatorException;
import com.google.javascript.rhino.head.Parser;
import com.google.javascript.rhino.head.ast.AstRoot;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import java.io.IOException;
import java.util.HashSet;
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
    initResourceConfig();
    Set<String> effectiveAnnotationNames;
    if (extraAnnotationNames == null) {
      effectiveAnnotationNames = annotationNames;
    } else {
      effectiveAnnotationNames = new HashSet<String>(annotationNames);
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
   * @return The AST of the given text.
   * @throws IOException
   */
  public static ParseResult parse(StaticSourceFile sourceFile,
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
    if (astRoot != null) {
      root = IRFactory.transformTree(
          astRoot, sourceFile, sourceString, config, errorReporter);
      root.setIsSyntheticBlock(true);
    }
    return new ParseResult(root, astRoot);
  }

  /**
   * Holds results of parsing. Includes both ast formats.
   */
  public static class ParseResult {
    public final Node ast;
    public final AstRoot oldAst;

    public ParseResult(Node ast, AstRoot oldAst) {
      this.ast = ast;
      this.oldAst = oldAst;
    }
  }
}
