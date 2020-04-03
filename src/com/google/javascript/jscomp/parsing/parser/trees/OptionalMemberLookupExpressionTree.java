/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

/**
 * Creates a OptionalMemberExpressionTree that represents a getprop expression within an optional
 * chain. Has an isOptionalChainStart field to indicate whether it is the start of an optional
 * chain.
 */
public class OptionalMemberLookupExpressionTree extends ParseTree {

  public final ParseTree operand;
  public final ParseTree memberExpression;
  public final boolean isStartOfOptionalChain;

  public OptionalMemberLookupExpressionTree(
      SourceRange location,
      ParseTree operand,
      ParseTree memberExpression,
      boolean isStartOfOptionalChain) {
    super(ParseTreeType.OPT_CHAIN_MEMBER_LOOKUP_EXPRESSION, location);

    this.operand = operand;
    this.memberExpression = memberExpression;
    this.isStartOfOptionalChain = isStartOfOptionalChain;
  }
}
