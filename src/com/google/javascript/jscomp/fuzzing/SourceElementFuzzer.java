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

import org.json.JSONObject;

import java.util.Random;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
public class SourceElementFuzzer extends Dispatcher {
  public SourceElementFuzzer(Random random, ScopeManager scopeManager,
      JSONObject config, StringNumberGenerator snGenerator) {
    super(random, scopeManager, config, snGenerator);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.Dispatcher#initCandidates()
   */
  @Override
  protected void initCandidates() {
    candidates = new AbstractFuzzer[] {
      new StatementFuzzer(random, scopeManager, config, snGenerator),
      new FunctionFuzzer(random, scopeManager, config, snGenerator, false)
    };
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "sourceElement";
  }
}
