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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import org.json.JSONObject;

import java.util.Random;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class ForFuzzer extends AbstractFuzzer {

  public ForFuzzer(Random random, ScopeManager scopeManager, JSONObject config,
      StringNumberGenerator snGenerator) {
    super(random, scopeManager, config, snGenerator);
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
   */
  @Override
  protected boolean isEnough(int budget) {
    return budget >= 2;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
   */
  @Override
  protected Node generate(int budget) {
    int totalHeaderBudget =
        (int) ((budget - 1) * getOwnConfig().optDouble("headBudget"));
    int bodyBudget = budget - 1 - totalHeaderBudget;
    ExpressionFuzzer exprFuzzer =
        new ExpressionFuzzer(random, scopeManager, config, snGenerator);
    AbstractFuzzer[] fuzzers = {
        new ForInitializerFuzzer(random, scopeManager, config, snGenerator),
        exprFuzzer, exprFuzzer};

    Node[] headers = distribute(totalHeaderBudget, fuzzers);
    Node node = new Node(Token.FOR, headers);
    scopeManager.localScope().loopNesting++;
    Node body = new BlockFuzzer(random, scopeManager, config, snGenerator).
        generate(bodyBudget);
    scopeManager.localScope().loopNesting--;
    node.addChildToBack(body);
    return node;
  }

  @Override
  protected Node fuzz(AbstractFuzzer fuzzer, int budget) {
    if (fuzzer.isEnough(budget)) {
      return fuzzer.generate(budget);
    } else {
      return new Node(Token.EMPTY);
    }
  }

  private class ForInitializerFuzzer extends Dispatcher {
    ForInitializerFuzzer(Random random, ScopeManager scopeManager,
        JSONObject config, StringNumberGenerator snGenerator) {
      super(random, scopeManager, config, snGenerator);
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.Dispatcher#initCandidates()
     */
    @Override
    protected void initCandidates() {
      candidates = new AbstractFuzzer[] {
        new VarFuzzer(random, scopeManager, config, snGenerator),
        new ExpressionFuzzer(random, scopeManager, config, snGenerator)
      };
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
     */
    @Override
    protected String getConfigName() {
      return "forInitializer";
    }

  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "for";
  }
}
