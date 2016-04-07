/*
 * Copyright 2010 The Closure Compiler Authors.
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
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

/**
 * An implementation of {@link SourceAst} that avoids re-creating the AST
 * unless it was manually cleared.  This creates a single defensive copy of the
 * AST; however, it is not safe for multiple compilations to use this
 * simultaneously, as all compilations mutate this.  Since this class copies
 * the tree, you instead should create a central RecoverableJsAst that does the
 * caching across compilations, and create new RecoverableJsAst's that act as
 * copying proxies around the original.
 *
 */
public class RecoverableJsAst implements SourceAst {

  private static final long serialVersionUID = 1L;

  // The AST copy that will be kept around.
  private Node root = null;

  // This is the actual SourceAst this caching wrapper wraps around.
  private final SourceAst realSource;

  private final boolean reportParseErrors;

  /**
   * Wraps around an existing SourceAst that provides caching between
   * compilations.
   */
  public RecoverableJsAst(SourceAst realSource, boolean reportParseErrors) {
    Preconditions.checkNotNull(realSource);
    this.realSource = realSource;
    this.reportParseErrors = reportParseErrors;
  }

  @Override
  public synchronized Node getAstRoot(AbstractCompiler compiler) {
    if (root == null) {
      // The original source (generally SourceAst) might not be thread-safe;
      // synchronize on it.
      synchronized (realSource) {
        root = realSource.getAstRoot(compiler).cloneTree(true);

        // Maybe replay parse error
        JsAst.ParseResult result = (JsAst.ParseResult) root.getProp(Node.PARSE_RESULTS);
        if (reportParseErrors && result != null) {
          replay(compiler.getErrorManager(), CheckLevel.ERROR, result.errors);
          replay(compiler.getErrorManager(), CheckLevel.WARNING, result.warnings);
        }
      }
    }
    return root;
  }

  private void replay(ErrorManager errorManager, CheckLevel level, ImmutableList<JSError> errors) {
    for (JSError error : errors) {
      errorManager.report(level, error);
    }
  }

  @Override
  public void clearAst() {
    // Just do a shallow clear; don't re-parse the input.
    this.root = null;
  }

  @Override
  public InputId getInputId() {
    synchronized (realSource) {
      return realSource.getInputId();
    }
  }

  @Override
  public SourceFile getSourceFile() {
    synchronized (realSource) {
      return realSource.getSourceFile();
    }
  }

  @Override
  public void setSourceFile(SourceFile file) {
    // Explicitly forbid this operation through this interface; this
    // RecoverableJsAst is a proxy view only.
    throw new UnsupportedOperationException();
  }
}
