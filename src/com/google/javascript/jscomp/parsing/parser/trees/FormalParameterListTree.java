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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

public class FormalParameterListTree extends ParseTree {
  public final ImmutableList<ParseTree> parameters;
  public final boolean hasTrailingComma;
  public final ImmutableList<SourcePosition> commaPositions;

  public FormalParameterListTree(
      SourceRange location,
      ImmutableList<ParseTree> parameters,
      boolean hasTrailingComma,
      ImmutableList<SourcePosition> commaPositions) {
    super(ParseTreeType.FORMAL_PARAMETER_LIST, location);
    int numParams = parameters.size();
    int numCommas = commaPositions.size();
    checkArgument(
        numCommas <= numParams && numCommas >= numParams - 1,
        "Unexpected # of comma and formal params.\nparams: %s\ncomma positions: %s",
        parameters,
        commaPositions);
    this.parameters = parameters;
    this.hasTrailingComma = hasTrailingComma;
    this.commaPositions = commaPositions;
  }
}
