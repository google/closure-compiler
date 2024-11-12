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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Generates an AST for a JavaScript source file.
 */
public class JsAst implements SourceAst {
  private static final long serialVersionUID = 1L;

  private final InputId inputId;
  private final SourceFile sourceFile;

  private @Nullable Node root;
  private FeatureSet features;

  public JsAst(SourceFile sourceFile) {
    this.inputId = new InputId(sourceFile.getName());
    this.sourceFile = sourceFile;
  }

  @Override
  public Node getAstRoot(AbstractCompiler compiler) {
    if (this.isParsed()) {
      return this.root;
    }

    Supplier<Node> astRootSource = compiler.getTypedAstDeserializer(this.sourceFile);
    if (astRootSource != null) {
      this.root = astRootSource.get();
      this.features = (FeatureSet) this.root.getProp(Node.FEATURE_SET);
    } else {
      this.parse(compiler);
    }
    checkState(identical(this.root.getStaticSourceFile(), this.sourceFile));
    this.root.setInputId(this.inputId);
    // Clear the cached source after parsing.  It will be re-read for snippet generation if needed.
    sourceFile.clearCachedSource();
    return this.root;
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

  public FeatureSet getFeatures(AbstractCompiler compiler) {
    getAstRoot(compiler); // parse if required
    return features;
  }

  boolean isParsed() {
    return root != null;
  }

  private void parse(AbstractCompiler compiler) {
    try {
      ParserRunner.ParseResult result =
          ParserRunner.parse(
              sourceFile,
              sourceFile.getCode(),
              compiler.getParserConfig(
                  sourceFile.isExtern()
                      ? AbstractCompiler.ConfigContext.EXTERNS
                      : AbstractCompiler.ConfigContext.DEFAULT),
              compiler.getDefaultErrorReporter());
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
    }

    // Set the source name so that the compiler passes can track
    // the source file and module.
    root.setStaticSourceFile(sourceFile);
  }
}
