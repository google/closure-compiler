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
package com.google.javascript.jscomp.testing;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.truth.Correspondence;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;

/** Well known {@link Correspondence} instances for use in tests. */
public final class JSCompCorrespondences {

  public static final Correspondence<JSError, DiagnosticType> DIAGNOSTIC_EQUALITY =
      Correspondence.transforming(JSError::getType, "has diagnostic type equal to");

  public static final Correspondence<JSError, String> DESCRIPTION_EQUALITY =
      Correspondence.transforming((e) -> e.getDescription(), "has description equal to");

  public static final Correspondence<CompilerInput, String> INPUT_NAME_EQUALITY =
      Correspondence.transforming(CompilerInput::getName, "has name equal to");

  public static final Correspondence<Node, String> EQUALITY_WHEN_PARSED_AS_EXPRESSION =
      Correspondence.from(
          (actual, expected) -> parseExpr(expected).isEquivalentTo(actual),
          "matches nodes parsed from");

  /** A compiler for parsing snippets of code into AST as leniently as possible. */
  private static final Compiler COMPILER_FOR_PARSING = new Compiler();

  /** Parses {@code expr} into an expression AST as leniently as possible. */
  private static Node parseExpr(String expr) {
    Node exprRoot =
        COMPILER_FOR_PARSING
            .parse(SourceFile.fromCode("expr", "(" + expr + ")")) // SCRIPT
            .getFirstChild() // EXPR_RESULT
            .getFirstChild(); // expr
    return checkNotNull(exprRoot, "Failed to parse expression");
  }

  // Not instantiable.
  private JSCompCorrespondences() {}
}
