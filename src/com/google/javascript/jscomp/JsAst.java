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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * Generates an AST for a JavaScript source file.
 *
 */
public class JsAst implements SourceAst {
  private static final long serialVersionUID = 1L;

  private final InputId inputId;
  private SourceFile sourceFile;
  private final String fileName;
  private Node root;
  private FeatureSet features;

  public JsAst(SourceFile sourceFile) {
    this.inputId = new InputId(sourceFile.getName());
    this.sourceFile = sourceFile;
    this.fileName = sourceFile.getName();
  }

  @Override
  public Node getAstRoot(AbstractCompiler compiler) {
    if (!isParsed()) {
      parse(compiler);
      root.setInputId(inputId);
    }
    return checkNotNull(root);
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
    checkState(fileName.equals(file.getName()));
    sourceFile = file;
  }

  public FeatureSet getFeatures(AbstractCompiler compiler) {
    getAstRoot(compiler); // parse if required
    return features;
  }

  /** Representation of Rhino parser error. */
  public static class RhinoError implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String message;
    public final String sourceName;
    public final int line;
    public final int lineOffset;

    public RhinoError(String message, String sourceName, int line, int lineOffset) {
      this.message = message;
      this.sourceName = sourceName;
      this.line = line;
      this.lineOffset = lineOffset;
    }
  }

  /** Simple class to share parse results between compilation jobs */
  public static class ParseResult implements Serializable {
    private static final long serialVersionUID = 1L;

    public final ImmutableList<RhinoError> errors;
    public final ImmutableList<RhinoError> warnings;

    ParseResult(ImmutableList<RhinoError> errors, ImmutableList<RhinoError> warnings) {
      this.errors = errors;
      this.warnings = warnings;
    }
  }

  private static class RecordingReporterProxy implements ErrorReporter {
    final ArrayList<RhinoError> errors = new ArrayList<>();
    final ArrayList<RhinoError> warnings = new ArrayList<>();
    private final ErrorReporter delegateReporter;

    RecordingReporterProxy(ErrorReporter delegateReporter) {
      this.delegateReporter = delegateReporter;
    }

    @Override
    public void warning(String message, String sourceName, int line, int lineOffset) {
      warnings.add(new RhinoError(message, sourceName, line, lineOffset));
      delegateReporter.warning(message, sourceName, line, lineOffset);
    }

    @Override
    public void error(String message, String sourceName, int line, int lineOffset) {
      errors.add(new RhinoError(message, sourceName, line, lineOffset));
      delegateReporter.error(message, sourceName, line, lineOffset);
    }
  }

  boolean isParsed() {
    return root != null;
  }

  private void parse(AbstractCompiler compiler) {
    RecordingReporterProxy reporter = new RecordingReporterProxy(
        compiler.getDefaultErrorReporter());

    try {
      ParserRunner.ParseResult result = ParserRunner.parse(
          sourceFile,
          sourceFile.getCode(),
          compiler.getParserConfig(sourceFile.isExtern()
              ? AbstractCompiler.ConfigContext.EXTERNS
              : AbstractCompiler.ConfigContext.DEFAULT),
          reporter);
      root = result.ast;
      features = result.features;

      if (compiler.getOptions().preservesDetailedSourceInfo()) {
        compiler.addComments(sourceFile.getName(), result.comments);
      }
      if (result.sourceMapURL != null && compiler.getOptions().resolveSourceMapAnnotations) {
        boolean parseInline = compiler.getOptions().parseInlineSourceMaps;
        SourceFile sourceMapSourceFile =
            SourceMapResolver.extractSourceMap(sourceFile, result.sourceMapURL, parseInline);
        if (sourceMapSourceFile != null) {
          compiler.addInputSourceMap(sourceFile.getName(), new SourceMapInput(sourceMapSourceFile));
        }
      }
    } catch (IOException e) {
      compiler.report(
          JSError.make(AbstractCompiler.READ_ERROR, sourceFile.getName(), e.getMessage()));
    }

    if (root == null) {
      root = IR.script();
    } else {
      compiler.prepareAst(root);
    }

    if (!reporter.errors.isEmpty() || !reporter.warnings.isEmpty()) {
      ParseResult result = new ParseResult(
          ImmutableList.copyOf(reporter.errors),
          ImmutableList.copyOf(reporter.warnings));
      root.putProp(Node.PARSE_RESULTS, result);
    }

    // Set the source name so that the compiler passes can track
    // the source file and module.
    root.setStaticSourceFile(sourceFile);
  }

  @GwtIncompatible("ObjectinputStream")
  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    AbstractCompiler compiler = ((HasCompiler) in).getCompiler();
    in.defaultReadObject();
    // Retrieve the code from the compiler object.
    CompilerInput input = compiler.getInput(inputId);
    if (input != null) {
      sourceFile.restoreFrom(input.getSourceFile());
    }
  }
}
