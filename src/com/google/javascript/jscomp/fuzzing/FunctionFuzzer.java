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

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.Random;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 */
class FunctionFuzzer extends AbstractFuzzer {
  private boolean isExpression;
  private IdentifierFuzzer idFuzzer;
  public FunctionFuzzer(Random random, ScopeManager scopeManager,
      JSONObject config, StringNumberGenerator snGenerator,
      boolean isExpression) {
    super(random, scopeManager, config, snGenerator);
    this.isExpression = isExpression;
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
   */
  @Override
  protected boolean isEnough(int budget) {
    if (isExpression) {
      return budget >= 3;
    } else {
      return budget >= 4;
    }
  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
   */
  @Override
  protected Node generate(int budget) {
    int paramBodyBudget;
    Node name;
    if (isExpression) {
      Preconditions.checkArgument(budget >= 3);
      scopeManager.addFunctionScope();
      if (budget >= 4 && random.nextInt(2) == 0) {
        // the name of function expression is only visible in the function
        name = getIdFuzzer().generate(1);
        paramBodyBudget = budget - 3;
      } else {
        name = Node.newString(Token.NAME, "");
        paramBodyBudget = budget - 2;
      }
    } else {
      Preconditions.checkArgument(budget >= 4);
      name = getIdFuzzer().generate(1);
      paramBodyBudget = budget - 3;
      scopeManager.addFunctionScope();
    }
    int numComponents =
        generateLength(paramBodyBudget - 1) + 1;
    AbstractFuzzer[] fuzzers = new AbstractFuzzer[numComponents];
    Arrays.fill(
        fuzzers,
        new SourceElementFuzzer(random, scopeManager, config, snGenerator));
    fuzzers[0] = new ParamListFuzzer(random, scopeManager, config, snGenerator);
    Node[] components = distribute(paramBodyBudget, fuzzers);
    Node body = new Node(Token.BLOCK);
    for (int i = 1; i < numComponents; i++) {
      body.addChildToBack(components[i]);
    }
    scopeManager.removeScope();
    return new Node(Token.FUNCTION,
        name, components[0], body);
  }

  /**
   * @return the idFuzzer
   */
  private IdentifierFuzzer getIdFuzzer() {
    if (idFuzzer == null) {
      idFuzzer = new IdentifierFuzzer(random, scopeManager, config, snGenerator);
    }
    return idFuzzer;
  }

  private class ParamListFuzzer extends AbstractFuzzer {

    ParamListFuzzer(Random random, ScopeManager scopeManager,
        JSONObject config, StringNumberGenerator snGenerator) {
      super(random, scopeManager, config, snGenerator);
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#isEnough(int)
     */
    @Override
    protected boolean isEnough(int budget) {
      return budget >= 1;
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#generate(int)
     */
    @Override
    protected Node generate(int budget) {
      Node node = new Node(Token.PARAM_LIST);
      for (int i = 0; i < budget - 1; i++) {
        node.addChildToBack(getIdFuzzer().generate(1));
      }
      return node;
    }

    /* (non-Javadoc)
     * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
     */
    @Override
    protected String getConfigName() {
      return null;
    }

  }

  /* (non-Javadoc)
   * @see com.google.javascript.jscomp.fuzzing.AbstractFuzzer#getConfigName()
   */
  @Override
  protected String getConfigName() {
    return "function";
  }
}
