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

import java.util.Arrays;
import java.util.Set;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class SwitchFuzzer extends AbstractFuzzer {

  SwitchFuzzer(FuzzingContext context) {
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
    int numCases = budget > 2 ? context.random.nextInt(budget - 2) : 0;
    AbstractFuzzer[] fuzzers = new AbstractFuzzer[numCases + 1];
    CaseFuzzer caseFuzzer =
        new CaseFuzzer(context, Token.CASE);
    Arrays.fill(fuzzers, caseFuzzer);
    fuzzers[0] =
        new ExpressionFuzzer(context);
    if (numCases > 0) {
      int defaultClauseIndex = context.random.nextInt(numCases);
      fuzzers[defaultClauseIndex + 1] =
          new CaseFuzzer(context, Token.DEFAULT_CASE);
    }
    Scope localScope = context.scopeManager.localScope();
    localScope.switchNesting++;
    Node[] components = distribute(budget - 1, fuzzers);
    localScope.switchNesting--;
    return new Node(Token.SWITCH, components);
  }

  private static class CaseFuzzer extends AbstractFuzzer {
    private int nodeType;
    CaseFuzzer(FuzzingContext context, int nodeType) {
      super(context);
      this.nodeType = nodeType;
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
     */
    @Override
    protected boolean isEnough(int budget) {
      if (nodeType == Token.CASE) {
        return budget >= 3;
      } else {
        return budget >= 2;
      }
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
     */
    @Override
    protected Node generate(int budget, Set<Type> types) {
      Node clause = new Node(nodeType);
      if (nodeType == Token.CASE) {
        int valueBudget =
            (int) (budget * getOwnConfig().get("valueBudget").getAsDouble());
        if (valueBudget == 0) {
          valueBudget = 1;
        }
        clause.addChildToBack(
            new ExpressionFuzzer(context).
            generate(valueBudget));
        budget -= valueBudget;
      }
      // increase budget by one to generate the synthetic block node for free
      Node block = new BlockFuzzer(context).
          generate(budget + 1);
      // set it synthetic to conform the requirement from the compiler
      block.setIsSyntheticBlock(true);
      clause.addChildrenToBack(block);

      return clause;
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
     */
    @Override
    protected String getConfigName() {
      return "case";
    }

  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "switch";
  }
}
