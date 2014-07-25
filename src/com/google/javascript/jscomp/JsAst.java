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
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Generates an AST for a JavaScript source file.
 *
 */
public class JsAst implements SourceAst {
  private static final Logger logger_ = Logger.getLogger(JsAst.class.getName());
  private static final long serialVersionUID = 1L;

  private transient InputId inputId;
  private transient SourceFile sourceFile;
  private String fileName;
  private Node root;

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

  private void parse(AbstractCompiler compiler) {
    int startErrorCount = compiler.getErrorManager().getErrorCount();
    try {
      ParserRunner.ParseResult result = ParserRunner.parse(
          sourceFile,
          sourceFile.getCode(),
          compiler.getParserConfig(sourceFile.isExtern()
                        ? AbstractCompiler.ConfigContext.EXTERNS
                        : AbstractCompiler.ConfigContext.DEFAULT),
          compiler.getDefaultErrorReporter());
      root = result.ast;
      if (compiler.isIdeMode()) {
        compiler.addComments(sourceFile.getName(), result.comments);
      }
    } catch (IOException e) {
      compiler.report(
          JSError.make(AbstractCompiler.READ_ERROR, sourceFile.getName()));
    }


    if (root == null ||
        // Most passes try to report as many errors as possible,
        // so there may already be errors. We only care if there were
        // errors in the code we just parsed.
        (compiler.getErrorManager().getErrorCount() > startErrorCount && !compiler.isIdeMode())) {
      // There was a parse error or IOException, so use a dummy block.
      root = IR.script();
    } else {
      compiler.prepareAst(root);
    }

    // Set the source name so that the compiler passes can track
    // the source file and module.
    root.setStaticSourceFile(sourceFile);
  }
}
