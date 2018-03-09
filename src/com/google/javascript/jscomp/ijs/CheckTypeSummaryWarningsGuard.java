/*
 * Copyright 2018 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.ijs;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.FileAwareWarningsGuard;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;

/**
 * A warnings guard that sets the errors found in type summary files to be warnings, leaving only
 * the errors found in the original source.
 */
public class CheckTypeSummaryWarningsGuard extends FileAwareWarningsGuard {

  public CheckTypeSummaryWarningsGuard(AbstractCompiler compiler) {
    super(compiler);
  }

  @Override
  public CheckLevel level(JSError error) {
    if (inTypeSummary(error)) {
      return CheckLevel.WARNING;
    }
    return null;
  }

  @Override
  protected int getPriority() {
    // Treat warnings in .i.js files as though they are whitelisted.
    return Priority.SUPPRESS_BY_WHITELIST.getValue();
  }

  /** Return whether the given error was produced inside a type summary file */
  private boolean inTypeSummary(JSError error) {
    Node scriptNode = getScriptNodeForError(error);
    return scriptNode != null && NodeUtil.isFromTypeSummary(scriptNode);
  }
}
