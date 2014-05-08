/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Converts ES6 code to valid ES3 code.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public class Es6ToEs3Converter implements CompilerPass, NodeTraversal.Callback {
  private final AbstractCompiler compiler;

  private final LanguageMode languageIn;
  private final LanguageMode languageOut;

  public Es6ToEs3Converter(AbstractCompiler compiler, CompilerOptions options) {
    this.compiler = compiler;
    this.languageIn = options.getLanguageIn();
    this.languageOut = options.getLanguageOut();
  }

  public void process(Node externs, Node root) {
    if (languageOut != languageIn &&
        languageIn.isEs6OrHigher() && !languageOut.isEs6OrHigher()) {
      convert(root);
    }

    compiler.setLanguageMode(languageOut);
  }

  private void convert(Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.STRING_KEY:
        visitStringKey(n);
        break;
    }
  }

  /**
   * Convert extended object literal {a} to {a:a}.
   */
  private void visitStringKey(Node n) {
    if (!n.hasChildren()) {
      Node name = IR.name(n.getString());
      name.copyInformationFrom(n);
      n.addChildToBack(name);
      compiler.reportCodeChange();
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }
}

