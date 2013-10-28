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
enum Statement {
  BLOCK(1, 1),
  VAR(1, 1),
  EMPTY(1, 1),
  EXPR(2, 1),
  IF(3, 1),
  WHILE(3, 1),
  DO_WHILE(3, 1),
  FOR(2, 1),
  FOR_IN(4, 1),
  CONTINUE(1, 1),
  BREAK(1, 1),
  RETURN(1, 1),
  SWITCH(2, 1),
  LABEL(3, 1),
  THROW(2, 1),
  TRY(3, 1);

  int minBudget;
  double weight;

  Statement(int minBudget, double weight) {
    this.minBudget = minBudget;
    this.weight = weight;
  }
}
