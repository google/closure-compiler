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

package com.google.javascript.jscomp.parsing.parser.trees;

import com.google.javascript.jscomp.parsing.parser.Token;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

/**
 * Represents UpdateExpression productions from the spec.
 *
 * <pre><code>
 * UpdateExpression :=
 *     { ++ | -- } UnaryExpression
 *     LeftHandSideExpression [no LineTerminator here] { ++ | -- }
 * </code></pre>
 */
public class UpdateExpressionTree extends ParseTree {

  /**
   * Position of the operator relative to the operand.
   */
  public enum OperatorPosition {
    PREFIX,
    POSTFIX;
  }

  public final Token operator;
  public final OperatorPosition operatorPosition;
  public final ParseTree operand;

  public UpdateExpressionTree(
      SourceRange location, Token operator, OperatorPosition operatorPosition, ParseTree operand) {
    super(ParseTreeType.UPDATE_EXPRESSION, location);

    this.operator = operator;
    this.operatorPosition = operatorPosition;
    this.operand = operand;
  }

  public static UpdateExpressionTree prefix(
      SourceRange location, Token operator, ParseTree operand) {
    return new UpdateExpressionTree(location, operator, OperatorPosition.PREFIX, operand);
  }

  public static UpdateExpressionTree postfix(
      SourceRange location, Token operator, ParseTree operand) {
    return new UpdateExpressionTree(location, operator, OperatorPosition.POSTFIX, operand);
  }
}
