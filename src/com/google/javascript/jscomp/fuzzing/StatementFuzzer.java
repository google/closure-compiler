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

import org.json.JSONObject;

import java.util.Random;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class StatementFuzzer extends Dispatcher {

  public StatementFuzzer(Random random, ScopeManager scopeManager,
      JSONObject config, StringNumberGenerator snGenerator) {
    super(random, scopeManager, config, snGenerator);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.Dispatcher#initCandidates()
   */
  @Override
  protected void initCandidates() {
    candidates = new AbstractFuzzer[] {
        new BlockFuzzer(random, scopeManager, config, snGenerator),
        new VarFuzzer(random, scopeManager, config, snGenerator),
        new SimpleFuzzer(Token.EMPTY, "empty"),
        new ExprStmtFuzzer(random, scopeManager, config, snGenerator),
        new IfFuzzer(random, scopeManager, config, snGenerator),
        new WhileFuzzer(random, scopeManager, config, snGenerator),
        new DoWhileFuzzer(random, scopeManager, config, snGenerator),
        new ForFuzzer(random, scopeManager, config, snGenerator),
        new ForInFuzzer(random, scopeManager, config, snGenerator),
        new ContinueFuzzer(random, scopeManager, config),
        new BreakFuzzer(random, scopeManager, config),
        new ReturnFuzzer(random, scopeManager, config, snGenerator),
        new SwitchFuzzer(random, scopeManager, config, snGenerator),
        new LabelFuzzer(random, scopeManager, config, snGenerator),
        new ThrowFuzzer(random, scopeManager, config, snGenerator),
        new TryFuzzer(random, scopeManager, config, snGenerator)
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
