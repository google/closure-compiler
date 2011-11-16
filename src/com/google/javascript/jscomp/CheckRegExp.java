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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.regex.RegExpTree;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.Node;

/**
 * Look for references to the global RegExp object that would cause
 * regular expressions to be unoptimizable, and checks that regular expressions
 * are syntactically valid.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class CheckRegExp extends AbstractPostOrderCallback implements CompilerPass {

  static final DiagnosticType REGEXP_REFERENCE =
    DiagnosticType.warning("JSC_REGEXP_REFERENCE",
        "References to the global RegExp object prevents " +
        "optimization of regular expressions.");
  static final DiagnosticType MALFORMED_REGEXP = DiagnosticType.warning(
        "JSC_MALFORMED_REGEXP",
        "Malformed Regular Expression: {0}");

  private final AbstractCompiler compiler;
  private boolean globalRegExpPropertiesUsed = false;

  public boolean isGlobalRegExpPropertiesUsed() {
    return globalRegExpPropertiesUsed;
  }

  public CheckRegExp(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (NodeUtil.isReferenceName(n)) {
      String name = n.getString();
      if (name.equals("RegExp") && t.getScope().getVar(name) == null) {
        int parentType = parent.getType();
        boolean first = (n == parent.getFirstChild());
        if (!((parentType == Token.NEW && first)
               || (parentType == Token.CALL && first)
               || (parentType == Token.INSTANCEOF && !first))) {
          t.report(n, REGEXP_REFERENCE);
          globalRegExpPropertiesUsed = true;
        }
      }

    // Check the syntax of regular expression patterns.
    } else if (n.isRegExp()) {
      String pattern = n.getFirstChild().getString();
      String flags = n.getChildCount() == 2
          ? n.getLastChild().getString() : "";
      try {
        RegExpTree.parseRegExp(pattern, flags);
      } catch (IllegalArgumentException ex) {
        t.report(n, MALFORMED_REGEXP, ex.getMessage());
      }
    }
  }
}
