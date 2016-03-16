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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

import java.io.IOException;

/**
 * Generates an AST for a JavaScript source file.
 *
 */
public class JsAst implements SourceAst {
  private static final long serialVersionUID = 1L;

  private transient InputId inputId;
  private transient SourceFile sourceFile;
  private String fileName;
  private Node root;
  private FeatureSet features;

  public JsAst(SourceFile sourceFile) {
    this.inputId = new InputId(sourceFile.getName());
    this.sourceFile = sourceFile;
    this.fileName = sourceFile.getName();
  }

  @Override
  public Node getAstRoot(AbstractCompiler compiler) {
    if (root == null) {
      parse(compiler);
      root.setInputId(inputId);
    }
    return root;
  }

  @Override
  public void clearAst() {
    root = null;
    // While we're at it, clear out any saved text in the source file on
    // the assumption that if we're dumping the parse tree, then we probably
    // assume regenerating everything else is a smart idea also.
    sourceFile.clearCachedSource();
  }

  @Override
  public InputId getInputId() {
    return inputId;
  }

  @Override
  public SourceFile getSourceFile() {
    return sourceFile;
  }

  @Override
  public void setSourceFile(SourceFile file) {
    Preconditions.checkState(fileName.equals(file.getName()));
    sourceFile = file;
  }

  public FeatureSet getFeatures(AbstractCompiler compiler) {
    getAstRoot(compiler); // parse if required
    return features;
  }

  /** Simple class to share parse results between compilation jobs */
  public static class ParseResult {
    public final ImmutableList<JSError> errors;
    public final ImmutableList<JSError> warnings;
    ParseResult(ImmutableList<JSError> errors, ImmutableList<JSError> warnings) {
      this.errors = errors;
      this.warnings = warnings;
    }
  }

  private void parse(AbstractCompiler compiler) {
    ErrorManager errorManager = compiler.getErrorManager();
    int startErrorCount = errorManager.getErrorCount();
    int startWarningCount = errorManager.getWarningCount();
    try {
      ParserRunner.ParseResult result = ParserRunner.parse(
          sourceFile,
          sourceFile.getCode(),
          compiler.getParserConfig(sourceFile.isExtern()
                        ? AbstractCompiler.ConfigContext.EXTERNS
                        : AbstractCompiler.ConfigContext.DEFAULT),
          compiler.getDefaultErrorReporter());
      root = result.ast;
      features = result.features;

      if (compiler.isIdeMode()) {
        compiler.addComments(sourceFile.getName(), result.comments);
      }
    } catch (IOException e) {
      compiler.report(
          JSError.make(AbstractCompiler.READ_ERROR, sourceFile.getName()));
    }

    if (root == null
        // Most passes try to report as many errors as possible,
        // so there may already be errors. We only care if there were
        // errors in the code we just parsed.
        || (errorManager.getErrorCount() > startErrorCount && !compiler.isIdeMode())) {
      // There was a parse error or IOException, so use a dummy block.


      root = IR.script();
    } else {
      compiler.prepareAst(root);
    }

    if (errorManager.getErrorCount() > startErrorCount
        || errorManager.getWarningCount() > startWarningCount) {
      ParseResult result = new ParseResult(
          slice(errorManager.getErrors(), startErrorCount),
          slice(errorManager.getWarnings(), startWarningCount));
      root.putProp(Node.PARSE_RESULTS, result);
    }

    // Set the source name so that the compiler passes can track
    // the source file and module.
    root.setStaticSourceFile(sourceFile);
  }

  ImmutableList<JSError> slice(JSError[] errors, int startErrorCount) {
    if (errors.length > startErrorCount) {
      ImmutableList.Builder<JSError> builder = ImmutableList.<JSError>builder();
      for (int i = startErrorCount; i < errors.length; i++) {
        JSError error = errors[i];
        Preconditions.checkState(error.node == null);
        builder.add(errors[i]);
      }
      return builder.build();
    } else {
      return ImmutableList.of();
    }
  }
}
