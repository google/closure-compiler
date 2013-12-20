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

import com.google.javascript.rhino.Token;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class ExpressionFuzzer extends Dispatcher {

  ExpressionFuzzer(FuzzingContext context) {
    super(context);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.Dispatcher#initCandidates()
   */
  @Override
  protected void initCandidates() {
    candidates = new AbstractFuzzer[] {
        new SimpleFuzzer(Token.THIS, "this", Type.OBJECT),
        new ExistingIdentifierFuzzer(context),
        new LiteralFuzzer(context),
        new FunctionCallFuzzer(context),
        new UnaryExprFuzzer(context),
        new BinaryExprFuzzer(context),
        new TernaryExprFuzzer(context),
        new FunctionFuzzer(context, true)
      };
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "expression";
  }

}
