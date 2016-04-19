/*
 * Copyright 2016 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;

import java.io.IOException;

/**
 * Check for statements that should end with a semicolon according to the Google style guide.
 */
public final class CheckMissingSemicolon extends AbstractPostOrderCallback implements CompilerPass {
  public static final DiagnosticType MISSING_SEMICOLON =
      DiagnosticType.disabled("JSC_MISSING_SEMICOLON", "Missing semicolon");

  private final AbstractCompiler compiler;

  public CheckMissingSemicolon(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!n.isScript() && NodeUtil.isStatement(n) && shouldHaveSemicolon(n)) {
      checkSemicolon(t, n);
    }
  }

  private boolean shouldHaveSemicolon(Node statement) {
    if (statement.isFunction()
        || statement.isClass()
        || statement.isBlock()
        || statement.isLabelName()
        || (NodeUtil.isControlStructure(statement) && !statement.isDo())) {
      return false;
    }
    if (statement.isExport()) {
      return shouldHaveSemicolon(statement.getFirstChild());
    }
    return true;
  }

  private void checkSemicolon(NodeTraversal t, Node n) {
    StaticSourceFile staticSourceFile = n.getStaticSourceFile();
    if (staticSourceFile instanceof SourceFile) {
      SourceFile sourceFile = (SourceFile) staticSourceFile;

      String code;
      try {
        code = sourceFile.getCode();
      } catch (IOException e) {
        // We can't read the original source file. Just skip this check.
        return;
      }

      int length = n.getLength();
      if (length == 0) {
        // This check needs node lengths to work correctly. If we're not in IDE mode, we don't have
        // that information, so just skip the check.
        return;
      }
      int position = n.getSourceOffset() + length - 1;
      boolean endsWithSemicolon = code.charAt(position) == ';';
      if (!endsWithSemicolon) {
        t.report(n, MISSING_SEMICOLON);
      }
    }
  }
}
