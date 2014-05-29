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

package com.google.javascript.jscomp.parsing.parser.trees;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

public class ComprehensionTree extends ParseTree {
  public static enum ComprehensionType {
    ARRAY,
    GENERATOR
  };

  public final ComprehensionType type;

  // The "for of" and "if" trees.
  public final ImmutableList<ParseTree> children;

  // The AssignmentExpression at the end of the ComprehensionTail.
  public final ParseTree tailExpression;

  public ComprehensionTree(
      SourceRange location,
      ComprehensionType type,
      ImmutableList<ParseTree> children,
      ParseTree tailExpression) {
    super(ParseTreeType.COMPREHENSION, location);
    this.type = type;
    this.children = children;
    this.tailExpression = tailExpression;
  }
}
