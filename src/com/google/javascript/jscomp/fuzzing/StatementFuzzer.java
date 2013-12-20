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
class StatementFuzzer extends Dispatcher {

  StatementFuzzer(FuzzingContext context) {
    super(context);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.Dispatcher#initCandidates()
   */
  @Override
  protected void initCandidates() {
    candidates = new AbstractFuzzer[] {
        new BlockFuzzer(context),
        new VarFuzzer(context),
        new SimpleFuzzer(Token.EMPTY, "empty", Type.UNDEFINED),
        new ExprStmtFuzzer(context),
        new IfFuzzer(context),
        new WhileFuzzer(context),
        new DoWhileFuzzer(context),
        new ForFuzzer(context),
        new ForInFuzzer(context),
        new ContinueFuzzer(context),
        new BreakFuzzer(context),
        new ReturnFuzzer(context),
        new SwitchFuzzer(context),
        new LabelFuzzer(context),
        new ThrowFuzzer(context),
        new TryFuzzer(context)
    };
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "statement";
  }

}
