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

import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class ForFuzzer extends AbstractFuzzer {

  ForFuzzer(FuzzingContext context) {
    super(context);
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
  protected Node generate(int budget, Set<Type> types) {
    int totalHeaderBudget =
        (int) ((budget - 1) * getOwnConfig().get("headBudget").getAsDouble());
    int bodyBudget = budget - 1 - totalHeaderBudget;
    ExpressionFuzzer exprFuzzer =
        new ExpressionFuzzer(context);
    AbstractFuzzer[] fuzzers = {
        new ForInitializerFuzzer(context),
        exprFuzzer, exprFuzzer};

    Node[] headers = distribute(totalHeaderBudget, fuzzers);
    Node node = new Node(Token.FOR, headers);
    context.scopeManager.localScope().loopNesting++;
    Node body = new BlockFuzzer(context).
        generate(bodyBudget);
    context.scopeManager.localScope().loopNesting--;
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

  private static class ForInitializerFuzzer extends Dispatcher {

    ForInitializerFuzzer(FuzzingContext context) {
      super(context);
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.Dispatcher#initCandidates()
     */
    @Override
    protected void initCandidates() {
      candidates = new AbstractFuzzer[] {
        new VarFuzzer(context),
        new ExpressionFuzzer(context)
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
