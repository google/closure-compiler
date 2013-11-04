/*
 * Copyright 2013 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.fuzzing;

/**
 * @author zplin@google.com (Zhongpeng Lin)
 */
public enum Expression {
  THIS(1, 1),
  IDENTIFIER(1, 1),
  LITERAL(1, 1),
  FUNCTION_CALL(2, 1),
  UNARY_EXPR(2, 1),
  BINARY_EXPR(3, 1),
  FUNCTION_EXPR(3, 1),
  TERNARY_EXPR(4, 1);

  int minBudget;
  // the higher the weight an expression has, the more likely it will be
  // generated
  double weight;

  Expression(int minBudget, double weight) {
    this.minBudget = minBudget;
    this.weight = weight;
  }

}
