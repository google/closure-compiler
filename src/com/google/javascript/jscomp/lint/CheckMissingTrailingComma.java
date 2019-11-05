/*
 * Copyright 2019 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import java.io.IOException;

/**
 * Check for array and object literals that should include a trailing comma before the closing brace
 * or bracket according to the Google style guide.
 */
public final class CheckMissingTrailingComma extends AbstractPostOrderCallback
    implements CompilerPass {
  public static final DiagnosticType MISSING_TRAILING_COMMA =
      DiagnosticType.disabled(
          "JSC_MISSING_TRAILING_COMMA",
          "Missing trailing comma");

  private final AbstractCompiler compiler;

  public CheckMissingTrailingComma(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    Node lastElement;
    if (n.isArrayLit()) {
      if (!n.hasChildren()) {
        return; // Adding a comma to an empty array would append a hole to it.
      }
      lastElement = n.getLastChild();
      if (lastElement.isEmpty()) {
        return; // Array has trailing holes; adding a comma would append another one.
      }
    } else if (n.isObjectLit()) {
      if (!n.hasChildren()) {
        return; // Can't add a comma to an empty object.
      }
      lastElement = n.getLastChild();
      switch (lastElement.getToken()) {
        case STRING_KEY:
        case MEMBER_FUNCTION_DEF:
        case GETTER_DEF:
        case SETTER_DEF:
          // Node is just the name; its child is the value.
          lastElement = lastElement.getOnlyChild();
          break;
        case COMPUTED_PROP:
        case OBJECT_SPREAD:
          break; // Node's span includes that of all its children.
        default:
          throw new IllegalArgumentException(
              "Unexpected child of OBJECTLIT: " + lastElement.toStringTree());
      }
    } else {
      return;
    }
    if (n.getLength() == 0) {
      return; // Length information is not available, so can't do this check.
    }
    int endOfLastElementPosition = lastElement.getSourceOffset() + lastElement.getLength();
    int closingBraceOrBracketPosition = n.getSourceOffset() + n.getLength() - 1;
    if (endOfLastElementPosition > closingBraceOrBracketPosition) {
      throw new IllegalArgumentException(
          "Unexpected positions: "
              + endOfLastElementPosition
              + " > "
              + closingBraceOrBracketPosition);
    }
    StaticSourceFile staticSourceFile = n.getStaticSourceFile();
    if (!(staticSourceFile instanceof SourceFile)) {
      return; // Source file is not available, so can't do this check.
    }
    String code;
    try {
      code = ((SourceFile) staticSourceFile).getCode();
    } catch (IOException e) {
      return;
    }
    String span = code.substring(endOfLastElementPosition, closingBraceOrBracketPosition);
    if (span.contains("\n") && !span.contains(",")) {
      t.report(n, MISSING_TRAILING_COMMA);
    }
  }
}
